package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate

/** A lift's estimated 1RM at the start of the year and at its best in it, in kg. */
data class LiftGain(val exercise: String, val from: Double, val to: Double) {
    val gain: Double get() = to - from
}

/** The year in numbers (Year in review). [bestMonth] is the month 1 to 12 with the most volume; [longestStreakWeeks] weeks in a row with a workout. */
data class YearReview(
    val year: Int,
    val totals: PeriodTotals,
    val topLifts: List<LiftGain>,
    val bestMonth: Pair<Int, Double>?,
    val longestStreakWeeks: Int,
    val records: Double,
    val mostTrained: Pair<String, Double>?,
    /** The volume of each month, January first (twelve figures; 0 for a month with no workout). */
    val monthVolumes: List<Double> = emptyList(),
    /** The five exercises with the most working sets, most first ([mostTrained] is the first). */
    val topExercises: List<Pair<String, Double>> = emptyList(),
)

/** The years that have a workout, newest first. */
fun yearsWithWorkouts(workouts: List<Workout>): List<Int> =
    workouts.mapNotNull { it.day.take(4).toIntOrNull() }.distinct().sortedDescending()

/** Whether Profile shows its quiet "Your year" entry: from 1 December to 31 January. The year it is about is [yearInReviewFor]. */
fun yearReviewSeason(today: LocalDate): Boolean = today.monthValue == 12 || today.monthValue == 1

/** The year the December-to-January entry is about: the year ending, so in January the one before. */
fun yearInReviewFor(today: LocalDate): Int = if (today.monthValue == 1) today.year - 1 else today.year

/**
 * The numbers of [year], or null when it has no workout. [volumeOf] is a workout's volume; [progressFor] is an exercise's sessions as
 * chart values (`progressOf` with its load type and bodyweights), used for the estimated 1RM; [prs] is [prsOf] over the whole history.
 * A lift's gain is its best estimate in the year less its best before the year (its first session's in the year when it had none before).
 */
fun yearReview(
    workouts: List<Workout>,
    year: Int,
    volumeOf: (Workout) -> Double,
    progressFor: (String, List<Session>) -> List<Progress>,
    prs: Map<String, List<Set<PrKind>>>,
): YearReview? {
    val range = DayRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    val inYear = workouts.filter { it.day.startsWith("$year-") }
    if (inYear.isEmpty()) return null
    val totals = totalsIn(workouts, range, volumeOf)
    val byMonth = DoubleArray(13)
    for (w in inYear) byMonth[w.day.substring(5, 7).toInt()] += volumeOf(w)
    val best = (1..12).filter { byMonth[it] > 0 }.maxByOrNull { byMonth[it] }?.let { it to byMonth[it] }
    val weeks = inYear.map { weekStart(LocalDate.parse(it.day)) }.distinct().sorted()
    var run = 0
    var longest = 0
    for ((i, w) in weeks.withIndex()) {
        run = if (i > 0 && weeks[i - 1].plusWeeks(1) == w) run + 1 else 1
        longest = maxOf(longest, run)
    }
    val gains = sessionsByExercise(workouts).mapNotNull { (exercise, sessions) ->
        val rows = progressFor(exercise, sessions).filter { it.estimated1Rm != null }
        val now = rows.filter { it.date.startsWith("$year-") }
        if (now.isEmpty()) return@mapNotNull null
        val before = rows.filter { it.date.take(4).toIntOrNull()?.let { y -> y < year } == true }.maxOfOrNull { it.estimated1Rm!! }
        val from = before ?: now.first().estimated1Rm!!
        val to = now.maxOf { it.estimated1Rm!! }
        LiftGain(exercise, from, to).takeIf { it.gain > 0 }
    }.sortedWith(compareByDescending<LiftGain> { it.gain }.thenBy { it.exercise }).take(3)
    return YearReview(
        year, totals, gains, best, longest,
        inYear.sumOf { recordCount(it, prs[it.date].orEmpty()) }, topExercise(workouts, range),
        (1..12).map { byMonth[it] }, topExercises(workouts, range, 5),
    )
}
