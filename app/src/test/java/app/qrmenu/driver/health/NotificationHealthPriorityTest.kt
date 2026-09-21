package app.qrmenu.driver.health

import app.qrmenu.driver.alerts.NotificationHealth
import app.qrmenu.driver.alerts.NotificationHealthConcern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationHealthPriorityTest {

    private fun health(
        notificationsEnabled: Boolean = true,
        offerChannelEnabled: Boolean = true,
        fullScreenIntentGranted: Boolean = true,
        dndBypassGranted: Boolean = true,
    ) = NotificationHealth(
        notificationsEnabled = notificationsEnabled,
        offerChannelEnabled = offerChannelEnabled,
        fullScreenIntentGranted = fullScreenIntentGranted,
        dndBypassGranted = dndBypassGranted,
    )

    @Test
    fun `fully healthy shows no concern`() {
        assertNull(NotificationHealthPriority.primaryConcern(health()))
    }

    @Test
    fun `only the full-screen-intent signal being unhealthy still shows nothing`() {
        // Frozen design: a refused full-screen takeover still delivers a
        // heads-up notification, so it must never surface as a failure.
        val result = NotificationHealthPriority.primaryConcern(
            health(fullScreenIntentGranted = false),
        )
        assertNull(result)
    }

    @Test
    fun `notifications disabled wins over every other broken signal`() {
        val result = NotificationHealthPriority.primaryConcern(
            health(
                notificationsEnabled = false,
                offerChannelEnabled = false,
                fullScreenIntentGranted = false,
                dndBypassGranted = false,
            ),
        )
        assertEquals(NotificationHealthConcern.NOTIFICATIONS_DISABLED, result)
    }

    @Test
    fun `channel disabled wins over dnd when notifications are on`() {
        val result = NotificationHealthPriority.primaryConcern(
            health(offerChannelEnabled = false, dndBypassGranted = false),
        )
        assertEquals(NotificationHealthConcern.OFFER_CHANNEL_DISABLED, result)
    }

    @Test
    fun `dnd bypass is the only thing left once notifications and channel are fine`() {
        val result = NotificationHealthPriority.primaryConcern(
            health(dndBypassGranted = false),
        )
        assertEquals(NotificationHealthConcern.DND_BYPASS_NOT_GRANTED, result)
    }
}
