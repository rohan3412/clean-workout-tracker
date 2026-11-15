package clean.core

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {
    @Test
    fun offUntilDaysAreChosen() {
        assertEquals(emptySet(), parseRemindDays(null))
        assertNull(encodeRemindDays(emptySet()))
        assertNull(nextReminder(LocalDateTime.of(2026, 10, 19, 9, 0), emptySet(), REMIND_DEFAULT_TIME))
    }

    @Test
    fun daysRoundTripMondayFirst() {
        assertEquals("1,3,5", encodeRemindDays(setOf(FRIDAY, MONDAY, WEDNESDAY)))
        assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY), parseRemindDays("5,1,x,9,3"))
    }

    @Test
    fun theNextReminderIsLaterTodayOrOnTheNextChosenDay() {
        val days = setOf(MONDAY, WEDNESDAY)
        // 2026-10-19 is a Monday.
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 0), nextReminder(LocalDateTime.of(2026, 10, 19, 9, 0), days, 18 * 60))
        assertEquals(LocalDateTime.of(2026, 10, 21, 18, 0), nextReminder(LocalDateTime.of(2026, 10, 19, 18, 0), days, 18 * 60))
        // Only Monday: the one after a Monday evening is a week on.
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 30), nextReminder(LocalDateTime.of(2026, 10, 19, 8, 0), setOf(MONDAY), 7 * 60 + 30))
    }
}
