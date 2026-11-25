package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidesCarryTest {
    private fun set(weight: Double, reps: Int, type: SetType = SetType.NORMAL) =
        SetItem(WorkoutRow("1", "2026-01-01 10:00:00", "w", "60", "x", "1", weight.toString(), reps.toString(), "", "0.0", "0.0", "", ""), type)

    private val L = Side.LEFT
    private val R = Side.RIGHT

    @Test fun pairedToOrdinaryTakesTheHeavierSide() {
        // Round 1: right is heavier; round 2: left is heavier by reps.
        val s = Session("d", listOf(set(20.0, 8), set(24.0, 8), set(20.0, 12), set(20.0, 10)), listOf(L, R, L, R))
        assertEquals(listOf(24.0, 20.0), carriedSets(s, false, L).map { it.weight })
        assertEquals(listOf(8, 12), carriedSets(s, false, L).map { it.reps })
    }

    @Test fun aTieGoesToTheLeft() {
        val l = set(20.0, 10)
        val r = set(20.0, 10)
        val s = Session("d", listOf(l, r), listOf(L, R))
        assertEquals(listOf(l), carriedSets(s, false, R))
        assertEquals(l, heavierSide(Round(l, r)))
    }

    @Test fun aMissingHalfIsTheHalfThatIsThere() {
        val s = Session("d", listOf(set(20.0, 10), set(20.0, 10), set(30.0, 5)), listOf(L, R, L))
        assertEquals(2, roundsOfSession(s).size)
        assertEquals(listOf(10, 5), carriedSets(s, false, L).map { it.reps })
        val lone = Session("d", listOf(set(30.0, 5)), listOf(R))
        assertEquals(listOf(5), carriedSets(lone, false, L).map { it.reps })
    }

    @Test fun aRightFirstRoundIsStillOneRound() {
        val s = Session("d", listOf(set(20.0, 12), set(20.0, 10), set(20.0, 9), set(20.0, 9)), listOf(R, L, L, R))
        assertEquals(2, roundsOfSession(s).size)
        // Round 1: the right set (12 reps) is the heavier and was stored first; round 2 is a tie, so the left (9).
        assertEquals(listOf(12, 9), carriedSets(s, false, L).map { it.reps })
    }

    @Test fun anOrdinaryCardFromAnOrdinarySessionIsUnchanged() {
        val sets = listOf(set(60.0, 8), set(70.0, 5))
        assertEquals(sets, carriedSets(Session("d", sets), false, L))
    }

    @Test fun orderedToPairedGivesBothHalvesSetK() {
        val sets = listOf(set(60.0, 8), set(70.0, 5))
        val s = Session("d", sets)
        assertEquals(sets, carriedSets(s, true, L))
        assertEquals(sets, carriedSets(s, true, R))
    }

    @Test fun pairedToPairedKeepsEachSide() {
        val s = Session("d", listOf(set(20.0, 8), set(24.0, 7), set(20.0, 6), set(24.0, 5)), listOf(L, R, L, R))
        assertEquals(listOf(8, 6), carriedSets(s, true, L).map { it.reps })
        assertEquals(listOf(7, 5), carriedSets(s, true, R).map { it.reps })
    }

    @Test fun kindsStayWithTheirRound() {
        // Warm-up round, working round, drop round: each collapses to its own kind, in order.
        val s = Session("d", listOf(
            set(10.0, 10, SetType.WARMUP), set(10.0, 10, SetType.WARMUP),
            set(20.0, 8), set(22.0, 8),
            set(15.0, 8, SetType.DROP), set(15.0, 6, SetType.DROP),
        ), listOf(L, R, L, R, L, R))
        val c = carriedSets(s, false, L)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROP), c.map { it.type })
        assertEquals(listOf(10.0, 22.0, 15.0), c.map { it.weight })
    }

    @Test fun noSessionCarriesNothing() {
        assertEquals(emptyList(), carriedSets(null, false, L))
        assertEquals(emptyList(), carriedSets(null, true, R))
    }

    @Test fun sameOffersTheSameSideThenTheOtherSideOfTheRoundAbove() {
        val items = List(6) { true } // (0,1) (2,3) (4,5)
        assertEquals(listOf(1, 0), setsAbove(items, 3, true))
        assertEquals(listOf(2, 3), setsAbove(items, 4, true))
        // The first round has nothing above; a right half still has its own left.
        assertEquals(emptyList(), setsAbove(items, 0, true))
        assertEquals(listOf(0), setsAbove(items, 1, true))
        // An ordinary card is the previous set only.
        assertEquals(listOf(1), setsAbove(items, 2, false))
    }
}
