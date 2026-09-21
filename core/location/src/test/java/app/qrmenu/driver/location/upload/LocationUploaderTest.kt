package app.qrmenu.driver.location.upload

import app.qrmenu.driver.network.api.AvailabilityApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.LocationBatchRequest
import app.qrmenu.driver.network.dto.LocationPointDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUploaderTest {

    private val point = LocationPointDto(lat = 1.0, lng = 2.0, recordedAt = "2026-09-21T10:00:00+03:00")
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-21T10:05:00Z"), ZoneOffset.UTC)

    @Test
    fun `a successful send clears the batch and flips connectivity on`() = runTest {
        val api = mockk<AvailabilityApi>()
        coEvery { api.sendLocations(any()) } returns AcceptedDto(ok = true)

        val batcher = LocationPointBatcher()
        batcher.enqueue(point)
        val connectivity = LocationConnectivityState()
        // Simulate a previously-failed send so we can prove this flips it back.
        connectivity.markSendFailed()

        val uploader = LocationUploader(api, batcher, connectivity, fixedClock)
        uploader.uploadPendingBatch()

        coVerify(exactly = 1) { api.sendLocations(LocationBatchRequest(points = listOf(point))) }
        assertEquals(0, batcher.pendingCount())
        assertTrue(connectivity.isConnected.value)
        assertEquals(fixedClock.millis(), connectivity.lastSuccessfulUploadAtMillis.value)
    }

    @Test
    fun `a failed send (dead zone) keeps the points and flips connectivity off`() = runTest {
        val api = mockk<AvailabilityApi>()
        coEvery { api.sendLocations(any()) } throws IOException("no network")

        val batcher = LocationPointBatcher()
        batcher.enqueue(point)
        val connectivity = LocationConnectivityState()

        val uploader = LocationUploader(api, batcher, connectivity, fixedClock)
        uploader.uploadPendingBatch()

        assertFalse(connectivity.isConnected.value)
        assertNull(connectivity.lastSuccessfulUploadAtMillis.value)
        // The point was NOT lost — it comes back out on the next attempt.
        assertEquals(1, batcher.pendingCount())
        assertEquals(listOf(point), batcher.takeBatchForUpload())
    }

    @Test
    fun `an idle cycle with nothing queued never calls the API`() = runTest {
        val api = mockk<AvailabilityApi>()
        val batcher = LocationPointBatcher()
        val connectivity = LocationConnectivityState()

        val uploader = LocationUploader(api, batcher, connectivity, fixedClock)
        uploader.uploadPendingBatch()

        coVerify(exactly = 0) { api.sendLocations(any()) }
    }

    @Test
    fun `connectivity recovers on the first success after a run of failures`() = runTest {
        val api = mockk<AvailabilityApi>()
        coEvery { api.sendLocations(any()) } throws IOException("no network") andThenThrows
            IOException("still no network") andThen AcceptedDto(ok = true)

        val batcher = LocationPointBatcher()
        val connectivity = LocationConnectivityState()
        val uploader = LocationUploader(api, batcher, connectivity, fixedClock)

        batcher.enqueue(point)
        uploader.uploadPendingBatch()
        assertFalse(connectivity.isConnected.value)

        uploader.uploadPendingBatch()
        assertFalse(connectivity.isConnected.value)

        uploader.uploadPendingBatch()
        assertTrue(connectivity.isConnected.value)
    }
}
