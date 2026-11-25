package clean.core

import clean.core.Muscle.*

/** The three groups the training split is told in. */
enum class SplitGroup(val label: String) { PUSH("Push"), PULL("Pull"), LEGS("Legs") }

/**
 * Which group a main muscle belongs to: pressing muscles push, back and arm-flexing muscles (and the lower back, which the hinge and the
 * row work) pull, the hips and legs are legs. Abs, obliques, neck and the rotator cuff belong to none of the three.
 */
fun splitGroupOf(muscle: Muscle): SplitGroup? = when (muscle) {
    CHEST, SHOULDERS, TRICEPS, SERRATUS -> SplitGroup.PUSH
    LATS, UPPER_BACK, TRAPS, BICEPS, FOREARMS, LOWER_BACK -> SplitGroup.PULL
    GLUTES, QUADS, HAMSTRINGS, ADDUCTORS, CALVES, TIBIALIS, HIP_FLEXORS -> SplitGroup.LEGS
    ABS, OBLIQUES, NECK, ROTATOR_CUFF -> null
}

/** The share of sets each group had, in percent that add up to 100. */
data class TrainingSplit(val push: Int, val pull: Int, val legs: Int) {
    fun of(group: SplitGroup) = when (group) { SplitGroup.PUSH -> push; SplitGroup.PULL -> pull; SplitGroup.LEGS -> legs }
}

/**
 * The split of the working sets of [workouts] done on or after [since] (`YYYY-MM-DD`) between push, pull and legs. A set counts once, shared
 * equally between the groups of the exercise's main muscles ([primaryOf]); an exercise with no main muscle in a group is left out. Null when
 * nothing counts. The percents are rounded by largest remainder so they add up to 100.
 */
fun trainingSplit(workouts: List<Workout>, since: String, primaryOf: (String) -> Set<Muscle>): TrainingSplit? {
    val totals = DoubleArray(3)
    val groups = HashMap<String, List<SplitGroup>>()
    for (w in workouts) if (w.day >= since) for (b in w.blocks) {
        val sets = b.workingSets
        if (sets == 0) continue
        val g = groups.getOrPut(b.exercise) { primaryOf(b.exercise).mapNotNull(::splitGroupOf).distinct() }
        for (x in g) totals[x.ordinal] += sets.toDouble() / g.size
    }
    val sum = totals.sum()
    if (sum <= 0.0) return null
    val exact = totals.map { it / sum * 100 }
    val floors = exact.map { it.toInt() }.toIntArray()
    var left = 100 - floors.sum()
    for (i in exact.indices.sortedByDescending { exact[it] - floors[it] }) if (left-- > 0) floors[i]++
    return TrainingSplit(floors[0], floors[1], floors[2])
}
