package app.qrmenu.driver.trip

import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.OfferDto
import app.qrmenu.driver.network.dto.OrderCompanyDto
import app.qrmenu.driver.network.dto.OrderCustomerDto
import app.qrmenu.driver.network.dto.DeliveryAddressDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
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
class OfferViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var orderApi: OrderApi

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        orderApi = mockk()
        // Every test that reaches Content needs this — declared once here so
        // individual tests only override what they care about.
        coEvery { orderApi.ack(any(), any()) } returns AcceptedDto(ok = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun httpError(status: Int, body: String): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private fun offer(id: Long = 1, expiresAt: Instant): DriverOrderDto = DriverOrderDto(
        id = id,
        orderNumber = "0001-AAAA",
        status = "confirmed",
        deliveryMethod = "delivery",
        paymentMethod = "cash",
        paymentStatus = "pending",
        total = 50.0,
        currency = "SAR",
        cashToCollect = 50.0,
        driverFee = 8.0,
        company = OrderCompanyDto(name = "Lauren"),
        branch = BranchDto(id = 1, name = "Al Olaya"),
        offer = OfferDto(id = 9001, expiresAt = expiresAt.toString(), wave = 1),
    )

    private fun assigned(id: Long = 1): DriverOrderDto = DriverOrderDto(
        id = id,
        orderNumber = "0001-AAAA",
        status = "out_for_delivery",
        deliveryMethod = "delivery",
        paymentMethod = "cash",
        paymentStatus = "pending",
        total = 50.0,
        currency = "SAR",
        cashToCollect = 50.0,
        driverFee = 8.0,
        company = OrderCompanyDto(name = "Lauren"),
        branch = BranchDto(id = 1, name = "Al Olaya"),
        customer = OrderCustomerDto(name = "Sultan", phone = "+966500000000"),
        deliveryAddress = DeliveryAddressDto(text = "King Fahd Rd"),
    )

    private fun viewModel(): OfferViewModel = OfferViewModel(OfferRepository(orderApi))

    // ── ack fires exactly once ──────────────────────────────────────────

    @Test
    fun `ack fires exactly once even if start is called again by recomposition`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))

        val model = viewModel()
        model.start(1)
        model.start(1) // a recomposition re-invoking start() must not send a second ack
        model.start(1)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { orderApi.ack(1, any()) }
        // And the order itself was fetched only once too — start() is one-shot end to end.
        coVerify(exactly = 1) { orderApi.order(1) }
    }

    @Test
    fun `the offer stays fully usable even if the ack call fails outright`() = runTest(dispatcher) {
        coEvery { orderApi.ack(any(), any()) } throws java.io.IOException("no route to host")
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        // A dead ack must never block the offer from rendering as content.
        val phase = model.state.value.phase
        assertTrue(phase is OfferPhase.Content)
    }

    // ── loading → content, anchored to the server's expires_at ─────────

    @Test
    fun `loading resolves into content carrying the server's own expiry`() = runTest(dispatcher) {
        val expiresAt = Instant.now().plusSeconds(30)
        coEvery { orderApi.order(1) } returns offer(expiresAt = expiresAt)

        val model = viewModel()
        assertTrue("must start loading, not a guessed empty state", model.state.value.phase is OfferPhase.Loading)

        model.start(1)
        dispatcher.scheduler.runCurrent()

        val content = model.state.value.phase as OfferPhase.Content
        assertEquals(expiresAt, content.order.expiresAt)
        assertFalse(content.isActing)
        assertNull(content.error)
    }

    @Test
    fun `an order with no live offer resolves as expired immediately`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45)).copy(offer = null)

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(OfferPhase.Resolved(OfferOutcome.Expired), model.state.value.phase)
    }

    @Test
    fun `the offer's own GET failing surfaces as a load error, not a blank screen`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } throws java.io.IOException("timeout")

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        assertTrue(model.state.value.phase is OfferPhase.LoadFailed)
    }

    // ── accept maps a 409's CODE, never its message ─────────────────────

    /**
     * 🔴 Reproduces a DEAD SCREEN seen on the S25: a push arrived for an offer
     * the server no longer held, the GET came back `not_your_order`, and the
     * driver was left on an otherwise empty screen holding one red banner —
     * no retry (the code is terminal, so the banner offers none) and no way
     * back. A stale push must close itself, never strand the driver.
     */
    @Test
    fun `a stale push whose offer is gone closes the screen instead of stranding the driver`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } throws httpError(403, """{"code":"not_your_order","message":"..."}""")

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(OfferPhase.Resolved(OfferOutcome.Expired), model.state.value.phase)
    }

    /**
     * The terminal codes are answers, not load failures — each must resolve on
     * the LOAD path too, not only when the driver taps accept.
     */
    @Test
    fun `a terminal code on the GET resolves its own outcome rather than a load error`() = runTest(dispatcher) {
        val cases = listOf(
            "already_claimed" to OfferOutcome.AlreadyClaimed,
            "offer_expired" to OfferOutcome.Expired,
            "order_cancelled" to OfferOutcome.OrderCancelled,
        )

        cases.forEach { (code, expected) ->
            coEvery { orderApi.order(1) } throws httpError(409, """{"code":"$code","message":"..."}""")

            val model = viewModel()
            model.start(1)
            dispatcher.scheduler.runCurrent()

            assertEquals(OfferPhase.Resolved(expected), model.state.value.phase)
        }
    }

    @Test
    fun `already_claimed resolves the screen as AlreadyClaimed, not a generic error banner`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))
        coEvery { orderApi.accept(1, any()) } throws httpError(409, """{"code":"already_claimed","message":"..."}""")

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        model.accept(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(OfferPhase.Resolved(OfferOutcome.AlreadyClaimed), model.state.value.phase)
    }

    @Test
    fun `offer_expired, order_cancelled, too_many_active_orders and cash_limit_exceeded each resolve their own outcome`() =
        runTest(dispatcher) {
            val cases = listOf(
                "offer_expired" to OfferOutcome.Expired,
                "order_cancelled" to OfferOutcome.OrderCancelled,
                "too_many_active_orders" to OfferOutcome.TooManyActiveOrders,
                "cash_limit_exceeded" to OfferOutcome.CashLimitExceeded,
            )

            for ((wireCode, expectedOutcome) in cases) {
                coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))
                coEvery { orderApi.accept(1, any()) } throws httpError(409, """{"code":"$wireCode","message":"..."}""")

                val model = viewModel()
                model.start(1)
                dispatcher.scheduler.runCurrent()
                model.accept(1)
                dispatcher.scheduler.runCurrent()

                assertEquals(
                    "wire code '$wireCode' must resolve as $expectedOutcome",
                    OfferPhase.Resolved(expectedOutcome),
                    model.state.value.phase,
                )
            }
        }

    @Test
    fun `accept succeeding carries the assigned order for a caller to navigate on`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))
        coEvery { orderApi.accept(1, any()) } returns assigned(1)

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()
        model.accept(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(1L, model.state.value.acceptedOrder?.id)
        assertEquals("Sultan", model.state.value.acceptedOrder?.customer?.name)
    }

    @Test
    fun `a retryable accept failure (offline) stays on content as an inline banner, order and countdown untouched`() =
        runTest(dispatcher) {
            val expiresAt = Instant.now().plusSeconds(45)
            coEvery { orderApi.order(1) } returns offer(expiresAt = expiresAt)
            coEvery { orderApi.accept(1, any()) } throws java.io.IOException("timeout")

            val model = viewModel()
            model.start(1)
            dispatcher.scheduler.runCurrent()
            model.accept(1)
            dispatcher.scheduler.runCurrent()

            val content = model.state.value.phase as OfferPhase.Content
            assertEquals(expiresAt, content.order.expiresAt)
            assertFalse(content.isActing)
            assertTrue(content.error != null)
        }

    @Test
    fun `accept while an action is already in flight is ignored`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))
        coEvery { orderApi.accept(1, any()) } returns assigned(1)

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        model.accept(1)
        model.accept(1) // fired again before the first coroutine has even run
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { orderApi.accept(1, any()) }
    }

    // ── decline ──────────────────────────────────────────────────────────

    @Test
    fun `a successful decline resolves the screen as Declined`() = runTest(dispatcher) {
        coEvery { orderApi.order(1) } returns offer(expiresAt = Instant.now().plusSeconds(45))
        coEvery { orderApi.decline(1, any(), any()) } returns AcceptedDto(ok = true)

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        model.decline(1, "too_far")
        dispatcher.scheduler.runCurrent()

        assertEquals(OfferPhase.Resolved(OfferOutcome.Declined), model.state.value.phase)
    }

    // ── expiry resolves the screen on its own ───────────────────────────

    @Test
    fun `the screen resolves itself as Expired the instant the server's countdown runs out`() = runTest(dispatcher) {
        val expiresAt = Instant.now().plusMillis(200)
        coEvery { orderApi.order(1) } returns offer(expiresAt = expiresAt)

        val model = viewModel()
        model.start(1)
        dispatcher.scheduler.runCurrent()

        assertTrue("must be a live offer before the clock runs out", model.state.value.phase is OfferPhase.Content)

        // Fast-forwards the virtual clock through the scheduled expiry delay —
        // deliberately `advanceUntilIdle`, not `runCurrent`, here: this is the
        // one test that WANTS the clock to run out.
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OfferPhase.Resolved(OfferOutcome.Expired), model.state.value.phase)
    }

    @Test
    fun `an offer accepted before its own expiry timer fires is not overwritten back to Expired`() =
        runTest(dispatcher) {
            val expiresAt = Instant.now().plusMillis(500)
            coEvery { orderApi.order(1) } returns offer(expiresAt = expiresAt)
            coEvery { orderApi.accept(1, any()) } returns assigned(1)

            val model = viewModel()
            model.start(1)
            dispatcher.scheduler.runCurrent()

            model.accept(1)
            dispatcher.scheduler.runCurrent()

            // The expiry timer is still scheduled underneath — fast-forward
            // PAST it and confirm it did not clobber a phase accept() already moved on.
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1L, model.state.value.acceptedOrder?.id)
        }
}
