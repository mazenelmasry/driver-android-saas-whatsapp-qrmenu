package app.qrmenu.driver.ui.text

/**
 * Direction isolation for the values this app is almost entirely made of:
 * phone numbers, amounts, distances, countdowns, version names.
 *
 * ## Why every one of them needs it
 * Arabic and Urdu lay text out right-to-left, and the Unicode bidirectional
 * algorithm then reorders any run of neutral characters — `+`, `-`, `.`, `:`,
 * spaces, parentheses — around the digits according to the SURROUNDING
 * direction, not the value's own. The digits themselves stay in logical order,
 * but everything between and around them moves:
 *
 *   * `+966501234567` renders as `966501234567+` — the plus jumps to the end,
 *     and a driver comparing it against the number the restaurant read out no
 *     longer sees the same string.
 *   * `1.0.0 (10000)` puts the bracket on the wrong side.
 *   * `12.50 SAR` and `4.2 km` swap their unit across.
 *
 * Wrapping the value in LRI…PDI (U+2066 / U+2069) tells the algorithm to treat
 * it as one left-to-right island and leave its interior alone, without changing
 * the direction of the sentence around it. This is the same job Android's
 * `BidiFormatter` does; it is written out here because it is four characters of
 * logic, it has no Android dependency, and it is used by every screen.
 *
 * Use it for a VALUE, never for a sentence: isolating Arabic prose would pin it
 * left-to-right and break it.
 */
object BidiText {

    private const val LRI = '⁦'
    private const val PDI = '⁩'

    /**
     * Returns [value] as a left-to-right island. Blank input is returned as-is —
     * isolating nothing only adds invisible characters that then show up in
     * string comparisons and in test failure messages.
     */
    fun ltr(value: String): String =
        if (value.isBlank()) value else "$LRI$value$PDI"

    /** Strips the isolation marks — for logging, tests, and anything that compares text. */
    fun strip(value: String): String = value.replace(LRI.toString(), "").replace(PDI.toString(), "")
}

/** Convenience for the call site: `phone.ltr()` reads better inside a Composable. */
fun String.ltr(): String = BidiText.ltr(this)
