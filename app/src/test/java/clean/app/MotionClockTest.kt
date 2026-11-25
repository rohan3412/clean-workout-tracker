package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** The blink's clock: out then in, never both above 10 %, about 150 ms in all; the window animations in res/anim say the same. */
@Category(Regression::class)
class MotionClockTest {
    private fun TweenSpec<Float>.at(ms: Int): Float =
        vectorize(Float.VectorConverter).getValueFromNanos(ms * 1_000_000L, AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)).value

    @Test
    fun `blink never shows two screens`() {
        val total = Motion.BLINK_OUT_MS + Motion.BLINK_IN_MS
        assertEquals(150, total)
        for (t in 0..total) {
            val out = 1f - Motion.blinkOutSpec<Float>().at(t)
            val inn = Motion.blinkInSpec<Float>().at(t)
            assertFalse("at $t ms: out $out, in $inn", out > 0.1f && inn > 0.1f)
        }
        assertEquals(1f, Motion.blinkInSpec<Float>().at(total), 0.001f)
    }

    @Test
    fun `window blink matches the clock`() {
        val res = listOf(File("src/main/res/anim"), File("app/src/main/res/anim")).first { it.exists() }
        fun duration(name: String) = Regex("android:duration=\"(\\d+)\"").find(File(res, "$name.xml").readText())!!.groupValues[1].toInt()
        assertEquals(Motion.BLINK_IN_MS, duration("blink_in"))
        assertEquals(Motion.BLINK_OUT_MS, duration("blink_out"))
    }
}
