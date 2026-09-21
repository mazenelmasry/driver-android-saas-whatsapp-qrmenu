package app.qrmenu.driver.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Every way this screen can end, WITHOUT the driver holding the order (see [OfferUiState.acceptedOrder] for that one). */
enum class OfferOutcome {
    /** 🔴 Another driver's `accept` won the row-lock first. Normal — not a fault of this driver's. */
    AlreadyClaimed,

    /** The countdown reached the server's `expires_at`, locally or via a 409 from the server itself. */
    Expired,

    OrderCancelled,
    TooManyActiveOrders,
    CashLimitExceeded,

    /** This driver declined it. */
    Declined,
}

/** What [OfferScreen] renders — the four mandatory states (driver-ui-standards), plus the two terminal outcomes an offer can resolve into. */
sealed interface OfferPhase {
    /** Fetching the offer's own data — never the four-fact list, just this one order. */
    data object Loading : OfferPhase

    /**
     * A live, decidable offer.
     *
     * [error] is the accept/decline attempt's OWN inline failure (offline,
     * rate-limited, server_error, an unmapped code) — kept apart from
     * [OfferPhase.LoadFailed] because it must render IN PLACE, without
     * throwing away [order] or resetting the countdown a driver is still
     * watching.
     */
    data class Content(
        val order: OfferSummary,
        val isActing: Boolean = false,
        val error: DriverApiError? = null,
    ) : OfferPhase

    /** The offer's own GET failed outright — nothing to decide on yet. */
    data class LoadFailed(val error: DriverApiError) : OfferPhase

    /** Terminal: the offer is gone. See [OfferOutcome] for why. */
    data class Resolved(val outcome: OfferOutcome) : OfferPhase
}

data class OfferUiState(
    val phase: OfferPhase = OfferPhase.Loading,
    /**
     * Set the instant `accept` succeeds — the ASSIGNED shape, address and
     * phone included, so a caller navigating to the trip screen (week 5) does
     * not need a second round trip for what this response already carried.
     *
     * A one-shot navigation event kept as state rather than a stored lambda:
     * [OfferScreen] consumes it via `LaunchedEffect`, matching how
     * [OfferPhase.Resolved] is consumed the same way.
     */
    val acceptedOrder: DriverOrderDto? = null,
)

/**
 * The 45-second full-screen offer (screen 9, CLAUDE.md § خريطة الشاشات /
 * محرك النداء).
 *
 * Owns three things a Composable must not: WHEN the offer counts as expired
 * (anchored to the server's instant, never the screen's own open time — see
 * [OfferCountdown]), that the ack fires exactly ONCE regardless of how many
 * times [start] is called (recomposition, a re-entered screen), and that
 * accept/decline map a 409's CODE to the outcome it actually is rather than a
 * generic red banner. The live "N seconds left" tick is left to the
 * Composable (`produceState`, like `:feature:orders`' `ReadinessPill`) — it is
 * a rendering concern with nothing for a ViewModel test to prove, whereas
 * "does the screen resolve itself when the clock runs out" does, and is
 * covered here.
 */
@HiltViewModel
class OfferViewModel @Inject constructor(
    private val repository: OfferRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OfferUiState())
    val state: StateFlow<OfferUiState> = _state.asStateFlow()

    private var hasStarted = false
    private var hasAcked = false

    /**
     * Loads the offer and sends its ack — both exactly once per screen
     * instance, however many times recomposition calls this.
     */
    fun start(orderId: Long) {
        if (hasStarted) return
        hasStarted = true
        ackOnce(orderId)
        loadOffer(orderId)
    }

    /**
     * 🔴 Fired the instant the offer is on screen, before the driver has
     * decided anything — this is the spine of "zero lost offers"
     * (CLAUDE.md § الإشعارات): no ack within 15s re-sends the push, no ack
     * within 45s re-dispatches to the next candidate.
     *
     * Deliberately never surfaced as a UI failure: the offer must be fully
     * usable even if this call fails outright (no network yet, a slow cold
     * start) — the 15s server-side resend is what recovers a lost ack, not
     * this screen retrying it.
     */
    private fun ackOnce(orderId: Long) {
        if (hasAcked) return
        hasAcked = true
        viewModelScope.launch {
            runCatching { repository.ack(orderId) }
        }
    }

    /** Retries the GET after [OfferPhase.LoadFailed] — unlike [start], not one-shot: a driver may tap it more than once. */
    fun retryLoad(orderId: Long) {
        loadOffer(orderId)
    }

    private fun loadOffer(orderId: Long) {
        _state.update { it.copy(phase = OfferPhase.Loading) }
        viewModelScope.launch {
            runCatching { repository.fetch(orderId) }
                .onSuccess { dto -> onOfferLoaded(dto) }
                .onFailure { thrown ->
                    _state.update { it.copy(phase = OfferPhase.LoadFailed(thrown.toDriverApiError())) }
                }
        }
    }

    private fun onOfferLoaded(dto: DriverOrderDto) {
        val summary = dto.toOfferSummary()
        val now = Instant.now()
        if (summary == null || isExpired(summary.expiresAt, now)) {
            // No live offer on this order (already resolved elsewhere, or the
            // countdown ran out before the GET even returned) — the screen
            // resolves itself rather than offering a dead order.
            _state.update { it.copy(phase = OfferPhase.Resolved(OfferOutcome.Expired)) }
            return
        }
        _state.update { it.copy(phase = OfferPhase.Content(order = summary)) }
        scheduleExpiry(summary.expiresAt)
    }

    /**
     * Resolves the screen on its own the instant the server's clock runs out
     * — a driver must never be left staring at a dead offer waiting for a
     * button press that can no longer succeed. A DELAY, not a poll: the exact
     * instant is already known, so there is nothing to check early for.
     */
    private fun scheduleExpiry(expiresAt: Instant) {
        viewModelScope.launch {
            val remainingMillis = java.time.Duration.between(Instant.now(), expiresAt).toMillis()
            if (remainingMillis > 0) delay(remainingMillis)
            _state.update { current ->
                // Only resolve a STILL-live, STILL-undecided offer — a
                // successful accept leaves [OfferPhase.Content] in place
                // (see [accept]'s doc) with [OfferUiState.acceptedOrder] set,
                // and this stale timer racing in after that must not clobber
                // it back to Expired.
                if (current.phase is OfferPhase.Content && current.acceptedOrder == null) {
                    current.copy(phase = OfferPhase.Resolved(OfferOutcome.Expired))
                } else {
                    current
                }
            }
        }
    }

    /**
     * On success, [OfferPhase.Content] is left exactly as it is (still
     * `isActing`) and only [OfferUiState.acceptedOrder] is set — this
     * screen's job ends at "the driver holds this order now"; the caller
     * (`OfferRoute`) reacts to [OfferUiState.acceptedOrder] and navigates
     * away, so there is no terminal phase here to design a redundant one for.
     */
    fun accept(orderId: Long) {
        val content = _state.value.phase as? OfferPhase.Content ?: return
        if (content.isActing) return
        _state.update { it.copy(phase = content.copy(isActing = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.accept(orderId) }
                .onSuccess { assigned -> _state.update { it.copy(acceptedOrder = assigned) } }
                .onFailure { thrown -> handleActionFailure(thrown) }
        }
    }

    fun decline(orderId: Long, reason: String) {
        val content = _state.value.phase as? OfferPhase.Content ?: return
        if (content.isActing) return
        _state.update { it.copy(phase = content.copy(isActing = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.decline(orderId, reason) }
                .onSuccess { _state.update { it.copy(phase = OfferPhase.Resolved(OfferOutcome.Declined)) } }
                .onFailure { thrown -> handleActionFailure(thrown) }
        }
    }

    /**
     * 🔴 Branches on the ERROR CODE, never on `message` (CLAUDE.md § أكواد
     * الأخطاء / the task brief). The five 409 outcomes the contract names for
     * `accept` are not failures the driver did something wrong to cause —
     * `already_claimed` above all is the NORMAL outcome of "offered to every
     * candidate at once" — so each maps to its own [OfferOutcome] and the
     * screen resolves, exactly like a successful decline. Anything else
     * (offline, rate_limited, server_error, an unmapped future code) is
     * genuinely retryable and stays ON [OfferPhase.Content] as an inline
     * banner, the countdown and the order untouched.
     */
    private fun handleActionFailure(thrown: Throwable) {
        val error = thrown.toDriverApiError()
        val outcome = (error as? DriverApiError.Api)?.code?.toOfferOutcome()
        _state.update { current ->
            val content = current.phase as? OfferPhase.Content ?: return@update current
            if (outcome != null) {
                current.copy(phase = OfferPhase.Resolved(outcome))
            } else {
                current.copy(phase = content.copy(isActing = false, error = error))
            }
        }
    }

    private fun DriverErrorCode.toOfferOutcome(): OfferOutcome? = when (this) {
        DriverErrorCode.AlreadyClaimed -> OfferOutcome.AlreadyClaimed
        DriverErrorCode.OfferExpired -> OfferOutcome.Expired
        DriverErrorCode.TooManyActiveOrders -> OfferOutcome.TooManyActiveOrders
        DriverErrorCode.OrderCancelled -> OfferOutcome.OrderCancelled
        DriverErrorCode.CashLimitExceeded -> OfferOutcome.CashLimitExceeded
        else -> null
    }
}
