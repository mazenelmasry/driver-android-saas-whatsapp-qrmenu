package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.AvailableOrdersResponse
import app.qrmenu.driver.network.dto.BreadcrumbsRequest
import app.qrmenu.driver.network.dto.DeclineRequest
import app.qrmenu.driver.network.dto.DeliveredRequest
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.IssueRequest
import app.qrmenu.driver.network.dto.MyOrdersResponse
import app.qrmenu.driver.network.dto.OrderHistoryResponse
import app.qrmenu.driver.network.dto.PickedUpRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The trip.
 *
 * 🔴 Every COMMAND takes `idempotencyKey` as a REQUIRED parameter, so the
 * compiler enforces what the contract requires. It is deliberately not an
 * interceptor reading an optional tag: a driver is on mobile data in a moving
 * car, a lost response WILL be retried, and a `delivered` retry without a stable
 * key credits the fee and the collected cash to the ledger twice. The key is
 * generated once per COMMAND — never per attempt — so a repeat returns the same
 * 200 instead of acting again.
 */
interface OrderApi {

    /**
     * Offers and claimable orders. `context` says WHY the list is empty
     * (decision 47) — an empty list with no reason is what makes a driver
     * believe the app is broken.
     */
    @GET("driver/orders/available")
    suspend fun available(): AvailableOrdersResponse

    /** At most one — a driver holds a single trip at a time (decision 21). */
    @GET("driver/orders/mine")
    suspend fun mine(): MyOrdersResponse

    @GET("driver/orders/history")
    suspend fun history(
        @Query("date") date: String? = null,
        @Query("company_id") companyId: Long? = null,
    ): OrderHistoryResponse

    /**
     * The ASSIGNED shape (customer + address) only when this driver holds the
     * order, otherwise the OFFERED shape. A driver who holds neither gets 404,
     * never 403 — an id is not something to confirm.
     */
    @GET("driver/orders/{id}")
    suspend fun order(@Path("id") id: Long): DriverOrderDto

    /**
     * Acknowledges that the offer was RECEIVED — this is not accepting it.
     *
     * The spine of "zero lost offers": no ack within 15s re-sends the push, no
     * ack within 45s re-dispatches to the next candidate. So it is sent the
     * instant the offer screen appears, before the driver has decided anything.
     */
    @POST("driver/orders/{id}/ack")
    suspend fun ack(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): AcceptedDto

    /** Settled under a row lock: the first driver wins, everyone else gets 409 `already_claimed`. */
    @POST("driver/orders/{id}/accept")
    suspend fun accept(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DriverOrderDto

    @POST("driver/orders/{id}/decline")
    suspend fun decline(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: DeclineRequest,
    ): AcceptedDto

    /** Self-serve assignment modes: whoever picks it up first gets it. */
    @POST("driver/orders/{id}/claim")
    suspend fun claim(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DriverOrderDto

    /** Hands the order back BEFORE pickup. A driver can never cancel an order. */
    @POST("driver/orders/{id}/release")
    suspend fun release(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): AcceptedDto

    @POST("driver/orders/{id}/picked-up")
    suspend fun pickedUp(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: PickedUpRequest,
    ): DriverOrderDto

    /** Where the delivery fee is earned (decision 31), and where the cash enters the book. */
    @POST("driver/orders/{id}/delivered")
    suspend fun delivered(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: DeliveredRequest,
    ): DeliveredResponse

    /**
     * Reports a problem WITHOUT moving the order. A problem is information for
     * the restaurant, not a state change the driver gets to decide.
     */
    @POST("driver/orders/{id}/issue")
    suspend fun issue(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: IssueRequest,
    ): AcceptedDto

    /** A point a minute, purged after 30 days. Settles "the driver never arrived" — not a live feed. */
    @POST("driver/orders/{id}/breadcrumbs")
    suspend fun breadcrumbs(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: BreadcrumbsRequest,
    ): AcceptedDto
}
