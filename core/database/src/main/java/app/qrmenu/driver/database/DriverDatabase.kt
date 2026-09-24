package app.qrmenu.driver.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.dao.NotificationHistoryDao
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.database.entity.NotificationHistoryEntity

// See CLAUDE.md's "سجلّ مخطط Room" — keep that table in sync with every
// version bump made here.
@Database(
    version = 3,
    exportSchema = true,
    entities = [
        DriverActionOutboxEntity::class,
        NotificationHistoryEntity::class,
    ],
)
@TypeConverters(DriverActionTypeConverter::class, NotificationHistoryTypeConverter::class)
abstract class DriverDatabase : RoomDatabase() {
    abstract fun driverActionOutboxDao(): DriverActionOutboxDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
}

/**
 * Adds `notification_history` (the notification centre's data source and the
 * `DriverHeader` bell-badge count) — see `.claude/skills/add-room-entity` §5
 * and `NotificationHistoryEntity`'s class doc. Column list and types must
 * match the entity exactly; verified by running this against a real v1
 * database in `DriverDatabaseMigrationTest` (§7 of the same skill — "reads
 * correctly" is not proof it runs correctly).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notification_history` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `type` TEXT NOT NULL,
              `order_id` INTEGER,
              `offer_id` INTEGER,
              `occurred_at` INTEGER NOT NULL,
              `is_read` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_history_occurred_at` " +
                "ON `notification_history` (`occurred_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_history_is_read` " +
                "ON `notification_history` (`is_read`)",
        )
    }
}

/**
 * Adds `driver_actions_outbox.driver_id` — see [DriverActionOutboxEntity]'s
 * class doc for why an unstamped outbox row on a shared device could be
 * replayed and lost under a second driver's session. Nullable with no
 * backfill: an existing queued row (queued by whoever was signed in before
 * this migration ships) has no way to know its author retroactively, so it
 * keeps today's behaviour — replayed regardless of who is signed in now —
 * rather than being guessed at or dropped.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `driver_actions_outbox` ADD COLUMN `driver_id` INTEGER DEFAULT NULL",
        )
    }
}
