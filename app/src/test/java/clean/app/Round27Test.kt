package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.open
import clean.core.SetType
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
 * Round 27: the number pad of the workout screen closes on a tap anywhere but its own ground (the number boxes and the set's tick), a
 * tick works with the pad up, and the Health Connect settings.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round27Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { Health.availableForTest = null; runCatching { launched?.close() }; launched = null }

    private val names = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)")
    private val eight = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)", "Deadlift (Barbell)", "Overhead Press (Barbell)",
        "Bicep Curl (Dumbbell)", "Leg Press", "Triceps Pushdown (Cable)")

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    /** A workout in progress with [exercises], two empty sets each (or the sets given), on the workout screen. */
    private fun open(exercises: List<String> = names, sets: (String) -> List<DraftSet> = { listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL)) }) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            // The database outlives a test in this class: the Health keys of an earlier one must not leak.
            for (k in listOf("health", "health.last", "health.missing")) repo.dao.remove(k)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        go {
            model.putDraft(Draft("Workout", System.currentTimeMillis(), exercises.map { DraftBlock(it, sets(it)) }))
            model.open(Screen.Active)
        }
        settle(300)
    }

    private fun padUp() = compose.onAllNodes(hasContentDescription("Close keyboard"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()


    /** The first exercise's box number [index] (0 is the first set's weight, 1 its reps, 2 the second set's weight ...). */
    private fun box(card: Int, set: Int, reps: Boolean) = compose.onAllNodes(hasContentDescription("Set ${set + 1} ${if (reps) "reps" else "weight"}", substring = true), useUnmergedTree = true)[card]

    private fun tapBox(card: Int, set: Int, reps: Boolean) { box(card, set, reps).performClick(); settle(300) }

    /** The pad's key with this label: a button showing it (the set labels and the list's numbers are not buttons). */
    private fun key(label: String) {
        val key = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        compose.onAllNodes(key).onFirst().performTouchInput { click(center) }
        settle(200)
    }

    private fun tapText(text: String) {
        compose.onAllNodes(hasText(text), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(300)
    }

    private fun item(card: Int, set: Int) = model.draft!!.blocks[card].items[set] as DraftSet

    // ---- Part A: the number pad ----

    @Test fun aTapOnACardHeaderClosesThePadAndStillDoesWhatTheHeaderDoes() {
        open()
        tapBox(0, 0, false)
        assertTrue("the pad is up", padUp())
        val screens = model.stack.toList()
        val draft = model.draft
        tapText(names[1])
        assertFalse("the pad closed", padUp())
        // The name is a button of its own (it opens the exercise page): the tap that closes the pad is not taken from it.
        assertEquals(screens.size + 1, model.stack.size)
        assertEquals(draft, model.draft)
    }

    @Test fun aTapOnTheTopBarClosesThePad() {
        open()
        tapBox(0, 0, false)
        assertTrue(padUp())
        // The workout's clock in the top bar is not a number box and not a button of its own.
        compose.onAllNodes(hasContentDescription("Minimise workout"), useUnmergedTree = true).onFirst().performTouchInput { click(Offset(center.x + 300f, center.y)) }
        settle(300)
        assertFalse("the pad closed", padUp())
    }

    @Test fun aTapOnAnotherNumberBoxMovesThePadToItWithoutClosingIt() {
        open()
        tapBox(0, 0, false)
        key("5")
        assertEquals("5", item(0, 0).weight)
        // Another set's weight: the pad is still up and the next digit goes there, replacing nothing of the first.
        tapBox(0, 1, false)
        assertTrue("the pad is still up", padUp())
        key("7")
        assertEquals("5", item(0, 0).weight)
        assertEquals("7", item(0, 1).weight)
        // A reps box of another exercise.
        tapBox(1, 0, true)
        assertTrue(padUp())
        key("9")
        assertEquals("9", item(1, 0).reps)
    }

    @Test fun tickingASetWithThePadUpTicksItAndKeepsThePadOnTheSameBox() {
        open()
        tapBox(0, 0, false); key("5")
        tapBox(0, 0, true); key("8")
        assertTrue(padUp())
        val before = item(0, 0)
        assertFalse(before.done)
        // The set's own tick, under a real touch.
        compose.onAllNodes(hasContentDescription("Set 1"), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(400)
        assertTrue("the set is ticked", item(0, 0).done)
        assertTrue("the pad is still up", padUp())
        // The pad stayed on the reps box of that set: the next digit is added to it.
        key("0")
        assertEquals("80", item(0, 0).reps)
        assertEquals("5", item(0, 0).weight)
        // And unticking works the same way.
        compose.onAllNodes(hasContentDescription("Set 1"), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(400)
        assertFalse("the set is unticked", item(0, 0).done)
        assertTrue("the pad is still up", padUp())
    }

    @Test fun tickingASetStartsTheRestAsEver() {
        open()
        tapBox(0, 0, false); key("5")
        tapBox(0, 0, true); key("8")
        compose.onAllNodes(hasContentDescription("Set 1"), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(400)
        assertTrue("the set is ticked", item(0, 0).done)
        assertTrue("the rest started", item(0, 0).rest > 0 || model.restEnd != null)
    }

    @Test fun aTapOnAButtonClosesThePadAndRunsTheButton() {
        open()
        tapBox(0, 0, false)
        assertTrue(padUp())
        val sets = model.draft!!.blocks[0].items.size
        // Add set of the first card: it is a button, and it is on the screen with the pad up.
        compose.onAllNodes(hasText("Add set"), useUnmergedTree = true).onFirst().performTouchInput { click(center) }
        settle(400)
        assertFalse("the pad closed", padUp())
        assertEquals("the button ran", sets + 1, model.draft!!.blocks[0].items.size)
    }

    @Test fun aScrollDoesNotCloseThePadButATapAfterItDoes() {
        open(eight)
        tapBox(0, 0, false)
        assertTrue(padUp())
        val list = compose.onAllNodes(hasScrollAction()).onFirst()
        list.performTouchInput { down(center); repeat(10) { moveBy(Offset(0f, -40f)) }; up() }
        settle(400)
        assertTrue("the pad is still up after a scroll", padUp())
        val shown = eight.first { n -> compose.onAllNodes(hasText(n), useUnmergedTree = true).fetchSemanticsNodes().any { s -> s.boundsInRoot.top in 100f..500f } }
        tapText(shown)
        assertFalse("a tap closes it", padUp())
    }

    @Test fun aHoldDoesNotCloseThePad() {
        open()
        tapBox(0, 0, false)
        compose.onAllNodes(hasText(names[1]), useUnmergedTree = true).onFirst().performTouchInput { down(center); advanceEventTime(1000); up() }
        settle(300)
        assertTrue("a hold is not a tap", padUp())
    }

    @Test fun backClosesThePadFirstAndThenLeavesTheWorkoutScreenOnTheNextBack() {
        open()
        tapBox(0, 0, false)
        assertTrue(padUp())
        launched!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(300)
        assertFalse("Back closed the pad", padUp())
        assertEquals("still on the workout", Screen.Active, model.stack.last())
    }

    @Test fun thePadsOwnKeysStillWorkAndDoNotCloseIt() {
        open()
        tapBox(0, 0, false)
        key("1"); key("2")
        assertTrue(padUp())
        assertEquals("12", item(0, 0).weight)
        key("⌫")
        assertEquals("1", item(0, 0).weight)
        assertTrue(padUp())
    }

    // ---- Part B: the Health Connect settings and the sync's words (the calls to Health Connect itself are a phone check) ----

    private fun healthSettings() {
        Health.availableForTest = true
        go { model.setOption("health", "on") }
        compose.waitUntil(10_000) { model.options.value["health"] == "on" }
        go { model.stack.clear(); model.settingsPage = PAGE_TRANSFER; model.open(Screen.Settings) }; settle(600)
        runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("s-health") }
        settle(400)
    }

    @Test fun withTheSwitchOnAndNoHealthConnectTheLastSyncSaysSo() {
        open()
        go { model.setOption("health", "on") }
        compose.waitUntil(10_000) { model.options.value["health"] == "on" }
        runBlocking { model.healthCatchUp() }
        settle(1000)
        assertEquals("Health Connect failed: not available on this phone", model.healthLast.value)
    }

    @Test fun withHealthConnectThereButNoPermissionTheLastSyncSaysThePermissionIsGone() {
        open()
        Health.availableForTest = true
        go { model.setOption("health", "on") }
        compose.waitUntil(10_000) { model.options.value["health"] == "on" }
        runBlocking { model.healthCatchUp() }
        settle(1000)
        assertEquals("Health Connect refused: permission removed", model.healthLast.value)
    }

    @Test fun theSettingsSayWhatIsSentAndShowTheLastSync() {
        open()
        go { model.setOption("health.last", "Sent 3, removed 1") }
        healthSettings()
        assertTrue(compose.onAllNodes(hasText("Send to Health Connect"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Workouts (with a summary of the sets), body weight and body fat"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Last sync: Sent 3, removed 1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun switchingItOffAsksOnceWhetherToKeepWhatWasSent() {
        open()
        healthSettings()
        compose.onAllNodes(hasText("Send to Health Connect"), useUnmergedTree = true).onFirst().performClick()
        settle(500)
        assertEquals("off", model.options.value["health"])
        assertTrue(compose.onAllNodes(hasText("Keep them"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Remove them"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(hasText("Keep them"), useUnmergedTree = true).onFirst().performClick()
        settle(500)
        assertTrue(compose.onAllNodes(hasText("Keep them"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // ---- The tour ----

    private fun shot(name: String) {
        settle(300)
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
            try { step(); shot("round27-$prefix-$name") } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round27-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        s("01-pad-up-set-ticked") {
            go { model.stack.clear(); model.open(Screen.Active) }; settle(500)
            tapBox(0, 1, false)
        }
        s("02-health-settings") {
            clean.app.android.Health.availableForTest = true
            go { model.setOption("health", "on"); model.setOption("health.last", "Sent 3, removed 1"); model.setOption("health.missing", "body fat") }
            settle(500)
            go { model.stack.clear(); model.settingsPage = PAGE_TRANSFER; model.open(Screen.Settings) }; settle(600)
            val lists = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
            if (lists > 0) runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("s-health") }
            settle(400)
        }
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        open(listOf("Bench Press (Barbell)", "Squat (Barbell)")) { listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL)) }
        look("dark")
        tour("graphite-dark")
        go { model.stack.clear(); model.open(Screen.Active) }; settle(500)
        look("light")
        tour("graphite-light")
    }
}
