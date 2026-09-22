package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.DeliveryAddressDto
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTargetTest {

    @Test
    fun `coordinates present chooses turn-by-turn coordinates`() {
        val address = DeliveryAddressDto(text = "شارع الأمير سلطان", lat = 24.7, lng = 46.6)
        assertEquals(NavigationTarget.Coordinates(24.7, 46.6), navigationTargetFor(address))
    }

    @Test
    fun `no coordinates but usable text chooses a text search on the cleaned text`() {
        val address = DeliveryAddressDto(text = "شارع الملك فهد، https://maps.app.goo.gl/xYz، حي العليا", lat = null, lng = null)
        assertEquals(NavigationTarget.TextSearch("شارع الملك فهد، حي العليا"), navigationTargetFor(address))
    }

    @Test
    fun `neither coordinates nor usable text (address is only a URL) has no target`() {
        val address = DeliveryAddressDto(text = "https://maps.app.goo.gl/xYz", lat = null, lng = null)
        assertEquals(NavigationTarget.None, navigationTargetFor(address))
    }

    @Test
    fun `a null address has no target`() {
        assertEquals(NavigationTarget.None, navigationTargetFor(null))
    }

    @Test
    fun `blank address text has no target`() {
        val address = DeliveryAddressDto(text = "   ", lat = null, lng = null)
        assertEquals(NavigationTarget.None, navigationTargetFor(address))
    }
}
