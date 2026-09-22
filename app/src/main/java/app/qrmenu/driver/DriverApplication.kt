package app.qrmenu.driver

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.qrmenu.driver.alerts.OfferNotificationChannels
import app.qrmenu.driver.offers.DeviceTokenRegistrar
import app.qrmenu.driver.trip.outbox.OutboxFlushScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Things that start here, in this order:
 *  1. Notification channels — the offer channel is created up front so a
 *     high-priority offer push can never arrive before its channel exists
 *     (an offer notification posted to a not-yet-created channel is silently
 *     dropped by the platform, not queued).
 *  2. A device-token registration attempt — covers a session that survived
 *     process death: FCM (via [app.qrmenu.driver.push.DriverMessagingService])
 *     can wake this process with no Activity ever having been created, so
 *     registering only from `MainActivity` would miss that case. See
 *     [DeviceTokenRegistrar] for why this is always safe to attempt (no
 *     session yet, or no Firebase project on this brand → both no-ops).
 *
 * Not started here: Sentry init and the self-updater's version check (still
 * scaffold). The location foreground service is NOT started here either —
 * it starts only when the driver goes "available" (CLAUDE.md § الموقع فى
 * الخلفية).
 */
@HiltAndroidApp
class DriverApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var notificationChannels: OfferNotificationChannels

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var outboxFlushScheduler: OutboxFlushScheduler

    /**
     * 🔴 This alone is not enough — the default `WorkManagerInitializer` is
     * ALSO removed in the manifest. Left in place it initialises WorkManager
     * eagerly at startup with the stock factory, before this provider is ever
     * consulted, and `OutboxFlushWorker` then fails to construct because
     * nothing can inject `TripRepository` into it. The symptom is a worker
     * that never runs, with no error anywhere the app can see.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Deliberately not viewModelScope-shaped — nothing here belongs to a
    // screen, and this object's whole reason to exist is to run before any
    // screen does.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notificationChannels.ensureChannels()
        appScope.launch { deviceTokenRegistrar.registerCurrentToken() }
        // Covers a row queued by a process that died before it could schedule
        // anything — and costs nothing when the queue is empty, since the
        // worker's first act is to find no rows and finish.
        outboxFlushScheduler.scheduleFlush()
    }
}
