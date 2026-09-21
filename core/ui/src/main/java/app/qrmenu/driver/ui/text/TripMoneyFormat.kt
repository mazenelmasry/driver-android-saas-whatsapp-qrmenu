package app.qrmenu.driver.ui.text

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats a trip money amount for display: two decimals, LATIN digits always,
 * bidi-isolated.
 *
 * ## Why not `String.format("%.2f", amount)` with the device locale
 * `Locale.getDefault()` on an Arabic device renders Eastern Arabic-Indic digits
 * (٠١٢٣…) by default on some OEM skins/ICU builds, and a driver comparing
 * `١٢٫٥٠ SAR` on screen against `12.50` on a printed receipt or the restaurant's
 * own register no longer sees the same string — money is exactly the value this
 * app cannot afford to let drift by locale. [DecimalFormatSymbols] is pinned to
 * [Locale.US] to force Latin digits and a `.` separator regardless of device
 * locale, matching the wire format the amount arrived in.
 *
 * ## Why it still needs [BidiText]
 * A left-to-right numeral run inside a right-to-left sentence still gets its
 * neighbouring punctuation and currency code reordered by the Unicode
 * bidirectional algorithm (see [BidiText]'s own doc) — `12.50 SAR` can render
 * with the currency code on the wrong side. Wrapping the whole formatted string
 * isolates it as one unit.
 *
 * `amount` is a `Double?` on purpose, matching `cash_collected` on the wire
 * (nullable = "already paid online, nothing to collect") — a null renders as
 * an em dash rather than being silently formatted as `0.00`, which would read
 * as "zero cash to collect" instead of "not applicable".
 */
object TripMoneyFormat {

    private const val NO_VALUE = "—"

    private fun formatter(): DecimalFormat =
        DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_UP
        }

    /** `12.50 SAR`, direction-isolated. Null amount renders as an em dash, never as `0.00`. */
    fun format(amount: Double?, currency: String): String {
        if (amount == null) return NO_VALUE.ltr()
        return "${formatter().format(amount)} $currency".ltr()
    }

    /** The bare number, no currency — for a field the driver EDITS (e.g. `cash_collected`). */
    fun formatAmount(amount: Double?): String =
        if (amount == null) "" else formatter().format(amount)
}
