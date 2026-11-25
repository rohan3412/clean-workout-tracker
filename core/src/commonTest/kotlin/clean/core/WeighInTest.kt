package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class WeighInTest {
    @Test
    fun `the pad types digits and one point`() {
        var t = ""
        for (k in listOf("8", "2", ".", "5", ".", "5")) t = padKey(t, k, fresh = t.isEmpty() && k == "8")
        assertEquals("82.55", t)
        assertEquals("82.5", padKey("82.55", "back", false))
        assertEquals("5", padKey("0", "5", false))
        assertEquals("0.", padKey("", ".", false))
        assertEquals("123456", padKey("123456", "7", false))
    }

    @Test
    fun `the first key after the pad opens replaces the number`() {
        assertEquals("7", padKey("82.50", "7", fresh = true))
        assertEquals("0.", padKey("82.50", ".", fresh = true))
        assertEquals("", padKey("82.50", "back", fresh = true))
    }

    @Test
    fun `a typed weight is rounded to the ruler's step and kept in its range`() {
        assertEquals(82.55, padValue("82.55", 20.0, 400.0, 20))
        assertEquals(82.55, padValue("82.56", 20.0, 400.0, 20))
        assertEquals(82.5, padValue("82,5", 20.0, 400.0, 20))
        assertEquals(82.0, padValue("82.", 20.0, 400.0, 20))
        assertEquals(20.0, padValue("3", 20.0, 400.0, 20))
        assertEquals(400.0, padValue("999", 20.0, 400.0, 20))
        assertNull(padValue("", 20.0, 400.0, 20))
        assertNull(padValue(".", 20.0, 400.0, 20))
    }

    @Test
    fun `the last seven days start with today and yesterday`() {
        val chips = recentDays(LocalDate.parse("2026-11-19")) // a Thursday
        assertEquals(listOf("Today", "Yesterday", "Tue 17", "Mon 16", "Sun 15", "Sat 14", "Fri 13"), chips.map { it.label })
        assertEquals(LocalDate.parse("2026-11-13"), chips.last().day)
    }

    @Test
    fun `a month grid starts on Monday and pads to whole weeks`() {
        val grid = monthGrid(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertTrue(grid.all { it.size == 7 })
        assertEquals(List(6) { null } + LocalDate.parse("2026-11-01"), grid.first())
        assertEquals(LocalDate.parse("2026-11-30"), grid.last().filterNotNull().last())
        assertEquals(30, grid.flatten().filterNotNull().size)
        assertEquals(LocalDate.parse("2026-06-01"), monthGrid(YearMonth.of(2026, 6)).first().first())
    }

    @Test
    fun `the week pill is due once a week`() {
        val thursday = LocalDate.parse("2026-11-19")
        assertTrue(weekPillDue(null, thursday))
        assertTrue(weekPillDue("2026-11-09", thursday))
        assertFalse(weekPillDue("2026-11-16", thursday))
        assertFalse(weekPillDue("2026-11-16", LocalDate.parse("2026-11-22")))
        assertTrue(weekPillDue("2026-11-16", LocalDate.parse("2026-11-23")))
    }

    private fun week(monday: String, count: Int, target: Int) =
        WeekResult(LocalDate.parse(monday), count, target, count >= target, false, null)

    @Test
    fun `the pill says last week against its target`() {
        val today = LocalDate.parse("2026-11-19")
        val results = listOf(week("2026-11-02", 1, 3), week("2026-11-09", 3, 3), week("2026-11-16", 1, 3).copy(current = true))
        assertEquals("Last week: 3 of 3 ✓", lastWeekLine(results, today, ownTarget = false))
        assertEquals("Last week: 2 of 3", lastWeekLine(listOf(week("2026-11-09", 2, 3)), today, ownTarget = false))
        assertNull(lastWeekLine(listOf(week("2026-11-16", 1, 3)), today, ownTarget = true))
        assertNull(lastWeekLine(listOf(week("2026-11-09", 0, 3)), today, ownTarget = false))
        assertEquals("Last week: 0 of 3", lastWeekLine(listOf(week("2026-11-09", 0, 3)), today, ownTarget = true))
    }
}
