package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.DeclineRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Week 4 "offer & lock": `ack` / `accept` / `decline` / `claim` / `release`.
 *
 * The contract marks `Idempotency-Key` REQUIRED on all five (a repeat of a trip
 * command must be recognisable by the server, or a lost response on `accept`
 * either double-assigns the order or silently drops the driver's claim). This
 * exercises [OrderApi] through a real Retrofit + OkHttp stack against a mock
 * server, so what is asserted is the header that would actually leave the
 * phone — not what the `@Header` annotation merely declares.
 */
class OrderApiOfferLockTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OrderApi

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OrderApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueAccepted() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
    }

    private fun enqueueAssigned() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 7, "order_number": "ORD-7", "status": "assigned",
                  "delivery_method": "delivery", "payment_method": "cash",
                  "payment_status": "unpaid", "total": 40.0, "currency": "SAR",
                  "cash_to_collect": 40.0, "driver_fee": 6.0,
                  "company": {"name": "Test Co"},
                  "branch": {"id": 1, "name": "Branch 1"},
                  "items": [],
                  "customer": {"name": "Ahmed", "phone": "0500000000"},
                  "delivery_address": {"text": "Some street"}
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `ack sends the idempotency key`() = runTest {
        enqueueAccepted()
        api.ack(id = 7, idempotencyKey = "key-ack-1")

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/ack", recorded.path)
        assertEquals("key-ack-1", recorded.getHeader("Idempotency-Key"))
    }

    @Test
    fun `accept sends the idempotency key and returns the assigned shape`() = runTest {
        enqueueAssigned()
        val result = api.accept(id = 7, idempotencyKey = "key-accept-1")

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/accept", recorded.path)
        assertEquals("key-accept-1", recorded.getHeader("Idempotency-Key"))
        assertEquals(true, result.isAssigned)
        assertEquals("Ahmed", result.customer?.name)
    }

    @Test
    fun `decline sends the idempotency key and the reason body`() = runTest {
        enqueueAccepted()
        api.decline(id = 7, idempotencyKey = "key-decline-1", body = DeclineRequest(reason = "too far"))

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/decline", recorded.path)
        assertEquals("key-decline-1", recorded.getHeader("Idempotency-Key"))
        assertEquals("""{"reason":"too far"}""", recorded.body.readUtf8())
    }

    @Test
    fun `claim sends the idempotency key and returns the assigned shape`() = runTest {
        enqueueAssigned()
        val result = api.claim(id = 7, idempotencyKey = "key-claim-1")

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/claim", recorded.path)
        assertEquals("key-claim-1", recorded.getHeader("Idempotency-Key"))
        assertEquals(true, result.isAssigned)
    }

    @Test
    fun `release sends the idempotency key`() = runTest {
        enqueueAccepted()
        api.release(id = 7, idempotencyKey = "key-release-1")

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/release", recorded.path)
        assertEquals("key-release-1", recorded.getHeader("Idempotency-Key"))
    }

    /**
     * A retry of the SAME command must carry the SAME key (never a fresh one
     * per attempt) — that is what lets the server recognise it as a repeat
     * rather than a second `accept`. [OrderApi] enforces this by making the
     * caller supply the key rather than generating one per call; this asserts
     * the value the caller chose survives unchanged.
     */
    @Test
    fun `a retried accept with the same key is sent as the same key twice`() = runTest {
        enqueueAssigned()
        runCatching { api.accept(id = 7, idempotencyKey = "stable-key") }
        server.takeRequest()

        enqueueAssigned()
        api.accept(id = 7, idempotencyKey = "stable-key")
        val second = server.takeRequest()

        assertEquals("stable-key", second.getHeader("Idempotency-Key"))
    }
}
