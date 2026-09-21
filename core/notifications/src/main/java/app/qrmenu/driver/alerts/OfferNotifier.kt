package app.qrmenu.driver.alerts

import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts (and cancels) the offer notification. This is the ONE place that
 * decides between a full-screen takeover and a plain heads-up fallback —
 * a feature module never needs to ask [FullScreenIntentEligibility] itself.
 *
 * This class does not start/stop [OfferAlarm] — sound/vibration lifetime is
 * owned by whoever knows when the offer was answered or expired, which this
 * class does not track. It only renders the visible notification.
 */
@Singleton
class OfferNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eligibility: FullScreenIntentEligibility,
) {

    /**
     * @param contentIntent opened when the notification body is tapped
     *   (normally the same destination as [fullScreenIntent], the offer
     *   screen).
     * @param fullScreenIntent the activity to launch full-screen when the
     *   platform allows it. Ignored (and the notification quietly degrades
     *   to a heads-up notification the driver must tap open) when it does
     *   not — see [FullScreenIntentEligibility].
     */
    fun notifyOffer(
        notificationId: Int,
        titleRes: Int,
        bodyRes: Int,
        contentIntent: PendingIntent,
        fullScreenIntent: PendingIntent,
    ) {
        val builder = NotificationCompat.Builder(context, OfferNotificationChannels.CHANNEL_OFFERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(bodyRes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // The eligibility check IS the fallback: when the platform will not
        // honour a full-screen takeover (API 34+ without the grant), the
        // exact same notification is still posted as an ordinary
        // high-priority heads-up notification via setContentIntent above —
        // the offer always reaches the driver, it just may need a tap
        // instead of taking the screen automatically.
        if (eligibility.isGranted()) {
            builder.setFullScreenIntent(fullScreenIntent, /* highPriority */ true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val OFFER_NOTIFICATION_ID = 4501
    }
}

/** Convenience for building the two [PendingIntent]s [OfferNotifier] needs from one activity intent. */
fun offerPendingIntent(context: Context, activityIntent: Intent, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        activityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
