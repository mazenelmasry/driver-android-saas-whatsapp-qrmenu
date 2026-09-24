package app.qrmenu.driver.orders

import app.qrmenu.driver.network.api.OrderApi
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two lists this screen shows, and nothing else — accept/decline/pickup
 * land with `:feature:trip` (week 4/5, decision-owned there). Kept a thin
 * wrapper over [OrderApi] rather than a cache: the contract's own freshness
 * guarantee is a pull-to-refresh plus a modest poll while the screen is
 * resumed (a 20s safety net until Reverb lands — CLAUDE.md), not a local
 * store a driver could act on stale.
 */
@Singleton
class OrdersRepository @Inject constructor(
    private val orderApi: OrderApi,
) {
    /** «المتاحة» — offers/claimable orders, plus WHY the list looks the way it does (decision 47). */
    suspend fun available(): Pair<List<DriverOrderDto>, AvailabilityContextDto> {
        val response = orderApi.available()
        return response.data to response.context
    }

    /**
     * «طلباتى» — at most one: a driver holds a single trip at a time
     * (decision 21), and the contract caps the array at one item.
     *
     * 🔴 Enforced here as well, not merely trusted: if a backend bug or a
     * race ever returned two, the screen would show a driver two trips they
     * cannot both run, and every downstream assumption ("the order I am on")
     * would quietly have two answers. `mine` is ordered newest-first, so the
     * first is the safe reading.
     *
     * Deliberately not logged through `android.util.Log`: this class is unit
     * tested on the JVM, where that call throws unless the whole module opts
     * into returning defaults. A server returning two trips is a backend bug
     * and is visible there; making the phone's behaviour correct is this
     * line's job.
     */
    suspend fun mine(): List<DriverOrderDto> = orderApi.mine().data.take(1)

    /**
     * «خُذ الطلب» — take an order out of the open list.
     *
     * 🔴 Safe to call on EVERY card in «المتاحة», in every assignment mode,
     * and that is a property of the server rather than a rule this app has to
     * remember: `DispatchPolicy::isVisibleToDriver()` is the exact predicate
     * the backend's own `claim` re-checks. `manual` puts nothing in this list
     * at all; an automatic mode puts in only the order this driver already
     * holds a live offer for, or one the assigner opened to everyone after
     * running out of candidates. So the app never needs to know the branch's
     * `driver_assignment_mode` — which is why it is deliberately absent from
     * the contract, not merely missing from it.
     *
     * Claiming an order this driver was individually offered settles that
     * offer identically to `accept`, so the one button is correct for both
     * shapes of row.
     *
     * Returns the order in its ASSIGNED shape (customer and address now
     * present — earned by holding it). Throws on 409 `already_claimed`, which
     * is the ordinary outcome of losing a race, not a fault.
     *
     * The `Idempotency-Key` is generated here, once per COMMAND — a driver's
     * tap is what invokes this, and a re-tap is a new command deserving a new
     * key. Same rule as `OfferRepository`.
     */
    suspend fun claim(orderId: Long): DriverOrderDto =
        orderApi.claim(orderId, UUID.randomUUID().toString())
}
