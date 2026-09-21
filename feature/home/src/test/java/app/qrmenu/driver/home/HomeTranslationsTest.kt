package app.qrmenu.driver.home

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen exists in all five languages.
 *
 * A missing key does not fail the build — Android falls back to English — and
 * this is the first screen every driver lands on after signing in.
 */
class HomeTranslationsTest {

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
     * The three link statuses must read as three different sentences, not a
     * copy-paste of the same word three times — that is the one thing standing
     * between a driver understanding why no orders are arriving and not.
     */
    @Test
    fun `the three link statuses are worded differently`() {
        for (language in SupportedLocales.supported) {
            val text = stringsFor(language).readText()
            fun value(key: String) = Regex("""<string name="$key">([^<]+)<""").find(text)?.groupValues?.get(1)

            val active = value("home_status_active")
            val invited = value("home_status_invited")
            val suspended = value("home_status_suspended")

            assertTrue("home_status_active missing in $language", active != null)
            assertTrue("home_status_invited missing in $language", invited != null)
            assertTrue("home_status_suspended missing in $language", suspended != null)
            assertTrue(
                "The three statuses read identically in $language",
                setOf(active, invited, suspended).size == 3,
            )
        }
    }
}
