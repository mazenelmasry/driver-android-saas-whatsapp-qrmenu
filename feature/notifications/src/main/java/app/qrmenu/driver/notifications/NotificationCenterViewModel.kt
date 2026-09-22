package app.qrmenu.driver.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.database.NotificationHistoryStore
import app.qrmenu.driver.database.entity.NotificationHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val items: List<NotificationHistoryEntity> = emptyList(),
    /**
     * True only if the local Room [kotlinx.coroutines.flow.Flow] itself
     * failed — there is no network call on this screen (all data is
     * already on the device), so this is a genuine local-storage failure,
     * not the offline/refused distinction `DriverErrorBanner` renders for
     * API-backed screens elsewhere in the app.
     */
    val error: Boolean = false,
)

/**
 * Loads the notification history list and marks it read the moment this
 * screen is opened — a driver who has looked at the centre must not still
 * see a red number on [app.qrmenu.driver.ui.components.DriverHeader]'s bell.
 */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val store: NotificationHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
        markAllRead()
    }

    fun retry() = observe()

    private fun observe() {
        observeJob?.cancel()
        _state.update { it.copy(isLoading = true, error = false) }
        observeJob = viewModelScope.launch {
            store.recentHistory()
                .catch { _state.update { current -> current.copy(isLoading = false, error = true) } }
                .collect { items ->
                    _state.update { current -> current.copy(isLoading = false, items = items, error = false) }
                }
        }
    }

    private fun markAllRead() {
        viewModelScope.launch { runCatching { store.markAllRead() } }
    }
}
