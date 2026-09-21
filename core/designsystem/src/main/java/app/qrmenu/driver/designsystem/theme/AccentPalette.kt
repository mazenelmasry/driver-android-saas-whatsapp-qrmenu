package app.qrmenu.driver.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The primary-colour family derived from one accent seed chosen in Settings.
 *
 * The seed is darkened until white text on it reaches 4.5:1, so a light pick
 * (yellow, sky) still produces a readable Pay button; the container is a pale
 * tint of the seed and its text a deep shade of it.
 */
data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
) {
    companion object {
        fun from(seed: Color): AccentPalette {
            var primary = seed
            var guard = 0
            while (contrast(Color.White, primary) < 4.5f && guard < 20) {
                primary = lerp(primary, Color.Black, 0.08f)
                guard++
            }
            return AccentPalette(
                primary = primary,
                onPrimary = Color.White,
                container = lerp(seed, Color.White, 0.84f),
                onContainer = lerp(seed, Color.Black, 0.62f),
            )
        }

        private fun contrast(a: Color, b: Color): Float {
            val la = a.luminance() + 0.05f
            val lb = b.luminance() + 0.05f
            return if (la > lb) la / lb else lb / la
        }

        /** "#RRGGBB" → Color, or null for "default"/anything unparsable. */
        fun parse(value: String?): Color? {
            val hex = value?.removePrefix("#") ?: return null
            if (hex.length != 6) return null
            return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        }

        /** Swatches offered in Settings, "default" first. */
        val presets: List<String> = listOf(
            "default",
            "#C2410C", // burnt orange
            "#B91C1C", // red
            "#BE185D", // raspberry
            "#7C3AED", // violet
            "#1D4ED8", // blue
            "#0E7490", // teal
            "#15803D", // green
            "#A16207", // amber
            "#334155", // slate
        )
    }
}
