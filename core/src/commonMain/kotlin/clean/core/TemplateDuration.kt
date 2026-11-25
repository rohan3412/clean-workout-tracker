package clean.core

/** How many of a template's latest sessions its estimated length is taken from. */
const val DURATION_SESSIONS = 5

/**
 * How long each workout name usually takes, in seconds: the median of its latest [DURATION_SESSIONS] sessions that lasted a minute or
 * more (a workout logged with no time, or started and dropped at once, says nothing). A template is matched by its name, as its page
 * matches its workouts. Names with no such session are left out.
 */
fun templateDurations(workouts: List<Workout>): Map<String, Long> =
    workouts.filter { it.durationSeconds >= 60 }.groupBy { it.name }.mapValues { (_, list) ->
        val latest = list.sortedByDescending { it.date }.take(DURATION_SESSIONS).map { it.durationSeconds }.sorted()
        val mid = latest.size / 2
        if (latest.size % 2 == 1) latest[mid] else (latest[mid - 1] + latest[mid]) / 2
    }

/** `~55 min`, `~1 h 20 min`: an estimate rounded to five minutes, never under five. */
fun estimateText(seconds: Long): String {
    val minutes = maxOf(5L, jRound(seconds / 300.0) * 5)
    return if (minutes >= 60) "~${minutes / 60} h" + (if (minutes % 60 != 0L) " ${minutes % 60} min" else "") else "~$minutes min"
}
