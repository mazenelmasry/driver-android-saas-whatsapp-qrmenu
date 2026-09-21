package app.qrmenu.driver.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.LedgerEntryDto
import app.qrmenu.driver.network.dto.LedgerResponse
import app.qrmenu.driver.network.dto.SettlementDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which pane of this module is on screen — a local two-screen "navigation", not the app's NavHost. */
enum class WalletPane { Book, Settlements }

/**
 * ONE restaurant's book: summary + entries.
 *
 * There is deliberately no "offline" variant distinct from [error]
 * (`DriverApiError.Offline` already renders as its own sentence via
 * `DriverErrorBanner` — the same choice `OrderListState` makes) — every load
 * here is a one-shot GET, not a standing connection to lose.
 */
data class BookState(
    val isLoading: Boolean = true,
    val summary: LedgerResponse? = null,
    val error: DriverApiError? = null,
    val isRefreshing: Boolean = false,
) {
    val entries: List<LedgerEntryDto> get() = summary?.entries.orEmpty()
}

data class SettlementsState(
    val isLoading: Boolean = true,
    val settlements: List<SettlementDto> = emptyList(),
    val error: DriverApiError? = null,
)

/**
 * 🔴 [selectedCompanyId] is the single source of truth for which restaurant's
 * book is on screen, and [book] holds ONLY that restaurant's figures — there
 * is no field anywhere on this class that could hold a cross-restaurant total
 * (binding rule 2). Switching restaurants replaces [book] outright rather than
 * merging into it.
 */
data class WalletUiState(
    val pane: WalletPane = WalletPane.Book,
    val restaurantsLoading: Boolean = true,
    val restaurants: List<RestaurantOption> = emptyList(),
    val restaurantsError: DriverApiError? = null,
    val selectedCompanyId: Long? = null,
    val book: BookState = BookState(),
    val settlements: SettlementsState = SettlementsState(),
)

/**
 * Loads the driver's linked restaurants, then ONE restaurant's book at a
 * time. Switching restaurants is a full reload of [BookState] for the new
 * `companyId` — never a merge — which is what keeps two currencies from ever
 * sharing a number on screen (CLAUDE.md, binding rule 2).
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repository: WalletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    init {
        loadRestaurants()
    }

    private fun loadRestaurants() {
        _state.update { it.copy(restaurantsLoading = true, restaurantsError = null) }
        viewModelScope.launch {
            runCatching { repository.restaurants() }
                .onSuccess { restaurants ->
                    _state.update {
                        it.copy(
                            restaurantsLoading = false,
                            restaurants = restaurants,
                            // Keep an already-chosen restaurant selected across a
                            // refresh when it is still linked; otherwise land on
                            // the first one. A driver with zero restaurants stays
                            // `null` — the empty state names that reason.
                            selectedCompanyId = it.selectedCompanyId
                                ?.takeIf { id -> restaurants.any { r -> r.companyId == id } }
                                ?: restaurants.firstOrNull()?.companyId,
                        )
                    }
                    _state.value.selectedCompanyId?.let { companyId -> loadBook(companyId) }
                }
                .onFailure { thrown ->
                    _state.update {
                        it.copy(restaurantsLoading = false, restaurantsError = thrown.toDriverApiError())
                    }
                }
        }
    }

    /** The driver taps a different restaurant in the switcher — a full reload, never a merge. */
    fun selectRestaurant(companyId: Long) {
        if (companyId == _state.value.selectedCompanyId) return
        _state.update { it.copy(selectedCompanyId = companyId) }
        loadBook(companyId)
    }

    fun loadBook(companyId: Long, isRefresh: Boolean = false) {
        _state.update {
            it.copy(book = it.book.copy(isLoading = !isRefresh, isRefreshing = isRefresh, error = null))
        }
        viewModelScope.launch {
            runCatching { repository.ledger(companyId) }
                .onSuccess { response ->
                    // Guard against a stale response landing after the driver has
                    // already switched restaurants again.
                    if (_state.value.selectedCompanyId != companyId) return@onSuccess
                    _state.update { it.copy(book = BookState(isLoading = false, summary = response)) }
                }
                .onFailure { thrown ->
                    if (_state.value.selectedCompanyId != companyId) return@onFailure
                    _state.update {
                        it.copy(
                            book = it.book.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = thrown.toDriverApiError(),
                            ),
                        )
                    }
                }
        }
    }

    fun retryBook() {
        _state.value.selectedCompanyId?.let { loadBook(it) }
    }

    fun retryRestaurants() = loadRestaurants()

    /** Opens the settlements pane, loading it on first entry (not eagerly with the book). */
    fun openSettlements() {
        _state.update { it.copy(pane = WalletPane.Settlements) }
        loadSettlements()
    }

    fun closeSettlements() {
        _state.update { it.copy(pane = WalletPane.Book) }
    }

    fun loadSettlements() {
        val companyId = _state.value.selectedCompanyId ?: return
        _state.update { it.copy(settlements = it.settlements.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.settlements(companyId) }
                .onSuccess { settlements ->
                    if (_state.value.selectedCompanyId != companyId) return@onSuccess
                    _state.update { it.copy(settlements = SettlementsState(isLoading = false, settlements = settlements)) }
                }
                .onFailure { thrown ->
                    if (_state.value.selectedCompanyId != companyId) return@onFailure
                    _state.update {
                        it.copy(settlements = it.settlements.copy(isLoading = false, error = thrown.toDriverApiError()))
                    }
                }
        }
    }

    fun retrySettlements() = loadSettlements()
}
