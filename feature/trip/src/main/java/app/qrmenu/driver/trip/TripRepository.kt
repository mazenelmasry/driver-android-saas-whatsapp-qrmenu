package app.qrmenu.driver.trip

import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.DeliveredRequest
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.IssueRequest
import app.qrmenu.driver.network.dto.PickedUpRequest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The trip's four commands (`picked-up` / `delivered` / `issue` — `breadcrumbs`
 * is `:core:location`'s job, not this screen's) plus the fetch that resumes a
 * trip already in progress.
 *
 * Thin over [OrderApi] for the same reason [OfferRepository] is: nothing to
 * cache, nothing to retry on its own — a failed command surfaces to the
 * driver as [TripScreen]'s inline banner, and a driver TAP is what
 * re-invokes these methods. The `Idempotency-Key` is NOT generated here (contrast
 * [OfferRepository]): [TripViewModel] owns a command's key for its whole
 * lifetime — including every retry after a failure — because a retry that
 * minted a fresh key here would double-credit the ledger on `delivered`
 * exactly the failure mode the header on [app.qrmenu.driver.network.api.OrderApi]
 * warns about. The key is a plain parameter for that reason.
 */
@Singleton
class TripRepository @Inject constructor(
    private val orderApi: OrderApi,
) {
    /** Resumes a trip already in progress — after process death, the app was killed, or a fresh open of the tab. */
    suspend fun fetch(orderId: Long): DriverOrderDto = orderApi.order(orderId)

    suspend fun pickedUp(orderId: Long, idempotencyKey: String): DriverOrderDto =
        orderApi.pickedUp(
            id = orderId,
            idempotencyKey = idempotencyKey,
            body = PickedUpRequest(occurredAt = now()),
        )

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
     * asking the driver for it.
     */
    suspend fun delivered(
        orderId: Long,
        idempotencyKey: String,
        cashCollected: Double?,
        deliveryCode: String?,
        note: String?,
    ): DeliveredResponse = orderApi.delivered(
        id = orderId,
        idempotencyKey = idempotencyKey,
        body = DeliveredRequest(cashCollected = cashCollected, deliveryCode = deliveryCode, note = note, occurredAt = now()),
    )

    /** Deliberately does not touch order status — see [IssueRequest]'s own doc. */
    suspend fun issue(orderId: Long, idempotencyKey: String, code: String, note: String?): AcceptedDto =
        orderApi.issue(
            id = orderId,
            idempotencyKey = idempotencyKey,
            body = IssueRequest(code = code, note = note, occurredAt = now()),
        )

    private fun now(): String = Instant.now().toString()
}
