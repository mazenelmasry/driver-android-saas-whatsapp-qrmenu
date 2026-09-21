package app.qrmenu.driver.onboarding.language

import app.qrmenu.driver.common.locale.SupportedLocales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOptionTest {

    /**
     * A language the app supports but the picker has no row for is unreachable:
     * the driver who needs it can never select it, and nothing else in the app
     * would report the gap. [LanguageOption.all] is built FROM
     * [SupportedLocales] so this cannot happen silently — the list throws while
     * building instead.
     */
    @Test
    fun `the picker offers exactly the languages the app supports, in the same order`() {
        assertEquals(SupportedLocales.supported, LanguageOption.all.map { it.code })
    }

    /**
     * The names are deliberately NOT string resources — see the class doc. This
     * pins that: they must be real words in their own script, never a
     * placeholder or a repeat of the English name.
     */
    @Test
    fun `every language names itself in its own script`() {
        val expected = mapOf(
            "ar" to "العربية",
            "en" to "English",
            "ur" to "اردو",
            "bn" to "বাংলা",
            "hi" to "हिन्दी",
        )

        for (option in LanguageOption.all) {
            assertEquals(expected[option.code], option.nativeName)
        }
    }

    @Test
    fun `no two rows share a name or a code`() {
        assertEquals(LanguageOption.all.size, LanguageOption.all.map { it.code }.toSet().size)
        assertEquals(LanguageOption.all.size, LanguageOption.all.map { it.nativeName }.toSet().size)
    }

    /**
     * Arabic and Urdu rows must render right-to-left even while the app is in
     * English, or the driver they exist for cannot recognise their own language.
     */
    @Test
    fun `arabic and urdu are the right-to-left rows`() {
        assertTrue(LanguageOption.Arabic.isRtl)
        assertTrue(LanguageOption.Urdu.isRtl)
        assertFalse(LanguageOption.English.isRtl)
        assertFalse(LanguageOption.Bengali.isRtl)
        assertFalse(LanguageOption.Hindi.isRtl)
    }
}
