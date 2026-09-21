package app.qrmenu.driver.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `LedgerSummary` — the driver's book at ONE restaurant.
 *
 * 🔴 Per (driver × company), never netted across restaurants (binding rule 2).
 * That is also what keeps a multi-country driver's currencies apart, since
 * [currency] follows the company — summing two restaurants would add riyals to
 * pounds.
 */
@Serializable
data class LedgerSummaryDto(
    @SerialName("company_id") val companyId: Long,
    val currency: String,
    @SerialName("earned_today") val earnedToday: Double,
    /** Collected on the restaurant's behalf and not yet handed over. */
    @SerialName("cash_on_hand") val cashOnHand: Double,
    /**
     * Fees earned minus cash held. Positive = the restaurant owes the driver,
     * negative = the driver owes the restaurant.
     */
    val net: Double,
    /** Null or 0 means no ceiling. */
    @SerialName("cash_limit") val cashLimit: Double? = null,
)

/** `GET /driver/ledger` — the summary plus its entries (contract `allOf`). */
@Serializable
data class LedgerResponse(
    @SerialName("company_id") val companyId: Long,
    val currency: String,
    @SerialName("earned_today") val earnedToday: Double,
    @SerialName("cash_on_hand") val cashOnHand: Double,
    val net: Double,
    @SerialName("cash_limit") val cashLimit: Double? = null,
    val entries: List<LedgerEntryDto> = emptyList(),
)

@Serializable
data class LedgerEntryDto(
    val id: Long,
    /**
     * delivery_fee_earned · cash_collected · failed_trip_fee · settlement ·
     * manual_adjustment. A String, not an enum: an unrecognised future entry
     * type must render as a plain row, never fail the whole book's decode.
     */
    val type: String,
    val amount: Double,
    @SerialName("balance_after") val balanceAfter: Double,
    @SerialName("order_id") val orderId: Long? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SettlementDto(
    val id: Long,
    @SerialName("company_id") val companyId: Long,
    val amount: Double,
    val currency: String? = null,
    /** to_driver | from_driver. */
    val direction: String,
    @SerialName("period_from") val periodFrom: String? = null,
    @SerialName("period_to") val periodTo: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SettlementsResponse(
    val data: List<SettlementDto> = emptyList(),
)
