package clean.core

/*
 * Renaming or merging an exercise rewrites the exercise-name text of stored rows, only at the user's
 * request and with an exact undo (docs/domain.md, Renaming and merging). These are the row-level rules,
 * kept free of the database so they can be tested: a rename changes the rows named exactly `from`
 * (no trimming, no case folding), and an undo puts `from` back on exactly those rows.
 */

/** A stored workout row with its database id, so a rename can be undone row by row. */
class IdRow(val id: Long, val row: WorkoutRow)

/** [rows] with every row whose exercise name is exactly [from] renamed to [to], and the ids of the rows changed. */
fun renameRows(rows: List<IdRow>, from: String, to: String): Pair<List<IdRow>, List<Long>> {
    val changed = ArrayList<Long>()
    val out = rows.map {
        if (it.row.exerciseName != from) it
        else {
            changed += it.id
            IdRow(it.id, it.row.copy(exerciseName = to))
        }
    }
    return out to changed
}

/**
 * [rows] with [from] put back on the rows [ids] changed to [to]. A row that has been deleted since is
 * not there to restore, and one that now carries another name (moved by hand since) is left alone.
 * Rows added under [to] after the rename were never in [ids] and stay.
 */
fun restoreRows(rows: List<IdRow>, ids: Collection<Long>, from: String, to: String): List<IdRow> {
    val wanted = ids.toHashSet()
    return rows.map { if (it.id in wanted && it.row.exerciseName == to) IdRow(it.id, it.row.copy(exerciseName = from)) else it }
}

/**
 * The stored weekly program with [from] named [to] in the exercises each training day was planned with,
 * or null when no day names [from] (nothing to write). A day that then names [to] twice names it once.
 */
fun renameInProgram(text: String?, from: String, to: String): String? {
    val versions = decodeProgram(text)
    if (versions.none { v -> v.days.any { it?.exercises?.contains(from) == true } }) return null
    return encodeProgram(versions.map { v ->
        v.copy(days = v.days.map { d -> d?.copy(exercises = d.exercises.map { if (it == from) to else it }.distinct()) })
    })
}
