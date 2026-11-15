package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The smooth line of the charts: through every point, and never beyond the two points it runs between. */
class SmoothTest {
    /** The cubic Hermite curve on segment [k] at fraction [t]. */
    private fun at(x: DoubleArray, y: DoubleArray, m: DoubleArray, k: Int, t: Double): Double {
        val h = x[k + 1] - x[k]
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * y[k] + (t3 - 2 * t2 + t) * h * m[k] + (-2 * t3 + 3 * t2) * y[k + 1] + (t3 - t2) * h * m[k + 1]
    }

    @Test
    fun passesThroughEveryPointAndStaysBetweenNeighbours() {
        val x = doubleArrayOf(0.0, 1.0, 3.0, 4.0, 8.0, 9.0, 10.0, 14.0)
        val y = doubleArrayOf(5.0, 5.0, 9.0, 2.0, 2.5, 20.0, 19.0, 0.0)
        val m = monotoneSlopes(x, y)
        for (k in 0 until x.size - 1) {
            assertEquals(y[k], at(x, y, m, k, 0.0), 1e-9)
            assertEquals(y[k + 1], at(x, y, m, k, 1.0), 1e-9)
            val low = minOf(y[k], y[k + 1])
            val high = maxOf(y[k], y[k + 1])
            for (i in 0..100) {
                val v = at(x, y, m, k, i / 100.0)
                assertTrue(v >= low - 1e-9 && v <= high + 1e-9, "segment $k at ${i / 100.0}: $v outside $low..$high")
            }
        }
    }

    @Test
    fun twoPointsAreAStraightLineAndOnePointHasNoSlope() {
        assertEquals(listOf(2.0, 2.0), monotoneSlopes(doubleArrayOf(0.0, 3.0), doubleArrayOf(1.0, 7.0)).toList())
        assertEquals(listOf(0.0), monotoneSlopes(doubleArrayOf(0.0), doubleArrayOf(1.0)).toList())
    }
}
