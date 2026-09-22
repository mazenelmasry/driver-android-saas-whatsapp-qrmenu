package app.qrmenu.driver.onboarding.gms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure decision only — [Context.googleServicesPresence] itself is a
 * two-line [android.content.pm.PackageManager] wrapper deliberately left
 * untested (see its doc), so this file never touches Android APIs and runs
 * on the plain JVM like every other feature-module test here.
 */
class GoogleServicesPresenceTest {

    @Test
    fun `enabled GMS needs no warning`() {
        assertFalse(shouldWarnAboutMissingGoogleServices(GmsPresence.Enabled))
    }

    /**
     * Disabled is treated the same as missing: a device policy that turned
     * GMS off produces the identical symptom (no FCM push) as a phone that
     * never had it, so the driver needs the identical message.
     */
    @Test
    fun `disabled GMS still warns`() {
        assertTrue(shouldWarnAboutMissingGoogleServices(GmsPresence.Disabled))
    }

    @Test
    fun `missing GMS warns`() {
        assertTrue(shouldWarnAboutMissingGoogleServices(GmsPresence.NotInstalled))
    }
}
