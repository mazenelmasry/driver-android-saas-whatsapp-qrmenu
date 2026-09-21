package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.AppVersionDto
import app.qrmenu.driver.network.dto.BrandingDto
import app.qrmenu.driver.network.dto.LoginRequest
import app.qrmenu.driver.network.dto.LoginResponse
import app.qrmenu.driver.network.dto.MeResponse
import app.qrmenu.driver.network.dto.RedeemInviteRequest
import app.qrmenu.driver.network.dto.RedeemInviteResponse
import app.qrmenu.driver.network.dto.RequestOtpRequest
import app.qrmenu.driver.network.dto.SetPasswordRequest
import app.qrmenu.driver.network.dto.VerifyOtpRequest
import app.qrmenu.driver.network.dto.VerifyOtpResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Tag
import kotlinx.serialization.json.JsonObject
import app.qrmenu.driver.network.interceptors.PublicEndpoint

/**
 * Invite → OTP → PIN. There is no self sign-up (decision 15), so every account
 * starts from a restaurant's invitation.
 *
 * The four calls made BEFORE a session exists take a [PublicEndpoint] tag, which
 * tells `DriverAuthInterceptor` not to sign them. It is a parameter rather than
 * a URL rule so the decision is visible at the call site and survives a rename.
 */
interface AuthApi {

    /** Public — read before login, to know whether this build is even allowed in. */
    @GET("driver/app-version")
    suspend fun appVersion(
        @Tag public: PublicEndpoint = PublicEndpoint.INSTANCE,
    ): AppVersionDto

    /**
     * Platform identity for the login screen. Public — it is read before a
     * session exists, which is the whole point of it.
     */
    @GET("driver/branding")
    suspend fun branding(
        @Tag public: PublicEndpoint = PublicEndpoint.INSTANCE,
    ): BrandingDto

    @POST("driver/auth/request-otp")
    suspend fun requestOtp(
        @Body body: RequestOtpRequest,
        @Tag public: PublicEndpoint = PublicEndpoint.INSTANCE,
    ): AcceptedDto

    @POST("driver/auth/verify-otp")
    suspend fun verifyOtp(
        @Body body: VerifyOtpRequest,
        @Tag public: PublicEndpoint = PublicEndpoint.INSTANCE,
    ): VerifyOtpResponse

    @POST("driver/auth/login")
    suspend fun login(
        @Body body: LoginRequest,
        @Tag public: PublicEndpoint = PublicEndpoint.INSTANCE,
    ): LoginResponse

    /**
     * Chooses (or resets) the password, on the session `verify-otp` just opened.
     *
     * There is no separate reset endpoint: proving the phone by SMS IS the
     * reset, because it is the only credential a driver reliably has.
     */
    @POST("driver/auth/set-password")
    suspend fun setPassword(@Body body: SetPasswordRequest): AcceptedDto

    @POST("driver/auth/redeem-invite")
    suspend fun redeemInvite(@Body body: RedeemInviteRequest): RedeemInviteResponse

    @POST("driver/auth/logout")
    suspend fun logout(): AcceptedDto

    @GET("driver/me")
    suspend fun me(): MeResponse

    /**
     * Authorises `private-driver.{own driverId}` for Reverb. Neither the session
     * cookie nor a POS staff token can authorise a DriverToken holder, hence a
     * driver-specific endpoint. The body is whatever Pusher's handshake needs, so
     * it is passed through untyped rather than frozen into a DTO.
     */
    @POST("driver/broadcasting/auth")
    suspend fun broadcastingAuth(@Body body: JsonObject): JsonObject
}
