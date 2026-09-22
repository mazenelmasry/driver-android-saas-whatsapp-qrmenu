package app.qrmenu.driver.trip.outbox

/**
 * Asks for the outbox to be drained once there is a network again.
 *
 * An interface, not the WorkManager class itself, for one reason that matters:
 * [app.qrmenu.driver.trip.TripRepository] must be able to prove in a plain JVM
 * test that a transient failure actually SCHEDULES a retry. That is the whole
 * behaviour — a row left queued with nothing coming back for it is the bug,
 * not the fix — and a concrete scheduler needing a `Context` would push the
 * only test of it onto a device, where it would not get written.
 */
interface OutboxFlushScheduler {
    fun scheduleFlush()
}
