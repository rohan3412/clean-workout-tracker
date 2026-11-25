package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records, progress, PRs and progression per side (docs/left-right.md, Records and progress per side). */
class SideRecordsTest {
    private fun n(weight: Double, reps: Int) = NewSet(SetType.NORMAL, weight, reps)

    /** One workout with the given blocks, all of exercise `x`; a paired block comes after any ordinary one. */
    private fun day(day: String, vararg blocks: NewBlock) =
        buildWorkouts(rowsFor(1, "$day 10:00:00", "w", 60, blocks.toList()))[0]

    private fun ordinary(vararg sets: NewSet) = NewBlock("x", sets.toList())
    private fun paired(vararg sets: NewSet) = NewBlock("x", sets.toList(), paired = true)

    private fun e1rm(w: Double, r: Int) = estimate1Rm(w, r, EPLEY_RULE)!!
    private val rule = EPLEY_RULE

    @Test
    fun `an exercise with no left and right block has two identical series and the old figures`() {
        val log = listOf(day("2025-01-01", ordinary(n(20.0, 10), n(25.0, 8))), day("2025-01-08", ordinary(n(22.5, 10))))
        val sessions = sessionsOf(log, "x")
        assertTrue(sessions.none { it.paired })
        // A hand-made Session means ordinary sets.
        assertEquals(sessions, sessions.map { Session(it.date, it.sets) })
        val both = recordsBySide(sessions, rule)
        assertEquals(both.left, both.right)
        assertEquals(both.left, both.comparable)
        assertEquals(both.comparable, recordsOf(sessions, rule))
        assertEquals(e1rm(25.0, 8), both.comparable.estimated1Rm!!.value)
        val progress = progressBySide(sessions, rule)
        assertTrue(progress.all { it.left == null && it.right == null })
        assertEquals(progressOf(sessions, rule), progress.map { it.comparable })
        assertEquals(25.0, progressOf(sessions, rule)[0].topWeight)
        assertNull(imbalance(progress[0]))
        // The PR rule on one side is the old rule.
        val bests = prBestsBySide(sessions, LoadType.NORMAL, { null }, rule)
        assertEquals(bests.left.estimate, bests.right.estimate)
        assertEquals(bests.left.estimate, prBestsOf(sessions, LoadType.NORMAL, { null }, rule).estimate)
        val first = day("2025-01-01", ordinary(n(20.0, 10)))
        val kinds = prsOf(listOf(first), rule).getValue(first.date)
        assertEquals(listOf(setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS)), kinds)
        assertEquals(3.0, recordCount(first, kinds))
        // The same targets on both sides as nextFor gives.
        val scheme = Progression(8, 12, 2.5)
        val last = sessions.last().sets
        assertEquals(nextFor(last, scheme) to nextFor(last, scheme), nextForSides(last, last, scheme))
    }

    @Test
    fun `older ordinary sessions are in both series, and the comparable figure is the weaker side's`() {
        val log = listOf(
            day("2025-01-01", ordinary(n(20.0, 10))),
            day("2025-01-08", paired(n(20.0, 12), n(20.0, 8))),
            day("2025-01-15", paired(n(20.0, 12), n(20.0, 10))),
        )
        val sessions = sessionsOf(log, "x")
        assertEquals(listOf(false, true, true), sessions.map { it.paired })
        val records = recordsBySide(sessions, rule)
        assertEquals(e1rm(20.0, 12), records.left.estimated1Rm!!.value)
        // The right arm's best is the ordinary session's 20 x 10, which also stands for the left arm there.
        assertEquals(e1rm(20.0, 10), records.right.estimated1Rm!!.value)
        assertEquals(e1rm(20.0, 10), records.comparable.estimated1Rm!!.value)
        assertEquals(12.0, records.left.reps!!.value)
        assertEquals(10.0, records.comparable.reps!!.value)
        assertEquals(listOf(10.0), records.comparable.repsAtWeight.map { it.value })
        assertEquals(records.comparable, recordsOf(sessions, rule))

        val progress = progressBySide(sessions, rule)
        assertNull(progress[0].left)
        assertNull(progress[0].right)
        assertEquals(12, progress[1].left!!.maxReps)
        assertEquals(8, progress[1].right!!.maxReps)
        assertEquals(8, progress[1].comparable.maxReps)
        assertEquals(e1rm(20.0, 8), progress[1].comparable.estimated1Rm)
        assertEquals(progressOf(sessions, rule), progress.map { it.comparable })

        val gap = imbalance(progress[1])!!
        assertEquals(-4.0, gap.reps!!.difference)
        assertEquals(-100.0 / 3, gap.reps!!.percent, 1e-9)
        assertEquals(e1rm(20.0, 8) - e1rm(20.0, 12), gap.oneRm!!.difference, 1e-9)
        assertEquals(Side.RIGHT, gap.lagging)
        assertNull(imbalance(progress[0]))
    }

    @Test
    fun `the weaker arm's PR is recognised and counts half for each set that earned it`() {
        val log = listOf(
            day("2025-01-01", paired(n(30.0, 10), n(20.0, 10))),
            day("2025-01-08", paired(n(25.0, 10), n(22.0, 10))),
        )
        val prs = prsOf(log, rule)
        // A first workout: each side earns what it has a value for.
        val full = setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS)
        assertEquals(listOf(full, full), prs[log[0].date])
        // 22 x 10 beats the right arm's 20 x 10 though 30 x 10 would have hidden it, and 22 kg is the right arm's heaviest
        // (a weight PR since 2026-11-02); 25 x 10 beats nothing on the left.
        assertEquals(
            listOf(setOf(PrKind.REPS_MATCHED), setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS_MATCHED, PrKind.WEIGHT)),
            prs[log[1].date],
        )
        // A round with the same three kinds on both sides counts what one ordinary set does; one side's three kinds count half each.
        assertEquals(3.0, recordCount(log[0], prs.getValue(log[0].date)))
        assertEquals(1.5, recordCount(log[1], prs.getValue(log[1].date)))
        val round = prs.getValue(log[1].date)
        assertEquals(
            listOf(PrKind.ONE_RM to setOf(Side.RIGHT), PrKind.WEIGHT to setOf(Side.RIGHT), PrKind.VOLUME to setOf(Side.RIGHT), PrKind.REPS_MATCHED to setOf(Side.LEFT, Side.RIGHT)),
            sidesOf(round[0], round[1]),
        )
        val counts = recordCounts(log, rule)
        assertEquals(listOf(3.0, 1.5), counts.toSortedMap().values.toList())
    }

    @Test
    fun `weight is one badge per side, and a kind both sides earned counts one`() {
        val log = listOf(
            day("2025-01-01", ordinary(n(20.0, 10), n(25.0, 8))),
            day("2025-01-08", paired(n(20.0, 11), n(20.0, 11), n(25.0, 9), n(25.0, 9))),
        )
        val kinds = prsOf(log, rule).getValue(log[1].date)
        // The heavier weight of each side carries its "weight" badge.
        assertEquals(listOf(false, false, true, true), kinds.map { PrKind.WEIGHT in it })
        // Each set of a paired block counts half, so a kind both sides earned is 1.
        val real = kinds.sumOf { it.records() }
        assertEquals(real / 2.0, recordCount(log[1], kinds))
    }

    @Test
    fun `a matched kind is shown by side and stays out of counts`() {
        val log = listOf(day("2025-01-01", ordinary(n(20.0, 10))), day("2025-01-08", paired(n(20.0, 10), n(20.0, 12))))
        val kinds = prsOf(log, rule).getValue(log[1].date)
        assertTrue(PrKind.ONE_RM_MATCHED in kinds[0] && PrKind.ONE_RM !in kinds[0])
        assertEquals(0, kinds[0].records())
        assertTrue(PrKind.ONE_RM in kinds[1])
        // ONE_RM, VOLUME, REPS and WEIGHT, half each.
        assertEquals(2.0, recordCount(log[1], kinds))
        val shown = sidesOf(kinds[0], kinds[1]).toMap()
        assertEquals(setOf(Side.RIGHT), shown[PrKind.ONE_RM])
        assertEquals(setOf(Side.LEFT), shown[PrKind.ONE_RM_MATCHED])
    }

    @Test
    fun `an ordinary set in a left and right workout is judged against the stricter bests and updates both sides`() {
        val log = listOf(
            day("2025-01-01", paired(n(30.0, 10), n(20.0, 10))),
            // The ordinary block first: a block of the same exercise right after a paired one would join it.
            day("2025-01-08", ordinary(n(25.0, 10)), paired(n(20.0, 10), n(20.0, 10))),
            day("2025-01-15", paired(n(30.0, 10), n(25.0, 10))),
        )
        val prs = prsOf(log, rule)
        // 25 x 10 would be a PR on the right arm alone; against the stricter (left's 30 x 10) it earns nothing but a matched rep best.
        val second = prs.getValue(log[1].date)
        assertEquals(setOf(PrKind.REPS_MATCHED), second[0])
        // It then updated the right side: 25 x 10 on the right now only equals that.
        val third = prs.getValue(log[2].date)
        assertTrue(PrKind.ONE_RM_MATCHED in third[1] && PrKind.ONE_RM !in third[1])
        assertTrue(PrKind.ONE_RM_MATCHED in third[0])
    }

    @Test
    fun `a lone left set is half a round, judged on the left`() {
        val log = listOf(day("2025-01-01", ordinary(n(20.0, 10))), day("2025-01-08", paired(n(25.0, 10))))
        val kinds = prsOf(log, rule).getValue(log[1].date)
        // 25 kg is heavier than any set before on the left: a weight PR too (2026-11-02).
        assertEquals(setOf(PrKind.ONE_RM, PrKind.VOLUME, PrKind.REPS_MATCHED, PrKind.WEIGHT), kinds[0])
        assertEquals(1.5, recordCount(log[1], kinds))
        val progress = progressBySide(sessionsOf(log, "x"), rule)[1]
        assertNotNull(progress.left)
        // The right series that day has no set; the comparable is the left's.
        assertNull(progress.right!!.maxReps)
        assertEquals(progress.left, progress.comparable)
    }

    @Test
    fun `a goal needs both sides`() {
        val log = listOf(
            day("2025-01-01", paired(n(100.0, 5), n(90.0, 5))),
            day("2025-01-08", paired(n(100.0, 5), n(100.0, 5))),
        )
        assertEquals(log[1].date, goalReachedOn(sessionsOf(log, "x"), 100.0, 5, rule, LoadType.NORMAL) { null })
        // Above the rule's rep limit: a set of the weight for the reps, on each side.
        assertEquals(log[1].date, goalReachedOn(sessionsOf(log, "x"), 100.0, 5, OneRm(OneRmFormula.EPLEY, 3), LoadType.NORMAL) { null })
    }

    @Test
    fun `each side is progressed from its own last session and the weight steps only when both reached the top`() {
        val scheme = Progression(8, 12, 2.5)
        fun session(vararg sets: NewSet) = sessionsOf(listOf(day("2025-01-01", paired(*sets))), "x")[0]
        fun targets(vararg sets: NewSet) = session(*sets).let { nextForSides(it.forSide(Side.LEFT).sets, it.forSide(Side.RIGHT).sets, scheme) }

        // Left at the top, right one short: nobody steps; the left repeats, the right adds a rep.
        assertEquals(
            listOf(Target(20.0, 12), Target(20.0, 12)) to listOf(Target(20.0, 11), Target(20.0, 11)),
            targets(n(20.0, 12), n(20.0, 10), n(20.0, 12), n(20.0, 10)),
        )
        // Both at the top: both step.
        assertEquals(
            listOf(Target(22.5, 8), Target(22.5, 8)) to listOf(Target(22.5, 8), Target(22.5, 8)),
            targets(n(20.0, 12), n(20.0, 12), n(20.0, 12), n(20.0, 12)),
        )
        // Each from its own weight.
        assertEquals(
            listOf(Target(22.5, 8)) to listOf(Target(17.5, 8)),
            targets(n(20.0, 12), n(15.0, 12)),
        )
    }
}
