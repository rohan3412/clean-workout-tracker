package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.Kv
import clean.app.data.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The summary after a workout lists its exercises and their sets, as the workout's page does. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h3000dp-mdpi")
class SummaryExercisesTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `the summary shows every exercise with its sets`() {
        val date = "2026-10-01 18:00:00"
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
            val rows = listOf(
                "\"1\";\"$date\";\"Push\";\"3600\";\"Chest Fly (Machine)\";\"1\";\"70.0\";\"8\";\"\";\"\";\"\";\"\";\"\"",
                "\"1\";\"$date\";\"Push\";\"3600\";\"Chest Fly (Machine)\";\"2\";\"70.0\";\"7\";\"\";\"\";\"\";\"\";\"\"",
                "\"1\";\"$date\";\"Push\";\"3600\";\"Upright Row (Cable)\";\"1\";\"25.0\";\"6\";\"\";\"\";\"\";\"\";\"\"",
            )
            repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded }
        compose.runOnIdle { model.open(Screen.Summary(date)) }
        compose.waitForIdle()
        fun shown(text: String, exact: Boolean = true) = compose.onAllNodes(hasText(text, substring = !exact), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue("Exercises heading", shown("Exercises"))
        assertTrue("first exercise", shown("Chest Fly (Machine)"))
        assertTrue("second exercise", shown("Upright Row (Cable)"))
        assertTrue("a set", shown("× 7", exact = false))
        scenario.close()
    }

    @Test fun `History's card shows each exercise's best estimated 1RM by the chosen formula`() {
        val date = "2026-10-02 18:00:00"
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
            val rows = listOf(
                "\"1\";\"$date\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"W\";\"60.0\";\"5\";\"\";\"\";\"\";\"\";\"\"",
                "\"1\";\"$date\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"1\";\"100.0\";\"5\";\"\";\"\";\"\";\"\";\"\"",
            )
            repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded }
        compose.runOnIdle { model.open(Screen.History) }
        compose.waitForIdle()
        fun shown(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue("1RM head", shown("1RM"))
        // Hybrid under 10 reps is Brzycki: 100 × 36 / 32.
        assertTrue("estimate", shown("112.5"))
        scenario.close()
    }
}
