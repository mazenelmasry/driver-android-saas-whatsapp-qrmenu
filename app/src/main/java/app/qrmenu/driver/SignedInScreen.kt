package app.qrmenu.driver

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.availability.AvailabilityRoute
import app.qrmenu.driver.account.AccountRoute
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.health.NotificationHealthBanner
import app.qrmenu.driver.health.RequestNotificationPermissionOnce
import app.qrmenu.driver.notifications.NotificationCenterRoute
import app.qrmenu.driver.location.DriverLocationService
import app.qrmenu.driver.location.permission.LocationPermissionStep
import app.qrmenu.driver.location.permission.rememberLocationPermissionFlow
import app.qrmenu.driver.location.upload.LocationConnectivityState
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.orders.OrdersRoute
import app.qrmenu.driver.trip.OfferRoute
import app.qrmenu.driver.trip.TripRoute
import app.qrmenu.driver.updater.BlockedUpdateScreen
import app.qrmenu.driver.updater.UpdateBannerHost
import app.qrmenu.driver.updater.UpdateDeferredBanner
import app.qrmenu.driver.updater.UpdateRequirement
import app.qrmenu.driver.updater.rememberUpdateAvailability
import app.qrmenu.driver.updater.rememberUpdateGateViewModel
import app.qrmenu.driver.updater.rememberUpdateRequirement
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
    heldTripViewModel: HeldTripViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(SignedInTab.Availability) }

    // 🔴 What `accept` on the offer screen hands back: the ASSIGNED shape
    // (customer + full address already included — see `DriverOrderDto`'s own
    // doc) for the trip the driver now holds. Week 5's trip screen reads it
    // directly, so accepting an offer does not cost a second network round
    // trip for data the response already carried.
    // The trip is addressed by its ID, which `rememberSaveable` carries through
    // process death — the seed DTO is only an optimisation that spares the
    // screen one request, so it may be lost without the driver losing the
    // trip. Holding ONLY the DTO (it is not Parcelable) meant a driver whose
    // app was killed mid-delivery had no route back to "picked up" at all.
    //
    // Hoisted above every other `remember`/early-return in this function
    // (moved here from beside the offer/trip overlays below) so the screen
    // 17 update gate — which needs to know "is a trip in this driver's
    // hands?" before it decides whether to block — can read it without
    // itself sitting inside the trip branch it is deciding about.
    var activeTripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeTripSeed by remember { mutableStateOf<DriverOrderDto?>(null) }

    // 🔴 `activeTripId` alone answers "is a route open to a trip right now?",
    // NOT "does this driver hold one?" — on a cold start (process killed,
    // phone rebooted mid-shift, floor raised while backgrounded) it is
    // `null` for the SAME reason a genuinely trip-free driver's is: nothing
    // has asked yet. Reading that `null` as "no trip" is exactly the device
    // bug this fixes — a driver `out_for_delivery` was walled off before the
    // screen that would have proven otherwise ever got to load. `HeldTripViewModel`
    // asks `driver/orders/mine` — exempted from the version floor server-side
    // for precisely this — and resolves the ambiguity from the server instead
    // of assuming an answer from unloaded UI state.
    val heldTripProbe by heldTripViewModel.probe.collectAsStateWithLifecycle()

    // The instant the probe confirms a held trip, adopt it as the SAME
    // `activeTripId`/`activeTripSeed` the offer-accept path already uses —
    // a cold start with a trip in progress should resume the trip screen,
    // not merely avoid blocking while leaving the driver stranded on the
    // tab bar. `activeTripId == null` guards against overwriting a trip the
    // driver already navigated into some other way (offer just accepted,
    // Orders tab already opened it) with a slower-to-resolve probe result.
    LaunchedEffect(heldTripProbe) {
        val held = (heldTripProbe as? HeldTripProbe.Resolved)?.order
        if (held != null && activeTripId == null) {
            activeTripId = held.id
            activeTripSeed = held
        }
    }

    // Tri-state, not a fallback to `false`: `true` once a trip is confirmed
    // held (either route above), `false` only once the probe has CONFIRMED
    // there is none, and `null` — unknown — for every moment before that
    // answer exists (including a failed/offline lookup, which never resolves
    // to `Resolved` — see `HeldTripViewModel`). `UpdateDecision.requirement`
    // treats `null` the same as `true`: unanswerable means allowed, never
    // means blocked.
    val hasActiveTrip: Boolean? = when {
        activeTripId != null -> true
        heldTripProbe is HeldTripProbe.Resolved -> false
        else -> null
    }

    // Screen 17 (CLAUDE.md § خريطة الشاشات / التوزيع والتحديث الذاتى) — the
    // one thing allowed to outrank even the ringing offer below: a build the
    // backend has stopped serving gets no further screens, full stop, UNLESS
    // this driver is holding a trip right now, or it is not yet known
    // whether they are — in which case `:feature:updater` hands back
    // `DeferredForActiveTrip`/`DeferredUnknown` instead of `Blocked` and this
    // early-return does not fire. The trip branch further down renders
    // `UpdateDeferredBanner` over a CONFIRMED held trip; the unknown case
    // renders nothing extra and simply waits — there is no trip screen open
    // yet to caption, and no confirmed absence to act on.
    val updateRequirement = rememberUpdateRequirement(hasActiveTrip = hasActiveTrip)
    if (updateRequirement is UpdateRequirement.Blocked) {
        BlockedUpdateScreen(info = updateRequirement.info)
        return
    }

    // Fires at most once per app run (CLAUDE.md §🔔) — placed unconditionally
    // here, ABOVE every early `return` below, because `SignedInScreen` itself
    // never leaves composition while the driver is signed in (see the class
    // doc), so this is reached exactly once per sign-in regardless of which
    // overlay (offer, trip, notification centre) is showing when it fires.
    RequestNotificationPermissionOnce()

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
        // Back belongs to the overlay, not to the task: without this the
        // system pops the whole Activity and the driver is thrown out of the
        // app instead of back to the tab they came from.
        BackHandler { showingNotifications = false }
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
            onAccepted = { assigned ->
                pendingOfferViewModel.dismiss()
                activeTripId = assigned.id
                activeTripSeed = assigned
            },
            onResolved = { pendingOfferViewModel.dismiss() },
        )
        return
    }

    // The trip screen, same as the offer above it: it seizes the screen for
    // the one trip a driver holds at a time (decision 21) rather than being a
    // tab, and hands the driver back to حيث كانوا — the الطلبات tab, since the
    // trip they were just working is now behind them — once it resolves.
    activeTripId?.let { tripId ->
        // Same reasoning as the notification overlay — and it matters more
        // here: pressing back mid-delivery used to CLOSE the app on a driver
        // holding someone's food and 82 riyals of someone's cash. An offer
        // deliberately has no such handler: it is the one screen that must
        // be answered, not dismissed.
        BackHandler {
            activeTripId = null
            activeTripSeed = null
            tab = SignedInTab.Orders
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Consumed here, once, for the whole stack — `TripRoute`
                // below applies this SAME inset again inside its own
                // `DriverScreenScaffold`; without `consumeWindowInsets` the
                // driver would see two status-bar-height gaps stacked when
                // the banner is showing (one above it, one — spurious —
                // between it and the trip screen).
                .windowInsetsPadding(WindowInsets.statusBars)
                .consumeWindowInsets(WindowInsets.statusBars),
        ) {
            // The one place `DeferredForActiveTrip` is ever shown — see the
            // `updateRequirement` doc above. A trip in progress is the ONLY
            // reason this build is still allowed on screen at all right now.
            if (updateRequirement is UpdateRequirement.DeferredForActiveTrip) {
                UpdateDeferredBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                TripRoute(
                    orderId = tripId,
                    // Only hand over a seed that is actually THIS order —
                    // after process death the id survives and the seed does
                    // not, and a stale seed would render someone else's
                    // address.
                    initialOrder = activeTripSeed?.takeIf { it.id == tripId },
                    onOpenNotifications = { showingNotifications = true },
                    onExit = {
                        activeTripId = null
                        activeTripSeed = null
                        tab = SignedInTab.Orders
                    },
                )
            }
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = insets.calculateBottomPadding()),
        ) {
            // 🔴 Shown above whichever tab is on screen, on ALL four — a
            // driver spends their entire shift on this bar, most of it on
            // متاح waiting for the exact alert this is warning them might
            // never arrive. Anchoring it to one tab would hide it the moment
            // they switch away from it.
            NotificationHealthBanner()

            // The dismissible "an update exists" notice — never shown here
            // when `updateRequirement` is anything but `NotRequired` (this
            // Scaffold itself is unreachable otherwise, see the two early
            // `return`s above), so it never competes with the wall or the
            // trip-deferred notice for the driver's attention.
            val updateAvailability = rememberUpdateAvailability()
            val updateGateViewModel = rememberUpdateGateViewModel()
            UpdateBannerHost(
                availability = updateAvailability,
                onDismiss = { latestVersionCode -> updateGateViewModel.dismissBanner(latestVersionCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
            )

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    SignedInTab.Availability -> AvailabilityTab(
                        noOrdersContext = noOrdersContext,
                        onOpenNotifications = { showingNotifications = true },
                    )
                    SignedInTab.Orders -> OrdersRoute(
                        // The way back into a trip the driver is already holding.
                        onOpenTrip = { activeTripId = it },
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
