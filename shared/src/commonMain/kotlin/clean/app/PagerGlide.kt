package clean.app

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Round 41: how the tabs pager settles once the finger lifts (I-169). The stock snap held the page still for the first frame after the release and then sped
 * past the finger (a spring pulling toward the page), which read as a lurch at every release in the user's recording. This one goes on at the finger's own
 * speed and eases to rest on the page, on a curve that starts with that speed and ends at rest (a cubic Hermite), and it moves the page on the very first
 * frame: that frame counts as one frame of a 120 Hz screen after the release.
 *
 * The page it goes to: the next one in the swipe's direction when the finger was moving faster than [minFling] px/s, else the nearest.
 */
class PagerGlide(private val state: PagerState, private val minFling: Float) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
        val page = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        if (page <= 0) return initialVelocity
        val at = state.currentPage + state.currentPageOffsetFraction
        val target = when {
            initialVelocity >= minFling -> floor(at + 0.001f) + 1f
            initialVelocity <= -minFling -> ceil(at - 0.001f) - 1f
            else -> at.roundToInt().toFloat()
        }.coerceIn(0f, (state.pageCount - 1).toFloat())
        val distance = (target - at) * page
        if (abs(distance) < 0.5f) { onRemainingDistanceUpdated(0f); return 0f }
        val v = initialVelocity
        // Going on at the release speed and slowing evenly takes 2 d / v; kept between a short and a calm glide.
        val seconds = if (v * distance > 0f) (2f * abs(distance) / abs(v)).coerceIn(GLIDE_MIN, GLIDE_MAX) else GLIDE_BACK
        // The curve's slope at its start, in distance per glide: the release speed, kept where the curve stays monotonic (or turns once when the finger was going the other way).
        val slope = (v * seconds / distance).coerceIn(-1f, 3f)
        var done = 0f
        var first = true
        var start = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (first) { start = now - FIRST_FRAME_NANOS; first = false }
            val s = ((now - start) / 1e9f / seconds).coerceIn(0f, 1f)
            val p = hermite(s, slope)
            val want = distance * p - done
            val moved = scrollBy(want)
            done += moved
            onRemainingDistanceUpdated(distance - done)
            if (s >= 1f || abs(moved - want) > 0.5f) break
        }
        return 0f
    }
}

/** From 0 to 1 over [s] in 0..1, leaving with slope [m0] and arriving with slope 0. */
internal fun hermite(s: Float, m0: Float): Float = (s * s * s - 2f * s * s + s) * m0 + (-2f * s * s * s + 3f * s * s)

private const val GLIDE_MIN = 0.16f
private const val GLIDE_MAX = 0.42f
private const val GLIDE_BACK = 0.30f
private const val FIRST_FRAME_NANOS = 8_333_333L
