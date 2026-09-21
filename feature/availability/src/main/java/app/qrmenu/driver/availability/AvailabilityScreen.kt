package app.qrmenu.driver.availability

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Motion
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.AvailabilityContextDto
import app.qrmenu.driver.network.dto.ContextBranchDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single busiest screen in the app: the global "Available / Offline"
 * switch (decision 20), which of the driver's linked branches are currently
 * offering work and why not otherwise (decision 47), and whether the
 * connection the switch depends on is even alive.
 *
 * [connectionFailing] and [noOrdersContext] are optional inputs on purpose —
 * see [AvailabilityViewModel] and [NoOrdersReason] for why this module does
 * not own either feed yet, and does not need to in order to compile, preview,
 * or be tested today.
 */
@Composable
fun AvailabilityRoute(
    /**
     * From `:core:location`'s heartbeat uploader — true while the last upload
     * failed. A plain [StateFlow] parameter rather than a hard module
     * dependency, so this module builds standalone; the app wires the real
     * flow in when it composes this route.
     */
    connectionFailing: StateFlow<Boolean> = MutableStateFlow(false),
    /**
     * The live "why no orders" feed from `GET /driver/orders/available`
     * (`:feature:trip`, landing next week). `null` = not wired yet / not known —
     * see [NoOrdersReason]'s doc for why this screen does not call that
     * endpoint itself.
     */
    noOrdersContext: StateFlow<AvailabilityContextDto?> = MutableStateFlow(null),
    /** `:core:location` renders permission/battery-optimisation warnings here; this module owns none of that content. */
    warningSlot: @Composable () -> Unit = {},
    /** Called once the server has CONFIRMED `is_online = true`. Starts the location service — this module never starts it itself. */
    onGoOnline: () -> Unit = {},
    /** Called once the server has CONFIRMED `is_online = false`. Stops the location service. */
    onGoOffline: () -> Unit = {},
    viewModel: AvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionLost by connectionFailing.collectAsStateWithLifecycle()
    val context by noOrdersContext.collectAsStateWithLifecycle()

    AvailabilityScreen(
        state = state,
        connectionLost = connectionLost,
        context = context,
        warningSlot = warningSlot,
        onRetryLoad = viewModel::retry,
        onToggle = viewModel::onToggle,
    )

    // Fires only once a request has SETTLED (never mid-flight, `isPending`
    // guards that), and only on the value the server actually returned — the
    // exact same "never optimistic" guarantee `AvailabilityViewModel.onToggle`
    // gives the switch also governs when the location service starts/stops.
    LaunchedEffect(state.isOnline, state.isPending) {
        if (state.isPending) return@LaunchedEffect
        if (state.isOnline) onGoOnline() else onGoOffline()
    }
}

@Composable
internal fun AvailabilityScreen(
    state: AvailabilityUiState,
    connectionLost: Boolean,
    context: AvailabilityContextDto?,
    warningSlot: @Composable () -> Unit,
    onRetryLoad: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Scaffold { insets ->
        when {
            // First load only — never shown again once anything has arrived,
            // same contract as `:feature:home`'s HomeUiState.isLoading.
            state.isLoading -> AvailabilityLoadingSkeleton(modifier = Modifier.padding(insets))

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.availability_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Connection lost is its OWN banner, distinct from
                // DriverErrorBanner: this is not a request that failed, it is
                // the heartbeat the switch depends on going silent, and the
                // server will act on it (offline in 3 minutes) whether or not
                // the driver ever touches the switch again.
                if (connectionLost) {
                    item { ConnectionLostBanner() }
                }

                if (state.error != null) {
                    item {
                        DriverErrorBanner(
                            error = state.error,
                            onRetry = if (state.driverNeverLoaded) onRetryLoad else null,
                        )
                    }
                }

                item {
                    AvailabilitySwitch(
                        isOnline = state.isOnline,
                        isPending = state.isPending,
                        onlineSince = state.onlineSince,
                        onToggle = onToggle,
                    )
                }

                item { warningSlot() }

                if (context != null) {
                    item { NoOrdersReasonCard(context = context) }
                }
            }
        }
    }
}

/**
 * A retryable request failure retries the SAME thing that produced it
 * (reloading `/driver/me`) — a toggle failure has its own retry, the switch
 * itself (tap it again). `has_active_trip` from a toggle attempt has nothing
 * to retry at all (`DriverApiError.isRetryable` already returns false for it),
 * so [DriverErrorBanner] hides the button on its own in that case.
 */
private val AvailabilityUiState.driverNeverLoaded: Boolean
    get() = !isLoading && onlineSince == null && !isOnline && error != null

@Composable
private fun ConnectionLostBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(Radius.card))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column {
            Text(
                text = stringResource(R.string.availability_connection_lost),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.availability_connection_lost_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The biggest, most obvious control on the screen, and a custom shape rather
 * than `M3 Switch` on purpose: it needs a THIRD visual state — pending — that
 * a two-state switch cannot represent honestly (see
 * [AvailabilityViewModel.onToggle]'s doc on why optimism is a trap here).
 * On/off/pending are told apart by icon AND colour together (a power icon on a
 * muted surface when off; a filled check on the brand colour when on; a
 * spinner on that same muted surface while pending) so the state reads in
 * direct sun and for a colour-blind driver without reading the label at all.
 */
@Composable
private fun AvailabilitySwitch(
    isOnline: Boolean,
    isPending: Boolean,
    onlineSince: String?,
    onToggle: (Boolean) -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (isOnline && !isPending) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // `Motion.standard` is typed for Float (it drives numeric props like
        // alpha/offset elsewhere); a Color animation needs its own spring with
        // matching stiffness/damping rather than reusing that instance.
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        ),
        label = "availability_switch_color",
    )
    val content = if (isOnline && !isPending) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(TouchTarget.primary * 2)
            .clip(RoundedCornerShape(Radius.card))
            .background(container)
            // Disabled while pending — a second tap mid-flight must not queue a
            // second PATCH; the tap target itself refuses input here, on top of
            // the ViewModel's own re-entrancy guard.
            .clickable(enabled = !isPending) { onToggle(!isOnline) }
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isPending -> CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.xl),
                    color = content,
                )
                isOnline -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = content)
                else -> Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = content)
            }
            Text(
                text = stringResource(
                    when {
                        isPending -> R.string.availability_switch_pending
                        isOnline -> R.string.availability_switch_online
                        else -> R.string.availability_switch_offline
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = content,
            )
        }

        if (isOnline && !isPending && onlineSince != null) {
            ElapsedSince(isoInstant = onlineSince, color = content)
        }
    }
}

/**
 * A live-ticking `HH:MM:SS`, Latin-digit and direction-isolated ([ltr]) so it
 * never reorders inside an RTL layout — the same treatment the driver's own
 * phone number gets on `:feature:home`. Digits, not a translated "X minutes
 * ago" sentence: this is exactly the kind of value the project's UI rules
 * single out as always-Latin, and it sidesteps every language's plural rules
 * at once.
 */
@Composable
private fun ElapsedSince(isoInstant: String, color: Color) {
    val since = remember(isoInstant) { runCatching { Instant.parse(isoInstant) }.getOrNull() }
    if (since == null) return

    val elapsed by produceState(initialValue = Duration.between(since, Instant.now()), since) {
        while (true) {
            value = Duration.between(since, Instant.now())
            delay(1_000)
        }
    }

    Text(
        text = stringResource(R.string.availability_online_since, formatElapsed(elapsed).ltr()),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

private fun formatElapsed(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/**
 * Why no orders (decision 47) — shown whenever a [AvailabilityContextDto] has
 * been supplied. Every linked branch is named, exactly as the contract
 * intends: "I registered in Riyadh and see nothing" is usually the driver
 * standing in the wrong city or a branch that closed early, and this card is
 * the honest answer instead of a blank list.
 */
@Composable
private fun NoOrdersReasonCard(context: AvailabilityContextDto) {
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
                text = stringResource(R.string.availability_branches_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            context.branches.forEach { branch -> BranchRow(branch) }
        }
    }
}

@Composable
private fun BranchRow(branch: ContextBranchDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = branch.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (branch.isOpen) R.string.availability_branch_open else R.string.availability_branch_closed,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (branch.isOpen) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/**
 * A shimmer matching the eventual layout — never a centred spinner (the
 * project's mandatory-states rule, and this skill's Three Hard Rules).
 * `tween` here is the one place it is allowed: an ambient loop, not motion the
 * driver caused.
 */
@Composable
private fun AvailabilityLoadingSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(Spacing.lg)
                .clip(RoundedCornerShape(Radius.chip))
                .background(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.primary * 2)
                .clip(RoundedCornerShape(Radius.card))
                .background(brush),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.giant)
                .clip(RoundedCornerShape(Radius.card))
                .background(brush),
        )
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "availability_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.shimmerCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "availability_skeleton_shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(SHIMMER_TRAVEL * translate - SHIMMER_TRAVEL, 0f),
        end = Offset(SHIMMER_TRAVEL * translate, 0f),
    )
}

private const val SHIMMER_TRAVEL = 600f

// region Previews

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
private annotation class AvailabilityStatePreviews

private val previewBranches = listOf(
    ContextBranchDto(id = 1, name = "فرع العليا", companyName = "مطعم البيت السعيد", isOpen = true, distanceKm = 1.2, withinRadius = true),
    ContextBranchDto(id = 2, name = "فرع الملز", companyName = "مطعم البيت السعيد", isOpen = false, distanceKm = 4.0, withinRadius = true),
)

@AvailabilityStatePreviews
@Composable
private fun AvailabilityLoadingPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(isLoading = true),
            connectionLost = false,
            context = null,
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityOfflinePreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(isLoading = false, isOnline = false),
            connectionLost = false,
            context = AvailabilityContextDto(
                isOnline = false,
                branches = previewBranches,
                reason = "offline",
            ),
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityOnlineNothingPendingPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(
                isLoading = false,
                isOnline = true,
                onlineSince = Instant.now().minusSeconds(5_425).toString(),
            ),
            connectionLost = false,
            context = AvailabilityContextDto(
                isOnline = true,
                branches = previewBranches,
                reason = "nothing_pending",
            ),
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityPendingPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(isLoading = false, isOnline = false, isPending = true),
            connectionLost = false,
            context = null,
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityConnectionLostPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(
                isLoading = false,
                isOnline = true,
                onlineSince = Instant.now().minusSeconds(600).toString(),
            ),
            connectionLost = true,
            context = null,
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityHasActiveTripBlockPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(
                isLoading = false,
                isOnline = true,
                onlineSince = Instant.now().minusSeconds(1_200).toString(),
                error = DriverApiError.Api(
                    httpStatus = 409,
                    code = DriverErrorCode.HasActiveTrip,
                    message = null,
                    rawCode = "has_active_trip",
                ),
            ),
            connectionLost = false,
            context = null,
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

@AvailabilityStatePreviews
@Composable
private fun AvailabilityLoadErrorPreview() {
    DriverTheme {
        AvailabilityScreen(
            state = AvailabilityUiState(
                isLoading = false,
                isOnline = false,
                error = DriverApiError.Offline,
            ),
            connectionLost = false,
            context = null,
            warningSlot = {},
            onRetryLoad = {},
            onToggle = {},
        )
    }
}

// endregion
