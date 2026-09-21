package app.qrmenu.driver.location.upload

import app.qrmenu.driver.location.LocationConstants
import app.qrmenu.driver.network.dto.LocationPointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPointBatcherTest {

    private fun point(seq: Int) = LocationPointDto(
        lat = seq.toDouble(),
        lng = seq.toDouble(),
        recordedAt = "2026-09-21T10:00:${seq.toString().padStart(2, '0')}+03:00",
    )

    @Test
    fun `nothing queued yields no batch`() {
        val batcher = LocationPointBatcher()
        assertNull(batcher.takeBatchForUpload())
    }

    @Test
    fun `a batch is capped at MAX_BATCH_POINTS and drops the OLDEST points`() {
        val batcher = LocationPointBatcher()
        // One more than the cap.
        for (i in 1..(LocationConstants.MAX_BATCH_POINTS + 1)) {
            batcher.enqueue(point(i))
        }

        val batch = batcher.takeBatchForUpload()
        assertEquals(LocationConstants.MAX_BATCH_POINTS, batch?.size)
        // Point #1 (the oldest) must have been evicted; the newest (#61) survives.
        assertTrue(batch!!.none { it.lat == 1.0 })
        assertTrue(batch.any { it.lat == (LocationConstants.MAX_BATCH_POINTS + 1).toDouble() })
    }

    @Test
    fun `a failed upload keeps every point for retry, oldest-first order preserved`() {
        val batcher = LocationPointBatcher()
        batcher.enqueue(point(1))
        batcher.enqueue(point(2))

        val batch = batcher.takeBatchForUpload()
        assertEquals(2, batch?.size)

        batcher.markUploadFailed()

        // Nothing was lost — the same batch comes back out.
        val retried = batcher.takeBatchForUpload()
        assertEquals(listOf(1.0, 2.0), retried?.map { it.lat })
    }

    @Test
    fun `a later success clears the batch that was retried`() {
        val batcher = LocationPointBatcher()
        batcher.enqueue(point(1))

        val batch = batcher.takeBatchForUpload()
        batcher.markUploadFailed()

        val retried = batcher.takeBatchForUpload()
        assertEquals(1, retried?.size)
        batcher.markUploadSucceeded()

        assertEquals(0, batcher.pendingCount())
        assertNull(batcher.takeBatchForUpload())
    }

    @Test
    fun `points enqueued while a batch is in flight are not lost and are not re-sent early`() {
        val batcher = LocationPointBatcher()
        batcher.enqueue(point(1))
        val firstBatch = batcher.takeBatchForUpload()
        assertEquals(1, firstBatch?.size)

        // A new point arrives mid-upload.
        batcher.enqueue(point(2))
        // No second concurrent batch is handed out while one is in flight.
        assertNull(batcher.takeBatchForUpload())

        batcher.markUploadSucceeded()

        val nextBatch = batcher.takeBatchForUpload()
        assertEquals(listOf(2.0), nextBatch?.map { it.lat })
    }
}
