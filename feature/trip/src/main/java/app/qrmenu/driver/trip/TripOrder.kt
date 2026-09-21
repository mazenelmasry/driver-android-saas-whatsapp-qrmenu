package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.DriverOrderDto

/**
 * Week 5's own data surface, and the mirror image of [OfferSummary]'s
 * doc: THAT type is structurally incapable of holding a phone number or an
 * address because the driver has not earned the right to see one yet; this
 * screen's job starts the moment they have. [DriverOrderDto] already carries
 * [DriverOrderDto.customer] and [DriverOrderDto.deliveryAddress] for exactly
 * that reason (the ASSIGNED shape), so the trip screen reads the DTO
 * directly rather than mapping into a second type — there is nothing left to
 * hide from this screen, only something to STOP showing the instant the
 * order is handed over. See [strippedOfPii] for that half of the contract.
 */

/**
 * 🔴 The privacy rule's OTHER half (driver-ui-standards: "the customer's phone
 * number and address must disappear from both the UI AND any cached state the
 * moment the order is delivered").
 *
 * `DriverOrderAssigned` on the wire never stops carrying `customer`/
 * `delivery_address` once an order is delivered — the contract has no reason
 * to blank them server-side, since a completed trip's record legitimately
 * keeps them for the RESTAURANT's history. It is this app's OWN cached copy —
 * the [DriverOrderDto] sitting in [TripViewModel]'s state — that must not go
 * on holding them a moment after the driver's job with that customer is over.
 *
 * Called exactly once, right where `delivered()` succeeds, so the phone
 * number and address are never reachable from this screen's state again —
 * not through a later recomposition, not through process death and a Room
 * read-back, because there is nothing left in memory to read back.
 */
fun DriverOrderDto.strippedOfPii(): DriverOrderDto = copy(customer = null, deliveryAddress = null)

/** `status == "ready"` — the one instant "استلمت" stops being disabled (CLAUDE.md § دورة الرحلة). */
fun DriverOrderDto.isReadyForPickup(): Boolean = status == STATUS_READY

private const val STATUS_READY = "ready"

/**
 * Whether a driver-entered `cash_collected` requires the mandatory reason the
 * contract enforces server-side (`delivered` returns 422 without one for a
 * changed amount) — enforced here too so a driver sees why the button is
 * disabled instead of learning it from a failed request after a long press.
 *
 * A cent-level epsilon, not exact equality: the pre-filled amount round-trips
 * through a text field as a formatted string, and a floating-point compare
 * against the original [Double] must not manufacture a "changed" verdict out
 * of a rounding artefact the driver never touched.
 */
fun deliveryAmountChanged(cashToCollect: Double, enteredAmount: Double): Boolean =
    kotlin.math.abs(enteredAmount - cashToCollect) > 0.005

/**
 * `null` when the delivery may proceed as entered. A non-null value is
 * exactly the reason "تأكيد التسليم" stays disabled — see [TripScreen]'s
 * delivery sheet for where it renders.
 */
sealed interface DeliveryBlockReason {
    /** The amount field is blank — there is nothing to submit yet. */
    data object AmountRequired : DeliveryBlockReason

    /** A DIFFERENT amount than `cash_to_collect` with no note explaining the gap. */
    data object ReasonRequiredForChangedAmount : DeliveryBlockReason

    /**
     * 🔴 [DeliverySheetState.codeRequired] is `true` and the code field is
     * blank. The app has no way to know ahead of time whether an order
     * carries a delivery code (decision 48) — it only learns this the moment
     * a first `delivered` attempt with no code answers 422
     * `delivery_code_required`. From then on this reason blocks a resubmit
     * with an empty code client-side, rather than spending a second round trip
     * on a rejection the app already knows is coming.
     */
    data object CodeRequired : DeliveryBlockReason
}

/**
 * The single source of truth [TripScreen] and any test asks the same
 * question of: "can the driver confirm this delivery right now?"
 *
 * The delivery-code check runs FIRST and independently of [isCashOrder] — an
 * order paid online needs proof of handover exactly as much as a cash one,
 * arguably more (there is no cash exchange to serve as its own evidence).
 * `isCashOrder = false` only ever skips the AMOUNT checks below it (see
 * [TripScreen]'s doc on `cash_collected: null`).
 */
fun deliveryBlockReason(
    isCashOrder: Boolean,
    cashToCollect: Double,
    enteredAmount: Double?,
    note: String?,
    codeRequired: Boolean = false,
    enteredCode: String? = null,
): DeliveryBlockReason? {
    if (codeRequired && enteredCode.isNullOrBlank()) return DeliveryBlockReason.CodeRequired
    if (!isCashOrder) return null
    if (enteredAmount == null) return DeliveryBlockReason.AmountRequired
    val changed = deliveryAmountChanged(cashToCollect, enteredAmount)
    if (changed && note.isNullOrBlank()) return DeliveryBlockReason.ReasonRequiredForChangedAmount
    return null
}
