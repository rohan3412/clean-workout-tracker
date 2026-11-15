package clean.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import clean.app.data.Repo
import clean.core.WorkoutCsv
import clean.core.WorkoutRow
import clean.core.buildWorkouts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** "Read as left and right" on a finished block and back, through the database, on a hand-written export: the file must come back byte for byte. */
@RunWith(AndroidJUnit4::class)
class MarkSidesOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = Repo(Room.inMemoryDatabaseBuilder(context, CleanDb::class.java).build())

    private fun row(number: String, date: String, exercise: String, order: String, weight: String = "", notes: String = "") =
        WorkoutRow(number, date, "Pull", "3600", exercise, order, weight, if (weight.isEmpty()) "" else "8", "", "", "", notes, "")

    private val bytes = WorkoutCsv.serialise(listOf(
        row("1", "2026-01-01 10:00:00", "One Arm Row", "1", "20.0"),
        row("1", "2026-01-01 10:00:00", "One Arm Row", "2", "20.0"),
        row("1", "2026-01-01 10:00:00", "Curl", "1", "10.0"),
        row("2", "2026-01-05 10:00:00", "One Arm Row", "1", "22.0"),
        row("2", "2026-01-05 10:00:00", "One Arm Row", "2", "22.0"),
    ))

    @Test
    fun markThenUnmarkGivesBackTheSameFile() = runBlocking {
        val id = repo.createProfile("A")
        repo.commit(id, repo.previewWorkouts(id, bytes))
        assertArrayEquals(bytes, repo.exportWorkouts(id))

        assertTrue(repo.markSides(id, "2026-01-05 10:00:00", "One Arm Row", 0, true))
        val marked = WorkoutCsv.parse(repo.exportWorkouts(id))
        assertEquals(6, marked.size)
        val workouts = buildWorkouts(marked)
        assertFalse(workouts[0].blocks[0].paired)
        assertTrue(workouts[1].blocks[0].paired)
        // Marking again, or taking off a mark that is not there, does nothing.
        assertFalse(repo.markSides(id, "2026-01-05 10:00:00", "One Arm Row", 0, true))
        assertFalse(repo.markSides(id, "2026-01-01 10:00:00", "One Arm Row", 0, false))

        assertTrue(repo.markSides(id, "2026-01-05 10:00:00", "One Arm Row", 0, false))
        assertArrayEquals(bytes, repo.exportWorkouts(id))
    }
}
