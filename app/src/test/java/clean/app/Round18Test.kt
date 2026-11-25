package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Round 18: the body figure's hold card, the Library pull that really raises the keyboard, and the accent on History's PR marks. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round18Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: AppModel
    private var window: View? = null

    private val lifts = listOf("Bench Press (Barbell)", "Squat (Barbell)")

    private fun launch(): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
            repo.dao.put(Kv("ui.motion", "on"))
            val rows = ArrayList<WorkoutRow>()
            for (n in 0 until 6) {
                val date = LocalDate.now().minusDays(n * 3L + 3).atTime(18, 0).format(STRONG_DATE_FORMAT)
                for (lift in lifts) for (s in 1..3) rows += WorkoutRow((n + 1).toString(), date, "Push", "3600", lift, s.toString(), (60.0 + n * 2.5).toString(), "8", "", "", "", "", "")
            }
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun textCount(text: String) = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        if (dialogs) {
            val canvas = android.graphics.Canvas(bitmap)
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== window && root.width > 0) root.draw(canvas)
        }
        val out = java.io.File("build/screens/default").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun theme(name: String) {
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, name) }
        repeat(200) { if (model.look.value.themeName == name) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.themeName == name)
    }

    private fun look(dark: Boolean) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, if (dark) "dark" else "light") }
        repeat(200) { if (model.look.value.dark == dark) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.dark == dark)
    }

    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + node.children.flatMap { texts(it) }

    private fun peekTexts(): List<String>? =
        compose.onAllNodes(hasTestTag("muscle peek"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { texts(it) }

    private fun figure(): Rect =
        compose.onAllNodes(SemanticsMatcher("figure") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Body figure") } == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 100f } ?: throw AssertionError("no figure on screen")

    /** Holds at the first point of a grid over the figure that lands on a muscle; returns that point. */
    private fun holdOnAMuscle(f: Rect): Offset {
        for (fy in listOf(0.22f, 0.3f, 0.38f, 0.46f)) for (fx in listOf(0.2f, 0.25f, 0.3f)) {
            val at = Offset(f.left + f.width * fx, f.top + f.height * fy)
            root().performTouchInput { down(at) }
            compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
            if (peekTexts() != null) return at
            root().performTouchInput { up() }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        }
        throw AssertionError("no muscle under the grid; figure $f; screen ${window?.width}x${window?.height}")
    }

    @Test fun holdingAMuscleShowsItsCardSlidingUpdatesAndReleaseDismisses() {
        val s = launch()
        go { model.open(Screen.Body) }
        settle()
        val f = figure()
        holdOnAMuscle(f)
        val first = peekTexts()!!
        assertTrue("name missing: $first", first.first().isNotEmpty())
        assertTrue("last trained line missing: $first", first.any { it.startsWith("Last trained") || it == "Never trained" })
        shot("round18-body-hold", dialogs = true)
        var other: List<String>? = null
        search@ for (fy in listOf(0.55f, 0.65f, 0.75f, 0.35f, 0.5f, 0.2f, 0.85f)) for (fx in listOf(0.2f, 0.3f, 0.12f, 0.38f, 0.25f)) {
            root().performTouchInput { moveTo(Offset(f.left + f.width * fx, f.top + f.height * fy)) }
            compose.waitForIdle()
            val now = peekTexts()
            if (now != null && now.first() != first.first()) { other = now; break@search }
        }
        assertNotNull("the card did not follow the finger", other)
        root().performTouchInput { up() }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertNull("the card stayed after release", peekTexts())
        assertEquals("a hold opened the tap tip", 0, textCount("Open ›"))
        s.close()
    }

    private fun place(at: Offset, centered: Boolean): androidx.compose.ui.unit.IntOffset =
        InsideWindow(at, centered, 32, 56f, 84f, 28f, 36f).calculatePosition(
            androidx.compose.ui.unit.IntRect(0, 100, 800, 700), androidx.compose.ui.unit.IntSize(800, 1700), androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.unit.IntSize(496, 167),
        )

    @Test fun cardsAreKeptInsideTheWindowMargins() {
        // A hold near either edge: centred on the finger but pushed in; above it, and below it when the top has no room.
        assertEquals(32, place(Offset(5f, 400f), true).x)
        assertEquals(800 - 32 - 496, place(Offset(795f, 400f), true).x)
        assertTrue(place(Offset(400f, 400f), true).y < 100 + 400)
        assertTrue(place(Offset(400f, 20f), true).y >= 100 + 20)
        // A tap tip's corner (finger less 56, 84), also pushed inside, and never above the margin.
        val tip = place(Offset(5f, 5f), false)
        assertEquals(32, tip.x); assertTrue(tip.y >= 32)
        val far = place(Offset(790f, 590f), false)
        assertTrue(far.x + 496 <= 800 - 32)
    }

    @Test fun aTapOnAMuscleStillShowsTheTip() {
        val s = launch()
        go { model.open(Screen.Body) }
        settle()
        val f = figure()
        var tipped = false
        loop@ for (fy in listOf(0.22f, 0.3f, 0.38f, 0.46f)) for (fx in listOf(0.2f, 0.25f, 0.3f)) {
            root().performTouchInput { click(Offset(f.left + f.width * fx, f.top + f.height * fy)) }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
            if (textCount("Open ›") > 0) { tipped = true; break@loop }
        }
        assertTrue("no tap tip", tipped)
        assertEquals(0, count("muscle peek"))
        s.close()
    }

    private fun benchRow(): Rect =
        compose.onAllNodes(hasText("Bench Press (Barbell)"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width > 0f && it.top in 100f..1500f }

    @Test fun pullingTheLibraryDownPastTheThresholdFocusesTheSearchAndAsksForTheKeyboard() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        val list = benchRow()
        root().performTouchInput { down(Offset(list.center.x, list.center.y)); moveBy(Offset(0f, 150f)); moveBy(Offset(0f, 150f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        root().performTouchInput { up() }
        settle(800)
        assertTrue("the search was not focused", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true)
        val imm = org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(android.view.inputmethod.InputMethodManager::class.java))
        assertTrue("the keyboard was not asked for", imm.isSoftInputVisible)
        s.close()
    }

    @Test fun aShortPullDoesNotFocusTheSearch() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        val list = benchRow()
        root().performTouchInput { down(Offset(list.center.x, list.center.y)); moveBy(Offset(0f, 50f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        root().performTouchInput { up() }
        settle(800)
        assertEquals("the search stayed closed", 0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        s.close()
    }

    @Test fun historyShowsItsPrMarksInTheAccentInSeveralThemes() {
        val s = launch()
        go { model.tab = Tab.Home; model.openHistory() }
        settle()
        for ((name, dark) in listOf("Graphite" to true, "Ember" to true, "Ember" to false, "Cobalt" to true, "Graphite" to false)) {
            theme(name); look(dark)
            settle(500)
            shot("round18-history-${name.lowercase()}-${if (dark) "dark" else "light"}")
        }
        go { model.stack.clear(); model.tab = Tab.Progress }
        for (name in listOf("Ember", "Graphite")) { theme(name); look(true); settle(500); shot("round18-progress-${name.lowercase()}") }
        s.close()
    }
}
