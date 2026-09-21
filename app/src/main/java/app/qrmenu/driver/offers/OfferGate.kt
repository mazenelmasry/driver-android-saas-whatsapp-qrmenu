package app.qrmenu.driver.offers

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single answer to "has this offer already been raised?" — asked by BOTH
 * arms (the FCM push in [DriverPushHandler] and the 15-second poll in
 * `PendingOfferViewModel`) before either is allowed to ring the driver.
 *
 * Process-wide and singleton on purpose: a `ViewModel`-scoped set (what
 * `PendingOfferViewModel` held before this class existed) dies with the
 * screen, and the push arm can ring from a process the poll's `ViewModel`
 * was never even constructed in. Only something that outlives both can stop
 * them producing two rings for one offer.
 *
 * Keyed on `offerId`, not `orderId` — a later wave of the SAME order is a
 * NEW offer the driver must still get (a fresh `offer_id`, a fresh 45s
 * window), so collapsing on the order would silently eat a legitimate
 * re-offer. [resolve] is what makes that safe: once an offer is answered or
 * expires, its id is forgotten, and the next wave's id is unseen and free to
 * raise.
 *
 * Bounded on purpose ([evictIfOverCapacity]): this object lives for the
 * whole process, and a driver who is never rung twice for the SAME offer id
 * (because [resolve] is always called) will still, over a long shift,
 * accumulate ids in [pending] if something upstream forgets to resolve one.
 * Capping and evicting the oldest entries when that happens is cheap
 * insurance against an unbounded process-lifetime leak — it does not need to
 * be exact, only to stop growing forever.
 */
@Singleton
class OfferGate @Inject constructor() {

    private val lock = Any()

    /** Insertion-ordered so eviction can drop the oldest entries first. */
    private val pending = LinkedHashSet<Long>()

    /**
     * True (and records the id as pending) the FIRST time this is called for
     * a given [offerId]. Every later call for the same id — from either arm,
     * from any thread — returns false without ringing anything.
     *
     * Atomic: the push arm calls this from an FCM callback thread while the
     * poll arm calls it from a `viewModelScope` coroutine, and a race
     * between the two is exactly the scenario this class exists to close.
     */
    fun shouldRaise(offerId: Long): Boolean = synchronized(lock) {
        if (!pending.add(offerId)) {
            return false
        }
        evictIfOverCapacity()
        true
    }

    /**
     * Forgets [offerId] — called once the driver answers it or it expires,
     * so a genuinely new offer (a new id) is free to ring again. Safe to
     * call for an id that was never raised, or already resolved.
     */
    fun resolve(offerId: Long) {
        synchronized(lock) { pending.remove(offerId) }
    }

    /** Must be called while already holding [lock]. */
    private fun evictIfOverCapacity() {
        while (pending.size > MAX_TRACKED) {
            val oldest = pending.firstOrNull() ?: return
            pending.remove(oldest)
        }
    }

    private companion object {
        /**
         * Generous relative to reality — one driver holds at most one trip
         * at a time (decision 21) — but cheap to keep, and it is the number
         * that stops an unresolved id from being the seed of a slow,
         * unbounded leak over a multi-hour shift.
         */
        const val MAX_TRACKED = 64
    }
}
