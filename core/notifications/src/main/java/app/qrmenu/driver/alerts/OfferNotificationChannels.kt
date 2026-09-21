package app.qrmenu.driver.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two notification channels this app needs, and nothing more.
 *
 * An "offer" (a dispatch invitation the driver must answer within the
 * acceptance window — CLAUDE.md §29, 45s) is a fundamentally different kind
 * of interruption than everything else the app might tell the driver:
 * it is time-boxed, it pays the driver's rent, and missing it silently
 * hands the trip to someone else. It gets maximum importance, an insistent
 * sound and DND bypass. A generic channel exists for everything that is
 * NOT that (e.g. "your payout was processed") — collapsing the two into one
 * channel would force the driver to choose between muting rent-paying
 * offers and being buzzed for every minor update.
 */
@Singleton
class OfferNotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Idempotent — safe to call on every app start. Recreating a channel
     * with the same id and the same settings is a no-op; Android only lets
     * the *user* change importance/sound/vibration after creation, which is
     * intentional platform behaviour we must not fight.
     */
    fun ensureChannels() {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        manager.createNotificationChannel(buildOfferChannel())
        manager.createNotificationChannel(buildGeneralChannel())
    }

    private fun buildOfferChannel(): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_OFFERS,
            context.getString(R.string.notif_channel_offers_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_offers_description)
            enableVibration(true)
            vibrationPattern = OFFER_VIBRATION_PATTERN
            enableLights(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(offerSoundUri(context), offerAudioAttributes())
        }

        // Bypassing Do Not Disturb only takes effect once the user has
        // separately granted this app "Do Not Disturb access" in system
        // settings (NotificationHealth.isDndBypassGranted). Setting the
        // flag here is safe either way — Android silently ignores it until
        // that access is granted, so there is nothing to guard or catch.
        channel.setBypassDnd(true)
        return channel
    }

    private fun buildGeneralChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.notif_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_general_description)
        }

    companion object {
        const val CHANNEL_OFFERS = "offers"
        const val CHANNEL_GENERAL = "general"

        private val OFFER_VIBRATION_PATTERN = longArrayOf(0, 500, 250, 500, 250, 500)

        fun offerAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
            // USAGE_ALARM (not USAGE_NOTIFICATION_RINGTONE) is deliberate: it
            // is the one usage class that reliably plays through the phone's
            // ringer/media volume even when the user has the ringer on
            // silent-but-not-DND — an offer missed because the driver's
            // phone was face-down on "silent" is exactly the failure this
            // channel exists to prevent.
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /**
         * No custom audio asset is bundled (a licensed/branded alert sound
         * is a design asset, not something to fabricate here) — the
         * device's own alarm sound is used instead, which is exactly the
         * "insistent" register this channel needs and requires no binary
         * asset shipped in this module.
         */
        fun offerSoundUri(context: Context): Uri =
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }
}
