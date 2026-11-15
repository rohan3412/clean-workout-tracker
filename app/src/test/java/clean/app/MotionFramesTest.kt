package clean.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Real rendered frames of the blink: the app itself (MainActivity on a database seeded with hand-written workouts), the clock paused
 * and stepped 16 ms at a time through a tab switch and a page opening and closing. Each change is saved as a strip under
 * build/motion-frames/ (at rest, the first twelve frames, at rest) to be looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class MotionFramesTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun seed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = Repo(CleanDb.open(context))
        val id = repo.createProfile("Test")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val start = LocalDateTime.now().minusDays(40).withHour(18).withMinute(0).withSecond(0)
        val rows = (0 until 14).flatMap { n ->
            val date = start.plusDays(n * 3L).format(format)
            val name = if (n % 2 == 0) "Push" else "Pull"
            val lift = if (n % 2 == 0) "Bench Press (Barbell)" else "Lat Pulldown (Machine)"
            (1..3).map { set -> "\"${n + 1}\";\"$date\";\"$name\";\"3600\";\"$lift\";\"$set\";\"${60 + n}.0\";\"8\";\"\";\"\";\"\";\"\";\"\"" }
        }
        val bytes = (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()
        repo.commit(id, repo.previewWorkouts(id, bytes))
    }

    private var window: android.view.View? = null
    private lateinit var model: AppModel

    /** The window drawn as it stands at this point of the paused clock (capturing through the test rule waits for a redraw that never comes). */
    private fun frame(): Bitmap {
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    /** Every semantics node on screen, by id, with its bounds. */
    private fun nodes(): Map<Int, Rect> {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        return compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap(::walk).associate { it.id to it.boundsInRoot }
    }

    private fun saveStrip(name: String, frames: List<Bitmap>) {
        val scale = 2
        val w = frames.first().width / scale
        val h = frames.first().height / scale
        val strip = java.awt.image.BufferedImage(w * frames.size, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b ->
            val small = Bitmap.createScaledBitmap(b, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            strip.setRGB(i * w, 0, w, h, pixels, 0, w)
        }
        File("build/motion-frames").mkdirs()
        javax.imageio.ImageIO.write(strip, "png", File("build/motion-frames/$name.png"))
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun diff(a: Int, b: Int) =
        Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))

    /**
     * How much of each screen a frame shows: over the pixels where only [a] has something (the other screen is background there), how
     * far the frame is from the background as a share of how far [a] is; the same for [b].
     */
    private fun opacities(frame: IntArray, a: IntArray, b: IntArray): Pair<Float, Float> {
        val bg = a.groupBy { it }.maxBy { it.value.size }.key
        fun share(x: IntArray, y: IntArray): Float {
            var num = 0L
            var den = 0L
            for (i in x.indices) if (diff(x[i], bg) > 120 && diff(y[i], bg) < 12) { num += diff(frame[i], bg); den += diff(x[i], bg) }
            return if (den == 0L) 0f else num.toFloat() / den
        }
        return share(a, b) to share(b, a)
    }

    private fun launch(): ActivityScenario<MainActivity> {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        compose.waitForIdle()
        return scenario
    }

    /**
     * One change ([act]) stepped 16 ms at a time: (a) nothing on screen in two frames running moves between them, (b) never both
     * screens above 10 %, (c) from 160 ms on every frame is the screen at rest.
     */
    private fun blink(name: String, act: () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val before = frame()
        compose.runOnIdle(act)
        val frames = mutableListOf<Bitmap>()
        val bounds = mutableListOf<Map<Int, Rect>>()
        var t = 0
        while (t < 320) { compose.mainClock.advanceTimeBy(16); frames += frame(); bounds += nodes(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        val after = frame()
        compose.mainClock.autoAdvance = true
        saveStrip(name, listOf(before) + frames.take(12) + after)

        for (i in 1 until bounds.size) for ((id, r) in bounds[i]) {
            // A node that has just come or is going has no size for a frame: that is it appearing or leaving, not moving.
            val was = bounds[i - 1][id]?.takeIf { it.width > 0f && it.height > 0f } ?: continue
            if (r.width <= 0f || r.height <= 0f) continue
            assertTrue(
                "$name frame ${i + 1}: node $id moved from $was to $r",
                Math.abs(was.left - r.left) < 1f && Math.abs(was.top - r.top) < 1f && Math.abs(was.width - r.width) < 1f && Math.abs(was.height - r.height) < 1f,
            )
        }
        val a = pixels(before)
        val b = pixels(after)
        for ((i, f) in frames.withIndex()) {
            val (oa, ob) = opacities(pixels(f), a, b)
            assertTrue("$name at ${(i + 1) * 16} ms: old at $oa, new at $ob", !(oa > 0.1f && ob > 0.1f))
        }
        // The clock starts at the first frame that shows the change (the frames before it are the new page being built).
        val begun = frames.indexOfFirst { f -> pixels(f).let { p -> p.indices.count { diff(p[it], a[it]) > 12 } * 50 >= p.size } }
        assertTrue("$name never changed", begun >= 0)
        println("$name: the change shows from frame ${begun + 1} (${(begun + 1) * 16} ms)")
        for ((i, f) in frames.withIndex()) if (i >= begun + 160 / 16) {
            val p = pixels(f)
            val offAt = p.indices.filter { diff(p[it], b[it]) > 12 }
            val w = f.width
            assertTrue(
                "$name at ${(i - begun) * 16} ms after it began: ${offAt.size} pixels not yet at rest, between x ${offAt.minOfOrNull { it % w }}..${offAt.maxOfOrNull { it % w }}, y ${offAt.minOfOrNull { it / w }}..${offAt.maxOfOrNull { it / w }}",
                offAt.size * 1000 < p.size,
            )
        }
    }

    @Test fun tabSwitch() {
        val scenario = launch()
        blink("tab-home-progress") { model.tab = Tab.Progress }
        scenario.close()
    }

    @Test fun activityToCalendarAndBack() {
        val scenario = launch()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        blink("calendar-open") { model.open(Screen.Calendar()) }
        blink("calendar-back") { model.back() }
        scenario.close()
    }
}
