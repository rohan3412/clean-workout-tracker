package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaynightTest {
    @Test
    fun `clock text round trips`() {
        assertEquals(6 * 60 + 30, parseClock("06:30"))
        assertEquals("18:05", clockText(18 * 60 + 5))
        assertNull(parseClock("25:00"))
        assertNull(parseClock("6"))
    }

    @Test
    fun `day may run past midnight`() {
        assertTrue(isDaytime(12 * 60, 6 * 60 + 30, 18 * 60 + 30))
        assertTrue(!isDaytime(19 * 60, 6 * 60 + 30, 18 * 60 + 30))
        assertTrue(isDaytime(1 * 60, 20 * 60, 5 * 60))
        assertTrue(!isDaytime(12 * 60, 20 * 60, 5 * 60))
    }

    @Test
    fun `sun times at 52 degrees north in midsummer and midwinter`() {
        val (rise, set) = assertNotNull(sunTimes(LocalDate.parse("2025-06-21"), 52.0, 0.0, 0))
        assertTrue(rise in 3 * 60 + 30..3 * 60 + 55, "rise ${clockText(rise)}")
        assertTrue(set in 20 * 60 + 5..20 * 60 + 35, "set ${clockText(set)}")
        val (winterRise, winterSet) = assertNotNull(sunTimes(LocalDate.parse("2025-12-21"), 52.0, 0.0, 0))
        assertTrue(winterRise in 7 * 60 + 50..8 * 60 + 20, "rise ${clockText(winterRise)}")
        assertTrue(winterSet in 15 * 60 + 40..16 * 60 + 10, "set ${clockText(winterSet)}")
    }

    @Test
    fun `time zone shifts the local times`() {
        val utc = assertNotNull(sunTimes(LocalDate.parse("2025-03-20"), 0.0, 0.0, 0))
        val plusTwo = assertNotNull(sunTimes(LocalDate.parse("2025-03-20"), 0.0, 0.0, 120))
        assertEquals(utc.first + 120, plusTwo.first)
    }

    @Test
    fun `polar day and night have no sun times and fall back to the set times`() {
        assertNull(sunTimes(LocalDate.parse("2025-06-21"), 80.0, 0.0, 0))
        assertNull(sunTimes(LocalDate.parse("2025-12-21"), 80.0, 0.0, 0))
        assertEquals(DEFAULT_DAY_FROM to DEFAULT_NIGHT_FROM, dayWindow(SWITCH_SUN, LocalDate.parse("2025-06-21"), 80.0, 0, 0, 0))
        assertEquals(300 to 1200, dayWindow(SWITCH_TIMES, LocalDate.parse("2025-06-21"), 52.0, 0, 300, 1200))
    }
}
