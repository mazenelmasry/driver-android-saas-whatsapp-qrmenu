package app.qrmenu.driver.common.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenExpiryTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `a deadline in the future is not expired`() {
        assertFalse(TokenExpiry.isExpired(now + day, now))
    }

    @Test
    fun `a deadline in the past is expired`() {
        assertTrue(TokenExpiry.isExpired(now - 1, now))
    }

    @Test
    fun `the exact instant of the deadline is expired`() {
        assertTrue(TokenExpiry.isExpired(now, now))
    }

    /**
     * The server is the authority on validity. A login response that simply did
     * not carry an expiry must not sign the driver out of a session that works —
     * a dead token comes back as 401 and is cleared then.
     */
    @Test
    fun `an unknown deadline is treated as live, not expired`() {
        assertFalse(TokenExpiry.isExpired(null, now))
        assertFalse(TokenExpiry.needsRenewal(null, now))
        assertNull(TokenExpiry.daysRemaining(null, now))
    }

    @Test
    fun `renewal is due inside the window and not before it`() {
        assertTrue(TokenExpiry.needsRenewal(now + day - 1, now))
        assertFalse(TokenExpiry.needsRenewal(now + day + 1, now))
    }

    /**
     * An already-dead token is not "due for renewal" — it is a login. Saying
     * otherwise would have the app trying to refresh a token the server has
     * already forgotten, instead of showing the PIN screen.
     */
    @Test
    fun `an expired token does not report as needing renewal`() {
        assertFalse(TokenExpiry.needsRenewal(now - day, now))
    }

    @Test
    fun `days remaining floors and never goes negative`() {
        assertEquals(30L, TokenExpiry.daysRemaining(now + 30 * day, now))
        assertEquals(0L, TokenExpiry.daysRemaining(now + day - 1, now))
        assertEquals(0L, TokenExpiry.daysRemaining(now - 5 * day, now))
    }

    @Test
    fun `the documented lifetime is thirty days`() {
        assertEquals(30L, TokenExpiry.LIFETIME_DAYS)
    }
}
