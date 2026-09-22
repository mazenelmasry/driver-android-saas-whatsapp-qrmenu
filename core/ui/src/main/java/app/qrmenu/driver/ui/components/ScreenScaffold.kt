package app.qrmenu.driver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget

/**
 * The frame every main screen wears.
 *
 * It exists so the four destinations read as one app rather than four screens
 * written in four weeks: same header, same bell, same page padding, same
 * background. A screen that builds its own header drifts within a release,
 * and the drift is what makes an app look assembled rather than designed.
 *
 * 🔴 The order here is the fix to a real complaint: TITLE first, then the
 * app-level alert banners, then the content. The banners used to be drawn
 * above every tab's scaffold — so the first thing a driver's eye met on
 * opening any screen was a warning, and the name of the screen came second.
 * They now arrive through [LocalAppBanners], which `SignedInScreen` fills and
 * this reads, so the banners can stay app-wide without being app-TOP.
 */
@Composable
fun DriverScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadNotifications: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    /** Content placed INSIDE the coloured block, under the title — a tab row. */
    belowTitle: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val banners = LocalAppBanners.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The header pays the status-bar inset itself — it draws behind the
        // clock on purpose, so the gradient reaches the top of the glass.
        DriverHeader(
            title = title,
            subtitle = subtitle,
            unreadNotifications = unreadNotifications,
            onOpenNotifications = onOpenNotifications,
            belowTitle = belowTitle,
        )

        banners()

        content()
    }
}

/**
 * The app-level alert banners (notification health, unsent actions, "an
 * update exists"), handed down from `SignedInScreen` to whichever scaffold is
 * on screen.
 *
 * A composition local rather than a parameter on every route: these belong to
 * the app, not to any one screen, and threading them through four route
 * signatures would mean a fifth screen silently loses them the day it is
 * added. The default renders nothing, which is what the auth and onboarding
 * screens — outside the signed-in shell — should show.
 */
val LocalAppBanners = staticCompositionLocalOf<@Composable ColumnScope.() -> Unit> { {} }


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

