package app.qrmenu.driver.trip

import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.database.entity.DriverActionType
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.BreadcrumbsRequest
import app.qrmenu.driver.network.dto.DeliveredRequest
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.IssueRequest
import app.qrmenu.driver.network.dto.PickedUpRequest
import app.qrmenu.driver.trip.outbox.OutboxFlushScheduler
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * The trip's four commands (`picked-up` / `delivered` / `issue` — `breadcrumbs`
 * is `:core:location`'s job, not this screen's) plus the fetch that resumes a
 * trip already in progress.
 *
 * Thin over [OrderApi] for the same reason [OfferRepository] is: nothing to
 * cache on top of the queue below, and a failed command still surfaces to the
 * driver as [TripScreen]'s inline banner — a driver TAP is what re-invokes
 * these methods while the app is foregrounded. The `Idempotency-Key` is NOT
 * generated here (contrast [OfferRepository]): [TripViewModel] owns a
 * command's key for its whole lifetime — including every retry after a
 * failure — because a retry that minted a fresh key here would double-credit
 * the ledger on `delivered`, exactly the failure mode the header on
 * [app.qrmenu.driver.network.api.OrderApi] warns about. The key is a plain
 * parameter for that reason, and it is ALSO the [DriverActionOutboxEntity]
 * primary key — the same value the driver's tap minted survives a process
 * death via Room, so a retry after the app was killed mid-request looks
 * identical, on the wire, to a retry the driver triggered by tapping again.
 *
 * 🔴 THE OFFLINE QUEUE (closes the gap the seed brief describes: a driver taps
 * "delivered" in a basement with no signal — the sheet used to show an error
 * and the key lived only in [TripViewModel]'s memory, so killing the app lost
 * the delivery AND the cash it collected, with nothing on either side to prove
 * either happened). Every command now writes its [DriverActionOutboxEntity]
 * row via [enqueueAction] BEFORE the network call is attempted — if the
 * process dies between the two, the row survives. [runQueued] then either
 * deletes the row on success ([DriverActionOutboxDao.acknowledge]) or, on a
 * transient failure, leaves it for [flushPending] to retry later
 * ([DriverActionOutboxDao.recordFailure]).
 *
 * MOST 4xx responses are a PURE REJECTION, never a partial write — the exact
 * reasoning [TripViewModel.confirmDelivery] already documents for
 * `delivery_code_required`/`delivery_code_mismatch` generalises to every
 * command: the server did not apply anything, and replaying the identical
 * body will only get the identical 4xx forever (a corrected retry, e.g. with
 * `delivery_code` filled in, is a DIFFERENT body under a DIFFERENT key that
 * [TripViewModel] mints itself). Those delete the outbox row.
 *
 * 🔴 But NOT every 4xx — 401, 408 and 429 mean "later", not "no", and
 * discarding their row would destroy the record of a delivery that really
 * happened. See [isPureRejection], which is where that line is drawn. Those,
 * and every genuinely retryable failure (offline, timeout, 5xx), keep the row
 * queued.
 */
@Singleton
class TripRepository @Inject constructor(
    private val orderApi: OrderApi,
    private val outboxDao: DriverActionOutboxDao,
    private val flushScheduler: OutboxFlushScheduler,
    private val tokenStore: TokenStore,
) {
    /** Resumes a trip already in progress — after process death, the app was killed, or a fresh open of the tab. */
    suspend fun fetch(orderId: Long): DriverOrderDto = orderApi.order(orderId)

    /**
     * 🔴 The idempotency key a still-queued row already holds for this exact
     * (order, action) pair — or `null` when there is none.
     *
     * [TripViewModel] mints one key per driver-committed action and is
     * supposed to reuse it across every retry, but that promise only held
     * within a single process lifetime: the key lived in a plain `var`, so a
     * driver who tapped "سلّمت", had the app killed before a response came
     * back, and reopened it, got a BRAND NEW key on the next tap — and a
     * fresh [DriverActionOutboxEntity] row alongside whatever the first tap
     * already queued. If both eventually reached the server (the first one
     * having only been slow, not actually lost), that is two `delivered`
     * calls for one delivery. [TripViewModel] calls this BEFORE minting a
     * fresh key so a queued row from before the process died is reused
     * instead of orphaned.
     *
     * Scoped to the driver signed in now, same rule [flushPending] already
     * applies — a row a previous driver on a shared device left queued must
     * not be handed back as if it belonged to whoever is signed in today.
     */
    suspend fun pendingKeyFor(orderId: Long, type: DriverActionType): String? {
        val currentDriverId = tokenStore.driverId.value
        return outboxDao.observePendingForDriver(currentDriverId).first()
            .firstOrNull { it.order_id == orderId && it.action_type == type }
            ?.idempotency_key
    }

    suspend fun pickedUp(orderId: Long, idempotencyKey: String): DriverOrderDto {
        val occurredAtMillis = System.currentTimeMillis()
        val body = PickedUpRequest(occurredAt = isoOf(occurredAtMillis))
        enqueueAction(idempotencyKey, orderId, DriverActionType.PickedUp, PickedUpRequest.serializer(), body, occurredAtMillis)
        return runQueued(idempotencyKey) { orderApi.pickedUp(id = orderId, idempotencyKey = idempotencyKey, body = body) }
    }

    /**
     * `cashCollected` is `null` for an order already paid online — never `0.0`
     * (see [DeliveredRequest]'s own doc: a zero is "collected nothing", a null
     * is "nothing to collect").
     *
     * `deliveryCode` is `null` on the first attempt of every delivery — the app
     * never knows ahead of time whether an order carries one (decision 48; see
     * [DeliveredRequest]'s own doc). It is filled in only once the server has
     * already answered 422 `delivery_code_required`/`delivery_code_mismatch`
     * for THIS delivery, which is exactly the moment [TripViewModel] starts
     * asking the driver for it — and mints a FRESH key for the corrected retry
     * (see the class doc on [isPureRejection]), so the row this call queues is
     * always for the exact body actually in flight.
     */
    suspend fun delivered(
        orderId: Long,
        idempotencyKey: String,
        cashCollected: Double?,
        deliveryCode: String?,
        note: String?,
    ): DeliveredResponse {
        val occurredAtMillis = System.currentTimeMillis()
        val body = DeliveredRequest(
            cashCollected = cashCollected,
            deliveryCode = deliveryCode,
            note = note,
            occurredAt = isoOf(occurredAtMillis),
        )
        enqueueAction(idempotencyKey, orderId, DriverActionType.Delivered, DeliveredRequest.serializer(), body, occurredAtMillis)
        return runQueued(idempotencyKey) { orderApi.delivered(id = orderId, idempotencyKey = idempotencyKey, body = body) }
    }

    /** Deliberately does not touch order status — see [IssueRequest]'s own doc. */
    suspend fun issue(orderId: Long, idempotencyKey: String, code: String, note: String?): AcceptedDto {
        val occurredAtMillis = System.currentTimeMillis()
        val body = IssueRequest(code = code, note = note, occurredAt = isoOf(occurredAtMillis))
        enqueueAction(idempotencyKey, orderId, DriverActionType.Issue, IssueRequest.serializer(), body, occurredAtMillis)
        return runQueued(idempotencyKey) { orderApi.issue(id = orderId, idempotencyKey = idempotencyKey, body = body) }
    }

    /**
     * Retries every row still queued, OLDEST FIRST (the table's own ordering —
     * picked-up before delivered for the same order), STOPPING at the first
     * row that is still not retryable rather than skipping ahead to a later
     * one: a driver holds one trip at a time (decision 21), so the queue is
     * for one order's own commands in the order they happened, and sending
     * `delivered` before a still-queued `picked-up` reached the server would
     * report the trip out of sequence.
     *
     * Called on screen entry ([TripViewModel.start]) and opportunistically
     * after any command that just succeeded (proof this device is online
     * right now) — see [TripViewModel]'s own doc for why that is the best this
     * module can do without a connectivity listener or WorkManager wired at
     * the app level.
     *
     * 🔴 Only replays rows that belong to the driver signed in RIGHT NOW (or
     * carry no owner at all — see [DriverActionOutboxEntity.driver_id]'s own
     * doc). On a shared device, driver B signing in must not inherit and
     * replay driver A's still-queued commands under B's token: the server
     * would answer 404 (order not B's) and [isPureRejection] would then
     * DELETE that row, permanently losing A's delivery/cash record. A row
     * skipped here simply stays queued — untouched — until A signs back in.
     */
    suspend fun flushPending() {
        val currentDriverId = tokenStore.driverId.value
        val pending = outboxDao.observePendingForDriver(currentDriverId).first()
        for (action in pending) {
            val succeeded = runCatching { replay(action) }.isSuccess
            // 🔴 A stuck `Breadcrumbs` row never blocks this drain — see
            // [DriverActionType.Breadcrumbs]'s own doc: unlike
            // picked-up/delivered/issue for ONE order, breadcrumb batches
            // carry no cross-row ordering guarantee this loop has to protect
            // (`:core:location`'s BreadcrumbCollector already retries them
            // independently on its own timer). Breaking here on a breadcrumb
            // failure would let an unrelated dead zone stall a queued
            // `delivered` behind it — the exact loss this whole table exists
            // to prevent, for the row that matters most.
            if (!succeeded && action.action_type != DriverActionType.Breadcrumbs) break
        }
    }

    private suspend fun replay(action: DriverActionOutboxEntity) {
        try {
            when (action.action_type) {
                DriverActionType.PickedUp -> {
                    val body = json.decodeFromString(PickedUpRequest.serializer(), action.payload_json)
                    orderApi.pickedUp(id = action.order_id, idempotencyKey = action.idempotency_key, body = body)
                }
                DriverActionType.Delivered -> {
                    val body = json.decodeFromString(DeliveredRequest.serializer(), action.payload_json)
                    orderApi.delivered(id = action.order_id, idempotencyKey = action.idempotency_key, body = body)
                }
                DriverActionType.Issue -> {
                    val body = json.decodeFromString(IssueRequest.serializer(), action.payload_json)
                    orderApi.issue(id = action.order_id, idempotencyKey = action.idempotency_key, body = body)
                }
                DriverActionType.Breadcrumbs -> {
                    // Belt-and-braces only — the primary drain for this type
                    // is `:core:location`'s `BreadcrumbOutbox`/`BreadcrumbCollector`,
                    // entirely self-contained and running on its own timer
                    // regardless of whether the trip screen is even open. This
                    // branch exists so a row THAT drain's own attempt left
                    // behind is still swept up whenever a trip command
                    // succeeds, the screen reopens, or `OutboxFlushWorker` runs.
                    val body = json.decodeFromString(BreadcrumbsRequest.serializer(), action.payload_json)
                    orderApi.breadcrumbs(id = action.order_id, idempotencyKey = action.idempotency_key, body = body)
                }
            }
            outboxDao.acknowledge(action.idempotency_key)
        } catch (thrown: Throwable) {
            recordOutcome(action.idempotency_key, thrown)
            throw thrown
        }
    }

    private suspend fun <T> enqueueAction(
        idempotencyKey: String,
        orderId: Long,
        type: DriverActionType,
        serializer: KSerializer<T>,
        body: T,
        occurredAtMillis: Long,
    ) {
        outboxDao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = idempotencyKey,
                order_id = orderId,
                action_type = type,
                payload_json = json.encodeToString(serializer, body),
                occurred_at = occurredAtMillis,
                created_at = System.currentTimeMillis(),
                driver_id = tokenStore.driverId.value,
            ),
        )
    }

    private suspend fun <T> runQueued(idempotencyKey: String, block: suspend () -> T): T {
        try {
            val result = block()
            outboxDao.acknowledge(idempotencyKey)
            return result
        } catch (thrown: Throwable) {
            recordOutcome(idempotencyKey, thrown)
            throw thrown
        }
    }

    /**
     * See the class doc: a 4xx is a pure rejection (nothing applied, an
     * unchanged retry can only fail again the same way) and its row is
     * DELETED — reusing [DriverActionOutboxDao.acknowledge] for that, since
     * "sent successfully" and "abandoned, nothing left to retry" both mean
     * the same thing to this table: the row stops existing. Anything else
     * (offline, timeout, a 5xx) is genuinely worth retrying with the SAME
     * body later, so [DriverActionOutboxDao.recordFailure] leaves it queued.
     */
    private suspend fun recordOutcome(idempotencyKey: String, thrown: Throwable) {
        if (isPureRejection(thrown)) {
            outboxDao.acknowledge(idempotencyKey)
        } else {
            outboxDao.recordFailure(idempotencyKey, thrown.message)
            // The row just survived a failure, so something has to come back
            // for it. Scheduling HERE — at the moment the row is left behind —
            // rather than only at app start is what lets a delivery reach the
            // restaurant while the driver's phone is in their pocket and the
            // app is closed.
            flushScheduler.scheduleFlush()
        }
    }

    /**
     * 🔴 NOT simply "any 4xx".
     *
     * Three 4xx codes are TRANSIENT, and deleting their row would destroy the
     * record of a delivery that really happened — the exact loss this whole
     * table exists to prevent:
     *
     *  - **401** the session expired. The same body succeeds once the driver
     *    signs in again, and the flush on next start is what sends it.
     *  - **408** a timeout is the server saying "ask me again", not "no".
     *  - **429** rate limiting is explicitly an expected state here
     *    (`rate_limited` is in the frozen error-code list), and a driver
     *    whose "سلّمت" happened to land in a throttled window must not lose
     *    the money record because of it.
     *
     * Every other 4xx is a real refusal of THIS body — a wrong delivery code,
     * an order already delivered, one that is not the driver's — where an
     * unchanged retry can only fail the same way forever.
     */
    private fun isPureRejection(thrown: Throwable): Boolean {
        val code = (thrown as? HttpException)?.code() ?: return false

        return code in 400..499 && code !in RETRYABLE_CLIENT_CODES
    }

    /** The device's real clock at the moment the driver committed the action — never re-stamped at send/replay time (see [replay]). */
    private fun isoOf(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** 4xx codes that mean "later", not "no" — see [isPureRejection]. */
        val RETRYABLE_CLIENT_CODES = setOf(401, 408, 429)
    }
}
