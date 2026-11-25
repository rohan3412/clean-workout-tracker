package clean.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/** The speed (dp per second) that makes a slide along a row a flick. */
const val SLIDE_FLICK = 600

/**
 * The dock's slide, for every row of equal slots that is slid along (the dock, Round 29 to 31; the jump chips of a Settings page, Round 37).
 *
 * It is relative: it starts from the slot that was current ([first]), not the one under the finger. The selection is that slot plus the drag in [slot] widths,
 * rounded and clamped, so nothing changes before half a slot; [onFinger] reports the finger's position in slots (NaN once it lifts), [onStep] each
 * new selection with a [Haptic.Tick], and [onEnd] the selection when a slide that did move is over. A flick (faster than [SLIDE_FLICK]) that did not
 * leave the start slot moves one slot on, in its direction. A tap is left to what is under it; a start that is vertical does nothing and is not consumed.
 * With [onHold] a press that stays still for the long-press time calls it with the finger's place (a peek), the rest of that touch is the hold's, and
 * [onHoldEnd] runs on release.
 */
@Composable
fun Modifier.slideAlong(
    key: Any?, count: Int, first: () -> Int, slot: PointerInputScope.() -> Float,
    onFinger: (Float) -> Unit, onStep: (Int) -> Unit, onEnd: (Int) -> Unit,
    onHold: ((Offset) -> Unit)? = null, onHoldEnd: () -> Unit = {},
): Modifier {
    val haptic = rememberHaptic()
    val firstNow by rememberUpdatedState(first)
    val fingerNow by rememberUpdatedState(onFinger)
    val stepNow by rememberUpdatedState(onStep)
    val endNow by rememberUpdatedState(onEnd)
    val holdNow by rememberUpdatedState(onHold)
    val holdEndNow by rememberUpdatedState(onHoldEnd)
    val hasHold = onHold != null
    return pointerInput(key, count, hasHold) {
        val slop = viewConfiguration.touchSlop
        val flick = SLIDE_FLICK.dp.toPx()
        val longPress = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = slot().coerceAtLeast(1f)
            val start = firstNow().coerceIn(0, (count - 1).coerceAtLeast(0))
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var sliding = false
            var held = false
            var at = start
            var last = down.uptimeMillis
            try {
                while (true) {
                    val remaining = longPress - (last - down.uptimeMillis)
                    val event = if (hasHold && !sliding && !held) {
                        withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                    } else awaitPointerEvent(if (held) PointerEventPass.Initial else PointerEventPass.Main)
                    if (event == null) {
                        // Still for the long-press time: a hold.
                        held = true
                        haptic(Haptic.Tick)
                        holdNow?.invoke(down.position)
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    last = change.uptimeMillis
                    if (held) {
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) break
                        continue
                    }
                    if (change.isConsumed && !sliding) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (sliding && at == start) {
                            val v = tracker.calculateVelocity().x
                            val to = (start + if (v > flick) 1 else if (v < -flick) -1 else 0).coerceIn(0, count - 1)
                            if (to != start) { haptic(Haptic.Tick); at = to; stepNow(to) }
                        }
                        break
                    }
                    val d = change.position - down.position
                    if (!sliding) {
                        if (abs(d.y) > slop && abs(d.y) > abs(d.x)) break
                        if (abs(d.x) > slop) sliding = true
                    }
                    if (sliding) {
                        change.consume()
                        val pos = (start + d.x / width).coerceIn(0f, count - 1f)
                        fingerNow(pos)
                        val index = roundHalfUp(pos)
                        if (index != at) { at = index; haptic(Haptic.Tick); stepNow(index) }
                    }
                }
            } finally {
                fingerNow(Float.NaN)
                if (held) holdEndNow()
                else if (sliding) endNow(at)
            }
        }
    }
}
