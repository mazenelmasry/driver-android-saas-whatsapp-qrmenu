package app.qrmenu.driver.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens. **No dp/sp literal may appear outside this file** (CLAUDE.md
 * § الممنوعات) — a reviewer, human or agent, rejects an inlined magic number.
 */

/** 8dp grid. Allowed paddings/margins live here exclusively. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
    val giant = 64.dp
}

/** Border radius scale. */
object Radius {
    val chip = 8.dp
    val card = 12.dp
    val sheet = 16.dp
    val fab = 28.dp
    val pill = 999.dp
}

/** Elevation scale. */
object Elevation {
    val flat = 0.dp
    val card = 1.dp
    val cardSelected = 3.dp
    val sheet = 8.dp
    val drag = 12.dp
}

/**
 * Touch target floors.
 *
 * Larger than the POS tablet's: this is used one-handed, sometimes gloved, often
 * while standing beside a running car. Trip actions ("استلمت" / "سلّمت") are
 * [primary] and span the screen width.
 */
object TouchTarget {
    val min = 56.dp

    /** Every trip action and every primary CTA. Never smaller. */
    val primary = 64.dp

    /** Low-frequency secondary controls only (a settings row, a chip). Never a
     *  trip action, never the offer accept/decline pair. */
    val compact = 48.dp
}

/**
 * Spring-based motion specs. `tween` is reserved for ambient looping
 * (skeleton shimmer, breathing dots) — never for user-driven motion.
 */
object Motion {
    val snappy = spring<Float>(
        stiffness = Spring.StiffnessHigh,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )
    val standard = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )
    val bouncy = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )
    val shimmerCycleMs = 1400
    val skeletonShimmer = tween<Float>(durationMillis = shimmerCycleMs)
}

/** Selection accent strip — the brand primary. */
val SelectionAccentBar: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary
