package app.qrmenu.driver.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.dao.NotificationHistoryDao
import app.qrmenu.driver.datastore.LocaleManager
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.datastore.UiScale
import app.qrmenu.driver.datastore.UiScaleStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.DeletionRequestDto
import app.qrmenu.driver.network.dto.RequestAccountDeletionRequest
import app.qrmenu.driver.network.errors.DriverApiError
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
    private val notificationHistoryDao: NotificationHistoryDao,
    outboxDao: DriverActionOutboxDao,
) : ViewModel() {

    private val _me = MutableStateFlow(AccountUiState())
    val me: StateFlow<AccountUiState> = _me.asStateFlow()

    /**
     * Whether THIS driver still has trip commands queued for the server —
     * backs the distinct warning in [SignOutConfirmDialog]: a driver signing
     * out on a phone that will be handed to someone else should know some
     * actions haven't reached the restaurant yet, not just tap through a
     * generic confirmation.
     */
    val hasUnsentActions: StateFlow<Boolean> =
        outboxDao.observePendingCountForDriver(tokenStore.driverId.value)
            .map { it > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The driver's chosen language, or the app default while nothing has been explicitly picked. */
    val language: StateFlow<String> = localeManager.language
        .map { it ?: localeManager.default }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            localeManager.language.value ?: localeManager.default,
        )

    val uiScale: StateFlow<UiScale> = uiScaleStore.scale

    private val _deletionRequest = MutableStateFlow(DeletionRequestUiState())
    val deletionRequest: StateFlow<DeletionRequestUiState> = _deletionRequest.asStateFlow()

    init {
        load()
        loadDeletionRequest()
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
     *
     * 🔴 Also purges `notification_history` — a shared device's NEXT driver
     * must not see the previous driver's offers/notifications. The offline
     * outbox is deliberately NOT touched here: a queued row can be unsent,
     * money-affecting state (see `DriverActionOutboxEntity`'s class doc), and
     * it is now stamped with its owning driver id and skipped by anyone
     * else's session (see [TripRepository.flushPending]'s own doc) — it does
     * not need clearing to stay private, and clearing it would destroy the
     * only record of a delivery that hasn't reached the server yet.
     */
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            runCatching { authApi.logout() }
            tokenStore.clear()
            runCatching { notificationHistoryDao.clearAll() }
            onSignedOut()
        }
    }

    /**
     * Reads whatever deletion request already exists (null when the driver
     * has never filed one, or every prior one was rejected) — so the screen
     * can show "under review" instead of offering the delete action again.
     * Silent on failure: a driver whose screen otherwise loaded fine must not
     * be blocked from anything else by this one secondary call failing.
     */
    fun loadDeletionRequest() {
        viewModelScope.launch {
            runCatching { authApi.accountDeletionRequest() }
                .onSuccess { response ->
                    _deletionRequest.update {
                        it.copy(isLoading = false, request = response.deletionRequest, error = null)
                    }
                }
                .onFailure {
                    _deletionRequest.update { it.copy(isLoading = false) }
                }
        }
    }

    /**
     * Files the account-deletion request. Idempotent server-side — a driver
     * who taps twice (or reopens the screen mid-flight) gets the same pending
     * request back rather than a second row — but [isSubmitting] still guards
     * a genuine double-tap so the dialog cannot fire two requests at once.
     */
    fun requestAccountDeletion(reason: String?) {
        if (_deletionRequest.value.isSubmitting) return
        _deletionRequest.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val trimmedReason = reason?.trim()?.takeIf { it.isNotEmpty() }
            runCatching { authApi.requestAccountDeletion(RequestAccountDeletionRequest(reason = trimmedReason)) }
                .onSuccess { request ->
                    _deletionRequest.update { it.copy(isSubmitting = false, request = request, error = null) }
                }
                .onFailure { thrown ->
                    _deletionRequest.update { it.copy(isSubmitting = false, error = thrown.toDriverApiError()) }
                }
        }
    }

    /** Clears a failed submission's error, e.g. when the driver dismisses the dialog and reopens it. */
    fun dismissDeletionError() {
        _deletionRequest.update { it.copy(error = null) }
    }
}

/**
 * The Play-Store-required account-deletion request, as last known.
 *
 * [isLoading] guards only the FIRST read (before the driver ever opens the
 * confirm dialog) — a request already loaded is never covered back up by a
 * skeleton while [requestAccountDeletion] runs; that is what [isSubmitting]
 * is for instead.
 */
data class DeletionRequestUiState(
    val isLoading: Boolean = true,
    val request: DeletionRequestDto? = null,
    val isSubmitting: Boolean = false,
    val error: DriverApiError? = null,
) {
    val isPending: Boolean get() = request?.status == "pending"
}
