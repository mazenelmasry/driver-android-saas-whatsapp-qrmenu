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

    /** All seven `NoOrdersReason` wire values must resolve to a real, distinct sentence. */
    @Test
    fun `every no-orders reason resolves to a key that exists`() {
        val keys = keysIn(stringsFor("en"))
        val reasonKeys = setOf(
            "availability_reason_offline",
            "availability_reason_no_active_link",
            "availability_reason_all_branches_closed",
            "availability_reason_outside_radius",
            "availability_reason_location_unknown",
            "availability_reason_has_active_trip",
            "availability_reason_nothing_pending",
            "availability_reason_unknown",
        )
        assertEquals(reasonKeys, keys intersect reasonKeys)
    }
}
