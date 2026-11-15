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
 * panels over the list), made from the look so each preset's own style reaches into the workout. Light, Dark and AMOLED give the values the
 * screens always had; Mono draws paper and ink outlines; Linen grey tiles, a faint orange tint and a black tick.
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
    val ink = scheme.onSurface
    val paper = scheme.background
    if (mono) return WorkoutSkin(
        boxFill = paper, boxBorder = ink, lineWidth = 1.dp, focusBorder = ink, focusWidth = 2.dp,
        rowDone = ink.copy(alpha = 0.10f), rowNext = ink.copy(alpha = 0.05f), rowIdleHalf = Color.Transparent,
        tickIdle = paper, tickIdleBorder = ink, tickDone = ink, checkDone = paper, checkIdle = ink.copy(alpha = 0.6f),
        padFill = paper, padBorder = ink, keyFill = paper, keyBorder = ink,
        panelBorder = BorderStroke(1.dp, ink),
    )
    if (inkMainButton) return WorkoutSkin(
        boxFill = tile, boxBorder = Color.Transparent, lineWidth = 1.5.dp, focusBorder = scheme.primary, focusWidth = 1.5.dp,
        rowDone = orange.copy(alpha = 0.12f), rowNext = orange.copy(alpha = 0.06f), rowIdleHalf = Color.Transparent,
        tickIdle = tile, tickIdleBorder = Color.Transparent, tickDone = text, checkDone = onInk, checkIdle = scheme.onSurfaceVariant,
        padFill = scheme.surfaceContainer, padBorder = Color.Transparent, keyFill = scheme.surfaceVariant, keyBorder = Color.Transparent,
        panelBorder = null,
    )
    return WorkoutSkin(
        boxFill = scheme.surfaceVariant, boxBorder = Color.Transparent, lineWidth = 1.5.dp, focusBorder = scheme.primary, focusWidth = 1.5.dp,
        rowDone = meaning.done.copy(alpha = 0.24f), rowNext = if (sheetPage) tile else scheme.primary.copy(alpha = 0.30f), rowIdleHalf = scheme.surfaceVariant.copy(alpha = 0.35f),
        tickIdle = scheme.surfaceVariant, tickIdleBorder = Color.Transparent, tickDone = meaning.done, checkDone = Color.Black, checkIdle = scheme.onSurfaceVariant,
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
