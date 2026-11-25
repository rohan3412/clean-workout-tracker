package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDateTime

/** How a set was logged. In the CSV this is the Set Order column: a number, `W`, `F` or `D`. */
enum class SetType(val letter: String) { NORMAL(""), WARMUP("W"), FAILURE("F"), DROP("D") }

/**
 * Whether a set takes a number among its exercise's sets: warm-ups and (since 2026-11-02, the user's words: "drop set should not count
 * as a numbered set, like warm ups") drop sets do not, so the working sets read 1, 2, 3; a failure set takes a number's place but shows F.
 */
val SetType.numbered: Boolean get() = this != SetType.WARMUP && this != SetType.DROP

/** What each of an exercise's sets is called, in order: a normal set its number (see [numbered]), any other its letter. */
fun setLabels(types: List<SetType>): List<String> {
    var number = 0
    return types.map { type ->
        if (type.numbered) number++
        if (type == SetType.NORMAL) number.toString() else type.letter
    }
}

/** One CSV row read as what it is. [row] is always the untouched original. */
sealed interface Item { val row: WorkoutRow }
data class NoteItem(override val row: WorkoutRow) : Item { val text get() = row.notes }
data class RestItem(override val row: WorkoutRow) : Item { val seconds get() = row.seconds.toDoubleOrNull() }
data class SetItem(override val row: WorkoutRow, val type: SetType) : Item {
    // Read from the row's text once, when the set is built: these are asked for in every loop over the history.
    val weight: Double? = row.weightKg.toDoubleOrNull()
    val reps: Int? = row.reps.toIntOrNull()
    /** How hard the set felt (6 to 10 in Strong's RPE column); null when none was logged. */
    val rpe: Double? = row.rpe.toDoubleOrNull()
    val seconds: Double? = row.seconds.toDoubleOrNull()
    val distance: Double? = row.distanceMeters.toDoubleOrNull()
}

/**
 * One exercise card: a run of consecutive rows with the same exercise name. A note that comes
 * after sets starts a new card, because that is the same exercise added a second time
 * (docs/data-format.md).
 */
data class Block(val exercise: String, val items: List<Item>) {
    val sets: List<SetItem> = items.filterIsInstance<SetItem>()

    /** How many of the sets count as training: all but the warm-ups. */
    val workingSets: Int = sets.count { it.type != SetType.WARMUP }

    /** The working set with the highest Epley figure; see [bestSetOf]. */
    val bestSet: SetItem? by lazy { bestSetOf(sets) }

    private val marker: NoteItem? = items.takeWhile { it !is SetItem }.firstOrNull { it is NoteItem && isSidesMarker(it.text) } as NoteItem?

    /** Whether the block was logged as left and right: it opens with the reserved marker note (docs/left-right.md). */
    val paired: Boolean = marker != null

    /** The user's own notes: the marker is in [items] so it is written back, but it is not something the user typed. */
    val notes: List<NoteItem> = items.filterIsInstance<NoteItem>().filter { !isSidesMarker(it.text) }

    /** The layout a marker that spells it out gives, when it names every set; null otherwise (plain marker, mismatch). */
    private val layout: List<RoundShape>? = marker?.let { sidesLayout(it.text) }?.takeIf { l -> l.sumOf { it.sides.size } == sets.size }

    /**
     * Each set's side in order, parallel to [sets]: all null in a block that is not paired. A marker that spells out the layout
     * is used when it names every set; otherwise the sets are read two at a time, left then right.
     */
    val setSides: List<Side?> = if (!paired) sets.map { null } else {
        layout?.flatMap { it.sides } ?: sets.indices.map { if (it % 2 == 0) Side.LEFT else Side.RIGHT }
    }

    /** The rounds of a paired block, in row order; empty for any other block. A round may have only one half; see [Round.rightFirst]. */
    val rounds: List<Round> = if (paired) roundsFrom(sets, layout ?: shapesOf(setSides.map { it ?: Side.LEFT })) else emptyList()

    /** Which side [set] (one of this block's sets, found by identity) is, or null in a block that is not paired. */
    fun sideOf(set: SetItem): Side? {
        val index = sets.indexOfFirst { it === set }
        return if (index < 0) null else setSides[index]
    }

    /** What [set] counts for wherever sets are counted or summed: half in a paired block, so a round is one set. */
    @Suppress("UNUSED_PARAMETER")
    fun weightOf(set: SetItem): Double = if (paired) 0.5 else 1.0

    /** [workingSets] as counted: a round of a paired block is one set, a lone left set half of one. */
    val workingCount: Double = sets.filter { it.type != SetType.WARMUP }.sumOf { weightOf(it) }
}

data class Workout(
    val number: String, val date: String, val name: String, val durationSeconds: Long, val blocks: List<Block>,
    /** The Workout Notes column as stored (the first row that has any); empty when none. */
    val notes: String = "",
) {
    /** `YYYY-MM-DD`. */
    val day: String = date.take(10)

    /** Working sets in the whole workout. */
    val workingSets: Int = blocks.sumOf { it.workingSets }

    /** [workingSets] as counted, a round of a paired block being one set. */
    val workingCount: Double = blocks.sumOf { it.workingCount }
}

/** The working set with the highest Epley figure (no rep limit: this ranks sets, it does not claim a 1RM), ties going to more reps. */
fun bestSetOf(sets: List<SetItem>): SetItem? = sets.filter { it.type != SetType.WARMUP }.maxWithOrNull(
    compareBy<SetItem> { (it.weight ?: 0.0) * (1 + (it.reps ?: 0) / 30.0) }.thenBy { it.reps ?: 0 },
)

/** The date format of Strong's export, in which each workout's start is stored. */
val STRONG_DATE_FORMAT = DatePattern("yyyy-MM-dd HH:mm:ss")

/**
 * A stored date, or null when the text is not one. Asked for every workout in every pass over the history, so the usual
 * `yyyy-MM-dd HH:mm:ss` with a real date is read digit by digit; anything else goes through [STRONG_DATE_FORMAT] as before.
 */
fun parseStrongDate(date: String): LocalDateTime? {
    if (date.length == 19 && date[4] == '-' && date[7] == '-' && date[10] == ' ' && date[13] == ':' && date[16] == ':') {
        fun n(from: Int, to: Int): Int {
            var v = 0
            for (i in from until to) { val d = date[i] - '0'; if (d !in 0..9) return -1; v = v * 10 + d }
            return v
        }
        val year = n(0, 4); val month = n(5, 7); val day = n(8, 10); val hour = n(11, 13); val minute = n(14, 16); val second = n(17, 19)
        if (year > 0 && month in 1..12 && day >= 1 && day <= monthLength(year, month) && hour in 0..23 && minute in 0..59 && second in 0..59) return LocalDateTime.of(year, month, day, hour, minute, second)
    }
    return runCatching { STRONG_DATE_FORMAT.parse(date) }.getOrNull()
}

fun itemOf(row: WorkoutRow): Item = when (row.setOrder) {
    "Note" -> NoteItem(row)
    "Rest Timer" -> RestItem(row)
    "W" -> SetItem(row, SetType.WARMUP)
    "F" -> SetItem(row, SetType.FAILURE)
    "D" -> SetItem(row, SetType.DROP)
    else -> SetItem(row, SetType.NORMAL)
}

/** Groups rows into workouts and exercise blocks without reordering anything. */
fun buildWorkouts(rows: List<WorkoutRow>): List<Workout> {
    val workouts = mutableListOf<Workout>()
    var i = 0
    while (i < rows.size) {
        val first = rows[i]
        val blocks = mutableListOf<Block>()
        var notes = ""
        while (i < rows.size && rows[i].workoutNumber == first.workoutNumber && rows[i].date == first.date) {
            val exercise = rows[i].exerciseName
            val items = mutableListOf<Item>()
            while (i < rows.size && rows[i].workoutNumber == first.workoutNumber && rows[i].date == first.date &&
                rows[i].exerciseName == exercise
            ) {
                if (notes.isEmpty()) notes = rows[i].workoutNotes
                val item = itemOf(rows[i])
                if (item is NoteItem && items.any { it is SetItem }) break
                items += item
                i++
            }
            blocks += Block(exercise, items)
        }
        workouts += Workout(first.workoutNumber, first.date, first.workoutName, first.durationSeconds.toLongOrNull() ?: 0, blocks, notes)
    }
    return workouts
}

// --- Writing workouts logged in Clean ---

sealed interface NewItem
data class NewNote(val text: String) : NewItem
data class NewSet(
    val type: SetType,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Double? = null,
    val distanceMeters: Double? = null,
    /** How hard the set felt, 6 to 10 in half steps, as typed; empty when not given. */
    val rpe: String = "",
) : NewItem

/** The rest that followed a set, as Strong used to write it: a row of its own after the set. */
data class NewRest(val seconds: Int) : NewItem

/**
 * One exercise's planned or logged items; [paired] writes the reserved marker note first (docs/left-right.md). [shapes] is
 * how the block's sets, in order, make rounds (null: left then right each time): the marker spells it out unless every round is that.
 */
data class NewBlock(val exercise: String, val items: List<NewItem>, val paired: Boolean = false, val shapes: List<RoundShape>? = null)

/**
 * Rows for a workout logged in Clean, in Strong's format. A numbered set's value is its position
 * among the non-warm-up sets since the start of the block or the last note (docs/data-format.md). [workoutNotes] goes in the
 * Workout Notes column of every row, as the other workout-level fields do (docs/data-format.md).
 */
fun rowsFor(number: Int, date: String, name: String, durationSeconds: Long, blocks: List<NewBlock>, workoutNotes: String = ""): List<WorkoutRow> {
    val rows = mutableListOf<WorkoutRow>()
    for (block in blocks) {
        var position = 0
        val layout = block.shapes?.takeIf { l -> l.sumOf { it.sides.size } == block.items.count { item -> item is NewSet } }
        val items = if (block.paired) listOf<NewItem>(NewNote(sidesMarker(layout))) + block.items else block.items
        for (item in items) {
            val base = WorkoutRow(number.toString(), date, name, durationSeconds.toString(), block.exercise, "", "", "", "", "", "", "", workoutNotes)
            rows += when (item) {
                is NewNote -> {
                    position = 0
                    base.copy(setOrder = "Note", notes = item.text)
                }
                is NewSet -> {
                    if (item.type.numbered) position++
                    base.copy(
                        setOrder = if (item.type == SetType.NORMAL) position.toString() else item.type.letter,
                        weightKg = item.weightKg?.let(::decimalText) ?: "",
                        reps = item.reps?.toString() ?: "",
                        distanceMeters = item.distanceMeters?.let(::decimalText) ?: "",
                        seconds = item.seconds?.let(::decimalText) ?: "",
                        rpe = item.rpe,
                    )
                }
                is NewRest -> base.copy(setOrder = "Rest Timer", seconds = decimalText(item.seconds.toDouble()))
            }
        }
    }
    return rows
}

// --- Numbers and units ---

const val KG_PER_LB = 0.45359237

/** A number the way Strong writes it: at least one decimal, at most four (`10.0`, `7.5`, `54.4311`). */
fun decimalText(value: Double): String {
    val plain = plainDecimal(value, 4)
    return if ('.' in plain) plain else "$plain.0"
}

fun lbToKg(lb: Double): Double = roundedUnscaled(lb * KG_PER_LB, 4) / 10000.0

/** Pounds for display, to the nearest 0.5. */
fun kgToLb(kg: Double): Double = jRound(kg / KG_PER_LB * 2) / 2.0

/** A number for the screen: no trailing `.0`, at most two decimals. */
fun shortNumber(value: Double): String =
    plainDecimal(value, 2)
