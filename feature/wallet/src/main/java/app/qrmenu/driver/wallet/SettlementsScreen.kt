package app.qrmenu.driver.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Elevation
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke
import app.qrmenu.driver.designsystem.theme.TouchTarget
import app.qrmenu.driver.network.dto.SettlementDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.ui.components.DriverArt
import app.qrmenu.driver.ui.components.DriverEmptyState
import app.qrmenu.driver.ui.components.DriverErrorBanner
import app.qrmenu.driver.ui.text.ltr

/**
 * "When did I last hand cash over, and how much" — every settlement already
 * recorded, newest first (contract order). Read-only: the ✅ «تسوية» button
 * that clears a driver's balance is the RESTAURANT's action (CLAUDE.md's
 * «الدفتر المالى» — "المدير يُدخل المبلغ والاتجاه"), not this app's.
 */
@Composable
internal fun SettlementsScreen(
    state: SettlementsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold { insets ->
        Column(modifier = Modifier.padding(insets)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget.compact)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.wallet_settlements_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.wallet_settlements_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }

            when {
                state.isLoading -> WalletLoadingSkeleton()

                state.error != null -> Column(modifier = Modifier.padding(Spacing.lg)) {
                    DriverErrorBanner(error = state.error, onRetry = onRetry)
                }

                state.settlements.isEmpty() -> Column(modifier = Modifier.padding(Spacing.lg)) {
                    // Same ledger artwork as the book's own empty state
                    // (task brief: `DriverArt.Wallet` for an empty ledger) —
                    // a settlement history is the same kind of "nothing has
                    // happened here yet" as the book above it.
                    DriverEmptyState(
                        art = DriverArt.Wallet,
                        title = stringResource(R.string.wallet_settlements_empty_title),
                        body = stringResource(R.string.wallet_settlements_empty_body),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Same generous bottom clearance as the book's list
                    // (task brief's defect #1) — this list sits under the
                    // same tab bar.
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.md,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.settlements, key = SettlementDto::id) { settlement ->
                        SettlementRow(settlement)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementRow(settlement: SettlementDto) {
    val toDriver = settlement.direction == "to_driver"
    val currency = settlement.currency.orEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = Elevation.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        if (toDriver) R.string.wallet_settlement_to_driver else R.string.wallet_settlement_from_driver,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = (if (toDriver) "+" else "-").plus(formatMoney(settlement.amount, currency)).ltr(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (toDriver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            val periodFrom = settlement.periodFrom
            val periodTo = settlement.periodTo
            val period = if (periodFrom != null && periodTo != null) {
                val from = formatEntryDate(periodFrom) ?: periodFrom
                val to = formatEntryDate(periodTo) ?: periodTo
                stringResource(R.string.wallet_settlement_period, from, to)
            } else {
                formatEntryTimestamp(settlement.createdAt) ?: settlement.createdAt
            }
            Text(
                text = period.ltr(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            settlement.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private annotation class SettlementsStatePreviews

private val previewSettlement = SettlementDto(
    id = 1,
    companyId = 1,
    amount = 145.0,
    currency = "SAR",
    direction = "from_driver",
    periodFrom = "2026-09-01",
    periodTo = "2026-09-07",
    note = null,
    createdAt = "2026-09-08T10:00:00Z",
)

@SettlementsStatePreviews
@Composable
private fun SettlementsLoadingPreview() {
    DriverTheme { SettlementsScreen(state = SettlementsState(isLoading = true), onBack = {}, onRetry = {}) }
}

@SettlementsStatePreviews
@Composable
private fun SettlementsContentPreview() {
    DriverTheme {
        SettlementsScreen(
            state = SettlementsState(isLoading = false, settlements = listOf(previewSettlement)),
            onBack = {},
            onRetry = {},
        )
    }
}

@SettlementsStatePreviews
@Composable
private fun SettlementsEmptyPreview() {
    DriverTheme { SettlementsScreen(state = SettlementsState(isLoading = false), onBack = {}, onRetry = {}) }
}

@SettlementsStatePreviews
@Composable
private fun SettlementsErrorPreview() {
    DriverTheme {
        SettlementsScreen(
            state = SettlementsState(isLoading = false, error = DriverApiError.Offline),
            onBack = {},
            onRetry = {},
        )
    }
}

// endregion
