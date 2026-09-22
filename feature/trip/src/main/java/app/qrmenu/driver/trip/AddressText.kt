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
 */
private val urlPattern = Regex("""https?://\S+""")

/** Splits on a comma-like separator OR the `\u0000` marker a removed URL leaves behind. */
private val splitPattern = Regex("""[،,]|\u0000""")

internal fun stripMapLinks(text: String?): String? {
    if (text.isNullOrBlank()) return text

    val withoutUrls = urlPattern.replace(text, "\u0000")
    val segments = withoutUrls
        .split(splitPattern)
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return if (segments.isEmpty()) null else segments.joinToString(separator = "، ")
}
