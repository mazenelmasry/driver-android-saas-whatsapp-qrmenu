package app.qrmenu.driver.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressTextTest {

    @Test
    fun `an address that is only a URL strips to nothing`() {
        assertNull(stripMapLinks("https://maps.app.goo.gl/xYz"))
    }

    @Test
    fun `an Arabic address with a URL in the middle keeps both real segments joined by the Arabic comma`() {
        val input = "شارع الملك فهد، https://maps.app.goo.gl/xYz، حي العليا"
        assertEquals("شارع الملك فهد، حي العليا", stripMapLinks(input))
    }

    @Test
    fun `text with no URL and no separator is returned unchanged`() {
        assertEquals("شارع الأمير سلطان", stripMapLinks("شارع الأمير سلطان"))
    }

    @Test
    fun `text with no URL but a Latin comma still round-trips its two segments`() {
        assertEquals("Building 4، apartment 12", stripMapLinks("Building 4, apartment 12"))
    }

    @Test
    fun `a URL glued to the rest of the text by a separator still strips`() {
        val input = "https://maps.app.goo.gl/xYz، حي العليا"
        assertEquals("حي العليا", stripMapLinks(input))
    }

    @Test
    fun `null input stays null`() {
        assertNull(stripMapLinks(null))
    }

    @Test
    fun `blank input stays as-is`() {
        assertEquals("", stripMapLinks(""))
    }
}
