package app.qrmenu.driver.availability

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.api.AvailabilityApi
import app.qrmenu.driver.network.dto.AvailabilityRequest
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The global on/off switch (decision 20 — one switch, not one per restaurant).
 *
 * 🔴 [isOnline] only ever changes to a value the SERVER confirmed. A tap sets
 * [isPending] and nothing else — the switch keeps showing whatever it showed
 * before the tap. This is deliberate, not an oversight: optimistic toggling is
 * exactly the trap this screen exists to avoid. A driver who flips to
 * "available", sees it turn green, and puts the phone in their pocket has been
 * told they are now earning — if the PATCH then fails (dead signal, a 409
 * because a trip landed on them in the same second, a 5xx) they sit there
 * getting nothing while believing otherwise, which is worse than a driver who
 * sees the switch simply refuse to move and knows to try again. [isPending]
 * disables the control and drives a THIRD visual state on screen (distinct
 * from both on and off — see `AvailabilityScreen`), so nothing is ever shown
 * that the server has not actually said.
 *
 * Initial state comes from `GET /driver/me` (the same call `:feature:home`
 * makes) rather than any local cache — a driver may have been put offline by
 * the server (branch closed, 3-minute silent heartbeat) while the phone was
 * asleep, and the switch must reflect that truth on open, not a stale local one.
 */
data class AvailabilityUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = false,
    /** ISO-8601 from the server; the screen turns this into a live-ticking duration. */
    val onlineSince: String? = null,
    /** True while a PATCH is in flight. See the class doc — this is what stands in for optimism. */
    val isPending: Boolean = false,
    val error: DriverApiError? = null,
) {
    /**
     * `has_active_trip` gets its own truth so the screen can phrase it as "you
     * have an order in hand" rather than a generic failure — the driver did
     * nothing wrong, the server is simply not letting them go dark mid-delivery.
     */
    val hasActiveTripBlock: Boolean
        get() = (error as? DriverApiError.Api)?.code == DriverErrorCode.HasActiveTrip
}

@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val availabilityApi: AvailabilityApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AvailabilityUiState())
    val state: StateFlow<AvailabilityUiState> = _state.asStateFlow()

    init {
        loadInitialState()
    }

    /**
     * Reads the CONFIRMED state from the server, never assumes it. Failure here
     * still lands on a usable screen — the switch stays OFF (the safe default: a
     * driver who cannot be told whether they are online must not be shown a
     * green switch) with the inline error, retryable via [loadInitialState].
     */
    fun loadInitialState() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { authApi.me() }
                .onSuccess { me ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isOnline = me.driver.isOnline,
                            onlineSince = me.driver.onlineSince,
                            error = null,
                        )
                    }
                }
                .onFailure { thrown ->
                    _state.update { it.copy(isLoading = false, error = thrown.toDriverApiError()) }
                }
        }
    }

    fun retry() = loadInitialState()

    /**
     * The tap handler. See the class doc for why this does not flip [isOnline]
     * itself — only [isPending] changes synchronously, so the switch visibly
     * "thinks" rather than lies.
     *
     * Re-entrant taps are ignored while a request is already in flight: the
     * switch is disabled on screen for exactly this reason, but a defensive
     * guard here means a second, unrelated caller (a future deep link, a test)
     * cannot race the first PATCH either.
     */
    fun onToggle(online: Boolean) {
        if (_state.value.isPending) return
        _state.update { it.copy(isPending = true, error = null) }
        viewModelScope.launch {
            runCatching { availabilityApi.setAvailability(AvailabilityRequest(online = online)) }
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isPending = false,
                            isOnline = response.isOnline,
                            onlineSince = response.onlineSince,
                            error = null,
                        )
                    }
                }
                .onFailure { thrown ->
                    // isOnline is untouched — the switch settles back to whatever
                    // was last confirmed, which is exactly what never changed.
                    _state.update { it.copy(isPending = false, error = thrown.toDriverApiError()) }
                }
        }
    }

    fun onDismissError() = _state.update { it.copy(error = null) }
}
