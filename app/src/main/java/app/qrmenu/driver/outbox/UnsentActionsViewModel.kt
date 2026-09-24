package app.qrmenu.driver.outbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.datastore.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * How many trip actions are still waiting to reach the server.
 *
 * Reads `observePendingCountForDriver()`, which until now had no consumer
 * outside tests — the queue could hold a delivery for hours and the driver
 * had no way to know. That is tolerable while the background drain is
 * succeeding and unacceptable once it has given up: `OutboxFlushWorker`
 * deliberately stops rescheduling after its retry ceiling WITHOUT deleting
 * anything, precisely so a human can be told instead of the record being
 * dropped. This is the telling.
 *
 * Scoped to the currently signed-in driver — a row left behind by a PREVIOUS
 * driver on a shared device is not "unsent" from this driver's point of view
 * (see `DriverActionOutboxEntity.driver_id`'s own doc).
 */
@HiltViewModel
class UnsentActionsViewModel @Inject constructor(
    outboxDao: DriverActionOutboxDao,
    tokenStore: TokenStore,
) : ViewModel() {

    val pendingCount: StateFlow<Int> = outboxDao.observePendingCountForDriver(tokenStore.driverId.value)
        .stateIn(
            scope = viewModelScope,
            // Keeps the Room query alive across the tab switches a driver makes
            // constantly, without holding it while the app is backgrounded.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
