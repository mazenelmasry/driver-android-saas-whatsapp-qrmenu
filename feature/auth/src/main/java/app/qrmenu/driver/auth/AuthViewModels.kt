package app.qrmenu.driver.auth

import android.app.Activity
import android.util.Log
import app.qrmenu.driver.auth.invite.InviteCodeSanitizer
import app.qrmenu.driver.auth.login.MIN_PASSWORD_LENGTH
import app.qrmenu.driver.auth.otp.OTP_LENGTH
import app.qrmenu.driver.auth.phone.DriverPhoneVerifier
import app.qrmenu.driver.auth.phone.PhoneVerificationOutcome
import app.qrmenu.driver.auth.phone.toPhoneVerificationError
import app.qrmenu.driver.common.session.TokenExpiry
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.RedeemInviteRequest
import app.qrmenu.driver.network.dto.RequestOtpRequest
import app.qrmenu.driver.network.dto.SetPasswordRequest
import app.qrmenu.driver.network.dto.VerifyOtpRequest
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OtpUiState(
    val code: String = "",
    /** The id Firebase gave the latest `CodeSent`; what [OtpViewModel.verify] confirms against. */
    val verificationId: String? = null,
    /**
     * True the moment Firebase resolved the number by itself (auto-retrieval /
     * Play Services instant verification) and the backend call is in flight —
     * the screen should show "confirming automatically", not a code field with
     * nothing to type.
     */
    val isAutoVerifying: Boolean = false,
    val isSubmitting: Boolean = false,
    /**
     * Counts down to when "send again" becomes tappable. Starts at zero: the
     * first code has not been asked for yet when this state is built, and a
     * countdown running before any request looks like a message is on its way.
     */
    val resendInSeconds: Int = 0,
    /**
     * This build has no Firebase project wired in (see [DriverPhoneVerifier]
     * doc). Distinct from [error]: nothing the driver did was wrong, and no
     * retry will ever fix it.
     */
    val isFirebaseUnavailable: Boolean = false,
    /**
     * The OS killed this process mid-wait, and what came back after
     * [OtpViewModel]'s [SavedStateHandle] was restored was a phone with NO
     * `verificationId` to go with it — a request was in flight when the
     * process died, so it is unknown whether Firebase ever sent anything.
     *
     * Distinct from [isFirebaseUnavailable] (nothing will ever work here) and
     * from [error] (a normal, retryable failure): this is "we do not know
     * whether a code is on the way — tap 'send again' to be sure", not a
     * generic error banner over an empty code field.
     */
    val needsFreshCode: Boolean = false,
    val error: DriverApiError? = null,
) {
    companion object {
        /**
         * Long enough for a carrier to actually deliver the SMS. A shorter
         * window teaches the driver to tap "send again" while the first message
         * is still in flight, and each tap is a real cost and a real rate limit.
         */
        const val RESEND_COOLDOWN_SECONDS = 45
    }
}

@HiltViewModel
class OtpViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val phoneVerifier: DriverPhoneVerifier,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * 🔴 Process death loses the in-memory [OtpUiState] the same way it loses
     * every other `ViewModel` — but [AuthStepSaver] already carried the PHONE
     * back through `rememberSaveable`, so the very next thing that happens is
     * [start] being called again for it. Without persisting these two here as
     * well, that restart looked "fine" (a new SMS just went out) right up
     * until the backend's rate limiter or a flaky connection meant the SECOND
     * request never produced a `verificationId` — at which point a driver
     * holding a perfectly valid code from the FIRST request had no way to
     * submit it.
     *
     * [KEY_PHONE] is compared against the phone [start] is called with rather
     * than trusted blindly: a driver who tapped "change number" and came back
     * for a DIFFERENT phone must never have this stale id offered up for it.
     */
    private var pendingVerificationId: String?
        get() = savedStateHandle[KEY_VERIFICATION_ID]
        set(value) {
            savedStateHandle[KEY_VERIFICATION_ID] = value
        }

    private var pendingPhone: String?
        get() = savedStateHandle[KEY_PHONE]
        set(value) {
            savedStateHandle[KEY_PHONE] = value
        }

    private val _state = MutableStateFlow(
        OtpUiState(verificationId = savedStateHandle[KEY_VERIFICATION_ID]),
    )
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    /** Held from [start] so an auto-verify can complete the flow without the driver tapping anything. */
    private var onConfirmedCallback: ((needsPassword: Boolean, hasRestaurants: Boolean) -> Unit)? = null

    private var hasRequested = false

    private var verificationJob: Job? = null

    /**
     * Forgets this confirmation entirely — called when the driver LEAVES the
     * step (back, "change number"), never on a rotation. The ViewModel is
     * scoped to the whole sign-in route, so without this a driver who went
     * back and returned found the old digits, the old error, and no new SMS
     * (`hasRequested` still true) — a dead end (S25, 2026-09-25).
     */
    fun reset() {
        verificationJob?.cancel()
        verificationJob = null
        hasRequested = false
        onConfirmedCallback = null
        pendingPhone = null
        pendingVerificationId = null
        _state.value = OtpUiState()
    }

    /**
     * Sends the first code, once, when the screen opens — UNLESS a restart
     * already restored a `verificationId` for this exact phone, in which case
     * the code Firebase already sent is still good and a second SMS would
     * only waste it (and the rate limit).
     *
     * 🔴 Firebase does NOT send anything when the restaurant adds a driver — the
     * app asks, and until it does no SMS exists. That is why the previous screen
     * has a visible way in ("first time?" / "forgot your password?") and why
     * arriving here IS the request: a driver who reached this screen is by
     * definition waiting for a message.
     *
     * The BACKEND is asked first — it is the rate limiter and the audit record —
     * and only on its success is Firebase asked to actually send the SMS. A
     * backend that refuses (rate-limited, phone not eligible) must not still
     * result in a text message landing on the driver's phone.
     *
     * Guarded by [hasRequested] because a recomposition must not spend a second
     * SMS — each one costs money and counts against the rate limit.
     */
    fun start(
        phone: String,
        activity: Activity,
        onConfirmed: (needsPassword: Boolean, hasRestaurants: Boolean) -> Unit,
    ) {
        onConfirmedCallback = onConfirmed
        if (hasRequested) return
        hasRequested = true

        val restoredId = pendingVerificationId
        if (pendingPhone == phone && restoredId != null) {
            _state.update { it.copy(verificationId = restoredId, needsFreshCode = false, error = null) }
            return
        }

        if (pendingPhone == phone && restoredId == null) {
            // A request for THIS phone was in flight when the process died —
            // it may or may not have reached Firebase. Rather than guess
            // (auto-resending spends an SMS if it did; staying silent strands
            // the driver if it did not), surface a clear "request a new code"
            // path and let them decide.
            _state.update { it.copy(needsFreshCode = true, resendInSeconds = 0) }
            return
        }

        pendingPhone = phone
        pendingVerificationId = null
        sendCode(phone, activity)
    }

    private fun sendCode(phone: String, activity: Activity) {
        viewModelScope.launch {
            runCatching { authApi.requestOtp(RequestOtpRequest(phone)) }
                .onSuccess {
                    _state.update { it.copy(needsFreshCode = false) }
                    startCooldown()
                    listenForVerification(phone, activity)
                }
                .onFailure { thrown ->
                    _state.update { it.copy(resendInSeconds = 0, error = thrown.toDriverApiError()) }
                }
        }
    }

    /** Collects Firebase's stream of events for the life of this screen. */
    private fun listenForVerification(phone: String, activity: Activity) {
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            phoneVerifier.verificationEvents(activity, phone).collect { outcome ->
                when (outcome) {
                    is PhoneVerificationOutcome.CodeSent -> {
                        pendingVerificationId = outcome.verificationId
                        _state.update {
                            it.copy(verificationId = outcome.verificationId, error = null)
                        }
                    }

                    is PhoneVerificationOutcome.CodeRetrieved -> _state.update {
                        it.copy(code = outcome.code.filter(Char::isDigit).take(OTP_LENGTH), error = null)
                    }

                    is PhoneVerificationOutcome.AutoVerified -> {
                        _state.update { it.copy(isAutoVerifying = true, error = null) }
                        finishWithIdToken(phone, outcome.idToken)
                    }

                    is PhoneVerificationOutcome.Failed -> _state.update {
                        it.copy(error = outcome.error, isAutoVerifying = false)
                    }

                    PhoneVerificationOutcome.Unavailable -> _state.update {
                        it.copy(isFirebaseUnavailable = true, resendInSeconds = 0)
                    }
                }
            }
        }
    }

    fun onCodeChange(value: String) = _state.update {
        it.copy(code = value.filter(Char::isDigit).take(OTP_LENGTH), error = null)
    }

    private fun startCooldown() {
        viewModelScope.launch {
            _state.update { it.copy(resendInSeconds = OtpUiState.RESEND_COOLDOWN_SECONDS) }
            while (_state.value.resendInSeconds > 0) {
                delay(1_000)
                _state.update { it.copy(resendInSeconds = it.resendInSeconds - 1) }
            }
        }
    }

    fun resend(phone: String, activity: Activity) {
        if (_state.value.resendInSeconds > 0 || _state.value.isFirebaseUnavailable) return
        sendCode(phone, activity)
    }

    /**
     * Confirms the six typed digits.
     *
     * The digits never leave the device as a "token" — they are checked by
     * Firebase against [OtpUiState.verificationId], and only the ID token that
     * comes back from THAT check is sent to the backend.
     */
    fun verify(phone: String) {
        val current = _state.value
        val verificationId = current.verificationId
        if (current.code.length != OTP_LENGTH || current.isSubmitting || verificationId == null) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            phoneVerifier.confirmCode(verificationId, current.code)
                .onSuccess { idToken -> finishWithIdToken(phone, idToken) }
                .onFailure { thrown ->
                    // 🔴 A FIREBASE failure, so the Firebase mapper — never the
                    // network one. `toDriverApiError()` knows nothing about
                    // Firebase exceptions and turned a mistyped code, an expired
                    // session, or a code typed into a session it does not belong
                    // to into «حدث خطأ ما» (found on a real release build on the
                    // S25, 2026-09-23). The auto-verified path already used this.
                    val error = thrown.toPhoneVerificationError().alsoLogIfUnclassified("confirmCode")
                    _state.update {
                        it.copy(isSubmitting = false, error = error)
                    }
                }
        }
    }

    /** The shared tail of both the typed-code path and the auto-verified path. */
    private fun finishWithIdToken(phone: String, idToken: String) {
        viewModelScope.launch {
            runCatching {
                authApi.verifyOtp(VerifyOtpRequest(phone = phone, firebaseToken = idToken))
            }.onSuccess { response ->
                // Verifying the phone SIGNS IN — the session is saved here so
                // the next screen (choosing a password) is an authenticated
                // call rather than a second proof of the same phone.
                withContext(NonCancellable) {
                    tokenStore.save(
                        token = response.token,
                        driverId = response.driver.id,
                        driverName = response.driver.name,
                        phone = response.driver.phone,
                        deviceName = null,
                        expiresAtMillis = TokenExpiry.parseExpiresAt(response.expiresAt),
                    )
                }
                // The verification this phone/id pair was for is spent — a
                // future ConfirmPhone (a second restaurant's invite, later)
                // must never see this as something to restore.
                pendingPhone = null
                pendingVerificationId = null
                _state.update { it.copy(isSubmitting = false, isAutoVerifying = false) }
                onConfirmedCallback?.invoke(response.needsPassword, response.restaurants.isNotEmpty())
            }.onFailure { thrown ->
                val error = thrown.toDriverApiError().alsoLogIfUnclassified("verify-otp")
                _state.update {
                    it.copy(isSubmitting = false, isAutoVerifying = false, error = error)
                }
            }
        }
    }

    /**
     * An unclassified failure is shown as the generic «حدث خطأ ما» and was
     * otherwise swallowed — on a minified release build there was no trace of
     * WHAT failed on the one screen every driver must pass. Logs the class and
     * message only: never the code, the phone or any token.
     */
    private fun DriverApiError.alsoLogIfUnclassified(step: String): DriverApiError = also {
        if (this is DriverApiError.Unknown) {
            Log.w(TAG, "$step failed unclassified: ${cause::class.java.name}: ${cause.message}")
        }
    }

    private companion object {
        const val TAG = "OtpViewModel"
        const val KEY_VERIFICATION_ID = "otp_verification_id"
        const val KEY_PHONE = "otp_pending_phone"
    }
}

data class SetPasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val isVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: DriverApiError? = null,
)

@HiltViewModel
class SetPasswordViewModel @Inject constructor(
    private val authApi: AuthApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SetPasswordUiState())
    val state: StateFlow<SetPasswordUiState> = _state.asStateFlow()

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onConfirmationChange(value: String) = _state.update { it.copy(confirmation = value, error = null) }

    /** One toggle for both fields — they exist to be compared against each other. */
    fun onToggleVisibility() = _state.update { it.copy(isVisible = !it.isVisible) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.password.length < MIN_PASSWORD_LENGTH || current.password != current.confirmation) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { authApi.setPassword(SetPasswordRequest(current.password)) }
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    onSaved()
                }
                .onFailure { thrown ->
                    _state.update { it.copy(isSubmitting = false, error = thrown.toDriverApiError()) }
                }
        }
    }
}

data class InviteCodeUiState(
    val code: String = "",
    val isSubmitting: Boolean = false,
    val error: DriverApiError? = null,
) {
    val canSubmit: Boolean get() = !isSubmitting && InviteCodeSanitizer.isComplete(code)
}

/**
 * Screen for a driver whose phone is verified but who nobody has invited yet
 * (an empty `restaurants` list from `login`/`verify-otp` — CLAUDE.md decision
 * 15: there is no self sign-up).
 */
@HiltViewModel
class InviteCodeViewModel @Inject constructor(
    private val authApi: AuthApi,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteCodeUiState())
    val state: StateFlow<InviteCodeUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) = _state.update {
        it.copy(code = InviteCodeSanitizer.sanitize(value), error = null)
    }

    fun redeem(onRedeemed: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { authApi.redeemInvite(RedeemInviteRequest(current.code)) }
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    onRedeemed()
                }
                .onFailure { thrown ->
                    _state.update { it.copy(isSubmitting = false, error = thrown.toDriverApiError()) }
                }
        }
    }
}
