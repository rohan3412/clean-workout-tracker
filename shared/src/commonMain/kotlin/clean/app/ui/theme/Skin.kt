package clean.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The fills, outlines and ticks of the workout in progress (set boxes, tick boxes, the next and done rows, the number pad's keys, the small
 * panels over the list), made from the look. Dark and Light share one shape (since 1.1.0 there are no other looks); the colours come from the scheme.
 */
@Immutable
class WorkoutSkin(
    /** A weight or reps box: its fill, its outline when idle (transparent for none), that outline's width, and the outline when it holds the pad. */
    val boxFill: Color, val boxBorder: Color, val lineWidth: Dp, val focusBorder: Color, val focusWidth: Dp,
    /** A set row's tint when it is done, when it is the next to do, and a half of a pair's tint when it is neither. */
    val rowDone: Color, val rowNext: Color, val rowIdleHalf: Color,
    /** The tick box: unticked fill and outline, ticked fill, and the check's colour on each. */
    val tickIdle: Color, val tickIdleBorder: Color, val tickDone: Color, val checkDone: Color, val checkIdle: Color,
    /** The number pad: its surface and the outline along its top edge, a key's fill (a transparent one for the digits) and outline. */
    val padFill: Color, val padBorder: Color, val keyFill: Color, val keyBorder: Color,
    /** An outline for the small panels over the list (the undo bar, the rest card and pill); null for none. */
    val panelBorder: BorderStroke?,
)

/** The workout skin of this look, given the scheme and meaning colours it is drawn in (so the negative look follows). */
fun Look.workoutSkin(scheme: ColorScheme, meaning: CleanColors): WorkoutSkin {
    // The current row (round 28) is the accent washed over the page with a leading bar and its number in the accent; a done row is a fainter wash without them.
    val boxStep = if (dark) over(tint, 0.15f, page) else over(accent.second, 0.16f, Color.White)
    return WorkoutSkin(
        boxFill = boxStep, boxBorder = Color.Transparent, lineWidth = 1.5.dp, focusBorder = scheme.primary, focusWidth = 1.5.dp,
        rowDone = scheme.primary.copy(alpha = 0.08f), rowNext = scheme.primary.copy(alpha = if (dark) 0.11f else 0.09f), rowIdleHalf = scheme.surfaceVariant.copy(alpha = 0.35f),
        tickIdle = scheme.surfaceVariant, tickIdleBorder = Color.Transparent, tickDone = scheme.primary, checkDone = ink, checkIdle = scheme.onSurfaceVariant,
        padFill = scheme.surfaceContainer, padBorder = Color.Transparent, keyFill = scheme.surfaceVariant, keyBorder = Color.Transparent,
        panelBorder = null,
    )
}

/** The workout skin under the current look. */
@Composable
fun workoutSkin(): WorkoutSkin {
    val look = LocalLook.current
    val scheme = MaterialTheme.colorScheme
    val meaning = LocalCleanColors.current
    return remember(look, scheme, meaning) { look.workoutSkin(scheme, meaning) }
}
