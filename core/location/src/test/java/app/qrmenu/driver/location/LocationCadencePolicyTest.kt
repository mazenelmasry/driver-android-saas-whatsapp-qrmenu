package app.qrmenu.driver.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationCadencePolicyTest {

    @Test
    fun `unavailable never tracks regardless of movement or trip`() {
        assertEquals(
            DriverLocationState.UNAVAILABLE,
            LocationCadencePolicy.resolve(isAvailable = false, hasActiveTrip = true, isMoving = true),
        )
        assertNull(
            LocationCadencePolicy.resolveIntervalMillis(isAvailable = false, hasActiveTrip = true, isMoving = true),
        )
    }

    @Test
    fun `moving wins over having an active trip`() {
        val state = LocationCadencePolicy.resolve(isAvailable = true, hasActiveTrip = true, isMoving = true)
        assertEquals(DriverLocationState.MOVING, state)
        assertEquals(LocationConstants.MOVING_INTERVAL_MS, state.samplingIntervalMillis())
    }

    @Test
    fun `stopped with an active trip samples at the stopped tier`() {
        val state = LocationCadencePolicy.resolve(isAvailable = true, hasActiveTrip = true, isMoving = false)
        assertEquals(DriverLocationState.STOPPED, state)
        assertEquals(LocationConstants.STOPPED_INTERVAL_MS, state.samplingIntervalMillis())
    }

    @Test
    fun `available with no active trip and not moving samples at the idle tier`() {
        val state = LocationCadencePolicy.resolve(isAvailable = true, hasActiveTrip = false, isMoving = false)
        assertEquals(DriverLocationState.AVAILABLE_IDLE, state)
        assertEquals(LocationConstants.AVAILABLE_IDLE_INTERVAL_MS, state.samplingIntervalMillis())
    }

    @Test
    fun `isMoving classifies against the frozen speed threshold`() {
        assertEquals(false, LocationCadencePolicy.isMoving(null))
        assertEquals(false, LocationCadencePolicy.isMoving(0f))
        assertEquals(false, LocationCadencePolicy.isMoving(1.49f))
        assertEquals(true, LocationCadencePolicy.isMoving(1.5f))
        assertEquals(true, LocationCadencePolicy.isMoving(5f))
    }
}
