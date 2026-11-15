package clean.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** How far a goal has come, whether it is met, and the day the recent trend reaches it (null when the trend says nothing). */
data class GoalProgress(val fraction: Double, val reached: Boolean, val projected: LocalDate?)

/** The window the projection fits, the least it needs and the furthest it will look ahead. */
const val GOAL_FIT_DAYS = 56L
const val GOAL_FIT_MIN_POINTS = 3
const val GOAL_FIT_MIN_SPAN_DAYS = 14L
const val GOAL_FIT_MAX_AHEAD_DAYS = 730L

/** The share of the way [have] is from [from] to [target], 0 to 1; a target already at [from] counts as there once [have] has reached it. */
fun shareOf(have: Double, from: Double, target: Double): Double = when {
    target == from -> if (have == target) 1.0 else 0.0
    else -> ((have - from) / (target - from)).coerceIn(0.0, 1.0)
}

/**
 * The day the straight line fitted to [points] (the last [GOAL_FIT_DAYS] days up to [today]) reaches [target], or null: fewer than
 * [GOAL_FIT_MIN_POINTS] points, under [GOAL_FIT_MIN_SPAN_DAYS] between the first and last, a line that is flat or heads the wrong way
 * ([rising] false is a goal below the values), or a day more than [GOAL_FIT_MAX_AHEAD_DAYS] away. A line already at the target gives today.
 */
fun projectDate(points: List<Pair<LocalDate, Double>>, target: Double, today: LocalDate, rising: Boolean = true): LocalDate? {
    val recent = points.filter { !it.first.isBefore(today.minusDays(GOAL_FIT_DAYS)) && !it.first.isAfter(today) }.sortedBy { it.first }
    if (recent.size < GOAL_FIT_MIN_POINTS) return null
    if (recent.last().first.toEpochDay() - recent.first().first.toEpochDay() < GOAL_FIT_MIN_SPAN_DAYS) return null
    val sign = if (rising) 1.0 else -1.0
    val (slope, intercept) = linearFit(recent.map { it.first.toEpochDay().toDouble() to it.second * sign }) ?: return null
    if (slope <= 1e-9) return null
    val day = ceil((target * sign - intercept) / slope - 1e-6).toLong()
    val at = if (day <= today.toEpochDay()) today else LocalDate.ofEpochDay(day)
    return at.takeIf { it.toEpochDay() - today.toEpochDay() <= GOAL_FIT_MAX_AHEAD_DAYS }
}

/** `12 Feb`, with the year added when it is not [today]'s. */
fun projectedDay(day: LocalDate, today: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))

/** `On track for about 12 Feb`. */
fun projectedLine(day: LocalDate, today: LocalDate) = "On track for about ${projectedDay(day, today)}"

/**
 * How far an exercise goal of [kg] for [reps] has come. Up to the rule's rep limit it is the best estimated 1RM as a share of the goal's,
 * and the date comes from the estimated 1RM of the last eight weeks' sessions. Past the limit it is the most reps at the goal's weight or
 * more (the weaker side's, for left and right) over the goal's reps, with no date. A met goal is whole. Null for a timed or cardio exercise.
 */
fun exerciseGoalProgress(
    sessions: List<Session>, kg: Double, reps: Int, rule: OneRm, loadType: LoadType, bodyweightAt: (String) -> Double?, today: LocalDate,
): GoalProgress? {
    if (loadType == LoadType.TIMED || loadType == LoadType.CARDIO || reps < 1) return null
    val reached = goalReachedOn(sessions, kg, reps, rule, loadType, bodyweightAt) != null
    val target = goalOneRm(kg, reps, rule, loadType, bodyweightAt("$today 00:00:00"))
    if (target == null) {
        fun mostOn(series: List<Session>) = series.flatMap { it.sets }
            .filter { it.type != SetType.WARMUP && it.weight != null && (if (loadType == LoadType.ASSISTED) it.weight <= kg else it.weight >= kg) }
            .maxOfOrNull { it.reps ?: 0 } ?: 0
        val most = if (sessions.any { it.paired }) Side.values().minOf { side -> mostOn(sessions.map { it.forSide(side) }) } else mostOn(sessions)
        return GoalProgress(if (reached) 1.0 else (most.toDouble() / reps).coerceIn(0.0, 1.0), reached, null)
    }
    val best = recordsOf(sessions, rule, loadType, bodyweightAt).estimated1Rm?.value ?: 0.0
    if (reached) return GoalProgress(1.0, true, null)
    val points = progressOf(sessions, rule, loadType, bodyweightAt).mapNotNull { p -> p.estimated1Rm?.let { LocalDate.parse(p.date.take(10)) to it } }
    return GoalProgress((best / target).coerceIn(0.0, 1.0), false, projectDate(points, target, today))
}

/**
 * A body-weight goal, kept per profile (kv `goal.weight:<profileId>`) as `target|start|yyyy-MM-dd`: the target and the weight when it was set,
 * both in kg, and the day. Lose or gain is read from the two: a start above the target is a loss.
 */
data class WeightGoal(val targetKg: Double, val startKg: Double, val since: LocalDate) {
    val lose: Boolean get() = startKg > targetKg

    fun encode() = "${decimalText(targetKg)}|${decimalText(startKg)}|$since"

    /** The share of the distance from the start to the target covered by [currentKg]. */
    fun progress(currentKg: Double) = shareOf(currentKg, startKg, targetKg)

    fun reached(currentKg: Double) = if (lose) currentKg <= targetKg else currentKg >= targetKg

    /** Progress, and the projected day from [weighIns] (day, kg), which are fitted over the last eight weeks. */
    fun progressOf(weighIns: List<Pair<LocalDate, Double>>, today: LocalDate): GoalProgress? {
        val current = weighIns.maxByOrNull { it.first }?.second ?: return null
        val met = reached(current)
        return GoalProgress(if (met) 1.0 else progress(current), met, if (met) null else projectDate(weighIns, targetKg, today, rising = !lose))
    }

    companion object {
        /** A goal at [targetKg] set while weighing [currentKg]; null when they are the same, as there is no direction. */
        fun set(targetKg: Double, currentKg: Double, today: LocalDate) =
            if (targetKg > 0 && targetKg != currentKg) WeightGoal(targetKg, currentKg, today) else null

        fun decode(text: String?): WeightGoal? {
            val parts = text?.split('|') ?: return null
            if (parts.size != 3) return null
            val target = parts[0].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
            val start = parts[1].toDoubleOrNull()?.takeIf { it > 0 && it != target } ?: return null
            val since = runCatching { LocalDate.parse(parts[2]) }.getOrNull() ?: return null
            return WeightGoal(target, start, since)
        }
    }
}
