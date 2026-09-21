package app.qrmenu.driver.availability

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This is the single most-used screen in the app (CLAUDE.md) — it exists in
 * all five languages, or a missing key silently falls back to English.
 */
class AvailabilityTranslationsTest {

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
     * Online and offline must read as opposites, never a copy-paste of one
     * word — this is the glossary term (CLAUDE.md § مسرد المصطلحات) drivers see
     * more than any other in the app.
     */
    @Test
    fun `available and offline are worded differently in every language`() {
        for (language in SupportedLocales.supported) {
            val text = stringsFor(language).readText()
            fun value(key: String) = Regex("""<string name="$key">([^<]+)<""").find(text)?.groupValues?.get(1)

            val online = value("availability_switch_online")
            val offline = value("availability_switch_offline")

            assertTrue("availability_switch_online missing in $language", online != null)
            assertTrue("availability_switch_offline missing in $language", offline != null)
            assertTrue("online/offline read identically in $language", online != offline)
        }
    }
}
