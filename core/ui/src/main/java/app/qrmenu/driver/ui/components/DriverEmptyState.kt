package app.qrmenu.driver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.qrmenu.driver.designsystem.theme.Spacing

/**
 * What a screen shows when it has nothing — the state a waiting driver is in
 * for most of a shift, and therefore the one that decides what they think of
 * the app.
 *
 * 🔴 It is a PICTURE, a sentence and — where there is one — a way out. The
 * grey two-line box this replaces told the driver nothing was there and then
 * left the other two thirds of the screen blank, which reads as a failure to
 * load rather than as a state. Every screen's emptiness now uses this one
 * composable so "nothing here" looks the same everywhere, which is most of
 * what makes an app feel built rather than assembled.
 *
 * [body] stays optional and [action] doubly so: an empty state with a button
 * that does not lead anywhere useful is worse than one without.
 */
@Composable
fun DriverEmptyState(
    art: DriverArt,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DriverArtwork(art)

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = Spacing.xs),
            ) {
                Text(text = actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
