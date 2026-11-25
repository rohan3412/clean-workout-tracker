package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate

/** Exercises of a goal, and the most trained of the last [DELOAD_WINDOW_WEEKS] weeks, are checked for a stall: at least this many stalled is a signal. */
const val DELOAD_STALLED_MIN = 3
const val DELOAD_TOP_EXERCISES = 10

/** The weeks that make up "usual", and the weeks looked back over for a lighter one. */
const val DELOAD_WINDOW_WEEKS = 8

/** A week counts as a rise at this share of the 8 weeks before it, for [DELOAD_RISE_WEEKS] complete weeks running. */
const val DELOAD_RISE = 1.25
const val DELOAD_RISE_WEEKS = 3

/** Muscle groups reading fatigued now. */
const val DELOAD_FATIGUED_MIN = 4

/** A lighter week has at most this share of the usual weekly working sets. */
const val DELOAD_LIGHT_SHARE = 0.7

/** A lighter week is suggested when at least this many signals hold. */
const val DELOAD_SIGNALS_MIN = 2

enum class DeloadSignal { STALLED, VOLUME_RISE, FATIGUED, NO_LIGHT_WEEK }

/** A signal that holds, in one line; [exercises] are the stalled ones for the first signal. */
class DeloadFinding(val signal: DeloadSignal, val reason: String, val exercises: List<String> = emptyList())

class Deload(val findings: List<DeloadFinding>) {
    val suggested get() = findings.size >= DELOAD_SIGNALS_MIN
}

/**
 * Whether a lighter week might help (docs/domain.md, Deload suggestion), from the log alone.
 * [progressOf] gives an exercise's sessions as chart values, [fatigue] the current level of each muscle.
 * A signal that needs more history than there is does not hold.
 */
fun deloadSignals(workouts: List<Workout>, today: LocalDate, goalExercises: Set<String>, progressOf: (String) -> List<Progress>, fatigue: Map<Muscle, Double>): Deload {
    val found = ArrayList<DeloadFinding>()
    val thisWeek = weekStart(today)
    val sets = HashMap<LocalDate, Double>()
    for (w in workouts) weekStart(LocalDate.parse(w.day)).let { k -> sets[k] = (sets[k] ?: 0.0) + w.workingCount }
    val first = sets.keys.minOrNull()
    fun week(back: Int) = thisWeek.minusWeeks(back.toLong())
    fun inWeek(back: Int) = sets[week(back)] ?: 0.0
    fun usualBefore(back: Int) = (back + 1..back + DELOAD_WINDOW_WEEKS).sumOf { inWeek(it) } / DELOAD_WINDOW_WEEKS.toDouble()

    // (a) Stalled exercises among those with a goal and the most trained lately.
    val cutoff = today.minusWeeks(DELOAD_WINDOW_WEEKS.toLong())
    val trained = HashMap<String, Double>()
    for (w in workouts) if (LocalDate.parse(w.day) > cutoff) for (b in w.blocks) if (b.workingCount > 0) trained[b.exercise] = (trained[b.exercise] ?: 0.0) + b.workingCount
    val checked = goalExercises + trained.entries.sortedByDescending { it.value }.take(DELOAD_TOP_EXERCISES).map { it.key }
    val stuck = checked.filter { stalled(progressOf(it)) }.sorted()
    if (stuck.size >= DELOAD_STALLED_MIN) found += DeloadFinding(DeloadSignal.STALLED, "${stuck.size} exercises have set no new best in their last five sessions.", stuck)

    // (b) Each of the last three complete weeks well above the eight before it.
    val enoughForRise = first != null && first <= week(DELOAD_RISE_WEEKS + DELOAD_WINDOW_WEEKS)
    if (enoughForRise && (1..DELOAD_RISE_WEEKS).all { usualBefore(it) > 0 && inWeek(it) >= DELOAD_RISE * usualBefore(it) }) {
        found += DeloadFinding(DeloadSignal.VOLUME_RISE, "Sets a week have been at least 25% above the weeks before, $DELOAD_RISE_WEEKS weeks running.")
    }

    // (c) Several muscles tired right now.
    val tired = fatigue.count { fatigueWord(it.value) == "fatigued" }
    if (tired >= DELOAD_FATIGUED_MIN) found += DeloadFinding(DeloadSignal.FATIGUED, "$tired muscle groups read fatigued now.")

    // (d) No week of at most 70% of the usual in the last eight complete weeks.
    if (first != null && first <= week(DELOAD_WINDOW_WEEKS)) {
        val recent = (1..DELOAD_WINDOW_WEEKS).map { inWeek(it) }
        val usual = recent.average()
        if (usual > 0 && recent.none { it <= DELOAD_LIGHT_SHARE * usual }) {
            found += DeloadFinding(DeloadSignal.NO_LIGHT_WEEK, "No lighter week in the last $DELOAD_WINDOW_WEEKS weeks.")
        }
    }
    return Deload(found)
}
