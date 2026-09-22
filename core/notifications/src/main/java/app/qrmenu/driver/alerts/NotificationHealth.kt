package app.qrmenu.driver.alerts

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A snapshot of whether an offer notification will actually reach and wake
 * the driver right now, plus where to send them to fix each thing that
 * won't. This is the data source for the frozen "notifications working
 * ✅ / disabled ⚠️" in-app indicator (CLAUDE.md §🔔) — the indicator screen
 * itself is owned by a feature module; this only answers the question.
 */
data class NotificationHealth(
    val notificationsEnabled: Boolean,
    val offerChannelEnabled: Boolean,
    val fullScreenIntentGranted: Boolean,
    val dndBypassGranted: Boolean,
) {
    /** True only when every signal needed for a missed-offer-proof alert is in place. */
    val isFullyHealthy: Boolean
        get() = notificationsEnabled && offerChannelEnabled && dndBypassGranted

    companion object {
        /**
         * Pure assembly from already-read platform booleans — kept separate
         * from [NotificationHealthProvider.current] so the composition
         * itself (not the platform reads) is unit-testable.
         */
        fun from(
            notificationsEnabled: Boolean,
            offerChannelEnabled: Boolean,
            fullScreenIntentGranted: Boolean,
            dndBypassGranted: Boolean,
        ) = NotificationHealth(
            notificationsEnabled = notificationsEnabled,
            offerChannelEnabled = offerChannelEnabled,
            fullScreenIntentGranted = fullScreenIntentGranted,
            dndBypassGranted = dndBypassGranted,
        )
    }
}

/** One of the things [NotificationHealth] can be unhealthy about, and where to fix it. */
enum class NotificationHealthConcern {
    NOTIFICATIONS_DISABLED,
    OFFER_CHANNEL_DISABLED,
    FULL_SCREEN_INTENT_NOT_GRANTED,
    DND_BYPASS_NOT_GRANTED,
}

@Singleton
class NotificationHealthProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fullScreenIntentEligibility: FullScreenIntentEligibility,
) {

    fun current(): NotificationHealth {
        val managerCompat = NotificationManagerCompat.from(context)
        val manager = context.getSystemService<NotificationManager>()

        return NotificationHealth.from(
            notificationsEnabled = managerCompat.areNotificationsEnabled(),
            offerChannelEnabled = isOfferChannelEnabled(manager, managerCompat),
            fullScreenIntentGranted = fullScreenIntentEligibility.isGranted(),
            dndBypassGranted = isDndBypassGranted(manager),
        )
    }

    /**
     * The intent that opens the right system settings screen to resolve
     * [concern]. The caller (the in-app health indicator) is responsible
     * for only offering [NotificationHealthConcern.FULL_SCREEN_INTENT_NOT_GRANTED]
     * on API 34+, where it is meaningful.
     */
    fun settingsIntentFor(concern: NotificationHealthConcern): Intent = when (concern) {
        NotificationHealthConcern.NOTIFICATIONS_DISABLED,
        NotificationHealthConcern.OFFER_CHANNEL_DISABLED,
        -> appNotificationSettingsIntent()

        NotificationHealthConcern.FULL_SCREEN_INTENT_NOT_GRANTED ->
            fullScreenIntentEligibility.settingsIntent()

        NotificationHealthConcern.DND_BYPASS_NOT_GRANTED ->
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    private fun isOfferChannelEnabled(
        manager: NotificationManager?,
        managerCompat: NotificationManagerCompat,
    ): Boolean {
        if (!managerCompat.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = manager?.getNotificationChannel(OfferNotificationChannels.CHANNEL_OFFERS)
            ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 🔴 Answers "will Do Not Disturb silence an offer RIGHT NOW", not "does
     * this app hold DND policy access".
     *
     * It used to be the latter, and that is a permission the user must grant
     * by hand in system Settings — so it reads false on essentially every
     * phone, forever. The banner therefore appeared on every device with DND
     * switched OFF, which is where this was caught: `zen_mode = 0` on the
     * test device and the warning showing anyway.
     *
     * A permanent warning is worse than no warning. It is the top item in a
     * stack that also has to carry "notifications are disabled" and "you have
     * unsent deliveries" — a driver who learns the stack always has something
     * in it stops reading the stack, and the real alerts go with it.
     */
    private fun isDndBypassGranted(manager: NotificationManager?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (manager == null) return true

        // Nothing is being silenced, so there is nothing to warn about. This
        // is the normal state of almost every phone almost all the time, and
        // it is the check that was missing.
        if (!isDoNotDisturbActive(manager)) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // No channels to ask, so policy access is the only signal left.
            return manager.isNotificationPolicyAccessGranted
        }

        // DND IS on — the only question that matters now is whether the offer
        // channel is one of the things allowed through it.
        val channel = manager.getNotificationChannel(OfferNotificationChannels.CHANNEL_OFFERS)
            ?: return manager.isNotificationPolicyAccessGranted

        return channel.canBypassDnd()
    }

    /**
     * `INTERRUPTION_FILTER_UNKNOWN` is treated as "not active" on purpose:
     * the project's rule is that what cannot be judged is allowed, and the
     * cost of guessing wrong here is a permanent banner nobody can clear.
     */
    private fun isDoNotDisturbActive(manager: NotificationManager): Boolean =
        when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            -> false

            else -> true
        }

    private fun appNotificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }
}
