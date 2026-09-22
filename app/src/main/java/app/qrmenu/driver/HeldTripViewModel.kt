package app.qrmenu.driver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.DriverOrderDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Answers, from the server rather than from whatever `SignedInScreen`
 * happens to remember, the one question screen 17's update gate needs before
 * it may EVER wall a driver off: "is a trip in their hands right now?"
 *
 * This exists in `:app`, not `:feature:updater`, on purpose — `:feature:updater`
 * has no business knowing what an order or a trip is (see
 * [app.qrmenu.driver.updater.UpdateGateViewModel]'s own doc, which already
 * draws this exact line). `:feature:trip` owns the actual trip SCREEN but
 * would be the wrong home too: this lookup has to run and answer BEFORE the
 * update gate decides whether the driver is even allowed to reach a screen
 * that could open a trip, so it cannot live behind that screen's own
 * `hiltViewModel()`. `:app`, which already wires `activeTripId` across both
 * of those and is the one place allowed to know about all of them, is the
 * only remaining owner that does not create a new module coupling to answer
 * a question one module needs about a domain the other one owns.
 *
 * 🔴 The bug this fixes, on a real device (CLAUDE.md verification protocol):
 * a cold start (process killed, or the floor raised while backgrounded) left
 * `activeTripId` at its initial `null` — indistinguishable, in memory alone,
 * from "confirmed no trip" — so the gate blocked a driver who was actually
 * `out_for_delivery`. The backend now exempts `driver/orders/mine`,
 * `driver/orders/{id}` and the four trip actions from the version floor
 * specifically so THIS lookup can still run on a blocked build; this class
 * is what makes the app actually make that call before deciding anything.
 */
@HiltViewModel
class HeldTripViewModel @Inject constructor(
    private val orderApi: OrderApi,
) : ViewModel() {

    private val _probe = MutableStateFlow<HeldTripProbe>(HeldTripProbe.Unknown)

    /** [HeldTripProbe.Unknown] until the first successful `driver/orders/mine` answers. */
    val probe: StateFlow<HeldTripProbe> = _probe.asStateFlow()

    init {
        viewModelScope.launch {
            // Retries on failure (offline, timeout, 5xx) rather than settling
            // for "unknown forever" — constraint 1 (unanswerable means
            // allowed, never means blocked) covers the driver WHILE the
            // answer is missing, but a connectivity blip should not be the
            // reason a genuinely trip-free driver never gets walled off once
            // the floor is actually raised. Stops retrying the instant an
            // answer — either one — is known; a driver who later starts a
            // NEW trip is covered by `activeTripId` directly, not by this.
            while (isActive && _probe.value is HeldTripProbe.Unknown) {
                runCatching { orderApi.mine().data.firstOrNull() }
                    .onSuccess { held -> _probe.value = HeldTripProbe.Resolved(held) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "held-trip lookup failed, will retry", error)
                    }
                if (_probe.value is HeldTripProbe.Unknown) delay(RETRY_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TAG = "HeldTripProbe"
        const val RETRY_INTERVAL_MS = 15_000L
    }
}

/** Tri-state on purpose — see [HeldTripViewModel]'s own doc. */
sealed interface HeldTripProbe {
    /** Not yet asked, or the last attempt failed. NOT "confirmed none". */
    data object Unknown : HeldTripProbe

    /** The server answered. `order` is `null` when confirmed — this driver holds nothing. */
    data class Resolved(val order: DriverOrderDto?) : HeldTripProbe
}
