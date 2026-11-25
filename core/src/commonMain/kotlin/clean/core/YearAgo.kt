package clean.core

import clean.core.time.*
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** A lift now against about a year ago: the best estimated 1RM of the session nearest a year back and of the last 30 days. [series] is the estimates between. */
data class YearAgoLift(val exercise: String, val thenDate: LocalDate, val then: Double, val now: Double, val series: List<Double>) {
    /** The change as a fraction of then (0.15 is +15 %). */
    val change: Double get() = now / then - 1
}

/** The 30 days around a year ago against the last 30 days, for when no lift can be compared. */
data class YearAgoTotals(val thenWorkouts: Int, val nowWorkouts: Int, val thenVolume: Double, val nowVolume: Double) {
    val change: Double get() = nowVolume / thenVolume - 1
}

/** Days either side of a year ago that count as "about a year ago", and the length of "now". */
const val YEAR_AGO_WINDOW = 30

/** Smallest change worth a card: 5 % in a lift's estimated 1RM, 20 % in the volume. */
const val YEAR_AGO_LIFT_MIN = 0.05
const val YEAR_AGO_TOTALS_MIN = 0.20

private fun dayOf(date: String): LocalDate? = parseStrongDate(date)?.toLocalDate()

/**
 * The lift to compare: among exercises with a session within [YEAR_AGO_WINDOW] days of a year before [today] and one in the last 30 days, the one
 * with the most sessions overall (ties A to Z) that has an estimate on both sides. Null when there is none, or when the change is under 5 %.
 * [progress] gives an exercise's progress (the user's 1RM rule, weaker side for left and right) and is asked only for the candidates.
 */
fun yearAgoLift(today: LocalDate, sessions: Map<String, List<Session>>, progress: (String, List<Session>) -> List<Progress>): YearAgoLift? {
    val anchor = today.minusDays(365)
    val nowFrom = today.minusDays(YEAR_AGO_WINDOW.toLong())
    fun isThen(d: LocalDate) = abs(d.toEpochDay() - anchor.toEpochDay()) <= YEAR_AGO_WINDOW
    fun isNow(d: LocalDate) = d >= nowFrom && d <= today
    val candidates = sessions.filter { (_, list) ->
        val days = list.mapNotNull { dayOf(it.date) }
        days.any(::isThen) && days.any(::isNow)
    }.entries.sortedWith(compareBy({ -it.value.size }, { it.key }))
    for ((name, list) in candidates) {
        val points = progress(name, list).mapNotNull { p -> dayOf(p.date)?.let { d -> p.estimated1Rm?.let { Triple(d, it, p) } } }
        val then = points.filter { isThen(it.first) }.minWithOrNull(compareBy({ abs(it.first.toEpochDay() - anchor.toEpochDay()) }, { it.first })) ?: continue
        val now = points.filter { isNow(it.first) }.maxOfOrNull { it.second } ?: continue
        if (then.second <= 0) continue
        val lift = YearAgoLift(name, then.first, then.second, now, points.filter { it.first >= then.first && it.first <= today }.map { it.second })
        return lift.takeIf { abs(it.change) >= YEAR_AGO_LIFT_MIN - 1e-9 }
    }
    return null
}

/** The totals fallback: null when nothing was logged around a year ago, or when the volume moved less than 20 %. */
fun yearAgoTotals(today: LocalDate, workouts: List<Workout>, volume: (Workout) -> Double): YearAgoTotals? {
    val anchor = today.minusDays(365)
    val nowFrom = today.minusDays(YEAR_AGO_WINDOW.toLong())
    val dated = workouts.mapNotNull { w -> dayOf(w.date)?.let { it to w } }
    val then = dated.filter { abs(it.first.toEpochDay() - anchor.toEpochDay()) <= YEAR_AGO_WINDOW }.map { it.second }
    val now = dated.filter { it.first >= nowFrom && it.first <= today }.map { it.second }
    val totals = YearAgoTotals(then.size, now.size, then.sumOf(volume), now.sumOf(volume))
    if (then.isEmpty() || totals.thenVolume <= 0) return null
    return totals.takeIf { abs(it.change) >= YEAR_AGO_TOTALS_MIN - 1e-9 }
}

/** The signed percent of a change as text: "+15 %", "−8 %" (a real minus). */
fun changePercent(change: Double): String {
    val n = jRound(change * 100).toInt()
    return when {
        n > 0 -> "+$n %"
        n < 0 -> "−${-n} %"
        else -> "0 %"
    }
}
