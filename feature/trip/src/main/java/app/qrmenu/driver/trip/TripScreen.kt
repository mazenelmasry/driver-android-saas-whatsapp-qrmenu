package app.qrmenu.driver.trip

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Elevation
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DriverIssueCode
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.dto.LedgerSummaryDto
import app.qrmenu.driver.network.dto.OrderCompanyDto
import app.qrmenu.driver.network.dto.OrderCustomerDto
import app.qrmenu.driver.network.dto.DeliveryAddressDto
import app.qrmenu.driver.network.dto.OrderItemDto
import app.qrmenu.driver.network.dto.OrderZoneDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.ui.components.DigitCellsField
import app.qrmenu.driver.ui.components.DriverArt
import app.qrmenu.driver.ui.components.DriverArtwork
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.components.DriverScreenScaffold
import app.qrmenu.driver.ui.orders.messageResource
import app.qrmenu.driver.ui.text.TripMoneyFormat
import app.qrmenu.driver.ui.text.ltr

/**
 * Screens 10–12 — the trip a driver holds between accepting an offer and
 * handing the order over.
 *
 * 🔴 [initialOrder] is the ASSIGNED [DriverOrderDto] `OfferRoute.onAccepted`
 * already handed the caller — when it is non-null [TripViewModel.start] skips
 * the GET entirely (see its own doc). It is null only when this route is
 * entered without that hand-off — the app having been killed mid-trip and
 * reopened — and the trip is loaded from the server instead, the
 * "resuming a trip in progress" shape driver-ui-standards asks for.
 */
@Composable
fun TripRoute(
    orderId: Long,
    initialOrder: DriverOrderDto? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(orderId) { viewModel.start(orderId, initialOrder) }

    // 🔴 Runs ONLY while this screen is actually resumed, the same separation
    // `OrdersRoute` keeps: a poll belongs to "the OS says this is on screen",
    // which is a lifecycle question, not something a state holder can answer.
    // Backgrounding the app stops it rather than burning a driving driver's
    // battery on a screen nobody is looking at.
    val tripLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(tripLifecycleOwner, orderId) {
        tripLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Asked once on resume as well as on the interval: returning to
            // the app after a few minutes away is exactly when the answer is
            // most likely to have changed.
            viewModel.refreshQuietly(orderId)
            pollTripForever { viewModel.refreshQuietly(orderId) }
        }
    }

    TripScreen(
        state = state,
        onOpenNotifications = onOpenNotifications,
        onRetryLoad = { viewModel.retryLoad(orderId) },
        onExitEndedTrip = onExit,
        onPickUp = { viewModel.pickUp(orderId) },
        onOpenDeliverySheet = viewModel::openDeliverySheet,
        onDismissDeliverySheet = viewModel::dismissDeliverySheet,
        onDeliveryAmountChange = viewModel::updateDeliveryAmount,
        onDeliveryNoteChange = viewModel::updateDeliveryNote,
        onDeliveryCodeChange = viewModel::updateDeliveryCode,
        onConfirmDelivery = { isCash, cashToCollect -> viewModel.confirmDelivery(orderId, isCash, cashToCollect) },
        onDoneAfterDelivery = {
            viewModel.consumeDeliveredResult()
            onExit()
        },
        onOpenIssueSheet = viewModel::openIssueSheet,
        onDismissIssueSheet = viewModel::dismissIssueSheet,
        onSelectIssueCode = viewModel::selectIssueCode,
        onIssueNoteChange = viewModel::updateIssueNote,
        onSubmitIssue = { viewModel.submitIssue(orderId) },
        onDismissIssueReported = viewModel::consumeIssueReported,
        modifier = modifier,
    )
}

@Composable
internal fun TripScreen(
    state: TripUiState,
    onOpenNotifications: (() -> Unit)?,
    onRetryLoad: () -> Unit,
    /** [TripPhase.Resolved]'s own way out — no delivery, no earnings, just back to the list. */
    onExitEndedTrip: () -> Unit,
    onPickUp: () -> Unit,
    onOpenDeliverySheet: () -> Unit,
    onDismissDeliverySheet: () -> Unit,
    onDeliveryAmountChange: (String) -> Unit,
    onDeliveryNoteChange: (String) -> Unit,
    onDeliveryCodeChange: (String) -> Unit,
    onConfirmDelivery: (isCashOrder: Boolean, cashToCollect: Double) -> Unit,
    onDoneAfterDelivery: () -> Unit,
    onOpenIssueSheet: () -> Unit,
    onDismissIssueSheet: () -> Unit,
    onSelectIssueCode: (DriverIssueCode) -> Unit,
    onIssueNoteChange: (String) -> Unit,
    onSubmitIssue: () -> Unit,
    onDismissIssueReported: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 🔴 `DriverScreenScaffold` wraps `DriverHeader`, which pays the
    // status-bar inset ITSELF (`windowInsetsPadding(WindowInsets.statusBars)`
    // — it draws its coloured band behind the clock on purpose). `Scaffold`'s
    // own default `contentWindowInsets` is `WindowInsets.safeDrawing`, which
    // ALSO reserves the status-bar's height at the top of `padding` — so this
    // screen paid for the status bar twice: once as a real gap above the
    // header, once again inside it. The result on a real device was a thin
    // light strip sitting above the coloured header. Bottom and horizontal
    // stay in the request — the trip's bottom action buttons and the delivery
    // sheet's own "رجوع" button still need the navigation-bar and side-notch
    // insets Scaffold was already supplying correctly.
    val contentInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Scaffold(modifier = modifier, contentWindowInsets = contentInsets) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            DriverScreenScaffold(
                title = stringResource(R.string.trip_title),
                onOpenNotifications = onOpenNotifications,
            ) {
                when (val phase = state.phase) {
                    TripPhase.Loading -> TripResumingSkeleton()
                    is TripPhase.LoadFailed -> TripLoadError(error = phase.error, onRetry = onRetryLoad)
                    is TripPhase.Content -> TripContent(
                        phase = phase,
                        onPickUp = onPickUp,
                        onOpenDeliverySheet = onOpenDeliverySheet,
                        onOpenIssueSheet = onOpenIssueSheet,
                    )
                    is TripPhase.Resolved -> TripEndedScreen(order = phase.order, outcome = phase.outcome, onExit = onExitEndedTrip)
                }
            }
        }

        val content = state.phase as? TripPhase.Content
        if (content != null && state.deliverySheet.visible) {
            DeliverySheet(
                order = content.order,
                sheet = state.deliverySheet,
                onDismiss = onDismissDeliverySheet,
                onAmountChange = onDeliveryAmountChange,
                onNoteChange = onDeliveryNoteChange,
                onCodeChange = onDeliveryCodeChange,
                onConfirm = { onConfirmDelivery(content.order.cashToCollect > 0.0, content.order.cashToCollect) },
            )
        }

        if (content != null && state.issueSheet.visible) {
            IssueSheet(
                sheet = state.issueSheet,
                onDismiss = onDismissIssueSheet,
                onSelectCode = onSelectIssueCode,
                onNoteChange = onIssueNoteChange,
                onSubmit = onSubmitIssue,
            )
        }

        if (state.issueSheet.visible.not() && state.issueReported) {
            IssueReportedOverlay(onDismiss = onDismissIssueReported)
        }

        state.deliveredResult?.let { result ->
            TripDeliveredOverlay(result = result, onDone = onDoneAfterDelivery)
        }
    }
}

// region Content — screen 10

@Composable
private fun TripContent(
    phase: TripPhase.Content,
    onPickUp: () -> Unit,
    onOpenDeliverySheet: () -> Unit,
    onOpenIssueSheet: () -> Unit,
) {
    val order = phase.order
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 🔴 The three facts CLAUDE.md forbids putting behind a scroll — branch
        // name, delivery area, amount to collect — sit ABOVE the scrollable
        // body, never inside it.
        TripTopFacts(order = order)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(modifier = Modifier.height(Spacing.xxs))

            QuickActionsRow(order = order, context = context)

            displayableRecipientName(order.customer?.name)?.let { name ->
                TripRecipientCard(name = name)
            }

            val address = order.deliveryAddress
            // 🔴 [TripTopFacts]'s "delivery area" line already shows this SAME
            // full address text whenever there is no delivery zone name to
            // show instead (its own `area` falls back to
            // `deliveryAddress.text`) — verified duplicated on-device. This
            // card exists for the detail the top card structurally cannot
            // carry: delivery notes, and the full untruncated text on the
            // (common) case a zone name freed up the top line for something
            // shorter. It has nothing new to say — and must not render an
            // empty shell — when a zone was NOT shown up top (so this would
            // repeat the exact text already on screen) AND there are no notes.
            //
            // This is still only a COARSE pre-filter: a blank `zone.name`
            // still counts as "no zone" for this check (`?.name` alone would
            // let an empty string through), and even when it passes,
            // [TripAddressCard] itself runs the precise
            // [addressCardHasContent] check below before drawing anything —
            // a zone shown up top does not guarantee `address.text` itself is
            // non-blank once map links are stripped from it.
            val showZoneNameUpTop = blankToNull(order.zone?.name) != null
            if (address != null && (showZoneNameUpTop || address.notes != null)) {
                TripAddressCard(
                    address = address,
                    showAddressText = showZoneNameUpTop,
                    isApproximateLocation = address.isApproximatePin(),
                    showMapLink = address.shouldOfferMapLink(),
                )
            }

            if (order.items.isNotEmpty()) {
                TripItemsCard(order = order)
            }

            TripNoteCard(note = order.notes)

            if (phase.error != null) {
                DriverErrorBanner(error = phase.error)
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))
        }

        TripBottomActions(
            order = order,
            isPickingUp = phase.isPickingUp,
            onPickUp = onPickUp,
            onOpenDeliverySheet = onOpenDeliverySheet,
            onOpenIssueSheet = onOpenIssueSheet,
        )
    }
}

/**
 * 🔴 The full-screen outcome (option ب, the project owner's own decision):
 * "the trip simply vanished in silence" is the bug this replaces. A driver
 * who was mid-trip when the restaurant cancelled or rejected the order lands
 * HERE instead of on a trip screen whose buttons either fail outright
 * (pick-up/deliver on a dead order) or, worse, would let them complete a sale
 * that no longer exists — see [TripPhase.Resolved]'s own doc for why the
 * order can still be sitting in this driver's hands on the wire at all.
 *
 * Same full-screen-outcome shape [OfferOutcomeScreen] already established for
 * this module (artwork + title + body) — deliberately not a second pattern.
 * What differs from that screen: this one needs the branch's phone number
 * (the owner's own words: the restaurant has to tell the driver to bring the
 * order back, and calling them is the fastest way to actually have that
 * conversation), so it reuses [QuickActionButton] + [dialIntent] directly —
 * the exact mechanism [QuickActionsRow] already calls this screen's own
 * "call branch" button through, not a rebuilt one — and an explicit button
 * back to the orders list, since there is no earnings screen or delivery
 * sheet after this to carry the driver forward on its own.
 */
@Composable
private fun TripEndedScreen(order: DriverOrderDto, outcome: TripOutcome, onExit: () -> Unit) {
    val context = LocalContext.current
    val title = when (outcome) {
        TripOutcome.Cancelled -> R.string.trip_ended_cancelled_title
        TripOutcome.Rejected -> R.string.trip_ended_rejected_title
    }
    // 🔴 The ONE thing that actually differs for the driver, per the task
    // brief: "take the order back" is the wrong sentence for someone who
    // never collected it in the first place. [DriverOrderDto.pickedUpAt] is
    // the one signal this DTO carries for that — non-null the instant
    // `pickedUp` succeeded (TripViewModel.pickUp) — so it, not the outcome
    // itself, decides which of the two bodies below is shown.
    val body = if (order.pickedUpAt != null) {
        R.string.trip_ended_body_return_to_branch
    } else {
        R.string.trip_ended_body_nothing_to_return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DriverArtwork(art = DriverArt.EmptyBag)
        Spacer(modifier = Modifier.height(Spacing.md))
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
        Spacer(modifier = Modifier.height(Spacing.lg))

        // 🔴 NOT the accept/decline/pick-up/deliver row this screen normally
        // shows — this order cannot be acted on any more. Only the two
        // things that actually help: reach the restaurant, and leave.
        order.branch.phone?.let { phone ->
            QuickActionButton(
                icon = Icons.Filled.Storefront,
                label = stringResource(R.string.trip_action_call_branch),
                contentDescription = stringResource(R.string.a11y_call_branch),
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical),
                onClick = { safeStartActivity(context, dialIntent(phone)) },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }

        Button(
            onClick = onExit,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.primaryPhysical),
        ) {
            Text(
                text = stringResource(R.string.trip_ended_back_to_orders),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TripTopFacts(order: DriverOrderDto) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        // 🔴 The one card that must never read as equal weight to the ones
        // below it: it carries the three facts CLAUDE.md forbids hiding
        // behind a scroll. Real elevation, not just a hairline, is what makes
        // "look here first" legible at a glance rather than something a
        // driver has to read to discover.
        shadowElevation = Elevation.cardSelected,
        border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // 🔴 See [displayableBranchName]'s own doc for why a blank name
            // hides this whole row instead of falling back to a placeholder.
            displayableBranchName(order.branch.name)?.let { branchName ->
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
                        text = branchName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val area = displayableArea(order.zone?.name, order.deliveryAddress?.text)
            if (!area.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ControlSize.inlineIcon),
                    )
                    Text(
                        text = area,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
                Text(
                    text = if (order.cashToCollect > 0.0) {
                        stringResource(
                            R.string.trip_offer_cash_to_collect,
                            TripMoneyFormat.format(order.cashToCollect, order.currency),
                        ).ltr()
                    } else {
                        stringResource(R.string.trip_offer_paid_online)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(order: DriverOrderDto, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        order.customer?.phone?.let { phone ->
            QuickActionButton(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.trip_action_call_customer),
                contentDescription = stringResource(R.string.a11y_call_customer),
                modifier = Modifier.weight(1f),
                onClick = { safeStartActivity(context, dialIntent(phone)) },
            )
            QuickActionButton(
                icon = Icons.Filled.Chat,
                label = stringResource(R.string.trip_action_whatsapp),
                contentDescription = stringResource(R.string.a11y_whatsapp_customer),
                modifier = Modifier.weight(1f),
                onClick = { safeStartActivity(context, whatsAppIntent(phone)) },
            )
        }
        order.branch.phone?.let { phone ->
            QuickActionButton(
                icon = Icons.Filled.Storefront,
                label = stringResource(R.string.trip_action_call_branch),
                contentDescription = stringResource(R.string.a11y_call_branch),
                modifier = Modifier.weight(1f),
                onClick = { safeStartActivity(context, dialIntent(phone)) },
            )
        }
        // 🔴 Always rendered when there is ANY address information at all —
        // see [NavigationTarget]'s own doc. `lat`/`lng` give turn-by-turn;
        // bare text still opens a Maps search instead of leaving the driver
        // with no way to get moving.
        val navigationTarget = navigationTargetFor(order.deliveryAddress)
        if (navigationTarget != NavigationTarget.None) {
            QuickActionButton(
                icon = Icons.Filled.Navigation,
                label = stringResource(R.string.trip_action_navigate),
                contentDescription = stringResource(R.string.a11y_navigate_to_customer),
                modifier = Modifier.weight(1f),
                onClick = { navigationIntent(context, navigationTarget)?.let { safeStartActivity(context, it) } },
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.card),
        // The row's own padding, not the button's: four weighted buttons at
        // the default inset leave almost no text width, which is what cut
        // "واتساب" short and left the two call buttons reading the
        // same word.
        contentPadding = PaddingValues(horizontal = Spacing.xxs, vertical = Spacing.xs),
        modifier = modifier.heightIn(min = TouchTarget.primaryPhysical),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(ControlSize.inlineIcon))
            // 🔴 Wraps instead of truncating. Adding the navigation button
            // took this row from three buttons to four, and at four
            // `maxLines = 1` clipped "اتصال بالعميل" and "اتصال بالفرع"
            // down to the same word — two different phone numbers behind one
            // label, at a door, in a hurry. This project does not truncate
            // names for exactly this reason.
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The person the driver is about to hand the order to — shown once, above the
 * address, so the driver knows the name before knocking (it was missing from
 * this screen entirely; the call/WhatsApp buttons above have the phone number
 * but never printed the name that goes with it).
 */
@Composable
private fun TripRecipientCard(name: String) {
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface, border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ControlSize.inlineIcon),
            )
            Column {
                Text(
                    text = stringResource(R.string.trip_recipient_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * [showAddressText] is `false` exactly when [TripTopFacts] is already showing
 * this same [DeliveryAddressDto.text] as its "delivery area" line (no zone
 * name to show instead) — see the call site's own doc. The call site's guard
 * is only a coarse pre-filter, though (it does not know yet whether the
 * address text survives [stripMapLinks], or whether it is blank) — this
 * Composable runs the precise [addressCardHasContent] check itself, below,
 * and renders nothing at all when it comes back `false`. That is the fix for
 * the "bare card" bug: a zone name shown up top (so `showAddressText` is
 * `false`) with a blank/null `address.text`, no notes, an exact (not
 * approximate) pin, and no map link to offer left this card as nothing more
 * than its own "العنوان" label — an icon-shaped promise with nothing behind
 * it, the same failure mode as every other row fixed in this file.
 *
 * Both [DeliveryAddressDto.text] and [DeliveryAddressDto.notes] go through
 * [stripMapLinks] before being rendered — defence in depth against a raw URL
 * a customer pasted into their address ending up on a driver's screen (see
 * that helper's own doc); the backend's own fix does not cover orders already
 * in the database.
 *
 * 🔴 [isApproximateLocation] labels a pin the customer never actually
 * placed: `location_source == "approx"` is a SILENT GPS reading taken when
 * they picked a delivery zone — their PHONE at order time, which may be their
 * office while they order delivery to home. Unlabelled, a driver follows it to
 * the wrong building believing it confirmed, which is the whole reason the
 * contract carries the source rather than just the coordinates.
 */
@Composable
private fun TripAddressCard(
    address: DeliveryAddressDto,
    showAddressText: Boolean,
    isApproximateLocation: Boolean = false,
    showMapLink: Boolean = false,
) {
    val hasContent = addressCardHasContent(
        addressText = if (showAddressText) address.text else null,
        notes = address.notes,
        isApproximateLocation = isApproximateLocation,
        showMapLink = showMapLink,
    )
    if (!hasContent) return

    val context = LocalContext.current
    val a11yOpenCustomerLocation = stringResource(R.string.a11y_open_customer_location)
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface, border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(R.string.trip_address_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showAddressText) {
                stripMapLinks(address.text)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (isApproximateLocation) {
                // The icon carries the signal; the colour stays calm on
                // purpose. A red caption here would be dishonest — an
                // approximate pin is a caveat, not a failure — and a driver
                // who sees red on an ordinary delivery learns to skip red.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ControlSize.inlineIcon),
                    )
                    Text(
                        text = stringResource(R.string.trip_address_approximate_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            stripMapLinks(address.notes)?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 🔴 Offered only when it BEATS the navigate button beside it
            // — see [shouldOfferMapLink]. It lives here rather than in the
            // quick-actions row because a fifth weighted button would squeeze
            // all five below a thumb's width on a phone, and because this is
            // the card the driver is already reading the address on.
            //
            // The URL is opened, never shown: `text` is deliberately scrubbed
            // of it just above.
            if (showMapLink) {
                address.openableMapLink()?.let { link ->
                    OutlinedButton(
                        onClick = { safeStartActivity(context, mapLinkIntent(link)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TouchTarget.primaryPhysical)
                            .semantics { contentDescription = a11yOpenCustomerLocation },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(ControlSize.inlineIcon),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(text = stringResource(R.string.trip_action_open_customer_location))
                    }
                }
            }
        }
    }
}

@Composable
private fun TripItemsCard(order: DriverOrderDto) {
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surface, border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.trip_section_items_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            order.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(text = "x${item.quantity}".ltr(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TripNoteCard(note: String?) {
    Surface(shape = RoundedCornerShape(Radius.card), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(R.string.trip_section_note_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = note ?: stringResource(R.string.trip_no_customer_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The trip's one committed action at a time: "استلمت" before pickup,
 * "سلّمت" + "مشكلة" after. `onPickUp` is disabled — and SAYS why via the
 * hint line beneath it — until [DriverOrderDto.isReadyForPickup] (CLAUDE.md
 * § دورة الرحلة: معطّل حتى Ready).
 */
@Composable
private fun TripBottomActions(
    order: DriverOrderDto,
    isPickingUp: Boolean,
    onPickUp: () -> Unit,
    onOpenDeliverySheet: () -> Unit,
    onOpenIssueSheet: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = Spacing.xxs) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (!order.isAssignedPickedUp()) {
                val ready = order.isReadyForPickup()
                Button(
                    onClick = onPickUp,
                    enabled = ready && !isPickingUp,
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical),
                ) {
                    if (isPickingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ControlSize.buttonSpinner),
                            strokeWidth = ControlSize.buttonSpinnerStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(text = stringResource(R.string.trip_action_pickup), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (!ready) {
                    Text(
                        text = stringResource(R.string.trip_pickup_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Button(
                    onClick = onOpenDeliverySheet,
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical),
                ) {
                    Text(text = stringResource(R.string.trip_action_deliver), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenIssueSheet,
                    shape = RoundedCornerShape(Radius.card),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.compact),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(ControlSize.inlineIcon))
                        Text(text = stringResource(R.string.trip_action_report_issue), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** `picked_up_at` set — the pickup step is behind us for this trip. */
private fun DriverOrderDto.isAssignedPickedUp(): Boolean = pickedUpAt != null

// endregion

// region Screen 11 — delivery sheet

/**
 * True only when the server rejected the DELIVERY CODE itself, as opposed to
 * the amount/note validation or the request failing on the way there — mirrors
 * `OtpScreen`'s own `isAboutTheCode()` for the identical reason: only a
 * rejection of what the driver typed should paint that field red.
 */
private fun DriverApiError?.isAboutTheDeliveryCode(): Boolean =
    this is DriverApiError.Api && (
        code == DriverErrorCode.DeliveryCodeRequired || code == DriverErrorCode.DeliveryCodeMismatch
        )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeliverySheet(
    order: DriverOrderDto,
    sheet: DeliverySheetState,
    onDismiss: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val isCashOrder = order.cashToCollect > 0.0
    val amount = sheet.amountText.toDoubleOrNull()
    val blockReason = deliveryBlockReason(
        isCashOrder = isCashOrder,
        cashToCollect = order.cashToCollect,
        enteredAmount = amount,
        note = sheet.note,
        codeRequired = sheet.codeRequired,
        enteredCode = sheet.deliveryCode,
    )
    val haptics = LocalHapticFeedback.current

    Box(
        // 🔴 The inset padding belongs on THIS box, not on the column inside
        // the sheet. `enableEdgeToEdge()` means the window never resizes for
        // the keyboard, so a full-screen box still spans the area the
        // keyboard now covers — and a sheet anchored to ITS bottom sits
        // underneath. Shrinking the box by the IME inset is what lifts the
        // sheet to rest on top of the keyboard. Padding the inner column only
        // moved content around inside a sheet that was still in the wrong
        // place, which is exactly what the first attempt at this did.
        //
        // 🔴 `WindowInsets.navigationBars.union(WindowInsets.ime)` — NOT
        // `.imePadding()` alone, and NOT the two chained (which would double
        // the gap: the IME inset already reaches past the navigation bar once
        // the keyboard is up, so padding for both would add the nav bar's
        // height a second time). `union` takes the LARGER of the two on each
        // side, so this box pads by the nav bar's own height when no keyboard
        // is showing, and by the (taller) keyboard's height once one is — and
        // either way, the confirm/"رجوع" pair at the bottom of the sheet ends
        // up above whichever obstruction is actually on screen, instead of
        // "رجوع" sitting clipped under the gesture bar the moment the code
        // field closes the keyboard again.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 🔴 `enableEdgeToEdge()` in MainActivity means the manifest's
            // `adjustResize` never applies — the window no longer fits system
            // windows, so the keyboard is OUR inset to handle. Without the
            // IME inset above, the numeric keypad covered the code cells, the
            // hint telling the driver to ask for them, and the confirm button:
            // a driver at a door typing blind into something they cannot see,
            // unable to reach the one button that finishes the delivery.
            // Scrolling as well, because a short phone in landscape still runs
            // out of room once the sheet is pushed up.
            Column(
                modifier = Modifier
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.trip_delivery_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (isCashOrder) {
                    // 🔴 The unmissable "Collect: X" number (driver-ui-standards) —
                    // Latin digits, direction-isolated, via TripMoneyFormat.
                    Text(
                        text = "${stringResource(R.string.trip_delivery_collect_label)} ${TripMoneyFormat.format(order.cashToCollect, order.currency)}".ltr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = sheet.amountText,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(R.string.trip_delivery_amount_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (blockReason == DeliveryBlockReason.ReasonRequiredForChangedAmount) {
                        OutlinedTextField(
                            value = sheet.note,
                            onValueChange = onNoteChange,
                            label = { Text(stringResource(R.string.trip_delivery_note_label)) },
                            placeholder = { Text(stringResource(R.string.trip_delivery_note_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val warning = when (blockReason) {
                        DeliveryBlockReason.AmountRequired -> stringResource(R.string.trip_delivery_amount_required)
                        DeliveryBlockReason.ReasonRequiredForChangedAmount -> stringResource(R.string.trip_delivery_reason_required)
                        // Shown next to the code field itself, not here.
                        DeliveryBlockReason.CodeRequired, null -> null
                    }
                    warning?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.trip_delivery_already_paid),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 🔴 Absent for the FIRST attempt of every delivery, cash or
                // not — the app never knows in advance whether an order
                // carries a code (decision 48). It appears only once a
                // `delivered` call with no code has already come back 422
                // `delivery_code_required`; see [DeliverySheetState.codeRequired].
                if (sheet.codeRequired) {
                    Text(
                        text = stringResource(R.string.trip_delivery_code_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DigitCellsField(
                        value = sheet.deliveryCode,
                        onValueChange = onCodeChange,
                        length = DELIVERY_CODE_LENGTH,
                        enabled = !sheet.isSubmitting,
                        // Never masked — same reasoning as the SMS code in
                        // `OtpScreen`: the customer just read it out loud or
                        // off their own screen, there is nothing to protect by
                        // hiding what the driver just typed.
                        isMasked = false,
                        isError = sheet.error.isAboutTheDeliveryCode(),
                    )
                    Text(
                        text = stringResource(R.string.trip_delivery_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (blockReason == DeliveryBlockReason.CodeRequired) {
                        Text(
                            text = stringResource(R.string.trip_delivery_code_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                // 🔴 The server's own five-attempt budget for THIS order, not a
                // client-side guess — from here on every `delivered` call
                // answers `too_many_attempts` no matter what is typed, so the
                // generic banner ("حاول لاحقاً") below is replaced, not
                // supplemented, by a message that actually tells the driver
                // what to do: this order's code is locked, and only the
                // restaurant can lift it. See [DeliverySheetState.deliveryLocked].
                if (sheet.deliveryLocked) {
                    Text(
                        text = stringResource(R.string.trip_delivery_code_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    order.branch.phone?.let { phone ->
                        val context = LocalContext.current
                        OutlinedButton(
                            onClick = { safeStartActivity(context, dialIntent(phone)) },
                            shape = RoundedCornerShape(Radius.card),
                            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.compact),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(ControlSize.inlineIcon))
                                Text(text = stringResource(R.string.trip_action_call_branch), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                } else if (sheet.error != null) {
                    DriverErrorBanner(error = sheet.error)
                }

                // A plain tap, by the project owner's decision (2026-09-21):
                // nothing on this screen should be harder than it has to be for
                // someone working one-handed at a door.
                //
                // The long press it replaced existed to stop an accidental tap
                // delivering an order — and the delivery code now does that job
                // properly. A driver cannot complete a coded delivery without
                // four digits only the customer has, so a stray touch reaches a
                // server that refuses it. The gesture was guarding a door the
                // code already locks.
                Button(
                    onClick = onConfirm,
                    enabled = blockReason == null && !sheet.isSubmitting && !sheet.deliveryLocked,
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.primaryPhysical),
                ) {
                    if (sheet.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ControlSize.buttonSpinner),
                            strokeWidth = ControlSize.buttonSpinnerStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.trip_delivery_confirm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                TextButton(onClick = onDismiss, enabled = !sheet.isSubmitting, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.compact)) {
                    Text(text = stringResource(R.string.trip_delivery_cancel))
                }
            }
        }
    }
}

// endregion

// region Screen 12 — issue sheet

private val issueCodes = DriverIssueCode.entries

@Composable
private fun IssueSheet(
    sheet: IssueSheetState,
    onDismiss: () -> Unit,
    onSelectCode: (DriverIssueCode) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Box(
        // 🔴 Same reasoning — and the same `navigationBars.union(ime)` fix —
        // as the delivery sheet above: this sheet's own "إلغاء" sits at the
        // bottom too, and plain `imePadding()` left it clipped under the
        // gesture bar the moment the note field's keyboard closed.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                // Same reason as the delivery sheet above: the note field
                // summons a keyboard this sheet would otherwise sit under.
                modifier = Modifier
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(text = stringResource(R.string.trip_issue_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.trip_issue_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                issueCodes.forEach { code ->
                    val selected = sheet.selectedCode == code
                    OutlinedButton(
                        onClick = { onSelectCode(code) },
                        enabled = !sheet.isSubmitting,
                        shape = RoundedCornerShape(Radius.card),
                        colors = if (selected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical),
                    ) {
                        Text(text = stringResource(code.messageResource()), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                OutlinedTextField(
                    value = sheet.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(R.string.trip_issue_note_label)) },
                    placeholder = { Text(stringResource(R.string.trip_issue_note_placeholder)) },
                    enabled = !sheet.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (sheet.error != null) {
                    DriverErrorBanner(error = sheet.error)
                }

                Button(
                    onClick = onSubmit,
                    enabled = sheet.selectedCode != null && !sheet.isSubmitting,
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical),
                ) {
                    if (sheet.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ControlSize.buttonSpinner),
                            strokeWidth = ControlSize.buttonSpinnerStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(ControlSize.inlineIcon))
                            Text(text = stringResource(R.string.trip_issue_submit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                TextButton(onClick = onDismiss, enabled = !sheet.isSubmitting, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.compact)) {
                    Text(text = stringResource(R.string.trip_issue_cancel))
                }
            }
        }
    }
}

@Composable
private fun IssueReportedOverlay(onDismiss: () -> Unit) {
    Box(
        // 🔴 `imePadding()` belongs on THIS box, not on the column inside the
        // sheet. `enableEdgeToEdge()` means the window never resizes for the
        // keyboard, so a full-screen box still spans the area the keyboard
        // now covers — and a sheet anchored to ITS bottom sits underneath.
        // Shrinking the box by the IME inset is what lifts the sheet to rest
        // on top of the keyboard. Padding the inner column only moved content
        // around inside a sheet that was still in the wrong place, which is
        // exactly what the first attempt at this did.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.trip_issue_reported_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.trip_issue_reported_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = onDismiss, shape = RoundedCornerShape(Radius.card), modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical)) {
                    Text(text = stringResource(R.string.trip_outcome_close))
                }
            }
        }
    }
}

// endregion

// region Delivered success

/**
 * What the driver just earned (contract: `delivered` returns `{order, ledger}`)
 * — driver-ui-standards' "show the driver what they just earned". Not a
 * transient toast: this is real money information and stays on screen for a
 * deliberate "Done" tap.
 */
@Composable
private fun TripDeliveredOverlay(result: DeliveredResponse, onDone: () -> Unit) {
    val ledger = result.ledger
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 🔴 The payoff for the whole trip used to be plain text — the
            // one moment on this screen that should feel like something
            // happened now has a picture, matching the quality bar every
            // empty state on this app already meets.
            DriverArtwork(art = DriverArt.Wallet)

            Text(
                text = stringResource(R.string.trip_delivered_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Surface(
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = Elevation.cardSelected,
                border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LedgerRow(label = stringResource(R.string.trip_delivered_fee_label), amount = ledger.earnedToday, currency = ledger.currency, emphasized = true)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LedgerRow(label = stringResource(R.string.trip_delivered_net_label), amount = ledger.net, currency = ledger.currency, emphasized = false)
                }
            }

            Button(onClick = onDone, shape = RoundedCornerShape(Radius.card), modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget.primaryPhysical)) {
                Text(text = stringResource(R.string.trip_delivered_done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LedgerRow(label: String, amount: Double, currency: String, emphasized: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = TripMoneyFormat.format(amount, currency),
            style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// endregion

// region Loading / error

/**
 * Distinct in doc from [OfferLoadingSkeleton]'s (`:feature:trip`'s own week-4
 * screen): the caption names what is actually happening — a trip picked up
 * before, not a first-open — per driver-ui-standards' "resuming a trip in
 * progress" shape.
 */
@Composable
private fun TripResumingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "trip_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1_400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "trip_skeleton_shimmer_translate",
    )
    val brush = Brush.linearGradient(colors = listOf(base, highlight, base), start = Offset(600f * translate - 600f, 0f), end = Offset(600f * translate, 0f))

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = stringResource(R.string.trip_resuming_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth().height(Spacing.giant * 2).background(brush, RoundedCornerShape(Radius.card)))
        Box(modifier = Modifier.fillMaxWidth().height(Spacing.giant).background(brush, RoundedCornerShape(Radius.card)))
        Box(modifier = Modifier.fillMaxWidth().height(TouchTarget.primary).background(brush, RoundedCornerShape(Radius.card)))
    }
}

@Composable
private fun TripLoadError(error: DriverApiError, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.Center) {
        DriverErrorBanner(error = error, onRetry = onRetry)
    }
}

// endregion

// region External intents — call, WhatsApp, navigation with a Waze fallback

private fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))

/** `wa.me` accepts digits only — the leading `+` and any spaces are stripped. */
private fun whatsAppIntent(phone: String): Intent {
    val digits = phone.filter { it.isDigit() }
    return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
}

/**
 * External navigation ONLY — no in-app map (driver-ui-standards). Returns
 * `null` only for [NavigationTarget.None] — callers gate the button itself on
 * that case, so in practice this always returns an [Intent] when called.
 *
 * 🔴 "Open the customer's own dropped pin" (a map link the customer pasted)
 * would be the most accurate option of all — more accurate than any text
 * search — but is NOT implemented: it needs a map-link field on
 * [app.qrmenu.driver.network.dto.DeliveryAddressDto] that does not exist
 * today, and adding one is out of scope for `:feature:trip` (touches
 * `core/network` and the OpenAPI contract, both forbidden here). TODO: once
 * such a field ships, add a `NavigationTarget.MapLink(url)` case here that
 * opens it directly via `ACTION_VIEW` — never display the raw URL, only open
 * it (see [stripMapLinks]'s own doc on why a raw URL must never reach the
 * screen).
 */
private fun navigationIntent(context: Context, target: NavigationTarget): Intent? = when (target) {
    is NavigationTarget.Coordinates -> navigationIntent(context, target.lat, target.lng)
    is NavigationTarget.TextSearch -> navigationSearchIntent(context, target.query)
    NavigationTarget.None -> null
}

/** Google Maps turn-by-turn first; a driver without it installed falls back to Waze, which speaks the same `lat,lng` language. */
private fun navigationIntent(context: Context, lat: Double, lng: Double): Intent {
    val google = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")).apply {
        setPackage("com.google.android.apps.maps")
    }
    return if (google.resolveActivity(context.packageManager) != null) {
        google
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes"))
    }
}

/**
 * No coordinates to hand a turn-by-turn intent, only address text — a Maps
 * text search still gets the driver most of the way there instead of leaving
 * the "Navigate" button absent entirely. `Uri.encode` — not raw string
 * concatenation — so Arabic text and spaces in the address survive as a valid
 * URL (verified against an Arabic address in [NavigationTargetTest]).
 *
 * Uses the `https://www.google.com/maps/search/?api=1&query=` form (Google's
 * documented Maps URL scheme) rather than `geo:0,0?q=`: the https form is
 * guaranteed to open Google Maps directly (via `setPackage` below) when it is
 * installed, while `geo:` is a generic Android URI any app declaring itself a
 * geo handler can claim, which is a worse guarantee for a driver who needs
 * Maps or Waze specifically, not whatever else registered for `geo:`.
 */
private fun navigationSearchIntent(context: Context, query: String): Intent {
    val url = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
    val google = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.google.android.apps.maps")
    }
    return if (google.resolveActivity(context.packageManager) != null) {
        google
    } else {
        // No Google Maps installed — let the system chooser (or Waze, if it
        // registered for https Maps search links) handle it.
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
}

/**
 * Opens the map link the customer pasted, in whatever app claims it. No
 * `setPackage`: a shortened Google link resolves through the browser or Maps
 * depending on what the driver has, and forcing one of them turns a working
 * pin into a dead button.
 */
private fun mapLinkIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

private fun safeStartActivity(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure { if (it is ActivityNotFoundException) { /* No app can handle it — silently ignored, nothing this screen can do about it. */ } }
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
private annotation class TripStatePreviews

private val previewAssignedOrder = DriverOrderDto(
    id = 7,
    orderNumber = "0007-AAAA",
    status = "ready",
    deliveryMethod = "delivery",
    paymentMethod = "cash",
    paymentStatus = "unpaid",
    total = 68.5,
    currency = "SAR",
    cashToCollect = 68.5,
    driverFee = 9.0,
    company = OrderCompanyDto(name = "مطعم البيت السعيد"),
    branch = BranchDto(id = 1, name = "فرع العليا", phone = "+966112223333"),
    zone = OrderZoneDto(name = "حي العليا"),
    items = listOf(OrderItemDto(name = "برجر لحم", quantity = 2), OrderItemDto(name = "بطاطس", quantity = 1)),
    notes = "بدون بصل من فضلك",
    customer = OrderCustomerDto(name = "سلطان العتيبي", phone = "+966501234567"),
    deliveryAddress = DeliveryAddressDto(text = "شارع الأمير سلطان، حي العليا", lat = 24.7, lng = 46.6, notes = "الدور الثانى"),
)

@TripStatePreviews
@Composable
private fun TripResumingPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(phase = TripPhase.Loading),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripNotReadyPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(phase = TripPhase.Content(order = previewAssignedOrder.copy(status = "confirmed"))),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripReadyPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(phase = TripPhase.Content(order = previewAssignedOrder)),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripPickedUpWithDeliverySheetPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(
                phase = TripPhase.Content(order = previewAssignedOrder.copy(status = "out_for_delivery", pickedUpAt = "2026-09-21T10:00:00Z")),
                deliverySheet = DeliverySheetState(visible = true, amountText = "68.50"),
            ),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripDeliveredPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(
                phase = TripPhase.Content(order = previewAssignedOrder.strippedOfPii().copy(status = "delivered")),
                deliveredResult = DeliveredResponse(
                    order = previewAssignedOrder.strippedOfPii(),
                    ledger = LedgerSummaryDto(companyId = 1, currency = "SAR", earnedToday = 9.0, cashOnHand = 68.5, net = -59.5, cashLimit = null),
                ),
            ),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripEndedAfterPickupPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(
                phase = TripPhase.Resolved(
                    order = previewAssignedOrder.copy(status = "cancelled", pickedUpAt = "2026-09-21T10:00:00Z"),
                    outcome = TripOutcome.Cancelled,
                ),
            ),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripEndedBeforePickupPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(
                phase = TripPhase.Resolved(
                    order = previewAssignedOrder.copy(status = "rejected", pickedUpAt = null),
                    outcome = TripOutcome.Rejected,
                ),
            ),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

@TripStatePreviews
@Composable
private fun TripLoadFailedPreview() {
    DriverTheme {
        TripScreen(
            state = TripUiState(phase = TripPhase.LoadFailed(DriverApiError.Offline)),
            onOpenNotifications = null, onRetryLoad = {}, onExitEndedTrip = {}, onPickUp = {}, onOpenDeliverySheet = {}, onDismissDeliverySheet = {},
            onDeliveryAmountChange = {}, onDeliveryNoteChange = {}, onDeliveryCodeChange = {}, onConfirmDelivery = { _, _ -> }, onDoneAfterDelivery = {},
            onOpenIssueSheet = {}, onDismissIssueSheet = {}, onSelectIssueCode = {}, onIssueNoteChange = {}, onSubmitIssue = {}, onDismissIssueReported = {},
        )
    }
}

// endregion
