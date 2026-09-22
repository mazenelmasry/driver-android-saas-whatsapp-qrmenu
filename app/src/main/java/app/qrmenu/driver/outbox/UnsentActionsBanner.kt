package app.qrmenu.driver.outbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.qrmenu.driver.R
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing

/**
 * "Some of what you did has not reached the restaurant yet."
 *
 * 🔴 Deliberately NOT an error, and deliberately not dismissible.
 *
 * Not an error, because nothing has gone wrong from the driver's side: they
 * delivered the order, the queue held the record, and the phone will send it.
 * Alarming them would push them to re-press "سلّمت" on an order the server has
 * already been told about — the one action that could genuinely confuse the
 * ledger.
 *
 * Not dismissible, because it disappears on its own the moment the queue
 * drains, and the only case where it lingers is the one the driver most needs
 * to see: `OutboxFlushWorker` hit its retry ceiling and stopped asking. The
 * rows are still there — they are money the driver is carrying — and this is
 * the only surface that says so.
 *
 * Renders nothing when the queue is empty, which is almost always.
 */
@Composable
fun UnsentActionsBanner(
    modifier: Modifier = Modifier,
    viewModel: UnsentActionsViewModel = hiltViewModel(),
) {
    val pending by viewModel.pendingCount.collectAsState()
    if (pending <= 0) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(ControlSize.inlineIcon),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    text = pluralStringResource(R.plurals.unsent_actions_title, pending, pending),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.unsent_actions_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
