package app.qrmenu.driver.push

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferPushPayloadTest {

    private fun validData(overrides: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "type" to "driver_offer",
            "order_id" to "123",
            "offer_id" to "456",
            "wave" to "1",
            "expires_at" to "2026-09-21T12:00:00Z",
        ) + overrides

    @Test
    fun `a well-formed payload parses`() {
        val payload = OfferPushPayload.from(validData())

        assertNotNull(payload)
        assertEquals(123L, payload!!.orderId)
        assertEquals(456L, payload.offerId)
        assertEquals(1, payload.wave)
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), payload.expiresAt)
    }

    @Test
    fun `a wrong type returns null`() {
        assertNull(OfferPushPayload.from(validData(mapOf("type" to "something_else"))))
    }

    @Test
    fun `a missing type returns null`() {
        assertNull(OfferPushPayload.from(validData().minus("type")))
    }

    @Test
    fun `a missing order_id returns null`() {
        assertNull(OfferPushPayload.from(validData().minus("order_id")))
    }

    @Test
    fun `a garbage order_id returns null`() {
        assertNull(OfferPushPayload.from(validData(mapOf("order_id" to "not-a-number"))))
    }

    @Test
    fun `a missing offer_id returns null`() {
        assertNull(OfferPushPayload.from(validData().minus("offer_id")))
    }

    @Test
    fun `a garbage offer_id returns null`() {
        assertNull(OfferPushPayload.from(validData(mapOf("offer_id" to "abc"))))
    }

    @Test
    fun `a missing wave still parses - it must never cost the driver the offer`() {
        // `wave` is cosmetic and the contract declares `Offer.wave` nullable.
        // Rejecting a LIVE offer over an absent optional field would lose a
        // real order to defend a number no screen displays.
        val payload = OfferPushPayload.from(validData().minus("wave"))

        assertNotNull(payload)
        assertEquals(OfferPushPayload.UNKNOWN_WAVE, payload!!.wave)
    }

    @Test
    fun `a garbage wave still parses`() {
        val payload = OfferPushPayload.from(validData(mapOf("wave" to "many")))

        assertNotNull(payload)
        assertEquals(OfferPushPayload.UNKNOWN_WAVE, payload!!.wave)
    }

    @Test
    fun `a missing expires_at returns null`() {
        assertNull(OfferPushPayload.from(validData().minus("expires_at")))
    }

    @Test
    fun `an unparsable expires_at returns null`() {
        assertNull(OfferPushPayload.from(validData(mapOf("expires_at" to "not-a-date"))))
    }

    @Test
    fun `isLive is true strictly before expires_at`() {
        val payload = OfferPushPayload.from(validData())!!
        assertTrue(payload.isLive(payload.expiresAt.minusSeconds(1)))
    }

    @Test
    fun `isLive is false exactly at expires_at`() {
        val payload = OfferPushPayload.from(validData())!!
        assertFalse(payload.isLive(payload.expiresAt))
    }

    @Test
    fun `isLive is false after expires_at`() {
        val payload = OfferPushPayload.from(validData())!!
        assertFalse(payload.isLive(payload.expiresAt.plusSeconds(1)))
    }

    @Test
    fun `surrounding whitespace and extra unknown keys do not break parsing`() {
        val data = validData(
            mapOf(
                "order_id" to " 123 ",
                "offer_id" to " 456 ",
                "wave" to " 1 ",
                "expires_at" to " 2026-09-21T12:00:00Z ",
                "a_future_field_this_app_does_not_know_about" to "surprise",
            ),
        )

        val payload = OfferPushPayload.from(data)

        assertNotNull(payload)
        assertEquals(123L, payload!!.orderId)
        assertEquals(456L, payload.offerId)
    }

    /**
     * 🔴 The REAL shape on the wire. Laravel's `toIso8601String()` under
     * `APP_TIMEZONE=Asia/Riyadh` emits a numeric offset, never `Z`. A parser
     * that only accepts `Z` drops every push on any device whose platform
     * `java.time` predates the JDK 12 relaxation of `ISO_INSTANT` — silently,
     * and on exactly the cheap handsets this app is built for.
     */
    @Test
    fun `the offset form the backend actually sends parses to the same instant`() {
        val payload = OfferPushPayload.from(
            validData(mapOf("expires_at" to "2026-09-21T15:00:00+03:00")),
        )

        assertNotNull(payload)
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), payload!!.expiresAt)
    }

    @Test
    fun `a negative offset parses too`() {
        val payload = OfferPushPayload.from(
            validData(mapOf("expires_at" to "2026-09-21T07:00:00-05:00")),
        )

        assertNotNull(payload)
        assertEquals(Instant.parse("2026-09-21T12:00:00Z"), payload!!.expiresAt)
    }
}
