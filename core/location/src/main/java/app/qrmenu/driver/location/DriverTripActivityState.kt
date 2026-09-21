package app.qrmenu.driver.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Is the driver currently working an active trip?" — the one input
 * [LocationCadencePolicy] needs that this module does not itself produce.
 *
 * `:feature:trip` does not exist yet (see `settings.gradle.kts`). Rather than
 * have `:core:location` depend on a feature module (wrong direction — core
 * modules never depend on feature modules) or invent trip state itself, this
 * is a tiny mutable flag `:feature:trip` is expected to update via
 * [setHasActiveTrip] once it ships (accept/picked-up ⇒ true,
 * delivered/cancelled/no assignment ⇒ false). Defaults to `false`, which only
 * ever widens the cadence from STOPPED (30s) to AVAILABLE_IDLE (60s) — never
 * the reverse — so an un-wired flag fails safe towards LESS battery use, not
 * towards silently under-sampling a driver mid-trip.
 */
@Singleton
class DriverTripActivityState @Inject constructor() {

    private val _hasActiveTrip = MutableStateFlow(false)
    val hasActiveTrip: StateFlow<Boolean> = _hasActiveTrip.asStateFlow()

    fun setHasActiveTrip(active: Boolean) {
        _hasActiveTrip.value = active
    }
}
