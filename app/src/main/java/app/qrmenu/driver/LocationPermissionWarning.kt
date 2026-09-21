package app.qrmenu.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.designsystem.theme.Spacing

/**
 * The standing location-permission notice on the availability screen.
 *
 * 🔴 Two different situations, said in two different voices, because the driver
 * has to do two different things:
 *
 *  - [isBlocking] — location is refused outright. Nothing can work; this is red
 *    and it asks.
 *  - otherwise — "Allow all the time" was refused but location itself is
 *    granted. That is a SUPPORTED state (frozen decision 40: the app is designed
 *    to run on foreground location alone), so it must read as a caution about
 *    what may happen with the screen off, never as a fault. A driver told their
 *    app is broken when it is working stops trusting every later warning.
 */
@Composable
fun LocationPermissionWarning(isBlocking: Boolean, onAction: () -> Unit) {
    // 🔴 The non-blocking case is NOT green and NOT a warning triangle.
    //
    // Green reads "all good" and a triangle reads "something is wrong"; together
    // they say nothing. This state is neither: location works, the app is doing
    // its job, and there is a caveat worth knowing about. That is a neutral
    // surface and an info mark. Only the blocking case — location refused
    // outright, nothing can reach the driver — earns the error colour.
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
                imageVector = if (isBlocking) Icons.Filled.LocationOff else Icons.Outlined.Info,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isBlocking) R.string.permission_location_blocked_title
                        else R.string.permission_location_partial_title,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (isBlocking) R.string.permission_location_blocked_body
                        else R.string.permission_location_partial_body,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onAction) {
                Text(stringResource(R.string.permission_location_action))
            }
        }
    }
}
