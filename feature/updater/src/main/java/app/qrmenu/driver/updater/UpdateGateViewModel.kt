package app.qrmenu.driver.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.AppVersionDto
import app.qrmenu.driver.network.update.ClientUpdateGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the advisory `driver/app-version` poll — on creation (app start, since
 * this is instantiated once at [app.qrmenu.driver.updater.UpdateGate]'s call
 * site inside the always-composed signed-in screen) and every 6 hours after,
 * for as long as the process lives (CLAUDE.md § التوزيع والتحديث الذاتى) —
 * and folds its result together with [ClientUpdateGate] (the 426 that has
 * already happened this process, if any) and [UpdateDismissalStore] (what the
 * driver already closed) into the one thing a screen needs: [state].
 *
 * Deliberately does NOT know about "does the driver have an active trip" —
 * that fact lives at the app-navigation level (`activeTripId` in
 * `SignedInScreen`), not in this feature module, and is passed in by whatever
 * reads [state] rather than duplicated here.
 */
@HiltViewModel
class UpdateGateViewModel @Inject constructor(
    private val repository: AppVersionRepository,
    private val updateGate: ClientUpdateGate,
    private val dismissalStore: UpdateDismissalStore,
) : ViewModel() {

    private val remote = MutableStateFlow<AppVersionDto?>(null)

    val state: StateFlow<UpdateGateState> = combine(
        remote,
        updateGate.requiredVersionCode,
        dismissalStore.dismissedVersionCode,
    ) { remoteVersion, interceptorMin, dismissed ->
        UpdateGateState(remote = remoteVersion, interceptorMinVersionCode = interceptorMin, dismissedVersionCode = dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdateGateState())

    init {
        viewModelScope.launch {
            while (isActive) {
                // A failed check leaves `remote` at its last known-good value
                // rather than nulling it out — losing signal for one poll must
                // not un-block a driver the previous, successful poll already
                // knew was below the floor, nor drop a banner the driver has
                // not dismissed yet.
                repository.check()?.let { remote.value = it }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun dismissBanner(latestVersionCode: Int) {
        dismissalStore.dismiss(latestVersionCode)
    }

    private companion object {
        val POLL_INTERVAL_MS = 6.hours.inWholeMilliseconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Raw inputs to [UpdateDecision] — combined here, decided by the pure object. */
data class UpdateGateState(
    val remote: AppVersionDto? = null,
    val interceptorMinVersionCode: Int? = null,
    val dismissedVersionCode: Int? = null,
)
