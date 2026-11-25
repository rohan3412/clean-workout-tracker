package clean.core


/*
 * Variations the library offers for an exercise by rule, on top of the hand-written ones in
 * Library.kt. They are worked out when asked for and are never part of LIBRARY, so the exercise
 * list does not grow. A variation is named `Exercise (Label)`: `Lat Pulldown (Machine) (Wide Grip)`.
 *
 * One line per rule: `labels | who | words | not | equipment`.
 * - labels: what the rule offers. A rule is one choice (a grip width, a stance), so it is skipped
 *   for an exercise whose name already says one of its labels.
 * - who: the exercise's main muscles are in the `upper` body, the `lower` body or the `trunk`;
 *   `timed` is an exercise logged in seconds. `*` is upper, lower and trunk. Cardio gets nothing.
 * - words: the name has at least one of these (`*` for any name). A plural counts.
 * - not: the name has none of these (`-` for nothing).
 * - equipment: what the name says it is done with (`*` for anything); `none` is a name with no equipment in it.
 * A label is left out where the library already has that exercise under a name of its own
 * (`Lat Pulldown - Wide Grip (Cable)`, `Incline Bench Press (Barbell)`, `Hammer Curl (Dumbbell)`).
 */
private const val RULES = """
Wide Grip, Close Grip | upper | pulldown, pull up, chin up, row, bench press, chest press, overhead press, shoulder press, military press, push up, curl | single, one arm, wrist, concentration, straight arm, hammer, diamond | barbell, ez bar, smith machine, cable, machine, plate loaded, band, bodyweight, weighted, assisted, none
Supinated, Neutral Grip | upper | pulldown, pull up | straight arm, reverse, kipping, scapular, negative | cable, machine, plate loaded, band, bodyweight, weighted, assisted, none
Supinated, Pronated, Neutral Grip | upper | row, raise, pushdown, triceps extension | reverse, upright, lever, plate | dumbbell, cable, machine, plate loaded, band, kettlebell, bodyweight, suspension, none
Supinated, Pronated | upper | row, raise | reverse, upright | barbell, ez bar, smith machine
Pronated, Neutral Grip | upper | curl | wrist, reverse, zottman | dumbbell, cable, machine, plate loaded, band, kettlebell
Pronated | upper | curl | wrist, reverse | barbell, ez bar
Neutral Grip | upper | bench press, chest press, overhead press, shoulder press | arnold | dumbbell, machine, plate loaded
Single Arm | upper | row, pulldown, press, curl, raise, fly, pushdown, extension, kickback, pullover, shrug, crossover | push press, landmine | dumbbell, cable, machine, plate loaded, kettlebell, band
Rope, Straight Bar, V Bar | upper | pushdown, triceps extension, curl, row, pulldown | single, one arm, bar, straight arm, upright, crossover | cable
Seated | upper | overhead press, shoulder press, military press, curl, lateral raise, front raise, triceps extension | standing, lying, incline, decline, preacher, concentration, spider, kneeling, bent over, wrist | dumbbell, barbell, ez bar, kettlebell, band
Incline, Decline | upper | bench press, chest press, fly, push up, squeeze press | seated, standing, floor, pike, handstand, reverse, rear, low to high, high to low, feet up | *
Incline | upper | curl, lateral raise, front raise | seated, standing, lying, preacher, concentration, spider, wrist, bent over | dumbbell
Wide Stance, Narrow Stance | lower | squat, deadlift, leg press, hip thrust, glute bridge | split, pistol, single, one leg, lunge, jump, sissy, lateral, pull, row, thruster, overhead | *
Feet High, Feet Low | lower | leg press, hack squat | single, calf | *
Heels Elevated | lower | squat | split, pistol, single, lunge, jump, sissy, lateral, hack, overhead, row, thruster, box | *
Deficit | lower | deadlift | rack, block, pull, single | barbell, dumbbell, kettlebell, smith machine, trap bar
Toes Out, Toes In | lower | calf raise, calf press, calf extension, leg extension, leg curl | - | *
Single Leg | lower | leg press, leg extension, leg curl, calf raise, calf press, calf extension, hip thrust, glute bridge, romanian deadlift, stiff leg deadlift | - | *
Glute Focus, Lower Back Focus | trunk, lower | back extension, hyperextension | reverse | *
Decline | trunk | crunch, sit up | reverse, bicycle, standing, hanging, cross | none, bodyweight, weighted
Weighted | upper, lower, trunk, timed | crunch, sit up, leg raise, knee raise, pull up, chin up, dip, push up, back extension, hyperextension, inverted row, plank, nordic, glute bridge, pistol squat, v up, toes to bar | kipping, scapular, clap | none, bodyweight
Pause, Slow Eccentric, Partials, 1.5 Reps | * | * | stretch, walk, carry, jump, hop, skip, sprint, run, clean, snatch, jerk, swing, throw, slam, toss, hold, plank, burpee, climber, jack, crawl, sled, battle, get up, thruster, kipping, muscle up, wall ball, pose, tilt, circle, windmill, bike, bicycle, kick, flutter, scissor, dead bug, bird dog, plyo, clap, roller, twist, around the world, halo, boxing | *
"""

/** Other words for a label: a name that has one already is that variation, whatever it calls it. */
private val SAME_AS = mapOf(
    "Wide Grip" to listOf("wide"),
    "Close Grip" to listOf("close", "narrow"),
    "Supinated" to listOf("underhand", "reverse grip", "palms up", "chin up"),
    "Pronated" to listOf("overhand", "palms down", "reverse curl"),
    "Neutral Grip" to listOf("neutral", "hammer"),
    "Single Arm" to listOf("single", "one arm", "unilateral", "alternate", "alternating"),
    "Single Leg" to listOf("single", "one leg", "pistol"),
    "Wide Stance" to listOf("wide", "sumo"),
    "Narrow Stance" to listOf("narrow", "close"),
    "Feet High" to listOf("high feet"),
    "Feet Low" to listOf("low feet"),
    "Pause" to listOf("paused"),
    "Slow Eccentric" to listOf("eccentric", "negative", "tempo"),
    "Partials" to listOf("partial"),
)

// Equipment as a name writes it, longest first so "smith machine" wins over "machine".
private val EQUIPMENT = listOf(
    "smith machine" to "smith machine", "plate loaded" to "plate loaded", "ez bar" to "ez bar", "trap bar" to "trap bar",
    "stability ball" to "ball", "medicine ball" to "ball", "bosu ball" to "ball", "barbell" to "barbell", "dumbbell" to "dumbbell",
    "cable" to "cable", "machine" to "machine", "band" to "band", "kettlebell" to "kettlebell", "suspension" to "suspension",
    "landmine" to "landmine", "plate" to "plate", "bodyweight" to "bodyweight", "weighted" to "weighted", "assisted" to "assisted",
    "smith" to "smith machine",
)

// Words that are equipment only inside the brackets: a "Hammer Curl" is not done with a hammer.
private val BRACKET_EQUIPMENT = listOf("rope", "roller", "wheel", "tire", "ring", "hammer", "chair").map { it to "other" }

private val LOWER = setOf(Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS, Muscle.CALVES)
private val TRUNK = setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.LOWER_BACK)

private class Rule(val labels: List<String>, val who: Set<String>, val words: List<String>, val not: List<String>, val equipment: Set<String>)

private val PARSED: List<Rule> by lazy {
    fun cells(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "*" && it != "-" }
    RULES.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('|').map { it.trim() }
        val who = if (parts[1] == "*") setOf("upper", "lower", "trunk") else cells(parts[1]).toSet()
        Rule(cells(parts[0]), who, cells(parts[2]), cells(parts[3]), cells(parts[4]).toSet())
    }.toList()
}

private val LABELS: Set<String> by lazy { PARSED.flatMapTo(HashSet()) { it.labels } }

/** A name as plain lower-case words: `Iso-Lateral Row (Machine)` is `iso lateral row machine`. */
private val NOT_A_WORD = Regex("[^a-z0-9.]+")

private fun words(name: String): String = name.lowercase().replace(NOT_A_WORD, " ").trim()

private fun has(words: String, phrase: String): Boolean = " $words ".let { " $phrase " in it || " ${phrase}s " in it }

private fun key(words: String): String = words.split(' ').sorted().joinToString(" ")

/** Every library name as its sorted words, to spot `Incline Bench Press (Barbell)` from `Bench Press (Barbell)` and `Incline`. */
private val LIBRARY_KEYS: Set<String> by lazy { LIBRARY.mapTo(HashSet()) { key(words(it.name)) } }

/**
 * What a name says the exercise is done with, `none` when it does not say, and null when its last
 * brackets hold something else (`Pull Up (Wide Grip)`): such a name is a variation already.
 */
private fun equipmentOf(name: String): String? {
    if (!name.endsWith(")")) return EQUIPMENT.firstOrNull { has(words(name), it.first) }?.second ?: "none"
    val inner = words(name.substringAfterLast('('))
    return (EQUIPMENT + BRACKET_EQUIPMENT).firstOrNull { has(inner, it.first) }?.second
}

private fun same(label: String): List<String> = listOf(words(label)) + SAME_AS[label].orEmpty()

/** The labels the rules give a library exercise, in the table's order. */
private fun labelsOf(exercise: LibraryExercise): List<String> {
    val name = exercise.name
    if (" - " in name || exercise.loadType == LoadType.CARDIO) return emptyList()
    val equipment = equipmentOf(name) ?: return emptyList()
    val who = when {
        exercise.loadType == LoadType.TIMED -> "timed"
        exercise.muscles.primary.any { it in LOWER } -> "lower"
        exercise.muscles.primary.all { it in TRUNK } -> "trunk"
        else -> "upper"
    }
    val n = words(name)
    val siblings = OVERALL[name]?.let { VARIATIONS[it] }.orEmpty().filter { it != name }
    // The library has it already: the same words plus the label, or a sibling that says the label and is done with the same thing.
    fun covered(label: String) = same(label).any { said ->
        key("$n $said") in LIBRARY_KEYS || siblings.any { has(words(it), said) && equipmentOf(it).let { e -> e == null || e == "none" || e == equipment } }
    }
    return PARSED.filter { rule ->
        who in rule.who && (rule.words.isEmpty() || rule.words.any { has(n, it) }) && rule.not.none { has(n, it) } &&
            (rule.equipment.isEmpty() || equipment in rule.equipment) && rule.labels.none { label -> same(label).any { has(n, it) } }
    }.flatMap { it.labels }.distinct().filter { !covered(it) }
}

private val GENERATED = ConcurrentCache<String, List<String>>()

/**
 * The rule-made variations of one built-in exercise (by its name or another name for it), each
 * named `[exercise] (Label)`. Empty for a name the library does not know.
 */
fun generatedVariations(exercise: String): List<String> {
    GENERATED[exercise]?.let { return it }
    val definition = libraryExercise(exercise) ?: canonicalName(exercise)?.let(::libraryExercise) ?: return emptyList()
    val made = labelsOf(definition).map { "$exercise ($it)" }.filter { libraryExercise(it) == null && canonicalName(it) == null }
    GENERATED[exercise] = made
    return made
}

/**
 * The exercise a rule-made variation was made from, or null when [name] is not exactly a built-in
 * exercise plus a label the rules give that exercise. A name that only looks similar is nobody's variation.
 */
fun variationBase(name: String): String? {
    val open = name.lastIndexOf(" (")
    if (open <= 0 || !name.endsWith(")") || name.substring(open + 2, name.length - 1) !in LABELS) return null
    return name.take(open).takeIf { name in generatedVariations(it) }
}
