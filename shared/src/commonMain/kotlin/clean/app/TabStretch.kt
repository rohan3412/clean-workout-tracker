package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * An indicator that glides and stretches from one tab to the next (the tab bar's pill, the exercise page's underline): its leading edge
 * goes first and the trailing edge follows, so it stretches and then settles. On a swipe it follows the finger instead, through the
 * same stretch, so a swipe and a tap look alike.
 */

/** The indicator now: it runs from tab [from] to tab [to]; each edge is that share of the way between the two tabs' own edges. */
class Span(val from: Int, val to: Int, val leftT: Float, val rightT: Float) {
    fun left(leftOf: (Int) -> Float) = leftOf(from) + (leftOf(to) - leftOf(from)) * leftT
    fun right(rightOf: (Int) -> Float) = rightOf(from) + (rightOf(to) - rightOf(from)) * rightT
    /** How much of tab [i] (from i to i + 1 in tab units) the indicator covers, 0 to 1. */
    fun cover(i: Int): Float = (min(right { it + 1f }, i + 1f) - max(left { it.toFloat() }, i.toFloat())).coerceIn(0f, 1f)
}

/** The indicator [t] of the way from tab [from] to tab [to], its leading edge (the one toward [to]) ahead. */
fun spanOf(from: Int, to: Int, t: Float): Span {
    val lead = Motion.stretchLead(t)
    val trail = Motion.stretchTrail(t)
    return if (to >= from) Span(from, to, trail, lead) else Span(from, to, lead, trail)
}

/**
 * The indicator for the selected tab [index], read in the draw phase. A change of [index] glides it there on [Motion.pill]. While
 * [follow] gives a position (in tabs, fractional: a swipe under way) it is there instead, stretched the same way, and when the swipe
 * ends the glide goes on from where it was. With animations off it is a cut.
 */
@Composable
fun rememberStretch(index: Int, follow: () -> Float? = { null }): () -> Span {
    val motion = LocalMotion.current
    val t = remember { Animatable(1f) }
    var from by remember { mutableIntStateOf(index) }
    var to by remember { mutableIntStateOf(index) }
    // Where the finger last had it, for the glide to take over from.
    val last = remember { floatArrayOf(Float.NaN) }
    val followNow by rememberUpdatedState(follow)
    val following by remember { derivedStateOf { followNow() != null } }
    // A hand-off is owed once a composition has seen the swipe under way: the effect below then runs when it ends and settles it. Only then is the
    // finger's last place drawn in the frames before the effect (a drag that starts and ends between compositions owes none, and its place is not held).
    val owed = remember { booleanArrayOf(false) }
    if (following) owed[0] = true
    LaunchedEffect(index, following) {
        if (following) return@LaunchedEffect
        val p = last[0]
        // [last] is cleared only once the glide holds the hand-off: until then the frame draws where the finger left it (Round 41, I-171: cleared first,
        // the one frame between the swipe's end and this effect drew the glide's old state, the tab the swipe came from).
        if (!motion) { from = index; to = index; t.snapTo(1f); last[0] = Float.NaN; owed[0] = false; return@LaunchedEffect }
        if (!p.isNaN()) {
            val i = floor(p).toInt()
            val f = p - i
            when (index) {
                i + 1 -> { from = i; to = index; t.snapTo(f) }
                i -> { from = i + 1; to = index; t.snapTo(1f - f) }
                else -> { from = roundHalfUp(p); to = index; t.snapTo(0f) }
            }
            last[0] = Float.NaN
            owed[0] = false
        } else {
            owed[0] = false
            if (index == to && t.value == 1f) return@LaunchedEffect
            from = if (t.value < 0.5f) from else to
            to = index
            t.snapTo(0f)
        }
        if (from != to) t.animateTo(1f, Motion.pill()) else t.snapTo(1f)
    }
    return {
        val p = followNow()
        if (p != null) last[0] = p
        // Always read: whoever reads the span (a draw, a layout) is then told when the hand-off sets the glide up, which ends the held place.
        val glide = spanOf(from, to, t.value)
        val at = p ?: if (owed[0]) last[0] else Float.NaN
        if (!at.isNaN()) {
            val i = floor(at).toInt()
            val f = at - i
            if (f < 0.0005f) spanOf(i, i, 1f) else spanOf(i, i + 1, f)
        } else glide
    }
}
