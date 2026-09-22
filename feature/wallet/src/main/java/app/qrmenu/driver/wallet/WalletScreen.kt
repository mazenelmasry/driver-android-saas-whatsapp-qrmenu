package app.qrmenu.driver.wallet

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Elevation
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.ui.components.DriverScreenScaffold
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.LedgerEntryDto
import app.qrmenu.driver.network.dto.LedgerResponse
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverArt
import app.qrmenu.driver.ui.components.DriverEmptyState
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr

/**
 * «المحفظة» — one restaurant's book at a time.
 *
 * 🔴 Never a total across restaurants (CLAUDE.md's «الدفتر المالى», binding
 * rule 2): the switcher changes WHICH restaurant is shown, it never adds one
 * restaurant's figures to another's — see [WalletUiState.selectedCompanyId]
 * and [WalletViewModel.selectRestaurant].
 */
@Composable
fun WalletRoute(
    /** Opens the notification centre from this screen's bell. Null hides the bell. */
    onOpenNotifications: (() -> Unit)? = null,
    /**
     * How many notifications the driver has not opened yet — the number on
     * the bell. Zero draws no badge at all, which is almost always.
     */
    unreadNotifications: Int = 0,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.pane) {
        WalletPane.Book -> WalletScreen(
            state = state,
            onOpenNotifications = onOpenNotifications,
            unreadNotifications = unreadNotifications,
            onSelectRestaurant = viewModel::selectRestaurant,
            onRefreshBook = {
                state.selectedCompanyId?.let { viewModel.loadBook(it, isRefresh = true) }
            },
            onRetryBook = viewModel::retryBook,
            onRetryRestaurants = viewModel::retryRestaurants,
            onOpenSettlements = viewModel::openSettlements,
        )

        WalletPane.Settlements -> SettlementsScreen(
            state = state.settlements,
            onBack = viewModel::closeSettlements,
            onRetry = viewModel::retrySettlements,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun WalletScreen(
    state: WalletUiState,
    onSelectRestaurant: (Long) -> Unit,
    onRefreshBook: () -> Unit,
    onRetryBook: () -> Unit,
    onRetryRestaurants: () -> Unit,
    onOpenSettlements: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null,
    /**
     * How many notifications the driver has not opened yet — the number on
     * the bell. Zero draws no badge at all, which is almost always.
     */
    unreadNotifications: Int = 0,
) {
    // Restaurant context is framed INSIDE the coloured header now, not as bare
    // text floating above the figures (task brief's complaint #5): one
    // restaurant becomes the header's subtitle, several become a switcher row
    // living in `belowTitle` — never both, and neither renders until the
    // restaurant list has actually loaded successfully.
    val restaurantsReady = !state.restaurantsLoading && state.restaurantsError == null && state.restaurants.isNotEmpty()
    val singleRestaurantName = state.restaurants.singleOrNull().takeIf { restaurantsReady }?.name

    Scaffold {
        // The shared frame — one title placement and one bell across all four
        // destinations, rather than each screen drawing its own header.
        DriverScreenScaffold(
            title = stringResource(R.string.wallet_title),
            subtitle = singleRestaurantName,
            onOpenNotifications = onOpenNotifications,
            unreadNotifications = unreadNotifications,
            belowTitle = if (restaurantsReady && state.restaurants.size > 1) {
                {
                    RestaurantSwitcher(
                        restaurants = state.restaurants,
                        selectedCompanyId = state.selectedCompanyId,
                        onSelect = onSelectRestaurant,
                    )
                }
            } else {
                null
            },
        ) {
            when {
                state.restaurantsLoading -> WalletLoadingSkeleton()

                state.restaurantsError != null -> Box(modifier = Modifier.padding(Spacing.lg)) {
                    DriverErrorBanner(error = state.restaurantsError, onRetry = onRetryRestaurants)
                }

                state.restaurants.isEmpty() -> RestaurantsEmptyState()

                else -> BookPane(
                    state = state,
                    onRefreshBook = onRefreshBook,
                    onRetryBook = onRetryBook,
                    onOpenSettlements = onOpenSettlements,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BookPane(
    state: WalletUiState,
    onRefreshBook: () -> Unit,
    onRetryBook: () -> Unit,
    onOpenSettlements: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.book.isLoading -> BookLoadingSkeleton()

            state.book.error != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                item { DriverErrorBanner(error = state.book.error, onRetry = onRetryBook) }
            }

            else -> PullToRefreshBox(isRefreshing = state.book.isRefreshing, onRefresh = onRefreshBook) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 🔴 Bottom clearance beyond the ordinary rhythm on
                    // purpose (Spacing.xxl, not Spacing.md): the last ledger
                    // row must fully clear the tab bar rather than sit flush
                    // against it or read as cut off (task brief's defect #1).
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.md,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    state.book.summary?.let { summary ->
                        item(key = "figures") { TheThreeFigures(summary) }
                    }

                    item(key = "settlements_link") {
                        TextButton(onClick = onOpenSettlements, modifier = Modifier.heightIn(min = TouchTarget.compact)) {
                            Icon(
                                imageVector = Icons.Filled.SyncAlt,
                                contentDescription = null,
                                modifier = Modifier.size(ControlSize.inlineIcon),
                            )
                            Spacer(Modifier.width(Spacing.xxs))
                            Text(
                                text = stringResource(R.string.wallet_settlements_link),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    item(key = "book_heading") {
                        Text(
                            text = stringResource(R.string.wallet_book_heading),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.book.entries.isEmpty()) {
                        item(key = "book_empty") { BookEmptyState() }
                    }

                    items(state.book.entries, key = LedgerEntryDto::id) { entry ->
                        LedgerEntryRow(entry = entry, currency = state.book.summary?.currency.orEmpty())
                    }
                }
            }
        }
    }
}

/**
 * A row of the driver's linked restaurants — the ONLY control that changes
 * which book is shown.
 *
 * Lives inside the header's coloured block now (via `belowTitle`), which
 * is what gives it the framing the task brief asked for (complaint #5): it
 * reads as "you are choosing whose book this is" rather than as a stray row
 * of text sitting above the figures. Because it renders on the gradient, its
 * colours come from [LocalContentColor] (the header's `onHeader` tone) rather
 * than the page's own surface roles — a chip using `surfaceContainerHigh`
 * here would be a flat grey patch on burnt orange.
 */
@Composable
private fun RestaurantSwitcher(
    restaurants: List<RestaurantOption>,
    selectedCompanyId: Long?,
    onSelect: (Long) -> Unit,
) {
    val onHeader = LocalContentColor.current
    LazyRow(
        contentPadding = PaddingValues(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(restaurants, key = RestaurantOption::companyId) { restaurant ->
            val selected = restaurant.companyId == selectedCompanyId
            val a11yLabel = stringResource(R.string.wallet_a11y_select_restaurant, restaurant.name)
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    onHeader.copy(alpha = UNSELECTED_CHIP_ALPHA)
                },
                border = if (selected) {
                    null
                } else {
                    BorderStroke(Stroke.hairline, onHeader.copy(alpha = UNSELECTED_CHIP_BORDER_ALPHA))
                },
                modifier = Modifier
                    .heightIn(min = TouchTarget.compact)
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(onClick = { onSelect(restaurant.companyId) })
                    .semantics { contentDescription = a11yLabel },
            ) {
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        onHeader
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}

private const val UNSELECTED_CHIP_ALPHA = 0.16f
private const val UNSELECTED_CHIP_BORDER_ALPHA = 0.4f

/**
 * كسبتَ اليوم · بحوزتك نقداً · الصافى — the hero of the screen (CLAUDE.md's
 * exact three labels). "بحوزتك نقداً" gets the strongest treatment: it is the
 * figure that changes a driver's day, and the one a cash ceiling threatens.
 */
@Composable
private fun TheThreeFigures(summary: LedgerResponse) {
    // The two tiles stack instead of clipping when the type gets big.
    //
    // Two half-width tiles are right at the design's own size. At the 200%
    // system font scale this app is required to support (CLAUDE.md — the POS
    // pins the scale and that was judged wrong for an older driver), each one
    // is barely wider than the word "SAR": the figure was first CLIPPED to
    // "25.0", and once allowed to wrap it broke INSIDE the number —
    // "25.0 / 0 / SAR" — which reads as two amounts. A taller screen is the
    // least bad of the three. Nothing changes below the threshold.
    val stacked = LocalDensity.current.fontScale >= STACK_FIGURES_ABOVE_FONT_SCALE

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                EarnedTodayFigure(summary, Modifier.fillMaxWidth())
                NetFigure(summary, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                // `IntrinsicSize.Min` makes both cards as tall as the taller
                // one. Without it the card carrying a third line ("المطعم
                // يدين لك") stands taller than its neighbour, and two figures
                // that belong to the same glance stop looking like a pair.
                // Meaningless once they are stacked, so it is asked for here
                // only.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                EarnedTodayFigure(summary, Modifier.weight(1f).fillMaxHeight())
                NetFigure(summary, Modifier.weight(1f).fillMaxHeight())
            }
        }

        CashOnHandCard(
            cashOnHand = summary.cashOnHand,
            cashLimit = summary.cashLimit,
            currency = summary.currency,
        )
    }
}

@Composable
private fun EarnedTodayFigure(summary: LedgerResponse, modifier: Modifier) {
    FigureCard(
        label = stringResource(R.string.wallet_earned_today_label),
        value = formatMoney(summary.earnedToday, summary.currency),
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        iconTint = MaterialTheme.colorScheme.primary,
        iconContainer = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    )
}

/**
 * The net, whose icon follows its own sign — "the restaurant owes you" and
 * "you owe the restaurant" are told apart by MORE than the figure's colour
 * (driver-ui-standards), because a cheap screen in sunlight may not resolve
 * one warm hue from another.
 */
@Composable
private fun NetFigure(summary: LedgerResponse, modifier: Modifier) {
    val netIsPositive = summary.net >= 0
    FigureCard(
        label = stringResource(R.string.wallet_net_label),
        value = formatSignedMoney(summary.net, summary.currency),
        icon = if (netIsPositive) {
            Icons.AutoMirrored.Filled.TrendingUp
        } else {
            Icons.AutoMirrored.Filled.TrendingDown
        },
        iconTint = if (netIsPositive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        iconContainer = if (netIsPositive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        valueColor = if (netIsPositive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        supporting = stringResource(
            if (netIsPositive) R.string.wallet_net_owed_to_you else R.string.wallet_net_you_owe,
        ),
        modifier = modifier,
    )
}

/**
 * Above this system font scale the two figure tiles stop sharing a row.
 *
 * 1.5 rather than 2.0: the breakage starts well before the maximum — at 150%
 * the currency is already crowding the digits — and stacking early costs
 * nothing but a little scrolling.
 */
private const val STACK_FIGURES_ABOVE_FONT_SCALE = 1.5f


/**
 * One of the two hero tiles. Carries an icon badge — the same circular,
 * tinted-container language the ledger rows below use — so the figures that
 * changed a driver's day read as members of one card family rather than as
 * flat white boxes with numbers in them (task brief's complaint #2). The
 * badge is tonal, not just decorative: it repeats the earn/owe colour the
 * value text already carries, one more way (beyond hue) to tell the two
 * tiles apart at a glance.
 */
@Composable
private fun FigureCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    supporting: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.card),
        // White, not a tonal step — this theme's language is white panels
        // floating on warm paper (see `Theme.kt`), and a grey tile on that
        // paper reads as muddy rather than as raised. The weight comes from
        // the shadow and the icon badge instead, so these still support the
        // stronger `CashOnHandCard` beneath rather than competing with it.
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elevation.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .size(ControlSize.orderAvatar)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
            }

            Spacer(Modifier.height(Spacing.xxs))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 🔴 Money is never clipped, and this is why the type here is a
            // step smaller than the cash card's.
            //
            // With `maxLines = 1` and no ellipsis, "25.00 SAR" in a half-width
            // tile was CLIPPED to "25.0" on the device at a large font scale.
            // A figure that loses a digit and its currency is not a smaller
            // figure, it is a wrong one.
            //
            // So it wraps instead, and `maxLines = 2` is what it wraps within.
            // Measured on the device, a half-width tile is NOT wide enough for
            // "25.00 SAR" on one line even at `titleLarge` — the amount takes
            // one line and the currency the next, in both tiles alike, which
            // reads as a deliberate treatment rather than as damage. Above
            // 150% font scale the pair stops sharing a row altogether and the
            // whole question goes away (see `TheThreeFigures`).
            Text(
                text = value.ltr(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 2,
            )
            supporting?.let {
                // Same reasoning: "المطعم يدين لك" clipped to "المطعم" says
                // the opposite of what it means.
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "بحوزتك نقداً" as its own full-width card — the strongest treatment on the
 * screen (task brief), because it is the figure that changes what a driver
 * does next: crossing [cashLimit] stops cash-on-delivery offers reaching
 * them (CLAUDE.md's «الدفتر المالى»).
 */
@Composable
private fun CashOnHandCard(cashOnHand: Double, cashLimit: Double?, currency: String) {
    val hasLimit = cashLimit != null && cashLimit > 0
    val atLimit = hasLimit && cashOnHand >= (cashLimit ?: 0.0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = if (atLimit) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shadowElevation = Elevation.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Payments,
                    contentDescription = null,
                    tint = if (atLimit) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
                Text(
                    text = stringResource(R.string.wallet_cash_on_hand_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (atLimit) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }

            Text(
                text = formatMoney(cashOnHand, currency).ltr(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (atLimit) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                maxLines = 1,
            )

            if (cashLimit != null && hasLimit) {
                LinearProgressIndicator(
                    progress = { (cashOnHand / cashLimit).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (atLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Text(
                    text = stringResource(
                        R.string.wallet_cash_limit_hint,
                        formatMoney(cashOnHand, currency).ltr(),
                        formatMoney(cashLimit, currency).ltr(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (atLimit) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                if (atLimit) {
                    Text(
                        text = stringResource(R.string.wallet_cash_limit_reached),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

/**
 * One row of the book: what happened, the order it belongs to (when there is
 * one), the signed amount, and when.
 *
 * Earning vs. debt is told apart by BOTH the icon/sign AND the colour — never
 * colour alone (driver-ui-standards). An unrecognised future [LedgerEntryDto.type]
 * renders as a plain neutral row rather than failing to render at all (the DTO's
 * own contract — see its doc comment).
 */
@Composable
private fun LedgerEntryRow(entry: LedgerEntryDto, currency: String) {
    val kind = entryKind(entry.type)
    val isCredit = entry.amount >= 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        // A hint of lift so the row reads as a card in a stack rather than a
        // flat line in a list (task brief's complaint #3) — kept subtle
        // ([Elevation.card], the same weight the hero tiles carry) so a whole
        // scrolling list of these does not turn into a wall of shadows.
        shadowElevation = Elevation.card,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ControlSize.orderAvatar)
                    .clip(CircleShape)
                    .background(
                        if (isCredit) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = if (isCredit) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(ControlSize.inlineIcon),
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(kind.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondLine = listOfNotNull(
                    entry.orderNumber?.let { stringResource(R.string.wallet_entry_order_reference, it) },
                    formatEntryTimestamp(entry.createdAt),
                ).joinToString(" · ")
                if (secondLine.isNotBlank()) {
                    Text(
                        text = secondLine.ltr(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = formatSignedMoney(entry.amount, currency).ltr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
            )
        }
    }
}

private data class EntryKind(val icon: ImageVector, val labelRes: Int)

private fun entryKind(type: String): EntryKind = when (type) {
    "delivery_fee_earned" -> EntryKind(Icons.AutoMirrored.Filled.TrendingUp, R.string.wallet_entry_type_delivery_fee_earned)
    "cash_collected" -> EntryKind(Icons.Filled.CreditCard, R.string.wallet_entry_type_cash_collected)
    "failed_trip_fee" -> EntryKind(Icons.AutoMirrored.Filled.TrendingUp, R.string.wallet_entry_type_failed_trip_fee)
    "settlement" -> EntryKind(Icons.Filled.SyncAlt, R.string.wallet_entry_type_settlement)
    "manual_adjustment" -> EntryKind(Icons.AutoMirrored.Filled.TrendingDown, R.string.wallet_entry_type_manual_adjustment)
    else -> EntryKind(Icons.AutoMirrored.Filled.ReceiptLong, R.string.wallet_entry_type_unknown)
}

/**
 * No restaurant has this driver on its list — [DriverArt.Storefront] (task
 * brief), replacing the flat grey two-line box this used to be (complaint
 * #4). This is a distinct reason from an empty book (below): there is no
 * restaurant to have a book WITH yet.
 */
@Composable
private fun RestaurantsEmptyState() {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        DriverEmptyState(
            art = DriverArt.Storefront,
            title = stringResource(R.string.wallet_restaurants_empty_title),
            body = stringResource(R.string.wallet_restaurants_empty_body),
        )
    }
}

/** Nothing in the ledger yet — [DriverArt.Wallet] (task brief), same replacement. */
@Composable
private fun BookEmptyState() {
    DriverEmptyState(
        art = DriverArt.Wallet,
        title = stringResource(R.string.wallet_book_empty_title),
        body = stringResource(R.string.wallet_book_empty_body),
    )
}

/** Shimmer matching the eventual figure cards + list — never a centred spinner. */
@Composable
internal fun WalletLoadingSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.giant)
                .clip(RoundedCornerShape(Radius.card))
                .background(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.giant * 2)
                .clip(RoundedCornerShape(Radius.card))
                .background(brush),
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.giant)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(brush),
            )
        }
    }
}

@Composable
private fun BookLoadingSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.giant * 2)
                .clip(RoundedCornerShape(Radius.card))
                .background(brush),
        )
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.giant)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(brush),
            )
        }
    }
}

@Composable
internal fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "wallet_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wallet_skeleton_shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(600f * translate - 600f, 0f),
        end = Offset(600f * translate, 0f),
    )
}

// region Previews

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class WalletStatePreviews

private val previewRestaurantOne = RestaurantOption(companyId = 1, name = "مطعم البيت السعيد", logo = null, currency = "SAR")
private val previewRestaurantTwo = RestaurantOption(companyId = 2, name = "Cairo Kitchen", logo = null, currency = "EGP")

private val previewLedger = LedgerResponse(
    companyId = 1,
    currency = "SAR",
    earnedToday = 87.5,
    cashOnHand = 165.0,
    net = -77.5,
    cashLimit = 200.0,
    entries = listOf(
        LedgerEntryDto(
            id = 1,
            type = "delivery_fee_earned",
            amount = 7.5,
            balanceAfter = -77.5,
            orderId = 501,
            orderNumber = "0012-AB",
            createdAt = java.time.Instant.now().minusSeconds(600).toString(),
        ),
        LedgerEntryDto(
            id = 2,
            type = "cash_collected",
            amount = -45.0,
            balanceAfter = -85.0,
            orderId = 500,
            orderNumber = "0011-AA",
            createdAt = java.time.Instant.now().minusSeconds(4_000).toString(),
        ),
        LedgerEntryDto(
            id = 3,
            type = "settlement",
            amount = 50.0,
            balanceAfter = -40.0,
            createdAt = java.time.Instant.now().minusSeconds(90_000).toString(),
        ),
    ),
)

@WalletStatePreviews
@Composable
private fun WalletLoadingPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(restaurantsLoading = true),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletContentSingleRestaurantPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(
                restaurantsLoading = false,
                restaurants = listOf(previewRestaurantOne),
                selectedCompanyId = 1,
                book = BookState(isLoading = false, summary = previewLedger),
            ),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletContentMultipleRestaurantsPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(
                restaurantsLoading = false,
                restaurants = listOf(previewRestaurantOne, previewRestaurantTwo),
                selectedCompanyId = 1,
                book = BookState(isLoading = false, summary = previewLedger),
            ),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletCashLimitReachedPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(
                restaurantsLoading = false,
                restaurants = listOf(previewRestaurantOne),
                selectedCompanyId = 1,
                book = BookState(isLoading = false, summary = previewLedger.copy(cashOnHand = 210.0, cashLimit = 200.0)),
            ),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletBookEmptyPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(
                restaurantsLoading = false,
                restaurants = listOf(previewRestaurantOne),
                selectedCompanyId = 1,
                book = BookState(isLoading = false, summary = previewLedger.copy(entries = emptyList())),
            ),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletRestaurantsEmptyPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(restaurantsLoading = false, restaurants = emptyList()),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

@WalletStatePreviews
@Composable
private fun WalletErrorPreview() {
    DriverTheme {
        WalletScreen(
            state = WalletUiState(
                restaurantsLoading = false,
                restaurants = listOf(previewRestaurantOne),
                selectedCompanyId = 1,
                book = BookState(isLoading = false, error = DriverApiError.Offline),
            ),
            onSelectRestaurant = {},
            onRefreshBook = {},
            onRetryBook = {},
            onRetryRestaurants = {},
            onOpenSettlements = {},
        )
    }
}

// endregion
