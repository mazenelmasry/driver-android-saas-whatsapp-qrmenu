package app.qrmenu.driver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.R

/**
 * The frame every main screen wears.
 *
 * It exists so the four destinations read as one app rather than four screens
 * written in four weeks: same title placement, same bell, same page padding,
 * same background. A screen that builds its own header drifts within a
 * release, and the drift is what makes an app look assembled rather than
 * designed.
 */
@Composable
fun DriverScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadNotifications: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The app draws edge-to-edge, so the frame — not each screen —
            // owes the status bar its space. Without this the title sits
            // under the clock on the one screen that forgot to ask.
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            onOpenNotifications?.let { NotificationBell(unreadNotifications, it) }
        }

        content()
    }
}

/**
 * The bell, with the count of what the driver has not seen.
 *
 * The badge is a COUNT, not a dot: "three things happened while you were
 * riding" and "one did" are different decisions about whether to stop and
 * look. Zero renders no badge at all rather than a "0".
 */
@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(TouchTarget.compact),
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = stringResource(R.string.a11y_open_notifications),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (unread > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -Spacing.xxs, y = Spacing.xxs),
            ) {
                Text(
                    text = if (unread > MAX_BADGE) "$MAX_BADGE+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                )
            }
        }
    }
}

private const val MAX_BADGE = 9

/**
 * A titled block of related rows — the unit every settings-like screen is
 * built from. Same surface treatment as an order card, so a driver moving
 * between tabs is looking at one material, not three.
 */
@Composable
fun DriverSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xxs),
            )
        }

        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * One row inside a [DriverSection]: an icon, a label, an optional value, and
 * an optional tap.
 *
 * [TouchTarget.compact] rather than the 64dp trip-action floor — these are
 * settings a driver reads while parked, not an action taken at speed. The
 * 64dp rule is for trip actions and is not relaxed by this.
 */
@Composable
fun DriverSettingRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = TouchTarget.compact)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ControlSize.inlineIcon),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        trailing?.invoke()
    }
}

/** A hairline between rows of the same section. */
@Composable
fun DriverRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.xxl),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = Stroke.hairline,
    )
}

