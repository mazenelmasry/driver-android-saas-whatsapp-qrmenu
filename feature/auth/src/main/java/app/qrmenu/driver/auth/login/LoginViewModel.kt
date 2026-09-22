package app.qrmenu.driver.auth.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.common.session.TokenExpiry
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.BrandingDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.toDriverApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val region: DialingRegion? = null,
    val nationalNumber: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    /**
     * Whether the phone number is kept for next time. It does NOT control the
     * session — see the screen's RememberAndForgotRow for why that distinction
     * is kept honest in the label.
     */
    val rememberNumber: Boolean = true,
    val isSubmitting: Boolean = false,
    /**
     * The platform's name and logo, from the admin panel. Null while loading or
     * when it could not be fetched — the screen falls back to the build
     * flavour's own name, and shows no logo at all rather than a broken one.
     */
    val branding: BrandingDto? = null,
    val error: DriverApiError? = null,
    /**
     * Why a code could not be requested yet. Not an [error]: nothing failed, the
     * driver is simply a step early or a digit off, and dressing either in the
     * failure banner would read as "the app is broken".
     *
     * The two are kept apart because the fix is different — one is "type your
     * number", the other is "that number is wrong" — and a driver told to enter
     * a number they can plainly see on screen stops believing the app.
     */
    val phoneHint: PhoneHint? = null,
) {
    /**
     * Enabled only when both values are complete. The button is not a way to
     * find out whether the number is valid — the field says that, and a driver
     * pressing a dead button in a car learns nothing.
     */
    val canSubmit: Boolean
        get() = !isSubmitting && region != null && nationalNumber.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH
}

/** See [LoginUiState.phoneHint]. */
enum class PhoneHint { Missing, Invalid }

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi,
    private val phoneNumberUtil: PhoneNumberUtil,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val regions = DialingRegions(phoneNumberUtil)

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        detectRegion()
        loadBranding()
    }

    /**
     * The dialling code is guessed from the SIM, not asked for — see
     * [DialingRegions.detect]. It stays a guess the driver can overrule.
     */
    private fun detectRegion() {
        viewModelScope.launch {
            val detected = withContext(Dispatchers.IO) {
                regions.detect(context, Locale.getDefault())
            }
            _state.update { it.copy(region = it.region ?: detected) }
        }
    }

    /**
     * Fetches the platform's identity.
     *
     * 🔴 A failure here is SILENT on purpose: it must never block login or show
     * an error. The logo is decoration; the driver came to sign in, very likely
     * on a weak connection, and an error banner about a logo would read as "you
     * cannot sign in". The screen simply falls back to the flavour's own name.
     */
    private fun loadBranding() {
        viewModelScope.launch {
            runCatching { authApi.branding() }
                .onSuccess { branding -> _state.update { it.copy(branding = branding) } }
        }
    }

    fun onRegionSelected(region: DialingRegion) = _state.update { it.copy(region = region, error = null) }

    fun onNumberChange(value: String) =
        _state.update { it.copy(nationalNumber = value, error = null, phoneHint = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onTogglePasswordVisibility() = _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun onToggleRememberNumber() = _state.update { it.copy(rememberNumber = !it.rememberNumber) }

    fun onDismissError() = _state.update { it.copy(error = null) }

    fun regionsFor(locale: Locale): List<DialingRegion> = regions.all(locale)

    /**
     * Hands the typed number to the reset path, in E.164.
     *
     * Falls back to the raw digits when the number does not parse: the reset
     * screen can still show it and let the driver correct it there, which is
     * better than refusing to move at all and leaving them stuck on a screen
     * whose password they do not have.
     */
    fun forgotPassword(onContinue: (String) -> Unit) {
        val current = _state.value
        val region = current.region

        // 🔴 Without a number there is nothing to send a code TO. Moving on
        // anyway would land the driver on a screen that says "we sent a code to
        // +966" and waits forever for a message that was never addressed.
        if (current.nationalNumber.isBlank() || region == null) {
            _state.update { it.copy(phoneHint = PhoneHint.Missing) }
            return
        }

        val e164 = regions.toE164(region, current.nationalNumber)
        if (e164 == null) {
            _state.update { it.copy(phoneHint = PhoneHint.Invalid) }
            return
        }
        _state.update { it.copy(phoneHint = null) }
        onContinue(e164)
    }

    /**
     * Signs in.
     *
     * The number is normalised to E.164 HERE rather than on the server's word,
     * so an obviously wrong number is caught before it costs a round trip on
     * mobile data — and, more importantly, before it burns one of the five
     * attempts a minute the login limiter allows.
     */
    fun submit(deviceName: String, appVersionCode: Int, onSuccess: (hasRestaurants: Boolean) -> Unit) {
        val current = _state.value
        val region = current.region ?: return
        if (current.isSubmitting) return

        val e164 = regions.toE164(region, current.nationalNumber)
        if (e164 == null) {
            // 🔴 The number never left the phone, so nothing about the
            // CREDENTIALS is known yet. Claiming "wrong phone or password"
            // here sends the driver hunting a password that may be perfectly
            // correct, and hides the one thing they can actually fix — which
            // is why this marks the phone FIELD, the same way [continueToOtp]
            // already does a few lines above, instead of raising a login
            // error the server never returned.
            _state.update { it.copy(phoneHint = PhoneHint.Invalid, error = null) }
            return
        }
        _state.update { it.copy(phoneHint = null) }

        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            runCatching {
                authApi.login(
                    app.qrmenu.driver.network.dto.LoginRequest(
                        phone = e164,
                        password = current.password,
                        deviceName = deviceName,
                        appVersion = appVersionCode,
                    ),
                )
            }.onSuccess { response ->
                // 🔴 Wrapped in NonCancellable — a save that a navigation event
                // is free to cut short is exactly how the previous project
                // (CLAUDE.md's pitfall list) lost a just-completed sign-in.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    tokenStore.save(
                        token = response.token,
                        driverId = response.driver.id,
                        driverName = response.driver.name,
                        phone = response.driver.phone,
                        deviceName = deviceName,
                        expiresAtMillis = TokenExpiry.parseExpiresAt(response.expiresAt),
                    )
                }
                _state.update { it.copy(isSubmitting = false) }
                onSuccess(response.restaurants.isNotEmpty())
            }.onFailure { thrown ->
                _state.update { it.copy(isSubmitting = false, error = thrown.toDriverApiError()) }
            }
        }
    }
}
