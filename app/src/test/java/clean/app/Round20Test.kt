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
import androidx.compose.ui.test.performClick
import clean.app.ui.theme.meaning
import clean.app.ui.theme.orange
import androidx.compose.ui.test.performTextInput
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
import clean.core.ACCENT_CHOICES
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

/** Round 20: PR colour, per-theme chart colours, meaning colours apart from the accent, the search icon with pull-down, count-ups. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round20Test {
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

    private val themes = ACCENT_CHOICES
    private fun fieldCount() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
    private fun focused() = compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    // 10
    @Test fun prMarksAreTheAccentInThreeThemes() {
        val s = launch()
        for ((name, dark) in listOf("Graphite" to false, "Ember" to true, "Green" to true)) {
            theme(name); look(dark)
            go { model.stack.clear(); model.tab = Tab.Home }
            settle(500)
            shot("round20-pr-home-${name.lowercase()}-${if (dark) "dark" else "light"}")
            go { model.openHistory() }
            settle(500)
            shot("round20-pr-history-${name.lowercase()}-${if (dark) "dark" else "light"}")
        }
        s.close()
    }

    // 11
    @Test fun theMultiLineChartHasItsOwnColoursInEveryTheme() {
        val s = launch()
        go { model.open(Screen.ExerciseDetail("Bench Press (Barbell)", tab = 2)) }
        settle()
        assertTrue("no chart", textCount("Est. 1RM") > 0)
        for (name in themes) for (dark in listOf(true, false)) {
            theme(name); look(dark)
            settle(1_200)
            shot("round20-chart-${name.lowercase()}-${if (dark) "dark" else "light"}")
        }
        s.close()
    }

    // 13
    @Test fun greenAndEmberMeaningColoursSitNextToTheirAccent() {
        val s = launch()
        go { model.open(Screen.Body) }
        settle()
        for (name in listOf("Green", "Ember")) for (dark in listOf(true, false)) {
            theme(name); look(dark)
            settle(800)
            shot("round20-meaning-${name.lowercase()}-${if (dark) "dark" else "light"}")
        }
        s.close()
    }

    // 14
    @Test fun theLibrarySearchIsAnIconThatOpensIntoAFieldAndBackCollapsesIt() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        assertEquals("the field was out at rest", 0, fieldCount())
        shot("round20-library-search-collapsed")
        compose.onNodeWithContentDescription("Search exercises").performClick()
        settle(800)
        assertEquals(1, fieldCount())
        assertTrue("the field was not focused", focused())
        compose.onNode(hasSetTextAction()).performTextInput("bench")
        settle(500)
        shot("round20-library-search-expanded")
        s.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(800)
        assertEquals("Back did not collapse the search", 0, fieldCount())
        assertEquals("Back left the tab", Tab.Exercises, model.tab)
        // The close control empties and collapses it too.
        compose.onNodeWithContentDescription("Search exercises").performClick(); settle(800)
        assertEquals(1, fieldCount())
        compose.onNodeWithContentDescription("Close search").performClick(); settle(800)
        assertEquals(0, fieldCount())
        s.close()
    }

    @Test fun pullingHistoryDownOpensItsSearchAndTheSameFieldFindsAWorkout() {
        val s = launch()
        go { model.tab = Tab.Home; model.openHistory() }
        settle()
        assertEquals(0, fieldCount())
        root().performTouchInput { down(Offset(width / 2f, height * 0.4f)); moveBy(Offset(0f, 150f)); moveBy(Offset(0f, 150f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        assertTrue("no release hint", textCount("Release to search") == 1)
        root().performTouchInput { up() }
        settle(800)
        assertEquals(1, fieldCount())
        assertTrue("the search was not focused", focused())
        compose.onNode(hasSetTextAction()).performTextInput("push")
        settle(800)
        shot("round20-history-search")
        compose.onNodeWithContentDescription("Close search").performClick(); settle(800)
        assertEquals(0, fieldCount())
        s.close()
    }

    // 18
    @Test fun theMonthTotalsCountUpAndLandOnTheirFigures() {
        val s = launch()
        val month = model.workouts.value.last().day.take(7)
        val expected = model.workouts.value.count { it.day.startsWith(month) }
        go { model.open(Screen.Month(month)) }
        settle(2_000)
        assertTrue("the workouts figure is missing", compose.onAllNodesWithText(expected.toString(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("round20-month-totals")
        s.close()
    }

    /** Plain swatches, drawn without the app: each theme's accent beside its meaning colours, and its chart colours, Dark and Light. */
    @Test fun swatchesOfEveryThemeAreDrawn() {
        val out = java.io.File("build/screens/default").apply { mkdirs() }
        fun argb(c: androidx.compose.ui.graphics.Color) = android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
        for (name in themes) {
            val bmp = Bitmap.createBitmap(1000, 880, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 26f }
            var y = 0
            for ((dark, safe) in listOf(true to false, false to false, true to true, false to true)) {
                val look = clean.app.ui.theme.resolveLook(if (dark) "dark" else "light", accent = name, colourBlindSafe = safe)
                val m = look.meaning()
                canvas.drawRect(0f, y.toFloat(), 1000f, y + 220f, android.graphics.Paint().apply { color = argb(look.page) })
                paint.color = argb(look.text)
                canvas.drawText("$name ${if (dark) "dark" else "light"}${if (safe) " colour-blind-safe" else ""}: accent, done, ready, recovering, warmup, fatigued, drop, underweight", 20f, y + 36f, paint)
                var x = 20f
                for (c in listOf(look.orange, m.done, m.ready, m.recovering, m.warmup, m.fatigued, m.drop, m.underweight)) {
                    canvas.drawRoundRect(x, y + 56f, x + 100f, y + 126f, 12f, 12f, android.graphics.Paint().apply { color = argb(c) }); x += 118f
                }
                x = 20f
                for (c in look.series) { canvas.drawCircle(x + 35f, y + 172f, 30f, android.graphics.Paint().apply { color = argb(c) }); x += 118f }
                y += 220
            }
            java.io.File(out, "round20-swatches-${name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
