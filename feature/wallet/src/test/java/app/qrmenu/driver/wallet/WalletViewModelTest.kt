package app.qrmenu.driver.wallet

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.api.LedgerApi
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.LedgerEntryDto
import app.qrmenu.driver.network.dto.LedgerResponse
import app.qrmenu.driver.network.dto.LinkedCompanyDto
import app.qrmenu.driver.network.dto.MeResponse
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.dto.SettlementDto
import app.qrmenu.driver.network.dto.SettlementsResponse
import app.qrmenu.driver.network.errors.DriverApiError
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var authApi: AuthApi
    private lateinit var ledgerApi: LedgerApi

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authApi = mockk()
        ledgerApi = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun link(companyId: Long, name: String, currency: String = "SAR"): RestaurantLinkDto =
        RestaurantLinkDto(
            company = LinkedCompanyDto(id = companyId, name = name, currency = currency),
            branches = emptyList(),
            status = "active",
        )

    private fun ledger(companyId: Long, currency: String = "SAR", earnedToday: Double = 10.0): LedgerResponse =
        LedgerResponse(
            companyId = companyId,
            currency = currency,
            earnedToday = earnedToday,
            cashOnHand = 20.0,
            net = -10.0,
            entries = listOf(
                LedgerEntryDto(
                    id = companyId * 100,
                    type = "delivery_fee_earned",
                    amount = 10.0,
                    balanceAfter = -10.0,
                    createdAt = "2026-09-21T10:00:00Z",
                ),
            ),
        )

    private fun viewModel(): WalletViewModel = WalletViewModel(WalletRepository(authApi, ledgerApi))

    // ── the switcher and its default selection ──────────────────────────

    @Test
    fun `restaurants load and the book of the FIRST one loads automatically`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery"), link(2, "Cairo Kitchen", currency = "EGP")),
        )
        coEvery { ledgerApi.ledger(1) } returns ledger(1)

        val model = viewModel()
        assertTrue("must start loading restaurants, not a guessed empty list", model.state.value.restaurantsLoading)
        assertTrue("book must start loading too", model.state.value.book.isLoading)

        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.restaurantsLoading)
        assertEquals(2, state.restaurants.size)
        assertEquals(1L, state.selectedCompanyId)
        assertFalse(state.book.isLoading)
        assertEquals(10.0, state.book.summary?.earnedToday)
        coVerify(exactly = 1) { ledgerApi.ledger(1) }
    }

    @Test
    fun `zero linked restaurants leaves the book untouched and names no company`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = emptyList(),
        )

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.restaurantsLoading)
        assertTrue(state.restaurants.isEmpty())
        assertNull(state.selectedCompanyId)
        coVerify(exactly = 0) { ledgerApi.ledger(any()) }
    }

    @Test
    fun `restaurants failing to load surfaces a retryable error and retry recovers`() = runTest(dispatcher) {
        coEvery { authApi.me() } throws IOException("no route to host")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DriverApiError.Offline, model.state.value.restaurantsError)

        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery")),
        )
        coEvery { ledgerApi.ledger(1) } returns ledger(1)

        model.retryRestaurants()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertNull(state.restaurantsError)
        assertEquals(1, state.restaurants.size)
    }

    // ── the per-restaurant switch: binding rule 2 — never merged, never summed ─

    @Test
    fun `selecting a different restaurant replaces the book, never merges it`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery", "SAR"), link(2, "Cairo Kitchen", "EGP")),
        )
        coEvery { ledgerApi.ledger(1) } returns ledger(1, currency = "SAR", earnedToday = 55.0)
        coEvery { ledgerApi.ledger(2) } returns ledger(2, currency = "EGP", earnedToday = 300.0)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val first = model.state.value
        assertEquals(1L, first.selectedCompanyId)
        assertEquals("SAR", first.book.summary?.currency)
        assertEquals(55.0, first.book.summary?.earnedToday)

        model.selectRestaurant(2)
        // Switching restaurants must show a fresh skeleton for the new book,
        // never the old restaurant's figures held over.
        assertTrue(model.state.value.book.isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val second = model.state.value
        assertEquals(2L, second.selectedCompanyId)
        assertEquals("EGP", second.book.summary?.currency)
        assertEquals(300.0, second.book.summary?.earnedToday)
        // The book holds exactly ONE restaurant's numbers — never a total, and
        // never the previous restaurant's figures bleeding through.
        assertEquals(1, listOfNotNull(second.book.summary).size)
    }

    @Test
    fun `a stale ledger response for an abandoned restaurant is discarded`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery"), link(2, "Cairo Kitchen", "EGP")),
        )
        // company 1's ledger call never completes within this test — company 2
        // resolves first, simulating a slow first request racing a fast switch.
        val neverCompletes = kotlinx.coroutines.CompletableDeferred<LedgerResponse>()
        coEvery { ledgerApi.ledger(1) } coAnswers { neverCompletes.await() }
        coEvery { ledgerApi.ledger(2) } returns ledger(2, currency = "EGP", earnedToday = 300.0)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.selectRestaurant(2)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2L, model.state.value.selectedCompanyId)
        assertEquals("EGP", model.state.value.book.summary?.currency)

        // Company 1's slow response finally lands — it must be dropped, not
        // overwrite the restaurant the driver is now actually looking at.
        neverCompletes.complete(ledger(1, currency = "SAR", earnedToday = 55.0))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2L, model.state.value.selectedCompanyId)
        assertEquals("EGP", model.state.value.book.summary?.currency)
    }

    // ── the book's own error/retry ───────────────────────────────────────

    @Test
    fun `the book failing to load surfaces a retryable error and retry recovers`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery")),
        )
        coEvery { ledgerApi.ledger(1) } throws IOException("timeout")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DriverApiError.Offline, model.state.value.book.error)

        coEvery { ledgerApi.ledger(1) } returns ledger(1)
        model.retryBook()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertNull(state.book.error)
        assertEquals(10.0, state.book.summary?.earnedToday)
    }

    // ── settlements pane ─────────────────────────────────────────────────

    @Test
    fun `opening settlements loads them for the selected restaurant only`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery")),
        )
        coEvery { ledgerApi.ledger(1) } returns ledger(1)
        val settlement = SettlementDto(
            id = 9,
            companyId = 1,
            amount = 40.0,
            currency = "SAR",
            direction = "from_driver",
            createdAt = "2026-09-20T09:00:00Z",
        )
        coEvery { ledgerApi.settlements(1) } returns SettlementsResponse(data = listOf(settlement))

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(WalletPane.Book, model.state.value.pane)

        model.openSettlements()
        assertEquals(WalletPane.Settlements, model.state.value.pane)
        assertTrue(model.state.value.settlements.isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.settlements.isLoading)
        assertEquals(listOf(settlement), state.settlements.settlements)
        coVerify(exactly = 1) { ledgerApi.settlements(1) }

        model.closeSettlements()
        assertEquals(WalletPane.Book, model.state.value.pane)
    }

    @Test
    fun `settlements failing to load surfaces a retryable error and retry recovers`() = runTest(dispatcher) {
        coEvery { authApi.me() } returns MeResponse(
            driver = DriverDto(id = 1, name = "Ali", phone = "+966500000000", isActive = true, isOnline = true),
            restaurants = listOf(link(1, "Lauren Bakery")),
        )
        coEvery { ledgerApi.ledger(1) } returns ledger(1)
        coEvery { ledgerApi.settlements(1) } throws IOException("timeout")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        model.openSettlements()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DriverApiError.Offline, model.state.value.settlements.error)

        coEvery { ledgerApi.settlements(1) } returns SettlementsResponse(data = emptyList())
        model.retrySettlements()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertNull(state.settlements.error)
        assertTrue(state.settlements.settlements.isEmpty())
    }
}
