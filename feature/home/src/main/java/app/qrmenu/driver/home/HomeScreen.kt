package app.qrmenu.driver.home

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Elevation
import app.qrmenu.driver.designsystem.theme.Motion
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.LinkedCompanyDto
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr
import coil3.compose.AsyncImage

/**
 * Screen 5 (project numbering picks up after auth) — the first honest screen a
 * driver lands on after signing in: who am I, and which restaurants do I work
 * for.
 *
 * A driver may work for several restaurants at once (decision 19), so this is
 * the screen that makes that real — one card per restaurant, each naming its
 * own [LinkStatus] rather than silently hiding a restaurant that has not
 * activated the driver yet or has stopped them (decision 47: the reason a
 * driver sees no orders must be named on screen, never left to a blank list).
 */
@Composable
fun HomeRoute(
    onSignedOut: () -> Unit,
    /** Routes to the invite-code screen; that screen belongs to another module. */
    onEnterInviteCode: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onSignOut = { viewModel.signOut(onSignedOut) },
        onEnterInviteCode = onEnterInviteCode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onEnterInviteCode: () -> Unit,
) {
    // A local `val` so the compiler's null-check smart-casts it inside the
    // `else` branch below — including inside `LazyColumn`'s content lambda,
    // where a smart cast on `state.driver` itself would not be trusted to survive.
    val driver = state.driver

    Scaffold { insets ->
        when {
            // Nothing has ever loaded yet — a shimmer skeleton shaped like the
            // content below it, never a centred spinner (CLAUDE.md § rules).
            state.isLoading -> HomeLoadingSkeleton(modifier = Modifier.padding(insets))

            // The FIRST load failed and there is nothing to show underneath —
            // the inline banner is the whole screen's content, not a strip
            // above a list that does not exist yet.
            driver == null -> HomeErrorState(
                error = state.error,
                onRetry = onRefresh,
                modifier = Modifier.padding(insets),
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.lg,
                        vertical = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item { DriverHeader(driver = driver, onSignOut = onSignOut) }

                    // A refresh that failed keeps whatever was already on
                    // screen (CLAUDE.md's whole reason for `isRefreshing`
                    // being separate from `isLoading`) — the failure is a
                    // strip above the list, not a replacement for it.
                    if (state.error != null) {
                        item {
                            DriverErrorBanner(
                                error = state.error,
                                onRetry = onRefresh,
                            )
                        }
                    }

                    if (state.isEmpty) {
                        item { EmptyRestaurantsState(onEnterInviteCode = onEnterInviteCode) }
                    } else {
                        items(state.restaurants, key = { it.company.id }) { link ->
                            RestaurantCard(link = link)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The driver's own identity: name, and phone direction-isolated so `+9665…`
 * does not reorder inside an Arabic layout (see `BidiText`).
 */
@Composable
private fun DriverHeader(driver: DriverDto, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_greeting, driver.name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = driver.phone.ltr(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(
            onClick = onSignOut,
            modifier = Modifier.heightIn(min = TouchTarget.compact),
        ) {
            Text(
                text = stringResource(R.string.home_sign_out),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Zero restaurants is a real, expected state — a phone verified but not yet
 * invited anywhere. It must say so plainly and point at the fix (ask the
 * restaurant for an invite code, decision 15/16), never render a blank list.
 */
@Composable
private fun EmptyRestaurantsState(onEnterInviteCode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        TextButton(
            onClick = onEnterInviteCode,
            modifier = Modifier.heightIn(min = TouchTarget.primary),
        ) {
            Text(
                text = stringResource(R.string.home_empty_action),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * One restaurant, including [LinkStatus] rendered so it cannot be mistaken for
 * one of the other two:
 *
 * * [LinkStatus.Active] — a filled, fully-opaque card; a green check.
 * * [LinkStatus.Invited] — an OUTLINED card only (no fill, no elevation) with
 *   a clock, plus a sentence explaining that no orders will arrive yet. The
 *   shape difference reads at a glance, before the chip's text is even read.
 * * [LinkStatus.Suspended] — a filled card at reduced opacity with a block
 *   icon and a sentence naming what to do about it. Dimming, rather than
 *   hiding, keeps the restaurant visible as something that once worked.
 */
@Composable
private fun RestaurantCard(link: RestaurantLinkDto) {
    val status = link.linkStatus()

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CompanyLogo(logoUrl = link.company.logo)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.company.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (link.branches.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(
                            R.string.home_branches,
                            link.branches.joinToString(separator = stringResource(R.string.home_list_separator)) {
                                it.name
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Spacing.xs))
                StatusChip(status = status)

                val hint = when (status) {
                    LinkStatus.Invited -> R.string.home_status_invited_hint
                    LinkStatus.Suspended -> R.string.home_status_suspended_hint
                    LinkStatus.Active, LinkStatus.Unknown -> null
                }
                if (hint != null) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (status) {
        LinkStatus.Invited -> OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
        ) { content() }

        LinkStatus.Suspended -> Card(
            modifier = Modifier
                .fillMaxWidth()
                // Dimmed, not hidden — a driver a restaurant has stopped should
                // still see the restaurant is there, just not working for it.
                .alpha(SUSPENDED_CONTENT_ALPHA),
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat),
            shape = RoundedCornerShape(Radius.card),
        ) { content() }

        LinkStatus.Active, LinkStatus.Unknown -> Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
            shape = RoundedCornerShape(Radius.card),
        ) { content() }
    }
}

/** A restaurant is left effect-free either way — its logo is decoration, not information. */
@Composable
private fun CompanyLogo(logoUrl: String?) {
    Box(
        modifier = Modifier
            .size(ControlSize.platformLogo)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ControlSize.platformLogo)
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * The status badge: colour AND icon AND text differ per status, so the
 * difference survives sunlight, colour-blindness and a language the reader is
 * only starting to learn.
 */
@Composable
private fun StatusChip(status: LinkStatus) {
    val (container, content, icon, label) = when (status) {
        LinkStatus.Active -> StatusChipStyle(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Filled.CheckCircle,
            label = stringResource(R.string.home_status_active),
        )
        LinkStatus.Invited -> StatusChipStyle(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.home_status_invited),
        )
        LinkStatus.Suspended -> StatusChipStyle(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Filled.Block,
            label = stringResource(R.string.home_status_suspended),
        )
        LinkStatus.Unknown -> StatusChipStyle(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Filled.WarningAmber,
            label = stringResource(R.string.home_status_active),
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            // The label right beside it says the same thing in words.
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(ControlSize.selectionDot),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/** A `data class` already synthesises the `componentN()` this is destructured with. */
private data class StatusChipStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    val label: String,
)

private const val SUSPENDED_CONTENT_ALPHA = 0.6f

/**
 * The inline failure surface for a first load that never produced any data —
 * the whole screen's content, never a dialog and never a toast (CLAUDE.md §
 * mandatory states). [DriverErrorBanner] already tells "no connection" apart
 * from "the server refused" — reused rather than re-invented.
 */
@Composable
private fun HomeErrorState(
    error: DriverApiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error != null) {
            DriverErrorBanner(error = error, onRetry = onRetry)
        }
    }
}

/**
 * A shimmer skeleton shaped like the driver header and the restaurant cards
 * underneath it — never a centred spinner. `tween` is used here on purpose:
 * CLAUDE.md reserves it for exactly this, an ambient looping shimmer, never
 * for motion the driver's own action drives.
 */
@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Header: a name line and a shorter phone line.
        ShimmerBlock(brush, widthFraction = 0.5f, height = Spacing.lg)
        Spacer(Modifier.height(Spacing.xxs))
        ShimmerBlock(brush, widthFraction = 0.3f, height = Spacing.md)

        Spacer(Modifier.height(Spacing.md))

        repeat(SKELETON_CARD_COUNT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SKELETON_CARD_BACKGROUND_ALPHA),
                        RoundedCornerShape(Radius.card),
                    )
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(ControlSize.platformLogo)
                        .clip(CircleShape)
                        .background(brush),
                )
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBlock(brush, widthFraction = 0.6f, height = Spacing.md)
                    Spacer(Modifier.height(Spacing.xs))
                    ShimmerBlock(brush, widthFraction = 0.4f, height = Spacing.sm)
                    Spacer(Modifier.height(Spacing.xs))
                    ShimmerBlock(brush, widthFraction = 0.3f, height = Spacing.sm)
                }
            }
        }
    }
}

@Composable
private fun ShimmerBlock(brush: Brush, widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(Radius.chip))
            .background(brush),
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "home_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.shimmerCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_skeleton_shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(SHIMMER_TRAVEL * translate - SHIMMER_TRAVEL, 0f),
        end = Offset(SHIMMER_TRAVEL * translate, 0f),
    )
}

private const val SKELETON_CARD_COUNT = 3
private const val SKELETON_CARD_BACKGROUND_ALPHA = 0.35f
private const val SHIMMER_TRAVEL = 600f

// region Previews

/**
 * Every state, in every language, in both themes (CLAUDE.md § rules — a
 * `@Preview(locale=…)` per screen, light AND dark).
 */
@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ur dark", locale = "ur", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "bn dark", locale = "bn", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "hi dark", locale = "hi", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class HomeStatePreviews

private val previewDriver = DriverDto(
    id = 1,
    name = "محمد العتيبي",
    phone = "+966501234567",
    isActive = true,
    isOnline = false,
)

private val previewBranch = BranchDto(id = 1, name = "فرع العليا")

@HomeStatePreviews
@Composable
private fun HomeScreenLoadingPreview() {
    DriverTheme {
        HomeScreen(
            state = HomeUiState(isLoading = true),
            onRefresh = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@HomeStatePreviews
@Composable
private fun HomeScreenLoadedPreview() {
    DriverTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                driver = previewDriver,
                restaurants = listOf(
                    RestaurantLinkDto(
                        company = LinkedCompanyDto(id = 1, name = "مطعم البيت السعيد"),
                        branches = listOf(previewBranch, previewBranch.copy(id = 2, name = "فرع الملز")),
                        status = "active",
                    ),
                    RestaurantLinkDto(
                        company = LinkedCompanyDto(id = 2, name = "مطعم الأصايل"),
                        branches = listOf(previewBranch),
                        status = "invited",
                    ),
                    RestaurantLinkDto(
                        company = LinkedCompanyDto(id = 3, name = "مطعم زاد"),
                        branches = listOf(previewBranch),
                        status = "suspended",
                    ),
                ),
            ),
            onRefresh = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@HomeStatePreviews
@Composable
private fun HomeScreenEmptyPreview() {
    DriverTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, driver = previewDriver, restaurants = emptyList()),
            onRefresh = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@HomeStatePreviews
@Composable
private fun HomeScreenErrorPreview() {
    DriverTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                driver = null,
                error = DriverApiError.Api(
                    httpStatus = 500,
                    code = DriverErrorCode.ServerError,
                    message = null,
                    rawCode = "server_error",
                ),
            ),
            onRefresh = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@HomeStatePreviews
@Composable
private fun HomeScreenOfflinePreview() {
    DriverTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, driver = null, error = DriverApiError.Offline),
            onRefresh = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

// endregion
