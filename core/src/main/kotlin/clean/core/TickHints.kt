package clean.core

/** The boxes of a set as they are logged once it is ticked. */
data class Accepted(val weight: String, val reps: String)

/**
 * What ticking a set logs: each empty box takes exactly the grey hint it shows, a box the user typed keeps its value.
 * Null when the reps box (the seconds or minutes box for timed and cardio work) ends up with no usable number, so the
 * set cannot be ticked. An empty weight with no hint stays empty, as it does for a bodyweight set.
 */
fun acceptHints(weight: String, reps: String, hintWeight: String, hintReps: String, load: LoadType): Accepted? {
    val filled = reps.ifEmpty { hintReps }
    val usable = if (load == LoadType.CARDIO || load == LoadType.TIMED) filled.trim().replace(',', '.').toDoubleOrNull() != null else filled.toIntOrNull() != null
    return if (usable) Accepted(weight.ifEmpty { hintWeight }, filled) else null
}

/**
 * The sets the number pad's "Same" key may copy from, best first: indices in [isSet] (true for a set, false for a note).
 * In an ordinary card that is the previous set. In a left and right card it is the same side of the previous round, then
 * the other side of that round (for when the first has no value, or the round is short of a side); a right half with no
 * round before it has the left half of its own round. Empty when there is none.
 */
fun setsAbove(isSet: List<Boolean>, index: Int, paired: Boolean): List<Int> {
    if (!paired) return listOfNotNull((index - 1 downTo 0).firstOrNull { isSet[it] })
    val rounds = sidePairs(isSet)
    val k = rounds.indexOfFirst { it.first == index || it.second == index }
    if (k < 0) return emptyList()
    val above = rounds.getOrNull(k - 1)
        ?: return if (rounds[k].second == index) listOf(rounds[k].first) else emptyList()
    return listOfNotNull(if (rounds[k].first == index) above.first else above.second, if (rounds[k].first == index) above.second else above.first)
}

/** The first of [setsAbove]: the set "Same" copies from by position alone. */
fun setAbove(isSet: List<Boolean>, index: Int, paired: Boolean): Int? = setsAbove(isSet, index, paired).firstOrNull()
