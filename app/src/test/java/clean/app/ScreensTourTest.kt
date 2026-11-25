package clean.app

import clean.app.android.*

import clean.core.time.*
import kotlinx.datetime.LocalDateTime
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Repo
import clean.app.data.Profile
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.core.STRONG_DATE_FORMAT
import clean.core.SetType
import clean.core.WorkoutRow
import clean.core.decimalText
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.datetime.LocalDate

/**
 * A picture of each main screen of the real app over a made-up log, saved under build/screens/ to be looked at in a design review
 * (customization/uiux-pass.md). It checks nothing. The look is the default of a new install (Dark, pure black off, theme Graphite); a Light Home and an Ember Home are shot once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ScreensTourTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Bicep Curl (Dumbbell)",
        "Triceps Pushdown (Cable)", "Romanian Deadlift (Barbell)", "Lateral Raise (Dumbbell)",
    )

    private fun seed() = runBlocking {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        // The screenshots are drawn on a software canvas, where Haze cannot blur; the solid glass is what shows here.
        repo.dao.put(Kv("glass.reduce", "on"))
        val rows = ArrayList<WorkoutRow>()
        val first = LocalDate.now().minusWeeks(30)
        var n = 0
        for (week in 0 until 30) for (offset in listOf(0L, 2L, 4L)) {
            n++
            val day = first.plusWeeks(week.toLong()).plusDays(offset)
            if (day >= LocalDate.now()) continue
            val date = day.atTime(18, 0).format(STRONG_DATE_FORMAT)
            val name = listOf("Push", "Pull", "Legs")[n % 3]
            for (e in 0 until 4) {
                val exercise = names[(n + e * 3) % names.size]
                rows += WorkoutRow(n.toString(), date, name, "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                for (s in 1..3) rows += WorkoutRow(n.toString(), date, name, "3900", exercise, s.toString(), decimalText(40.0 + n * 0.5 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
            }
        }
        repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        // Templates (named like the workouts so Up next has a last date), a height, a goal and a few measurements for Home's and Profile's sections.
        for (t in listOf("Push", "Pull", "Legs")) {
            val blocks = names.take(6).joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
            repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
        }
        repo.dao.update(Profile(id, "Rohan", "kg", 178.0))
        repo.dao.put(ExerciseSetting(id, "Bench Press (Barbell)", goal = "100.0x5"))
        repo.dao.put(Kv("goal.weight:$id", "78.0|82.0|${LocalDate.now().minusWeeks(20)}"))
        repo.dao.insertMeasurements((0 until 4).flatMap { k ->
            val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
            listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
        })
        repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
    }

    private lateinit var model: AppModel
    private var window: android.view.View? = null

    private fun shot(name: String, dialogs: Boolean = false) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        // A sheet is a window of its own: the windows over the activity are drawn on top, in the order they were added.
        if (dialogs) {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<android.view.View>
            for (root in roots) if (root !== view && root.width > 0) root.draw(canvas)
        }
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The Library's list is made off the main thread: wait (in real time) until the typed search has replaced the list at rest. */
    private fun typed() = compose.waitUntil(10_000) { compose.onAllNodesWithText("Show all", substring = true).fetchSemanticsNodes().isEmpty() }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)

    /** Chooses Dark or Light as Settings does and waits (in real time) until the app has read the choice back: the write goes through the database, which the test clock does not wait for. */
    private fun look(theme: String) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        // The write goes through Room's own thread and comes back by the main looper: give both real time, idling the screen between looks.
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.dark == (theme == "dark")) { "the look did not change to $theme" }
    }

    /** Chooses a theme as the Appearance page does and waits (in real time) until the app has read it back. */
    private fun theme(name: String) {
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, name) }
        repeat(200) { if (model.look.value.themeName == name) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.themeName == name) { "the theme did not change to $name" }
    }

    @Test fun `main screens`() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        shot("01-home")
        // Activity starts at the same y on Home and on Progress at rest (one constant under both pages): taken here, compared on Progress below.
        val homeActivityY = model.activityY.getValue(Tab.Home).floatValue
        check(!homeActivityY.isNaN()) { "Home did not report where Activity starts" }
        // Home scrolled down to the tiles, then back to the top for the looks below.
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }; shot("01d-home-tiles")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeDown() }
        // The same Home in Light, then back to Dark.
        look("light"); shot("01b-home-light")
        look("dark")
        theme("Ember"); shot("01c-home-ember")
        theme("Graphite")
        // Round 6b (A2): the dock with the plus on the left.
        go { model.setOption("dock.left", "on") }
        repeat(200) { if ((model.options.value["dock.left"] == "on")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check((model.options.value["dock.left"] == "on")) { "dock.left did not switch on" }
        shot("01e-home-dock-left")
        go { model.setOption("dock.left", "off") }
        repeat(200) { if (!(model.options.value["dock.left"] == "on")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        go { model.tab = Tab.Progress }; shot("02-progress")
        val progressActivityY = model.activityY.getValue(Tab.Progress).floatValue
        check(!progressActivityY.isNaN()) { "Progress did not report where Activity starts" }
        check(kotlin.math.abs(progressActivityY - homeActivityY) <= 1f) { "Activity starts at y=$homeActivityY on Home but y=$progressActivityY on Progress" }
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }; shot("02b-progress-rows")
        go { model.tab = Tab.Home; model.openWorkoutSheet() }; shot("03-workout-sheet")
        go { model.workoutSheet = false; model.tab = Tab.Exercises }; shot("04-library")
        // Recent expanded, then back; then two searches.
        compose.onNodeWithText("Show all", substring = true).performClick(); shot("04c-library-recent-all")
        compose.onNodeWithText("Show fewer").performClick()
        compose.onNodeWithContentDescription("Search exercises").performClick(); compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("incline db pres"); typed(); shot("04b-library-search")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("pull down"); typed(); shot("04d-library-search-done")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("rwo"); typed(); shot("04e-library-nothing")
        compose.onNode(hasSetTextAction()).performTextClearance()
        go { model.tab = Tab.Profile }; shot("05-profile")
        // Profile scrolled down to Strength and Goals and milestones.
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }; shot("05b-profile-lower")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeDown() }
        // Snapshots: solid-colour pictures dated today, last week, this month and last year; Profile's tile first empty, then the fan, then the Moments page.
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(2) } }; shot("05f-profile-snapshots-empty")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0)
        val colours = listOf(0xFF3B5BA5, 0xFF8A4F7D, 0xFF4F8A6B, 0xFFB5793A, 0xFF5A6B7C, 0xFF9A4D4D, 0xFF3F7F8F, 0xFF7A8A3F, 0xFF6D5BA5, 0xFFA5873B)
        val dates = listOf(now, now.minusHours(2), now.minusDays(2), now.minusDays(4), now.minusDays(10), now.minusDays(12), now.minusDays(30), now.minusDays(45), now.minusYears(1), now.minusYears(1).minusDays(3))
        for ((i, at) in dates.withIndex()) {
            val file = MomentFiles.file(context, model.activeId.value!!, clean.core.momentOf(at, "%08x".format(i + 1)).name).also { it.parentFile!!.mkdirs() }
            val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).also { it.eraseColor(colours[i].toInt()) }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
        go { model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.size == dates.size }
        repeat(4) { Thread.sleep(500); compose.waitForIdle(); compose.mainClock.advanceTimeBy(100) }; shot("05g-profile-snapshots")
        go { kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Profile).scrollToItem(0) } }
        go { model.open(Screen.Moments) }; repeat(4) { Thread.sleep(500); compose.waitForIdle(); compose.mainClock.advanceTimeBy(100) }; shot("05h-moments")
        go { model.stack.clear() }
        // The weigh-in sheet (a window of its own): its ruler and date card, the month open under Earlier, then the number pad typing the weight.
        fun labelled(label: String) = androidx.compose.ui.test.SemanticsMatcher("clickable $label") {
            it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) && it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label == label
        }
        go { model.logWeight() }; shot("05c-weigh-in", dialogs = true)
        compose.onNode(labelled("Change when")).performClick(); compose.waitForIdle(); shot("05c2-weigh-in-when", dialogs = true)
        compose.onNodeWithText("Earlier").performClick(); shot("05d-weigh-in-date", dialogs = true)
        compose.onNode(labelled("Type the weight")).performClick(); shot("05e-weigh-in-pad", dialogs = true)
        compose.onNodeWithText("8").performClick(); compose.onNodeWithText("1").performClick(); compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithText("81.00").fetchSemanticsNodes().isNotEmpty()) { "the typed weight did not reach the sheet" }
        // Round 8: the weight page (the + in the bar, the value, the quiet goal line, the period control), another measurement page, and Home mid-pull.
        go { model.stack.clear(); model.tab = Tab.Home }; shot("05i0-home-settled")
        go { model.stack.clear(); model.logWeightNow = false; model.open(Screen.MeasurementDetail("weight.csv")) }; shot("05i-weight-page")
        go { model.stack.clear(); model.open(Screen.MeasurementDetail("chest.csv")) }; shot("05j-chest-page")
        go { model.stack.clear(); model.tab = Tab.Home }; compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().performTouchInput { down(androidx.compose.ui.geometry.Offset(centerX, 500f)); moveBy(androidx.compose.ui.geometry.Offset(0f, 300f)) }; shot("01f-home-pulled")
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().performTouchInput { up() }; compose.waitForIdle()

        go { model.stack.clear(); model.tab = Tab.Profile }
        // Settings is the gear on the Profile tab; Home's top right has no round buttons.
        compose.onNodeWithContentDescription("Settings").assertExists()
        go { model.tab = Tab.Home }; compose.waitForIdle()
        // All four tabs stay composed once warm, so Profile's gear exists off to the side; none may be on screen over Home.
        val screenWidth = compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().fetchSemanticsNode().boundsInRoot.width
        val onScreen = compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().filter { it.boundsInRoot.right > 0f && it.boundsInRoot.left < screenWidth }
        org.junit.Assert.assertTrue("Home shows a Settings button: $onScreen", onScreen.isEmpty())
        go { model.tab = Tab.Home; model.openHistory() }; shot("06-history")
        // A hold on a workout card: the page dims, the card lifts and its menu sits beside it (B5).
        compose.onAllNodesWithText("Push", substring = true).onFirst().performTouchInput { down(center) }; shot("06b-history-hold"); check(model.holds.hold != null) { "the hold did not open" }
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        go { model.holds.hold = null }
        go { model.stack.clear(); model.openBody() }; shot("07-body")
        go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.last().date)) }; shot("08-workout-detail")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0])) }; shot("09-exercise-info")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 2)) }; shot("10-exercise-charts")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 3)) }; shot("10b-exercise-prs")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 1)) }; shot("10c-exercise-history")
        go { model.stack.clear(); model.open(Screen.Summary(model.workouts.value.last().date)) }; shot("10d-summary")
        go { model.stack.clear(); model.open(Screen.MuscleDetail(clean.core.Muscle.entries.first())) }; shot("10e-muscle")
        go { model.stack.clear(); model.open(Screen.TemplateStats(model.templates.value.first().id)) }; shot("10f-template")
        // Round 7 (N10): a few workouts tagged on purpose; History then has its row of tag chips, and a tagged workout's page its tag line and sheet.
        val tagged = model.allWorkouts.value.takeLast(8).map { it.date }
        go { tagged.take(3).forEach { model.setWorkoutTag(it, "Deload") }; model.setWorkoutTag(tagged[3], "Sick") }
        repeat(200) { if (model.tagsInUse.value.size == 2) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.tagsInUse.value == listOf("Deload", "Sick")) { "the tags did not arrive" }
        go { model.stack.clear(); model.tab = Tab.Home; model.openHistory() }; shot("06c-history-tag-chips")
        compose.onNodeWithText("Deload").performClick(); shot("06d-history-tag-filtered")
        go { model.stack.clear(); model.open(Screen.WorkoutDetail(tagged[0])) }; shot("08b-workout-tagged")
        compose.onNodeWithText("Deload").performClick(); shot("08c-tag-sheet", dialogs = true)
        go { model.stack.clear() }; compose.waitForIdle()
        // Round 7 (N14): the year in review, with the other years as chips.
        go { model.stack.clear(); model.open(Screen.YearReview(LocalDate.now().year)) }; repeat(4) { Thread.sleep(300); compose.waitForIdle(); compose.mainClock.advanceTimeBy(300) }; shot("19-year-review")
        go {
            model.stack.clear()
            model.putDraft(Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.take(3).map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
            model.open(Screen.Active)
        }
        shot("11-active")
        // A weight box tapped: the number pad floats over the list as glass.
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("edit box") { (androidx.compose.ui.semantics.SemanticsConfiguration::class.java.let { _ -> if (it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label else null }) == "Edit" }).onFirst().performClick()
        shot("11b-active-pad")
        go { model.stack.clear(); model.open(Screen.Settings) }; shot("12-settings")
        // Settings is closed in between so each page opens fresh (it reads the page asked for when it is composed).
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_LOOK; model.open(Screen.Settings) }; shot("12b-settings-appearance")
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_WORKOUT; model.open(Screen.Settings) }; shot("12c-settings-workout")
        // The rest sounds, a little way down the same page.
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.25f, durationMillis = 3_000) }; shot("12f-settings-rest-sound")
        // The toast that ends a saved workout, and the slide that guards Delete everything (B8, B7).
        go { model.stack.clear(); model.tab = Tab.Home; model.saidSaved("Push", model.workouts.value.last().date) }; shot("12d-saved-toast")
        go { model.notice = null }
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_BACKUP; model.open(Screen.Settings) }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        repeat(8) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
        compose.onNodeWithText("Delete everything…").performClick()
        repeat(200) { if (compose.onAllNodesWithText("Slide to delete everything").fetchSemanticsNodes().isEmpty()) { Thread.sleep(50); compose.waitForIdle() } }
        shot("12e-slide-to-confirm")
        go { model.stack.clear(); model.open(Screen.Trophies) }; shot("13-trophies")
        go { model.stack.clear(); model.open(Screen.Trends) }; shot("16-trends")
        go { model.stack.clear(); model.open(Screen.PersonalBests) }; shot("17-personal-bests")
        go { model.stack.clear(); model.open(Screen.Calendar()) }; shot("18-calendar")
        // Round 13: the one day card from a tap on the last workout's square.
        val lastDay = LocalDate.parse(model.workouts.value.last().day).dayOfMonth.toString()
        compose.onAllNodesWithText(lastDay).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick(); shot("18b-calendar-card")
        // The running look: the same Home with a workout going (tint, clock pill).
        go { model.stack.clear(); model.tab = Tab.Home }; shot("14-home-running")
        go { model.tab = Tab.Progress }; shot("15-progress-running")
        // Adding to the running workout: the Library with one exercise just added (band, + buttons, Added chip, toast), then an exercise's page.
        go { model.stack.clear(); model.tab = Tab.Exercises; model.addToRunning(names[3]) }; shot("04f-library-running")
        check(model.draft.holds(names[3])) { "the exercise was not added" }
        go { model.notice = null; model.open(Screen.ExerciseDetail(names[4])) }; shot("09b-exercise-running")
        // Round 13: the rest bar over the workout, the Undo pill on the workout and on Home, and a hold on a row of Progress's History card.
        // The test clock would run a 90 s rest and the pill's 8 s out on its own while the screen idles, so it is held and stepped by hand here.
        compose.mainClock.autoAdvance = false
        go { model.stack.clear(); model.open(Screen.Active); model.startRest(90) }; shot("11c-active-rest-bar")
        go { model.say("Set removed") {} }; shot("11d-active-undo-pill")
        go { model.startRest(0); model.undoExpired(); model.stack.clear(); model.tab = Tab.Home; model.say("Workout deleted") {} }; shot("12g-undo-pill-home")
        go { model.undoExpired(); model.tab = Tab.Progress; kotlinx.coroutines.runBlocking { model.tabLists.getValue(Tab.Progress).scrollToItem(0) } }; compose.waitForIdle()
        compose.mainClock.autoAdvance = true
        val pushes = compose.onAllNodesWithText("Push")
        pushes[pushes.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.width > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 800f && it.boundsInRoot.top >= 0f && it.boundsInRoot.top < 1200f }].performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        check(compose.onAllNodesWithText("18:00", substring = true).fetchSemanticsNodes().isNotEmpty()) { "no peek on Progress; holds=${model.holds.hold}" }
        shot("02c-progress-history-peek")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        scenario.close()
    }
    /** Round 16: every floating surface in the one material (the solid form shows here), Dark then Light: the same tone, hairline and shadow, nothing showing through. */
    @Test fun floatingMaterial() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        fun quietly(name: String, step: () -> Unit) { try { step() } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("$name.failed.txt").writeText(e.toString()) } }
        for (mode in listOf("dark", "light")) {
            look(mode)
            val p = "f-$mode"
            go { model.stack.clear(); model.tab = Tab.Home; model.say("Workout deleted") {} }; shot("$p-1-undo-pill-home")
            go { model.undoExpired(); model.saidSaved("Push", model.workouts.value.last().date) }; shot("$p-2-toast")
            go { model.notice = null; model.stack.clear(); model.open(Screen.Calendar()) }; shot("$p-3-calendar")
            val lastDay = LocalDate.parse(model.workouts.value.last().day).dayOfMonth.toString()
            quietly("$p-4-day-card") { compose.onAllNodesWithText(lastDay).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick(); shot("$p-4-day-card") }
            go { model.stack.clear(); model.tab = Tab.Home; model.openHistory() }
            quietly("$p-5-hold-menu") { compose.onAllNodesWithText("Push", substring = true).onFirst().performTouchInput { down(center) }; shot("$p-5-hold-menu"); compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }; go { model.holds.hold = null } }
            quietly("$p-6-whats-new") { go { model.javaClass.getDeclaredMethod("setWhatsNew", clean.core.Release::class.java).apply { isAccessible = true }.invoke(model, clean.core.Release("1.1.0", null, listOf(clean.core.Change("A new look", "Everything that floats is one material.")))) }; shot("$p-6-whats-new", dialogs = true); go { model.closeWhatsNew() } }
            quietly("$p-7-chart-card") {
                go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 2)) }; compose.waitForIdle()
                compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("latest", substring = true), useUnmergedTree = true).onFirst().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.7f, height * 0.5f)) }
                shot("$p-7-chart-card", dialogs = true)
            }
            quietly("$p-8-body-card") {
                go { model.stack.clear(); model.openBody() }; compose.waitForIdle()
                compose.onAllNodes(isRoot()).onFirst().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.4f)) }
                shot("$p-8-body-card", dialogs = true)
            }
            quietly("$p-9-weight-sheet") { go { model.stack.clear(); model.tab = Tab.Home; model.logWeight() }; shot("$p-9-weight-sheet", dialogs = true); go { model.stack.clear() } }
            go {
                model.stack.clear()
                model.putDraft(Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.take(3).map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
                model.open(Screen.Active)
            }
            quietly("$p-a-set-dialog") { compose.onAllNodesWithText("W").onFirst().performClick(); shot("$p-a-set-dialog") ; compose.onAllNodes(isRoot()).onFirst().performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, 5f)) } }
            quietly("$p-b-picker") { repeat(4) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }; compose.onNodeWithText("Add exercise").performClick(); shot("$p-b-picker", dialogs = true) }
            go { model.stack.clear(); model.open(Screen.Active) }
            compose.mainClock.autoAdvance = false
            go { model.startRest(90) }; shot("$p-c-rest-bar")
            go { model.say("Set removed") {} }; shot("$p-d-active-undo-pill")
            go { model.startRest(0); model.undoExpired() }
            compose.mainClock.autoAdvance = true
            go { model.cancelWorkout(); model.stack.clear(); model.tab = Tab.Home }
            quietly("$p-e-dialog") {
                go { model.settingsPage = PAGE_BACKUP; model.open(Screen.Settings) }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
                repeat(8) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
                compose.onNodeWithText("Delete everything…").performClick(); shot("$p-e-dialog", dialogs = true)
            }
            go { model.stack.clear() }; compose.waitForIdle()
        }
        scenario.close()
    }

    /** Round 14: every Settings page from the top, scrolled a screen at a time, and the longest ones in Light and at the largest text. */
    @Test fun settingsPages() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        look("dark")
        fun pages(prefix: String, page: String?, scrolls: Int) {
            go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
            go { model.settingsPage = page; model.open(Screen.Settings) }; shot("$prefix-0")
            for (i in 1..scrolls) {
                compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.2f, durationMillis = 3_000) }
                shot("$prefix-$i")
            }
        }
        pages("s-main", null, 0)
        pages("s-workout", PAGE_WORKOUT, 6)
        pages("s-appearance", PAGE_LOOK, 3)
        pages("s-data", PAGE_BACKUP, 5)
        pages("s-about", PAGE_ABOUT, 3)
        look("light")
        pages("s-light-appearance", PAGE_LOOK, 1)
        pages("s-light-workout", PAGE_WORKOUT, 1)
        pages("s-light-data", PAGE_BACKUP, 1)
        look("dark")
        go { model.setOption("textSize", "Largest") }
        repeat(200) { if (model.textSize.value == "Largest") return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.textSize.value == "Largest") { "text size did not change" }
        pages("s-large-workout", PAGE_WORKOUT, 2)
        pages("s-large-appearance", PAGE_LOOK, 1)
        pages("s-large-data", PAGE_BACKUP, 2)
        scenario.close()
    }
}
