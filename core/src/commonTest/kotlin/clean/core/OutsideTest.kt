package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class OutsideTest {
    @Test
    fun resumeOnlyWhileRunning() {
        assertEquals(listOf(ShortcutKind.START, ShortcutKind.WEIGHT), shortcutsShown(false))
        assertEquals(ShortcutKind.entries.toList(), shortcutsShown(true))
    }

    @Test
    fun weekStripStates() {
        // 2026-10-15 is a Thursday.
        val marks = weekMarks(LocalDate.of(2026, 10, 15), setOf("2026-10-12", "2026-10-15"))
        assertEquals(listOf(DayMark.TRAINED, DayMark.PAST, DayMark.PAST, DayMark.TRAINED, DayMark.FUTURE, DayMark.FUTURE, DayMark.FUTURE), marks)
        assertEquals(DayMark.TODAY, weekMarks(LocalDate.of(2026, 10, 15), emptySet())[3])
    }

    @Test
    fun notificationFormAndColours() {
        assertEquals(listOf(-1, 5, 0), decodeArgb(encodeArgb(listOf(-1, 5, 0)), 3))
        assertNull(decodeArgb("1,2", 3))
        assertNull(decodeArgb("1,x,3", 3))
        assertNull(decodeArgb(null, 3))
    }

    @Test
    fun workoutNotificationIsOnUnlessSwitchedOff() {
        assertTrue(workoutNotifOn(null))
        assertTrue(workoutNotifOn("on"))
        assertFalse(workoutNotifOn("off"))
    }

    @Test
    fun workoutNotificationLine() {
        val end = LocalTime.of(17, 32, 5)
        assertEquals("Resting until 17:32:05 · Next: Bench · set 2", workoutNotifText("Next: Bench · set 2", end))
        assertEquals("Next: Bench · set 2", workoutNotifText("Next: Bench · set 2", null))
        assertEquals("Resting until 17:32:05", workoutNotifText(null, end))
        assertNull(workoutNotifText(null, null))
    }
}
