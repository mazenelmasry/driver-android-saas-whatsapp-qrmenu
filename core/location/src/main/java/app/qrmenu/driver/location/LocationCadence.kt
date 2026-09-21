package app.qrmenu.driver.location

/**
 * The four states named in CLAUDE.md § الموقع فى الخلفية, as a closed type so a
 * caller cannot invent a fifth tier by accident.
 */
enum class DriverLocationState {
    /** Available and the fused speed reading is above [LocationConstants.MOVING_SPEED_THRESHOLD_MPS]. */
    MOVING,

    /** Available, working an active trip, but not currently moving. */
    STOPPED,

    /** Available, no active trip, not moving. */
    AVAILABLE_IDLE,

    /** Not available. The only state in which nothing is sampled at all. */
    UNAVAILABLE,
}

/**
 * Picks the sampling interval for a [DriverLocationState] — the ONE place that
 * maps CLAUDE.md's four cadence tiers to milliseconds. [LocationCadencePolicy]
 * (below) is what actually derives the state from raw inputs; this function is
 * kept separate and pure so a unit test can assert the mapping without also
 * having to fake a GPS reading.
 *
 * @return `null` for [DriverLocationState.UNAVAILABLE] — "no interval" IS the
 *   promise made on the permission screen: an unavailable driver is not tracked
 *   at all, not tracked slowly.
 */
fun DriverLocationState.samplingIntervalMillis(): Long? = when (this) {
    DriverLocationState.MOVING -> LocationConstants.MOVING_INTERVAL_MS
    DriverLocationState.STOPPED -> LocationConstants.STOPPED_INTERVAL_MS
    DriverLocationState.AVAILABLE_IDLE -> LocationConstants.AVAILABLE_IDLE_INTERVAL_MS
    DriverLocationState.UNAVAILABLE -> null
}

/**
 * Derives [DriverLocationState] from the three raw facts the service actually
 * has: is the driver available at all, are they working an active trip right
 * now, and does the last fused-location sample say they are moving.
 *
 * Kept as a plain object function (no Android types) so it is unit-testable on
 * the JVM with zero mocking — see `LocationCadencePolicyTest`.
 */
object LocationCadencePolicy {

    fun resolve(
        isAvailable: Boolean,
        hasActiveTrip: Boolean,
        isMoving: Boolean,
    ): DriverLocationState = when {
        !isAvailable -> DriverLocationState.UNAVAILABLE
        isMoving -> DriverLocationState.MOVING
        hasActiveTrip -> DriverLocationState.STOPPED
        else -> DriverLocationState.AVAILABLE_IDLE
    }

    /** Convenience: resolve straight to milliseconds, or `null` to mean "stop tracking". */
    fun resolveIntervalMillis(
        isAvailable: Boolean,
        hasActiveTrip: Boolean,
        isMoving: Boolean,
    ): Long? = resolve(isAvailable, hasActiveTrip, isMoving).samplingIntervalMillis()

    /** A fused-location speed reading (m/s) classified against the frozen threshold. */
    fun isMoving(speedMetersPerSecond: Float?): Boolean =
        (speedMetersPerSecond ?: 0f) >= LocationConstants.MOVING_SPEED_THRESHOLD_MPS
}
