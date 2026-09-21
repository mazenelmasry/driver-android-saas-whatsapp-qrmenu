package app.qrmenu.driver.auth

import android.app.Activity
import app.qrmenu.driver.auth.invite.InviteCodeSanitizer
import app.qrmenu.driver.auth.login.MIN_PASSWORD_LENGTH
import app.qrmenu.driver.auth.otp.OTP_LENGTH
import app.qrmenu.driver.auth.phone.DriverPhoneVerifier
import app.qrmenu.driver.auth.phone.PhoneVerificationOutcome
import app.qrmenu.driver.common.session.TokenExpiry
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.RedeemInviteRequest
import app.qrmenu.driver.network.dto.RequestOtpRequest
import app.qrmenu.driver.network.dto.SetPasswordRequest
import app.qrmenu.driver.network.dto.VerifyOtpRequest
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
) : ViewModel() {

    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    /** Held from [start] so an auto-verify can complete the flow without the driver tapping anything. */
    private var onConfirmedCallback: ((needsPassword: Boolean, hasRestaurants: Boolean) -> Unit)? = null

    private var hasRequested = false

    /**
     * Sends the first code, once, when the screen opens.
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
        sendCode(phone, activity)
    }

    private fun sendCode(phone: String, activity: Activity) {
        viewModelScope.launch {
            runCatching { authApi.requestOtp(RequestOtpRequest(phone)) }
                .onSuccess {
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
        viewModelScope.launch {
            phoneVerifier.verificationEvents(activity, phone).collect { outcome ->
                when (outcome) {
                    is PhoneVerificationOutcome.CodeSent -> _state.update {
                        it.copy(verificationId = outcome.verificationId, error = null)
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
                    _state.update {
                        it.copy(isSubmitting = false, error = thrown.toDriverApiError())
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
                _state.update { it.copy(isSubmitting = false, isAutoVerifying = false) }
                onConfirmedCallback?.invoke(response.needsPassword, response.restaurants.isNotEmpty())
            }.onFailure { thrown ->
                _state.update {
                    it.copy(isSubmitting = false, isAutoVerifying = false, error = thrown.toDriverApiError())
                }
            }
        }
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
