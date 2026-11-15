package clean.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The view a progress photo is taken from. */
enum class Pose(val key: String, val label: String) {
    FRONT("front", "Front"), SIDE("side", "Side"), BACK("back", "Back");

    companion object {
        fun of(key: String): Pose? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A progress photo. The file name is the whole record: `yyyyMMdd-HHmmss_<pose>.jpg`, so the moment and the pose are
 * read from it and changing either renames the file. Rules: docs/data-format.md, "Progress photos".
 */
data class Photo(val name: String, val at: LocalDateTime, val pose: Pose) {
    val day: LocalDate get() = at.toLocalDate()
}

private val PHOTO_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

fun photoName(at: LocalDateTime, pose: Pose): String = "${at.format(PHOTO_STAMP)}_${pose.key}.jpg"

/** The photo a file name stands for, or null for anything else in the folder (a file with no known pose, or a stamp that is not a moment). */
fun parsePhoto(name: String): Photo? {
    val parts = name.removeSuffix(".jpg").split('_')
    if (!name.endsWith(".jpg") || parts.size != 2) return null
    val pose = Pose.of(parts[1]) ?: return null
    val at = runCatching { LocalDateTime.parse(parts[0], PHOTO_STAMP) }.getOrNull() ?: return null
    // Only the exact spelling counts, so no two names stand for one moment.
    return Photo(name, at, pose).takeIf { photoName(at, pose) == name }
}

/** The photo for [at] and [pose] under a name no one in [taken] has: a second is added until it is free. */
fun freePhoto(at: LocalDateTime, pose: Pose, taken: Set<String>): Photo {
    var moment = at.withNano(0)
    while (photoName(moment, pose) in taken) moment = moment.plusSeconds(1)
    return Photo(photoName(moment, pose), moment, pose)
}

/** Newest first. */
fun newestFirst(photos: List<Photo>): List<Photo> = photos.sortedWith(compareByDescending<Photo> { it.at }.thenBy { it.pose.ordinal })

class PhotoDay(val day: LocalDate, val photos: List<Photo>)

/** The photos of [pose] (all when null), a day at a time, the newest day first; within a day front, side, back, then by time. */
fun groupByDay(photos: List<Photo>, pose: Pose? = null): List<PhotoDay> =
    photos.filter { pose == null || it.pose == pose }.groupBy { it.day }.entries.sortedByDescending { it.key }
        .map { (day, list) -> PhotoDay(day, list.sortedWith(compareBy<Photo> { it.pose.ordinal }.thenBy { it.at })) }

/** The oldest and the newest of [pose] (of all when null); null with fewer than two. */
fun defaultPair(photos: List<Photo>, pose: Pose?): Pair<Photo, Photo>? {
    val pool = photos.filter { pose == null || it.pose == pose }.sortedBy { it.at }
    return if (pool.size < 2) null else pool.first() to pool.last()
}

/** The pose to compare when [asked] for one: that pose if two photos have it, else the pose with the most (two at least), else null for all of them. */
fun comparePose(photos: List<Photo>, asked: Pose?): Pose? {
    val counts = Pose.entries.associateWith { p -> photos.count { it.pose == p } }
    if (asked != null && counts.getValue(asked) >= 2) return asked
    return counts.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
}

/** `14 weeks apart`, `5 days apart`, `1 year 3 months apart`, `Same day`. */
fun apartText(a: LocalDate, b: LocalDate): String {
    val days = Math.abs(ChronoUnit.DAYS.between(a, b))
    return when {
        days == 0L -> "Same day"
        days < 14 -> "$days ${if (days == 1L) "day" else "days"} apart"
        days < 365 -> "${days / 7} weeks apart"
        else -> {
            val years = days / 365
            val months = (days % 365) / 30
            "$years ${if (years == 1L) "year" else "years"}${if (months > 0) " $months ${if (months == 1L) "month" else "months"}" else ""} apart"
        }
    }
}

/** `Today`, `Yesterday`, `5 days ago`, `3 weeks ago`, `4 months ago`, `2 years ago`. */
fun agoText(day: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        days < 730 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}

/** `98 days after the first`, with `First photo` for the first one. */
fun afterFirstText(first: LocalDate, day: LocalDate): String {
    val days = ChronoUnit.DAYS.between(first, day)
    return if (days <= 0) "First photo" else "$days ${if (days == 1L) "day" else "days"} after the first"
}

private val EXIF_STAMP = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

/** An EXIF `DateTimeOriginal` (`2026:10:12 09:30:00`) as a moment; null when it is missing or not one (cameras write `0000:00:00 00:00:00`). */
fun parseExifDate(text: String?): LocalDateTime? = text?.trim()?.let { runCatching { LocalDateTime.parse(it, EXIF_STAMP) }.getOrNull() }
