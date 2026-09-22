package app.qrmenu.driver.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types shared by more than one endpoint, mirroring
 * `openapi/driver.v1.yaml` § components.schemas.
 *
 * Nullability rule used throughout this package: a field is non-nullable and
 * without a default ONLY where the contract lists it in `required` AND does not
 * mark it nullable. Anywhere else it is nullable — a field the parser insists on
 * that the server is allowed to omit kills the WHOLE decode, not just that field.
 */

/** `Accepted` — the `{ok, message}` body every command answers with. */
@Serializable
data class AcceptedDto(
    val ok: Boolean,
    val message: String? = null,
)

/** `AppVersion`. The two CODES are what the updater compares; the names display. */
@Serializable
data class AppVersionDto(
    @SerialName("min_version_code") val minVersionCode: Int,
    @SerialName("latest_version_code") val latestVersionCode: Int,
    @SerialName("min_version_name") val minVersionName: String? = null,
    @SerialName("latest_version_name") val latestVersionName: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val notes: String? = null,
)

/**
 * `LocationPoint` — sent, never received.
 *
 * `recorded_at` is the DEVICE's clock; the server clamps it to (now − 6h)…now
 * and stores both times, so a trip finished in a dead zone keeps the real order
 * of its events.
 */
@Serializable
data class LocationPointDto(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    @SerialName("recorded_at") val recordedAt: String,
)

/**
 * `Branding` — the platform's own name, logo and accent, edited in the admin
 * panel rather than shipped in the APK.
 *
 * 🔴 Every field is nullable, and the login screen must render without any of
 * them. It is read BEFORE login, which is exactly when a driver is most likely
 * to have no signal — a login screen that waits for a logo is a login screen
 * that cannot be used on a weak connection.
 */
@Serializable
data class BrandingDto(
    @SerialName("name_ar") val nameAr: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    /** Hex, e.g. `#B4471F`. The build flavour's colour is the fallback. */
    @SerialName("accent_color") val accentColor: String? = null,
    /**
     * The platform's published legal and support links.
     *
     * 🔴 Google Play refuses an app with accounts that does not reach its
     * privacy policy from INSIDE the app, and expects a route to support and
     * to account deletion in the same place. They come from the server rather
     * than the APK for the same reason the name and logo do: a platform that
     * moves its policy page must not have to ship a build to every driver's
     * phone — and this repo forbids a hardcoded domain outright.
     *
     * Every one of them is nullable, and a row simply does not appear when
     * its link is absent. A deployment that has configured none of them still
     * gives the driver a working account screen.
     */
    @SerialName("privacy_url") val privacyUrl: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null,
    @SerialName("help_url") val helpUrl: String? = null,
    /** Digits only, ready for a `wa.me` link — never a formatted number. */
    @SerialName("support_whatsapp") val supportWhatsApp: String? = null,
    @SerialName("support_email") val supportEmail: String? = null,
)

/**
 * `DeletionRequest` — the account-deletion request Google Play requires,
 * reviewed by an admin rather than applied instantly (a driver may still be
 * carrying a restaurant's cash, and the trip history belongs to the
 * restaurant too). Deliberately carries no `admin_notes`/`processed_by` —
 * those are for the admin panel only, never shown to the driver.
 */
@Serializable
data class DeletionRequestDto(
    val id: Long,
    val status: String,
    val reason: String? = null,
    @SerialName("requested_at") val requestedAt: String,
    @SerialName("processed_at") val processedAt: String? = null,
)

/** Body of `POST driver/account/deletion-request`. */
@Serializable
data class RequestAccountDeletionRequest(
    val reason: String? = null,
)

/** `GET driver/account/deletion-request` response — null when never filed, or every prior one was rejected. */
@Serializable
data class DeletionRequestResponse(
    @SerialName("deletion_request") val deletionRequest: DeletionRequestDto? = null,
)

/** `Branch`. Everything but id/name is nullable — a branch may have no coordinates yet. */
@Serializable
data class BranchDto(
    val id: Long,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    // Not in the contract's `required` list, so it may be absent: treated as
    // "unknown", never as "closed" — a missing flag must not hide work.
    @SerialName("is_open") val isOpen: Boolean? = null,
)

/** `OrderItem` — a summary WITHOUT prices. The driver is delivering, not selling. */
@Serializable
data class OrderItemDto(
    val name: String,
    val quantity: Int,
)
