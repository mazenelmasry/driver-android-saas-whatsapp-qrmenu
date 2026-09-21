package app.qrmenu.driver.auth.phone

import android.app.Activity
import app.qrmenu.driver.network.errors.DriverApiError
import kotlinx.coroutines.flow.Flow

/**
 * The bridge to Firebase Phone Auth, behind an interface so `OtpViewModel`
 * never touches the Firebase SDK directly.
 *
 * 🔴 Firebase does not send an SMS by itself — the APP asks (see
 * `OtpViewModel.start`), and this is the thing that does the asking. The
 * backend's `verify-otp` never sees the six typed digits; it verifies the
 * Firebase **ID token** this interface hands back once Firebase itself has
 * confirmed the code.
 */
interface DriverPhoneVerifier {

    /**
     * Starts verification for [e164Phone] and streams every state Firebase
     * reports for it.
     *
     * A live `Activity` is required by `PhoneAuthProvider.verifyPhoneNumber`
     * itself (SMS auto-retrieval binds to it) — this is not decoration, Firebase
     * refuses to start without one.
     *
     * The flow can emit [PhoneVerificationOutcome.CodeSent] and later
     * [PhoneVerificationOutcome.AutoVerified] **on the same call**: Android's
     * SMS Retriever / Play Services instant verification can resolve the
     * number automatically well after the code was already reported as sent,
     * which is exactly the case that must skip the six-digit screen rather
     * than wait for digits the driver will never type.
     */
    fun verificationEvents(activity: Activity, e164Phone: String): Flow<PhoneVerificationOutcome>

    /**
     * Confirms the six typed digits against the `verificationId` from the
     * latest [PhoneVerificationOutcome.CodeSent], and returns Firebase's ID
     * token on success.
     */
    suspend fun confirmCode(verificationId: String, code: String): Result<String>
}

/** Everything [DriverPhoneVerifier.verificationEvents] can report. */
sealed interface PhoneVerificationOutcome {

    /**
     * Android resolved the SMS itself (auto-retrieval or Play Services instant
     * verification) — no code was ever typed. The screen must move on by
     * itself; waiting for digits here strands a driver who will never see any.
     */
    data class AutoVerified(val idToken: String) : PhoneVerificationOutcome

    /** The SMS is away. [verificationId] is what [DriverPhoneVerifier.confirmCode] needs next. */
    data class CodeSent(val verificationId: String) : PhoneVerificationOutcome

    /** Firebase itself refused before or during verification — mapped onto [DriverApiError]. */
    data class Failed(val error: DriverApiError) : PhoneVerificationOutcome

    /**
     * This build has no Firebase project wired in (taaj today — see CLAUDE.md's
     * owner checklist). Distinct from [Failed] because the fix is not "try
     * again", it is "this feature does not exist yet on this app" — a
     * retryable-looking banner here would send a driver into an infinite loop.
     */
    data object Unavailable : PhoneVerificationOutcome
}
