package app.qrmenu.driver.alerts

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this app is currently allowed to take over the screen with
 * [android.app.Notification.Builder.setFullScreenIntent] for an incoming
 * offer, and where to send the driver to fix it when it isn't.
 *
 * On Android 14 (API 34) and above, `USE_FULL_SCREEN_INTENT` is restricted:
 * declaring the permission in the manifest is no longer enough on its own
 * — the platform also requires the user (or, for a pre-installed app, the
 * OEM) to have explicitly granted it, and it can be revoked from Settings
 * at any time. [PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent]
 * is the actual decision the rest of this module trusts; this class only
 * supplies it with what the platform currently reports.
 */
@Singleton
class FullScreenIntentEligibility @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * True when a full-screen offer takeover will actually be honoured by
     * the platform right now. Below API 34 this was never gated, so it is
     * always true there.
     */
    fun isGranted(): Boolean =
        PureFullScreenIntentPolicy.shouldAttemptFullScreenIntent(
            sdkInt = Build.VERSION.SDK_INT,
            platformReportsGranted = platformGrant(),
        )

    private fun platformGrant(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService<NotificationManager>() ?: return false
        return manager.canUseFullScreenIntent()
    }

    /**
     * Deep-links to the system screen where the driver grants (or the OEM
     * shows why it withheld) full-screen-intent access. Only meaningful on
     * API 34+; callers should not offer this on older devices where the
     * permission is implicit.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}

/**
 * The platform-free half of the decision, kept separate from
 * [FullScreenIntentEligibility] so it can be unit-tested without a device,
 * an emulator, or Robolectric.
 */
object PureFullScreenIntentPolicy {

    /**
     * @param sdkInt the running device's `Build.VERSION.SDK_INT`.
     * @param platformReportsGranted what `NotificationManager.canUseFullScreenIntent()`
     *   returned — irrelevant, and ignored, below API 34.
     */
    fun shouldAttemptFullScreenIntent(sdkInt: Int, platformReportsGranted: Boolean): Boolean =
        if (sdkInt < 34) true else platformReportsGranted
}
