package app.qrmenu.driver.push

import java.time.Instant
import java.time.OffsetDateTime

/**
 * A pure, defensive parse of the FCM data message the backend sends for a
 * driver offer. This process may have just been woken from death by the
 * push itself, so nothing here does I/O — it only turns the raw
 * String→String map FCM hands `onMessageReceived` into something the caller
 * can trust the shape of.
 *
 * The five keys (`type`, `order_id`, `offer_id`, `wave`, `expires_at`) are
 * frozen with the backend agent building the send side against this same
 * list — do not rename or add without updating both.
 */
data class OfferPushPayload(
    val orderId: Long,
    val offerId: Long,
    val wave: Int,
    val expiresAt: Instant,
) {

    /**
     * `expiresAt` is absolute and server-owned — it is the offer's actual
     * deadline, not a hint. A push that lands after it must not ring: the
     * project owner named "a full-screen takeover for an offer that already
     * went to another driver" as strictly worse than staying silent.
     */
    fun isLive(now: Instant): Boolean = now.isBefore(expiresAt)

    companion object {
        private const val TYPE_OFFER = "driver_offer"

        private const val KEY_TYPE = "type"
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_OFFER_ID = "offer_id"
        private const val KEY_WAVE = "wave"
        private const val KEY_EXPIRES_AT = "expires_at"

        /** Stands in for an absent `wave`; never shown, never compared. */
        internal const val UNKNOWN_WAVE = 0

        /**
         * 🔴 Offset-tolerant on purpose, and this is not defensive padding.
         * The backend serialises with Laravel's `toIso8601String()` under
         * `APP_TIMEZONE=Asia/Riyadh`, so the value on the wire is
         * `2026-09-21T15:04:05+03:00` — WITH a numeric offset, not `Z`.
         * `Instant.parse` uses `ISO_INSTANT`, which accepts a numeric offset
         * only from JDK 12 onward; on the older semantics it throws. This
         * module builds with `isCoreLibraryDesugaringEnabled = false`, so
         * which behaviour applies is decided by each DEVICE's platform
         * java.time — and the driver this app is for is on a cheap, old
         * phone. Relying on `Instant.parse` alone means every push silently
         * fails to parse and NEVER rings, on exactly the handsets least able
         * to spare a lost order, with no error surfaced anywhere.
         */
        internal fun parseInstant(raw: String): Instant? =
            runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(raw) }.getOrNull()

        /**
         * Returns null for anything that is not a well-formed offer push:
         * a wrong/missing `type`, or a missing/unparsable `order_id`,
         * `offer_id` or `expires_at` — the three the ring decision actually
         * needs. FCM's data map has no numeric or date type: every value
         * arrives as a string, so every field is parsed defensively rather
         * than trusted.
         */
        fun from(data: Map<String, String>): OfferPushPayload? {
            if (data[KEY_TYPE] != TYPE_OFFER) {
                return null
            }

            val orderId = data[KEY_ORDER_ID]?.trim()?.toLongOrNull() ?: return null
            val offerId = data[KEY_OFFER_ID]?.trim()?.toLongOrNull() ?: return null
            // 🔴 `wave` must NEVER be able to reject the payload. It is
            // cosmetic here — nothing in the ring decision reads it — and the
            // contract itself declares `Offer.wave` nullable. Dropping a LIVE
            // offer because an optional field was absent would lose the
            // driver a real order to defend a number nobody displays.
            val wave = data[KEY_WAVE]?.trim()?.toIntOrNull() ?: UNKNOWN_WAVE
            val expiresAt = data[KEY_EXPIRES_AT]?.trim()?.let(::parseInstant) ?: return null

            return OfferPushPayload(
                orderId = orderId,
                offerId = offerId,
                wave = wave,
                expiresAt = expiresAt,
            )
        }
    }
}
