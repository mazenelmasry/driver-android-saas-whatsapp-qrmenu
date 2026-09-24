package app.qrmenu.driver.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Is the driver currently working an active trip, and which order is it?" —
 * the input [LocationCadencePolicy] needs (as [hasActiveTrip]) and the input
 * [app.qrmenu.driver.location.upload.BreadcrumbCollector] needs (as
 * [activeOrderId], to know which order a location fix belongs to and, per the
 * task brief, to gate collection to picked-up → delivered).
 *
 * `:feature:trip`'s `TripViewModel` is expected to call [setActiveOrder]:
 * non-`null` once `picked-up` succeeds (or a resumed trip's own GET already
 * shows `picked_up_at` set), `null` the instant `delivered` succeeds or the
 * restaurant ends the trip out from under the driver (`TripPhase.Resolved`).
 *
 * Defaults to `null`/`false`, which only ever widens [LocationCadencePolicy]'s
 * cadence from STOPPED (30s) to AVAILABLE_IDLE (60s) — never the reverse —
 * and only ever WITHHOLDS breadcrumbs, never invents extra ones — so an
 * un-wired caller fails safe towards less battery use and less data
 * collected, never towards silently under-sampling or over-collecting.
 */
@Singleton
class DriverTripActivityState @Inject constructor() {

    private val _activeOrderId = MutableStateFlow<Long?>(null)

    /** `null` when this driver holds no trip past pickup right now. */
    val activeOrderId: StateFlow<Long?> = _activeOrderId.asStateFlow()

    private val _hasActiveTrip = MutableStateFlow(false)
    val hasActiveTrip: StateFlow<Boolean> = _hasActiveTrip.asStateFlow()

    fun setActiveOrder(orderId: Long?) {
        _activeOrderId.value = orderId
        _hasActiveTrip.value = orderId != null
    }
}
