package app.qrmenu.driver.location.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.location.R

/**
 * "دليل قتلة البطارية" — CLAUDE.md calls this "جزء من المنتج لا تفصيل", so it
 * ships as a real screen, not a settings footnote. No feature module owns this
 * surface yet, so it lives here beside the detection logic it renders.
 *
 * Content-only: the caller supplies navigation (a back action via [onDone]).
 */
@Composable
fun BatteryOptimizationScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryOptimizationGuide.currentState(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.battery_guide_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.battery_guide_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(state.manufacturer.displayNameRes),
            style = MaterialTheme.typography.titleMedium,
        )

        state.manufacturer.guideStepsRes.forEachIndexed { index, stepRes ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(stepRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Button(
            onClick = {
                context.startActivity(BatteryOptimizationGuide.requestIgnoreBatteryOptimizationsIntent(context))
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.primary),
        ) {
            Text(stringResource(R.string.battery_guide_open_system_dialog))
        }

        val manufacturerIntent = remember(state.manufacturer) {
            BatteryOptimizationGuide.manufacturerSettingsIntent(context, state.manufacturer)
        }
        if (manufacturerIntent != null) {
            OutlinedButton(
                onClick = { context.startActivity(manufacturerIntent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget.primary),
            ) {
                Text(stringResource(R.string.battery_guide_open_manufacturer_settings))
            }
        }

        OutlinedButton(
            onClick = {
                state = BatteryOptimizationGuide.currentState(context)
                if (state.isIgnoringBatteryOptimizations) onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.primary),
        ) {
            Text(stringResource(R.string.battery_guide_done))
        }
    }
}
