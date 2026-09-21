package app.qrmenu.driver

import android.app.Application
import app.qrmenu.driver.alerts.OfferNotificationChannels
import app.qrmenu.driver.offers.DeviceTokenRegistrar
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
class DriverApplication : Application() {

    @Inject
    lateinit var notificationChannels: OfferNotificationChannels

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    // Deliberately not viewModelScope-shaped — nothing here belongs to a
    // screen, and this object's whole reason to exist is to run before any
    // screen does.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notificationChannels.ensureChannels()
        appScope.launch { deviceTokenRegistrar.registerCurrentToken() }
    }
}
