package app.qrmenu.driver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.location.permission.rememberLocationServicesEnabled

/**
 * The device's SYSTEM location toggle being off — distinct from
 * [LocationPermissionWarning], which is about whether the app has been
 * GRANTED the permission at all. A driver can have granted every permission
 * this app asks for and still have the toggle itself off (airplane-mode
 * remnants, a battery-saving habit, an accidental quick-settings swipe): the
 * availability screen would otherwise keep reading "متاح ✓" for a whole hour
 * with nothing telling the driver the server is receiving no live fix.
 *
 * Renders nothing when the toggle is on — same rule as every other banner in
 * this bar: a fixture a driver learns to ignore is worse than nothing.
 */
@Composable
fun LocationServicesDisabledBanner(modifier: Modifier = Modifier) {
    val enabled by rememberLocationServicesEnabled()
    if (enabled) return

    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.GpsOff, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.location_services_disabled_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.location_services_disabled_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Nothing safer to fall back to; the banner stays on
                        // screen rather than crashing the app over it.
                    }
                },
            ) {
                Text(stringResource(R.string.permission_location_action))
            }
        }
    }
}
