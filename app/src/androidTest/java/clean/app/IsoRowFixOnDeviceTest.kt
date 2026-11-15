package clean.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import clean.app.data.Repo
import clean.core.ISO_ROW
import clean.core.ISO_ROW_WIDE
import clean.core.NewBlock
import clean.core.NewNote
import clean.core.NewSet
import clean.core.OneRm
import clean.core.SIDES_NOTE
import clean.core.SetType
import clean.core.buildWorkouts
import clean.core.planIsoRowFix
import clean.core.recordsOf
import clean.core.sessionsOf
import clean.core.volumeOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Iso-Lateral Row history fix through its real Apply and Undo, on the user's own export (copied into files/original by hand,
 * see README.md, "On-device tests"; skipped when it is not there) and on two workouts standing in for what was logged on the phone after the export.
 * Nothing real is touched: a context whose database and files live in a throwaway folder is what the database and the backup
 * are opened with, so the app's own clean.db is never opened.
 */
@RunWith(AndroidJUnit4::class)
class IsoRowFixOnDeviceTest {
    private val real = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(real.cacheDir, "isofix-test-${System.nanoTime()}")

    /** The app's context with its database and files moved into [dir]. */
    private val sandbox = object : ContextWrapper(real) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = File(dir, "db/$name").also { it.parentFile?.mkdirs() }
        override fun getFilesDir(): File = File(dir, "files").also { it.mkdirs() }
        override fun getCacheDir(): File = File(dir, "cache").also { it.mkdirs() }
    }

    @Before fun open() { CleanDb.close() }

    @After fun close() {
        CleanDb.close()
        dir.deleteRecursively()
    }

    @Test
    fun applyThenUndoOnTheRealExport() = runBlocking {
        val file = File(real.filesDir, "original").listFiles()?.firstOrNull { it.name.endsWith(".csv") }
        assumeTrue("no export in files/original", file != null)
        val repo = Repo(CleanDb.open(sandbox))
        val id = repo.createProfile("Test")
        repo.commit(id, repo.previewWorkouts(id, file!!.readBytes()))

        // After the export: one workout already logged left and right (left alone), one logged plainly (split like the others).
        repo.saveWorkout(id, "2026-10-12 18:00:00", "Pull", 3600, listOf(
            NewBlock(ISO_ROW, listOf(NewSet(SetType.NORMAL, 50.0, 8), NewSet(SetType.NORMAL, 50.0, 7)), paired = true),
        ))
        repo.saveWorkout(id, "2026-10-14 18:00:00", "Pull", 3600, listOf(
            NewBlock(ISO_ROW, listOf(NewNote("Standing"), NewSet(SetType.FAILURE, 100.0, 8))),
        ))
        val before = repo.dao.rowsNow(id)
        val exportBefore = repo.exportWorkouts(id)
        val workoutsBefore = buildWorkouts(before.map { it.toRow() })

        val plan = planIsoRowFix(before.map { it.toRow() })
        assertEquals(11, plan.movedToWide)
        assertEquals(6, plan.convertedToSides)
        assertEquals(17 + 1, plan.setRowsSplit)
        assertEquals(1, plan.leftAlone)

        // The page's order: the backup is written first, and holds the database as it was.
        val zip = ByteArrayOutputStream().also { Backup.write(sandbox, it, false) }.toByteArray()
        val entries = ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry?.name }.toList() }
        assertTrue(entries.containsAll(listOf("clean-backup.json", "clean.db")))

        val saved = repo.applyIsoRowFix(id, plan)
        val after = repo.dao.rowsNow(id)
        val rows = after.map { it.toRow() }

        // 11 workouts with rows under Wide Grip; none of the export's rows under the old name before 9 Jun.
        assertEquals(11, rows.filter { it.exerciseName == ISO_ROW_WIDE }.map { it.date }.distinct().size)
        assertTrue(rows.none { it.exerciseName == ISO_ROW && it.date < "2026-06-09" })
        assertEquals(1, rows.filter { it.exerciseName == ISO_ROW_WIDE && it.date.startsWith("2026-07-27") }.map { it.date }.distinct().size)

        // 5 Oct, exactly: Wide Grip L/R 40×6, 35×6 and the Machine L/R 55×3, each block opening with the marker and keeping its note.
        val oct5 = rows.filter { it.date.startsWith("2026-10-05") && (it.exerciseName == ISO_ROW || it.exerciseName == ISO_ROW_WIDE) }
        val wide = oct5.filter { it.exerciseName == ISO_ROW_WIDE }
        assertEquals(SIDES_NOTE, wide.first().notes)
        assertTrue(wide.any { it.setOrder == "Note" && it.notes == "Wide grip, (make new exercise)" })
        assertEquals(listOf("40.0" to "6", "40.0" to "6", "35.0" to "6", "35.0" to "6"), wide.filter { it.setOrder != "Note" }.map { it.weightKg to it.reps })
        val neutral = oct5.filter { it.exerciseName == ISO_ROW }
        assertEquals(SIDES_NOTE, neutral.first().notes)
        assertTrue(neutral.any { it.setOrder == "Note" && it.notes == "Neutral" })
        assertEquals(listOf("55.0" to "3", "55.0" to "3"), neutral.filter { it.setOrder != "Note" }.map { it.weightKg to it.reps })

        // The workout already left and right is untouched, row for row; the plain one after the export is split at half weight.
        assertEquals(before.filter { it.date == "2026-10-12 18:00:00" }.map { it.toRow() }, rows.filter { it.date == "2026-10-12 18:00:00" })
        assertEquals(listOf("50.0" to "8", "50.0" to "8"), rows.filter { it.date == "2026-10-14 18:00:00" && it.setOrder != "Note" }.map { it.weightKg to it.reps })

        // Notes are kept: every note of the export is still there; only markers were added.
        val notes = { list: List<clean.core.WorkoutRow> -> list.filter { it.setOrder == "Note" && it.notes != SIDES_NOTE }.map { it.date + "|" + it.notes }.sorted() }
        assertEquals(notes(before.map { it.toRow() }), notes(rows))

        // Everything else is as it was.
        val other = { list: List<clean.core.WorkoutRow> -> list.filter { it.exerciseName != ISO_ROW && it.exerciseName != ISO_ROW_WIDE } }
        assertEquals(other(before.map { it.toRow() }), other(rows))

        // Figures follow from the rows. A left and right round's volume is the average of its sides (docs/left-right.md), so each
        // split working row now counts half of what it counted as one both-arms set; nothing else moves. Wide Grip has its own sessions and records.
        val workoutsAfter = buildWorkouts(rows)
        val split = before.map { it.toRow() }.filter {
            (it.exerciseName == ISO_ROW) && it.date >= "2026-09-15" && it.date != "2026-10-12 18:00:00" && it.setOrder !in setOf("Note", "W", "Rest Timer")
        }.sumOf { (it.weightKg.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0) }
        assertEquals(workoutsBefore.sumOf { volumeOf(it) } - split / 2, workoutsAfter.sumOf { volumeOf(it) }, 0.001)
        val wideSessions = sessionsOf(workoutsAfter, ISO_ROW_WIDE)
        assertEquals(11, wideSessions.size)
        assertNotNull(recordsOf(wideSessions, OneRm.DEFAULT).estimated1Rm)
        assertTrue(sessionsOf(workoutsAfter, ISO_ROW).filter { it.date >= "2026-09-15" }.all { it.paired })

        // The way to the fix is gone: it is marked done, and there is nothing left to plan.
        assertNotNull(repo.dao.get("isofix.done:$id"))
        assertTrue(planIsoRowFix(rows).isEmpty)

        // Undo: row for row (ids and workout numbers too) and byte for byte in the export, as before Apply.
        repo.undoIsoRowFix(id, saved)
        assertEquals(before, repo.dao.rowsNow(id))
        assertArrayEquals(exportBefore, repo.exportWorkouts(id))
        assertNull(repo.dao.get("isofix.done:$id"))
        assertTrue(!planIsoRowFix(repo.dao.rowsNow(id).map { it.toRow() }).isEmpty)
    }
}
