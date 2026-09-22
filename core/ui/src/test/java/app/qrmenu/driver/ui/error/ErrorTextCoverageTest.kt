package app.qrmenu.driver.ui.error

import app.qrmenu.driver.common.locale.SupportedLocales
import app.qrmenu.driver.network.errors.DriverErrorCode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every error code the server can send has a sentence, in every language the
 * app ships.
 *
 * This is the guard behind the rule that the app translates BY CODE: a code with
 * no string of its own falls through to the generic line, and a code whose
 * string exists only in English reaches an Urdu-reading driver in English. Both
 * failures are silent — nothing crashes, the driver is simply not told what
 * happened — so they have to be caught here.
 */
class ErrorTextCoverageTest {

    private val resourceDirectory = File("src/main/res")

    private fun stringsFor(language: String): File =
        File(resourceDirectory, (if (language == "en") "values" else "values-$language") + "/strings.xml")

    private fun keysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    /**
     * Derived from the enum, so adding a code to the frozen contract list
     * without writing its sentence fails here rather than in a driver's hand.
     */
    private val requiredKeys: Set<String> =
        DriverErrorCode.entries.map { "error_${it.wire}" }.toSet() +
            setOf("error_network_unavailable", "error_unknown", "action_retry")

    @Test
    fun `every error code has a string in every supported language`() {
        for (language in SupportedLocales.supported) {
            val file = stringsFor(language)
            assertTrue("Missing ${file.path}", file.exists())
            assertEquals(
                "Missing error strings in ${file.path} — a driver reading '$language' would be told nothing useful.",
                emptySet<String>(),
                requiredKeys - keysIn(file),
            )
        }
    }

    /**
     * Every resource is named `error_<wire>`, mechanically — so the mapping
     * cannot quietly diverge from the contract's own vocabulary the way it did
     * the first time this was written (`error_server` against the code
     * `server_error`, which read fine and covered nothing).
     */
    @Test
    fun `the contract's codes are all mapped`() {
        assertEquals(29, DriverErrorCode.entries.size)
        val declared = keysIn(stringsFor("en"))
        for (code in DriverErrorCode.entries) {
            assertTrue("No string for '${code.wire}'", "error_${code.wire}" in declared)
        }
    }

    @Test
    fun `non-latin languages are actually translated`() {
        val englishValues = Regex("""<string name="[^"]+">([^<]+)<""")
            .findAll(stringsFor("en").readText()).map { it.groupValues[1] }.toSet()

        for (language in listOf("ar", "ur", "bn", "hi")) {
            val values = Regex("""<string name="[^"]+">([^<]+)<""")
                .findAll(stringsFor(language).readText()).map { it.groupValues[1] }.toSet()
            assertEquals(
                "Untranslated strings left in values-$language.",
                emptySet<String>(),
                values intersect englishValues,
            )
        }
    }
}
