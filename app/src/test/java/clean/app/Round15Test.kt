package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.app.data.open
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.format.DateTimeFormatter

/**
 * Round 15 (the delights of 15a) on the real app, a frame at a time on the paused clock: each happens once, is cut with animations off, and
 * moves nothing in the layout. 1 Up next sliding on, 2 the week ring's new segment, 3 the anniversary line, 5 the all-time-high bar's glint,
 * 7 a double tap on a chart, 8 the muscles trained today breathing.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round15Test {
    @get:Rule val compose = createEmptyComposeRule()

    private val lifts = listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)")
    private lateinit var model: AppModel
    private var window: View? = null

    /** A workout of the seeded history: [back] days ago, named [name], each of three lifts with three sets of [kg] for 8. */
    private class Day(val back: Long, val name: String, val kg: Double)

    private fun seed(history: List<Day>, motion: Boolean, today: List<Day> = emptyList()) = runBlocking {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("glass.reduce", "on"))
        repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
        repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
        for (t in listOf("Push", "Pull")) {
            val blocks = lifts.joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
            repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
        }
        val rows = ArrayList<WorkoutRow>()
        for ((n, d) in (history + today).withIndex()) {
            val date = LocalDate.now().minusDays(d.back).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for (lift in lifts) for (s in 1..3) rows += WorkoutRow((n + 1).toString(), date, d.name, "3600", lift, s.toString(), d.kg.toString(), "8", "", "", "", "", "")
        }
        repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
    }

    private fun launch(history: List<Day>, motion: Boolean = true, today: List<Day> = emptyList()): ActivityScenario<MainActivity> {
        seed(history, motion, today)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    private fun frame(): Bitmap {
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun diff(a: Int, b: Int) = Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))

    /** How many pixels of [region] differ between [a] and [b]. */
    private fun differing(a: Bitmap, b: Bitmap, region: Rect): Int {
        val pa = pixels(a); val pb = pixels(b)
        var n = 0
        for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(a.height)) for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(a.width)) {
            if (diff(pa[y * a.width + x], pb[y * a.width + x]) > 12) n++
        }
        return n
    }

    /** Steps the paused clock 16 ms at a time for [ms] after [act], then to rest; every frame is returned, the last is at rest. [watch] is read after each step. */
    private fun frames(act: () -> Unit, ms: Int, watch: () -> Unit = {}): List<Bitmap> {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val out = mutableListOf(frame())
        act()
        var t = 0
        while (t < ms) { compose.mainClock.advanceTimeBy(16); out += frame(); watch(); t += 16 }
        compose.mainClock.advanceTimeBy(3000)
        out += frame()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return out
    }

    private fun rectOf(tag: String): Rect = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun rectDescribed(prefix: String): Rect =
        compose.onAllNodes(hasContentDescription(prefix, substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width > 0f && it.left >= 0f && it.right <= 800f }

    private fun waitReal(what: String, done: () -> Boolean) {
        var waited = 0
        while (!done() && waited++ < 800) { Thread.sleep(25); compose.waitForIdle() }
        assertTrue("never happened: $what", done())
    }

    /** A workout [back] days ago named [name]: [sets] sets of [kg] for 10 of one lift, added to the stored rows while the app runs. */
    private fun addWorkout(back: Long, name: String, sets: Int, kg: Int, lift: String = lifts[0]) {
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val date = LocalDateTime.now().minusDays(back).withHour(8).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val number = 900 + back.toInt() + (System.nanoTime() % 50).toInt()
        val rows = (1..sets).map { set -> "\"$number\";\"$date\";\"$name\";\"3600\";\"$lift\";\"$set\";\"$kg.0\";\"10\";\"\";\"\";\"\";\"\";\"\"" }
        val bytes = (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>()))
            val id = model.activeId.value!!
            repo.commit(id, repo.previewWorkouts(id, bytes))
        }
    }

    /** The frames (after [from]) that are neither [a] nor [b] in [region]: a move in between, as against a cut. */
    private fun between(frames: List<Bitmap>, from: Int, a: Bitmap, b: Bitmap, region: Rect, margin: Int = 60) =
        (from until frames.size - 1).filter { differing(frames[it], a, region) > margin && differing(frames[it], b, region) > margin }

    // ---- 1. Up next slides on ----

    private val rotation = listOf(Day(9, "Push", 60.0), Day(7, "Pull", 60.0), Day(5, "Push", 60.0), Day(3, "Pull", 60.0))

    private fun upNextName(): String? = clean.core.upNextTemplate(model.templates.value.map { it.ref() }, model.workouts.value, model.splitOn.value, CURRENT_SPLIT, model.today, model.skippedTemplates)?.template?.name

    @Test fun upNextSlidesOnOnceAfterAWorkoutAndNotWhenPickedByHand() {
        val scenario = launch(rotation)
        val first = upNextName()!!
        // A cold start shows the name with no move, and remembers it.
        assertEquals(first, model.seen["upnext:name"])
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, first, 9, 100)
        waitReal("the template Up next names changes") { upNextName() != first }
        val second = upNextName()!!
        settle(1000)
        // While a page covers Home the old name is what was last seen: nothing moved yet.
        assertEquals(first, model.seen["upnext:name"])
        val card = rectOf("card:home:today")
        val region = Rect(card.left, card.top, card.right, card.top + card.height * 0.5f)
        val cards = ArrayList<Rect>()
        val f = frames({ model.back() }, 900) { cards += rectOf("card:home:today") }
        assertEquals(second, model.seen["upnext:name"])
        saveStrip("up-next-slide", f.filterIndexed { i, _ -> i % 2 == 0 }.map { crop(it, region) })
        val old = f[14]; val rest = f.last()
        assertTrue("the old name was not on screen when Home came back", differing(old, rest, region) > 150)
        assertTrue("the first frames after Home came back already moved: ${differing(f[15], old, region)}", differing(f[15], old, region) < 150)
        val mids = between(f, 17, old, rest, region)
        assertTrue("the name did not slide: $mids", mids.size >= 4)
        assertTrue("the slide ran longer than 600 ms: ${mids.last()}", (mids.last() - 17) * 16 <= 700)
        for (i in 52 until f.size) assertTrue("frame $i is still moving", differing(f[i], rest, region) < 30)
        assertTrue("the card moved while the name slid: ${cards.distinct()}", cards.all { Math.abs(it.top - card.top) < 1f && Math.abs(it.height - card.height) < 1f && Math.abs(it.left - card.left) < 1f })
        // Once: another tab and back, and a page and back, show it at rest.
        for (away in listOf<() -> Unit>({ model.tab = Tab.Progress }, { model.open(Screen.History) })) {
            go(away); settle()
            val again = frames({ if (model.stack.isNotEmpty()) model.back() else model.tab = Tab.Home }, 700)
            assertTrue("the slide came again: ${between(again, 17, again[14], again.last(), region)}", between(again, 17, again[14], again.last(), region).isEmpty())
        }
        // A template picked by hand is a cut, not a slide.
        val before = model.skippedTemplates
        val pick = frames({ model.skippedTemplates = before + model.templates.value.first { it.name == upNextName() }.id }, 700)
        assertTrue("the pick did not change the name", differing(pick.first(), pick.last(), region) > 150)
        assertTrue("a template picked by hand slid: ${between(pick, 1, pick.first(), pick.last(), region)}", between(pick, 1, pick.first(), pick.last(), region).size <= 1)
        scenario.close()
    }

    @Test fun upNextIsCutWithAnimationsOff() {
        val scenario = launch(rotation, motion = false)
        val first = upNextName()!!
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, first, 9, 100)
        waitReal("the template Up next names changes") { upNextName() != first }
        settle(1000)
        val card = rectOf("card:home:today")
        val region = Rect(card.left, card.top, card.right, card.top + card.height * 0.5f)
        val f = frames({ model.back() }, 700)
        assertTrue("it slid with animations off: ${between(f, 14, f[14], f.last(), region)}", between(f, 14, f[14], f.last(), region).isEmpty())
        for (i in 16 until f.size) assertTrue("frame $i is not at rest", differing(f[i], f.last(), region) < 30)
        scenario.close()
    }

    // ---- 2. The week ring's new segment ----

    /** History with nothing in the current week, so the first workout of the week is the one added. */
    private val beforeThisWeek = listOf(Day(40, "Push", 60.0), Day(33, "Pull", 60.0), Day(26, "Push", 60.0), Day(19, "Pull", 60.0), Day(12, "Push", 60.0))

    private fun ringRegion(): Rect = rectOf("card:home:week").let { Rect(it.left, it.top + it.height * 0.25f, it.left + 150f, it.bottom) }

    @Test fun weekRingDrawsItsNewSegmentOnceWhenHomeIsShownAgain() {
        val scenario = launch(beforeThisWeek)
        val count = model.consistency.value!!.now.count
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, "Push", 9, 100)
        waitReal("the week counts the workout") { model.consistency.value!!.now.count == count + 1 }
        settle(1000)
        val region = ringRegion()
        val tile = rectOf("card:home:week")
        val tiles = ArrayList<Rect>()
        val f = frames({ model.back() }, 1700) { tiles += rectOf("card:home:week") }
        saveStrip("week-ring", f.filterIndexed { i, _ -> i % 2 == 0 }.map { crop(it, region) })
        val old = f[14]; val rest = f.last()
        assertTrue("the ring did not change", differing(old, rest, region) > 40)
        assertTrue("the ring moved before Home was shown again: ${differing(f[15], old, region)}", differing(f[15], old, region) < 40)
        val mids = between(f, 17, old, rest, region, margin = 20)
        assertTrue("the segment did not draw: $mids", mids.size >= 4)
        for (i in 104 until f.size) assertTrue("frame $i is still moving", differing(f[i], rest, region) < 15)
        assertTrue("the tile moved", tiles.all { Math.abs(it.top - tile.top) < 1f && Math.abs(it.height - tile.height) < 1f })
        // Once.
        go { model.tab = Tab.Progress }; settle()
        val again = frames({ model.tab = Tab.Home }, 700)
        assertTrue("the ring drew again: ${between(again, 17, again[14], again.last(), ringRegion(), margin = 20)}", between(again, 17, again[14], again.last(), ringRegion(), margin = 20).isEmpty())
        scenario.close()
    }

    @Test fun weekRingIsCutWithAnimationsOff() {
        val scenario = launch(beforeThisWeek, motion = false)
        val count = model.consistency.value!!.now.count
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, "Push", 9, 100)
        waitReal("the week counts the workout") { model.consistency.value!!.now.count == count + 1 }
        settle(1000)
        val region = ringRegion()
        val f = frames({ model.back() }, 700)
        assertTrue("the ring drew with animations off: ${between(f, 14, f[14], f.last(), region, margin = 20)}", between(f, 14, f[14], f.last(), region, margin = 20).isEmpty())
        scenario.close()
    }

    // ---- 3. The anniversary line ----

    @Test fun homeSaysAYearOfTrainingOnTheDay() {
        val scenario = launch(listOf(Day(0L, "Push", 60.0)).let { listOf(Day(ChronoUnit.DAYS.between(LocalDate.now().minusYears(1), LocalDate.now()), "Push", 60.0)) + rotation })
        compose.onNode(hasText("1 year of training today", substring = true), useUnmergedTree = true).assertExists()
        scenario.close()
    }

    @Test fun homeSaysNothingOnOtherDays() {
        val scenario = launch(listOf(Day(ChronoUnit.DAYS.between(LocalDate.now().minusYears(1), LocalDate.now()) + 1, "Push", 60.0)) + rotation)
        assertEquals(0, compose.onAllNodesWithText("of training today", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        scenario.close()
    }

    // ---- 5. The all-time-high bar's glint ----

    /** An old big day, so it is the high first: this month when it can be, with smaller days in other months. */
    private fun highHistory(): List<Day> {
        val big = if (LocalDate.now().day >= 3) 2L else 0L
        return listOf(Day(70, "Push", 40.0), Day(50, "Pull", 40.0), Day(big, "Push", 100.0))
    }

    private fun barsRegion(): Rect = rectDescribed("Volume lifted").let { Rect(it.left, it.top, it.right, it.bottom) }

    private fun onProgressWithBars() {
        go { model.stack.clear(); model.tab = Tab.Progress }
        settle()
        go { runBlocking { model.tabLists.getValue(Tab.Progress).scrollToItem(3) } }
        settle()
    }

    @Test fun anAllTimeHighBarGlintsOnceWhenItComesIntoView() {
        val scenario = launch(highHistory().let { if (LocalDate.now().day >= 3) it else it.dropLast(1) + Day(0, "Push", 100.0) })
        onProgressWithBars()
        val seen = model.seen["high:DAY"] as? Double
        assertNotNull("the high of the first look was not remembered", seen)
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, "Pull", 9, 120)
        waitReal("the new day is in the log") { model.workouts.value.any { it.name == "Pull" && it.day == model.today.toString() } }
        settle(1500)
        // Nothing glinted while a page covered the bars; what was last seen is still the old high.
        assertEquals(seen, model.seen["high:DAY"] as? Double)
        val region = barsRegion()
        val f = frames({ model.back() }, 1000)
        saveStrip("high-glint", f.filterIndexed { i, _ -> i % 2 == 0 }.map { crop(it, region) })
        val rest = f.last()
        val lit = (16 until f.size - 1).filter { differing(f[it], rest, region) > 20 }
        assertTrue("the cap did not glint: $lit", lit.size >= 3)
        assertTrue("the glint ran longer than 700 ms: ${lit.last()}", lit.last() * 16 <= 800)
        for (i in 60 until f.size) assertTrue("frame $i is not at rest", differing(f[i], rest, region) < 20)
        assertTrue("a new high was not remembered", (model.seen["high:DAY"] as? Double ?: 0.0) > seen!!)
        // Once: out and back shows nothing.
        go { model.open(Screen.History) }; settle()
        val again = frames({ model.back() }, 800)
        val litAgain = (16 until again.size - 1).filter { differing(again[it], again.last(), region) > 20 }
        assertTrue("the glint came again: $litAgain", litAgain.isEmpty())
        scenario.close()
    }

    @Test fun anAllTimeHighBarDoesNotGlintWithAnimationsOff() {
        val scenario = launch(highHistory().let { if (LocalDate.now().day >= 3) it else it.dropLast(1) + Day(0, "Push", 100.0) }, motion = false)
        onProgressWithBars()
        go { model.open(Screen.History) }
        settle()
        addWorkout(0, "Pull", 9, 120)
        waitReal("the new day is in the log") { model.workouts.value.any { it.name == "Pull" && it.day == model.today.toString() } }
        settle(1500)
        val region = barsRegion()
        val f = frames({ model.back() }, 800)
        val lit = (16 until f.size - 1).filter { differing(f[it], f.last(), region) > 20 }
        assertTrue("it glinted with animations off: $lit", lit.isEmpty())
        scenario.close()
    }

    // ---- 7. A double tap on a chart ----

    private fun weighIns() = runBlocking {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        for ((back, kg) in listOf(300L to 80.0, 240L to 79.0, 200L to 78.0, 150L to 77.5, 110L to 77.0, 80L to 76.0, 60L to 75.6, 45L to 75.2, 30L to 74.0, 20L to 74.6, 10L to 73.5, 3L to 73.0)) {
            assertTrue("weigh-in", model.addMeasurement("weight.csv", kg, LocalDateTime.now().minusDays(back).withHour(8).withMinute(0).withSecond(0).format(format)))
        }
    }

    private fun chartNode() = compose.onNode(hasContentDescription("Chart", substring = true), useUnmergedTree = true)

    private fun openWeight() {
        weighIns()
        go { model.setOption("ui.measurementRange", "365") }
        settle()
        go { model.stack.clear(); model.open(Screen.MeasurementDetail("weight.csv")) }
        settle(2500)
    }

    private fun doubleTapChart() { chartNode().performTouchInput { doubleClick(Offset(width * 0.5f, height * 0.5f)) } }

    @Test fun aDoubleTapSwitchesAChartBetweenTheWholeRangeAndTheLastThreeMonths() {
        val scenario = launch(rotation)
        openWeight()
        val box = chartNode().fetchSemanticsNode().boundsInRoot
        val region = Rect(box.left, box.top - 8f, box.right, box.bottom + 40f)
        compose.onAllNodesWithText("Last 3 months", useUnmergedTree = true).assertCountEquals0()
        val whole = frames({}, 16).first()
        compose.mainClock.autoAdvance = false
        val shots = mutableListOf(frame())
        doubleTapChart()
        for (t in 0 until 640 step 16) { compose.mainClock.advanceTimeBy(16); shots += frame() }
        compose.mainClock.advanceTimeBy(3000); shots += frame()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip("chart-recent", shots.filterIndexed { i, _ -> i % 2 == 0 }.map { crop(it, region) }, 8)
        // The page has its own 3M / 1Y / All control: the double tap moved it to 3M, and no separate label says so.
        waitReal("the control shows 90") { model.options.value["ui.measurementRange"] == "90" }
        compose.onNode(hasText("3M") and isSelected()).assertExists()
        compose.onAllNodesWithText("Last 3 months", useUnmergedTree = true).assertCountEquals0()
        val first = shots.first(); val last = shots.last()
        assertTrue("the chart did not change", differing(first, last, region) > 300)
        val mids = shots.indices.filter { differing(shots[it], last, region) > 80 && differing(shots[it], first, region) > 80 }
        assertTrue("it cut instead of stretching: ${mids.size} frames in between", mids.size >= 6)
        assertTrue("the chart went blank at the start", differing(shots[2], first, region) * 3 < region.width.toInt() * region.height.toInt())
        // A second double tap goes back to the option that was chosen before (1Y), the control with it.
        go { }
        compose.mainClock.autoAdvance = false
        doubleTapChart()
        compose.mainClock.advanceTimeBy(3000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodesWithText("Last 3 months", useUnmergedTree = true).assertCountEquals0()
        waitReal("the control shows 365") { model.options.value["ui.measurementRange"] == "365" }
        settle()
        assertTrue("the whole range did not come back: ${differing(frame(), whole, region)}", differing(frame(), whole, region) < 120)
        compose.onNode(hasText("1Y") and isSelected()).assertExists()
        // The choice is kept while the page stays: another page on top and Back.
        doubleTapChart(); settle()
        waitReal("the control shows 90") { model.options.value["ui.measurementRange"] == "90" }
        go { model.open(Screen.History) }; settle()
        go { model.back() }; settle()
        waitReal("the control shows 90") { model.options.value["ui.measurementRange"] == "90" }
        scenario.close()
    }

    @Test fun aSingleTapStillOpensThePointCardAndADragStillScrollsThePage() {
        val scenario = launch(rotation)
        openWeight()
        val before = chartNode().fetchSemanticsNode().boundsInRoot.top
        chartNode().performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) }
        settle()
        val after = chartNode().fetchSemanticsNode().boundsInRoot.top
        assertTrue("a drag on the chart did not scroll the page: $before -> $after", after < before - 20f)
        go { model.stack.clear(); model.open(Screen.MeasurementDetail("weight.csv")) }
        settle(2000)
        val kg = { compose.onAllNodes(hasText(" kg", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size }
        val shown = kg()
        chartNode().performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        settle(600)
        assertTrue("a tap did not open the point's card: $shown -> ${kg()}", kg() > shown)
        compose.onAllNodesWithText("Last 3 months", useUnmergedTree = true).assertCountEquals0()
        scenario.close()
    }

    @Test fun aDoubleTapIsCutWithAnimationsOff() {
        val scenario = launch(rotation, motion = false)
        openWeight()
        val box = chartNode().fetchSemanticsNode().boundsInRoot
        val region = Rect(box.left, box.top - 8f, box.right, box.bottom + 40f)
        compose.mainClock.autoAdvance = false
        val first = frame()
        doubleTapChart()
        val shots = mutableListOf<Bitmap>()
        for (t in 0 until 480 step 16) { compose.mainClock.advanceTimeBy(16); shots += frame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val last = shots.last()
        assertTrue("the chart did not change", differing(first, last, region) > 300)
        val mids = shots.indices.filter { differing(shots[it], last, region) > 80 && differing(shots[it], first, region) > 80 }
        assertTrue("it stretched with animations off: ${mids.size} frames", mids.size <= 1)
        scenario.close()
    }

    // ---- 8. The muscles trained today breathe once ----

    private fun figureRegion(): Rect = rectDescribed("Body figure")

    private fun onBodyCard(motion: Boolean, trainedToday: Boolean): ActivityScenario<MainActivity> {
        val scenario = launch(rotation, motion, today = if (trainedToday) listOf(Day(0, "Push", 80.0)) else emptyList())
        waitReal("the figures are worked out") { model.muscleFigures.value?.lastTrained?.isNotEmpty() == true }
        // Progress is the tab, a page covers it: the card is composed and in the window, but not in view.
        go { model.stack.clear(); model.tab = Tab.Progress; model.open(Screen.History) }
        settle(2500)
        return scenario
    }

    @Test fun theMusclesTrainedTodayBreatheOnceTheFirstTimeTheCardIsInView() {
        val scenario = onBodyCard(motion = true, trainedToday = true)
        assertTrue("some muscles were trained today", model.muscleFigures.value!!.lastTrained.values.any { it.date == model.today })
        assertNull("it breathed under a page", model.breathDay())
        val region = figureRegion()
        val figures = ArrayList<Rect>()
        val f = frames({ model.back() }, 1100) { figures += figureRegion() }
        saveStrip("body-breath", f.filterIndexed { i, _ -> i % 2 == 0 }.map { crop(it, region) }, 12)
        val rest = f.last()
        val lit = (16 until f.size - 1).filter { differing(f[it], rest, region) > 150 }
        assertTrue("the muscles did not brighten: $lit", lit.size >= 4)
        assertTrue("the breath ran past 900 ms: ${lit.last()}", lit.last() * 16 <= 1000)
        for (i in 66 until f.size) assertTrue("frame $i is not at rest", differing(f[i], rest, region) < 30)
        assertTrue("the figure moved: ${figures.distinct()}", figures.all { Math.abs(it.top - region.top) < 1f && Math.abs(it.left - region.left) < 1f && Math.abs(it.width - region.width) < 1f })
        waitReal("the day is marked") { model.breathDay() == model.today.toString() }
        // Once that day: out and back shows no breath.
        go { model.open(Screen.History) }; settle()
        val again = frames({ model.back() }, 1000)
        val litAgain = (16 until again.size - 1).filter { differing(again[it], again.last(), region) > 150 }
        assertTrue("it breathed again: $litAgain", litAgain.isEmpty())
        scenario.close()
    }

    private fun noBreath(motion: Boolean, today: Boolean) {
        val scenario = onBodyCard(motion, today)
        val region = figureRegion()
        val f = frames({ model.back() }, 1000)
        val lit = (16 until f.size - 1).filter { differing(f[it], f.last(), region) > 150 }
        assertTrue("it breathed (motion=$motion, today=$today): $lit", lit.isEmpty())
        assertNull(model.breathDay())
        scenario.close()
    }

    @Test fun theMusclesDoNotBreatheWithAnimationsOff() = noBreath(motion = false, today = true)

    @Test fun theMusclesDoNotBreatheWhenNoneTrainedToday() = noBreath(motion = true, today = false)

    // ---- strips for a look ----

    private fun crop(f: Bitmap, r: Rect): Bitmap {
        val x = r.left.toInt().coerceIn(0, f.width - 1)
        val y = r.top.toInt().coerceIn(0, f.height - 1)
        return Bitmap.createBitmap(f, x, y, r.width.toInt().coerceAtMost(f.width - x).coerceAtLeast(1), r.height.toInt().coerceAtMost(f.height - y).coerceAtLeast(1))
    }

    private fun saveStrip(name: String, frames: List<Bitmap>, perRow: Int = 10) {
        if (frames.isEmpty()) return
        val w = frames.maxOf { it.width }; val h = frames.maxOf { it.height }
        val cols = minOf(perRow, frames.size)
        val rows = (frames.size + cols - 1) / cols
        val strip = java.awt.image.BufferedImage(w * cols, h * rows, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b ->
            val p = IntArray(b.width * b.height)
            b.getPixels(p, 0, b.width, 0, 0, b.width, b.height)
            strip.setRGB((i % cols) * w, (i / cols) * h, b.width, b.height, p, 0, b.width)
        }
        java.io.File("build/round15-frames").mkdirs()
        javax.imageio.ImageIO.write(strip, "png", java.io.File("build/round15-frames/$name.png"))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
    val n = fetchSemanticsNodes().size
    assertTrue("expected none, found $n", n == 0)
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountAtLeast1() {
    assertTrue("expected some, found none", fetchSemanticsNodes().isNotEmpty())
}
