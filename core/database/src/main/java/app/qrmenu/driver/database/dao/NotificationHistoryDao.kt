package app.qrmenu.driver.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.qrmenu.driver.database.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {

    @Insert
    suspend fun insert(notification: NotificationHistoryEntity): Long

    // Newest first — a driver opening the centre wants "what just happened",
    // not a scroll from the beginning of time.
    @Query("SELECT * FROM notification_history ORDER BY occurred_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = MAX_HISTORY_ROWS): Flow<List<NotificationHistoryEntity>>

    // Backs the bell badge on DriverHeader — see NotificationHistoryStore,
    // which is what actually exposes this outside :core:database.
    @Query("SELECT COUNT(*) FROM notification_history WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    // Opening the centre marks everything read (feature spec) — a driver who
    // has looked at the list must not still see a red number.
    @Query("UPDATE notification_history SET is_read = 1 WHERE is_read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_history WHERE occurred_at < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long)

    // Sign-out privacy: a shared device's next driver must not see the
    // previous driver's offers/notifications. Unlike the outbox (unsent,
    // possibly money-affecting state — kept on purpose), this table is a
    // pure notification log with nothing to lose by clearing it.
    @Query("DELETE FROM notification_history")
    suspend fun clearAll()

    // Row-count cap, independent of age — a driver working long, busy shifts
    // can accumulate more than the age window would prune in a single day.
    @Query(
        "DELETE FROM notification_history WHERE id NOT IN " +
            "(SELECT id FROM notification_history ORDER BY occurred_at DESC LIMIT :keep)",
    )
    suspend fun purgeBeyond(keep: Int)

    companion object {
        /**
         * Retention cap (CLAUDE.md's "🗃️ الاحتفاظ بالبيانات" table has no row
         * for this data yet — nearest analogues are breadcrumbs at 30 days
         * and the on-device order cache at 7 days). Chosen at 30 days: this
         * is an append-only device-local event log a driver checks "after
         * the fact, often parked" (NotificationCenterScreen's own doc), so
         * it should behave like the breadcrumb trail it sits next to in
         * spirit, not like the short-lived order cache.
         */
        const val RETENTION_WINDOW_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        /**
         * Row-count cap alongside the age cap — a driver on a long, busy
         * shift can rack up far more than fits comfortably in a scrolling
         * list on a cheap phone well before 30 days pass. 200 is generous
         * for a single push-per-offer stream (dozens of offers a day at
         * most) while keeping the table, and the list it backs, small.
         */
        const val MAX_HISTORY_ROWS: Int = 200
    }
}
