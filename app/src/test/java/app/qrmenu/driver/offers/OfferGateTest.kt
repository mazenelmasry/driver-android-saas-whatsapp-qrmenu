package app.qrmenu.driver.offers

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferGateTest {

    @Test
    fun `the first call for an offer id raises, the second for the same id does not`() {
        val gate = OfferGate()

        assertTrue(gate.shouldRaise(offerId = 1L))
        assertFalse(gate.shouldRaise(offerId = 1L))
    }

    @Test
    fun `after resolve, a new offer id for the same order still raises`() {
        val gate = OfferGate()

        assertTrue(gate.shouldRaise(offerId = 1L))
        gate.resolve(offerId = 1L)

        // A later wave of the SAME order arrives under a NEW offer id — must
        // still ring, per the frozen rule this class's doc names.
        assertTrue(gate.shouldRaise(offerId = 2L))
    }

    @Test
    fun `resolve on an id that was never raised is a harmless no-op`() {
        val gate = OfferGate()

        gate.resolve(offerId = 999L)

        assertTrue(gate.shouldRaise(offerId = 999L))
    }

    @Test
    fun `resolve is idempotent`() {
        val gate = OfferGate()
        gate.shouldRaise(offerId = 1L)

        gate.resolve(offerId = 1L)
        gate.resolve(offerId = 1L)

        assertTrue(gate.shouldRaise(offerId = 1L))
    }

    @Test
    fun `concurrent callers racing for the same offer id yield exactly one winner`() {
        val gate = OfferGate()
        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val winners = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                if (gate.shouldRaise(offerId = 42L)) {
                    winners.incrementAndGet()
                }
                doneLatch.countDown()
            }
        }

        // Every thread is parked at the same starting line before any of them
        // is allowed to call `shouldRaise` — this is what turns "probably
        // fine" into an actual race on the shared state.
        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, winners.get())
    }

    @Test
    fun `distinct offer ids in flight at once each raise independently`() {
        val gate = OfferGate()

        assertTrue(gate.shouldRaise(offerId = 1L))
        assertTrue(gate.shouldRaise(offerId = 2L))
        assertTrue(gate.shouldRaise(offerId = 3L))
        assertFalse(gate.shouldRaise(offerId = 1L))
        assertFalse(gate.shouldRaise(offerId = 2L))
    }
}
