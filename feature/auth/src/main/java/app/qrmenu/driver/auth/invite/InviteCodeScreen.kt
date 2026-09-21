package app.qrmenu.driver.auth.invite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import app.qrmenu.driver.auth.R
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverErrorBanner

/**
 * Reached when a phone is verified but nobody has invited it yet — an empty
 * `restaurants` list from `login`/`verify-otp` (CLAUDE.md decision 15: there is
 * no self sign-up, so this is not a registration screen, it is the missing
 * half of an invitation the driver already received another way).
 *
 * The code is six characters, read aloud over the phone or pasted from a
 * WhatsApp message (decision 16) — so the field upper-cases as the driver
 * types, accepts a paste of a whole sentence and keeps only the code out of
 * it, and can never contain `0/O` or `1/I/L`: those pairs are exactly what a
 * driver mishears on a call or misreads in a small font, and typing the wrong
 * one produces a silent "invalid invite" rather than a working code.
 *
 * 🔴 Deliberately NOT `DigitCellsField` (core:ui): that component is digits-only
 * (`KeyboardType.NumberPassword`), and this code is alphanumeric. A single
 * pinned-LTR box does the same job here — fixed length shown by the character
 * counter below it, not by six boxes.
 */
@Composable
internal fun InviteCodeScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    isSubmitting: Boolean,
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
                text = stringResource(R.string.invite_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.invite_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    enabled = !isSubmitting,
                    isError = error != null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.2.em,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
                    shape = RoundedCornerShape(Radius.card),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.invite_code_placeholder),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.primary),
                )
            }

            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.invite_code_counter, code.length, InviteCodeSanitizer.LENGTH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                        text = stringResource(R.string.invite_submit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
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
private fun InviteCodeScreenPreview() {
    DriverTheme {
        InviteCodeScreen(
            code = "XG7K4",
            onCodeChange = {},
            onSubmit = {},
            canSubmit = false,
            isSubmitting = false,
            error = null,
        )
    }
}
