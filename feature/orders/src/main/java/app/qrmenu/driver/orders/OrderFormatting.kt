package app.qrmenu.driver.orders

import java.util.Locale

/**
 * Money and distance, Latin-digit always (driver-ui-standards: "Numbers, phone
 * numbers, and money are always Latin-digit... regardless of locale"). `%.2f`
 * under `Locale.getDefault()` renders Eastern Arabic-Indic digits on an ar/ur
 * device — [Locale.US] is passed explicitly so this never happens, then the
 * caller wraps the result in `.ltr()` for direction isolation.
 *
 * 🔴 The currency comes from the ORDER, never from the device locale or a
 * remembered default: a driver may deliver for a Riyadh restaurant and a Cairo
 * one on the same day, and a fee shown as a bare number — or worse, in the
 * wrong currency — is one they cannot act on. `DriverOrderDto.currency` is
 * required on the wire for that reason.
 */
fun formatMoney(amount: Double, currency: String): String =
    String.format(Locale.US, "%.2f %s", amount, currency)

fun formatDistanceKm(distanceKm: Double?): String? =
    distanceKm?.let { String.format(Locale.US, "%.1f km", it) }
