package clean.core

/*
 * Template values: a template keeps, per planned set, an optional weight (always kg) and reps to aim for.
 * Strong's end-of-workout prompt can update them ("Update values only"); these are the rules for that.
 * docs/domain.md, Template values.
 */

/** What a planned set aims for; either part may be missing (a bodyweight set has no weight). A timed set's reps are its seconds. */
data class SetTarget(val weightKg: Double?, val reps: Int?)

/**
 * One planned exercise of a template. [targets] runs alongside [sets] by index; a shorter list means no values for
 * the rest. A left and right exercise keeps one set, and one target, per pair.
 */
data class TemplateBlock(val exercise: String, val sets: List<SetType>, val superset: String? = null, val targets: List<SetTarget?> = emptyList())

private const val SAME_WEIGHT = 1e-9

private fun SetTarget?.holdsValue() = this != null && (weightKg != null || reps != null)

/** [targets] with a set that holds nothing as null and trailing nulls dropped: a template with no values holds an empty list. */
fun tidyTargets(targets: List<SetTarget?>): List<SetTarget?> =
    targets.map { it.takeIf { t -> t.holdsValue() } }.dropLastWhile { it == null }

/**
 * Whether [new] beats [old]: a heavier weight, or the same weight for more reps. A missing weight or reps counts
 * as 0. An [old] with no values is beaten by any [new] that has some; a [new] with none beats nothing.
 */
fun isIncrease(old: SetTarget?, new: SetTarget?): Boolean {
    if (!new.holdsValue()) return false
    if (!old.holdsValue()) return true
    val heavier = (new!!.weightKg ?: 0.0) - (old!!.weightKg ?: 0.0)
    return heavier > SAME_WEIGHT || (heavier >= -SAME_WEIGHT && (new.reps ?: 0) > (old.reps ?: 0))
}

/** The sets of [done] that beat the template's: set [set] of the template's block [templateBlock], done in block [doneBlock]. */
data class ValueIncrease(val templateBlock: Int, val doneBlock: Int, val set: Int)

/** Which template block each of [done] is: by name, the nth block of a name in the workout being the nth in the template; null when it has none. */
private fun matchBlocks(template: List<TemplateBlock>, done: List<TemplateBlock>): List<Int?> {
    val seen = HashMap<String, Int>()
    return done.map { d ->
        val n = seen.getOrDefault(d.exercise, 0)
        seen[d.exercise] = n + 1
        template.indices.filter { template[it].exercise == d.exercise }.getOrNull(n)
    }
}

/**
 * The sets of the finished workout [done] (its ticked sets, as template blocks) that beat what [template] holds.
 * Sets are matched by position within a matched exercise and only when their kinds agree; sets beyond the
 * template's are not values, and a lower or equal set is not an increase.
 */
fun valueIncreases(template: List<TemplateBlock>, done: List<TemplateBlock>): List<ValueIncrease> =
    matchBlocks(template, done).flatMapIndexed { di, ti ->
        if (ti == null) emptyList() else {
            val t = template[ti]
            val d = done[di]
            (0 until minOf(t.sets.size, d.sets.size))
                .filter { i -> t.sets[i] == d.sets[i] && isIncrease(t.targets.getOrNull(i), d.targets.getOrNull(i)) }
                .map { ValueIncrease(ti, di, it) }
        }
    }

/** [template] with only the increased sets' values taken from [done]; its exercises and sets stay as they are. */
fun withValuesUpdated(template: List<TemplateBlock>, done: List<TemplateBlock>): List<TemplateBlock> {
    val up = valueIncreases(template, done)
    return template.mapIndexed { ti, t ->
        val mine = up.filter { it.templateBlock == ti }
        if (mine.isEmpty()) t else {
            val targets = MutableList(t.sets.size) { t.targets.getOrNull(it) }
            for (u in mine) targets[u.set] = done[u.doneBlock].targets.getOrNull(u.set)
            t.copy(targets = tidyTargets(targets))
        }
    }
}

/**
 * The shape of [done] as the new template, with the values of what is already planned kept unless the workout
 * beat them. A set the template did not have (a new exercise, or beyond its sets, or of another kind) takes the workout's.
 */
fun withStructureUpdated(template: List<TemplateBlock>, done: List<TemplateBlock>): List<TemplateBlock> {
    val matched = matchBlocks(template, done)
    return done.mapIndexed { di, d ->
        val t = matched[di]?.let { template[it] }
        val targets = d.sets.indices.map { i ->
            val new = d.targets.getOrNull(i)
            if (t != null && i < t.sets.size && t.sets[i] == d.sets[i]) t.targets.getOrNull(i).let { old -> if (isIncrease(old, new)) new else old } else new
        }
        d.copy(targets = tidyTargets(targets))
    }
}

/** What differs in the exercises and sets, as the prompt counts them. Sets are counted only for exercises in both. */
data class ShapeChange(val addedExercises: Int, val removedExercises: Int, val addedSets: Int, val removedSets: Int)

fun shapeChange(before: List<TemplateBlock>, after: List<TemplateBlock>): ShapeChange {
    val old = before.groupBy { it.exercise }.mapValues { (_, b) -> b.sumOf { it.sets.size } }
    val new = after.groupBy { it.exercise }.mapValues { (_, b) -> b.sumOf { it.sets.size } }
    val both = old.keys.filter { it in new }
    return ShapeChange(
        addedExercises = new.keys.count { it !in old },
        removedExercises = old.keys.count { it !in new },
        addedSets = both.sumOf { maxOf(0, new.getValue(it) - old.getValue(it)) },
        removedSets = both.sumOf { maxOf(0, old.getValue(it) - new.getValue(it)) },
    )
}

/** Whether the two lists plan the same exercises in the same order with the same kinds of sets and supersets; values are not looked at. */
fun sameShape(before: List<TemplateBlock>, after: List<TemplateBlock>): Boolean =
    before.size == after.size && before.indices.all { i ->
        before[i].exercise == after[i].exercise && before[i].sets == after[i].sets && before[i].superset == after[i].superset
    }

/**
 * Whether a set still not ticked holds what its exercise logs, so that ticking it makes sense: reps typed (seconds
 * for a timed hold; kilometres and minutes for cardio), and a weight typed when the load takes one. A box showing
 * only last time's value is empty text here. Bodyweight, assisted and timed sets may have no weight.
 */
fun isValidSet(loadType: LoadType, weight: String, reps: String): Boolean {
    fun number(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    return when (loadType) {
        LoadType.CARDIO -> number(weight) != null && number(reps) != null
        LoadType.TIMED -> number(reps) != null && (weight.isBlank() || number(weight) != null)
        LoadType.BODYWEIGHT, LoadType.ASSISTED -> reps.trim().toIntOrNull() != null && (weight.isBlank() || number(weight) != null)
        LoadType.NORMAL -> reps.trim().toIntOrNull() != null && number(weight) != null
    }
}
