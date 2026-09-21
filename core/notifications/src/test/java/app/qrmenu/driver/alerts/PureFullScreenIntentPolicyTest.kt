package app.qrmenu.driver.alerts

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The whole point of this module's Android-14 handling: below API 34 the
 * restriction doesn't exist, so a full-screen offer is always attempted.
 * From API 34 the platform's own grant decides — and when it says no, the
 * offer must still fall back to a heads-up notification rather than
 * silently not reaching the driver.
 */
class PureFullScreenIntentPolicyTest {

    @Test
    fun `below API 34, full-screen is always attempted regardless of the platform flag`() {
        assertTrue(
            PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
                sdkInt = 33,
                platformReportsGranted = false,
            ),
        )
        assertTrue(
            PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
                sdkInt = 26,
                platformReportsGranted = false,
            ),
        )
    }

    @Test
    fun `on API 34+, full-screen is only attempted when the platform grants it`() {
        assertTrue(
            PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
                sdkInt = 34,
                platformReportsGranted = true,
            ),
        )
        assertFalse(
            PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
                sdkInt = 34,
                platformReportsGranted = false,
            ),
        )
    }

    @Test
    fun `a future SDK level is treated the same as API 34 - deny by default`() {
        assertFalse(
            PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
                sdkInt = 36,
                platformReportsGranted = false,
            ),
        )
    }
}
