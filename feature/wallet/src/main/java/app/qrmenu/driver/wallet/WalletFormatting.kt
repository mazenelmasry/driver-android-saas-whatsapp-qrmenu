package app.qrmenu.driver.wallet

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Money and timestamps for the ledger, Latin-digit always (driver-ui-standards:
 * "Numbers, phone numbers, and money are always Latin-digit and
 * direction-isolated regardless of locale"). [Locale.US] is passed explicitly —
 * `%.2f` under `Locale.getDefault()` renders Eastern Arabic-Indic digits on an
 * ar/ur device — and the caller wraps the result in `.ltr()` for direction
 * isolation, exactly as `feature:orders`' `OrderFormatting.formatMoney` does.
 *
 * 🔴 The currency is whatever [LedgerSummaryDto.currency] / [SettlementDto]
 * says — never guessed, never a device default. A driver may hold books with
 * two restaurants in two countries at once; a bare number here is one they
 * cannot act on (binding rule 2, CLAUDE.md's «الدفتر المالى»).
 */
fun formatMoney(amount: Double, currency: String): String =
    String.format(Locale.US, "%.2f %s", amount, currency)

/**
 * A signed amount for a ledger row: `+12.00 SAR` for what the driver earned,
 * `-45.00 SAR` for cash they now owe back. The sign is drawn explicitly rather
 * than relying on the minus `%.2f` already prints for a negative [amount],
 * because a positive entry (an earning) needs its own `+` — without it,
 * "earned" and "owed" are told apart by colour alone, which the driver
 * standards explicitly forbid ("never colour alone").
 */
fun formatSignedMoney(amount: Double, currency: String): String {
    val sign = if (amount >= 0) "+" else "-"
    return String.format(Locale.US, "%s%.2f %s", sign, kotlin.math.abs(amount), currency)
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ZoneId.systemDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(ZoneId.systemDefault())

/**
 * "Today, 14:32" collapses to just the time; anything older carries its own
 * date. Returns null (never a raw ISO string) when the instant cannot be
 * parsed — the caller renders nothing rather than garbage, the same rule
 * `ReadinessCountdown` follows for a malformed timestamp.
 */
fun formatEntryTimestamp(isoInstant: String): String? {
    val instant = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return null
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val entryDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return if (entryDate == today) timeFormatter.format(instant) else dateTimeFormatter.format(instant)
}

/** A settlement's `created_at`, or its `period_from`/`period_to` when present — date-only, no time of day. */
fun formatEntryDate(isoDate: String): String? {
    val instant = runCatching { Instant.parse(isoDate) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()
        ?: return null
    return dateFormatter.format(instant)
}
