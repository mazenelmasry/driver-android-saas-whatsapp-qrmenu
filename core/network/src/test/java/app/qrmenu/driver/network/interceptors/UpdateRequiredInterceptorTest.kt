package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.network.update.ClientUpdateGate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Exercised against a real OkHttp client + mock server, matching
 * [DriverRequestHeadersTest]'s convention: what is asserted is the effect on
 * the client of bytes that actually crossed the wire.
 */
class UpdateRequiredInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var gate: ClientUpdateGate

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        gate = ClientUpdateGate()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val client: OkHttpClient
        get() = OkHttpClient.Builder()
            .addInterceptor(UpdateRequiredInterceptor(gate))
            .build()

    private fun call(request: Request) {
        client.newCall(request).execute().close()
    }

    @Test
    fun `a 426 raises the gate with the reported floor`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(426)
                .setBody("""{"message":"update required","code":"app_update_required","min_version_code":42}"""),
        )

        call(Request.Builder().url(server.url("/driver/orders/7/delivered")).build())

        assertEquals(42, gate.requiredVersionCode.value)
    }

    @Test
    fun `a normal 200 does not raise the gate`() {
        server.enqueue(MockResponse().setBody("{}"))

        call(Request.Builder().url(server.url("/driver/me")).build())

        assertNull(gate.requiredVersionCode.value)
    }

    /**
     * A malformed 426 body must still block the app — a gate that silently
     * stays closed because one field failed to parse is worse than one that
     * blocks with an unknown floor. See [ClientUpdateGate.NO_VERSION_REPORTED].
     */
    @Test
    fun `a 426 with a body that carries no version still raises the gate`() {
        server.enqueue(MockResponse().setResponseCode(426).setBody("not json at all"))

        call(Request.Builder().url(server.url("/driver/me")).build())

        assertEquals(Int.MAX_VALUE, gate.requiredVersionCode.value)
    }

    @Test
    fun `a 426 never clears an auth header on the request that carried it`() {
        // The interceptor must not strip Authorization from the request it
        // forwards — proving it never touches the token store, only the gate.
        server.enqueue(MockResponse().setResponseCode(426).setBody("""{"min_version_code":10}"""))

        call(
            Request.Builder()
                .url(server.url("/driver/orders/7/delivered"))
                .header("Authorization", "Bearer tok_123")
                .build(),
        )

        assertEquals("Bearer tok_123", server.takeRequest().getHeader("Authorization"))
    }
}
