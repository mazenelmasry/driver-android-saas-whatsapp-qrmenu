package app.qrmenu.driver.database

import androidx.room.withTransaction
import app.qrmenu.driver.database.dao.NotificationHistoryDao
import app.qrmenu.driver.database.entity.NotificationHistoryEntity
import app.qrmenu.driver.database.entity.NotificationHistoryType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The one entry point to the notification-history table — `:core:push`
 * (writer, via `NotificationHistoryRecorder`) and `:feature:notifications`
 * (reader) both go through this, never the DAO directly, so the retention
 * policy (insert-then-prune, always together) can never be bypassed by a
 * caller that only knows about [NotificationHistoryDao.insert].
 *
 * [unreadCount] is a real [StateFlow], not a cold [Flow], so `:app`'s header
 * can read a plain current value the moment it composes (`DriverHeader`'s
 * `unreadNotifications: Int` parameter) without itself owning a collection
 * lifecycle for a value it doesn't otherwise care about. It is backed by
 * its own [SupervisorJob]-rooted scope rather than a Hilt-injected
 * application scope: none exists yet in this codebase (see decision log —
 * `:core:location` is the only other place that stands up its own scope
 * this way), and adding an app-wide qualifier for one caller would be a
 * bigger surface than this store owning it.
 */
@Singleton
class NotificationHistoryStore @Inject constructor(
    private val database: DriverDatabase,
    private val dao: NotificationHistoryDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val unreadCount: StateFlow<Int> = dao.observeUnreadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(UNREAD_COUNT_STOP_TIMEOUT_MS), 0)

    fun recentHistory(): Flow<List<NotificationHistoryEntity>> = dao.observeRecent()

    suspend fun markAllRead() = dao.markAllRead()

    /**
     * Records one arrival and enforces retention in the same transaction —
     * see [NotificationHistoryDao]'s companion for why 30 days / 200 rows.
     * Never throws: the age cutoff is computed from [occurredAtMillis], so a
     * clock-skewed device still prunes relative to what it just wrote, not
     * relative to a server time it doesn't have here.
     */
    suspend fun record(
        type: NotificationHistoryType,
        orderId: Long?,
        offerId: Long?,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ) {
        database.withTransaction {
            dao.insert(
                NotificationHistoryEntity(
                    type = type,
                    order_id = orderId,
                    offer_id = offerId,
                    occurred_at = occurredAtMillis,
                ),
            )
            dao.purgeOlderThan(occurredAtMillis - NotificationHistoryDao.RETENTION_WINDOW_MILLIS)
            dao.purgeBeyond(NotificationHistoryDao.MAX_HISTORY_ROWS)
        }
    }

    private companion object {
        const val UNREAD_COUNT_STOP_TIMEOUT_MS = 5_000L
    }
}
