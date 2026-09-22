package app.qrmenu.driver.orders

import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.AvailableOrdersResponse
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.CashHoldBranchDto
import app.qrmenu.driver.network.dto.CashHoldDto
import app.qrmenu.driver.network.dto.DeliveryAddressDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.MyOrdersResponse
import app.qrmenu.driver.network.dto.OrderCompanyDto
import app.qrmenu.driver.network.dto.OrderCustomerDto
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
class OrdersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var orderApi: OrderApi

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        orderApi = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun httpError(status: Int, body: String): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private fun order(
        id: Long = 1,
        customer: OrderCustomerDto? = null,
        deliveryAddress: DeliveryAddressDto? = null,
    ): DriverOrderDto = DriverOrderDto(
        id = id,
        orderNumber = "0001-AAAA",
        status = "assigned",
        deliveryMethod = "delivery",
        paymentMethod = "cash",
        paymentStatus = "pending",
        total = 50.0,
        currency = "SAR",
        cashToCollect = 50.0,
        driverFee = 8.0,
        company = OrderCompanyDto(name = "Lauren"),
        branch = BranchDto(id = 1, name = "Al Olaya"),
        customer = customer,
        deliveryAddress = deliveryAddress,
    )

    private val defaultContext = AvailabilityContextDto(isOnline = true, reason = "nothing_pending")

    private fun viewModel(): OrdersViewModel = OrdersViewModel(OrdersRepository(orderApi))

    // ── the four states, for each list ──────────────────────────────────

    @Test
    fun `mine goes from loading to content`() = runTest(dispatcher) {
        val theOrder = order(id = 7)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(theOrder))
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = emptyList(), context = defaultContext)

        val model = viewModel()
        assertTrue("must start loading, not a guessed empty list", model.state.value.mine.isLoading)

        dispatcher.scheduler.advanceUntilIdle()

        val mine = model.state.value.mine
        assertFalse(mine.isLoading)
        assertNull(mine.error)
        assertEquals(listOf(theOrder), mine.orders)
    }

    @Test
    fun `mine goes from loading to empty and available carries the server's reason`() = runTest(dispatcher) {
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(
            data = emptyList(),
            context = AvailabilityContextDto(isOnline = false, reason = "offline"),
        )

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.mine.isLoading)
        assertNull(state.mine.error)
        assertTrue(state.mine.orders.isEmpty())

        assertFalse(state.available.isLoading)
        assertNull(state.available.error)
        assertTrue(state.available.orders.isEmpty())
        assertEquals("offline", state.availableContext?.reason)
    }

    @Test
    fun `a cash hold survives into state even when available orders are not empty`() = runTest(dispatcher) {
        // The hold only withholds UNPAID CASH orders — card orders for the same
        // branch still arrive, so `available.orders` can be non-empty while the
        // driver is still held. The banner reads `availableContext?.cashHold`
        // directly, independent of `orders`/`reason`, so this proves the DTO
        // (not just an empty-list special case) survives repository → state.
        val cardOrder = order(id = 42)
        val heldContext = AvailabilityContextDto(
            isOnline = true,
            reason = "nothing_pending",
            cashHold = CashHoldDto(
                branches = listOf(
                    CashHoldBranchDto(
                        branchId = 1,
                        branchName = "Al Olaya",
                        companyId = 5,
                        companyName = "Lauren",
                        cashOnHand = 620.0,
                        limit = 500.0,
                        currency = "SAR",
                    ),
                ),
            ),
        )
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(
            data = listOf(cardOrder),
            context = heldContext,
        )

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertTrue("card orders still arrive during a cash hold", state.available.orders.isNotEmpty())
        assertEquals(heldContext.cashHold, state.availableContext?.cashHold)
        assertEquals(1, state.availableContext?.cashHold?.branches?.size)
        assertEquals("Al Olaya", state.availableContext?.cashHold?.branches?.first()?.branchName)
    }

    @Test
    fun `mine goes from loading to error and retry re-calls the API and recovers`() = runTest(dispatcher) {
        coEvery { orderApi.mine() } throws IOException("no route to host")
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = emptyList(), context = defaultContext)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val failed = model.state.value.mine
        assertFalse(failed.isLoading)
        assertEquals(DriverApiError.Offline, failed.error)
        assertTrue(failed.orders.isEmpty())

        val recoveredOrder = order(id = 9)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(recoveredOrder))

        model.retry(OrdersTab.Mine)
        dispatcher.scheduler.advanceUntilIdle()

        val recovered = model.state.value.mine
        assertFalse(recovered.isLoading)
        assertNull(recovered.error)
        assertEquals(listOf(recoveredOrder), recovered.orders)
        coVerify(exactly = 2) { orderApi.mine() }
    }

    @Test
    fun `available goes from loading to error and retry re-calls the API and recovers`() = runTest(dispatcher) {
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } throws httpError(500, """{"code":"server_error"}""")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val failed = model.state.value.available
        assertFalse(failed.isLoading)
        assertTrue(failed.error is DriverApiError.Api)
        assertTrue(failed.orders.isEmpty())

        coEvery { orderApi.available() } returns AvailableOrdersResponse(
            data = listOf(order(id = 3)),
            context = defaultContext,
        )

        model.retry(OrdersTab.Available)
        dispatcher.scheduler.advanceUntilIdle()

        val recovered = model.state.value.available
        assertFalse(recovered.isLoading)
        assertNull(recovered.error)
        assertEquals(1, recovered.orders.size)
        coVerify(exactly = 2) { orderApi.available() }
    }

    // ── the two tabs never block each other ─────────────────────────────

    @Test
    fun `an error on available does not blank out mine, and vice versa`() = runTest(dispatcher) {
        val minesOrder = order(id = 5)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(minesOrder))
        coEvery { orderApi.available() } throws IOException("timeout")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        // "المتاحة" failed …
        assertEquals(DriverApiError.Offline, state.available.error)
        assertTrue(state.available.orders.isEmpty())
        // … but "طلباتى" is untouched: loaded, no error, its order intact.
        assertNull(state.mine.error)
        assertFalse(state.mine.isLoading)
        assertEquals(listOf(minesOrder), state.mine.orders)
    }

    @Test
    fun `an error on mine does not blank out available, and vice versa`() = runTest(dispatcher) {
        val offer = order(id = 6)
        coEvery { orderApi.mine() } throws httpError(500, """{"code":"server_error"}""")
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(offer), context = defaultContext)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.mine.error is DriverApiError.Api)
        assertTrue(state.mine.orders.isEmpty())

        assertNull(state.available.error)
        assertFalse(state.available.isLoading)
        assertEquals(listOf(offer), state.available.orders)
    }

    // ── refreshQuietly never flips content back into the skeleton ───────

    @Test
    fun `refreshQuietly does not flip a loaded list back into loading`() = runTest(dispatcher) {
        val firstOrder = order(id = 1)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(firstOrder))
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = emptyList(), context = defaultContext)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(model.state.value.mine.isLoading)

        val secondOrder = order(id = 2)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(secondOrder))

        model.refreshQuietly()
        // Immediately after the call returns (before the coroutine resumes),
        // the list must never have been put back into the skeleton state.
        assertFalse(
            "a quiet refresh must never show a skeleton for content already on screen",
            model.state.value.mine.isLoading,
        )

        dispatcher.scheduler.advanceUntilIdle()

        val refreshed = model.state.value.mine
        assertFalse(refreshed.isLoading)
        assertEquals(listOf(secondOrder), refreshed.orders)
    }

    @Test
    fun `refreshQuietly updates the available context and orders without a skeleton flash`() = runTest(dispatcher) {
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(
            data = emptyList(),
            context = AvailabilityContextDto(isOnline = true, reason = "nothing_pending"),
        )

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val fresh = order(id = 11)
        coEvery { orderApi.available() } returns AvailableOrdersResponse(
            data = listOf(fresh),
            context = AvailabilityContextDto(isOnline = true, reason = null),
        )

        model.refreshQuietly()
        assertFalse(model.state.value.available.isLoading)
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.available.isLoading)
        assertEquals(listOf(fresh), state.available.orders)
        assertNull(state.availableContext?.reason)
    }

    // ── decision 23: the offered shape cannot carry what it has no field for ─

    @Test
    fun `an available-list response that wrongly carries a customer still yields a populated offered state`() =
        runTest(dispatcher) {
            // The server slipping: an "available" (pre-acceptance) order that
            // nonetheless carries customer + delivery address on the wire.
            val leaking = order(
                id = 21,
                customer = OrderCustomerDto(name = "Sara", phone = "+966500000000"),
                deliveryAddress = DeliveryAddressDto(text = "123 King Fahd Rd"),
            )
            coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
            coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(leaking), context = defaultContext)

            val model = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            val available = model.state.value.available
            assertFalse(available.isLoading)
            assertNull(available.error)
            assertEquals(1, available.orders.size)

            // OfferedOrderSummary has no field that could hold a customer or an
            // address at all — the mapping either succeeds (offer data intact)
            // or it doesn't compile. We assert it succeeded and is fully usable.
            val summary = available.orders.single().toOfferedSummary()
            assertEquals(21L, summary.id)
            assertEquals("Lauren", summary.companyName)
            assertEquals("Al Olaya", summary.branchName)
        }

    @Test
    fun `toOfferedSummary never reads customer or delivery address`() {
        val withLeakedFields = order(
            id = 40,
            customer = OrderCustomerDto(name = "Sara", phone = "+966500000000"),
            deliveryAddress = DeliveryAddressDto(text = "123 King Fahd Rd"),
        )
        val withoutThem = withLeakedFields.copy(customer = null, deliveryAddress = null)

        // If toOfferedSummary() read `customer`/`deliveryAddress` at all, the two
        // resulting summaries would differ (id is the same in both). They must
        // be exactly equal — proof the mapping never touched those fields.
        assertEquals(withoutThem.toOfferedSummary(), withLeakedFields.toOfferedSummary())
    }

    // ── decision 21: mine never holds more than one trip ────────────────

    /**
     * Decision 21 is enforced on BOTH sides: the contract caps `mine` at one
     * item, and [OrdersRepository.mine] trims anyway. A server that regressed
     * and sent two would otherwise put a driver on a screen showing two trips
     * they cannot both run.
     */
    @Test
    fun `mine keeps a single trip even if the server sends two`() = runTest(dispatcher) {
        val first = order(id = 100)
        val second = order(id = 101)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(first, second))
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = emptyList(), context = defaultContext)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        // Decision 21: one trip at a time. A second would put two answers
        // behind "the order I am on" — the repository trims it defensively
        // rather than trusting the server to never regress.
        val mine = model.state.value.mine
        assertEquals(listOf(first), mine.orders)
    }
}
