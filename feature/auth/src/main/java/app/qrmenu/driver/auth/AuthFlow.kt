package app.qrmenu.driver.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.auth.login.LoginRoute
import app.qrmenu.driver.auth.otp.OtpScreen
import app.qrmenu.driver.auth.password.SetPasswordScreen

/**
 * The three sign-in screens, and the order they run in.
 *
 * ```
 * first run / forgotten password        returning driver
 *   phone  ──► SMS code ──► choose          phone + password ──► in
 *              (cells)      a password
 * ```
 *
 * 🔴 The SMS is spent ONCE per device, not every shift — that is the whole point
 * of there being a password at all (project owner, 2026-09-21). And forgetting
 * the password leads back through the same SMS rather than to a reset link:
 * proving the phone is the only reset a driver can perform on the road, and the
 * phone is the one credential the restaurant already reached them on.
 *
 * Held as one `when` over a sealed step rather than a `NavHost`: the flow is
 * three screens with one entry and one exit, the back behaviour is "go back a
 * step, keep the number", and a navigation graph would spread that across four
 * files to express the same thing.
 */
sealed interface AuthStep {
    /** Phone + password. The default: most sign-ins are a returning driver. */
    data object SignIn : AuthStep

    /** Confirming the phone by SMS — first run, or a forgotten password. */
    data class ConfirmPhone(val phone: String, val isReset: Boolean) : AuthStep

    /** Choosing the password, on the session the SMS just opened. */
    data class ChoosePassword(val phone: String) : AuthStep
}

@Composable
fun AuthFlow(onSignedIn: () -> Unit) {
    var step by remember { mutableStateOf<AuthStep>(AuthStep.SignIn) }

    when (val current = step) {
        AuthStep.SignIn -> LoginRoute(
            onSignedIn = onSignedIn,
            onForgotPassword = { phone ->
                step = AuthStep.ConfirmPhone(phone = phone, isReset = true)
            },
        )

        is AuthStep.ConfirmPhone -> ConfirmPhoneRoute(
            phone = current.phone,
            onConfirmed = { needsPassword ->
                step = if (needsPassword) {
                    AuthStep.ChoosePassword(current.phone)
                } else {
                    // Already has one and just proved the phone: nothing left
                    // to ask for.
                    AuthStep.SignIn.also { onSignedIn() }
                }
            },
            onChangeNumber = { step = AuthStep.SignIn },
        )

        is AuthStep.ChoosePassword -> ChoosePasswordRoute(onSaved = onSignedIn)
    }
}

/**
 * Wiring for the OTP screen.
 *
 * ⚠️ Firebase Phone Auth is not provisioned yet (it needs the Blaze plan and a
 * SHA-1 per flavour — see CLAUDE.md's owner checklist), so nothing here can
 * actually send an SMS today. The screen, its states and its transitions are
 * real; the send and the verify are the two calls that land with Firebase.
 */
@Composable
private fun ConfirmPhoneRoute(
    phone: String,
    onConfirmed: (needsPassword: Boolean) -> Unit,
    onChangeNumber: () -> Unit,
) {
    val viewModel: OtpViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Arriving here IS the request — see OtpViewModel.start. Keyed on the phone
    // so correcting the number and coming back sends to the new one.
    LaunchedEffect(phone) { viewModel.start(phone) }

    OtpScreen(
        phone = phone,
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        onVerify = { viewModel.verify(phone, onConfirmed) },
        onResend = { viewModel.resend(phone) },
        onChangeNumber = onChangeNumber,
        resendInSeconds = state.resendInSeconds,
        isSubmitting = state.isSubmitting,
        error = state.error,
    )
}

@Composable
private fun ChoosePasswordRoute(onSaved: () -> Unit) {
    val viewModel: SetPasswordViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SetPasswordScreen(
        password = state.password,
        confirmation = state.confirmation,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmationChange = viewModel::onConfirmationChange,
        isVisible = state.isVisible,
        onToggleVisibility = viewModel::onToggleVisibility,
        onSubmit = { viewModel.save(onSaved) },
        isSubmitting = state.isSubmitting,
        error = state.error,
    )
}
