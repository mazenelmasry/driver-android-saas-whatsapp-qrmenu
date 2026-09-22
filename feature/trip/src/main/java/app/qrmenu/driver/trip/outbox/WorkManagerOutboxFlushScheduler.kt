package app.qrmenu.driver.trip.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real scheduler: hands the drain to the platform so it survives the app
 * being closed, and waits for a network rather than burning battery guessing.
 *
 * ## Why `KEEP` and not `REPLACE`
 * `REPLACE` cancels the in-flight request and restarts its backoff from zero,
 * so a driver making several deliveries offline would keep resetting the very
 * timer meant to get their earlier ones through. `KEEP` lets the first request
 * see it through — it drains the whole queue anyway, including rows added
 * after it was scheduled.
 *
 * ## Why `CONNECTED` and not `UNMETERED`
 * These requests are a few hundred bytes, and one of them is the record of
 * money the restaurant is owed. Holding a delivery report back until the
 * driver reaches Wi-Fi would be saving the wrong thing.
 */
@Singleton
class WorkManagerOutboxFlushScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : OutboxFlushScheduler {

    override fun scheduleFlush() {
        val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "driver-outbox-flush"
        const val BACKOFF_SECONDS = 30L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OutboxSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindScheduler(impl: WorkManagerOutboxFlushScheduler): OutboxFlushScheduler
}
