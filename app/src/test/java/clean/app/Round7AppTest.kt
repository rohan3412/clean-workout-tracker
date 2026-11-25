package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import clean.core.time.*
import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.core.STRONG_DATE_FORMAT
import clean.core.SetType
import clean.core.WorkoutRow
import java.io.File
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Round 7 in the app: the scheduled backup into a folder, workout tags kept out of the CSV and left out of the stats, and the notification's "Set done". */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round7AppTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun row(n: Int, day: LocalDate, exercise: String, order: String, kg: String, reps: String) =
        WorkoutRow(n.toString(), day.atTime(18, 0).format(STRONG_DATE_FORMAT), "Push", "3600", exercise, order, kg, reps, "", "", "", "", "")

    /** A profile with three workouts, the middle one the heaviest. */
    private fun seed(): Long = runBlocking {
        val repo = Repo(CleanDb.open(context))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        val rows = listOf(
            row(1, LocalDate.now().minusDays(10), "Bench Press (Barbell)", "1", "100.0", "5"),
            row(2, LocalDate.now().minusDays(7), "Bench Press (Barbell)", "1", "140.0", "5"),
            row(3, LocalDate.now().minusDays(4), "Bench Press (Barbell)", "1", "105.0", "5"),
        )
        repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        id
    }

    private fun model(): AppModel {
        lateinit var model: AppModel
        ActivityScenario.launch(MainActivity::class.java).onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(30_000) { model.allWorkouts.value.size == 3 }
        return model
    }

    /** Waits, in real time, for what goes through Room's own thread and back by the main looper. */
    private fun until(what: String, check: () -> Boolean) {
        repeat(200) { if (check()) return; Thread.sleep(50); compose.waitForIdle() }
        check(check()) { what }
    }

    @Test fun `a backup into a folder is a zip and only the newest five scheduled ones stay`() {
        runBlocking { Repo(CleanDb.open(context)).createProfile("Rohan") }
        val dir = File(context.cacheDir, "auto-backup-test").apply { deleteRecursively(); mkdirs() }
        val own = File(dir, "holiday.zip").apply { writeText("not mine") }
        val folder = DocumentFile.fromFile(dir)
        for (day in 1..7) AutoBackup.backupInto(context, folder, LocalDateTime.of(2026, 11, day, 8, 0))
        val names = dir.list()!!.sorted()
        assertEquals(6, names.size)
        assertTrue(own.exists())
        assertEquals((3..7).map { "Clean auto backup 2026-11-0$it 0800.zip" }, names.filter { it != "holiday.zip" })
        // Each is the manual backup's zip.
        assertTrue(Backup.isBackup(File(dir, "Clean auto backup 2026-11-07 0800.zip")))
        // The same minute twice writes once.
        AutoBackup.backupInto(context, folder, LocalDateTime.of(2026, 11, 7, 8, 0))
        assertEquals(6, dir.list()!!.size)
    }

    @Test fun `a tag is kept apart from the CSV and a tag left out drops its workout from the stats only`() {
        val id = seed()
        val model = model()
        val before = runBlocking { model.repo.exportWorkouts(id) }
        val heavy = model.allWorkouts.value[1].date
        model.setWorkoutTag(heavy, "  sick   day ")
        until("the tag did not arrive") { model.workoutTags.value[heavy] == "sick day" }
        assertEquals(listOf("sick day"), model.tagsInUse.value)
        // Not left out yet: the stats still count it.
        assertEquals(3, model.workouts.value.size)
        model.setTagLeftOut("sick day", true)
        until("the tag was not left out") { model.workouts.value.size == 2 }
        assertEquals(3, model.allWorkouts.value.size)
        assertFalse(model.workouts.value.any { it.date == heavy })
        // The heaviest session no longer sets the record the next one is judged against.
        val last = model.allWorkouts.value.last().date
        until("records were not worked out again") { model.prs.value[heavy] == null }
        assertTrue(model.prs.value.getValue(last).flatten().isNotEmpty())
        // The export is byte for byte what it was.
        assertEquals(before.toList(), runBlocking { model.repo.exportWorkouts(id) }.toList())
        model.setWorkoutTag(heavy, "")
        until("the tag did not go") { model.workoutTags.value.isEmpty() }
        assertEquals(3, model.workouts.value.size)
    }

    @Test fun `Set done ticks the next set as its check would and starts the rest`() {
        seed()
        val model = model()
        val bench = "Bench Press (Barbell)"
        val filled = DraftSet(SetType.NORMAL, "60", "8")
        val empty = DraftSet(SetType.NORMAL)
        compose.runOnIdle { model.putDraft(Draft("Push", System.currentTimeMillis(), listOf(DraftBlock(bench, listOf(filled, empty, empty))))) }
        assertEquals(null, model.restEnd)
        compose.runOnIdle { model.tickNextSet() }
        val first = model.draft!!.blocks.single().items.filterIsInstance<DraftSet>()
        assertTrue(first[0].done)
        assertEquals("60", first[0].weight)
        assertFalse(first[1].done)
        assertNotNull("the rest did not start", model.restEnd)
        assertTrue(first[0].rest > 0)
        // The next one has no set of that number last time, so it takes what the set above holds, as its grey hint would.
        compose.runOnIdle { model.tickNextSet() }
        val second = model.draft!!.blocks.single().items.filterIsInstance<DraftSet>()
        assertTrue(second[1].done)
        assertEquals("8", second[1].reps)
        assertFalse(second[2].done)
    }
}
