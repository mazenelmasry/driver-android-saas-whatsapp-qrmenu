package app.qrmenu.driver

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.alerts.OfferAlarm
import app.qrmenu.driver.alerts.OfferNotifier
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.offers.OfferCoordinator
import app.qrmenu.driver.offers.OfferGate
import app.qrmenu.driver.offers.PendingOffer
import app.qrmenu.driver.offers.parseOfferInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watches for an offer this driver is being held to answer, and hands the
 * offer screen the order id to take over the phone with.
 *
 * 🔴 Two arms feed the SAME state, on purpose — this is the frozen "zero
 * lost offers" architecture (CLAUDE.md), not a poll-then-push migration:
 *  - [poll], a 15-second safety-net cadence run from here while the app is
 *    on screen — works with no Firebase project, no SHA-1 fingerprint and
 *    no Play Services, which is exactly the state the `taaj` brand is in.
 *  - [app.qrmenu.driver.offers.DriverPushHandler], which can ring the driver
 *    even while this `ViewModel` does not exist yet (app was dead).
 *
 * Neither arm owns the "is there a pending offer" state directly any more —
 * both write through [OfferCoordinator] and gate on the SAME [OfferGate], so
 * a push and a poll racing for the same offer id can never both win, and the
 * screen never has to know which arm actually rang it.
 *
 * 🔴 This view model still does not POST a notification for its own poll
 * hits: it only lives while the app is on screen, and a full-screen
 * notification for a screen already in front of the driver is noise. Only
 * the push arm posts one (because it can fire with no screen at all) — see
 * [dismiss], which cancels it regardless of which arm raised the offer,
 * since cancelling a notification that was never posted is a harmless no-op.
 */
@HiltViewModel
class PendingOfferViewModel @Inject constructor(
    private val orderApi: OrderApi,
    private val alarm: OfferAlarm,
    private val notifier: OfferNotifier,
    private val offerGate: OfferGate,
    private val coordinator: OfferCoordinator,
) : ViewModel() {

    val pending: StateFlow<PendingOffer?> = coordinator.pending

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
        notifier.cancel(OfferNotifier.OFFER_NOTIFICATION_ID)
        coordinator.pending.value?.let { offerGate.resolve(it.offerId) }
        coordinator.clear()
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
            val expiresAt = order.offer?.expiresAt?.let(::parseOfferInstant)
            expiresAt != null && expiresAt.isAfter(Instant.now())
        } ?: return

        val offerId = offered.offer?.id ?: return

        if (!offerGate.shouldRaise(offerId)) {
            return
        }

        Log.i(TAG, "raising offer $offerId for order ${offered.id}")
        coordinator.raise(offered.id, offerId)
        runCatching { alarm.start() }.onFailure { Log.w(TAG, "alarm failed", it) }
    }

    private companion object {
        const val TAG = "DriverOffers"

        /** The frozen 15-second safety-net cadence. */
        const val POLL_INTERVAL_MS = 15_000L
    }
}
