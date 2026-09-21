package app.qrmenu.driver.onboarding.language

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import android.content.res.Configuration
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.common.locale.SupportedLocales
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.onboarding.R

/**
 * Screen 1 — the language picker (decisions 8 and 9).
 *
 * It is the FIRST thing a new driver sees, before login, because the app must
 * not guess: guessing from the phone's system language would hand an Urdu- or
 * Bengali-speaking driver an Arabic app on a Saudi handset, and there is no way
 * back out of a screen you cannot read.
 *
 * Nothing here loads from the network, so the four mandatory states have nothing
 * to describe on this screen — see [LanguageViewModel].
 */
@Composable
fun LanguageRoute(
    onContinue: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LanguageScreen(
        selected = selected,
        onSelect = viewModel::select,
        onContinue = {
            viewModel.confirm()
            onContinue()
        },
    )
}

/**
 * Previews the highlighted language LIVE — without changing the app's language.
 *
 * 🔴 The obvious implementation (apply the locale globally on every tap) is
 * wrong twice over: it commits a half-made choice before the driver confirms,
 * and on this activity it does not even repaint, because the language is read
 * once in `attachBaseContext`. So the screen resolves ITS OWN strings against
 * the selected locale instead, leaving the app untouched until Continue.
 *
 * The result is what the screen is for: tapping "বাংলা" turns the title, the
 * hint and the button into Bengali immediately, so the driver confirms
 * something they can already read rather than something promised.
 */
@Composable
private fun LocalizedTo(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply {
        setLocale(SupportedLocales.buildLatinLocale(language))
    }
    val localized = remember(language, context) { context.createConfigurationContext(configuration) }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides
            if (SupportedLocales.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

@Composable
internal fun LanguageScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) = LocalizedTo(selected) {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.size(Spacing.xxl))

            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = stringResource(R.string.language_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(Spacing.xl))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for (option in LanguageOption.all) {
                    LanguageRow(
                        option = option,
                        isSelected = option.code == selected,
                        onSelect = { onSelect(option.code) },
                    )
                }
                Spacer(Modifier.size(Spacing.md))
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    // A primary CTA is never below the trip-action floor, even
                    // on a screen a driver only sees once: the same thumb, the
                    // same conditions.
                    .heightIn(min = TouchTarget.primary),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Text(
                    text = stringResource(R.string.language_continue),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Composable
private fun LanguageRow(
    option: LanguageOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    // Colour is the only thing that animates, and it does so on a spring: the
    // row must not move, resize or reflow under a thumb that is already
    // travelling towards the next one.
    val border by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(),
        label = "languageRowBorder",
    )
    val background by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(),
        label = "languageRowBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.primary)
            .background(background, RoundedCornerShape(Radius.card))
            .border(
                width = if (isSelected) Stroke.selected else Stroke.hairline,
                color = border,
                shape = RoundedCornerShape(Radius.card),
            )
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 🔴 Each name renders in ITS OWN direction, not the app's. Urdu shown
        // while the app is in English must still read right-to-left, or the
        // driver it is there for cannot recognise their own language.
        CompositionLocalProvider(
            LocalLayoutDirection provides if (option.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.nativeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (option.isRtl) TextAlign.Right else TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = option.englishName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (option.isRtl) TextAlign.Right else TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        SelectionDot(isSelected = isSelected)
    }
}

/**
 * A filled dot rather than a Material `RadioButton`: the stock control is drawn
 * at 20dp and cannot be enlarged without also enlarging its own 48dp touch
 * target, which would fight the row's. The whole ROW is the target here, so this
 * is decoration and is marked as such for TalkBack — the row already announces
 * its selected state through [Role.RadioButton].
 */
@Composable
private fun SelectionDot(isSelected: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "languageSelectionDot",
    )
    Box(
        modifier = Modifier
            .size(ControlSize.selectionDot)
            .background(fill, CircleShape)
            .border(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Preview(name = "ar", locale = "ar", showBackground = true)
@Preview(name = "en", locale = "en", showBackground = true)
@Preview(name = "ur", locale = "ur", showBackground = true)
@Preview(name = "bn", locale = "bn", showBackground = true)
@Preview(name = "hi", locale = "hi", showBackground = true)
@Preview(name = "ar dark", locale = "ar", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "en dark", locale = "en", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LanguageScreenPreview() {
    DriverTheme {
        LanguageScreen(selected = "ar", onSelect = {}, onContinue = {})
    }
}
