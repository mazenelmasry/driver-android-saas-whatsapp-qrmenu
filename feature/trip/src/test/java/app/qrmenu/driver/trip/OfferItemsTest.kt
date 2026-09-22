package app.qrmenu.driver.trip

import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.OfferDto
import app.qrmenu.driver.network.dto.OrderCompanyDto
import app.qrmenu.driver.network.dto.OrderItemDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offer card has ~45 seconds of a driver's attention, and the question it
 * has to answer in them is whether the load fits on the bike.
 */
class OfferItemsTest {

    private fun offerWith(items: List<OrderItemDto>): DriverOrderDto = DriverOrderDto(
        id = 1,
        orderNumber = "0001-AAAA",
        status = "confirmed",
        deliveryMethod = "delivery",
        paymentMethod = "cash",
        paymentStatus = "pending",
        total = 50.0,
        currency = "SAR",
        cashToCollect = 50.0,
        driverFee = 8.0,
        company = OrderCompanyDto(name = "Lauren"),
        branch = BranchDto(id = 1, name = "Al Olaya"),
        items = items,
        offer = OfferDto(id = 9001, expiresAt = Instant.now().plusSeconds(45).toString(), wave = 1),
    )

    @Test
    fun `the count is pieces, not lines`() {
        // The whole reason this changed: one line of six boxes used to read
        // exactly like one coffee.
        val summary = offerWith(listOf(OrderItemDto("بوكس عائلى", 6))).toOfferSummary()!!
        assertEquals(6, summary.itemCount)
    }

    @Test
    fun `pieces add up across lines`() {
        val summary = offerWith(
            listOf(OrderItemDto("برجر", 2), OrderItemDto("بطاطس", 1), OrderItemDto("عصير", 3)),
        ).toOfferSummary()!!
        assertEquals(6, summary.itemCount)
    }

    @Test
    fun `the preview shows the first lines and counts the rest`() {
        val summary = offerWith(
            listOf(OrderItemDto("برجر", 2), OrderItemDto("بطاطس", 1), OrderItemDto("عصير", 3)),
        ).toOfferSummary()!!

        assertEquals(listOf(OfferItem("برجر", 2), OfferItem("بطاطس", 1)), summary.itemPreview)
        assertEquals(1, summary.hiddenItemCount)
    }

    @Test
    fun `nothing is hidden when everything fits`() {
        val summary = offerWith(listOf(OrderItemDto("برجر", 2))).toOfferSummary()!!
        assertEquals(1, summary.itemPreview.size)
        assertEquals(0, summary.hiddenItemCount)
    }

    @Test
    fun `an order with no item detail falls back to the count and shows no preview`() {
        val summary = offerWith(emptyList()).toOfferSummary()!!
        assertTrue(summary.itemPreview.isEmpty())
        assertEquals(0, summary.hiddenItemCount)
        assertEquals(0, summary.itemCount)
    }

    @Test
    fun `a negative quantity cannot drag the total below the real load`() {
        // Defensive: a bad row must not make a full bike look empty.
        val summary = offerWith(
            listOf(OrderItemDto("برجر", 3), OrderItemDto("خطأ", -5)),
        ).toOfferSummary()!!
        assertEquals(3, summary.itemCount)
    }
}
