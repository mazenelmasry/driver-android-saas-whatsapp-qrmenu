package app.qrmenu.driver.database.dao

import androidx.room.Room
import app.qrmenu.driver.database.DriverDatabase
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.database.entity.DriverActionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Real Room, real SQLite (via Robolectric's android.database.sqlite), no
 * mocks — proving the enqueue → read → acknowledge cycle actually round-trips
 * through the database engine, not just that the DAO interface compiles.
 * Per `.claude/skills/add-room-entity` §7 / CLAUDE.md's verification
 * protocol: "a migration/table that reads correctly is not proof it runs
 * correctly."
 */
@RunWith(RobolectricTestRunner::class)
class DriverActionOutboxDaoTest {

    private lateinit var database: DriverDatabase
    private lateinit var dao: DriverActionOutboxDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            DriverDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.driverActionOutboxDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun actionOf(
        key: String,
        orderId: Long = 501L,
        createdAt: Long = 1_700_000_000_000L,
        driverId: Long? = null,
    ) = DriverActionOutboxEntity(
        idempotency_key = key,
        order_id = orderId,
        action_type = DriverActionType.Delivered,
        payload_json = """{"cash_collected":25.0}""",
        occurred_at = createdAt - 5_000L,
        created_at = createdAt,
        driver_id = driverId,
    )

    @Test
    fun `enqueue then observePending returns the row in insertion order`() = runBlocking {
        dao.enqueue(actionOf("key-1", createdAt = 1_000L))
        dao.enqueue(actionOf("key-2", createdAt = 2_000L))

        val pending = dao.observePending().first()

        assertEquals(listOf("key-1", "key-2"), pending.map { it.idempotency_key })
        assertEquals(DriverActionType.Delivered, pending.first().action_type)
    }

    @Test
    fun `acknowledge deletes the row so a sent action disappears from the queue`() = runBlocking {
        dao.enqueue(actionOf("key-1"))

        dao.acknowledge("key-1")

        assertTrue(dao.observePending().first().isEmpty())
        assertEquals(0, dao.observePendingCount().first())
    }

    @Test
    fun `a retry enqueue with the same idempotency key does not duplicate or reset the row`() = runBlocking {
        dao.enqueue(actionOf("key-1"))
        dao.recordFailure("key-1", "timeout")

        // Same key re-queued after a crash mid-send — must be ignored, not
        // silently wipe the attempts/last_error the first send already wrote.
        dao.enqueue(actionOf("key-1"))

        val pending = dao.observePending().first()
        assertEquals(1, pending.size)
        assertEquals(1, pending.single().attempts)
        assertEquals("timeout", pending.single().last_error)
    }

    @Test
    fun `recordFailure increments attempts and stores the error without deleting the row`() = runBlocking {
        dao.enqueue(actionOf("key-1"))

        dao.recordFailure("key-1", "network unreachable")
        dao.recordFailure("key-1", "network unreachable")

        val row = dao.observePending().first().single()
        assertEquals(2, row.attempts)
        assertEquals("network unreachable", row.last_error)
    }

    @Test
    fun `observePendingCount reflects the queue size for the unsent-actions banner`() = runBlocking {
        assertEquals(0, dao.observePendingCount().first())

        dao.enqueue(actionOf("key-1"))
        dao.enqueue(actionOf("key-2"))
        assertEquals(2, dao.observePendingCount().first())

        dao.acknowledge("key-1")
        assertEquals(1, dao.observePendingCount().first())
        assertNull(dao.observePending().first().find { it.idempotency_key == "key-1" })
    }

    @Test
    fun `observePendingForDriver includes legacy null-owner rows and this driver's own rows only`() = runBlocking {
        dao.enqueue(actionOf("legacy", createdAt = 1_000L, driverId = null))
        dao.enqueue(actionOf("mine", createdAt = 2_000L, driverId = 42L))
        dao.enqueue(actionOf("someone-elses", createdAt = 3_000L, driverId = 99L))

        val visible = dao.observePendingForDriver(42L).first()

        assertEquals(listOf("legacy", "mine"), visible.map { it.idempotency_key })
    }

    @Test
    fun `observePendingCountForDriver excludes another driver's queued rows`() = runBlocking {
        dao.enqueue(actionOf("legacy", driverId = null))
        dao.enqueue(actionOf("mine", driverId = 42L))
        dao.enqueue(actionOf("someone-elses", driverId = 99L))

        assertEquals(2, dao.observePendingCountForDriver(42L).first())
        assertEquals(2, dao.observePendingCountForDriver(99L).first())
        // A device with nobody signed in yet still sees legacy unowned rows.
        assertEquals(1, dao.observePendingCountForDriver(null).first())
    }
}
