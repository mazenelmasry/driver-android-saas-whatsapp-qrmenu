package app.qrmenu.driver.network.interceptors

import app.qrmenu.driver.datastore.TokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Real OkHttp client + mock server, matching [DriverRequestHeadersTest]'s
 * convention — what is asserted is the effect of bytes that actually crossed
 * the wire.
 *
 * The server, not this interceptor, decides whether a session was renewed —
 * `X-Driver-Token-Expires-At` is read verbatim off every successful signed
 * response and mirrored into [TokenStore] only when it actually changed.
 */
class SessionRenewalInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tokenStore(storedExpiresAtMillis: Long?): TokenStore = mockk(relaxed = true) {
        every { this@mockk.expiresAt } returns MutableStateFlow(storedExpiresAtMillis)
    }

    private fun client(tokenStore: TokenStore): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(SessionRenewalInterceptor(tokenStore)).build()

    private fun call(
        client: OkHttpClient,
        signed: Boolean = true,
        tag: PublicEndpoint? = null,
    ) {
        val builder = Request.Builder().url(server.url("/driver/me"))
        if (signed) builder.header("Authorization", "Bearer tok_123")
        if (tag != null) builder.tag(PublicEndpoint::class.java, tag)
        client.newCall(builder.build()).execute().close()
    }

    @Test
    fun `a successful signed response with a DIFFERENT reported expiry touches the store to that value`() {
        // 2026-10-21T09:00:00Z
        server.enqueue(
            MockResponse().setBody("{}").setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store))

        val expected = java.time.Instant.parse("2026-10-21T09:00:00Z").toEpochMilli()
        verify(exactly = 1) { store.touchExpiry(expected) }
    }

    @Test
    fun `a reported expiry equal to what is already stored touches nothing`() {
        val already = java.time.Instant.parse("2026-10-21T09:00:00Z").toEpochMilli()
        server.enqueue(
            MockResponse().setBody("{}").setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = already)

        call(client(store))

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `a response with no expiry header touches nothing`() {
        server.enqueue(MockResponse().setBody("{}"))
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store))

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `an unparseable expiry header touches nothing rather than storing garbage`() {
        server.enqueue(
            MockResponse().setBody("{}").setHeader("X-Driver-Token-Expires-At", "not-a-date"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store))

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `a failed response never touches the store, even with an expiry header`() {
        server.enqueue(
            MockResponse().setResponseCode(500).setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store))

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `a 401 never touches the store`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store))

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `an unsigned request is never synced, even with an expiry header on the response`() {
        server.enqueue(
            MockResponse().setBody("{}").setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store), signed = false)

        verify(exactly = 0) { store.touchExpiry(any()) }
    }

    @Test
    fun `a public-tagged request is never synced`() {
        server.enqueue(
            MockResponse().setBody("{}").setHeader("X-Driver-Token-Expires-At", "2026-10-21T09:00:00Z"),
        )
        val store = tokenStore(storedExpiresAtMillis = 0L)

        call(client(store), tag = PublicEndpoint.INSTANCE)

        verify(exactly = 0) { store.touchExpiry(any()) }
    }
}
