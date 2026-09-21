package app.qrmenu.driver.trip

import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.DeclineRequest
import app.qrmenu.driver.network.dto.DriverOrderDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three offer commands (`ack`/`accept`/`decline`) plus the single fetch
 * that loads the offer's own data — a thin wrapper over [OrderApi], not a
 * cache, for the same reason `:feature:orders`' `OrdersRepository` is one:
 * an offer is either live on the server or it is not, and there is nothing to
 * act on stale here.
 *
 * 🔴 Every command generates its `Idempotency-Key` HERE, once, for the whole
 * call — never inside a retry loop (there is none; a failed command surfaces
 * to the driver as a retryable banner, and a driver TAP is what re-invokes
 * these methods, generating a fresh key for a fresh command, exactly as the
 * contract's own doc on `OrderApi` requires: "generated once per COMMAND —
 * never per attempt").
 */
@Singleton
class OfferRepository @Inject constructor(
    private val orderApi: OrderApi,
) {
    /** The offer as this driver may see it BEFORE deciding — see [DriverOrderDto] for the two wire shapes. */
    suspend fun fetch(orderId: Long): DriverOrderDto = orderApi.order(orderId)

    /** Fire-and-forget from the caller's point of view: the screen must stay usable even if this fails. */
    suspend fun ack(orderId: Long): AcceptedDto = orderApi.ack(orderId, idempotencyKey())

    /** 409 with `already_claimed`/`offer_expired`/`too_many_active_orders`/`order_cancelled`/`cash_limit_exceeded` on loss. */
    suspend fun accept(orderId: Long): DriverOrderDto = orderApi.accept(orderId, idempotencyKey())

    suspend fun decline(orderId: Long, reason: String): AcceptedDto =
        orderApi.decline(orderId, idempotencyKey(), DeclineRequest(reason = reason))

    private fun idempotencyKey(): String = UUID.randomUUID().toString()
}
