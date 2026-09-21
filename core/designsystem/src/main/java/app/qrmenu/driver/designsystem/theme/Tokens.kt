package app.qrmenu.driver.designsystem.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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

    /**
     * [primary], corrected for the driver's chosen UI scale so it stays 64dp
     * of ACTUAL GLASS whatever the setting says.
     *
     * 🔴 Use this — not [primary] — for every trip action. The size control
     * scales density, so a plain `64.dp` at the "small" setting would land at
     * roughly 56dp of real screen, quietly undoing the one number CLAUDE.md
     * freezes: a gloved thumb in a moving car cannot reliably hit less.
     */
    val primaryPhysical: Dp
        @Composable
        @ReadOnlyComposable
        get() = primary / LocalUiScaleFactor.current
}

/**
 * Stroke widths and the few fixed control sizes.
 *
 * They live here for the same reason spacing does: no dp literal may appear
 * outside this file, so "a slightly thicker border when selected" has one
 * definition instead of one per screen.
 */
object Stroke {
    /** An unselected outline, or a decorative ring. */
    val hairline = 1.dp

    /** A selected outline — thick enough to read at a glance in sunlight. */
    val selected = 2.dp

    /**
     * The colour strip down the leading edge of a card that is ABOUT the
     * driver right now (the trip in their hand). It is what lets a glance,
     * at arm's length in a moving car, separate "mine" from "on offer"
     * before any word is read.
     */
    val accentBar = 4.dp
}

/** Fixed sizes for small decorative controls (not touch targets — see [TouchTarget]). */
object ControlSize {
    /** The filled dot that marks a chosen row. */
    val selectionDot = 24.dp

    /**
     * The dialling-code button beside the phone field. Fixed width so the number
     * field does not resize as the code changes from +20 to +966 — a field that
     * jumps while a thumb is travelling towards it is a field that gets mistyped.
     */
    val dialCode = 96.dp

    /** The spinner shown INSIDE a primary button while its press is in flight. */
    val buttonSpinner = 24.dp
    val buttonSpinnerStroke = 2.dp

    /**
     * The platform logo on the sign-in screen — height only; the aspect ratio is
     * the logo's own. Kept small on purpose: this screen's job is the two fields
     * and the button, and a wordmark that dominates it pushes the work below the
     * fold on a short phone.
     */
    val platformLogo = 44.dp

    /**
     * One cell of a code field. Taller than the 56dp floor because a six-cell
     * row on a narrow phone makes each cell only about 48dp WIDE — the height
     * is what keeps the tap area honest.
     */
    val digitCell = 64.dp

    /** The dot drawn in place of a digit while a code is masked. */
    val maskDot = 12.dp

    /**
     * The round restaurant mark on an order card. Big enough to be the thing
     * the eye lands on first when scanning a list of otherwise similar cards,
     * small enough to leave the branch name the widest element on the row.
     */
    val orderAvatar = 44.dp

    /** A small leading glyph inside a chip or a secondary row. */
    val inlineIcon = 16.dp

    /** The status dot inside a readiness pill. */
    val statusDot = 8.dp
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
