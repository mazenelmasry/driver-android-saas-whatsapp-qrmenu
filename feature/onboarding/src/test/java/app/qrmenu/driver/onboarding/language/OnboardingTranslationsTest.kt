package app.qrmenu.driver.onboarding.language

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every string in this module exists in all five languages.
 *
 * A missing translation does not fail the build — Android silently falls back
 * to the default (English) file. On the language picker of all screens that is
 * the worst possible failure: a driver who chose Bengali because they read
 * nothing else would be shown English, on the one screen whose entire purpose
 * is to prevent that.
 *
 * The XML is read as text rather than through the resource system because these
 * are JVM unit tests with no Android runtime. It is crude, and it is enough:
 * what is being asserted is that the keys are present, not how they render.
 */
class OnboardingTranslationsTest {

    private val resourceDirectory = File("src/main/res")

    private fun stringsFor(language: String): File {
        // `values/` is the default and holds English (decision 8 lists `en`
        // among the five, so it needs no separate values-en).
        val directory = if (language == "en") "values" else "values-$language"
        return File(resourceDirectory, "$directory/strings.xml")
    }

    private fun keysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `every supported language has a strings file`() {
        for (language in SupportedLocales.supported) {
            val file = stringsFor(language)
            assertTrue("Missing ${file.path} — '$language' would fall back to English.", file.exists())
        }
    }

    @Test
    fun `no language is missing a key the default file declares`() {
        val expected = keysIn(stringsFor("en"))
        assertTrue("The default strings file declares nothing.", expected.isNotEmpty())

        for (language in SupportedLocales.supported) {
            assertEquals(
                "Keys missing from ${stringsFor(language).path} — they would silently render in English.",
                emptySet<String>(),
                expected - keysIn(stringsFor(language)),
            )
        }
    }

    /**
     * A "translation" that is byte-identical to the English one is almost always
     * a forgotten copy-paste. Checked only for the scripts where a genuine match
     * is impossible — Arabic, Urdu, Bengali and Hindi do not write "Continue" in
     * Latin letters.
     */
    @Test
    fun `non-latin languages are actually translated, not copied`() {
        val english = File(resourceDirectory, "values/strings.xml").readText()
        val englishValues = Regex("""<string name="[^"]+">([^<]+)<""").findAll(english)
            .map { it.groupValues[1] }.toSet()

        for (language in listOf("ar", "ur", "bn", "hi")) {
            val values = Regex("""<string name="[^"]+">([^<]+)<""")
                .findAll(stringsFor(language).readText())
                .map { it.groupValues[1] }.toSet()

            assertEquals(
                "Untranslated strings left in values-$language.",
                emptySet<String>(),
                values intersect englishValues,
            )
        }
    }
}
