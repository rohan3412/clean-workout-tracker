package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * A snapshot kept on the Moments page. Like a progress photo the file name is the whole record: `yyyyMMdd-HHmmss_<id>.jpg`,
 * the moment it was taken and a short id that keeps two taken in the same second apart. Rules: docs/data-format.md, "Moments".
 */
data class Moment(val name: String, val at: LocalDateTime, val id: String) {
    val day: LocalDate get() = at.toLocalDate()
}

private val MOMENT_STAMP = DatePattern("yyyyMMdd-HHmmss")
private val MOMENT_ID = Regex("[0-9a-f]{8}")

fun momentName(at: LocalDateTime, id: String): String = "${at.format(MOMENT_STAMP)}_$id.jpg"

fun momentOf(at: LocalDateTime, id: String): Moment = at.withNano(0).let { Moment(momentName(it, id), it, id) }

/** The moment a file name stands for, or null for anything else in the folder (a half-written file, say). */
fun parseMoment(name: String): Moment? {
    val parts = name.removeSuffix(".jpg").split('_')
    if (!name.endsWith(".jpg") || parts.size != 2 || !MOMENT_ID.matches(parts[1])) return null
    val at = runCatching { LocalDateTime.parse(parts[0], MOMENT_STAMP) }.getOrNull() ?: return null
    return momentOf(at, parts[1]).takeIf { it.name == name }
}

/** Newest first. */
fun newestMomentsFirst(moments: List<Moment>): List<Moment> = moments.sortedWith(compareByDescending<Moment> { it.at }.thenBy { it.id })

class MomentGroup(val title: String, val moments: List<Moment>)

/**
 * The moments under their headers, newest first: "Today", "This week" (the six days before today), then each month by its name,
 * with the year too for a month of an earlier year than [today]'s. A moment dated after [today] counts as today.
 */
fun groupMoments(moments: List<Moment>, today: LocalDate): List<MomentGroup> {
    fun title(day: LocalDate): String {
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> "Today"
            days < 7 -> "This week"
            else -> day.month.englishName() + if (day.year != today.year) " ${day.year}" else ""
        }
    }
    val groups = ArrayList<MomentGroup>()
    for ((t, list) in newestMomentsFirst(moments).groupBy { title(it.day) }.entries.sortedByDescending { it.value.first().at }) groups += MomentGroup(t, list)
    return groups
}

class MomentStats(val total: Int, val thisMonth: Int, val days: Int)

/** How many moments there are, how many are of [today]'s month (a later date counts too), and on how many different days. */
fun momentStats(moments: List<Moment>, today: LocalDate): MomentStats =
    MomentStats(moments.size, moments.count { it.day.year == today.year && it.day.month == today.month }, moments.map { it.day }.distinct().size)
