package app.qrmenu.driver.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiTextTest {

    private val lri = '⁦'
    private val pdi = '⁩'

    /**
     * The isolation must wrap the WHOLE value, marks outside the digits — that
     * is what keeps a leading `+` from sliding to the far end of a phone number
     * in an Arabic layout.
     */
    @Test
    fun `a value is wrapped in an isolate`() {
        val isolated = BidiText.ltr("+966501234567")

        assertEquals(lri, isolated.first())
        assertEquals(pdi, isolated.last())
        assertEquals("+966501234567", BidiText.strip(isolated))
    }

    @Test
    fun `the version line survives a round trip`() {
        assertEquals("1.0.0 (10000)", BidiText.strip("1.0.0 (10000)".ltr()))
    }

    /**
     * Isolating nothing only adds invisible characters, which then show up in
     * equality checks and make a test failure unreadable.
     */
    @Test
    fun `blank input is left alone`() {
        assertEquals("", BidiText.ltr(""))
        assertEquals("   ", BidiText.ltr("   "))
    }

    @Test
    fun `stripping is safe on text that was never isolated`() {
        assertEquals("plain", BidiText.strip("plain"))
    }

    @Test
    fun `isolation marks are invisible, not printable`() {
        val isolated = "12.50".ltr()

        assertTrue(isolated.length == "12.50".length + 2)
        assertTrue(isolated.contains("12.50"))
    }
}
