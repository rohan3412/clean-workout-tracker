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
import androidx.compose.ui.test.swipe
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
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
 * Round 31: a set swipes away on a quick flick from the screen's edge and when the system takes the touch away after 40 %; Lock photos; the dock slide moves only
 * the dock and the tab changes once on release, as a tap changes it; a soft software blur; the Snapshots fan without its card.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round31Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

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

    // ---- 1. Swiping a set away ----

    private fun open(vararg sets: DraftSet) {
        launch()
        go { model.putDraft(Draft("Workout", System.currentTimeMillis(), listOf(DraftBlock("Bench Press (Barbell)", sets.toList())))); model.open(Screen.Active) }
        settle(400)
    }

    private fun setField(n: Int = 1) = compose.onAllNodes(hasContentDescription("Set $n weight", substring = true), useUnmergedTree = true).onFirst()
    private fun fieldLeft(n: Int = 1) = setField(n).fetchSemanticsNode().positionInRoot.x
    private fun items() = model.draft!!.blocks[0].items.size
    private val two get() = arrayOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "70", "5"))
    private fun rowY(n: Int = 1) = setField(n).fetchSemanticsNode().boundsInRoot.center.y

    @Test fun aQuickFlickFromTheScreensRightEdgeRemovesTheSet() {
        open(*two)
        val y = rowY()
        // About 35 % of the width (140 of 400 px) in 80 ms, from 4 px inside the screen's right edge, as a thumb flicks it.
        compose.onRoot().performTouchInput { swipe(Offset(396f, y), Offset(256f, y), 80) }
        settle(900)
        assertEquals("a quick flick removes the set", 1, items())
    }

    @Test fun aSlowerSwipeFromTheEdgePastFortyPercentRemovesTheSet() {
        open(*two)
        val y = rowY()
        compose.onRoot().performTouchInput { swipe(Offset(396f, y), Offset(216f, y), 250) }
        settle(900)
        assertEquals(1, items())
    }

    /** What the phone does when its edge gesture takes the touch: the app is told the touch is cancelled. This failed before Round 31 (the row sprang back, the set stayed). */
    @Test fun aSwipePastFortyPercentThatTheSystemCancelsStillRemovesTheSet() {
        open(*two)
        val y = rowY()
        compose.onRoot().performTouchInput {
            down(Offset(396f, y))
            for (i in 1..12) { moveBy(Offset(-15f, 0f)); advanceEventTime(8) }
            cancel()
        }
        settle(900)
        assertEquals("180 px (45 %), then the system took the touch: the set goes, as if let go", 1, items())
    }

    @Test fun aShortSwipeTheSystemCancelsSpringsBackWithAnAnimationAndKeepsTheSet() {
        open(*two)
        val before = fieldLeft()
        val y = rowY()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(Offset(396f, y))
            for (i in 1..6) { moveBy(Offset(-15f, 0f)); advanceEventTime(8) }
            cancel()
        }
        compose.waitForIdle()
        val first = fieldLeft()
        assertTrue("moved left by the drag ($first against $before)", first < before - 40f)
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val mid = fieldLeft()
        assertTrue("an animation, not a jump: a frame or two on it is neither where it was nor home ($mid)", mid > first + 1f && mid < before - 1f)
        settle(900)
        assertEquals(before, fieldLeft(), 1.5f)
        assertEquals(2, items())
    }

    @Test fun letGoShortOfTheLineSlowlyTheRowSpringsBackAnimated() {
        open(*two)
        val before = fieldLeft()
        val y = rowY()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(Offset(300f, y))
            for (i in 1..8) { moveBy(Offset(-12f, 0f)); advanceEventTime(60) }
            up()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val mid = fieldLeft()
        assertTrue("sliding home, not at once ($mid, rest $before)", mid < before - 2f && mid > before - 90f)
        settle(900)
        assertEquals(before, fieldLeft(), 1.5f)
        assertEquals(2, items())
    }

    @Test fun theRowSlidesFullyOffThenTheHeightClosesAndUndoShows() {
        open(*two)
        val y = rowY()
        val rest = fieldLeft()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(Offset(396f, y))
            for (i in 1..12) { moveBy(Offset(-15f, 0f)); advanceEventTime(8) }
            up()
        }
        compose.waitForIdle()
        var last = fieldLeft()
        var moving = 0
        val trace = ArrayList<Float>()
        for (f in 0 until 40) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            val now = if (items() == 2) fieldLeft() else break
            trace += now
            if (now < -350f || now > 395f) break // the row is off the screen; what follows is its hidden place closing
            if (now < last - 1f) moving++
            if (now > last + 40f) break // the first set is now another row: the removed one has gone
            last = now
        }
        assertTrue("it slid, frame by frame, to the left ($moving frames)", moving >= 3)
        assertTrue("and it never came back toward rest before it went: $trace", last < rest - 150f)
        settle(1200)
        assertEquals("gone", 1, items())
        assertTrue("the compact Undo pill is up", has("Set removed"))
    }

    @Test fun theColourIsTheGroundBehindTheRowWithAnIconAndNoWord() {
        open(*two)
        val y = rowY(2)
        setField(2).performTouchInput { down(Offset(right - 5f, centerY)); for (i in 1..6) { moveBy(Offset(-20f, 0f)); advanceEventTime(16) } }
        compose.waitForIdle()
        assertFalse("no word on it", compose.onAllNodes(hasText("Remove"), useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.center.y.toInt() == y.toInt() })
        val red = snap().getPixel(385, y.toInt())
        assertTrue("red at the far edge behind the row (r=${android.graphics.Color.red(red)} g=${android.graphics.Color.green(red)})", android.graphics.Color.red(red) > android.graphics.Color.green(red) + 30)
        setField(2).performTouchInput { moveBy(Offset(100f, 0f)); up() }
        settle(700)
    }

    @Test fun theSetRowsKeepTheSystemsBackSwipeOffTheirEdges() {
        open(*two)
        val rects = ArrayList<android.graphics.Rect>()
        go {
            fun walk(v: View) { rects += v.systemGestureExclusionRects; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
            walk(window!!)
        }
        assertTrue("strips are kept at the rows' edges (${rects.size} found)", rects.size >= 2)
        assertTrue("narrow strips, 36 dp", rects.all { it.width() <= 40 })
    }

    // ---- 2. Lock photos ----

    private val phone get() = model.platform as AndroidPlatform
    private val asked = ArrayList<String>()
    private fun lockWith(result: Boolean?) {
        asked.clear()
        phone.testHasLock = result != null
        phone.testLock = if (result == null) null else { reason, cb -> asked += reason; cb(result) }
    }

    private fun switchOn() { go { model.setPhotoLock(true) }; compose.waitUntil(5_000) { model.photoLockOn }; compose.waitForIdle() }

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

    @Test fun mainActivityIsAFragmentActivityForTheBiometricPrompt() {
        launch()
        var activity: android.app.Activity? = null
        launched!!.onActivity { activity = it }
        assertTrue(activity is androidx.fragment.app.FragmentActivity)
    }

    @Test fun theSwitchIsInSettingsNextToBlurPhotosAndOffByDefault() {
        launch()
        go { model.settingsPage = PAGE_PRIVACY; model.open(Screen.Settings) }; settle(600)
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(hasText("Lock photos"))
        settle(300)
        assertTrue(has("Blur photos")); assertTrue(has("Lock photos"))
        assertFalse(model.photoLockOn)
    }

    @Test fun withNoLockOnThePhoneTheSwitchSaysSoAndStaysOff() {
        launch()
        lockWith(null)
        go { model.setPhotoLock(true) }; settle(300)
        Thread.sleep(300); compose.waitForIdle()
        assertFalse(model.photoLockOn)
        assertTrue("said so: ${model.message}", model.message!!.contains("screen lock"))
    }

    @Test fun turningItOnShutsThePhotosAndOpeningThePhotosPageAsksThePhonesLock() {
        launch()
        lockWith(true)
        switchOn()
        assertTrue(model.photoLockOn)
        assertTrue("locked at once", model.photosShut)
        go { model.open(Screen.Photos) }; settle(600)
        assertEquals("asked once as the page appeared", listOf("Show your photos"), asked)
        assertFalse("through: the page", has("Photos are locked"))
        assertFalse(model.photosShut)
        // Another photo page in the same stay does not ask again.
        go { model.open(Screen.PhotoCompare(null)) }; settle(600)
        go { model.open(Screen.Moments) }; settle(600)
        assertEquals("still one ask", 1, asked.size)
    }

    @Test fun aRefusedOrCancelledPromptLeavesThePageLockedWithAnUnlockButton() {
        launch()
        lockWith(true)
        switchOn()
        lockWith(false)
        go { model.open(Screen.Moments) }; settle(600)
        assertTrue(has("Photos are locked"))
        assertEquals(1, asked.size)
        lockWith(true)
        tapText("Unlock")
        assertFalse(has("Photos are locked"))
        assertEquals(listOf("Show your photos"), asked)
    }

    @Test fun everyPhotoPageIsBehindTheLock() {
        launch()
        lockWith(true)
        switchOn()
        lockWith(false)
        for (screen in listOf(Screen.Photos, Screen.PhotoCompare(null), Screen.Photo("x.jpg"), Screen.Moments)) {
            go { model.stack.clear(); model.open(screen) }; settle(500)
            assertTrue("$screen is locked", has("Photos are locked"))
        }
    }

    @Test fun theUnlockEndsWhenTheAppLeavesTheScreen() {
        launch()
        lockWith(true)
        switchOn()
        go { model.unlockPhotos() }; settle(200)
        assertFalse(model.photosShut)
        launched!!.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        launched!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertTrue("locked again once the app was in the background", model.photosShut)
    }

    @Test fun turningItOffNeedsTheLock() {
        launch()
        lockWith(true)
        switchOn()
        lockWith(false)
        go { model.setPhotoLock(false) }; settle(300)
        Thread.sleep(300); compose.waitForIdle()
        assertTrue("refused: still on", model.photoLockOn)
        assertEquals(listOf("Turn off Lock photos"), asked)
        lockWith(true)
        go { model.setPhotoLock(false) }
        compose.waitUntil(5_000) { !model.photoLockOn }
    }

    @Test fun whileShutTheThumbnailsAreBlurredWhateverTheBlurSettingSays() {
        launch()
        lockWith(true)
        val files = makeMoments(4)
        go { model.setOption("photos.blur", "off"); model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 4 }
        switchOn(); go { model.tab = Tab.Profile }; settle(300)
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
        waitForThumb(thumbKey(files[0].path, 360, true), "the fan, locked")
        assertNull("no sharp thumbnail was made", thumb(thumbKey(files[0].path, 360, false)))
        go { model.unlockPhotos() }
        waitForThumb(thumbKey(files[0].path, 360, false), "the fan, unlocked and the setting off")
    }

    // ---- 3. The dock slide ----

    private fun slot(tab: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(tab) and androidx.compose.ui.test.SemanticsMatcher("is a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }, useUnmergedTree = true)
    private fun centre(tab: String): Offset = slot(tab).fetchSemanticsNode().boundsInRoot.center
    private fun slotWidth(): Float = slot("Home").fetchSemanticsNode().boundsInRoot.width
    private fun selected(): Tab = listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile).first { slot(it.label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
    private fun slideDock(over: String, slots: Float, steps: Int = 12) {
        val from = centre(over)
        val w = slotWidth()
        compose.onRoot().performTouchInput { down(from); for (i in 1..steps) { moveTo(Offset(from.x + slots * w * i / steps, from.y)); advanceEventTime(16) } }
        compose.waitForIdle()
    }
    private fun lift() { compose.onRoot().performTouchInput { up() }; compose.waitForIdle() }

    /** The page's pixels (the top 60 % of the screen) as a mean brightness, to tell a page that is mid-blink from one at rest. */
    private fun pageBrightness(): Double {
        val b = snap()
        var sum = 0.0
        var n = 0
        for (y in 0 until (b.height * 0.6).toInt() step 4) for (x in 0 until b.width step 4) { sum += lum(b.getPixel(x, y)); n++ }
        return sum / n
    }

    @Test fun whileSlidingOnlyTheDockMovesAndThePageAndTheTabStayPut() {
        launch(manual = true)
        val tabChanges = ArrayList<Tab>()
        val before = pageBrightness()
        val from = centre("Home")
        val w = slotWidth()
        compose.onRoot().performTouchInput { down(from) }
        for (i in 1..24) {
            compose.onRoot().performTouchInput { moveTo(Offset(from.x + 3 * w * i / 24f, from.y)) }
            compose.mainClock.advanceTimeBy(16); compose.waitForIdle()
            if (tabChanges.lastOrNull() != model.tab) tabChanges += model.tab
            assertEquals("the page is not blinking or changing", before, pageBrightness(), 0.5)
        }
        assertEquals("the app's tab never changed during the slide", listOf(Tab.Home), tabChanges)
        assertEquals("but the dock followed the finger", Tab.Profile, selected())
        lift()
        assertEquals("the tab changed once, on release", Tab.Profile, model.tab)
    }

    @Test fun theSlideTicksAtEachCrossingWithoutChangingThePage() {
        launch(manual = true)
        HapticGate.reset()
        val shadow = org.robolectric.Shadows.shadowOf(window!!)
        slideDock("Home", 0.7f)
        assertEquals(android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, shadow.lastHapticFeedbackPerformed())
        assertEquals(Tab.Home, model.tab)
        lift()
    }

    @Test fun afterASlideThePageBlinksJustAsAfterATap() {
        launch(manual = true)
        slot("Library").performTouchInput { click(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(60); compose.waitForIdle()
        val tapped = pageBrightness()
        assertEquals(Tab.Exercises, model.tab)
        settle(1500)
        go { model.tab = Tab.Home }; settle(1500)
        slideDock("Home", 2f)
        lift()
        compose.mainClock.advanceTimeBy(60); compose.waitForIdle()
        val slid = pageBrightness()
        assertEquals("the same frame 60 ms in: a tap and a slide blink alike", tapped, slid, 1.0)
        settle(1500)
        assertEquals(Tab.Exercises, model.tab)
    }

    @Test fun aSlideBetweenHomeAndProgressMovesTheActivityCardAsATapDoes() {
        launch(manual = true)
        slot("Progress").performTouchInput { click(center) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(150); compose.waitForIdle()
        val tapOverlay = model.activityOverlay.value
        settle(1500)
        go { model.tab = Tab.Home }; settle(1500)
        slideDock("Home", 1f)
        lift()
        compose.mainClock.advanceTimeBy(150); compose.waitForIdle()
        assertEquals("the shared Activity card is up for the blink after a slide as after a tap", tapOverlay, model.activityOverlay.value)
        settle(1500)
        assertEquals(Tab.Progress, model.tab)
    }

    @Test fun aFlickIsOneTabChangedOnReleaseWithTheTapsTransition() {
        launch(manual = true)
        val w = slotWidth()
        val c = centre("Home")
        compose.onRoot().performTouchInput { swipe(c, Offset(c.x + 0.3f * w, c.y), durationMillis = 30) }
        compose.waitForIdle()
        assertEquals(Tab.Progress, model.tab)
        compose.mainClock.advanceTimeBy(60); compose.waitForIdle()
        settle(1500)
        assertEquals(Tab.Progress, model.tab)
    }

    // ---- 4. A soft blur ----

    private fun stripes(w: Int, h: Int): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val paint = android.graphics.Paint().apply { color = 0xFF000000.toInt() }
        var y = 0
        while (y < h) { canvas.drawRect(0f, y.toFloat(), w.toFloat(), (y + h / 24).toFloat(), paint); y += h / 12 }
        return bmp.asImageBitmap()
    }

    private fun maxStep(img: androidx.compose.ui.graphics.ImageBitmap): Int {
        val px = IntArray(img.width * img.height)
        img.readPixels(px, 0, 0, img.width, img.height)
        var worst = 0
        for (y in 0 until img.height) for (x in 0 until img.width - 1) worst = maxOf(worst, kotlin.math.abs(android.graphics.Color.red(px[y * img.width + x]) - android.graphics.Color.red(px[y * img.width + x + 1])))
        for (y in 0 until img.height - 1) for (x in 0 until img.width) worst = maxOf(worst, kotlin.math.abs(android.graphics.Color.red(px[y * img.width + x]) - android.graphics.Color.red(px[(y + 1) * img.width + x])))
        return worst
    }

    @Test fun theBlurIsSmallSmoothAndLeavesOnlyColourShapes() {
        val src = stripes(240, 320)
        val hard = shrunk(src, BLUR_SIDE)
        val soft = softBlurred(src)
        assertTrue("small: at most $BLUR_SIDE px (${soft.width} x ${soft.height})", maxOf(soft.width, soft.height) <= BLUR_SIDE)
        assertEquals("the same shape as a plain cut", hard.width, soft.width)
        val hardStep = maxStep(hard)
        val softStep = maxStep(soft)
        assertTrue("the plain cut is still stripes (step $hardStep)", hardStep > 80)
        assertTrue("the blur has no hard steps (step $softStep against $hardStep)", softStep < hardStep / 2 && softStep < 70)
    }

    @Test fun aBlurOfAPlainPictureKeepsItsColourAndAnEdgeDoesNotDarken() {
        val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF3B5BA5.toInt()) }
        val soft = softBlurred(bmp.asImageBitmap())
        val px = IntArray(soft.width * soft.height)
        soft.readPixels(px, 0, 0, soft.width, soft.height)
        for (p in listOf(px[0], px[soft.width - 1], px[px.size / 2], px.last())) {
            assertTrue(kotlin.math.abs(android.graphics.Color.red(p) - 0x3B) <= 2); assertTrue(kotlin.math.abs(android.graphics.Color.blue(p) - 0xA5) <= 2)
        }
    }

    // ---- 5. The Snapshots fan ----

    @Test fun theFanIsAsLargeAsTheWidthAllowsAndKeepsItsCornersInside() {
        val narrow = fanLayout(328.dp)
        assertTrue("larger than the 76 dp of Round 30 (${narrow.card})", narrow.card >= 100.dp)
        for (width in listOf(280.dp, 328.dp, 400.dp)) {
            val f = fanLayout(width)
            val span = f.step * 3f + f.card
            assertTrue("$width: the four cards ($span) fit with a margin", span <= width - 20.dp)
            assertTrue("the fan is as tall as its cards", f.height >= f.card * (100f / 76f))
        }
    }

    @Test fun theFanSitsOnThePageWithNoCardBehindIt() {
        launch()
        makeMoments(4)
        go { model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 4 }
        go { model.tab = Tab.Profile }; settle(400)
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
        settle(600)
        val card = compose.onAllNodes(hasTestTag("card:profile:moments"), useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        val b = snap()
        val y = card.center.y.toInt()
        // Just inside the fan's box, left of the first card, is the page's own ground, the same as outside it.
        val inside = b.getPixel(card.left.toInt() + 3, y)
        val outside = b.getPixel(3, y)
        assertTrue("no tile fill inside the fan's box (${Integer.toHexString(inside)} against ${Integer.toHexString(outside)})", kotlin.math.abs(lum(inside) - lum(outside)) < 6)
        assertTrue("the fan is the page's width less its gutters", card.width > 340f)
        // Tapping the fan opens Moments, and Back brings the fan home unchanged.
        val before = card
        compose.onAllNodes(hasTestTag("card:profile:moments"), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(900)
        assertEquals(Screen.Photos, model.stack.lastOrNull())
        go { model.back() }; settle(900)
        assertTrue(model.stack.isEmpty())
        val after = compose.onAllNodes(hasTestTag("card:profile:moments"), useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        assertEquals("nothing changed when the transition ended", before.left, after.left, 1f)
        assertEquals(before.width, after.width, 1f)
    }

    // ---- The tour ----

    private fun snap(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }
    private fun lum(c: Int) = 0.30 * android.graphics.Color.red(c) + 0.59 * android.graphics.Color.green(c) + 0.11 * android.graphics.Color.blue(c)

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun shot(name: String) { settle(300); save(snap(), name) }

    private fun look(theme: String) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, "Graphite") }
        repeat(200) { if (model.look.value.themeName == "Graphite") return@repeat; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun attempt(name: String, step: () -> Unit) {
        try { step() } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("$name.failed.txt").writeText(e.toString()) }
    }

    /** Five frames of one set's row, one above the other: rest, 20 %, 45 %, let go and sliding off, gone with the Undo pill. */
    private fun swipeStrip(prefix: String) {
        attempt("round31-$prefix-01-swipe-strip") {
            go {
                model.stack.clear()
                model.putDraft(Draft("Push day", System.currentTimeMillis() - 754_000, listOf(DraftBlock("Bench Press (Barbell)", listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(SetType.NORMAL, "62.5", "8"), DraftSet(SetType.NORMAL, "62.5", "8"))))))
                model.open(Screen.Active)
            }
            settle(800)
            compose.mainClock.autoAdvance = false
            val y = rowY(2).toInt()
            val frames = ArrayList<Bitmap>()
            fun crop() { val b = snap(); frames += Bitmap.createBitmap(b, 0, (y - 70).coerceAtLeast(0), b.width, 150) }
            crop()
            compose.onRoot().performTouchInput { down(Offset(396f, y.toFloat())); for (i in 1..5) { moveBy(Offset(-16f, 0f)); advanceEventTime(16) } }
            compose.waitForIdle(); crop()
            compose.onRoot().performTouchInput { for (i in 1..6) { moveBy(Offset(-16f, 0f)); advanceEventTime(16) } }
            compose.waitForIdle(); crop()
            compose.onRoot().performTouchInput { for (i in 1..2) { moveBy(Offset(-14f, 0f)); advanceEventTime(8) }; up() }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(110); compose.waitForIdle(); crop()
            compose.mainClock.advanceTimeBy(1200); compose.waitForIdle(); crop()
            val strip = Bitmap.createBitmap(frames[0].width, frames.sumOf { it.height + 6 }, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(strip)
            canvas.drawColor(0xFF808080.toInt())
            var top = 0f
            for (f in frames) { canvas.drawBitmap(f, 0f, top, null); top += f.height + 6 }
            save(strip, "round31-$prefix-01-swipe-strip")
        }
        compose.mainClock.autoAdvance = true
        go { model.cancelWorkout() }; settle(300)
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        launch(history = ::history)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        makeMoments(5)
        go { model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == 5 }
        for ((theme, prefix) in listOf("dark" to "graphite-dark", "light" to "graphite-light")) {
            look(theme)
            swipeStrip(prefix)
            go { model.stack.clear(); model.tab = Tab.Profile; model.setOption("photos.blur", "off") }; settle(500)
            go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }
            attempt("round31-$prefix-02-fan") { repeat(6) { Thread.sleep(150); compose.waitForIdle() }; shot("round31-$prefix-02-fan") }
            attempt("round31-$prefix-03-fan-blurred") { go { model.setOption("photos.blur", "on") }; repeat(8) { Thread.sleep(150); compose.waitForIdle() }; shot("round31-$prefix-03-fan-blurred") }
            go { model.setOption("photos.blur", "off"); model.tab = Tab.Home }; settle(400)
        }
    }
}
