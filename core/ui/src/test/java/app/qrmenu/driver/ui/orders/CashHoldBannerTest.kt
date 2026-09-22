package app.qrmenu.driver.ui.orders

import app.qrmenu.driver.network.dto.CashHoldBranchDto
import app.qrmenu.driver.network.dto.CashHoldDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashHoldBannerTest {

    private val branch = CashHoldBranchDto(
        branchId = 1,
        branchName = "Lauren Bakery — Al Olaya",
        companyId = 9,
        companyName = "Lauren Group",
        cashOnHand = 520.0,
        limit = 500.0,
        currency = "SAR",
    )

    @Test
    fun `a null context has no held branches`() {
        assertTrue(null.heldBranches().isEmpty())
    }

    @Test
    fun `a context with an empty branch list has no held branches`() {
        assertTrue(CashHoldDto(branches = emptyList()).heldBranches().isEmpty())
    }

    @Test
    fun `a held branch passes through unchanged`() {
        val held = CashHoldDto(branches = listOf(branch)).heldBranches()

        assertEquals(1, held.size)
        assertEquals(branch, held.first())
    }

    /**
     * Multiple branches — the case that matters here is that NOTHING is
     * collapsed into a count. A driver held at two branches of two different
     * companies sees both, by name, with their own numbers.
     */
    @Test
    fun `multiple held branches all pass through, none collapsed into a count`() {
        val second = branch.copy(
            branchId = 2,
            branchName = "Lauren Bakery — Al Malaz",
            companyId = 11,
            companyName = "Chicken Co.",
            cashOnHand = 610.0,
            limit = 600.0,
            currency = "SAR",
        )

        val held = CashHoldDto(branches = listOf(branch, second)).heldBranches()

        assertEquals(listOf(branch, second), held)
    }
}
