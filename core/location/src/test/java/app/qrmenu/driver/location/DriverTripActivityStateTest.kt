package app.qrmenu.driver.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverTripActivityStateTest {

    @Test
    fun `defaults to no active trip`() {
        val state = DriverTripActivityState()

        assertEquals(null, state.activeOrderId.value)
        assertFalse(state.hasActiveTrip.value)
    }

    @Test
    fun `setting an order id arms both flows`() {
        val state = DriverTripActivityState()

        state.setActiveOrder(42L)

        assertEquals(42L, state.activeOrderId.value)
        assertTrue(state.hasActiveTrip.value)
    }

    @Test
    fun `clearing to null disarms both flows`() {
        val state = DriverTripActivityState()
        state.setActiveOrder(42L)

        state.setActiveOrder(null)

        assertEquals(null, state.activeOrderId.value)
        assertFalse(state.hasActiveTrip.value)
    }

    @Test
    fun `switching orders never leaves hasActiveTrip stale`() {
        val state = DriverTripActivityState()
        state.setActiveOrder(1L)

        state.setActiveOrder(2L)

        assertEquals(2L, state.activeOrderId.value)
        assertTrue(state.hasActiveTrip.value)
    }
}
