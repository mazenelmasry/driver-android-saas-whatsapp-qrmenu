package app.qrmenu.driver.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The continuous sound + vibration that plays while an offer is pending an
 * answer, independent of the notification's own (single-shot) channel
 * sound. A dispatch offer is time-boxed (CLAUDE.md §29 — 45s to accept) and
 * missing it hands the trip to someone else; a sound that plays once and
 * falls silent is easy to sleep through in a moving car. This is deliberately
 * NOT tied to the notification's lifecycle — [start] and [stop] are called
 * explicitly by whoever owns the offer's lifetime (answered, expired, or the
 * notification dismissed), so a feature module composes it without needing
 * to know anything about `NotificationManager`.
 */
@Singleton
class OfferAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /** Starts looping alert sound + repeating vibration. Idempotent. */
    fun start() {
        if (ringtone?.isPlaying == true) return
        startSound()
        startVibration()
    }

    /** Silences both. Safe to call even if [start] was never called. */
    fun stop() {
        ringtone?.let { tone ->
            if (tone.isPlaying) tone.stop()
        }
        ringtone = null

        vibrator?.cancel()
        vibrator = null
    }

    private fun startSound() {
        val uri = OfferNotificationChannels.offerSoundUri(context)
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = alarmAudioAttributes()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            tone.isLooping = true
        }
        tone.play()
        ringtone = tone

        // Ringtone#isLooping only exists from API 28. Below that, Ringtone
        // plays once and stops — acceptable degradation on very old
        // devices (minSdk 26/27), since the repeating vibration below still
        // keeps signalling the driver.
    }

    private fun startVibration() {
        val vib = systemVibrator() ?: return
        if (!vib.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, /* repeat index */ 0)
        vib.vibrate(effect)
        vibrator = vib
    }

    private fun systemVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }

    private fun alarmAudioAttributes(): AudioAttributes = OfferNotificationChannels.offerAudioAttributes()

    private companion object {
        // 500ms on, 300ms off, repeating from index 0 — audible over road
        // noise without being indistinguishable from a stuck buzzer.
        val VIBRATION_PATTERN = longArrayOf(0, 500, 300)
    }
}
