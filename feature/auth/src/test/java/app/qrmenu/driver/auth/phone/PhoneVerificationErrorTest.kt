package app.qrmenu.driver.auth.phone

import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.PhoneVerificationFailureReason
import app.qrmenu.driver.network.errors.toDriverApiError
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import io.mockk.every
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

    private fun authException(errorCode: String): FirebaseAuthException {
        val exception = mockk<FirebaseAuthException>(relaxed = true)
        every { exception.errorCode } returns errorCode
        return exception
    }

    // Play Integrity AND reCAPTCHA both failed to attest this device/app —
    // usually a stale token or a flaky reCAPTCHA page, so it is mapped as
    // retryable rather than as a dead end.
    @Test
    fun `a missing client identifier is told as a retryable attestation failure`() {
        val error = authException("ERROR_MISSING_CLIENT_IDENTIFIER").toPhoneVerificationError()

        assertTrue(error is DriverApiError.PhoneVerification)
        assertEquals(
            PhoneVerificationFailureReason.Unavailable,
            (error as DriverApiError.PhoneVerification).reason,
        )
    }

    @Test
    fun `an invalid app credential is told the same as a missing client identifier`() {
        val error = authException("ERROR_INVALID_APP_CREDENTIAL").toPhoneVerificationError()

        assertEquals(
            PhoneVerificationFailureReason.Unavailable,
            (error as DriverApiError.PhoneVerification).reason,
        )
    }

    // A fingerprint/package mismatch with Firebase's console config — no
    // amount of retrying fixes it, only we can, so this is its own reason.
    @Test
    fun `an unauthorized app is told to contact support, not to retry`() {
        val error = authException("ERROR_APP_NOT_AUTHORIZED").toPhoneVerificationError()

        assertTrue(error is DriverApiError.PhoneVerification)
        assertEquals(
            PhoneVerificationFailureReason.ConfigError,
            (error as DriverApiError.PhoneVerification).reason,
        )
    }

    @Test
    fun `a closed verification page is told as such`() {
        val error = authException("ERROR_WEB_CONTEXT_CANCELED").toPhoneVerificationError()

        assertTrue(error is DriverApiError.PhoneVerification)
        assertEquals(
            PhoneVerificationFailureReason.Cancelled,
            (error as DriverApiError.PhoneVerification).reason,
        )
    }

    @Test
    fun `a quota exceeded error reads the same as too many attempts`() {
        val error = authException("ERROR_QUOTA_EXCEEDED").toPhoneVerificationError()

        assertTrue(error is DriverApiError.Api)
        assertEquals(DriverErrorCode.TooManyAttempts, (error as DriverApiError.Api).code)
    }

    // Firebase's device-level ban ("blocked all requests … unusual
    // activity") is a different failure from a single code's short attempt
    // limit — a driver told to "wait a little" for what is actually a
    // multi-hour block would just keep failing and never learn why.
    @Test
    fun `a device blocked by firebase is told apart from a per-code attempt limit`() {
        val blocked = mockk<FirebaseTooManyRequestsException>(relaxed = true)

        val error = blocked.toPhoneVerificationError()

        assertTrue(error is DriverApiError.PhoneVerification)
        assertEquals(
            PhoneVerificationFailureReason.Blocked,
            (error as DriverApiError.PhoneVerification).reason,
        )
    }
}
