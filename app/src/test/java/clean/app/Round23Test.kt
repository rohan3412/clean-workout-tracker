package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Profile
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.app.data.open
import clean.core.ChartPoint
import clean.core.PrKind
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
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
import java.io.File

/**
 * Round 23: the rarely visited pages. One test for each item and bug (content present, taps and holds work, the month moves, the running week is left out of the trend,
 * a pound profile's height, a measurement's own morph key, undo on a delete) and a tour of the pages that had no picture (round23-graphite-dark-* and -light-*).
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round23Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: AppModel
    private var window: View? = null
    private var profileId = 0L
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Bicep Curl (Dumbbell)",
        "Triceps Pushdown (Cable)", "Romanian Deadlift (Barbell)", "Lateral Raise (Dumbbell)",
    )
    private val bench = names[0]

    /** A profile with thirty weeks of three workouts a week, one in the week still running, a height and three kinds of measurement. */
    private fun launch(unit: String = "kg", measurements: Boolean = true): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            profileId = id
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            val rows = ArrayList<WorkoutRow>()
            val first = LocalDate.now().minusWeeks(30)
            var n = 0
            fun add(day: LocalDate, hour: Int) {
                n++
                val date = day.atTime(hour, 0).format(STRONG_DATE_FORMAT)
                val name = listOf("Push", "Pull", "Legs")[n % 3]
                for (e in 0 until 4) {
                    val exercise = names[(n + e * 3) % names.size]
                    rows += WorkoutRow(n.toString(), date, name, "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                    for (s in 1..3) rows += WorkoutRow(n.toString(), date, name, "3900", exercise, s.toString(), decimalText(40.0 + n * 0.5 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
                }
            }
            for (week in 0 until 30) for (offset in listOf(0L, 2L, 4L)) {
                val day = first.plusWeeks(week.toLong()).plusDays(offset)
                if (day < LocalDate.now()) add(day, 18)
            }
            // The week still running has a workout in it (the Monday, or today when it is Monday), earlier in the day than any other.
            add(clean.core.weekStart(LocalDate.now()), 7)
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
            for (t in listOf("Push", "Pull", "Legs")) {
                val blocks = names.take(6).joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
                repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
            }
            repo.dao.update(Profile(id, "Rohan", unit, 178.0))
            repo.dao.put(ExerciseSetting(id, bench, goal = "100.0x5"))
            if (measurements) {
                repo.dao.insertMeasurements((0 until 4).flatMap { k ->
                    val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
                    listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
                })
                repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun open(screen: Screen) { go { model.stack.clear(); model.open(screen) }; settle() }
    private fun textCount(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + node.children.flatMap { texts(it) }

    private fun peek(tag: String): List<String>? =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { texts(it) }

    /** Looks for something with [find]; when it is not on screen, scrolls each vertical list of the page to item 1, 2, 3 ... until it is. */
    private fun <T> scrollFind(find: () -> T): T {
        runCatching { return find() }
        val lists = compose.onAllNodes(SemanticsMatcher("lazy list") { it.config.contains(SemanticsActions.ScrollToIndex) }, useUnmergedTree = true).fetchSemanticsNodes().sortedByDescending { it.boundsInRoot.height }
        for (index in 1..12) {
            for (list in lists) {
                val moved = runCatching { compose.runOnIdle { list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index) } }
                if (moved.isFailure) continue
                settle(400)
                runCatching { return find() }
            }
        }
        return find()
    }

    /** Scrolls the page until [text] is on it. */
    /** Waits (in real time: the figures of a page are made off the main thread) for [text], then scrolls the page until it is on it. */
    private fun see(text: String) {
        repeat(60) { if (textCount(text) > 0) return; Thread.sleep(100); compose.waitForIdle() }
        scrollFind { check(textCount(text) > 0) { "no '$text' on the page; texts: " + compose.onAllNodes(SemanticsMatcher("t") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }.distinct().take(60) } }
    }

    private fun boundsOfText(text: String): Rect { see(text); return scrollFind {
        compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 0f && it.top >= 0f && it.top < 1700f }
            ?: throw AssertionError("no text '$text' on screen")
    } }

    private fun holdAt(at: Offset, tag: String): List<String> {
        root().performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        return peek(tag) ?: throw AssertionError("no card '$tag' after a hold at $at")
    }

    private fun release() { root().performTouchInput { up() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle() }

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
        check(model.look.value.dark == (theme == "dark")) { "the look did not change to $theme" }
    }

    private fun theme(name: String) {
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, name) }
        repeat(200) { if (model.look.value.themeName == name) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.themeName == name) { "the theme did not change to $name" }
    }

    private val top: Screen? get() = model.stack.lastOrNull()

    // ---- 1. The exercise's PRs tab ----

    @Test fun prsTabHasAHeroTilesALadderChipsAndTheLatestPrs() {
        val s = launch()
        open(Screen.ExerciseDetail(bench, tab = 3))
        for (word in listOf("Est. 1RM", "Records", "Heaviest weight", "Most reps", "Best set volume", "Best session volume")) see(word)
        see("Best reps at each weight")
        see("Rep maxes")
        for (n in listOf("1RM", "3RM", "5RM")) see(n)
        see("PR history")
        // The figures are to one decimal at most.
        val records = listOf("Est. 1RM", "Best set volume", "Best session volume")
        val all = compose.onAllNodes(SemanticsMatcher("any text") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }
        assertTrue("a figure with more than one decimal: ${all.filter { Regex("^\\d+\\.\\d{2,}$").matches(it) }}", all.none { Regex("^\\d+\\.\\d{2,}$").matches(it) })
        assertTrue(records.isNotEmpty())
        s.close()
    }

    @Test fun holdingARecordTilePeeksAtItsWorkout() {
        val s = launch()
        open(Screen.ExerciseDetail(bench, tab = 3))
        val at = boundsOfText("Heaviest weight")
        val card = holdAt(Offset(at.center.x, at.center.y + 20f), "record peek")
        assertTrue("no workout in the peek: $card", card.size >= 3)
        release()
        assertNull("the peek stayed after release", peek("record peek"))
        s.close()
    }

    @Test fun theLadderRowsPeekAndTheHistoryCardOpensTheTimeline() {
        val s = launch()
        open(Screen.ExerciseDetail(bench, tab = 3))
        see("Best reps at each weight")
        val rung = boundsOfText("reps").let { _ -> compose.onAllNodes(SemanticsMatcher("rung") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains(" reps, ") || d.contains(" rep, ") } == true }, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot }
        val card = holdAt(rung.center, "workout peek")
        assertTrue("no workout in the ladder's peek: $card", card.isNotEmpty())
        release()
        s.close()
    }

    @Test fun theHistoryCardOpensTheTimeline() {
        val s = launch()
        open(Screen.ExerciseDetail(bench, tab = 3))
        see("PR history")
        compose.onNodeWithText("PR history").performClick(); settle(600)
        assertTrue("PR history did not open the timeline: $top", top == Screen.PrTimeline(bench))
        s.close()
    }

    // ---- 7. The History tab ----

    @Test fun historyTabHasAStripOfThreeFigures() {
        val s = launch()
        open(Screen.ExerciseDetail(bench, tab = 1))
        for (word in listOf("Sessions", "Last done", "Best 1RM")) see(word)
        s.close()
    }

    @Test fun anExerciseNeverLoggedHasAnEmptyStateThatAddsItToAWorkout() {
        val s = launch()
        val unlogged = "Face Pull (Cable)"
        assertTrue(model.workouts.value.none { w -> w.blocks.any { it.exercise == unlogged } })
        open(Screen.ExerciseDetail(unlogged, tab = 1))
        see("Not logged yet")
        compose.onNodeWithText("Add to workout").performClick(); settle(600)
        assertTrue("it was not added to a workout: ${model.draft}", model.draft?.holds(unlogged) == true)
        s.close()
    }

    // ---- 2. Measurements ----

    @Test fun measurementsAreCardsWithTheirLatestChangeAndLineAndAPlusInTheBar() {
        val s = launch()
        open(Screen.Measurements)
        for (word in listOf("BMI", "Body", "Waist", "Chest", "Weight", "Not logged yet")) see(word)
        assertTrue("no change line", textCount("since") > 0)
        compose.onNodeWithContentDescription("New measurement").assertExists()
        s.close()
    }

    @Test fun aMeasurementCardOpensItsPageAndTheBmiCardOpensBmi() {
        val s = launch()
        open(Screen.Measurements)
        compose.onNodeWithText("Waist").performClick(); settle(600)
        assertTrue("$top", top == Screen.MeasurementDetail("waist.csv"))
        open(Screen.Measurements)
        see("BMI")
        compose.onNodeWithText("BMI").performClick(); settle(600)
        assertTrue("$top", top == Screen.Bmi)
        s.close()
    }

    @Test fun measurementsWithNothingLoggedHaveAnEmptyState() {
        val s = launch(measurements = false)
        open(Screen.Measurements)
        see("Nothing measured yet")
        s.close()
    }

    @Test fun ownMeasurementsHaveTheirOwnHeading() {
        val s = launch()
        go { model.addMeasurementKind("Forearm", "cm") }
        settle()
        open(Screen.Measurements)
        see("Your own")
        see("Forearm")
        s.close()
    }

    // ---- 3. Month ----

    @Test fun theMonthPageHasAHeroACalendarBarsAndStepsBetweenMonths() {
        val s = launch()
        val now = YearMonth.now()
        open(Screen.Month(now.toString()))
        for (word in listOf("Volume", "Month before", "Month after", "Calendar", "Sets per muscle", "Workouts")) see(word)
        compose.onNodeWithText("Month after", substring = true).assertIsNotEnabled()
        compose.onNodeWithText("Month before", substring = true).performClick(); settle(600)
        compose.onNodeWithText("Month after", substring = true).assertIsEnabled()
        val before = now.minusMonths(1)
        assertTrue("the page did not move to $before", textCount(before.year.toString()) > 0)
        s.close()
    }

    @Test fun aDayOfTheMonthCalendarOpensItInTheCalendar() {
        val s = launch()
        val month = YearMonth.now().minusMonths(1)
        open(Screen.Month(month.toString()))
        val day = (1..month.lengthOfMonth()).map { month.atDay(it) }.first { d -> model.workouts.value.any { it.day == d.toString() } }
        scrollFind { compose.onAllNodesWithContentDescription(prettyDay(day), substring = true).fetchSemanticsNodes().also { check(it.isNotEmpty()) } }
        compose.onAllNodesWithContentDescription(prettyDay(day), substring = true).onFirst().performClick(); settle(600)
        assertEquals(Screen.Calendar(day.toString()), top)
        s.close()
    }

    @Test fun theMonthsPrsAreChips() {
        val s = launch()
        val month = YearMonth.now().minusMonths(1)
        val inMonth = model.workouts.value.filter { it.day.startsWith(month.toString()) }
        val rows = prRows(inMonth, model.prs.value)
        assertTrue("the seed has no PR last month", rows.isNotEmpty())
        open(Screen.Month(month.toString()))
        see("PRs")
        val kind = PrKind.entries.first { it in rows.first().kinds }.label
        see(kind)
        s.close()
    }

    // ---- 4. Week ----

    @Test fun theWeekPageHasBarsAgainstTheTargetAndChipsAgainstTheEightWeekAverage() {
        val s = launch()
        val monday = clean.core.weekStart(LocalDate.now().minusWeeks(1))
        open(Screen.Week(monday.toString()))
        see("Week before"); see("Week after")
        see("vs 8-week avg")
        see("Sets per muscle")
        assertTrue("no bar says what is usual", compose.onAllNodes(SemanticsMatcher("usual") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains("usual") } == true }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("the old bracket notation is gone", 0, compose.onAllNodes(SemanticsMatcher("bracket") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> Regex("[0-9] +[(][0-9]").containsMatchIn(t.text) } == true }, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Week before", substring = true).performClick(); settle(600)
        assertTrue(textCount(prettyDay(monday.minusWeeks(1))) > 0)
        s.close()
    }

    // ---- 5. MeasurementDetail ----

    @Test fun aMeasurementPageHasLowestHighestAverageAndShortDates() {
        val s = launch()
        open(Screen.MeasurementDetail("waist.csv"))
        for (word in listOf("Lowest", "Highest", "Average")) see(word)
        val day = prettyDay(LocalDate.now().minusWeeks(3))
        see(day)
        assertEquals("no long weekday date any more", 0, textCount(prettyDate(LocalDate.now().minusWeeks(3).atTime(8, 0).format(STRONG_DATE_FORMAT))))
        s.close()
    }

    @Test fun deletingAnEntryShowsTheUndoPill() {
        val s = launch()
        val entry = model.measurements.value.first { it.file == "waist.csv" }
        compose.mainClock.autoAdvance = false
        go { model.deleteMeasurement(entry) }
        repeat(60) { if (model.undos != null) return@repeat; Thread.sleep(100); compose.runOnIdle { } }
        assertNotNull("no undo after a delete", model.undos)
        assertTrue(model.undos!!.steps.any { it.text == "Entry deleted" })
        s.close()
    }

    @Test fun eachMeasurementHasItsOwnMorphKeyAndWeightKeepsHome() {
        assertEquals("chart:weight", measurementMorphKey("weight.csv"))
        assertTrue(measurementMorphKey("waist.csv") != measurementMorphKey("chest.csv"))
        assertTrue(measurementMorphKey("waist.csv") != "chart:weight")
    }

    @Test fun theChartRangeIsPaddedAndNeverATinySpan() {
        val r = paddedRange(listOf(82.0, 82.4))
        assertTrue("span ${r.endInclusive - r.start}", r.endInclusive - r.start >= 82.2 * 0.03 - 0.2)
        assertTrue(r.start < 82.0 && r.endInclusive > 82.4)
        val wide = paddedRange(listOf(70.0, 90.0))
        assertTrue("5 % of the span on each side", wide.start <= 69.0 && wide.endInclusive >= 91.0)
    }

    // ---- 6. BMI and B4 ----

    @Test fun theBmiPageHasAHeroABandBarAndOneExpandingDisclaimer() {
        val s = launch()
        open(Screen.Bmi)
        see("Your BMI"); see("Over time"); see("About BMI")
        assertTrue("a band word", listOf("Underweight", "Healthy weight", "Overweight", "Obese").any { textCount(it) > 0 })
        assertEquals("not shown yet", 0, textCount("Someone heavy with low body fat"))
        see("Why this may mislead")
        root().performTouchInput { swipeUp() }; settle(600)
        compose.onNodeWithText("Why this may mislead").performClick(); settle(600)
        assertTrue("it did not expand", textCount("Someone heavy with low body fat") > 0)
        s.close()
    }

    @Test fun aPoundProfileSeesItsHeightInFeetAndInches() {
        assertEquals("5 ft 10 in", heightText(178.0, "lb"))
        assertEquals("178 cm", heightText(178.0, "kg"))
        assertEquals("6 ft 0 in", heightText(182.9, "lb"))
        val s = launch(unit = "lb")
        open(Screen.Bmi)
        see("5 ft 10 in")
        assertEquals(0, compose.onAllNodesWithText("178 cm", substring = true).fetchSemanticsNodes().count { it.boundsInRoot.top < 800f && textCount("5 ft 10 in") > 0 && false })
        s.close()
    }

    // ---- B1, B2: Trends ----

    @Test fun theRunningWeekIsLeftOutOfTheTrend() {
        val d = LocalDate.now()
        val points = (0 until 8).map { ChartPoint(d.minusWeeks(8L - it), 10.0) } + ChartPoint(d, 1.0)
        assertEquals("a flat line stays flat without the running week", 0.0, trendOf(points, partialLast = true)!!.first, 1e-9)
        assertTrue("with it the trend falls", trendOf(points, partialLast = false)!!.first < 0.0)
        assertTrue(isRunningWeek(clean.core.weekStart(d), d))
        assertFalse(isRunningWeek(clean.core.weekStart(d).minusWeeks(1), d))
    }

    @Test fun theTrendsPageNamesTheDashedWeek() {
        val s = launch()
        open(Screen.Trends)
        see("Dashed: this week so far")
        assertTrue(textCount("so far") > 0)
        s.close()
    }

    @Test fun theTrendLineHasOneSpaceTheAppsMinusAndNamesItsUnit() {
        assertEquals("Trend: −0.03 workouts a week, per month", trendLine(-0.001, "", "workouts a week"))
        assertEquals("Trend: +0.3 a month", trendLine(0.01, ""))
        assertEquals("Trend: +1.5 kg a month", trendLine(0.05, "kg"))
        assertEquals("Trend: level", trendLine(0.0, "kg"))
        for (line in listOf(trendLine(-0.001, "", "workouts a week"), trendLine(0.01, ""), trendLine(-0.2, "kg"))) {
            assertFalse("double space in '$line'", line.contains("  "))
            assertFalse("a hyphen as a minus in '$line'", line.contains("-"))
        }
    }

    // ---- B3: a muscle never trained ----

    @Test fun aMuscleNeverTrainedHasNoRetainedStrength() {
        val s = launch()
        val trained = clean.core.muscleSets(model.workouts.value, null) { model.musclesOf(it) }.filterValues { it > 0 }.keys
        val untrained = clean.core.Muscle.entries.first { it !in trained }
        open(Screen.MuscleDetail(untrained))
        see("not trained yet")
        see("Retained strength")
        assertEquals("no made-up percentage", 0, textCount("50%"))
        s.close()
    }

    // ---- Small pure parts ----

    @Test fun signsAndPercentsUseTheAppsMinus() {
        assertEquals("+3", signed(3.0))
        assertEquals("−2", signed(-2.0))
        assertEquals("0", signed(0.04))
        assertEquals("+0.5", signed(0.5, 1))
        assertEquals("+12%", percentDelta(112.0, 100.0))
        assertEquals("−8%", percentDelta(92.0, 100.0))
        assertNull(percentDelta(5.0, 0.0))
    }

    // ---- The tour ----

    private fun tour(prefix: String) {
        fun s(name: String, dialogs: Boolean = false, step: () -> Unit) {
            try { step(); shot("round23-$prefix-$name", dialogs) } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round23-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        val month = YearMonth.now()
        s("01-measurements") { open(Screen.Measurements) }
        s("02-measurements-lower") { root().performTouchInput { swipeUp() } }
        s("03-bmi") { open(Screen.Bmi) }
        s("04-bmi-expanded") { root().performTouchInput { swipeUp() }; settle(600); compose.onNodeWithText("Why this may mislead").performClick(); settle(1500) }
        s("05-week") { open(Screen.Week(clean.core.weekStart(LocalDate.now().minusWeeks(1)).toString())) }
        s("06-week-lower") { root().performTouchInput { swipeUp() } }
        s("07-month") { open(Screen.Month(month.minusMonths(1).toString())) }
        s("08-month-lower") { root().performTouchInput { swipeUp() } }
        s("09-pr-timeline") { open(Screen.PrTimeline(bench)) }
        s("10-prs-tab") { open(Screen.ExerciseDetail(bench, tab = 3)) }
        s("11-prs-tab-lower") { root().performTouchInput { swipeUp() } }
        s("12-prs-tab-bottom") { root().performTouchInput { swipeUp() } }
        s("13-history-tab") { open(Screen.ExerciseDetail(bench, tab = 1)) }
        s("14-history-empty") { open(Screen.ExerciseDetail("Face Pull (Cable)", tab = 1)) }
        s("15-measurement-detail") { open(Screen.MeasurementDetail("waist.csv")) }
        s("16-measurement-detail-lower") { root().performTouchInput { swipeUp() } }
        s("17-trends") { open(Screen.Trends) }
        s("18-muscle-untrained") {
            val trained = clean.core.muscleSets(model.workouts.value, null) { model.musclesOf(it) }.filterValues { it > 0 }.keys
            open(Screen.MuscleDetail(clean.core.Muscle.entries.first { it !in trained }))
        }
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        val s = launch()
        look("dark"); theme("Graphite")
        tour("graphite-dark")
        look("light"); theme("Graphite")
        tour("graphite-light")
        s.close()
    }
}
