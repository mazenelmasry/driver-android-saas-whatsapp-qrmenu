package app.qrmenu.driver.auth.login

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.auth.BuildConfig
import app.qrmenu.driver.auth.R
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.BrandingDto
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr
import coil3.compose.AsyncImage
import java.util.Locale

/**
 * Screen 2 — signing in with a phone number and a PASSWORD (decision 17, as
 * amended by the project owner on 2026-09-21).
 *
 * There is no e-mail: a driver is invited by a restaurant and identified by
 * their PHONE, the one thing they were reached on and will not mistype from
 * memory.
 *
 * The password is deliberately NOT the six digits that arrive by SMS. Those
 * are a CODE, typed once per device into open cells; this is a PASSWORD, typed
 * from memory for months into one masked field with an eye. Confusing the two
 * names is what produced the earlier four-digit design — the shape follows the
 * name, so the names are kept apart.
 */
@Composable
fun LoginRoute(
    onSignedIn: (hasRestaurants: Boolean) -> Unit,
    /**
     * Carries the number the driver already typed, so the OTP screen does not
     * ask for it a second time — and so a driver who cannot remember their
     * password is not also made to retype the one thing they got right.
     */
    onForgotPassword: (phone: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isPickingRegion by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()

    LoginScreen(
        state = state,
        onNumberChange = viewModel::onNumberChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePassword = viewModel::onTogglePasswordVisibility,
        onPickRegion = { isPickingRegion = true },
        onToggleRemember = viewModel::onToggleRememberNumber,
        onForgotPassword = { viewModel.forgotPassword(onForgotPassword) },
        // Same destination as "forgot": both need the phone proved by SMS, and
        // both end at "choose a password". They are worded differently because
        // the driver arriving at each has a different question in mind.
        onFirstTime = { viewModel.forgotPassword(onForgotPassword) },
        onSubmit = {
            viewModel.submit(
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                appVersionCode = BuildConfig.APP_VERSION_CODE,
                onSuccess = onSignedIn,
            )
        },
    )

    if (isPickingRegion) {
        DialingRegionPicker(
            regions = viewModel.regionsFor(locale),
            onPick = {
                viewModel.onRegionSelected(it)
                isPickingRegion = false
            },
            onDismiss = { isPickingRegion = false },
        )
    }
}

@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onNumberChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onPickRegion: () -> Unit,
    onToggleRemember: () -> Unit,
    onForgotPassword: () -> Unit,
    onFirstTime: () -> Unit,
    onSubmit: () -> Unit,
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

            PlatformMark(branding = state.branding)

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            PhoneNumberField(
                region = state.region,
                nationalNumber = state.nationalNumber,
                onNumberChange = onNumberChange,
                onPickRegion = onPickRegion,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))

            PasswordField(
                password = state.password,
                onPasswordChange = onPasswordChange,
                isVisible = state.isPasswordVisible,
                onToggleVisibility = onTogglePassword,
                enabled = !state.isSubmitting,
                onDone = onSubmit,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))

            RememberAndForgotRow(
                rememberNumber = state.rememberNumber,
                onToggleRemember = onToggleRemember,
                onForgotPassword = onForgotPassword,
                enabled = !state.isSubmitting,
            )

            if (state.error != null) {
                Spacer(Modifier.height(Spacing.md))
                DriverErrorBanner(error = state.error, onRetry = onSubmit)
            }

            Spacer(Modifier.height(Spacing.xl))

            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.primary),
                shape = RoundedCornerShape(Radius.card),
            ) {
                if (state.isSubmitting) {
                    // The one place a spinner is right: it is INSIDE the button
                    // the driver just pressed, saying "this press is being
                    // worked on". The rule forbidden elsewhere is a spinner
                    // standing in for a screen's content.
                    CircularProgressIndicator(
                        modifier = Modifier.size(ControlSize.buttonSpinner),
                        strokeWidth = ControlSize.buttonSpinnerStroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_submit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // 🔴 Without this, a driver the restaurant has just invited is
            // stuck: they have a phone number and no password, and nothing on
            // screen offers a way to get one. Firebase does not send a code by
            // itself — the APP asks for it — so there has to be a visible way to
            // ask. It sits under the button because most sign-ins are returning
            // drivers, and it is worded as a question so the new driver
            // recognises themselves in it.
            TextButton(
                onClick = onFirstTime,
                enabled = !state.isSubmitting,
                modifier = Modifier.heightIn(min = TouchTarget.compact),
            ) {
                Text(
                    text = stringResource(R.string.login_first_time),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (state.phoneHint != null) {
                Text(
                    text = stringResource(
                        when (state.phoneHint) {
                            PhoneHint.Missing -> R.string.login_first_time_needs_phone
                            PhoneHint.Invalid -> R.string.login_phone_invalid
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(Spacing.lg))

            AppVersionLine()

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * "Remember my number" and "Forgot your code?" on one line.
 *
 * 🔴 The switch remembers the NUMBER, and the label says exactly that — it does
 * not control whether the driver stays signed in. The session lasts thirty days
 * and renews on use no matter what this is set to, so a switch labelled "keep me
 * signed in" would be claiming credit for something it does not do, and turning
 * it off would look like a way to sign out. Remembering the number is a real,
 * separate convenience: the phone is not a secret, the code is, and a driver
 * signed out mid-shift should not have to type their number again to get back in.
 *
 * "Forgot your code?" is the OTP path (decision 18) — the phone gets a one-time
 * code and the driver sets a new secret one, rather than burning an SMS on every
 * sign-in. The restaurant can also reset it from the panel.
 */
@Composable
private fun RememberAndForgotRow(
    rememberNumber: Boolean,
    onToggleRemember: () -> Unit,
    onForgotPassword: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // The whole label is the target, not just the switch: a 32dp
                // thumb is not something to aim at one-handed.
                .heightIn(min = TouchTarget.compact)
                .toggleable(
                    value = rememberNumber,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { onToggleRemember() },
                ),
        ) {
            Switch(
                checked = rememberNumber,
                // null: the Row above owns the click and the semantics, so the
                // switch must not announce itself a second time.
                onCheckedChange = null,
                enabled = enabled,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.login_remember_me),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(
            onClick = onForgotPassword,
            enabled = enabled,
            modifier = Modifier.heightIn(min = TouchTarget.compact),
        ) {
            Text(
                text = stringResource(R.string.login_forgot_password),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * The platform's mark — fetched from the admin panel, never shipped in the APK.
 *
 * 🔴 A logo compiled into the app means a rebrand needs a new build pushed to
 * every driver's phone. It is read from `GET /driver/branding` instead, which is
 * public precisely so it can be shown HERE, before anyone has signed in.
 *
 * It renders nothing at all while loading and nothing if the fetch failed — a
 * placeholder box or a broken-image glyph on the sign-in screen reads as "this
 * app is broken" at the exact moment a new driver is deciding whether to trust
 * it. The platform NAME below it always shows, falling back to the flavour's own.
 */
@Composable
private fun PlatformMark(branding: BrandingDto?) {
    val isArabic = LocalConfiguration.current.locales[0]?.language == "ar"
    val name = (if (isArabic) branding?.nameAr else branding?.nameEn)
        ?: branding?.nameEn
        ?: branding?.nameAr
        ?: stringResource(R.string.app_platform_name_fallback)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (branding?.logoUrl != null) {
            AsyncImage(
                model = branding.logoUrl,
                // The name is right underneath, so announcing the logo as well
                // would just make TalkBack read the platform twice.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(ControlSize.platformLogo),
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The installed version, at the bottom of the screen.
 *
 * It is here because it is the first thing anyone asks a driver who reports a
 * problem, and the self-updater's whole argument is about which build they are
 * on. Shown as name and code together: the NAME is what a person says out loud,
 * the CODE is what the updater actually compares (as strings, "1.10.0" sorts
 * before "1.9.0").
 *
 * Direction-isolated, or the brackets and dots reorder inside an Arabic layout.
 */
@Composable
private fun AppVersionLine() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})".ltr(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LoginScreenPreview() {
    DriverTheme {
        LoginScreen(
            state = LoginUiState(
                region = DialingRegion("SA", "السعودية", 966),
                nationalNumber = "501234567",
                password = "secret",
            ),
            onNumberChange = {},
            onPasswordChange = {},
            onTogglePassword = {},
            onPickRegion = {},
            onToggleRemember = {},
            onForgotPassword = {},
            onFirstTime = {},
            onSubmit = {},
        )
    }
}
