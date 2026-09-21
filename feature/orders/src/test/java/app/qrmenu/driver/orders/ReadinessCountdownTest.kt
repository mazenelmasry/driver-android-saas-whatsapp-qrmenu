package app.qrmenu.driver.orders

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessCountdownTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `neither timestamp present shows no countdown`() {
        assertEquals(Readiness.Unknown, readinessState(null, null, now))
    }

    @Test
    /**
     * `ready_at` is a stamp of something that ALREADY happened — the kitchen
     * tapped "ready". It can only read as future through clock skew between
     * the phone and the server, and a few seconds of skew must not turn a
     * ready order back into a countdown in the driver's hand.
     */
    fun `a present ready_at reads as ready even if the clock says it is seconds away`() {
        val readyAt = now.plusSeconds(300).toString()
        val expected = now.plusSeconds(1_000).toString()
        assertEquals(Readiness.Ready, readinessState(expected, readyAt, now))
    }

    @Test
    /**
     * 🔴 An estimate running out is NOT the kitchen finishing. Calling it
     * ready sends a driver inside to stand at a counter with nothing to
     * collect — and it is exactly the state in which "picked up" must stay
     * disabled, which only a real `ready_at` may unlock.
     */
    fun `an elapsed estimate waits on the kitchen instead of claiming ready`() {
        val expected = now.minusSeconds(120).toString()
        assertEquals(Readiness.AwaitingKitchen, readinessState(expected, null, now))
    }

    @Test
    fun `ready_at in the past reads as ready`() {
        val readyAt = now.minusSeconds(10).toString()
        assertEquals(Readiness.Ready, readinessState(null, readyAt, now))
    }

    @Test
    fun `expected_ready_at used when ready_at is absent`() {
        val expected = now.plusSeconds(120).toString()
        val result = readinessState(expected, null, now)
        assertTrue(result is Readiness.Counting)
        assertEquals(2L, (result as Readiness.Counting).minutesRemaining)
    }

    @Test
    fun `a partial minute rounds up so zero is never shown early`() {
        val expected = now.plusSeconds(61).toString()
        val result = readinessState(expected, null, now) as Readiness.Counting
        assertEquals(2L, result.minutesRemaining)
    }
}
