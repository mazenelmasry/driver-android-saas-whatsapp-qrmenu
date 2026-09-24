package app.qrmenu.driver.ui.orders

import app.qrmenu.driver.common.locale.SupportedLocales
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision 47 in test form: a driver staring at an empty list must be told
 * WHY, in their own language. A reason that maps to a missing key silently
 * renders in English — which, for the one screen that exists to explain
 * something, is the same failure as saying nothing.
 */
class NoOrdersReasonTest {

    private val resourceDirectory = File("src/main/res")

    private fun stringsFor(language: String): File =
        File(resourceDirectory, (if (language == "en") "values" else "values-$language") + "/strings.xml")

    private fun keysIn(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    /** The eight values the contract's `AvailabilityContext.reason` enum lists. */
    @Test
    fun `every wire value the server can send maps to a reason`() {
        val wire = listOf(
            "offline",
            "no_active_link",
            "all_branches_closed",
            "outside_radius",
            "location_unknown",
            "location_stale",
            "has_active_trip",
            "nothing_pending",
        )

        for (value in wire) {
            assertNotNull("No reason for wire value '$value'", NoOrdersReason.fromWire(value))
            assertEquals(value, NoOrdersReason.fromWire(value)?.wire)
        }
    }

    /**
     * [LocationUnknown] ("never arrived, grant a permission") and [LocationStale]
     * ("stopped arriving, reopen the app") are different faults with different fixes.
     * Sharing a sentence would send half these drivers to the wrong one.
     */
    @Test
    fun `location unknown and location stale render different messages`() {
        val unknown = NoOrdersReason.fromWire("location_unknown")
        val stale = NoOrdersReason.fromWire("location_stale")

        assertNotNull(unknown)
        assertNotNull(stale)
        assertTrue(unknown != stale)
        assertTrue(unknown!!.messageResource() != stale!!.messageResource())
    }

    /** A reason this build has never heard of must still say something honest. */
    @Test
    fun `an unknown wire value degrades instead of disappearing`() {
        assertEquals(NoOrdersReason.Unknown, NoOrdersReason.fromWire("some_future_reason"))
    }

    /** "We don't know yet" is not a reason to render. */
    @Test
    fun `a null wire value stays null`() {
        assertNull(NoOrdersReason.fromWire(null))
    }

    @Test
    fun `every reason resolves to a key that exists in all five languages`() {
        val byLanguage = SupportedLocales.supported.associateWith { keysIn(stringsFor(it)) }

        for (language in SupportedLocales.supported) {
            assertTrue("Missing strings for $language", byLanguage.getValue(language).isNotEmpty())
        }

        for (reason in NoOrdersReason.entries) {
            val key = "availability_reason_" + reason.name
                .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .lowercase()

            for ((language, keys) in byLanguage) {
                assertTrue("$key missing from $language — it would render in English", key in keys)
            }
        }
    }
}
