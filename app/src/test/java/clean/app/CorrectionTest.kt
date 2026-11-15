package clean.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.core.PrKind
import kotlinx.coroutines.runBlocking
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

/**
 * Correcting a finished workout on its own page (`AppModel.correction`, `Screen.Correct`), on the real app: no workout in progress
 * is made, the start and end can be changed, Save replaces the workout and its PRs follow, Discard leaves it as it was, and a
 * correction left in the workout in progress by an older version moves to the Correct page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class CorrectionTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val first = "2026-09-24 18:00:00"
    private val date = "2026-10-01 18:00:00"

    private fun open(storedDraft: Draft? = null): Pair<ActivityScenario<MainActivity>, AppModel> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
            val rows = listOf(
                "\"1\";\"$first\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"1\";\"80.0\";\"5\";\"\";\"\";\"\";\"\";\"\"",
                "\"2\";\"$date\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"1\";\"60.0\";\"8\";\"\";\"\";\"\";\"\";\"\"",
                "\"2\";\"$date\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"2\";\"60.0\";\"8\";\"\";\"\";\"\";\"\";\"\"",
            )
            repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
            storedDraft?.let { repo.dao.put(Kv("draft:$id", it.toJson())) }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded && model.workouts.value.size == 2 }
        return scenario to model
    }

    /** Waits in real time, letting the main thread run, for what Room's own threads hand back after a write. */
    private fun awaitTrue(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Condition still not satisfied after 10 s" }
            Thread.sleep(50)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    private fun AppModel.workout() = workouts.value.first { it.date == date || it.name == "Push again" }

    /** The correction with its first set's weight typed as [weight]. */
    private fun Draft.withFirstWeight(weight: String) = copy(blocks = blocks.mapIndexed { i, b ->
        if (i != 0) b else b.copy(items = b.items.toMutableList().also { items -> items[0] = (items[0] as DraftSet).copy(weight = weight) })
    })

    @Test fun `editing opens the Correct page and makes no workout in progress`() {
        val (scenario, model) = open()
        compose.runOnIdle { model.editWorkout(model.workout()) }
        compose.waitForIdle()
        assertNull(model.draft)
        assertNotNull(model.correction)
        assertEquals(Screen.Correct, model.stack.last())
        // No set is ticked on the Correct page; every one with values counts.
        assertTrue(model.correction!!.blocks.flatMap { it.items }.filterIsInstance<DraftSet>().none { it.done })
        assertFalse(model.correctionChanged)
        scenario.close()
    }

    @Test fun `save replaces the workout in place with new times and sets, and its PRs follow`() {
        val (scenario, model) = open()
        // 60 x 8 does not beat 80 x 5 under Hybrid; 80 x 8 does.
        assertFalse(PrKind.ONE_RM in model.prs.value[date].orEmpty().flatten())
        compose.runOnIdle {
            model.editWorkout(model.workout())
            val c = model.correction!!
            model.putCorrection(c.withFirstWeight("80").copy(name = "Push again", pastDate = "2026-10-01 17:30:00", pastSeconds = 2700))
            assertTrue(model.correctionChanged)
            assertTrue(model.saveCorrection())
        }
        awaitTrue { model.workouts.value.any { it.name == "Push again" } }
        val saved = model.workouts.value.single { it.name == "Push again" }
        assertEquals("2026-10-01 17:30:00", saved.date)
        assertEquals(2700L, saved.durationSeconds)
        assertEquals(listOf(80.0, 60.0), saved.blocks.single().sets.map { it.weight })
        assertEquals(2, model.workouts.value.size)
        awaitTrue { PrKind.ONE_RM in model.prs.value[saved.date].orEmpty().flatten() }
        assertNull(model.correction)
        assertNull(model.draft)
        scenario.close()
    }

    @Test fun `the end must be after the start, and discarding leaves the workout as it was`() {
        val (scenario, model) = open()
        val before = model.workout()
        compose.runOnIdle {
            model.editWorkout(before)
            model.putCorrection(model.correction!!.withFirstWeight("100").copy(pastSeconds = 0))
            assertFalse(model.saveCorrection())
            model.putCorrection(model.correction!!.copy(pastSeconds = -60))
            assertFalse(model.saveCorrection())
            model.discardCorrection()
        }
        compose.waitForIdle()
        assertNull(model.correction)
        assertEquals(before, model.workout())
        scenario.close()
    }

    @Test fun `a workout in progress is left alone while another is corrected`() {
        val (scenario, model) = open()
        compose.runOnIdle {
            model.startWorkout()
            model.addExercise("Squat (Barbell)")
            model.editWorkout(model.workout())
            model.putCorrection(model.correction!!.withFirstWeight("62.5"))
            assertTrue(model.saveCorrection())
        }
        awaitTrue { model.workout().blocks.single().sets.first().weight == 62.5 }
        assertEquals(listOf("Squat (Barbell)"), model.draft!!.blocks.map { it.exercise })
        scenario.close()
    }

    @Test fun `a correction an older version left as the workout in progress moves to the Correct page`() {
        val old = Draft("Push", 0L, listOf(DraftBlock("Bench Press (Barbell)", listOf(DraftSet(weight = "60", reps = "8", done = true)))), pastDate = date, pastSeconds = 3600, editOf = date)
        val (scenario, model) = open(old)
        compose.waitUntil(10_000) { model.correction != null }
        assertNull(model.draft)
        assertEquals(date, model.correction!!.editOf)
        assertTrue(model.correction!!.blocks.flatMap { it.items }.filterIsInstance<DraftSet>().none { it.done })
        scenario.close()
    }
}
