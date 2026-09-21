package app.qrmenu.driver.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.auth.login.MIN_PASSWORD_LENGTH
import app.qrmenu.driver.auth.otp.OTP_LENGTH
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.RequestOtpRequest
import app.qrmenu.driver.network.dto.SetPasswordRequest
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtpUiState(
    val code: String = "",
    val isSubmitting: Boolean = false,
    /**
     * Counts down to when "send again" becomes tappable. Starts at zero: the
     * first code has not been asked for yet when this state is built, and a
     * countdown running before any request looks like a message is on its way.
     */
    val resendInSeconds: Int = 0,
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
) : ViewModel() {

    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    /**
     * Sends the first code, once, when the screen opens.
     *
     * 🔴 Firebase does NOT send anything when the restaurant adds a driver — the
     * app asks, and until it does no SMS exists. That is why the previous screen
     * has a visible way in ("first time?" / "forgot your password?") and why
     * arriving here IS the request: a driver who reached this screen is by
     * definition waiting for a message.
     *
     * Guarded by [hasRequested] because a recomposition must not spend a second
     * SMS — each one costs money and counts against the rate limit.
     */
    fun start(phone: String) {
        if (hasRequested) return
        hasRequested = true
        sendCode(phone)
    }

    private var hasRequested = false

    /**
     * The cooldown starts only on SUCCESS. A request that never left the phone
     * sent no SMS, so making the driver watch forty-five seconds for a message
     * that does not exist is the opposite of what the countdown is for.
     */
    private fun sendCode(phone: String) {
        viewModelScope.launch {
            runCatching { authApi.requestOtp(RequestOtpRequest(phone)) }
                .onSuccess { startCooldown() }
                .onFailure { thrown ->
                    _state.update { it.copy(resendInSeconds = 0, error = thrown.toDriverApiError()) }
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

    fun resend(phone: String) {
        if (_state.value.resendInSeconds > 0) return
        sendCode(phone)
    }

    /**
     * Verifies the code.
     *
     * ⚠️ The `firebase_token` sent here is the ID token Firebase Phone Auth
     * hands back once IT has verified the SMS — the backend never sees the six
     * digits. Firebase is not provisioned yet (Blaze plan + a SHA-1 per
     * flavour), so this call cannot succeed today; what is real is the screen,
     * its states, and where the result goes.
     */
    fun verify(phone: String, onConfirmed: (needsPassword: Boolean) -> Unit) {
        val code = _state.value.code
        if (code.length != OTP_LENGTH || _state.value.isSubmitting) return

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                authApi.verifyOtp(
                    app.qrmenu.driver.network.dto.VerifyOtpRequest(
                        phone = phone,
                        firebaseToken = code,
                    ),
                )
            }.onSuccess { response ->
                // Verifying the phone SIGNS IN — the session is saved here so
                // the next screen (choosing a password) is an authenticated
                // call rather than a second proof of the same phone.
                tokenStore.save(
                    token = response.token,
                    driverId = response.driver.id,
                    driverName = response.driver.name,
                    phone = response.driver.phone,
                    deviceName = null,
                    expiresAtMillis = null,
                )
                _state.update { it.copy(isSubmitting = false) }
                onConfirmed(response.needsPassword)
            }.onFailure { thrown ->
                _state.update { it.copy(isSubmitting = false, error = thrown.toDriverApiError()) }
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
