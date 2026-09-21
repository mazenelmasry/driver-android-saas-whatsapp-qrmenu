package app.qrmenu.driver.location.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

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
    private val requestForeground: () -> Unit,
    private val requestBackground: () -> Unit,
    private val requestNotifications: () -> Unit,
) {
    var state: LocationPermissionState by mutableStateOf(initialState)
        internal set

    /** Launches whichever system dialog [LocationPermissionState.nextStep] calls for.
     *  A caller sitting on [LocationPermissionStep.RATIONALE] should show its own
     *  screen instead and call [continuePastRationale] once the driver taps through. */
    fun requestNext() {
        when (state.nextStep) {
            LocationPermissionStep.FOREGROUND -> requestForeground()
            LocationPermissionStep.RATIONALE -> Unit // the calling screen's job
            LocationPermissionStep.BACKGROUND -> requestBackground()
            LocationPermissionStep.NOTIFICATIONS -> requestNotifications()
            LocationPermissionStep.DONE -> Unit
        }
    }

    fun continuePastRationale() = requestBackground()
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

    val flowState = remember {
        LocationPermissionFlowState(
            initialState = LocationPermissions.readState(context),
            requestForeground = {},
            requestBackground = {},
            requestNotifications = {},
        )
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { flowState.state = LocationPermissions.readState(context) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { flowState.state = LocationPermissions.readState(context) }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { flowState.state = LocationPermissions.readState(context) }

    return remember(foregroundLauncher, backgroundLauncher, notificationsLauncher) {
        LocationPermissionFlowState(
            initialState = flowState.state,
            requestForeground = { foregroundLauncher.launch(LocationPermissions.FOREGROUND) },
            requestBackground = { backgroundLauncher.launch(LocationPermissions.BACKGROUND) },
            requestNotifications = {
                LocationPermissions.NOTIFICATIONS?.let { notificationsLauncher.launch(it) }
            },
        )
    }
}
