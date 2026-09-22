package app.qrmenu.driver.account

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.datastore.UiScale
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Motion
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.BranchDto
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.LinkedCompanyDto
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.components.DriverRowDivider
import app.qrmenu.driver.ui.components.DriverScreenScaffold
import app.qrmenu.driver.ui.components.DriverSection
import app.qrmenu.driver.ui.components.DriverSettingRow
import app.qrmenu.driver.ui.text.ltr

/**
 * Screen — «حسابى». Replaces `:feature:home`'s tab: everything that screen
 * showed (who am I, which restaurants do I work for, decision 19/47) plus the
 * device-local settings a driver actually reaches for — language, app size,
 * the battery nudge, sign-out.
 */
@Composable
fun AccountRoute(
    onSignedOut: () -> Unit,
    /** Routes to the invite-code screen; that screen belongs to another module. */
    onEnterInviteCode: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val uiScale by viewModel.uiScale.collectAsStateWithLifecycle()

    val context = LocalContext.current

    AccountScreen(
        me = me,
        language = language,
        uiScale = uiScale,
        onRefresh = viewModel::refresh,
        onSelectLanguage = { code ->
            viewModel.selectLanguage(code)
            // 🔴 Storing the language is not applying it.
            //
            // The locale reaches the UI through `MainActivity.attachBaseContext`,
            // which runs once per Activity creation — so without this the driver
            // picks a language, the sheet closes, and every string on screen stays
            // exactly as it was until the process next dies. Which looks, entirely
            // reasonably, like the picker is broken.
            //
            // `AppCompatDelegate.setApplicationLocales` inside `LocaleManager.set`
            // does not cover it either: this app's Activity is a plain
            // `ComponentActivity`, not an `AppCompatActivity`, so there is no
            // delegate to act on it.
            //
            // The first-run picker already does exactly this at its own call site
            // (see `MainActivity`'s LanguageRoute). It belongs HERE rather than at
            // this route's call site so that a future screen reusing `AccountRoute`
            // cannot forget it — which is how this was missed the first time.
            (context as? Activity)?.recreate()
        },
        onSelectUiScale = viewModel::selectUiScale,
        onSignOut = { viewModel.signOut(onSignedOut) },
        onEnterInviteCode = onEnterInviteCode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(
    me: AccountUiState,
    language: String,
    uiScale: UiScale,
    onRefresh: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectUiScale: (UiScale) -> Unit,
    onSignOut: () -> Unit,
    onEnterInviteCode: () -> Unit,
) {
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    DriverScreenScaffold(title = stringResource(R.string.account_title)) {
        val driver = me.driver

        when {
            // Nothing has ever loaded yet — a shimmer skeleton shaped like the
            // content below it, never a centred spinner.
            me.isLoading -> AccountLoadingSkeleton()

            // The FIRST load failed and there is nothing to show underneath —
            // the inline banner is the whole screen's content.
            driver == null -> AccountErrorState(error = me.error, onRetry = onRefresh)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item { IdentityCard(driver = driver) }

                // A refresh that failed keeps whatever was already on screen —
                // the failure is a strip above the content, not a replacement.
                if (me.error != null) {
                    item { DriverErrorBanner(error = me.error, onRetry = onRefresh) }
                }

                item {
                    RestaurantsSection(
                        isEmpty = me.isEmpty,
                        restaurants = me.restaurants,
                        onEnterInviteCode = onEnterInviteCode,
                    )
                }

                item {
                    DriverSection(title = stringResource(R.string.account_language_row_label)) {
                        DriverSettingRow(
                            icon = Icons.Filled.Translate,
                            label = stringResource(R.string.account_language_row_label),
                            value = LanguageOption.forCode(language).nativeName,
                            onClick = { showLanguageSheet = true },
                            trailing = { ChevronRight() },
                        )
                    }
                }

                item {
                    UiScaleSection(
                        selected = uiScale,
                        onSelect = onSelectUiScale,
                    )
                }

                item {
                    val context = LocalContext.current
                    DriverSection {
                        DriverSettingRow(
                            icon = Icons.Filled.Battery3Bar,
                            label = stringResource(R.string.account_battery_row_label),
                            supporting = stringResource(R.string.account_battery_supporting),
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            },
                            trailing = { ChevronRight() },
                        )
                    }
                }

                item {
                    DriverSection {
                        DriverSettingRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            label = stringResource(R.string.account_sign_out),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showSignOutConfirm = true },
                        )
                    }
                }

                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }

    if (showLanguageSheet) {
        LanguagePickerSheet(
            selected = language,
            onSelect = {
                onSelectLanguage(it)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }
}

// region Identity

@Composable
private fun IdentityCard(driver: DriverDto) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            InitialMark(name = driver.name)

            Column {
                Text(
                    text = stringResource(R.string.account_greeting, driver.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = driver.phone.ltr(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A circular initial mark, the same shape language as an order card's restaurant mark. */
@Composable
private fun InitialMark(name: String) {
    Box(
        modifier = Modifier
            .size(ControlSize.platformLogo)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// endregion

// region Restaurants ("مطاعمى")

@Composable
private fun RestaurantsSection(
    isEmpty: Boolean,
    restaurants: List<RestaurantLinkDto>,
    onEnterInviteCode: () -> Unit,
) {
    DriverSection(title = stringResource(R.string.account_restaurants_title)) {
        if (isEmpty) {
            EmptyRestaurantsRow(onEnterInviteCode = onEnterInviteCode)
        } else {
            restaurants.forEachIndexed { index, link ->
                RestaurantRow(link = link)
                if (index != restaurants.lastIndex) {
                    DriverRowDivider()
                }
            }
        }
    }
}

/**
 * Zero restaurants is a real, expected state — a phone verified but not yet
 * invited anywhere. It says so plainly, inside the section it would
 * otherwise leave empty, and points at the fix (decision 47).
 */
@Composable
private fun EmptyRestaurantsRow(onEnterInviteCode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.account_empty_restaurants_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.account_empty_restaurants_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onEnterInviteCode, modifier = Modifier.heightIn(min = TouchTarget.compact)) {
            Icon(imageVector = Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(Spacing.xxs))
            Text(
                text = stringResource(R.string.account_enter_invite_code),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * One restaurant link, its [LinkStatus] visible as a colour+icon+text badge —
 * see `:feature:home`'s original doc for why the three must LOOK different,
 * not just read differently.
 */
@Composable
private fun RestaurantRow(link: RestaurantLinkDto) {
    val status = link.linkStatus()
    val branches = if (link.branches.isNotEmpty()) {
        stringResource(
            R.string.account_branches,
            link.branches.joinToString(separator = stringResource(R.string.account_list_separator)) { it.name },
        )
    } else {
        null
    }
    val hint = when (status) {
        LinkStatus.Invited -> stringResource(R.string.account_status_invited_hint)
        LinkStatus.Suspended -> stringResource(R.string.account_status_suspended_hint)
        LinkStatus.Active, LinkStatus.Unknown -> null
    }
    val supporting = listOfNotNull(branches, hint).joinToString(separator = "\n")

    DriverSettingRow(
        icon = Icons.Filled.Storefront,
        label = link.company.name,
        supporting = supporting.ifBlank { null },
        trailing = { StatusChip(status = status) },
    )
}

@Composable
private fun StatusChip(status: LinkStatus) {
    val (container, content, icon, label) = when (status) {
        LinkStatus.Active -> StatusChipStyle(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Filled.CheckCircle,
            label = stringResource(R.string.account_status_active),
        )
        LinkStatus.Invited -> StatusChipStyle(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.account_status_invited),
        )
        LinkStatus.Suspended -> StatusChipStyle(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Filled.Block,
            label = stringResource(R.string.account_status_suspended),
        )
        LinkStatus.Unknown -> StatusChipStyle(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Filled.WarningAmber,
            label = stringResource(R.string.account_status_active),
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
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(ControlSize.inlineIcon),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

private data class StatusChipStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
    val label: String,
)

// endregion

// region Language picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Text(
                text = stringResource(R.string.account_language_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))

            for (option in LanguageOption.all) {
                LanguageRow(
                    option = option,
                    isSelected = option.code == selected,
                    onSelect = { onSelect(option.code) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun LanguageRow(option: LanguageOption, isSelected: Boolean, onSelect: () -> Unit) {
    // Colour is the only thing that animates, and it does so on a spring:
    // the row must not move, resize or reflow under a thumb already
    // travelling towards the next one.
    val border by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(),
        label = "accountLanguageRowBorder",
    )
    val background by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(),
        label = "accountLanguageRowBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.compact)
            .padding(vertical = Spacing.xxs)
            .background(background, RoundedCornerShape(Radius.card))
            .border(Stroke.hairline, border, RoundedCornerShape(Radius.card))
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each name renders in its own direction so a driver who cannot read
        // the app's current language can still find their own.
        CompositionLocalProvider(
            LocalLayoutDirection provides if (option.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.nativeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (option.isRtl) TextAlign.Right else TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = option.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (option.isRtl) TextAlign.Right else TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// endregion

// region App size ("حجم التطبيق")

/**
 * The four fixed steps, rendered with a LIVE preview above them: a miniature
 * order-card-like block whose density visibly changes as the driver taps
 * through the options, so this is never a setting toggled blindly.
 */
@Composable
private fun UiScaleSection(selected: UiScale, onSelect: (UiScale) -> Unit) {
    DriverSection(title = stringResource(R.string.account_ui_scale_row_label)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            UiScalePreview(scale = selected)

            Spacer(Modifier.height(Spacing.md))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val steps = UiScale.entries.toList()
                steps.forEachIndexed { index, step ->
                    SegmentedButton(
                        selected = step == selected,
                        onClick = { onSelect(step) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = steps.size),
                        modifier = Modifier.heightIn(min = TouchTarget.compact),
                    ) {
                        Text(
                            text = uiScaleLabel(step),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun uiScaleLabel(scale: UiScale): String = when (scale) {
    UiScale.Small -> stringResource(R.string.account_ui_scale_small)
    UiScale.Default -> stringResource(R.string.account_ui_scale_default)
    UiScale.Large -> stringResource(R.string.account_ui_scale_large)
    UiScale.Largest -> stringResource(R.string.account_ui_scale_largest)
}

/**
 * A miniature stand-in for an order card, scaled ONLY inside this preview
 * (via [DriverTheme]'s own `uiScaleFactor`, not the live app density) — so a
 * driver sees the effect before committing, and the real screens below the
 * tab bar pick it up the moment they next compose against the persisted
 * value.
 */
@Composable
private fun UiScalePreview(scale: UiScale) {
    DriverTheme(uiScaleFactor = scale.factor) {
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(ControlSize.orderAvatar)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.account_ui_scale_preview_branch),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.account_ui_scale_preview_fee, "25 SAR".ltr()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// endregion

// region Sign out confirm

@Composable
private fun SignOutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_sign_out_confirm_title)) },
        text = { Text(stringResource(R.string.account_sign_out_confirm_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.account_sign_out_confirm_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_sign_out_confirm_cancel))
            }
        },
    )
}

// endregion

@Composable
private fun ChevronRight() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(ControlSize.inlineIcon),
    )
}

/** The inline failure surface for a first load that never produced any data. */
@Composable
private fun AccountErrorState(error: DriverApiError?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
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
 * A shimmer skeleton matching the identity card and the sections below it —
 * never a centred spinner. `tween` is used here on purpose: reserved for
 * exactly this, an ambient looping shimmer, never for motion the driver's own
 * action drives.
 */
@Composable
private fun AccountLoadingSkeleton() {
    val brush = rememberShimmerBrush()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ShimmerBlock(brush, widthFraction = 1f, height = Spacing.xxxl)
        ShimmerBlock(brush, widthFraction = 1f, height = Spacing.huge)
        ShimmerBlock(brush, widthFraction = 1f, height = Spacing.huge)
        ShimmerBlock(brush, widthFraction = 1f, height = Spacing.huge)
    }
}

@Composable
private fun ShimmerBlock(brush: Brush, widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(Radius.card))
            .background(brush),
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "account_skeleton_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.shimmerCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "account_skeleton_shimmer_translate",
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
private annotation class AccountStatePreviews

private val previewDriver = DriverDto(
    id = 1,
    name = "محمد العتيبي",
    phone = "+966501234567",
    isActive = true,
    isOnline = false,
)

private val previewBranch = BranchDto(id = 1, name = "فرع العليا")

@AccountStatePreviews
@Composable
private fun AccountScreenLoadingPreview() {
    DriverTheme {
        AccountScreen(
            me = AccountUiState(isLoading = true),
            language = "ar",
            uiScale = UiScale.Default,
            onRefresh = {},
            onSelectLanguage = {},
            onSelectUiScale = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@AccountStatePreviews
@Composable
private fun AccountScreenLoadedPreview() {
    DriverTheme {
        AccountScreen(
            me = AccountUiState(
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
            language = "ar",
            uiScale = UiScale.Default,
            onRefresh = {},
            onSelectLanguage = {},
            onSelectUiScale = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@AccountStatePreviews
@Composable
private fun AccountScreenEmptyPreview() {
    DriverTheme {
        AccountScreen(
            me = AccountUiState(isLoading = false, driver = previewDriver, restaurants = emptyList()),
            language = "en",
            uiScale = UiScale.Large,
            onRefresh = {},
            onSelectLanguage = {},
            onSelectUiScale = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@AccountStatePreviews
@Composable
private fun AccountScreenErrorPreview() {
    DriverTheme {
        AccountScreen(
            me = AccountUiState(
                isLoading = false,
                driver = null,
                error = DriverApiError.Api(
                    httpStatus = 500,
                    code = DriverErrorCode.ServerError,
                    message = null,
                    rawCode = "server_error",
                ),
            ),
            language = "ar",
            uiScale = UiScale.Default,
            onRefresh = {},
            onSelectLanguage = {},
            onSelectUiScale = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

@AccountStatePreviews
@Composable
private fun AccountScreenOfflinePreview() {
    DriverTheme {
        AccountScreen(
            me = AccountUiState(isLoading = false, driver = null, error = DriverApiError.Offline),
            language = "ar",
            uiScale = UiScale.Default,
            onRefresh = {},
            onSelectLanguage = {},
            onSelectUiScale = {},
            onSignOut = {},
            onEnterInviteCode = {},
        )
    }
}

// endregion
