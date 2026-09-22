package app.qrmenu.driver.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.R

/**
 * The coloured block every signed-in screen wears at the top.
 *
 * 🔴 Why a block of brand colour and not a plain title.
 *
 * The screens used to open with a bare word floating on the same paper as the
 * content, which is why the app read as a form rather than a product: nothing
 * anchored the eye, nothing said which app this was, and four tabs that each
 * drew their own title drifted apart within a release. Every delivery app a
 * driver already has on that phone opens with a saturated band, and the
 * absence of one is read — correctly — as "unfinished", not as "restrained".
 *
 * It also fixes an ordering bug that was worse than it looked: the app-level
 * alert banners were drawn ABOVE the screen's title, so the first thing a
 * driver's eye met was a warning about notification health rather than the
 * name of the screen they had just opened. The banners now hang BELOW this
 * block (see [DriverScreenScaffold]) — a warning is important, but it is not
 * the answer to "where am I".
 *
 * The block owns the status bar: it draws behind it and pays its inset, so
 * the gradient runs all the way to the top of the glass. That is also why it
 * flips the system icons to light for as long as it is on screen — white
 * clock on burnt orange, restored on the way out so the sign-in screens keep
 * their dark icons on paper.
 */
@Composable
fun DriverHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unreadNotifications: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    /** Anything the screen wants inside the colour — a tab row, a chip row. */
    belowTitle: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LightSystemIconsWhileVisible()

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < MID_LUMINANCE

    // 🔴 The header does NOT use `primary` in dark mode.
    //
    // `Theme.kt` lightens the brand for dark mode on purpose — `primary` there
    // is the colour of TEXT and ICONS drawn on a dark surface, so it is a pale
    // salmon. Filling the top of a dark screen with it produced exactly that
    // on the device: a washed-out band that glares at a driver working at
    // night and belongs to no brand anyone would recognise. In dark mode the
    // header takes the DEEP end of the same family instead, which is what
    // `primaryContainer` already is there.
    //
    // Two stops of the SAME family either way: a gradient the driver cannot
    // name is a gradient that reads as depth rather than as decoration.
    val base = if (isDark) scheme.primaryContainer else scheme.primary
    val highlight = if (isDark) {
        lerp(scheme.primaryContainer, scheme.secondaryContainer, HIGHLIGHT_MIX)
    } else {
        lerp(scheme.primary, scheme.secondary, HIGHLIGHT_MIX)
    }
    val brush = Brush.verticalGradient(listOf(highlight, base))
    val onHeader = if (isDark) scheme.onPrimaryContainer else scheme.onPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = Radius.header, bottomEnd = Radius.header))
            .background(brush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xxs, bottom = Spacing.md),
    ) {
        CompositionLocalProvider(LocalContentColor provides onHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PlatformLockup(onHeader = onHeader)

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onHeader,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            // Not a grey: a tint of the header's own ink, so it
                            // recedes without looking disabled.
                            color = onHeader.copy(alpha = SUBTITLE_ALPHA),
                        )
                    }
                }

                onOpenNotifications?.let {
                    HeaderBell(unread = unreadNotifications, onClick = it, onHeader = onHeader)
                }
            }

            belowTitle?.let {
                Column(modifier = Modifier.fillMaxWidth()) { it() }
            }
        }
    }
}

/**
 * The platform's own mark, on every signed-in screen.
 *
 * The app used to say who it belonged to on exactly ONE screen — sign-in,
 * which a driver sees once and then never again. Everything after that was
 * anonymous: four tabs of grey cards that could have been any app on the
 * phone. One APK serves two platforms, so the mark cannot be drawn into the
 * design; it comes from the flavour through [LocalPlatformBrand].
 *
 * Small and above the screen title on purpose. Identity is a thing a driver
 * should never have to look for and never have to look AT — the title is
 * still the largest thing on the header, because the question the header
 * answers first is "where am I", not "whose app is this".
 *
 * Renders nothing at all when no brand has been provided, which is the case
 * outside the signed-in shell.
 */
@Composable
private fun PlatformLockup(onHeader: Color) {
    val brand = LocalPlatformBrand.current ?: return
    val logoUrl = LocalPlatformLogoUrl.current

    // The monogram-and-name lockup holds the space until the logo has ACTUALLY
    // decoded, then steps aside for it entirely.
    //
    // Two earlier attempts failed on the device. Drawing the logo INSIDE the
    // monogram square left the letter showing through a logo with its own
    // background — it read as a smudge, not a mark. And dropping the monogram
    // the moment a URL exists is worse still: the URL exists long before the
    // bytes do, so on a cold start or a weak connection (the moments a driver
    // is most likely to be looking at this header) there would simply be a
    // hole where the brand should be.
    //
    // A real logo is a wordmark and already says the name, so showing the
    // name beside it would print the platform twice.
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        modifier = Modifier.padding(bottom = Spacing.xxs),
    ) {
        if (!logoLoaded) {
            Box(
                modifier = Modifier
                    .size(ControlSize.brandMark)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(onHeader.copy(alpha = BRAND_MARK_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = brand.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onHeader,
                )
            }

            // One line. At the 200% font scale this app supports, the
            // platform's name wrapped and shoved the monogram off the edge —
            // and this is a standing label beside the screen's title, not a
            // restaurant or branch name (which the project forbids clipping,
            // because a driver has to READ those to do the job).
            Text(
                text = brand,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = onHeader.copy(alpha = SUBTITLE_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                // The platform's name is what this image says, so a
                // description would only repeat it to TalkBack — which reads
                // the screen title right after.
                contentDescription = brand,
                contentScale = ContentScale.Fit,
                onSuccess = { logoLoaded = true },
                onError = { logoLoaded = false },
                // HEIGHT only — a wordmark's width is its own business, and
                // forcing it into a square is what made it unreadable.
                modifier = Modifier.height(ControlSize.brandMark),
            )
        }
    }
}

/**
 * The platform's display name for this build — "منيورا للسائقين" or "تاج
 * للسائقين", from the flavour's own `app_name`.
 *
 * Provided by `:app` because the flavour source sets live there; `:core:ui`
 * is built once for both platforms and must not know either name. `null`
 * means "no brand here", which is what the auth and onboarding screens get.
 */
val LocalPlatformBrand = staticCompositionLocalOf<String?> { null }

/**
 * The platform's logo, as uploaded in the admin panel and served by
 * `GET /driver/branding`.
 *
 * Separate from [LocalPlatformBrand] because the two have different lifetimes:
 * the name is compiled into the flavour and is therefore always true, while
 * this is a URL that may be absent, stale, or slow. Nothing may depend on it —
 * it is a layer over the monogram, never a replacement for it.
 */
val LocalPlatformLogoUrl = staticCompositionLocalOf<String?> { null }

/**
 * The bell, with the count of what the driver has not seen.
 *
 * The badge is a COUNT, not a dot: "three things happened while you were
 * riding" and "one did" are different decisions about whether to stop and
 * look. Zero renders no badge at all rather than a "0".
 *
 * White pill with brand-coloured digits rather than the theme's error red —
 * red on burnt orange is two warm saturated colours fighting, and the number
 * is the part that has to be legible at arm's length.
 */
@Composable
private fun HeaderBell(unread: Int, onClick: () -> Unit, onHeader: Color) {
    Box {
        // A soft disc behind the glyph so the bell reads as a control rather
        // than a sticker printed on the gradient.
        Box(
            modifier = Modifier
                .size(TouchTarget.compact)
                .clip(CircleShape)
                .background(onHeader.copy(alpha = BELL_WELL_ALPHA)),
        )
        IconButton(onClick = onClick, modifier = Modifier.size(TouchTarget.compact)) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.a11y_open_notifications),
                tint = onHeader,
            )
        }

        if (unread > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Spacing.xxs, y = -Spacing.xxs),
            ) {
                Text(
                    text = if (unread > MAX_BADGE) "$MAX_BADGE+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                )
            }
        }
    }
}

/**
 * Light status-bar icons for as long as a coloured header is on screen.
 *
 * `DriverTheme` sets them from the light/dark mode, which is right for the
 * sign-in and onboarding screens — those are paper to the top edge. It is
 * wrong under this header, where in LIGHT mode the theme asks for dark icons
 * and the gradient behind them is burnt orange: the clock disappears.
 *
 * Restored on dispose rather than left flipped, because the screens without a
 * header are reachable again (sign out, the unsupported-device wall).
 */
@Composable
private fun LightSystemIconsWhileVisible() {
    val view = LocalView.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < MID_LUMINANCE

    DisposableEffect(view, isDarkTheme) {
        if (view.isInEditMode) return@DisposableEffect onDispose { }
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = !isDarkTheme }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private const val MAX_BADGE = 9
private const val HIGHLIGHT_MIX = 0.28f
private const val SUBTITLE_ALPHA = 0.82f
private const val BELL_WELL_ALPHA = 0.16f
private const val BRAND_MARK_ALPHA = 0.22f
private const val MID_LUMINANCE = 0.5f
