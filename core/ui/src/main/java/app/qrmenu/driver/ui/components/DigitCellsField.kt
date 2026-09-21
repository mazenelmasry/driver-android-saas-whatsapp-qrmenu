package app.qrmenu.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.qrmenu.driver.designsystem.theme.ControlSize
import app.qrmenu.driver.designsystem.theme.Radius
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.designsystem.theme.Stroke

/**
 * A short numeric code entered as SEPARATE CELLS — one box per digit — rather
 * than as an ordinary text field.
 *
 * ## Why cells, and not a single box
 * Every code in this app is a fixed, known length: six digits from the SMS, four
 * for the secret code. Cells say that length before the driver types a
 * character, show at a glance how many are still missing, and make a mistyped
 * digit visible in the position it happened. A plain field says none of that: a
 * driver glancing at it mid-glance, in a car, cannot tell "4 of 6" from "done".
 *
 * ## Masking is a choice, and the two codes differ
 * 🔴 The SMS code is **never masked**. It was just read aloud by the phone or is
 * sitting in the notification shade two centimetres above the field — hiding it
 * protects nothing and only stops the driver checking what they typed against
 * what they were sent.
 *
 * The **secret code is masked**, with a reveal toggle ([onToggleMask]), because
 * it is a lasting credential that someone standing at the branch counter could
 * read over a shoulder — and the toggle exists because "wrong code" and "I have
 * forgotten my code" look identical behind four dots.
 *
 * The whole row is pinned left-to-right: a code is dialled, not read as prose.
 */
@Composable
fun DigitCellsField(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isMasked: Boolean = false,
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    BasicTextField(
        // Held as TextFieldValue with the caret pinned past the last digit, so
        // tapping a cell in the middle cannot drop the caret there and have the
        // next keypress insert into the middle of a code.
        value = TextFieldValue(text = value, selection = TextRange(value.length)),
        onValueChange = { typed -> onValueChange(typed.text.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        // The real field is invisible: it only carries the keyboard and the
        // caret. Everything the driver sees is drawn by the decoration below.
        cursorBrush = SolidColor(Color.Transparent),
        interactionSource = remember { MutableInteractionSource() },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.isFocused },
        decorationBox = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    for (index in 0 until length) {
                        DigitCell(
                            digit = value.getOrNull(index),
                            isMasked = isMasked,
                            // The cell the next keypress will fill. Only lit
                            // while the field actually has focus, so a filled-in
                            // code at rest does not look like it is waiting.
                            isActive = hasFocus && index == value.length.coerceAtMost(length - 1),
                            isError = isError,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DigitCell(
    digit: Char?,
    isMasked: Boolean,
    isActive: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isActive -> MaterialTheme.colorScheme.primary
            digit != null -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(),
        label = "digitCellBorder",
    )

    Box(
        modifier = modifier
            .height(ControlSize.digitCell)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
            .border(
                width = if (isActive || isError) Stroke.selected else Stroke.hairline,
                color = border,
                shape = RoundedCornerShape(Radius.card),
            )
            // The row as a whole is one field to a screen reader; announcing
            // six empty boxes individually is noise.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit == null -> Unit
            isMasked -> Box(
                modifier = Modifier
                    .size(ControlSize.maskDot)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            )
            else -> Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

