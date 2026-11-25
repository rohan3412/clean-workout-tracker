package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate

/** The 10th, 25th, 50th, 100th, 150th and 200th workout, then every 100th, are the ones the summary says a word about. */
fun isRoundWorkout(n: Int): Boolean = n == 10 || n == 25 || n == 50 || n == 100 || n == 150 || n == 200 || (n > 200 && n % 100 == 0)

/** `1st`, `2nd`, `11th`, `50th`, `101st`. */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** "Your 50th workout" for a round-number workout counted in its profile, else null. */
fun roundWorkoutLine(n: Int): String? = if (isRoundWorkout(n)) "Your ${ordinal(n)} workout" else null

/**
 * The months the activity calendar shows, from [a] to [b]: `Mar`, `Mar – Jul`, or with the year when the view is not wholly inside [thisYear]
 * or takes in a January (`Jan 2026`, `Dec 2025 – Feb 2026`). Views in two different years always name both years.
 */
fun monthsInView(a: LocalDate, b: LocalDate, thisYear: Int): String {
    val month = DatePattern("MMM")
    val full = DatePattern("MMM yyyy")
    if (a.year != b.year) return "${a.format(full)} – ${b.format(full)}"
    val sameMonth = a.monthValue == b.monthValue
    val year = a.year != thisYear || a.monthValue == 1
    return when {
        sameMonth -> a.format(if (year) full else month)
        year -> "${a.format(month)} – ${b.format(full)}"
        else -> "${a.format(month)} – ${b.format(month)}"
    }
}

/**
 * The milestones in [earned] that have not been glinted yet, given the ids already [seen]. With nothing stored yet ([seen] null) everything earned so
 * far counts as seen, so a first run of the feature does not glint the whole shelf.
 */
fun unglinted(earned: Collection<String>, seen: Set<String>?): List<String> = if (seen == null) emptyList() else earned.filter { it !in seen }

/**
 * How many whole years ago [first] (the day of the first logged workout) is, when [today] is its anniversary (same month and day, at least one
 * year on), else null. A first workout on 29 February has its anniversary on 28 February in the years that have no 29th.
 */
fun anniversaryYears(first: LocalDate, today: LocalDate): Int? {
    val years = today.year - first.year
    if (years < 1) return null
    val sameDay = first.monthValue == today.monthValue && first.day == today.day
    val leapDay = first.monthValue == 2 && first.day == 29 && today.monthValue == 2 && today.day == 28 && !isLeapYear(today.year)
    return if (sameDay || leapDay) years else null
}

/** "1 year of training today", "3 years of training today" on the anniversary of [first], else null. */
fun anniversaryLine(first: LocalDate?, today: LocalDate): String? {
    val years = first?.let { anniversaryYears(it, today) } ?: return null
    return "$years ${if (years == 1) "year" else "years"} of training today"
}
