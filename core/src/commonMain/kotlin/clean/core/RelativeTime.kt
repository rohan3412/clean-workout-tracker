package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDateTime

/** How old a time may be and still be said relative to now ("3 days ago"); from this on a date is shown (Round 36). */
const val RELATIVE_DAYS = 7

/**
 * [then] said relative to [now]: `just now` under a minute, `12 minutes ago`, `1 hour ago`, `5 hours ago`, `1 day ago`, `3 days ago`. Null from [RELATIVE_DAYS] days
 * on, where the caller shows the date. A time ahead of [now] (the phone's clock was set back) is `just now`.
 */
fun relativeTime(then: LocalDateTime, now: LocalDateTime): String? {
    val seconds = now.toEpochSecondUtc() - then.toEpochSecondUtc()
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> unit(minutes, "minute")
        hours < 24 -> unit(hours, "hour")
        days < RELATIVE_DAYS -> unit(days, "day")
        else -> null
    }
}

private fun unit(n: Long, word: String) = "$n $word${if (n == 1L) "" else "s"} ago"
