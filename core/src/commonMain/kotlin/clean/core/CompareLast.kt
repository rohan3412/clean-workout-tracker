package clean.core

import kotlin.math.abs

/** How an exercise's best set compares with its previous session's. */
enum class Verdict { FIRST, UP, SAME, DOWN }

/**
 * One exercise of a workout against the session of it before that workout (docs/domain.md, Compared with last time).
 * [today] and [before] are the best sets compared (the weaker side's, for left and right); [before] is null for [Verdict.FIRST].
 */
data class ExerciseComparison(val exercise: String, val verdict: Verdict, val loadType: LoadType, val today: SetItem, val before: SetItem?) {
    /** The change as words: "+2.5 kg, −1 rep", "same", "down −2.5 kg"; [weight] writes a weight in kg the way the profile shows it, with its unit. */
    fun words(weight: (Double) -> String): String {
        val before = before ?: return "First time"
        val parts = mutableListOf<String>()
        fun signed(d: Double, text: String) { if (d != 0.0) parts += (if (d > 0) "+" else "−") + text }
        val dw = (today.weight ?: 0.0) - (before.weight ?: 0.0)
        val dr = (today.reps ?: 0) - (before.reps ?: 0)
        val ds = (today.seconds ?: 0.0) - (before.seconds ?: 0.0)
        val dd = ((today.distance ?: 0.0) - (before.distance ?: 0.0)) / 1000.0
        when (loadType) {
            LoadType.TIMED -> { signed(ds, shortNumber(abs(ds)) + " s"); signed(dw, weight(abs(dw))) }
            LoadType.CARDIO -> { signed(dd, shortNumber(abs(dd)) + " km"); signed(ds, shortNumber(abs(ds)) + " s") }
            else -> {
                signed(dw, weight(abs(dw)))
                signed(dr.toDouble(), "${abs(dr)} ${if (abs(dr) == 1) "rep" else "reps"}")
            }
        }
        val change = parts.joinToString(", ")
        return when (verdict) {
            Verdict.SAME -> if (change.isEmpty()) "same" else "same ($change)"
            Verdict.DOWN -> "down $change".trim()
            else -> change
        }
    }
}

/** [rows] in workout order (first-time exercises included, as [Verdict.FIRST]); [compared] is those with an earlier session, [improved] those that went up. */
data class LastTimeComparison(val rows: List<ExerciseComparison>) {
    val compared: Int = rows.count { it.verdict != Verdict.FIRST }
    val improved: Int = rows.count { it.verdict == Verdict.UP }
}

/** The set that stands for a session of [type]: the best working set by that kind of exercise's own measure, or null with none. */
private fun bestFor(sets: List<SetItem>, type: LoadType): SetItem? {
    val working = sets.filter { it.type != SetType.WARMUP }
    return when (type) {
        LoadType.TIMED -> working.maxWithOrNull(compareBy<SetItem> { it.seconds ?: 0.0 }.thenBy { it.weight ?: 0.0 })
        LoadType.CARDIO -> working.maxWithOrNull(compareBy<SetItem> { it.distance ?: 0.0 }.thenByDescending { it.seconds ?: Double.MAX_VALUE })
        // Less help is better.
        LoadType.ASSISTED -> working.maxWithOrNull(compareBy<SetItem> { -(it.weight ?: 0.0) }.thenBy { it.reps ?: 0 })
        else -> bestSetOf(sets)
    }
}

/**
 * Sign of [a] against [b] by the measure of [type]: the estimated 1RM of the best set by [rule] (with no rep limit short of Brzycki's,
 * so a long set still compares; reps when neither has a weight), seconds, distance then time.
 */
private fun compareSets(a: SetItem, b: SetItem, type: LoadType, rule: OneRm): Int {
    fun round(d: Double) = jRound(d * 1e6) / 1e6
    val open = rule.copy(maxReps = 30)
    fun epley(s: SetItem) = round(estimate1Rm(s.weight ?: 0.0, s.reps ?: 0, open, s.rpe) ?: 0.0)
    return when (type) {
        LoadType.TIMED -> round(a.seconds ?: 0.0).compareTo(round(b.seconds ?: 0.0))
        LoadType.CARDIO -> round(a.distance ?: 0.0).compareTo(round(b.distance ?: 0.0)).let {
            if (it != 0) it else round(b.seconds ?: 0.0).compareTo(round(a.seconds ?: 0.0))
        }
        LoadType.ASSISTED -> round(b.weight ?: 0.0).compareTo(round(a.weight ?: 0.0)).let { if (it != 0) it else (a.reps ?: 0).compareTo(b.reps ?: 0) }
        else -> if ((a.weight ?: 0.0) <= 0.0 && (b.weight ?: 0.0) <= 0.0) (a.reps ?: 0).compareTo(b.reps ?: 0) else epley(a).compareTo(epley(b))
    }
}

/** A session's representative set; in a left and right session the weaker side's best set. */
private fun representative(session: Session, type: LoadType, rule: OneRm): SetItem? {
    if (!session.paired) return bestFor(session.sets, type)
    val sides = listOf(Side.LEFT, Side.RIGHT).mapNotNull { bestFor(session.forSide(it).sets, type) }
    return sides.reduceOrNull { a, b -> if (compareSets(a, b, type, rule) <= 0) a else b }
}

/**
 * Each exercise of [workout], in order, against its session before the workout's date (not the latest overall, so an old
 * workout compares with the one before it). [sessions] gives an exercise's sessions, oldest first, as `sessionsIn` does.
 * A session without a working set is skipped on either side. Left and right exercises compare the weaker side
 * of each session.
 */
fun compareWithLast(workout: Workout, sessions: (String) -> List<Session>, loadTypeOf: (String) -> LoadType, rule: OneRm = OneRm.DEFAULT): LastTimeComparison {
    val rows = workout.blocks.map { it.exercise }.distinct().mapNotNull { exercise ->
        val type = loadTypeOf(exercise)
        val all = sessions(exercise)
        val today = all.lastOrNull { it.date == workout.date }?.let { representative(it, type, rule) } ?: return@mapNotNull null
        val earlier = all.filter { it.date < workout.date }.asReversed().firstNotNullOfOrNull { representative(it, type, rule) }
        val verdict = if (earlier == null) Verdict.FIRST else compareSets(today, earlier, type, rule).let { if (it > 0) Verdict.UP else if (it < 0) Verdict.DOWN else Verdict.SAME }
        ExerciseComparison(exercise, verdict, type, today, earlier)
    }
    return LastTimeComparison(rows)
}
