package app.qrmenu.driver.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.datastore.LocaleManager
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.datastore.UiScale
import app.qrmenu.driver.datastore.UiScaleStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «حسابى» — identity, linked restaurants, language, size and the
 * battery-optimisation nudge, in one screen.
 *
 * [me] carries the SAME network state `:feature:home`'s `HomeViewModel`
 * carried (this module replaces that screen) — the four mandatory states
 * (loading skeleton / content / empty-with-a-reason / error incl. offline)
 * describe that part only. [language] and [uiScale] are device-local
 * preferences with no network state of their own, same reasoning as
 * `:feature:onboarding`'s `LanguageViewModel`.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val localeManager: LocaleManager,
    private val uiScaleStore: UiScaleStore,
) : ViewModel() {

    private val _me = MutableStateFlow(AccountUiState())
    val me: StateFlow<AccountUiState> = _me.asStateFlow()

    /** The driver's chosen language, or the app default while nothing has been explicitly picked. */
    val language: StateFlow<String> = localeManager.language
        .map { it ?: localeManager.default }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            localeManager.language.value ?: localeManager.default,
        )

    val uiScale: StateFlow<UiScale> = uiScaleStore.scale

    init {
        load()
    }

    /**
     * Fetches `/driver/me`.
     *
     * A restaurant may activate the driver, invite them for the first time,
     * or stop them while this screen is already open — hence [refresh] rather
     * than a one-shot fetch, exactly as `:feature:home`'s `HomeViewModel` did.
     */
    fun load() {
        val hasDataAlready = _me.value.driver != null
        _me.update {
            it.copy(
                isLoading = !hasDataAlready,
                isRefreshing = hasDataAlready,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { authApi.me() }
                .onSuccess { response ->
                    _me.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            driver = response.driver,
                            restaurants = response.restaurants,
                            error = null,
                        )
                    }
                }
                .onFailure { thrown ->
                    _me.update {
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

    /**
     * Commits a language choice and applies it immediately — the exact same
     * write [app.qrmenu.driver.datastore.LocaleManager.set] that
     * `:feature:onboarding`'s first-run picker uses, not a second mechanism.
     */
    fun selectLanguage(code: String) {
        localeManager.set(code)
    }

    fun selectUiScale(scale: UiScale) {
        uiScaleStore.set(scale)
    }

    /**
     * Signs the driver out.
     *
     * 🔴 The session is cleared REGARDLESS of whether [AuthApi.logout] reaches
     * the server — the same contract `:feature:home`'s `HomeViewModel` upheld:
     * a driver on a dead network must still be able to get off a shared
     * phone. Reaching the server is best-effort; [onSignedOut] runs only
     * after [tokenStore] is actually empty.
     */
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            runCatching { authApi.logout() }
            tokenStore.clear()
            onSignedOut()
        }
    }
}
