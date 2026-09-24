package app.qrmenu.driver.location.upload

import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.database.entity.DriverActionType
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.BreadcrumbsRequest
import app.qrmenu.driver.network.dto.LocationPointDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * An in-memory stand-in for the real Room DAO — same fake shape as
 * `feature:trip`'s own `FakeDriverActionOutboxDao` (this module has no Room
 * test dependency and does not need one for [BreadcrumbOutbox]'s own logic).
 */
private class FakeDriverActionOutboxDao : DriverActionOutboxDao {
    private val rows = MutableStateFlow<List<DriverActionOutboxEntity>>(emptyList())

    val current: List<DriverActionOutboxEntity> get() = rows.value

    override suspend fun enqueue(action: DriverActionOutboxEntity) {
        if (rows.value.any { it.idempotency_key == action.idempotency_key }) return
        rows.value = rows.value + action
    }

    override fun observePending(): Flow<List<DriverActionOutboxEntity>> = rows

    override fun observePendingCount(): Flow<Int> = rows.map { it.size }

    override fun observePendingForDriver(driverId: Long?): Flow<List<DriverActionOutboxEntity>> =
        rows.map { list -> list.filter { it.driver_id == null || it.driver_id == driverId } }

    override fun observePendingCountForDriver(driverId: Long?): Flow<Int> =
        observePendingForDriver(driverId).map { it.size }

    override suspend fun acknowledge(idempotencyKey: String) {
        rows.value = rows.value.filterNot { it.idempotency_key == idempotencyKey }
    }

    override suspend fun recordFailure(idempotencyKey: String, error: String?) {
        rows.value = rows.value.map {
            if (it.idempotency_key == idempotencyKey) {
                it.copy(attempts = it.attempts + 1, last_error = error)
            } else {
                it
            }
        }
    }
}

class BreadcrumbOutboxTest {

    private val point = LocationPointDto(lat = 24.7, lng = 46.6, recordedAt = "2026-09-24T10:00:00+03:00")
    private val json = Json { ignoreUnknownKeys = true }

    private fun tokenStore(driverId: Long? = 9L): TokenStore = mockk {
        every { this@mockk.driverId } returns MutableStateFlow(driverId)
    }

    @Test
    fun `enqueueAndSend writes a durable row and sends it once`() = runTest {
        val dao = FakeDriverActionOutboxDao()
        val api = mockk<OrderApi> {
            coEvery { breadcrumbs(any(), any(), any()) } returns AcceptedDto(true)
        }
        val outbox = BreadcrumbOutbox(dao, api, tokenStore(driverId = 9L))

        outbox.enqueueAndSend(orderId = 55L, points = listOf(point, point))

        // A successful send acknowledges (deletes) the row — nothing left queued.
        assertTrue(dao.current.isEmpty())
        coVerify(exactly = 1) { api.breadcrumbs(id = 55L, idempotencyKey = any(), body = any()) }
    }

    @Test
    fun `an empty batch enqueues nothing and calls the API zero times`() = runTest {
        val dao = FakeDriverActionOutboxDao()
        val api = mockk<OrderApi>()
        val outbox = BreadcrumbOutbox(dao, api, tokenStore())

        outbox.enqueueAndSend(orderId = 55L, points = emptyList())

        assertTrue(dao.current.isEmpty())
        coVerify(exactly = 0) { api.breadcrumbs(any(), any(), any()) }
    }

    @Test
    fun `a failed send leaves the row queued for the next drain, never dropped`() = runTest {
        val dao = FakeDriverActionOutboxDao()
        val failure = HttpException(Response.error<Any>(500, "".toResponseBody("application/json".toMediaType())))
        val api = mockk<OrderApi> {
            coEvery { breadcrumbs(any(), any(), any()) } throws failure
        }
        val outbox = BreadcrumbOutbox(dao, api, tokenStore(driverId = 9L))

        outbox.enqueueAndSend(orderId = 55L, points = listOf(point))

        assertEquals(1, dao.current.size)
        assertEquals(DriverActionType.Breadcrumbs, dao.current.single().action_type)
        assertEquals(1, dao.current.single().attempts)
        assertEquals(9L, dao.current.single().driver_id)
    }

    @Test
    fun `flushQueuedForCurrentDriver retries only this driver's own rows, others stay untouched`() = runTest {
        val dao = FakeDriverActionOutboxDao()
        val body = json.encodeToString(BreadcrumbsRequest.serializer(), BreadcrumbsRequest(points = listOf(point)))
        dao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = "own-row",
                order_id = 55L,
                action_type = DriverActionType.Breadcrumbs,
                payload_json = body,
                occurred_at = 0L,
                created_at = 0L,
                driver_id = 9L,
            ),
        )
        dao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = "other-drivers-row",
                order_id = 56L,
                action_type = DriverActionType.Breadcrumbs,
                payload_json = body,
                occurred_at = 0L,
                created_at = 0L,
                driver_id = 3L,
            ),
        )
        val sentOrderIds = mutableListOf<Long>()
        val api = mockk<OrderApi> {
            coEvery { breadcrumbs(any(), any(), any()) } answers {
                sentOrderIds.add(firstArg())
                AcceptedDto(true)
            }
        }
        val outbox = BreadcrumbOutbox(dao, api, tokenStore(driverId = 9L))

        outbox.flushQueuedForCurrentDriver()

        assertEquals(listOf(55L), sentOrderIds)
        // The other driver's row is untouched — still queued, never sent under 9's session.
        assertEquals(1, dao.current.size)
        assertEquals("other-drivers-row", dao.current.single().idempotency_key)
    }

    @Test
    fun `a non-breadcrumb row is never touched by the breadcrumb drain`() = runTest {
        val dao = FakeDriverActionOutboxDao()
        dao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = "picked-up-row",
                order_id = 55L,
                action_type = DriverActionType.PickedUp,
                payload_json = "{}",
                occurred_at = 0L,
                created_at = 0L,
                driver_id = 9L,
            ),
        )
        val api = mockk<OrderApi>()
        val outbox = BreadcrumbOutbox(dao, api, tokenStore(driverId = 9L))

        outbox.flushQueuedForCurrentDriver()

        coVerify(exactly = 0) { api.breadcrumbs(any(), any(), any()) }
        assertEquals(1, dao.current.size)
    }
}
