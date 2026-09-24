package app.qrmenu.driver.location.upload

import app.qrmenu.driver.location.DriverTripActivityState
import app.qrmenu.driver.location.LocationConstants
import app.qrmenu.driver.network.dto.LocationPointDto
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun locationPoint(lat: Double = 24.7) =
    LocationPointDto(lat = lat, lng = 46.6, recordedAt = "2026-09-24T10:00:00+03:00")

class BreadcrumbCollectorTest {

    @Test
    fun `record buffers points without sending until flushed`() = runTest {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val collector = BreadcrumbCollector(DriverTripActivityState(), outbox)

        collector.record(orderId = 7L, point = locationPoint())
        collector.record(orderId = 7L, point = locationPoint())

        coVerify(exactly = 0) { outbox.enqueueAndSend(any(), any()) }
    }

    @Test
    fun `flushNow sends everything buffered for the pending order, once`() = runTest {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val collector = BreadcrumbCollector(DriverTripActivityState(), outbox)
        collector.record(orderId = 7L, point = locationPoint(24.0))
        collector.record(orderId = 7L, point = locationPoint(25.0))
        val sentPoints = slot<List<LocationPointDto>>()

        collector.flushNow()

        coVerify(exactly = 1) { outbox.enqueueAndSend(eq(7L), capture(sentPoints)) }
        assertEquals(2, sentPoints.captured.size)
    }

    @Test
    fun `flushNow with nothing buffered calls the outbox zero times`() = runTest {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val collector = BreadcrumbCollector(DriverTripActivityState(), outbox)

        collector.flushNow()

        coVerify(exactly = 0) { outbox.enqueueAndSend(any(), any()) }
    }

    @Test
    fun `a second flushNow after a successful one sends nothing new`() = runTest {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val collector = BreadcrumbCollector(DriverTripActivityState(), outbox)
        collector.record(orderId = 7L, point = locationPoint())
        collector.flushNow()

        collector.flushNow()

        coVerify(exactly = 1) { outbox.enqueueAndSend(any(), any()) }
    }

    @Test
    fun `reaching the per-request cap flushes automatically, without waiting for the timer`(): Unit = runBlocking {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val collector = BreadcrumbCollector(DriverTripActivityState(), outbox)
        val sentPoints = slot<List<LocationPointDto>>()

        repeat(LocationConstants.MAX_BREADCRUMB_BATCH_POINTS) {
            collector.record(orderId = 7L, point = locationPoint())
        }

        // The cap-triggered flush runs on the collector's own background
        // scope, not this test's dispatcher — polled via MockK's own
        // verify(timeout=) rather than a real 60s wait for the periodic timer.
        coVerify(timeout = 2_000) { outbox.enqueueAndSend(eq(7L), capture(sentPoints)) }
        assertEquals(LocationConstants.MAX_BREADCRUMB_BATCH_POINTS, sentPoints.captured.size)
    }

    @Test
    fun `the trip ending flushes whatever was still buffered for it`(): Unit = runBlocking {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val tripActivity = DriverTripActivityState()
        tripActivity.setActiveOrder(7L)
        val collector = BreadcrumbCollector(tripActivity, outbox)
        collector.record(orderId = 7L, point = locationPoint())
        val sentPoints = slot<List<LocationPointDto>>()

        // TripViewModel's `delivered` success path — see its own doc.
        tripActivity.setActiveOrder(null)

        coVerify(timeout = 2_000) { outbox.enqueueAndSend(eq(7L), capture(sentPoints)) }
        assertEquals(1, sentPoints.captured.size)
    }

    @Test
    fun `switching to a different order flushes the previous order's buffer under its own id`(): Unit = runBlocking {
        val outbox = mockk<BreadcrumbOutbox>(relaxed = true)
        val tripActivity = DriverTripActivityState()
        tripActivity.setActiveOrder(7L)
        val collector = BreadcrumbCollector(tripActivity, outbox)
        collector.record(orderId = 7L, point = locationPoint())

        tripActivity.setActiveOrder(8L)
        collector.record(orderId = 8L, point = locationPoint())
        collector.flushNow()

        coVerify(timeout = 2_000) { outbox.enqueueAndSend(eq(7L), any()) }
        coVerify(timeout = 2_000) { outbox.enqueueAndSend(eq(8L), any()) }
    }
}
