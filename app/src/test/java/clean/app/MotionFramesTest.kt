package clean.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import clean.app.data.Kv
import clean.app.data.Repo
import kotlinx.coroutines.runBlocking
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
    private fun isIgnored(i: Int, w: Int) = ignored?.let { (i % w) >= it.left && (i % w) <= it.right && (i / w) >= it.top && (i / w) <= it.bottom } == true
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
            dao.put(Kv("theme.preset", preset ?: "amoled"))
            dao.put(Kv("ui.motion", if (seedOff) "off" else "on"))
            // Every test starts on the Body page's first view, whatever an earlier test swiped to.
            dao.put(Kv("ui.bodyMode", "Balance"))
            // The last workout first, wide, so it is on the screen in every look.
            if (lastFirst) dao.put(Kv("home.cards:${dao.get("active")}", "last:w,streak:h,volume:h,week:h"))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded }
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
    private class Trail(val rects: List<Rect>, val grown: List<Float>, val matched: Set<String>, val ended: Int)

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
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16
            perFrame()
            for ((k, st) in MorphProbe.states) if (st.isMatchFound) matched += k
            val drew = clip.draws != seen
            if (drew) { seen = clip.draws; rects += clip.rect; grown += clip.grown }
            // The move is over after the last frame in which the container moved.
            if (drew && grown.size > 1 && Math.abs(grown.last() - grown[grown.size - 2]) > 0.0001f) ended = frames.size - 1
        }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return Trail(rects, grown, matched, ended)
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
        scrollTo(key)
        compose.waitForIdle()
        val card = cardRect(key)
        val moving = shared + extra()
        val travel = extra().isNotEmpty() || chart != null
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames)
        assertTrue("$name: the card did not open a page", model.stack.isNotEmpty())
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
        val differing = before.indices.count { diff(before[it], after[it]) > 12 }
        val w = backFrames.last().width
        val off = before.indices.filter { diff(before[it], after[it]) > 12 }
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

    @Test fun listCards() {
        val scenario = launch("linen")
        for (t in listOf(Tab.Profile)) {
            compose.waitForIdle()
            compose.runOnIdle { model.tab = t }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1500)
            println("TAGS $t: " + compose.onAllNodes(androidx.compose.ui.test.hasTestTag("x") or androidx.compose.ui.test.SemanticsMatcher("card tag") { n -> (n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) && n.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("card:")) }, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] + it.boundsInRoot })
        }
        scenario.close()
    }
    @Test fun progressBodyCard() = cardMorph("body", Tab.Progress, "progress:body", shared = listOf("title", "figure", "view"))
    @Test fun progressHistoryCard() = cardMorph("history", Tab.Progress, "progress:history")
    @Test fun progressActivityCard() = cardMorph("activity", Tab.Progress, "progress:activity")
    @Test fun homeWideCard() = cardMorph("home-wide", Tab.Home, "home:last")
    @Test fun homeHalfTile() = cardMorph("home-tile", Tab.Home, "home:streak", shared = listOf("title", "number"))
    // Trends opens at the chart the tile names the first time only, so its page opened a second time (for the animations-off comparison) is not the same screen.
    @Test fun homeChartTile() = cardMorph("home-chart", Tab.Home, "home:volume", chart = "chart:Volume a week", samePage = false)
    @Test fun homeChartTileLinen() = cardMorph("home-chart", Tab.Home, "home:volume", "linen", chart = "chart:Volume a week", samePage = false)
    @Test fun profileCard() = cardMorph("profile-tile", Tab.Profile, "profile:membersince")
    // A template card on the Workout tab, a Statistics row and an exercise row: containers that grow from the row or card into the page.
    @Test fun workoutTemplateCard() = cardMorph("workout-template", Tab.Workout, "workout:template:1", setup = {
        val t = Template(1, "Push", listOf(clean.core.TemplateBlock("Bench Press (Barbell)", listOf(clean.core.SetType.NORMAL, clean.core.SetType.NORMAL))), "")
        runBlocking { Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao.put(clean.app.data.TemplateEntity(1L, model.activeId.value!!, t.name, t.blocksJson().toString(), "")) }
        compose.waitForIdle()
        // The list is made off the main thread: wait in real time for it.
        var waited = 0
        while (model.templates.value.none { it.id == 1L } && waited++ < 100) { Thread.sleep(100); compose.waitForIdle() }
        assertTrue("the template has id 1 ${model.templates.value.map { it.id }}", model.templates.value.any { it.id == 1L })
    })
    @Test fun statisticsRow() = cardMorph("stat-trends", Tab.Progress, "progress:stat:trends")
    @Test fun exerciseRow() = cardMorph("exercise-row", Tab.Exercises, "exercises:r:Bench Press (Barbell)")

    // What is on both a card and its page travels (stage E): rows, numbers, badges and photos. Each test names what must be matched
    // both ways (opening and Back) and that the frames after the move are the frame at rest, with the same screen when animations are off.
    private fun daoNow() = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao
    /** Shows these Home cards (stored as `key:w` / `key:h`) and nothing else. */
    private fun homeCards(cards: String) {
        runBlocking { daoNow().put(Kv("home.cards:${model.activeId.value}", cards)) }
        compose.waitForIdle()
    }
    /** Three weigh-ins in three different weeks, newest 72.5 kg, so the weight tile and chart have something to show. */
    private fun weighIns() {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        runBlocking {
            for ((back, kg) in listOf(16L to 74.0, 9L to 73.2, 2L to 72.5)) {
                assertTrue("weigh-in", model.addMeasurement("weight.csv", kg, LocalDateTime.now().minusDays(back).withHour(8).withMinute(0).withSecond(0).format(format)))
            }
        }
        compose.waitForIdle()
    }

    /** Home's Recent workouts rows: each name and day travels onto the same workout's card on the History page. */
    @Test fun homeRecentWorkoutsRows() = cardMorph("recent-workouts", Tab.Home, "home:recentworkouts", setup = { homeCards("recentworkouts:w,week:h,streak:h") }, extra = {
        model.workouts.value.takeLast(3).flatMap { listOf("row:name/${it.date}", "row:date/${it.date}") }
    })
    @Test fun homeRecentWorkoutsRowsLinen() = cardMorph("recent-workouts", Tab.Home, "home:recentworkouts", "linen", setup = { homeCards("recentworkouts:w,week:h,streak:h") }, extra = {
        model.workouts.value.takeLast(3).flatMap { listOf("row:name/${it.date}", "row:date/${it.date}") }
    })
    /** Recent PRs' rows: each exercise's name travels onto its line on Personal bests. */
    @Test fun homeRecentPrsRows() = cardMorph("recent-prs", Tab.Home, "home:recentprs", setup = { homeCards("recentprs:w,week:h,streak:h") }, extra = {
        listOf("pr:name/Bench Press (Barbell)", "pr:name/Lat Pulldown (Machine)")
    })
    /** Profile's Body measurements rows: label, latest value and day travel onto the Measurements page's row of the file. */
    @Test fun profileMeasurementsRows() = cardMorph("measurements-rows", Tab.Profile, "profile:measurements", setup = { weighIns() }, extra = {
        listOf("m:label/weight.csv", "m:value/weight.csv", "m:date/weight.csv")
    })
    /** The weight tile's number travels into the latest weigh-in on the weight page. */
    @Test fun homeWeightTile() = cardMorph("weight-tile", Tab.Home, "home:weight", shared = listOf("title", "number"), chart = "chart:weight", setup = { homeCards("weight:h,week:h,streak:h"); weighIns() }, extra = { listOf("number") })
    @Test fun homeWeightTileLinen() = cardMorph("weight-tile", Tab.Home, "home:weight", "linen", shared = listOf("title", "number"), chart = "chart:weight", setup = { homeCards("weight:h,week:h,streak:h"); weighIns() }, extra = { listOf("number") })
    /** The wide body weight card's latest figure does the same. */
    /** A chart line whose page has far more points than the card's (the 1RM line on the exercise's Charts tab) hands over inside the move too. */
    @Test fun homeOneRmTile() = cardMorph("onerm-tile", Tab.Home, "home:onerm1", chart = "chart:1rm", setup = { homeCards("onerm1:h,week:h,streak:h") })
    @Test fun homeWeightChartCard() = cardMorph("weight-card", Tab.Home, "home:weightchart", shared = listOf("title", "number"), chart = "chart:weight", setup = { homeCards("weightchart:w,week:h,streak:h"); weighIns() }, extra = { listOf("number") })
    /** Profile's Trophies card: the latest badge's name travels onto the same badge on the Trophies page. */
    @Test fun profileTrophiesBadge() = cardMorph("trophies-badge", Tab.Profile, "profile:trophies", extra = {
        val latest = model.consistency.value!!.awards.filter { it.earned != null }.maxByOrNull { it.earned!! }
        assertTrue("no badge is earned in the seeded history", latest != null)
        listOf("badge/${latest!!.title}")
    })

    /** Profile's Progress photos card: the latest photos travel into the same photos of the timeline. */
    @Test fun profilePhotos() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        cardMorph("photos", Tab.Profile, "profile:photos", setup = {
            val id = model.activeId.value!!
            val day = DateTimeFormatter.ofPattern("yyyyMMdd")
            for (n in 0 until 6) {
                val file = PhotoFiles.file(context, id, "${LocalDateTime.now().minusDays(3L + n * 5).format(day)}-080000_${listOf("front", "side", "back")[n % 3]}.jpg")
                file.parentFile!!.mkdirs()
                val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bitmap).apply {
                    drawColor(android.graphics.Color.rgb(40 * n + 20, 200 - 30 * n, 90))
                    drawCircle(150f, 120f, 60f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
                    drawRect(90f, 200f, 210f, 360f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
                }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            model.refreshPhotos()
            compose.waitForIdle()
            var waited = 0
            while (model.photos.value.size < 6 && waited++ < 100) { Thread.sleep(50); compose.waitForIdle() }
            assertTrue("the photos were not listed", model.photos.value.size == 6)
        // The timeline shows three photos on its first screen; the card's fourth has no cell on screen to go to, and fades with the card.
        }, extra = { model.photos.value.take(3).map { "photo/${it.name}" } })
    }
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
        saveStrip("history-rows-open-$tag", pick(openFrames, 2, 17), 6)
// Once the move has run its 500 ms nothing of the rows is left over the page: the frames after it are the frame at rest.
        stillAtTheStart("history rows open", openFrames, historyCard, androidx.compose.ui.geometry.Offset(historyCard.left + 8f, historyCard.top + 8f))
        settledAndStill("history rows open", openFrames, "history-rows-open-$tag", open.ended)
        saveStrip("history-rows-open-$tag-big", listOf(10, 14, 18, 22, 26, 30, 34, 38).map { openFrames[it] } + openFrames.last(), 3, 1)
        for (role in listOf("row:name", "row:date", "row:prs")) {
            val matched = rows.filter { "$key/$role/$it" in open.matched }
            assertTrue("history rows open: no $role matched; matched ${open.matched}", matched.isNotEmpty())
        }
        // Rows that are not on the page's screen go below it (matched under the last card).
        val below = rows.filter { "$key/row:out/$it" in open.matched }
        assertTrue("history rows open: no row travelled below the page; matched ${open.matched}", below.isNotEmpty())
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        stillAtTheStart("history rows back", backFrames, null)
        settledAndStill("history rows back", backFrames, "history-rows-back-$tag", back.ended)
        saveStrip("history-rows-back-$tag", pick(backFrames, 2, 17), 6)
        saveStrip("history-rows-back-$tag-big", listOf(0, 4, 8, 12, 16, 20, 24, 28, 32).map { backFrames[it] }, 3, 1)
        for (role in listOf("row:name", "row:date", "row:prs", "row:out")) {
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
    @Test fun historyCardToWorkoutLinen() = pageCardMorph("history-card", { "page:history:" + latest(null) }, { compose.runOnIdle { model.open(Screen.History) } }, "linen")
    /** An exercise page's History tab: a session's card into the workout's page. */
    @Test fun exerciseSessionToWorkout() = pageCardMorph(
        "exercise-session", { "page:exercise:Bench Press (Barbell):" + latest("Bench Press (Barbell)") },
        { compose.runOnIdle { model.open(Screen.ExerciseDetail("Bench Press (Barbell)", tab = 1)) } },
    )

    @Test fun progressHistoryRows() = historyRows(null)
    @Test fun progressHistoryRowsLinen() = historyRows("linen")
    @Test fun progressBodyCardLinen() = cardMorph("body", Tab.Progress, "progress:body", "linen", listOf("title", "figure", "view"))
    @Test fun progressHistoryCardLinen() = cardMorph("history", Tab.Progress, "progress:history", "linen")
    @Test fun progressActivityCardLinen() = cardMorph("activity", Tab.Progress, "progress:activity", "linen")
    @Test fun homeWideCardLinen() = cardMorph("home-wide", Tab.Home, "home:last", "linen")
    @Test fun homeHalfTileLinen() = cardMorph("home-tile", Tab.Home, "home:streak", "linen", listOf("title", "number"))
    @Test fun profileCardLinen() = cardMorph("profile-tile", Tab.Profile, "profile:membersince", "linen")

    /** A page opened from a card, a page over it and Back to it is the blink (the card's elements do not fly again); Back from it is the morph. */
    @Test fun deeperPageAndBack() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        val opened = mutableListOf<Bitmap>()
        trail({ compose.onNodeWithTag("card:progress:body", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, opened)
        blink("body-deeper") { model.open(Screen.MuscleDetail(clean.core.Muscle.entries.first())) }
        blink("body-deeper-back") { model.back() }
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
        val roles = listOf("row:name", "row:date", "row:prs")
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
        val conv = Rect.VectorConverter
        val overlaps = mutableListOf<String>()
        for (forward in listOf(true, false)) for (date in dates) {
            val tracks = roles.mapNotNull { role ->
                val row = atRow["$key/$role/$date/card"] ?: return@mapNotNull null
                val card = atCard["$key/$role/$date/page"] ?: return@mapNotNull null
                val from = if (forward) row else card
                val to = if (forward) card else row
                val spec = Motion.legged(from, to, forward).vectorize(conv)
                role to { ms: Int -> conv.convertFromVector(spec.getValueFromNanos(ms * 1_000_000L, conv.convertToVector(from), conv.convertToVector(to), androidx.compose.animation.core.AnimationVector4D(0f, 0f, 0f, 0f))) }
            }
            assertTrue("no row boxes were found for $date", tracks.size >= 2)
            for (ms in 0..500 step 8) for (a in tracks.indices) for (b in a + 1 until tracks.size) {
                val ra = tracks[a].second(ms)
                val rb = tracks[b].second(ms)
                val w = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
                val h = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
                if (w > 1f && h > 1f) overlaps += "${if (forward) "open" else "back"} $date at $ms ms: ${tracks[a].first} $ra and ${tracks[b].first} $rb overlap by $w x $h"
            }
        }
        assertTrue("boxes of one row overlap on their way: ${overlaps.take(6)}", overlaps.isEmpty())
        // The real boxes do as the legs say: on opening, a date and a PR count stay at their x while they go to their line (the first 200 ms).
        for (date in dates.take(1)) for (role in listOf("row:date", "row:prs")) {
            val first = openShots.first()["$key/$role/$date/page"]!!
            for (shot in openShots.take(10)) {
                val now = shot["$key/$role/$date/page"]!!
                assertTrue("$role moved sideways ${now.left - first.left} px before it had reached its line", Math.abs(now.left - first.left) < 1.5f)
            }
            assertTrue("$role never went to its line", Math.abs(openShots.last()["$key/$role/$date/page"]!!.top - first.top) > 3f)
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
        val today = java.time.LocalDate.now().dayOfMonth
        val numbers = compose.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text?.takeIf { it.matches(Regex("\\d{1,2}")) }?.let { it.toInt() to n.boundsInRoot } }
        val last = numbers.filter { it.first == today }.maxByOrNull { it.second.top }!!
        val below = numbers.filter { it.second.top > last.second.top + 1f }.map { it.first }
        println("today $today at ${last.second}; day numbers below it: $below")
        assertTrue("days after today are drawn: $below", below.isEmpty())
        assertTrue("a later day of the month is drawn on today's row", numbers.none { it.first > today && Math.abs(it.second.top - last.second.top) < 2f && it.second.left > last.second.left })
        scenario.close()
    }

    @Test fun tabSwitch() {
        val scenario = launch()
        // The Activity card's first fill is a wave (activityWave); this is the plain blink, with that first time used up.
        compose.runOnIdle { model.seen["wave:activity"] = true }
        blink("tab-home-progress") { model.tab = Tab.Progress }
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

    /** Idea 10: a tile's number that changed while its card was away shows the old figure, then rolls once when the card comes into view. */
    @Test fun tileNumberRolls() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Home }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        fun volumeText() = model.volume(model.workouts.value.filter { it.day >= clean.core.weekStart(java.time.LocalDate.now()).toString() }.sumOf { clean.core.volumeOf(it, model::loadOf) })
        val old = volumeText()
        assertTrue("the old volume $old is on Home", textNodes(old).fetchSemanticsNodes().isNotEmpty())
        val tile = textNodes(old).fetchSemanticsNodes().first().boundsInRoot
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        addWorkout(0, "Bench Press (Barbell)", 5, 100)
        waitReal("the new workout is in the list") { model.workouts.value.any { it.name == "Extra" } }
        val fresh = volumeText()
        assertTrue("the volume changed ($old, $fresh)", fresh != old)
        compose.mainClock.advanceTimeBy(1500)
        // Home is composed beside the tab shown but not seen: it still says what the user last saw.
        assertTrue("Home shows the old volume until seen", textNodes(old).fetchSemanticsNodes().isNotEmpty() && textNodes(fresh).fetchSemanticsNodes().isEmpty())
        val frames = run({ model.tab = Tab.Home }, 640)
        val region = Rect(tile.left - 4f, tile.top - 4f, tile.right + 60f, tile.bottom + 4f)
        saveStrip("tile-roll", frames.step(2).map { crop(it, Rect(region.left, region.top - 30f, region.right, region.bottom + 30f)) }, 6, 1)
        saveStrip("tile-roll-full", pick(frames, 4, 11), 6)
        assertTrue("Home shows the new volume once seen", textNodes(fresh).fetchSemanticsNodes().isNotEmpty() && textNodes(old).fetchSemanticsNodes().isEmpty())
        // The figure rolls: some frames of the tile are neither the old figure nor the new.
        val end = frames.last()
        val mids = frames.indices.filter { differing(frames[it], end, region) > 20 && differing(frames[it], frames.first(), region) > 20 }
        assertTrue("no frame of the tile is between the old figure and the new one", mids.size >= 3)
        println("tile roll: ${mids.size} frames in between")
        // Once per change: away and back again is the figure as it was, no second roll.
        compose.runOnIdle { model.tab = Tab.Progress }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1500)
        val again = run({ model.tab = Tab.Home }, 480)
        assertTrue("the figure rolled a second time", again.indices.all { differing(again[it], again.last(), region) <= 20 || it < 16 })
        scenario.close()
    }

    /** Idea 11: switching 1M / 1Y on the weight page stretches the line along its dates; the last frame of the move is the chart at rest. */
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

    /** Idea 12: the week ring fills from where the user last saw it, and the day done since fills with a pop. */
    @Test fun weekRingAndDays() {
        val scenario = launch()
        compose.runOnIdle { model.setWeeklyTarget(7) }
        compose.waitForIdle()
        homeCards("week:w,streak:h,volume:h")
        compose.runOnIdle { model.tab = Tab.Home }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        val before = model.consistency.value!!.now.count
        val ring = compose.onNode(androidx.compose.ui.test.hasContentDescription("of 7 workouts this week", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val today = compose.onNode(androidx.compose.ui.test.hasContentDescription(", today", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        addWorkout(0, "Bench Press (Barbell)", 3, 80)
        waitReal("the week counts the new workout") { model.consistency.value?.now?.count == before + 1 }
        compose.mainClock.advanceTimeBy(1500)
        assertTrue("the ring still says $before while unseen", textNodes("$before / 7").fetchSemanticsNodes().isNotEmpty())
        val frames = run({ model.tab = Tab.Home }, 960)
        val ringArea = Rect(ring.left - 4f, ring.top - 4f, ring.right + 4f, ring.bottom + 4f)
        val dayArea = Rect(today.left - 4f, today.top - 4f, today.right + 4f, today.bottom + 4f)
        saveStrip("week-ring", frames.step(3).map { crop(it, ringArea) }, 11, 1)
        saveStrip("week-day-pop", frames.step(2).map { crop(it, dayArea) }, 12, 1)
        saveStrip("week-card-full", pick(frames, 6, 11), 6)
        val end = frames.last()
        val ringMids = frames.indices.filter { differing(frames[it], end, ringArea) > 15 && differing(frames[it], frames.first(), ringArea) > 15 }
        assertTrue("the ring did not sweep: ${ringMids.size} frames in between", ringMids.size >= 5)
        val dayMids = frames.indices.filter { differing(frames[it], end, dayArea) > 15 && differing(frames[it], frames.first(), dayArea) > 15 }
        assertTrue("the day did not fill: ${dayMids.size} frames in between", dayMids.size >= 3)
        assertTrue("today is marked trained once seen", described(", trained", true).fetchSemanticsNodes().isNotEmpty())
        println("ring ${ringMids.size} frames, day ${dayMids.size} frames")
        scenario.close()
    }

    /** Idea 17: the calendar's squares come in from the top left the first time it opens in a session, and not again. */
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

    /** The Activity card's squares wave the first time Progress is seen. */
    @Test fun activityWave() {
        val scenario = launch(lastFirst = true)
        compose.mainClock.advanceTimeBy(1500)
        val frames = run({ model.tab = Tab.Progress }, 1400)
        saveStrip("activity-wave", pick(frames, 5, 17), 6)
        val rest = frames.last()
        val card = cardRect("progress:activity")
        val moving = frames.indices.filter { differing(frames[it], rest, card) > 100 }
        assertTrue("the activity squares never moved", moving.size >= 10)
        assertTrue("it is not at rest at the end", differing(frames[frames.size - 2], rest, card) < 30)
        scenario.close()
    }

    /** The calendar opened by the Activity card's morph does not wave: the morph moved it. */
    @Test fun calendarFromCardDoesNotWave() {
        val scenario = launch(lastFirst = true)
        compose.runOnIdle { model.tab = Tab.Progress }
        compose.waitForIdle()
        scrollTo("progress:activity")
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf<Bitmap>()
        trail({ compose.onNodeWithTag("card:progress:activity", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, frames, 1400)
        assertTrue("the card opened its page", model.stack.isNotEmpty())
        val rest = pixels(frames.last())
        // 700 ms on, the morph is over; a wave (it runs more than a second) would still be filling squares.
        for (i in 46 until frames.size - 1) {
            val p = pixels(frames[i])
            assertTrue("frame $i still changes after the morph (a wave?)", p.indices.count { diff(p[it], rest[it]) > 12 } * 200 < p.size)
        }
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

    /** Idea 6: the workout banner on the tab bar grows into the workout page and back, its name and clock travelling to the page's. */
    @Test fun bannerGrowsIntoTheWorkout() = cardMorph("banner-workout", Tab.Home, BANNER_CARD, shared = listOf("name"), setup = {
        compose.runOnIdle {
            model.setOption("rest.seconds", "0")
            model.putDraft(Draft("Push day", System.currentTimeMillis() - 95_000, listOf(DraftBlock("Bench Press (Barbell)", setsOf(false, false)))))
        }
        compose.waitForIdle()
        ignored = Rect(340f, 695f, 400f, 725f)
    }, extra = { listOf("clock") })

    /** The bounds of the tab bar's slot named [label]. */
    private fun slotRect(label: String): Rect =
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.top > 600f }

    /** The longest run of pixels of colour [colour] along row [y] of [f] between x [from] and [to] (gaps up to [gap] px allowed: an icon in the pill), as its first and last x. */
    private fun runOf(f: Bitmap, y: Int, from: Int, to: Int, colour: Int, gap: Int = 26): Pair<Int, Int>? {
        val hits = (from until to).filter { diff(f.getPixel(it, y), colour) < 10 }
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

    /** Idea 13: the tab bar's pill glides from tab to tab with its leading edge first, and follows a swipe of the pager. */
    @Test fun tabPillGlidesAndFollowsASwipe() {
        val scenario = launch()
        compose.runOnIdle { model.seen["wave:activity"] = true }
        compose.mainClock.advanceTimeBy(1500)
        val home = slotRect("Home")
        val rest = frame()
        val x0 = (home.center.x - 22f).toInt()
        val bg = rest.getPixel(home.left.toInt() + 3, home.center.y.toInt())
        val top = (home.top.toInt() until home.bottom.toInt()).first { diff(rest.getPixel(x0, it), bg) > 6 }
        val y = top + 16
        val pill = rest.getPixel(x0, y)
        val from = home.left.toInt()
        val to = slotRect("Profile").right.toInt()
        fun edges(f: Bitmap) = runOf(f, y, from, to, pill)!!
        val homeEdges = edges(rest)
        val bar = Rect(0f, home.top - 8f, 400f, home.bottom + 8f)
        // A tap: Home to Progress.
        val frames = run({ model.tab = Tab.Progress }, 480)
        saveStrip("tab-pill-tap", frames.step(2).take(16).map { crop(it, bar) }, 2, 1)
        val tapEdges = frames.map(::edges)
        val progress = slotRect("Progress")
        val atProgress = tapEdges.last()
        assertTrue("the pill ends at the Progress slot's middle: $atProgress against ${progress.center.x}", Math.abs((atProgress.first + atProgress.second) / 2f - progress.center.x) <= 2f)
        assertTrue("the pill's width changed at rest: $homeEdges, $atProgress", Math.abs((atProgress.second - atProgress.first) - (homeEdges.second - homeEdges.first)) <= 2)
        checkStretch("the pill on a tap", tapEdges, homeEdges, atProgress)
        // A swipe: back to Home first, then the finger drags the pager and the pill goes with it.
        compose.runOnIdle { model.tab = Tab.Home }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val page = compose.onNodeWithTag("page:tabs", useUnmergedTree = true)
        val left = androidx.compose.ui.geometry.Offset(-30f, 0f)
        page.performTouchInput { down(androidx.compose.ui.geometry.Offset(300f, 300f)); moveBy(left); moveBy(left); moveBy(left) }
        compose.waitForIdle()
        val early = frame()
        val a = edges(early)
        val more = androidx.compose.ui.geometry.Offset(-60f, 0f)
        page.performTouchInput { moveBy(more); moveBy(more) }
        compose.waitForIdle()
        val later = frame()
        val b = edges(later)
        page.performTouchInput { up() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        val landed = edges(frame())
        saveStrip("tab-pill-swipe", listOf(rest, early, later, frame()).map { crop(it, bar) }, 1, 1)
        println("pill on a swipe: rest $homeEdges, early $a, later $b, landed $landed")
        val restWidth = homeEdges.second - homeEdges.first
        assertTrue("the pill did not follow the finger (stretched): $a", a.second > homeEdges.second + 8 && a.second - a.first >= restWidth + 8)
        assertTrue("the pill did not go on with the finger: $b after $a", b.second > a.second + 8 && b.first > a.first + 4)
        assertTrue("the pill did not land on Progress: $landed against $atProgress", Math.abs(landed.first - atProgress.first) <= 2 && Math.abs(landed.second - atProgress.second) <= 2)
        // Animations off: a cut.
        compose.runOnIdle { model.setOption("ui.motion", "off") }
        compose.waitForIdle()
        val cut = run({ model.tab = Tab.Profile }, 96)
        val profile = slotRect("Profile")
        val first = edges(cut[3])
        assertTrue("with animations off the pill is at Profile at once: $first against ${profile.center.x}", Math.abs((first.first + first.second) / 2f - profile.center.x) <= 2f && edges(cut.last()) == first)
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
        val monday = clean.core.weekStart(java.time.LocalDate.now()).toString()
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
}
private fun <T> List<T>.step(n: Int) = filterIndexed { i, _ -> i % n == 0 }
