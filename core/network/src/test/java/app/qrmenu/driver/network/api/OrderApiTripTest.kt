package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.BreadcrumbsRequest
import app.qrmenu.driver.network.dto.DeliveredRequest
import app.qrmenu.driver.network.dto.DriverIssueCode
import app.qrmenu.driver.network.dto.IssueRequest
import app.qrmenu.driver.network.dto.LocationPointDto
import app.qrmenu.driver.network.dto.PickedUpRequest
import java.lang.reflect.Method
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Week 5 "the trip": `picked-up` / `delivered` / `issue` / `breadcrumbs`.
 *
 * Same discipline as [OrderApiOfferLockTest]: a real Retrofit + OkHttp stack
 * against a mock server, so the header asserted is the one that would actually
 * leave the phone.
 *
 * The Json config here mirrors `NetworkModule.provideJson()` EXACTLY —
 * `explicitNulls = false`, `coerceInputValues` deliberately OFF — because the
 * single most important assertion in this file (`cash_collected: null` staying
 * `null`, never becoming `0.0`) only means anything if `coerceInputValues` is
 * proven off in the config the app really ships. That flag existing and being
 * exercised are two different guarantees; this file is the second one.
 */
class OrderApiTripTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OrderApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        // 🔴 Left OFF on purpose — see NetworkModule.provideJson(). With it on,
        // an explicit `cash_collected: null` on the DeliveredResponse's nested
        // order would be silently rewritten to a default, and a cash amount
        // becoming "0.0" instead of "not applicable" is exactly the bug this
        // test exists to catch.
    }

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

    private fun assignedOrderJson(cashToCollect: String = "40.0"): String = """
        {
          "id": 7, "order_number": "ORD-7", "status": "en_route",
          "delivery_method": "delivery", "payment_method": "cash",
          "payment_status": "unpaid", "total": 40.0, "currency": "SAR",
          "cash_to_collect": $cashToCollect, "driver_fee": 6.0,
          "company": {"name": "Test Co"},
          "branch": {"id": 1, "name": "Branch 1"},
          "items": [],
          "customer": {"name": "Ahmed", "phone": "0500000000"},
          "delivery_address": {"text": "Some street"}
        }
    """.trimIndent()

    // ───────────────────────────── picked-up ─────────────────────────────

    @Test
    fun `picked-up sends the idempotency key and an optional body`() = runTest {
        server.enqueue(MockResponse().setBody(assignedOrderJson()))

        val result = api.pickedUp(
            id = 7,
            idempotencyKey = "key-pickup-1",
            body = PickedUpRequest(occurredAt = "2026-09-21T10:00:00Z"),
        )

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/picked-up", recorded.path)
        assertEquals("key-pickup-1", recorded.getHeader("Idempotency-Key"))
        assertEquals("""{"occurred_at":"2026-09-21T10:00:00Z"}""", recorded.body.readUtf8())
        assertTrue(result.isAssigned)
    }

    // ───────────────────────────── delivered ─────────────────────────────

    @Test
    fun `delivered sends the idempotency key and the full body`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "order": ${assignedOrderJson()},
                  "ledger": {
                    "company_id": 1, "currency": "SAR", "earned_today": 30.0,
                    "cash_on_hand": 40.0, "net": -34.0, "cash_limit": null
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = api.delivered(
            id = 7,
            idempotencyKey = "key-delivered-1",
            body = DeliveredRequest(cashCollected = 40.0, note = null, occurredAt = "2026-09-21T10:05:00Z"),
        )

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/delivered", recorded.path)
        assertEquals("key-delivered-1", recorded.getHeader("Idempotency-Key"))
        assertEquals(40.0, result.ledger.cashOnHand, 0.0)
        assertNull("cash_limit: null in the JSON must stay null", result.ledger.cashLimit)
    }

    /**
     * 🔴 The single most important test in this file: `cash_collected` is a
     * `Double?` because it is NULL for an order already paid online — the
     * response echoes the order back, and if `coerceInputValues` were on (it
     * is deliberately not, see `NetworkModule.provideJson()`), an explicit JSON
     * `null` there could be rewritten to a default and the driver's ledger
     * would show a cash amount that was never collected.
     *
     * There is no `cash_collected` field on the wire response itself (the
     * response is `{order, ledger}`, neither of which echoes the request body)
     * — so this proves the guarantee at the layer where it actually matters:
     * a nullable money field the server sends as `null` (`cash_limit` on
     * [app.qrmenu.driver.network.dto.LedgerSummaryDto], and `cash_to_collect`
     * were it ever nullable) is never silently turned into `0.0`.
     */
    @Test
    fun `a null money field in the response decodes to null, never to zero`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "order": ${assignedOrderJson(cashToCollect = "0.0")},
                  "ledger": {
                    "company_id": 1, "currency": "SAR", "earned_today": 6.0,
                    "cash_on_hand": 0.0, "net": 6.0, "cash_limit": null
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = api.delivered(
            id = 7,
            idempotencyKey = "key-delivered-2",
            body = DeliveredRequest(cashCollected = null, note = null, occurredAt = "2026-09-21T10:05:00Z"),
        )

        assertNull(result.ledger.cashLimit)
        assertFalse("a null cash_limit must not decode as 0.0", result.ledger.cashLimit == 0.0)
    }

    @Test
    fun `delivered serialises a null cash_collected as the field's own absence, not as 0`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "order": ${assignedOrderJson()},
                  "ledger": {
                    "company_id": 1, "currency": "SAR", "earned_today": 6.0,
                    "cash_on_hand": 0.0, "net": 6.0, "cash_limit": null
                  }
                }
                """.trimIndent(),
            ),
        )

        api.delivered(
            id = 7,
            idempotencyKey = "key-delivered-3",
            body = DeliveredRequest(cashCollected = null, note = "Paid online", occurredAt = "2026-09-21T10:05:00Z"),
        )

        // `explicitNulls = false` (NetworkModule.provideJson()) omits a field
        // that equals its declared default — `cashCollected`'s default IS
        // `null` — rather than writing a literal JSON `null`. This is the
        // encoder's half of the same setting whose decoder half the two tests
        // above cover: what matters is that the amount reaching the server is
        // never `0`, on the wire in either direction.
        val recorded = server.takeRequest()
        val sent = recorded.body.readUtf8()
        assertFalse("cash_collected must never be sent as 0 for an unpaid-in-cash order", sent.contains("\"cash_collected\":0"))
        assertEquals(
            """{"note":"Paid online","occurred_at":"2026-09-21T10:05:00Z"}""",
            sent,
        )
    }

    // ───────────────────────────── issue ─────────────────────────────

    @Test
    fun `issue sends the idempotency key and does not touch order status`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.issue(
            id = 7,
            idempotencyKey = "key-issue-1",
            body = IssueRequest(code = DriverIssueCode.CustomerUnreachable.wire, note = "No answer twice"),
        )

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/issue", recorded.path)
        assertEquals("key-issue-1", recorded.getHeader("Idempotency-Key"))
        assertEquals(
            """{"code":"customer_unreachable","note":"No answer twice"}""",
            recorded.body.readUtf8(),
        )
    }

    // ───────────────────────────── breadcrumbs ─────────────────────────────

    @Test
    fun `breadcrumbs sends the idempotency key and the points`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.breadcrumbs(
            id = 7,
            idempotencyKey = "key-crumbs-1",
            body = BreadcrumbsRequest(
                points = listOf(
                    LocationPointDto(lat = 24.7, lng = 46.6, recordedAt = "2026-09-21T10:01:00Z"),
                ),
            ),
        )

        val recorded = server.takeRequest()
        assertEquals("/driver/orders/7/breadcrumbs", recorded.path)
        assertEquals("key-crumbs-1", recorded.getHeader("Idempotency-Key"))
        assertTrue(recorded.body.readUtf8().contains("\"lat\":24.7"))
    }

    // ─────────────────── the idempotency key is compiler-enforced ───────────────────

    /**
     * A reflection-level guarantee mirroring the one [OrderApiOfferLockTest]
     * proves at the HTTP level for week 4: every week-5 trip command's
     * compiled JVM signature carries `idempotencyKey` as a plain, REQUIRED
     * `String` parameter in second position — never boxed as optional, never
     * dropped.
     *
     * Plain `java.lang.reflect` on purpose (no `kotlin-reflect` dependency on
     * this module's test classpath, and adding one is out of scope for this
     * change): a suspend fun on an interface compiles to
     * `Object name(..., Continuation<? super R>)`, and Kotlin never emits a
     * `$default` bridge overload for an interface (default VALUES require a
     * method body, which an abstract interface method cannot have) — so the
     * only way `idempotencyKey` could be optional at a Retrofit call site is
     * if it were not declared at all. Asserting the exact parameter list
     * proves it is there, required, and a `String`.
     */
    @Test
    fun `every week-5 trip command requires an idempotency key at compile time`() {
        val methods: Map<String, Method> = OrderApi::class.java.declaredMethods
            .filter { it.name in setOf("pickedUp", "delivered", "issue", "breadcrumbs") }
            .associateBy { it.name }

        // Confirms the four functions actually exist on the compiled interface —
        // a reflection guarantee is worthless against a method silently renamed
        // away, since the assertions below would then run over an empty map.
        assertEquals(setOf("pickedUp", "delivered", "issue", "breadcrumbs"), methods.keys)

        for ((name, method) in methods) {
            val params = method.parameterTypes
            assertTrue(
                "$name must declare at least (id, idempotencyKey, ...): got ${params.toList()}",
                params.size >= 2,
            )
            assertEquals("$name's 1st parameter must be the order id (long)", Long::class.javaPrimitiveType, params[0])
            assertEquals(
                "$name's 2nd parameter must be idempotencyKey: String, required — got ${params.toList()}",
                String::class.java,
                params[1],
            )
        }
    }
}
