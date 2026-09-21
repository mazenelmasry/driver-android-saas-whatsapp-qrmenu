package app.qrmenu.driver.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionRequestGateTest {

    private companion object {
        const val API_33 = 33
        const val API_32 = 32
    }

    @Test
    fun `below API 33 never requests, regardless of anything else`() {
        val result = NotificationPermissionRequestGate.shouldRequest(
            sdkInt = API_32,
            alreadyAskedThisRun = false,
            alreadyGranted = false,
        )
        assertFalse(result)
    }

    @Test
    fun `already asked this run never requests again`() {
        val result = NotificationPermissionRequestGate.shouldRequest(
            sdkInt = API_33,
            alreadyAskedThisRun = true,
            alreadyGranted = false,
        )
        assertFalse(result)
    }

    @Test
    fun `already granted never requests`() {
        val result = NotificationPermissionRequestGate.shouldRequest(
            sdkInt = API_33,
            alreadyAskedThisRun = false,
            alreadyGranted = true,
        )
        assertFalse(result)
    }

    @Test
    fun `API 33 plus, not yet asked, not yet granted, requests`() {
        val result = NotificationPermissionRequestGate.shouldRequest(
            sdkInt = API_33,
            alreadyAskedThisRun = false,
            alreadyGranted = false,
        )
        assertTrue(result)
    }
}
