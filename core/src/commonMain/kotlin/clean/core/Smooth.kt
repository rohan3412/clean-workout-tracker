package clean.core

/**
 * The slope at each point of a monotone cubic curve through ([x], [y]) (Fritsch and Carlson's idea, with Dougherty's weighted
 * harmonic mean for the inner points): a flat stretch or a turning point gets slope 0 and every other point a slope no steeper than
 * three times either neighbouring secant, so the curve through them never leaves the range of the two points it joins and
 * never overshoots. [x] must not decrease; a repeated x is a step, and its secant counts as 0.
 */
fun monotoneSlopes(x: DoubleArray, y: DoubleArray): DoubleArray {
    val n = x.size
    if (n < 2) return DoubleArray(n)
    val h = DoubleArray(n - 1) { x[it + 1] - x[it] }
    val d = DoubleArray(n - 1) { if (h[it] > 0) (y[it + 1] - y[it]) / h[it] else 0.0 }
    val m = DoubleArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (k in 1 until n - 1) {
        if (d[k - 1] * d[k] <= 0.0) continue
        val w1 = 2 * h[k] + h[k - 1]
        val w2 = h[k] + 2 * h[k - 1]
        m[k] = (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
    }
    return m
}
