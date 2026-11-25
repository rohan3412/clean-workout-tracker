package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class StatsPeriodTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun row(day: String, exercise: String, order: String) =
        WorkoutRow("1", "$day 10:00:00", "w", "3600", exercise, order, "50", "5", "", "", "", "", "")

    /** A workout on [day] of 3600 s with [sets] working sets of [exercise]. */
    private fun w(day: String, exercise: String = "Squat", sets: Int = 2): Workout =
        buildWorkouts((1..sets).map { row(day, exercise, it.toString()) }).single()

    @Test fun weekRunsFromMondayAndComparesTheSameDays() {
        val wed = d("2026-10-14")
        assertEquals(DayRange(d("2026-10-12"), wed), periodRange(StatsPeriod.WEEK, wed, null))
        assertEquals(DayRange(d("2026-10-05"), d("2026-10-07")), previousRange(StatsPeriod.WEEK, wed))
    }

    @Test fun comparisonSentenceNamesTheCountAndTheVolume() {
        val before = PeriodTotals(4, 1000.0, 0, 0.0)
        assertEquals("1 fewer workout than October, volume down 8 %", comparisonSentence(PeriodTotals(3, 920.0, 0, 0.0), before, "October"))
        assertEquals("2 more workouts than last week, volume up 50 %", comparisonSentence(PeriodTotals(6, 1500.0, 0, 0.0), before, "last week"))
        assertEquals("Same number of workouts as last year, volume the same", comparisonSentence(PeriodTotals(4, 1000.0, 0, 0.0), before, "last year"))
        assertEquals("3 more workouts than October", comparisonSentence(PeriodTotals(3, 500.0, 0, 0.0), PeriodTotals(0, 0.0, 0, 0.0), "October"))
        assertEquals("October", previousName(StatsPeriod.MONTH, d("2026-11-17")))
        assertNull(previousName(StatsPeriod.ALL, d("2026-11-17")))
    }

    @Test fun monthComparesTheSameDaysOfTheMonthBefore() {
        val today = d("2026-10-16")
        assertEquals(DayRange(d("2026-10-01"), today), periodRange(StatsPeriod.MONTH, today, null))
        assertEquals(DayRange(d("2026-09-01"), d("2026-09-16")), previousRange(StatsPeriod.MONTH, today))
    }

    @Test fun aLongMonthAgainstAShortOneEndsWithTheShortOne() {
        assertEquals(DayRange(d("2026-02-01"), d("2026-02-28")), previousRange(StatsPeriod.MONTH, d("2026-03-31")))
        assertEquals(DayRange(d("2024-02-01"), d("2024-02-29")), previousRange(StatsPeriod.MONTH, d("2024-03-31")))
        assertEquals(DayRange(d("2026-01-01"), d("2026-01-28")), previousRange(StatsPeriod.MONTH, d("2026-02-28")))
    }

    @Test fun yearComparesTheSameDaysOfTheYearBefore() {
        assertEquals(DayRange(d("2026-01-01"), d("2026-10-16")), periodRange(StatsPeriod.YEAR, d("2026-10-16"), null))
        assertEquals(DayRange(d("2025-01-01"), d("2025-10-16")), previousRange(StatsPeriod.YEAR, d("2026-10-16")))
        // The 366th day of a leap year ends the year before at its 365th.
        assertEquals(DayRange(d("2023-01-01"), d("2023-12-31")), previousRange(StatsPeriod.YEAR, d("2024-12-31")))
    }

    @Test fun allStartsAtTheFirstWorkoutAndHasNoEarlierPeriod() {
        assertEquals(DayRange(d("2025-03-04"), d("2026-10-16")), periodRange(StatsPeriod.ALL, d("2026-10-16"), d("2025-03-04")))
        assertEquals(DayRange(d("2026-10-16"), d("2026-10-16")), periodRange(StatsPeriod.ALL, d("2026-10-16"), null))
        assertNull(previousRange(StatsPeriod.ALL, d("2026-10-16")))
    }

    @Test fun totalsAddUpTheWorkoutsInARange() {
        val workouts = listOf(w("2026-09-30"), w("2026-10-01", sets = 3), w("2026-10-16"), w("2026-10-17"))
        val t = totalsIn(workouts, DayRange(d("2026-10-01"), d("2026-10-16")), { 100.0 })
        assertEquals(PeriodTotals(2, 200.0, 7200, 5.0), t)
        assertEquals(PeriodTotals(0, 0.0, 0, 0.0), totalsIn(emptyList(), DayRange(d("2026-10-01"), d("2026-10-16")), { 1.0 }))
    }

    @Test fun changesAreWrittenWithASignOrSame() {
        assertEquals("+3", countChange(8.0, 5.0))
        assertEquals("−2", countChange(3.0, 5.0))
        assertEquals("+0.5", countChange(5.5, 5.0))
        assertEquals("same", countChange(5.0, 5.0))
        assertEquals("+12 %", percentChange(112.0, 100.0))
        assertEquals("−50 %", percentChange(50.0, 100.0))
        assertEquals("same", percentChange(100.2, 100.0))
        assertEquals("new", percentChange(10.0, 0.0))
        assertEquals("same", percentChange(0.0, 0.0))
    }

    @Test fun weekBucketsAreSevenDaysWithTheFutureEmpty() {
        val wed = d("2026-10-14")
        val b = volumeBuckets(StatsPeriod.WEEK, wed, null, mapOf(d("2026-10-12") to 10.0, d("2026-10-14") to 5.0))
        assertEquals(7, b.size)
        assertEquals(listOf(10.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0), b.map { it.volume })
        assertEquals(listOf(false, false, false, true, true, true, true), b.map { it.future })
        assertEquals(2, b.indexOfFirst { it.current })
    }

    @Test fun monthBucketsAreTheDaysOfTheMonth() {
        assertEquals(29, volumeBuckets(StatsPeriod.MONTH, d("2024-02-10"), null, emptyMap()).size)
        assertEquals(31, volumeBuckets(StatsPeriod.MONTH, d("2026-10-16"), null, emptyMap()).size)
    }

    @Test fun yearBucketsAreTwelveMonths() {
        val b = volumeBuckets(StatsPeriod.YEAR, d("2026-10-16"), null, mapOf(d("2026-01-05") to 3.0, d("2026-01-20") to 4.0, d("2025-12-31") to 99.0))
        assertEquals(12, b.size)
        assertEquals(7.0, b[0].volume)
        assertEquals(9, b.indexOfFirst { it.current })
        assertTrue(b[10].future && b[11].future)
    }

    @Test fun allIsByMonthThenByYearPastTwoYears() {
        assertTrue(volumeBuckets(StatsPeriod.ALL, d("2026-10-16"), null, emptyMap()).isEmpty())
        val months = volumeBuckets(StatsPeriod.ALL, d("2026-10-16"), d("2026-08-20"), mapOf(d("2026-08-20") to 5.0))
        assertEquals(3, months.size)
        assertEquals(5.0, months[0].volume)
        assertTrue(months.last().current)
        val years = volumeBuckets(StatsPeriod.ALL, d("2026-10-16"), d("2023-01-01"), mapOf(d("2023-06-01") to 5.0))
        assertEquals(listOf(2023, 2024, 2025, 2026), years.map { it.start.year })
        assertEquals(5.0, years[0].volume)
        assertTrue(years.last().current)
        // Exactly 24 months stay months.
        assertEquals(24, volumeBuckets(StatsPeriod.ALL, d("2026-10-16"), d("2024-11-03"), emptyMap()).size)
    }

    @Test fun topExerciseIsTheOneWithTheMostWorkingSets() {
        val workouts = listOf(w("2026-10-01", "Squat", 2), w("2026-10-02", "Bench", 3), w("2026-10-03", "Squat", 2), w("2026-09-01", "Row", 9))
        assertEquals("Squat" to 4.0, topExercise(workouts, DayRange(d("2026-10-01"), d("2026-10-16"))))
        assertNull(topExercise(workouts, DayRange(d("2026-11-01"), d("2026-11-16"))))
    }

    private fun day(s: String) = kotlinx.datetime.LocalDate.parse(s)

    @Test
    fun `the biggest day of a week that beat every other day of the log is its all-time high`() {
        val today = day("2026-11-20")
        val perDay = mapOf(day("2026-11-16") to 4000.0, day("2026-11-18") to 9000.0, day("2026-03-02") to 8000.0)
        val buckets = volumeBuckets(StatsPeriod.WEEK, today, day("2026-03-02"), perDay)
        assertEquals(BarHigh(2, BarSize.DAY, 9000.0), allTimeHigh(buckets, perDay))
    }

    @Test
    fun `a day that only ties or trails the best day of the log is not a high, and neither is a log with nothing else`() {
        val today = day("2026-11-20")
        val tie = mapOf(day("2026-11-18") to 9000.0, day("2026-03-02") to 9000.0)
        assertEquals(null, allTimeHigh(volumeBuckets(StatsPeriod.WEEK, today, day("2026-03-02"), tie), tie))
        val behind = mapOf(day("2026-11-18") to 5000.0, day("2026-03-02") to 9000.0)
        assertEquals(null, allTimeHigh(volumeBuckets(StatsPeriod.WEEK, today, day("2026-03-02"), behind), behind))
        val alone = mapOf(day("2026-11-18") to 5000.0)
        assertEquals(null, allTimeHigh(volumeBuckets(StatsPeriod.WEEK, today, day("2026-11-18"), alone), alone))
        assertEquals(null, allTimeHigh(volumeBuckets(StatsPeriod.WEEK, today, null, emptyMap()), emptyMap()))
    }

    @Test
    fun `a month bar is compared with every other month of the log, and a year bar with every other year`() {
        val today = day("2026-11-20")
        // November is split over days but its month total beats September 2025's; the single biggest day of September is bigger than any day of November.
        val perDay = mapOf(day("2026-11-03") to 5000.0, day("2026-11-10") to 5000.0, day("2025-09-05") to 7000.0)
        val months = volumeBuckets(StatsPeriod.YEAR, today, day("2025-09-05"), perDay)
        assertEquals(BarHigh(10, BarSize.MONTH, 10000.0), allTimeHigh(months, perDay))
        val all = volumeBuckets(StatsPeriod.ALL, today, day("2025-09-05"), perDay)
        assertEquals(BarSize.MONTH, allTimeHigh(all, perDay)?.size)
        val years = mapOf(day("2026-11-03") to 5000.0, day("2020-01-10") to 12000.0, day("2021-05-05") to 7000.0, day("2022-05-05") to 3000.0)
        val yearBars = volumeBuckets(StatsPeriod.ALL, today, day("2020-01-10"), years)
        assertEquals(BarHigh(0, BarSize.YEAR, 12000.0), allTimeHigh(yearBars, years))
        val more = years + (day("2026-11-10") to 9000.0)
        assertEquals(BarHigh(6, BarSize.YEAR, 14000.0), allTimeHigh(volumeBuckets(StatsPeriod.ALL, today, day("2020-01-10"), more), more))
    }
}
