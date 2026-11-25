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
 * Round 22: every main screen and the important subpages in Graphite Light, saved as round22-graphite-light-*.png, for the contrast audit. Checks nothing itself.
 * (customization/uiux-pass.md). It checks nothing. The look is the default of a new install (Dark, pure black off, theme Graphite); a Light Home and an Ember Home are shot once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round22Test {
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

    private fun s(name: String, dialogs: Boolean = false, step: () -> Unit = {}) {
        try { step(); shot("round22-graphite-light-$name", dialogs) } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round22-$name.failed.txt").writeText(e.toString()) }
    }

    @Test fun `graphite light tour`() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        look("light"); theme("Graphite")
        s("01-home")
        s("02-home-tiles") { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
        s("03-progress") { go { model.tab = Tab.Progress } }
        s("04-progress-rows") { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
        s("05-workout-sheet", dialogs = true) { go { model.tab = Tab.Home; model.openWorkoutSheet() } }
        s("06-library") { go { model.workoutSheet = false; model.tab = Tab.Exercises } }
        s("07-library-search") { compose.onNodeWithContentDescription("Search exercises").performClick(); compose.waitForIdle(); compose.onNode(hasSetTextAction()).performTextInput("incline db pres"); typed() }
        s("07b-library-search-empty") { compose.onNode(hasSetTextAction()).performTextClearance(); compose.onNode(hasSetTextAction()).performTextInput("rwo"); typed() }
        s("08-profile") { compose.onNode(hasSetTextAction()).performTextClearance(); go { model.tab = Tab.Profile } }
        s("09-profile-lower") { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
        s("10-weigh-in", dialogs = true) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeDown() }; go { model.logWeight() } }
        s("11-history") { go { model.stack.clear(); model.tab = Tab.Home; model.openHistory() } }
        s("12-history-hold") { compose.onAllNodesWithText("Push", substring = true).onFirst().performTouchInput { down(center) } }
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }; go { model.holds.hold = null }
        s("13-body") { go { model.stack.clear(); model.openBody() } }
        s("14-weight-page") { go { model.stack.clear(); model.open(Screen.MeasurementDetail("weight.csv")) } }
        s("15-workout-detail") { go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.last().date)) } }
        s("16-exercise-info") { go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0])) } }
        s("17-exercise-charts") { go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 2)) } }
        s("18-exercise-prs") { go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 3)) } }
        s("19-exercise-history") { go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 1)) } }
        s("20-summary") { go { model.stack.clear(); model.open(Screen.Summary(model.workouts.value.last().date)) } }
        s("21-muscle") { go { model.stack.clear(); model.open(Screen.MuscleDetail(clean.core.Muscle.entries.first())) } }
        s("22-template") { go { model.stack.clear(); model.open(Screen.TemplateStats(model.templates.value.first().id)) } }
        s("23-trophies") { go { model.stack.clear(); model.open(Screen.Trophies) } }
        s("24-trends") { go { model.stack.clear(); model.open(Screen.Trends) } }
        s("25-personal-bests") { go { model.stack.clear(); model.open(Screen.PersonalBests) } }
        s("26-calendar") { go { model.stack.clear(); model.open(Screen.Calendar()) } }
        s("27-calendar-card") {
            val lastDay = LocalDate.parse(model.workouts.value.last().day).dayOfMonth.toString()
            compose.onAllNodesWithText(lastDay).let { it[it.fetchSemanticsNodes().lastIndex] }.performClick()
        }
        s("28-year-review") { go { model.stack.clear(); model.open(Screen.YearReview(LocalDate.now().year)) }; repeat(4) { Thread.sleep(300); compose.waitForIdle(); compose.mainClock.advanceTimeBy(300) } }
        s("29-settings") { go { model.stack.clear(); model.open(Screen.Settings) } }
        for ((tag, page) in listOf("30-settings-appearance" to PAGE_LOOK, "31-settings-workout" to PAGE_WORKOUT, "32-settings-data" to PAGE_BACKUP, "33-settings-about" to PAGE_ABOUT)) {
            go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
            s(tag) { go { model.settingsPage = page; model.open(Screen.Settings) } }
        }
        go { model.stack.clear() }; compose.waitForIdle()
        s("34-saved-toast") { go { model.tab = Tab.Home; model.saidSaved("Push", model.workouts.value.last().date) } }
        go { model.notice = null }
        s("35-active") {
            go {
                model.stack.clear()
                model.putDraft(Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.take(3).map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
                model.open(Screen.Active)
            }
        }
        s("36-active-pad") {
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("edit box") { if (it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) it.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label == "Edit" else false }).onFirst().performClick()
        }
        go { model.stack.clear(); model.open(Screen.Active) }; compose.waitForIdle()
        s("37-active-set-dialog") { compose.onAllNodesWithText("W").onFirst().performClick() }
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, 5f)) }
        compose.mainClock.autoAdvance = false
        s("38-active-rest-bar") { go { model.stack.clear(); model.open(Screen.Active); model.startRest(90) } }
        s("39-active-undo-pill") { go { model.say("Set removed") {} } }
        go { model.startRest(0); model.undoExpired() }
        compose.mainClock.autoAdvance = true
        s("40-home-running") { go { model.stack.clear(); model.tab = Tab.Home } }
        s("41-library-running") { go { model.tab = Tab.Exercises; model.addToRunning(names[3]) } }
        scenario.close()
    }
}
