package app.qrmenu.driver.offers

import java.time.Instant
import java.time.OffsetDateTime

/**
 * The ONE place in `:app` that parses a server-sent instant string for an
 * offer's `expires_at`/`expiresAt`.
 *
 * The backend serialises with Laravel's `toIso8601String()` under
 * `APP_TIMEZONE=Asia/Riyadh`, which produces a NUMERIC OFFSET
 * (`2026-09-21T15:04:05+03:00`), not a `Z`-suffixed UTC instant.
 * `java.time.Instant.parse()` uses the strict `DateTimeFormatter.ISO_INSTANT`
 * pattern, which on JDK 8–11 java.time semantics REJECTS a numeric offset
 * outright (only `Z` is accepted; offset support was only relaxed in
 * JDK 12+). This app has no core-library desugaring enabled, so what an
 * individual device accepts here is whatever THAT device's platform
 * java.time implementation does — and the driver this app is built for is
 * exactly the profile most likely to be on an old one. A call site that only
 * ever tries `Instant.parse` would silently fail to parse every offer on
 * such a device, and an offer that never parses never rings — with no error
 * surfaced anywhere, because the existing call sites swallow the parse
 * failure into `null` (a plain "no live offer" reading, not a crash).
 *
 * [parseOfferInstant] tries the tolerant [OffsetDateTime] parse first (it
 * accepts both `Z` and a numeric offset) and only falls back to the strict
 * `Instant.parse` for a bare form neither of those covers. Garbage input
 * returns null from both, exactly like the old single-attempt call site did.
 */
fun parseOfferInstant(raw: String): Instant? =
    runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(raw) }.getOrNull()
