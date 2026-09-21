package app.qrmenu.driver.availability

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.api.AvailabilityApi
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AvailabilityRequest
import app.qrmenu.driver.network.dto.AvailabilityResponse
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.MeResponse
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var authApi: AuthApi
    private lateinit var availabilityApi: AvailabilityApi
    private lateinit var orderApi: OrderApi

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authApi = mockk()
        availabilityApi = mockk()
        // The "why no orders" context is an explanation this screen fetches
        // for itself; every test here is about the switch, so it is stubbed to
        // fail silently — which is exactly how the screen treats it.
        orderApi = mockk()
        coEvery { orderApi.available() } throws IOException("no context in these tests")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun httpError(status: Int, body: String): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private val offlineDriver = DriverDto(
        id = 1,
        name = "Ahmed",
        phone = "+966501234567",
        isActive = true,
        isOnline = false,
        onlineSince = null,
    )

    private fun viewModel(): AvailabilityViewModel = AvailabilityViewModel(authApi, availabilityApi, orderApi)

    // ── initial load ─────────────────────────────────────────────────────

    @Test
    fun `the initial state is the server's, not a local guess`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = offlineDriver.copy(isOnline = true, onlineSince = "2026-01-01T10:00:00Z"),
        )

        val model = viewModel()
        assertTrue("the very first state must be loading, never a green switch by default", model.state.value.isLoading)

        dispatcher.scheduler.advanceUntilIdle()

        val loaded = model.state.value
        assertFalse(loaded.isLoading)
        assertTrue(loaded.isOnline)
        assertEquals("2026-01-01T10:00:00Z", loaded.onlineSince)
    }

    // ── the toggle never lies ────────────────────────────────────────────

    /**
     * Turning on succeeds: the confirmed [AvailabilityResponse] — not the
     * boolean the driver tapped — is what lands in state, and pending clears.
     */
    @Test
    fun `toggling online succeeds and reflects exactly what the server confirmed`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(driver = offlineDriver)
        coEvery { availabilityApi.setAvailability(AvailabilityRequest(online = true)) } returns
            AvailabilityResponse(isOnline = true, onlineSince = "2026-01-01T12:00:00Z")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.state.value.isOnline)

        model.onToggle(true)
        // Immediately after the call returns (before the coroutine resumes),
        // the switch must not have moved yet — only `isPending` may have.
        assertTrue(model.state.value.isPending)
        assertFalse("must not flip optimistically before the server answers", model.state.value.isOnline)

        dispatcher.scheduler.advanceUntilIdle()

        val settled = model.state.value
        assertFalse(settled.isPending)
        assertTrue(settled.isOnline)
        assertEquals("2026-01-01T12:00:00Z", settled.onlineSince)
        coVerify(exactly = 1) { availabilityApi.setAvailability(AvailabilityRequest(online = true)) }
    }

    /**
     * `has_active_trip` (409) is the server refusing to let the driver go dark
     * mid-delivery — this must leave the switch exactly where it was (still
     * available, since a driver mid-trip is available by definition) and name
     * the specific reason, not a generic failure.
     */
    @Test
    fun `a 409 has_active_trip leaves the switch untouched and names the reason`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = offlineDriver.copy(isOnline = true, onlineSince = "2026-01-01T09:00:00Z"),
        )
        coEvery { availabilityApi.setAvailability(AvailabilityRequest(online = false)) } throws
            httpError(409, """{"code":"has_active_trip","message":"Finish your trip first."}""")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.state.value.isOnline)

        model.onToggle(false)
        dispatcher.scheduler.advanceUntilIdle()

        val result = model.state.value
        assertFalse(result.isPending)
        assertTrue("a request the server refused must not flip the confirmed state", result.isOnline)
        assertTrue(result.hasActiveTripBlock)
        assertEquals(DriverErrorCode.HasActiveTrip, (result.error as DriverApiError.Api).code)
    }

    /**
     * A dropped connection must not leave the app claiming a state the server
     * never confirmed — same rule as the 409 case, different cause.
     */
    @Test
    fun `a network failure during toggle does not claim a state the server never confirmed`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(driver = offlineDriver)
        coEvery { availabilityApi.setAvailability(any()) } throws IOException("no route to host")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onToggle(true)
        dispatcher.scheduler.advanceUntilIdle()

        val result = model.state.value
        assertFalse(result.isPending)
        assertFalse("must stay OFF — the server never said yes", result.isOnline)
        assertEquals(DriverApiError.Offline, result.error)
    }

    // ── the server can flip this out from under the driver ──────────────

    /**
     * The branch closing, or a silent 3-minute heartbeat, turns availability
     * off server-side with no tap involved — a fresh `/driver/me` read (e.g.
     * on `refresh`/re-entry) must surface that truth.
     */
    @Test
    fun `the server turning availability off underneath is reflected on the next load`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = offlineDriver.copy(isOnline = true, onlineSince = "2026-01-01T08:00:00Z"),
        )
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.state.value.isOnline)

        coEvery { authApi.me() } returns MeResponse(driver = offlineDriver.copy(isOnline = false, onlineSince = null))
        model.retry()
        dispatcher.scheduler.advanceUntilIdle()

        val result = model.state.value
        assertFalse(result.isOnline)
        assertNull(result.onlineSince)
    }

    // ── re-entrancy ───────────────────────────────────────────────────────

    @Test
    fun `a second toggle while one is already in flight is ignored`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(driver = offlineDriver)
        coEvery { availabilityApi.setAvailability(any()) } returns
            AvailabilityResponse(isOnline = true, onlineSince = "2026-01-01T12:00:00Z")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onToggle(true)
        model.onToggle(false) // ignored — a request is already in flight
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { availabilityApi.setAvailability(any()) }
    }
}
