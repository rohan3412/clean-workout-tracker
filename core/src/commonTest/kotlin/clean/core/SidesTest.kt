package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidesTest {
    private fun sets(n: Int) = List(n) { true }

    @Test
    fun `sets are paired in order and an odd one is left without a partner`() {
        assertEquals(emptyList(), sidePairs(sets(0)))
        assertEquals(listOf(0 to null), sidePairs(sets(1)))
        assertEquals(listOf(0 to 1), sidePairs(sets(2)))
        assertEquals(listOf(0 to 1, 2 to null), sidePairs(sets(3)))
        assertEquals(listOf(0 to 1, 2 to 3), sidePairs(sets(4)))
    }

    @Test
    fun `notes are skipped when pairing`() {
        // note, set, note, set, set
        val items = listOf(false, true, false, true, true)
        assertEquals(listOf(1 to 3, 4 to null), sidePairs(items))
        assertEquals(1 to 3, pairHolding(items, 3))
        assertNull(pairHolding(items, 0))
    }

    @Test
    fun `removing a pair takes both of its sets`() {
        val items = listOf(true, true, false, true, true, true)
        assertEquals(listOf(0, 1), pairIndices(items, 1))
        assertEquals(listOf(3, 4), pairIndices(items, 3))
        assertEquals(listOf(5), pairIndices(items, 5))
        assertEquals(emptyList(), pairIndices(items, 2))
    }

    @Test
    fun `adding a pair completes an odd card first`() {
        assertEquals(2, setsToAddPair(0))
        assertEquals(1, setsToAddPair(3))
        assertEquals(2, setsToAddPair(4))
        assertEquals(0, setsToCompletePairs(4))
        assertEquals(1, setsToCompletePairs(5))
    }

    @Test
    fun `only the first tick of a pair starts the rest between sides`() {
        assertTrue(startsSidesRest(hasPartner = true, partnerDone = false))
        assertFalse(startsSidesRest(hasPartner = true, partnerDone = true))
        assertFalse(startsSidesRest(hasPartner = false, partnerDone = false))
    }

    @Test
    fun `a template keeps one set kind per pair`() {
        val types = listOf(SetType.WARMUP, SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.DROP, SetType.DROP)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROP), pairTypes(types))
        // A pair counts when either half was done; an undone pair is left out.
        assertEquals(listOf(SetType.WARMUP, SetType.DROP), pairTypes(types, listOf(true, false, false, false, false, true)))
        assertEquals(listOf(SetType.NORMAL), pairTypes(listOf(SetType.NORMAL), listOf(true)))
    }
}
