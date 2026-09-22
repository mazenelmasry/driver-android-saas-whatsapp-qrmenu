package app.qrmenu.driver.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Turns [UpdateGateViewModel]'s raw state into the one decision a caller
 * needs, for the CURRENT process and this build.
 *
 * [hasActiveTrip] is supplied by the caller rather than read here — this
 * module has no notion of a trip (see [UpdateGateViewModel]'s own doc). It is
 * a tri-state (`null` = not yet known) because the app cannot always answer
 * it from memory alone — see [UpdateDecision.requirement]'s own doc for why
 * that distinction is load-bearing, not decorative. The app wires it from
 * the same `activeTripId`/held-trip-lookup state it already threads through
 * `SignedInScreen`'s offer/trip overlays.
 *
 * Relies on `hiltViewModel()`'s default-param caching (the same pattern this
 * app already uses for `PendingOfferViewModel` in `SignedInScreen`): two
 * calls to [rememberUpdateRequirement] and [rememberUpdateAvailability] made
 * from the SAME composition point, with no explicit `viewModel` argument,
 * resolve to the one [UpdateGateViewModel] instance for as long as the
 * driver stays signed in — so the poll it runs (§ `UpdateGateViewModel.init`)
 * is never started twice.
 */
@Composable
fun rememberUpdateRequirement(
    hasActiveTrip: Boolean?,
    viewModel: UpdateGateViewModel = hiltViewModel(),
): UpdateRequirement {
    val gateState by viewModel.state.collectAsStateWithLifecycle()
    return remember(gateState, hasActiveTrip) {
        UpdateDecision.requirement(
            currentVersionCode = BuildConfig.APP_VERSION_CODE,
            remote = gateState.remote,
            interceptorMinVersionCode = gateState.interceptorMinVersionCode,
            hasActiveTrip = hasActiveTrip,
        )
    }
}

@Composable
fun rememberUpdateAvailability(
    viewModel: UpdateGateViewModel = hiltViewModel(),
): UpdateAvailability {
    val gateState by viewModel.state.collectAsStateWithLifecycle()
    return remember(gateState) {
        UpdateDecision.availability(
            currentVersionCode = BuildConfig.APP_VERSION_CODE,
            remote = gateState.remote,
            dismissedForVersionCode = gateState.dismissedVersionCode,
        )
    }
}

/** For the banner's dismiss button — same shared instance, see this file's doc. */
@Composable
fun rememberUpdateGateViewModel(viewModel: UpdateGateViewModel = hiltViewModel()): UpdateGateViewModel = viewModel
