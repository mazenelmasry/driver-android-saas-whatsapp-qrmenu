package app.qrmenu.driver.trip

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferCountdownTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    /**
     * 🔴 The exact scenario the brief names: a push dispatched with a
     * 45-second window that only reached the phone 20 seconds later must show
     * ~25 seconds left, not a fresh 45 counted from when this screen opened.
     */
    @Test
    fun `a late-arriving push shows the time actually left, not 45 fresh seconds`() {
        val expiresAt = now.plusSeconds(25) // 45s window, 20s already spent in transit
        assertEquals(25L, remainingSeconds(expiresAt, now))
    }

    @Test
    fun `a fresh offer shows the full window`() {
        val expiresAt = now.plusSeconds(45)
        assertEquals(45L, remainingSeconds(expiresAt, now))
    }

    @Test
    fun `remaining seconds never goes negative`() {
        val expiresAt = now.minusSeconds(10)
        assertEquals(0L, remainingSeconds(expiresAt, now))
    }

    @Test
    fun `isExpired is false while time remains`() {
        assertFalse(isExpired(now.plusSeconds(1), now))
    }

    @Test
    fun `isExpired is true at the exact instant and after`() {
        assertTrue(isExpired(now, now))
        assertTrue(isExpired(now.minusSeconds(1), now))
    }
}
