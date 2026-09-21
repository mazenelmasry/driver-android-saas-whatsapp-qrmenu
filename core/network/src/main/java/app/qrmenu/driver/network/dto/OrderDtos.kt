package app.qrmenu.driver.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One order as the driver sees it — covering BOTH contract shapes,
 * `DriverOrderOffered` and `DriverOrderAssigned`.
 *
 * 🔴 On the wire the two are different objects: before acceptance the customer
 * and the full address are STRUCTURALLY ABSENT — not null, not blanked
 * (decision 23). That guarantee is the SERVER's, and it is enforced there by the
 * schema so it cannot be broken by forgetting to clear a field.
 *
 * On this side the same guarantee arrives as NULLABILITY: [customer] and
 * [deliveryAddress] are null until this driver holds the order, and Kotlin then
 * makes it impossible to render a phone number or a street the driver has not
 * earned the right to see. Modelling it as one type is deliberate — the two
 * shapes share eighteen fields, and two near-identical classes would drift.
 * [isAssigned] states the distinction where a screen needs to branch on it.
 *
 * Never sent at all, in either shape: the customer's e-mail, coupons, discounts,
 * online-payment detail, staff notes.
 */
@Serializable
data class DriverOrderDto(
    val id: Long,
    @SerialName("order_number") val orderNumber: String,
    val status: String,
    @SerialName("delivery_method") val deliveryMethod: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_status") val paymentStatus: String,
    val total: Double,
    /**
     * The BRANCH's currency code, not the device's and not the account's:
     * one driver may hold links to restaurants in different countries, and
     * the per-(driver × restaurant) ledger exists for exactly that reason.
     * Required on the wire — an amount without it is a number the driver
     * cannot act on.
     */
    val currency: String,
    /** 0 for an order already paid online. */
    @SerialName("cash_to_collect") val cashToCollect: Double,
    /**
     * The SNAPSHOT stamped when the order was created (binding rule 3) — never
     * the live zone fee, which a "free delivery" offer zeroes out and with it
     * the driver's pay.
     */
    @SerialName("driver_fee") val driverFee: Double,
    val company: OrderCompanyDto,
    val branch: BranchDto,
    val zone: OrderZoneDto? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    val items: List<OrderItemDto> = emptyList(),
    /** The CUSTOMER's note. Staff notes are never sent. */
    val notes: String? = null,
    @SerialName("expected_ready_at") val expectedReadyAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    val offer: OfferDto? = null,

    // ── assigned-only: present only while THIS driver holds the order ──
    val customer: OrderCustomerDto? = null,
    @SerialName("delivery_address") val deliveryAddress: DeliveryAddressDto? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("claimed_at") val claimedAt: String? = null,
) {
    /**
     * True when the server sent the ASSIGNED shape. Read it from [customer]
     * rather than from [status]: the status vocabulary belongs to the order, the
     * visibility of the customer belongs to who is asking.
     */
    val isAssigned: Boolean get() = customer != null
}

@Serializable
data class OrderCompanyDto(
    val name: String,
    val logo: String? = null,
)

@Serializable
data class OrderZoneDto(val name: String)

@Serializable
data class OrderCustomerDto(
    val name: String,
    val phone: String,
)

@Serializable
data class DeliveryAddressDto(
    val text: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val notes: String? = null,
)

/** `Offer`. */
@Serializable
data class OfferDto(
    /**
     * 45 seconds from DISPATCH (decision 29). The countdown runs to this
     * instant, not to 45s from whenever the push happened to arrive — otherwise
     * a delayed notification silently extends every offer.
     */
    @SerialName("expires_at") val expiresAt: String,
    val wave: Int? = null,
)

/**
 * `AvailabilityContext` — WHY the available list looks the way it does.
 *
 * This is decision 47 in wire form. An empty list is the normal case for most of
 * a shift, and a silent blank screen is what makes a driver conclude the app is
 * broken (and ask for a country picker that would not have helped). Only the
 * server knows the radius and the candidate rules, so only the server can name
 * the reason.
 */
@Serializable
data class AvailabilityContextDto(
    @SerialName("is_online") val isOnline: Boolean,
    /** One trip at a time (decision 21), so a held order hides the rest. */
    @SerialName("has_active_trip") val hasActiveTrip: Boolean? = null,
    val branches: List<ContextBranchDto> = emptyList(),
    @SerialName("search_radius_km") val searchRadiusKm: Double? = null,
    /**
     * Null whenever `data` is non-empty. One of: offline · no_active_link ·
     * all_branches_closed · outside_radius · location_unknown · has_active_trip ·
     * nothing_pending. Kept as a String so an unknown future reason degrades to
     * a generic line instead of failing the decode.
     */
    val reason: String? = null,
)

/** A branch of an ACTIVE link, NAMED — so the driver can see the restaurant thinks they cover Riyadh while they are in Jeddah. */
@Serializable
data class ContextBranchDto(
    val id: Long,
    val name: String,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("is_open") val isOpen: Boolean,
    /** Null when the driver's position is unknown (offline, or no fix yet). */
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("within_radius") val withinRadius: Boolean? = null,
)

@Serializable
data class AvailableOrdersResponse(
    val data: List<DriverOrderDto> = emptyList(),
    val context: AvailabilityContextDto,
)

@Serializable
data class MyOrdersResponse(
    val data: List<DriverOrderDto> = emptyList(),
)

@Serializable
data class OrderHistoryResponse(
    val data: List<DriverOrderDto> = emptyList(),
    @SerialName("delivered_today") val deliveredToday: Int? = null,
)

@Serializable
data class AvailabilityRequest(val online: Boolean)

@Serializable
data class AvailabilityResponse(
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("online_since") val onlineSince: String? = null,
)

@Serializable
data class LocationBatchRequest(val points: List<LocationPointDto>)

@Serializable
data class DeclineRequest(val reason: String)

@Serializable
data class PickedUpRequest(
    @SerialName("occurred_at") val occurredAt: String? = null,
)

/**
 * `cash_collected` is pre-filled by the app with `cash_to_collect` and is
 * editable — but a DIFFERENT amount requires a note, because the gap between
 * what the order said and what the driver holds is exactly what the ledger
 * settles. Null for an order already paid.
 */
@Serializable
data class DeliveredRequest(
    @SerialName("cash_collected") val cashCollected: Double? = null,
    val note: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

@Serializable
data class DeliveredResponse(
    val order: DriverOrderDto,
    val ledger: LedgerSummaryDto,
)

/** Five reasons plus a note. Reporting a problem deliberately does NOT move the order. */
@Serializable
data class IssueRequest(
    val code: String,
    val note: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
)

/** The frozen `issue.code` vocabulary from the contract. */
enum class DriverIssueCode(val wire: String) {
    CustomerUnreachable("customer_unreachable"),
    AddressWrong("address_wrong"),
    CustomerRefused("customer_refused"),
    VehicleProblem("vehicle_problem"),
    Other("other"),
}

@Serializable
data class BreadcrumbsRequest(val points: List<LocationPointDto>)
