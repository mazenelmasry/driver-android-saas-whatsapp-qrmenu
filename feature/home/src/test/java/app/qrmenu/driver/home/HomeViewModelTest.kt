package app.qrmenu.driver.home

import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.LinkedCompanyDto
import app.qrmenu.driver.network.dto.MeResponse
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.errors.DriverApiError
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authApi = mockk()
        tokenStore = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val someDriver = DriverDto(
        id = 1,
        name = "Ahmed",
        phone = "+966501234567",
        isActive = true,
        isOnline = false,
    )

    private fun linkFor(companyId: Long, status: String) = RestaurantLinkDto(
        company = LinkedCompanyDto(id = companyId, name = "Company $companyId"),
        status = status,
    )

    // loading → loaded

    @Test
    fun `state starts loading then loads the driver and their restaurants`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = someDriver,
            restaurants = listOf(linkFor(1, "active")),
        )

        val viewModel = HomeViewModel(authApi, tokenStore)

        assertTrue("the very first state must be loading, never empty by default", viewModel.state.value.isLoading)

        dispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.state.value
        assertFalse(loaded.isLoading)
        assertEquals(someDriver, loaded.driver)
        assertEquals(1, loaded.restaurants.size)
        assertNull(loaded.error)
    }

    // error → retry succeeds

    @Test
    fun `a failed load can be retried and recovers`() = runTest(dispatcher) {
        coEvery { authApi.me() } throws IOException("no route to host")

        val viewModel = HomeViewModel(authApi, tokenStore)
        dispatcher.scheduler.advanceUntilIdle()

        val failed = viewModel.state.value
        assertFalse(failed.isLoading)
        assertNull(failed.driver)
        assertEquals(DriverApiError.Offline, failed.error)

        // The restaurant is now reachable — the exact same call the retry
        // button drives.
        coEvery { authApi.me() } returns MeResponse(driver = someDriver, restaurants = emptyList())
        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        val recovered = viewModel.state.value
        assertNull(recovered.error)
        assertEquals(someDriver, recovered.driver)
    }

    // empty stays empty

    @Test
    fun `zero restaurants is rendered as the empty state, not an error`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(driver = someDriver, restaurants = emptyList())

        val viewModel = HomeViewModel(authApi, tokenStore)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isEmpty)
        assertNull(state.error)
        assertTrue(state.restaurants.isEmpty())
    }

    // sign-out clears the session even when the network throws

    @Test
    fun `signing out clears the session even when logout fails on the network`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(driver = someDriver, restaurants = emptyList())
        coEvery { authApi.logout() } throws IOException("offline")
        // TokenStore.clear() returns Unit; declared explicitly even though the
        // mock is relaxed, so the verification below checks real behaviour,
        // not a relaxed no-op that would pass regardless.
        every { tokenStore.clear() } just Runs

        val viewModel = HomeViewModel(authApi, tokenStore)
        dispatcher.scheduler.advanceUntilIdle()

        var signedOut = false
        viewModel.signOut { signedOut = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authApi.logout() }
        verify(exactly = 1) { tokenStore.clear() }
        assertTrue("onSignedOut must fire even though the network call threw", signedOut)
    }
}
