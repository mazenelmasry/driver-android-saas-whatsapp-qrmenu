package app.qrmenu.driver

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Scaffold only (phase 0). Things that will start here, in this order:
 *  1. [app.qrmenu.driver.datastore.LocaleManager].restoreFromPreferences() — before
 *     the first frame, so the driver sees their own language immediately.
 *  2. Sentry init (DSN from BuildConfig, empty = disabled).
 *  3. Notification channels — the dispatch-offer channel is created up front so a
 *     high-priority offer never arrives before its channel exists.
 *  4. The self-updater's version check.
 *
 * The location foreground service is NOT started here — it starts only when the
 * driver goes "available" (CLAUDE.md § الموقع فى الخلفية).
 */
@HiltAndroidApp
class DriverApplication : Application()
