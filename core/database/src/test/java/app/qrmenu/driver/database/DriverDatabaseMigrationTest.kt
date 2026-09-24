package app.qrmenu.driver.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import app.qrmenu.driver.database.entity.DriverActionType
import app.qrmenu.driver.database.entity.NotificationHistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Proves MIGRATION_1_2 actually runs against a real, previously-shipped v1
 * database — not just that it reads correctly. Per
 * `.claude/skills/add-room-entity` §7 (and CLAUDE.md's verification
 * protocol, stated twice): "a migration that reads correctly is not proof
 * it runs correctly."
 *
 * The v1 database file here is built from the EXACT `createSql`/setup
 * queries recorded in `schemas/app.qrmenu.driver.database.DriverDatabase
 * /1.json` — the schema this app actually shipped — with one pre-existing
 * outbox row seeded before the migration runs, so a migration that
 * accidentally drops or recreates `driver_actions_outbox` (a table holding
 * unsent, possibly money-affecting driver state) fails this test even
 * though it only touches `notification_history`.
 *
 * After the migration, the database is reopened through actual
 * `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)` — Room's own
 * `onValidateSchema` step (run internally on every open after a migration)
 * introspects the live table/column/index structure and throws if it
 * disagrees with what `DriverDatabase`'s current (v2) annotations compiled
 * to. That is the authoritative check this test leans on, not a hand-rolled
 * column comparison.
 */
@RunWith(RobolectricTestRunner::class)
class DriverDatabaseMigrationTest {

    private val dbName = "migration-1-2-test.db"

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun `migration 1 to 2 adds notification_history without touching the existing outbox table`() {
        seedVersion1DatabaseWithOneOutboxRow()

        val migrated = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            DriverDatabase::class.java,
            dbName,
        )
            // The database's compiled version is 3 now, so a v1 file needs
            // the whole chain to open — not just the migration this test is
            // actually exercising.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            // Robolectric's SQLite shadow cannot reliably open a
            // file-backed database in WAL mode on this host (CANTOPEN);
            // TRUNCATE journalling is what the app itself falls back to
            // implicitly via Room's own device-capability detection on
            // real, more constrained devices, so this does not weaken what
            // the migration itself is being asked to do.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        try {
            // Room validates the post-migration schema against its compiled
            // v2 expectations the moment the database is actually touched —
            // an incorrect CREATE TABLE/INDEX in MIGRATION_1_2 throws here.
            val survivingOutbox = runBlocking { migrated.driverActionOutboxDao().observePending().first() }
            assertEquals(1, survivingOutbox.size)
            assertEquals("preexisting-action", survivingOutbox.single().idempotency_key)

            // The new table is genuinely usable, not just present.
            runBlocking {
                migrated.notificationHistoryDao().insert(
                    app.qrmenu.driver.database.entity.NotificationHistoryEntity(
                        type = NotificationHistoryType.Offer,
                        order_id = 501L,
                        offer_id = 9001L,
                        occurred_at = 1_700_000_000_000L,
                    ),
                )
                val rows = migrated.notificationHistoryDao().observeRecent().first()
                assertEquals(1, rows.size)
                assertEquals(501L, rows.single().order_id)
                assertTrue(rows.single().is_read.not())
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * Hand-builds the v1 database file exactly as Room itself would have
     * left it on a real device that had already used the app before this
     * migration existed — createSql copied verbatim from `schemas/…/1.json`.
     */
    private fun seedVersion1DatabaseWithOneOutboxRow() {
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
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, '9cfa2136bab194230a57c632df8acf0b')",
            )
            database.execSQL("PRAGMA user_version = 1")

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
