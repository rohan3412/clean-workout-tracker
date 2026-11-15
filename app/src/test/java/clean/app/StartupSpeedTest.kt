package clean.app

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Timings of the real app on the JVM (Robolectric) over a made-up log of three years, four workouts a week: from launch until the
 * first page's figures are worked out and the first page has settled, and opening the Body page. (A workout written during the test does not come
 * back through Room's flows under Robolectric, so the work after a save is timed in core's `DataPathSpeedTest` instead.)
 * A phone is slower and starts cold, so these numbers are for comparing before and after a change (customization/speed-pass.md),
 * not for reading as phone times. One launch per run, in a fresh JVM, so it is a cold start like a phone's; run it several times
 * (tools/speed/startup.sh) and take the median.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class StartupSpeedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Deadlift (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)",
        "Bicep Curl (Dumbbell)", "Triceps Pushdown (Cable)", "Leg Press", "Seated Cable Row", "Lateral Raise (Dumbbell)",
        "Incline Bench Press (Dumbbell)", "Romanian Deadlift (Barbell)", "Leg Curl (Machine)", "Leg Extension (Machine)",
        "Pull Up", "Chest Fly (Machine)", "Hammer Curl (Dumbbell)", "Calf Raise (Machine)", "Face Pull (Cable)", "Hip Thrust (Barbell)",
    )

    private fun rows(): List<WorkoutRow> {
        val rows = ArrayList<WorkoutRow>()
        val first = LocalDate.now().minusWeeks(156)
        var n = 0
        for (week in 0 until 156) for (offset in listOf(0L, 1L, 3L, 4L)) {
            n++
            val date = first.plusWeeks(week.toLong()).plusDays(offset).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for (e in 0 until 6) {
                val exercise = names[(n * 3 + e * 7) % names.size]
                rows += WorkoutRow(n.toString(), date, "Day ${n % 4}", "3600", exercise, "W", "20.0", "10", "", "", "", "", "")
                for (s in 1..3) rows += WorkoutRow(n.toString(), date, "Day ${n % 4}", "3600", exercise, s.toString(), decimalText(40.0 + n / 8 * 1.25 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
            }
        }
        return rows
    }

    private fun median(values: List<Double>) = values.sorted()[values.size / 2]

    @Test fun `start-up and the Body page`() {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        runBlocking {
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.insertRows(rows().map { RowEntity.of(id, it) })
            repo.dao.insertMeasurements((0 until 150).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), (80.0 - it * 0.02).toString()) })
        }
        val loaded = ArrayList<Double>()
        val settled = ArrayList<Double>()
        val body = ArrayList<Double>()
        run {
            val start = System.nanoTime()
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            lateinit var model: AppModel
            scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
            // What the first page shows: the history, the week's standing, the PRs and the weekly totals (Robolectric now and then never
            // sets `loaded` itself, while every figure it waits for is there, so the figures are waited for directly).
            compose.waitUntil(30_000) {
                model.workouts.value.isNotEmpty() && model.consistency.value != null && model.prs.value.isNotEmpty() && model.weekTotals.value.isNotEmpty()
            }
            val atLoaded = System.nanoTime()
            compose.waitForIdle()
            val atIdle = System.nanoTime()

            val beforeBody = System.nanoTime()
            compose.runOnIdle { model.openBody() }
            compose.waitUntil(30_000) { model.muscleSetCounts.value.isNotEmpty() && model.muscleFigures.value != null }
            compose.waitForIdle()
            val atBody = System.nanoTime()
            compose.runOnIdle { model.back() }
            compose.waitForIdle()

            scenario.close()

            run {
                loaded += (atLoaded - start) / 1e6
                settled += (atIdle - start) / 1e6
                body += (atBody - beforeBody) / 1e6
            }
        }
        println("App on the JVM, ${rows().size} rows, one cold launch:")
        println("  %-38s %8.1f ms".format("launch to first page's figures", median(loaded)))
        println("  %-38s %8.1f ms".format("launch to first page settled", median(settled)))
        println("  %-38s %8.1f ms".format("open Body page to settled", median(body)))
    }
}
