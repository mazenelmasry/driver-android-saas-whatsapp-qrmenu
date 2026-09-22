package app.qrmenu.driver.onboarding.gms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.onboarding.R

/**
 * Screen 18 — "جهاز غير مدعوم" in the driver CLAUDE.md screen map.
 *
 * 🔴 Despite the map's short label, this is a WARNING screen, never a gate.
 * `:core:push` already falls back to a 15s poll when FCM never fires (the
 * "صفر إشعار ضائع" architecture — see CLAUDE.md), so a phone without Google
 * Mobile Services still works end-to-end. The only real consequence is that
 * no offer/assignment RINGS — the driver has to keep the app open and watch
 * the list instead of waiting for a sound. That is exactly what this screen
 * says, and nothing more; it never blocks Continue.
 *
 * Shown once, right after language selection and before permissions, only
 * when [UnsupportedDeviceViewModel.shouldWarn] is true. A driver on a normal
 * phone never sees it.
 */
@Composable
fun UnsupportedDeviceRoute(
    onContinue: () -> Unit,
    viewModel: UnsupportedDeviceViewModel = hiltViewModel(),
) {
    UnsupportedDeviceScreen(onContinue = onContinue)
}

@Composable
internal fun UnsupportedDeviceScreen(onContinue: () -> Unit) {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.size(Spacing.xxl))

            Text(
                text = stringResource(R.string.unsupported_device_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = stringResource(R.string.unsupported_device_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(Spacing.xl))

            // A single explanatory card — not a wall, not a form, just the one
            // fact the driver needs before they put the phone in the cup
            // holder: no ringing, so keep watching the list.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(Radius.card),
                    )
                    .padding(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.unsupported_device_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.primary),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Text(
                    text = stringResource(R.string.unsupported_device_continue),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Preview(name = "Unsupported device — light")
@Composable
private fun UnsupportedDeviceScreenPreviewLight() {
    DriverTheme(darkTheme = false) {
        UnsupportedDeviceScreen(onContinue = {})
    }
}

@Preview(name = "Unsupported device — dark")
@Composable
private fun UnsupportedDeviceScreenPreviewDark() {
    DriverTheme(darkTheme = true) {
        UnsupportedDeviceScreen(onContinue = {})
    }
}
