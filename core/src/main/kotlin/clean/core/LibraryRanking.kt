package clean.core

/**
 * One exercise as the Library ranks it: what search reads of it (its name, other names and extra words, see [SearchTarget]) and what
 * the user has done with it. [sessions] is every logged session, [recentSessions] those in the last 12 weeks, [lastDate] the newest
 * (empty when never logged). Built once per list, off the main thread.
 */
class LibraryEntry(
    val name: String, otherNames: List<String> = emptyList(), extra: List<String> = emptyList(),
    val favourite: Boolean = false, val sessions: Int = 0, val recentSessions: Int = 0, val lastDate: String = "",
) {
    internal val target = SearchTarget(name, otherNames, extra)
    internal val nameKey = name.lowercase()
    val performed get() = sessions > 0
}

/** A search's answer in its two groups: "You've done these" (favourites, then performed) and "More in the library". Each is in order. */
class LibraryResult(val done: List<LibraryEntry>, val more: List<LibraryEntry>)

private class Hit(val entry: LibraryEntry, val tier: Int)

private val BY_RECENCY = compareBy<Hit> { it.tier }.thenByDescending { it.entry.recentSessions }.thenByDescending { it.entry.lastDate }
    .thenByDescending { it.entry.sessions }.thenBy { it.entry.nameKey }

private val DONE_ORDER = compareByDescending<Hit> { it.entry.favourite }.then(BY_RECENCY)

private val MORE_ORDER = compareBy<Hit> { it.tier }.thenBy { it.entry.nameKey }

/**
 * The Library's one ranked list for what was typed (docs/design.md, "Exercise search"). Two groups: favourites and exercises the user
 * has performed come first, then the rest of the library. In the first, favourites lead; then within favourites and within the
 * performed the closer match ([SearchQuery.rank]) wins, then the one trained more in the last 12 weeks, then the newest, then the one
 * done more often. So a typing slip on a performed exercise beats an exact match on one never done. In the second, the closer match,
 * then A to Z. [keep] leaves out what a filter excludes; [stillWanted] is called now and then and may throw to stop a search that was
 * replaced.
 */
fun rankLibrary(search: SearchQuery, entries: List<LibraryEntry>, keep: (LibraryEntry) -> Boolean = { true }, stillWanted: () -> Unit = {}): LibraryResult {
    val done = ArrayList<Hit>()
    val more = ArrayList<Hit>()
    for ((i, e) in entries.withIndex()) {
        if (i % 128 == 0) stillWanted()
        if (!keep(e)) continue
        val tier = search.rank(e.target) ?: continue
        if (e.favourite || e.performed) done += Hit(e, tier) else more += Hit(e, tier)
    }
    done.sortWith(DONE_ORDER)
    more.sortWith(MORE_ORDER)
    return LibraryResult(done.map { it.entry }, more.map { it.entry })
}

/** [rankLibrary] for a typed [query] (for one-off use and tests, as it reads the query each time). */
fun rankLibrary(query: String, entries: List<LibraryEntry>): LibraryResult = rankLibrary(SearchQuery(query), entries)

/**
 * The name closest to what was typed, for "Did you mean …?" when nothing matched, or null when none is reasonably close (about one
 * edit in four letters, at most three). What was typed is compared run together with the whole name, each of its words and each
 * start of it made of whole words ("bnech pres" with "Bench Press"); of equal distance the exercise done most wins, then the shorter name.
 */
fun nearestName(typed: String, entries: List<LibraryEntry>): String? {
    val q = searchWords(typed).joinToString("")
    if (q.length < 3) return null
    val limit = when { q.length >= 9 -> 3; q.length >= 5 -> 2; else -> 1 }
    var best: LibraryEntry? = null
    var bestDistance = limit + 1
    for (e in entries) {
        val d = distanceToName(q, e.target, limit)
        if (d > limit) continue
        val b = best
        if (b == null || d < bestDistance || d == bestDistance && (e.sessions > b.sessions || e.sessions == b.sessions && e.name.length < b.name.length)) {
            best = e
            bestDistance = d
        }
    }
    return best?.name
}

/** The least distance from [q] to the name run together, to one of its words or to the first words of it run together; more than [limit] when none is close. */
private fun distanceToName(q: String, target: SearchTarget, limit: Int): Int {
    var best = limit + 1
    val words = target.nameWords
    val prefix = StringBuilder()
    for (w in words) {
        prefix.append(w)
        // A word on its own, or the words so far.
        if (Math.abs(w.length - q.length) <= limit) best = minOf(best, editDistance(q, w, limit))
        if (prefix.length != w.length && Math.abs(prefix.length - q.length) <= limit) best = minOf(best, editDistance(q, prefix.toString(), limit))
    }
    return best
}

/** The edits (insert, delete, change, swap two neighbours) between [a] and [b]; any number above [max] means "more than max". */
internal fun editDistance(a: String, b: String, max: Int): Int {
    if (Math.abs(a.length - b.length) > max) return max + 1
    var older = IntArray(b.length + 1)
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        var rowMin = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, older[j - 2] + 1)
            cur[j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return max + 1
        val t = older; older = prev; prev = cur; cur = t
    }
    return prev[b.length]
}
