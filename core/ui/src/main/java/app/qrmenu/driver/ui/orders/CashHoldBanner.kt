package app.qrmenu.driver.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Warning
import app.qrmenu.driver.network.dto.CashHoldBranchDto
import app.qrmenu.driver.network.dto.CashHoldDto
import app.qrmenu.driver.ui.R
import app.qrmenu.driver.ui.text.TripMoneyFormat

/**
 * "You are being throttled, and it is not a bug" — the card that tells a
 * driver why cash orders have gone quiet at a specific restaurant, instead of
 * letting them read the ordinary "nothing pending" line and stand there
 * waiting for something that structurally cannot arrive.
 *
 * ## Why this exists (the bug it closes)
 * The availability/orders screens already explain "no orders right now" via
 * [NoOrdersReason] — but [NoOrdersReason.NothingPending] means "everything is
 * fine, just wait", and a driver over a branch's cash ceiling is NOT in that
 * state: the branch will keep refusing to offer them cash orders until they
 * settle up, no matter how long they wait. Reusing the reassuring line here
 * would be lying to them. This card is deliberately a distinct, persistent
 * WARNING (amber, not the red of [DriverErrorBanner] and not the neutral grey
 * of the "nothing pending" card) — it is not a failure that just happened, and
 * it is not the calm "you're all caught up" state either.
 *
 * ## Why it is one row PER BRANCH, always
 * See [CashHoldDto]'s own doc: the hold is per COMPANY, the ceiling is per
 * BRANCH, so a driver can be well within limits at one linked branch while
 * blocked at another of the same company. Naming every held branch (never
 * collapsing to "N restaurants") is the same discipline
 * `AvailabilityScreen`'s `NoOrdersReasonCard` already applies to
 * `context.branches` — a driver comparing what the app says against what they
 * remember handing the restaurant needs the actual name and the actual
 * numbers, not a count. This also means the layout does not need a
 * singular/plural split: one branch is a list of one row.
 *
 * ## Why it lives in `:core:ui`
 * `AvailabilityContextDto.cashHold` is read by both the availability screen
 * (which explains why the switch is on but cash orders are not coming) and
 * the orders list (which explains why the "متاحة" tab is quiet). Same reason
 * [NoOrdersReason] sits here instead of in one feature module: the two
 * screens must describe the same hold identically, and a feature module
 * cannot depend on another feature module to share this.
 *
 * Renders nothing when [cashHold] is null or carries no branches — the normal
 * case for the overwhelming majority of drivers, and every day for the ones
 * who do get held. Callers pass `context.cashHold` (or `state.cashHold`)
 * straight through; there is no reason to make every call site repeat the
 * emptiness check.
 */
@Composable
fun CashHoldBanner(cashHold: CashHoldDto?, modifier: Modifier = Modifier) {
    val branches = cashHold.heldBranches()
    if (branches.isEmpty()) return

    // A dedicated amber tone, not `colorScheme.tertiary`: this theme maps
    // tertiary to [Success] (green) for the "trip in hand" accent bar, so
    // reusing it here would make a cash-limit warning read as good news.
    // Blended against the surface the same way `Theme.kt` derives its own
    // tonal pairs from `Success`/`Danger`, so it still tracks light/dark.
    val container = lerp(MaterialTheme.colorScheme.surface, Warning, 0.20f)
    val onContainer = lerp(MaterialTheme.colorScheme.onSurface, Warning, 0.65f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(Radius.card))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.cash_hold_banner_title),
            style = MaterialTheme.typography.titleSmall,
            color = onContainer,
        )
        Text(
            text = stringResource(R.string.cash_hold_banner_body),
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            branches.forEach { branch -> CashHoldBranchRow(branch, onContainer) }
        }
    }
}

/**
 * The one piece of non-Compose logic in this file, pulled out so it is
 * unit-testable on the JVM without a device: a `null` DTO (no context loaded
 * yet, or the endpoint predates this field) and an empty `branches` list
 * (the overwhelmingly common case — nobody is being held) collapse to the
 * same "nothing to show" answer.
 */
internal fun CashHoldDto?.heldBranches(): List<CashHoldBranchDto> = this?.branches.orEmpty()

/**
 * Name on its own line, amount on the next — never a `Row` with the amount
 * squeezed to one side. This project never truncates a restaurant's name
 * (CLAUDE.md §الممنوعات), and a branch name is free text a merchant typed;
 * putting it beside a fixed-width amount in a `Row` is exactly the layout
 * that ends up eliding it under real data.
 */
@Composable
private fun CashHoldBranchRow(
    branch: CashHoldBranchDto,
    onContainer: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = branch.branchName,
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer,
        )
        Text(
            text = stringResource(
                R.string.cash_hold_branch_amount,
                TripMoneyFormat.format(branch.cashOnHand, branch.currency),
                TripMoneyFormat.format(branch.limit, branch.currency),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = onContainer,
        )
    }
}
