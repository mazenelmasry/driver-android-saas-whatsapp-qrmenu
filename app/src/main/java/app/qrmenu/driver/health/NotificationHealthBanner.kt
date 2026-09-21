package app.qrmenu.driver.health

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.qrmenu.driver.R
import app.qrmenu.driver.alerts.NotificationHealthConcern
import app.qrmenu.driver.designsystem.theme.Spacing

/**
 * The frozen in-app health indicator (CLAUDE.md §🔔: "الإشعارات تعمل ✅ /
 * معطّلة ⚠️ بزر يفتح إعدادات النظام") — a driver who has permanently refused
 * `POST_NOTIFICATIONS`, muted the offer channel, or never granted DND bypass
 * gets no SYSTEM dialog to fix it (Android stops offering one after two
 * refusals), so this is the only thing that can tell them, and the only
 * thing that can send them to the right settings screen.
 *
 * Renders nothing when healthy — this must not become a permanent fixture a
 * driver learns to ignore.
 */
@Composable
fun NotificationHealthBanner(viewModel: NotificationHealthViewModel = hiltViewModel()) {
    val health by viewModel.health.collectAsState()

    // Re-read on every resume, not just once: a driver fixes this in system
    // Settings and comes straight back — a banner that lingers after the fix
    // trains them to stop reading banners.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    val concern = NotificationHealthPriority.primaryConcern(health) ?: return

    val context = LocalContext.current
    val isBlocking = concern != NotificationHealthConcern.DND_BYPASS_NOT_GRANTED

    // Same two-tone rule `LocationPermissionWarning` established: red only
    // for "nothing can reach you", a neutral info surface for a caveat about
    // what MIGHT happen (DND can silence an alert that otherwise works fine).
    val container = if (isBlocking) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (isBlocking) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val (titleRes, bodyRes) = when (concern) {
        NotificationHealthConcern.NOTIFICATIONS_DISABLED ->
            R.string.notification_health_disabled_title to R.string.notification_health_disabled_body
        NotificationHealthConcern.OFFER_CHANNEL_DISABLED ->
            R.string.notification_health_channel_title to R.string.notification_health_channel_body
        NotificationHealthConcern.DND_BYPASS_NOT_GRANTED ->
            R.string.notification_health_dnd_title to R.string.notification_health_dnd_body
        // Never returned by NotificationHealthPriority — see its doc.
        NotificationHealthConcern.FULL_SCREEN_INTENT_NOT_GRANTED ->
            R.string.notification_health_dnd_title to R.string.notification_health_dnd_body
    }

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isBlocking) Icons.Filled.NotificationsOff else Icons.Outlined.Info,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = {
                    val intent = viewModel.settingsIntentFor(concern)
                    // A driver on a stripped-down ROM that lacks the exact
                    // settings screen must not crash the app over a banner
                    // meant to help them.
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Nothing to fall back to that would not itself risk
                        // failing the same way; the banner stays on screen.
                    }
                },
            ) {
                Text(stringResource(R.string.notification_health_action))
            }
        }
    }
}
