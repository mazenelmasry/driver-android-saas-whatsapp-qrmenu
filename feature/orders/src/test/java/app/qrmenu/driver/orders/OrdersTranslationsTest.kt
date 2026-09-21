package app.qrmenu.driver.orders

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `:feature:availability`'s `AvailabilityTranslationsTest` — same rule, same risk. */
class OrdersTranslationsTest {

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
