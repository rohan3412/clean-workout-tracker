package clean.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Left and right blocks: the marker note, the rounds read from it, and what counts as one set (docs/left-right.md). */
class PairedBlockTest {
    private val header = String(javaClass.getResourceAsStream("/workouts.csv")!!.readBytes(), Charsets.UTF_8).substringBefore("\n") + "\n"

    private fun line(vararg f: String) = f.joinToString(";") { "\"$it\"" } + "\n"
    private fun row(exercise: String, order: String, weight: String = "", reps: String = "", notes: String = "") =
        line("1", "2025-03-01 10:00:00", "Pull", "3600", exercise, order, weight, reps, "", "", "", notes, "")

    private val text = header +
        row("One Arm Row", "Note", notes = SIDES_NOTE) +
        row("One Arm Row", "1", "20.0", "12") + row("One Arm Row", "2", "20.0", "10") +
        row("One Arm Row", "3", "20.0", "12") + row("One Arm Row", "4", "20.0", "10") +
        row("Curl", "Note", notes = "Slow ") +
        row("Curl", "1", "10.0", "8") + row("Curl", "2", "10.0", "8")

    private val workout get() = buildWorkouts(WorkoutCsv.parse(text.toByteArray()))[0]
    private val biceps = { _: String -> MuscleUse(primary = setOf(Muscle.BICEPS)) }

    private fun logged(block: NewBlock, day: String = "2025-03-01") =
        buildWorkouts(rowsFor(1, "$day 10:00:00", "w", 3600, listOf(block)))

    private fun sets(vararg reps: Int, type: SetType = SetType.NORMAL) = reps.map { NewSet(type, 20.0, it) }

    @Test
    fun `a block opening with the marker is paired and reads its sets two at a time`() {
        val (rows, plain) = workout.blocks
        assertTrue(rows.paired)
        assertFalse(plain.paired)
        assertEquals(2, rows.rounds.size)
        assertEquals(listOf(12, 10), rows.rounds[0].let { listOf(it.left?.reps, it.right?.reps) })
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), rows.sets.map { rows.sideOf(it) })
        assertNull(plain.sideOf(plain.sets[0]))
        assertEquals(emptyList(), plain.rounds)
        // The marker is in the items, so it is written back, but it is not one of the user's notes.
        assertTrue(rows.items.first() is NoteItem)
        assertEquals(emptyList(), rows.notes)
        assertEquals(listOf("Slow "), plain.notes.map { it.text })
    }

    @Test
    fun `a set is found by identity, so two equal sets keep their own side`() {
        val marker = WorkoutRow("1", "d", "w", "60", "x", "Note", "", "", "", "", "", SIDES_NOTE, "")
        val set = WorkoutRow("1", "d", "w", "60", "x", "1", "20.0", "10", "", "", "", "", "")
        val block = Block("x", listOf(NoteItem(marker), SetItem(set, SetType.NORMAL), SetItem(set, SetType.NORMAL)))
        assertEquals(block.sets[0], block.sets[1])
        assertEquals(listOf(Side.LEFT, Side.RIGHT), block.sets.map { block.sideOf(it) })
    }

    @Test
    fun `a file with the marker round-trips byte for byte`() {
        assertEquals(text, String(WorkoutCsv.serialise(WorkoutCsv.parse(text.toByteArray())), Charsets.UTF_8))
    }

    @Test
    fun `writing a paired block puts the marker first and nothing else changes`() {
        val rows = rowsFor(1, "2025-03-01 10:00:00", "w", 60, listOf(NewBlock("x", listOf(NewNote("n"), NewSet(SetType.NORMAL, 40.0, 8)), paired = true)))
        assertEquals(listOf("Note", "Note", "1"), rows.map { it.setOrder })
        assertEquals(WorkoutRow("1", "2025-03-01 10:00:00", "w", "60", "x", "Note", "", "", "", "", "", SIDES_NOTE, ""), rows[0])
        assertEquals("n", rows[1].notes)
        assertTrue(buildWorkouts(rows)[0].blocks[0].paired)

        val plain = rowsFor(1, "2025-03-01 10:00:00", "w", 60, listOf(NewBlock("x", listOf(NewSet(SetType.NORMAL, 40.0, 8)))))
        assertEquals(listOf(WorkoutRow("1", "2025-03-01 10:00:00", "w", "60", "x", "1", "40.0", "8", "", "", "", "", "")), plain)
    }

    @Test
    fun `a round is one set, a lone left set half of one and warm-ups are left out`() {
        val round2 = logged(NewBlock("x", sets(12, 10, 12, 10), paired = true))[0]
        val ordinary = logged(NewBlock("x", sets(12, 10)))[0]
        assertEquals(2.0, round2.workingCount, 0.0)
        assertEquals(ordinary.workingCount, round2.workingCount, 0.0)
        assertEquals(4, round2.workingSets)
        // The average of the sides, per round: the same as two ordinary sets of 12 and 10.
        assertEquals(440.0, volumeOf(round2), 1e-9)
        assertEquals(volumeOf(ordinary), volumeOf(round2), 1e-9)
        assertEquals(muscleSets(listOf(ordinary), null, biceps), muscleSets(listOf(round2), null, biceps))
        assertEquals(2.0, muscleSets(listOf(round2), null, biceps).getValue(Muscle.BICEPS), 0.0)

        val lone = logged(NewBlock("x", sets(12, 10, 12), paired = true))[0]
        assertEquals(1.5, lone.workingCount, 0.0)
        assertEquals(1.5, muscleSets(listOf(lone), null, biceps).getValue(Muscle.BICEPS), 0.0)

        val warm = logged(NewBlock("x", sets(8, 8, type = SetType.WARMUP) + sets(12, 10, 12), paired = true))[0]
        assertEquals(1.5, warm.workingCount, 0.0)
        assertEquals(1.5, warm.blocks[0].workingCount, 0.0)
        assertEquals(3, warm.workingSets)
    }

    @Test
    fun `breakdowns, heads and weekly totals count a round once`() {
        val w = logged(NewBlock("Triceps Pushdown (Cable)", sets(12, 10, 12, 10), paired = true))
        val use = { _: String -> MuscleUse(primary = setOf(Muscle.TRICEPS), heads = mapOf(Head.TRICEPS_LATERAL to 1.0)) }
        val part = muscleBreakdown(w, null, use, Muscle.TRICEPS)
        assertEquals(2.0, part.exercises.single().counted, 0.0)
        assertEquals(2.0, part.workouts.single().counted, 0.0)
        assertEquals(2.0, headSets(w, null, use).getValue(Head.TRICEPS_LATERAL), 0.0)
        assertEquals(2.0, headBreakdown(w, null, use, Head.TRICEPS_LATERAL).single().counted, 0.0)
        assertEquals(listOf(2.0), weeklyTotals(w).map { it.sets })
        assertEquals(2.0, w[0].workingCount, 0.0)
    }

    @Test
    fun `fatigue and what was trained treat a round as one set`() {
        val now = LocalDateTime.parse("2025-03-02T10:00:00")
        // Four rounds against four ordinary sets: enough to still stand above the floor a day later.
        val round = logged(NewBlock("x", sets(10, 10, 10, 10, 10, 10, 10, 10), paired = true))
        val ordinary = logged(NewBlock("x", sets(10, 10, 10, 10)))
        assertEquals(fatigue(ordinary, now, biceps), fatigue(round, now, biceps))
        assertTrue(fatigue(round, now, biceps).getValue(Muscle.BICEPS) > 0)
        assertEquals(lastTrained(ordinary, biceps), lastTrained(round, biceps))
        assertEquals(retainedStrength(ordinary, now, biceps), retainedStrength(round, now, biceps))
    }

    @Test
    fun `side credit gives an ordinary set to both sides and each half of a round to its own`() {
        fun credit(block: NewBlock) = muscleSetsBySide(logged(block), null, biceps).getValue(Muscle.BICEPS)
        assertEquals(SideCredit(3.0, 3.0), credit(NewBlock("x", sets(10, 10, 10))))
        assertEquals(SideCredit(2.0, 2.0), credit(NewBlock("x", sets(10, 10, 10, 10), paired = true)))
        assertEquals(SideCredit(2.0, 1.0), credit(NewBlock("x", sets(10, 10, 10), paired = true)))
        assertEquals(SideCredit(1.0, 1.0), credit(NewBlock("x", sets(8, 8, type = SetType.WARMUP) + sets(10, 10), paired = true)))
        // A complete round gives each side what one ordinary set gives it, and the group total the same.
        val round = logged(NewBlock("x", sets(10, 10), paired = true))
        assertEquals(1.0, muscleSets(round, null, biceps).getValue(Muscle.BICEPS), 0.0)

        val heads = { _: String -> MuscleUse(primary = setOf(Muscle.TRICEPS), heads = mapOf(Head.TRICEPS_LONG to 0.5)) }
        val both = logged(NewBlock("x", sets(10, 10, 10), paired = true)) + logged(NewBlock("x", sets(10)), "2025-03-02")
        assertEquals(SideCredit(1.5, 1.0), headSetsBySide(both, null, heads).getValue(Head.TRICEPS_LONG))
    }

    @Test
    fun `marking a block and unmarking it again gives back the same file`() {
        val plainText = header +
            row("Curl", "1", "10.0", "8") + row("Curl", "2", "10.0", "8") +
            row("Row", "Note", notes = "Slow ") + row("Row", "1", "20.0", "12") +
            row("Curl", "Note", notes = "Again") + row("Curl", "1", "12.0", "6")
        val rows = WorkoutCsv.parse(plainText.toByteArray())
        val marked = withSidesMarker(rows, "Curl", 1, paired = true)!!
        val read = buildWorkouts(marked)[0].blocks
        assertEquals(listOf(false, false, true), read.map { it.paired })
        // The marker is put first, ahead of the block's own note, and nothing else changed.
        assertEquals(rows.size + 1, marked.size)
        assertEquals(listOf("Note", "Note", "1"), marked.drop(4).map { it.setOrder })
        assertEquals(rows, marked.filter { it.notes != SIDES_NOTE })
        assertEquals(listOf("Again"), read[2].notes.map { it.text })
        // Already marked, or no such block: nothing to do.
        assertNull(withSidesMarker(marked, "Curl", 1, paired = true))
        assertNull(withSidesMarker(rows, "Curl", 2, paired = true))
        assertNull(withSidesMarker(rows, "Curl", 0, paired = false))
        val back = withSidesMarker(marked, "Curl", 1, paired = false)!!
        assertEquals(rows, back)
        assertEquals(plainText, String(WorkoutCsv.serialise(back), Charsets.UTF_8))
    }

    @Test
    fun `a round with only a right half spells the order out in the marker and reads back as it was`() {
        val order = listOf(Side.LEFT, Side.RIGHT, Side.RIGHT, Side.LEFT, Side.RIGHT)
        val rows = rowsFor(1, "2025-03-01 10:00:00", "w", 3600, listOf(NewBlock("x", sets(12, 10, 9, 11, 8), paired = true, shapes = shapesOf(order))))
        assertEquals("Left, then right: LR R- LR", rows[0].notes)
        val bytes = WorkoutCsv.serialise(rows)
        assertEquals(String(bytes, Charsets.UTF_8), String(WorkoutCsv.serialise(WorkoutCsv.parse(bytes)), Charsets.UTF_8))

        val workout = buildWorkouts(WorkoutCsv.parse(bytes))[0]
        val block = workout.blocks[0]
        assertTrue(block.paired)
        assertEquals(emptyList(), block.notes)
        assertEquals(order, block.setSides)
        assertEquals(listOf(12 to 10, null to 9, 11 to 8), block.rounds.map { it.left?.reps to it.right?.reps })
        val session = sessionsOf(listOf(workout), "x").single()
        assertEquals(2, session.forSide(Side.LEFT).sets.size)
        assertEquals(3, session.forSide(Side.RIGHT).sets.size)

        // A lone right set credits the right side only.
        val lone = logged(NewBlock("x", sets(10), paired = true, shapes = shapesOf(listOf(Side.RIGHT))))
        assertEquals(SideCredit(0.0, 1.0), muscleSetsBySide(lone, null, biceps).getValue(Muscle.BICEPS))
        assertEquals(0.5, lone[0].workingCount, 0.0)
    }

    @Test
    fun `sets that simply alternate write the plain marker, and a count that does not match is ignored`() {
        val alternating = listOf(Side.LEFT, Side.RIGHT, Side.LEFT)
        val plain = rowsFor(1, "d", "w", 60, listOf(NewBlock("x", sets(1, 2, 3), paired = true)))
        assertEquals(SIDES_NOTE, plain[0].notes)
        assertEquals(plain, rowsFor(1, "d", "w", 60, listOf(NewBlock("x", sets(1, 2, 3), paired = true, shapes = shapesOf(alternating)))))
        // Letters for the wrong number of sets are not used when writing, and are read two at a time when found.
        assertEquals(plain, rowsFor(1, "d", "w", 60, listOf(NewBlock("x", sets(1, 2, 3), paired = true, shapes = shapesOf(listOf(Side.RIGHT))))))
        val odd = plain.map { if (it.setOrder == "Note") it.copy(notes = "Left, then right: R R") else it }
        assertEquals(alternating, buildWorkouts(odd)[0].blocks[0].setSides)
        assertEquals(emptyList(), buildWorkouts(odd)[0].blocks[0].notes)
        assertTrue(isSidesMarker("Left, then right: L R"))
        assertFalse(isSidesMarker("Left, then right: L X"))
    }

    private fun markerRows(marker: String, count: Int) =
        listOf(row("x", "Note", notes = marker)) + (1..count).map { row("x", "$it", "10.0", "${10 + it}") }

    private fun blockOf(marker: String, count: Int) = buildWorkouts(WorkoutCsv.parse((header + markerRows(marker, count).joinToString("")).toByteArray()))[0].blocks[0]

    @Test
    fun `a marker is read as plain, as the old letters or as the new round tokens, and anything else is a note`() {
        assertNull(sidesLayout(SIDES_NOTE))
        assertEquals(listOf(RoundShape.LEFT_RIGHT, RoundShape.RIGHT_ONLY, RoundShape.LEFT_RIGHT), sidesLayout("Left, then right: L R R L R"))
        assertEquals(listOf(RoundShape.RIGHT_LEFT, RoundShape.RIGHT_LEFT, RoundShape.LEFT_ONLY), sidesLayout("Left, then right: RL RL L-"))
        assertEquals(listOf(RoundShape.RIGHT_ONLY, RoundShape.LEFT_ONLY), sidesLayout("Left, then right: R- L-"))
        assertTrue(isSidesMarker("Left, then right: RL R-"))
        assertFalse(isSidesMarker("Left, then right: RL R"))
        assertFalse(isSidesMarker("Left, then right: L RL"))
        assertFalse(isSidesMarker("Left, then right: LL"))
        assertFalse(isSidesMarker("Left, then right: "))

        val b = blockOf("Left, then right: RL RL L-", 5)
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.LEFT), b.setSides)
        assertEquals(listOf(true, true, false), b.rounds.map { it.rightFirst })
        assertEquals(listOf(12 to 11, 14 to 13, 15 to null), b.rounds.map { it.left?.reps to it.right?.reps })
        assertEquals(emptyList(), b.notes)

        val old = blockOf("Left, then right: L R R L R", 5)
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.RIGHT, Side.LEFT, Side.RIGHT), old.setSides)
        assertTrue(old.rounds.none { it.rightFirst })
        // A count that does not match falls back to alternating.
        val wrong = blockOf("Left, then right: RL RL", 5)
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT), wrong.setSides)
        assertTrue(wrong.rounds.none { it.rightFirst })
    }

    @Test
    fun `the marker is plain when every round goes left then right, else spelled with tokens and never the old letters`() {
        val (lr, rl, l, r) = RoundShape.values().let { listOf(it[0], it[1], it[2], it[3]) }
        assertEquals(SIDES_NOTE, sidesMarker(null))
        assertEquals(SIDES_NOTE, sidesMarker(listOf(lr, lr)))
        assertEquals(SIDES_NOTE, sidesMarker(listOf(lr, lr, l)))
        assertEquals("Left, then right: LR L- LR", sidesMarker(listOf(lr, l, lr)))
        assertEquals("Left, then right: RL LR", sidesMarker(listOf(rl, lr)))
        assertEquals("Left, then right: R-", sidesMarker(listOf(r)))
        assertEquals("Left, then right: LR R-", sidesMarker(listOf(lr, r)))
    }

    @Test
    fun `a round done right first is written right then left and reads back and round-trips`() {
        val shapes = listOf(RoundShape.RIGHT_LEFT, RoundShape.LEFT_RIGHT, RoundShape.LEFT_ONLY)
        val rows = rowsFor(1, "2025-03-01 10:00:00", "w", 3600, listOf(NewBlock("x", sets(12, 10, 9, 11, 8), paired = true, shapes = shapes)))
        assertEquals("Left, then right: RL LR L-", rows[0].notes)
        assertEquals(listOf("12", "10", "9", "11", "8"), rows.drop(1).map { it.reps })
        val bytes = WorkoutCsv.serialise(rows)
        assertEquals(rows, WorkoutCsv.parse(bytes))
        assertEquals(String(bytes, Charsets.UTF_8), String(WorkoutCsv.serialise(WorkoutCsv.parse(bytes)), Charsets.UTF_8))

        val block = buildWorkouts(WorkoutCsv.parse(bytes))[0].blocks[0]
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.LEFT, Side.RIGHT, Side.LEFT), block.setSides)
        assertEquals(listOf(10 to 12, 9 to 11, 8 to null), block.rounds.map { it.left?.reps to it.right?.reps })
        assertEquals(listOf(true, false, false), block.rounds.map { it.rightFirst })
        val session = sessionsOf(buildWorkouts(WorkoutCsv.parse(bytes)), "x").single()
        assertEquals(3, session.forSide(Side.LEFT).sets.size)
        assertEquals(2, session.forSide(Side.RIGHT).sets.size)
    }

    // --- A kind per half (2026-10-16) ---

    private fun mixedBlock() = NewBlock(
        "x",
        listOf(NewSet(SetType.WARMUP, 10.0, 12), NewSet(SetType.NORMAL, 10.0, 12), NewSet(SetType.FAILURE, 20.0, 12), NewSet(SetType.NORMAL, 20.0, 10), NewSet(SetType.WARMUP, 5.0, 12), NewSet(SetType.WARMUP, 5.0, 12)),
        paired = true,
    )

    @Test
    fun `each half of a round keeps its own kind when written and read back`() {
        val rows = rowsFor(1, "2025-03-01 10:00:00", "w", 3600, listOf(mixedBlock()))
        // Each stored row has its own Set Order; the marker is the plain one.
        assertEquals(listOf("Note", "W", "1", "F", "3", "W", "W"), rows.map { it.setOrder })
        assertEquals(SIDES_NOTE, rows[0].notes)
        val bytes = WorkoutCsv.serialise(rows)
        assertEquals(String(bytes, Charsets.UTF_8), String(WorkoutCsv.serialise(WorkoutCsv.parse(bytes)), Charsets.UTF_8))
        val block = buildWorkouts(WorkoutCsv.parse(bytes))[0].blocks[0]
        assertEquals(
            listOf(SetType.WARMUP to SetType.NORMAL, SetType.FAILURE to SetType.NORMAL, SetType.WARMUP to SetType.WARMUP),
            block.rounds.map { it.left!!.type to it.right!!.type },
        )
    }

    @Test
    fun `a round whose halves differ is numbered unless both are warm-ups and each half shows its own letter`() {
        val block = logged(mixedBlock())[0].blocks[0]
        // A warm-up with a working half counts as a round; two warm-ups do not.
        assertEquals(listOf("1", "2", "W"), roundLabels(block.rounds))
        assertNull(roundKind(SetType.WARMUP, SetType.NORMAL))
        assertEquals(SetType.WARMUP, roundKind(SetType.WARMUP, SetType.WARMUP))
        assertEquals(SetType.FAILURE, roundKind(null, SetType.FAILURE))
        assertEquals("W", halfLetter(block.rounds[0], Side.LEFT))
        assertEquals("N", halfLetter(block.rounds[0], Side.RIGHT))
        assertNull(halfLetter(block.rounds[2], Side.LEFT))
    }

    @Test
    fun `counts and records treat each half by its own kind`() {
        val w = logged(mixedBlock())
        // Round 1: only the right half works (0.5); round 2: both (1); round 3: none.
        assertEquals(1.5, w[0].workingCount, 0.0)
        assertEquals(1.5, muscleSets(w, null, biceps).getValue(Muscle.BICEPS), 0.0)
        val kinds = prsOf(w, EPLEY_RULE).values.single()
        assertTrue(kinds[0].isEmpty(), "a warm-up half earns nothing")
        assertTrue(kinds[1].isNotEmpty(), "the working half beside it does")
        assertTrue(kinds[2].isNotEmpty() && kinds[3].isNotEmpty())
        assertTrue(kinds[4].isEmpty() && kinds[5].isEmpty())
    }

    @Test
    fun `a round keeps the left half's kind in a template made from the workout`() {
        // pairTypes keeps one kind per round: the first half's (a template holds one kind per planned round).
        val types = listOf(SetType.FAILURE, SetType.NORMAL, SetType.WARMUP, SetType.WARMUP)
        assertEquals(listOf(SetType.FAILURE, SetType.WARMUP), pairTypes(types))
    }
}
