package app.qrmenu.driver.alerts

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * [NotificationHealth.isFullyHealthy] is the single boolean the in-app
 * "notifications working ✅ / disabled ⚠️" indicator will read. Getting its
 * composition right matters more than any individual flag: a driver whose
 * offers can never actually wake their phone (DND bypass withheld) must not
 * be told everything is fine just because the app-level toggle is on.
 */
class NotificationHealthTest {

    @Test
    fun `fully healthy only when notifications, the offer channel and DND bypass all hold`() {
        val health = NotificationHealth.from(
            notificationsEnabled = true,
            offerChannelEnabled = true,
            fullScreenIntentGranted = true,
            dndBypassGranted = true,
        )

        assertTrue(health.isFullyHealthy)
    }

    @Test
    fun `notifications disabled at the OS level is unhealthy even if everything else is fine`() {
        val health = NotificationHealth.from(
            notificationsEnabled = false,
            offerChannelEnabled = true,
            fullScreenIntentGranted = true,
            dndBypassGranted = true,
        )

        assertFalse(health.isFullyHealthy)
    }

    @Test
    fun `the offer channel being muted is unhealthy even if the app-level toggle is on`() {
        val health = NotificationHealth.from(
            notificationsEnabled = true,
            offerChannelEnabled = false,
            fullScreenIntentGranted = true,
            dndBypassGranted = true,
        )

        assertFalse(health.isFullyHealthy)
    }

    @Test
    fun `DND bypass not granted is unhealthy - an offer during DND would ring silent`() {
        val health = NotificationHealth.from(
            notificationsEnabled = true,
            offerChannelEnabled = true,
            fullScreenIntentGranted = true,
            dndBypassGranted = false,
        )

        assertFalse(health.isFullyHealthy)
    }

    @Test
    fun `full-screen intent alone is NOT required for isFullyHealthy - the fallback makes it non-fatal`() {
        // FullScreenIntentEligibility already degrades gracefully to a
        // heads-up notification (OfferNotifier), so lacking it should warn
        // the driver about a lesser experience, not report the whole
        // notification pipeline as broken.
        val health = NotificationHealth.from(
            notificationsEnabled = true,
            offerChannelEnabled = true,
            fullScreenIntentGranted = false,
            dndBypassGranted = true,
        )

        assertTrue(health.isFullyHealthy)
    }
}
