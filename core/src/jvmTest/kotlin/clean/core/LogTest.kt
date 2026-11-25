package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class LogTest {
    private val rows = WorkoutCsv.parse(javaClass.getResourceAsStream("/workouts.csv")!!.readBytes())
    private val workouts = buildWorkouts(rows)

    @Test
    fun `rows group into workouts and blocks in file order`() {
        assertEquals(listOf("Pull Day ", "Morning"), workouts.map { it.name })
        assertEquals(
            // The note after sets in the first Lat Pulldown run starts a second card.
            listOf("Lat Pulldown (Machine)", "Lat Pulldown (Machine)", "Pull Up", "Hanging Knee Raise", "Bicep Curl (Dumbbell)", "Lat Pulldown (Machine)"),
            workouts[0].blocks.map { it.exercise },
        )
        assertEquals(3605, workouts[0].durationSeconds)
        assertEquals("2025-01-06", workouts[0].day)
        assertEquals(rows, workouts.flatMap { w -> w.blocks.flatMap { b -> b.items.map { it.row } } })
    }

    @Test
    fun `set types and values are read from the row`() {
        val block = workouts[0].blocks[0]
        assertEquals(
            listOf(SetType.WARMUP, SetType.FAILURE, SetType.NORMAL, SetType.DROP),
            block.sets.map { it.type },
        )
        assertEquals(45.0, block.sets[1].weight)
        assertEquals(7, block.sets[1].reps)
        assertEquals("Neutral", (workouts[0].blocks[1].items[0] as NoteItem).text)
        assertEquals(listOf("1"), workouts[0].blocks[1].sets.map { it.row.setOrder })
        assertNull(workouts[0].blocks[3].sets[0].weight)
        // 45 x 7 beats 40 x 9 and the drop set; the warm-up is never the best set.
        assertEquals("45.0", block.bestSet?.row?.weightKg)
        assertEquals(120.0, (block.items[3] as RestItem).seconds)
        assertEquals("Wide grip ", (block.items[0] as NoteItem).text)
    }

    @Test
    fun `a workout logged in Clean is written the way Strong writes it`() {
        val made = rowsFor(
            1, "2025-01-06 18:02:11", "Pull Day ", 3605,
            listOf(
                NewBlock(
                    "Lat Pulldown (Machine)",
                    listOf(
                        NewNote("Wide grip "),
                        NewSet(SetType.WARMUP, 20.0, 8),
                        NewSet(SetType.FAILURE, 45.0, 7),
                        NewSet(SetType.NORMAL, 40.0, 9),
                        NewSet(SetType.DROP, 30.0, 6),
                        NewNote("Neutral"),
                        NewSet(SetType.NORMAL, 42.5, 8),
                    ),
                ),
                NewBlock("Pull Up", listOf(NewSet(SetType.NORMAL, 0.0, 6), NewSet(SetType.NORMAL, 0.0, 5))),
                NewBlock("Hanging Knee Raise", listOf(NewNote(""), NewSet(SetType.NORMAL, reps = 12))),
            ),
        )
        // The fixture has a rest timer at index 3, which Clean does not write.
        assertEquals(rows.take(12).filterIndexed { i, _ -> i != 3 }, made)
    }

    @Test
    fun `numbers are written with at least one decimal and at most four`() {
        assertEquals("10.0", decimalText(10.0))
        assertEquals("7.5", decimalText(7.5))
        assertEquals("3.75", decimalText(3.75))
        assertEquals("0.0", decimalText(0.0))
        assertEquals("54.4311", decimalText(lbToKg(120.0)))
        assertEquals(120.0, kgToLb(54.4311))
        assertEquals("100", shortNumber(100.0))
        assertEquals("7.5", shortNumber(7.5))
    }

    @Test
    fun `week streak counts back from the current week`() {
        fun w(day: String) = Workout("1", "$day 10:00:00", "x", 60, emptyList())
        val log = listOf(w("2025-01-01"), w("2025-01-08"), w("2025-01-09"), w("2025-01-22"), w("2025-01-28"))
        assertEquals(2, weekStreak(log, LocalDate.parse("2025-02-01")))
        // Monday of a new week, nothing logged yet: last week's streak still stands.
        assertEquals(2, weekStreak(log, LocalDate.parse("2025-02-03")))
        assertEquals(0, weekStreak(log, LocalDate.parse("2025-02-10")))
        assertEquals(mapOf(LocalDate.parse("2025-01-06") to 3605L, LocalDate.parse("2025-01-08") to 18L), secondsByDay(workouts))
    }

    @Test
    fun `volume leaves out warm-ups and uses the real load`() {
        // Lat Pulldown 45x7 + 40x9 + 30x6 + 42.5x8 + 54.4311x5, curls 7.5x10 + 3.75x12; pull-ups and knee raises weigh nothing as logged.
        val logged = 315 + 360 + 180 + 340 + 272.1555 + 75 + 45
        assertEquals(logged, volumeOf(workouts[0]), 1e-9)
        // Counting pull-ups as bodyweight lifts at 80 kg adds 11 reps x 80.
        val real = volumeOf(workouts[0]) { exercise, weight, _ -> realLoad(if (exercise == "Pull Up") LoadType.BODYWEIGHT else LoadType.NORMAL, weight, 80.0) }
        assertEquals(logged + 880, real, 1e-9)
        // With no weigh-in near the workout the pull-ups have no known load and add nothing.
        val unknown = volumeOf(workouts[0]) { exercise, weight, _ -> realLoad(if (exercise == "Pull Up") LoadType.BODYWEIGHT else LoadType.NORMAL, weight, null) }
        assertEquals(logged, unknown, 1e-9)
        assertEquals(50.0, realLoad(LoadType.ASSISTED, 30.0, 80.0))
        assertEquals(0.0, realLoad(LoadType.ASSISTED, 90.0, 80.0))
        assertNull(realLoad(LoadType.ASSISTED, 30.0, null))
    }

    @Test
    fun `a workout uses the closest weigh-in, and only one within seven days`() {
        val log = Bodyweights(listOf("2025-01-01 08:00:00" to 80.0, "2025-01-10 08:00:00" to 82.0, "2025-03-01 08:00:00" to 85.0))
        assertEquals(80.0, log.at("2025-01-04 18:00:00"))
        assertEquals(82.0, log.at("2025-01-06 18:00:00"))
        // A weigh-in after the workout counts as well as one before it.
        assertEquals(85.0, log.at("2025-02-23 09:00:00"))
        assertNull(log.at("2025-02-01 18:00:00"))
        assertNull(Bodyweights(emptyList()).at("2025-01-04 18:00:00"))
    }

    @Test
    fun `assisted lifts set records by less help and by real load`() {
        fun w(day: String, vararg sets: NewSet) = buildWorkouts(rowsFor(1, day + " 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0]
        val log = listOf(
            w("2025-01-01", NewSet(SetType.NORMAL, 30.0, 8)),
            // Less help: a weight record, and at 80 kg bodyweight 60 x 5 beats 50 x 8 on estimated 1RM (70 against 63.3) but not on volume (300 against 400).
            w("2025-01-02", NewSet(SetType.NORMAL, 20.0, 5)),
            // More help is never a record.
            w("2025-01-03", NewSet(SetType.NORMAL, 40.0, 8)),
        )
        val assisted = { _: String -> LoadType.ASSISTED }
        // The first workout earns 1RM, set volume and reps; less help next time is a better 1RM (70 against 63.3) only.
        // Less help than ever is a weight PR (2026-11-02).
        assertEquals(listOf(3.0, 2.0, 0.0), recordCounts(log, EPLEY_RULE, assisted) { 80.0 }.toSortedMap().values.toList())
        // Without a weigh-in there is no load to compare, only the reps.
        assertEquals(listOf(1.0, 1.0, 0.0), recordCounts(log, EPLEY_RULE, assisted).toSortedMap().values.toList())
    }

    @Test
    fun `records are counted per workout against everything before it`() {
        fun w(day: String, vararg sets: NewSet) = buildWorkouts(rowsFor(1, day + " 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0]
        val counts = recordCounts(
            listOf(
                w("2025-01-01", NewSet(SetType.NORMAL, 40.0, 10)),
                // Heavier and a better estimate (58.3 against 53.3), but 250 of set volume against 400; the warm-up is ignored.
                w("2025-01-02", NewSet(SetType.WARMUP, 100.0, 1), NewSet(SetType.NORMAL, 50.0, 5)),
                w("2025-01-03", NewSet(SetType.NORMAL, 50.0, 5)),
                // Same weight, better estimate and more volume.
                w("2025-01-04", NewSet(SetType.NORMAL, 50.0, 9)),
            ),
            EPLEY_RULE,
        )
        // First: 1RM, set volume, reps. Second: a better estimate only. Third: everything only matched. Fourth: 1RM, set volume and more reps at 50 kg.
        // 50 kg is heavier than any working set before it: a weight PR too (2026-11-02); the 100 kg warm-up is not.
        assertEquals(listOf(3.0, 2.0, 0.0, 3.0), counts.toSortedMap().values.toList())
    }

    @Test
    fun `a PR belongs to the set that set it`() {
        fun w(day: String, vararg sets: NewSet) = buildWorkouts(rowsFor(1, day + " 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0]
        val prs = prsOf(
            listOf(
                w("2025-01-01", NewSet(SetType.NORMAL, 40.0, 10)),
                // The warm-up sets nothing. 50 x 5 is the heaviest and the best estimate (58.3); 45 x 10 has the most volume (450).
                w("2025-01-02", NewSet(SetType.WARMUP, 60.0, 1), NewSet(SetType.NORMAL, 50.0, 5), NewSet(SetType.NORMAL, 45.0, 10)),
            ),
            EPLEY_RULE,
        )
        // An exercise's first workout earns every kind it has a value for.
        assertEquals(listOf(setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS)), prs["2025-01-01 10:00:00"])
        // 45 x 10 estimates 60, which beats 50 x 5 as well, so it takes the 1RM and the volume; its ten reps equal the most before.
        // 50 x 5 is heavier than anything before, but being the heaviest is not a kind of its own.
        assertEquals(listOf(emptySet(), setOf(PrKind.WEIGHT), setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS_MATCHED)), prs["2025-01-02 10:00:00"])
    }
}
