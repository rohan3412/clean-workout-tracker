package clean.core

/*
 * Left and right: an exercise done one side at a time is drawn as pairs of sets, but it is stored as
 * ordinary consecutive sets (left, right, left, right ...). The only thing in the rows is the reserved
 * note [SIDES_NOTE] that opens a paired block (docs/left-right.md). These are the rules for reading a
 * flat list of a card's items (sets and notes) as pairs.
 */

/** The text of the note row that marks a block as logged left and right; Strong shows it as an exercise note. */
const val SIDES_NOTE = "Left, then right"

enum class Side { LEFT, RIGHT }

/**
 * A round: a left set and the right set that went with it. [right] is null for a left set whose right half was not done,
 * and [left] for a right set done without one. [rightFirst] is true when the right set was done (and is stored) first.
 */
data class Round(val left: SetItem?, val right: SetItem?, val rightFirst: Boolean = false)

/** How one round is stored, in row order; its [token] is what the marker note spells. */
enum class RoundShape(val token: String, val sides: List<Side>) {
    LEFT_RIGHT("LR", listOf(Side.LEFT, Side.RIGHT)),
    RIGHT_LEFT("RL", listOf(Side.RIGHT, Side.LEFT)),
    LEFT_ONLY("L-", listOf(Side.LEFT)),
    RIGHT_ONLY("R-", listOf(Side.RIGHT)),
}

/** Whether [text] is a block's marker note: [SIDES_NOTE] alone, or with the layout of its sets spelled out after a colon. */
fun isSidesMarker(text: String): Boolean = text == SIDES_NOTE || sidesLayout(text) != null

/**
 * The rounds a marker that spells out the layout names, null for plain [SIDES_NOTE] and for any other text. Two spellings:
 * `Left, then right: LR RL L-` (a token per round, the new one) and `Left, then right: L R R L R` (the side of each set,
 * the old one, read with the old rule of [shapesOf]); a marker mixing the two is not a marker.
 */
fun sidesLayout(text: String): List<RoundShape>? {
    val prefix = "$SIDES_NOTE: "
    if (!text.startsWith(prefix)) return null
    val tokens = text.removePrefix(prefix).split(' ')
    if (tokens.all { it == "L" || it == "R" }) return shapesOf(tokens.map { if (it == "L") Side.LEFT else Side.RIGHT })
    return tokens.map { t -> RoundShape.values().firstOrNull { it.token == t } ?: return null }
}

/** [sides] cut into rounds by the old rule: a left set opens one, a right set completes the open one, else stands alone. */
fun shapesOf(sides: List<Side>): List<RoundShape> {
    val shapes = mutableListOf<RoundShape>()
    var open = false
    for (side in sides) {
        if (side == Side.LEFT) {
            if (open) shapes += RoundShape.LEFT_ONLY
            open = true
        } else {
            shapes += if (open) RoundShape.LEFT_RIGHT else RoundShape.RIGHT_ONLY
            open = false
        }
    }
    if (open) shapes += RoundShape.LEFT_ONLY
    return shapes
}

/**
 * The marker note for a block whose rounds are stored as [shapes]: plain when every round is left then right (a lone left
 * at the end allowed), which is also what null means; else the layout spelled out, one token per round.
 */
fun sidesMarker(shapes: List<RoundShape>?): String =
    if (shapes == null || shapes.withIndex().all { (i, s) -> s == RoundShape.LEFT_RIGHT || (s == RoundShape.LEFT_ONLY && i == shapes.lastIndex) }) SIDES_NOTE
    else SIDES_NOTE + ": " + shapes.joinToString(" ") { it.token }

/** [sets] (in row order) cut into rounds as [shapes] says; the shapes must account for exactly the sets. */
fun roundsFrom(sets: List<SetItem>, shapes: List<RoundShape>): List<Round> {
    var next = 0
    return shapes.map { shape ->
        val first = sets[next]
        val second = sets.getOrNull(next + 1)
        next += shape.sides.size
        when (shape) {
            RoundShape.LEFT_RIGHT -> Round(first, second)
            RoundShape.RIGHT_LEFT -> Round(second, first, rightFirst = true)
            RoundShape.LEFT_ONLY -> Round(first, null)
            RoundShape.RIGHT_ONLY -> Round(null, first)
        }
    }
}

/** What a muscle (or head) was credited on each side; see [muscleSetsBySide]. */
data class SideCredit(val left: Double, val right: Double)

/**
 * The card's sets paired in order: each entry is the item index of a left set and of its right one,
 * null for the right of a last, lone set. [isSet] is true for a set and false for a note.
 */
fun sidePairs(isSet: List<Boolean>): List<Pair<Int, Int?>> =
    isSet.indices.filter { isSet[it] }.chunked(2).map { it[0] to it.getOrNull(1) }

/** The pair holding the item at [index], or null when it is not a set. */
fun pairHolding(isSet: List<Boolean>, index: Int): Pair<Int, Int?>? =
    sidePairs(isSet).firstOrNull { it.first == index || it.second == index }

/** The item indices that go when the pair holding [index] is removed. */
fun pairIndices(isSet: List<Boolean>, index: Int): List<Int> =
    pairHolding(isSet, index)?.let { listOfNotNull(it.first, it.second) }.orEmpty()

/** How many sets a card with [setCount] sets is short of whole pairs: 1 when the last pair has no right half, else 0. */
fun setsToCompletePairs(setCount: Int): Int = setCount % 2

/** How many sets "Add set" adds to a card with [setCount] sets: the missing half, else a whole new pair. */
fun setsToAddPair(setCount: Int): Int = if (setCount % 2 == 1) 1 else 2

/**
 * Whether ticking a set starts the rest between sides: it is the first of its pair to be done. A set with no
 * partner, or one whose partner is already done, gets the ordinary rest for its kind of set.
 */
fun startsSidesRest(hasPartner: Boolean, partnerDone: Boolean): Boolean = hasPartner && !partnerDone

/**
 * The kinds of the planned sets of a left and right exercise, one per pair (a template keeps `n`, not `2n`).
 * With [done] given, only the pairs with a set done count, as the template of a workout that was done.
 */
fun pairTypes(types: List<SetType>, done: List<Boolean>? = null): List<SetType> =
    types.indices.chunked(2).filter { c -> done == null || c.any { done.getOrElse(it) { false } } }.map { types[it.first()] }

/**
 * One workout's [rows] with the [nth] block of [exercise] (counted as [buildWorkouts] cuts blocks) marked as left and
 * right ([paired] true: the marker row is put first in the block) or taken back to ordinary sets (the marker row goes).
 * Nothing else about the rows changes. Null when there is no such block or it is already as asked.
 */
fun withSidesMarker(rows: List<WorkoutRow>, exercise: String, nth: Int, paired: Boolean): List<WorkoutRow>? {
    // The blocks, as the first row and one past the last row of each.
    val blocks = mutableListOf<IntRange>()
    var start = 0
    for (i in 1..rows.size) {
        val breaks = i == rows.size || rows[i].exerciseName != rows[start].exerciseName ||
            (rows[i].setOrder == "Note" && rows.subList(start, i).any { itemOf(it) is SetItem })
        if (breaks) {
            blocks += start until i
            start = i
        }
    }
    val block = blocks.filter { rows[it.first].exerciseName == exercise }.getOrNull(nth) ?: return null
    // The marker is among the rows before the block's first set.
    val opening = block.takeWhile { itemOf(rows[it]) !is SetItem }
    val markers = opening.filter { rows[it].setOrder == "Note" && isSidesMarker(rows[it].notes) }
    if (paired) {
        if (markers.isNotEmpty()) return null
        val first = rows[block.first]
        val marker = first.copy(setOrder = "Note", weightKg = "", reps = "", rpe = "", distanceMeters = "", seconds = "", notes = SIDES_NOTE)
        return rows.subList(0, block.first) + marker + rows.subList(block.first, rows.size)
    }
    if (markers.isEmpty()) return null
    return rows.filterIndexed { i, _ -> i !in markers }
}
