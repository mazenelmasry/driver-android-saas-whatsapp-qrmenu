package app.qrmenu.driver.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import app.qrmenu.driver.database.entity.DriverActionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Proves MIGRATION_2_3 actually runs against a real, previously-shipped v2
 * database — per `.claude/skills/add-room-entity` §7: "a migration that
 * reads correctly is not proof it runs correctly."
 *
 * The v2 file here is built from the exact `createSql`/setup queries in
 * `schemas/app.qrmenu.driver.database.DriverDatabase/2.json`, seeded with a
 * pre-existing outbox row (no `driver_id` column exists yet, since it did
 * not exist at v2) before the migration runs. After migrating, that row must
 * survive with `driver_id = NULL` — see DriverActionOutboxEntity's class doc
 * on why a legacy row has no owner to backfill and must keep today's
 * behaviour (replayed regardless of who is signed in).
 */
@RunWith(RobolectricTestRunner::class)
class DriverDatabaseMigration2To3Test {

    private val dbName = "migration-2-3-test.db"

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun `migration 2 to 3 adds a nullable driver_id without losing the existing row`() {
        seedVersion2DatabaseWithOneOutboxRow()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            DriverDatabase::class.java,
            dbName,
        )
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        try {
            // Room validates the post-migration schema against its compiled
            // v3 expectations the moment the database is touched — a wrong
            // ALTER TABLE in MIGRATION_2_3 throws here.
            val row = runBlocking { migrated.driverActionOutboxDao().observePending().first().single() }
            assertEquals("preexisting-action", row.idempotency_key)
            assertNull(row.driver_id)

            // The new column is genuinely queryable via the driver-scoped
            // DAO methods TripRepository actually relies on, not just
            // present in the schema.
            runBlocking {
                val scoped = migrated.driverActionOutboxDao().observePendingForDriver(999L).first()
                assertEquals(1, scoped.size)
                assertEquals(1, migrated.driverActionOutboxDao().observePendingCountForDriver(999L).first())
            }
        } finally {
            migrated.close()
        }
    }

    private fun seedVersion2DatabaseWithOneOutboxRow() {
        val path = RuntimeEnvironment.getApplication().getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        db.use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `driver_actions_outbox` (" +
                    "`idempotency_key` TEXT NOT NULL, `order_id` INTEGER NOT NULL, " +
                    "`action_type` TEXT NOT NULL, `payload_json` TEXT NOT NULL, " +
                    "`occurred_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`attempts` INTEGER NOT NULL, `last_error` TEXT, " +
                    "PRIMARY KEY(`idempotency_key`))",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_driver_actions_outbox_order_id` " +
                    "ON `driver_actions_outbox` (`order_id`)",
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `notification_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`order_id` INTEGER, `offer_id` INTEGER, `occurred_at` INTEGER NOT NULL, " +
                    "`is_read` INTEGER NOT NULL)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_history_occurred_at` " +
                    "ON `notification_history` (`occurred_at`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_history_is_read` " +
                    "ON `notification_history` (`is_read`)",
            )
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, '5ca40537ffc35ad4afd2bdb7c984ab4a')",
            )
            database.execSQL("PRAGMA user_version = 2")

            database.execSQL(
                "INSERT INTO driver_actions_outbox " +
                    "(idempotency_key, order_id, action_type, payload_json, occurred_at, created_at, attempts, last_error) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "preexisting-action",
                    501L,
                    DriverActionType.PickedUp.name,
                    "{}",
                    1_699_000_000_000L,
                    1_699_000_000_000L,
                    0L,
                    null,
                ),
            )
        }
    }
}
