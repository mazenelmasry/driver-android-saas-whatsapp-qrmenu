package app.qrmenu.driver

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.qrmenu.driver.account.AccountRoute
import app.qrmenu.driver.notifications.NotificationCenterRoute
import app.qrmenu.driver.location.DriverLocationService
import app.qrmenu.driver.location.permission.LocationPermissionStep
import app.qrmenu.driver.location.permission.rememberLocationPermissionFlow
import app.qrmenu.driver.location.upload.LocationConnectivityState
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.orders.OrdersRoute
import app.qrmenu.driver.trip.OfferRoute
import app.qrmenu.driver.wallet.WalletRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What a signed-in driver sees: four destinations, in the order a shift uses
 * them.
 *
 *   متاح      — the switch the whole job hangs on
 *   الطلبات   — the work itself
 *   المحفظة   — what the work paid, and what cash is owed back
 *   حسابى     — who I am, which restaurants, language, size, sign out
 *
 * «مطاعمى» is no longer a destination of its own: a list of the driver's
 * restaurants is something they check when something is wrong, not four times
 * a shift, and it now lives inside حسابى beside the rest of their identity.
 * That kept the bar at four — five tabs on a phone is a row of targets too
 * narrow for the thumb this app is designed around.
 *
 * Deliberately still a `when` over a saved tab rather than a `NavHost`: nothing
 * here is deep-linked or takes arguments yet, and the bar IS the navigation.
 * The offer screen, which landed this week, did NOT change that: it is not a
 * destination the driver navigates to — it seizes the screen and hands it
 * back. It becomes a graph the day a notification tap has to open one of
 * these directly, which is the FCM receiver's week.
 */
@Composable
fun SignedInScreen(
    onSignedOut: () -> Unit,
    onEnterInviteCode: () -> Unit,
    pendingOfferViewModel: PendingOfferViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(SignedInTab.Availability) }

    // The "why is المتاحة empty" context (decision 47) is owned by
    // `:feature:orders`'s `OrdersRoute`, but `AvailabilityRoute`'s existing
    // `noOrdersContext` slot needs the SAME value even while the Orders tab is
    // not the one on screen. A plain `remember`ed flow at this scope — rather
    // than a new shared ViewModel — is enough: it survives the `when` switching
    // which composable is visible, because `SignedInScreen` itself never leaves
    // composition while the driver is signed in.
    val noOrdersContext = remember { MutableStateFlow<AvailabilityContextDto?>(null) }

    // The notification centre is a full screen layered OVER the tabs rather
    // than a fifth tab: it is opened from the bell on any screen, read, and
    // dismissed back to wherever the driver was — a tab would instead take
    // over the bar and lose their place.
    var showingNotifications by rememberSaveable { mutableStateOf(false) }

    if (showingNotifications) {
        NotificationCenterRoute(onBack = { showingNotifications = false })
        return
    }

    // 🔴 The offer outranks everything, including the notification centre
    // above it: a live offer is 45 seconds of the driver's income and the
    // ONLY screen in this app that is allowed to interrupt. It is layered
    // over the tabs rather than being a destination, so answering it returns
    // the driver exactly where they were — a driver who loses their place
    // every time an offer arrives stops trusting the app.
    val pendingOffer by pendingOfferViewModel.pending.collectAsState()

    pendingOffer?.let { offer ->
        OfferRoute(
            orderId = offer.orderId,
            // Both endings stop the ringing. Accepting is not "success and
            // the alarm sorts itself out" — the alarm is deliberately
            // insistent, so every exit has to switch it off explicitly.
            onAccepted = {
                pendingOfferViewModel.dismiss()
                tab = SignedInTab.Orders
            },
            onResolved = { pendingOfferViewModel.dismiss() },
        )
        return
    }

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
                SignedInTab.Availability -> AvailabilityTab(
                    noOrdersContext = noOrdersContext,
                    onOpenNotifications = { showingNotifications = true },
                )
                SignedInTab.Orders -> OrdersRoute(
                    noOrdersContextOut = { noOrdersContext.value = it },
                    onOpenNotifications = { showingNotifications = true },
                )
                SignedInTab.Wallet -> WalletRoute(
                    onOpenNotifications = { showingNotifications = true },
                )
                SignedInTab.Account -> AccountRoute(
                    onSignedOut = onSignedOut,
                    onEnterInviteCode = onEnterInviteCode,
                )
            }
        }
    }
}

enum class SignedInTab(@StringRes val label: Int, val icon: ImageVector) {
    Availability(R.string.tab_availability, Icons.Filled.TwoWheeler),
    Orders(R.string.tab_orders, Icons.Filled.ListAlt),
    Wallet(R.string.tab_wallet, Icons.Filled.AccountBalanceWallet),
    Account(R.string.tab_account, Icons.Filled.AccountCircle),
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
private fun AvailabilityTab(
    noOrdersContext: StateFlow<AvailabilityContextDto?>,
    onOpenNotifications: () -> Unit,
    viewModel: LocationWiringViewModel = hiltViewModel(),
) {
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
        noOrdersContext = noOrdersContext,
        onOpenNotifications = onOpenNotifications,
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
