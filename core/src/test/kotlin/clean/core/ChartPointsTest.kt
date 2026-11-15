package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The points a chart is given: split only for a session with both sides, the middle plotted, the imbalance series. */
class ChartPointsTest {
    private fun set(weight: Double, reps: Int, type: SetType = SetType.NORMAL) = NewSet(type, weight, reps)

    private fun paired(date: String, vararg sets: NewSet): Session {
        val b = buildWorkouts(rowsFor(1, date, "w", 3600, listOf(NewBlock("Curl", sets.toList(), paired = true))))[0].blocks[0]
        return Session(date, b.sets, b.setSides)
    }

    private fun plain(date: String, vararg sets: NewSet): Session {
        val b = buildWorkouts(rowsFor(1, date, "w", 3600, listOf(NewBlock("Curl", sets.toList()))))[0].blocks[0]
        return Session(date, b.sets, b.setSides)
    }

    private val rule = EPLEY_RULE
    private val sessions = listOf(
        plain("2025-03-01 10:00:00", set(20.0, 10)),
        paired("2025-03-08 10:00:00", set(20.0, 10), set(20.0, 8)),
        paired("2025-03-15 10:00:00", set(20.0, 10), set(20.0, 10)),
        // Only the left half: no split, the comparable figure.
        paired("2025-03-22 10:00:00", set(20.0, 10)),
    )
    private val progress = progressBySide(sessions, rule)

    @Test
    fun `an ordinary session is one point and a left and right session a split around its middle`() {
        val points = chartPoints(progress) { it.maxReps?.toDouble() }
        assertEquals(4, points.size)
        assertFalse(points[0].split)
        assertEquals(10.0, points[0].value)
        assertTrue(points[1].split)
        assertEquals(10.0, points[1].left)
        assertEquals(8.0, points[1].right)
        assertEquals(9.0, points[1].value)
        assertEquals(8.0, points[1].low)
        assertEquals(10.0, points[1].high)
        // Equal sides are still a split (drawn as one marker with a ring).
        assertTrue(points[2].split)
        assertFalse(points[3].split)
    }

    @Test
    fun `an exercise with no paired session gives only plain points, as before`() {
        val points = chartPoints(progressBySide(sessions.take(1), rule)) { it.estimated1Rm }
        assertEquals(1, points.size)
        assertNull(points[0].left)
        assertEquals(progressOf(sessions.take(1), rule)[0].estimated1Rm, points[0].value)
    }

    @Test
    fun `the range cuts by date and a measure a session lacks leaves it out`() {
        assertEquals(2, chartPoints(progress, LocalDate.of(2025, 3, 15)) { it.maxReps?.toDouble() }.size)
        assertTrue(chartPoints(progress) { it.longestSeconds }.isEmpty())
    }

    @Test
    fun `imbalance is right minus left in percent of the larger side, for paired sessions only`() {
        val series = imbalanceSeries(progress)!!
        assertEquals("est. 1RM", series.measure)
        // Sessions 2 and 3 only: 20 x 10 left, 20 x 8 right is right behind; equal is 0.
        assertEquals(2, series.points.size)
        assertTrue(series.points[0].value < 0)
        assertEquals(0.0, series.points[1].value)
        assertFalse(series.points[0].split)
        assertEquals(LocalDate.of(2025, 3, 8), series.points[0].date)
    }

    @Test
    fun `reps stand in when no weight was lifted, and none without a pair`() {
        val bodyweight = listOf(paired("2025-03-08 10:00:00", NewSet(SetType.NORMAL, null, 12), NewSet(SetType.NORMAL, null, 10)))
        val series = imbalanceSeries(progressBySide(bodyweight, rule))
        assertNotNull(series)
        assertEquals("reps", series.measure)
        assertNull(imbalanceSeries(progressBySide(sessions.take(1), rule)))
        assertNull(imbalanceSeries(progress, LocalDate.of(2025, 4, 1)))
    }
}
