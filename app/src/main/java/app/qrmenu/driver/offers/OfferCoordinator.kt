package app.qrmenu.driver.offers

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The shared "there is a pending offer" state both arms write and the UI
 * reads — moved out of `PendingOfferViewModel` (which used to own a private
 * `MutableStateFlow` directly) because the push arm must be able to set it
 * while NO `ViewModel` exists: the app may have been dead when the push
 * arrived, FCM just woke the process for [app.qrmenu.driver.push.DriverMessagingService],
 * and no Activity — and so no `ViewModel` — has been created yet.
 *
 * By the time the driver taps the notification and `MainActivity` starts,
 * [pending] is already non-null, so the offer screen is there immediately
 * instead of an empty app waiting up to 15s for the next poll to catch up.
 */
@Singleton
class OfferCoordinator @Inject constructor() {

    private val _pending = MutableStateFlow<PendingOffer?>(null)
    val pending: StateFlow<PendingOffer?> = _pending.asStateFlow()

    /**
     * @param offerId the id this offer was raised under in [OfferGate] —
     *   carried on [PendingOffer] itself so [dismiss][app.qrmenu.driver.PendingOfferViewModel.dismiss]
     *   can resolve the SAME id the gate blocked on, regardless of which arm
     *   (push or poll) raised it.
     */
    fun raise(orderId: Long, offerId: Long) {
        _pending.value = PendingOffer(orderId, offerId)
    }

    fun clear() {
        _pending.value = null
    }
}

data class PendingOffer(val orderId: Long, val offerId: Long)
