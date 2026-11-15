package clean.core

import clean.core.Muscle.*
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * The parts of a muscle group that the body figure can show on their own. A head is not a muscle of
 * its own: it belongs to one [group], and an exercise names a head only when it singles that part out.
 */
enum class Head(val label: String, val group: Muscle) {
    FRONT_DELT("Front delt", SHOULDERS), SIDE_DELT("Side delt", SHOULDERS), REAR_DELT("Rear delt", SHOULDERS),
    UPPER_CHEST("Upper chest", CHEST), LOWER_CHEST("Lower chest", CHEST),
    BICEPS_LONG("Long head", BICEPS), BICEPS_SHORT("Short head", BICEPS), BRACHIALIS("Brachialis", BICEPS),
    TRICEPS_LONG("Long head", TRICEPS), TRICEPS_LATERAL("Lateral head", TRICEPS), TRICEPS_MEDIAL("Medial head", TRICEPS),
    FOREARM_FLEXORS("Flexors", FOREARMS), FOREARM_EXTENSORS("Extensors", FOREARMS),
    UPPER_TRAPS("Upper traps", TRAPS), LOWER_TRAPS("Lower traps", TRAPS),
    RHOMBOIDS("Rhomboids", UPPER_BACK),
    UPPER_ABS("Upper abs", ABS), LOWER_ABS("Lower abs", ABS),
    RECTUS_FEMORIS("Rectus femoris", QUADS), VASTUS_LATERALIS("Vastus lateralis", QUADS), VASTUS_MEDIALIS("Vastus medialis", QUADS),
    BICEPS_FEMORIS("Biceps femoris", HAMSTRINGS),
    // Semitendinosus and semimembranosus, which lie together on the inner side and work as one.
    INNER_HAMSTRINGS("Inner hamstrings", HAMSTRINGS),
    GLUTEUS_MAXIMUS("Gluteus maximus", GLUTES), GLUTEUS_MEDIUS("Gluteus medius", GLUTES),
    GASTROCNEMIUS("Gastrocnemius", CALVES), SOLEUS("Soleus", CALVES),
}

/*
 * The exercises that name heads, by the words in their name. Isolation work only: a squat, a row or
 * a press trains a whole group and names no head. The exception is the angle of a chest press or
 * fly, which is the point of it.
 *
 * One line per rule: `words | not | heads`. The first rule that fits and leaves a head standing wins,
 * so the more specific phrase goes first.
 * - words: alternatives, any one of which is enough; `a + b` needs both words. A plural counts.
 * - not: the name has none of these (`-` for nothing).
 * - heads: `HEAD level`, level being main (1), supporting (0.5) or minor (0.25).
 * A head only counts where its group is main or supporting for the exercise ([headsFor]), which is
 * what keeps `leg raise` off Lateral Leg Raises (glutes), `kickback` apart for triceps and glutes,
 * and `incline` off Incline Row.
 */
private const val HEAD_RULES = """
seated + calf | leg press | SOLEUS main, GASTROCNEMIUS minor
calf | - | GASTROCNEMIUS main, SOLEUS minor
incline + press, incline + fly, incline + push up, incline + bench, low to high | - | UPPER_CHEST main, LOWER_CHEST minor
decline + press, decline + fly, decline + push up, decline + bench, chest dip, high to low | - | LOWER_CHEST main, UPPER_CHEST minor
lateral raise, side raise | - | SIDE_DELT main
front raise | - | FRONT_DELT main
face pull | - | REAR_DELT main, RHOMBOIDS supporting
reverse fly, rear delt | - | REAR_DELT main
overhead + extension, french press | - | TRICEPS_LONG main
pushdown, pressdown | - | TRICEPS_LATERAL main, TRICEPS_MEDIAL supporting
skullcrusher, skull crusher, lying + extension | - | TRICEPS_LONG main, TRICEPS_LATERAL supporting
kickback | - | TRICEPS_LATERAL main, TRICEPS_LONG supporting
reverse wrist curl, wrist extension | - | FOREARM_EXTENSORS main
wrist curl | - | FOREARM_FLEXORS main
reverse curl | - | BRACHIALIS main, FOREARM_EXTENSORS main
hammer | - | BRACHIALIS main
incline + curl, drag curl, bayesian | - | BICEPS_LONG main
preacher, concentration, spider | reverse | BICEPS_SHORT main
shrug | - | UPPER_TRAPS main
y raise, prone trap raise | - | LOWER_TRAPS main
leg extension | - | RECTUS_FEMORIS main, VASTUS_LATERALIS main, VASTUS_MEDIALIS main
sissy squat | - | RECTUS_FEMORIS main
hip thrust, glute bridge, kickback | - | GLUTEUS_MAXIMUS main
hip abduction, hip abductor, clamshell, lateral band walk, side lying leg raise | - | GLUTEUS_MEDIUS main
leg curl, nordic | - | BICEPS_FEMORIS main, INNER_HAMSTRINGS main
reverse crunch, leg raise, knee raise | - | LOWER_ABS main
crunch, sit up | oblique, bicycle | UPPER_ABS main
"""

private class HeadRule(val alternatives: List<List<String>>, val not: List<String>, val heads: Map<Head, Double>)

private val PARSED_HEAD_RULES: List<HeadRule> by lazy {
    HEAD_RULES.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('|').map { it.trim() }
        HeadRule(
            parts[0].split(',').map { alt -> alt.split('+').map { it.trim() } },
            parts[1].split(',').map { it.trim() }.filter { it != "-" },
            parts[2].split(',').associate { cell ->
                val (head, level) = cell.trim().split(' ')
                Head.valueOf(head) to when (level) { "main" -> 1.0; "supporting" -> SECONDARY_SHARE; else -> MINOR_SHARE }
            },
        )
    }.toList()
}

private val NOT_A_HEAD_WORD = Regex("[^a-z0-9]+")

private fun headHas(words: String, phrase: String): Boolean = " $words ".let { " $phrase " in it || " ${phrase}s " in it }

private val HEADS_MADE = ConcurrentHashMap<Pair<String, MuscleUse>, Map<Head, Double>>()

/**
 * The heads the rules give an exercise by its name, keeping only those whose group is main or
 * supporting in [groups]. Asked for every set in a pass over the history, so answers are kept.
 */
fun headsFor(name: String, groups: MuscleUse): Map<Head, Double> {
    val bare = if (groups.heads.isEmpty()) groups else groups.copy(heads = emptyMap())
    return HEADS_MADE.getOrPut(name to bare) {
        val words = name.lowercase().replace(NOT_A_HEAD_WORD, " ").trim()
        var found = emptyMap<Head, Double>()
        for (rule in PARSED_HEAD_RULES) {
            if (rule.alternatives.none { alt -> alt.all { headHas(words, it) } } || rule.not.any { headHas(words, it) }) continue
            val kept = rule.heads.filterKeys { it.group in bare.primary || it.group in bare.secondary }
            if (kept.isNotEmpty()) { found = kept; break }
        }
        found
    }
}

/**
 * The heads an exercise names: those stored with its muscles when the user's [use] has any, else
 * the rules' (also when the groups themselves are the user's, since the first edit stores them).
 */
fun headsOf(name: String, use: MuscleUse): Map<Head, Double> = use.heads.ifEmpty { headsFor(name, use) }

/**
 * Sets worked per head since [from], like [muscleSets]: warm-ups do not count, and a working set adds the head's share
 * (a round of a left and right exercise is one set).
 */
fun headSets(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse): Map<Head, Double> {
    val sets = HashMap<Head, Double>()
    for (w in workouts) {
        if (from != null && (parseStrongDate(w.date) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = b.workingCount
            if (n > 0) for ((head, share) in musclesOf(b.exercise).heads) sets[head] = (sets[head] ?: 0.0) + share * n
        }
    }
    return sets
}

/** [headSets] by side, credited as [muscleSetsBySide] credits a muscle. */
fun headSetsBySide(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse): Map<Head, SideCredit> {
    val sets = HashMap<Head, SideCredit>()
    for (w in workouts) {
        if (from != null && (parseStrongDate(w.date) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = sideCounts(b)
            if (n.left == 0.0 && n.right == 0.0) continue
            for ((head, share) in musclesOf(b.exercise).heads) {
                val had = sets[head]
                sets[head] = SideCredit((had?.left ?: 0.0) + share * n.left, (had?.right ?: 0.0) + share * n.right)
            }
        }
    }
    return sets
}

/**
 * The sets of a [group] that came from exercises naming none of its heads, so a page can show the
 * heads and "Other work" beside them. The heads do not add up to the group: an exercise that names
 * one head still counts for the whole group.
 */
fun otherWork(group: Muscle, workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse): Double {
    var sets = 0.0
    for (w in workouts) {
        if (from != null && (parseStrongDate(w.date) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = b.workingCount
            if (n == 0.0) continue
            val use = musclesOf(b.exercise)
            val share = use.shares[group] ?: continue
            if (use.heads.keys.none { it.group == group }) sets += share * n
        }
    }
    return sets
}

/** The exercises that name [head], like [muscleBreakdown] but for one head: each set counts as the head's share. */
fun headBreakdown(workouts: List<Workout>, from: LocalDateTime?, musclesOf: (String) -> MuscleUse, head: Head): List<MuscleExercise> {
    val exercises = LinkedHashMap<String, MuscleExercise>()
    for (w in workouts) {
        if (from != null && (parseStrongDate(w.date) ?: continue) < from) continue
        for (b in w.blocks) {
            val n = b.workingSets
            val share = musclesOf(b.exercise).heads[head] ?: continue
            if (n == 0) continue
            val had = exercises[b.exercise]
            exercises[b.exercise] = MuscleExercise(b.exercise, (had?.sets ?: 0) + n, share, w.date, (had?.weighted ?: 0.0) + b.workingCount)
        }
    }
    return exercises.values.sortedByDescending { it.counted }
}
