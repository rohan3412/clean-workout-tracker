package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The workout note lives in Strong's existing "Workout Notes" column (docs/data-format.md). Strong's importer would still
 * import every exercise of a file that carries one: the note sits in its own column of a format Strong itself writes, every
 * field is quoted with quotes doubled, so a comma, a quote or a newline in the note cannot move a field or split a row, and
 * the header and the column count of every row are exactly those of a Strong export. Strong has no use for the field and at
 * worst drops it; the exercises, sets, weights and reps in the other columns are the ones it reads either way.
 */
class WorkoutNotesTest {
    private val note = "Felt strong, \"PR day\"\nslept badly; shoulder tight"

    private val blocks = listOf(
        NewBlock("Bench Press (Barbell)", listOf(NewNote("Pause on chest"), NewSet(SetType.WARMUP, 20.0, 10), NewSet(SetType.NORMAL, 60.0, 8), NewSet(SetType.FAILURE, 65.0, 6))),
        NewBlock("Pull Up", listOf(NewSet(SetType.NORMAL, 0.0, 6), NewSet(SetType.DROP, 0.0, 4))),
        NewBlock("Plank", listOf(NewSet(SetType.NORMAL, seconds = 60.0))),
    )

    private val rows = rowsFor(1, "2026-10-16 18:00:00", "Push", 3600, blocks, note)

    /** What each exercise is, set by set, without the workout note. */
    private fun shape(workout: Workout) = workout.blocks.map { b -> b.exercise to b.items.map { it.row.copy(workoutNotes = "") } }

    @Test
    fun `a note with a comma, quotes and a newline survives the Strong CSV`() {
        val back = WorkoutCsv.parse(WorkoutCsv.serialise(rows))
        assertEquals(rows, back)
        val workout = buildWorkouts(back).single()
        assertEquals(note, workout.notes)
        val plain = buildWorkouts(rowsFor(1, "2026-10-16 18:00:00", "Push", 3600, blocks)).single()
        assertEquals(shape(plain), shape(workout))
        assertEquals(listOf("Bench Press (Barbell)", "Pull Up", "Plank"), workout.blocks.map { it.exercise })
        assertEquals(listOf("W", "1", "F"), workout.blocks[0].sets.map { it.row.setOrder })
        assertEquals(listOf(20.0, 60.0, 65.0), workout.blocks[0].sets.map { it.weight })
        assertEquals(listOf(10, 8, 6), workout.blocks[0].sets.map { it.reps })
        assertEquals(listOf(SetType.NORMAL, SetType.DROP), workout.blocks[1].sets.map { it.type })
        assertEquals("Pause on chest", workout.blocks[0].notes.single().text)
    }

    @Test
    fun `with the note column blanked the same exercises and sets are read`() {
        val blanked = WorkoutCsv.parse(WorkoutCsv.serialise(rows.map { it.copy(workoutNotes = "") }))
        val workout = buildWorkouts(blanked).single()
        assertEquals("", workout.notes)
        assertEquals(shape(buildWorkouts(rows).single()), shape(workout))
    }

    @Test
    fun `the file has Strong's header and the same column count on every row`() {
        val text = WorkoutCsv.serialise(rows).toString(Charsets.UTF_8)
        assertTrue(text.startsWith("\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\"\n"))
        // Each record is 13 quoted fields; the newline inside the note is inside quotes, so it is not a record end.
        val records = Regex("(\"(?:[^\"]|\"\")*\")(;|\n)").findAll(text).toList()
        var fields = 0
        var count = 0
        for (m in records) {
            fields++
            if (m.groupValues[2] == "\n") { assertEquals(13, fields); fields = 0; count++ }
        }
        assertEquals(rows.size + 1, count)
        // Every row of the workout carries the note, as the other workout-level columns do.
        assertTrue(rows.all { it.workoutNotes == note })
    }

    @Test
    fun `no note leaves every row as before`() {
        assertEquals(rowsFor(1, "d", "w", 60, blocks), rowsFor(1, "d", "w", 60, blocks, ""))
        assertTrue(rowsFor(1, "d", "w", 60, blocks).all { it.workoutNotes.isEmpty() })
    }
}
