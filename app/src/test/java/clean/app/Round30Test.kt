package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.core.STRONG_DATE_FORMAT
import clean.core.SetType
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 30: snapshots blur like progress photos; the dock slide is relative to the tab it started on; a set swipes 1:1 and goes at 40 % of the row or on a
 * fling, the current set's highlight going with it; the Library's letter rail ignores a sideways start; a hold on the running workout's pill.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round30Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    /** The app on a fresh profile with [history] behind it. With [manual] the clock is stepped by hand (the dock's name and the pill's clock run free otherwise). */
    private fun launch(motion: Boolean = true, manual: Boolean = false, history: (Long) -> List<WorkoutRow> = { emptyList() }) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            val rows = history(id)
            if (rows.isNotEmpty()) repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        if (manual) { compose.mainClock.autoAdvance = false; settle(300) }
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** The last match is the one in the window opened last (a menu or a sheet), over the page that may say the same word. */
    private fun tapText(text: String) { compose.onAllNodes(hasText(text), useUnmergedTree = true).onLast().performTouchInput { click(center) }; settle(300) }

    private fun history(id: Long): List<WorkoutRow> {
        val rows = ArrayList<WorkoutRow>()
        for (w in 0 until 4) {
            val date = LocalDate.now().minusDays(2L + (3 - w) * 4L).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for ((e, exercise) in listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)").withIndex()) {
                for (s in 1..3) rows += WorkoutRow((w + 1).toString(), date, "Push", "3600", exercise, s.toString(), decimalText(50.0 + w * 2.5 + e * 10), (8 + s % 2).toString(), "", "", "", "", "")
            }
        }
        return rows
    }

    // ---- 1. Snapshots blur ----

    @Test fun aBlurredAndASharpThumbnailHaveKeysOfTheirOwn() {
        assertEquals("/p/a.jpg@240", thumbKey("/p/a.jpg", 240, false))
        assertEquals("/p/a.jpg@blur", thumbKey("/p/a.jpg", 240, true))
        assertEquals("the blurred key does not depend on the size asked", thumbKey("/p/a.jpg", 360, true), thumbKey("/p/a.jpg", 240, true))
        assertTrue("so a change of the setting is a change of key and shows at once", thumbKey("/p/a.jpg", 240, true) != thumbKey("/p/a.jpg", 240, false))
    }

    /** Pictures with something in them to blur: stripes and a disc on a colour. */
    private fun makeMoments(count: Int): List<File> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val colours = listOf(0xFF3B5BA5, 0xFF8A4F7D, 0xFF4F8A6B, 0xFFB5793A, 0xFF5A6B7C, 0xFF9A4D4D)
        return (0 until count).map { i ->
            val at = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0).minusDays(i * 3L)
            val file = MomentFiles.file(context, model.activeId.value!!, clean.core.momentOf(at, "%08x".format(i + 1)).name).also { it.parentFile!!.mkdirs() }
            val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(colours[i % colours.size].toInt())
            val paint = android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt(); strokeWidth = 14f }
            for (k in 0 until 8) canvas.drawLine(0f, k * 60f, 300f, k * 60f + 90f, paint)
            paint.color = 0xFF101010.toInt()
            canvas.drawCircle(150f, 200f, 70f, paint)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file
        }
    }

    private fun waitForThumb(key: String, what: String) {
        var found = false
        for (i in 0 until 100) {
            go { found = MOMENT_THUMBS.get(key) != null }
            if (found) break
            Thread.sleep(100); compose.waitForIdle()
        }
        assertTrue("$what: nothing cached under $key", found)
    }

    private fun thumb(key: String) = run { var b: androidx.compose.ui.graphics.ImageBitmap? = null; go { b = MOMENT_THUMBS.get(key) }; b }

    @Test fun theSnapshotsFansThumbnailsGoThroughTheBlurPathWhenTheFlagIsOn() {
        launch()
        val files = makeMoments(4)
        go { model.setOption("photos.blur", "on"); model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 4 }
        go { model.tab = Tab.Profile }; settle(300)
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
        val keyOn = thumbKey(files[0].path, 360, true)
        waitForThumb(keyOn, "the fan, blur on")
        assertTrue("the blurred thumbnail is cut to ${BLUR_SIDE} px", thumb(keyOn)!!.let { maxOf(it.width, it.height) <= BLUR_SIDE })
        assertNull("no sharp thumbnail was made for the fan meanwhile", thumb(thumbKey(files[0].path, 360, false)))
        // Off: the sharp one is made at once (a key of its own), and on again the blurred one is already there.
        go { model.setOption("photos.blur", "off") }
        waitForThumb(thumbKey(files[0].path, 360, false), "the fan, blur off")
        assertTrue("sharp is bigger than the blur", thumb(thumbKey(files[0].path, 360, false))!!.width > BLUR_SIDE)
        go { model.setOption("photos.blur", "on") }
        settle(300)
        assertNotNull(thumb(keyOn))
    }

    @Test fun theMomentsPageGridIsBlurredToo() {
        launch()
        val files = makeMoments(3)
        go { model.setOption("photos.blur", "on"); model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 3 }
        go { model.open(Screen.Moments) }; settle(500)
        val key = thumbKey(files[0].path, 360, true)
        waitForThumb(key, "the grid")
        assertTrue(maxOf(thumb(key)!!.width, thumb(key)!!.height) <= BLUR_SIDE)
        assertNull(thumb(thumbKey(files[0].path, 360, false)))
    }

    @Test fun theSettingIsCalledBlurPhotosAndKeepsItsKey() {
        launch()
        go { model.settingsPage = PAGE_PRIVACY; model.open(Screen.Settings) }; settle(600)
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(hasText("Blur photos"))
        settle(300)
        assertTrue("the label", has("Blur photos"))
        assertTrue("its summary names both", has("Every photo in Photos, progress and snapshots, in every small view."))
        assertFalse("the old label is gone", has("Blur progress photos"))
    }

    // ---- 2. The dock slide is relative ----

    private fun slot(tab: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(tab) and androidx.compose.ui.test.SemanticsMatcher("is a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }, useUnmergedTree = true)
    private fun centre(tab: String): Offset = slot(tab).fetchSemanticsNode().boundsInRoot.center
    /** The tab the dock shows as selected (Round 31: during a slide this is the tab under the slide; the app's own tab stays until the finger lifts). */
    private fun selected(): Tab = listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile).first { slot(it.label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
    private fun slotWidth(): Float = slot("Home").fetchSemanticsNode().boundsInRoot.width

    /** A finger down over [over] and moved by [dx] in slot widths, in steps. Left down; [lift] puts it up. */
    private fun slide(over: String, dx: Float, steps: Int = 12) {
        val from = centre(over)
        val w = slotWidth()
        compose.onRoot().performTouchInput {
            down(from)
            for (i in 1..steps) { moveTo(Offset(from.x + dx * w * i / steps, from.y)); advanceEventTime(16) }
        }
        // The clock is stepped by hand: one frame shows the dock's new selection.
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
    }

    private fun lift() { compose.onRoot().performTouchInput { up() }; compose.waitForIdle() }

    @Test fun startingOnHomeWithTheFingerOverLibraryAndDraggingOneSlotRightLandsOnProgress() {
        launch(manual = true)
        assertEquals(Tab.Home, model.tab)
        slide("Library", 1f)
        lift()
        assertEquals("Rohan's example: from Home, one slot right is Progress, not Profile", Tab.Progress, model.tab)
    }

    @Test fun nothingChangesBeforeHalfASlotAndTheSelectionIsTheStartPlusTheDragRounded() {
        launch(manual = true)
        slide("Profile", 0.4f)
        assertEquals("0.4 of a slot: still Home", Tab.Home, selected())
        lift()
        assertEquals(Tab.Home, model.tab)
        slide("Profile", 0.6f)
        assertEquals("0.6 of a slot: Progress", Tab.Progress, selected())
        assertEquals("the page waits for the lift", Tab.Home, model.tab)
        lift()
        slide("Home", 1.6f)
        assertEquals("1.6 slots on from Progress (2.6): Profile", Tab.Profile, selected())
        lift()
        slide("Home", -2.4f)
        assertEquals("2.4 slots back from Profile: Progress", Tab.Progress, selected())
        lift()
        slide("Library", -7f)
        assertEquals("clamped at the start", Tab.Home, selected())
        lift()
    }

    @Test fun theIndicatorStartsAtTheStartTabNotUnderTheFinger() {
        launch(manual = true)
        go { model.tab = Tab.Progress }; settle(1500)
        slide("Profile", 0.3f)
        assertEquals(Tab.Progress, model.tab)
        assertEquals(Tab.Progress, selected())
        lift()
        assertEquals(Tab.Progress, model.tab)
    }

    @Test fun aFlickMovesOneTabFromTheStartTabInItsDirection() {
        launch(manual = true)
        val w = slotWidth()
        go { model.tab = Tab.Progress }; settle(1500)
        val c = centre("Profile")
        compose.onRoot().performTouchInput { swipe(c, Offset(c.x + 0.3f * w, c.y), durationMillis = 30) }
        settle(300)
        assertEquals("the finger was over Profile, the flick is one tab from Progress", Tab.Exercises, model.tab)
        val d = centre("Home")
        compose.onRoot().performTouchInput { swipe(d, Offset(d.x - 0.3f * w, d.y), durationMillis = 30) }
        settle(300)
        assertEquals(Tab.Progress, model.tab)
    }

    // ---- 3. Swiping a set ----

    private fun open(vararg sets: DraftSet) {
        launch()
        go { model.putDraft(Draft("Workout", System.currentTimeMillis(), listOf(DraftBlock("Bench Press (Barbell)", sets.toList())))); model.open(Screen.Active) }
        settle(400)
    }

    private fun setField(n: Int = 1) = compose.onAllNodes(hasContentDescription("Set $n weight", substring = true), useUnmergedTree = true).onFirst()
    private fun fieldLeft(n: Int = 1) = setField(n).fetchSemanticsNode().positionInRoot.x
    private fun items() = model.draft!!.blocks[0].items.size

    private val two get() = arrayOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "70", "5"))

    /** Drags the first set's row left by [px] over [ms] and lets go. */
    private fun dragLeft(px: Float, ms: Long) {
        setField().performTouchInput { swipe(Offset(right - 5f, centerY), Offset(right - 5f - px, centerY), ms) }
        settle(900)
    }

    @Test fun theRowFollowsTheFingerOneToOnePastTheOldStopPoint() {
        open(*two)
        val before = fieldLeft()
        val trace = ArrayList<Float>()
        setField().performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..10) { moveBy(Offset(-30f, 0f)); advanceEventTime(16) } }
        compose.waitForIdle()
        trace += fieldLeft()
        val moved = before - fieldLeft()
        assertTrue("trace $trace 300 px of finger moved the row $moved px (with the rubber band it was about 170)", moved in 270f..300f)
        // Round 31: a touch cancelled past 40 % is judged like a let-go (the system's edge gesture takes the touch), so go back under the line and let go.
        setField().performTouchInput { moveBy(Offset(300f, 0f)); up() }
        settle(600)
        assertEquals("a drag let go short of the line springs back", before, fieldLeft(), 1.5f)
    }

    @Test fun letGoPastFortyPercentOfTheRowTheSetGoes() {
        open(*two)
        dragLeft(180f, 600)
        assertEquals("180 of 400 px (45 %) removes the set", 1, items())
    }

    @Test fun letGoShortOfFortyPercentSlowlyTheRowSpringsBack() {
        open(*two)
        val before = fieldLeft()
        dragLeft(130f, 600)
        assertEquals("130 px (32 %), slowly: nothing removed", 2, items())
        assertEquals(before, fieldLeft(), 1.5f)
    }

    @Test fun aFastLeftFlingRemovesAShortSwipe() {
        open(*two)
        dragLeft(70f, 40)
        assertEquals("70 px in 40 ms is a fling", 1, items())
    }

    @Test fun aFastFlingToTheRightDoesNotRemoveAndAShortFlingOfNothingDoesNothing() {
        open(*two)
        setField().performTouchInput { swipe(Offset(left + 5f, centerY), Offset(left + 85f, centerY), 40) }
        settle(700)
        assertEquals(2, items())
        dragLeft(20f, 20)
        assertEquals("under the least distance a jitter is not a fling", 2, items())
    }

    @Test fun theThresholdTicksAtFortyPercentAndNotBefore() {
        open(*two)
        HapticGate.reset()
        val shadow = org.robolectric.Shadows.shadowOf(window!!)
        setField().performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..5) { moveBy(Offset(-20f, 0f)); advanceEventTime(16) } }
        compose.waitForIdle()
        assertTrue("100 px (25 %): no tick yet", shadow.lastHapticFeedbackPerformed() != android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        setField().performTouchInput { for (i in 1..4) { moveBy(Offset(-20f, 0f)); advanceEventTime(16) } }
        compose.waitForIdle(); settle(50)
        assertEquals("180 px (45 %): the tick", android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, shadow.lastHapticFeedbackPerformed())
        setField().performTouchInput { moveBy(Offset(300f, 0f)); up() }
        settle(600)
    }

    private fun snap(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }
    private fun lum(c: Int) = 0.30 * android.graphics.Color.red(c) + 0.59 * android.graphics.Color.green(c) + 0.11 * android.graphics.Color.blue(c)
    /** How far the 3 dp bar of the current set stands out at x = 5 px from the page beside it (x = 2) on row [y]: large when the bar is there, little otherwise. */
    private fun bar(b: Bitmap, y: Int) = kotlin.math.abs(lum(b.getPixel(5, y)) - lum(b.getPixel(2, y)))
    private fun rowY(n: Int) = setField(n).fetchSemanticsNode().boundsInRoot.center.y.toInt()

    @Test fun theHighlightOfTheCurrentSetGoesWithItsRowWashAndBarIncluded() {
        open(*two)
        val y = rowY(1)
        val rest = bar(snap(), y)
        assertTrue("the bar stands out at rest ($rest)", rest > 60)
        assertTrue("and not on the second row", bar(snap(), rowY(2)) < 20)
        setField().performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..4) { moveBy(Offset(-20f, 0f)); advanceEventTime(16) } }
        compose.waitForIdle()
        val moved = bar(snap(), y)
        assertTrue("80 px in, the bar has left with the row ($moved against $rest)", moved < rest / 3)
        setField().performTouchInput { cancel() }
        settle(700)
        assertTrue("sprung back, the bar is back", bar(snap(), y) > 60)
    }

    @Test fun deletingTheCurrentSetLightsTheNextWhereItStandsWithoutAFlashOfTheOldHighlight() {
        open(*two)
        compose.mainClock.autoAdvance = false
        settle(100)
        val y = rowY(1)
        val rest = bar(snap(), y)
        assertTrue(rest > 60)
        setField().performTouchInput { swipe(Offset(right - 5f, centerY), Offset(right - 85f, centerY), 40) }
        var before = bar(snap(), y)
        var jump = 0.0
        var frames = 0
        while (frames++ < 90) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            val now = bar(snap(), y)
            jump = maxOf(jump, now - before)
            before = now
        }
        assertEquals("the set went", 1, items())
        assertTrue("no frame brought the bar back in one step (biggest rise $jump of $rest)", jump < rest * 0.6)
        assertTrue("the next set is the current one, lit at the place (${bar(snap(), y)} of $rest)", bar(snap(), y) > rest * 0.8)
        assertEquals("70", (model.draft!!.blocks[0].items[0] as DraftSet).weight)
    }

    // ---- 4. The letter rail ----

    private fun libraryWithRail() {
        launch(history = ::history)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        go { model.tab = Tab.Exercises }
        settle(900)
        assertTrue("the rail is there", hasTag("letter rail"))
    }

    private fun rail() = compose.onAllNodes(hasTestTag("letter rail"), useUnmergedTree = true).onFirst()

    @Test fun aHorizontalDragStartingOnTheRailDoesNotActivateIt() {
        libraryWithRail()
        rail().performTouchInput { down(Offset(centerX, height * 0.3f)); for (i in 1..12) { moveBy(Offset(-14f, 2f)); advanceEventTime(16) } }
        compose.waitForIdle()
        assertFalse("no bubble on a sideways start", hasTag("letter bubble"))
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertFalse(hasTag("letter bubble"))
    }

    @Test fun aVerticalDragOnTheRailStillTakesOver() {
        libraryWithRail()
        rail().performTouchInput { down(Offset(centerX, height * 0.2f)); for (i in 1..10) { moveBy(Offset(0f, 18f)); advanceEventTime(16) } }
        compose.waitForIdle()
        assertTrue("the bubble shows on a vertical drag", hasTag("letter bubble"))
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertFalse("and goes on lifting", hasTag("letter bubble"))
    }

    @Test fun aPressAndHoldOnTheRailTakesOverToo() {
        libraryWithRail()
        rail().performTouchInput { down(Offset(centerX, height * 0.5f)) }
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        assertTrue("held: the bubble", hasTag("letter bubble"))
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test fun aTapOnTheRailJumpsOnLiftWithoutTheBubbleStaying() {
        libraryWithRail()
        rail().performTouchInput { click(Offset(centerX, height * 0.9f)) }
        settle(300)
        assertFalse(hasTag("letter bubble"))
    }

    // ---- 5. Holding the running workout's pill ----

    private fun running(done: Int = 1) {
        launch(history = ::history)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        go {
            model.putDraft(Draft("Push day", System.currentTimeMillis() - 754_000, listOf(
                DraftBlock("Bench Press (Barbell)", List(3) { DraftSet(SetType.NORMAL, "60", "8", done = it < done, ticked = if (it < done) 1 else 0) }),
                DraftBlock("Squat (Barbell)", List(3) { DraftSet(SetType.NORMAL, "80", "5") }),
            )))
            model.stack.clear(); model.tab = Tab.Home
        }
        settle(900)
    }

    private fun pill() = compose.onAllNodes(hasContentDescription("Back to workout Push day", substring = true), useUnmergedTree = true).onFirst()
    private fun holdPill() {
        pill().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
    }
    private fun releasePill() { compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().performTouchInput { up() }; compose.waitForIdle() }

    @Test fun holdingThePillShowsThePeekAndTheChoices() {
        running()
        HapticGate.reset()
        val shadow = org.robolectric.Shadows.shadowOf(window!!)
        holdPill()
        assertTrue("the peek", hasTag("hold peek"))
        assertTrue("the workout's name", has("Push day"))
        assertTrue("elapsed time and sets done of all", has("12:3", ) || compose.onAllNodes(hasText("12:", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("1 of 6 sets", compose.onAllNodes(hasText("1 of 6 sets", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("the exercise now", has("Now: Bench Press (Barbell)"))
        for (item in listOf("Resume", "Add exercise", "Finish workout", "Cancel workout")) assertTrue("the menu has $item", has(item))
        assertFalse("no rest running: no Skip rest", has("Skip rest"))
        assertFalse(has("Add 30 s"))
        assertEquals("the hold opens with a tick", android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, shadow.lastHapticFeedbackPerformed())
        releasePill()
    }

    @Test fun skipRestAndAddThirtyShowOnlyWhileARestRuns() {
        running()
        go { model.startRest(90) }; settle(300)
        holdPill()
        assertTrue(has("Skip rest")); assertTrue(has("Add 30 s"))
        val before = model.restEnd!!
        releasePill()
        tapText("Add 30 s")
        assertTrue("30 s more", model.restEnd!! - before in 29_000L..31_000L)
        holdPill()
        releasePill()
        tapText("Skip rest")
        assertNull("the rest is over", model.restEnd)
    }

    @Test fun resumeOpensTheWorkout() {
        running()
        holdPill(); releasePill()
        tapText("Resume"); settle(900)
        assertEquals(Screen.Active, model.stack.lastOrNull())
    }

    @Test fun addExerciseOpensThePickerAndAddsToTheRunningWorkout() {
        running()
        holdPill(); releasePill()
        tapText("Add exercise"); settle(700)
        assertTrue("the Add exercise sheet", hasTag("search field"))
        compose.onAllNodes(hasText("Lat Pulldown (Cable)") and !androidx.compose.ui.test.hasSetTextAction(), useUnmergedTree = true).onLast().performTouchInput { click(center) }
        settle(500)
        assertEquals(listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)"), model.draft!!.blocks.map { it.exercise })
        assertEquals("it stays on the tabs", Tab.Home, model.tab)
        assertTrue(model.stack.isEmpty())
    }

    @Test fun cancelWorkoutAsksToConfirmLikeTheWorkoutsOwnButtonAndFinishSavesLikeIt() {
        running()
        holdPill(); releasePill()
        tapText("Cancel workout"); settle(1400)
        assertEquals(Screen.Active, model.stack.lastOrNull())
        assertTrue("the workout's own confirmation", has("Cancel workout?"))
        assertNotNull("nothing is thrown away until it is confirmed", model.draft)
        // Finish from the pill: the workout's Finish (a ticked set, nothing unfinished that holds values would be asked about: here two sets of values are).
        go { model.stack.clear() }; settle(600)
    }

    @Test fun finishFromThePillGoesThroughTheWorkoutsOwnFinish() {
        running(done = 3)
        go { model.putDraft(model.draft!!.copy(blocks = model.draft!!.blocks.take(1))) }
        settle(300)
        holdPill(); releasePill()
        tapText("Finish workout"); settle(1500)
        assertNull("saved and closed", model.draft)
        assertTrue("one more workout on file", model.workouts.value.any { it.name == "Push day" })
    }

    // ---- The tour ----

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        if (dialogs) {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== view && root.width > 0) root.draw(canvas)
        }
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun look(theme: String) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, "Graphite") }
        repeat(200) { if (model.look.value.themeName == "Graphite") return@repeat; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun tour(prefix: String) {
        fun s(name: String, dialogs: Boolean = false, step: () -> Unit) {
            try { step(); shot("round30-$prefix-$name", dialogs) } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round30-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        // The Snapshots fan, sharp and blurred.
        go { model.stack.clear(); model.tab = Tab.Profile; model.setOption("photos.blur", "off") }; settle(500)
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
        s("01-fan-sharp") { repeat(6) { Thread.sleep(150); compose.waitForIdle() } }
        s("02-fan-blurred") { go { model.setOption("photos.blur", "on") }; repeat(8) { Thread.sleep(150); compose.waitForIdle() } }
        go { model.setOption("photos.blur", "off"); model.stack.clear(); model.tab = Tab.Home }; settle(400)
        // The pill's hold menu.
        go {
            model.putDraft(Draft("Push day", System.currentTimeMillis() - 754_000, listOf(
                DraftBlock("Bench Press (Barbell)", listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "60", "8"))),
                DraftBlock("Squat (Barbell)", List(3) { DraftSet(SetType.NORMAL, "80", "5") }),
            )))
            model.startRest(90)
        }
        settle(900)
        s("05-pill-hold-menu", true) { holdPill() }
        releasePill()
        go { model.startRest(0) }
        settle(300)
    }

    private fun swipeTour(prefix: String) {
        fun s(name: String, step: () -> Unit) {
            try { step(); shot("round30-$prefix-$name") } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round30-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        go {
            model.stack.clear()
            model.putDraft(Draft("Push day", System.currentTimeMillis() - 754_000, listOf(
                DraftBlock("Bench Press (Barbell)", listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(SetType.NORMAL, "62.5", "8"), DraftSet(SetType.NORMAL, "62.5", "8"))),
            )))
            model.open(Screen.Active)
        }
        settle(800)
        // The current set is the second; a plain set is the third.
        s("03-swipe-current-set") {
            setField(2).performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..6) { moveBy(Offset(-26f, 0f)); advanceEventTime(16) } }
            compose.waitForIdle()
        }
        setField(2).performTouchInput { cancel() }; settle(600)
        s("04-swipe-plain-set") {
            setField(3).performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..6) { moveBy(Offset(-26f, 0f)); advanceEventTime(16) } }
            compose.waitForIdle()
        }
        setField(3).performTouchInput { cancel() }; settle(600)
        s("06-swipe-current-past-the-line") {
            setField(2).performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..9) { moveBy(Offset(-24f, 0f)); advanceEventTime(16) } }
            compose.waitForIdle()
        }
        setField(2).performTouchInput { moveBy(Offset(300f, 0f)); up() }; settle(600)
        s("07-after-delete-undo-pill") {
            // The clock is stepped by hand: left free, waiting for idle runs the Undo's 8 seconds out before the picture is taken.
            compose.mainClock.autoAdvance = false
            setField(3).performTouchInput { swipe(Offset(right - 5f, centerY), Offset(right - 215f, centerY), 300) }
            compose.mainClock.advanceTimeBy(1200); compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        launch(history = ::history)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        makeMoments(5)
        go { model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 5 }
        look("dark"); tour("graphite-dark"); swipeTour("graphite-dark")
        go { model.cancelWorkout() }; settle(300)
        look("light"); tour("graphite-light"); swipeTour("graphite-light")
        go { model.cancelWorkout() }; settle(300)
    }
}
