package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Iso-Lateral Row history fix: which blocks move to wide grip, which become left and right, and the rows they become (docs/domain.md). */
class IsoRowFixTest {
    private fun row(date: String, number: Int, exercise: String, order: String, weight: String = "", reps: String = "", notes: String = "", seconds: String = "", rpe: String = "") =
        WorkoutRow(number.toString(), "$date 10:00:00", "Pull", "3600", exercise, order, weight, reps, rpe, "", seconds, notes, "")

    /** One workout: the notes opening a block, then two sets of 40 kg with a rest after the first. */
    private fun workout(date: String, number: Int, vararg notes: String): List<WorkoutRow> =
        notes.map { row(date, number, ISO_ROW, "Note", notes = it) } + listOf(
            row(date, number, ISO_ROW, "1", "40.0", "10"), row(date, number, ISO_ROW, "Rest Timer", seconds = "90.0"), row(date, number, ISO_ROW, "2", "40.0", "8"),
        )

    private val before = listOf("2026-05-13", "2026-05-16", "2026-05-20", "2026-05-23", "2026-05-27", "2026-05-30", "2026-06-02", "2026-06-04", "2026-06-06")
    private val history: List<WorkoutRow> =
        before.mapIndexed { i, d -> workout(d, i + 1) }.flatten() +
            workout("2026-06-09", 10, "Neutral Grip") +
            workout("2026-07-15", 11, "Better form") +
            workout("2026-07-23", 12, "The last 4 reps werent perfect form ") +
            workout("2026-07-27", 13, "Regular grip") +
            workout("2026-08-14", 14, "Neutral Grip, very bad form") +
            workout("2026-09-08", 15, "95 with grips") +
            // From here on both arms' weights are added together; every kind of set is there.
            listOf(
                row("2026-09-15", 16, ISO_ROW, "Note", notes = "Standing, one arm at a time"),
                row("2026-09-15", 16, ISO_ROW, "W", "20.0", "12"), row("2026-09-15", 16, ISO_ROW, "1", "95.0", "10", rpe = "8"),
                row("2026-09-15", 16, ISO_ROW, "Rest Timer", seconds = "60.0"),
                row("2026-09-15", 16, ISO_ROW, "2", "112.5", "8"), row("2026-09-15", 16, ISO_ROW, "F", "90.0", "9"), row("2026-09-15", 16, ISO_ROW, "D", "70.0", "12"),
            ) +
            workout("2026-09-19", 17, "One side at a time") +
            workout("2026-09-26", 18) +
            // Two blocks in one workout, each opening with a note of its own.
            listOf(
                row("2026-10-05", 19, ISO_ROW, "Note", notes = "Wide grip, (make new exercise)"),
                row("2026-10-05", 19, ISO_ROW, "F", "80.0", "6"), row("2026-10-05", 19, ISO_ROW, "F", "70.0", "6"),
                row("2026-10-05", 19, ISO_ROW, "Note", notes = "Neutral"),
                row("2026-10-05", 19, ISO_ROW, "F", "110.0", "3"),
            )

    private val plan = planIsoRowFix(history)
    private fun at(date: String, index: Int = 0) = plan.blocks.first { it.date.startsWith(date) && it.index == index }
    private fun rowsOn(date: String, index: Int = 0): List<WorkoutRow> =
        buildWorkouts(plan.workouts.first { it.date.startsWith(date) }.rows).flatMap { it.blocks }.filter { it.exercise == ISO_ROW || it.exercise == ISO_ROW_WIDE }[index].items.map { it.row }

    @Test
    fun `the workouts before the neutral note go to wide grip with or without notes`() {
        for (d in before) {
            val b = at(d)
            assertEquals(IsoGrip.WIDE, b.grip)
            assertEquals(IsoAction.RENAME, b.action)
            assertEquals(ISO_ROW_WIDE, b.target)
        }
        val fix = plan.workouts.first { it.date.startsWith("2026-05-13") }
        assertEquals(history.filter { it.date.startsWith("2026-05-13") }.map { it.copy(exerciseName = ISO_ROW_WIDE) }, fix.rows)
    }

    @Test
    fun `from the neutral note on it stays neutral and keeps its name except where a note says wide`() {
        for (d in listOf("2026-06-09", "2026-07-15", "2026-07-23", "2026-08-14", "2026-09-08")) {
            assertEquals(IsoGrip.NEUTRAL, at(d).grip)
            assertEquals(IsoAction.NONE, at(d).action)
            assertTrue(plan.workouts.none { it.date.startsWith(d) })
        }
        assertEquals(IsoGrip.WIDE, at("2026-07-27").grip)
        assertEquals(IsoAction.RENAME, at("2026-07-27").action)
        assertEquals(history.filter { it.date.startsWith("2026-07-27") }.map { it.copy(exerciseName = ISO_ROW_WIDE) }, plan.workouts.first { it.date.startsWith("2026-07-27") }.rows)
    }

    @Test
    fun `from the standing note on every set becomes a left and a right set of half the weight`() {
        val rows = rowsOn("2026-09-15")
        assertEquals(listOf("Note", "Note", "W", "W", "1", "2", "Rest Timer", "3", "4", "F", "F", "D", "D"), rows.map { it.setOrder })
        assertEquals(listOf(SIDES_NOTE, "Standing, one arm at a time"), rows.take(2).map { it.notes })
        assertEquals(listOf("10.0", "10.0", "47.5", "47.5", "", "56.25", "56.25", "45.0", "45.0", "35.0", "35.0"), rows.drop(2).map { it.weightKg })
        assertEquals(listOf("12", "12", "10", "10", "", "8", "8", "9", "9", "12", "12"), rows.drop(2).map { it.reps })
        // Same RPE on both halves, the rest stays after the set it followed, the exercise keeps its name.
        assertEquals("8", rows[4].rpe)
        assertEquals("8", rows[5].rpe)
        assertEquals("60.0", rows[6].seconds)
        assertTrue(rows.all { it.exerciseName == ISO_ROW && it.date == "2026-09-15 10:00:00" && it.workoutName == "Pull" && it.durationSeconds == "3600" })
        val block = buildWorkouts(rows)[0].blocks[0]
        assertTrue(block.paired)
        assertEquals(5, block.rounds.size)
        assertEquals(listOf("Standing, one arm at a time"), block.notes.map { it.text })
        assertEquals(
            listOf("W 20×12 → L 10×12 + R 10×12", "1 95×10 → L 47.5×10 + R 47.5×10", "2 112.5×8 → L 56.25×8 + R 56.25×8", "F 90×9 → L 45×9 + R 45×9", "D 70×12 → L 35×12 + R 35×12"),
            at("2026-09-15").lines,
        )
    }

    @Test
    fun `set order is numbered as Clean writes a paired block`() {
        val mine = rowsOn("2026-09-15")
        val types = listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.FAILURE, SetType.DROP)
        val written = rowsFor(
            16, "2026-09-15 10:00:00", "Pull", 3600,
            listOf(NewBlock(ISO_ROW, listOf<NewItem>(NewNote("Standing, one arm at a time")) + types.flatMap { t -> listOf(NewSet(t, 40.0, 10), NewSet(t, 40.0, 10)) }, paired = true)),
        )
        assertEquals(written.map { it.setOrder }, mine.filter { it.setOrder != "Rest Timer" }.map { it.setOrder })
        assertEquals(written.map { it.notes }, mine.filter { it.setOrder != "Rest Timer" }.map { it.notes })
    }

    @Test
    fun `the fifth of October splits its two blocks as one wide and one neutral`() {
        val first = at("2026-10-05", 0)
        val second = at("2026-10-05", 1)
        assertEquals(listOf(IsoGrip.WIDE, IsoGrip.NEUTRAL), listOf(first.grip, second.grip))
        assertEquals(listOf(ISO_ROW_WIDE, ISO_ROW), listOf(first.target, second.target))
        assertEquals(listOf(IsoAction.SPLIT, IsoAction.SPLIT), listOf(first.action, second.action))
        assertEquals(listOf("F 80×6 → L 40×6 + R 40×6", "F 70×6 → L 35×6 + R 35×6"), first.lines)
        assertEquals(listOf("F 110×3 → L 55×3 + R 55×3"), second.lines)
        val rows = plan.workouts.first { it.date.startsWith("2026-10-05") }.rows
        assertEquals(
            listOf(
                ISO_ROW_WIDE to SIDES_NOTE, ISO_ROW_WIDE to "Wide grip, (make new exercise)",
                ISO_ROW_WIDE to "F 40.0×6", ISO_ROW_WIDE to "F 40.0×6", ISO_ROW_WIDE to "F 35.0×6", ISO_ROW_WIDE to "F 35.0×6",
                ISO_ROW to SIDES_NOTE, ISO_ROW to "Neutral", ISO_ROW to "F 55.0×3", ISO_ROW to "F 55.0×3",
            ),
            rows.map { it.exerciseName to if (it.setOrder == "Note") it.notes else "${it.setOrder} ${it.weightKg}×${it.reps}" },
        )
    }

    @Test
    fun `a block already logged left and right is left completely alone`() {
        val rows = listOf(
            row("2026-06-09", 1, ISO_ROW, "Note", notes = "Neutral"), row("2026-06-09", 1, ISO_ROW, "1", "40.0", "10"),
            row("2026-09-15", 2, ISO_ROW, "Note", notes = SIDES_NOTE), row("2026-09-15", 2, ISO_ROW, "Note", notes = "Standing"),
            row("2026-09-15", 2, ISO_ROW, "1", "40.0", "10"), row("2026-09-15", 2, ISO_ROW, "2", "40.0", "9"),
            row("2026-09-15", 2, "$ISO_ROW ", "1", "40.0", "10"), row("2026-09-15", 2, "Curl", "1", "10.0", "10"),
        )
        val p = planIsoRowFix(rows)
        assertEquals(IsoAction.UNCHANGED, p.blocks[1].action)
        assertEquals(1, p.leftAlone)
        assertTrue(p.isEmpty)
    }

    @Test
    fun `half weights are written as Clean writes weights`() {
        assertEquals("45.0", halfWeightText("90.0"))
        assertEquals("47.5", halfWeightText("95.0"))
        assertEquals("56.25", halfWeightText("112.5"))
        assertEquals("5.0", halfWeightText("10"))
        assertEquals("-12.5", halfWeightText("-25.0"))
        assertEquals("", halfWeightText(""))
    }

    @Test
    fun `the totals and a second run`() {
        // The 9 workouts before the neutral note, 27 Jul and the wide block of 5 Oct.
        assertEquals(11, plan.movedToWide)
        // 15, 19 and 26 Sep, and 5 Oct.
        assertEquals(4, plan.convertedToSides)
        // 5 + 2 + 2 + 2 + 1 set rows.
        assertEquals(12, plan.setRowsSplit)
        assertEquals(0, plan.leftAlone)
        // What the plan wrote, read again, has nothing left to do.
        assertTrue(planIsoRowFix(history.filter { r -> plan.workouts.none { it.date == r.date } } + plan.workouts.flatMap { it.rows }).isEmpty)
        assertTrue(planIsoRowFix(listOf(row("2026-09-15", 1, "Curl", "1", "10.0", "10"))).isEmpty)
    }
}
