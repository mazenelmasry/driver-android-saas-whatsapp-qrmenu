package app.qrmenu.driver.common.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocalesTest {

    @Test
    fun `supports exactly the five decided languages`() {
        assertEquals(listOf("ar", "en", "ur", "bn", "hi"), SupportedLocales.supported)
    }

    @Test
    fun `default is Arabic`() {
        assertEquals("ar", SupportedLocales.default)
    }

    @Test
    fun `Arabic and Urdu are RTL, the rest are not`() {
        assertTrue(SupportedLocales.isRtl("ar"))
        assertTrue(SupportedLocales.isRtl("ur"))
        assertFalse(SupportedLocales.isRtl("en"))
        assertFalse(SupportedLocales.isRtl("bn"))
        assertFalse(SupportedLocales.isRtl("hi"))
    }

    @Test
    fun `unsupported language is rejected`() {
        assertFalse(SupportedLocales.isSupported("fr"))
        assertTrue(SupportedLocales.isSupported("hi"))
    }

    @Test
    fun `Latin locale always carries the nu-latn numbering extension`() {
        val locale = SupportedLocales.buildLatinLocale("ar")
        assertEquals("ar", locale.language)
        assertEquals("latn", locale.getUnicodeLocaleType("nu"))
    }
}
