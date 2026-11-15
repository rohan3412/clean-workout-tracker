package clean.core

/** How the logged weight relates to the real load (docs/domain.md). */
enum class LoadType {
    NORMAL, BODYWEIGHT, ASSISTED,

    /** Logged as seconds, with an optional weight (a plank, a carry). */
    TIMED,

    /** Logged as distance and time. */
    CARDIO,
}

/** A best value with the set and day it came from. */
data class Record(val value: Double, val weight: Double?, val reps: Int?, val date: String)

data class ExerciseRecords(
    val weight: Record?,
    val reps: Record?,
    val estimated1Rm: Record?,
    val setVolume: Record?,
    val sessionVolume: Record?,
    /** Best reps at each weight, heaviest first (lightest first for assisted lifts). */
    val repsAtWeight: List<Record>,
    /** Some sets of a bodyweight or assisted lift had no weigh-in near them, so they have no volume or estimated 1RM. */
    val missingBodyweight: Boolean = false,
    /** The longest hold (a set with seconds and no reps), with the weight held if one was logged. */
    val longest: Record? = null,
    /** Longest hold at each weight held, heaviest first (lightest first for assisted lifts). */
    val longestAtWeight: List<Record> = emptyList(),
)

/**
 * One workout's sets of one exercise. [sides] says which of [sets] are the left or right sets of a block logged
 * left and right (parallel to [sets], null for any other set); it is empty for a session of ordinary sets, which
 * is what `Session(date, sets)` means (docs/left-right.md).
 */
data class Session(val date: String, val sets: List<SetItem>, val sides: List<Side?> = emptyList()) {
    val workingSets: Int get() = sets.count { it.type != SetType.WARMUP }

    /** Whether any set has a side. */
    val paired: Boolean = sides.any { it != null }

    fun sideOf(index: Int): Side? = sides.getOrNull(index)

    /** One side's series of the session: its own sets and the ordinary ones, which count for both sides. */
    fun forSide(side: Side): Session =
        if (!paired) this else Session(date, sets.filterIndexed { i, _ -> sideOf(i).let { it == null || it == side } })
}

const val MAX_REPS_FOR_1RM = 12

/** The formulas that turn a set into an estimated one-rep max. Every one of them gives the weight itself for a single. */
enum class OneRmFormula(val label: String) {
    EPLEY("Epley"), BRZYCKI("Brzycki"), LANDER("Lander"), LOMBARDI("Lombardi"),
    MAYHEW("Mayhew"), OCONNER("O'Conner"), WATHAN("Wathan"), AVERAGE("Average"),
    /** Brzycki under 10 reps, Epley from 10 up; the two meet at exactly 10. */
    HYBRID("Hybrid"),
}

/**
 * The chosen formula and the most reps it is trusted for: above [maxReps] there is no estimate. With [factorRpe] a set logged with an
 * RPE counts the reps it had left in it (`10 - RPE`) as done ([effectiveReps]).
 */
data class OneRm(val formula: OneRmFormula, val maxReps: Int, val factorRpe: Boolean = false) {
    companion object {
        /** Hybrid up to 12 reps, the default since 2026-10-22 (Epley before). */
        val DEFAULT = OneRm(OneRmFormula.HYBRID, MAX_REPS_FOR_1RM)
    }
}

/** The lowest RPE that is read as one: below it a logged RPE is taken for a slip and left out. */
const val RPE_FLOOR = 5.0

/**
 * The reps a set stands for: its [reps], plus the reps in reserve (`10 - RPE`) when [rule] factors in RPE and [rpe] is logged from
 * [RPE_FLOOR] to 10. RPE 10 adds nothing; RPE 8 adds two; RPE 8.5 adds one and a half.
 */
fun effectiveReps(reps: Int, rpe: Double?, rule: OneRm): Double =
    if (rule.factorRpe && rpe != null && rpe >= RPE_FLOOR && rpe <= 10.0) reps + (10.0 - rpe) else reps.toDouble()

/** What a weight is multiplied by to estimate the one-rep max at [r] reps (more than 1) by [formula]. Every formula is linear in the weight. */
private fun oneRmFactor(formula: OneRmFormula, r: Double): Double {
    return when (formula) {
        OneRmFormula.EPLEY -> 1 + r / 30.0
        OneRmFormula.BRZYCKI -> 36.0 / (37.0 - r)
        OneRmFormula.LANDER -> 100.0 / (101.3 - 2.67123 * r)
        OneRmFormula.LOMBARDI -> Math.pow(r, 0.10)
        OneRmFormula.MAYHEW -> 100.0 / (52.2 + 41.9 * Math.exp(-0.055 * r))
        OneRmFormula.OCONNER -> 1 + 0.025 * r
        OneRmFormula.WATHAN -> 100.0 / (48.8 + 53.8 * Math.exp(-0.075 * r))
        OneRmFormula.HYBRID -> oneRmFactor(if (r < 10) OneRmFormula.BRZYCKI else OneRmFormula.EPLEY, r)
        OneRmFormula.AVERAGE -> (oneRmFactor(OneRmFormula.EPLEY, r) + oneRmFactor(OneRmFormula.BRZYCKI, r) + oneRmFactor(OneRmFormula.LOMBARDI, r)) / 3
    }
}

/**
 * The one place a set becomes an estimated one-rep max. A single (at RPE 10, or with no RPE factored in) is the measurement itself;
 * above the rule's rep limit there is no honest estimate. [rpe] is the set's logged RPE, used only when the rule factors it in.
 * Brzycki, alone or within Hybrid, is never asked past the 20 + 5 reps the rep limits and RPE allow, short of where it breaks down (37 reps).
 */
fun estimate1Rm(weight: Double, reps: Int, rule: OneRm, rpe: Double? = null): Double? {
    if (weight <= 0 || reps < 1 || reps > rule.maxReps) return null
    val r = effectiveReps(reps, rpe, rule)
    return if (r <= 1.0) weight else weight * oneRmFactor(rule.formula, r)
}

/** The weight that is [oneRm] estimated for a set of [reps] under [rule]: the formula the other way round (it is linear, so this is exact). */
fun weightForReps(oneRm: Double, reps: Int, rule: OneRm): Double = if (reps <= 1) oneRm else oneRm / oneRmFactor(rule.formula, reps.toDouble())

/**
 * Whether an exercise has stalled: none of its last [sessions] sessions reached a better estimated
 * 1RM than the best before them. Needs a few earlier sessions to compare with, else it is not called.
 */
fun stalled(progress: List<Progress>, sessions: Int = 5): Boolean {
    val values = progress.mapNotNull { it.estimated1Rm }
    if (values.size < sessions + 3) return false
    return values.takeLast(sessions).max() <= values.dropLast(sessions).max()
}

/** A weight and a number of reps to aim for. */
data class Target(val weight: Double, val reps: Int)

/**
 * The next set to aim for after [last] (the best set of the last session) so that its estimated
 * 1RM beats [best1Rm]. Reps first: the same weight with more reps, as few more as it takes. When
 * that would go past [MAX_REPS_FOR_1RM], one [step] up in weight at the fewest reps that still beat it.
 */
fun nextTarget(last: Target, best1Rm: Double?, step: Double, rule: OneRm = OneRm.DEFAULT): Target {
    val goal = best1Rm ?: 0.0
    if (last.weight <= 0 || step <= 0) return Target(last.weight, last.reps + 1)
    // Judged without RPE: the target is a set to do, not one done.
    val plain = rule.copy(factorRpe = false)
    for (reps in last.reps + 1..MAX_REPS_FOR_1RM) if ((estimate1Rm(last.weight, reps, plain) ?: 0.0) > goal) return Target(last.weight, reps)
    var weight = last.weight + step
    while (true) {
        for (reps in 1..MAX_REPS_FOR_1RM) if ((estimate1Rm(weight, reps, plain) ?: 0.0) > goal) return Target(weight, reps)
        weight += step
    }
}

/** Ways to warm up to a working weight: the share of it and the reps for each set, lightest first. The second is the default. */
val WARMUP_SCHEMES: Map<String, List<Pair<Double, Int>>> = linkedMapOf(
    "2 sets" to listOf(0.5 to 8, 0.75 to 3),
    "3 sets" to listOf(0.4 to 8, 0.6 to 5, 0.8 to 2),
    "4 sets" to listOf(0.4 to 8, 0.55 to 5, 0.7 to 3, 0.85 to 1),
)

/**
 * The warm-up sets leading to [working] by [scheme], each weight rounded to a multiple of [step].
 * Sets that round to nothing, to the working weight or to the same weight as the one before are left out.
 */
fun warmupSets(working: Double, step: Double, scheme: List<Pair<Double, Int>>): List<Target> {
    if (working <= 0 || step <= 0) return emptyList()
    val sets = mutableListOf<Target>()
    for ((share, reps) in scheme) {
        val weight = Math.round(working * share / step) * step
        if (weight > 0 && weight < working && sets.none { it.weight == weight }) sets += Target(weight, reps)
    }
    return sets
}

/** The smallest jump between two weights ever logged for an exercise (at least half a kilo), or 2.5 kg when there is nothing to go on. */
fun weightStep(sessions: List<Session>): Double =
    sessions.flatMap { it.sets }.mapNotNull { it.weight }.distinct().sorted().zipWithNext { a, b -> b - a }.filter { it >= 0.5 }.minOrNull() ?: 2.5

/** One workout's session of an exercise from its [blocks], or null when they have no sets. */
private fun sessionOf(date: String, blocks: List<Block>): Session? {
    val sets = blocks.flatMap { it.sets }
    if (sets.isEmpty()) return null
    return Session(date, sets, blocks.flatMap { it.setSides }.takeIf { s -> s.any { it != null } }.orEmpty())
}

/** Every session of [exercise], oldest first. Blocks of the same exercise in one workout are one session. */
fun sessionsOf(workouts: List<Workout>, exercise: String): List<Session> =
    workouts.mapNotNull { w -> sessionOf(w.date, w.blocks.filter { it.exercise == exercise }) }

/** [sessionsOf] for every exercise at once, in one pass over the history. */
fun sessionsByExercise(workouts: List<Workout>): Map<String, List<Session>> {
    val all = HashMap<String, MutableList<Session>>()
    for (w in workouts) {
        // Blocks of the same exercise in one workout are one session.
        for ((exercise, blocks) in w.blocks.groupBy { it.exercise }) {
            sessionOf(w.date, blocks)?.let { all.getOrPut(exercise) { ArrayList() } += it }
        }
    }
    return all
}

/**
 * Records from working sets only: warm-ups count for nothing, drop sets count for volume only. For sessions
 * with left and right blocks this is the comparable figure, the weaker side's of each kind ([recordsBySide]).
 *
 * [Record.weight] is always the weight as logged. For a bodyweight or assisted lift the weight
 * record and the reps table use that logged number (least help wins, for an assisted lift), while
 * estimated 1RM and volume use the real load and exist only for sessions with a weigh-in close
 * enough ([bodyweightAt] gives the bodyweight for a session's date, or null).
 */
fun recordsOf(
    sessions: List<Session>,
    rule: OneRm,
    loadType: LoadType = LoadType.NORMAL,
    bodyweightAt: (String) -> Double? = { null },
): ExerciseRecords = recordsBySide(sessions, rule, loadType, bodyweightAt).comparable

/** An exercise's records per side, and the comparable figure: for each kind the weaker side's. */
data class SideRecords(val left: ExerciseRecords, val right: ExerciseRecords, val comparable: ExerciseRecords)

/**
 * [recordsOf] for each side's series (the side's own sets and the ordinary ones, [Session.forSide]). An exercise
 * with no left and right session has one series, so all three are the same.
 */
fun recordsBySide(
    sessions: List<Session>,
    rule: OneRm,
    loadType: LoadType = LoadType.NORMAL,
    bodyweightAt: (String) -> Double? = { null },
): SideRecords {
    val left = recordsOfSeries(sessions.map { it.forSide(Side.LEFT) }, rule, loadType, bodyweightAt)
    if (sessions.none { it.paired }) return SideRecords(left, left, left)
    val right = recordsOfSeries(sessions.map { it.forSide(Side.RIGHT) }, rule, loadType, bodyweightAt)
    return SideRecords(left, right, weakerOf(left, right, loadType == LoadType.ASSISTED))
}

/** The weaker of two sides' records per kind; a kind one side has none of has none. Assisted weight is weaker the more help. */
private fun weakerOf(a: ExerciseRecords, b: ExerciseRecords, assisted: Boolean): ExerciseRecords {
    fun weaker(x: Record?, y: Record?, lowerIsBetter: Boolean = false): Record? = when {
        x == null || y == null -> null
        (if (lowerIsBetter) x.value >= y.value else x.value <= y.value) -> x
        else -> y
    }
    // The table of one weight each: the fewer reps (the shorter hold) of the two sides, at the weights both have done.
    fun table(x: List<Record>, y: List<Record>) = x.mapNotNull { r -> y.firstOrNull { it.weight == r.weight }?.let { o -> if (o.value < r.value) o else r } }
    return ExerciseRecords(
        weaker(a.weight, b.weight, lowerIsBetter = assisted), weaker(a.reps, b.reps), weaker(a.estimated1Rm, b.estimated1Rm),
        weaker(a.setVolume, b.setVolume), weaker(a.sessionVolume, b.sessionVolume), table(a.repsAtWeight, b.repsAtWeight),
        a.missingBodyweight || b.missingBodyweight, weaker(a.longest, b.longest), table(a.longestAtWeight, b.longestAtWeight),
    )
}

private fun recordsOfSeries(
    sessions: List<Session>,
    rule: OneRm,
    loadType: LoadType,
    bodyweightAt: (String) -> Double?,
): ExerciseRecords {
    var weight: Record? = null
    var reps: Record? = null
    var e1rm: Record? = null
    var setVolume: Record? = null
    var sessionVolume: Record? = null
    var missing = false
    var longest: Record? = null
    val atWeight = sortedMapOf<Double, Record>()
    val heldAt = sortedMapOf<Double, Record>()
    val assisted = loadType == LoadType.ASSISTED

    fun better(old: Record?, new: Record, lowerWins: Boolean = false) =
        if (old == null || (if (lowerWins) new.value < old.value else new.value > old.value)) new else old

    for (session in sessions) {
        val bodyweight = if (loadType == LoadType.NORMAL) null else bodyweightAt(session.date)
        var total = 0.0
        for (set in session.sets) {
            if (set.type == SetType.WARMUP) continue
            val w = set.weight
            val r = set.reps ?: 0
            if (r < 1) {
                // A hold: seconds and no reps. Cardio logs seconds too, but it has no records here.
                val held = set.seconds?.takeIf { it > 0 && set.type != SetType.DROP && loadType != LoadType.CARDIO } ?: continue
                longest = better(longest, Record(held, w, null, session.date))
                if (w != null && w > 0) heldAt[w] = better(heldAt[w], Record(held, w, null, session.date))
                continue
            }
            val load = w?.let { realLoad(loadType, it, bodyweight) }
            if (w != null && load == null) missing = true
            if (load != null) {
                val volume = load * r
                total += volume
                if (set.type != SetType.DROP && volume > 0) setVolume = better(setVolume, Record(volume, w, r, session.date))
            }
            if (set.type == SetType.DROP) continue
            reps = better(reps, Record(r.toDouble(), w, r, session.date))
            if (w == null) continue
            weight = better(weight, Record(w, w, r, session.date), lowerWins = assisted)
            atWeight[w] = better(atWeight[w], Record(r.toDouble(), w, r, session.date))
            if (load != null) estimate1Rm(load, r, rule, set.rpe)?.let { e1rm = better(e1rm, Record(it, w, r, session.date)) }
        }
        if (total > 0) sessionVolume = better(sessionVolume, Record(total, null, null, session.date))
    }
    val table = atWeight.values.toList()
    val held = heldAt.values.toList()
    return ExerciseRecords(weight, reps, e1rm, setVolume, sessionVolume, if (assisted) table else table.reversed(), missing, longest, if (assisted) held else held.reversed())
}

/** One session of an exercise as chart values; null where the session has nothing for that measure. */
data class Progress(
    val date: String,
    /** Best logged weight: the heaviest, or the least help for an assisted lift. */
    val topWeight: Double?,
    val estimated1Rm: Double?,
    val volume: Double?,
    val maxReps: Int?,
    /** Longest timed set. */
    val longestSeconds: Double?,
    val totalSeconds: Double?,
    val totalMeters: Double?,
)

/**
 * Every session of an exercise as one row of chart values, oldest first. Warm-ups are left out; estimated 1RM and volume use the real load.
 * A session with left and right blocks gives the comparable figure, the weaker side's of each measure ([progressBySide]).
 */
fun progressOf(sessions: List<Session>, rule: OneRm, loadType: LoadType = LoadType.NORMAL, bodyweightAt: (String) -> Double? = { null }): List<Progress> =
    progressBySide(sessions, rule, loadType, bodyweightAt).map { it.comparable }

/**
 * A session as chart values: [comparable] always, and each side's own [left] and [right] only for a session with a
 * left and right block (a chart draws a split there and a single marker elsewhere).
 */
data class SidedProgress(val date: String, val comparable: Progress, val left: Progress?, val right: Progress?)

/** [progressOf] with each side's figures for the sessions that have sides. A side with no set that session leaves the other's as the comparable. */
fun progressBySide(sessions: List<Session>, rule: OneRm, loadType: LoadType = LoadType.NORMAL, bodyweightAt: (String) -> Double? = { null }): List<SidedProgress> =
    sessions.map { session ->
        if (!session.paired) return@map SidedProgress(session.date, progressOfSession(session, rule, loadType, bodyweightAt), null, null)
        val l = session.forSide(Side.LEFT)
        val r = session.forSide(Side.RIGHT)
        val left = progressOfSession(l, rule, loadType, bodyweightAt)
        val right = progressOfSession(r, rule, loadType, bodyweightAt)
        val comparable = when {
            r.sets.isEmpty() -> left
            l.sets.isEmpty() -> right
            else -> weakerOf(left, right, loadType == LoadType.ASSISTED)
        }
        SidedProgress(session.date, comparable, left, right)
    }

/** The weaker of two sides' figures per measure; one that a side has none of is none. Assisted top weight is weaker the more help. */
private fun weakerOf(a: Progress, b: Progress, assisted: Boolean): Progress {
    fun <T> both(x: T?, y: T?, pick: (T, T) -> T): T? = if (x == null || y == null) null else pick(x, y)
    return Progress(
        a.date,
        both(a.topWeight, b.topWeight) { x, y -> if (assisted) maxOf(x, y) else minOf(x, y) },
        both(a.estimated1Rm, b.estimated1Rm) { x, y -> minOf(x, y) },
        both(a.volume, b.volume) { x, y -> minOf(x, y) },
        both(a.maxReps, b.maxReps) { x, y -> minOf(x, y) },
        both(a.longestSeconds, b.longestSeconds) { x, y -> minOf(x, y) },
        both(a.totalSeconds, b.totalSeconds) { x, y -> minOf(x, y) },
        both(a.totalMeters, b.totalMeters) { x, y -> minOf(x, y) },
    )
}

/** How far apart the two sides were in one measure: [difference] is right minus left, [percent] that in percent of the larger side (negative when the right lags). */
data class SideGap(val difference: Double, val percent: Double)

/** The gaps of a session with left and right sets. [lagging] is the weaker side by the first measure (1RM, volume, reps) that differs, null on a tie. */
data class Imbalance(val date: String, val oneRm: SideGap?, val reps: SideGap?, val volume: SideGap?, val lagging: Side?)

/** The imbalance of a session, or null for one without left and right sets. */
fun imbalance(progress: SidedProgress): Imbalance? {
    val left = progress.left ?: return null
    val right = progress.right ?: return null
    fun gap(l: Double?, r: Double?): SideGap? {
        if (l == null || r == null) return null
        val larger = maxOf(l, r)
        return if (larger <= 0) null else SideGap(r - l, (r - l) / larger * 100)
    }
    val oneRm = gap(left.estimated1Rm, right.estimated1Rm)
    val reps = gap(left.maxReps?.toDouble(), right.maxReps?.toDouble())
    val volume = gap(left.volume, right.volume)
    val lagging = listOf(oneRm, volume, reps).firstOrNull { it != null && it.difference != 0.0 }?.let { if (it.difference < 0) Side.RIGHT else Side.LEFT }
    return Imbalance(progress.date, oneRm, reps, volume, lagging)
}

private fun progressOfSession(session: Session, rule: OneRm, loadType: LoadType, bodyweightAt: (String) -> Double?): Progress {
    val needsBodyweight = loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED
    val bodyweight = if (needsBodyweight) bodyweightAt(session.date) else null
    val sets = session.sets.filter { it.type != SetType.WARMUP }
    val weights = sets.filter { (it.reps ?: 0) >= 1 }.mapNotNull { it.weight }
    var estimate: Double? = null
    var volume = 0.0
    for (set in sets) {
        val reps = set.reps ?: continue
        val load = set.weight?.let { realLoad(loadType, it, bodyweight) } ?: continue
        volume += load * reps
        if (set.type != SetType.DROP) estimate1Rm(load, reps, rule, set.rpe)?.let { e -> estimate = maxOf(estimate ?: 0.0, e) }
    }
    val seconds = sets.mapNotNull { it.seconds }
    val meters = sets.mapNotNull { it.distance }
    return Progress(
        session.date,
        if (loadType == LoadType.ASSISTED) weights.minOrNull() else weights.maxOrNull(),
        estimate,
        volume.takeIf { it > 0 },
        sets.mapNotNull { it.reps }.maxOrNull(),
        seconds.maxOrNull(),
        seconds.sum().takeIf { seconds.isNotEmpty() },
        meters.sum().takeIf { meters.isNotEmpty() },
    )
}
