package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.AvailabilityRequest
import app.qrmenu.driver.network.dto.AvailabilityResponse
import app.qrmenu.driver.network.dto.LocationBatchRequest
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

/**
 * Availability is GLOBAL, not per restaurant (decision 20) — one switch covers
 * every restaurant the driver is linked to. The server refuses to go offline
 * mid-trip, and turns availability off by itself only when the branch closes or
 * the heartbeat has been silent for three minutes.
 */
interface AvailabilityApi {

    @PATCH("driver/availability")
    suspend fun setAvailability(@Body body: AvailabilityRequest): AvailabilityResponse

    /**
     * Batched heartbeat, roughly every 15s while available. Nothing is tracked
     * at all while unavailable — that promise is made to the driver on the
     * permission screen, so it is kept here by simply not calling this.
     */
    @POST("driver/location")
    suspend fun sendLocations(@Body body: LocationBatchRequest): AcceptedDto
}
