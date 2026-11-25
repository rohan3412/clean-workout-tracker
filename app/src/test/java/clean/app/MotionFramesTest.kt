package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import clean.core.time.*
import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.Kv
import clean.app.data.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.getOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDateTime

/**
 * Real rendered frames of the blink: the app itself (MainActivity on a database seeded with hand-written workouts), the clock paused
 * and stepped 16 ms at a time through a tab switch and a page opening and closing. Each change is saved as a strip under
 * build/motion-frames/ (at rest, the first twelve frames, at rest) to be looked at.
 */
@Category(Regression::class)
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
        // The first-open-of-the-week pill fades on a clock of its own and would show as a change between frames: this week's is taken as shown.
        repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
        repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
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

    private fun saveStrip(name: String, frames: List<Bitmap>, perRow: Int = 100, scale: Int = 2) {
        val w = frames.first().width / scale
        val h = frames.first().height / scale
        val cols = minOf(perRow, frames.size)
        val rows = (frames.size + cols - 1) / cols
        val strip = java.awt.image.BufferedImage(w * cols, h * rows, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b ->
            val small = Bitmap.createScaledBitmap(b, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            strip.setRGB((i % cols) * w, (i / cols) * h, w, h, pixels, 0, w)
        }
        File("build/motion-frames").mkdirs()
        javax.imageio.ImageIO.write(strip, "png", File("build/motion-frames/$name.png"))
    }

    /** A part of the window left out of the frame comparisons: the workout banner's clock, which counts real seconds. */
    private var ignored: Rect? = null
    /** And the clock pill's pulsing dot, which never rests while a workout runs. */
    private var ignoredToo: Rect? = null
    /**
     * The dock's four slots. The dock paints one thing of its own clock that a page's frames must not be judged by: the capsule (shown for
     * CAPSULE_HOLD, then faded over CAPSULE_FADE; the arrival disc behind a tab's icon, E6, was removed in Round 41). It starts when a tab becomes the current
     * one, which a card's Back can do again; where a frame falls in them depends on when the bar was composed, which changed from run to run and
     * failed whole-class runs in different tests at x 94..233, y 732..787. It is tested on its own (tabHighlightStaysThenFades); every comparison of a page with itself leaves the slots out.
     */
    private var dockSlots: Rect? = null
    private fun isIgnored(i: Int, w: Int) = listOfNotNull(ignored, ignoredToo, dockSlots).any { (i % w) >= it.left && (i % w) <= it.right && (i / w) >= it.top && (i / w) <= it.bottom }
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

    private fun launch(preset: String? = null, lastFirst: Boolean = false): ActivityScenario<MainActivity> {
        seed()
        runBlocking {
            val dao = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao
            // Dark with pure black, the page the frames were calibrated on.
            dao.put(Kv(clean.core.LOOK_THEME_KEY, if (preset == "light") "light" else "dark"))
            dao.put(Kv(clean.core.LOOK_BLACK_KEY, if (preset == "light") "off" else "on"))
            dao.put(Kv(clean.core.LOOK_DONE_KEY, "1"))
            // Cobalt (Round 34; Ocean before): the frames are calibrated on a coloured accent (the body figure's colour, the done tint), which Graphite's greys do not give.
            dao.put(Kv(clean.core.LOOK_ACCENT_KEY, "Cobalt"))
            dao.put(Kv(clean.core.LOOK_V2_KEY, "1"))
            dao.put(Kv("ui.motion", if (seedOff) "off" else "on"))
            dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            // Every test starts on the Body page's first view, whatever an earlier test swiped to.
            dao.put(Kv("ui.bodyMode", "Balance"))
            // The last workout first, wide, so it is on the screen in every look.
            if (lastFirst) dao.put(Kv("home.cards:${dao.get("active")}", "last:w,streak:h,volume:h,week:h"))
        }
        MorphProbe.boxes.clear()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded }
        compose.waitForIdle()
        dockSlots = runCatching { Rect(slotRect("Home").left - 8f, slotRect("Home").top - 8f, slotRect("Profile").right + 8f, slotRect("Home").bottom + 8f) }.getOrNull()
        return scenario
    }

    /**
     * One change ([act]) stepped 16 ms at a time: (a) nothing on screen in two frames running moves between them, (b) never both
     * screens above 10 %, (c) from 160 ms on every frame is the screen at rest.
     */
    private fun blink(name: String, overlap: Float = 0.1f, act: () -> Unit) {
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
            // Round 29, deliberate: a dock icon rises (at most the name's lift, 7 dp) as the new tab's name fades in, and settles as it fades out.
            val lifting = dockSlots?.contains(r.center) == true && Math.abs(was.left - r.left) < 1f && Math.abs(was.top - r.top) <= 8f * window!!.resources.displayMetrics.density
            assertTrue(
                "$name frame ${i + 1}: node $id moved from $was to $r",
                lifting || (Math.abs(was.left - r.left) < 1f && Math.abs(was.top - r.top) < 1f && Math.abs(was.width - r.width) <= 1f && Math.abs(was.height - r.height) <= 1f),
            )
        }
        val a = pixels(before)
        val b = pixels(after)
        for ((i, f) in frames.withIndex()) {
            val (oa, ob) = opacities(pixels(f), a, b)
            assertTrue("$name at ${(i + 1) * 16} ms: old at $oa, new at $ob", !(oa > overlap && ob > overlap))
        }
        // The clock starts at the first frame that shows the change (the frames before it are the new page being built).
        val begun = frames.indexOfFirst { f -> pixels(f).let { p -> p.indices.count { diff(p[it], a[it]) > 12 } * 50 >= p.size } }
        assertTrue("$name never changed", begun >= 0)
        println("$name: the change shows from frame ${begun + 1} (${(begun + 1) * 16} ms)")
        for ((i, f) in frames.withIndex()) if (i >= begun + 160 / 16) {
            val p = pixels(f)
            val w = f.width
            // The tab bar is left out: its pill glides to the new tab on its own clock (Motion.PILL), longer than the blink; tabPillGlidesAndFollowsASwipe covers it.
            val offAt = p.indices.filter { diff(p[it], b[it]) > 12 && it / w < 700 }
            assertTrue(
                "$name at ${(i - begun) * 16} ms after it began: ${offAt.size} pixels not yet at rest, between x ${offAt.minOfOrNull { it % w }}..${offAt.maxOfOrNull { it % w }}, y ${offAt.minOfOrNull { it / w }}..${offAt.maxOfOrNull { it / w }}",
                offAt.size * 1000 < p.size,
            )
        }
    }

    /** Steps [ms] of the paused clock after [act], returning every frame (the first is the frame before [act]). */
    private fun run(act: () -> Unit, ms: Int = 480): List<Bitmap> {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) { compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return frames
    }

    /** The window bounds of the card tagged [key] on the tabs page. */
    private fun cardRect(key: String): Rect = compose.onNodeWithTag("card:$key", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun scrollTo(key: String) {
        val tag = androidx.compose.ui.test.hasTestTag("card:$key")
        // On the screen already (the tag sits inside the card, so a scroll to it by the merged tree would not find it).
        val here = compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        // Cards that wait for data come a moment after the tab.
        try { compose.waitUntil(4000) { compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { }
        val here2 = compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: here
        if (here2 != null && here2.top >= 0f && here2.bottom <= 800f && here2.width > 0f) return
        val lists = compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until lists) {
            try { compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[i].performScrollToKey(key.substringAfter(':')); compose.waitForIdle(); return } catch (e: Throwable) { }
        }
        // A list that will not scroll to a node it has not composed yet is swiped, as a finger would.
        var swipes = 0
        while (compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().let { n -> n.isEmpty() || n.first().boundsInRoot.let { it.width <= 0f || it.bottom > 700f } } && swipes++ < 4) {
            compose.onNodeWithTag("page:tabs", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    /** What the container did in one change: the bounds it was drawn at in each frame it was drawn, and how far it had grown. */
    private class Trail(val rects: List<Rect>, val grown: List<Float>, val matched: Set<String>, val ended: Int, val before: Map<String, Rect> = emptyMap(), val after: Map<String, Rect> = emptyMap(), val boxes: List<Map<String, Rect>> = emptyList(), val progress: List<Float> = emptyList(), val drawn: List<Boolean> = emptyList())

    private fun trail(act: () -> Unit, frames: MutableList<Bitmap>, ms: Int = 640, perFrame: () -> Unit = {}): Trail {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        val clip = MorphProbe.clip!!
        val rects = mutableListOf<Rect>()
        val grown = mutableListOf<Float>()
        var seen = clip.draws
        val matched = HashSet<String>()
        var ended = -1
        val before = HashMap(MorphProbe.boxes)
        val boxes = mutableListOf<Map<String, Rect>>()
        val progress = mutableListOf<Float>()
        val drawn = mutableListOf<Boolean>()
        val draws0 = clip.draws
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16
            perFrame()
            boxes += HashMap(MorphProbe.boxes); progress += clip.grown; drawn += (clip.draws != draws0)
            for ((k, st) in MorphProbe.states) if (st.isMatchFound) matched += k
            val drew = clip.draws != seen
            if (drew) { seen = clip.draws; rects += clip.rect; grown += clip.grown }
            // The move is over after the last frame in which the container moved.
            if (drew && grown.size > 1 && Math.abs(grown.last() - grown[grown.size - 2]) > 0.0001f) ended = frames.size - 1
        }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return Trail(rects, grown, matched, ended, before, HashMap(MorphProbe.boxes), boxes, progress, drawn)
    }

    /**
     * Everything that travels is on the container's one clock (Stage H): every shared box that moved (a title, a number, a History row's name,
     * date and PR count, the box a row goes to below the page, the figure, a photo), normalised by its own start-to-end distance, is where the
     * container's progress says in every frame, never early or late: at its start on frame 0 and at its end by the last frame of the move.
     * A box that goes by legs is compared along its legs ([Motion.leggedAt]). Boxes that do not move (under 24 px) are skipped.
     * [from] and [to] are the sides the box starts and ends on (`card` and `page` opening, the other way back).
     */
    private fun onTheClock(label: String, t: Trail, forward: Boolean, need: Boolean = false) {
        val from = if (forward) "card" else "page"
        val to = if (forward) "page" else "card"
        val legged = listOf("/row:name/", "/row:date/", "/row:prs/")
        var checked = 0
        val off = mutableListOf<String>()
        // The move: up to the first frame the container has arrived (the sides are swapped after it).
        val n = (t.progress.indexOfFirst { if (forward) it >= 0.999f else it <= 0.001f }.let { if (it < 0) t.progress.size else it + 1 }).coerceAtMost(t.boxes.size)
        for ((k, was) in t.before) {
            if (!k.endsWith("/$from")) continue
            // (where it rests as the change begins: the layout may have moved since the last time it was noted)
            val start = t.boxes.firstOrNull()?.get(k) ?: was
            val base = k.removeSuffix("/$from")
            if (base !in t.matched) continue
            val end = t.after["$base/$to"] ?: continue
            // (the container's progress is read from its size, which a tall card's photos do not follow on Back; they are checked opening)
            if (!forward && base.contains("/photo/")) continue
            // (the side that is not drawn is laid out empty, at the window's corner)
            val side = listOf(to).firstOrNull { sd -> t.boxes.take(n).drop(2).all { (it["$base/$sd"]?.width ?: 0f) > 0f } } ?: continue
            val track = t.boxes.take(n).map { it["$base/$side"] ?: Rect.Zero }
            val isLegged = legged.any { base.contains(it) }
            val distance = maxOf(Math.abs(end.center.x - start.center.x), Math.abs(end.center.y - start.center.y))
            if (distance < 24f) continue
            checked++
            // (the first two frames' boxes are those of the layout before the change, and a frame in which the container has not been drawn yet still reports where the last change ended)
            var worst = 0f
            var worstAt = ""
            for (i in track.indices) {
                if (i < 2 || !t.drawn[i]) continue
                // the change's clock: frame 1 is its time 0, and a frame is 16 ms
                // (the banner's container starts two frames after the tap, so it and Back read the container's own progress, which is not the shares of a photo's tall card)
                val p = if (forward && base.startsWith("banner:")) t.progress[i] else if (forward) Motion.Emphasized.transform(((i - 1) * 16f / Motion.CARD_TO_PAGE).coerceIn(0f, 1f))
                    else 1f - t.progress[i]
                val want = if (isLegged) Motion.leggedAt(start, end, forward, p) else androidx.compose.ui.geometry.lerp(start, end, p)
                // (a box that changes size is held by its start edge, its centre or its end edge: any is on the clock)
                val gap = maxOf(minOf(Math.abs(track[i].left - want.left), Math.abs(track[i].center.x - want.center.x), Math.abs(track[i].right - want.right)), minOf(Math.abs(track[i].top - want.top), Math.abs(track[i].center.y - want.center.y), Math.abs(track[i].bottom - want.bottom)))
                if (gap > worst) { worst = gap; worstAt = "frame $i progress $p (container ${t.progress[i]}): at ${track[i]} want $want" }
            }
            if (worst > 0.06f * distance + 2f) off += "$base ($side) distance $distance worst $worstAt"
        }
        println("$label: $checked travelling boxes checked against the clock")
        if (need) assertTrue("$label: nothing travelling was checked: before ${t.before.keys.sorted()} after ${t.after.keys.sorted()}", checked > 0)
        assertTrue("$label: boxes off the container's clock: ${off.take(6)} (${off.size})", off.isEmpty())
    }

    private fun pick(frames: List<Bitmap>, every: Int, count: Int) = (0 until frames.size step every).map { frames[it] }.take(count) + frames.last()

    /**
     * A card opening its page and Back, in [preset] (null the default look): strips of both, and the container (a) is drawn while it
     * moves, (b) grows steadily one way and shrinks steadily the other, (c) on Back lands on the card where it now sits on the tabs
     * page, (d) leaves nothing behind: the tabs page after Back is the one before the tap.
     */
    private fun cardMorph(name: String, tab: Tab, key: String, preset: String? = null, shared: List<String> = listOf("title"), chart: String? = null, setup: () -> Unit = {}, extra: () -> List<String> = { emptyList() }, samePage: Boolean = true) {
        val scenario = launch(preset, lastFirst = true)
        setup()
        compose.runOnIdle { model.tab = tab }
        compose.waitForIdle()
        // Past start-up (the neighbour tab is laid out after 800 ms and evens out the Activity gap; the cold-start rise ends by 1.5 s), so
        // the card is measured where it rests, as a user who opens a card a moment after launch sees it.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        scrollTo(key)
        compose.waitForIdle()
        val card = cardRect(key)
        val moving = shared + extra()
        val travel = extra().isNotEmpty() || chart != null
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames)
        assertTrue("$name: the card did not open a page", model.stack.isNotEmpty())
        if (travel) onTheClock("$name open", open, true)
        val tag = preset ?: "default"
        saveStrip("$name-open-$tag", pick(openFrames, 3, 11), 6)
        saveStrip("$name-open-$tag-fine", openFrames.subList(0, 12), 6)
        assertTrue("$name open: the container was drawn in only ${open.rects.size} frames", open.rects.size >= 8)
        assertTrue("$name open: the container does not grow steadily ${open.grown}", open.grown.zipWithNext().all { (a, b) -> b >= a - 0.001f })
        assertTrue("$name open: it ends at ${open.grown.last()}", open.grown.last() > 0.9f)
        assertTrue("$name open: it starts at ${open.grown.first()}", open.grown.first() < 0.6f)
        // A title that is the same words on card and page is one text that moves: it found its match on the way there and on the way back.
        // What is on both the card and the page found its match, so it is one moving box (a label into the title, a number into the number).
        for (role in moving) assertTrue("$name open: $role never matched; matched ${open.matched}", "$key/$role" in open.matched)
        // A chart on both is one line that travels and reshapes, drawn over both, not two charts crossing over.
        if (chart != null) assertTrue("$name open: the chart line did not travel; drew ${MorphProbe.chartsDrawn}", "$key/$chart" in MorphProbe.chartsDrawn)
        if (chart != null) noStepAtTheEnd("$name open", openFrames, open.ended)
        MorphProbe.chartsDrawn.clear()
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        if (travel) onTheClock("$name back", back, false)
        for (role in moving) assertTrue("$name back: $role never matched; matched ${back.matched}", "$key/$role" in back.matched)
        stillAtTheStart("$name back", backFrames, null)
        settledAndStill("$name back", backFrames, "$name-back-$tag", back.ended)
        saveStrip("$name-back-$tag", pick(backFrames, 3, 11), 6)
        saveStrip("$name-back-$tag-fine", backFrames.subList(0, 12), 6)
        if (chart != null) assertTrue("$name back: the chart line did not travel; drew ${MorphProbe.chartsDrawn}", "$key/$chart" in MorphProbe.chartsDrawn)
        if (chart != null) noStepAtTheEnd("$name back", backFrames, back.ended)
        assertTrue("$name back: the container was drawn in only ${back.rects.size} frames", back.rects.size >= 8)
        assertTrue("$name back: it does not shrink steadily ${back.grown}", back.grown.zipWithNext().all { (a, b) -> b <= a + 0.001f })
        assertTrue("$name back: it starts at ${back.grown.first()}", back.grown.first() > 0.4f)
        val now = cardRect(key)
        assertTrue("$name back: it ends at ${back.grown.last()}", back.grown.last() < 0.45f)
        assertTrue("$name back: the card moved from $card to $now", Math.abs(card.top - now.top) < 1f && Math.abs(card.left - now.left) < 1f)
        // Nothing left behind: the tabs page after Back is the one before the tap.
        saveStrip("$name-compare-$tag", listOf(openFrames.first(), backFrames.last()), 2)
        val before = pixels(openFrames.first())
        val after = pixels(backFrames.last())
        val w = backFrames.last().width
        val off = before.indices.filter { diff(before[it], after[it]) > 12 && !isIgnored(it, w) }
        val differing = off.size
        assertTrue("$name back: $differing pixels differ from before the tap, x ${off.minOfOrNull { it % w }}..${off.maxOfOrNull { it % w }}, y ${off.minOfOrNull { it / w }}..${off.maxOfOrNull { it / w }}", differing * 200 < before.size)
        if (travel) sameWithAnimationsOff(name, key, backFrames.last(), samePage)
        scenario.close()
    }

    /**
     * The frame after a card was tapped (or Back asked for) is the frame before it: nothing about the layout, the words, their
     * ellipsis, colour or size changes at that moment, the clock has hardly begun. Only the press ripple around [tap] and the edge of
     * the container (a pixel or two on its way) may differ. Part of every morph test.
     */
    private fun stillAtTheStart(label: String, frames: List<Bitmap>, card: Rect?, tap: androidx.compose.ui.geometry.Offset? = null) {
        val a = pixels(frames[0])
        val b = pixels(frames[1])
        val w = frames[0].width
        fun masked(i: Int): Boolean {
            val x = i % w
            val y = i / w
            if (tap != null && Math.hypot((x - tap.x).toDouble(), (y - tap.y).toDouble()) < 72.0) return true
            // The container's edge moves a pixel or two in the first frame.
            if (card != null && x >= card.left - 6 && x <= card.right + 6 && y >= card.top - 6 && y <= card.bottom + 6 && !(x > card.left + 6 && x < card.right - 6 && y > card.top + 6 && y < card.bottom - 6)) return true
            return false
        }
        val off = a.indices.filter { diff(a[it], b[it]) > 24 && !masked(it) && !isIgnored(it, w) }
        println("$label: ${off.size} pixels differ between the frame before the tap and the frame after it")
        saveStrip("$label-first-frames", frames.subList(0, 3), 3, 1)
        assertTrue("$label: ${off.size} pixels changed in the first frame after the tap, x ${off.minOfOrNull { it % w }}..${off.maxOfOrNull { it % w }}, y ${off.minOfOrNull { it / w }}..${off.maxOfOrNull { it / w }}", off.isEmpty())
    }

    /**
     * The move slows towards its end and nothing steps at the hand-over: the pixels that change from one frame to the next, over the last
     * frames before the container arrives and the frame it does, never grow (a chart's own line taking over from the travelling one, a
     * wash or a dot appearing, would show as a step). Part of every morph that carries a chart.
     */
    private fun noStepAtTheEnd(label: String, frames: List<Bitmap>, ended: Int) {
        val changes = (ended - 8..ended + 1).map { i -> pixels(frames[i]).let { a -> pixels(frames[i + 1]).let { b -> a.indices.count { diff(a[it], b[it]) > 24 } } } }
        println("$label: pixels changing frame to frame towards the end $changes")
        // The frames each side of the hand-over and what changed between the last two, to be looked at.
        val a0 = pixels(frames[ended]); val a1 = pixels(frames[ended + 1])
        val delta = Bitmap.createBitmap(frames[0].width, frames[0].height, Bitmap.Config.ARGB_8888)
        delta.setPixels(IntArray(a0.size) { if (diff(a0[it], a1[it]) > 24) 0xFFFF0000.toInt() else 0xFF000000.toInt() }, 0, delta.width, 0, 0, delta.width, delta.height)
        saveStrip("${label.replace(' ', '-')}-handover", listOf(frames[ended - 1], frames[ended], frames[ended + 1], delta), 4, 1)
        val earlier = changes.dropLast(2).maxOrNull() ?: 0
        assertTrue("$label: a step at the hand-over, changes per frame $changes", changes.takeLast(2).all { it <= earlier + 40 })
    }

    /**
     * What a change that moves things must end as: the last frame of the move is the frame at rest, so nothing is left over the screen
     * (no pop when the clock ends). Strips of the last frames of the move and of the frame at rest are saved to be looked at.
     */
    private fun settledAndStill(label: String, frames: List<Bitmap>, strip: String, ended: Int) {
        assertTrue("$label: the container never reached its end", ended in 20 until frames.size - 1)
        saveStrip("$strip-rest", listOf(frames[frames.size - 8], frames[frames.size - 5], frames[frames.size - 3], frames[frames.size - 2], frames.last()), 5, 1)
        val rest = pixels(frames.last())
        val w = frames.last().width
        // Every frame after the move ([ended] is the one in which the container arrives) is the frame at rest, so nothing hands over, pops or is missing after it.
        for (i in ended + 1 until frames.size - 1) {
            val p = pixels(frames[i])
            val off = p.indices.filter { diff(p[it], rest[it]) > 12 && !isIgnored(it, w) }
            assertTrue("$label: ${off.size} pixels still differ from rest in frame $i of ${frames.size}, x ${off.minOfOrNull { it % w }}..${off.maxOfOrNull { it % w }}, y ${off.minOfOrNull { it / w }}..${off.maxOfOrNull { it / w }}", off.isEmpty())
        }
    }

    /**
     * What moves adds nothing at rest: with animations off the page (opened by the card's tap again) and the tabs page after Back are
     * drawn as they are with animations on. Ends the test's morphs, so it comes last.
     */
    private fun sameWithAnimationsOff(label: String, key: String, tabAtRest: Bitmap, samePage: Boolean) {
        val pageFrames = mutableListOf<Bitmap>()
        trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, pageFrames)
        val pageOn = pixels(pageFrames.last())
        trail({ model.back() }, mutableListOf())
        compose.runOnIdle { model.setOption("ui.motion", "off") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val tabOff = pixels(frame())
        val tabOn = pixels(tabAtRest)
        val w = tabAtRest.width
        val offTab = tabOn.indices.filter { diff(tabOn[it], tabOff[it]) > 12 }
        println("$label: ${offTab.size} pixels of the tabs page differ between animations on and off")
        assertTrue("$label: the tabs page at rest differs in ${offTab.size} pixels with animations off, x ${offTab.minOfOrNull { it % w }}..${offTab.maxOfOrNull { it % w }}, y ${offTab.minOfOrNull { it / w }}..${offTab.maxOfOrNull { it / w }}", offTab.size * 1000 < tabOn.size)
        compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val pageOff = pixels(frame())
        val offPage = pageOn.indices.filter { diff(pageOn[it], pageOff[it]) > 12 }
        println("$label: ${offPage.size} pixels of the page differ between animations on and off")
        saveStrip("$label-off-vs-on", listOf(pageFrames.last(), frame()), 2, 1)
        if (samePage) assertTrue("$label: the page at rest differs in ${offPage.size} pixels with animations off, x ${offPage.minOfOrNull { it % w }}..${offPage.maxOfOrNull { it % w }}, y ${offPage.minOfOrNull { it / w }}..${offPage.maxOfOrNull { it / w }}", offPage.size * 1000 < pageOn.size)
    }

    @Test fun progressBodyCard() = cardMorph("body", Tab.Progress, "progress:body", shared = listOf("title", "figure", "view"))
    @Test fun progressHistoryCard() = cardMorph("history", Tab.Progress, "progress:history")
    @Test fun statisticsRow() = cardMorph("stat-trends", Tab.Progress, "progress:stat:trends")
    @Test fun exerciseRow() = cardMorph("exercise-row", Tab.Exercises, "exercises:r:Bench Press (Barbell)")

    // What is on both a card and its page travels (stage E): rows, numbers, badges and photos. Each test names what must be matched
    // both ways (opening and Back) and that the frames after the move are the frame at rest, with the same screen when animations are off.
    private fun daoNow() = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao


    /**
     * The History card's rows travel into the History page: the rows whose cards are on the page's screen match their name, PR count and
     * date (one text each, moving from the row to the card), and the rows whose cards are further down go below the page (their own
     * thing, matched with a place under the last card). Strips of both ways are saved to be looked at.
     */
    private fun historyRows(preset: String?) {
        val scenario = launch(preset)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        scrollTo("progress:history")
        compose.waitForIdle()
        val rows = model.workouts.value.asReversed().map { it.date }
        val tag = preset ?: "default"
        val key = "progress:history"
        val historyCard = cardRect(key)
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames)
        assertTrue("history rows: the card did not open a page", model.stack.isNotEmpty())
        onTheClock("history rows open", open, true, need = true)
        saveStrip("history-rows-open-$tag", pick(openFrames, 2, 17), 6)
// Once the move has run its 500 ms nothing of the rows is left over the page: the frames after it are the frame at rest.
        stillAtTheStart("history rows open", openFrames, historyCard, androidx.compose.ui.geometry.Offset(historyCard.left + 8f, historyCard.top + 8f))
        settledAndStill("history rows open", openFrames, "history-rows-open-$tag", open.ended)
        saveStrip("history-rows-open-$tag-big", listOf(10, 14, 18, 22, 26, 30, 34, 38).map { openFrames[it] } + openFrames.last(), 3, 1)
        for (role in listOf("row:name", "row:prs")) {
            val matched = rows.filter { "$key/$role/$it" in open.matched }
            assertTrue("history rows open: no $role matched; matched ${open.matched}", matched.isNotEmpty())
        }
        // Round 21: the date does not travel, it fades out and in where it is.
        assertTrue("history rows open: a date travelled; matched ${open.matched}", open.matched.none { it.startsWith("$key/row:date/") })
        // Rows that are not on the page's screen go below it (matched under the last card).
        val below = rows.filter { "$key/row:out/$it" in open.matched }
        assertTrue("history rows open: no row travelled below the page; matched ${open.matched}", below.isNotEmpty())
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        onTheClock("history rows back", back, false, need = true)
        stillAtTheStart("history rows back", backFrames, null)
        settledAndStill("history rows back", backFrames, "history-rows-back-$tag", back.ended)
        saveStrip("history-rows-back-$tag", pick(backFrames, 2, 17), 6)
        saveStrip("history-rows-back-$tag-big", listOf(0, 4, 8, 12, 16, 20, 24, 28, 32).map { backFrames[it] }, 3, 1)
        for (role in listOf("row:name", "row:prs", "row:out")) {
            assertTrue("history rows back: no $role matched; matched ${back.matched}", rows.any { "$key/$role/$it" in back.matched })
        }
        scenario.close()
    }

    /**
     * A card on a page opening another page (page to page): [openPage] puts the first page up, the card tagged [key] on it is tapped and
     * the container grows out of it into the workout's page, the card's title travelling to the page's title; Back shrinks it onto the
     * card, and the first page after Back is the one before the tap. Strips of both ways and of the frames at rest are saved.
     */
    private fun pageCardMorph(name: String, keyOf: () -> String, openPage: () -> Unit, preset: String? = null, moving: List<String> = listOf("title"), setup: () -> Unit = {}) {
        val scenario = launch(preset)
        setup()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        openPage()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val key = keyOf()
        val card = cardRect(key)
        val tag = preset ?: "default"
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames)
        assertTrue("$name: the card did not open a page", model.stack.size >= 2)
        onTheClock("$name open", open, true)
        stillAtTheStart("$name open", openFrames, card, androidx.compose.ui.geometry.Offset(card.left + 8f, card.top + 8f))
        settledAndStill("$name open", openFrames, "$name-open-$tag", open.ended)
        saveStrip("$name-open-$tag", pick(openFrames, 3, 11), 6)
        saveStrip("$name-open-$tag-fine", openFrames.subList(0, 12), 6)
        saveStrip("$name-open-$tag-rest", listOf(openFrames[openFrames.size - 3], openFrames[openFrames.size - 2], openFrames.last()), 3, 1)
        stillAtTheStart("$name open", openFrames, card, androidx.compose.ui.geometry.Offset(card.left + 8f, card.top + 8f))
        settledAndStill("$name open", openFrames, "$name-open-$tag", open.ended)
        assertTrue("$name open: the container was drawn in only ${open.rects.size} frames", open.rects.size >= 8)
        assertTrue("$name open: the container does not grow steadily ${open.grown}", open.grown.zipWithNext().all { (a, b) -> b >= a - 0.001f })
        assertTrue("$name open: it ends at ${open.grown.last()}", open.grown.last() > 0.9f)
        assertTrue("$name open: it starts at ${open.grown.first()}", open.grown.first() < 0.6f)
        for (role in moving) assertTrue("$name open: $role never matched; matched ${open.matched}", "$key/$role" in open.matched)
        // The container is over by the end of its clock: the frame after it is the frame at rest.
        val atEnd = pixels(openFrames[openFrames.size - 2])
        val rest = pixels(openFrames.last())
        val stillMoving = atEnd.indices.count { diff(atEnd[it], rest[it]) > 12 }
        println("$name open: $stillMoving pixels differ between the last frame of the move and rest")
        assertTrue("$name open: $stillMoving pixels still differ after the move", stillMoving * 1000 < atEnd.size)
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        onTheClock("$name back", back, false)
        saveStrip("$name-back-$tag", pick(backFrames, 3, 11), 6)
        saveStrip("$name-back-$tag-fine", backFrames.subList(0, 12), 6)
        saveStrip("$name-back-$tag-rest", listOf(backFrames[backFrames.size - 3], backFrames[backFrames.size - 2], backFrames.last()), 3, 1)
        stillAtTheStart("$name back", backFrames, null)
        settledAndStill("$name back", backFrames, "$name-back-$tag", back.ended)
        for (role in moving) assertTrue("$name back: $role never matched; matched ${back.matched}", "$key/$role" in back.matched)
        assertTrue("$name back: the container was drawn in only ${back.rects.size} frames", back.rects.size >= 8)
        assertTrue("$name back: it does not shrink steadily ${back.grown}", back.grown.zipWithNext().all { (a, b) -> b <= a + 0.001f })
        assertTrue("$name back: it starts at ${back.grown.first()}", back.grown.first() > 0.4f)
        assertTrue("$name back: it ends at ${back.grown.last()}", back.grown.last() < 0.45f)
        val now = cardRect(key)
        assertTrue("$name back: the card moved from $card to $now", Math.abs(card.top - now.top) < 1f && Math.abs(card.left - now.left) < 1f)
        saveStrip("$name-compare-$tag", listOf(openFrames.first(), backFrames.last()), 2)
        val before = pixels(openFrames.first())
        val after = pixels(backFrames.last())
        val differing = before.indices.count { diff(before[it], after[it]) > 12 }
        println("$name back: $differing pixels differ from before the tap")
        assertTrue("$name back: $differing pixels differ from before the tap", differing * 1000 < before.size)
        // The first page's last frame of the move is its frame at rest.
        val lastMove = pixels(backFrames[backFrames.size - 2])
        val settled = pixels(backFrames.last())
        val inMotion = lastMove.indices.count { diff(lastMove[it], settled[it]) > 12 }
        assertTrue("$name back: $inMotion pixels still differ after the move", inMotion * 1000 < lastMove.size)
        scenario.close()
    }

    private fun latest(exercise: String?) = model.workouts.value.filter { w -> exercise == null || w.blocks.any { it.exercise == exercise } }.maxOf { it.date }
    private val openHistoryByCard = { compose.onNodeWithTag("card:progress:history", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }

    /** The History page's newest workout card into its workout's page, the History page opened by a blink (no tab card). */
    @Test fun historyCardToWorkout() = pageCardMorph("history-card", { "page:history:" + latest(null) }, { compose.runOnIdle { model.open(Screen.History) } })
    /** The same with the History page opened by Progress's History card (its rows are ready for Back to that card). */
    @Test fun historyCardToWorkoutFromCard() = pageCardMorph("history-card-from-card", { "page:history:" + latest(null) }, { openHistoryByCard() })
    /** An exercise page's History tab: a session's card into the workout's page. */
    @Test fun exerciseSessionToWorkout() = pageCardMorph(
        "exercise-session", { "page:exercise:Bench Press (Barbell):" + latest("Bench Press (Barbell)") },
        { compose.runOnIdle { model.open(Screen.ExerciseDetail("Bench Press (Barbell)", tab = 1)) } },
    )

    @Test fun progressHistoryRows() = historyRows(null)

    /** A page opened from a card, a page over it and Back to it is the blink (the card's elements do not fly again); Back from it is the morph. */
    @Test fun deeperPageAndBack() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        val opened = mutableListOf<Bitmap>()
        trail({ compose.onNodeWithTag("card:progress:body", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, opened)
        blink("body-deeper") { model.open(Screen.MuscleDetail(clean.core.Muscle.entries.first())) }
        // The Body page is no longer a card but the figure on the page, the figure and the segmented switch show at once on the page, so the estimate of the two pages crossing reads higher.
        blink("body-deeper-back", overlap = 0.35f) { model.back() }
        val back = trail({ model.back() }, mutableListOf())
        assertTrue("back to the card after a deeper page: the container was drawn in only ${back.rects.size} frames", back.rects.size >= 8)
        scenario.close()
    }

    /** A card that is no longer in the tab shown (or has been scrolled away) cannot be landed on: Back is the blink, the container is never drawn. */
    @Test fun backWithoutTheCardIsTheBlink() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        trail({ compose.onNodeWithTag("card:progress:body", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, mutableListOf())
        compose.runOnIdle { model.stack.clear(); model.tab = Tab.Home }
        compose.waitForIdle()
        compose.runOnIdle { model.open(Screen.Body) }
        compose.waitForIdle()
        val draws = MorphProbe.clip!!.draws
        trail({ model.back() }, mutableListOf())
        assertTrue("the container was drawn on a Back with no card to land on", MorphProbe.clip!!.draws == draws)
        scenario.close()
    }

    /** With the app's animations off a card's page is there on the next frame, and Back too. */
    @Test fun animationsOffCut() {
        seedOff = true
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        val frames = mutableListOf<Bitmap>()
        val draws = MorphProbe.clip?.draws ?: 0
        trail({ compose.onNodeWithTag("card:progress:body", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, frames)
        val rest = pixels(frames.last())
        val soon = pixels(frames[3])
        assertTrue("the page was not there at once", soon.indices.count { diff(soon[it], rest[it]) > 12 } * 200 < rest.size)
        assertTrue("the container was drawn with animations off", (MorphProbe.clip?.draws ?: 0) == draws)
        scenario.close()
    }
    private var seedOff = false

    /** A sideways swipe on the figure whose description contains [description]: the finger goes left, as for the next view. */
    private fun swipeFigure(description: String) {
        compose.onNode(androidx.compose.ui.test.hasContentDescription(description, substring = true), useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-60f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-120f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-80f, 0f))
            up()
        }
    }

    /**
     * A swipe between the body figure's views: the colours do not crossfade, they slide (so a frame in the middle is neither the first
     * nor the last), and a tap on a chip does not slide (it is a cut, as before).
     */
    private fun slideCheck(name: String, description: String, open: () -> Unit) {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        open()
        compose.waitForIdle()
        waitReal("the body figures") { model.muscleSetCounts.value.isNotEmpty() && model.muscleFigures.value != null }
        compose.mainClock.advanceTimeBy(1500)
        val frames = run({ swipeFigure(description) }, 480)
        saveStrip("$name-swipe", pick(frames, 2, 12), 6)
        val first = pixels(frames.first())
        val last = pixels(frames.last())
        val w = frames.first().width
        val changed = first.indices.count { diff(first[it], last[it]) > 12 }
        assertTrue("$name: swiping changed nothing", changed * 100 > first.size)
        // Somewhere in the first 300 ms a frame is a mix: not yet the last, no longer the first, and it never shows a pop (the frame after the swipe is the first, nearly).
        val mid = frames.drop(1).take(20).any { f -> pixels(f).let { p -> p.indices.count { diff(p[it], first[it]) > 12 } * 200 > p.size && p.indices.count { diff(p[it], last[it]) > 12 } * 200 > p.size } }
        assertTrue("$name: no frame between the two views", mid)
        val soon = pixels(frames[1])
        assertTrue("$name: the first frame after the swipe already differs a lot from before (a pop)", soon.indices.count { diff(soon[it], first[it]) > 12 } * 10 < soon.size)
        println("$name: swipe changed ${changed * 100 / first.size}% of the pixels, width $w")
        scenario.close()
    }


    /** Long names on the History mini card (the user's own: "Shoulder (Follow Along Day)", "Lying Leg Curl (Machine)") and, on the History page, on its cards. */
    private fun longNames() {
        addWorkout(1, "Lying Leg Curl (Machine)", 3, 40, name = "Shoulder (Follow Along Day)")
        addWorkout(2, "Lying Leg Curl (Machine)", 3, 42, name = "Shoulder (Follow Along Day)")
        waitReal("the long-named workouts") { model.workouts.value.any { it.name.startsWith("Shoulder") } }
        compose.waitForIdle()
    }

    /** The near-white ink of the frame (names are white, the star counts blue and the dates grey) inside [band], outside [box] horizontally. */
    private fun inkOutside(f: Bitmap, band: Rect, box: Rect): Int {
        val p = pixels(f)
        var n = 0
        for (y in band.top.toInt().coerceAtLeast(0) until band.bottom.toInt().coerceAtMost(f.height)) for (x in band.left.toInt().coerceAtLeast(0) until band.right.toInt().coerceAtMost(f.width)) {
            if (x >= box.left - 1 && x <= box.right + 1) continue
            val c = p[y * f.width + x]
            if ((c shr 16 and 255) > 225 && (c shr 8 and 255) > 225 && (c and 255) > 225) n++
        }
        return n
    }

    /**
     * Progress's History card with long names, opening and Back: the frame after the tap is the frame before it, the card's names stay cut
     * short ("Shoulder (Follow Al...") in every frame (the page's own are whole), no name's ink is outside its moving box while the card's words
     * show, and the frames after the move are the page at rest.
     */
    @Test fun historyMiniCardWithLongNames() {
        val scenario = launch()
        longNames()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        scrollTo("progress:history")
        compose.waitForIdle()
        val key = "progress:history"
        val date = model.workouts.value.filter { it.name.startsWith("Shoulder") }.maxOf { it.date }
        val nameKey = "$key/row:name/$date/card"
        val card = cardRect(key)
        assertTrue("the long name is not cut short on the card at rest: ${MorphProbe.texts[nameKey]?.overflowed} ${MorphProbe.texts.keys.filter { it.startsWith(key) }}", MorphProbe.texts[nameKey]?.overflowed == true)
        val kept = mutableListOf<Boolean>()
        val stray = mutableListOf<String>()
        val frames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, frames, perFrame = {
            kept += MorphProbe.texts[nameKey]?.overflowed == true
            val box = MorphProbe.boxes[nameKey]
            // While the card's words are the ones on show (before the container is half grown), none of their ink is outside their box.
            if (box != null && MorphProbe.clip!!.grown < 0.3f) {
                val n = inkOutside(frames.last(), Rect(card.left, box.top, card.right, box.bottom), box)
                if (n > 0) stray += "frame ${frames.size}: $n px of ink outside $box"
            }
        })
        saveStrip("history-long-open", pick(frames, 2, 17), 6)
        saveStrip("history-long-open-big", listOf(1, 2, 3, 4, 5, 6, 8, 10, 14).map { frames[it].let { f -> crop(f, Rect(card.left - 4, card.top - 4, card.right + 4, card.bottom + 4)) } }, 3, 1)
        stillAtTheStart("history long names open", frames, card, androidx.compose.ui.geometry.Offset(card.left + 8f, card.top + 8f))
        settledAndStill("history long names open", frames, "history-long-open", open.ended)
        assertTrue("the card's long name was set differently during the move (not cut short in ${kept.count { !it }} frames)", kept.all { it })
        assertTrue("name ink outside its box: $stray", stray.isEmpty())
        val back = mutableListOf<Bitmap>()
        val shrink = trail({ model.back() }, back)
        saveStrip("history-long-back", pick(back, 2, 17), 6)
        stillAtTheStart("history long names back", back, null)
        settledAndStill("history long names back", back, "history-long-back", shrink.ended)
        scenario.close()
    }

    /**
     * The History page's card into its workout's page with long names: what the card and the page have in common (the name, the date, the
     * duration, the volume, the PR count, the exercise's name and its estimate) travels both ways; the frame after the tap is the frame
     * before it and the frames after the move are the page at rest (the helper's checks).
     */
    @Test fun historyCardToWorkoutSharedPieces() = pageCardMorph(
        "history-card-pieces", { "page:history:" + latest(null) }, { compose.runOnIdle { model.open(Screen.History) } },
        moving = listOf("title", "date", "duration", "volume", "prs", "ex/0", "est/0"), setup = { longNames() },
    )

    /**
     * The rows of Progress's History card travelling to their cards (and back): in no frame do the name's, the date's and the PR count's boxes of one row
     * overlap, and no two of them are closer than they were at the start of the way (the date goes down to its line first and then sideways, the PR
     * count up first and then right: [Motion.legged]).
     */
    @Test fun historyRowsNeverCrossOnTheirWay() {
        val scenario = launch()
        longNames()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        scrollTo("progress:history")
        compose.waitForIdle()
        val key = "progress:history"
        val dates = model.workouts.value.sortedByDescending { it.date }.take(4).map { it.date }
        val roles = listOf("row:name", "row:prs")
        fun snapshot() = HashMap(MorphProbe.boxes.filterKeys { k -> k.startsWith("$key/row:") && roles.any { k.startsWith("$key/$it/") } })
        val atRow = snapshot()
        val openShots = mutableListOf<Map<String, Rect>>()
        val openFrames = mutableListOf<Bitmap>()
        trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames, perFrame = { openShots += snapshot() })
        val atCard = snapshot()
        saveStrip("history-rows-legs-open", pick(openFrames, 2, 17), 6)
        val backShots = mutableListOf<Map<String, Rect>>()
        val backFrames = mutableListOf<Bitmap>()
        trail({ model.back() }, backFrames, perFrame = { backShots += snapshot() })
        saveStrip("history-rows-legs-back", pick(backFrames, 2, 17), 6)
        // Where each box is at time t on its way, as the legs say ([Motion.legged]): no two boxes of one row overlap at any time, either way.
        val overlaps = mutableListOf<String>()
        for (forward in listOf(true, false)) for (date in dates) {
            val tracks = roles.mapNotNull { role ->
                val row = atRow["$key/$role/$date/card"] ?: return@mapNotNull null
                val card = atCard["$key/$role/$date/page"] ?: return@mapNotNull null
                val from = if (forward) row else card
                val to = if (forward) card else row
                role to { p: Float -> Motion.leggedAt(from, to, forward, p) }
            }
            assertTrue("no row boxes were found for $date", tracks.isNotEmpty())
            for (step in 0..200) for (a in tracks.indices) for (b in a + 1 until tracks.size) {
                val p = step / 200f
                val ra = tracks[a].second(p)
                val rb = tracks[b].second(p)
                val w = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
                val h = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
                if (w > 1f && h > 1f) overlaps += "${if (forward) "open" else "back"} $date at $p: ${tracks[a].first} $ra and ${tracks[b].first} $rb overlap by $w x $h"
            }
        }
        assertTrue("boxes of one row overlap on their way: ${overlaps.take(6)}", overlaps.isEmpty())
        // The real boxes do as the legs say: on opening, a date and a PR count stay at their x while they go to their line (the first 64 ms, the legs being on the emphasized clock).
        for (date in dates.take(1)) for (role in listOf("row:prs")) {
            val first = openShots.first()["$key/$role/$date/page"] ?: continue
            for (shot in openShots.take(4)) {
                val now = shot["$key/$role/$date/page"]!!
                assertTrue("$role moved sideways ${now.left - first.left} px before it had reached its line", Math.abs(now.left - first.left) < 1.5f)
            }
            // A row that already sits on the line of its card (a tighter card can put them within a pixel or two) has nowhere to go.
            val line = atCard["$key/$role/$date/page"]!!.top
            assertTrue("$role never went to its line", Math.abs(openShots.last()["$key/$role/$date/page"]!!.top - first.top) > 3f || Math.abs(first.top - line) <= 3f)
        }
        scenario.close()
    }

    /**
     * A swipe between the body figure's views on Progress's Body card: the new coloured figure comes in from the card's side, over the card
     * and not only the body (coloured pixels appear beyond the figure's final extent, inside the card), the old one goes out the other way
     * (coloured pixels beyond its first extent, inside the card), and nothing outside the card changes.
     */
    @Test fun bodySwipeColourEntersFromTheCardEdge() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        scrollTo("progress:body")
        compose.waitForIdle()
        waitReal("the body figures") { model.muscleSetCounts.value.isNotEmpty() && model.muscleFigures.value != null }
        compose.mainClock.advanceTimeBy(1500)
        val card = cardRect("progress:body")
        val frames = run({ swipeFigure("Body figure, ") }, 480)
        saveStrip("body-edge-swipe", pick(frames, 2, 12), 6)
        saveStrip("body-edge-swipe-big", listOf(1, 2, 3, 4, 6, 9, 14, 20).map { crop(frames[it], Rect(card.left - 4, card.top - 4, card.right + 4, card.bottom + 4)) }, 4, 1)
        fun saturated(f: Bitmap): List<Pair<Int, Int>> {
            val p = pixels(f)
            val out = mutableListOf<Pair<Int, Int>>()
            for (y in card.top.toInt() + 2 until card.bottom.toInt() - 2) for (x in card.left.toInt() + 2 until card.right.toInt() - 2) {
                val c = p[y * f.width + x]
                val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
                if (maxOf(r, g, b) - minOf(r, g, b) > 70) out += x to y
            }
            return out
        }
        val firstXs = saturated(frames.first()).map { it.first }
        val lastXs = saturated(frames.last()).map { it.first }
        assertTrue("no colour on the card before or after the swipe", firstXs.isNotEmpty() && lastXs.isNotEmpty())
        val right = maxOf(firstXs.max(), lastXs.max())
        val left = minOf(firstXs.min(), lastXs.min())
        var comesIn = false
        var goesOut = false
        for (f in frames.drop(1).take(24)) {
            val xs = saturated(f).map { it.first }
            if (xs.any { it > right + 4 }) comesIn = true
            if (xs.any { it < left - 4 }) goesOut = true
        }
        assertTrue("the new colours never showed to the right of the figure, from the card's edge", comesIn)
        assertTrue("the old colours never went out over the card on the left", goesOut)
        // Nothing outside the card changes (the History card beside it, the page around it).
        val before = pixels(frames.first())
        val w = frames.first().width
        for ((i, f) in frames.withIndex()) {
            val p = pixels(f)
            val off = p.indices.filter { diff(p[it], before[it]) > 40 && (it % w < card.left - 1 || it % w > card.right + 1 || it / w < card.top - 1 || it / w > card.bottom + 1) && it / w < 700 }
            assertTrue("frame $i: ${off.size} pixels outside the card changed, x ${off.minOfOrNull { it % w }}..${off.maxOfOrNull { it % w }}, y ${off.minOfOrNull { it / w }}..${off.maxOfOrNull { it / w }}", off.isEmpty())
        }
        scenario.close()
    }

    @Test fun swipeMiniFigure() = slideCheck("body-mini", "Body figure, ") { }
    @Test fun swipePageFigure() = slideCheck("body-page", "Body figure:") { compose.runOnIdle { model.open(Screen.Body) } }

    // ---- the workout screen's moves (stage F2) ----

    private fun openWorkout(blocks: List<DraftBlock>): ActivityScenario<MainActivity> {
        val scenario = launch()
        compose.runOnIdle {
            model.setOption("rest.seconds", "0")
            model.putDraft(Draft("Workout", System.currentTimeMillis(), blocks))
            model.open(Screen.Active)
        }
        compose.waitForIdle()
        return scenario
    }

    private fun setsOf(vararg done: Boolean) = done.map { DraftSet(clean.core.SetType.NORMAL, "60", "8", it) }

    private fun described(text: String, substring: Boolean = false) = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(text, substring = substring), useUnmergedTree = true)

    private fun textNodes(text: String, substring: Boolean = false) = compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring), useUnmergedTree = true)

    /** Steps [ms] after [act] one frame at a time, giving each frame with [probe]'s reading of it (the first reading is before [act]). */
    private fun <T> stepped(act: () -> Unit, ms: Int, probe: () -> T): Pair<List<Bitmap>, List<T>> {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        val readings = mutableListOf(probe())
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) { compose.mainClock.advanceTimeBy(16); frames += frame(); readings += probe(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        readings += probe()
        compose.mainClock.autoAdvance = true
        return frames to readings
    }

    private fun top(nodes: androidx.compose.ui.test.SemanticsNodeInteractionCollection): Float? = nodes.fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

    @Test fun workoutTickGlideAndSweep() {
        val scenario = openWorkout(listOf(DraftBlock("Bench Press (Barbell)", setsOf(false, false, false))))
        val tick1 = described("Set 1")[0].fetchSemanticsNode().boundsInRoot
        val tick2 = described("Set 2")[0].fetchSemanticsNode().boundsInRoot
        val y1 = tick1.center.y.toInt()
        val y2 = tick2.center.y.toInt()
        val (frames, _) = stepped({ described("Set 1")[0].performClick() }, 480) { 0 }
        saveStrip("workout-tick", pick(frames, 2, 13), 7)
        saveStrip("workout-tick-fine", frames.subList(0, 14), 7)
        saveStrip("workout-tick-rest", frames.takeLast(4), 4, 1)
        fun px(f: Bitmap, x: Int, y: Int) = f.getPixel(x, y)
        val right = 392
        val left = 6
        val start = frames.first()
        val end = frames.last()
        val lit = px(start, left, y1)
        assertTrue("the next set was not lit before the tick", diff(lit, px(start, left, y2)) > 12)
        assertTrue("the second set is not lit at the end", diff(px(end, left, y2), lit) < 14)
        val done = px(end, left, y1)
        assertTrue("the ticked row has no done tint at the end", diff(done, lit) > 12 && diff(done, px(start, left, y2)) > 12)
        // The tint reaches the tick's end of the row before the far end.
        val atRight = frames.indexOfFirst { diff(px(it, right, y1), px(end, right, y1)) < 8 }
        val atLeft = frames.indexOfFirst { diff(px(it, left, y1), done) < 8 }
        println("tint reaches the right at frame $atRight, the left at frame $atLeft")
        assertTrue("the tint did not spread from the tick: right at $atRight, left at $atLeft", atRight in 0 until atLeft)
        assertTrue("the tint did not take some frames: left at $atLeft", atLeft >= 8)
        // One highlight glides down: where the lit pixels of the left edge are, between the two rows.
        val centres = frames.map { f ->
            val ys = (y1 - 30..y2 + 30).filter { diff(px(f, left, it), lit) < 14 }
            if (ys.isEmpty()) null else ys.average()
        }
        println("highlight centre per frame: $centres")
        assertTrue("the highlight never travelled between the rows", centres.any { it != null && it > y1 + 8 && it < y2 - 8 })
        val seen = centres.filterNotNull()
        assertTrue("the highlight went back up: $centres", seen.zipWithNext().all { (a, b) -> b >= a - 1.0 })
        assertTrue("the highlight did not end on the second row: ${seen.last()}", Math.abs(seen.last() - y2) < 24)
        scenario.close()
    }

    @Test fun workoutTickCutWithAnimationsOff() {
        seedOff = true
        val scenario = openWorkout(listOf(DraftBlock("Bench Press (Barbell)", setsOf(false, false, false))))
        val (frames, _) = stepped({ described("Set 1")[0].performClick() }, 160) { 0 }
        val rest = pixels(frames.last())
        val soon = pixels(frames[3])
        val band = (frames.first().width * 250 until frames.first().width * 420).filter { it % frames.first().width < 340 }
        saveStrip("workout-tick-off", listOf(frames[0], frames[1], frames[2], frames[3], frames.last()), 5, 1)
        println("animations off: " + band.count { diff(soon[it], rest[it]) > 12 } + " of " + band.size + " differ at frame 3")
        assertTrue("the tick tint and the highlight were not at rest at once", band.count { diff(soon[it], rest[it]) > 12 } * 400 < band.size)
        scenario.close()
    }

    @Test fun workoutFinishedExerciseFolds() {
        val scenario = openWorkout(listOf(DraftBlock("Bench Press (Barbell)", setsOf(true, true, false))))
        val before = top(textNodes("Add exercise"))!!
        val (frames, ys) = stepped({ described("Set 3")[0].performClick() }, 480) { top(textNodes("Add exercise")) }
        saveStrip("workout-fold", pick(frames, 2, 13), 7)
        saveStrip("workout-fold-rest", frames.takeLast(4), 4, 1)
        println("fold: Add exercise at $ys")
        val moved = ys.filterNotNull().let { all -> all.drop(all.indexOf(all.max())) }
        assertTrue("the rows did not fold up over frames: $moved", moved.zipWithNext().all { (a, b) -> b <= a + 0.5f } && moved.distinct().size >= 8)
        assertTrue("the exercise did not shrink", moved.last() < before - 60f)
        assertTrue("the summary line is not there", textNodes("× 8", substring = true).fetchSemanticsNodes().isNotEmpty())
        // Touching the line unfolds it again, the same way back.
        val (back, ys2) = stepped({ compose.onAllNodes(androidx.compose.ui.test.hasText("× 8", substring = true) and androidx.compose.ui.test.hasClickAction())[0].performClick() }, 480) { top(textNodes("Add exercise")) }
        saveStrip("workout-unfold", pick(back, 2, 13), 7)
        val grew = ys2.filterNotNull()
        println("unfold: Add exercise at $ys2")
        assertTrue("the rows did not unfold over frames: $grew", grew.zipWithNext().all { (a, b) -> b >= a - 0.5f } && grew.distinct().size >= 8)
        assertTrue("it did not come back to where it was: ${grew.last()} against $before", Math.abs(grew.last() - before) < 1f)
        scenario.close()
    }

    @Test fun workoutRemoveSetClosesTheGapAndUndoOpensIt() {
        val scenario = openWorkout(listOf(DraftBlock("Bench Press (Barbell)", setsOf(false, false, false))))
        val start = top(textNodes("Add exercise"))!!
        val original = model.draft!!
        val (frames, _) = stepped({ described("Set 2, normal", substring = true)[0].performClick() }, 400) { 0 }
        saveStrip("workout-remove-dialog", pick(frames, 2, 13), 7)
        assertTrue("the dialog did not open", textNodes("Remove set").fetchSemanticsNodes().isNotEmpty())
        val (gone, ys2) = stepped({ textNodes("Remove set")[0].performClick() }, 480) { top(textNodes("Add exercise")).toString() + "/" + model.draft!!.blocks[0].items.size }
        saveStrip("workout-remove", pick(gone, 2, 13), 7)
        saveStrip("workout-remove-fine", gone.subList(0, 14), 7)
        saveStrip("workout-remove-rest", gone.takeLast(4), 4, 1)
        println("remove sizes: $ys2"); val up = ys2.map { it!!.substringBefore("/").toFloat() }
        println("remove: Add exercise at $ys2")
        assertTrue("the gap did not close over frames: $up", up.zipWithNext().all { (a, b) -> b <= a + 0.5f } && up.distinct().size >= 8)
        assertTrue("the rows below did not rise by a row: ${up.first()} to ${up.last()}", up.first() - up.last() > 40f)
        assertTrue("the set was not removed", model.draft!!.blocks[0].items.size == 2)
        val shut = up.last()
        val (back, ys3) = stepped({ compose.runOnIdle { model.putDraft(original) } }, 480) { top(textNodes("Add exercise")) }
        saveStrip("workout-undo", pick(back, 2, 13), 7)
        saveStrip("workout-undo-fine", back.subList(0, 14), 7)
        saveStrip("workout-undo-rest", back.takeLast(4), 4, 1)
        val down = ys3.filterNotNull()
        println("undo: Add exercise at $ys3")
        assertTrue("the gap did not open over frames: $down", down.zipWithNext().all { (a, b) -> b >= a - 0.5f } && down.distinct().size >= 8)
        assertTrue("it did not end where it started: ${down.last()} against $start", Math.abs(down.last() - start) < 1f && down.first() <= shut + 1f)
        scenario.close()
    }

    @Test fun workoutChangeTypeDialogGrowsFromTheNumber() {
        val scenario = openWorkout(listOf(DraftBlock("Bench Press (Barbell)", setsOf(false, false, false)), DraftBlock("Squat (Barbell)", setsOf(false, false))))
        val number = described("Set 2, normal", substring = true)[0].fetchSemanticsNode().boundsInRoot
        val (frames, boxes) = stepped({ described("Set 2, normal", substring = true)[0].performClick() }, 480) {
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        }
        saveStrip("workout-dialog-open", pick(frames, 2, 13), 7)
        saveStrip("workout-dialog-rest", frames.takeLast(4), 4, 1)
        val last = boxes.last()
        println("dialog items: $boxes")
        assertTrue("the dialog is not there at rest", last != null)
        // In the middle of the width and clear of the set it belongs to.
        assertTrue("the dialog is not centred: $last", Math.abs(last!!.center.x - 200f) < 60f)
        assertTrue("the dialog covers the set: $last against ${number.top}..${number.bottom}", last.top > number.bottom || last.bottom < number.top)
        // The set stays lit and in view: its pixels are as before the dialog.
        val end = frames.last()
        val rowY = number.center.y.toInt()
        assertTrue("the set's row was dimmed by the scrim", diff(end.getPixel(6, rowY), frames.first().getPixel(6, rowY)) < 14)
        // Closing shrinks it back into the number.
        val (closing, boxes2) = stepped({ scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }, 480) {
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        }
        saveStrip("workout-dialog-close", pick(closing, 2, 13), 7)
        assertTrue("the dialog did not close", boxes2.last() == null)
        scenario.close()
    }

    @Test fun calendarEndsAtToday() {
        val scenario = launch()
        compose.runOnIdle { model.open(Screen.Calendar()) }
        compose.waitForIdle()
        saveStrip("calendar-ends-today", listOf(frame()), 1, 1)
        val today = LocalDate.now().dayOfMonth
        val numbers = compose.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text?.takeIf { it.matches(Regex("\\d{1,2}")) }?.let { it.toInt() to n.boundsInRoot } }
        val last = numbers.filter { it.first == today }.maxByOrNull { it.second.top }!!
        val below = numbers.filter { it.second.top > last.second.top + 1f }.map { it.first }
        println("today $today at ${last.second}; day numbers below it: $below")
        assertTrue("days after today are drawn: $below", below.isEmpty())
        assertTrue("a later day of the month is drawn on today's row", numbers.none { it.first > today && Math.abs(it.second.top - last.second.top) < 2f && it.second.left > last.second.left })
        scenario.close()
    }

    /**
     * Round 41 (I-169): a swipe between tabs goes on at the finger's speed when the finger lifts and eases onto the page. The stock snap held still for the
     * first frame after the release and then sped past the finger (16, 0, 14, 20, 23 px a frame), a lurch at every release in the user's recording. Each pair
     * of tabs, both ways: the first frame after the release moves, no frame after it is faster than the finger was, the page never turns back, and it lands.
     */
    @Test fun aReleasedSwipeGoesOnAtTheFingersSpeed() {
        val scenario = launch()
        compose.runOnIdle { model.seen["wave:activity"] = true; model.seen["wave:calendar"] = true }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(3000); compose.waitForIdle()
        val page = compose.onNodeWithTag("page:tabs", useUnmergedTree = true)
        for ((dir, to) in listOf(-1 to Tab.Progress, -1 to Tab.Exercises, -1 to Tab.Profile, 1 to Tab.Exercises, 1 to Tab.Progress, 1 to Tab.Home)) {
            compose.mainClock.autoAdvance = false
            val at = mutableListOf(PagerWatch.pos)
            // Above the cards (the Body figure takes a sideways drag of its own).
            page.performTouchInput { down(androidx.compose.ui.geometry.Offset(if (dir < 0) 360f else 40f, 30f)) }
            repeat(14) {
                page.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(dir * 16f, 0f)) }
                compose.mainClock.advanceTimeBy(16); compose.waitForIdle(); at += PagerWatch.pos
            }
            page.performTouchInput { up() }
            repeat(40) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle(); at += PagerWatch.pos }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
            val steps = at.zipWithNext { x, y -> (y - x) * 400f }
            val finger = abs(steps[13])
            val after = steps.drop(14)
            println("$to steps ${steps.map { it.roundToInt() }}")
            assertEquals("the swipe lands on $to", to, model.tab)
            assertTrue("the first frame after the release moves the page (${after[0]})", abs(after[0]) >= 1f)
            assertTrue("no frame after the release is faster than the finger ($finger px): $after", after.all { abs(it) <= finger + 1f })
            assertTrue("the page never turns back: $after", after.all { it * -dir >= -0.5f })
        }
        scenario.close()
    }

    /**
     * Round 41 (I-171): at the end of a swipe the dock's capsule goes on from where the swipe left it. It drew the tab the swipe came from for one frame (the
     * glide's old state, between the swipe's end and the effect that hands over to the glide): Library to Progress flashed back to Library near the end.
     */
    @Test fun theCapsuleNeverFlashesBackAtTheEndOfASwipe() {
        val scenario = launch()
        compose.runOnIdle { model.seen["wave:activity"] = true; model.seen["wave:calendar"] = true; model.tab = Tab.Exercises }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(3000); compose.waitForIdle()
        val page = compose.onNodeWithTag("page:tabs", useUnmergedTree = true)
        for ((dir, to) in listOf(1 to Tab.Progress, -1 to Tab.Exercises, -1 to Tab.Profile, 1 to Tab.Exercises)) {
            val start = TAB_ORDER.indexOf(model.tab).toFloat()
            val end = TAB_ORDER.indexOf(to).toFloat()
            compose.mainClock.autoAdvance = false
            val at = mutableListOf<Float>()
            page.performTouchInput { down(androidx.compose.ui.geometry.Offset(if (dir < 0) 360f else 40f, 30f)) }
            repeat(14) {
                page.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(dir * 16f, 0f)) }
                compose.mainClock.advanceTimeBy(16); compose.waitForIdle(); frame(); at += PagerWatch.capsule
            }
            page.performTouchInput { up() }
            repeat(60) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle(); frame(); at += PagerWatch.capsule }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
            println("$to capsule ${at.map { (it * 100).roundToInt() / 100f }}")
            assertEquals(to, model.tab)
            // Once on its way, the capsule only moves toward the new tab: no frame lies back toward the old one by more than a hair.
            val toward = at.map { (it - start) / (end - start) }
            var furthest = 0f
            for ((i, x) in toward.withIndex()) {
                assertTrue("frame $i: the capsule went back toward the old tab ($x after $furthest): $toward", x >= furthest - 0.02f)
                furthest = maxOf(furthest, x)
            }
            assertTrue("it ends on the new tab (${at.last()})", abs(at.last() - end) < 0.02f)
        }
        scenario.close()
    }

    @Test fun tabSwitch() {
        val scenario = launch()
        // The Activity calendars' first fill (Progress's and Home's) is a wave (activityWave); this is the plain blink, with that first time used up.
        // Both waves are played out first (visit Progress, come back), so the tab change itself is the plain blink.
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { model.tab = Tab.Home }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(3000)
        blink("tab-home-progress") { model.tab = Tab.Progress }
        blink("tab-progress-library", overlap = 0.15f) { model.tab = Tab.Exercises }
        blink("tab-library-profile") { model.tab = Tab.Profile }
        scenario.close()
    }

    /**
     * Round 4b: Activity is one fixed element under Home and Progress. At rest it starts at the same y on both; while the pager is dragged between
     * them its pixels do not move (the block is drawn once above the pager), and once settled on Progress it is still where it was.
     */
    @Test fun activityDoesNotMoveBetweenHomeAndProgress() {
        val scenario = launch()
        compose.runOnIdle { model.seen["wave:activity"] = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
        val homeY = model.activityY.getValue(Tab.Home).floatValue
        val progressY = model.activityY.getValue(Tab.Progress).floatValue
        assertTrue("Activity's top is not known on both pages: $homeY, $progressY", !homeY.isNaN() && !progressY.isNaN())
        assertTrue("Activity starts at y=$homeY on Home and y=$progressY on Progress", Math.abs(homeY - progressY) <= 1f)
        val density = window!!.resources.displayMetrics.density
        // The title, the switch and the calendar's rows.
        val block = Rect(0f, homeY, window!!.width.toFloat(), homeY + 200f * density)
        val rest = frame()
        val page = compose.onNodeWithTag("page:tabs", useUnmergedTree = true)
        val left = androidx.compose.ui.geometry.Offset(-40f, 0f)
        // The finger goes down above Activity (on Home's header): a drag that starts on Activity scrolls its calendar, so the pager takes this one.
        page.performTouchInput { down(androidx.compose.ui.geometry.Offset(300f, 100f)); moveBy(left); moveBy(left); moveBy(left) }
        compose.waitForIdle()
        assertTrue("Activity is not drawn once above the pager while it moves", model.activityOverlay.value)
        val early = differing(frame(), rest, block)
        page.performTouchInput { moveBy(left); moveBy(left); moveBy(left) }
        compose.waitForIdle()
        val later = differing(frame(), rest, block)
        println("Activity pixels moved mid-swipe: $early, $later of ${(block.width * block.height).toInt()}")
        assertTrue("Activity moved with the pages ($early / $later pixels differ)", early < 400 && later < 400)
        page.performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertTrue("the overlay is still up after the pager settled", !model.activityOverlay.value)
        val settled = differing(frame(), rest, block)
        assertTrue("Activity is not where it was once Progress is shown ($settled pixels differ)", settled < 400)
        scenario.close()
    }

    @Test fun activityToCalendarAndBack() {
        val scenario = launch()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        // The calendar's first fill is a wave (calendarWave); this is the blink with that first time used up.
        compose.runOnIdle { model.seen["wave:calendar"] = true }
        blink("calendar-open") { model.open(Screen.Calendar()) }
        blink("calendar-back") { model.back() }
        scenario.close()
    }

    // ---- stage F3: numbers rolling, the range stretching, the ring and days, the calendar's wave, the figure's colours, a page settling in ----

    /** Adds one finished workout [daysAgo] days back (at 08:00) of [sets] sets of [lift], through the same path the seed uses. */
    private fun addWorkout(daysAgo: Long, lift: String, sets: Int, kg: Int, number: Int = 500 + daysAgo.toInt(), name: String = "Extra") {
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val date = LocalDateTime.now().minusDays(daysAgo).withHour(8).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val rows = (1..sets).map { set -> "\"$number\";\"$date\";\"$name\";\"3600\";\"$lift\";\"$set\";\"$kg.0\";\"10\";\"\";\"\";\"\";\"\";\"\"" }
        val bytes = (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>()))
            val id = model.activeId.value!!
            repo.commit(id, repo.previewWorkouts(id, bytes))
        }
    }

    /** Waits (in real time, the paused clock aside) until [done] holds. */
    private fun waitReal(what: String, done: () -> Boolean) {
        var waited = 0
        while (!done() && waited++ < 800) { Thread.sleep(25); compose.waitForIdle() }
        assertTrue("never happened: $what", done())
    }

    /** Like [run] but gives the background work a moment between frames (the figures and totals are worked out off the main thread). */
    private fun runLive(act: () -> Unit, ms: Int = 800): List<Bitmap> {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) { compose.mainClock.advanceTimeBy(16); Thread.sleep(6); frames += frame(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return frames
    }

    /** How many pixels of [region] in [a] differ from [b]. */
    private fun differing(a: Bitmap, b: Bitmap, region: Rect): Int {
        val pa = pixels(a); val pb = pixels(b)
        var n = 0
        for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(a.height)) for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(a.width)) {
            if (diff(pa[y * a.width + x], pb[y * a.width + x]) > 12) n++
        }
        return n
    }

    private fun crop(f: Bitmap, r: Rect): Bitmap {
        val x = r.left.toInt().coerceIn(0, f.width - 1)
        val y = r.top.toInt().coerceIn(0, f.height - 1)
        return Bitmap.createBitmap(f, x, y, r.width.toInt().coerceAtMost(f.width - x).coerceAtLeast(1), r.height.toInt().coerceAtMost(f.height - y).coerceAtLeast(1))
    }

    @Test fun weightRangeStretches() {
        val scenario = launch()
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        runBlocking {
            for ((back, kg) in listOf(300L to 80.0, 200L to 79.0, 130L to 77.5, 90L to 76.0, 50L to 75.2, 20L to 74.0, 12L to 74.6, 5L to 73.5, 1L to 73.0)) {
                assertTrue("weigh-in", model.addMeasurement("weight.csv", kg, LocalDateTime.now().minusDays(back).withHour(8).withMinute(0).withSecond(0).format(format)))
            }
        }
        compose.runOnIdle { model.setOption("ui.measurementRange", "30") }
        compose.waitForIdle()
        compose.runOnIdle { model.open(Screen.MeasurementDetail("weight.csv")) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        val chart = compose.onNode(androidx.compose.ui.test.hasContentDescription("Chart", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val region = Rect(chart.left, chart.top - 8f, chart.right, chart.bottom + 40f)
        for ((name, to) in listOf("longer" to "365", "shorter" to "30")) {
            val frames = run({ model.setOption("ui.measurementRange", to) }, 640)
            saveStrip("weight-range-$name", pick(frames, 2, 17).map { crop(it, region) }, 6)
            val first = frames.first(); val last = frames.last()
            assertTrue("weight range $name: nothing changed", differing(first, last, region) > 200)
            // A move, not a cut: frames in between are neither the old chart nor the new, and it settles.
            val mids = frames.indices.filter { differing(frames[it], last, region) > 80 && differing(frames[it], first, region) > 80 }
            assertTrue("weight range $name: it cut ($mids)", mids.size >= 6)
            val rest = frames.indices.filter { it > frames.size - 8 }
            assertTrue("weight range $name: the end is not still", rest.all { differing(frames[it], last, region) < 30 })
            // The chart is not redrawn from nothing: the line is there in the first frames of the move.
            val soon = frames[2]
            assertTrue("weight range $name: the chart went blank at the start", differing(soon, first, region) * 3 < region.width.toInt() * region.height.toInt())
            println("weight range $name: ${mids.size} frames of the move")
        }
        scenario.close()
    }

    /** Round 13: the shared Activity copy, kept composed while hidden, lay over the page's own card and swallowed a tap on the grid. */
    @Test fun activityGridTapOpensTheCalendar() {
        val scenario = launch(lastFirst = true)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        for (tab in listOf(Tab.Home, Tab.Progress)) {
            compose.runOnIdle { model.stack.clear(); model.tab = tab }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(2000); compose.waitForIdle()
            val nodes = compose.onAllNodesWithContentDescription("Activity chart", substring = true, useUnmergedTree = true)
            val i = nodes.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.width > 0f }
            nodes[i].performTouchInput { click(Offset(width - 20f, height * 0.5f)) }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500); compose.waitForIdle()
            assertTrue("a tap on $tab's Activity grid did not open the calendar: ${model.stack.toList()}", model.stack.lastOrNull() is Screen.Calendar)
        }
        scenario.close()
    }

    @Test fun calendarWave() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3000)
        val frames = run({ model.open(Screen.Calendar()) }, 1200)
        saveStrip("calendar-wave", pick(frames, 4, 17), 6)
        val rest = frames.last()
        val w = rest.width; val h = rest.height
        val upper = Rect(0f, h * 0.2f, w * 0.5f, h * 0.5f)
        val lower = Rect(w * 0.5f, h * 0.55f, w * 1f, h * 0.9f)
        // Squares in the top left have come further than those in the bottom right at some frame, and in the end everything is at rest.
        val total = Pair(maxOf(1, differing(frames.first(), rest, upper)), maxOf(1, differing(frames.first(), rest, lower)))
        val progress = frames.map { f -> (1f - differing(f, rest, upper).toFloat() / total.first) to (1f - differing(f, rest, lower).toFloat() / total.second) }
        val ahead = progress.indices.filter { progress[it].first - progress[it].second > 0.2f }
        assertTrue("the top left never ran ahead of the bottom right: ${progress.map { "%.2f/%.2f".format(it.first, it.second) }}", ahead.size >= 3)
        assertTrue("the wave is not over after 1.2 s", differing(frames[frames.size - 3], rest, Rect(0f, 0f, w.toFloat(), h.toFloat())) * 500 < w * h)
        // Later opens do not wave: Back, then open again, is at rest from the first frames the page is shown.
        compose.runOnIdle { model.back() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val again = run({ model.open(Screen.Calendar()) }, 480)
        val settled = pixels(again.last())
        saveStrip("calendar-second-open", pick(again, 2, 17), 6)
        println("second open differs from rest by " + again.map { f -> pixels(f).let { p -> p.indices.count { diff(p[it], settled[it]) > 12 } } })
        val late = again.drop(14)
        assertTrue("the second open waved", late.all { f -> pixels(f).let { p -> p.indices.count { diff(p[it], settled[it]) > 12 } * 100 < p.size } })
        scenario.close()
    }

    /** Idea 18: the figure's colours ease from the old to the new after a change, instead of snapping. */
    @Test fun figureColoursEase() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.open(Screen.Body) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2500)
        val figure = compose.onNode(androidx.compose.ui.test.hasContentDescription("Body figure:", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The workout is added and the figures worked out with the clock stopped, so the first frame stepped is the first that shows the new colours.
        compose.mainClock.autoAdvance = false
        val shot = frame()
        val before = model.muscleSetCounts.value
        addWorkout(0, "Squat (Barbell)", 12, 140)
        waitReal("the figures are worked out again") { model.muscleSetCounts.value != before }
        val frames = mutableListOf(shot)
        for (t in 0 until 1200 step 16) { compose.mainClock.advanceTimeBy(16); frames += frame() }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        saveStrip("figure-ease", pick(frames, 3, 24).map { crop(it, figure) }, 8)
        val first = frames.first(); val last = frames.last()
        assertTrue("the figure did not change at all", differing(first, last, figure) > 300)
        // Measured against the end: the figure goes through several different in-between looks, one after the other.
        val gaps = frames.map { differing(it, last, figure) }
        val start = gaps.indexOfFirst { it < gaps.first() - 100 }
        assertTrue("it never started", start > 0)
        val distinct = gaps.drop(start).takeWhile { it > 40 }.distinct().size
        assertTrue("the colours snapped: only $distinct looks between ($gaps)", distinct >= 4)
        scenario.close()
    }

    /** Idea 20: a page opened by the blink has its first cards rise one after the other; Back to it does not. */
    @Test fun pageSettlesIn() {
        val scenario = launch()
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        val frames = run({ model.open(Screen.History) }, 640)
        saveStrip("page-settle", pick(frames, 2, 23), 8)
        val rest = frames.last()
        val dates = model.workouts.value.asReversed().take(6).map { it.date }
        // The cards that are laid out on the screen (a taller phone would show all six).
        val rects = dates.mapNotNull { d -> compose.onAllNodes(androidx.compose.ui.test.hasTestTag("card:page:history:$d"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.takeIf { it.bottom < 800f } }
        assertTrue("only ${rects.size} cards on the screen", rects.size >= 3)
        // The frame in which a card first looks as it does at rest and stays so.
        val arrival = rects.map { r -> frames.indices.firstOrNull { i -> (i until frames.size).all { differing(frames[it], rest, r) < 40 } } ?: frames.size }
        println("arrival frames of the first six cards: $arrival")
        assertTrue("the cards did not come one after another: $arrival", arrival.zipWithNext().all { (a, b) -> b >= a } && arrival.last() - arrival.first() >= 5)
        // Back to the page is as it was, with nothing rising.
        compose.runOnIdle { model.open(Screen.WorkoutDetail(dates.first())) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val back = run({ model.back() }, 480)
        val after = pixels(back.last())
        for (i in 14 until back.size - 1) {
            val p = pixels(back[i])
            assertTrue("Back to History is still moving in frame $i", p.indices.count { diff(p[it], after[it]) > 12 } * 200 < p.size)
        }
        scenario.close()
    }

    // ---- stage F4: the banner into the workout, the tab pill, the exercise underline, a PR badge landing, the coin, the ruler ----


    /** The bounds of the tab bar's slot named [label]. */
    private fun slotRect(label: String): Rect =
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.top > 600f }

    /** The longest run of pixels of colour [colour] along row [y] of [f] between x [from] and [to] (gaps up to [gap] px allowed: an icon in the pill), as its first and last x. */
    private fun runOf(f: Bitmap, y: Int, from: Int, to: Int, colour: Int, gap: Int = 26): Pair<Int, Int>? {
        // A lone pixel is the page showing through the glass, not the indicator.
        val near = (from until to).filter { diff(f.getPixel(it, y), colour) < 10 }.toSet()
        val hits = near.filter { (it - 1) in near || (it + 1) in near }.sorted()
        if (hits.isEmpty()) return null
        var best = hits[0] to hits[0]
        var bestCount = 0
        var start = hits[0]
        var prev = hits[0]
        var count = 0
        for (x in hits) {
            if (x - prev > gap) { if (count > bestCount) { best = start to prev; bestCount = count }; start = x; count = 0 }
            prev = x; count++
        }
        if (count > bestCount) best = start to prev
        return best
    }

    /** Checks a moving indicator's edges over a change: starts at [first], ends at [last], stretches, its leading edge (right going right) is there first, and neither edge goes back. */
    private fun checkStretch(what: String, edges: List<Pair<Int, Int>>, first: Pair<Int, Int>, last: Pair<Int, Int>) {
        assertTrue("$what starts at $first, not ${edges.first()}", Math.abs(edges.first().first - first.first) <= 2 && Math.abs(edges.first().second - first.second) <= 2)
        assertTrue("$what ends at $last, not ${edges.last()}", Math.abs(edges.last().first - last.first) <= 2 && Math.abs(edges.last().second - last.second) <= 2)
        val rest = first.second - first.first
        val widest = edges.maxOf { it.second - it.first }
        assertTrue("$what never stretched: widest $widest against $rest", widest >= rest + 10)
        val lead = edges.indexOfFirst { it.second >= last.second - 3 }
        val trail = edges.indexOfFirst { it.first >= last.first - 3 }
        assertTrue("$what: the leading edge got there in frame $lead and the trailing one in $trail", lead in 0 until trail)
        assertTrue("$what: an edge went back, $edges", edges.zipWithNext().all { (a, b) -> b.first >= a.first - 2 && b.second >= a.second - 2 })
    }

    /**
     * The dock's highlight (round 4): none at rest; while the bar moves between tabs a capsule the full height of the bar stretches from the old
     * slot to the new one with its leading edge first, and goes again as it settles; it follows a swipe of the pager; with animations off there is none.
     */
    @Test fun tabHighlightStaysThenFades() {
        val scenario = launch()
        compose.runOnIdle { model.seen["wave:activity"] = true }
        compose.mainClock.advanceTimeBy(1500)
        val home = slotRect("Home")
        val progress = slotRect("Progress")
        val last = slotRect("Profile")
        val dp = home.height / 56f
        // A row 2 dp under the slot's top: inside the capsule (the slot is the bar's inside, so the capsule starts at its top) and clear of the icon, which is centred.
        val y = (home.top + 2 * dp).toInt()
        // Not from the slot's very left: the bar's round end curves in under that row.
        val from = (home.center.x - 0.25f * (progress.center.x - home.center.x)).toInt()
        val to = progress.right.toInt()
        val bar = Rect(0f, home.top - 8f, 400f, home.bottom + 8f)
        // The bar's own colour in that frame: a slot the capsule is not near (Profile).
        fun background(f: Bitmap) = f.getPixel(last.center.x.toInt(), y)
        /** The longest run of pixels that differ from the bar's colour along the row, as its first and last x; null with none. */
        fun capsule(f: Bitmap): Pair<Int, Int>? {
            val bg = background(f)
            val lit = (from until to).filter { diff(f.getPixel(it, y), bg) > 20 }.toSet()
            val hits = lit.filter { (it - 1) in lit || (it + 1) in lit }.sorted()
            if (hits.isEmpty()) return null
            var best = hits[0] to hits[0]; var bestLen = 0; var start = hits[0]; var prev = hits[0]
            for (x in hits) {
                if (x - prev > 3) { if (prev - start > bestLen) { best = start to prev; bestLen = prev - start }; start = x }
                prev = x
            }
            return if (prev - start > bestLen) start to prev else best
        }
        val rest = frame()
        assertNull("a highlight is drawn at rest: ${capsule(rest)}", capsule(rest))
        // A tap: Home to Progress.
        val frames = run({ model.tab = Tab.Progress }, 480)
        saveStrip("tab-highlight-tap", frames.step(2).take(16).map { crop(it, bar) }, 2, 1)
        val seen = frames.map(::capsule)
        // It stays for a while after the glide (round 5b) and is gone once the hold and the fade are over.
        assertNotNull("the highlight went as soon as the glide settled", seen[seen.size - 2])
        assertNull("a highlight is drawn long after settling: ${seen.last()}", seen.last())
        val widest = seen.withIndex().filter { it.value != null }.maxByOrNull { it.value!!.second - it.value!!.first }
        assertNotNull("no highlight appeared during the glide", widest)
        val born = seen.filterNotNull().first()
        assertTrue("the highlight did not stretch: ${widest!!.value} against $born as it appeared", widest.value!!.second - widest.value!!.first >= born.second - born.first + 20)
        // The leading edge (right) is there before the trailing one, and neither goes back.
        // (up to the widest: as it fades out the pixels that still differ from the bar shrink with it.)
        val edges = seen.take(widest.index + 1).filterNotNull()
        assertTrue("an edge went back: $edges", edges.zipWithNext().all { (a, b) -> b.first >= a.first - 2 && b.second >= a.second - 2 })
        // The capsule is the bar's full height: along the line between the two slots, from the slot's top to its bottom.
        val x = ((home.right + progress.left) / 2f).toInt()
        val settled = frames.last()
        fun column(f: Bitmap) = (home.top.toInt() until home.bottom.toInt()).filter { diff(f.getPixel(x, it), settled.getPixel(x, it)) > 20 }
        val rows = column(frames[widest.index])
        assertTrue("the highlight is not the bar's full height: rows ${rows.firstOrNull()}..${rows.lastOrNull()} of ${home.top}..${home.bottom}",
            rows.isNotEmpty() && rows.first() <= home.top + 4 * dp && rows.last() >= home.bottom - 4 * dp)
        // A swipe: back to Home first, then the finger drags the pager and the highlight goes with it.
        compose.runOnIdle { model.tab = Tab.Home }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val page = compose.onNodeWithTag("page:tabs", useUnmergedTree = true)
        val left = androidx.compose.ui.geometry.Offset(-30f, 0f)
        page.performTouchInput { down(androidx.compose.ui.geometry.Offset(300f, 100f)); moveBy(left); moveBy(left); moveBy(left) }
        compose.waitForIdle()
        val early = frame()
        val a = capsule(early)
        val more = androidx.compose.ui.geometry.Offset(-60f, 0f)
        page.performTouchInput { moveBy(more); moveBy(more) }
        compose.waitForIdle()
        val later = frame()
        val b = capsule(later)
        page.performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val held = capsule(frame())
        compose.mainClock.advanceTimeBy(2500)
        val landed = capsule(frame())
        saveStrip("tab-highlight-swipe", listOf(rest, early, later, frame()).map { crop(it, bar) }, 1, 1)
        println("highlight on a swipe: early $a, later $b, landed $landed")
        assertNotNull("the highlight did not follow the finger", a)
        assertNotNull("the highlight did not go on with the finger", b)
        assertTrue("the highlight did not go on with the finger: $b after $a", b!!.second > a!!.second + 8)
        assertNull("a highlight is drawn after the swipe: $landed", landed)
        // Animations off: a cut, and no highlight at any moment.
        compose.runOnIdle { model.setOption("ui.motion", "off") }
        compose.waitForIdle()
        val cut = run({ model.tab = Tab.Exercises }, 96)
        // A cut: the highlight shows for the hold and is then gone, with no glide in between.
        compose.mainClock.advanceTimeBy(2500)
        assertNull("with animations off a highlight is drawn after the hold", capsule(frame()))
        scenario.close()
    }

    /** The bounds of the exercise page's tab named [label]. */
    private fun exerciseTab(label: String): Rect =
        compose.onAllNodes(androidx.compose.ui.test.hasText(label) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().first().boundsInRoot

    /** Idea 14: the exercise page's underline glides and stretches between its tabs and follows a swipe, and the content slides with the finger. */
    @Test fun exerciseUnderlineGlidesAndTheContentSlides() {
        val scenario = launch()
        compose.runOnIdle { model.open(Screen.ExerciseDetail("Bench Press (Barbell)", tab = 0)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val info = exerciseTab("Info")
        val rest = frame()
        val y = info.bottom.toInt() - 1
        val line = rest.getPixel(info.center.x.toInt(), y)
        fun edges(f: Bitmap) = runOf(f, y, 0, 400, line, 2)!!
        val infoEdges = edges(rest)
        val history = exerciseTab("History")
        fun listLeft() = compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left ?: 0f
        // A tap: Info to History; the content comes in from the right.
        val (frames, lefts) = stepped({ compose.onAllNodes(androidx.compose.ui.test.hasText("History") and androidx.compose.ui.test.hasClickAction())[0].performClick() }, 480) { listLeft() }
        val strip = Rect(0f, info.top - 4f, 400f, info.bottom + 160f)
        saveStrip("exercise-underline-tap", frames.step(2).take(16).map { crop(it, strip) }, 4, 1)
        val tapEdges = frames.map(::edges)
        checkStretch("the underline on a tap", tapEdges, infoEdges, tapEdges.last())
        assertTrue("the underline ends under History: ${tapEdges.last()} against $history", Math.abs((tapEdges.last().first + tapEdges.last().second) / 2f - history.center.x) <= 3f)
        println("content left on a tap: ${lefts.step(2)}")
        assertTrue("the content did not slide in sideways: $lefts", lefts.maxOf { Math.abs(it) } >= 30f && Math.abs(lefts.last()) < 1f)
        // A swipe: the underline and the content go with the finger, and letting go steps on to Charts.
        val atHistory = tapEdges.last()
        val list = compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
        val left = androidx.compose.ui.geometry.Offset(-40f, 0f)
        list.performTouchInput { down(androidx.compose.ui.geometry.Offset(200f, 200f)); moveBy(left); moveBy(left) }
        compose.waitForIdle()
        val held = frame()
        val e = edges(held)
        list.performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val charts = exerciseTab("Charts")
        val landed = edges(frame())
        saveStrip("exercise-underline-swipe", listOf(rest, held, frame()).map { crop(it, strip) }, 3, 1)
        println("underline on a swipe: held $e, landed $landed")
        // The content went with the finger: the left edge of the page under the tabs is not what it is at rest on History.
        assertTrue("the content did not follow the finger", differing(held, frames.last(), Rect(0f, info.bottom + 10f, 60f, info.bottom + 150f)) > 100)
        assertTrue("the underline did not follow the finger (stretched): $e from $atHistory", e.second > atHistory.second + 10 && e.second - e.first >= (atHistory.second - atHistory.first) + 10)
        assertTrue("the underline did not land under Charts: $landed against $charts", Math.abs((landed.first + landed.second) / 2f - charts.center.x) <= 3f)
        scenario.close()
    }

    /** The count of light pixels of [region] in [f] (the shimmer's highlight lifts a badge's dark green or amber to a pale one). */
    private fun whites(f: Bitmap, region: Rect): Int {
        val p = pixels(f)
        var n = 0
        for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(f.height)) for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(f.width)) {
            val c = p[y * f.width + x]
            if ((c shr 16 and 255) > 120 && (c shr 8 and 255) > 120 && (c and 255) > 120) n++
        }
        return n
    }

    /** Idea 15: a PR badge earned by ticking a set lands with the bounce and one shimmer; a badge there when the screen opened does not. */
    @Test fun newPrBadgeLandsOnce() {
        // Lat Pulldown has a set done at the start (its badges are there when the screen opens); ticking Bench Press's first set earns new ones.
        fun set(done: Boolean) = DraftSet(clean.core.SetType.NORMAL, "100", "8", done)
        val scenario = openWorkout(listOf(DraftBlock("Lat Pulldown (Machine)", listOf(set(true), set(false))), DraftBlock("Bench Press (Barbell)", listOf(set(false), set(false)))))
        compose.mainClock.advanceTimeBy(1500)
        val old = described(", new PR", substring = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("the badges of the set done at the start are shown", old.isNotEmpty())
        val oldRegion = Rect(old.minOf { it.left } - 4f, old.minOf { it.top } - 4f, old.maxOf { it.right } + 4f, old.maxOf { it.bottom } + 4f)
        val (frames, _) = stepped({ described("Set 1")[1].performClick() }, 640) { 0 }
        val added = described(", new PR", substring = true).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.top > oldRegion.bottom - 6f }
        assertTrue("the ticked set earned badges", added.isNotEmpty())
        val region = Rect(added.minOf { it.left } - 6f, added.minOf { it.top } - 6f, added.maxOf { it.right } + 6f, added.maxOf { it.bottom } + 6f)
        saveStrip("pr-badge-land", frames.take(32).map { crop(it, region) }, 8, 1)
        saveStrip("pr-badge-land-rest", frames.takeLast(3).map { crop(it, region) }, 3, 1)
        val end = frames.last()
        // The badge that was there is as it was in every frame (the highlight and tint move in other parts of the row).
        assertTrue("a badge that was already there moved or flashed", frames.all { differing(it, end, oldRegion) < 40 })
        // The new one is small at first, bounces and is as at rest once the move is over, with a light highlight crossing it.
        assertTrue("the new badge was not small at first", differing(frames[2], end, region) > 40)
        val bright = frames.map { whites(it, region) }
        println("whites per frame: $bright")
        assertTrue("no highlight crossed the badge: $bright", bright.max() > bright.last() + 5)
        val settled = frames.indices.first { i -> (i until frames.size).all { differing(frames[it], end, region) < 30 } }
        println("the new badge is at rest from frame $settled")
        assertTrue("the badge was still moving late: frame $settled", settled in 6 until 45)
        scenario.close()
    }

    /** Idea 16: a badge, shield or streak earned on the summary flips in like a coin, once. */
    @Test fun earnedBadgeFlipsInLikeACoin() {
        val scenario = launch()
        addWorkout(0, "Bench Press (Barbell)", 3, 60)
        waitReal("the new workout is in the list") { model.workouts.value.any { it.name == "Extra" } }
        // A weekly target this one workout meets and without it the week does not.
        val monday = clean.core.weekStart(LocalDate.now()).toString()
        val inWeek = model.workouts.value.count { it.day >= monday }
        val id = model.activeId.value!!
        runBlocking { daoNow().put(Kv("goal.week:$id", "2020-01-06:$inWeek")) }
        waitReal("the target is in") { model.targets.value?.lastOrNull()?.target == inWeek }
        val date = model.workouts.value.first { it.name == "Extra" }.date
        // The summary's celebration is worked out off the main thread, so the clock is stepped with a moment of real time between frames; each frame says whether it is on screen yet.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        val shown = mutableListOf(false)
        fun celebrating() = compose.onAllNodes(androidx.compose.ui.test.hasText("Target hit: ", substring = true), useUnmergedTree = true)
        compose.runOnIdle { model.open(Screen.Summary(date)) }
        var t = 0
        while (t < 1600) { compose.mainClock.advanceTimeBy(16); Thread.sleep(6); frames += frame(); shown += celebrating().fetchSemanticsNodes().isNotEmpty(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        shown += true
        compose.mainClock.autoAdvance = true
        saveStrip("coin-full", pick(frames, 8, 12), 6)
        val appeared = shown.indexOf(true)
        assertTrue("the summary did not celebrate: $shown", appeared in 0 until frames.size - 2)
        val row = celebrating().fetchSemanticsNodes().first().boundsInRoot
        // The ring with its star, left of the words.
        val region = Rect(row.left - 90f, row.top - 24f, row.left, row.bottom + 24f)
        saveStrip("coin-flip", frames.drop(appeared).take(48).step(2).map { crop(it, Rect(0f, region.top, 400f, region.bottom)) }, 4, 1)
        val end = frames.last()
        val gaps = frames.drop(appeared).map { differing(it, end, region) }
        println("celebration shown from frame $appeared; gaps from the ring to rest: $gaps")
        // The star spins in over the ring's own sweep, so it is not at rest until Motion.COIN is over, and is the same at rest after.
        val moving = gaps.indexOfLast { it > 20 }
        assertTrue("the coin was over in only $moving frames: $gaps", moving >= 30)
        assertTrue("the coin did not turn through different looks: $gaps", gaps.take(moving + 1).distinct().size >= 12)
        assertTrue("the coin is not at rest at the end: ${gaps.takeLast(5)}", gaps.takeLast(5).all { it < 10 })
        scenario.close()
    }

    /** Idea 19: the weight ruler coasts after a fling and settles on a step of 0.05. */
    @Test fun rulerFlingsAndSettlesOnAStep() {
        val scenario = launch()
        compose.runOnIdle { model.logWeight() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val ruler = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) and androidx.compose.ui.test.hasContentDescription("Body weight"), useUnmergedTree = true)
        fun value() = ruler[0].fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)!!.substringBefore(' ').toDouble()
        val start = value()
        ruler[0].performTouchInput { swipe(androidx.compose.ui.geometry.Offset(320f, 20f), androidx.compose.ui.geometry.Offset(80f, 20f), 80) }
        compose.waitForIdle()
        val end = value()
        println("ruler: $start to $end")
        // The finger moved 240 px = 24 ticks of 10 px = 1.2 kg; the fling carries it further.
        assertTrue("the ruler did not coast after the finger: $start to $end", end - start > 2.0)
        assertTrue("the ruler did not settle on a step: $end", Math.abs(end * 20 - Math.round(end * 20)) < 1e-6)
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("the value moved after it had settled", value() == end)
        scenario.close()
    }

    // ---- round 4c: the running workout opens from, and closes into, the dock's clock pill and Home's In progress card ----

    private fun runningDraft() = Draft("Push day", System.currentTimeMillis() - 754_000, listOf(DraftBlock("Bench Press (Barbell)", setsOf(true, false, false))))

    /**
     * The dock's clock pill opens the running workout as a container that grows out of it, the workout's name and clock travelling from the
     * pill's to the page's, and Back (and the chevron) shrinks it into the pill with the words travelling back; nothing blinks.
     */
    @Test fun runningWorkoutGrowsOutOfThePillAndBack() {
        val scenario = launch()
        compose.runOnIdle { model.setOption("rest.seconds", "0"); model.putDraft(runningDraft()) }
        compose.waitForIdle()
        val key = DOCK_WORKOUT
        val pill = cardRect(key)
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click() } }, openFrames)
        assertTrue("the pill did not open the workout", model.stack.lastOrNull() == Screen.Active)
        saveStrip("pill-open", pick(openFrames, 3, 11), 6)
        saveStrip("pill-open-fine", openFrames.subList(0, 12), 6)
        assertTrue("open: the container was drawn in only ${open.rects.size} frames, so it blinked", open.rects.size >= 8)
        assertTrue("open: the container does not grow steadily ${open.grown}", open.grown.zipWithNext().all { (a, b) -> b >= a - 0.001f })
        assertTrue("open: it ends at ${open.grown.last()}", open.grown.last() > 0.9f)
        for (role in listOf("name", "clock")) assertTrue("open: $role never matched; matched ${open.matched}", "$key/$role" in open.matched)
        // The words really travel: the name sits in the pill's box before and in the page's after, and is somewhere between mid-way.
        val from = open.before["$key/name/card"]!!
        val to = open.after["$key/name/page"]!!
        assertTrue("open: the name does not move ($from to $to)", Math.abs(from.top - to.top) > 24f || Math.abs(from.left - to.left) > 24f)
        onTheClock("pill open", open, true)
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        saveStrip("pill-back", pick(backFrames, 3, 11), 6)
        for (role in listOf("name", "clock")) assertTrue("back: $role never matched; matched ${back.matched}", "$key/$role" in back.matched)
        onTheClock("pill back", back, false)
        assertTrue("back: the container was drawn in only ${back.rects.size} frames, so it blinked", back.rects.size >= 8)
        assertTrue("back: it does not shrink steadily ${back.grown}", back.grown.zipWithNext().all { (a, b) -> b <= a + 0.001f })
        assertTrue("back: it ends at ${back.grown.last()}", back.grown.last() < 0.45f)
        val now = cardRect(key)
        assertTrue("back: the pill moved from $pill to $now", Math.abs(pill.top - now.top) < 1f && Math.abs(pill.left - now.left) < 1f)
        scenario.close()
    }

    /** Home's In progress card: Resume (and a tap on the card) grows the workout out of it with its name and clock travelling. */
    @Test fun runningWorkoutGrowsOutOfTheInProgressCard() = cardMorph(
        "resume-card", Tab.Home, "home:today", shared = listOf("name", "clock"), setup = { compose.runOnIdle { model.putDraft(runningDraft()) }; compose.waitForIdle() },
        // The clocks (the card's and the workout page's) count real seconds, so the comparisons leave out the card and the page's title lines.
        extra = { cardRect("home:today").let { ignored = Rect(0f, minOf(60f, it.top), 400f, it.bottom) }; ignoredToo = cardRect(DOCK_WORKOUT); emptyList() },
    )

    /** With animations off the pill opens the workout with no motion: no container is drawn. */
    @Test fun runningWorkoutFromThePillCutWithAnimationsOff() {
        seedOff = true
        val scenario = launch()
        compose.runOnIdle { model.putDraft(runningDraft()) }
        compose.waitForIdle()
        val frames = mutableListOf<Bitmap>()
        val t = trail({ compose.onNodeWithTag("card:$DOCK_WORKOUT", useUnmergedTree = true).performTouchInput { click() } }, frames)
        assertTrue("the pill did not open the workout", model.stack.lastOrNull() == Screen.Active)
        assertTrue("a container was drawn with animations off: ${t.rects.size}", t.rects.isEmpty())
        scenario.close()
    }

    /** Idea 10: Home's 1RM tile's chart travels into the exercise's Charts tab (its 1RM line), and back. */
    @Test fun oneRmTileChartTravelsToTheExercisePage() = cardMorph("onerm-tile", Tab.Home, "home:onerm0", chart = "chart:1rm")
}
private fun <T> List<T>.step(n: Int) = filterIndexed { i, _ -> i % n == 0 }
