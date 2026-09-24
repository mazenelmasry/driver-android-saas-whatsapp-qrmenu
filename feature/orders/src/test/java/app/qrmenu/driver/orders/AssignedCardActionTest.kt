package app.qrmenu.driver.orders

import org.junit.Assert.assertEquals
import org.junit.Test

class AssignedCardActionTest {

    @Test
    fun `not ready and not picked up waits`() {
        assertEquals(AssignedCardAction.WaitingForReady, assignedCardAction(ready = false, pickedUp = false))
    }

    @Test
    fun `ready and not picked up offers pick-up`() {
        assertEquals(AssignedCardAction.PickUp, assignedCardAction(ready = true, pickedUp = false))
    }

    @Test
    fun `picked up offers continue delivery regardless of readiness`() {
        assertEquals(
            "readiness is a kitchen concern — once picked up, the food already left the branch",
            AssignedCardAction.ContinueDelivery,
            assignedCardAction(ready = true, pickedUp = true),
        )
        assertEquals(
            AssignedCardAction.ContinueDelivery,
            assignedCardAction(ready = false, pickedUp = true),
        )
    }
}
