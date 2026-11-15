package clean.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Repo
import clean.app.data.RowEntity
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
 * (customization/uiux-pass.md). It checks nothing; [PRESET] picks the look (`-Dpreset=linen`, default the app's own).
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
        System.getProperty("preset")?.let { repo.dao.put(Kv("theme.preset", it)) }
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
        val out = File("build/screens/${System.getProperty("preset") ?: "default"}").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)

    @Test fun `main screens`() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        shot("01-home")
        go { model.tab = Tab.Progress }; shot("02-progress")
        go { model.tab = Tab.Workout }; shot("03-workout")
        go { model.tab = Tab.Exercises }; shot("04-exercises")
        go { model.tab = Tab.Profile }; shot("05-profile")
        go { model.tab = Tab.Home; model.openHistory() }; shot("06-history")
        go { model.stack.clear(); model.openBody() }; shot("07-body")
        go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.last().date)) }; shot("08-workout-detail")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0])) }; shot("09-exercise-info")
        go { model.stack.clear(); model.open(Screen.ExerciseDetail(names[0], tab = 2)) }; shot("10-exercise-charts")
        go {
            model.stack.clear()
            model.putDraft(Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.take(3).map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
            model.open(Screen.Active)
        }
        shot("11-active")
        go { model.stack.clear(); model.open(Screen.Settings) }; shot("12-settings")
        go { model.stack.clear(); model.open(Screen.Trophies) }; shot("13-trophies")
        scenario.close()
    }
}
