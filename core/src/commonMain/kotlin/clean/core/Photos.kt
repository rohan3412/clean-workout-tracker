package clean.core

import clean.core.time.*
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

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

private val PHOTO_STAMP = DatePattern("yyyyMMdd-HHmmss")

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
    val days = abs(ChronoUnit.DAYS.between(a, b))
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

private val EXIF_STAMP = DatePattern("yyyy:MM:dd HH:mm:ss")

/** An EXIF `DateTimeOriginal` (`2026:10:12 09:30:00`) as a moment; null when it is missing or not one (cameras write `0000:00:00 00:00:00`). */
fun parseExifDate(text: String?): LocalDateTime? = text?.trim()?.let { runCatching { LocalDateTime.parse(it, EXIF_STAMP) }.getOrNull() }

// --- One Photos section over the two stores (Round 39) ---

/**
 * One picture of the Photos page: a progress photo (it has a pose) or a snapshot (a moment, no pose). A layer over the two stores, which stay as they were:
 * progress photos in `photos/<profile>/`, snapshots in `moments/<profile>/`. The two kinds never share a file name, so the name says which it is.
 */
data class Pic(val photo: Photo?, val moment: Moment?) {
    val name: String get() = photo?.name ?: moment!!.name
    val at: LocalDateTime get() = photo?.at ?: moment!!.at
    val day: LocalDate get() = at.toLocalDate()
    val pose: Pose? get() = photo?.pose
    val snapshot: Boolean get() = photo == null

    /** The small label on a picture: "Front", "Side", "Back" or "Snapshot". */
    val label: String get() = pose?.label ?: "Snapshot"
}

fun Photo.asPic() = Pic(this, null)
fun Moment.asPic() = Pic(null, this)

/** Both stores together, newest first (a progress photo before a snapshot of the same second). */
fun mergedPics(photos: List<Photo>, moments: List<Moment>): List<Pic> =
    (photos.map { it.asPic() } + moments.map { it.asPic() }).sortedWith(compareByDescending<Pic> { it.at }.thenBy { it.snapshot }.thenBy { it.pose?.ordinal ?: 0 }.thenBy { it.name })

/** What the Photos page shows: everything, the progress photos (all or one pose) or the snapshots. The names of the poses are those of [Pose], so a stored old pose filter still reads. */
enum class PhotoFilter(val label: String, val pose: Pose? = null) {
    ALL("All"), PROGRESS("Progress"), SNAPSHOTS("Snapshots"),
    FRONT("Front", Pose.FRONT), SIDE("Side", Pose.SIDE), BACK("Back", Pose.BACK);

    /** The progress photos only, of one pose or of any. */
    val progress: Boolean get() = this == PROGRESS || pose != null

    fun matches(pic: Pic): Boolean = when (this) {
        ALL -> true
        PROGRESS -> !pic.snapshot
        SNAPSHOTS -> pic.snapshot
        else -> pic.pose == pose
    }

    companion object {
        fun of(pose: Pose?): PhotoFilter = entries.firstOrNull { it.pose == pose && pose != null } ?: PROGRESS
    }
}

class PicDay(val day: LocalDate, val pics: List<Pic>)

/** The pictures that [filter] lets through, a day at a time, newest day first; within a day the progress photos (front, side, back), then the snapshots by time. */
fun groupPicsByDay(pics: List<Pic>, filter: PhotoFilter = PhotoFilter.ALL): List<PicDay> =
    pics.filter { filter.matches(it) }.groupBy { it.day }.entries.sortedByDescending { it.key }
        .map { (day, list) -> PicDay(day, list.sortedWith(compareBy<Pic> { it.snapshot }.thenBy { it.pose?.ordinal ?: 0 }.thenBy { it.at })) }

/** Compare is for progress photos of one pose: offered under Progress or a pose, when two photos of the pose (any pose, for Progress) are there to compare. */
fun compareOffered(pics: List<Pic>, filter: PhotoFilter): Boolean {
    if (!filter.progress) return false
    val photos = pics.mapNotNull { it.photo }
    return if (filter.pose != null) photos.count { it.pose == filter.pose } >= 2 else comparePose(photos, null) != null
}

/**
 * The quiet line under Profile's fan: `42 photos · 18 progress · since Apr 2026 · last 3 days ago`. The count of progress photos is left out when there are none, the
 * time is relative under [RELATIVE_DAYS] days ([relativeTime], Round 36) and a date after that.
 */
fun photosSummary(pics: List<Pic>, now: LocalDateTime): String {
    if (pics.isEmpty()) return ""
    val progress = pics.count { !it.snapshot }
    val first = pics.minOf { it.at }
    val last = pics.maxOf { it.at }
    val lastText = relativeTime(last, now)?.let { if (it == "just now") "last one just now" else "last $it" }
        ?: "last ${last.dayOfMonth} ${last.month.englishName(false)}"
    return listOfNotNull(
        "${pics.size} ${if (pics.size == 1) "photo" else "photos"}",
        if (progress > 0) "$progress progress" else null,
        "since ${first.month.englishName(false)} ${first.year}",
        lastText,
    ).joinToString(" · ")
}

/** `6 weeks between your first and latest front photo`, for the pose with the most progress photos (two at least) over more than a day; null otherwise. */
fun photosSpan(pics: List<Pic>): String? {
    val photos = pics.mapNotNull { it.photo }
    val (first, last) = defaultPair(photos, comparePose(photos, null)) ?: return null
    val apart = apartText(first.day, last.day)
    if (apart == "Same day") return null
    return "${apart.removeSuffix(" apart")} between your first and latest ${first.pose.label.lowercase()} photo"
}

/**
 * Where a picture goes when it is given [at] and [pose] (null: a snapshot): the same kind keeps its file's own place, a change of kind is a rename from one folder to the other.
 * [takenPhotos] and [takenMoments] are the names in the two folders; [newId] makes a snapshot's short id.
 */
fun refiled(pic: Pic, at: LocalDateTime, pose: Pose?, takenPhotos: Set<String>, takenMoments: Set<String>, newId: () -> String): Pic {
    if (pose != null) return freePhoto(at, pose, takenPhotos - pic.name).asPic()
    val others = takenMoments - pic.name
    var moment = momentOf(at, pic.moment?.id ?: newId())
    while (moment.name in others) moment = momentOf(at, newId())
    return moment.asPic()
}
