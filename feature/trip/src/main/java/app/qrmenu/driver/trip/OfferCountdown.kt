package app.qrmenu.driver.trip

import java.time.Duration
import java.time.Instant

/**
 * 🔴 Seconds left, counted to the SERVER's absolute `offer.expires_at` — never
 * to "45 seconds from when this screen opened".
 *
 * A push that arrived 20 seconds late must show ~25 seconds left, not a fresh
 * 45: the 45-second window (decision 29) started at DISPATCH, on the server,
 * and every millisecond between dispatch and this phone rendering the screen
 * (network delay, Doze wake-up latency, a slow cold start) is time the driver
 * already does not have. Anchoring to the local clock instead would silently
 * extend every delayed offer and let a driver "accept" a trip the dispatcher
 * has already moved on from.
 *
 * Clamped at zero — never negative, so a caller can render it directly without
 * its own `coerceAtLeast`.
 */
fun remainingSeconds(expiresAt: Instant, now: Instant): Long =
    Duration.between(now, expiresAt).seconds.coerceAtLeast(0)

/** True once the server's instant has passed — the single source of "is this offer dead". */
fun isExpired(expiresAt: Instant, now: Instant): Boolean = !now.isBefore(expiresAt)
