package app.qrmenu.driver.account

import app.qrmenu.driver.network.dto.RestaurantLinkDto

/**
 * `RestaurantLink.status`, kept as a typed value so a card can render three
 * genuinely different treatments rather than branch on a raw string in the
 * UI.
 *
 * 🔴 The three must LOOK different, not just read differently — carried over
 * from `:feature:home`'s `LinkStatus` (the screen this module replaces) rather
 * than imported from it: that module is being removed in this same change
 * set, so a cross-feature dependency on it would only be a dependency on code
 * about to disappear. The three cases and their meaning are identical.
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
