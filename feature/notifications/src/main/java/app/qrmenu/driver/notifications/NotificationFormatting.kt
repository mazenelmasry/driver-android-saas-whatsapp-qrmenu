package app.qrmenu.driver.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [Locale.US] is passed explicitly — a device set to Arabic/Urdu/Bengali/
 * Hindi renders Arabic-Indic or other native digits by default, and this
 * app's rule is Latin digits everywhere (see `WalletFormatting`/
 * `OrderFormatting` for the same pattern, and the recorded bug: a bare
 * `DateTimeFormatter` silently followed the device locale's digits).
 */
private val timeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US).withZone(ZoneId.systemDefault())

fun formatNotificationTime(occurredAtMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(occurredAtMillis))
