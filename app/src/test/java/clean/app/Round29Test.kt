package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.open
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 29 (the slide changed in Round 31: the tab changes once, on release): the dock names the tab it has just switched to, and a finger can slide along it. The name shows under the icon after a tap, a swipe or code
 * (never at the app's start, nothing with animations off) and is gone after about 1.2 s; a slide moves the selection under the finger live and ends on it;
 * a quick flick moves one tab; a tap still switches; a slide never reaches the plus button.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round29Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    private fun launch(motion: Boolean = true) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off")) // always written: the database outlives a test
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        // The clock is stepped by hand: with it running free, waiting for idle would run the name's whole life before it could be looked at.
        compose.mainClock.autoAdvance = false
        settle(300)
    }

    private fun named(tab: String) = compose.onAllNodes(hasTestTag("dock name $tab"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun anyName() = listOf("Home", "Progress", "Library", "Profile").filter { named(it) }

    /** The dock's slot for [tab]: the node the phone's screen reader lands on. */
    private fun slot(tab: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(tab) and androidx.compose.ui.test.SemanticsMatcher("is a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }, useUnmergedTree = true)
    private fun centre(tab: String): Offset = slot(tab).fetchSemanticsNode().boundsInRoot.center
    private fun slotWidth(): Float = slot("Home").fetchSemanticsNode().boundsInRoot.width
    /** The tab the dock shows as selected (Round 31: during a slide this is the tab under the slide; the app's own tab stays until the finger lifts). */
    private fun selected(): Tab = listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile).first { slot(it.label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
    private fun tap(tab: String) { slot(tab).performTouchInput { click(center) }; settle(300) }

    private fun slideTo(x: Float, y: Float, from: Offset, steps: Int = 12) {
        compose.onRoot().performTouchInput {
            down(from)
            for (i in 1..steps) { moveTo(Offset(from.x + (x - from.x) * i / steps, from.y + (y - from.y) * i / steps)); advanceEventTime(16) }
        }
        compose.waitForIdle()
    }

    private fun lift() { compose.onRoot().performTouchInput { up() }; compose.waitForIdle() }

    // ---- The name ----

    @Test fun nameShowsAfterATapAndIsGoneAfterAboutAThirdOfASecondMoreThanASecond() {
        launch()
        assertEquals("never at the app's start", emptyList<String>(), anyName())
        settle(1500)
        assertEquals("nor later", emptyList<String>(), anyName())
        tap("Progress")
        assertEquals(Tab.Progress, model.tab)
        assertEquals(listOf("Progress"), anyName())
        settle(500)
        assertEquals("still up at 0.8 s", listOf("Progress"), anyName())
        settle(200)
        assertEquals("fading at 1.0 s", listOf("Progress"), anyName())
        settle(300)
        assertEquals("gone after 1.3 s", emptyList<String>(), anyName())
    }

    @Test fun aTabChangedByCodeShowsItsNameToo() {
        launch()
        go { model.tab = Tab.Profile }
        settle(400)
        assertEquals(listOf("Profile"), anyName())
        settle(1200)
        assertEquals(emptyList<String>(), anyName())
    }

    @Test fun theTabsKeepTheirContentDescriptionsWhileTheNameShows() {
        launch()
        tap("Library")
        assertEquals(listOf("Library"), anyName())
        for (t in listOf("Home", "Progress", "Library", "Profile")) assertEquals("one node speaks $t", 1, compose.onAllNodes(hasContentDescription(t), useUnmergedTree = true).fetchSemanticsNodes().count { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
        assertTrue(slot("Library").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true)
    }

    @Test fun withAnimationsOffNoNameShowsAndTheTabStillSwitches() {
        launch(motion = false)
        tap("Progress")
        assertEquals(Tab.Progress, model.tab)
        assertEquals(emptyList<String>(), anyName())
        go { model.tab = Tab.Profile }
        settle(300)
        assertEquals(emptyList<String>(), anyName())
    }

    // ---- Sliding ----

    @Test fun aSlideAcrossTheDockChangesTheTabLiveAndEndsUnderTheFinger() {
        launch()
        val w = slotWidth()
        val y = centre("Home").y
        val seen = ArrayList<Tab>()
        compose.onRoot().performTouchInput { down(centre("Home")) }
        for (i in 1..30) {
            compose.onRoot().performTouchInput { moveTo(Offset(centre("Home").x + 3 * w * i / 30f, y)) }
            settle(16)
            if (seen.lastOrNull() != selected()) seen += selected()
        }
        compose.waitForIdle()
        assertEquals("the dock's selection changes as the finger passes each", listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile), seen)
        assertEquals("Round 31: the app's tab (and its page) stay put while the finger is down", Tab.Home, model.tab)
        assertTrue("while the finger is down", "Profile" in anyName())
        settle(300)
        assertEquals("the name is held while the finger is down (the earlier ones have faded)", listOf("Profile"), anyName())
        settle(1500)
        assertEquals("and stays", listOf("Profile"), anyName())
        lift()
        assertEquals(Tab.Profile, model.tab)
        settle(400)
        assertEquals(Tab.Profile, model.tab)
        assertFalse("a slide never opens the workout sheet", model.workoutSheet)
        settle(1300)
        assertEquals(emptyList<String>(), anyName())
    }

    @Test fun aSlideThatStopsOnAMiddleTabStaysOnIt() {
        launch()
        val w = slotWidth()
        slideTo(centre("Home").x + 2 * w, centre("Home").y, centre("Home"))
        lift()
        assertEquals(Tab.Exercises, model.tab)
        settle(900)
        assertEquals(Tab.Exercises, model.tab)
        // and back the other way, past the start
        slideTo(centre("Library").x - 1.2f * w, centre("Library").y, centre("Library"))
        lift()
        assertEquals(Tab.Progress, model.tab)
    }

    @Test fun aSlideTicksAtEachBoundary() {
        launch()
        HapticGate.reset()
        val w = slotWidth()
        val shadow = org.robolectric.Shadows.shadowOf(window!!)
        slideTo(centre("Home").x + 0.6f * w, centre("Home").y, centre("Home"))
        settle(32)
        assertEquals(Tab.Progress, selected())
        assertEquals("the page has not changed yet", Tab.Home, model.tab)
        assertEquals("a tick at the boundary", android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, shadow.lastHapticFeedbackPerformed())
        lift()
    }

    @Test fun aSlideThatEndsOverThePlusButtonDoesNotPressIt() {
        launch()
        val w = slotWidth()
        slideTo(centre("Home").x + 7 * w, centre("Home").y, centre("Home"))
        lift()
        settle(500)
        assertEquals(Tab.Profile, model.tab)
        assertFalse(model.workoutSheet)
        assertTrue(model.draft == null)
    }

    @Test fun aSlideOverTheLabelsOfAQuietDockLeavesScrollAndShrinkAlone() {
        launch()
        // A vertical start on the dock does nothing new: no tab change.
        val c = centre("Home")
        slideTo(c.x + 5f, c.y - 120f, c, steps = 6)
        lift()
        settle(300)
        assertEquals(Tab.Home, model.tab)
        assertEquals(emptyList<String>(), anyName())
    }

    @Test fun aQuickFlickMovesOneTab() {
        launch()
        val w = slotWidth()
        // Round 30: a flick is one tab from the start tab (Home), whichever slot the finger is over.
        val c = centre("Progress")
        compose.onRoot().performTouchInput { swipe(c, Offset(c.x + 0.4f * w, c.y), durationMillis = 30) }
        settle(200)
        assertEquals(Tab.Progress, model.tab)
        val d = centre("Library")
        compose.onRoot().performTouchInput { swipe(d, Offset(d.x - 0.4f * w, d.y), durationMillis = 30) }
        settle(200)
        assertEquals(Tab.Home, model.tab)
        assertFalse(model.workoutSheet)
    }

    @Test fun aFlickAtTheEndOfTheDockStaysPut() {
        launch()
        val w = slotWidth()
        val c = centre("Home")
        compose.onRoot().performTouchInput { swipe(c, Offset(c.x - 0.4f * w, c.y), durationMillis = 30) }
        settle(300)
        assertEquals(Tab.Home, model.tab)
    }

    @Test fun aSlideWithAnimationsOffStillMovesTheTab() {
        launch(motion = false)
        val w = slotWidth()
        slideTo(centre("Home").x + 3 * w, centre("Home").y, centre("Home"))
        lift()
        assertEquals(Tab.Profile, model.tab)
        assertEquals(emptyList<String>(), anyName())
    }

    // ---- The tour ----

    private fun shot(name: String) {
        compose.waitForIdle()
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
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
        fun s(name: String, step: () -> Unit) {
            try { step(); shot("round29-$prefix-$name") } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round29-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        go { model.tab = Tab.Home }; settle(1600)
        s("01-name-showing") { tap("Progress"); settle(400) }
        s("02-name-mid-fade") { settle(350) }
        settle(1000)
        s("03-mid-slide") {
            val w = slotWidth()
            val c = centre("Progress")
            slideTo(c.x + 1.35f * w, c.y, c)
            settle(120)
        }
        lift(); settle(1600)
        go { model.tab = Tab.Home }; settle(1600)
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        launch()
        look("dark"); tour("graphite-dark")
        look("light"); tour("graphite-light")
    }
}
