package app.qrmenu.driver.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget

/**
 * The notification centre — what reached this driver while they were riding.
 *
 * 🔴 Today it has nothing to list, and that is a fact about the SYSTEM, not
 * about this driver: push (FCM) and the offer pipeline land in week 4. The
 * empty state therefore says exactly that in a sentence a driver can act on
 * ("you will be told here when an order is offered to you"), rather than the
 * generic "no notifications" that reads like something is broken. When the
 * offer pipeline lands, this screen gains its list and loses nothing else.
 *
 * It is a screen and not a dropdown because a driver checks it after the fact,
 * often parked, sometimes scrolling back over an hour of a shift — and a menu
 * that closes on an accidental tap is the wrong container for that.
 */
@Composable
fun NotificationCenterRoute(onBack: () -> Unit) {
    NotificationCenterScreen(onBack = onBack)
}

@Composable
private fun NotificationCenterScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget.compact)) {
                Icon(
                    // AutoMirrored: the arrow has to point the other way in
                    // Arabic and Urdu, and this app is RTL-first.
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.notifications_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        EmptyNotifications()
    }
}

@Composable
private fun EmptyNotifications() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(Spacing.md)
                    .size(ControlSize.orderAvatar),
            )
        }

        Text(
            text = stringResource(R.string.notifications_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.notifications_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Empty · ar", locale = "ar", showBackground = true)
@Preview(name = "Empty · ar dark", locale = "ar", showBackground = true, uiMode = 0x21)
@Preview(name = "Empty · en", locale = "en", showBackground = true)
@Composable
private fun NotificationCenterPreview() {
    DriverTheme {
        NotificationCenterScreen(onBack = {})
    }
}
