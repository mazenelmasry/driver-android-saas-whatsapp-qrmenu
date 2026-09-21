package app.qrmenu.driver.network.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The offer/lock error codes the week-4 endpoints (`accept`, `claim`,
 * `picked-up`) can return per `openapi/driver.v1.yaml`. Each must round-trip
 * through [DriverErrorCode.fromWire] to its own case — a code silently
 * collapsing to another (or to null) would mislabel a 409 to the driver, e.g.
 * showing "someone else already took it" for what was actually "you already
 * have too many active trips".
 */
class DriverErrorCodeOfferLockTest {

    @Test
    fun `already_claimed parses to its own case`() {
        assertEquals(DriverErrorCode.AlreadyClaimed, DriverErrorCode.fromWire("already_claimed"))
    }

    @Test
    fun `offer_expired parses to its own case`() {
        assertEquals(DriverErrorCode.OfferExpired, DriverErrorCode.fromWire("offer_expired"))
    }

    @Test
    fun `too_many_active_orders parses to its own case`() {
        assertEquals(DriverErrorCode.TooManyActiveOrders, DriverErrorCode.fromWire("too_many_active_orders"))
    }

    @Test
    fun `order_cancelled parses to its own case`() {
        assertEquals(DriverErrorCode.OrderCancelled, DriverErrorCode.fromWire("order_cancelled"))
    }

    @Test
    fun `cash_limit_exceeded parses to its own case`() {
        assertEquals(DriverErrorCode.CashLimitExceeded, DriverErrorCode.fromWire("cash_limit_exceeded"))
    }

    @Test
    fun `not_your_order parses to its own case`() {
        assertEquals(DriverErrorCode.NotYourOrder, DriverErrorCode.fromWire("not_your_order"))
    }

    @Test
    fun `order_not_ready parses to its own case`() {
        assertEquals(DriverErrorCode.OrderNotReady, DriverErrorCode.fromWire("order_not_ready"))
    }

    @Test
    fun `branch_closed parses to its own case`() {
        assertEquals(DriverErrorCode.BranchClosed, DriverErrorCode.fromWire("branch_closed"))
    }

    /**
     * A future server code this build has never heard of must degrade to null
     * (generic translated message + report), never crash the decode and never
     * silently alias to an existing case.
     */
    @Test
    fun `an unknown offer-lock code falls back to null, not to an existing case`() {
        assertNull(DriverErrorCode.fromWire("offer_already_taken_by_someone_else"))
    }
}
