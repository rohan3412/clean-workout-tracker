package clean.core

import clean.core.Muscle.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** The muscles the body figure can shade, head to toe; the last four were added later and are stored by name like the rest. */
enum class Muscle(val label: String) {
    NECK("Neck"), TRAPS("Traps"), SHOULDERS("Shoulders"), CHEST("Chest"), LATS("Lats"), UPPER_BACK("Upper back"),
    BICEPS("Biceps"), TRICEPS("Triceps"), FOREARMS("Forearms"),
    ABS("Abs"), OBLIQUES("Obliques"), LOWER_BACK("Lower back"),
    GLUTES("Glutes"), QUADS("Quads"), HAMSTRINGS("Hamstrings"), ADDUCTORS("Adductors"), CALVES("Calves"),
    SERRATUS("Serratus"), HIP_FLEXORS("Hip flexors"), TIBIALIS("Tibialis"), ROTATOR_CUFF("Rotator cuff"),
}

/**
 * How much of a set a helping muscle is credited with, against 1 for a main muscle. Half a set for
 * a supporting muscle is the "fractional sets" convention of the training-volume research; a
 * quarter for a minor one, which only grips, braces or steadies, is Clean's own addition.
 */
const val SECONDARY_SHARE = 0.5
const val MINOR_SHARE = 0.25

/**
 * Which muscles an exercise trains. Set by the user; a suggestion is only ever a starting point.
 * [heads] are the parts of those muscles it names (1 for a main head, 0.5, 0.25); they never change the group figures.
 */
data class MuscleUse(
    val primary: Set<Muscle> = emptySet(),
    val secondary: Set<Muscle> = emptySet(),
    val minor: Set<Muscle> = emptySet(),
    val heads: Map<Head, Double> = emptyMap(),
) {
    val isEmpty get() = primary.isEmpty() && secondary.isEmpty() && minor.isEmpty()
    val shares: Map<Muscle, Double> by lazy { minor.associateWith { MINOR_SHARE } + secondary.associateWith { SECONDARY_SHARE } + primary.associateWith { 1.0 } }

    /**
     * `CHEST,SHOULDERS|TRICEPS|ABS`: main, supporting, minor, with bars between; the minor part is left off when empty.
     * Stored heads follow as a fourth part, `UPPER_CHEST:1.0,LOWER_CHEST:0.25`, only when there are any.
     */
    fun encode(): String = if (isEmpty) "" else
        primary.joinToString(",") { it.name } + "|" + secondary.joinToString(",") { it.name } +
            (if (minor.isEmpty() && heads.isEmpty()) "" else "|" + minor.joinToString(",") { it.name }) +
            (if (heads.isEmpty()) "" else "|" + heads.entries.joinToString(",") { it.key.name + ":" + it.value })

    companion object {
        fun decode(text: String): MuscleUse {
            fun part(s: String) = s.split(',').mapNotNull { n -> Muscle.entries.firstOrNull { it.name == n } }.toSet()
            val halves = text.split('|')
            val groups = MuscleUse(part(halves[0]), part(halves.getOrElse(1) { "" }), part(halves.getOrElse(2) { "" }))
            // A head whose name is unknown, or whose group is not main or supporting here, is skipped.
            val heads = halves.getOrElse(3) { "" }.split(',').mapNotNull { h ->
                val head = Head.entries.firstOrNull { it.name == h.substringBefore(':') } ?: return@mapNotNull null
                val share = h.substringAfter(':', "").toDoubleOrNull() ?: return@mapNotNull null
                if (head.group in groups.primary || head.group in groups.secondary) head to share else null
            }.toMap()
            return groups.copy(heads = heads)
        }
    }
}

class Suggestion(val muscles: MuscleUse, val loadType: LoadType)

private fun use(vararg primary: Muscle) = MuscleUse(primary.toSet())
private infix fun MuscleUse.plus(secondary: List<Muscle>) = copy(secondary = secondary.toSet())

// Common gym vocabulary, most specific first; the first rule whose words all appear in the name wins.
private val RULES: List<Pair<List<String>, MuscleUse>> = run {
    listOf(
        listOf("tibialis") to use(TIBIALIS),
        listOf("serratus") to use(SERRATUS),
        listOf("push up plus") to (use(CHEST, SERRATUS) plus listOf(SHOULDERS, TRICEPS)),
        listOf("hip flexor") to use(HIP_FLEXORS),
        listOf("rotator") to (use(ROTATOR_CUFF) plus listOf(SHOULDERS)),
        listOf("hip", "rotation") to use(GLUTES),
        listOf("external rotation") to (use(ROTATOR_CUFF) plus listOf(SHOULDERS)),
        listOf("internal rotation") to (use(ROTATOR_CUFF) plus listOf(SHOULDERS)),
        listOf("face pull") to (use(SHOULDERS, UPPER_BACK) plus listOf(TRAPS)),
        listOf("reverse fly") to (use(SHOULDERS, UPPER_BACK) plus listOf(TRAPS)),
        listOf("rear delt") to (use(SHOULDERS, UPPER_BACK) plus listOf(TRAPS)),
        listOf("upright row") to (use(SHOULDERS, TRAPS) plus listOf(BICEPS)),
        listOf("shrug") to use(TRAPS),
        listOf("lateral raise") to use(SHOULDERS),
        listOf("front raise") to use(SHOULDERS),
        listOf("shoulder press") to (use(SHOULDERS) plus listOf(TRICEPS)),
        listOf("overhead press") to (use(SHOULDERS) plus listOf(TRICEPS)),
        listOf("straight arm") to (use(LATS) plus listOf(TRICEPS)),
        listOf("pulldown") to (use(LATS) plus listOf(BICEPS, UPPER_BACK)),
        listOf("pull up") to (use(LATS) plus listOf(BICEPS, UPPER_BACK)),
        listOf("chin up") to (use(LATS, BICEPS) plus listOf(UPPER_BACK)),
        listOf("lat row") to (use(LATS) plus listOf(BICEPS, UPPER_BACK)),
        listOf("row") to (use(UPPER_BACK, LATS) plus listOf(BICEPS, SHOULDERS)),
        listOf("triceps dip") to (use(TRICEPS, CHEST) plus listOf(SHOULDERS)),
        listOf("bench dip") to (use(TRICEPS) plus listOf(CHEST, SHOULDERS)),
        listOf("dip") to (use(TRICEPS, CHEST) plus listOf(SHOULDERS)),
        listOf("pushdown") to use(TRICEPS),
        listOf("tricep") to use(TRICEPS),
        listOf("skull") to use(TRICEPS),
        listOf("hammer curl") to (use(BICEPS, FOREARMS)),
        listOf("leg curl") to (use(HAMSTRINGS) plus listOf(CALVES)),
        listOf("wrist") to use(FOREARMS),
        listOf("curl") to (use(BICEPS) plus listOf(FOREARMS)),
        listOf("fly") to (use(CHEST) plus listOf(SHOULDERS)),
        listOf("bench press") to (use(CHEST) plus listOf(SHOULDERS, TRICEPS)),
        listOf("chest press") to (use(CHEST) plus listOf(SHOULDERS, TRICEPS)),
        listOf("push up") to (use(CHEST) plus listOf(SHOULDERS, TRICEPS)),
        listOf("oblique") to (use(OBLIQUES) plus listOf(ABS)),
        listOf("pallof") to (use(OBLIQUES) plus listOf(ABS)),
        listOf("crunch") to use(ABS),
        listOf("knee raise") to (use(ABS) plus listOf(HIP_FLEXORS)),
        listOf("leg raise") to (use(ABS) plus listOf(HIP_FLEXORS)),
        listOf("plank") to (use(ABS) plus listOf(OBLIQUES)),
        listOf("sit up") to use(ABS),
        listOf("back extension") to (use(LOWER_BACK) plus listOf(GLUTES, HAMSTRINGS)),
        listOf("hip thrust") to (use(GLUTES) plus listOf(HAMSTRINGS)),
        listOf("abductor") to use(GLUTES),
        listOf("adductor") to use(ADDUCTORS),
        listOf("calf") to use(CALVES),
        listOf("leg extension") to use(QUADS),
        listOf("leg press") to (use(QUADS, GLUTES) plus listOf(HAMSTRINGS)),
        listOf("split squat") to (use(QUADS, GLUTES) plus listOf(HAMSTRINGS)),
        listOf("lunge") to (use(QUADS, GLUTES) plus listOf(HAMSTRINGS)),
        listOf("squat") to (use(QUADS, GLUTES) plus listOf(HAMSTRINGS, LOWER_BACK)),
        listOf("romanian") to (use(HAMSTRINGS, GLUTES) plus listOf(LOWER_BACK)),
        listOf("deadlift") to (use(HAMSTRINGS, GLUTES, LOWER_BACK) plus listOf(TRAPS, QUADS)),
    )
}

private val BODYWEIGHT_WORDS = listOf(
    "pull up", "chin up", "push up", "dip", "bodyweight", "crunch", "knee raise", "leg raise", "back extension", "inverted row", "sit up",
)

/**
 * A starting point for an exercise's muscles and load type, from the words in its name. Null when
 * nothing matches. It applies on its own until the user sets their own (`AppModel.musclesOf`).
 */
fun suggest(name: String): Suggestion? = SUGGESTED.getOrPut(name) { guess(name) ?: NO_SUGGESTION }.takeIf { it !== NO_SUGGESTION }

/** Suggestions already worked out, by name: this is asked once per set in every pass over the history. */
private val SUGGESTED = java.util.concurrent.ConcurrentHashMap<String, Suggestion>()
private val NO_SUGGESTION = Suggestion(MuscleUse(), LoadType.NORMAL)

private fun guess(name: String): Suggestion? {
    // A built-in exercise has a definition; only names outside the library are guessed from their words.
    (libraryExercise(name) ?: canonicalName(name)?.let(::libraryExercise))?.let { return Suggestion(it.muscles, it.loadType) }
    // A rule-made variation trains what the exercise it was made from trains.
    variationBase(name)?.let { return suggest(it) }
    val words = name.lowercase().replace('-', ' ')
    val muscles = RULES.firstOrNull { (keys, _) -> keys.all { it in words } }?.second
    val load = when {
        "assisted" in words -> LoadType.ASSISTED
        "machine" in words || "cable" in words || "dumbbell" in words || "barbell" in words -> LoadType.NORMAL
        BODYWEIGHT_WORDS.any { it in words } -> LoadType.BODYWEIGHT
        else -> LoadType.NORMAL
    }
    return if (muscles == null && load == LoadType.NORMAL) null else Suggestion(muscles ?: MuscleUse(), load)
}

private fun time(workout: Workout): LocalDateTime? = parseStrongDate(workout.date)

/**
 * Sets worked per muscle since [from] (all time when null). Warm-ups do not count; sets, not kg, so a leg press cannot drown out a lateral raise.
 * A round of a left and right exercise is one set, as a set of both sides at once is (docs/left-right.md).
 */
fun muscleSets(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse): Map<Muscle, Double> {
    val sets = HashMap<Muscle, Double>()
    for (w in workouts) {
        if (from != null && (time(w) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = b.workingCount
            if (n > 0) for ((muscle, share) in musclesOf(b.exercise).shares) sets[muscle] = (sets[muscle] ?: 0.0) + share * n
        }
    }
    return sets
}

/**
 * How many working sets of [b] each side was worked by, in whole sets: a set of an ordinary block trains both
 * sides, a left set of a paired block the left only, a right set the right only. A complete round gives each
 * side what one ordinary set gives it, and (at half a set each) the group total [muscleSets] gives it: 1.
 */
internal fun sideCounts(b: Block): SideCredit {
    var left = 0.0
    var right = 0.0
    for ((i, set) in b.sets.withIndex()) {
        if (set.type == SetType.WARMUP) continue
        val side = b.setSides[i]
        if (side != Side.RIGHT) left++
        if (side != Side.LEFT) right++
    }
    return SideCredit(left, right)
}

/** [muscleSets] by side: see [sideCounts] for what a set credits to each. */
fun muscleSetsBySide(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse): Map<Muscle, SideCredit> {
    val sets = HashMap<Muscle, SideCredit>()
    for (w in workouts) {
        if (from != null && (time(w) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = sideCounts(b)
            if (n.left == 0.0 && n.right == 0.0) continue
            for ((muscle, share) in musclesOf(b.exercise).shares) {
                val had = sets[muscle]
                sets[muscle] = SideCredit((had?.left ?: 0.0) + share * n.left, (had?.right ?: 0.0) + share * n.right)
            }
        }
    }
    return sets
}

/** [muscleSetsBySide] of one [muscle] by week (keyed by the Monday), for the muscle page's left and right chart; weeks with no work are absent. */
fun muscleSideWeeks(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse, muscle: Muscle): Map<LocalDate, SideCredit> =
    sideWeeks(workouts, from) { musclesOf(it).shares[muscle] }

/** [muscleSideWeeks] for one head. */
fun headSideWeeks(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse, head: Head): Map<LocalDate, SideCredit> =
    sideWeeks(workouts, from) { musclesOf(it).heads[head] }

private fun sideWeeks(workouts: List<Workout>, from: LocalDateTime?, shareOf: (String) -> Double?): Map<LocalDate, SideCredit> {
    val weeks = HashMap<LocalDate, SideCredit>()
    for (w in workouts) {
        if (from != null && (time(w) ?: continue) < from) continue
        val monday = weekStart(LocalDate.parse(w.day))
        for (b in w.blocks) {
            val share = shareOf(b.exercise) ?: continue
            val n = sideCounts(b)
            if (n.left == 0.0 && n.right == 0.0) continue
            val had = weeks[monday]
            weeks[monday] = SideCredit((had?.left ?: 0.0) + share * n.left, (had?.right ?: 0.0) + share * n.right)
        }
    }
    return weeks
}

/** Whether any block in [workouts] since [from] was logged left and right with a working set: what makes a sides view worth showing. */
fun hasSideWork(workouts: List<Workout>, from: LocalDateTime?): Boolean =
    workouts.any { w -> (from == null || (time(w) ?: return@any false) >= from) && w.blocks.any { it.paired && it.workingCount > 0 } }

/**
 * One exercise's part in a muscle's set count: [sets] working sets, each counting as [share] of a set.
 * [weighted] is [sets] as counted, a round of a left and right exercise being one set (it is [sets] otherwise).
 */
data class MuscleExercise(val name: String, val sets: Int, val share: Double, val lastDate: String, val weighted: Double = sets.toDouble()) {
    val counted get() = weighted * share
}

/** What one workout added to a muscle's set count. */
data class MuscleWorkout(val date: String, val name: String, val counted: Double)

class MuscleBreakdown(val exercises: List<MuscleExercise>, val workouts: List<MuscleWorkout>)

/**
 * What [muscleSets] counted for one [muscle], taken apart: by exercise, biggest part first, and by
 * workout, newest first. Each list adds up to the muscle's figure.
 */
fun muscleBreakdown(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse, muscle: Muscle): MuscleBreakdown {
    val exercises = LinkedHashMap<String, MuscleExercise>()
    val sessions = ArrayList<MuscleWorkout>()
    for (w in workouts) {
        if (from != null && (time(w) ?: continue) < from) continue
        var counted = 0.0
        for (b in w.blocks) {
            val n = b.workingSets
            val share = musclesOf(b.exercise).shares[muscle] ?: continue
            if (n == 0) continue
            val had = exercises[b.exercise]
            exercises[b.exercise] = MuscleExercise(b.exercise, (had?.sets ?: 0) + n, share, w.date, (had?.weighted ?: 0.0) + b.workingCount)
            counted += share * b.workingCount
        }
        if (counted > 0) sessions += MuscleWorkout(w.date, w.name, counted)
    }
    return MuscleBreakdown(exercises.values.sortedByDescending { it.counted }, sessions.reversed())
}

/** When each muscle was last trained with a working set. */
fun lastTrained(workouts: List<Workout>, musclesOf: (String) -> MuscleUse): Map<Muscle, LocalDateTime> = lastTrained(workouts, musclesOf, null)

/** [lastTrained] by side: the left side was trained by its own sets and the ordinary ones, the right likewise. */
fun lastTrainedBySide(workouts: List<Workout>, musclesOf: (String) -> MuscleUse): Map<Side, Map<Muscle, LocalDateTime>> =
    Side.entries.associateWith { lastTrained(workouts, musclesOf, it) }

private fun lastTrained(workouts: List<Workout>, musclesOf: (String) -> MuscleUse, side: Side?): Map<Muscle, LocalDateTime> {
    val last = HashMap<Muscle, LocalDateTime>()
    for (w in workouts) {
        val t = time(w) ?: continue
        for (b in w.blocks) {
            if (b.sets.withIndex().none { (i, set) -> set.type != SetType.WARMUP && (side == null || b.setSides[i].let { it == null || it == side }) }) continue
            for (muscle in musclesOf(b.exercise).shares.keys) if (last[muscle]?.let { t > it } != false) last[muscle] = t
        }
    }
    return last
}

const val STRENGTH_FLOOR = 0.5
private const val STRENGTH_FULL_DAYS = 14.0
private const val STRENGTH_HALF_LIFE_DAYS = 28.0

/**
 * Retained strength per muscle, 1.0 down to [STRENGTH_FLOOR]: full for two weeks after the muscle
 * was last trained, then fading with a four-week half-life. A model, not a measurement.
 */
fun retainedStrength(workouts: List<Workout>, now: LocalDateTime, musclesOf: (String) -> MuscleUse): Map<Muscle, Double> =
    retained(lastTrained(workouts, musclesOf), now)

/** [retainedStrength] by side, from [lastTrainedBySide]: a side that was not trained reads as the floor, like a muscle never trained. */
fun retainedStrengthBySide(workouts: List<Workout>, now: LocalDateTime, musclesOf: (String) -> MuscleUse): Map<Muscle, SideCredit> {
    val last = lastTrainedBySide(workouts, musclesOf)
    val left = retained(last.getValue(Side.LEFT), now)
    val right = retained(last.getValue(Side.RIGHT), now)
    return Muscle.entries.associateWith { SideCredit(left.getValue(it), right.getValue(it)) }
}

private fun retained(last: Map<Muscle, LocalDateTime>, now: LocalDateTime): Map<Muscle, Double> =
    Muscle.entries.associateWith { muscle ->
        val days = last[muscle]?.let { Duration.between(it, now).toMinutes() / 1440.0 } ?: return@associateWith STRENGTH_FLOOR
        if (days <= STRENGTH_FULL_DAYS) 1.0 else maxOf(STRENGTH_FLOOR, 0.5.pow((days - STRENGTH_FULL_DAYS) / STRENGTH_HALF_LIFE_DAYS))
    }

const val FATIGUE_RECOVERING = 0.25
const val FATIGUE_FATIGUED = 0.5
private const val FATIGUE_HALF_LIFE_HOURS = 36.0
private const val FATIGUE_SCAN_DAYS = 30L

/** What a normal session gives a muscle at first, in kg x reps; each muscle's own normal only moves down from it. */
const val FATIGUE_REFERENCE = 2000.0

/**
 * The fatigue figure before the squash, per muscle, at [now]. With a [side] only that side's work counts: its own sets of a left
 * and right block in full, ordinary sets as they are; without one a set of a left and right block counts half ([Block.weightOf]).
 */
private fun fatigueSum(
    workouts: List<Workout>,
    now: LocalDateTime,
    musclesOf: (String) -> MuscleUse,
    loadOf: LoadOf,
    side: Side? = null,
): Map<Muscle, Double> {
    val reference = HashMap<Muscle, Double>()
    val level = HashMap<Muscle, Double>()
    val at = HashMap<Muscle, LocalDateTime>()
    fun decay(from: LocalDateTime, to: LocalDateTime) = 0.5.pow(Duration.between(from, to).toMinutes() / 60.0 / FATIGUE_HALF_LIFE_HOURS)

    for (w in workouts.sortedBy { time(it) }) {
        val t = time(w) ?: continue
        if (t < now.minusDays(FATIGUE_SCAN_DAYS) || t > now) continue
        val stimulus = HashMap<Muscle, Double>()
        for ((exercise, blocks) in w.blocks.groupBy { it.exercise }) {
            val shares = musclesOf(exercise).shares
            if (shares.isEmpty()) continue
            val counted = blocks.flatMap { b ->
                b.sets.withIndex().filter { it.value.type != SetType.WARMUP }.map { (i, set) ->
                    set to if (side == null) b.weightOf(set) else if (b.setSides[i].let { it == null || it == side }) 1.0 else 0.0
                }
            }
            // The session's best is read from all of the exercise's sets, both sides.
            val loads = counted.map { (set, weight) -> Triple(loadOf(exercise, set.weight ?: 0.0, w.date) ?: 0.0, set.reps ?: 0, weight) }
            val best = loads.maxOfOrNull { (load, reps) -> if (load > 0 && reps > 0) load * (1 + min(reps, MAX_REPS_FOR_1RM) / 30.0) else 0.0 } ?: 0.0
            var total = 0.0
            for ((load, reps, weight) in loads) {
                // A set with no load to measure still happened; it counts as a third of a normal session.
                total += weight * if (load > 0 && reps > 0 && best > 0) load * reps * min(1.0, load / best).pow(1.5) else FATIGUE_REFERENCE / 3
            }
            if (total <= 0.0) continue
            for ((muscle, share) in shares) stimulus[muscle] = (stimulus[muscle] ?: 0.0) + total * share
        }
        for ((muscle, amount) in stimulus) {
            val normal = reference[muscle] ?: FATIGUE_REFERENCE
            level[muscle] = (level[muscle] ?: 0.0) * (at[muscle]?.let { decay(it, t) } ?: 1.0) + amount / normal
            at[muscle] = t
            // "Normal" only ever moves down, towards lighter sessions, so a muscle trained lightly is not read as never tired.
            reference[muscle] = min(normal, normal + (amount - normal) / 3)
        }
    }
    return level.mapValues { (muscle, value) -> value * decay(at.getValue(muscle), now) }
}

/**
 * How recently and how hard each muscle was trained, 0 to 1: below [FATIGUE_RECOVERING] is ready,
 * above [FATIGUE_FATIGUED] is fatigued. A model, not a measurement.
 *
 * Each session gives a muscle a stimulus: load x reps of every working set, weighted by how close
 * the load was to that session's best estimated 1RM for the exercise (to the power 1.5), scaled by the
 * muscle's share; a set with no load to measure counts a third of [FATIGUE_REFERENCE]. It is measured
 * against what has been a normal session for that muscle lately (starting at [FATIGUE_REFERENCE] and
 * only moving down), fades with a 36-hour half-life, and the running total is squashed so it approaches
 * 1 without reaching it. [loadOf] turns a logged weight into the real load (bodyweight and assisted lifts).
 * This is the model from before 2026-10-12, restored on 2026-10-16: the effective-set model in between read too cool.
 */
fun fatigue(
    workouts: List<Workout>,
    now: LocalDateTime,
    musclesOf: (String) -> MuscleUse,
    loadOf: LoadOf = LOGGED_LOAD,
): Map<Muscle, Double> {
    val sum = fatigueSum(workouts, now, musclesOf, loadOf)
    return Muscle.entries.associateWith { 1 - exp(-(sum[it] ?: 0.0)) }
}

/**
 * [fatigue] per side: a left set credits the left side in full, a right set the right side, an ordinary set both; the
 * decay, the normal session, the squash and the thresholds are the same, so a side that did nothing reads ready.
 */
fun fatigueBySide(
    workouts: List<Workout>,
    now: LocalDateTime,
    musclesOf: (String) -> MuscleUse,
    loadOf: LoadOf = LOGGED_LOAD,
): Map<Muscle, SideCredit> {
    val left = fatigueSum(workouts, now, musclesOf, loadOf, Side.LEFT)
    val right = fatigueSum(workouts, now, musclesOf, loadOf, Side.RIGHT)
    return Muscle.entries.associateWith { SideCredit(1 - exp(-(left[it] ?: 0.0)), 1 - exp(-(right[it] ?: 0.0))) }
}

/**
 * When a muscle at fatigue [level] (as [fatigue] gives it at [now]) reads ready, or null if it
 * already does. The total fades as one exponential, so this is the half-life run backwards.
 */
fun readyAt(level: Double, now: LocalDateTime): LocalDateTime? {
    if (level < FATIGUE_RECOVERING) return null
    val hours = FATIGUE_HALF_LIFE_HOURS * ln(ln(1 - level) / ln(1 - FATIGUE_RECOVERING)) / ln(2.0)
    return now.plusSeconds(ceil(hours * 3600).toLong())
}

/** What a fatigue level is called: `fatigued`, `recovering` or `ready`. */
fun fatigueWord(level: Double): String = if (level > FATIGUE_FATIGUED) "fatigued" else if (level >= FATIGUE_RECOVERING) "recovering" else "ready"
