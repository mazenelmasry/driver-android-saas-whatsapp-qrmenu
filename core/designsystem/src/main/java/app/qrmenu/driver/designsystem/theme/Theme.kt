package app.qrmenu.driver.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.qrmenu.driver.designsystem.R

/**
 * The driver app theme — Material 3, brand-coloured, light AND dark.
 *
 * Ported from the POS design system (which is the settled Material 3 redesign on
 * that repo's `main`) with FOUR DELIBERATE DEVIATIONS. They are listed here
 * because copying POS verbatim would be wrong for a phone in a driver's hand:
 *
 *  1. **System font scale is honoured.** POS pins `fontScale = 1f` because its
 *     fixed tablet layouts and printed receipts break when enlarged. A driver
 *     may be older, may be reading in sunlight, and must be able to enlarge
 *     text. Every screen here is built to survive 200% (CLAUDE.md § الوصول).
 *  2. **A dark scheme exists and is designed.** POS is light-only because the
 *     cashier stands under shop lighting. Drivers work at night; a white screen
 *     at 1 a.m. is a usability and safety problem, not a preference.
 *  3. **No per-device UI scale.** POS offers compact/medium/large for tablet
 *     size preference. On a phone the system font scale already covers it.
 *  4. **No per-device accent picker.** The driver app's identity is the
 *     platform's, not the device owner's. The accent comes from the flavor, and
 *     may be overridden once by the platform default the admin panel sends at
 *     login — never by the driver.
 */

/** Builds a scheme from the nine brand values in `driver_brand_colors.xml`. */
@Composable
private fun brandColorScheme(accentSeed: Color?, dark: Boolean): ColorScheme {
    val accent = accentSeed?.let { AccentPalette.from(it) }
    val basePrimary = accent?.primary ?: colorResource(R.color.driver_brand_primary)
    val paper = colorResource(R.color.driver_brand_surface)
    val ink = colorResource(R.color.driver_brand_ink)

    return if (dark) {
        // Dark: the page is a near-ink tone of the brand (never pure black — an
        // OLED black makes the white cards vibrate at night), surfaces are steps
        // UP from it, and the primary is lightened so it stays legible on dark.
        val page = lerp(ink, Color.Black, 0.35f)
        fun tone(fraction: Float) = lerp(page, paper, fraction)
        val primary = lerp(basePrimary, Color.White, 0.34f)
        darkColorScheme(
            primary = primary,
            onPrimary = lerp(ink, Color.Black, 0.5f),
            primaryContainer = lerp(basePrimary, Color.Black, 0.45f),
            onPrimaryContainer = lerp(basePrimary, Color.White, 0.80f),
            inversePrimary = basePrimary,
            secondary = lerp(colorResource(R.color.driver_brand_secondary), Color.White, 0.30f),
            onSecondary = lerp(ink, Color.Black, 0.5f),
            secondaryContainer = lerp(colorResource(R.color.driver_brand_secondary), Color.Black, 0.55f),
            onSecondaryContainer = lerp(colorResource(R.color.driver_brand_secondary), Color.White, 0.80f),
            tertiary = lerp(Success, Color.White, 0.30f),
            onTertiary = Color.Black,
            tertiaryContainer = lerp(Success, Color.Black, 0.55f),
            onTertiaryContainer = lerp(Success, Color.White, 0.85f),
            error = lerp(Danger, Color.White, 0.28f),
            onError = Color.Black,
            errorContainer = lerp(Danger, Color.Black, 0.55f),
            onErrorContainer = lerp(Danger, Color.White, 0.85f),
            background = page,
            onBackground = tone(0.92f),
            surface = tone(0.07f),
            onSurface = tone(0.92f),
            surfaceVariant = tone(0.12f),
            onSurfaceVariant = tone(0.66f),
            surfaceTint = primary,
            inverseSurface = paper,
            inverseOnSurface = ink,
            outline = tone(0.42f),
            outlineVariant = tone(0.18f),
            scrim = Color.Black,
            surfaceBright = tone(0.16f),
            surfaceDim = page,
            surfaceContainerLowest = page,
            surfaceContainerLow = tone(0.05f),
            surfaceContainer = tone(0.08f),
            surfaceContainerHigh = tone(0.11f),
            surfaceContainerHighest = tone(0.15f),
        )
    } else {
        // Light: the page is a tonal step of the brand paper and the cards are
        // white on top of it — the floating-panel language, app-wide.
        fun tone(fraction: Float) = lerp(paper, ink, fraction)
        lightColorScheme(
            primary = basePrimary,
            onPrimary = accent?.onPrimary ?: colorResource(R.color.driver_brand_on_primary),
            primaryContainer = accent?.container ?: colorResource(R.color.driver_brand_primary_container),
            onPrimaryContainer = accent?.onContainer
                ?: colorResource(R.color.driver_brand_on_primary_container),
            inversePrimary = accent?.container ?: colorResource(R.color.driver_brand_primary_container),
            secondary = colorResource(R.color.driver_brand_secondary),
            onSecondary = Color.White,
            secondaryContainer = colorResource(R.color.driver_brand_secondary_container),
            onSecondaryContainer = colorResource(R.color.driver_brand_on_secondary_container),
            tertiary = Success,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDDF3E4),
            onTertiaryContainer = Color(0xFF0B4A22),
            error = Danger,
            onError = Color.White,
            errorContainer = Color(0xFFFDE2E1),
            onErrorContainer = Color(0xFF7A1212),
            background = tone(0.035f),
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = tone(0.07f),
            onSurfaceVariant = tone(0.68f),
            surfaceTint = basePrimary,
            inverseSurface = ink,
            inverseOnSurface = paper,
            outline = tone(0.38f),
            outlineVariant = tone(0.14f),
            scrim = Color.Black,
            surfaceBright = Color.White,
            surfaceDim = tone(0.09f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(paper, Color.White, 0.5f),
            surfaceContainer = tone(0.035f),
            surfaceContainerHigh = tone(0.06f),
            surfaceContainerHighest = tone(0.09f),
        )
    }
}

/** Material's shape roles, matched to the panel radii in [Radius]. */
private val DriverShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DriverTheme(
    /** Platform default sent by the backend at login; null = the flavor's palette. */
    accentSeed: Color? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = brandColorScheme(accentSeed, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // NOTE: no LocalDensity override. Deviation 1 — the system font scale is
    // honoured on purpose. If a layout breaks at 200%, fix the layout.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DriverTypography,
        shapes = DriverShapes,
        content = content,
    )
}
