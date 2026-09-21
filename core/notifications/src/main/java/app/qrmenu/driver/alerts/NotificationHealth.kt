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
     * "Do Not Disturb access" is a system-wide grant per app (distinct from
     * the per-channel `setBypassDnd` flag, which only takes effect once
     * this is also true). There is no crash risk in checking it — the
     * platform simply reports false until the user opts in from Settings.
     */
    private fun isDndBypassGranted(manager: NotificationManager?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return manager?.isNotificationPolicyAccessGranted == true
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
