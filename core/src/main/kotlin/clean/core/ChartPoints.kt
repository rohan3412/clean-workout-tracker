package clean.core

import java.time.LocalDate

/*
 * What a chart is given for an exercise done left and right (docs/left-right.md, "Showing it"): one point per session,
 * with the two sides' figures only for a session that has both. Pure, so the drawing only lays them out.
 */

/**
 * One session on a chart. [value] is the plotted figure: for a session with both sides the middle of [left] and [right]
 * (the line runs through the middle of the stem joining them), otherwise the session's own figure with no split.
 */
data class ChartPoint(val date: LocalDate, val value: Double, val left: Double? = null, val right: Double? = null) {
    /** Both sides are given, so a chart draws two markers and a stem here. */
    val split: Boolean get() = left != null && right != null
    /** The lowest and highest figure at this x: the ends of the stem, or the value. */
    val low: Double get() = if (split) minOf(left!!, right!!) else value
    val high: Double get() = if (split) maxOf(left!!, right!!) else value
}

/**
 * [measure] of every session of [progress] on or after [from] (all when null) that has one, oldest first. A session with a left
 * and right block gives a split point when both sides have the measure, and a plain point (the comparable figure) otherwise.
 */
fun chartPoints(progress: List<SidedProgress>, from: LocalDate? = null, measure: (Progress) -> Double?): List<ChartPoint> =
    progress.mapNotNull { p ->
        val day = parseStrongDate(p.date)?.toLocalDate() ?: return@mapNotNull null
        if (from != null && day < from) return@mapNotNull null
        val l = p.left?.let(measure)
        val r = p.right?.let(measure)
        if (l != null && r != null) return@mapNotNull ChartPoint(day, (l + r) / 2, l, r)
        measure(p.comparable)?.let { ChartPoint(day, it) }
    }

/** The imbalance chart's figures: [measure] names the one they are in (`est. 1RM`, `reps`, `volume`). */
data class ImbalanceSeries(val measure: String, val points: List<ChartPoint>)

/**
 * Right minus left in percent of the larger side, per session with both sides, on or after [from]. One measure is used for
 * the whole series: the first of estimated 1RM, reps, volume that any of those sessions has. Null when no session has one.
 */
fun imbalanceSeries(progress: List<SidedProgress>, from: LocalDate? = null): ImbalanceSeries? {
    val gaps = progress.mapNotNull { p ->
        val day = parseStrongDate(p.date)?.toLocalDate() ?: return@mapNotNull null
        if (from != null && day < from) return@mapNotNull null
        imbalance(p)?.let { day to it }
    }
    val measures = listOf<Pair<String, (Imbalance) -> SideGap?>>("est. 1RM" to { it.oneRm }, "reps" to { it.reps }, "volume" to { it.volume })
    for ((name, gap) in measures) {
        val points = gaps.mapNotNull { (day, g) -> gap(g)?.let { ChartPoint(day, it.percent) } }
        if (points.isNotEmpty()) return ImbalanceSeries(name, points)
    }
    return null
}
