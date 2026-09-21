package app.qrmenu.driver.offers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfferCoordinatorTest {

    @Test
    fun `starts with no pending offer`() {
        val coordinator = OfferCoordinator()

        assertNull(coordinator.pending.value)
    }

    @Test
    fun `raise publishes the order and offer id`() {
        val coordinator = OfferCoordinator()

        coordinator.raise(orderId = 10L, offerId = 100L)

        assertEquals(PendingOffer(orderId = 10L, offerId = 100L), coordinator.pending.value)
    }

    @Test
    fun `raise overwrites whatever was pending before it`() {
        val coordinator = OfferCoordinator()
        coordinator.raise(orderId = 10L, offerId = 100L)

        coordinator.raise(orderId = 11L, offerId = 101L)

        assertEquals(PendingOffer(orderId = 11L, offerId = 101L), coordinator.pending.value)
    }

    @Test
    fun `clear returns to no pending offer`() {
        val coordinator = OfferCoordinator()
        coordinator.raise(orderId = 10L, offerId = 100L)

        coordinator.clear()

        assertNull(coordinator.pending.value)
    }
}
