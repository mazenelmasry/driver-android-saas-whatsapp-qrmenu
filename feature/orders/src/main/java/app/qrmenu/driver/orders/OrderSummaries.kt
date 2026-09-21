package app.qrmenu.driver.orders

import app.qrmenu.driver.network.dto.DriverOrderDto

/**
 * The "المتاحة" card's entire data surface — built by [DriverOrderDto.toOfferedSummary]
 * below, which is the ONLY place a [DriverOrderDto] is read for this tab.
 *
 * 🔴 This is the pre-acceptance privacy rule (decision 23 / driver-ui-standards)
 * made a COMPILE-TIME property, not a discipline: this type simply has no field
 * that could hold a customer name, a phone number, or an address, so no
 * Composable that only ever receives an [OfferedOrderSummary] can render one —
 * not today, and not after a careless edit six months from now. The mapping
 * function below never reads `dto.customer` or `dto.deliveryAddress` at all,
 * so even a FUTURE non-null value on the wire (the contract slipping) cannot
 * leak through this type into [OfferedOrderCard].
 */
data class OfferedOrderSummary(
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
    val expectedReadyAt: String?,
    val readyAt: String?,
    val offerExpiresAt: String?,
)

/**
 * The "طلباتى" card's data surface — the ASSIGNED shape, where the customer
 * and address are exactly the fields this driver has earned the right to see
 * by holding the order. Built only from a [DriverOrderDto] whose
 * [DriverOrderDto.isAssigned] is already true (see [DriverOrderDto.toAssignedSummary]),
 * so an offer accidentally routed to "طلباتى" fails loudly (`require`) instead
 * of quietly rendering with blank contact fields.
 */
data class AssignedOrderSummary(
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
    val expectedReadyAt: String?,
    val readyAt: String?,
    val customerName: String,
    val customerPhone: String,
    val addressText: String,
    val pickedUpAt: String?,
)

/** The only place `DriverOrderDto` is read to build the pre-acceptance card. */
fun DriverOrderDto.toOfferedSummary(): OfferedOrderSummary = OfferedOrderSummary(
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
    expectedReadyAt = expectedReadyAt,
    readyAt = readyAt,
    offerExpiresAt = offer?.expiresAt,
)

/** Requires the ASSIGNED shape — see [AssignedOrderSummary]'s doc. */
fun DriverOrderDto.toAssignedSummary(): AssignedOrderSummary {
    val customer = requireNotNull(customer) { "toAssignedSummary() called on an order this driver does not hold (order $id)" }
    val address = deliveryAddress
    return AssignedOrderSummary(
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
        expectedReadyAt = expectedReadyAt,
        readyAt = readyAt,
        customerName = customer.name,
        customerPhone = customer.phone,
        addressText = address?.text.orEmpty(),
        pickedUpAt = pickedUpAt,
    )
}
