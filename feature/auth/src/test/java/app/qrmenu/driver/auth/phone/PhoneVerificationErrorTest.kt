package app.qrmenu.driver.auth.phone

import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.toDriverApiError
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A mistyped code is the most common failure on the screen every driver must
 * pass. `OtpViewModel.verify()` once sent Firebase's rejection through the
 * NETWORK mapper, which does not know Firebase exceptions, so a wrong code read
 * «حدث خطأ ما» instead of "the code is wrong" (seen on a real release build).
 */
class PhoneVerificationErrorTest {

    // Firebase's exception constructors call into android.text on the JVM,
    // so the rejection is stood in for by a mock of the same type.
    private val wrongCode = mockk<FirebaseAuthInvalidCredentialsException>(relaxed = true)

    @Test
    fun `a wrong code is told as a wrong code`() {
        val error = wrongCode.toPhoneVerificationError()

        assertTrue(error is DriverApiError.Api)
        assertEquals(DriverErrorCode.OtpInvalid, (error as DriverApiError.Api).code)
    }

    @Test
    fun `the network mapper cannot classify it, which is why verify must not use it`() {
        assertTrue(wrongCode.toDriverApiError() is DriverApiError.Unknown)
    }
}
