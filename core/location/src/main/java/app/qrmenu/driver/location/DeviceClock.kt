package app.qrmenu.driver.location

import java.time.OffsetDateTime

/**
 * `recorded_at` must be the DEVICE's clock, in ISO-8601 WITH offset — CLAUDE.md:
 * "`occurred_at` من الجهاز مقبول ومقصوص بين (الآن − ٦ ساعات) والآن". The server
 * does the clamping; this module's only job is to send the real device time it
 * captured the fix at, never `now()` re-stamped at upload time — a batch sent
 * after 10 minutes offline must still carry each point's true original moment.
 */
fun deviceTimeIso8601WithOffset(): String = OffsetDateTime.now().toString()
