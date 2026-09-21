package app.qrmenu.driver

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.availability.AvailabilityRoute
import app.qrmenu.driver.home.HomeRoute
import app.qrmenu.driver.location.DriverLocationService
import app.qrmenu.driver.location.permission.LocationPermissionStep
import app.qrmenu.driver.location.permission.rememberLocationPermissionFlow
import app.qrmenu.driver.location.upload.LocationConnectivityState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What a signed-in driver sees.
 *
 * Two destinations, because there are now genuinely two: the thing a driver
 * DOES (make themselves available and take work) and the thing they REFER to
 * (who they are, and which restaurants they belong to). Earlier there was one
 * real screen and a navigation graph would have been scaffolding; with two it
 * is the plain answer, and the tabs this project still owes — orders, ledger,
 * history — land beside these rather than replacing them.
 *
 * Deliberately still a `when` over a saved tab rather than a `NavHost`: nothing
 * here is deep-linked or takes arguments yet, and the bar IS the navigation. It
 * becomes a graph the day a notification has to open one of these directly,
 * which is the week the offer screen lands.
 */
@Composable
fun SignedInScreen(onSignedOut: () -> Unit, onEnterInviteCode: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SignedInTab.Availability) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                SignedInTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = insets.calculateBottomPadding()),
        ) {
            when (tab) {
                SignedInTab.Availability -> AvailabilityTab()
                SignedInTab.Restaurants -> HomeRoute(
                    onSignedOut = onSignedOut,
                    onEnterInviteCode = onEnterInviteCode,
                )
            }
        }
    }
}

enum class SignedInTab(@StringRes val label: Int, val icon: ImageVector) {
    Availability(R.string.tab_availability, Icons.Filled.TwoWheeler),
    Restaurants(R.string.tab_restaurants, Icons.Filled.Storefront),
}

/**
 * Joins the availability screen to the location service.
 *
 * 🔴 The service starts only once the SERVER has confirmed availability —
 * `AvailabilityRoute` fires `onGoOnline` from the confirmed state, never from
 * the tap. Starting it on the tap would drain a driver's battery while they
 * sit offline because the call failed.
 *
 * The permission ladder is asked for at the moment it means something: when
 * the driver actually goes available. Asking at launch, before they have any
 * reason to say yes, is how an app collects a permanent refusal it can never
 * recover from.
 */
@Composable
private fun AvailabilityTab(viewModel: LocationWiringViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val permissions = rememberLocationPermissionFlow()

    // "The server says this driver is available" and "this phone is allowed to
    // report where it is" are two separate facts, and the grant can arrive
    // seconds after the tap. Holding the intent and reacting to BOTH means a
    // driver who allows location on the prompt starts being tracked without
    // having to find the switch again.
    var wantsTracking by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(wantsTracking, permissions.state.fineGranted) {
        if (wantsTracking && permissions.state.fineGranted) {
            DriverLocationService.start(context)
        } else {
            DriverLocationService.stop(context)
        }
    }

    AvailabilityRoute(
        connectionFailing = viewModel.connectionFailing,
        warningSlot = {
            val state = permissions.state
            if (state.nextStep != LocationPermissionStep.DONE || state.foregroundOnlyLimited) {
                LocationPermissionWarning(
                    // "Allow all the time" refused is NOT a blocker — the whole
                    // design works on foreground location (frozen decision 40).
                    // It is a standing warning, and it must not read like the
                    // app is broken.
                    isBlocking = !state.fineGranted,
                    onAction = permissions::requestNext,
                )
            }
        },
        onGoOnline = {
            wantsTracking = true
            // Asking is asynchronous. Starting the service in the same breath
            // is what crashed this on the S25: a `location` foreground service
            // started before the grant throws SecurityException on Android 14+.
            if (!permissions.state.fineGranted) permissions.requestNext()
        },
        onGoOffline = { wantsTracking = false },
    )
}

@HiltViewModel
class LocationWiringViewModel @Inject constructor(
    connectivity: LocationConnectivityState,
) : ViewModel() {

    /**
     * Inverted on purpose: the module reports "connected", the screen asks
     * "is the connection failing". Flipping it here keeps the screen's question
     * the one a driver would actually ask.
     */
    val connectionFailing: StateFlow<Boolean> =
        connectivity.isConnected
            .map { !it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
