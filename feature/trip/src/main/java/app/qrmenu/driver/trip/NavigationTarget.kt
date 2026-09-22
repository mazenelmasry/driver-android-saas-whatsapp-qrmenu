package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.DeliveryAddressDto

/**
 * What the "Navigate" quick action should point at, decided purely from the
 * order's [DeliveryAddressDto] — kept side-effect-free (no [android.content.Intent]
 * here) so the decision itself is unit-testable without an Android runtime;
 * `TripScreen.kt` turns the result into an actual `Intent`.
 *
 * 🔴 The button must never be a dead end (backend audit, 2026-09-22): today
 * `lat`/`lng` are null for EVERY order (a backend bug fixed in parallel), and
 * even once fixed they will legitimately stay null whenever a customer typed
 * only free text and declined to share their location. [Coordinates] is the
 * precise case; [TextSearch] is the honest fallback that still gets the
 * driver most of the way there; [None] is the one case — no coordinates AND
 * no usable address text — where there truly is nothing to navigate to.
 */
internal sealed interface NavigationTarget {
    data class Coordinates(val lat: Double, val lng: Double) : NavigationTarget
    data class TextSearch(val query: String) : NavigationTarget
    data object None : NavigationTarget
}

internal fun navigationTargetFor(address: DeliveryAddressDto?): NavigationTarget {
    if (address == null) return NavigationTarget.None

    val lat = address.lat
    val lng = address.lng
    if (lat != null && lng != null) return NavigationTarget.Coordinates(lat, lng)

    // Never search for a raw URL that slipped into the text — strip it first,
    // same as what is shown on screen (see stripMapLinks's own doc).
    val query = stripMapLinks(address.text)
    return if (!query.isNullOrBlank()) NavigationTarget.TextSearch(query) else NavigationTarget.None
}

/**
 * The `location_source` values the contract defines. Kept as constants rather
 * than an enum: an unknown value the server adds later must degrade to "just
 * a pin", never crash a driver's screen mid-trip.
 */
private const val SOURCE_APPROX = "approx"

/**
 * True when the pin is the SILENT GPS reading taken as the customer picked a
 * delivery zone — their phone at order time, possibly their office while they
 * order delivery home. The screen labels it; see [NavigationTarget].
 *
 * Only meaningful when coordinates actually exist: a source without a pin
 * describes the provenance of nothing.
 */
internal fun DeliveryAddressDto.isApproximatePin(): Boolean =
    lat != null && lng != null && locationSource == SOURCE_APPROX

/**
 * The customer's own dropped pin, when they pasted one. More accurate than
 * any text search this app can build, and more accurate than an approximate
 * reading — so it is offered alongside navigation.
 *
 * 🔴 Returns a link to OPEN, never to render. See [stripMapLinks].
 */
internal fun DeliveryAddressDto.openableMapLink(): String? =
    mapLink?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

/**
 * Whether offering "open the customer's location" actually buys the driver
 * anything beyond the navigate button next to it.
 *
 * It does NOT when the coordinates were parsed FROM this very link
 * (`location_source == "link"`): turn-by-turn already aims at exactly the
 * place the link points to, so a second button would send the driver to the
 * same pin by a longer route — a choice that costs attention at a door and
 * returns nothing.
 *
 * It DOES when there are no coordinates at all (a short link the server could
 * not resolve, leaving only a text search) or when the pin is merely
 * [isApproximatePin] — there, the customer's own dropped pin is the better
 * of the two and the driver should be able to reach it.
 */
internal fun DeliveryAddressDto.shouldOfferMapLink(): Boolean {
    if (openableMapLink() == null) return false
    val hasCoordinates = lat != null && lng != null
    return !hasCoordinates || isApproximatePin()
}
