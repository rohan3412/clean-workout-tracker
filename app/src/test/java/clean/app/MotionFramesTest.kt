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
        while (compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().let { n -> n.isEmpty() || n.first().boundsInRoot.let { it.width <= 0f || it.bottom > 700f } } && swipes++ < 4) {
            compose.onNodeWithTag("page:tabs", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    /** What the container did in one change: the bounds it was drawn at in each frame it was drawn, and how far it had grown. */
    private class Trail(val rects: List<Rect>, val grown: List<Float>, val matched: Set<String>, val ended: Int)

    private fun trail(act: () -> Unit, frames: MutableList<Bitmap>, ms: Int = 640): Trail {
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
        if (travel) settledAndStill("$name open", openFrames, "$name-open-$tag", open.ended)
        // A chart on both is one line that travels and reshapes, drawn over both, not two charts crossing over.
        if (chart != null) assertTrue("$name open: the chart line did not travel; drew ${MorphProbe.chartsDrawn}", "$key/$chart" in MorphProbe.chartsDrawn)
        MorphProbe.chartsDrawn.clear()
        val backFrames = mutableListOf<Bitmap>()
        val back = trail({ model.back() }, backFrames)
        for (role in moving) assertTrue("$name back: $role never matched; matched ${back.matched}", "$key/$role" in back.matched)
        if (travel) settledAndStill("$name back", backFrames, "$name-back-$tag", back.ended)
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
        if (travel) sameWithAnimationsOff(name, key, backFrames.last(), samePage)
        scenario.close()
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
            val off = p.indices.filter { diff(p[it], rest[it]) > 12 }
            assertTrue("$label: ${off.size} pixels still differ from rest in frame $i of ${frames.size}, x ${off.minOfOrNull { it % w }}..${off.maxOfOrNull { it % w }}, y ${off.minOfOrNull { it / w }}..${off.maxOfOrNull { it / w }}", off.size * 1000 < p.size)
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
        val openFrames = mutableListOf<Bitmap>()
        val open = trail({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) } }, openFrames)
        assertTrue("history rows: the card did not open a page", model.stack.isNotEmpty())
        saveStrip("history-rows-open-$tag", pick(openFrames, 2, 17), 6)
// Once the move has run its 500 ms nothing of the rows is left over the page: the frame just after it is the frame at rest.        val atEnd = pixels(openFrames[openFrames.size - 2]); val rest = pixels(openFrames.last())        assertTrue("history rows open: ${atEnd.indices.count { diff(atEnd[it], rest[it]) > 12 }} pixels still differ after the move", atEnd.indices.count { diff(atEnd[it], rest[it]) > 12 } == 0)
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
    private fun pageCardMorph(name: String, keyOf: () -> String, openPage: () -> Unit, preset: String? = null) {
        val scenario = launch(preset)
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
        saveStrip("$name-open-$tag", pick(openFrames, 3, 11), 6)
        saveStrip("$name-open-$tag-fine", openFrames.subList(0, 12), 6)
        saveStrip("$name-open-$tag-rest", listOf(openFrames[openFrames.size - 3], openFrames[openFrames.size - 2], openFrames.last()), 3, 1)
        assertTrue("$name open: the container was drawn in only ${open.rects.size} frames", open.rects.size >= 8)
        assertTrue("$name open: the container does not grow steadily ${open.grown}", open.grown.zipWithNext().all { (a, b) -> b >= a - 0.001f })
        assertTrue("$name open: it ends at ${open.grown.last()}", open.grown.last() > 0.9f)
        assertTrue("$name open: it starts at ${open.grown.first()}", open.grown.first() < 0.6f)
        assertTrue("$name open: the title never matched; matched ${open.matched}", "$key/title" in open.matched)
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
        assertTrue("$name back: the title never matched; matched ${back.matched}", "$key/title" in back.matched)
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

    @Test fun swipeMiniFigure() = slideCheck("body-mini", "Body figure, ") { }
    @Test fun swipePageFigure() = slideCheck("body-page", "Body figure:") { compose.runOnIdle { model.open(Screen.Body) } }

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
private fun <T> List<T>.step(n: Int) = filterIndexed { i, _ -> i % n == 0 }
