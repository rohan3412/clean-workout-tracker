package clean.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.TemplateEntity
import clean.core.NewBlock
import clean.core.NewSet
import clean.core.SetType
import clean.core.WorkoutCsv
import clean.core.WorkoutRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renaming or merging an exercise and taking it back, through the database, on a small hand-written export (no personal data):
 * the undo must give back the same file, byte for byte, and everything keyed by the name must follow and come back.
 */
@RunWith(AndroidJUnit4::class)
class RenameOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = Repo(Room.inMemoryDatabaseBuilder(context, CleanDb::class.java).build())

    private fun row(number: String, date: String, exercise: String, order: String, weight: String = "", notes: String = "") =
        WorkoutRow(number, date, "Push ", "3600", exercise, order, weight, if (weight.isEmpty()) "" else "8", "", "", "", notes, "")

    // A name with a trailing space and one that differs only by case sit beside the exercise that is renamed.
    private val bytes = WorkoutCsv.serialise(listOf(
        row("1", "2026-01-01 10:00:00", "Bench Press (Barbell)", "1", "60.0"),
        row("1", "2026-01-01 10:00:00", "Bench Press (Barbell)", "2", "62.5"),
        row("1", "2026-01-01 10:00:00", "Bench Press (Barbell) ", "Note", notes = "Wide grip "),
        row("1", "2026-01-01 10:00:00", "bench press (barbell)", "1", "40.0"),
        row("2", "2026-01-05 10:00:00", "Bench Press (Barbell)", "1", "65.0"),
        row("2", "2026-01-05 10:00:00", "Squat (Barbell)", "1", "100.0"),
    ))

    private suspend fun profileWithTheFile(): Long {
        val id = repo.createProfile("A")
        repo.commit(id, repo.previewWorkouts(id, bytes))
        assertArrayEquals(bytes, repo.exportWorkouts(id))
        return id
    }

    @Test
    fun renameThenUndoGivesBackTheSameFile() = runBlocking {
        val id = profileWithTheFile()
        val from = "Bench Press (Barbell)"
        val blocks = """[{"exercise":"$from","sets":["","W"]},{"exercise":"Squat (Barbell)","sets":[""]}]"""
        repo.dao.put(TemplateEntity(profileId = id, name = "Push", blocks = blocks))
        repo.dao.put(Kv("prog:$id:$from", "8-12@2.5"))
        repo.dao.put(ExerciseSetting(id, from, favourite = true, note = "Seat 4"))

        val entry = repo.renameExercise(id, from, "Bench Press", merged = false, automatic = null, draft = false, at = "2026-10-12 10:00:00")

        // Only the rows named exactly so were renamed.
        val names = WorkoutCsv.parse(repo.exportWorkouts(id)).map { it.exerciseName }
        assertEquals(listOf("Bench Press", "Bench Press", "Bench Press (Barbell) ", "bench press (barbell)", "Bench Press", "Squat (Barbell)"), names)
        assertEquals(3, entry.rows.size)
        assertEquals(2, entry.workouts)
        // What was keyed by the name followed it.
        assertNull(repo.dao.get("prog:$id:$from"))
        assertEquals("8-12@2.5", repo.dao.get("prog:$id:Bench Press"))
        val moved = repo.dao.settingsNow(id).single()
        assertEquals("Bench Press", moved.name)
        assertTrue(moved.favourite)
        assertEquals("Seat 4", moved.note)
        assertTrue(repo.dao.templatesNow(id).single().blocks.contains("\"Bench Press\""))
        assertEquals(1, repo.renameLog(id).size)

        // A set logged under the new name afterwards stays there when the rename is undone.
        repo.saveWorkout(id, "2026-03-01 10:00:00", "Push ", 600, listOf(NewBlock("Bench Press", listOf(NewSet(SetType.NORMAL, 70.0, 5)))))
        assertEquals(3, repo.undoRename(id, entry))
        val out = repo.exportWorkouts(id)
        assertArrayEquals(bytes, out.copyOf(bytes.size))
        assertTrue(String(out.copyOfRange(bytes.size, out.size)).contains("\"Bench Press\";"))
        assertNull(repo.dao.get("prog:$id:Bench Press"))
        assertEquals("8-12@2.5", repo.dao.get("prog:$id:$from"))
        assertEquals(listOf(from), repo.dao.settingsNow(id).map { it.name })
        assertEquals(blocks, repo.dao.templatesNow(id).single().blocks)
        assertTrue(repo.renameLog(id).isEmpty())
    }

    @Test
    fun mergeThenUndoGivesBackTheSameFile() = runBlocking {
        val id = profileWithTheFile()
        val into = "Bench Press (Barbell)"
        repo.dao.put(ExerciseSetting(id, "bench press (barbell)", favourite = true))
        repo.dao.put(ExerciseSetting(id, into, note = "Keep"))

        val entry = repo.renameExercise(id, "bench press (barbell)", into, merged = true, automatic = null, draft = false, at = "2026-10-12 10:00:00")
        assertEquals(4, WorkoutCsv.parse(repo.exportWorkouts(id)).count { it.exerciseName == into })
        // The target keeps its own settings; the source's are only in the log.
        assertEquals(listOf(into), repo.dao.settingsNow(id).map { it.name })
        assertEquals("Keep", repo.dao.settingsNow(id).single().note)
        assertNotNull(entry.settingFrom)

        repo.undoRename(id, entry)
        assertArrayEquals(bytes, repo.exportWorkouts(id))
        assertEquals(setOf("bench press (barbell)", into), repo.dao.settingsNow(id).map { it.name }.toSet())
        assertTrue(repo.dao.settingsNow(id).first { it.name == "bench press (barbell)" }.favourite)
        assertEquals("Keep", repo.dao.settingsNow(id).first { it.name == into }.note)
    }

    @Test
    fun aDeletedWorkoutComesBackAsItWas() = runBlocking {
        val id = profileWithTheFile()
        val removed = repo.deleteWorkout(id, "2026-01-01 10:00:00")
        assertEquals(4, removed.size)
        assertTrue(repo.restoreWorkout(id, removed))
        assertArrayEquals(bytes, repo.exportWorkouts(id))
    }
}
