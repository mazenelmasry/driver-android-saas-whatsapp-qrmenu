package app.qrmenu.driver.notifications

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.database.entity.NotificationHistoryEntity
import app.qrmenu.driver.database.entity.NotificationHistoryType
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.components.DriverArt
import app.qrmenu.driver.ui.components.DriverEmptyState

/**
 * The notification centre — what reached this driver while they were riding.
 *
 * Backed by [NotificationCenterViewModel], itself backed by the local
 * `notification_history` Room table (`:core:database`'s
 * `NotificationHistoryStore`) that `:core:push` writes to the moment a live
 * push arrives — see that store's class doc for why this table holds no
 * title/body text (rendered here instead, from resources, so a language
 * switch after a notification arrived still renders correctly).
 *
 * Opening this screen marks everything read, which is also why there is no
 * per-item "mark as read" affordance: the whole point of a driver opening
 * the centre is that they have now seen it.
 *
 * It is a screen and not a dropdown because a driver checks it after the fact,
 * often parked, sometimes scrolling back over an hour of a shift — and a menu
 * that closes on an accidental tap is the wrong container for that.
 */
@Composable
fun NotificationCenterRoute(onBack: () -> Unit) {
    val viewModel: NotificationCenterViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationCenterScreen(state = state, onBack = onBack, onRetry = viewModel::retry)
}

@Composable
private fun NotificationCenterScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.md),
    ) {
        Row(
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

        when {
            state.isLoading -> NotificationsLoadingSkeleton()

            state.error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NotificationsErrorBanner(onRetry = onRetry)
            }

            state.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DriverEmptyState(
                    art = DriverArt.QuietBell,
                    title = stringResource(R.string.notifications_empty_title),
                    body = stringResource(R.string.notifications_empty_body),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.items, key = { it.id }) { notification ->
                    NotificationHistoryCard(notification)
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryCard(notification: NotificationHistoryEntity, modifier: Modifier = Modifier) {
    val (title, body) = notificationText(notification)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Unread indicator — fades the instant the screen finishes
            // marking history read, which is the intended behaviour (see
            // NotificationCenterViewModel's doc): a driver sees what was
            // new, then the badge clears itself.
            if (!notification.is_read) {
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .size(Spacing.xs)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else {
                Box(modifier = Modifier.size(Spacing.xs))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatNotificationTime(notification.occurred_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun notificationText(notification: NotificationHistoryEntity): Pair<String, String> = when (notification.type) {
    NotificationHistoryType.Offer -> stringResource(R.string.notifications_item_offer_title) to
        stringResource(R.string.notifications_item_offer_body, notification.order_id ?: 0L)
}

/** The local-storage failure state — see [NotificationsUiState.error]'s own doc. */
@Composable
private fun NotificationsErrorBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.notifications_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = TouchTarget.compact)) {
            Text(stringResource(R.string.notifications_error_retry))
        }
    }
}

/** Shimmer matching the eventual card shape — never a centred spinner. */
@Composable
private fun NotificationsLoadingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "notifications_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "notifications_skeleton_shimmer_translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(600f * translate - 600f, 0f),
        end = Offset(600f * translate, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.giant)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(brush),
            )
        }
    }
}

// region Previews

private val previewItems = listOf(
    NotificationHistoryEntity(
        id = 1,
        type = NotificationHistoryType.Offer,
        order_id = 4821,
        offer_id = 9001,
        occurred_at = 1_700_000_000_000L,
        is_read = false,
    ),
    NotificationHistoryEntity(
        id = 2,
        type = NotificationHistoryType.Offer,
        order_id = 4790,
        offer_id = 8990,
        occurred_at = 1_699_990_000_000L,
        is_read = true,
    ),
)

@Preview(name = "Empty · ar", locale = "ar", showBackground = true)
@Preview(name = "Empty · ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Empty · en", locale = "en", showBackground = true)
@Preview(name = "Empty · ur", locale = "ur", showBackground = true)
@Preview(name = "Empty · bn", locale = "bn", showBackground = true)
@Preview(name = "Empty · hi", locale = "hi", showBackground = true)
@Composable
private fun NotificationCenterEmptyPreview() {
    DriverTheme {
        NotificationCenterScreen(state = NotificationsUiState(isLoading = false), onBack = {}, onRetry = {})
    }
}

@Preview(name = "Loading · ar", locale = "ar", showBackground = true)
@Composable
private fun NotificationCenterLoadingPreview() {
    DriverTheme {
        NotificationCenterScreen(state = NotificationsUiState(isLoading = true), onBack = {}, onRetry = {})
    }
}

@Preview(name = "Error · ar", locale = "ar", showBackground = true)
@Composable
private fun NotificationCenterErrorPreview() {
    DriverTheme {
        NotificationCenterScreen(
            state = NotificationsUiState(isLoading = false, error = true),
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Content · ar", locale = "ar", showBackground = true)
@Preview(name = "Content · ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Content · en", locale = "en", showBackground = true)
@Composable
private fun NotificationCenterContentPreview() {
    DriverTheme {
        NotificationCenterScreen(
            state = NotificationsUiState(isLoading = false, items = previewItems),
            onBack = {},
            onRetry = {},
        )
    }
}

// endregion
