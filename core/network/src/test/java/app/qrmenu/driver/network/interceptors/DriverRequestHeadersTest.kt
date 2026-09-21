package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.datastore.AppLocaleStore
import app.qrmenu.driver.datastore.TokenStore
import io.mockk.every
import io.mockk.mockk
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The interceptor chain is exercised through a real OkHttp client against a
 * real (mock) server, so what is asserted is the bytes that would actually
 * leave the phone — not what the code reads like.
 */
class DriverRequestHeadersTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun tokenStore(token: String?): TokenStore = mockk {
        every { this@mockk.token } returns MutableStateFlow(token)
    }

    private fun localeStore(language: String?): AppLocaleStore = mockk {
        every { this@mockk.language } returns MutableStateFlow(language)
    }

    private fun client(token: String?, language: String? = "ar"): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(DriverAuthInterceptor(tokenStore(token)))
            .addInterceptor(LocaleInterceptor(localeStore(language)))
            .addInterceptor(AppVersionInterceptor())
            .build()

    private fun call(client: OkHttpClient, request: Request) {
        server.enqueue(MockResponse().setBody("{}"))
        client.newCall(request).execute().close()
    }

    @Test
    fun `an authenticated call carries the driver bearer token`() {
        call(client("tok_123"), Request.Builder().url(server.url("/driver/me")).build())

        assertEquals("Bearer tok_123", server.takeRequest().getHeader("Authorization"))
    }

    /**
     * `app-version`, `request-otp`, `verify-otp` and `login` are read or called
     * before any session exists. They are marked at the call site with a tag
     * rather than by URL, so renaming a path cannot silently change whether a
     * token is attached.
     */
    @Test
    fun `a public endpoint is never signed, even when a session exists`() {
        val request = Request.Builder()
            .url(server.url("/driver/app-version"))
            .tag(PublicEndpoint::class.java, PublicEndpoint.INSTANCE)
            .build()

        call(client("tok_123"), request)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `no session means no empty Authorization header`() {
        call(client(null), Request.Builder().url(server.url("/driver/me")).build())

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    /**
     * Accept matters as much as Accept-Language: without it Laravel renders an
     * HTML error page for a 401/422/500 and the parser then fails on '<'
     * instead of surfacing the real reason.
     */
    @Test
    fun `the in-app language is sent, not the system one`() {
        call(client("tok", language = "ur"), Request.Builder().url(server.url("/driver/me")).build())

        val recorded = server.takeRequest()
        assertEquals("ur", recorded.getHeader("Accept-Language"))
        assertEquals("application/json", recorded.getHeader("Accept"))
    }

    /**
     * The updater compares INTEGERS. As strings "1.10.0" sorts before "1.9.0",
     * which would lock out the driver running the newest build.
     */
    @Test
    fun `the reported app version is the integer version code`() {
        call(client("tok"), Request.Builder().url(server.url("/driver/me")).build())

        val version = server.takeRequest().getHeader("X-App-Version")
        assertEquals(version, version?.toIntOrNull()?.toString())
    }

    /**
     * A repeat of a trip command must be recognisable by the server, or a lost
     * response on `delivered` credits the fee and the cash to the ledger twice.
     * The key rides as a declared parameter on every command in [OrderApi], so
     * this only checks that the header survives the chain intact.
     */
    @Test
    fun `an idempotency key passes through untouched`() {
        val request = Request.Builder()
            .url(server.url("/driver/orders/7/delivered"))
            .header("Idempotency-Key", "cmd-abc-123")
            .post(ByteArray(0).toRequestBody())
            .build()

        call(client("tok"), request)

        assertEquals("cmd-abc-123", server.takeRequest().getHeader("Idempotency-Key"))
    }
}

/**
 * The DNS retry is driven against a stub chain rather than a server, because
 * what matters is exactly WHICH failures it retries — the OEM power-management
 * failure it exists for, and nothing that could have already reached the server.
 */
class DnsRetryInterceptorTest {

    private fun chain(request: Request, onProceed: (Int) -> Response): Interceptor.Chain =
        object : Interceptor.Chain {
            var attempts = 0
            override fun request(): Request = request
            override fun proceed(request: Request): Response = onProceed(attempts++)
            override fun connection() = null
            override fun call(): okhttp3.Call = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

    private val request = Request.Builder().url("https://example.test/driver/me").build()

    private fun ok(): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("{}".toResponseBody(null))
        .build()

    @Test
    fun `a first-request DNS failure is retried and then succeeds`() {
        var calls = 0
        val response = DnsRetryInterceptor().intercept(
            chain(request) { attempt ->
                calls++
                if (attempt == 0) throw UnknownHostException("Unable to resolve host") else ok()
            },
        )

        assertEquals(200, response.code)
        assertEquals(2, calls)
        response.close()
    }

    /**
     * A timeout can happen AFTER the body was sent. Retrying it blindly would
     * re-submit a `delivered` the server already processed, so it must surface.
     */
    @Test
    fun `a timeout is never retried`() {
        var calls = 0
        val thrown = runCatching {
            DnsRetryInterceptor().intercept(
                chain(request) {
                    calls++
                    throw java.net.SocketTimeoutException("timeout")
                },
            )
        }.exceptionOrNull()

        assertTrue(thrown is java.net.SocketTimeoutException)
        assertEquals(1, calls)
    }

    @Test
    fun `a permanently dead resolver gives up instead of looping`() {
        var calls = 0
        runCatching {
            DnsRetryInterceptor().intercept(
                chain(request) {
                    calls++
                    throw UnknownHostException("still dead")
                },
            )
        }

        assertEquals(4, calls)
    }
}
