package app.qrmenu.driver.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Who am I, and which restaurants do I work for — driven entirely by
 * `GET /driver/me`.
 *
 * A driver may work for several restaurants at once (decision 19), so
 * [restaurants] is a list from the first successful load, not a single
 * company picked once. The four screen states this drives are all mandatory
 * (CLAUDE.md § rules): [isLoading] alone means the skeleton, no [error] and an
 * empty [restaurants] with a non-null [driver] means the invite-only empty
 * state, a non-null [error] means the inline banner, and [DriverApiError.Offline]
 * inside that error is what makes the banner read as "no connection" rather
 * than "the server said no" (`DriverErrorBanner` already tells the two apart).
 */
data class HomeUiState(
    /** True only for the FIRST load, before any data has ever arrived — drives the skeleton. */
    val isLoading: Boolean = true,
    /** True while a pull-to-refresh is in flight on top of data already on screen. */
    val isRefreshing: Boolean = false,
    val driver: DriverDto? = null,
    val restaurants: List<RestaurantLinkDto> = emptyList(),
    val error: DriverApiError? = null,
) {
    /**
     * A verified phone with zero restaurants is a REAL, expected state (a
     * driver who proved their number but has not been invited yet) — never a
     * blank screen. Guarded on [driver] so a failed FIRST load (no data at
     * all) is never mistaken for "confirmed empty".
     */
    val isEmpty: Boolean
        get() = !isLoading && driver != null && restaurants.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Fetches `/driver/me`.
     *
     * The restaurant may activate the driver, or invite them for the first
     * time, while this screen is already open — hence [refresh] rather than a
     * one-shot fetch. [isLoading] vs [isRefreshing] is which spinner shows:
     * a shimmer skeleton replacing nothing on the FIRST load, a small
     * pull-to-refresh indicator over data already there on every load after.
     */
    fun load() {
        val hasDataAlready = _state.value.driver != null
        _state.update {
            it.copy(
                isLoading = !hasDataAlready,
                isRefreshing = hasDataAlready,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { authApi.me() }
                .onSuccess { me ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            driver = me.driver,
                            restaurants = me.restaurants,
                            error = null,
                        )
                    }
                }
                .onFailure { thrown ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = thrown.toDriverApiError(),
                        )
                    }
                }
        }
    }

    fun refresh() = load()

    fun onDismissError() = _state.update { it.copy(error = null) }

    /**
     * Signs the driver out.
     *
     * 🔴 The session is cleared REGARDLESS of whether [AuthApi.logout] reaches
     * the server: a driver on a dead network must still be able to get off a
     * shared phone. Reaching the server is best-effort — it tidies up the
     * server-side token row when it can, and leaves it to expire on its own
     * (30 days, CLAUDE.md § auth) when it cannot. [onSignedOut] is called in
     * both cases, once [tokenStore] is actually empty, never before.
     */
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            runCatching { authApi.logout() }
            tokenStore.clear()
            onSignedOut()
        }
    }
}
