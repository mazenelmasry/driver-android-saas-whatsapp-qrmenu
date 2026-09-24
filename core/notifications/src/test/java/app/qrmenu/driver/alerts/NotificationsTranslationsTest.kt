package app.qrmenu.driver.alerts

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `:core:notifications` had NO `values-ur`/`values-bn`/`values-hi` at all
 * before this — every notification channel name/description and health
 * banner string silently rendered in English for those three languages.
 * Mirrors `:feature:account`'s `AccountTranslationsTest` — same rule, same risk.
 */
class NotificationsTranslationsTest {

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
}
