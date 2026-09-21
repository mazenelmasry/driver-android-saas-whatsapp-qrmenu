package app.qrmenu.driver.location

/**
 * Every timing number this module uses, in one place, each tied back to the
 * frozen decision in the driver `CLAUDE.md` § الموقع فى الخلفية:
 *
 * > التردد: متحرك 5–10ث · متوقف 30ث · متاح بلا طلب 60ث · غير متاح = لا تتبّع إطلاقاً
 * > الإرسال مجمَّع كل 15ث
 *
 * Nothing here is invented — the four cadence tiers and the 15s upload cadence
 * are the frozen numbers; only the exact point picked inside the "5–10s" RANGE
 * is this module's choice, and that choice is documented below.
 */
object LocationConstants {

    /**
     * "متحرك" (moving). The decision gives a 5–10s RANGE, not a single number —
     * 7s is the midpoint, chosen so that FusedLocationProviderClient's own
     * scheduling jitter (it is a request, not a guarantee) still lands inside
     * the 5–10s window on either side.
     */
    const val MOVING_INTERVAL_MS: Long = 7_000L

    /** "متوقف" — available, working an active trip, but not currently moving
     *  (e.g. waiting at the restaurant counter). */
    const val STOPPED_INTERVAL_MS: Long = 30_000L

    /** "متاح بلا طلب" — online, no active trip, not moving. The cheapest tier
     *  that still proves the driver is alive for dispatch. */
    const val AVAILABLE_IDLE_INTERVAL_MS: Long = 60_000L

    /**
     * Speed threshold that separates [MOVING_INTERVAL_MS] from the other two
     * tiers. 1.5 m/s (~5.4 km/h) is a brisk walking pace — chosen deliberately
     * ABOVE GPS jitter noise (a stationary phone's fused speed reading is
     * usually well under 1 m/s) so a driver parked at a red light is not
     * mis-classified as "moving" by sensor noise alone.
     */
    const val MOVING_SPEED_THRESHOLD_MPS: Float = 1.5f

    /** "الإرسال مجمَّع كل 15ث" — one batched POST, not one request per point. */
    const val UPLOAD_INTERVAL_MS: Long = 15_000L

    /**
     * The contract caps a batch at 60 points (`openapi/driver.v1.yaml`). This
     * module additionally uses it as the LOCAL queue cap while offline — see
     * [LocationPointBatcher] for what happens when more than 60 accumulate
     * before a connection returns.
     */
    const val MAX_BATCH_POINTS: Int = 60

    /**
     * Server-side heartbeat timeout (CLAUDE.md: "انقطاع 3 دقائق ⇒ الخادم يُطفئ
     * التوفّر"). Not used to gate anything client-side — the app does not stop
     * trying just because the server may have already given up — but kept here
     * so the "الاتصال منقطع" UI copy and this module's retry logic are read
     * against the same number a reviewer would check the backend against.
     */
    const val SERVER_HEARTBEAT_TIMEOUT_MS: Long = 180_000L
}
