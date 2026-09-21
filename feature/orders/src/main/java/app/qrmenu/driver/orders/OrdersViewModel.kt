package app.qrmenu.driver.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
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
)

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

    fun retry(tab: OrdersTab) {
        when (tab) {
            OrdersTab.Mine -> loadMine()
            OrdersTab.Available -> loadAvailable()
        }
    }
}

/** The resumed-screen poll interval — a safety net until Reverb lands (CLAUDE.md). */
internal const val ORDERS_POLL_INTERVAL_MS = 20_000L

/** Suspends forever, calling [onTick] every [ORDERS_POLL_INTERVAL_MS] — cancelled with its coroutine scope. */
internal suspend fun pollForever(onTick: () -> Unit) {
    while (true) {
        delay(ORDERS_POLL_INTERVAL_MS)
        onTick()
    }
}
