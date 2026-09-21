package app.qrmenu.driver.offers

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferInstantsTest {

    @Test
    fun `parses a Z-suffixed UTC instant`() {
        val parsed = parseOfferInstant("2026-09-21T12:04:05Z")

        assertEquals(Instant.parse("2026-09-21T12:04:05Z"), parsed)
    }

    @Test
    fun `parses a numeric-offset instant the way the backend actually sends it`() {
        // What Laravel's toIso8601String() produces under APP_TIMEZONE=Asia/Riyadh
        // — a bare Instant.parse() rejects this under pre-JDK-12 java.time
        // semantics, which is exactly the bug this helper exists to close.
        val parsed = parseOfferInstant("2026-09-21T15:04:05+03:00")

        assertEquals(Instant.parse("2026-09-21T12:04:05Z"), parsed)
    }

    @Test
    fun `garbage input returns null rather than throwing`() {
        assertNull(parseOfferInstant("not a timestamp"))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(parseOfferInstant(""))
    }
}
