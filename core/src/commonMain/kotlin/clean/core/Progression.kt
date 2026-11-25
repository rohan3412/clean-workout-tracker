package clean.core

import kotlin.math.abs

/** A rep range and a weight step, both in kg like everything stored: double progression (docs/domain.md, Progression). */
data class Progression(val minReps: Int, val maxReps: Int, val step: Double) {
    /** `8-12@2.5`. */
    fun encode(): String = "$minReps-$maxReps@${decimalText(step)}"

    companion object {
        fun decode(text: String?): Progression? {
            val range = text?.substringBefore('@') ?: return null
            val min = range.substringBefore('-').toIntOrNull() ?: return null
            val max = range.substringAfter('-', "").toIntOrNull() ?: return null
            val step = text.substringAfter('@', "").toDoubleOrNull() ?: return null
            return if (min in 1..max && step > 0) Progression(min, max, step) else null
        }
    }
}

/** A set at this RPE or above is as hard as the lifter wanted and gets no added rep. */
const val PROGRESSION_HARD_RPE = 9.5

/**
 * What to aim for next time, one target per working set of [lastSession] (warm-ups and drop sets are
 * not targets), in the order they were done. Where heavier is harder only: normal and added-bodyweight
 * lifts; an assisted, timed or cardio exercise gets no targets.
 *
 * - Every working set at one weight and every one at [Progression.maxReps] or more: all sets go up by
 *   [Progression.step] and back to [Progression.minReps].
 * - Otherwise each set keeps its weight and aims for one more rep, unless it already has [Progression.maxReps],
 *   or was to failure or at RPE [PROGRESSION_HARD_RPE] or more, when it repeats weight and reps.
 */
fun nextFor(lastSession: List<SetItem>, scheme: Progression, loadType: LoadType = LoadType.NORMAL): List<Target> {
    val sets = targetSets(lastSession, loadType)
    return targetsFor(sets, scheme, reachedTop(sets, scheme))
}

/**
 * [nextFor] for an exercise done left and right: each side's targets come from that side's own last session
 * ([left] and [right] are its series, [Session.forSide]). The weight goes up only when both sides reached the top
 * of the range at one weight; otherwise each side adds a rep as the ordinary rule says. A side with no sets
 * leaves the other to decide. With the same sets on both sides this is [nextFor] twice.
 */
fun nextForSides(left: List<SetItem>, right: List<SetItem>, scheme: Progression, loadType: LoadType = LoadType.NORMAL): Pair<List<Target>, List<Target>> {
    val l = targetSets(left, loadType)
    val r = targetSets(right, loadType)
    val up = listOf(l, r).filter { it.isNotEmpty() }.all { reachedTop(it, scheme) }
    return targetsFor(l, scheme, up) to targetsFor(r, scheme, up)
}

/** The sets that are targets: normal and failure sets with a weight and reps, in lifts where heavier is harder only. */
private fun targetSets(lastSession: List<SetItem>, loadType: LoadType): List<SetItem> {
    if (loadType != LoadType.NORMAL && loadType != LoadType.BODYWEIGHT) return emptyList()
    return lastSession.filter { (it.type == SetType.NORMAL || it.type == SetType.FAILURE) && it.weight != null && (it.reps ?: 0) >= 1 }
}

/** Every set at one weight and every one at the range's top or more. */
private fun reachedTop(sets: List<SetItem>, scheme: Progression): Boolean {
    if (sets.isEmpty()) return false
    val first = sets.first().weight!!
    return sets.all { abs(it.weight!! - first) < 1e-6 } && sets.all { it.reps!! >= scheme.maxReps }
}

private fun targetsFor(sets: List<SetItem>, scheme: Progression, up: Boolean): List<Target> {
    if (sets.isEmpty()) return emptyList()
    if (up) {
        val raised = jRound((sets.first().weight!! + scheme.step) * 10000) / 10000.0
        return sets.map { Target(raised, scheme.minReps) }
    }
    return sets.map { set ->
        val reps = set.reps!!
        val hard = set.type == SetType.FAILURE || (set.row.rpe.toDoubleOrNull() ?: 0.0) >= PROGRESSION_HARD_RPE
        Target(set.weight!!, if (hard || reps >= scheme.maxReps) reps else reps + 1)
    }
}
