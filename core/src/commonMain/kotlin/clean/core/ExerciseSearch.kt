package clean.core

import kotlin.math.abs

/**
 * Finding an exercise by what is typed. Every typed word must match somewhere, in any order, ignoring case, brackets, hyphens
 * and other punctuation; gym shorthand ("db", "ohp", "tris") stands for its words; a word of four letters or more may be one
 * letter off (two from eight); and a word that is not in the name may be in the exercise's other names, its main muscles or its
 * equipment. Rules in `docs/design.md`, "Exercise search".
 */

/** Lower case, split at every character that is not a letter or a digit. */
fun searchWords(text: String): List<String> = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

/** Shorthand and other words for a typed word, each a phrase whose words must all match. The typed word itself is always tried too. */
private val SYNONYMS: Map<String, List<String>> = mapOf(
    "db" to listOf("dumbbell"), "dbs" to listOf("dumbbell"), "bb" to listOf("barbell"), "kb" to listOf("kettlebell"),
    "sm" to listOf("smith"), "bw" to listOf("bodyweight"), "ez" to listOf("ez bar"), "hs" to listOf("hammer strength"),
    "ohp" to listOf("overhead press", "shoulder press"), "rdl" to listOf("romanian deadlift"), "sldl" to listOf("stiff leg deadlift"),
    "bp" to listOf("bench press"), "lp" to listOf("leg press"), "cg" to listOf("close grip"), "wg" to listOf("wide grip"),
    "inc" to listOf("incline"), "dec" to listOf("decline"), "ext" to listOf("extension"), "ext." to listOf("extension"),
    "tri" to listOf("triceps"), "tris" to listOf("triceps"), "tricep" to listOf("triceps"),
    "bi" to listOf("biceps"), "bis" to listOf("biceps"), "bicep" to listOf("biceps"),
    "lat" to listOf("lateral", "lats"), "lats" to listOf("lat"),
    "pec" to listOf("chest"), "pecs" to listOf("chest"), "delt" to listOf("shoulders"), "delts" to listOf("shoulders", "delt"),
    "quad" to listOf("quads"), "ham" to listOf("hamstrings"), "hams" to listOf("hamstrings"), "hammies" to listOf("hamstrings"),
    "glute" to listOf("glutes"), "calf" to listOf("calves"), "calves" to listOf("calf"), "ab" to listOf("abs"),
    "military" to listOf("overhead"), "overhead" to listOf("shoulder"), "shoulder" to listOf("overhead"),
    // Lifts by their initials and the spellings people use for them.
    "ghr" to listOf("glute ham raise"), "ht" to listOf("hip thrust"), "hipthrust" to listOf("hip thrust"), "bss" to listOf("bulgarian split squat"),
    "gm" to listOf("good morning"), "hspu" to listOf("handstand push up"), "cgbp" to listOf("close grip bench press"),
    "dl" to listOf("deadlift"), "sq" to listOf("squat"), "alt" to listOf("alternating"),
    "pullup" to listOf("pull up", "chin up"), "chinup" to listOf("chin up", "pull up"),
    "fly" to listOf("flye"), "flye" to listOf("fly"), "flyes" to listOf("fly"), "flies" to listOf("fly"),
    "pressdown" to listOf("pushdown", "push down"), "pushdown" to listOf("push down", "pressdown"),
    "skullcrusher" to listOf("skull crusher", "lying triceps extension"), "facepull" to listOf("face pull"), "tbar" to listOf("t bar"),
    "hex" to listOf("trap bar"),
)

/** Two typed words that mean one thing, and what they stand for. */
private val PHRASES: Map<Pair<String, String>, List<String>> = mapOf(
    ("pec" to "deck") to listOf("chest fly machine", "butterfly"), ("pec" to "dec") to listOf("chest fly machine", "butterfly"),
    ("skull" to "crusher") to listOf("skullcrusher", "lying triceps extension"),
    ("skull" to "crushers") to listOf("skullcrusher", "lying triceps extension"),
    ("military" to "press") to listOf("overhead press", "shoulder press"),
    ("pull" to "up") to listOf("chin up"), ("pull" to "ups") to listOf("chin up"), ("chin" to "up") to listOf("pull up"), ("chin" to "ups") to listOf("pull up"),
    ("push" to "down") to listOf("pressdown"),
)

/** What a plural typed word may be the plural of. */
private fun singulars(w: String): List<String> {
    val out = ArrayList<String>(2)
    if (w.length >= 3 && w.endsWith("ies")) out += w.dropLast(3) + "y"
    if (w.length >= 4 && w.endsWith("s")) out += w.removeSuffix("s")
    if (w.length >= 5 && w.endsWith("es")) out += w.removeSuffix("es")
    return out
}

/** One typed word (or phrase): the ways it may match, each a list of words that must all be found. */
private class Term(val ways: List<List<String>>)

private fun termsOf(query: String): List<Term> {
    val words = searchWords(query)
    val terms = mutableListOf<Term>()
    var i = 0
    while (i < words.size) {
        val phrase = words.getOrNull(i + 1)?.let { PHRASES[words[i] to it] }
        if (phrase != null) {
            terms += Term(listOf(listOf(words[i], words[i + 1])) + phrase.map(::searchWords))
            i += 2
            continue
        }
        val w = words[i]
        val ways = mutableListOf(listOf(w))
        SYNONYMS[w]?.forEach { ways += searchWords(it) }
        // "rows" finds Row, "presses" Press, "flies" Fly, "rdls" what "rdl" finds.
        for (base in singulars(w)) {
            ways += listOf(base)
            SYNONYMS[base]?.forEach { ways += searchWords(it) }
        }
        terms += Term(ways)
        i++
    }
    return terms
}

/** Where an exercise's words come from, worked out once per list: the name, its other names, and its main muscles and equipment. */
class SearchTarget(val name: String, otherNames: List<String> = emptyList(), extra: List<String> = emptyList()) {
    internal val nameWords = searchWords(name).toTypedArray()
    internal val nameCompact = nameWords.joinToString("")
    internal val otherWords = otherNames.map { searchWords(it).toTypedArray() }
    internal val otherCompacts = otherWords.map { it.joinToString("") }
    internal val extraWords = extra.flatMap(::searchWords).toHashSet()
}

/**
 * What was typed, read once per keystroke and then ranked against every exercise: its words, their shorthand and the space for the
 * typing-slip check, so ranking one exercise allocates nothing. One query is used on one thread at a time.
 */
class SearchQuery(query: String) {
    private val typed = searchWords(query)
    private val compact = typed.joinToString("")
    private val terms = termsOf(query).map { t -> t.ways.map { it.toTypedArray() } }
    // Three rows of the edit-distance table, long enough for any word.
    private val rows = Array(3) { IntArray(64) }

    val blank get() = typed.isEmpty()

    /**
     * The rank of [target], lower first, or null when it does not match: 0 the name itself, 1 a name that starts with what was typed,
     * 2 every word whole or as the start of a word of the name, 3 inside the name, 4 in another name, 5 with a typing slip, 6 only
     * through the muscles or equipment. A blank query matches everything at 0.
     */
    fun rank(target: SearchTarget): Int? {
        if (typed.isEmpty()) return 0
        if (compact == target.nameCompact) return 0
        var worst = 0
        for (ways in terms) {
            var best = Int.MAX_VALUE
            for (way in ways) {
                val tier = wayTier(way, target)
                if (tier < best) best = tier
                if (best <= 2) break
            }
            if (best == Int.MAX_VALUE) return null
            if (best > worst) worst = best
        }
        return if (worst <= 3 && target.nameCompact.startsWith(compact)) 1 else worst
    }

    /** The worst tier of the words of one way of reading a term, or [Int.MAX_VALUE] when one of them is found nowhere. */
    private fun wayTier(way: Array<String>, target: SearchTarget): Int {
        var worst = 0
        for (q in way) {
            val tier = wordTier(q, target)
            if (tier == Int.MAX_VALUE) return tier
            if (tier > worst) worst = tier
        }
        return worst
    }

    private fun wordTier(q: String, target: SearchTarget): Int {
        val inName = wordMatch(q, target.nameWords, target.nameCompact, typos = false)
        if (inName >= 0) return if (inName < 2) 2 else 3
        var other = Int.MAX_VALUE
        for (i in target.otherWords.indices) if (wordMatch(q, target.otherWords[i], target.otherCompacts[i], typos = false) >= 0) { other = 4; break }
        if (other == 4) return 4
        // Slips last, as the dearest check: the name, then the other names.
        if (wordMatch(q, target.nameWords, target.nameCompact, typos = true) >= 0) return 5
        for (i in target.otherWords.indices) if (wordMatch(q, target.otherWords[i], target.otherCompacts[i], typos = true) >= 0) return 5
        if (q in target.extraWords) return 6
        return Int.MAX_VALUE
    }

    /** 0 a whole word, 1 the start of a word, 2 inside the words run together, 3 a typing slip (only with [typos]); -1 none. */
    private fun wordMatch(q: String, words: Array<String>, compact: String, typos: Boolean): Int {
        if (!typos) {
            for (w in words) if (w == q) return 0
            for (w in words) if (w.startsWith(q)) return 1
            // "isolateral" in Iso-Lateral, "pushdown" in Push Down, "down" inside Pushdown.
            return if (compact.contains(q)) 2 else -1
        }
        if (q.length < 4) return -1
        val allowed = if (q.length >= 8) 2 else 1
        for (w in words) {
            if (within(q, w, w.length, allowed)) return 3
            if (w.length > q.length && within(q, w, q.length, allowed)) return 3
        }
        return -1
    }

    /** Whether [a] and the first [n] letters of [b] are at most [max] edits apart (insert, delete, change, or swap two neighbours). */
    private fun within(a: String, b: String, n: Int, max: Int): Boolean {
        if (abs(a.length - n) > max) return false
        if (a.length >= 63 || n >= 63) return closeEnough(a, b.substring(0, n), max)
        var older = rows[0]
        var prev = rows[1]
        var cur = rows[2]
        for (j in 0..n) prev[j] = j
        var prevMin = 0
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, older[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            // Every way on from here costs more than allowed: this row is over, and a swap from the row before adds one to at least max.
            if (rowMin > max && prevMin >= max) return false
            prevMin = rowMin
            val t = older; older = prev; prev = cur; cur = t
        }
        return prev[n] <= max
    }
}

/** Whether [a] and [b] are at most [max] edits apart (insert, delete, change, or swap two neighbours). */
internal fun closeEnough(a: String, b: String, max: Int): Boolean {
    if (abs(a.length - b.length) > max) return false
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
        d[i][j] = v
    }
    return d[a.length][b.length] <= max
}

/** The rank of [target] for [query] (see [SearchQuery.rank]); for one-off use, as it reads the query each time. */
fun searchRank(query: String, target: SearchTarget): Int? = SearchQuery(query).rank(target)
