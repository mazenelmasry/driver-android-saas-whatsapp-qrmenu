package app.qrmenu.driver.trip

/**
 * Defence in depth against a raw map link surfacing inside address text.
 *
 * The backend joins `delivery_address.text` fields with the Arabic comma
 * (`، `), and — for orders placed before the backend's own fix — may have
 * flattened a Google Maps URL the customer pasted directly into that string
 * (e.g. `شارع الملك فهد، https://maps.app.goo.gl/xYz، حي العليا`). A driver
 * must never see a raw URL in the middle of an address line (CLAUDE.md §
 * رمز استلام العميل — "لا يظهر رقم … / URL خام"-style rule for anything shown
 * to a driver or customer), and old rows already in the database still carry
 * it — the app cannot rely on the backend fix alone.
 *
 * Splits on any comma-like separator (Latin or Arabic) as well as on any
 * `http(s)://` token, drops the blank pieces a removed URL leaves behind, and
 * rejoins the remaining pieces with the same Arabic comma the backend itself
 * uses — so a plain address with no URL round-trips essentially unchanged
 * (only whitespace around each segment is normalised).
 *
 * 🔴 Blank input (empty string, or a string that is only whitespace) returns
 * `null`, not the blank string itself. Every call site treats a non-null
 * return as "there is something to show" via `?.let { ... }` — returning the
 * blank string back would satisfy that null-check while drawing an icon next
 * to nothing, which is exactly the "icon with no value" bug this whole pass
 * exists to remove (see `TripScreen.kt`'s guards on the delivery-area line and
 * the address card). Normalising here, once, means every caller downstream —
 * present and future — gets the safe contract for free instead of having to
 * remember an `isNotBlank()` check on top of the null check.
 */
private val urlPattern = Regex("""https?://\S+""")

/** Splits on a comma-like separator OR the `\u0000` marker a removed URL leaves behind. */
private val splitPattern = Regex("""[،,]|\u0000""")

internal fun stripMapLinks(text: String?): String? {
    if (text.isNullOrBlank()) return null

    val withoutUrls = urlPattern.replace(text, "\u0000")
    val segments = withoutUrls
        .split(splitPattern)
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return if (segments.isEmpty()) null else segments.joinToString(separator = "، ")
}
