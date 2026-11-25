package clean.core

/** One change in a version: a few words and a sentence about them. */
data class Change(val title: String, val text: String)

/** One version's changes, newest version first in the list [parseChanges] gives. [date] is as written, or null. */
data class Release(val version: String, val date: String?, val changes: List<Change>)

/**
 * The app's list of changes (`assets/changes.md`): a `## 1.0.2 (2026-11-16)` heading per version, newest first, and under it one
 * `- Title: sentence` line per change (a line without `: ` is all title). Other lines are ignored.
 */
fun parseChanges(text: String): List<Release> {
    val releases = mutableListOf<Release>()
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.startsWith("## ")) {
            val head = line.removePrefix("## ").trim()
            val version = head.substringBefore(' ')
            val date = head.substringAfter('(', "").substringBefore(')').ifEmpty { null }
            releases += Release(version, date, emptyList())
        } else if (line.startsWith("- ") && releases.isNotEmpty()) {
            val item = line.removePrefix("- ")
            val change = if (": " in item) Change(item.substringBefore(": "), item.substringAfter(": ")) else Change(item, "")
            releases[releases.lastIndex] = releases.last().let { it.copy(changes = it.changes + change) }
        }
    }
    return releases
}

/**
 * The version to show as What's new: the one [current] names, when it has changes and was not seen yet ([seen] is the last version
 * shown, null when none ever was). Never on the very first run ([firstRun]), where there is nothing the person had before.
 */
fun whatsNewDue(releases: List<Release>, current: String, seen: String?, firstRun: Boolean): Release? {
    if (firstRun || seen == current) return null
    return releases.firstOrNull { it.version == current }?.takeIf { it.changes.isNotEmpty() }
}
