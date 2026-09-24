package app.qrmenu.driver.trip

/**
 * Pure "what should this row say, if anything" decisions for [TripScreen],
 * pulled out of the Composables so they are unit-testable without a Compose
 * UI test harness (this module has none — see `TripViewModelTest`/
 * `AddressTextTest` for the existing pure-JVM-logic style this follows).
 *
 * The rule they all enforce, per CLAUDE.md's driver-app bug report: a row
 * that pairs an icon/label with a value must not be drawn when that value is
 * absent OR blank. A `null` check alone is not enough — a backend field typed
 * as non-null `String` can still arrive as `""` or `"   "`, and to a driver
 * that reads exactly like a broken app, not like "this field is absent".
 */

/** A name-like field with nothing but whitespace is the same as no name at all. */
internal fun blankToNull(value: String?): String? = value?.takeIf { it.isNotBlank() }

/**
 * The single line [TripTopFacts] shows under the pin icon: the delivery
 * zone's name when there is a real one, otherwise the (URL-stripped) free-text
 * address as a fallback — never a blank zone name, which would otherwise win
 * the `?:` and hide the address text that was the whole point of the
 * fallback.
 */
internal fun displayableArea(zoneName: String?, addressText: String?): String? =
    blankToNull(zoneName) ?: stripMapLinks(addressText)

/**
 * The recipient's name for [TripRecipientCard] — `null` here means "hide the
 * whole card, label and all", not "draw the label above nothing".
 */
internal fun displayableRecipientName(name: String?): String? = blankToNull(name)

/**
 * The branch name for [TripTopFacts]'s first row.
 *
 * 🔴 Decision: hidden, not replaced with a placeholder, when blank — same as
 * every other row in this file. A fabricated "Unknown branch" string is not
 * more honest than showing nothing; it just moves the lie from an empty line
 * to a confidently wrong one, and this project already has no fallback string
 * for it. The branch's phone number (used by the "Call branch" quick action)
 * is guarded independently and unaffected by this — a driver who cannot read
 * the branch's name here can still call it.
 */
internal fun displayableBranchName(name: String?): String? = blankToNull(name)

/**
 * Whether [TripAddressCard] has anything at all to show beneath its
 * "العنوان" label. `false` means the card must not be composed — a label with
 * nothing under it (no address text, no notes, not an approximate pin, no map
 * link to offer) is the exact "bare shell" this decision exists to prevent.
 *
 * [addressText] should only be passed when the caller intends to show it
 * (mirrors `TripAddressCard`'s own `showAddressText` flag) — the caller
 * passes `null` otherwise, same as the Composable already does.
 */
internal fun addressCardHasContent(
    addressText: String?,
    notes: String?,
    isApproximateLocation: Boolean,
    showMapLink: Boolean,
): Boolean {
    val displayedText = stripMapLinks(addressText)
    val displayedNotes = stripMapLinks(notes)
    return displayedText != null || displayedNotes != null || isApproximateLocation || showMapLink
}
