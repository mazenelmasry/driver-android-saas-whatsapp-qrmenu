package app.qrmenu.driver.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.qrmenu.driver.designsystem.theme.Illustration

/**
 * The app's own empty-state artwork, painted rather than shipped.
 *
 * 🔴 Why drawn in code and not a drawable.
 *
 * A grey box with two sentences in it is what made the empty screens — which
 * is MOST of a waiting driver's shift — read as an unfinished app. The fix is
 * a picture, but a picture as PNG means five densities and a file per state,
 * and as imported clip-art it means someone else's line weight and someone
 * else's colour. These are a few dozen primitives on a square canvas, tinted
 * from the live [MaterialTheme] — so they are correct in dark mode, correct
 * on both flavours, and correct at 200% font scale, for no bytes at all.
 *
 * Every coordinate below is expressed as a FRACTION of the canvas, so the art
 * scales with [Illustration.canvas] instead of breaking when it changes.
 */
enum class DriverArt {
    /** Waiting to be offered work: an open road running to the horizon. */
    RoadAhead,

    /** No order in hand: a delivery bag, empty. */
    EmptyBag,

    /** Nothing in the ledger yet: a wallet, closed. */
    Wallet,

    /** No restaurant has this driver on its list: a shuttered storefront. */
    Storefront,

    /** Nothing has happened yet: a quiet bell. */
    QuietBell,
}

/**
 * Paints [art] at [Illustration.canvas], on a soft brand-tinted disc.
 *
 * The disc is what keeps five different drawings looking like one family, and
 * it is what gives an otherwise empty screen something with weight on it.
 */
@Composable
fun DriverArtwork(art: DriverArt, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val wash = scheme.primaryContainer
    val line = scheme.primary
    val soft = scheme.secondary

    val strokePx: Float
    val thinPx: Float
    with(androidx.compose.ui.platform.LocalDensity.current) {
        strokePx = Illustration.stroke.toPx()
        thinPx = Illustration.strokeThin.toPx()
    }

    Canvas(modifier = modifier.size(Illustration.canvas)) {
        // The disc sits slightly low and slightly leading, so the art appears
        // to rest on it rather than to be stamped in its exact middle.
        drawCircle(
            color = wash,
            radius = size.minDimension * DISC_RADIUS,
            center = Offset(size.width * DISC_CX, size.height * DISC_CY),
        )

        when (art) {
            DriverArt.RoadAhead -> drawRoadAhead(line, soft, strokePx, thinPx)
            DriverArt.EmptyBag -> drawEmptyBag(line, soft, strokePx, thinPx)
            DriverArt.Wallet -> drawWallet(line, soft, strokePx, thinPx)
            DriverArt.Storefront -> drawStorefront(line, soft, strokePx, thinPx)
            DriverArt.QuietBell -> drawQuietBell(line, soft, strokePx, thinPx)
        }
    }
}

/** A road narrowing towards a horizon, with a sun over it — "work is coming". */
private fun DrawScope.drawRoadAhead(line: Color, soft: Color, stroke: Float, thin: Float) {
    val w = size.width
    val h = size.height
    val farY = h * 0.42f
    val nearY = h * 0.84f

    // The sun, low and behind everything else.
    drawCircle(color = soft, radius = w * 0.10f, center = Offset(w * 0.70f, h * 0.30f))

    // 🔴 The road is a FILLED tapering surface, not two converging lines.
    //
    // Two lines meeting at a point read as a tent; two lines joined by a
    // horizontal bar read as a table. Both were tried on the device and both
    // failed. A filled quad that narrows as it recedes is the only one of the
    // three that a driver reads as a road without being told.
    drawPath(
        Path().apply {
            moveTo(w * 0.13f, nearY)
            lineTo(w * 0.87f, nearY)
            lineTo(w * 0.585f, farY)
            lineTo(w * 0.415f, farY)
            close()
        },
        color = line.copy(alpha = ROAD_FILL_ALPHA),
    )

    // The verges, drawn over the fill's own edges so they stay crisp.
    val cap = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(w * 0.13f, nearY)
            lineTo(w * 0.415f, farY)
        },
        color = line,
        style = cap,
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.87f, nearY)
            lineTo(w * 0.585f, farY)
        },
        color = line,
        style = cap,
    )

    // The centre line, dashed — the mark that names the shape.
    drawPath(
        Path().apply {
            moveTo(w * 0.50f, nearY)
            lineTo(w * 0.50f, farY + h * 0.04f)
        },
        color = line,
        style = Stroke(
            width = thin,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(h * 0.06f, h * 0.05f),
                0f,
            ),
        ),
    )
}

/** A paper delivery bag with its handle — the shape of an order not yet taken. */
private fun DrawScope.drawEmptyBag(line: Color, soft: Color, stroke: Float, thin: Float) {
    val w = size.width
    val h = size.height

    // Body.
    drawRoundRect(
        color = line,
        topLeft = Offset(w * 0.28f, h * 0.40f),
        size = Size(w * 0.44f, h * 0.40f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
        style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )

    // Handle: a half-circle rising out of the top edge.
    drawArc(
        color = line,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.39f, h * 0.27f),
        size = Size(w * 0.22f, h * 0.26f),
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )

    // The fold across the bag's mouth, and a label square — the marks that
    // stop it reading as a plain rectangle.
    drawLine(
        color = soft,
        start = Offset(w * 0.28f, h * 0.51f),
        end = Offset(w * 0.72f, h * 0.51f),
        strokeWidth = thin,
    )
    drawRoundRect(
        color = soft,
        topLeft = Offset(w * 0.43f, h * 0.60f),
        size = Size(w * 0.14f, h * 0.11f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        style = Stroke(width = thin),
    )
}

/** A closed wallet with its clasp — the ledger with nothing in it yet. */
private fun DrawScope.drawWallet(line: Color, soft: Color, stroke: Float, thin: Float) {
    val w = size.width
    val h = size.height

    drawRoundRect(
        color = line,
        topLeft = Offset(w * 0.22f, h * 0.36f),
        size = Size(w * 0.56f, h * 0.36f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
        style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )

    // The card peeking out of the top — what makes it a wallet and not a box.
    drawRoundRect(
        color = soft,
        topLeft = Offset(w * 0.33f, h * 0.27f),
        size = Size(w * 0.34f, h * 0.12f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        style = Stroke(width = thin),
    )

    // The clasp.
    drawCircle(color = soft, radius = w * 0.045f, center = Offset(w * 0.66f, h * 0.54f))
}

/** A storefront under its awning — "no restaurant has you on its list". */
private fun DrawScope.drawStorefront(line: Color, soft: Color, stroke: Float, thin: Float) {
    val w = size.width
    val h = size.height

    // Awning: three scallops along the top.
    val scallopWidth = w * 0.18f
    repeat(SCALLOPS) { index ->
        drawArc(
            color = soft,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.23f + scallopWidth * index, h * 0.30f),
            size = Size(scallopWidth, h * 0.14f),
            style = Stroke(width = thin),
        )
    }

    // Walls and floor.
    drawPath(
        Path().apply {
            moveTo(w * 0.25f, h * 0.37f)
            lineTo(w * 0.25f, h * 0.78f)
            lineTo(w * 0.75f, h * 0.78f)
            lineTo(w * 0.75f, h * 0.37f)
        },
        color = line,
        style = Stroke(
            width = stroke,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        ),
    )

    // The shuttered door.
    drawRoundRect(
        color = line,
        topLeft = Offset(w * 0.42f, h * 0.54f),
        size = Size(w * 0.16f, h * 0.24f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        style = Stroke(width = thin),
    )
}

/** A bell at rest, with the two quiet strokes beside it. */
private fun DrawScope.drawQuietBell(line: Color, soft: Color, stroke: Float, thin: Float) {
    val w = size.width
    val h = size.height

    // The dome.
    drawArc(
        color = line,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.31f, h * 0.31f),
        size = Size(w * 0.38f, h * 0.40f),
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    // The skirt and the rim.
    drawPath(
        Path().apply {
            moveTo(w * 0.31f, h * 0.51f)
            lineTo(w * 0.31f, h * 0.64f)
            moveTo(w * 0.69f, h * 0.51f)
            lineTo(w * 0.69f, h * 0.64f)
        },
        color = line,
        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    drawLine(
        color = line,
        start = Offset(w * 0.26f, h * 0.64f),
        end = Offset(w * 0.74f, h * 0.64f),
        strokeWidth = stroke,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    // The clapper.
    drawCircle(color = line, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.71f))

    // Two short arcs either side: the universal "it is not ringing, it is
    // just here" mark, drawn soft so it never competes with the bell.
    rotate(degrees = 0f) {
        drawArc(
            color = soft,
            startAngle = 120f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.36f),
            size = Size(w * 0.16f, h * 0.24f),
            style = Stroke(width = thin, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawArc(
            color = soft,
            startAngle = 300f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(w * 0.70f, h * 0.36f),
            size = Size(w * 0.16f, h * 0.24f),
            style = Stroke(width = thin, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

private const val DISC_RADIUS = 0.40f
private const val DISC_CX = 0.52f
private const val DISC_CY = 0.54f
private const val SCALLOPS = 3
private const val ROAD_FILL_ALPHA = 0.16f
