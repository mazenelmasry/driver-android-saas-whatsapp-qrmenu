package app.qrmenu.driver.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverActionOutboxDao {

    // IGNORE, not REPLACE: idempotency_key is the primary key. A retry that
    // re-enqueues the same key (e.g. the caller wasn't sure the first enqueue
    // committed) must not clobber attempts/last_error on a row that is
    // already mid-flight.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(action: DriverActionOutboxEntity)

    // Oldest first — actions must reach the server in the order they
    // happened (picked-up before delivered for the same order).
    @Query("SELECT * FROM driver_actions_outbox ORDER BY created_at ASC")
    fun observePending(): Flow<List<DriverActionOutboxEntity>>

    // Backs the "لديك إجراءات لم تُرسَل" banner (device-change flush,
    // CLAUDE.md's الحالات الحدّية table) without collecting the full list.
    @Query("SELECT COUNT(*) FROM driver_actions_outbox")
    fun observePendingCount(): Flow<Int>

    // A row is "sent" purely by ceasing to exist — there is no separate
    // status column to fall out of sync with the table's actual contents.
    @Query("DELETE FROM driver_actions_outbox WHERE idempotency_key = :idempotencyKey")
    suspend fun acknowledge(idempotencyKey: String)

    @Query(
        "UPDATE driver_actions_outbox SET attempts = attempts + 1, last_error = :error " +
            "WHERE idempotency_key = :idempotencyKey",
    )
    suspend fun recordFailure(idempotencyKey: String, error: String?)
}
