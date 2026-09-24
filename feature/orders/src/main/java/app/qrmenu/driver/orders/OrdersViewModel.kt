package app.qrmenu.driver.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the two lists is on screen. */
enum class OrdersTab { Mine, Available }

/**
 * One list's own loading/content/error state — the two tabs never block each
 * other (a slow `/orders/available` must not stall "طلباتى", and the reverse).
 *
 * There is deliberately no "offline" variant distinct from [error]: unlike
 * `:feature:availability`'s heartbeat, this screen has no standing connection
 * to lose — every load is a one-shot GET, and [DriverApiError.Offline] already
 * renders as its own sentence via `DriverErrorBanner`. A silent blank list is
 * still never allowed — see [OrdersUiState.availableReason] for the "المتاحة"
 * tab's decision-47 explanation, which fires even on a perfectly successful,
 * genuinely-empty response.
 */
data class OrderListState(
    val isLoading: Boolean = true,
    val orders: List<DriverOrderDto> = emptyList(),
    val error: DriverApiError? = null,
    /** True only while a pull-to-refresh (not the first load) is in flight. */
    val isRefreshing: Boolean = false,
)

data class OrdersUiState(
    val tab: OrdersTab = OrdersTab.Mine,
    val mine: OrderListState = OrderListState(),
    val available: OrderListState = OrderListState(),
    /**
     * WHY "المتاحة" is empty (decision 47) — set on every successful load of
     * that list, `null` the instant it holds a real order. Kept separate from
     * [OrderListState.error]: an empty list with a reason is success, not
     * failure, and must never render through the red error banner.
     */
    val availableContext: AvailabilityContextDto? = null,
    /**
     * The id of the order whose «خُذ الطلب» is in flight, or `null`. Only one
     * at a time: a driver holds one trip (decision 21), so a second claim
     * while the first is unresolved could only ever end in a refusal — and
     * two spinners on two cards would tell them they are getting both.
     */
    val claimingOrderId: Long? = null,
    /**
     * A claim that failed, as one line under the list — NOT through
     * [OrderListState.error]'s red banner.
     *
     * 🔴 Losing the race is the ORDINARY outcome of `self_claim`, not a
     * fault: every driver at that branch sees the same order and one of them
     * taps first. Rendering it in the same red the app uses for "you are
     * offline" would teach a driver that the normal working day is full of
     * errors, and the red they should act on would stop registering.
     */
    val claimMessage: ClaimMessage? = null,
    /** The error behind [ClaimMessage.Failed], so its own wording is shown. */
    val claimFailure: DriverApiError? = null,
)

/** Why a tap on «خُذ الطلب» did not end in a trip. */
enum class ClaimMessage {
    /** 409 `already_claimed` — somebody was faster. Expected, frequent, not an error. */
    Lost,

    /**
     * Anything else (offline, expired, over the cash ceiling, the order was
     * cancelled). Carries [OrdersUiState.claimFailure] so the driver reads
     * the server's own sentence rather than a generic one.
     */
    Failed,
}

/**
 * Loads and refreshes the two order lists. The 20s resumed-screen poll lives
 * in the Route (see [OrdersRoute]) rather than here, so this class stays a
 * plain request/response state holder testable without a `LifecycleOwner`.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val repository: OrdersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        loadMine()
        loadAvailable()
    }

    fun selectTab(tab: OrdersTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun loadMine(isRefresh: Boolean = false) {
        _state.update {
            it.copy(mine = it.mine.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null))
        }
        viewModelScope.launch {
            runCatching { repository.mine() }
                .onSuccess { orders ->
                    _state.update {
                        it.copy(mine = OrderListState(isLoading = false, orders = orders))
                    }
                }
                .onFailure { thrown ->
                    _state.update {
                        it.copy(
                            mine = it.mine.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = thrown.toDriverApiError(),
                            ),
                        )
                    }
                }
        }
    }

    fun loadAvailable(isRefresh: Boolean = false) {
        _state.update {
            it.copy(available = it.available.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null))
        }
        viewModelScope.launch {
            runCatching { repository.available() }
                .onSuccess { (orders, context) ->
                    _state.update {
                        it.copy(
                            available = OrderListState(isLoading = false, orders = orders),
                            availableContext = context,
                        )
                    }
                }
                .onFailure { thrown ->
                    _state.update {
                        it.copy(
                            available = it.available.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = thrown.toDriverApiError(),
                            ),
                        )
                    }
                }
        }
    }

    /**
     * A quiet background refresh (the screen's 20s safety-net poll, or a screen
     * resume) — never flips a list back into [OrderListState.isLoading], which
     * would replace real, already-shown orders with a skeleton for no reason a
     * driver could see any benefit from.
     */
    fun refreshQuietly() {
        viewModelScope.launch {
            runCatching { repository.mine() }.onSuccess { orders ->
                _state.update { it.copy(mine = it.mine.copy(orders = orders, error = null)) }
            }
        }
        viewModelScope.launch {
            runCatching { repository.available() }.onSuccess { (orders, context) ->
                _state.update {
                    it.copy(
                        available = it.available.copy(orders = orders, error = null),
                        availableContext = context,
                    )
                }
            }
        }
    }

    /**
     * «خُذ الطلب». On success the order is this driver's and [onClaimed] opens
     * the trip screen on it — the same place accepting a pushed offer lands,
     * because by this point the two are the same situation.
     *
     * 🔴 The card is dropped from «المتاحة» the instant the server answers,
     * win or lose. On a win it is no longer available to anyone; on a loss it
     * belongs to somebody else. Leaving it on screen in either case invites a
     * second tap on an order that cannot be had, and the poll would only
     * remove it up to 20 seconds later.
     */
    fun claim(orderId: Long, onClaimed: (Long) -> Unit) {
        if (_state.value.claimingOrderId != null) {
            // A double-tap, or a tap on a second card while the first is
            // still in flight. Dropped rather than queued: the server would
            // refuse it anyway, and a refusal the driver did not ask for
            // reads as the app having taken something away from them.
            return
        }

        _state.update { it.copy(claimingOrderId = orderId, claimMessage = null, claimFailure = null) }

        viewModelScope.launch {
            runCatching { repository.claim(orderId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            claimingOrderId = null,
                            available = it.available.copy(orders = it.available.orders.withoutOrder(orderId)),
                        )
                    }
                    // «طلباتى» is where this order lives now, and the driver
                    // may come back to this screen from the trip.
                    loadMine(isRefresh = true)
                    onClaimed(orderId)
                }
                .onFailure { thrown ->
                    val failure = thrown.toDriverApiError()
                    // Branches on the error CODE, never on `message` — the
                    // same rule `OfferViewModel` states for the identical
                    // race. `code` lives on the Api subclass alone: offline
                    // has no server code, and is not a lost race.
                    val lost = (failure as? DriverApiError.Api)?.code == DriverErrorCode.AlreadyClaimed

                    _state.update {
                        it.copy(
                            claimingOrderId = null,
                            claimMessage = if (lost) ClaimMessage.Lost else ClaimMessage.Failed,
                            claimFailure = failure.takeUnless { lost },
                            available = if (lost) {
                                it.available.copy(orders = it.available.orders.withoutOrder(orderId))
                            } else {
                                // 🔴 A failure that is NOT "someone else has
                                // it" leaves the order exactly where it was:
                                // it is still there to be taken, and removing
                                // it would hide work the driver can still do
                                // once they are back on signal.
                                it.available
                            },
                        )
                    }

                    // Whatever the reason, the list's truth now comes from the
                    // server rather than from this guess.
                    refreshQuietly()
                }
        }
    }

    /** Dismisses the claim line — tapping it, or taking another order. */
    fun dismissClaimMessage() {
        _state.update { it.copy(claimMessage = null, claimFailure = null) }
    }

    fun retry(tab: OrdersTab) {
        when (tab) {
            OrdersTab.Mine -> loadMine()
            OrdersTab.Available -> loadAvailable()
        }
    }
}

private fun List<DriverOrderDto>.withoutOrder(orderId: Long): List<DriverOrderDto> =
    filterNot { it.id == orderId }

/** The resumed-screen poll interval — a safety net until Reverb lands (CLAUDE.md). */
internal const val ORDERS_POLL_INTERVAL_MS = 20_000L

/** Suspends forever, calling [onTick] every [ORDERS_POLL_INTERVAL_MS] — cancelled with its coroutine scope. */
internal suspend fun pollForever(onTick: () -> Unit) {
    while (true) {
        delay(ORDERS_POLL_INTERVAL_MS)
        onTick()
    }
}
