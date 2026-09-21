package app.qrmenu.driver.auth.login

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-in screen exists in all five languages.
 *
 * A missing key here does not fail the build — Android falls back to English —
 * and the sign-in screen is the one a driver meets before they have any reason
 * to trust the app.
 */
class AuthTranslationsTest {

    private val resourceDirectory = File("src/main/res")

    private fun stringsFor(language: String): File =
        File(resourceDirectory, (if (language == "en") "values" else "values-$language") + "/strings.xml")

    private fun keysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `no language is missing a key the default file declares`() {
        val expected = keysIn(stringsFor("en"))
        assertTrue(expected.isNotEmpty())

        for (language in SupportedLocales.supported) {
            val file = stringsFor(language)
            assertTrue("Missing ${file.path}", file.exists())
            assertEquals(
                "Keys missing from ${file.path} — they would silently render in English.",
                emptySet<String>(),
                expected - keysIn(file),
            )
        }
    }

    /**
     * The reveal toggle's two labels must differ in every language. They are the
     * only thing a TalkBack user hears about that button, and a copy-paste that
     * leaves both saying "show password" makes it impossible to know the current
     * state without looking.
     */
    @Test
    fun `the reveal toggle says something different for each state`() {
        for (language in SupportedLocales.supported) {
            val text = stringsFor(language).readText()
            val show = Regex("""<string name="login_password_show">([^<]+)<""").find(text)?.groupValues?.get(1)
            val hide = Regex("""<string name="login_password_hide">([^<]+)<""").find(text)?.groupValues?.get(1)

            assertTrue("login_password_show missing in $language", show != null)
            assertTrue("login_password_hide missing in $language", hide != null)
            assertTrue("Show and hide read identically in $language", show != hide)
        }
    }
}
