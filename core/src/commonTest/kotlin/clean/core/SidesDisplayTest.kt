package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a finished left and right block shows: its best round, a round as a line, how far a side lagged. */
class SidesDisplayTest {
    private fun block(vararg sets: NewSet, order: List<Side>? = null): Block =
        buildWorkouts(rowsFor(1, "2025-03-01 10:00:00", "w", 3600, listOf(NewBlock("Curl", sets.toList(), paired = true, shapes = order?.let(::shapesOf)))))[0].blocks[0]

    private fun set(weight: Double, reps: Int, type: SetType = SetType.NORMAL) = NewSet(type, weight, reps)
    private val describe = { s: SetItem -> "${shortNumber(s.weight ?: 0.0)} kg × ${s.reps}" }

    @Test
    fun `the best round is the one whose weaker side is best`() {
        // Round 1: 20 x 12 and 20 x 10; round 2: 30 x 12 and 10 x 5 (a big left, a poor right); round 3: 20 x 11 and 20 x 11.
        val b = block(set(20.0, 12), set(20.0, 10), set(30.0, 12), set(10.0, 5), set(20.0, 11), set(20.0, 11))
        val best = bestRound(b.rounds)!!
        assertEquals(11, best.left?.reps)
        assertEquals(11, best.right?.reps)
    }

    @Test
    fun `equal weaker sides go to the heavier total and a lone set ranks below a complete round`() {
        val b = block(set(20.0, 10), set(20.0, 10), set(25.0, 10), set(20.0, 10), set(40.0, 12))
        // Round 2 is heavier than round 1 on the same weaker side; round 3 is a lone left set, however heavy.
        assertEquals(25.0, bestRound(b.rounds)!!.left?.weight)
        assertEquals(40.0, bestRound(block(set(40.0, 12)).rounds)!!.left?.weight)
    }

    @Test
    fun `warm-up rounds are never the best`() {
        val b = block(set(10.0, 10, SetType.WARMUP), set(10.0, 10, SetType.WARMUP))
        assertNull(bestRound(b.rounds))
        assertNull(bestRound(emptyList()))
    }

    @Test
    fun `a round is one line with a dash for a half not done`() {
        val b = block(set(20.0, 12), set(20.0, 10), set(20.0, 8))
        assertEquals("L 20 kg × 12 · R 20 kg × 10", roundLine(b.rounds[0], describe))
        assertEquals("L 20 kg × 8 · R –", roundLine(b.rounds[1], describe))
        val onlyRight = block(set(20.0, 7), order = listOf(Side.RIGHT))
        assertEquals("L – · R 20 kg × 7", roundLine(onlyRight.rounds[0], describe))
    }

    @Test
    fun `rounds are numbered past warm-ups and keep the letter of their kind`() {
        val b = block(set(10.0, 10, SetType.WARMUP), set(10.0, 10, SetType.WARMUP), set(20.0, 10), set(20.0, 10), set(20.0, 8, SetType.FAILURE), set(20.0, 8, SetType.FAILURE), set(20.0, 10), set(20.0, 10))
        assertEquals(listOf("W", "1", "F", "3"), roundLabels(b.rounds))
    }

    @Test
    fun `the lagging side is named by reps when only reps differ and by percent when the weight does`() {
        val rule = EPLEY_RULE
        // 20 x 12 left, 20 x 10 right: the 1RM estimate decides, so the caption is a percentage.
        val byWeightAndReps = imbalanceOf(block(set(20.0, 12), set(20.0, 10)), "2025-03-01 10:00:00", rule)!!
        assertEquals(Side.RIGHT, byWeightAndReps.lagging)
        assertEquals(false, byWeightAndReps.byReps)
        assertEquals("Right −${byWeightAndReps.percent} %", byWeightAndReps.caption())
        // Bodyweight reps with no weight have no 1RM or volume: reps decide, and the caption says reps.
        val plain = imbalanceOf(block(NewSet(SetType.NORMAL, null, 12), NewSet(SetType.NORMAL, null, 10)), "2025-03-01 10:00:00", rule)!!
        assertEquals(true, plain.byReps)
        assertEquals("Right −2 reps", plain.caption())
        assertEquals("Curl: right lagged by 2 reps (−17 %)", plain.sentence("Curl"))
    }

    @Test
    fun `equal sides and ordinary blocks have no imbalance`() {
        val rule = EPLEY_RULE
        assertNull(imbalanceOf(block(set(20.0, 10), set(20.0, 10)), "2025-03-01 10:00:00", rule))
        val plain = buildWorkouts(rowsFor(1, "2025-03-01 10:00:00", "w", 3600, listOf(NewBlock("Curl", listOf(set(20.0, 10), set(20.0, 8))))))[0].blocks[0]
        assertNull(imbalanceOf(plain, "2025-03-01 10:00:00", rule))
    }

    @Test
    fun `a left that lagged is named left`() {
        val note = imbalanceOf(block(set(20.0, 8), set(20.0, 10)), "2025-03-01 10:00:00", EPLEY_RULE)!!
        assertEquals(Side.LEFT, note.lagging)
        assertEquals("Left", note.caption().substringBefore(' '))
    }

    private fun sessionOf(date: String, vararg sets: NewSet, order: List<Side>? = null): Session {
        val b = block(*sets, order = order)
        return Session(date, b.sets, b.setSides)
    }

    @Test
    fun `two sides are one quiet line a missing side a dash`() {
        assertEquals("L 100 kg · R 92 kg", sidesLine("100 kg", "92 kg"))
        assertEquals("L 12 · R –", sidesLine("12", null))
        assertNull(sidesLine(null, null))
        assertEquals("Left 100 kg, right 92 kg", sidesSpoken("100 kg", "92 kg"))
        assertEquals("Left 12, right not done", sidesSpoken("12", null))
    }

    @Test
    fun `the gap is written in reps and percent where reps decided it`() {
        val rule = EPLEY_RULE
        val reps = imbalanceOf(block(NewSet(SetType.NORMAL, null, 12), NewSet(SetType.NORMAL, null, 10)), "2025-03-01 10:00:00", rule)!!
        assertEquals("Right −2 reps · −17 %", reps.detail())
        val weight = imbalanceOf(block(set(20.0, 12), set(20.0, 10)), "2025-03-01 10:00:00", rule)!!
        assertEquals(weight.caption(), weight.detail())
    }

    @Test
    fun `the latest imbalance is the latest paired session with two sides to compare`() {
        val rule = EPLEY_RULE
        val sessions = listOf(
            sessionOf("2025-03-01 10:00:00", set(20.0, 12), set(20.0, 10)),
            sessionOf("2025-03-08 10:00:00", set(20.0, 12), set(20.0, 12)),
            // Only a left half was done: nothing to compare, so the session before it is the latest.
            sessionOf("2025-03-15 10:00:00", set(20.0, 12)),
            Session("2025-03-22 10:00:00", listOf(block(set(20.0, 10)).sets[0])),
        )
        val (date, note) = latestImbalance(sessions, rule)!!
        assertEquals("2025-03-08 10:00:00", date)
        assertNull(note)
        val older = latestImbalance(sessions.take(1), rule)!!
        assertEquals(Side.RIGHT, older.second!!.lagging)
        assertNull(latestImbalance(sessions.takeLast(2), rule))
    }

    @Test
    fun `rounds logged weigh a round as one and a lone side as a half`() {
        val sessions = listOf(
            sessionOf("2025-03-01 10:00:00", set(10.0, 10, SetType.WARMUP), set(10.0, 10, SetType.WARMUP), set(20.0, 12), set(20.0, 10), set(20.0, 8)),
            Session("2025-03-08 10:00:00", listOf(block(set(20.0, 10)).sets[0])),
        )
        assertEquals(1.5, roundsLogged(sessions))
        assertEquals("1.5 rounds", roundsText(1.5))
        assertEquals("1 round", roundsText(1.0))
    }

    @Test
    fun `each side has its own estimated 1RM and a half not done has none`() {
        val b = block(set(10.0, 10, SetType.WARMUP), set(10.0, 10, SetType.WARMUP), set(60.0, 1), set(50.0, 1), set(40.0, 6))
        val (left, right) = sideOneRms("2025-03-01 10:00:00", b, EPLEY_RULE)
        // Left: 60 x 1 = 60 and 40 x 6 = 48, so 60. Right: 50 x 1 = 50 (the warm-up never counts).
        assertEquals(60.0, left!!, 0.001)
        assertEquals(50.0, right!!, 0.001)
        val onlyLeft = block(set(60.0, 1))
        assertNull(sideOneRms("2025-03-01 10:00:00", onlyLeft, EPLEY_RULE).second)
    }
}
