package app.qrmenu.driver.orders

/**
 * The primary button [AssignedOrderCard] shows — pulled out as a pure
 * decision so it is unit-testable without a Compose harness (this module has
 * none, same style as [ReadinessCountdown]'s `readinessState`).
 *
 * 🔴 Before this existed, the card only ever asked one question — "is it
 * ready?" — and reused the SAME disabled-until-ready "استلمت الطلب" button
 * for a trip already picked up. Once `pickedUpAt` was set the button stayed
 * disabled and grey FOREVER, with nothing on the card saying the delivery was
 * still in progress or how to get back to it — a driver who left the list and
 * came back (app killed, tab switched, phone locked) had no way back into a
 * trip they were already holding, short of a fresh push notification they may
 * have missed.
 */
enum class AssignedCardAction {
    /** Disabled — the branch has not marked this order ready yet. */
    WaitingForReady,

    /** Enabled — "استلمت الطلب". Nothing has been collected yet. */
    PickUp,

    /**
     * Enabled — "متابعة التوصيل". [DriverOrderSummary.pickedUpAt] is already
     * set, so pickup is behind this driver; tapping the card (or this button)
     * opens the trip screen exactly where they left it.
     */
    ContinueDelivery,
}

/**
 * `pickedUp` wins outright — a driver who already holds the order needs a
 * route back into the trip regardless of what [ready] says (readiness is
 * about the KITCHEN's own work, which is irrelevant once the food has left
 * the branch).
 */
fun assignedCardAction(ready: Boolean, pickedUp: Boolean): AssignedCardAction = when {
    pickedUp -> AssignedCardAction.ContinueDelivery
    ready -> AssignedCardAction.PickUp
    else -> AssignedCardAction.WaitingForReady
}
