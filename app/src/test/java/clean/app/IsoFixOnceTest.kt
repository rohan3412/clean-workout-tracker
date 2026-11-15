package clean.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.core.ISO_ROW
import clean.core.ISO_ROW_WIDE
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Iso-Lateral Row history fix runs by itself once at start-up (no page any more), and never again once marked done. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class IsoFixOnceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
    private fun row(n: Int, date: String, order: String, weight: String = "", reps: String = "", note: String = "") =
        "\"$n\";\"$date\";\"Pull\";\"3600\";\"$ISO_ROW\";\"$order\";\"$weight\";\"$reps\";\"\";\"\";\"\";\"$note\";\"\""

    private fun names(repo: Repo, id: Long) = runBlocking { repo.dao.rowsNow(id).map { it.date.take(10) + " " + it.exerciseName } }.distinct()

    private fun launchWith(done: Boolean): Pair<Repo, Long> {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = runBlocking {
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            if (done) repo.dao.put(Kv("isofix.done:$id", "1"))
            val rows = listOf(
                row(1, "2026-05-13 10:00:00", "1", "40.0", "10"),
                row(2, "2026-06-09 10:00:00", "Note", note = "Neutral Grip"), row(2, "2026-06-09 10:00:00", "1", "40.0", "10"),
            )
            repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
            id
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded }
        return repo to id
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Condition still not satisfied after 10 s" }
            Thread.sleep(50)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test fun `the fix is applied once at start-up and marked done`() {
        val (repo, id) = launchWith(done = false)
        awaitTrue { runBlocking { repo.dao.get("isofix.done:$id") } != null }
        assertEquals(listOf("2026-05-13 $ISO_ROW_WIDE", "2026-06-09 $ISO_ROW"), names(repo, id))
        assertNotNull(runBlocking { repo.isoFixSaved(id) })
    }

    @Test fun `once marked done the history is never looked at again`() {
        val (repo, id) = launchWith(done = true)
        Thread.sleep(500)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf("2026-05-13 $ISO_ROW", "2026-06-09 $ISO_ROW"), names(repo, id))
    }
}
