package app.qrmenu.driver.home

import app.qrmenu.driver.network.dto.RestaurantLinkDto

/**
 * `RestaurantLink.status`, kept as a typed value so the card can render three
 * genuinely different treatments rather than branch on a raw string in the UI.
 *
 * 🔴 The three must LOOK different, not just read differently (task brief).
 * A driver who sees no orders needs this screen to say why on its own — that
 * is decision 47's whole point, applied to the one place a driver actually
 * looks first.
 */
enum class LinkStatus {
    /** Not activated at this restaurant yet — the driver exists but cannot work here. */
    Invited,

    /** Working normally. */
    Active,

    /** This restaurant has stopped the driver. Distinct from [Invited]: it once worked. */
    Suspended,

    /**
     * A status this build does not know. Rendered like [Active] rather than
     * hidden — an unrecognised status must never make a real restaurant link
     * disappear from a driver's list.
     */
    Unknown,
}

fun RestaurantLinkDto.linkStatus(): LinkStatus = when (status) {
    "invited" -> LinkStatus.Invited
    "active" -> LinkStatus.Active
    "suspended" -> LinkStatus.Suspended
    else -> LinkStatus.Unknown
}
