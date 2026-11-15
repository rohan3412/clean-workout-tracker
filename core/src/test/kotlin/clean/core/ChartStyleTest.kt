package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartStyleTest {
    private val day = LocalDate.of(2026, 1, 1)

    @Test fun styleDefaultsToLine() {
        assertEquals(ChartStyle.LINE, chartStyleOf(null))
        assertEquals(ChartStyle.LINE, chartStyleOf("nonsense"))
        assertEquals(ChartStyle.BARS, chartStyleOf("bars"))
        assertEquals(ChartStyle.SMOOTH, chartStyleOf("smooth"))
    }

    @Test fun movingAverageShrinksAtTheEnds() {
        val out = movingAverage(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0))
        assertEquals(listOf(2.0, 2.5, 3.0, 4.0, 5.0, 5.5, 6.0), out)
    }

    @Test fun movingAverageOfShortSeriesAndEmpty() {
        assertEquals(listOf(5.0), movingAverage(listOf(5.0)))
        assertEquals(listOf(1.5, 1.5), movingAverage(listOf(1.0, 2.0)))
        assertEquals(emptyList(), movingAverage(emptyList()))
    }

    @Test fun smoothingPlainPointsKeepsDates() {
        val out = smoothedPoints(listOf(0.0, 10.0, 20.0).mapIndexed { i, v -> ChartPoint(day.plusDays(i.toLong()), v) })
        assertEquals(listOf(10.0, 10.0, 10.0), out.map { it.value })
        assertEquals(day.plusDays(2), out[2].date)
        assertEquals(false, out.any { it.split })
    }

    @Test fun smoothingAveragesEachSideOnItsOwn() {
        val points = listOf(
            ChartPoint(day, 15.0, 10.0, 20.0),
            ChartPoint(day.plusDays(1), 25.0, 20.0, 30.0),
            ChartPoint(day.plusDays(2), 35.0, 30.0, 40.0),
        )
        val out = smoothedPoints(points)
        assertEquals(listOf(20.0, 20.0, 20.0), out.map { it.left })
        assertEquals(listOf(30.0, 30.0, 30.0), out.map { it.right })
        assertEquals(25.0, out[1].value)
    }
}
