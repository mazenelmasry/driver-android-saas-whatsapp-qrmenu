package app.qrmenu.driver.trip.outbox

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.trip.TripRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains `driver_actions_outbox` while the app is CLOSED.
 *
 * Without this the queue only advanced when the driver happened to reopen the
 * trip screen, which is the one thing a driver who has finished their shift
 * will not do — so a delivery made in a basement could sit unsent overnight
 * with the restaurant's cash unaccounted for.
 *
 * ## Why it can never double-apply
 * Every queued row is keyed by the same `Idempotency-Key` the server dedupes
 * on, so this worker racing the foreground app (or itself, after a process
 * restart) cannot apply the same command twice. That is also why no "sending
 * now" column is needed to coordinate them.
 *
 * ## The three guards that matter
 *
 * 1. **No session, no attempt.** Every trip route is authenticated. Running
 *    with no session produces a 401 per row, and 401 is deliberately
 *    RETRYABLE ([TripRepository.isPureRejection] keeps the row, because the
 *    same body succeeds after the driver signs in again) — so an unguarded
 *    worker would spin on a signed-out device forever, burning battery to
 *    achieve nothing. It returns [Result.success] rather than retrying: there
 *    is nothing to retry until a human signs in, and that event reschedules
 *    this worker itself.
 *
 * 2. **A retry ceiling, and the rows SURVIVE it.** After [MAX_ATTEMPTS]
 *    backed-off attempts this stops rescheduling — but it never deletes
 *    anything. A row that cannot be sent is a record of money the driver is
 *    carrying; the correct end state is "still queued, and someone is told",
 *    never "quietly dropped". `observePendingCount()` is what surfaces it.
 *
 * 3. **Failure means retry, not loss.** Any throw returns [Result.retry] so
 *    WorkManager applies its exponential backoff. `flushPending()` itself
 *    stops at the first row it cannot send rather than skipping ahead, so
 *    ordering within one trip is preserved across attempts.
 */
@HiltWorker
class OutboxFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tripRepository: TripRepository,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!tokenStore.hasValidSession()) {
            return Result.success()
        }

        return try {
            tripRepository.flushPending()
            Result.success()
        } catch (thrown: Throwable) {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                // Stop the backoff, keep the rows. See guard 2 above.
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    internal companion object {
        /**
         * With WorkManager's default exponential backoff (30s base) this
         * spans roughly half a day of attempts before the app stops asking on
         * its own — long enough to cover a shift that ended out of coverage,
         * short enough not to retry a genuinely stuck row forever.
         */
        const val MAX_ATTEMPTS = 10
    }
}
