package clean.core

/*
 * Carrying last time over when it was logged the other way (docs/domain.md, Left and right: carrying values over).
 * The grey hints, the Previous column, auto-fill, progression and a tick all read last time's sets through
 * [carriedSets], so a left and right card and an ordinary card can follow each other's history.
 */

/** How hard a set was, for choosing between the two sides of a round; null when it has nothing to compare. */
private fun effort(set: SetItem): Double? {
    val reps = set.reps
    val weight = set.weight
    return if (weight != null && reps != null) weight * (1 + reps / 30.0)
    else reps?.toDouble() ?: set.seconds ?: set.distance
}

/**
 * The rounds of a session whose sets carry sides. A left and a right set that follow each other (either way round) are one
 * round; a set that is alone, or has no side, is a round of its own.
 */
fun roundsOfSession(session: Session): List<Round> {
    val rounds = mutableListOf<Round>()
    var i = 0
    while (i < session.sets.size) {
        val side = session.sideOf(i)
        val next = session.sideOf(i + 1)
        if (side != null && next != null && side != next && i + 1 < session.sets.size) {
            val a = session.sets[i]
            val b = session.sets[i + 1]
            rounds += if (side == Side.LEFT) Round(a, b) else Round(b, a, rightFirst = true)
            i += 2
        } else {
            val set = session.sets[i]
            rounds += if (side == Side.RIGHT) Round(null, set) else Round(set, null)
            i++
        }
    }
    return rounds
}

/** The one set a round stands for in an ordinary card: the heavier side's (higher estimated 1RM, ties to the left), or the half that is there. */
fun heavierSide(round: Round): SetItem? {
    val left = round.left ?: return round.right
    val right = round.right ?: return left
    val l = effort(left)
    val r = effort(right)
    return if (l == null && r != null) right else if (l != null && r != null && r > l) right else left
}

/**
 * The sets of [session] that a card is read against, in order: set k of the card is compared with item k of this list.
 * A paired card takes [side]'s sets (a session of ordinary sets gives both halves its set k); an ordinary card takes the
 * session's sets as they are, or, when last time was left and right, one set per round, the heavier side's.
 */
fun carriedSets(session: Session?, paired: Boolean, side: Side): List<SetItem> = when {
    session == null -> emptyList()
    paired -> session.forSide(side).sets
    session.paired -> roundsOfSession(session).mapNotNull(::heavierSide)
    else -> session.sets
}
