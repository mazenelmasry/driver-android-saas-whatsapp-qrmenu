package app.qrmenu.driver.orders

import java.time.Duration
import java.time.Instant

/** What [readinessState] renders. */
sealed class Readiness {
    /** Still cooking — the live "جاهز خلال ~N د" countdown. */
    data class Counting(val minutesRemaining: Long) : Readiness()

    /** The kitchen said so: the order carries a real `ready_at`. */
    data object Ready : Readiness()

    /**
     * The ESTIMATE has elapsed but the kitchen has not marked the order ready.
     *
     * 🔴 A separate state on purpose. An estimate running out is not the
     * kitchen finishing — treating it as [Ready] tells a driver the food is
     * waiting, sends them inside, and leaves them standing at a counter with
     * nothing to collect. It is also exactly the state in which "picked up"
     * must stay disabled, which only `ready_at` may unlock.
     */
    data object AwaitingKitchen : Readiness()

    /** Neither timestamp is present — no countdown rather than a wrong one. */
    data object Unknown : Readiness()
}

/**
 * Derives [Readiness] from an order's `ready_at`/`expected_ready_at` against
 * [now].
 *
 * 🔴 The two timestamps mean different things and are NOT interchangeable:
 * `ready_at` is a FACT the kitchen stamped, `expected_ready_at` is the
 * server's ESTIMATE from the branch's prep settings. A present `ready_at`
 * therefore means Ready outright — the kitchen does not un-finish an order,
 * and a clock skew of a few seconds must not turn a ready order back into a
 * countdown. An elapsed estimate, by contrast, means only that the guess ran
 * out: [Readiness.AwaitingKitchen], never [Readiness.Ready].
 */
fun readinessState(expectedReadyAt: String?, readyAt: String?, now: Instant): Readiness {
    if (readyAt?.let { runCatching { Instant.parse(it) }.getOrNull() } != null) {
        return Readiness.Ready
    }

    val expected = expectedReadyAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return Readiness.Unknown

    return if (now.isBefore(expected)) Readiness.Counting(minutesUntil(expected, now)) else Readiness.AwaitingKitchen
}

private fun minutesUntil(target: Instant, now: Instant): Long {
    val seconds = Duration.between(now, target).seconds
    // Round UP: "0 min left" must not be shown while there is still real time on
    // the clock — a driver reading "٠ د" would reasonably expect it in hand now.
    return ((seconds + 59) / 60).coerceAtLeast(1)
}
