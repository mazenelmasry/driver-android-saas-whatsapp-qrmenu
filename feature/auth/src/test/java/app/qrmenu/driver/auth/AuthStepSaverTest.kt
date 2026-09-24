package app.qrmenu.driver.auth

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AuthStepSaver] is what lets [AuthFlow] survive process death without
 * losing the phone number mid-OTP — see its own class doc. Proves the
 * round-trip for every [AuthStep] variant, not just that it compiles.
 */
class AuthStepSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun `SignIn round-trips`() {
        assertRoundTrips(AuthStep.SignIn)
    }

    @Test
    fun `ConfirmPhone round-trips with the phone and reset flag intact`() {
        assertRoundTrips(AuthStep.ConfirmPhone(phone = "+201234567890", isReset = true))
        assertRoundTrips(AuthStep.ConfirmPhone(phone = "+201234567890", isReset = false))
    }

    @Test
    fun `ChoosePassword round-trips with the phone and hasRestaurants flag intact`() {
        assertRoundTrips(AuthStep.ChoosePassword(phone = "+201234567890", hasRestaurants = true))
        assertRoundTrips(AuthStep.ChoosePassword(phone = "+201234567890", hasRestaurants = false))
    }

    @Test
    fun `RedeemInvite round-trips`() {
        assertRoundTrips(AuthStep.RedeemInvite)
    }

    private fun assertRoundTrips(step: AuthStep) {
        val saved = with(AuthStepSaver) { scope.save(step) }
        val restored = AuthStepSaver.restore(saved!!)
        assertEquals(step, restored)
    }
}
