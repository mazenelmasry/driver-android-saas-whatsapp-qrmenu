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

    // ── «خُذ الطلب» ─────────────────────────────────────────────────────
    //
    // 🔴 These drive the VIEWMODEL, which is what the screen's button calls —
    // not the repository or the API directly. The whole reason this feature
    // was missing for ten weeks is that the backend's `claim` was fully built
    // and fully tested while NOTHING on the phone could reach it, and a test
    // that calls the service itself would have stayed green throughout
    // (CLAUDE.md, «اختبار يستدعى الخدمة لا يُثبت شيئاً عن وصول المستخدم إليها»).

    @Test
    fun `claiming an order opens its trip and drops it from the available list`() = runTest(dispatcher) {
        val open = order(id = 31)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(open), context = defaultContext)
        coEvery { orderApi.claim(31, any()) } returns open

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(open), model.state.value.available.orders)

        var openedTrip: Long? = null
        model.claim(31) { openedTrip = it }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("a won claim must land the driver on the trip", 31L, openedTrip)
        assertNull(model.state.value.claimingOrderId)
        assertNull("winning is not a message", model.state.value.claimMessage)
        assertTrue(
            "the order is the driver's now — leaving it in «المتاحة» invites a second tap",
            model.state.value.available.orders.none { it.id == 31L },
        )
    }

    @Test
    fun `losing the race says so, drops the card, and never opens a trip`() = runTest(dispatcher) {
        val open = order(id = 31)
        // A realistic server: once somebody else holds the order it stops
        // being offered. A stub that kept returning it would be testing a
        // backend that cannot exist — and would hide the fact that the
        // reconciling refresh is deliberately allowed to bring an order BACK,
        // which is exactly what must happen if the winner later releases it.
        var taken = false
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } answers {
            AvailableOrdersResponse(
                data = if (taken) emptyList() else listOf(open),
                context = defaultContext,
            )
        }
        coEvery { orderApi.claim(31, any()) } answers {
            taken = true
            throw httpError(409, """{"code":"already_claimed","message":"Taken"}""")
        }

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue("precondition: the order is on offer", model.state.value.available.orders.any { it.id == 31L })

        var openedTrip: Long? = null
        model.claim(31) { openedTrip = it }
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("a lost race must not navigate anywhere", openedTrip)
        assertEquals(ClaimMessage.Lost, model.state.value.claimMessage)
        assertNull(
            "losing is the ordinary rhythm of self_claim, so it carries no error to render in red",
            model.state.value.claimFailure,
        )
        assertNull(
            "and it must never reach the list's red error banner either",
            model.state.value.available.error,
        )
        assertTrue(
            "somebody else has it — it cannot stay on offer",
            model.state.value.available.orders.none { it.id == 31L },
        )
    }

    @Test
    fun `an order the winner releases is allowed back into the list`() = runTest(dispatcher) {
        // The companion to the test above, and the reason the lost card is
        // dropped from STATE rather than remembered in a "never show again"
        // set: `release` puts an order back on offer, and a driver who lost
        // the first race must be able to win the second.
        val open = order(id = 31)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(open), context = defaultContext)
        coEvery { orderApi.claim(31, any()) } throws
            httpError(409, """{"code":"already_claimed","message":"Taken"}""")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.claim(31) {}
        dispatcher.scheduler.advanceUntilIdle()

        // The stub here NEVER stops offering the order, which is what the
        // server does after a release. The refresh must therefore restore it.
        assertTrue(
            "a released order has to be winnable again",
            model.state.value.available.orders.any { it.id == 31L },
        )
        assertEquals(
            "and the driver is still told they lost the first race",
            ClaimMessage.Lost,
            model.state.value.claimMessage,
        )
    }

    @Test
    fun `a claim that fails for any other reason leaves the order on offer`() = runTest(dispatcher) {
        // Offline is the case that matters: the order is still there to be
        // taken once the driver is back on signal, and removing it would hide
        // work they can still do.
        val open = order(id = 31)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(open), context = defaultContext)
        coEvery { orderApi.claim(31, any()) } throws IOException("no signal")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.claim(31) { throw AssertionError("must not navigate on a failed claim") }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ClaimMessage.Failed, model.state.value.claimMessage)
        assertEquals(DriverApiError.Offline, model.state.value.claimFailure)
        assertTrue(
            "the order is still open — a lost connection is not a lost race",
            model.state.value.available.orders.any { it.id == 31L },
        )
    }

    @Test
    fun `a second tap while a claim is in flight is dropped, not queued`() = runTest(dispatcher) {
        // A driver holds one trip at a time (decision 21), so a queued second
        // claim could only ever end in a refusal the driver never asked for.
        val first = order(id = 31)
        val second = order(id = 32)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns
            AvailableOrdersResponse(data = listOf(first, second), context = defaultContext)
        coEvery { orderApi.claim(31, any()) } returns first

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.claim(31) {}
        // Not advanced: the first claim is still in flight right here.
        assertEquals(31L, model.state.value.claimingOrderId)

        model.claim(32) { throw AssertionError("the second tap must not run") }
        assertEquals(
            "the in-flight claim is untouched by the second tap",
            31L,
            model.state.value.claimingOrderId,
        )

        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { orderApi.claim(32, any()) }
    }

    @Test
    fun `each claim carries its own idempotency key`() = runTest(dispatcher) {
        // Generated once per COMMAND, never per attempt — a driver's re-tap is
        // a new command and must not be collapsed into the previous one by the
        // server's idempotency middleware.
        val open = order(id = 31)
        val keys = mutableListOf<String>()
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(open), context = defaultContext)
        coEvery { orderApi.claim(31, capture(keys)) } throws IOException("no signal")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.claim(31) {}
        dispatcher.scheduler.advanceUntilIdle()
        model.claim(31) {}
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, keys.size)
        assertTrue("two taps are two commands", keys[0] != keys[1])
        assertTrue("and neither may be blank", keys.all { it.isNotBlank() })
    }

    @Test
    fun `starting a new claim clears the previous message`() = runTest(dispatcher) {
        val open = order(id = 31)
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())
        coEvery { orderApi.available() } returns AvailableOrdersResponse(data = listOf(open), context = defaultContext)
        coEvery { orderApi.claim(31, any()) } throws IOException("no signal")

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.claim(31) {}
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ClaimMessage.Failed, model.state.value.claimMessage)

        model.claim(31) {}
        assertNull(
            "a stale sentence about the last attempt must not sit over the new one",
            model.state.value.claimMessage,
        )

        dispatcher.scheduler.advanceUntilIdle()
        model.dismissClaimMessage()
        assertNull(model.state.value.claimMessage)
        assertNull(model.state.value.claimFailure)
    }

}
