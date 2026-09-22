package app.qrmenu.driver.updater

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Motion
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.text.ltr

/**
 * The dismissible "an update exists" notice (CLAUDE.md § التوزيع والتحديث
 * الذاتى — "إصدارى < latest_version ⇒ بانر يُذكّر بلا منع"). Never blocks:
 * only rendered by the caller once [UpdateRequirement] is `NotRequired` (see
 * [UpdateGate.kt]'s doc), so this banner and the full-screen wall never
 * appear at once.
 *
 * Dismissal is keyed to `latestVersionCode` in [UpdateDismissalStore] rather
 * than being a one-shot local flag — see that store's own doc for why closing
 * it is per-version, not forever.
 */
@Composable
fun UpdateBanner(
    availability: UpdateAvailability.Optional,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(ControlSize.inlineIcon),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.updater_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                availability.info.versionName?.let { version ->
                    Text(
                        text = stringResource(R.string.updater_banner_body, version.ltr()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                if (availability.info.downloadUrl != null) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(availability.info.downloadUrl)),
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.updater_banner_update_action),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(TouchTarget.compact),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.updater_a11y_dismiss_banner),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Wraps [UpdateBanner] with the appear/disappear transition — `spring()`-based
 * per driver-ui-standards (no `tween()` for a driver-caused dismissal).
 */
@Composable
fun UpdateBannerHost(
    availability: UpdateAvailability,
    onDismiss: (latestVersionCode: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = availability is UpdateAvailability.Optional,
        enter = fadeIn(animationSpec = Motion.snappy),
        exit = fadeOut(animationSpec = Motion.snappy),
        modifier = modifier,
    ) {
        (availability as? UpdateAvailability.Optional)?.let { optional ->
            UpdateBanner(availability = optional, onDismiss = { onDismiss(optional.latestVersionCode) })
        }
    }
}

@UpdateBannerPreviews
@Composable
private fun UpdateBannerPreview() {
    DriverTheme {
        UpdateBanner(
            availability = UpdateAvailability.Optional(
                latestVersionCode = 10_400,
                info = UpdateInfo(versionName = "1.4.0", downloadUrl = "https://app.taaj.me/driver/download", notes = null),
            ),
            onDismiss = {},
        )
    }
}

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class UpdateBannerPreviews
