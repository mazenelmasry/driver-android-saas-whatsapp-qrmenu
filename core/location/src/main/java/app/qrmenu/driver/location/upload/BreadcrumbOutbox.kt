package app.qrmenu.driver.location.upload

import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.database.entity.DriverActionType
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.BreadcrumbsRequest
import app.qrmenu.driver.network.dto.LocationPointDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Durable queue + best-effort sender for one order's breadcrumb trail — the
 * offline-safe half of `POST driver/orders/{id}/breadcrumbs`
 * ([app.qrmenu.driver.network.api.OrderApi.breadcrumbs]).
 *
 * 🔴 Deliberately reuses `driver_actions_outbox`
 * ([app.qrmenu.driver.database.dao.DriverActionOutboxDao]) — the SAME table
 * `:feature:trip`'s `TripRepository` queues `picked-up`/`delivered`/`issue`
 * in — rather than a second table. The task brief asks for exactly that ("if
 * the project has an operations/outbox queue with driver_id … reuse that
 * pattern rather than inventing a new one"), and the fit is not superficial:
 * a queued breadcrumb batch is, like those three, "unsent driver state that
 * must survive a process death and must not be replayed under a second
 * driver's session on a shared device" — [DriverActionOutboxEntity]'s own
 * doc on [DriverActionOutboxEntity.driver_id] applies here unchanged. One row
 * = one `Idempotency-Key` = one `points[]` batch (`payload_json`), exactly
 * the same shape [BreadcrumbsRequest] already is.
 *
 * ## Two independent drains, by design
 * A row this class enqueues is sent by EITHER of two paths, whichever gets
 * there first — both are safe to race because the server's `idempotency`
 * middleware on this route (`routes/api/driver.php`) already replays an
 * identical response for a repeated `Idempotency-Key`, and a row is "sent"
 * purely by ceasing to exist (see [DriverActionOutboxDao.acknowledge]'s own
 * doc):
 *
 *  1. **This class's own [enqueueAndSend]/periodic drain** (driven by
 *     [BreadcrumbCollector]) — self-contained inside `:core:location`, with
 *     no dependency on `:feature:trip`. This is what makes breadcrumbs
 *     upload "periodically" (task brief) even on a shift where no
 *     `picked-up`/`delivered`/`issue` command ever fails to trigger the
 *     other path below.
 *  2. **`TripRepository.flushPending()`** (feature:trip), because it drains
 *     the WHOLE table oldest-first, not just its own three action types —
 *     see the `Breadcrumbs` branch added to its `replay()`. That path is what
 *     catches a breadcrumb row this class's own send attempt left behind
 *     (still offline) whenever a trip command later succeeds, the trip
 *     screen reopens, or `OutboxFlushWorker` runs in the background — the
 *     same three occasions that already drain the table today.
 *
 * Never deletes a row on failure — a failed send is retried by drain path 2,
 * same as any other queued command. There is no "pure rejection" concept for
 * breadcrumbs the way [DriverActionOutboxDao]'s trip commands have one
 * (nothing about a breadcrumb batch can be permanently wrong the way a stale
 * delivery code is): every failure here is left queued.
 */
@Singleton
class BreadcrumbOutbox @Inject constructor(
    private val outboxDao: DriverActionOutboxDao,
    private val orderApi: OrderApi,
    private val tokenStore: TokenStore,
) {

    /** Writes the row, then makes ONE best-effort attempt to send it right now. */
    suspend fun enqueueAndSend(orderId: Long, points: List<LocationPointDto>) {
        if (points.isEmpty()) return
        val key = UUID.randomUUID().toString()
        val body = BreadcrumbsRequest(points = points)
        val now = System.currentTimeMillis()
        outboxDao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = key,
                order_id = orderId,
                action_type = DriverActionType.Breadcrumbs,
                payload_json = json.encodeToString(BreadcrumbsRequest.serializer(), body),
                occurred_at = now,
                created_at = now,
                driver_id = tokenStore.driverId.value,
            ),
        )
        attemptSend(key, orderId, body)
    }

    /**
     * Retries every breadcrumb row still queued for the driver signed in now
     * (or unowned — see [DriverActionOutboxEntity.driver_id]) — [enqueueAndSend]'s
     * own attempt already covers the common case, this is only for what that
     * attempt (this run's or a killed one's) left behind. Called on
     * [BreadcrumbCollector]'s periodic timer, so a long trip's trail does not
     * wait for a trip command to fail before it gets a second try.
     */
    suspend fun flushQueuedForCurrentDriver() {
        val driverId = tokenStore.driverId.value
        val rows = outboxDao.observePendingForDriver(driverId).first()
            .filter { it.action_type == DriverActionType.Breadcrumbs }
        for (row in rows) {
            val body = runCatching {
                json.decodeFromString(BreadcrumbsRequest.serializer(), row.payload_json)
            }.getOrNull() ?: continue
            attemptSend(row.idempotency_key, row.order_id, body)
        }
    }

    private suspend fun attemptSend(key: String, orderId: Long, body: BreadcrumbsRequest) {
        try {
            orderApi.breadcrumbs(id = orderId, idempotencyKey = key, body = body)
            outboxDao.acknowledge(key)
        } catch (cancelled: CancellationException) {
            // Not a send failure — see LocationUploader's own doc on the same
            // guard. Rethrown so structured concurrency actually cancels
            // instead of this being recorded as a retryable failure.
            throw cancelled
        } catch (thrown: Throwable) {
            outboxDao.recordFailure(key, thrown.message)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
