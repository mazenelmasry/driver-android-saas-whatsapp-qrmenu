package app.qrmenu.driver.location.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The permission LADDER CLAUDE.md is explicit about — never request all of
 * these together:
 *
 * `ACCESS_FINE_LOCATION` → rationale screen → `ACCESS_BACKGROUND_LOCATION` →
 * `POST_NOTIFICATIONS`
 *
 * This is a state holder, not a Composable, so `:feature:availability` can
 * drive its own screen from it (per this task's scope — that screen belongs to
 * that module).
 */
enum class LocationPermissionStep {
    /** Request `ACCESS_FINE_LOCATION` (and COARSE alongside it). */
    FOREGROUND,

    /** Show the rationale screen BEFORE the background-location system prompt
     *  — Android requires a separate, non-simultaneous request from API 30+,
     *  and a driver who does not understand why is far more likely to refuse. */
    RATIONALE,

    /** Request `ACCESS_BACKGROUND_LOCATION`. */
    BACKGROUND,

    /** Request `POST_NOTIFICATIONS` (API 33+ only; a no-op step below it). */
    NOTIFICATIONS,

    /**
     * The ladder is climbed as far as it can be. NOT necessarily "background
     * granted" — see [LocationPermissionState.foregroundOnlyLimited]: refusing
     * "Allow all the time" still reaches this step, because CLAUDE.md's
     * red-banner design means foreground-only is a supported end state, not a
     * dead end the ladder gets stuck on.
     */
    DONE,
}

data class LocationPermissionState(
    val fineGranted: Boolean,
    val backgroundGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    /**
     * 🔴 Frozen decision: refusing "Allow all the time" must still WORK. This
     * is true whenever foreground is granted but background is not — the
     * service samples location only while the app is in the foreground, which
     * `:feature:availability`'s permanent red banner (this module does not
     * build the banner) communicates honestly rather than pretending
     * background tracking is happening.
     */
    val foregroundOnlyLimited: Boolean get() = fineGranted && !backgroundGranted

    val nextStep: LocationPermissionStep
        get() = when {
            !fineGranted -> LocationPermissionStep.FOREGROUND
            !backgroundGranted -> LocationPermissionStep.RATIONALE
            needsNotificationPermission() && !notificationsGranted -> LocationPermissionStep.NOTIFICATIONS
            else -> LocationPermissionStep.DONE
        }

    private fun needsNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

object LocationPermissions {

    val FOREGROUND = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val BACKGROUND: String = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    /** `null` below API 33 — there is nothing to request, and the caller must
     *  treat that as "already granted" rather than looping forever. */
    val NOTIFICATIONS: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun readState(context: Context): LocationPermissionState = LocationPermissionState(
        fineGranted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notificationsGranted = NOTIFICATIONS?.let { isGranted(context, it) } ?: true,
    )

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** For the permanent red banner's "open settings" button (decision 40). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
}
