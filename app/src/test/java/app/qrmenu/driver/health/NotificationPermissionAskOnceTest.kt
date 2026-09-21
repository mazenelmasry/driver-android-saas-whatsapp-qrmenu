package app.qrmenu.driver.health

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NotificationPermissionAskOnce] is a bare process-lifetime flag, but its
 * only job is "mark, then report marked" — worth a direct test since a typo
 * here (e.g. `asked = false` in `markAsked`) would silently re-open the
 * dialog on every launch with nothing else catching it.
 */
class NotificationPermissionAskOnceTest {

    @Test
    fun `hasAsked reports true once markAsked has been called`() {
        NotificationPermissionAskOnce.markAsked()

        assertTrue(NotificationPermissionAskOnce.hasAsked())
    }

    @Test
    fun `marking is idempotent`() {
        NotificationPermissionAskOnce.markAsked()
        NotificationPermissionAskOnce.markAsked()

        assertTrue(NotificationPermissionAskOnce.hasAsked())
    }
}
