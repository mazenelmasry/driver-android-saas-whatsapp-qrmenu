package app.qrmenu.driver.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.auth.invite.InviteCodeScreen
import app.qrmenu.driver.auth.login.LoginRoute
import app.qrmenu.driver.auth.otp.OtpScreen
import app.qrmenu.driver.auth.password.SetPasswordScreen

/**
 * The sign-in screens, and the order they run in.
 *
 * ```
 * first run / forgotten password        returning driver
 *   phone  ──► SMS code ──► choose          phone + password ──► in
 *              (cells)      a password           │
 *        │                       │                │
 *        └───────────┬───────────┴────────────────┘
 *                     ▼ (empty restaurants list)
 *              enter invite code
 * ```
 *
 * 🔴 The SMS is spent ONCE per device, not every shift — that is the whole point
 * of there being a password at all (project owner, 2026-09-21). And forgetting
 * the password leads back through the same SMS rather than to a reset link:
 * proving the phone is the only reset a driver can perform on the road, and the
 * phone is the one credential the restaurant already reached them on.
 *
 * 🔴 An EMPTY `restaurants` list — from either sign-in path — means the phone is
 * verified but nobody has invited it (decision 15: no self sign-up). Both paths
 * funnel into the SAME invite-code step rather than leaving the driver on a
 * blank restaurant picker with nothing to do.
 *
 * Held as one `when` over a sealed step rather than a `NavHost`: the flow is a
 * handful of screens with one entry and one exit, the back behaviour is "go
 * back a step, keep the number", and a navigation graph would spread that
 * across several files to express the same thing.
 */
sealed interface AuthStep {
    /** Phone + password. The default: most sign-ins are a returning driver. */
    data object SignIn : AuthStep

    /** Confirming the phone by SMS — first run, or a forgotten password. */
    data class ConfirmPhone(val phone: String, val isReset: Boolean) : AuthStep

    /**
     * Choosing the password, on the session the SMS just opened.
     *
     * [hasRestaurants] is carried over from the `verify-otp` response that got
     * us here — set-password itself does not report the driver's restaurants,
     * so re-fetching after saving would be a redundant call for a fact already
     * known.
     */
    data class ChoosePassword(val phone: String, val hasRestaurants: Boolean) : AuthStep

    /** The phone is verified but has no restaurant yet — see the class doc. */
    data object RedeemInvite : AuthStep
}

@Composable
fun AuthFlow(onSignedIn: () -> Unit) {
    var step by remember { mutableStateOf<AuthStep>(AuthStep.SignIn) }

    when (val current = step) {
        AuthStep.SignIn -> LoginRoute(
            onSignedIn = { hasRestaurants ->
                if (hasRestaurants) onSignedIn() else step = AuthStep.RedeemInvite
            },
            onForgotPassword = { phone ->
                step = AuthStep.ConfirmPhone(phone = phone, isReset = true)
            },
        )

        is AuthStep.ConfirmPhone -> ConfirmPhoneRoute(
            phone = current.phone,
            onConfirmed = { needsPassword, hasRestaurants ->
                step = when {
                    needsPassword -> AuthStep.ChoosePassword(current.phone, hasRestaurants)
                    hasRestaurants -> AuthStep.SignIn.also { onSignedIn() }
                    else -> AuthStep.RedeemInvite
                }
            },
            onChangeNumber = { step = AuthStep.SignIn },
        )

        is AuthStep.ChoosePassword -> ChoosePasswordRoute(
            onSaved = {
                step = if (current.hasRestaurants) AuthStep.SignIn.also { onSignedIn() } else AuthStep.RedeemInvite
            },
        )

        AuthStep.RedeemInvite -> InviteCodeRoute(onRedeemed = onSignedIn)
    }
}

/**
 * Wiring for the OTP screen.
 *
 * Firebase Phone Auth needs a live [Activity] (SMS auto-retrieval binds to
 * it), so it is resolved here, at the Compose boundary, rather than injected —
 * a `ViewModel` must not hold an `Activity` reference past the call that needs it.
 */
@Composable
private fun ConfirmPhoneRoute(
    phone: String,
    onConfirmed: (needsPassword: Boolean, hasRestaurants: Boolean) -> Unit,
    onChangeNumber: () -> Unit,
) {
    val viewModel: OtpViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Arriving here IS the request — see OtpViewModel.start. Keyed on the phone
    // so correcting the number and coming back sends to the new one.
    LaunchedEffect(phone) { viewModel.start(phone, activity, onConfirmed) }

    OtpScreen(
        phone = phone,
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        onVerify = { viewModel.verify(phone) },
        onResend = { viewModel.resend(phone, activity) },
        onChangeNumber = onChangeNumber,
        resendInSeconds = state.resendInSeconds,
        isSubmitting = state.isSubmitting,
        isAutoVerifying = state.isAutoVerifying,
        isFirebaseUnavailable = state.isFirebaseUnavailable,
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

/**
 * Redeeming an invite code on its OWN, outside the sign-in flow.
 *
 * A driver can arrive here twice for different reasons: right after proving a
 * phone nobody has invited yet, and — later, already signed in — from the home
 * screen when a second restaurant hands them a code. Same screen, same call;
 * exposing it keeps the home screen from owning a copy of it.
 */
@Composable
fun RedeemInviteFlow(onRedeemed: () -> Unit) {
    InviteCodeRoute(onRedeemed = onRedeemed)
}

@Composable
private fun InviteCodeRoute(onRedeemed: () -> Unit) {
    val viewModel: app.qrmenu.driver.auth.InviteCodeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    InviteCodeScreen(
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        onSubmit = { viewModel.redeem(onRedeemed) },
        canSubmit = state.canSubmit,
        isSubmitting = state.isSubmitting,
        error = state.error,
    )
}

/** Unwraps a possibly-wrapped `Context` down to the hosting `Activity`. */
private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("No Activity found in the Context chain — DriverPhoneVerifier requires one.")
}
