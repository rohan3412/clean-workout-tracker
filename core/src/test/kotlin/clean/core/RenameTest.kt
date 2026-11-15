package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RenameTest {
    private fun row(date: String, exercise: String, order: String, weight: String = "", notes: String = "") =
        WorkoutRow("1", date, "Push ", "3600", exercise, order, weight, if (weight.isEmpty()) "" else "8", "", "", "", notes, "")

    // Hand-written: a name with a trailing space, one that differs only by case, and a plain one.
    private val rows = listOf(
        IdRow(1, row("2026-01-01 10:00:00", "Bench Press (Barbell)", "1", "60.0")),
        IdRow(2, row("2026-01-01 10:00:00", "Bench Press (Barbell)", "2", "62.5")),
        IdRow(3, row("2026-01-01 10:00:00", "Bench Press (Barbell) ", "Note", notes = "Wide grip ")),
        IdRow(4, row("2026-01-01 10:00:00", "bench press (barbell)", "1", "40.0")),
        IdRow(5, row("2026-01-03 10:00:00", "Squat (Barbell)", "1", "100.0")),
        IdRow(6, row("2026-01-05 10:00:00", "Bench Press (Barbell)", "1", "65.0")),
    )

    private fun csv(list: List<IdRow>) = WorkoutCsv.serialise(list.map { it.row })

    @Test
    fun `a rename changes the rows named exactly so and nothing else`() {
        val (after, changed) = renameRows(rows, "Bench Press (Barbell)", "Bench Press (Smith Machine)")
        assertEquals(listOf(1L, 2L, 6L), changed)
        assertEquals(
            listOf("Bench Press (Smith Machine)", "Bench Press (Smith Machine)", "Bench Press (Barbell) ", "bench press (barbell)", "Squat (Barbell)", "Bench Press (Smith Machine)"),
            after.map { it.row.exerciseName },
        )
        // Only the exercise-name text moved.
        assertEquals(rows.map { it.row.copy(exerciseName = "") }, after.map { it.row.copy(exerciseName = "") })
        assertEquals(rows.map { it.id }, after.map { it.id })
    }

    @Test
    fun `rename then undo is byte-identical`() {
        val (after, changed) = renameRows(rows, "Bench Press (Barbell)", "Bench Press")
        assertNotEquals(csv(rows).toList(), csv(after).toList())
        assertContentEquals(csv(rows), csv(restoreRows(after, changed, "Bench Press (Barbell)", "Bench Press")))
    }

    @Test
    fun `a name with a trailing space is its own exercise`() {
        val (after, changed) = renameRows(rows, "Bench Press (Barbell) ", "Bench Press (Barbell)")
        assertEquals(listOf(3L), changed)
        // That is a merge into the exercise that already exists, and the undo splits the one row back out.
        assertEquals(listOf("Bench Press (Barbell)", "Bench Press (Barbell)", "Bench Press (Barbell)", "bench press (barbell)", "Squat (Barbell)", "Bench Press (Barbell)"), after.map { it.row.exerciseName })
        assertContentEquals(csv(rows), csv(restoreRows(after, changed, "Bench Press (Barbell) ", "Bench Press (Barbell)")))
    }

    @Test
    fun `merge then undo is byte-identical and puts back only the rows that moved`() {
        // The one differing only by case is merged into the properly cased one.
        val (after, changed) = renameRows(rows, "bench press (barbell)", "Bench Press (Barbell)")
        assertEquals(listOf(4L), changed)
        assertEquals(4, after.count { it.row.exerciseName == "Bench Press (Barbell)" })
        assertContentEquals(csv(rows), csv(restoreRows(after, changed, "bench press (barbell)", "Bench Press (Barbell)")))
    }

    @Test
    fun `rows added after a rename stay under the new name when it is undone`() {
        val (after, changed) = renameRows(rows, "Bench Press (Barbell)", "Bench Press")
        val later = IdRow(7, row("2026-02-01 10:00:00", "Bench Press", "1", "70.0"))
        val undone = restoreRows(after + later, changed, "Bench Press (Barbell)", "Bench Press")
        assertEquals("Bench Press", undone.last().row.exerciseName)
        assertContentEquals(csv(rows), csv(undone.dropLast(1)))
    }

    @Test
    fun `a row moved elsewhere since, or deleted, is not touched by the undo`() {
        val (after, changed) = renameRows(rows, "Bench Press (Barbell)", "Bench Press")
        // Row 2 was moved to a variation by hand, row 6 was deleted with its workout.
        val meanwhile = after.filter { it.id != 6L }.map { if (it.id == 2L) IdRow(2, it.row.copy(exerciseName = "Bench Press (Wide Grip)")) else it }
        val undone = restoreRows(meanwhile, changed, "Bench Press (Barbell)", "Bench Press")
        assertEquals("Bench Press (Barbell)", undone.first { it.id == 1L }.row.exerciseName)
        assertEquals("Bench Press (Wide Grip)", undone.first { it.id == 2L }.row.exerciseName)
        assertEquals(5, undone.size)
    }

    @Test
    fun `renaming to the same name or an unknown one changes nothing`() {
        assertEquals(emptyList(), renameRows(rows, "Deadlift", "Deadlift (Barbell)").second)
        assertContentEquals(csv(rows), csv(renameRows(rows, "Deadlift", "Deadlift (Barbell)").first))
    }

    @Test
    fun `the program follows a renamed exercise`() {
        val day = ProgramDay("Push", 7, listOf("Bench Press (Barbell)", "Squat (Barbell)"))
        val text = encodeProgram(listOf(ProgramVersion(LocalDate.of(2026, 1, 5), listOf(day, null, null, null, null, null, null))))
        val renamed = renameInProgram(text, "Bench Press (Barbell)", "Bench Press")!!
        assertEquals(listOf("Bench Press", "Squat (Barbell)"), decodeProgram(renamed).single().days.first()!!.exercises)
        // Renaming it back is the undo.
        assertEquals(text, renameInProgram(renamed, "Bench Press", "Bench Press (Barbell)"))
        assertNull(renameInProgram(text, "Deadlift", "Row"))
        assertNull(renameInProgram(null, "Deadlift", "Row"))
    }

    @Test
    fun `a merged day names the exercise once`() {
        val day = ProgramDay("Push", 7, listOf("Bench Press", "Bench Press (Barbell)"))
        val text = encodeProgram(listOf(ProgramVersion(LocalDate.of(2026, 1, 5), listOf(day, null, null, null, null, null, null))))
        assertEquals(listOf("Bench Press"), decodeProgram(renameInProgram(text, "Bench Press (Barbell)", "Bench Press")!!).single().days.first()!!.exercises)
    }

    @Test
    fun `a workout carries the first workout note its rows have`() {
        val withNote = listOf(row("2026-01-01 10:00:00", "Squat", "1", "100.0"), row("2026-01-01 10:00:00", "Squat", "2", "100.0").copy(workoutNotes = "Felt strong"))
        assertEquals("Felt strong", buildWorkouts(withNote).single().notes)
        assertEquals("", buildWorkouts(listOf(row("2026-01-01 10:00:00", "Squat", "1", "100.0"))).single().notes)
    }

    @Test
    fun `a marker row goes with its block`() {
        val marked = rows + listOf(
            IdRow(7, row("2026-01-07 10:00:00", "Bench Press (Barbell)", "Note", notes = SIDES_NOTE)),
            IdRow(8, row("2026-01-07 10:00:00", "Bench Press (Barbell)", "1", "50.0")),
        )
        val (after, changed) = renameRows(marked, "Bench Press (Barbell)", "Bench Press")
        assertEquals(listOf(1L, 2L, 6L, 7L, 8L), changed)
        assertEquals(true, buildWorkouts(after.map { it.row }).last().blocks.single().let { it.paired && it.exercise == "Bench Press" })
        assertContentEquals(csv(marked), csv(restoreRows(after, changed, "Bench Press (Barbell)", "Bench Press")))
    }
}
