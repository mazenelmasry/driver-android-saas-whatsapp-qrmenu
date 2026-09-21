package app.qrmenu.driver.health

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for `POST_NOTIFICATIONS` where it is actually needed: the moment a
 * signed-in driver lands in the app, not at the tail of the location ladder
 * (`:feature:availability` still owns that ladder's own NOTIFICATIONS rung
 * for the case a driver lands on it before this ever fires — both paths
 * check [NotificationPermissionAskOnce] and Android itself no-ops a request
 * for an already-granted permission, so the two cannot double-prompt).
 *
 * Call this once, unconditionally, near the top of the signed-in screen —
 * it does not block or gate anything; it fires a `LaunchedEffect(Unit)` and
 * gets out of the way.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Whatever the driver answered, this run is done asking.
        NotificationPermissionAskOnce.markAsked()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (
            NotificationPermissionRequestGate.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                alreadyAskedThisRun = NotificationPermissionAskOnce.hasAsked(),
                alreadyGranted = granted,
            )
        ) {
            // Marked BEFORE launching, not in the callback alone: the
            // callback only fires once the driver answers, and a resume
            // that races the still-open system dialog must not launch a
            // second one on top of it.
            NotificationPermissionAskOnce.markAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
