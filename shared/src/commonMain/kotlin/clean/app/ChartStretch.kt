package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import clean.core.ChartPoint

/**
 * What a chart's axes span: [x0] the first day shown (as an epoch day) and [xSpan] the days it runs for, then [ys], the values the chart
 * needs to place a figure (a chart's low and high, or a low and a high per line), then whatever else is only to be crossed over.
 */
data class Frame(val x0: Double, val xSpan: Double, val ys: List<Double>)

/**
 * A chart changing range, as it is to be drawn (stage F3, 2026-11-16): [from] is the frame it is leaving (null at rest), [t] how far along
 * on [Motion.stretch], and [at] the frame in between, so the line drawn for the old range slides and scales along the dates until its
 * dates sit where they are in the new one. The points are those of both ranges together while it moves, so the older part comes in
 * from the left (or goes off it) and at the end the chart is exactly the new one.
 */
class Stretch(val from: Frame?, private val fromPoints: List<List<ChartPoint>>?, val t: Float) {
    val moving get() = from != null

    /** The frame between [from] and [to] (the new one when nothing moves, or when the two have a different number of figures). */
    fun at(to: Frame): Frame {
        val f = from ?: return to
        if (f.ys.size != to.ys.size) return to
        fun mix(a: Double, b: Double) = a + (b - a) * t
        return Frame(mix(f.x0, to.x0), mix(f.xSpan, to.xSpan), List(to.ys.size) { mix(f.ys[it], to.ys[it]) })
    }

    /** Line [i]'s points for this frame of the move: both ranges' together, by day. */
    fun union(i: Int, now: List<ChartPoint>): List<ChartPoint> {
        val old = fromPoints?.getOrNull(i) ?: return now
        return (old + now).distinctBy { it.date }.sortedBy { it.date }
    }
}

/**
 * Follows [frame] as a chart's range changes: nothing moves the first time, or with animations off, or when the frame is the same (a
 * new point on the same range just appears, as before). [reset] names what the chart is of (a measure, a unit); another one starts over
 * with no move, since a different measure is not the same line stretched.
 */
@Composable
fun rememberStretch(frame: Frame, points: List<List<ChartPoint>>, reset: Any?): Stretch {
    val motion = flourishOn()
    var settled by remember(reset) { mutableStateOf(frame to points) }
    var running by remember(reset) { mutableStateOf<Frame?>(null) }
    val progress = remember(reset) { Animatable(1f) }
    LaunchedEffect(frame, points, motion) {
        if (frame == settled.first || !motion) { settled = frame to points; return@LaunchedEffect }
        running = frame
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.stretch())
        settled = frame to points
    }
    val moving = motion && frame != settled.first
    // The frame that changes the range already starts the move, so the new chart is never shown for a frame before it.
    return Stretch(if (moving) settled.first else null, if (moving) settled.second else null, if (moving && running != frame) 0f else progress.value)
}
