package app.qrmenu.driver.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import app.qrmenu.driver.auth.R
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.components.DigitCellsField

/**
 * The phone field: dialling code on the LEFT, the number typed left-to-right
 * after it — in every language, including Arabic and Urdu.
 *
 * 🔴 This is one of the few places where the app deliberately ignores the
 * reading direction of the interface. A phone number is not prose: it is dialled
 * left-to-right, it is printed left-to-right on the card the restaurant handed
 * the driver, and the country code comes first when it is spoken. Letting an
 * Arabic layout mirror it would put `+966` on the right of the digits, so what
 * the driver sees no longer matches what they were told, and the `+` would drift
 * across the number as they type.
 *
 * So the whole row is pinned to [LayoutDirection.Ltr] and the text is
 * left-aligned inside it, while the LABEL above stays in the interface's own
 * direction — the label is prose, the value is not.
 */
@Composable
internal fun PhoneNumberField(
    region: DialingRegion?,
    nationalNumber: String,
    onNumberChange: (String) -> Unit,
    onPickRegion: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.login_phone_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialCodeButton(
                    region = region,
                    enabled = enabled,
                    onClick = onPickRegion,
                )

                OutlinedTextField(
                    value = nationalNumber,
                    // Digits only. A driver typing on a phone keypad in a car
                    // should not be able to produce a value the parser then
                    // rejects for a stray space or dash.
                    onValueChange = { typed -> onNumberChange(typed.filter(Char::isDigit).take(MAX_NATIONAL_DIGITS)) },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Left),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTarget.primary),
                )
            }
        }
    }
}

/**
 * The dialling code, as a button that opens the country list.
 *
 * It shows the CODE, not the country name or a flag: `+966` is what the driver
 * is checking against, it is the same width in every language, and it does not
 * need translating. The name belongs in the list, where there is room to read it.
 */
@Composable
private fun DialCodeButton(
    region: DialingRegion?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(ControlSize.dialCode)
            .heightIn(min = TouchTarget.primary)
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = region?.displayDialCode ?: stringResource(R.string.login_dial_code_unknown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The secret code — a PASSWORD field: one box, masked, with a reveal toggle.
 *
 * 🔴 Deliberately NOT the cell field ([DigitCellsField]). Cells are the shape of
 * a one-time code: they announce a fixed length that has just been sent to you
 * and that you are copying across. This value is a lasting credential typed from
 * memory, and dressing it as an OTP invites the driver to go looking for an SMS
 * that was never sent.
 *
 * That split is also why the eye lives HERE and not there: a code the phone just
 * received is sitting in the notification shade two centimetres away, so hiding
 * it protects nothing. A remembered secret is worth hiding from whoever is
 * standing at the branch counter — and the toggle exists because "wrong code"
 * and "I have forgotten my code" look identical behind four dots.
 *
 * It starts hidden: the driver may be handing the phone over.
 *
 * The value is pinned left-to-right, like the phone number: it is dialled, not
 * read as prose.
 */
@Composable
internal fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.login_password_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = password,
                // No digit filter and no length cap: this is a password the
                // driver chose, not a code with a known shape. Filtering it to
                // digits is what made it look like an OTP.
                onValueChange = onPasswordChange,
                enabled = enabled,
                isError = isError,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                visualTransformation = if (isVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                shape = RoundedCornerShape(Radius.card),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility, enabled = enabled) {
                        Icon(
                            imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            // Describes what the button DOES, not what is drawn
                            // on it — the only thing a TalkBack user can act on.
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

/**
 * The shortest password the contract accepts. Checked here too so the driver is
 * told before a round trip — and before it costs one of the five attempts a
 * minute the login limiter allows.
 */
internal const val MIN_PASSWORD_LENGTH = 6

/**
 * A phone number can be 15 digits at most (ITU E.164), and the country code is
 * separate here — so anything past this is a typo, not a number.
 */
private const val MAX_NATIONAL_DIGITS = 15
