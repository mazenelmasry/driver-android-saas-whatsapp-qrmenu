package app.qrmenu.driver.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
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
import app.qrmenu.driver.designsystem.theme.Elevation
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
import app.qrmenu.driver.ui.error.localized
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
    /**
     * 🔴 Not wired at this route's current call site (`SignedInScreen`'s
     * `AccountRoute(...)` passes neither) — the other three tabs all get a
     * bell, this one doesn't, and a driver who has learned "the bell lives in
     * the top corner" finds it missing on the one tab out of four where they
     * expect it least. The scaffold call below is ready for both the moment
     * `:app` passes them; until then this screen renders no bell, same as
     * today.
     */
    unreadNotifications: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    /**
     * The build's own version name/code (e.g. "1.4.2" / 10402), for the quiet
     * footer row at the bottom of the screen. `:feature:account` has no
     * `BuildConfig` of its own with the app's version in it — only `:app`
     * does — so this is handed in rather than read locally. `null` renders no
     * footer at all.
     */
    appVersionName: String? = null,
    appVersionCode: Int? = null,
    /**
     * The platform's published legal/support links (from `PlatformBrandingStore`,
     * cached from `GET driver/branding`). Handed in rather than read here for
     * the same reason [appVersionName] is: `:feature:account` must not gain a
     * `:core:datastore`-store-specific read of its own, so `:app` is the only
     * place that knows where [app.qrmenu.driver.datastore.PlatformBranding]
     * lives. `null`/blank renders no row for that link.
     */
    privacyUrl: String? = null,
    termsUrl: String? = null,
    helpUrl: String? = null,
    supportWhatsApp: String? = null,
    supportEmail: String? = null,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val uiScale by viewModel.uiScale.collectAsStateWithLifecycle()
    val deletionRequest by viewModel.deletionRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current

    AccountScreen(
        me = me,
        language = language,
        uiScale = uiScale,
        unreadNotifications = unreadNotifications,
        onOpenNotifications = onOpenNotifications,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        privacyUrl = privacyUrl,
        termsUrl = termsUrl,
        helpUrl = helpUrl,
        supportWhatsApp = supportWhatsApp,
        supportEmail = supportEmail,
        deletionRequest = deletionRequest,
        onRequestAccountDeletion = viewModel::requestAccountDeletion,
        onDismissDeletionError = viewModel::dismissDeletionError,
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
    unreadNotifications: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    appVersionName: String? = null,
    appVersionCode: Int? = null,
    privacyUrl: String? = null,
    termsUrl: String? = null,
    helpUrl: String? = null,
    supportWhatsApp: String? = null,
    supportEmail: String? = null,
    deletionRequest: DeletionRequestUiState = DeletionRequestUiState(isLoading = false),
    onRequestAccountDeletion: (String?) -> Unit = {},
    onDismissDeletionError: () -> Unit = {},
) {
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    DriverScreenScaffold(
        title = stringResource(R.string.account_title),
        unreadNotifications = unreadNotifications,
        onOpenNotifications = onOpenNotifications,
    ) {
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

                // A single, untitled row — same rhythm as the battery/sign-out
                // section below it. The row's own label already says
                // "Language"; a section heading repeating that word above it
                // was the section-heading inconsistency this screen used to
                // have (a one-row section titled with the row's own label,
                // next to multi-row sections that earn a title).
                item {
                    DriverSection {
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

                // Battery and sign-out are the two loose device-local rows
                // this screen has, with no shared theme beyond "single tap,
                // no sub-content" — grouped into one untitled section, same
                // shape as the language row above, instead of each getting
                // its own card with its own gap.
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
                        DriverRowDivider()
                        DriverSettingRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            label = stringResource(R.string.account_sign_out),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showSignOutConfirm = true },
                        )
                    }
                }

                item {
                    LegalSupportSection(
                        privacyUrl = privacyUrl,
                        termsUrl = termsUrl,
                        helpUrl = helpUrl,
                        supportWhatsApp = supportWhatsApp,
                        supportEmail = supportEmail,
                    )
                }

                item {
                    DeleteAccountSection(
                        deletionRequest = deletionRequest,
                        onClick = { showDeleteAccountConfirm = true },
                    )
                }

                item { AppVersionFooter(versionName = appVersionName, versionCode = appVersionCode) }

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

    // The dialog closes itself the moment a request successfully lands —
    // staying open on top of a row that has already flipped to "pending
    // review" would ask the driver to confirm a second time for nothing.
    LaunchedEffect(deletionRequest.request, deletionRequest.isSubmitting) {
        if (showDeleteAccountConfirm && deletionRequest.request != null && !deletionRequest.isSubmitting) {
            showDeleteAccountConfirm = false
        }
    }

    if (showDeleteAccountConfirm) {
        DeleteAccountConfirmDialog(
            isSubmitting = deletionRequest.isSubmitting,
            error = deletionRequest.error,
            onConfirm = { reason -> onRequestAccountDeletion(reason) },
            onDismiss = {
                showDeleteAccountConfirm = false
                onDismissDeletionError()
            },
        )
    }
}

// region Identity

@Composable
private fun IdentityCard(driver: DriverDto) {
    // The one card on this screen the driver's own identity lives in — it
    // gets the same lift an order card gets ([Elevation.card]), not the flat
    // hairline-only fill every row below it uses. That's what makes it read
    // as the top of the screen rather than as the first row in a long list.
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elevation.card,
        tonalElevation = Elevation.card,
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
 *
 * 🔴 Known defect fixed here: this used to render with no caption at all — a
 * card that says "فرع العليا / الأجرة 25 SAR" with nothing marking it as
 * fake reads as a live order a driver cannot open. The badge in the top
 * corner is the fix: it sits ON the card, in the driver's eye path before
 * the branch name, in a colour that never appears on a real order card.
 *
 * The empty circle was the other half of the same problem — a blank tinted
 * disc where an avatar failed to load. It now carries the sample branch
 * name's own initial, the same mark [IdentityCard] and a real order card
 * both use, so at a glance it reads as "a card" rather than "a broken one".
 */
@Composable
private fun UiScalePreview(scale: UiScale) {
    DriverTheme(uiScaleFactor = scale.factor) {
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = Elevation.card,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm)
                        .padding(top = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    val sampleBranchName = stringResource(R.string.account_ui_scale_preview_branch)

                    Box(
                        modifier = Modifier
                            .size(ControlSize.orderAvatar)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = sampleBranchName.trim().firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sampleBranchName,
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

                PreviewBadge(modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs))
            }
        }
    }
}

/** The "this is a sample, not a real order" label pinned to [UiScalePreview]'s corner. */
@Composable
private fun PreviewBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(ControlSize.inlineIcon),
        )
        Text(
            text = stringResource(R.string.account_ui_scale_preview_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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

// region Legal & support

/**
 * The Google-Play-required links: privacy policy, terms, help, and a way to
 * reach support — each served by the admin panel via `GET driver/branding`
 * (see [AccountRoute]'s doc on why they arrive as parameters). Every row is
 * independently optional; a deployment that has configured none of them
 * renders nothing here at all, not an empty framed section.
 */
@Composable
private fun LegalSupportSection(
    privacyUrl: String?,
    termsUrl: String?,
    helpUrl: String?,
    supportWhatsApp: String?,
    supportEmail: String?,
) {
    val context = LocalContext.current

    data class LegalRow(val icon: ImageVector, val label: String, val onClick: () -> Unit)

    val contactUrl = when {
        !supportWhatsApp.isNullOrBlank() -> "https://wa.me/$supportWhatsApp"
        !supportEmail.isNullOrBlank() -> "mailto:$supportEmail"
        else -> null
    }

    val rows = buildList {
        if (!privacyUrl.isNullOrBlank()) {
            add(
                LegalRow(
                    icon = Icons.Filled.PrivacyTip,
                    label = stringResource(R.string.account_privacy_policy),
                    onClick = { openUrl(context, privacyUrl) },
                ),
            )
        }
        if (!termsUrl.isNullOrBlank()) {
            add(
                LegalRow(
                    icon = Icons.Filled.Gavel,
                    label = stringResource(R.string.account_terms_of_service),
                    onClick = { openUrl(context, termsUrl) },
                ),
            )
        }
        if (!helpUrl.isNullOrBlank()) {
            add(
                LegalRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    label = stringResource(R.string.account_help),
                    onClick = { openUrl(context, helpUrl) },
                ),
            )
        }
        if (contactUrl != null) {
            add(
                LegalRow(
                    icon = Icons.Filled.SupportAgent,
                    label = stringResource(R.string.account_contact_support),
                    onClick = { openUrl(context, contactUrl) },
                ),
            )
        }
    }

    if (rows.isEmpty()) return

    DriverSection(title = stringResource(R.string.account_legal_section_title)) {
        rows.forEachIndexed { index, row ->
            DriverSettingRow(
                icon = row.icon,
                label = row.label,
                onClick = row.onClick,
                trailing = { ChevronRight() },
            )
            if (index != rows.lastIndex) {
                DriverRowDivider()
            }
        }
    }
}

/**
 * Opens a URL (http(s)/`wa.me`/`mailto:`) in whatever app handles it.
 *
 * A phone with no browser installed, or no WhatsApp, throws
 * [ActivityNotFoundException] — caught here rather than left to crash the
 * screen a driver is working from over a broken link.
 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // Nothing else to do — there is no in-app fallback for "no app can
        // open this link", and a toast here would be one more translated
        // string for a case the row's own icon already made optional.
    }
}

// endregion

// region Delete account

/**
 * The final, visually subordinate row for the Play-Store-required
 * account-deletion request — quiet like [AppVersionFooter] below it, not
 * alarmed like [SignOutConfirmDialog]'s confirm button, even though it is
 * destructive: this is a REQUEST an admin reviews, not an instant action.
 */
@Composable
private fun DeleteAccountSection(deletionRequest: DeletionRequestUiState, onClick: () -> Unit) {
    DriverSection {
        if (deletionRequest.isPending) {
            DriverSettingRow(
                icon = Icons.Filled.DeleteForever,
                label = stringResource(R.string.account_delete_pending_label),
                supporting = stringResource(R.string.account_delete_pending_supporting),
            )
        } else {
            DriverSettingRow(
                icon = Icons.Filled.DeleteForever,
                label = stringResource(R.string.account_delete_row_label),
                tint = MaterialTheme.colorScheme.error,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun DeleteAccountConfirmDialog(
    isSubmitting: Boolean,
    error: DriverApiError?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.account_delete_confirm_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.account_delete_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.account_delete_confirm_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = error.localized(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ControlSize.inlineIcon),
                        strokeWidth = Stroke.hairline,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text(stringResource(R.string.account_delete_confirm_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.account_delete_confirm_cancel))
            }
        },
    )
}

// endregion

// region App version

/**
 * A quiet footer, not a setting: the build's version name and code, for
 * Play Store support requests ("what version are you on?"). Centred,
 * `bodySmall`, muted — nothing here invites a tap.
 *
 * `:feature:account` has no `BuildConfig` of its own carrying the app's
 * version, so both values are handed in from `:app` (see [AccountRoute]).
 * Renders nothing at all when either is missing, rather than a placeholder
 * — an unwired footer should be invisible, not wrong.
 */
@Composable
private fun AppVersionFooter(versionName: String?, versionCode: Int?) {
    if (versionName == null || versionCode == null) return

    // Plain string interpolation, not resource `%d` formatting — Kotlin's
    // `Int.toString()` never locale-converts digits, but the digits still
    // sit inside an Arabic/Urdu sentence, so the whole value is isolated
    // LTR the same way a phone number or a fee is (see `BidiText`).
    val versionLabel = "$versionName ($versionCode)".ltr()

    Text(
        text = stringResource(R.string.account_app_version, versionLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md),
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
