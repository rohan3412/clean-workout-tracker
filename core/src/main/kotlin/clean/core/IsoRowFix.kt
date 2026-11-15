package clean.core

/*
 * A one-time correction of the history of "Iso-Lateral Row (Machine)" (docs/domain.md, Iso-Lateral Row history fix). The user
 * logged it under one name in three stretches: wide grip, then neutral grip (the standard now, which keeps the name), then one
 * arm at a time, with both arms' weights added together. The notes at the start of blocks say where each stretch began; this
 * reads the history in date order and plans the new rows. It only plans: nothing is written here, and the app writes the plan
 * only at the user's word, after a backup.
 */

/** The exercise the history was logged under, exactly as stored. Neutral grip keeps it. */
const val ISO_ROW = "Iso-Lateral Row (Machine)"

/** The exercise wide-grip blocks move to, exactly as it is to be named. */
const val ISO_ROW_WIDE = "Iso-Lateral Row (Wide Grip)"

/** The grip a block was done with. */
enum class IsoGrip { WIDE, NEUTRAL }

/** What happens to a block: its name changes, it is split into left and right sets, it is left as it is because it already is left and right, or nothing. */
enum class IsoAction { RENAME, SPLIT, UNCHANGED, NONE }

/** One block of the exercise and what becomes of it. [lines] are the set lines of a split, `F 90×9 → L 45×9 + R 45×9`. */
data class IsoBlockPlan(
    val date: String,
    val workoutName: String,
    /** Which of the workout's blocks of this exercise it is, counting from 0. */
    val index: Int,
    val sets: Int,
    val notes: List<String>,
    val grip: IsoGrip,
    /** In the one-arm stretch: its sets are both arms added together. */
    val sides: Boolean,
    val action: IsoAction,
    /** The exercise name the block's rows end up with. */
    val target: String,
    val lines: List<String>,
)

/** The new rows of one workout, in stored order, replacing all of its rows. */
class IsoWorkoutFix(val date: String, val rows: List<WorkoutRow>)

class IsoRowPlan(val blocks: List<IsoBlockPlan>, val workouts: List<IsoWorkoutFix>) {
    val isEmpty: Boolean get() = workouts.isEmpty()

    /** Workouts with a block that moves to wide grip, workouts with a block split into left and right, the set rows split, and blocks left alone. */
    val movedToWide: Int get() = blocks.filter { it.action == IsoAction.RENAME || it.action == IsoAction.SPLIT }.filter { it.grip == IsoGrip.WIDE }.map { it.date }.distinct().size
    val convertedToSides: Int get() = blocks.filter { it.action == IsoAction.SPLIT }.map { it.date }.distinct().size
    val setRowsSplit: Int get() = blocks.filter { it.action == IsoAction.SPLIT }.sumOf { it.sets }
    val leftAlone: Int get() = blocks.count { it.action == IsoAction.UNCHANGED }
}

private val SIDES_WORDS = listOf("standing", "one arm", "one side", "single arm")

/**
 * The plan for [rows] (the profile's, in stored order). Blocks are walked in date order, by their opening notes
 * (the left and right marker ignored; case does not matter):
 * - Grip: wide until the first block whose note says "neutral", from that block on neutral, except a block whose own note says
 *   wide ("wide", "regular grip"), which is wide. Notes naming no grip follow the way it was being done.
 * - One arm at a time: from the first block whose note says "standing", "one arm", "one side" or "single arm" on, every set is
 *   both arms added together, and becomes a left and a right set of half the weight.
 * Wide blocks move to [ISO_ROW_WIDE]; neutral ones keep [ISO_ROW]. A block that already carries the marker is left alone.
 * Other names, or [exercise] under another spelling, are not touched.
 */
fun planIsoRowFix(rows: List<WorkoutRow>, exercise: String = ISO_ROW, wide: String = ISO_ROW_WIDE): IsoRowPlan {
    var neutral = false
    var sides = false
    val blocks = mutableListOf<IsoBlockPlan>()
    val fixes = mutableListOf<IsoWorkoutFix>()
    for (workout in buildWorkouts(rows).sortedBy { it.date }) {
        var index = 0
        var touched = false
        val out = mutableListOf<WorkoutRow>()
        for (block in workout.blocks) {
            if (block.exercise != exercise) {
                out += block.items.map { it.row }
                continue
            }
            // A note only ever opens a block: one after a set starts the next block.
            val texts = block.notes.map { it.text }
            val said = texts.joinToString(" ").lowercase()
            if ("neutral" in said) neutral = true
            if (SIDES_WORDS.any { it in said }) sides = true
            val grip = if (!neutral || "neutral" !in said && ("wide" in said || "regular grip" in said)) IsoGrip.WIDE else IsoGrip.NEUTRAL
            val target = if (grip == IsoGrip.WIDE) wide else exercise
            val action = when {
                block.paired -> IsoAction.UNCHANGED
                sides -> IsoAction.SPLIT
                grip == IsoGrip.WIDE -> IsoAction.RENAME
                else -> IsoAction.NONE
            }
            val lines = if (action == IsoAction.SPLIT) block.sets.map(::splitLine) else emptyList()
            blocks += IsoBlockPlan(workout.date, workout.name, index, block.sets.size, texts, grip, sides, action, target, lines)
            when (action) {
                IsoAction.UNCHANGED, IsoAction.NONE -> out += block.items.map { it.row }
                IsoAction.RENAME -> { out += block.items.map { it.row.copy(exerciseName = target) }; touched = true }
                IsoAction.SPLIT -> { out += splitIsoBlock(block, target); touched = true }
            }
            index++
        }
        if (touched) fixes += IsoWorkoutFix(workout.date, out)
    }
    return IsoRowPlan(blocks, fixes)
}

/** Half of a stored weight in the text Clean writes weights in ([decimalText]); empty stays empty, text that is no number stays as it is, a sign is kept. */
fun halfWeightText(weight: String): String = weight.toDoubleOrNull()?.let { decimalText(it / 2) } ?: weight

/** The label of a set the way it is shown: its Set Order (`W`, `F`, `D` or its number). */
private fun splitLine(set: SetItem): String {
    fun part(weight: String) = (weight.toDoubleOrNull()?.let { shortNumber(it) } ?: weight).let { w -> if (w.isEmpty()) set.row.reps else "$w×${set.row.reps}" }
    val half = part(halfWeightText(set.row.weightKg))
    return "${set.row.setOrder} ${part(set.row.weightKg)} → L $half + R $half"
}

/**
 * [block]'s rows named [target], logged left and right: the marker note first, the original notes as they were, then each set
 * as a left and a right set of half the weight (same reps, seconds, RPE and kind), a rest row staying after the set it followed.
 * Set Order is numbered as [rowsFor] numbers a paired block: a numbered set counts the non-warm-up sets since the block's last
 * note, and W, F and D keep their letter.
 */
private fun splitIsoBlock(block: Block, target: String): List<WorkoutRow> {
    val first = block.items.first().row.copy(exerciseName = target)
    val out = mutableListOf(first.copy(setOrder = "Note", weightKg = "", reps = "", rpe = "", distanceMeters = "", seconds = "", notes = SIDES_NOTE))
    var position = 0
    for (item in block.items) {
        val row = item.row.copy(exerciseName = target)
        when (item) {
            is NoteItem -> { position = 0; out += row }
            is RestItem -> out += row
            is SetItem -> repeat(2) {
                if (item.type != SetType.WARMUP) position++
                out += row.copy(setOrder = if (item.type == SetType.NORMAL) position.toString() else item.type.letter, weightKg = halfWeightText(row.weightKg))
            }
        }
    }
    return out
}

/** The stored rows of [workouts] in the order they are held, for planning from what the app already has in memory. */
fun rowsOfWorkouts(workouts: List<Workout>): List<WorkoutRow> = workouts.flatMap { w -> w.blocks.flatMap { b -> b.items.map { it.row } } }
