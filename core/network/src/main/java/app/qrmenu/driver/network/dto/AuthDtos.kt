package app.qrmenu.driver.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Driver`. */
@Serializable
data class DriverDto(
    val id: Long,
    val name: String,
    /** E.164, as canonicalised by the server. */
    val phone: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("online_since") val onlineSince: String? = null,
    /** ar | en | ur | bn | hi. Kept as a String: an unknown value must not fail the decode. */
    val locale: String? = null,
    /** 0–1, feeds candidate ordering with distance. */
    @SerialName("acceptance_rate") val acceptanceRate: Double? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

/**
 * `RestaurantLink` — one restaurant the driver works for.
 *
 * 🔴 The driver's COUNTRY and working AREA come from here and nowhere else
 * (decisions 45–46). There is no country picker and no region picker in this
 * app, precisely so a driver invited by a Riyadh restaurant can never select
 * "Egypt" and then receive zero orders forever with nothing on screen to explain
 * it.
 */
@Serializable
data class RestaurantLinkDto(
    val company: LinkedCompanyDto,
    val branches: List<BranchDto> = emptyList(),
    val fleet: FleetDto? = null,
    /** invited | active | suspended. */
    val status: String,
    /** Set by the restaurant; 0 or null means no ceiling. */
    @SerialName("cash_limit") val cashLimit: Double? = null,
)

@Serializable
data class LinkedCompanyDto(
    val id: Long,
    val name: String,
    val logo: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val currency: String? = null,
)

@Serializable
data class FleetDto(
    val id: Long,
    val name: String? = null,
    /** restaurant | platform — `platform` is phase 4 and is not sold yet. */
    val type: String,
)

@Serializable
data class VerifyOtpRequest(
    val phone: String,
    @SerialName("firebase_token") val firebaseToken: String,
)

/**
 * Verifying the phone also SIGNS IN — the token here is a normal session token,
 * so the driver never proves the same phone twice in one sitting.
 *
 * [needsPassword] true means this phone has no password yet (first run) or is
 * resetting one, and the app goes straight to `set-password` with this token.
 *
 * An EMPTY [restaurants] means the phone is verified but nobody has invited it —
 * the app then asks for the invite code, because there is no self sign-up
 * (decision 15).
 */
@Serializable
data class VerifyOtpResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val driver: DriverDto,
    val restaurants: List<RestaurantLinkDto> = emptyList(),
    @SerialName("needs_password") val needsPassword: Boolean,
)

@Serializable
data class RequestOtpRequest(val phone: String)

/**
 * The driver CHOOSES this; it is never generated for them and never sent to
 * them. Sent on the session that `verify-otp` just opened — proving the phone is
 * the reset, so there is no separate reset endpoint and no reset link.
 */
@Serializable
data class SetPasswordRequest(val password: String)

/**
 * The FCM registration token, verbatim as the SDK returned it. NOT a
 * credential and not a Firebase ID token: it identifies the PHONE, never the
 * driver — the session token in the header is what says who is asking.
 */
@Serializable
data class DeviceTokenRequest(@SerialName("device_token") val deviceToken: String)

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
    /** versionCode (integer), never the version NAME. */
    @SerialName("app_version") val appVersion: Int,
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val driver: DriverDto,
    val restaurants: List<RestaurantLinkDto> = emptyList(),
)

@Serializable
data class RedeemInviteRequest(val code: String)

@Serializable
data class RedeemInviteResponse(
    val restaurants: List<RestaurantLinkDto> = emptyList(),
)

/** `GET /driver/me`. */
@Serializable
data class MeResponse(
    val driver: DriverDto,
    val restaurants: List<RestaurantLinkDto> = emptyList(),
)
