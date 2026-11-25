package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TemplateDurationTest {
    private fun w(day: Int, name: String, minutes: Long) = Workout("$day", "2026-10-%02d 18:00:00".format(day), name, minutes * 60, emptyList())

    @Test
    fun medianOfTheLatestFive() {
        // The oldest (120 min) is past the five; the median of 50, 55, 60, 65, 90 is 60.
        val list = listOf(w(1, "Push", 120), w(2, "Push", 90), w(3, "Push", 50), w(4, "Push", 60), w(5, "Push", 55), w(6, "Push", 65))
        assertEquals(60 * 60L, templateDurations(list)["Push"])
    }

    @Test
    fun evenCountsAverageTheMiddleTwoAndEmptyTimesAreLeftOut() {
        val list = listOf(w(1, "Legs", 40), w(2, "Legs", 50), w(3, "Legs", 0), w(4, "Pull", 0))
        assertEquals(45 * 60L, templateDurations(list)["Legs"])
        assertNull(templateDurations(list)["Pull"])
    }

    @Test
    fun estimatesAreRoundedToFiveMinutes() {
        assertEquals("~55 min", estimateText(53 * 60L))
        assertEquals("~5 min", estimateText(60L))
        assertEquals("~1 h", estimateText(61 * 60L))
        assertEquals("~1 h 20 min", estimateText(78 * 60L))
    }
}
