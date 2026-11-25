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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
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

/** Round 19: hold peeks on the multi-line chart, the volume bars, the week tile, the Trophies grid, Profile lifts, Personal bests, weigh-ins and the template menu. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round19Test {
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


    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + node.children.flatMap { texts(it) }

    private fun peek(tag: String): List<String>? =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { texts(it) }

    private fun holdAt(at: Offset, tag: String): List<String> {
        root().performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        return peek(tag) ?: throw AssertionError("no card '$tag' after a hold at $at")
    }

    private fun slideTo(at: Offset) { root().performTouchInput { moveTo(at) }; compose.waitForIdle() }
    private fun release() { root().performTouchInput { up() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle() }
    private fun tap(at: Offset) { root().performTouchInput { click(at) }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }

    private fun boundsOfDescription(start: String): Rect =
        compose.onAllNodes(SemanticsMatcher("desc $start") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith(start) } == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 50f && it.top >= 0f } ?: throw AssertionError("no node described '$start'; descriptions: " + compose.onAllNodes(SemanticsMatcher("any") { it.config.contains(SemanticsProperties.ContentDescription) }, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }.take(25))

    private fun boundsOfText(text: String): Rect =
        compose.onAllNodes(hasText(text, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 0f && it.top in 100f..1350f } ?: throw AssertionError("no text '$text' on screen")

    /** Looks for something with [find]; when it is not on screen, scrolls each vertical list of the page to item 1, 2, 3 ... until it is. */
    private fun <T> scrollFind(find: () -> T): T {
        runCatching { return find() }
        val lists = compose.onAllNodes(SemanticsMatcher("lazy list") { it.config.contains(SemanticsActions.ScrollToIndex) }, useUnmergedTree = true).fetchSemanticsNodes().sortedByDescending { it.boundsInRoot.height }
        for (index in 1..9) {
            for (list in lists) {
                val moved = runCatching { compose.runOnIdle { list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index) } }
                if (moved.isFailure) continue
                settle(500)
                runCatching { return find() }
            }
        }
        return find()
    }

    private fun weighIns() = runBlocking {
        val format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        for ((back, kg) in listOf(20L to 76.0, 10L to 74.5, 3L to 74.0)) {
            assertTrue("weigh-in", model.addMeasurement("weight.csv", kg, java.time.LocalDateTime.now().minusDays(back).withHour(8).withMinute(0).withSecond(0).format(format)))
        }
    }

    private fun templates() {
        val bench = clean.core.TemplateBlock("Bench Press (Barbell)", List(3) { clean.core.SetType.NORMAL })
        val squat = clean.core.TemplateBlock("Squat (Barbell)", List(3) { clean.core.SetType.NORMAL })
        go { model.save(Template(0, "Push", listOf(bench, squat))); model.save(Template(0, "Never done", listOf(bench))) }
        var waited = 0
        while (model.templates.value.size < 2 && waited++ < 400) { Thread.sleep(25); compose.waitForIdle() }
        assertEquals(2, model.templates.value.size)
        go { model.workoutSheet = true }
        settle(2_500)
    }

    // 1. MultiLineChart
    @Test fun holdingTheAllChartScrubsAndShowsOneCardWithEveryLine() {
        val s = launch()
        go { model.open(Screen.ExerciseDetail("Bench Press (Barbell)", tab = 2)) }
        settle(2_500)
        val chart = boundsOfDescription("Bench Press (Barbell):")
        val first = holdAt(Offset(chart.left + chart.width * 0.25f, chart.center.y), "chart peek")
        for (line in listOf("Est. 1RM", "Top weight", "Volume", "Most reps")) assertTrue("$line missing: $first", first.any { it == line })
        assertTrue("no date: $first", first.size >= 9)
        shot("round19-multi-chart-hold", dialogs = true)
        slideTo(Offset(chart.left + chart.width * 0.85f, chart.center.y))
        val later = peek("chart peek")!!
        assertTrue("the card did not follow the finger: $first vs $later", later.first() != first.first())
        release()
        assertNull("the card stayed after release", peek("chart peek"))
        tap(Offset(chart.left + chart.width * 0.5f, chart.center.y))
        assertNull("a tap showed the scrub card", peek("chart peek"))
        s.close()
    }

    @Test fun theExerciseChartsPassThePagesRangeSelector() {
        val src = java.io.File("../shared/src/commonMain/kotlin/clean/app/Exercises.kt").readText()
        val calls = Regex("SplitLineChart\\(").findAll(src).count()
        assertEquals("every SplitLineChart call of the exercise page has the selector", calls, Regex("selector = RangeSelector\\(range").findAll(src).count())
    }

    // 2. Volume bars
    @Test fun holdingTheVolumeBarsShowsTheBucketAndSlidingSwitchesIt() {
        val s = launch()
        go { model.tab = Tab.Progress }
        settle()
        val bars = scrollFind { boundsOfDescription("Volume lifted") }
        val today = LocalDate.now()
        // A day with a workout (3 days back) and, to slide to, one with none (4 days back), both in this month when it is late enough.
        val busy = today.minusDays(3); val idle = today.minusDays(4)
        org.junit.Assume.assumeTrue(busy.month == today.month && idle.month == today.month)
        val slot = bars.width / today.lengthOfMonth()
        fun at(day: LocalDate) = Offset(bars.left + slot * (day.dayOfMonth - 0.5f), bars.center.y)
        val card = holdAt(at(busy), "bars peek")
        assertTrue("no workout count: $card", card.any { it.contains("1 workout") })
        assertTrue("no sets: $card", card.any { it.contains("6 sets") })
        shot("round19-volume-bars-hold", dialogs = true)
        slideTo(at(idle))
        assertTrue("sliding did not update: ${peek("bars peek")}", peek("bars peek")!!.contains("No workouts"))
        release()
        assertNull(peek("bars peek"))
        s.close()
    }

    // 3. Home week tile
    @Test fun holdingTheWeekTileShowsTheDaysAndTheStreakAndATapStillOpensTrophies() {
        val s = launch()
        go { model.tab = Tab.Home }
        settle()
        val tile = scrollFind { boundsOfText("This week") }
        val at = Offset(tile.left + 60f, tile.bottom + 50f)
        val card = holdAt(at, "week peek")
        assertTrue("no head: $card", card.contains("This week"))
        assertTrue("no trained line: $card", card.any { it.startsWith("Trained ") || it == "Nothing trained yet" })
        assertTrue("no streak: $card", card.any { it.contains("streak") })
        shot("round19-home-week-hold", dialogs = true)
        release()
        assertNull(peek("week peek"))
        assertTrue("the hold opened Trophies", model.stack.isEmpty())
        tap(at)
        assertEquals(Screen.Trophies, model.stack.lastOrNull())
        s.close()
    }

    // 4. Trophies grid
    @Test fun holdingTheTrophiesWeekGridShowsTheWeekAndSlidingChangesIt() {
        val s = launch()
        go { model.open(Screen.Trophies) }
        settle(2_500)
        val cells = scrollFind {
            compose.onAllNodes(SemanticsMatcher("week cell") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Week of") } == true }, useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.width > 10f }.sortedWith(compareBy({ it.top }, { it.left })).also { check(it.size >= 3) }
        }
        assertTrue("no week cells: ${cells.size}", cells.size >= 3)
        // The newest of the first row of 13, and one three to its left.
        val last = cells.last()
        val card = holdAt(last.center, "week peek")
        assertTrue("no week heading: $card", card.first().startsWith("Week of"))
        assertTrue("no count against target: $card", card.any { it.contains(" of ") && (it.endsWith("workouts") || it.endsWith("program days")) })
        shot("round19-trophies-week-hold", dialogs = true)
        val head = card.first()
        slideTo(cells[cells.size - 2].center)
        assertTrue("sliding did not change the week", peek("week peek")!!.first() != head)
        release()
        assertNull(peek("week peek"))
        assertTrue("the hold opened a week", model.stack.lastOrNull() == Screen.Trophies)
        tap(last.center)
        assertTrue("a tap no longer opens the week: ${model.stack.lastOrNull()}", model.stack.lastOrNull() is Screen.Week)
        s.close()
    }

    // 5. Profile lifts
    @Test fun holdingAStrengthLiftShowsItsBestSetAndTheChange() {
        val s = launch()
        go { model.tab = Tab.Profile }
        settle(2_500)
        val head = scrollFind { boundsOfText("Strength") }
        val row = scrollFind { compose.onAllNodes(hasText("Bench Press (Barbell)"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.top > head.top && it.top < 1350f } }
        val at = Offset(row.left + 40f, row.center.y)
        val card = holdAt(at, "strength peek")
        assertTrue("no best set: $card", card.any { it.startsWith("Best set 60 kg × 8") })
        assertTrue("no change: $card", card.any { it.contains(" since ") })
        shot("round19-profile-lift-hold", dialogs = true)
        release()
        assertNull(peek("strength peek"))
        assertTrue("the hold opened the exercise", model.stack.isEmpty())
        tap(at)
        assertTrue("a tap no longer opens the exercise: ${model.stack.lastOrNull()}", model.stack.lastOrNull() is Screen.ExerciseDetail)
        s.close()
    }

    // 6. Personal bests
    @Test fun holdingAPersonalBestRowPeeksAtTheWorkoutInBothViews() {
        val s = launch()
        go { model.open(Screen.PersonalBests) }
        settle(2_500)
        val row = boundsOfText("Bench Press (Barbell)")
        val card = holdAt(Offset(row.left + 40f, row.center.y), "workout peek")
        assertTrue("not the workout: $card", card.contains("Push"))
        assertTrue("no day line: $card", card.any { it.contains("·") })
        shot("round19-bests-hold", dialogs = true)
        release()
        assertNull(peek("workout peek"))
        tap(Offset(row.left + 40f, row.center.y))
        assertTrue("a tap no longer opens the exercise: ${model.stack.lastOrNull()}", model.stack.lastOrNull() is Screen.ExerciseDetail)
        go { model.back() }
        settle()
        tap(boundsOfText("By month").center)
        settle(500)
        val month = boundsOfText("Bench Press (Barbell)")
        val peeked = holdAt(Offset(month.left + 40f, month.center.y), "workout peek")
        assertTrue("not the workout: $peeked", peeked.contains("Push"))
        release()
        s.close()
    }

    // 8. Measurement entries
    @Test fun holdingAWeighInShowsTheChangeAndTheGoalAndATapStillEdits() {
        val s = launch()
        weighIns()
        go { setWeightGoal(model, 70.0, 74.0); model.open(Screen.MeasurementDetail("weight.csv")) }
        settle(2_500)
        val entry = scrollFind { compose.onAllNodes(hasText("74.5 kg"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.top > 0f } }
        val at = Offset(entry.left + 10f, entry.center.y)
        val card = holdAt(at, "entry peek")
        assertTrue("no change: $card", card.any { it.contains("1.5 kg since") })
        assertTrue("no goal distance: $card", card.any { it.endsWith("kg to goal") })
        shot("round19-weighin-hold", dialogs = true)
        release()
        assertNull(peek("entry peek"))
        // A tap still reaches what is under the hold: the date of a day with a workout opens that workout (the row itself edits, in a dialog this suite cannot read).
        val date = compose.onAllNodes(hasText("8:00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.top > 100f }.minByOrNull { it.top }!!
        tap(Offset(date.left + 20f, date.center.y))
        assertTrue("a tap no longer opens the workout: ${model.stack.lastOrNull()}", model.stack.lastOrNull() is Screen.WorkoutDetail)
        s.close()
    }

    // 9. Template cards
    @Test fun theTemplateMenuHasAPeekHeadWhenTheTemplateWasDone() {
        val s = launch()
        templates()
        val done = compose.onAllNodes(hasText("Push"), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.width > 0f }.maxByOrNull { it.top }!!
        root().performTouchInput { down(Offset(done.left + 20f, done.center.y)) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertEquals("no peek head", 1, count("hold peek"))
        val head = peek("hold peek")!!
        assertTrue("no set and exercise count: $head", head.contains("2 exercises · 6 sets"))
        for (action in listOf("Open", "Edit", "Delete")) assertTrue("the menu lost $action", textCount(action) >= 1)
        shot("round19-template-hold", dialogs = true)
        root().performTouchInput { up() }
        s.close()
    }

    @Test fun theTemplateMenuHasNoHeadForATemplateNeverDone() {
        val s = launch()
        templates()
        val never = boundsOfText("Never done")
        root().performTouchInput { down(Offset(never.left + 20f, never.center.y)) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertEquals("a head for a template never done", 0, count("hold peek"))
        for (action in listOf("Open", "Edit", "Delete")) assertTrue("the menu lost $action", textCount(action) >= 1)
        root().performTouchInput { up() }
        s.close()
    }

    @Test fun templatePeekIsNullForATemplateNeverDone() {
        val t = Template(1, "Push", listOf(clean.core.TemplateBlock("A", List(2) { clean.core.SetType.NORMAL }), clean.core.TemplateBlock("B", List(1) { clean.core.SetType.NORMAL })))
        assertNull(templatePeek(t, null))
        assertEquals("3 sets", templatePeek(t, "2026-11-10 18:00:00")!![1].substringAfter("· "))
        assertEquals("2 exercises", templatePeek(t, "2026-11-10 18:00:00")!![1].substringBefore(" ·"))
    }

    @Test fun peekCardsAreKeptInsideTheWindowMargins() {
        // PeekPopup places with InsideWindow(centered, margin, above, below): near either edge it is pushed in, near the top it goes below the finger.
        fun place(at: Offset) = InsideWindow(at, true, 32, 0f, 0f, 56f, 72f).calculatePosition(
            androidx.compose.ui.unit.IntRect(0, 100, 800, 700), androidx.compose.ui.unit.IntSize(800, 1700), androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.unit.IntSize(496, 200),
        )
        assertEquals(32, place(Offset(4f, 400f)).x)
        assertEquals(800 - 32 - 496, place(Offset(796f, 400f)).x)
        assertTrue(place(Offset(400f, 10f)).y >= 100 + 10)
    }
}
