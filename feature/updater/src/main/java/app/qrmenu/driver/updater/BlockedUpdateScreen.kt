package app.qrmenu.driver.updater

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Illustration
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.text.ltr

/**
 * Screen 17 (CLAUDE.md § خريطة الشاشات) — the full-screen, un-dismissable
 * update wall. Reached only from [UpdateRequirement.Blocked]: by the time a
 * caller renders this, [UpdateDecision] has already confirmed there is no
 * trip in the driver's hands to protect (see [UpdateGateViewModel]'s doc on
 * how the deferral works) — this screen offers no button, gesture, or system
 * back handling that returns to the rest of the app. Deliberately no
 * `BackHandler` here either: the system back gesture is left to do whatever
 * it would on any other root screen (leave the app), which is a dead end for
 * the DRIVER'S WORK, not a way past this screen.
 */
@Composable
fun BlockedUpdateScreen(info: UpdateInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // A soft brand-tinted disc behind the glyph — the same "art on a
            // wash" family every empty state in the app uses (see
            // `DriverArtwork` in :core:ui), so this wall reads as a
            // considered screen rather than a generic system dialog. There is
            // no drawn illustration for "an update exists" in that shared set
            // (:core:ui is out of scope for this pass), so the disc carries
            // the family resemblance and the system-update glyph stays —
            // it is, after all, exactly what is being asked for.
            Box(
                modifier = Modifier
                    .size(Illustration.canvas)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Illustration.canvas * ICON_TO_CANVAS_RATIO),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = stringResource(R.string.updater_blocked_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.updater_blocked_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            info.versionName?.let { version ->
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.updater_blocked_version_label, version.ltr()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            info.notes?.let { notes ->
                Spacer(modifier = Modifier.height(Spacing.lg))
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.updater_blocked_notes_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))

            if (info.downloadUrl != null) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)),
                        )
                    },
                    shape = RoundedCornerShape(Radius.card),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.primaryPhysical),
                ) {
                    Text(
                        text = stringResource(R.string.updater_blocked_download_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.updater_blocked_no_download),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@BlockedUpdateScreenPreviews
@Composable
private fun BlockedUpdateScreenPreview_WithDownload() {
    DriverTheme {
        BlockedUpdateScreen(
            info = UpdateInfo(
                versionName = "1.4.0",
                downloadUrl = "https://app.taaj.me/driver/download",
                notes = "إصلاحات أداء وتسريع التتبّع الحىّ.",
            ),
        )
    }
}

@BlockedUpdateScreenPreviews
@Composable
private fun BlockedUpdateScreenPreview_NoDownload() {
    DriverTheme {
        BlockedUpdateScreen(info = UpdateInfo(versionName = "1.4.0", downloadUrl = null, notes = null))
    }
}

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class BlockedUpdateScreenPreviews

/** How much of the disc's own canvas the glyph fills — enough presence without touching the ring. */
private const val ICON_TO_CANVAS_RATIO = 0.45f
