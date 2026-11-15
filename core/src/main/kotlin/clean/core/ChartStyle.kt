package clean.core

/** How a chart draws its figures (Settings, Appearance, Charts): the line as it is, one bar per point, or a moving average over faint raw points. */
enum class ChartStyle(val key: String, val label: String) {
    LINE("line", "Line"),
    BARS("bars", "Bars"),
    SMOOTH("smooth", "Smoothed"),
}

/** The stored `chart.style`; absent or unknown is the line. */
fun chartStyleOf(stored: String?): ChartStyle = ChartStyle.entries.firstOrNull { it.key == stored } ?: ChartStyle.LINE

/** How many points the smoothed style averages. */
const val SMOOTH_WINDOW = 5

/**
 * The centred moving average of [values]: each figure is the mean of itself and up to `window / 2` neighbours on each side, the
 * window shrinking at the two ends so the result has as many figures as the input and the first and last still come from the data.
 */
fun movingAverage(values: List<Double>, window: Int = SMOOTH_WINDOW): List<Double> {
    val half = (window.coerceAtLeast(1)) / 2
    return values.indices.map { i ->
        val from = maxOf(0, i - half)
        val to = minOf(values.lastIndex, i + half)
        var sum = 0.0
        for (k in from..to) sum += values[k]
        sum / (to - from + 1)
    }
}

/**
 * [points] with every figure replaced by its moving average, each side averaged on its own (a plain session counts its figure
 * for both sides, as the left and right lines already do), so the smoothed curve of a split series keeps its two lines.
 */
fun smoothedPoints(points: List<ChartPoint>, window: Int = SMOOTH_WINDOW): List<ChartPoint> {
    val mid = movingAverage(points.map { it.value }, window)
    if (points.none { it.split }) return points.mapIndexed { i, p -> ChartPoint(p.date, mid[i]) }
    val left = movingAverage(points.map { it.left ?: it.value }, window)
    val right = movingAverage(points.map { it.right ?: it.value }, window)
    return points.mapIndexed { i, p -> if (p.split) ChartPoint(p.date, (left[i] + right[i]) / 2, left[i], right[i]) else ChartPoint(p.date, mid[i]) }
}
