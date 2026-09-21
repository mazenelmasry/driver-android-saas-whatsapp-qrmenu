package app.qrmenu.driver.common.session

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * When a driver session token is spent.
 *
 * Pure JVM on purpose (no Context, no AndroidX) so it is unit-testable without
 * Robolectric — the same reason [app.qrmenu.driver.common.locale.SupportedLocales]
 * lives here rather than in `:core:datastore`. The store that holds the token
 * ([app.qrmenu.driver.datastore.TokenStore]) needs `EncryptedSharedPreferences`
 * and therefore a device; the RULE about when that token is dead does not, and
 * it is the part worth a test.
 *
 * CLAUDE.md: the token lives **30 days and renews on use**. So "expired" is
 * COMPUTED from the stored instant against the clock — never a boolean written
 * down at login, which would be a lie the moment the app is reopened the next
 * day.
 */
object TokenExpiry {

    /** The contract's token lifetime. Renewal is the server's business; this is the ceiling. */
    const val LIFETIME_DAYS: Long = 30

    /**
     * Renew this far ahead of the deadline rather than at it.
     *
     * A driver whose token dies mid-shift is thrown back to the phone-and-PIN
     * screen while holding an order — and possibly while holding the customer's
     * cash. Renewing a day early costs one extra request; renewing at the
     * deadline costs a trip.
     */
    const val RENEW_WITHIN_MILLIS: Long = 24 * 60 * 60 * 1000L

    /**
     * True when there is no usable session.
     *
     * A null [expiresAtMillis] is treated as **live**, not expired: the server
     * is the authority on a token's validity, and a response that simply did
     * not carry an expiry must not log the driver out of a working session. A
     * genuinely dead token comes back as 401 and is cleared then.
     */
    fun isExpired(expiresAtMillis: Long?, nowMillis: Long): Boolean =
        expiresAtMillis != null && nowMillis >= expiresAtMillis

    /** True when the session still works but should be refreshed on the next call. */
    fun needsRenewal(
        expiresAtMillis: Long?,
        nowMillis: Long,
        withinMillis: Long = RENEW_WITHIN_MILLIS,
    ): Boolean {
        if (expiresAtMillis == null) {
            return false
        }

        return !isExpired(expiresAtMillis, nowMillis) &&
            expiresAtMillis - nowMillis <= withinMillis
    }

    /** Whole days left, floored, for the settings screen. Zero once expired. */
    fun daysRemaining(expiresAtMillis: Long?, nowMillis: Long): Long? {
        if (expiresAtMillis == null) {
            return null
        }

        val remaining = expiresAtMillis - nowMillis

        return if (remaining <= 0) 0 else remaining / (24 * 60 * 60 * 1000L)
    }

    /**
     * Reads the contract's `expires_at` — ISO-8601 WITH a UTC offset (e.g.
     * `2026-10-21T12:00:00+03:00`), never a bare `Z`-less local time.
     *
     * Null or unparseable both return `null`, which [isExpired] already treats
     * as "no expiry known ⇒ alive" (see its doc) — a server that sent a value
     * this build cannot read must not be able to sign a driver out of a working
     * session; a genuinely dead token still comes back as 401 and is cleared
     * then.
     */
    fun parseExpiresAt(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
