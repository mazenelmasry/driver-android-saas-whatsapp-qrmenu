package app.qrmenu.driver.auth.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import app.qrmenu.driver.auth.R
import app.qrmenu.driver.auth.login.MIN_PASSWORD_LENGTH
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverErrorBanner

/**
 * Screen 4 — choosing the password, straight after the phone was confirmed.
 *
 * 🔴 The password is CHOSEN by the driver. Nothing generates it and nothing
 * sends it to them — that is the whole difference from the code on the previous
 * screen, and the reason this screen exists at all: an SMS is spent once per
 * device instead of on every shift.
 *
 * Reached on first run, and again whenever a driver forgets. Both arrive here
 * through the same OTP, because proving the phone is the only reset a driver can
 * actually perform on the road.
 *
 * Both fields are ordinary password fields — one box, masked, with a reveal
 * toggle. Not cells: cells are the shape of something you are copying across,
 * and this is something you are inventing and will have to remember for months.
 * The confirm field exists because a typo here locks the driver out of every
 * shift until they sit through another SMS.
 */
@Composable
internal fun SetPasswordScreen(
    password: String,
    confirmation: String,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onSubmit: () -> Unit,
    isSubmitting: Boolean,
    error: DriverApiError?,
) {
    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    val canSubmit = !isSubmitting && password.length >= MIN_PASSWORD_LENGTH && confirmation == password

    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            Text(
                text = stringResource(R.string.set_password_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.set_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xl))

            PasswordEntry(
                label = stringResource(R.string.set_password_label),
                value = password,
                onValueChange = onPasswordChange,
                isVisible = isVisible,
                onToggleVisibility = onToggleVisibility,
                enabled = !isSubmitting,
                isError = tooShort,
                imeAction = ImeAction.Next,
                onDone = {},
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.set_password_rule),
                style = MaterialTheme.typography.bodySmall,
                // The rule is stated up front, in grey, and only turns into an
                // error once it has actually been broken — a red line before the
                // driver has typed anything reads as a failure they caused.
                color = if (tooShort) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.height(Spacing.md))

            PasswordEntry(
                label = stringResource(R.string.set_password_confirm_label),
                value = confirmation,
                onValueChange = onConfirmationChange,
                isVisible = isVisible,
                onToggleVisibility = onToggleVisibility,
                enabled = !isSubmitting,
                isError = mismatch,
                imeAction = ImeAction.Done,
                onDone = { if (canSubmit) onSubmit() },
            )
            if (mismatch) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.set_password_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(Spacing.md))
                DriverErrorBanner(error = error, onRetry = onSubmit)
            }

            Spacer(Modifier.height(Spacing.xl))

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
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
                        text = stringResource(R.string.set_password_submit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * One password box. The label reads in the interface's direction; the VALUE is
 * pinned left-to-right, like every other credential in this app.
 *
 * The reveal toggle is shared between the two fields on purpose — they are meant
 * to be compared, and revealing one while the other stays hidden defeats the
 * comparison the confirm field exists for.
 */
@Composable
private fun PasswordEntry(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                isError = isError,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Left),
                visualTransformation = if (isVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                shape = RoundedCornerShape(Radius.card),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility, enabled = enabled) {
                        Icon(
                            imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (isVisible) R.string.login_password_hide else R.string.login_password_show,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.primary),
            )
        }
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
private fun SetPasswordScreenPreview() {
    DriverTheme {
        SetPasswordScreen(
            password = "secret12",
            confirmation = "secret1",
            onPasswordChange = {},
            onConfirmationChange = {},
            isVisible = false,
            onToggleVisibility = {},
            onSubmit = {},
            isSubmitting = false,
            error = null,
        )
    }
}
