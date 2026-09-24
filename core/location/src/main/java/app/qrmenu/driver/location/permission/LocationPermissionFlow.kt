package app.qrmenu.driver.location.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/**
 * The reusable half of the ladder: a state holder + launchers
 * `:feature:availability`'s screen drives, so that screen owns layout and copy
 * while this module owns "which system dialog fires next and in what order."
 *
 * Deliberately does NOT auto-advance past [LocationPermissionStep.RATIONALE] —
 * that step is a screen the calling feature renders and the driver must
 * actively continue past, per CLAUDE.md's "لا تطلبها دفعة واحدة".
 */
class LocationPermissionFlowState internal constructor(
    initialState: LocationPermissionState,
    initialForegroundPermanentlyDenied: Boolean,
    internal var requestForeground: () -> Unit,
    internal var requestBackground: () -> Unit,
    internal var requestNotifications: () -> Unit,
    internal var openAppSettings: () -> Unit,
) {
    var state: LocationPermissionState by mutableStateOf(initialState)
        internal set

    /**
     * 🔴 True once a FOREGROUND request has come back denied with
     * `shouldShowRequestPermissionRationale` also false — Android's own
     * signal that "don't ask again" (or an OS-level block) is in effect.
     * From this point on, [ActivityResultContracts.RequestMultiplePermissions]
     * no longer shows any UI at all: it returns instantly, denied, every
     * time — a silent no-op the driver had no way to recover from before
     * [requestNext] started checking this and falling back to
     * [LocationPermissions.appSettingsIntent] instead.
     */
    var foregroundPermanentlyDenied: Boolean by mutableStateOf(initialForegroundPermanentlyDenied)
        internal set

    /** Launches whichever system dialog [LocationPermissionState.nextStep] calls for.
     *  A caller sitting on [LocationPermissionStep.RATIONALE] should show its own
     *  screen instead and call [continuePastRationale] once the driver taps through. */
    fun requestNext() {
        when (state.nextStep) {
            LocationPermissionStep.FOREGROUND ->
                if (foregroundPermanentlyDenied) openAppSettings() else requestForeground()
            LocationPermissionStep.RATIONALE -> Unit // the calling screen's job
            LocationPermissionStep.BACKGROUND -> requestBackground()
            LocationPermissionStep.NOTIFICATIONS -> requestNotifications()
            LocationPermissionStep.DONE -> Unit
        }
    }

    fun continuePastRationale() = requestBackground()
}

/** Unwraps a possibly-wrapped `Context` down to the hosting `Activity` —
 *  `shouldShowRequestPermissionRationale` needs one, and `LocalContext.current`
 *  inside Compose is not guaranteed to already be one. */
private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("No Activity found in the Context chain — location permission requests require one.")
}

/**
 * Wires the three system permission dialogs (foreground pair, background,
 * notifications) behind [LocationPermissionFlowState], re-reading granted
 * state after each result — Android does not push permission-revoked events,
 * so every launcher callback re-derives [LocationPermissionState] from
 * [LocationPermissions.readState] rather than optimistically flipping a flag.
 */
@Composable
fun rememberLocationPermissionFlow(): LocationPermissionFlowState {
    val context = LocalContext.current
    val activity = context.findActivity()

    // Survives rotation (not process death — this is a best-effort detector,
    // not a source of truth; a wrongly-false value just costs the driver one
    // more no-op tap of a system dialog that returns instantly, never a crash).
    var foregroundPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    // Whether a FOREGROUND request has actually been made THIS session —
    // needed to tell "never asked yet" (rationale is also false here) apart
    // from "asked once, refused with 'don't ask again'" (rationale is false
    // for the same reason). Only the second case is permanently denied.
    var hasRequestedForeground by rememberSaveable { mutableStateOf(false) }

    val flowState = remember {
        LocationPermissionFlowState(
            initialState = LocationPermissions.readState(context),
            initialForegroundPermanentlyDenied = foregroundPermanentlyDenied,
            requestForeground = {},
            requestBackground = {},
            requestNotifications = {},
            openAppSettings = {},
        )
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        flowState.state = LocationPermissions.readState(context)
        if (!flowState.state.fineGranted) {
            val canStillShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            if (hasRequestedForeground && !canStillShowRationale) {
                foregroundPermanentlyDenied = true
                flowState.foregroundPermanentlyDenied = true
            }
        }
        hasRequestedForeground = true
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { flowState.state = LocationPermissions.readState(context) }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { flowState.state = LocationPermissions.readState(context) }

    // 🔴 ONE state object, written by the launchers AND read by the screen.
    // This used to return a SECOND instance built from a snapshot of the first:
    // the launchers kept updating the first while the screen read the second,
    // so after a grant the banner stayed «الموقع مغلق», its button re-asked a
    // permission already granted (a silent no-op), and — worst — tracking never
    // started until the app was reopened (S25, 2026-09-25).
    flowState.requestForeground = { foregroundLauncher.launch(LocationPermissions.FOREGROUND) }
    flowState.requestBackground = { backgroundLauncher.launch(LocationPermissions.BACKGROUND) }
    flowState.requestNotifications = {
        LocationPermissions.NOTIFICATIONS?.let { notificationsLauncher.launch(it) }
    }
    flowState.openAppSettings = { context.startActivity(LocationPermissions.appSettingsIntent(context)) }

    // Android pushes no event when the driver grants from the system settings
    // screen (the permanently-denied path) — re-read on every return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                flowState.state = LocationPermissions.readState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return flowState
}
