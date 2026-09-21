package app.qrmenu.driver.offers

import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.DeviceTokenRequest
import app.qrmenu.driver.push.PushTokenProvider
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeviceTokenRegistrarTest {

    private val authApi = mockk<AuthApi>()
    private val tokenStore = mockk<TokenStore>()
    private val pushTokenProvider = mockk<PushTokenProvider>()

    init {
        // `android.util.Log` is not implemented in a JVM unit test and throws
        // "not mocked" — and the ONLY call site here is the failure branch,
        // i.e. exactly the behaviour the retry test exists to prove.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    private fun registrar() = DeviceTokenRegistrar(authApi, tokenStore, pushTokenProvider)

    /**
     * Both `DriverApplication` (process start) and `MainActivity` (the
     * `signedIn` flag turning true) call this on a normal cold start, which
     * sent two identical POSTs per launch on the S25. The server no-ops a
     * repeat, so nothing broke — but it is a wasted round trip on a driver's
     * mobile data every single time they open the app.
     */
    @Test
    fun `registering the same token twice in one process only calls the server once`() = runTest {
        coEvery { tokenStore.hasValidSession(any()) } returns true
        coEvery { pushTokenProvider.currentToken() } returns "fcm-token-1"
        coEvery { authApi.registerDeviceToken(any()) } returns AcceptedDto(ok = true)

        val registrar = registrar()
        registrar.registerCurrentToken()
        registrar.registerCurrentToken()

        coVerify(exactly = 1) { authApi.registerDeviceToken(DeviceTokenRequest("fcm-token-1")) }
    }

    /**
     * The memo must never outlive the driver who set it: the NEXT driver on
     * this phone shares the same FCM registration token, and their `register`
     * call is what STEALS it from the previous driver's row server-side. A
     * memo that survived sign-out would suppress exactly that call and leave
     * the previous driver receiving this one's offers.
     */
    @Test
    fun `after sign-out the same token is registered again for the next driver`() = runTest {
        coEvery { tokenStore.hasValidSession(any()) } returns true
        coEvery { pushTokenProvider.currentToken() } returns "fcm-token-1"
        coEvery { authApi.registerDeviceToken(any()) } returns AcceptedDto(ok = true)

        val registrar = registrar()
        registrar.registerCurrentToken()
        registrar.forgetLocalState()
        registrar.registerCurrentToken()

        coVerify(exactly = 2) { authApi.registerDeviceToken(DeviceTokenRequest("fcm-token-1")) }
    }

    /** A rotated token is a different token — the memo must not swallow it. */
    @Test
    fun `a rotated token is registered even though an earlier one succeeded`() = runTest {
        coEvery { tokenStore.hasValidSession(any()) } returns true
        coEvery { pushTokenProvider.currentToken() } returns "fcm-token-1"
        coEvery { authApi.registerDeviceToken(any()) } returns AcceptedDto(ok = true)

        val registrar = registrar()
        registrar.registerCurrentToken()
        registrar.onTokenRefreshed("fcm-token-2")

        coVerify(exactly = 1) { authApi.registerDeviceToken(DeviceTokenRequest("fcm-token-2")) }
    }

    /**
     * A failed registration must NOT be memoised — the next app start is the
     * retry, and a memo would turn one dropped call into a phone that never
     * receives a push again for the life of the install.
     */
    @Test
    fun `a failed registration is retried rather than memoised`() = runTest {
        coEvery { tokenStore.hasValidSession(any()) } returns true
        coEvery { pushTokenProvider.currentToken() } returns "fcm-token-1"
        coEvery { authApi.registerDeviceToken(any()) } throws RuntimeException("offline")

        val registrar = registrar()
        registrar.registerCurrentToken()
        registrar.registerCurrentToken()

        coVerify(exactly = 2) { authApi.registerDeviceToken(DeviceTokenRequest("fcm-token-1")) }
    }
}
