package app.qrmenu.driver.health

import android.os.Build

/**
 * Decides whether to fire the `POST_NOTIFICATIONS` system dialog right now.
 *
 * The offer notification is the only thing that reaches a driver whose phone
 * is in their pocket — measured dead on a real device (CLAUDE.md: 37 enqueued,
 * 0 posted) because this permission was only ever asked at the tail of the
 * unrelated location ladder. Requesting it here must not depend on that
 * ladder, but it still has to behave: ask AT MOST once per app run, because
 * Android stops showing the system dialog after two refusals and a request
 * that reappears on every resume trains a driver to refuse it out of
 * annoyance before they ever understand what it is for.
 */
object NotificationPermissionRequestGate {

    /**
     * @param sdkInt the running platform's `Build.VERSION.SDK_INT` (a
     *   parameter rather than a direct read, so this is testable on a plain
     *   JVM with no Android runtime).
     */
    fun shouldRequest(
        sdkInt: Int,
        alreadyAskedThisRun: Boolean,
        alreadyGranted: Boolean,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
        if (alreadyAskedThisRun) return false
        if (alreadyGranted) return false
        return true
    }
}
