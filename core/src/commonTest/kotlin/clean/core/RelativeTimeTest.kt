package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDateTime

class RelativeTimeTest {
    private val now = LocalDateTime.of(2026, 11, 25, 12, 0, 0)

    @Test fun underAMinuteIsJustNow() {
        assertEquals("just now", relativeTime(now, now))
        assertEquals("just now", relativeTime(now.minusSeconds(59), now))
        assertEquals("just now", relativeTime(now.plusMinutes(5), now))
    }

    @Test fun minutesHoursAndDaysAreSingularAtOne() {
        assertEquals("1 minute ago", relativeTime(now.minusSeconds(60), now))
        assertEquals("12 minutes ago", relativeTime(now.minusMinutes(12), now))
        assertEquals("59 minutes ago", relativeTime(now.minusMinutes(59), now))
        assertEquals("1 hour ago", relativeTime(now.minusMinutes(60), now))
        assertEquals("1 hour ago", relativeTime(now.minusMinutes(119), now))
        assertEquals("5 hours ago", relativeTime(now.minusHours(5), now))
        assertEquals("23 hours ago", relativeTime(now.minusHours(23), now))
        assertEquals("1 day ago", relativeTime(now.minusHours(24), now))
        assertEquals("3 days ago", relativeTime(now.minusDays(3), now))
        assertEquals("6 days ago", relativeTime(now.minusDays(6).minusHours(23), now))
    }

    @Test fun sevenDaysOrMoreIsLeftToTheDate() {
        assertNull(relativeTime(now.minusDays(7), now))
        assertNull(relativeTime(now.minusDays(40), now))
    }
}
