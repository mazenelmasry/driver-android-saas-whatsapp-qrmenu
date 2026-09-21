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
