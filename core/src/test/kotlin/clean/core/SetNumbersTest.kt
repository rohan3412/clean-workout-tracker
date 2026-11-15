package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Warm-ups and drop sets take no number; a failure set takes a number's place but shows F (2026-11-02). */
class SetNumbersTest {
    @Test fun `labels skip warm-ups and drop sets`() {
        val types = listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROP, SetType.NORMAL, SetType.FAILURE, SetType.NORMAL, SetType.DROP)
        assertEquals(listOf("W", "1", "D", "2", "F", "4"), setLabels(types).dropLast(1))
        assertEquals("D", setLabels(types).last())
    }

    @Test fun `a saved workout writes the same numbers in its Set Order`() {
        val sets = listOf(NewSet(SetType.WARMUP, 20.0, 10), NewSet(SetType.NORMAL, 60.0, 8), NewSet(SetType.DROP, 40.0, 10), NewSet(SetType.NORMAL, 60.0, 8))
        val rows = rowsFor(1, "2026-11-02 10:00:00", "w", 60, listOf(NewBlock("x", sets)))
        assertEquals(listOf("W", "1", "D", "2"), rows.map { it.setOrder })
    }

    @Test fun `a round of two drop sets takes no number`() {
        assertEquals(false, roundCounts(SetType.DROP, SetType.DROP))
        assertEquals(true, roundCounts(SetType.DROP, SetType.NORMAL))
    }
}
