package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetTargetsTest {
    private val range = SetRange(10, 20)

    @Test
    fun `under within and over with both ends inside`() {
        assertEquals(RangeState.UNDER, weeklySetState(9.5, range))
        assertEquals(RangeState.WITHIN, weeklySetState(10.0, range))
        assertEquals(RangeState.WITHIN, weeklySetState(20.0, range))
        assertEquals(RangeState.OVER, weeklySetState(20.25, range))
        assertEquals(RangeState.UNDER, weeklySetState(0.0, range))
    }

    @Test
    fun `a muscle's own range wins off means none and the default is 10 to 20`() {
        assertEquals(SetRange(10, 20), setRangeFor(null, null))
        assertEquals(SetRange(12, 16), setRangeFor(null, "12-16"))
        assertEquals(SetRange(6, 9), setRangeFor("6-9", "12-16"))
        assertNull(setRangeFor("off", "12-16"))
        // Unreadable text falls back.
        assertEquals(SetRange(12, 16), setRangeFor("x", "12-16"))
        assertEquals(SetRange(10, 20), setRangeFor(null, "20-10"))
        assertEquals("6-9", SetRange(6, 9).encode())
    }
}
