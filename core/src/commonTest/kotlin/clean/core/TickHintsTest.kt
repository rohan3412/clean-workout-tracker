package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TickHintsTest {
    @Test fun emptyBoxesTakeTheirHints() {
        assertEquals(Accepted("60", "8"), acceptHints("", "", "60", "8", LoadType.NORMAL))
    }

    @Test fun typedValuesAreKept() {
        assertEquals(Accepted("62.5", "8"), acceptHints("62.5", "", "60", "8", LoadType.NORMAL))
        assertEquals(Accepted("60", "10"), acceptHints("", "10", "60", "8", LoadType.NORMAL))
    }

    @Test fun noRepsAnywhereCannotBeTicked() {
        assertNull(acceptHints("60", "", "", "", LoadType.NORMAL))
        assertNull(acceptHints("", "", "60", "", LoadType.TIMED))
    }

    @Test fun bodyweightKeepsAnEmptyWeight() {
        assertEquals(Accepted("", "12"), acceptHints("", "", "", "12", LoadType.BODYWEIGHT))
    }

    @Test fun timedAndCardioTakeDecimals() {
        assertEquals(Accepted("", "7.5"), acceptHints("", "", "", "7,5".replace(',', '.'), LoadType.CARDIO))
        assertEquals(Accepted("5", "30"), acceptHints("", "", "5", "30", LoadType.TIMED))
        assertNull(acceptHints("", "", "", "7.5", LoadType.NORMAL))
    }

    @Test fun aboveInAnOrdinaryCardSkipsNotes() {
        val items = listOf(true, false, true)
        assertEquals(0, setAbove(items, 2, false))
        assertNull(setAbove(items, 0, false))
    }

    @Test fun aboveInAPairedCardIsTheSameSide() {
        val items = List(6) { true } // rounds: (0,1) (2,3) (4,5)
        assertNull(setAbove(items, 0, true))
        assertEquals(0, setAbove(items, 1, true))
        assertEquals(0, setAbove(items, 2, true))
        assertEquals(1, setAbove(items, 3, true))
        assertEquals(3, setAbove(items, 5, true))
    }

    @Test fun aLoneLeftHalfHasNoRightAboveIt() {
        val items = List(3) { true } // (0,1) (2,-)
        assertEquals(0, setAbove(items, 2, true))
    }
}
