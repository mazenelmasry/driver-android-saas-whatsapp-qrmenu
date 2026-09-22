package app.qrmenu.driver.onboarding.gms

import android.content.Context
import android.content.pm.PackageManager

/** The package that identifies Google Mobile Services on the device. */
internal const val GMS_PACKAGE_NAME = "com.google.android.gms"

/**
 * What we could establish about Google Mobile Services on this device.
 *
 * Kept as three states, not a boolean, because [Disabled] and [NotInstalled]
 * call for the same on-screen message but are worth telling apart in logs —
 * a device policy that disabled GMS is a different failure mode than a phone
 * (e.g. a Huawei without Play Services) that never had it.
 */
enum class GmsPresence {
    Enabled,
    Disabled,
    NotInstalled,
}

/**
 * Pure decision, separated from [Context] so it is testable on the JVM: does
 * this presence warrant telling the driver "no push notifications will
 * arrive, keep the app open"?
 *
 * 🔴 This is a WARNING, never a gate. `:core:push` falls back to a 15s poll
 * (see CLAUDE.md "معمارية صفر إشعار ضائع") when FCM never fires, so a
 * missing/disabled GMS does not break the app — it only means the driver
 * must watch the offer list instead of waiting for a ring.
 */
fun shouldWarnAboutMissingGoogleServices(presence: GmsPresence): Boolean =
    presence != GmsPresence.Enabled

/**
 * The Android-side lookup. Deliberately NOT unit-tested (it is a two-line
 * wrapper over [PackageManager]) — the logic worth testing is
 * [shouldWarnAboutMissingGoogleServices] above, which takes the result of
 * this function as plain data.
 *
 * No new Gradle dependency was added for this: `com.google.android.gms`
 * package presence is checked via the platform's own [PackageManager],
 * not via `play-services-base`/`GoogleApiAvailability` (see CLAUDE.md note
 * left in the handback report for why that dependency was not added here).
 */
fun Context.googleServicesPresence(): GmsPresence =
    try {
        val info = packageManager.getApplicationInfo(GMS_PACKAGE_NAME, 0)
        if (info.enabled) GmsPresence.Enabled else GmsPresence.Disabled
    } catch (notFound: PackageManager.NameNotFoundException) {
        GmsPresence.NotInstalled
    }
