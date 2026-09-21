package app.qrmenu.driver.location.upload

import app.qrmenu.driver.location.LocationConstants
import app.qrmenu.driver.network.dto.LocationPointDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The in-memory queue a batched upload is built from.
 *
 * ## The over-60 decision
 * The contract caps a single `POST driver/location` batch at 60 points. A
 * driver who has been in a dead zone for a while (moving-tier sampling every
 * 7s ⇒ 60 points is under 7 minutes offline) can easily accumulate more than
 * that before a connection returns.
 *
 * **This queue drops the OLDEST points once it holds more than
 * [LocationConstants.MAX_BATCH_POINTS], keeping the newest.** Deliberately, not
 * by default:
 * - The consumer of this data is live dispatch/tracking — "where is the driver
 *   RIGHT NOW" — not a full breadcrumb trail. The trip's complete path is
 *   already reconstructed server-side from `POST .../breadcrumbs`
 *   (CLAUDE.md: "مسار الرحلة: نقطة/دقيقة تُحفظ… يحسم نزاع 'لم يصل'"), which is
 *   a SEPARATE, denser record this module does not touch. `driver/location`
 *   only ever has to answer "is this driver still alive and where," so the
 *   most useful 60 points are always the most recent 60, not the oldest.
 * - Dropping the newest instead would mean: a driver who reconnects after 10
 *   dead minutes gets a batch that STILL claims their oldest, most stale
 *   position — exactly the wrong answer for "where is the driver right now."
 *
 * Failed sends keep every point they attempted — see [markUploadFailed] — so
 * this eviction only ever triggers while genuinely offline and accumulating,
 * never merely because an upload is in flight.
 */
@Singleton
class LocationPointBatcher @Inject constructor() {

    private val pending = ArrayDeque<LocationPointDto>()
    private var inFlight: List<LocationPointDto> = emptyList()

    @Synchronized
    fun enqueue(point: LocationPointDto) {
        pending.addLast(point)
        while (pending.size > LocationConstants.MAX_BATCH_POINTS) {
            pending.removeFirst() // oldest dropped — see class doc.
        }
    }

    @Synchronized
    fun pendingCount(): Int = pending.size + inFlight.size

    /**
     * Snapshots everything queued (capped at [LocationConstants.MAX_BATCH_POINTS],
     * belt-and-braces against the contract) as the next batch to upload, and
     * removes it from [pending] — but keeps it in [inFlight] until the caller
     * reports success or failure, so a crash mid-upload does not silently lose
     * points that were never actually confirmed sent.
     *
     * Returns `null` (and touches nothing) when there is nothing to send, or
     * when a batch is already in flight — one upload at a time, same ordering
     * guarantee the 15s scheduler relies on.
     */
    @Synchronized
    fun takeBatchForUpload(): List<LocationPointDto>? {
        if (inFlight.isNotEmpty() || pending.isEmpty()) return null
        val batch = pending.take(LocationConstants.MAX_BATCH_POINTS)
        repeat(batch.size) { pending.removeFirst() }
        inFlight = batch
        return batch
    }

    /** The batch handed out by [takeBatchForUpload] was accepted by the server — discard it. */
    @Synchronized
    fun markUploadSucceeded() {
        inFlight = emptyList()
    }

    /**
     * The batch handed out by [takeBatchForUpload] failed to send — a driver in
     * a dead zone is the normal case here, not the exception, so the points go
     * back to the FRONT of the queue (oldest-first order preserved) rather than
     * being lost, and normal cap eviction applies to whatever now exceeds
     * [LocationConstants.MAX_BATCH_POINTS] between the requeued batch and
     * whatever kept accumulating while the upload was in flight.
     */
    @Synchronized
    fun markUploadFailed() {
        if (inFlight.isEmpty()) return
        val requeued = inFlight
        inFlight = emptyList()
        // Put the failed batch back in front, oldest-first.
        for (point in requeued.asReversed()) {
            pending.addFirst(point)
        }
        while (pending.size > LocationConstants.MAX_BATCH_POINTS) {
            pending.removeFirst()
        }
    }
}
