package app.qrmenu.driver.health

import app.qrmenu.driver.alerts.NotificationHealth
import app.qrmenu.driver.alerts.NotificationHealthConcern

/**
 * Which ONE thing to tell a driver when several notification signals are
 * unhealthy at once, and in what order to check them.
 *
 * Pure and separate from the banner's Composable so "what do we say first"
 * is unit-testable without a Context.
 *
 * Order is root-cause-first: a driver told about a specific channel when
 * notifications are off ENTIRELY is being sent to the wrong settings screen
 * first — fixing the narrower problem does nothing until the broader one is
 * fixed too. DND bypass is checked last because it is the subtlest of the
 * three (a driver whose notifications and channel both work may never hit a
 * silenced DND window in a given session).
 *
 * 🔴 [NotificationHealthConcern.FULL_SCREEN_INTENT_NOT_GRANTED] is
 * deliberately never returned here — per the frozen design (CLAUDE.md §🔔 /
 * [NotificationHealth.isFullyHealthy]) the offer still arrives as a
 * heads-up notification when the platform refuses a full-screen takeover,
 * so that is not a failure worth alarming a driver over.
 */
object NotificationHealthPriority {

    fun primaryConcern(health: NotificationHealth): NotificationHealthConcern? = when {
        !health.notificationsEnabled -> NotificationHealthConcern.NOTIFICATIONS_DISABLED
        !health.offerChannelEnabled -> NotificationHealthConcern.OFFER_CHANNEL_DISABLED
        !health.dndBypassGranted -> NotificationHealthConcern.DND_BYPASS_NOT_GRANTED
        else -> null
    }
}
