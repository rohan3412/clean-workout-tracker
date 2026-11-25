package clean.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordsTest {
    @Test
    fun `working sets leave out the warm-ups, and a stored date reads back`() {
        val w = buildWorkouts(rowsFor(1, "2025-01-01 10:00:00", "w", 60, listOf(NewBlock("x", listOf(NewSet(SetType.WARMUP, 20.0, 5), NewSet(SetType.NORMAL, 40.0, 8), NewSet(SetType.DROP, 30.0, 8))))))[0]
        assertEquals(2, w.blocks[0].workingSets)
        assertEquals(2, w.workingSets)
        assertEquals(40.0, bestSetOf(w.blocks[0].sets)?.weight)
        assertEquals(null, parseStrongDate("not a date"))
        assertEquals("2025-01-01T10:00", parseStrongDate(w.date).toString())
    }

    @Test
    fun `weeks are counted Monday to Sunday and an empty week is still a week`() {
        fun w(day: String, reps: Int) = rowsFor(1, "$day 10:00:00", "w", 3600, listOf(NewBlock("x", listOf(NewSet(SetType.WARMUP, 20.0, 5), NewSet(SetType.NORMAL, 40.0, reps)))))
        // Monday 6 and Sunday 12 January 2025 are one week; nothing in the week of the 13th; Monday the 20th is the third.
        val weeks = weeklyTotals(buildWorkouts(w("2025-01-06", 10) + w("2025-01-12", 5) + w("2025-01-20", 10)))
        assertEquals(listOf("2025-01-06", "2025-01-13", "2025-01-20"), weeks.map { it.monday.toString() })
        assertEquals(listOf(2, 0, 1), weeks.map { it.workouts })
        assertEquals(listOf(7200L, 0L, 3600L), weeks.map { it.seconds })
        // The warm-up counts for neither sets nor volume.
        assertEquals(listOf(2.0, 0.0, 1.0), weeks.map { it.sets })
        assertEquals(listOf(600.0, 0.0, 400.0), weeks.map { it.volume })
    }

    @Test
    fun `a straight line is fitted through points`() {
        val (slope, start) = linearFit(listOf(0.0 to 1.0, 1.0 to 3.0, 2.0 to 5.0))!!
        assertEquals(2.0, slope, 1e-9)
        assertEquals(1.0, start, 1e-9)
        assertEquals(null, linearFit(listOf(0.0 to 1.0, 1.0 to 2.0)))
        assertEquals(null, linearFit(listOf(1.0 to 1.0, 1.0 to 2.0, 1.0 to 3.0)))
    }

    @Test
    fun `warm-up sets step up to the working weight in whole steps`() {
        assertEquals(listOf(Target(40.0, 8), Target(60.0, 5), Target(80.0, 2)), warmupSets(100.0, 2.5, WARMUP_SCHEMES.getValue("3 sets")))
        // 22.5 kg in 5 kg steps: 40% is 10, 60% is 15 (13.5 rounded), 80% is 20 (18 rounded).
        assertEquals(listOf(10.0, 15.0, 20.0), warmupSets(22.5, 5.0, WARMUP_SCHEMES.getValue("3 sets")).map { it.weight })
        // Too light to have anything under it.
        assertEquals(emptyList<Target>(), warmupSets(5.0, 5.0, WARMUP_SCHEMES.getValue("3 sets")))
    }

    @Test
    fun `a set's RPE and the rest after it are written as Strong writes them`() {
        val rows = rowsFor(1, "2025-01-01 10:00:00", "w", 60, listOf(NewBlock("x", listOf(NewSet(SetType.NORMAL, 40.0, 10, rpe = "8.5"), NewRest(120)))))
        assertEquals(listOf("8.5", ""), rows.map { it.rpe })
        assertEquals(listOf("1", "Rest Timer"), rows.map { it.setOrder })
        assertEquals("120.0", rows[1].seconds)
        // Read back, the rest is not a set.
        assertEquals(1, buildWorkouts(rows)[0].blocks[0].sets.size)
    }

    @Test
    fun `sessions for every exercise at once are the same as asking for each`() {
        val workouts = buildWorkouts(
            rowsFor(1, "2025-01-01 10:00:00", "a", 60, listOf(NewBlock("x", listOf(NewSet(SetType.NORMAL, 40.0, 10))), NewBlock("y", listOf(NewSet(SetType.NORMAL, 20.0, 8))), NewBlock("x", listOf(NewSet(SetType.NORMAL, 45.0, 6))))) +
                rowsFor(2, "2025-01-02 10:00:00", "b", 60, listOf(NewBlock("x", listOf(NewSet(SetType.NORMAL, 50.0, 5))))),
        )
        val all = sessionsByExercise(workouts)
        assertEquals(setOf("x", "y"), all.keys)
        for (name in all.keys) assertEquals(sessionsOf(workouts, name), all[name])
    }

    private fun session(date: String, vararg sets: NewSet) = Session(
        date,
        rowsFor(1, date, "w", 60, listOf(NewBlock("x", sets.toList()))).map { itemOf(it) as SetItem },
    )

    @Test
    fun `estimated 1RM is Epley, exact for a single, absent above twelve reps`() {
        assertEquals(100.0, estimate1Rm(100.0, 1, EPLEY_RULE))
        assertEquals(60.0, estimate1Rm(45.0, 10, EPLEY_RULE))
        assertEquals(84.0, estimate1Rm(60.0, 12, EPLEY_RULE))
        assertNull(estimate1Rm(60.0, 13, EPLEY_RULE))
        assertNull(estimate1Rm(0.0, 5, EPLEY_RULE))
    }

    @Test
    fun `records come from working sets, with drop sets counting for volume only`() {
        val records = recordsOf(
            listOf(
                session(
                    "2025-01-01 10:00:00",
                    NewSet(SetType.WARMUP, 100.0, 20),
                    NewSet(SetType.NORMAL, 40.0, 10),
                    NewSet(SetType.FAILURE, 45.0, 6),
                    NewSet(SetType.DROP, 30.0, 15),
                ),
                session("2025-01-08 10:00:00", NewSet(SetType.NORMAL, 40.0, 12), NewSet(SetType.FAILURE, 50.0, 3)),
            ),
            EPLEY_RULE,
        )
        assertEquals(Record(50.0, 50.0, 3, "2025-01-08 10:00:00"), records.weight)
        assertEquals(Record(12.0, 40.0, 12, "2025-01-08 10:00:00"), records.reps)
        // 40 x 12 -> 40 * 1.4 = 56; 45 x 6 -> 54; 50 x 3 -> 55.
        assertEquals(Record(56.0, 40.0, 12, "2025-01-08 10:00:00"), records.estimated1Rm)
        assertEquals(Record(480.0, 40.0, 12, "2025-01-08 10:00:00"), records.setVolume)
        // Day one: 400 + 270 + 450 (drop) = 1120; day two: 480 + 150 = 630.
        assertEquals(Record(1120.0, null, null, "2025-01-01 10:00:00"), records.sessionVolume)
        assertEquals(listOf(50.0 to 3, 45.0 to 6, 40.0 to 12), records.repsAtWeight.map { it.weight to it.reps })
    }

    @Test
    fun `bodyweight lifts record reps and assisted lifts prefer less help`() {
        val bodyweight = recordsOf(
            listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 0.0, 6), NewSet(SetType.NORMAL, 0.0, 8))),
            EPLEY_RULE,
            LoadType.BODYWEIGHT,
        )
        assertEquals(8.0, bodyweight.reps?.value)
        assertNull(bodyweight.estimated1Rm)
        assertNull(bodyweight.setVolume)

        val assisted = recordsOf(
            listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 30.0, 8), NewSet(SetType.NORMAL, 20.0, 5))),
            EPLEY_RULE,
            LoadType.ASSISTED,
        )
        assertEquals(20.0, assisted.weight?.value)
        assertEquals(listOf(20.0, 30.0), assisted.repsAtWeight.map { it.weight })
        // No weigh-in: nothing that needs the real load, and the records say so.
        assertNull(assisted.estimated1Rm)
        assertNull(assisted.sessionVolume)
        assertEquals(true, assisted.missingBodyweight)
    }

    @Test
    fun `with a weigh-in, assisted and bodyweight lifts get volume and an estimated 1RM on the real load`() {
        val sets = listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 30.0, 8), NewSet(SetType.NORMAL, 20.0, 5)))
        val assisted = recordsOf(sets, EPLEY_RULE, LoadType.ASSISTED) { 80.0 }
        // 80 - 30 = 50 for 8 is 400 of volume; 80 - 20 = 60 for 5 is 300, and 60 x (1 + 5/30) = 70 estimated.
        assertEquals(Record(70.0, 20.0, 5, "2025-01-01 10:00:00"), assisted.estimated1Rm)
        assertEquals(Record(400.0, 30.0, 8, "2025-01-01 10:00:00"), assisted.setVolume)
        assertEquals(700.0, assisted.sessionVolume?.value)
        assertEquals(false, assisted.missingBodyweight)

        val weighted = recordsOf(sets, EPLEY_RULE, LoadType.BODYWEIGHT) { 80.0 }
        // 80 + 30 = 110 for 8: 880 of volume and 110 x (1 + 8/30) estimated.
        assertEquals(30.0, weighted.weight?.value)
        assertEquals(880.0, weighted.setVolume?.value)
        assertEquals(110 * (1 + 8 / 30.0), weighted.estimated1Rm!!.value, 1e-9)
    }

    @Test
    fun `each session gives one row of chart values`() {
        val rows = progressOf(
            listOf(
                session("2025-01-01 10:00:00", NewSet(SetType.WARMUP, 100.0, 1), NewSet(SetType.NORMAL, 40.0, 10), NewSet(SetType.DROP, 30.0, 12)),
                session("2025-01-08 10:00:00", NewSet(SetType.NORMAL, 50.0, 3)),
            ),
            EPLEY_RULE,
        )
        // The warm-up counts for nothing; the drop set adds volume but sets no weight for the 1RM.
        assertEquals(Progress("2025-01-01 10:00:00", 40.0, 40 * (1 + 10 / 30.0), 760.0, 12, null, null, null), rows[0])
        assertEquals(50.0, rows[1].topWeight)
        assertEquals(150.0, rows[1].volume)

        val assisted = progressOf(listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 30.0, 8), NewSet(SetType.NORMAL, 20.0, 5))), EPLEY_RULE, LoadType.ASSISTED) { 80.0 }
        assertEquals(20.0, assisted[0].topWeight)
        assertEquals(700.0, assisted[0].volume)
        assertNull(progressOf(listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 30.0, 8))), EPLEY_RULE, LoadType.ASSISTED)[0].volume)

        val hold = progressOf(listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, seconds = 45.0), NewSet(SetType.NORMAL, seconds = 60.0))), EPLEY_RULE, LoadType.TIMED)
        assertEquals(60.0, hold[0].longestSeconds)
        assertEquals(105.0, hold[0].totalSeconds)
    }

    @Test
    fun `reps-only sets count for the reps record`() {
        val records = recordsOf(listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, reps = 12))), EPLEY_RULE)
        assertEquals(12.0, records.reps?.value)
        assertNull(records.weight)
    }

    @Test
    fun `the next target adds reps first, then weight`() {
        // One more rep at the same weight beats the best.
        assertEquals(Target(30.0, 11), nextTarget(Target(30.0, 10), 40.0, 2.5, EPLEY_RULE))
        // A weak last session needs more than one extra rep to beat an older best.
        assertEquals(Target(30.0, 11), nextTarget(Target(30.0, 8), 40.0, 2.5, EPLEY_RULE))
        // Past twelve reps: one step heavier, at the fewest reps that still beat 30 x 12 = 42.
        assertEquals(Target(32.5, 9), nextTarget(Target(30.0, 12), 42.0, 2.5, EPLEY_RULE))
        assertEquals(Target(30.0, 11), nextTarget(Target(30.0, 10), null, 2.5, EPLEY_RULE))
    }

    @Test
    fun `an exercise has stalled when its last sessions set no new best`() {
        fun line(vararg values: Double) = values.mapIndexed { i, v -> Progress("2025-01-%02d 10:00:00".format(i + 1), v, v, v, 5, null, null, null) }
        assertTrue(stalled(line(50.0, 55.0, 60.0, 60.0, 59.0, 58.0, 60.0, 57.0)))
        assertFalse(stalled(line(50.0, 55.0, 60.0, 60.0, 59.0, 58.0, 60.0, 61.0)))
        // Too little history to say.
        assertFalse(stalled(line(60.0, 60.0, 60.0, 60.0, 60.0)))
    }
}
