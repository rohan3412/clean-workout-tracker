package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** The Moments page's file names, headers and stats. */
class MomentsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = momentOf(LocalDateTime.of(y, m, d, h, 0, 0), "%08x".format(y * 10000 + m * 100 + d + h))

    @Test fun `a name is read back and nothing else is a moment`() {
        val m = momentOf(LocalDateTime.of(2026, 10, 12, 9, 30, 5, 99), "0a1b2c3d")
        assertEquals("20261012-093005_0a1b2c3d.jpg", m.name)
        assertEquals(m, parseMoment(m.name))
        assertNull(parseMoment("incoming.tmp"))
        assertNull(parseMoment("20261012-093005_front.jpg"))
        assertNull(parseMoment("20261312-093005_0a1b2c3d.jpg"))
        assertNull(parseMoment("20261012-093005_0A1B2C3D.jpg"))
    }

    @Test fun `headers run from today through this week to the months`() {
        val list = listOf(
            at(2025, 12, 30), at(2026, 11, 19, 8), at(2026, 11, 14), at(2026, 11, 13), at(2026, 11, 5), at(2026, 9, 1), at(2026, 11, 19, 20), at(2026, 11, 20),
        )
        val groups = groupMoments(list, today)
        assertEquals(listOf("Today", "This week", "November", "September", "December 2025"), groups.map { it.title })
        assertEquals(listOf(3, 2, 1, 1, 1), groups.map { it.moments.size })
        // Newest first inside a header; a later date is today's.
        assertEquals(listOf(20, 19, 19), groups[0].moments.map { it.at.dayOfMonth })
        assertEquals(listOf(12, 20, 8), groups[0].moments.map { it.at.hour })
    }

    @Test fun `six days back is this week and seven is the month`() {
        val groups = groupMoments(listOf(at(2026, 11, 13), at(2026, 11, 12)), today)
        assertEquals(listOf("This week", "November"), groups.map { it.title })
    }

    @Test fun `nothing gives no headers and no stats`() {
        assertEquals(0, groupMoments(emptyList(), today).size)
        val s = momentStats(emptyList(), today)
        assertEquals(Triple(0, 0, 0), Triple(s.total, s.thisMonth, s.days))
    }

    @Test fun `stats count the month and the days`() {
        val s = momentStats(listOf(at(2026, 11, 19, 8), at(2026, 11, 19, 20), at(2026, 11, 2), at(2025, 11, 3), at(2026, 10, 30)), today)
        assertEquals(Triple(5, 3, 4), Triple(s.total, s.thisMonth, s.days))
    }
}
