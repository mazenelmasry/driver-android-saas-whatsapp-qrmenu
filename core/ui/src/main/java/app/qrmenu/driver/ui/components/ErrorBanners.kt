package app.qrmenu.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.R
import app.qrmenu.driver.ui.error.isRetryable
import app.qrmenu.driver.ui.error.localized

/**
 * The app's one way of telling a driver that something failed — inline, in
 * place, never a dialog and never a toast that disappears while they are
 * looking at the road.
 *
 * 🔴 Losing signal and being refused by the server look DIFFERENT on purpose
 * (CLAUDE.md, the four mandatory states name them separately). A driver on
 * mobile data in a moving car loses signal constantly; that is the road, and it
 * fixes itself. "Your account has been stopped" never fixes itself and needs a
 * phone call to the restaurant. Rendering both as the same red box teaches the
 * driver to ignore the one that matters.
 *
 * The retry button appears only when retrying could plausibly work
 * ([isRetryable]) — offering one next to "this offer expired" teaches them the
 * button does nothing.
 */
@Composable
fun DriverErrorBanner(
    error: DriverApiError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val offline = error is DriverApiError.Offline

    val container = if (offline) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (offline) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(Radius.card))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = error.localized(),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )

        if (onRetry != null && error.isRetryable) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = TouchTarget.compact),
            ) {
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }
        }
    }
}
