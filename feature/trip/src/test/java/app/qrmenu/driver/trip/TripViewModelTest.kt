package app.qrmenu.driver.trip

import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.trip.outbox.OutboxFlushScheduler
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DeliveredRequest
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DeliveryAddressDto
import app.qrmenu.driver.network.dto.DriverIssueCode
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.LedgerSummaryDto
import app.qrmenu.driver.network.dto.OrderCompanyDto
import app.qrmenu.driver.network.dto.OrderCustomerDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

/**
 * An in-memory stand-in for the real Room DAO — this module has no Room test
 * dependency, and none of these tests need one: everything under test is
 * [TripRepository]'s own decisions about when a row is queued, acknowledged
 * (deleted) or left for a retry, which this fake reproduces exactly against
 * plain in-memory state. `IGNORE` conflict semantics are reproduced in
 * [enqueue] since [DriverActionOutboxDaoTest] (the real DAO's own test)
 * documents that as load-bearing: a re-enqueue of an already-queued key must
 * not clobber its `attempts`/`last_error`.
 */
private class FakeDriverActionOutboxDao : DriverActionOutboxDao {
    private val rows = MutableStateFlow<List<DriverActionOutboxEntity>>(emptyList())

    val current: List<DriverActionOutboxEntity> get() = rows.value

    override suspend fun enqueue(action: DriverActionOutboxEntity) {
        if (rows.value.any { it.idempotency_key == action.idempotency_key }) return
        rows.value = rows.value + action
    }

    override fun observePending(): Flow<List<DriverActionOutboxEntity>> = rows

    override fun observePendingCount(): Flow<Int> = rows.map { it.size }

    override suspend fun acknowledge(idempotencyKey: String) {
        rows.value = rows.value.filterNot { it.idempotency_key == idempotencyKey }
    }

    override suspend fun recordFailure(idempotencyKey: String, error: String?) {
        rows.value = rows.value.map {
            if (it.idempotency_key == idempotencyKey) it.copy(attempts = it.attempts + 1, last_error = error) else it
        }
    }
}

/**
 * Week 5 — the trip screen's state machine.
 *
 * Same shape as [OfferViewModelTest]: a mocked [OrderApi] behind
 * [TripRepository], `StandardTestDispatcher` for deterministic coroutine
 * ordering, and assertions on [TripViewModel.state] rather than on any
 * Compose layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {

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

    private fun assignedOrder(
        id: Long = 7,
        status: String = "ready",
        pickedUpAt: String? = null,
        cashToCollect: Double = 40.0,
        requiresDeliveryCode: Boolean = false,
    ): DriverOrderDto = DriverOrderDto(
        id = id,
        orderNumber = "0007-AAAA",
        status = status,
        deliveryMethod = "delivery",
        paymentMethod = "cash",
        paymentStatus = "unpaid",
        total = 40.0,
        currency = "SAR",
        cashToCollect = cashToCollect,
        driverFee = 6.0,
        company = OrderCompanyDto(name = "Lauren"),
        branch = BranchDto(id = 1, name = "Al Olaya", phone = "+966112223333"),
        customer = OrderCustomerDto(name = "Sultan", phone = "+966500000000"),
        deliveryAddress = DeliveryAddressDto(text = "King Fahd Rd"),
        pickedUpAt = pickedUpAt,
        requiresDeliveryCode = requiresDeliveryCode,
    )

    /** Records whether a retry was actually asked for — see [OutboxFlushScheduler]'s doc. */
    private class RecordingFlushScheduler : OutboxFlushScheduler {
        var scheduled = 0
            private set

        override fun scheduleFlush() {
            scheduled++
        }
    }

    private fun viewModel(
        outboxDao: DriverActionOutboxDao = FakeDriverActionOutboxDao(),
        scheduler: OutboxFlushScheduler = RecordingFlushScheduler(),
    ): TripViewModel = TripViewModel(TripRepository(orderApi, outboxDao, scheduler))

    // ───────────────────────── seeded start makes no network call ─────────────────────────

    @Test
    fun `starting with a seed order renders it immediately and calls the API zero times`() = runTest(dispatcher) {
        val model = viewModel()
        model.start(7, seed = assignedOrder())
        dispatcher.scheduler.runCurrent()

        val content = model.state.value.phase as TripPhase.Content
        assertEquals(7L, content.order.id)
        coVerify(exactly = 0) { orderApi.order(any()) }
    }

    @Test
    fun `starting without a seed fetches the trip from the server (resume after process death)`() = runTest(dispatcher) {
        coEvery { orderApi.order(7) } returns assignedOrder()

        val model = viewModel()
        model.start(7, seed = null)
        dispatcher.scheduler.runCurrent()

        assertTrue(model.state.value.phase is TripPhase.Content)
        coVerify(exactly = 1) { orderApi.order(7) }
    }

    // ───────────────────────── pick-up is blocked before Ready ─────────────────────────

    @Test
    fun `pick-up is a no-op and never calls the API before the order is ready`() = runTest(dispatcher) {
        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "confirmed"))
        dispatcher.scheduler.runCurrent()

        model.pickUp(7)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { orderApi.pickedUp(any(), any(), any()) }
        val content = model.state.value.phase as TripPhase.Content
        assertFalse(content.isPickingUp)
    }

    @Test
    fun `pick-up succeeds once the order is ready`() = runTest(dispatcher) {
        coEvery { orderApi.pickedUp(7, any(), any()) } returns assignedOrder(
            status = "out_for_delivery",
            pickedUpAt = "2026-09-21T10:00:00Z",
        )

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()

        model.pickUp(7)
        dispatcher.scheduler.runCurrent()

        val content = model.state.value.phase as TripPhase.Content
        assertEquals("out_for_delivery", content.order.status)
        assertEquals("2026-09-21T10:00:00Z", content.order.pickedUpAt)
    }

    // ───────────────────────── changed cash amount needs a reason (pure function) ─────────────────────────

    @Test
    fun `the pre-filled amount unchanged never requires a reason`() {
        assertNull(deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 40.0, note = null))
    }

    @Test
    fun `a blank amount is blocked as amount required, not as a missing reason`() {
        assertEquals(
            DeliveryBlockReason.AmountRequired,
            deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = null, note = null),
        )
    }

    @Test
    fun `a changed amount with no note is blocked client-side`() {
        assertEquals(
            DeliveryBlockReason.ReasonRequiredForChangedAmount,
            deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 35.0, note = null),
        )
        assertEquals(
            DeliveryBlockReason.ReasonRequiredForChangedAmount,
            deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 35.0, note = "   "),
        )
    }

    @Test
    fun `a changed amount WITH a note is allowed through`() {
        assertNull(deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 35.0, note = "Customer paid short"))
    }

    @Test
    fun `an order already paid online never blocks on the amount field`() {
        assertNull(deliveryBlockReason(isCashOrder = false, cashToCollect = 0.0, enteredAmount = null, note = null))
    }

    @Test
    fun `a required code left blank blocks BEFORE the amount is even checked, cash order or not`() {
        assertEquals(
            DeliveryBlockReason.CodeRequired,
            deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 40.0, note = null, codeRequired = true, enteredCode = null),
        )
        assertEquals(
            "an online-paid order needs the code exactly as much — there is no cash exchange to prove handover",
            DeliveryBlockReason.CodeRequired,
            deliveryBlockReason(isCashOrder = false, cashToCollect = 0.0, enteredAmount = null, note = null, codeRequired = true, enteredCode = "  "),
        )
    }

    @Test
    fun `a filled-in code is never blocked once it is non-blank — the server is the one that judges it`() {
        assertNull(
            deliveryBlockReason(isCashOrder = true, cashToCollect = 40.0, enteredAmount = 40.0, note = null, codeRequired = true, enteredCode = "4821"),
        )
    }

    @Test
    fun `confirmDelivery does not call the API when the amount is blocked`() = runTest(dispatcher) {
        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "2026-09-21T10:00:00Z"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.updateDeliveryAmount("35.00") // pre-filled 40.00, changed, no note yet
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { orderApi.delivered(any(), any(), any()) }
    }

    // ───────────────────────── delivered: privacy stripped the instant it succeeds ─────────────────────────

    /**
     * 🔴 The single most important test in this file (driver-ui-standards:
     * "the customer's phone number and address must disappear from both the
     * UI AND any cached state the moment the order is delivered"). Asserts on
     * [TripViewModel.state] itself — the ViewModel's own cache — not on a
     * Composable, because a screen that merely doesn't RENDER the field while
     * the ViewModel still holds it is not what this rule asks for.
     */
    @Test
    fun `the customer's phone and address are gone from state the instant delivered succeeds`() = runTest(dispatcher) {
        val deliveredOrder = assignedOrder(status = "delivered", pickedUpAt = "2026-09-21T10:00:00Z")
        coEvery { orderApi.delivered(7, any(), any()) } returns DeliveredResponse(
            order = deliveredOrder, // the server is free to keep echoing it — the app must not.
            ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
        )

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "2026-09-21T10:00:00Z"))
        dispatcher.scheduler.runCurrent()

        // Sanity: the PII is really there before delivery.
        val before = (model.state.value.phase as TripPhase.Content).order
        assertEquals("Sultan", before.customer?.name)
        assertEquals("King Fahd Rd", before.deliveryAddress?.text)

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        val after = (model.state.value.phase as TripPhase.Content).order
        assertNull("customer must be null the instant delivery succeeds", after.customer)
        assertNull("delivery address must be null the instant delivery succeeds", after.deliveryAddress)
        assertNull("no phone number should survive in state at all", after.customer?.phone)

        // And the ledger the driver just earned is still surfaced.
        assertEquals(6.0, model.state.value.deliveredResult?.ledger?.earnedToday)
    }

    @Test
    fun `a delivered order that is already paid online sends cash_collected as null, never 0`() = runTest(dispatcher) {
        val slot = slot<DeliveredRequest>()
        coEvery { orderApi.delivered(any(), any(), capture(slot)) } returns DeliveredResponse(
            order = assignedOrder(status = "delivered", cashToCollect = 0.0),
            ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 0.0, net = 6.0, cashLimit = null),
        )

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x", cashToCollect = 0.0))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = false, cashToCollect = 0.0)
        dispatcher.scheduler.runCurrent()

        assertNull(slot.captured.cashCollected)
    }

    // ───────────────────────── idempotency key is stable across a retry ─────────────────────────

    @Test
    fun `the pick-up idempotency key is minted once and reused across a retry of the same action`() = runTest(dispatcher) {
        val keys = mutableListOf<String>()
        var call = 0
        coEvery { orderApi.pickedUp(7, any(), any()) } coAnswers {
            keys.add(secondArg())
            call++
            if (call < 3) throw java.io.IOException("timeout") else assignedOrder(status = "out_for_delivery", pickedUpAt = "x")
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()

        model.pickUp(7) // fails
        dispatcher.scheduler.runCurrent()
        model.pickUp(7) // retried by the driver tapping again — fails
        dispatcher.scheduler.runCurrent()
        model.pickUp(7) // retried a third time — succeeds
        dispatcher.scheduler.runCurrent()

        assertEquals(3, keys.size)
        assertEquals("the same command's key must not change across retries", keys[0], keys[1])
        assertEquals("the same command's key must not change across retries", keys[1], keys[2])
    }

    @Test
    fun `a fresh pick-up after a previous one already succeeded mints a new key`() = runTest(dispatcher) {
        val keys = mutableListOf<String>()
        coEvery { orderApi.pickedUp(7, any(), any()) } coAnswers {
            keys.add(secondArg())
            assignedOrder(status = "out_for_delivery", pickedUpAt = "x")
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()
        model.pickUp(7)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, keys.size)
        // Once picked up, a second pickUp() call is a no-op (the order is no
        // longer "ready" for a fresh pickup) — the key list stays at one,
        // proving the key was cleared rather than silently reused for an
        // unrelated later command.
        model.pickUp(7)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, keys.size)
    }

    // ───────────────────────── delivery code (decision 48) ─────────────────────────

    @Test
    fun `an order that carries a code asks for it when the sheet opens, not after a refusal`() = runTest(dispatcher) {
        val model = viewModel()
        model.start(
            7,
            seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x", requiresDeliveryCode = true),
        )
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()

        // The whole point: the driver is told to ask for the code while they
        // are still facing the customer, not after the server has already
        // turned them down once.
        assertTrue(
            "the code field must be up before the first attempt",
            model.state.value.deliverySheet.codeRequired,
        )
        assertEquals(
            DeliveryBlockReason.CodeRequired,
            deliveryBlockReason(
                isCashOrder = true,
                cashToCollect = 40.0,
                enteredAmount = 40.0,
                note = null,
                codeRequired = model.state.value.deliverySheet.codeRequired,
                enteredCode = null,
            ),
        )
    }

    @Test
    fun `the first delivery attempt sends no delivery code — the field is absent for the common case`() = runTest(dispatcher) {
        val slot = slot<DeliveredRequest>()
        coEvery { orderApi.delivered(7, any(), capture(slot)) } returns DeliveredResponse(
            order = assignedOrder(status = "delivered"),
            ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
        )

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        assertFalse("this order does not carry a code, so nothing is asked for", model.state.value.deliverySheet.codeRequired)
        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertNull(slot.captured.deliveryCode)
    }

    @Test
    fun `delivery_code_required reveals the code field and does not clear anything to clear`() = runTest(dispatcher) {
        coEvery { orderApi.delivered(7, any(), any()) } throws
            httpError(422, """{"code":"delivery_code_required"}""")

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        val sheet = model.state.value.deliverySheet
        assertTrue("the field must now appear", sheet.codeRequired)
        assertEquals("nothing was typed yet — nothing to clear", "", sheet.deliveryCode)
        assertTrue("the driver stays on the sheet, never bounced out", sheet.visible)
        assertFalse(sheet.isSubmitting)
    }

    @Test
    fun `delivery_code_mismatch clears the entered code and keeps the driver on the sheet`() = runTest(dispatcher) {
        // 🔴 A fresh HttpException per invocation, not a single shared
        // `throws` value: `httpError`'s ResponseBody is consumed the first
        // time its envelope is parsed, so reusing ONE instance across this
        // test's two calls would make the second failure silently decode as
        // `code = null` — masking the exact behaviour under test.
        coEvery { orderApi.delivered(7, any(), any()) } coAnswers {
            throw httpError(422, """{"code":"delivery_code_mismatch"}""")
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        // The field is already known to be required (a first attempt already
        // asked for it in the real flow) — simulated directly here since this
        // test targets the mismatch branch in isolation.
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()
        model.updateDeliveryCode("9999") // the driver's wrong guess

        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        val sheet = model.state.value.deliverySheet
        assertEquals("a wrong code must not linger for a re-submit by mistake", "", sheet.deliveryCode)
        assertTrue(sheet.visible)
        assertTrue(sheet.codeRequired)
    }

    @Test
    fun `a resubmit with the corrected code sends it, and delivery still succeeds`() = runTest(dispatcher) {
        val slots = mutableListOf<DeliveredRequest>()
        var call = 0
        coEvery { orderApi.delivered(7, any(), capture(slots)) } coAnswers {
            call++
            if (call == 1) {
                throw httpError(422, """{"code":"delivery_code_required"}""")
            } else {
                DeliveredResponse(
                    order = assignedOrder(status = "delivered"),
                    ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
                )
            }
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // no code yet — rejected
        dispatcher.scheduler.runCurrent()

        model.updateDeliveryCode("4821")
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // corrected
        dispatcher.scheduler.runCurrent()

        assertEquals(2, slots.size)
        assertNull(slots[0].deliveryCode)
        assertEquals("4821", slots[1].deliveryCode)
        assertFalse("the sheet closes on eventual success, same as any other delivery", model.state.value.deliverySheet.visible)
    }

    /**
     * 🔴 The idempotency decision for this endpoint: a `delivery_code_required`
     * or `delivery_code_mismatch` response is a pure 422 REJECTION — nothing
     * was applied server-side — and the retry that follows carries a
     * DIFFERENT body (it adds or fixes `delivery_code`). It is therefore NOT
     * "a retry of the same command" the way a dropped connection is; reusing
     * the key risks an idempotency layer replaying the cached failure forever
     * even after the driver enters the right code. A fresh key costs nothing
     * here (nothing to double-apply) and removes that risk. Contrast the
     * generic-failure test above, where the key IS kept, because there the
     * app cannot tell whether the identical request was actually applied.
     */
    @Test
    fun `a retry after delivery_code_required or delivery_code_mismatch mints a fresh idempotency key`() = runTest(dispatcher) {
        val keys = mutableListOf<String>()
        var call = 0
        coEvery { orderApi.delivered(7, any(), any()) } coAnswers {
            keys.add(secondArg())
            call++
            when (call) {
                1 -> throw httpError(422, """{"code":"delivery_code_required"}""")
                2 -> throw httpError(422, """{"code":"delivery_code_mismatch"}""")
                else -> DeliveredResponse(
                    order = assignedOrder(status = "delivered"),
                    ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
                )
            }
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // 1: no code
        dispatcher.scheduler.runCurrent()
        model.updateDeliveryCode("0000")
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // 2: wrong code
        dispatcher.scheduler.runCurrent()
        model.updateDeliveryCode("4821")
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // 3: correct code
        dispatcher.scheduler.runCurrent()

        assertEquals(3, keys.size)
        assertTrue("delivery_code_required must mint a new key for the corrected retry", keys[0] != keys[1])
        assertTrue("delivery_code_mismatch must mint a new key for the corrected retry", keys[1] != keys[2])
    }

    @Test
    fun `confirmDelivery does not call the API when a required code is left blank`() = runTest(dispatcher) {
        coEvery { orderApi.delivered(7, any(), any()) } throws
            httpError(422, """{"code":"delivery_code_required"}""")

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // learns a code is required
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { orderApi.delivered(any(), any(), any()) }

        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // still blank — blocked client-side
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { orderApi.delivered(any(), any(), any()) }
    }

    // ───────────────────────── idempotency key is stable across a retry ─────────────────────────

    @Test
    fun `the delivery idempotency key is stable across a retry, then cleared on success`() = runTest(dispatcher) {
        val keys = mutableListOf<String>()
        var call = 0
        coEvery { orderApi.delivered(7, any(), any()) } coAnswers {
            keys.add(secondArg())
            call++
            if (call < 2) {
                throw java.io.IOException("timeout")
            } else {
                DeliveredResponse(
                    order = assignedOrder(status = "delivered"),
                    ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
                )
            }
        }

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // fails
        dispatcher.scheduler.runCurrent()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0) // retry
        dispatcher.scheduler.runCurrent()

        assertEquals(2, keys.size)
        assertEquals("a retry of the SAME delivery attempt must reuse its key", keys[0], keys[1])
    }

    // ───────────────────────── issue never touches order status ─────────────────────────

    @Test
    fun `reporting an issue does not change the order's status in state`() = runTest(dispatcher) {
        coEvery { orderApi.issue(7, any(), any()) } returns AcceptedDto(ok = true)

        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openIssueSheet()
        model.selectIssueCode(DriverIssueCode.CustomerUnreachable)
        model.submitIssue(7)
        dispatcher.scheduler.runCurrent()

        val content = model.state.value.phase as TripPhase.Content
        assertEquals("out_for_delivery", content.order.status)
        assertTrue(model.state.value.issueReported)
    }

    @Test
    fun `submitIssue is a no-op until a reason code is selected`() = runTest(dispatcher) {
        val model = viewModel()
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openIssueSheet()
        model.submitIssue(7)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 0) { orderApi.issue(any(), any(), any()) }
    }

    // ───────────────────────── load failure ─────────────────────────

    @Test
    fun `the trip's own GET failing surfaces as a load error, not a blank screen`() = runTest(dispatcher) {
        coEvery { orderApi.order(7) } throws java.io.IOException("timeout")

        val model = viewModel()
        model.start(7, seed = null)
        dispatcher.scheduler.runCurrent()

        assertTrue(model.state.value.phase is TripPhase.LoadFailed)
    }

    // ───────────────────────── offline outbox ─────────────────────────
    // The bug this closes: a driver taps "delivered" with no signal, the
    // process dies before a retry — before this, nothing survived to prove
    // either the delivery or the cash collected. See TripRepository's class
    // doc.

    @Test
    fun `an offline pick-up leaves its row queued in the outbox`() = runTest(dispatcher) {
        coEvery { orderApi.pickedUp(7, any(), any()) } throws java.io.IOException("no signal")
        val dao = FakeDriverActionOutboxDao()

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()

        model.pickUp(7)
        dispatcher.scheduler.runCurrent()

        assertEquals("the row must survive an offline failure for a later retry", 1, dao.current.size)
        assertEquals(1, dao.current.single().attempts)
    }

    @Test
    fun `a delivered command that reaches the server successfully is removed from the outbox`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        coEvery { orderApi.delivered(7, any(), any()) } returns DeliveredResponse(
            order = assignedOrder(status = "delivered"),
            ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
        )

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertTrue("a successfully-sent command must not remain queued", dao.current.isEmpty())
    }

    @Test
    fun `retrying the same failed pick-up does not duplicate its outbox row`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        var call = 0
        coEvery { orderApi.pickedUp(7, any(), any()) } coAnswers {
            call++
            if (call < 2) throw java.io.IOException("no signal") else assignedOrder(status = "out_for_delivery", pickedUpAt = "x")
        }

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()

        model.pickUp(7) // fails, queued
        dispatcher.scheduler.runCurrent()
        assertEquals(1, dao.current.size)

        model.pickUp(7) // same key, driver taps again — succeeds
        dispatcher.scheduler.runCurrent()

        assertTrue("no duplicate row for a retry of the same command", dao.current.isEmpty())
    }

    /**
     * 🔴 The case the brief calls out by name: a 422
     * `delivery_code_required`/`delivery_code_mismatch` is a pure rejection —
     * nothing was applied server-side, and [TripViewModel] mints a FRESH key
     * for the corrected retry (see its own doc on `confirmDelivery`). The OLD
     * key's row must therefore be deleted, never left to retry an unchanged,
     * already-rejected body forever.
     */
    @Test
    fun `a delivery_code_required rejection removes its outbox row instead of leaving it to retry forever`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        coEvery { orderApi.delivered(7, any(), any()) } throws
            httpError(422, """{"code":"delivery_code_required"}""")

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertTrue(
            "a pure 422 rejection must not sit in the queue waiting to be replayed with the same (already-rejected) body",
            dao.current.isEmpty(),
        )
    }

    /**
     * 🔴 What `OutboxFlushWorker` actually calls — untested until now, which
     * made the whole background drain an assumption.
     *
     * The assertion that matters is the KEY: a replay must send the stored
     * `Idempotency-Key`, not a fresh one. A new key would present the same
     * delivery to the server as a second, unrelated delivery — crediting the
     * ledger twice for one trip, which is worse than the lost record this
     * queue was built to prevent.
     */
    @Test
    fun `flushing replays a queued delivery under its ORIGINAL idempotency key and clears it`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        val scheduler = RecordingFlushScheduler()
        val repository = TripRepository(orderApi, dao, scheduler)

        // First attempt dies offline: the row survives.
        coEvery { orderApi.delivered(7, any(), any()) } throws java.io.IOException("no signal")
        runCatching {
            repository.delivered(
                orderId = 7,
                idempotencyKey = "key-from-the-basement",
                cashCollected = 40.0,
                deliveryCode = null,
                note = null,
            )
        }
        assertTrue("the failed delivery must stay queued", dao.current.isNotEmpty())

        // Network is back — this is the worker's call.
        coEvery { orderApi.delivered(7, any(), any()) } returns DeliveredResponse(
            order = assignedOrder(status = "delivered", pickedUpAt = "x"),
            ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 6.0, cashOnHand = 40.0, net = -34.0, cashLimit = null),
        )
        repository.flushPending()

        assertTrue("a delivered row must be cleared once the server has it", dao.current.isEmpty())
        coVerify {
            orderApi.delivered(
                id = 7,
                idempotencyKey = "key-from-the-basement",
                body = any(),
            )
        }
    }

    /** A replay that fails again leaves the row exactly where it was — never dropped. */
    @Test
    fun `flushing keeps the row when the retry also fails`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        val repository = TripRepository(orderApi, dao, RecordingFlushScheduler())

        coEvery { orderApi.delivered(7, any(), any()) } throws java.io.IOException("still no signal")
        runCatching {
            repository.delivered(7, "key-still-stuck", cashCollected = 40.0, deliveryCode = null, note = null)
        }
        repository.flushPending()

        assertTrue("a delivery that still cannot be sent must not be discarded", dao.current.isNotEmpty())
    }

    /**
     * A row left in the queue with nothing coming back for it is not a fix —
     * it is the same lost delivery with extra steps. This pins that a
     * transient failure actually ASKS for the background drain, which is the
     * only thing that reaches the server while the app is closed.
     */
    @Test
    fun `a delivery that fails offline schedules the background drain`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        val scheduler = RecordingFlushScheduler()
        coEvery { orderApi.delivered(7, any(), any()) } throws java.io.IOException("no signal")

        val model = viewModel(dao, scheduler)
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertTrue("the row survived, so a drain must have been scheduled for it", dao.current.isNotEmpty())
        assertTrue("nothing asked for the queue to be drained", scheduler.scheduled > 0)
    }

    /**
     * The mirror of the test above: a body the server has genuinely refused is
     * removed, so scheduling a drain for it would wake the device to send
     * nothing.
     */
    @Test
    fun `a pure rejection schedules no background drain`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        val scheduler = RecordingFlushScheduler()
        coEvery { orderApi.delivered(7, any(), any()) } throws
            httpError(422, """{"code":"delivery_code_required"}""")

        val model = viewModel(dao, scheduler)
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertTrue("a refused body must not be queued", dao.current.isEmpty())
        assertEquals("nothing to send, so nothing should have been scheduled", 0, scheduler.scheduled)
    }

    /**
     * 🔴 The three 4xx codes that mean "later", not "no".
     *
     * A blanket `code in 400..499 -> delete the row` reads sensible and is the
     * one rule this table cannot afford: 401 (session expired), 408 (timeout)
     * and 429 (`rate_limited`, an EXPECTED state in the frozen error-code
     * list) all succeed on a later retry of the SAME body. Discarding them
     * would destroy the record of a delivery that really happened and of the
     * cash the driver is carrying for it — the exact loss the whole outbox
     * exists to prevent, reintroduced by the code meant to implement it.
     */
    @Test
    fun `a session-expired, timed-out or throttled delivery stays queued instead of being discarded`() = runTest(dispatcher) {
        for (transientCode in listOf(401, 408, 429)) {
            val dao = FakeDriverActionOutboxDao()
            coEvery { orderApi.delivered(7, any(), any()) } throws httpError(transientCode, "{}")

            val model = viewModel(dao)
            model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
            dispatcher.scheduler.runCurrent()

            model.openDeliverySheet()
            model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
            dispatcher.scheduler.runCurrent()

            assertTrue(
                "HTTP $transientCode means try again later — the delivery and the cash it collected " +
                    "must still be in the queue, not thrown away",
                dao.current.isNotEmpty(),
            )
        }
    }

    @Test
    fun `a delivery_code_mismatch rejection also removes its outbox row`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        coEvery { orderApi.delivered(7, any(), any()) } throws
            httpError(422, """{"code":"delivery_code_mismatch"}""")

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "out_for_delivery", pickedUpAt = "x"))
        dispatcher.scheduler.runCurrent()

        model.openDeliverySheet()
        model.confirmDelivery(7, isCashOrder = true, cashToCollect = 40.0)
        dispatcher.scheduler.runCurrent()

        assertTrue(dao.current.isEmpty())
    }

    @Test
    fun `queued actions from a previous session are flushed the moment the trip screen starts`() = runTest(dispatcher) {
        val dao = FakeDriverActionOutboxDao()
        val key = "leftover-key"
        dao.enqueue(
            DriverActionOutboxEntity(
                idempotency_key = key,
                order_id = 7,
                action_type = app.qrmenu.driver.database.entity.DriverActionType.PickedUp,
                payload_json = """{"occurred_at":"2026-09-21T09:00:00Z"}""",
                occurred_at = 1L,
                created_at = 1L,
            ),
        )
        coEvery { orderApi.pickedUp(7, key, any()) } returns assignedOrder(status = "out_for_delivery", pickedUpAt = "x")

        val model = viewModel(dao)
        model.start(7, seed = assignedOrder(status = "ready"))
        dispatcher.scheduler.runCurrent()

        assertTrue("app-open must drain what an earlier session left queued", dao.current.isEmpty())
        coVerify(exactly = 1) { orderApi.pickedUp(7, key, any()) }
    }
}
