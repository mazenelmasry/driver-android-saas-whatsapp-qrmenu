package app.qrmenu.driver.auth.phone

import android.app.Activity
import android.content.Context
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The real bridge to Firebase Phone Auth, wrapping its listener-based SDK in a
 * `callbackFlow` so `OtpViewModel` sees a plain coroutine `Flow`.
 *
 * There is deliberately no separate "not configured" implementation swapped in
 * for a brand with no Firebase project (taaj, today): [FirebaseApp.getApps] is
 * checked at the moment verification is asked for, not at construction time —
 * `FirebaseAuth.getInstance()` itself throws the instant it is touched with no
 * default `FirebaseApp` installed, so the one thing this class must never do is
 * reach for it before that check has passed. `BuildConfig.FIREBASE_CONFIGURED`
 * lives on `:app`'s BuildConfig, not this module's, so it cannot be read from
 * here anyway — this is the correct, module-safe way to ask the same question.
 */
@Singleton
class FirebaseDriverPhoneVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriverPhoneVerifier {

    override fun verificationEvents(activity: Activity, e164Phone: String): Flow<PhoneVerificationOutcome> =
        callbackFlow {
            if (FirebaseApp.getApps(context).isEmpty()) {
                trySend(PhoneVerificationOutcome.Unavailable)
                close()
                return@callbackFlow
            }

            val auth = FirebaseAuth.getInstance()

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                // Android read the SMS itself (auto-retrieval) or Play Services
                // instant-verified the number — the driver never saw a code to
                // type, so the credential arrives here fully formed.
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    launch {
                        val result = signInAndGetIdToken(auth, credential)
                        result
                            .onSuccess { token -> trySend(PhoneVerificationOutcome.AutoVerified(token)) }
                            .onFailure { thrown -> trySend(PhoneVerificationOutcome.Failed(thrown.toPhoneVerificationError())) }
                        close()
                    }
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    trySend(PhoneVerificationOutcome.Failed(exception.toPhoneVerificationError()))
                    close()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    trySend(PhoneVerificationOutcome.CodeSent(verificationId))
                    // The channel STAYS open here on purpose: auto-retrieval can
                    // still fire onVerificationCompleted later in the same
                    // verification window, well after the code was "sent".
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164Phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)

            awaitClose { /* Firebase has no listener to detach here. */ }
        }

    override suspend fun confirmCode(verificationId: String, code: String): Result<String> {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInAndGetIdToken(FirebaseAuth.getInstance(), credential)
    }

    private suspend fun signInAndGetIdToken(
        auth: FirebaseAuth,
        credential: PhoneAuthCredential,
    ): Result<String> = runCatching {
        suspendCancellableCoroutine { continuation ->
            auth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        continuation.resumeWithException(IllegalStateException("Firebase sign-in returned no user"))
                        return@addOnSuccessListener
                    }
                    // forceRefresh=true: a token minted moments ago for a brand
                    // new sign-in must not come back from a stale local cache.
                    user.getIdToken(true)
                        .addOnSuccessListener { idTokenResult ->
                            val idToken = idTokenResult.token
                            if (idToken == null) {
                                continuation.resumeWithException(IllegalStateException("Firebase returned no ID token"))
                            } else {
                                continuation.resume(idToken)
                            }
                        }
                        .addOnFailureListener { thrown -> continuation.resumeWithException(thrown) }
                }
                .addOnFailureListener { thrown -> continuation.resumeWithException(thrown) }
        }
    }
}

/**
 * Maps a Firebase failure onto the app's [DriverErrorCode] vocabulary where one
 * fits — never a raw Firebase/English message onto the screen (CLAUDE.md,
 * error codes: a driver may be reading Urdu, Bengali or Hindi).
 */
internal fun Throwable.toPhoneVerificationError(): DriverApiError = when (this) {
    // Wrong six digits.
    is FirebaseAuthInvalidCredentialsException -> apiError(DriverErrorCode.OtpInvalid)
    // Firebase's own SMS-quota guard — same shape as the backend's rate limiter.
    is FirebaseTooManyRequestsException -> apiError(DriverErrorCode.TooManyAttempts)
    // Not an IOException, so `toDriverApiError()` in :core:network would never
    // catch this on its own — it must be classified here, before it reaches
    // that generic mapper via DriverApiError.Unknown.
    is FirebaseNetworkException -> DriverApiError.Offline
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_SESSION_EXPIRED", "ERROR_CODE_EXPIRED" -> apiError(DriverErrorCode.OtpExpired)
        "ERROR_INVALID_VERIFICATION_CODE" -> apiError(DriverErrorCode.OtpInvalid)
        "ERROR_TOO_MANY_REQUESTS" -> apiError(DriverErrorCode.TooManyAttempts)
        else -> DriverApiError.Unknown(this)
    }
    else -> DriverApiError.Unknown(this)
}

private fun apiError(code: DriverErrorCode): DriverApiError.Api =
    DriverApiError.Api(httpStatus = 0, code = code, message = null, rawCode = code.wire)
