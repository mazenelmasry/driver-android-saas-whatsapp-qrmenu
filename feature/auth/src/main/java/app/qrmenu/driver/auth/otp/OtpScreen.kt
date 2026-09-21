package app.qrmenu.driver.auth.otp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.qrmenu.driver.auth.R
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.ui.components.DigitCellsField
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr

/**
 * True only when the server rejected the CODE itself, as opposed to the request
 * failing on the way there. See the call site.
 */
private fun DriverApiError?.isAboutTheCode(): Boolean =
    this is DriverApiError.Api && (
        code == DriverErrorCode.OtpInvalid || code == DriverErrorCode.OtpExpired
        )

/** How many digits the SMS carries. Firebase Phone Auth sends six. */
const val OTP_LENGTH = 6

/**
 * Screen 3 — confirming the phone with the code sent by SMS.
 *
 * 🔴 The code is entered in CELLS and is never masked. It was sent to this
 * handset seconds ago and is sitting in the notification shade a couple of
 * centimetres above the field; hiding it would protect nothing and would only
 * stop the driver checking what they typed against what they were sent. There
 * is no reveal toggle here for the same reason — there is nothing to reveal.
 *
 * This screen is reached twice in a driver's life: on first run, and when they
 * have forgotten their password. Proving the phone IS the reset, because it is
 * the only credential a driver reliably has. Both paths continue to
 * "choose a password", so an SMS is spent once per device rather than every
 * shift.
 */
@Composable
internal fun OtpScreen(
    phone: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    resendInSeconds: Int,
    isSubmitting: Boolean,
    /**
     * Android resolved the SMS by itself (auto-retrieval / Play Services
     * instant verification) and the backend call is in flight — the screen
     * shows "confirming automatically" instead of a code field with nothing
     * left to type.
     */
    isAutoVerifying: Boolean,
    /** This build has no Firebase project wired in (see `DriverPhoneVerifier`). */
    isFirebaseUnavailable: Boolean,
    error: DriverApiError?,
) {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            Text(
                text = stringResource(R.string.otp_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                // The number is isolated left-to-right: in an Arabic layout the
                // leading + would otherwise slide to the far end and no longer
                // match the number the driver just typed.
                text = stringResource(R.string.otp_subtitle, phone.ltr()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            if (isFirebaseUnavailable) {
                // 🔴 Not a retryable error: no amount of tapping "try again"
                // will ever send an SMS on a build with no Firebase project
                // (taaj, today). A generic red banner with a retry button here
                // would send the driver into a loop that can never succeed.
                Text(
                    text = stringResource(R.string.otp_firebase_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else if (isAutoVerifying) {
                // The code cells never appear at all in this path — showing
                // them empty for a moment before whisking the driver away
                // reads as a glitch, not as "this happened for you".
                AutoVerifyingIndicator()
            } else {
                DigitCellsField(
                    value = code,
                    onValueChange = { typed ->
                        onCodeChange(typed)
                        // Six digits can only mean one thing. Making the driver
                        // reach for a button after the last one is a step that
                        // exists for no reason.
                        if (typed.length == OTP_LENGTH) onVerify()
                    },
                    length = OTP_LENGTH,
                    enabled = !isSubmitting,
                    isMasked = false,
                    // 🔴 Only a rejection OF THE CODE turns the cells red. A
                    // dropped connection is not the driver mistyping, and
                    // painting all six cells red for it tells them to re-read
                    // digits that were correct — while the banner right below
                    // already says what actually happened.
                    isError = error.isAboutTheCode(),
                    onDone = onVerify,
                )
            }

            if (error != null && !isFirebaseUnavailable) {
                Spacer(Modifier.height(Spacing.md))
                DriverErrorBanner(error = error, onRetry = onVerify)
            }

            if (!isFirebaseUnavailable && !isAutoVerifying) {
                Spacer(Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onChangeNumber,
                        enabled = !isSubmitting,
                        modifier = Modifier.heightIn(min = TouchTarget.compact),
                    ) {
                        Text(stringResource(R.string.otp_change_number))
                    }

                    if (resendInSeconds > 0) {
                        // A countdown rather than a disabled button with no
                        // explanation: an SMS that has not arrived yet is the
                        // most likely reason a driver is stuck here, and
                        // "wait 27s" is the answer.
                        Text(
                            text = stringResource(R.string.otp_resend_in, resendInSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(
                            onClick = onResend,
                            enabled = !isSubmitting,
                            modifier = Modifier.heightIn(min = TouchTarget.compact),
                        ) {
                            Text(stringResource(R.string.otp_resend))
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                Button(
                    onClick = onVerify,
                    enabled = !isSubmitting && code.length == OTP_LENGTH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.primary),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ControlSize.buttonSpinner),
                            strokeWidth = ControlSize.buttonSpinnerStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.otp_verify),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else if (isFirebaseUnavailable) {
                Spacer(Modifier.height(Spacing.lg))
                TextButton(
                    onClick = onChangeNumber,
                    modifier = Modifier.heightIn(min = TouchTarget.compact),
                ) {
                    Text(stringResource(R.string.otp_change_number))
                }
            }
        }
    }
}

/**
 * Shown only during [OtpUiState.isAutoVerifying][app.qrmenu.driver.auth.OtpUiState] —
 * a brief, self-explaining wait, not the generic spinner the app otherwise
 * forbids mid-screen: the sentence next to it says exactly what is happening
 * and why nothing needs typing.
 */
@Composable
private fun AutoVerifyingIndicator() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(ControlSize.buttonSpinner * 2),
            strokeWidth = ControlSize.buttonSpinnerStroke,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.otp_auto_verifying),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(
    name = "ar dark",
    locale = "ar",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtpScreenPreview() {
    DriverTheme {
        OtpScreen(
            phone = "+966501234567",
            code = "4821",
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onChangeNumber = {},
            resendInSeconds = 27,
            isSubmitting = false,
            isAutoVerifying = false,
            isFirebaseUnavailable = false,
            error = null,
        )
    }
}

@Preview(name = "auto-verifying", locale = "ar", showBackground = true)
@Composable
private fun OtpScreenAutoVerifyingPreview() {
    DriverTheme {
        OtpScreen(
            phone = "+966501234567",
            code = "",
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onChangeNumber = {},
            resendInSeconds = 0,
            isSubmitting = true,
            isAutoVerifying = true,
            isFirebaseUnavailable = false,
            error = null,
        )
    }
}

@Preview(name = "firebase unavailable", locale = "ar", showBackground = true)
@Composable
private fun OtpScreenFirebaseUnavailablePreview() {
    DriverTheme {
        OtpScreen(
            phone = "+966501234567",
            code = "",
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onChangeNumber = {},
            resendInSeconds = 0,
            isSubmitting = false,
            isAutoVerifying = false,
            isFirebaseUnavailable = true,
            error = null,
        )
    }
}
