package clean.core

/** The longest a workout tag can be; tags are short words, not notes. */
const val TAG_MAX = 24

/** A tag as typed, tidied for keeping: spaces trimmed and runs of them made one, cut at [TAG_MAX]. Empty when nothing is left. */
fun cleanTag(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(TAG_MAX).trim()

/**
 * [text] spelled as a tag already in use when one matches ignoring case (so "deload" does not become a second tag
 * beside "Deload"), else [cleanTag] of it.
 */
fun spellTag(text: String, known: Collection<String>): String {
    val clean = cleanTag(text)
    return known.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
}

/**
 * The tags in use, most used first (then alphabetical). [tagOf] is each workout's tag by its date; only workouts in
 * [existing] count, so a tag whose workout was deleted is not suggested.
 */
fun tagsByUse(tagOf: Map<String, String>, existing: Set<String>): List<String> =
    tagOf.filter { (date, tag) -> tag.isNotEmpty() && date in existing }.values.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() }).map { it.key }

/**
 * The workouts the statistics, records and streaks are worked out from: all of them except those whose tag is switched to
 * "Leave out of stats" ([leaveOut]). The same list, not a copy, when nothing is left out.
 */
fun statWorkouts(all: List<Workout>, tagOf: Map<String, String>, leaveOut: Set<String>): List<Workout> =
    if (leaveOut.isEmpty() || tagOf.isEmpty()) all else all.filter { w -> tagOf[w.date]?.let { it in leaveOut } != true }
