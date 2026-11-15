package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrRuleTest {
    private fun w(day: String, vararg sets: NewSet) =
        buildWorkouts(rowsFor(1, "$day 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0]

    private fun hold(seconds: Double, weight: Double? = null) = NewSet(SetType.NORMAL, weight, seconds = seconds)

    private fun kinds(log: List<Workout>, type: LoadType = LoadType.TIMED) =
        prsOf(log, EPLEY_RULE, { type }).toSortedMap().values.map { it.flatten().toSet() }

    @Test
    fun `a first hold earns duration, a longer one earns it again, an equal one is matched`() {
        val log = listOf(w("2025-01-01", hold(30.0)), w("2025-01-02", hold(40.0)), w("2025-01-03", hold(40.0)), w("2025-01-04", hold(35.0)))
        assertEquals(listOf(setOf(PrKind.DURATION), setOf(PrKind.DURATION), setOf(PrKind.DURATION_MATCHED), emptySet()), kinds(log))
    }

    @Test
    fun `an empty Reps column and a Reps of 0 both make a hold`() {
        val log = listOf(w("2025-01-01", NewSet(SetType.NORMAL, seconds = 30.0)), w("2025-01-02", NewSet(SetType.NORMAL, reps = 0, seconds = 45.0)))
        assertEquals(listOf(setOf(PrKind.DURATION), setOf(PrKind.DURATION)), kinds(log))
        // Not only for exercises logged as timed.
        assertEquals(listOf(setOf(PrKind.DURATION), setOf(PrKind.DURATION)), kinds(log, LoadType.NORMAL))
    }

    @Test
    fun `weight is earned by a longer hold at a weight held before, or a heavier weight than ever`() {
        // Unweighted, with a weight of 0: never.
        assertEquals(listOf(setOf(PrKind.DURATION), setOf(PrKind.DURATION)), kinds(listOf(w("2025-01-01", hold(30.0)), w("2025-01-02", hold(40.0)))))
        assertEquals(listOf(setOf(PrKind.DURATION), setOf(PrKind.DURATION)), kinds(listOf(w("2025-01-01", hold(30.0, 0.0)), w("2025-01-02", hold(40.0, 0.0)))))
        val log = listOf(
            w("2025-01-01", hold(30.0, 10.0)),
            // A heavier weight than ever held: a weight PR (2026-11-02), though shorter than the longest.
            w("2025-01-02", hold(20.0, 15.0)),
            // Longer than ever at 10 kg, and the longest overall.
            w("2025-01-03", hold(35.0, 10.0)),
            // Equal at 10 kg.
            w("2025-01-04", hold(35.0, 10.0)),
        )
        assertEquals(
            listOf(setOf(PrKind.DURATION), setOf(PrKind.WEIGHT), setOf(PrKind.DURATION, PrKind.WEIGHT), setOf(PrKind.DURATION_MATCHED, PrKind.WEIGHT_MATCHED)),
            kinds(log),
        )
    }

    @Test
    fun `a workout carries one weight badge per exercise, a PR over a match and the heaviest first`() {
        fun lift(w: Double, r: Int) = NewSet(SetType.NORMAL, w, r)
        // 95 kg and 80 kg were both done before: 6 and 10 reps.
        val before = listOf(w("2025-01-01", lift(95.0, 6), lift(80.0, 10)))
        fun weightBadges(vararg today: NewSet) =
            kinds(before + w("2025-01-02", *today), LoadType.NORMAL).last().filter { it == PrKind.WEIGHT || it == PrKind.WEIGHT_MATCHED }
        // A PR at 95 and a match at 80: only the PR.
        assertEquals(listOf(PrKind.WEIGHT), weightBadges(lift(80.0, 10), lift(95.0, 8)))
        // Two matches: one badge, the heavier's.
        assertEquals(listOf(PrKind.WEIGHT_MATCHED), weightBadges(lift(95.0, 6), lift(80.0, 10)))
        // Two PRs: one badge.
        assertEquals(listOf(PrKind.WEIGHT), weightBadges(lift(80.0, 11), lift(95.0, 7)))
        // Which set carries it: the heavier weight's.
        val day = prsOf(before + w("2025-01-02", lift(80.0, 11), lift(95.0, 7)), EPLEY_RULE, { LoadType.NORMAL }).entries.last().value
        assertEquals(false, PrKind.WEIGHT in day[0])
        assertEquals(true, PrKind.WEIGHT in day[1])
    }

    @Test
    fun `a lift with seconds and reps is not a hold, and cardio has no holds`() {
        assertEquals(listOf(setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS)), kinds(listOf(w("2025-01-01", NewSet(SetType.NORMAL, 40.0, 10, seconds = 30.0))), LoadType.NORMAL))
        assertEquals(listOf(emptySet()), kinds(listOf(w("2025-01-01", NewSet(SetType.NORMAL, distanceMeters = 1000.0, seconds = 300.0))), LoadType.CARDIO))
        // Warm-ups and drop sets set nothing.
        assertEquals(listOf(emptySet()), kinds(listOf(w("2025-01-01", NewSet(SetType.WARMUP, seconds = 30.0), NewSet(SetType.DROP, seconds = 40.0)))))
    }

    @Test
    fun `records keep the longest hold, overall and at each weight held`() {
        fun session(date: String, vararg sets: NewSet) = Session(date, buildWorkouts(rowsFor(1, date, "w", 60, listOf(NewBlock("x", sets.toList()))))[0].blocks[0].sets)
        val records = recordsOf(
            listOf(
                session("2025-01-01 10:00:00", hold(30.0), hold(50.0, 10.0)),
                session("2025-01-08 10:00:00", hold(40.0, 10.0), hold(20.0, 20.0), NewSet(SetType.WARMUP, seconds = 99.0)),
            ),
            EPLEY_RULE, LoadType.TIMED,
        )
        assertEquals(Record(50.0, 10.0, null, "2025-01-01 10:00:00"), records.longest)
        assertEquals(listOf(20.0 to 20.0, 10.0 to 50.0), records.longestAtWeight.map { it.weight to it.value })
        assertNull(recordsOf(listOf(session("2025-01-01 10:00:00", NewSet(SetType.NORMAL, 40.0, 5))), EPLEY_RULE).longest)
    }

    @Test
    fun `the badges while logging are the badges saved for the same sets`() {
        val earlier = listOf(
            w("2025-01-01", NewSet(SetType.NORMAL, 40.0, 10), NewSet(SetType.NORMAL, 50.0, 5)),
            w("2025-01-08", NewSet(SetType.NORMAL, 45.0, 10)),
        )
        val tonight = listOf(
            NewSet(SetType.WARMUP, 20.0, 5),
            NewSet(SetType.NORMAL, 45.0, 10),
            // Two equal sets: only the first can be the one that matched or set the record.
            NewSet(SetType.NORMAL, 45.0, 10),
            NewSet(SetType.NORMAL, 55.0, 4),
            NewSet(SetType.NORMAL, 55.0, 4),
            NewSet(SetType.DROP, 30.0, 20),
        )
        val saved = prsOf(earlier + w("2025-01-15", *tonight.toTypedArray()), EPLEY_RULE)["2025-01-15 10:00:00"]!!

        val bests = prBestsOf(sessionsOf(earlier, "x"), LoadType.NORMAL, { null }, EPLEY_RULE)
        val before = bests.copy()
        val live = judgePrs(bests, tonight.map { PrSet(it.type, it.weightKg, it.reps, it.seconds) }, LoadType.NORMAL, null, EPLEY_RULE, commit = false)
        assertEquals(saved, live)
        // 55 x 4 is a new best estimate and heavier than ever, so it carries the workout's one weight badge (a PR over the 45 kg match);
        // the first 45 x 10 only equals its volume and reps, and its twin earns nothing.
        assertEquals(listOf(emptySet(), setOf(PrKind.VOLUME_MATCHED, PrKind.REPS_MATCHED), emptySet(), setOf(PrKind.ONE_RM, PrKind.WEIGHT), emptySet(), emptySet()), saved)
        // Nothing was folded in.
        assertEquals(before.estimate, bests.estimate)
        assertEquals(before.atWeight, bests.atWeight)
    }

    @Test
    fun `the same holds judged live and saved agree`() {
        val earlier = listOf(w("2025-01-01", hold(30.0, 10.0)))
        val tonight = listOf(hold(30.0, 10.0), hold(40.0, 10.0), hold(40.0, 10.0))
        val saved = prsOf(earlier + w("2025-01-08", *tonight.toTypedArray()), EPLEY_RULE, { LoadType.TIMED })["2025-01-08 10:00:00"]!!
        val bests = prBestsOf(sessionsOf(earlier, "x"), LoadType.TIMED, { null }, EPLEY_RULE)
        val live = judgePrs(bests, tonight.map { PrSet(it.type, it.weightKg, it.reps, it.seconds) }, LoadType.TIMED, null, EPLEY_RULE, commit = false)
        assertEquals(saved, live)
    }

    private fun weightKinds(log: List<Workout>, type: LoadType = LoadType.NORMAL) =
        prsOf(log, EPLEY_RULE, { type }).toSortedMap().values.map { sets -> sets.flatten().filter { it == PrKind.WEIGHT || it == PrKind.WEIGHT_MATCHED }.toSet() }

    @Test
    fun `a weight heavier than any before is a weight PR, a tie is not a new one`() {
        fun lift(w: Double, r: Int, type: SetType = SetType.NORMAL) = NewSet(type, w, r)
        val log = listOf(
            w("2025-01-01", lift(60.0, 8)),
            // Heavier than ever, at fewer reps: a weight PR (it earns no other kind).
            w("2025-01-02", lift(65.0, 3)),
            // The same heaviest weight for the same reps: matched, not a new PR.
            w("2025-01-03", lift(65.0, 3)),
            // Heavier only as a warm-up or a drop set: nothing.
            w("2025-01-04", lift(70.0, 2, SetType.WARMUP), lift(40.0, 2)),
            w("2025-01-05", lift(70.0, 2, SetType.DROP), lift(40.0, 2)),
            // Heavier as a working set again.
            w("2025-01-06", lift(62.5, 1), lift(67.5, 1)),
        )
        assertEquals(
            // The 40 kg of the fifth day is a match of the fourth's (the drop set's 70 kg does not count).
            listOf(emptySet(), setOf(PrKind.WEIGHT), setOf(PrKind.WEIGHT_MATCHED), emptySet(), setOf(PrKind.WEIGHT_MATCHED), setOf(PrKind.WEIGHT)),
            weightKinds(log),
        )
    }

    @Test
    fun `an assisted lift's weight PR is less assistance than ever`() {
        val log = listOf(w("2025-01-01", NewSet(SetType.NORMAL, 30.0, 8)), w("2025-01-02", NewSet(SetType.NORMAL, 25.0, 3)), w("2025-01-03", NewSet(SetType.NORMAL, 35.0, 3)))
        assertEquals(listOf(emptySet(), setOf(PrKind.WEIGHT), emptySet()), weightKinds(log, LoadType.ASSISTED))
    }
}
