package app.qrmenu.driver

import android.util.Log
import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.MyOrdersResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeldTripViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        // `android.util.Log` is not implemented in a JVM unit test and throws
        // "not mocked" — the only call site here is the retry-on-failure
        // branch, exactly what two of these tests exercise on purpose.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `starts unknown, before the lookup has had a chance to answer`() {
        val orderApi = mockk<OrderApi>()
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())

        val viewModel = HeldTripViewModel(orderApi)

        assertTrue(viewModel.probe.value is HeldTripProbe.Unknown)
    }

    @Test
    fun `resolves to none once the server confirms no held order`() = runTest(dispatcher) {
        val orderApi = mockk<OrderApi>()
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = emptyList())

        val viewModel = HeldTripViewModel(orderApi)
        dispatcher.scheduler.runCurrent()

        val resolved = viewModel.probe.value as HeldTripProbe.Resolved
        assertNull(resolved.order)
    }

    @Test
    fun `resolves to the held order when one exists`() = runTest(dispatcher) {
        val order = mockk<DriverOrderDto>()
        val orderApi = mockk<OrderApi>()
        coEvery { orderApi.mine() } returns MyOrdersResponse(data = listOf(order))

        val viewModel = HeldTripViewModel(orderApi)
        dispatcher.scheduler.runCurrent()

        val resolved = viewModel.probe.value as HeldTripProbe.Resolved
        assertTrue(resolved.order === order)
    }

    // These two deliberately do NOT use `runTest` — the loop under test keeps
    // re-scheduling itself (fail, `delay(15s)`, retry, fail again, ...) for as
    // long as the mock keeps throwing, and `runTest`'s implicit final
    // `advanceUntilIdle()` would spin that forever trying to drain a coroutine
    // that, by design, never completes on its own (only `viewModelScope`
    // being torn down — which does not happen in this test — would end it).
    // Driving the same `StandardTestDispatcher` directly, one scheduler step
    // at a time, lets the test control exactly how much virtual time passes
    // without ever asking it to run to completion.

    @Test
    fun `a failed lookup stays unknown — never resolves to a confirmed absence`() {
        val orderApi = mockk<OrderApi>()
        coEvery { orderApi.mine() } throws IOException("offline")

        val viewModel = HeldTripViewModel(orderApi)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.probe.value is HeldTripProbe.Unknown)
    }

    @Test
    fun `a failed lookup retries and can still resolve once connectivity returns`() {
        val orderApi = mockk<OrderApi>()
        coEvery { orderApi.mine() } throws IOException("offline") andThen MyOrdersResponse(data = emptyList())

        val viewModel = HeldTripViewModel(orderApi)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.probe.value is HeldTripProbe.Unknown)

        dispatcher.scheduler.advanceTimeBy(16_000L)
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.probe.value is HeldTripProbe.Resolved)
    }
}
