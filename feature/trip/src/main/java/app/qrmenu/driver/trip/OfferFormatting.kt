package app.qrmenu.driver.trip

import java.util.Locale

/**
 * Money, Latin-digit always (driver-ui-standards: "Numbers, phone numbers, and
 * money are always Latin-digit... regardless of locale"). [Locale.US] is
 * passed explicitly because `%.2f` under `Locale.getDefault()` renders Eastern
 * Arabic-Indic digits on an ar/ur device — the caller then wraps the result in
 * `.ltr()` for direction isolation. Mirrors `:feature:orders`' `formatMoney`;
 * duplicated rather than shared for the same reason [OfferSummary] is (see its
 * doc) — feature modules here do not depend on one another.
 */
fun formatMoney(amount: Double, currency: String): String =
    String.format(Locale.US, "%.2f %s", amount, currency)

fun formatDistanceKm(distanceKm: Double?): String? =
    distanceKm?.let { String.format(Locale.US, "%.1f km", it) }
