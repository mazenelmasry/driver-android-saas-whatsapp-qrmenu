package app.qrmenu.driver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.alerts.OfferAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Watches for an offer this driver is being held to answer, and hands the
 * offer screen the order id to take over the phone with.
 *
 * 🔴 Polling, not push, is what is wired here on purpose — and it is not a
 * placeholder. The frozen "zero lost offers" architecture names THREE arms:
 * the FCM data message, this 15-second poll from the already-running
 * foreground service, and the ack that re-dispatches when neither arrived.
 * The poll is the arm that works with no Firebase project, no SHA-1
 * fingerprint and no Play Services — which is exactly the state this app is
 * in today, and exactly the state a driver's cheap phone can fall into at
 * any time. Building push first and calling the poll a fallback would have
 * meant nothing on this device could be proven at all.
 *
 * When the FCM receiver lands it feeds the SAME [PendingOffer] state, so the
 * screen and the alarm never learn there are two ways in.
 *
 * 🔴 No notification is POSTED from here, deliberately: this view model only
 * lives while the app is on screen, and a full-screen notification for a
 * screen that is already in front of the driver is noise. Posting the offer
 * notification belongs to the background path — the foreground location
 * service and the FCM receiver — which is the next piece of integration and
 * is NOT done yet. `:core:notifications` is ready for it.
 */
@HiltViewModel
class PendingOfferViewModel @Inject constructor(
    private val orderApi: OrderApi,
    private val alarm: OfferAlarm,
) : ViewModel() {

    private val _pending = MutableStateFlow<PendingOffer?>(null)
    val pending: StateFlow<PendingOffer?> = _pending.asStateFlow()

    /** Offers already raised, so one poll cycle cannot ring twice for one order. */
    private val raised = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            while (true) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops the ringing and clears the screen. Called for BOTH endings —
     * accepted, and resolved any other way — because an alarm that keeps
     * sounding after the driver answered is worse than one that never sounded.
     */
    fun dismiss() {
        alarm.stop()
        _pending.value = null
    }

    private suspend fun poll() {
        val result = runCatching { orderApi.available().data }
        result.exceptionOrNull()?.let { Log.w(TAG, "offer poll failed", it) }
        val orders = result.getOrNull() ?: return
        // Kept deliberately: this loop runs unattended in a pocket, and "did the
        // poll even run" is the first question any lost-offer report raises.
        Log.i(TAG, "poll: ${orders.size} order(s), offers=" + orders.count { it.offer != null })

        // Only an order carrying a live offer takes over the screen. A
        // claimable self-serve order is work on a list, not an interruption
        // that overrides Do Not Disturb.
        val offered = orders.firstOrNull { order ->
            val expiresAt = order.offer?.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            expiresAt != null && expiresAt.isAfter(Instant.now())
        } ?: return

        if (!raised.add(offered.id)) {
            return
        }

        Log.i(TAG, "raising offer for order ${offered.id}")
        _pending.value = PendingOffer(offered.id)
        runCatching { alarm.start() }.onFailure { Log.w(TAG, "alarm failed", it) }
    }

    private companion object {
        const val TAG = "DriverOffers"

        /** The frozen 15-second safety-net cadence. */
        const val POLL_INTERVAL_MS = 15_000L
    }
}

data class PendingOffer(val orderId: Long)
