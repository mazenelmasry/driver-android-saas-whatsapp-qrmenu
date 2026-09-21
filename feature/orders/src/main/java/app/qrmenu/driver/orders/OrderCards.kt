package app.qrmenu.driver.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Elevation
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.ui.text.ltr
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * The order card, in its two shapes.
 *
 * Designed for a glance from an arm's length, in a car, in sunlight — so the
 * layout is built around a reading ORDER rather than a list of facts:
 *
 *   1. WHERE am I going      — the mark and the branch line, widest on the card
 *   2. HOW FAR               — a pill on the opposite edge, read without moving
 *   3. WHAT DO I GET / OWE   — the fee as the one large number, cash beside it
 *   4. IS IT READY           — a coloured status pill, shape and colour first
 *
 * The three facts CLAUDE.md forbids putting behind a scroll (branch, area,
 * amount to collect) all sit in the first two blocks, so the card answers
 * "should I take this?" before the thumb moves.
 */

/**
 * The "المتاحة" card. 🔴 Renders ONLY what [OfferedOrderSummary] can hold —
 * branch, zone, distance, money, item count, readiness. Because that type has
 * no customer/phone/address field, there is nothing here that could show one;
 * see [OfferedOrderSummary]'s own doc for why that is a compile-time
 * guarantee and not a discipline this function has to uphold on its own.
 */
@Composable
fun OfferedOrderCard(summary: OfferedOrderSummary, modifier: Modifier = Modifier) {
    OrderCardShell(modifier = modifier, isMine = false) {
        OrderCardHeader(
            companyName = summary.companyName,
            branchName = summary.branchName,
            zoneName = summary.zoneName,
            distanceKm = summary.distanceKm,
        )

        OrderMetaRow(
            expectedReadyAt = summary.expectedReadyAt,
            readyAt = summary.readyAt,
            itemCount = summary.itemCount,
        )

        CardDivider()

        OrderMoneyBlock(
            driverFee = summary.driverFee,
            cashToCollect = summary.cashToCollect,
            currency = summary.currency,
            isPaidOnline = summary.isPaidOnline,
        )
    }
}

/**
 * The "طلباتى" card — the ASSIGNED shape, so the customer and address are
 * exactly the fields this driver has earned by holding the order (see
 * [AssignedOrderSummary]). The pickup action is rendered here but DISABLED
 * until the order reads ready.
 *
 * 🔴 [onOpenTrip] is week 5 landing the tap handler week 3 deliberately left
 * empty. It matters more than "the button now works": accepting an offer is
 * NOT the only way a driver arrives at a held order. The app gets killed in a
 * pocket, the phone reboots, the driver switches tabs — and this list is the
 * only place that still knows they are holding one. While this callback was
 * a no-op, such a driver stared at a full-width primary button that did
 * nothing and had no route to "picked up" or "delivered" at all.
 */
@Composable
fun AssignedOrderCard(
    summary: AssignedOrderSummary,
    onOpenTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = readinessState(summary.expectedReadyAt, summary.readyAt, Instant.now()) is Readiness.Ready
    val alreadyPickedUp = summary.pickedUpAt != null

    OrderCardShell(modifier = modifier, isMine = true) {
        OrderCardHeader(
            companyName = summary.companyName,
            branchName = summary.branchName,
            zoneName = summary.zoneName,
            distanceKm = summary.distanceKm,
        )

        OrderMetaRow(
            expectedReadyAt = summary.expectedReadyAt,
            readyAt = summary.readyAt,
            itemCount = summary.itemCount,
        )

        CardDivider()

        OrderMoneyBlock(
            driverFee = summary.driverFee,
            cashToCollect = summary.cashToCollect,
            currency = summary.currency,
            isPaidOnline = summary.isPaidOnline,
        )

        CardDivider()

        // Earned by holding the order — never rendered on an offer.
        DetailRow(icon = Icons.Filled.Person, text = summary.customerName)
        if (summary.addressText.isNotBlank()) {
            DetailRow(icon = Icons.Filled.Place, text = summary.addressText, maxLines = 2)
        }

        Button(
            onClick = onOpenTrip,
            enabled = ready && !alreadyPickedUp,
            shape = RoundedCornerShape(Radius.card),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // 64dp, never less: a thumb on a moving vehicle, possibly
                // gloved, cannot reliably land on anything smaller.
                .heightIn(min = TouchTarget.primary),
        ) {
            Text(
                text = stringResource(R.string.orders_action_picked_up),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The card surface itself.
 *
 * A real surface with a hairline border on the app's warm paper background,
 * not a flat grey block — on this palette a `surfaceVariant` fill reads as a
 * disabled area rather than a thing to act on. [isMine] adds the leading
 * accent strip that separates the trip in the driver's hand from an offer, by
 * colour and position, before a single word is read.
 */
@Composable
private fun OrderCardShell(
    modifier: Modifier = Modifier,
    isMine: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = Elevation.card,
    ) {
        Row(modifier = Modifier.height(androidx.compose.ui.unit.Dp.Unspecified)) {
            if (isMine) {
                Box(
                    modifier = Modifier
                        .width(Stroke.accentBar)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
        }
    }
}

/**
 * Who and where, and how far — the two things a driver decides on.
 *
 * The distance sits on the opposite edge from the name on purpose: the eye
 * lands on the mark, reads the name rightwards (or leftwards in RTL), and
 * finds the distance where it stopped, without a second pass.
 */
@Composable
private fun OrderCardHeader(
    companyName: String,
    branchName: String,
    zoneName: String?,
    distanceKm: Double?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RestaurantMark(companyName)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = companyName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
                Text(
                    text = listOfNotNull(branchName, zoneName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        formatDistanceKm(distanceKm)?.let { distance ->
            DistancePill(distance)
        }
    }
}

/** The restaurant's initial in a tinted disc — the anchor the eye lands on. */
@Composable
private fun RestaurantMark(companyName: String) {
    Box(
        modifier = Modifier
            .size(ControlSize.orderAvatar)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = companyName.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DistancePill(distance: String) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = distance.ltr(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

/**
 * Status and size on one line: what the kitchen is doing, and how big the
 * order is. Two facts that belong together and neither of which deserves a
 * row of its own beside dead space.
 */
@Composable
private fun OrderMetaRow(expectedReadyAt: String?, readyAt: String?, itemCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadinessPill(expectedReadyAt = expectedReadyAt, readyAt = readyAt)
        InfoChip(
            icon = Icons.Filled.ReceiptLong,
            text = stringResource(R.string.orders_card_items_count, itemCount),
        )
    }
}

/**
 * The money, in the order a driver cares about it: what they EARN as the one
 * large number on the card, and what they must COLLECT beside it — because
 * cash in the pocket is the fact that ruins a shift when it is missed.
 */
@Composable
private fun OrderMoneyBlock(
    driverFee: Double,
    cashToCollect: Double,
    currency: String,
    isPaidOnline: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(R.string.orders_card_fee_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(driverFee, currency).ltr(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }

        if (isPaidOnline) {
            InfoChip(
                icon = Icons.Filled.CreditCard,
                text = stringResource(R.string.orders_card_paid_online),
            )
        } else {
            // 🔴 Brand tint, not the error red it used to wear. Cash in the
            // pocket is the fact most worth noticing on this card, but it is
            // not a fault — painting every cash order in alarm colours
            // teaches a driver to read red as "normal", which is precisely
            // how a real warning later gets ignored.
            InfoChip(
                icon = Icons.Filled.Payments,
                text = stringResource(
                    R.string.orders_card_cash_to_collect,
                    formatMoney(cashToCollect, currency).ltr(),
                ),
                container = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(shape = RoundedCornerShape(Radius.pill), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ControlSize.inlineIcon),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ControlSize.inlineIcon),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Stroke.hairline)
}

/**
 * The live "جاهز خلال ~N د" countdown as a status pill — colour and shape
 * before words, because this is the field a driver checks while walking.
 *
 * Ticks via [produceState]/`delay`, never `tween()` (this is server-driven
 * time passing, not user-caused motion, so it is exempt from the spring-only
 * rule the same way `AvailabilityScreen`'s `ElapsedSince` is). Renders nothing
 * for [Readiness.Unknown] — no countdown is more honest than a wrong one.
 */
@Composable
private fun ReadinessPill(expectedReadyAt: String?, readyAt: String?) {
    val readiness by produceState<Readiness>(
        initialValue = readinessState(expectedReadyAt, readyAt, Instant.now()),
        expectedReadyAt,
        readyAt,
    ) {
        while (true) {
            value = readinessState(expectedReadyAt, readyAt, Instant.now())
            // Only a real `ready_at` ends the loop. An elapsed estimate keeps
            // ticking: the next poll may bring the kitchen's actual stamp.
            if (value is Readiness.Ready || value is Readiness.Unknown) break
            delay(1_000)
        }
    }

    when (val current = readiness) {
        is Readiness.Counting -> StatusPill(
            text = stringResource(R.string.orders_readiness_counting, current.minutesRemaining.ltr()),
            container = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )

        Readiness.Ready -> StatusPill(
            text = stringResource(R.string.orders_readiness_ready),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Readiness.AwaitingKitchen -> StatusPill(
            text = stringResource(R.string.orders_readiness_awaiting_kitchen),
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Readiness.Unknown -> Unit
    }
}

@Composable
private fun StatusPill(text: String, container: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(Radius.pill), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The dot carries the state for a glance that never reaches the
            // word — and for a driver who cannot read this locale's script.
            Box(
                modifier = Modifier
                    .size(ControlSize.statusDot)
                    .clip(CircleShape)
                    .background(contentColor),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

private fun Long.ltr(): String = this.toString().ltr()
