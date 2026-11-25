package clean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.workoutSkin

/**
 * The app's own number pad for typing one number (the weigh-in's weight, hour and minute): the workout pad's keys on the sheet's glass, the digits
 * with the point and delete under them and a Done key across the bottom. [onKey] gets a digit, `.` or `back`. A hardware keyboard works too: digits,
 * the point or a comma, Backspace, and Enter for Done.
 */
@Composable
fun TypePad(onKey: (String) -> Unit, onDone: () -> Unit, decimal: Boolean = true) {
    val haptic = rememberHaptic()
    val skin = workoutSkin()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    @Composable
    fun RowScope.Key(label: String, main: Boolean = false, side: Boolean = false, enabled: Boolean = true, spoken: String = label, onClick: () -> Unit) {
        val back = when { main -> MaterialTheme.colorScheme.primary; side -> skin.keyFill; else -> Color.Transparent }
        val front = if (main) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Box(
            // 54 dp less the air on each side: every key is at least 48 dp to the finger.
            Modifier.weight(1f).height(54.dp).padding(Space.xs).clip(cardShape()).background(back)
                .pressable(enabled, dip = 0.92f) { haptic(Haptic.Tick); onClick() }
                .semantics { role = Role.Button; if (spoken != label) contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = if (main || side) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = if (enabled) front else front.copy(alpha = 0.25f))
        }
    }
    Column(
        Modifier.fillMaxWidth().focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val c = event.utf16CodePoint.toChar()
            when {
                event.key == Key.Enter || event.key == Key.NumPadEnter -> { onDone(); true }
                event.key == Key.Backspace || event.key == Key.Delete -> { onKey("back"); true }
                c in '0'..'9' -> { onKey(c.toString()); true }
                (c == '.' || c == ',') && decimal -> { onKey("."); true }
                else -> false
            }
        },
    ) {
        for (line in listOf("123", "456", "789")) Row { for (d in line) Key(d.toString()) { onKey(d.toString()) } }
        Row {
            Key(".", side = true, enabled = decimal, spoken = "Decimal point") { onKey(".") }
            Key("0") { onKey("0") }
            Key("⌫", side = true, spoken = "Delete") { onKey("back") }
        }
        Row { Key("Done", main = true, onClick = onDone) }
    }
}
