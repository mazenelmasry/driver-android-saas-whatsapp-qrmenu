package app.qrmenu.driver.trip

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * Screen 9 — the full-screen 45-second offer (CLAUDE.md § خريطة الشاشات /
 * محرك النداء).
 *
 * This is a phone ringing, not a notification (driver-ui-standards): full
 * screen, a live countdown, and buttons a thumb cannot miss in a moving car.
 * Sound and vibration are `:core:notifications`' job — see [OfferRoute]'s doc
 * for the exact state this composable exposes to stop them.
 */
@Composable
fun OfferRoute(
    orderId: Long,
    onAccepted: (DriverOrderDto) -> Unit,
    onResolved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(orderId) { viewModel.start(orderId) }

    // One-shot navigation events — consumed exactly once, the same pattern
    // `:feature:orders`' resumed-screen poll uses `LaunchedEffect` for.
    LaunchedEffect(state.acceptedOrder) { state.acceptedOrder?.let(onAccepted) }

    // 🔴 THE HOOK FOR :core:notifications — see this composable's own doc.
    // Fires exactly once, the instant the phase becomes terminal (declined,
    // expired locally or by the server, or lost to another driver). The
    // caller's `onResolved` is where the ringing alarm — sound, vibration,
    // the full-screen notification intent — must be torn down; this module
    // owns none of that (CLAUDE.md: `:core:notifications` does) and only
    // reports the ONE fact that alarm needs: "this offer is no longer live".
    LaunchedEffect(state.phase is OfferPhase.Resolved) {
        if (state.phase is OfferPhase.Resolved) onResolved()
    }

    OfferScreen(
        state = state,
        onAccept = { viewModel.accept(orderId) },
        onDecline = { reason -> viewModel.decline(orderId, reason) },
        onRetryLoad = { viewModel.retryLoad(orderId) },
        modifier = modifier,
    )
}

@Composable
internal fun OfferScreen(
    state: OfferUiState,
    onAccept: () -> Unit,
    onDecline: (String) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (val phase = state.phase) {
                OfferPhase.Loading -> OfferLoadingSkeleton()
                is OfferPhase.LoadFailed -> OfferLoadError(error = phase.error, onRetry = onRetryLoad)
                is OfferPhase.Resolved -> OfferOutcomeScreen(outcome = phase.outcome)
                is OfferPhase.Content -> OfferContent(
                    phase = phase,
                    onAccept = onAccept,
                    onDecline = onDecline,
                )
            }
        }
    }
}

// region Content — the decidable offer

@Composable
private fun OfferContent(phase: OfferPhase.Content, onAccept: () -> Unit, onDecline: (String) -> Unit) {
    var showDeclineReasons by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        OfferCountdownBadge(expiresAt = phase.order.expiresAt)

        Text(
            text = stringResource(R.string.trip_offer_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        OfferDetailsCard(order = phase.order)

        if (phase.error != null) {
            DriverErrorBanner(error = phase.error)
        }

        Spacer(modifier = Modifier.height(Spacing.xxs))

        OfferActionButtons(
            isActing = phase.isActing,
            onAccept = onAccept,
            onDecline = { showDeclineReasons = true },
        )
    }

    if (showDeclineReasons) {
        DeclineReasonOverlay(
            isActing = phase.isActing,
            onDismiss = { showDeclineReasons = false },
            onConfirm = { reason ->
                showDeclineReasons = false
                onDecline(reason)
            },
        )
    }
}

/**
 * The live "N s left" tick. Anchored to [OfferSummary.expiresAt] — the
 * server's absolute instant — and re-reads it every second via
 * [remainingSeconds], the same `produceState`/`delay` shape `:feature:orders`'
 * `ReadinessPill` uses for the same reason: this is server-driven time
 * passing, not user-caused motion, so it is exempt from the spring-only rule.
 *
 * Colour shifts to the error tone in the final ten seconds — a driver glancing
 * at a mounted phone should not have to read the number to know it is nearly
 * gone.
 */
@Composable
private fun OfferCountdownBadge(expiresAt: Instant) {
    val secondsLeft by produceState(initialValue = remainingSeconds(expiresAt, Instant.now()), expiresAt) {
        while (value > 0) {
            delay(1_000)
            value = remainingSeconds(expiresAt, Instant.now())
        }
    }

    val urgent = secondsLeft <= URGENT_SECONDS_THRESHOLD
    val container = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(Radius.pill), color = container) {
            Text(
                text = stringResource(R.string.trip_offer_seconds_left, secondsLeft.toString()).ltr(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
        }
    }
}

private const val URGENT_SECONDS_THRESHOLD = 10

/**
 * What this driver may know before accepting (decision 23) — branch, zone,
 * distance, money, item count. Structurally incapable of showing more: see
 * [OfferSummary]'s own doc for why that is a compile-time guarantee.
 */
@Composable
private fun OfferDetailsCard(order: OfferSummary) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
                Text(
                    text = listOfNotNull(order.companyName, order.branchName, order.zoneName).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                formatDistanceKm(order.distanceKm)?.let { distance ->
                    Text(
                        text = distance.ltr(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.trip_offer_fee_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMoney(order.driverFee, order.currency).ltr(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                MoneyChip(
                    icon = if (order.isPaidOnline) Icons.Filled.CreditCard else Icons.Filled.Payments,
                    text = if (order.isPaidOnline) {
                        stringResource(R.string.trip_offer_paid_online)
                    } else {
                        stringResource(R.string.trip_offer_cash_to_collect, formatMoney(order.cashToCollect, order.currency).ltr())
                    },
                )
            }

            // 🔴 What the driver actually has 45 seconds to decide: does
            // this load fit on the bike? A bare count could not answer it —
            // "6 family boxes" and one coffee both used to read "1 item".
            // Names first, the piece count as the fallback when the server
            // sent no detail. One line, never a list to read.
            MoneyChip(
                icon = Icons.Filled.ReceiptLong,
                text = if (order.itemPreview.isEmpty()) {
                    stringResource(R.string.trip_offer_items_count, order.itemCount)
                } else {
                    // Resolved through the Context, not `stringResource`: the
                    // lambda `joinToString` takes is not a composable context.
                    val resources = LocalContext.current.resources
                    val shown = order.itemPreview.joinToString(separator = " · ") { item ->
                        resources.getString(
                            R.string.trip_offer_item_line,
                            item.quantity.toString().ltr(),
                            item.name,
                        )
                    }
                    if (order.hiddenItemCount > 0) {
                        shown + "  " + resources.getString(
                            R.string.trip_offer_items_more,
                            order.hiddenItemCount.toString().ltr(),
                        )
                    } else {
                        shown
                    }
                },
            )
        }
    }
}

@Composable
private fun MoneyChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(Radius.pill), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ControlSize.inlineIcon),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Accept/decline — 64dp minimum, stacked rather than side by side so a thumb
 * cannot brush both in one motion. Decline is the smaller, outlined control on
 * top; accept is the full-bleed filled button a thumb naturally lands on last,
 * at the bottom of a one-handed reach.
 */
@Composable
private fun OfferActionButtons(isActing: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(
            onClick = onDecline,
            enabled = !isActing,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.primaryPhysical),
        ) {
            Text(
                text = stringResource(R.string.trip_offer_decline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Button(
            onClick = onAccept,
            enabled = !isActing,
            shape = RoundedCornerShape(Radius.card),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // 🔴 64dp of ACTUAL glass, corrected for UI scale — never the
                // bare `TouchTarget.primary` (driver-ui-standards table).
                .heightIn(min = TouchTarget.primaryPhysical),
        ) {
            if (isActing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ControlSize.buttonSpinner),
                    strokeWidth = ControlSize.buttonSpinnerStroke,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.trip_offer_accept),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// endregion

// region Decline reason

private val declineReasons = listOf(
    "too_far" to R.string.trip_decline_reason_too_far,
    "vehicle_problem" to R.string.trip_decline_reason_vehicle_problem,
    "restaurant_issue" to R.string.trip_decline_reason_restaurant_issue,
    "low_pay" to R.string.trip_decline_reason_low_pay,
    "other" to R.string.trip_decline_reason_other,
)

/**
 * A DECLINE requires a reason (contract: `decline`'s body is `required:
 * [reason]`) — this collects one before the network call, as its own overlay
 * rather than a second full-screen route, so a driver who opens it by mistake
 * is one tap away from the offer they were just looking at.
 */
@Composable
private fun DeclineReasonOverlay(isActing: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.trip_decline_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                declineReasons.forEach { (wire, labelRes) ->
                    OutlinedButton(
                        onClick = { onConfirm(wire) },
                        enabled = !isActing,
                        shape = RoundedCornerShape(Radius.card),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TouchTarget.primaryPhysical),
                    ) {
                        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isActing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.compact),
                ) {
                    Text(text = stringResource(R.string.trip_decline_sheet_cancel))
                }
            }
        }
    }
}

// endregion

// region Terminal outcomes — decision-owned end states, never a generic red banner

@Composable
private fun OfferOutcomeScreen(outcome: OfferOutcome) {
    val (title, body) = when (outcome) {
        OfferOutcome.AlreadyClaimed ->
            R.string.trip_outcome_already_claimed_title to R.string.trip_outcome_already_claimed_body
        OfferOutcome.Expired ->
            R.string.trip_outcome_expired_title to R.string.trip_outcome_expired_body
        OfferOutcome.OrderCancelled ->
            R.string.trip_outcome_order_cancelled_title to R.string.trip_outcome_order_cancelled_body
        OfferOutcome.TooManyActiveOrders ->
            R.string.trip_outcome_too_many_active_orders_title to R.string.trip_outcome_too_many_active_orders_body
        OfferOutcome.CashLimitExceeded ->
            R.string.trip_outcome_cash_limit_exceeded_title to R.string.trip_outcome_cash_limit_exceeded_body
        OfferOutcome.Declined ->
            R.string.trip_outcome_declined_title to R.string.trip_outcome_declined_body
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// endregion

// region Loading / error

/** Shimmer matching the eventual card shape — never a centred spinner. */
@Composable
private fun OfferLoadingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "offer_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offer_skeleton_shimmer_translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(600f * translate - 600f, 0f),
        end = Offset(600f * translate, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(Spacing.xxxl)
                .clipToCard(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.giant * 2)
                .clipToCard(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.primary)
                .clipToCard(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.primary)
                .clipToCard(brush),
        )
    }
}

private fun Modifier.clipToCard(brush: Brush): Modifier =
    this.then(Modifier.background(brush, RoundedCornerShape(Radius.card)))

@Composable
private fun OfferLoadError(error: DriverApiError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        DriverErrorBanner(error = error, onRetry = onRetry)
    }
}

// endregion

// region Previews

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class OfferStatePreviews

private val previewOrder = OfferSummary(
    id = 1,
    currency = "SAR",
    companyName = "مطعم البيت السعيد",
    branchName = "فرع العليا",
    zoneName = "حي العليا",
    distanceKm = 3.4,
    driverFee = 7.5,
    cashToCollect = 45.0,
    isPaidOnline = false,
    itemCount = 9,
    itemPreview = listOf(OfferItem("برجر لحم", 2), OfferItem("بطاطس كبير", 1)),
    hiddenItemCount = 3,
    expiresAt = Instant.now().plusSeconds(32),
)

@OfferStatePreviews
@Composable
private fun OfferLoadingPreview() {
    DriverTheme {
        OfferScreen(state = OfferUiState(phase = OfferPhase.Loading), onAccept = {}, onDecline = {}, onRetryLoad = {})
    }
}

@OfferStatePreviews
@Composable
private fun OfferContentPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.Content(order = previewOrder)),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferUrgentCountdownPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.Content(order = previewOrder.copy(expiresAt = Instant.now().plusSeconds(7)))),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferActingPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.Content(order = previewOrder, isActing = true)),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferInlineErrorPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(
                phase = OfferPhase.Content(order = previewOrder, error = DriverApiError.Offline),
            ),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferLoadFailedPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.LoadFailed(DriverApiError.Offline)),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferAlreadyClaimedPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.Resolved(OfferOutcome.AlreadyClaimed)),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

@OfferStatePreviews
@Composable
private fun OfferExpiredPreview() {
    DriverTheme {
        OfferScreen(
            state = OfferUiState(phase = OfferPhase.Resolved(OfferOutcome.Expired)),
            onAccept = {},
            onDecline = {},
            onRetryLoad = {},
        )
    }
}

// endregion
