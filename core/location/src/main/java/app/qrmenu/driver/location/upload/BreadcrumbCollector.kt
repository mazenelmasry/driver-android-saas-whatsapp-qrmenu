package app.qrmenu.driver.location.upload

import app.qrmenu.driver.location.DriverTripActivityState
import app.qrmenu.driver.location.LocationConstants
import app.qrmenu.driver.network.dto.LocationPointDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The in-memory side of the trip breadcrumb trail — the durable side is
 * [BreadcrumbOutbox]. [record] is called from
 * [app.qrmenu.driver.location.DriverLocationService]'s existing
 * `LocationCallback` — the SAME fix already queued for the live
 * `driver/location` feed via [LocationPointBatcher] — so collecting
 * breadcrumbs starts NO second GPS request and costs no extra battery beyond
 * one more in-memory list append per fix.
 *
 * ## Why this exists apart from [BreadcrumbOutbox]
 * [BreadcrumbOutbox] only knows how to persist and send a batch it is HANDED —
 * it has no opinion on when one is ready. This class owns exactly that: it
 * buffers points for the order [DriverTripActivityState.activeOrderId] names,
 * and flushes the buffer (writing one durable outbox row + one best-effort
 * send attempt, via [flushNow]) when:
 *
 *  - the buffer reaches [LocationConstants.MAX_BREADCRUMB_BATCH_POINTS] (the
 *    contract's own per-request cap),
 *  - [LocationConstants.BREADCRUMB_FLUSH_INTERVAL_MS] has passed (task
 *    brief's "periodically"), or
 *  - the active order CHANGES — including to `null`, i.e. the trip ends
 *    (task brief's "on delivery") — so a trip's tail points are never left
 *    sitting in memory only, waiting for a batch that will never fill.
 *
 * [record]'s own buffer access is `@Synchronized` (same pattern as
 * [LocationPointBatcher]) rather than a suspending `Mutex`, precisely so it
 * can be called from [app.qrmenu.driver.location.DriverLocationService]'s
 * plain (non-suspend) `LocationCallback` without needing a coroutine per fix;
 * only the actual send — [flushNow], which does Room + network I/O — is
 * suspend, and only runs on this class's own background scope.
 */
@Singleton
class BreadcrumbCollector @Inject constructor(
    private val tripActivity: DriverTripActivityState,
    private val outbox: BreadcrumbOutbox,
) {
    private val pending = mutableListOf<LocationPointDto>()
    private var pendingOrderId: Long? = null

    // A small app-scoped supervisor scope, same shape as every other
    // singleton in this codebase that owns a periodic loop without an
    // injected application scope (see NotificationHistoryStore's own doc on
    // why — this module is the other deliberate instance of that choice).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 🔴 Read SYNCHRONOUSLY, in the constructor, not inside the `collect`
    // below. A `StateFlow` always replays its CURRENT value the instant
    // something subscribes, but that subscription happens asynchronously on
    // [scope] — by the time it actually runs, [tripActivity] may already have
    // moved on (a real trip start/end can easily land in the gap between
    // this object being constructed and its background coroutine actually
    // being scheduled). Comparing every subsequent emission against this
    // synchronous snapshot — taken at the one moment this class can read it
    // without any race — rather than against "whatever the first emission
    // happens to be" is what makes the boundary listener below correct
    // regardless of how fast or slow [scope] gets around to starting: a
    // trip that started and ended entirely before collection even began is
    // still detected as a transition (snapshot ≠ first emission), and a
    // subscription that catches up to a value already equal to the snapshot
    // correctly reports no transition at all.
    private var lastKnownOrderId: Long? = tripActivity.activeOrderId.value

    init {
        scope.launch {
            tripActivity.activeOrderId.collect { newOrderId ->
                val previousOrderId = lastKnownOrderId
                lastKnownOrderId = newOrderId
                // The trip just changed (including ending) — flush whatever
                // was buffered under whichever order this collector was
                // tracking, unless it already matches the new one (a
                // same-order re-emission, or nothing was pending).
                if (previousOrderId != null && previousOrderId != newOrderId) {
                    flushNow()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(LocationConstants.BREADCRUMB_FLUSH_INTERVAL_MS)
                flushNow()
                // Retries whatever a PREVIOUS attempt (this run's or a killed
                // one's) left queued — see
                // [BreadcrumbOutbox.flushQueuedForCurrentDriver]'s own doc.
                // [BreadcrumbOutbox.attemptSend] already turns every per-row
                // failure into a recorded, still-queued row — nothing
                // legitimate throws out of this call except cancellation,
                // which is left to propagate and end this loop.
                outbox.flushQueuedForCurrentDriver()
            }
        }
    }

    /**
     * `orderId` is read by the CALLER (see [DriverLocationService]) rather
     * than by this function, so a fix that arrived while no trip was active
     * is dropped before it is ever queued — this function only ever sees a
     * point that is meant to be collected.
     */
    @Synchronized
    fun record(orderId: Long, point: LocationPointDto) {
        if (pendingOrderId != null && pendingOrderId != orderId) {
            // A trip switched under us without the boundary listener above
            // having run yet — flush the OLD batch first rather than either
            // losing it or mixing two orders' points into one row.
            val previousOrderId = pendingOrderId!!
            val previousBatch = pending.toList()
            pending.clear()
            scope.launch { outbox.enqueueAndSend(previousOrderId, previousBatch) }
        }
        pendingOrderId = orderId
        pending.add(point)
        if (pending.size >= LocationConstants.MAX_BREADCRUMB_BATCH_POINTS) {
            scope.launch { flushNow() }
        }
    }

    /**
     * Flushes whatever is currently buffered — used by the timer, the
     * trip-boundary listener, and exposed (rather than `private`) purely so a
     * test can drive it deterministically instead of waiting on
     * [LocationConstants.BREADCRUMB_FLUSH_INTERVAL_MS] of real time.
     */
    suspend fun flushNow() {
        val orderId: Long
        val batch: List<LocationPointDto>
        synchronized(this) {
            val pendingOrder = pendingOrderId
            if (pendingOrder == null || pending.isEmpty()) {
                pendingOrderId = null
                return
            }
            orderId = pendingOrder
            batch = pending.toList()
            pending.clear()
            pendingOrderId = null
        }
        outbox.enqueueAndSend(orderId, batch)
    }
}
