package app.qrmenu.driver.orders

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.qrmenu.driver.ui.orders.CashHoldBanner
import app.qrmenu.driver.ui.orders.NoOrdersReason
import app.qrmenu.driver.ui.orders.messageResource
import app.qrmenu.driver.ui.orders.noOrdersReason
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.ui.components.DriverScreenScaffold
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.ContextBranchDto
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverErrorBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * «طلباتى» و«المتاحة» — the two order lists (README step 3 of the task brief).
 *
 * The 20s resumed-screen poll (a safety net until Reverb lands, CLAUDE.md)
 * lives here rather than in the ViewModel: it must run only while this screen
 * is actually on screen, which is a [Lifecycle] question, not a state-holder
 * one — the same separation `SignedInScreen`'s `LocationWiringViewModel`
 * keeps between "the server confirmed X" and "the OS says the screen is
 * visible".
 */
@Composable
fun OrdersRoute(
    onOpenTrip: (Long) -> Unit = {},
    noOrdersContextOut: (AvailabilityContextDto?) -> Unit = {},
    onOpenNotifications: (() -> Unit)? = null,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Surfaces the live "why is المتاحة empty" context to a caller — wired into
    // AvailabilityRoute's noOrdersContext parameter from SignedInScreen, so the
    // Availability tab finally shows the real decision-47 reason instead of the
    // `null` placeholder it shipped with while this module did not exist yet.
    LaunchedEffect(state.availableContext) { noOrdersContextOut(state.availableContext) }

    // The 20s safety-net poll (CLAUDE.md — a stand-in until Reverb lands) runs
    // ONLY while this screen is actually resumed: `repeatOnLifecycle` cancels
    // and restarts the block on every RESUMED/non-RESUMED transition, so
    // backgrounding the app (or switching to the Restaurants tab, which keeps
    // this composable alive but off-screen under `SignedInScreen`'s `when`)
    // stops the poll rather than burning battery on a hidden screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshQuietly()
            pollForever(viewModel::refreshQuietly)
        }
    }

    OrdersScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onRefreshMine = { viewModel.loadMine(isRefresh = true) },
        onRefreshAvailable = { viewModel.loadAvailable(isRefresh = true) },
        onRetry = viewModel::retry,
        onOpenTrip = onOpenTrip,
        onOpenNotifications = onOpenNotifications,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrdersScreen(
    state: OrdersUiState,
    onSelectTab: (OrdersTab) -> Unit,
    onRefreshMine: () -> Unit,
    onRefreshAvailable: () -> Unit,
    onRetry: (OrdersTab) -> Unit,
    onOpenTrip: (Long) -> Unit = {},
    onOpenNotifications: (() -> Unit)? = null,
) {
    Scaffold {
        // The shared frame, so this screen carries the same title placement
        // and the same bell as every other destination.
        DriverScreenScaffold(
            title = stringResource(R.string.orders_title),
            onOpenNotifications = onOpenNotifications,
        ) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == OrdersTab.Mine,
                    onClick = { onSelectTab(OrdersTab.Mine) },
                    text = { Text(stringResource(R.string.orders_tab_mine)) },
                    modifier = Modifier.height(TouchTarget.compact),
                )
                Tab(
                    selected = state.tab == OrdersTab.Available,
                    onClick = { onSelectTab(OrdersTab.Available) },
                    text = { Text(stringResource(R.string.orders_tab_available)) },
                    modifier = Modifier.height(TouchTarget.compact),
                )
            }

            when (state.tab) {
                OrdersTab.Mine -> MineList(
                    listState = state.mine,
                    onRefresh = onRefreshMine,
                    onRetry = { onRetry(OrdersTab.Mine) },
                    onOpenTrip = onOpenTrip,
                )
                OrdersTab.Available -> AvailableList(
                    listState = state.available,
                    context = state.availableContext,
                    onRefresh = onRefreshAvailable,
                    onRetry = { onRetry(OrdersTab.Available) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MineList(
    listState: OrderListState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    when {
        listState.isLoading -> OrdersLoadingSkeleton()
        else -> PullToRefreshBox(isRefreshing = listState.isRefreshing, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (listState.error != null) {
                    item { DriverErrorBanner(error = listState.error, onRetry = onRetry) }
                }

                if (listState.error == null && listState.orders.isEmpty()) {
                    item { MineEmptyState() }
                }

                items(listState.orders, key = DriverOrderDto::id) { order ->
                    AssignedOrderCard(
                        summary = order.toAssignedSummary(),
                        onOpenTrip = { onOpenTrip(order.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailableList(
    listState: OrderListState,
    context: AvailabilityContextDto?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        listState.isLoading -> OrdersLoadingSkeleton()
        else -> PullToRefreshBox(isRefreshing = listState.isRefreshing, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (listState.error != null) {
                    item { DriverErrorBanner(error = listState.error, onRetry = onRetry) }
                }

                // Deliberately independent of `orders`/`error`/`reason`: a driver
                // over a branch's cash ceiling still sees card orders come through
                // (so the list is not empty and `reason` is often `nothing_pending`,
                // which reads as "all good, just wait") — that would be a lie for
                // cash. Always shown while held, whether the list is empty or not.
                item { CashHoldBanner(cashHold = context?.cashHold) }

                // Decision 47: an empty "المتاحة" always names why — reused
                // verbatim from :feature:availability rather than a second copy.
                if (listState.error == null && listState.orders.isEmpty() && context != null) {
                    item { AvailableEmptyState(context = context) }
                }

                items(listState.orders, key = DriverOrderDto::id) { order ->
                    OfferedOrderCard(summary = order.toOfferedSummary())
                }
            }
        }
    }
}

@Composable
private fun MineEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.orders_mine_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.orders_mine_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decision-47 reason, plus every linked branch named (decision 47's other half). */
@Composable
private fun AvailableEmptyState(context: AvailabilityContextDto) {
    val reason = context.noOrdersReason()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (reason != null) {
            Text(
                text = stringResource(reason.messageResource()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (context.branches.isNotEmpty()) {
            Text(
                text = stringResource(R.string.orders_available_branches_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            context.branches.forEach { branch ->
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shimmer matching the eventual card shape — never a centred spinner. */
@Composable
private fun OrdersLoadingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "orders_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orders_skeleton_shimmer_translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(600f * translate - 600f, 0f),
        end = Offset(600f * translate, 0f),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.giant * 2)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(brush),
            )
        }
    }
}

// region Previews

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class OrdersStatePreviews

private val previewOfferedOrder = DriverOrderDto(
    id = 1,
    orderNumber = "0012-AB",
    status = "confirmed",
    deliveryMethod = "delivery",
    paymentMethod = "cash",
    paymentStatus = "pending",
    total = 45.0,
    cashToCollect = 45.0,
    currency = "SAR",
    driverFee = 7.5,
    company = app.qrmenu.driver.network.dto.OrderCompanyDto(name = "مطعم البيت السعيد"),
    branch = app.qrmenu.driver.network.dto.BranchDto(id = 1, name = "فرع العليا"),
    zone = app.qrmenu.driver.network.dto.OrderZoneDto(name = "حي العليا"),
    distanceKm = 3.4,
    items = listOf(app.qrmenu.driver.network.dto.OrderItemDto(name = "برجر", quantity = 2)),
    expectedReadyAt = java.time.Instant.now().plusSeconds(480).toString(),
)

private val previewAssignedOrder = previewOfferedOrder.copy(
    customer = app.qrmenu.driver.network.dto.OrderCustomerDto(name = "سلطان العتيبي", phone = "+966501234567"),
    deliveryAddress = app.qrmenu.driver.network.dto.DeliveryAddressDto(text = "شارع الأمير سلطان، حي العليا"),
    readyAt = java.time.Instant.now().minusSeconds(30).toString(),
)

@OrdersStatePreviews
@Composable
private fun OrdersMineLoadingPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(tab = OrdersTab.Mine, mine = OrderListState(isLoading = true)),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

@OrdersStatePreviews
@Composable
private fun OrdersMineContentPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(
                tab = OrdersTab.Mine,
                mine = OrderListState(isLoading = false, orders = listOf(previewAssignedOrder)),
            ),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

@OrdersStatePreviews
@Composable
private fun OrdersMineEmptyPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(tab = OrdersTab.Mine, mine = OrderListState(isLoading = false)),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

@OrdersStatePreviews
@Composable
private fun OrdersMineErrorPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(
                tab = OrdersTab.Mine,
                mine = OrderListState(isLoading = false, error = DriverApiError.Offline),
            ),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

@OrdersStatePreviews
@Composable
private fun OrdersAvailableContentPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(
                tab = OrdersTab.Available,
                available = OrderListState(isLoading = false, orders = listOf(previewOfferedOrder)),
            ),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

@OrdersStatePreviews
@Composable
private fun OrdersAvailableEmptyPreview() {
    DriverTheme {
        OrdersScreen(
            state = OrdersUiState(
                tab = OrdersTab.Available,
                available = OrderListState(isLoading = false),
                availableContext = AvailabilityContextDto(
                    isOnline = true,
                    branches = listOf(
                        ContextBranchDto(id = 1, name = "فرع العليا", isOpen = true, distanceKm = 1.2, withinRadius = true),
                    ),
                    reason = "nothing_pending",
                ),
            ),
            onSelectTab = {},
            onRefreshMine = {},
            onRefreshAvailable = {},
            onRetry = {},
        )
    }
}

// endregion
