package clean.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
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

    private fun saveStrip(name: String, frames: List<Bitmap>, perRow: Int = 100) {
        val scale = 2
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
            // The last workout first, wide, so it is on the screen in every look.
            if (lastFirst) dao.put(Kv("home.cards:${dao.get("active")}", "last:w,streak:h,volume:h,week:h"))
        }
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
        while (compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() && swipes++ < 4) {
            compose.onNodeWithTag("page:tabs", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    /** What the container did in one change: the bounds it was drawn at in each frame it was drawn, and how far it had grown. */
    private class Trail(val rects: List<Rect>, val grown: List<Float>, val matched: Set<String>)

    private fun trail(act: () -> Unit, frames: MutableList<Bitmap>, ms: Int = 640): Trail {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        val clip = MorphProbe.clip!!
        val rects = mutableListOf<Rect>()
        val grown = mutableListOf<Float>()
        var seen = clip.draws
        val matched = HashSet<String>()
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16
            for ((k, st) in MorphProbe.states) if (st.isMatchFound) matched += k
            if (clip.draws != seen) { seen = clip.draws; rects += clip.rect; grown += clip.grown }
        }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return Trail(rects, grown, matched)
    }

    private fun pick(frames: List<Bitmap>, every: Int, count: Int) = (0 until frames.size step every).map { frames[it] }.take(count) + frames.last()

    /**
     * A card opening its page and Back, in [preset] (null the default look): strips of both, and the container (a) is drawn while it
     * moves, (b) grows steadily one way and shrinks steadily the other, (c) on Back lands on the card where it now sits on the tabs
     * page, (d) leaves nothing behind: the tabs page after Back is the one before the tap.
     */
    private fun cardMorph(name: String, tab: Tab, key: String, preset: String? = null, shared: List<String> = listOf("title"), chart: String? = null) {
        val scenario = launch(preset, lastFirst = true)
        compose.runOnIdle { model.tab = tab }
        compose.waitForIdle()
        scrollTo(key)
        compose.waitForIdle()
        val card = cardRect(key)
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
        for (role in shared) assertTrue("$name open: $role never matched; matched ${open.matched}", "$key/$role" in open.matched)
        // A chart on both is one line that travels and reshapes, drawn over both, not two charts crossing over.
        if (chart != null) assertTrue("$name open: the chart line did not travel; drew ${MorphProbe.chartsDrawn}", "$key/$chart" in MorphProbe.chartsDrawn)
        MorphProbe.chartsDrawn.clear()
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        for (role in shared) assertTrue("$name back: $role never matched; matched ${back.matched}", "$key/$role" in back.matched)
        saveStrip("$name-back-$tag", pick(backFrames, 3, 11), 6)
        saveStrip("$name-back-$tag-fine", backFrames.subList(0, 12), 6)
        if (chart != null) assertTrue("$name back: the chart line did not travel; drew ${MorphProbe.chartsDrawn}", "$key/$chart" in MorphProbe.chartsDrawn)
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
        scenario.close()
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
    @Test fun progressBodyCard() = cardMorph("body", Tab.Progress, "progress:body", shared = listOf("title", "figure"))
    @Test fun progressHistoryCard() = cardMorph("history", Tab.Progress, "progress:history")
    @Test fun progressActivityCard() = cardMorph("activity", Tab.Progress, "progress:activity")
    @Test fun homeWideCard() = cardMorph("home-wide", Tab.Home, "home:last")
    @Test fun homeHalfTile() = cardMorph("home-tile", Tab.Home, "home:streak", shared = listOf("title", "number"))
    @Test fun homeChartTile() = cardMorph("home-chart", Tab.Home, "home:volume", chart = "chart:Volume a week")
    @Test fun homeChartTileLinen() = cardMorph("home-chart", Tab.Home, "home:volume", "linen", chart = "chart:Volume a week")
    @Test fun profileCard() = cardMorph("profile-tile", Tab.Profile, "profile:membersince")
    @Test fun progressBodyCardLinen() = cardMorph("body", Tab.Progress, "progress:body", "linen", listOf("title", "figure"))
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
