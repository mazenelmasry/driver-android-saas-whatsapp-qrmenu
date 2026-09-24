package app.qrmenu.driver.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the "a row whose value is absent must not be drawn"
 * rule (CLAUDE.md driver-app bug report). These test the decision functions
 * extracted into `TripDisplay.kt` rather than the Composables themselves —
 * `:feature:trip` has no Compose UI test infrastructure (see
 * `AddressTextTest`/`NavigationTargetTest` for the established pattern of
 * testing the pure decision instead).
 */
class TripDisplayTest {

    // region blankToNull

    @Test
    fun `blankToNull keeps a real value`() {
        assertEquals("سلطان العتيبي", blankToNull("سلطان العتيبي"))
    }

    @Test
    fun `blankToNull turns an empty string into null`() {
        assertNull(blankToNull(""))
    }

    @Test
    fun `blankToNull turns a whitespace-only string into null`() {
        assertNull(blankToNull("   "))
    }

    @Test
    fun `blankToNull passes through null`() {
        assertNull(blankToNull(null))
    }

    // endregion

    // region displayableArea

    @Test
    fun `displayableArea prefers a real zone name over the address text`() {
        assertEquals("حي العليا", displayableArea("حي العليا", "شارع الأمير سلطان"))
    }

    @Test
    fun `displayableArea falls back to the address text when there is no zone`() {
        assertEquals("شارع الأمير سلطان", displayableArea(null, "شارع الأمير سلطان"))
    }

    @Test
    fun `displayableArea falls back to the address text when the zone name is blank`() {
        // The bug: a blank zone name used to win the elvis operator outright
        // and hide a perfectly good address-text fallback behind it.
        assertEquals("شارع الأمير سلطان", displayableArea("   ", "شارع الأمير سلطان"))
    }

    @Test
    fun `displayableArea is null when both the zone and the address text are absent`() {
        assertNull(displayableArea(null, null))
    }

    @Test
    fun `displayableArea is null when both the zone and the address text are blank`() {
        assertNull(displayableArea("", "   "))
    }

    @Test
    fun `displayableArea strips a raw map link out of the address-text fallback`() {
        assertEquals("حي العليا", displayableArea(null, "https://maps.app.goo.gl/xYz، حي العليا"))
    }

    // endregion

    // region displayableRecipientName

    @Test
    fun `displayableRecipientName keeps a real name`() {
        assertEquals("سلطان العتيبي", displayableRecipientName("سلطان العتيبي"))
    }

    @Test
    fun `displayableRecipientName hides a blank name`() {
        assertNull(displayableRecipientName("   "))
    }

    @Test
    fun `displayableRecipientName hides a missing name`() {
        assertNull(displayableRecipientName(null))
    }

    // endregion

    // region displayableBranchName

    @Test
    fun `displayableBranchName keeps a real name`() {
        assertEquals("فرع العليا", displayableBranchName("فرع العليا"))
    }

    @Test
    fun `displayableBranchName hides a blank name instead of fabricating one`() {
        assertNull(displayableBranchName(""))
    }

    // endregion

    // region addressCardHasContent

    @Test
    fun `address card has content when the address text is shown and non-blank`() {
        assertTrue(
            addressCardHasContent(
                addressText = "شارع الأمير سلطان",
                notes = null,
                isApproximateLocation = false,
                showMapLink = false,
            ),
        )
    }

    @Test
    fun `address card has content from notes alone`() {
        assertTrue(
            addressCardHasContent(
                addressText = null,
                notes = "الدور الثانى",
                isApproximateLocation = false,
                showMapLink = false,
            ),
        )
    }

    @Test
    fun `address card has content from the approximate-pin notice alone`() {
        assertTrue(
            addressCardHasContent(
                addressText = null,
                notes = null,
                isApproximateLocation = true,
                showMapLink = false,
            ),
        )
    }

    @Test
    fun `address card has content from the map-link button alone`() {
        assertTrue(
            addressCardHasContent(
                addressText = null,
                notes = null,
                isApproximateLocation = false,
                showMapLink = true,
            ),
        )
    }

    @Test
    fun `address card has nothing when the text is blank and every other slot is empty`() {
        // The bug this guards: a zone name shown up top left `showAddressText`
        // false, but the caller could still reach this with a blank address
        // text under some other condition — and separately, a blank text
        // passed through as non-null used to slip past the old `?.let` guard.
        assertFalse(
            addressCardHasContent(
                addressText = "   ",
                notes = null,
                isApproximateLocation = false,
                showMapLink = false,
            ),
        )
    }

    @Test
    fun `address card has nothing when nothing at all was passed`() {
        assertFalse(
            addressCardHasContent(
                addressText = null,
                notes = null,
                isApproximateLocation = false,
                showMapLink = false,
            ),
        )
    }

    @Test
    fun `address card has nothing when the only address text is a bare map link`() {
        assertFalse(
            addressCardHasContent(
                addressText = "https://maps.app.goo.gl/xYz",
                notes = null,
                isApproximateLocation = false,
                showMapLink = false,
            ),
        )
    }

    // endregion
}
