package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.DeliveryAddressDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what a driver is told about a pin, and when a second
 * way to reach it is worth a button.
 */
class MapLinkOfferTest {

    private fun address(
        text: String = "شارع الملك فهد",
        lat: Double? = null,
        lng: Double? = null,
        source: String? = null,
        mapLink: String? = null,
    ) = DeliveryAddressDto(text = text, lat = lat, lng = lng, locationSource = source, mapLink = mapLink)

    @Test
    fun `an approx pin is labelled`() {
        assertTrue(address(lat = 24.7, lng = 46.6, source = "approx").isApproximatePin())
    }

    @Test
    fun `a pin the customer placed is not labelled`() {
        assertFalse(address(lat = 24.7, lng = 46.6, source = "link").isApproximatePin())
        assertFalse(address(lat = 24.7, lng = 46.6, source = "device").isApproximatePin())
    }

    @Test
    fun `a source with no coordinates describes nothing and is not labelled`() {
        assertFalse(address(source = "approx").isApproximatePin())
    }

    @Test
    fun `an unknown future source degrades to an ordinary pin rather than crashing`() {
        assertFalse(address(lat = 24.7, lng = 46.6, source = "something_added_next_year").isApproximatePin())
    }

    @Test
    fun `the link is not offered when the pin was parsed from that very link`() {
        // Navigate already aims at exactly where the link points — a second
        // button would cost attention at a door and return nothing.
        assertFalse(
            address(lat = 24.7, lng = 46.6, source = "link", mapLink = "https://maps.google.com/?q=24.7,46.6")
                .shouldOfferMapLink(),
        )
    }

    @Test
    fun `the link is offered when there is no pin at all`() {
        // A short link the server could not resolve: text search is all the
        // navigate button has, and the customer's own pin beats it.
        assertTrue(address(mapLink = "https://maps.app.goo.gl/xYz").shouldOfferMapLink())
    }

    @Test
    fun `the link is offered when the pin is only approximate`() {
        assertTrue(
            address(lat = 24.7, lng = 46.6, source = "approx", mapLink = "https://maps.app.goo.gl/xYz")
                .shouldOfferMapLink(),
        )
    }

    @Test
    fun `nothing is offered without a link`() {
        assertFalse(address(lat = 24.7, lng = 46.6, source = "approx").shouldOfferMapLink())
        assertNull(address().openableMapLink())
    }

    @Test
    fun `a non-http link is refused rather than handed to an intent`() {
        // `javascript:` and friends must never reach ACTION_VIEW.
        assertNull(address(mapLink = "javascript:alert(1)").openableMapLink())
        assertNull(address(mapLink = "   ").openableMapLink())
    }

    @Test
    fun `an http link survives surrounding whitespace`() {
        assertEquals(
            "https://maps.app.goo.gl/xYz",
            address(mapLink = "  https://maps.app.goo.gl/xYz  ").openableMapLink(),
        )
    }
}
