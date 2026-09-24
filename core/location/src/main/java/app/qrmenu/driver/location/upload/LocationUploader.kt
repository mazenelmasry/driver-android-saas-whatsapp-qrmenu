package app.qrmenu.driver.location.upload

import app.qrmenu.driver.network.api.AvailabilityApi
import app.qrmenu.driver.network.dto.LocationBatchRequest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Drives one batched `POST driver/location` cycle: take whatever
 * [LocationPointBatcher] has queued, send it, and report the outcome back to
 * both the batcher (so a failure is retried, never dropped) and
 * [LocationConnectivityState] (so "الاتصال منقطع" is true the moment a send
 * fails, per CLAUDE.md).
 *
 * [clock] is injected — not `System.currentTimeMillis()` — purely so a test can
 * assert [LocationConnectivityState.lastSuccessfulUploadAtMillis] without a
 * real wall clock.
 */
@Singleton
class LocationUploader @Inject constructor(
    private val api: AvailabilityApi,
    private val batcher: LocationPointBatcher,
    private val connectivity: LocationConnectivityState,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Runs one cycle. Safe to call on a fixed 15s timer even when there is
     * nothing queued — [LocationPointBatcher.takeBatchForUpload] returns `null`
     * and this is then a no-op, so an idle driver does not spam empty POSTs.
     */
    suspend fun uploadPendingBatch() {
        val batch = batcher.takeBatchForUpload() ?: return
        try {
            api.sendLocations(LocationBatchRequest(points = batch))
            batcher.markUploadSucceeded()
            connectivity.markSendSucceeded(clock.millis())
        } catch (cancelled: CancellationException) {
            // A cancelled upload is not a failed one — swallowing this would
            // both mask coroutine cancellation (breaking structured
            // concurrency for whatever scope this ran in) and wrongly mark
            // the batch failed/offline for a send that was never actually
            // attempted-and-lost.
            throw cancelled
        } catch (t: Throwable) {
            // A driver in a dead zone is the normal case, not the exception —
            // the points are NOT lost, they go back to the front of the queue.
            batcher.markUploadFailed()
            connectivity.markSendFailed()
        }
    }
}
