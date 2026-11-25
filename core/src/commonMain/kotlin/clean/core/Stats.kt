package clean.core

import clean.core.time.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The Monday on or before [day]. */
fun weekStart(day: LocalDate) = day.previousOrSameMonday()

/** One week of training, Monday to Sunday. */
/** [sets] counts a round of a left and right exercise as one set, so it can end in a half. */
data class WeekTotal(val monday: LocalDate, val workouts: Int, val seconds: Long, val volume: Double, val sets: Double)

/**
 * Every week from the first workout's to the last one's, oldest first, with what was done in it.
 * A week with no workout is there too, as zeros: a break is part of the picture.
 */
fun weeklyTotals(workouts: List<Workout>, loadOf: LoadOf = LOGGED_LOAD): List<WeekTotal> {
    if (workouts.isEmpty()) return emptyList()
    val byWeek = workouts.groupBy { weekStart(LocalDate.parse(it.day)) }
    val weeks = mutableListOf<WeekTotal>()
    var week = byWeek.keys.min()
    val last = byWeek.keys.max()
    while (week <= last) {
        val done = byWeek[week].orEmpty()
        weeks += WeekTotal(
            week, done.size, done.sumOf { it.durationSeconds }, done.sumOf { volumeOf(it, loadOf) },
            done.sumOf { w -> w.workingCount },
        )
        week = week.plusWeeks(1)
    }
    return weeks
}

/**
 * The straight line that fits [points] best (least squares), as its slope and its value at x = 0.
 * Null when there are fewer than three points or they all share one x.
 */
fun linearFit(points: List<Pair<Double, Double>>): Pair<Double, Double>? {
    if (points.size < 3) return null
    val meanX = points.sumOf { it.first } / points.size
    val meanY = points.sumOf { it.second } / points.size
    val spread = points.sumOf { (it.first - meanX) * (it.first - meanX) }
    if (spread == 0.0) return null
    val slope = points.sumOf { (it.first - meanX) * (it.second - meanY) } / spread
    return slope to meanY - slope * meanX
}

/** Seconds trained on each day that has a workout. */
fun secondsByDay(workouts: List<Workout>): Map<LocalDate, Long> =
    workouts.groupBy { LocalDate.parse(it.day) }.mapValues { (_, w) -> w.sumOf { it.durationSeconds } }

/** Volume per day. */
fun volumeByDay(workouts: List<Workout>, loadOf: LoadOf = LOGGED_LOAD): Map<LocalDate, Double> =
    workouts.groupBy { LocalDate.parse(it.day) }.mapValues { (_, w) -> w.sumOf { volumeOf(it, loadOf) } }

/**
 * Real load x reps over every non-warm-up set. A set whose real load is unknown (a bodyweight or
 * assisted lift with no weigh-in near the workout) adds nothing. A set of a left and right block
 * counts half, so a round adds the average of its two sides.
 */
fun volumeOf(workout: Workout, loadOf: LoadOf = LOGGED_LOAD): Double =
    workout.blocks.sumOf { b ->
        b.sets.filter { it.type != SetType.WARMUP }.sumOf { (loadOf(b.exercise, it.weight ?: 0.0, workout.date) ?: 0.0) * (it.reps ?: 0) * b.weightOf(it) }
    }

/**
 * The kinds of personal record a set can set. A [matched] kind is not a record: the set equalled
 * the best there was, which is worth a mention of its own. [WEIGHT] is the most reps at a weight
 * lifted before, which the user calls just "weight"; there is no kind for the heaviest weight as such.
 * [DURATION] is the longest hold. The order is the order of the badges on screen.
 */
enum class PrKind(val label: String, val matched: Boolean = false) {
    ONE_RM("1RM"), WEIGHT("Weight"), VOLUME("Set vol."), REPS("Reps"),
    ONE_RM_MATCHED("1RM", true), WEIGHT_MATCHED("Weight", true), VOLUME_MATCHED("Set vol.", true), REPS_MATCHED("Reps", true),
    DURATION("Duration"), DURATION_MATCHED("Duration", true),
}

/** A set as the PR rule reads it: a hold has [seconds] and no [reps]. */
data class PrSet(val type: SetType, val weight: Double?, val reps: Int?, val seconds: Double?, val rpe: Double? = null)

fun SetItem.prSet() = PrSet(type, weight, reps, seconds, rpe)

/** The best of each kind so far for one exercise. */
class PrBests {
    var estimate = 0.0
    var volume = 0.0
    var reps = 0
    var seconds = 0.0
    val atWeight = HashMap<Double, Int>()
    val secondsAt = HashMap<Double, Double>()
    /** The heaviest and the lightest weight above zero of a working set (the lightest is an assisted lift's best); 0 for none yet. */
    var heaviest = 0.0
    var lightest = 0.0

    fun copy() = PrBests().also { it.absorb(this) }

    /** Keeps the better of each figure of the two. */
    fun absorb(other: PrBests) {
        estimate = maxOf(estimate, other.estimate)
        volume = maxOf(volume, other.volume)
        reps = maxOf(reps, other.reps)
        seconds = maxOf(seconds, other.seconds)
        for ((w, r) in other.atWeight) if (r > (atWeight[w] ?: 0)) atWeight[w] = r
        for ((w, s) in other.secondsAt) if (s > (secondsAt[w] ?: 0.0)) secondsAt[w] = s
        heaviest = maxOf(heaviest, other.heaviest)
        if (other.lightest > 0 && (lightest == 0.0 || other.lightest < lightest)) lightest = other.lightest
    }
}

/**
 * The one PR rule, for a finished workout and for one being logged alike: the kinds each of [sets]
 * (one exercise, one workout, in order) earns against [before]. Given generously (the user's wish):
 * the set that first reached the workout's best gets
 * - the best estimated 1RM and the most volume in one set, both on the real load,
 * - the most reps in one set,
 * - "weight": the most reps at a weight that has been lifted before, or a weight heavier than any before (the least assistance for an
 *   assisted lift; since 2026-11-02, the user's "highest weight in an exercise should be a weight PR"); one badge per workout, a PR
 *   over a match, then the heaviest weight; an equal heaviest weight is judged by its reps at that weight,
 * - "duration": the longest hold, a set with seconds and no reps (and, at a weight held before, "weight" too).
 * An exercise's first ever workout earns all but "weight". Equalling the best gives the matched kind
 * instead. Warm-ups and drop sets set none, and cardio has no holds. With [commit] the workout is
 * folded into [before] afterwards; without it [before] is left as it was.
 */
fun judgePrs(before: PrBests, sets: List<PrSet>, loadType: LoadType, bodyweight: Double?, rule: OneRm, commit: Boolean = true): List<Set<PrKind>> {
    val prs = List(sets.size) { mutableSetOf<PrKind>() }
    // This workout's best of each kind, and the set that reached it first.
    val now = PrBests()
    var estimateAt = -1
    var volumeAt = -1
    var repsAt = -1
    var secondsAt = -1
    val atWeightAt = HashMap<Double, Int>()
    val heldAtAt = HashMap<Double, Int>()
    // The set that first reached this workout's heaviest (or, assisted, lightest) weight.
    var topAt = -1
    val assisted = loadType == LoadType.ASSISTED
    for ((index, set) in sets.withIndex()) {
        if (set.type == SetType.WARMUP || set.type == SetType.DROP) continue
        val lifted = set.weight?.takeIf { it > 0 && loadType != LoadType.CARDIO && ((set.reps ?: 0) >= 1 || (set.seconds ?: 0.0) > 0) }
        if (lifted != null) {
            if (lifted > now.heaviest) { now.heaviest = lifted; if (!assisted) topAt = index }
            if (now.lightest == 0.0 || lifted < now.lightest) { now.lightest = lifted; if (assisted) topAt = index }
        }
        val reps = set.reps ?: 0
        if (reps < 1) {
            val held = set.seconds?.takeIf { it > 0 && loadType != LoadType.CARDIO } ?: continue
            if (held > now.seconds) {
                now.seconds = held
                secondsAt = index
            }
            val weight = set.weight
            if (weight != null && weight > 0 && held > (now.secondsAt[weight] ?: 0.0)) {
                now.secondsAt[weight] = held
                heldAtAt[weight] = index
            }
            continue
        }
        if (reps > now.reps) {
            now.reps = reps
            repsAt = index
        }
        val weight = set.weight ?: continue
        if (reps > (now.atWeight[weight] ?: 0)) {
            now.atWeight[weight] = reps
            atWeightAt[weight] = index
        }
        val load = realLoad(loadType, weight, bodyweight) ?: continue
        val estimate = estimate1Rm(load, reps, rule, set.rpe) ?: 0.0
        if (estimate > now.estimate) {
            now.estimate = estimate
            estimateAt = index
        }
        if (load * reps > now.volume) {
            now.volume = load * reps
            volumeAt = index
        }
    }

    /** [compared] is above zero when this workout did better than everything before it, zero when it did the same. */
    fun judge(at: Int, had: Boolean, compared: Int, kind: PrKind, matched: PrKind) {
        if (at < 0) return
        if (!had || compared > 0) prs[at] += kind else if (compared == 0) prs[at] += matched
    }

    judge(estimateAt, before.estimate > 0, now.estimate.compareTo(before.estimate), PrKind.ONE_RM, PrKind.ONE_RM_MATCHED)
    judge(volumeAt, before.volume > 0, now.volume.compareTo(before.volume), PrKind.VOLUME, PrKind.VOLUME_MATCHED)
    judge(repsAt, before.reps > 0, now.reps.compareTo(before.reps), PrKind.REPS, PrKind.REPS_MATCHED)
    judge(secondsAt, before.seconds > 0, now.seconds.compareTo(before.seconds), PrKind.DURATION, PrKind.DURATION_MATCHED)
    // "Weight" is judged at every weight, but a workout carries one badge of it per exercise: a PR over a match, and of
    // several, the one at the heaviest weight. A weight never lifted before has no reps (or seconds) to beat.
    class AtWeight(val weight: Double, val index: Int, val compared: Int)
    val atWeight = ArrayList<AtWeight>()
    for ((weight, reps) in now.atWeight) {
        val old = before.atWeight[weight] ?: continue
        atWeight += AtWeight(weight, atWeightAt.getValue(weight), reps.compareTo(old))
    }
    for ((weight, seconds) in now.secondsAt) {
        val old = before.secondsAt[weight] ?: continue
        atWeight += AtWeight(weight, heldAtAt.getValue(weight), seconds.compareTo(old))
    }
    // A weight past every one before is a PR of its own; an exercise's first workout has none to pass.
    val top = if (assisted) now.lightest else now.heaviest
    val record = if (assisted) before.lightest else before.heaviest
    if (topAt >= 0 && record > 0 && (if (assisted) top < record else top > record)) atWeight += AtWeight(top, topAt, 1)
    (atWeight.filter { it.compared > 0 }.maxByOrNull { if (assisted) -it.weight else it.weight } ?: atWeight.filter { it.compared == 0 }.maxByOrNull { if (assisted) -it.weight else it.weight })
        ?.let { judge(it.index, true, it.compared, PrKind.WEIGHT, PrKind.WEIGHT_MATCHED) }
    if (commit) before.absorb(now)
    return prs
}

/**
 * The bests of an exercise per side (docs/left-right.md): the left series is the left sets and the ordinary ones,
 * the right series the right sets and the ordinary ones. An exercise never done left and right has two equal sides.
 */
class SidedBests(val left: PrBests = PrBests(), right: PrBests = PrBests()) {
    /** Whether a left or right set was ever folded in; until then the sides are the same, which spares a second judging. */
    var split = false
        private set

    private val rightBests = right
    /**
     * Set while the sides are the same and the right has not yet taken in the left's latest: copying the whole left into the right after
     * every workout was most of the work over a long history, so it is done once, when the right is asked for.
     */
    private var behind = false
    val right: PrBests get() {
        catchUp()
        return rightBests
    }

    /** Brings the right up to the left; done before anything changes the left on its own. */
    private fun catchUp() {
        if (behind) { rightBests.absorb(left); behind = false }
    }

    fun copy() = SidedBests(left.copy(), right.copy()).also { it.split = split }

    fun absorb(other: SidedBests) {
        catchUp()
        left.absorb(other.left)
        right.absorb(other.right)
        split = split || other.split
    }

    /** What an ordinary set has to beat: per kind the larger of the two sides' bests. */
    fun stricter(): PrBests = left.copy().also { it.absorb(right) }

    /**
     * [judgePrs] per side for one exercise in one workout: [sides] (parallel to [sets]) says which are left sets, right
     * sets or ordinary ones. Left sets are judged against the left bests, right sets against the right, each group as a
     * workout of its own (so "weight" is one badge per side); ordinary sets against [stricter]. With [commit] a left
     * set then updates the left bests, a right set the right, an ordinary set both. The result has the shape of
     * [judgePrs]'s: one entry per set, in order.
     */
    fun judge(sets: List<PrSet>, sides: List<Side?>, loadType: LoadType, bodyweight: Double?, rule: OneRm, commit: Boolean = true): List<Set<PrKind>> {
        val lefts = sets.indices.filter { sides[it] == Side.LEFT }
        val rights = sets.indices.filter { sides[it] == Side.RIGHT }
        if (!split && lefts.isEmpty() && rights.isEmpty()) {
            val kinds = judgePrs(left, sets, loadType, bodyweight, rule, commit)
            if (commit) behind = true
            return kinds
        }
        catchUp()
        val kinds = MutableList<Set<PrKind>>(sets.size) { emptySet() }
        val ordinary = sets.indices.filter { sides[it] == null }
        val bar = if (ordinary.isEmpty()) null else stricter()
        fun run(at: List<Int>, bests: PrBests) {
            judgePrs(bests, at.map { sets[it] }, loadType, bodyweight, rule, commit).forEachIndexed { i, k -> kinds[at[i]] = k }
        }
        if (lefts.isNotEmpty()) run(lefts, left)
        if (rights.isNotEmpty()) run(rights, right)
        if (bar != null) {
            val mine = ordinary.map { sets[it] }
            judgePrs(bar, mine, loadType, bodyweight, rule, commit = false).forEachIndexed { i, k -> kinds[ordinary[i]] = k }
            if (commit) {
                // What the ordinary sets did, folded into both sides: judged against nothing to get the workout's own bests.
                val now = PrBests()
                judgePrs(now, mine, loadType, bodyweight, rule)
                left.absorb(now)
                right.absorb(now)
            }
        }
        if (commit && (lefts.isNotEmpty() || rights.isNotEmpty())) split = true
        return kinds
    }
}

/** The bests of an exercise's [sessions], oldest first, per side. */
fun prBestsBySide(sessions: List<Session>, loadType: LoadType, bodyweightAt: (String) -> Double?, rule: OneRm): SidedBests {
    val bests = SidedBests()
    for (session in sessions) {
        val bodyweight = if (loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED) bodyweightAt(session.date) else null
        bests.judge(session.sets.map { it.prSet() }, session.sets.indices.map { session.sideOf(it) }, loadType, bodyweight, rule)
    }
    return bests
}

/** The bests of an exercise's [sessions], oldest first, as an ordinary set has to beat them ([SidedBests.stricter]). */
fun prBestsOf(sessions: List<Session>, loadType: LoadType, bodyweightAt: (String) -> Double?, rule: OneRm): PrBests =
    prBestsBySide(sessions, loadType, bodyweightAt, rule).stricter()

/**
 * For every workout (by date), the PRs each of its sets set, in the order of `workout.blocks.flatMap { it.sets }`; see [judgePrs].
 * A set of a left and right block is judged against its own side's history ([SidedBests.judge]).
 */
fun prsOf(
    workouts: List<Workout>,
    rule: OneRm,
    loadTypeOf: (String) -> LoadType = { LoadType.NORMAL },
    bodyweightAt: (String) -> Double? = { null },
): Map<String, List<Set<PrKind>>> {
    val best = HashMap<String, SidedBests>()
    val result = HashMap<String, List<Set<PrKind>>>()
    for (w in workouts) {
        val sets = w.blocks.flatMap { b -> b.sets.mapIndexed { i, set -> Triple(b.exercise, set, b.setSides[i]) } }
        val prs = MutableList(sets.size) { emptySet<PrKind>() }
        for ((exercise, indexed) in sets.withIndex().groupBy { it.value.first }) {
            val type = loadTypeOf(exercise)
            val bodyweight = if (type == LoadType.BODYWEIGHT || type == LoadType.ASSISTED) bodyweightAt(w.date) else null
            val kinds = best.getOrPut(exercise) { SidedBests() }
                .judge(indexed.map { it.value.second.prSet() }, indexed.map { it.value.third }, type, bodyweight, rule)
            for ((i, entry) in indexed.withIndex()) prs[entry.index] = kinds[i]
        }
        result[w.date] = prs
    }
    return result
}

/** How many of a set's (or a workout's) kinds are real PRs, leaving out the matched ones. */
fun Collection<PrKind>.records(): Int = count { !it.matched }

/**
 * The PRs of a workout as counted: each set's real kinds weigh what the set does ([Block.weightOf], half in a left
 * and right block), so a kind both sides of a round earned is 1 and one side's is half. [kindsPerSet] is in the
 * order of `workout.blocks.flatMap { it.sets }`, as [prsOf] gives it.
 */
fun recordCount(workout: Workout, kindsPerSet: List<Set<PrKind>>): Double {
    var at = 0
    var total = 0.0
    for (b in workout.blocks) for (set in b.sets) total += b.weightOf(set) * (kindsPerSet.getOrNull(at++)?.records() ?: 0)
    return total
}

/** How many PRs each workout set, by its date, counted by [recordCount]. */
fun recordCounts(
    workouts: List<Workout>,
    rule: OneRm,
    loadTypeOf: (String) -> LoadType = { LoadType.NORMAL },
    bodyweightAt: (String) -> Double? = { null },
): Map<String, Double> {
    val prs = prsOf(workouts, rule, loadTypeOf, bodyweightAt)
    return workouts.associate { it.date to recordCount(it, prs[it.date].orEmpty()) }
}

/**
 * For a round's badge row: each kind either of its two sets earned, once, in badge order, with the side(s) that
 * earned it. A lone left set gives an empty [right].
 */
fun sidesOf(left: Set<PrKind>, right: Set<PrKind>): List<Pair<PrKind, Set<Side>>> =
    (left + right).sorted().map { kind -> kind to listOfNotNull(Side.LEFT.takeIf { kind in left }, Side.RIGHT.takeIf { kind in right }).toSet() }

/**
 * Consecutive weeks (Monday to Sunday) with at least one workout, counting back from this week.
 * A week still in progress with no workout yet does not break the streak.
 */
fun weekStreak(workouts: List<Workout>, today: LocalDate): Int {
    val weeks = workouts.map { weekStart(LocalDate.parse(it.day)) }.toSet()
    var week = weekStart(today)
    if (week !in weeks) week = week.minusWeeks(1)
    var streak = 0
    while (week in weeks) {
        streak++
        week = week.minusWeeks(1)
    }
    return streak
}

/** Measurement files of a Strong export, in the order they sit in the zip. */
val STANDARD_MEASUREMENTS: List<Pair<String, String>> = listOf(
    "weight.csv" to "Weight (kg)",
    "body_fat_percentage.csv" to "Body fat percentage",
    "caloric_intake.csv" to "Caloric intake",
    "neck.csv" to "Neck (cm)",
    "shoulders.csv" to "Shoulders (cm)",
    "chest.csv" to "Chest (cm)",
    "left_bicep.csv" to "Left bicep (cm)",
    "right_bicep.csv" to "Right bicep (cm)",
    "left_forearm.csv" to "Left forearm (cm)",
    "right_forearm.csv" to "Right forearm (cm)",
    "upper_abs.csv" to "Upper abs (cm)",
    "waist.csv" to "Waist (cm)",
    "lower_abs.csv" to "Lower abs (cm)",
    "hips.csv" to "Hips (cm)",
    "left_thigh.csv" to "Left thigh (cm)",
    "right_thigh.csv" to "Right thigh (cm)",
    "left_calf.csv" to "Left calf (cm)",
    "right_calf.csv" to "Right calf (cm)",
)
