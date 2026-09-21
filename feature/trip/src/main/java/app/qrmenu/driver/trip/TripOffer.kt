package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.DriverOrderDto
import java.time.Instant

/**
 * The full-screen offer's entire data surface — decision 23 / driver-ui-standards'
 * pre-acceptance privacy rule made a COMPILE-TIME property, not a discipline
 * this screen has to uphold on its own.
 *
 * 🔴 This type has no field that could hold a customer name, a phone number,
 * or a full address, so no Composable that only ever receives an
 * [OfferSummary] can render one — not today, and not after a careless edit six
 * months from now. [DriverOrderDto.toOfferSummary] never reads `dto.customer`
 * or `dto.deliveryAddress` at all, so even a FUTURE non-null value on the wire
 * (the contract slipping) cannot leak through this type onto [OfferScreen].
 *
 * This mirrors `:feature:orders`' `OfferedOrderSummary` byte for byte. It is
 * not reused from there on purpose — feature modules in this project do not
 * depend on one another (see `:core:ui`'s `NoOrdersReason` doc for why a
 * SHARED vocabulary lives in `:core:ui` instead), and this type is small
 * enough that duplicating it costs less than the coupling would.
 */
data class OfferSummary(
    val id: Long,
    val currency: String,
    val companyName: String,
    val branchName: String,
    val zoneName: String?,
    val distanceKm: Double?,
    val driverFee: Double,
    val cashToCollect: Double,
    val isPaidOnline: Boolean,
    val itemCount: Int,
    /** The absolute server instant the offer dies at — never re-derived from "45s from now". */
    val expiresAt: Instant,
)

/**
 * The only place a [DriverOrderDto] is read to build the offer screen's data.
 *
 * Null when the order carries no live [DriverOrderDto.offer] (already resolved
 * by someone else, or the id was never an offer at all) or its `expires_at`
 * fails to parse — both callers treat that as the offer being gone, never as
 * zero seconds remaining on a countdown that would otherwise flash "٠".
 */
fun DriverOrderDto.toOfferSummary(): OfferSummary? {
    val expiresAt = offer?.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return OfferSummary(
        id = id,
        currency = currency,
        companyName = company.name,
        branchName = branch.name,
        zoneName = zone?.name,
        distanceKm = distanceKm,
        driverFee = driverFee,
        cashToCollect = cashToCollect,
        isPaidOnline = cashToCollect <= 0.0,
        itemCount = items.size,
        expiresAt = expiresAt,
    )
}
