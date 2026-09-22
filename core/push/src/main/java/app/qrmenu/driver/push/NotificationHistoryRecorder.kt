package app.qrmenu.driver.push

import android.util.Log
import app.qrmenu.driver.database.NotificationHistoryStore
import app.qrmenu.driver.database.entity.NotificationHistoryType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persists a row for the notification centre when a push arrives —
 * `:feature:notifications`' data source and the source of `DriverHeader`'s
 * unread badge.
 *
 * 🔴 Fire-and-forget by construction, and that is not a shortcut — it is
 * the whole point. [DriverMessagingService.onMessageReceived] is on the
 * critical path for waking a driver to a 45-second offer; CLAUDE.md is
 * explicit that nothing may slow down or endanger that path. [record] never
 * suspends the caller: it launches on its own [SupervisorJob]-rooted
 * `Dispatchers.IO` scope and returns immediately, and the write itself is
 * wrapped in [runCatching] so a full disk, a locked database, or any other
 * failure can log and stop — it can never propagate back into
 * `onMessageReceived` and it can never delay the [PushHandler.onOfferPush]
 * call that follows it, because that call is not waited on here at all.
 */
@Singleton
class NotificationHistoryRecorder @Inject constructor(
    private val store: NotificationHistoryStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordOfferPush(payload: OfferPushPayload) {
        scope.launch {
            runCatching {
                store.record(
                    type = NotificationHistoryType.Offer,
                    orderId = payload.orderId,
                    offerId = payload.offerId,
                )
            }.onFailure { thrown ->
                Log.w(TAG, "failed to persist notification history for offer ${payload.offerId}", thrown)
            }
        }
    }

    private companion object {
        const val TAG = "DriverPush"
    }
}
