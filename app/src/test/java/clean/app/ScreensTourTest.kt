package clean.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
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
import java.time.LocalDate

/**
 * A picture of each main screen of the real app over a made-up log, saved under build/screens/ to be looked at in a design review
 * (customization/uiux-pass.md). It checks nothing. The look is the default of a new install (Dark, pure black off, Blue); a Light Home is shot once.
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
        repo.dao.put(Kv("goal.weight:$id", "78.0|82.0|${LocalDate.now().minusWeeks(20)}"))
        repo.dao.insertMeasurements((0 until 4).flatMap { k ->
            val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
            listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
        })
        repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
    }

    private lateinit var model: AppModel
    private var window: android.view.View? = null

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
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

    @Test fun `main screens`() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        shot("01-home")
        // The same Home in Light, then back to Dark.
        look("light"); shot("01b-home-light")
        look("dark")
        go { model.tab = Tab.Progress }; shot("02-progress")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }; shot("02b-progress-rows")
        go { model.tab = Tab.Home; model.openWorkoutSheet() }; shot("03-workout-sheet")
        go { model.workoutSheet = false; model.tab = Tab.Exercises }; shot("04-library")
        // Recent expanded, then back; then two searches.
        compose.onNodeWithText("Show all", substring = true).performClick(); shot("04c-library-recent-all")
        compose.onNodeWithText("Show fewer").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("incline db pres"); typed(); shot("04b-library-search")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("pull down"); typed(); shot("04d-library-search-done")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("rwo"); typed(); shot("04e-library-nothing")
        compose.onNode(hasSetTextAction()).performTextClearance()
        go { model.tab = Tab.Home; model.open(Screen.Profile) }; shot("05-profile")
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
        go { model.settingsPage = PAGE_APPEARANCE; model.open(Screen.Settings) }; shot("12b-settings-appearance")
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_WORKOUT; model.open(Screen.Settings) }; shot("12c-settings-workout")
        // The toast that ends a saved workout, and the slide that guards Delete everything (B8, B7).
        go { model.stack.clear(); model.tab = Tab.Home; model.saidSaved("Push", model.workouts.value.last().date) }; shot("12d-saved-toast")
        go { model.notice = null }
        go { model.stack.clear() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        go { model.settingsPage = PAGE_DATA; model.open(Screen.Settings) }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_000)
        repeat(8) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() } }
        compose.onNodeWithText("Delete everything…").performClick()
        repeat(200) { if (compose.onAllNodesWithText("Slide to delete everything").fetchSemanticsNodes().isEmpty()) { Thread.sleep(50); compose.waitForIdle() } }
        shot("12e-slide-to-confirm")
        go { model.stack.clear(); model.open(Screen.Trophies) }; shot("13-trophies")
        go { model.stack.clear(); model.open(Screen.Trends) }; shot("16-trends")
        go { model.stack.clear(); model.open(Screen.PersonalBests) }; shot("17-personal-bests")
        go { model.stack.clear(); model.open(Screen.Calendar()) }; shot("18-calendar")
        // The running look: the same Home with a workout going (tint, clock pill).
        go { model.stack.clear(); model.tab = Tab.Home }; shot("14-home-running")
        go { model.tab = Tab.Progress }; shot("15-progress-running")
        scenario.close()
    }
}
