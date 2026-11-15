package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusModeTest {
    @Test
    fun opensOnTheFirstExerciseWithAnOpenSet() {
        assertEquals(1, focusStart(listOf(false, true, true)))
        assertEquals(2, focusStart(listOf(false, false, false)))
        assertEquals(0, focusStart(emptyList()))
    }

    @Test
    fun movesOnToTheNextOpenExerciseThenWraps() {
        assertEquals(2, focusAfter(listOf(false, false, true, true), 0))
        assertEquals(0, focusAfter(listOf(true, false, false), 2))
        assertEquals(3, focusAfter(listOf(false, false, false, true), 1))
        assertNull(focusAfter(listOf(false, false), 0))
        assertNull(focusAfter(emptyList(), 0))
    }
}
