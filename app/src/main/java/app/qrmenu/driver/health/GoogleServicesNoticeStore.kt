package app.qrmenu.driver.health

import android.content.Context

/**
 * Whether the driver has already been told, on THIS install, that their phone
 * has no working Google Mobile Services and so no offer will ever ring.
 *
 * Persisted rather than process-scoped (unlike [NotificationPermissionAskOnce])
 * because the thing it gates is a FULL SCREEN, not a system dialog: a
 * process-lifetime flag would put that screen in front of the driver on every
 * cold start, and a screen shown every morning to say something that has not
 * changed since yesterday is a screen drivers learn to dismiss without reading.
 *
 * Shown once is deliberately not the same as "said once". The condition is
 * permanent for most of these devices, so the standing reminder belongs on the
 * notification-health banner that is already on the signed-in screen — this
 * store only stops the one-time full-screen explanation from repeating.
 *
 * Plain [android.content.SharedPreferences], not the encrypted store: there is
 * nothing here worth protecting, and pulling the token store's dependency in
 * for a boolean would be the wrong trade.
 */
object GoogleServicesNoticeStore {

    private const val PREFS = "driver_device_notices"
    private const val KEY_ACKNOWLEDGED = "gms_missing_acknowledged"

    fun hasAcknowledged(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACKNOWLEDGED, false)

    fun markAcknowledged(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACKNOWLEDGED, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
