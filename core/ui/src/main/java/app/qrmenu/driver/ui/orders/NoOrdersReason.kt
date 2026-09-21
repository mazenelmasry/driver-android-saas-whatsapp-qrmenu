package app.qrmenu.driver.ui.orders

import androidx.annotation.StringRes
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.ui.R

/**
 * Why the driver is not getting orders right now — decision 47 in Kotlin form.
 *
 * The wire vocabulary (`AvailabilityContextDto.reason`) is kept as a raw
 * `String` on the DTO on purpose (an unknown future reason must not fail the
 * decode), so this is where it becomes something the compiler can exhaustively
 * render. A reason this build has never heard of maps to [Unknown] — a
 * generic, still-honest line, deliberately NOT the same wording as
 * [NothingPending] (which promises "everything is fine, just wait") — never to
 * a blank card.
 *
 * It lives in `:core:ui` because TWO screens now render the same vocabulary:
 * the availability screen (which explains why the switch is on but nothing is
 * happening) and the orders list (which explains why "المتاحة" is empty). They
 * must never disagree, and a feature module depending on another feature
 * module to share a value type is not how this project is laid out — the same
 * reasoning that put the 26 error codes' text here.
 */
enum class NoOrdersReason(val wire: String?) {
    Offline("offline"),
    NoActiveLink("no_active_link"),
    AllBranchesClosed("all_branches_closed"),
    OutsideRadius("outside_radius"),
    LocationUnknown("location_unknown"),
    HasActiveTrip("has_active_trip"),

    /** The healthy case: online, in range, a branch is open — there is simply nothing to offer yet. */
    NothingPending("nothing_pending"),

    /** A wire value this build has never heard of. Not reachable via [wire]. */
    Unknown(null),
    ;

    companion object {
        private val byWire: Map<String, NoOrdersReason> =
            entries.filter { it.wire != null }.associateBy { it.wire!! }

        /** Null input (no context loaded yet) stays null — "we don't know" is not a reason to render. */
        fun fromWire(value: String?): NoOrdersReason? = value?.let { byWire[it] ?: Unknown }
    }
}

/** From the raw context DTO — null when there is no context to explain, or `data` was non-empty. */
fun AvailabilityContextDto.noOrdersReason(): NoOrdersReason? = NoOrdersReason.fromWire(reason)

@StringRes
fun NoOrdersReason.messageResource(): Int = when (this) {
    NoOrdersReason.Offline -> R.string.availability_reason_offline
    NoOrdersReason.NoActiveLink -> R.string.availability_reason_no_active_link
    NoOrdersReason.AllBranchesClosed -> R.string.availability_reason_all_branches_closed
    NoOrdersReason.OutsideRadius -> R.string.availability_reason_outside_radius
    NoOrdersReason.LocationUnknown -> R.string.availability_reason_location_unknown
    NoOrdersReason.HasActiveTrip -> R.string.availability_reason_has_active_trip
    NoOrdersReason.NothingPending -> R.string.availability_reason_nothing_pending
    NoOrdersReason.Unknown -> R.string.availability_reason_unknown
}
