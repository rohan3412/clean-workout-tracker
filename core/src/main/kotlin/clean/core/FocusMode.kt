package clean.core

/** Focus mode shows one exercise at a time; [open] says, per exercise in order, whether it still has a set not ticked. */

/** The exercise focus mode opens on: the first with a set not ticked, else the last (the workout is done, or empty: 0). */
fun focusStart(open: List<Boolean>): Int = open.indexOf(true).takeIf { it >= 0 } ?: open.lastIndex.coerceAtLeast(0)

/** The exercise to move to once [from] is finished: the next one with a set not ticked, wrapping to the start; null when none is left. */
fun focusAfter(open: List<Boolean>, from: Int): Int? {
    for (i in from + 1..open.lastIndex) if (open[i]) return i
    for (i in 0 until minOf(from, open.size)) if (open[i]) return i
    return null
}
