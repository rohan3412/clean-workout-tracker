package clean.core

import kotlinx.datetime.isoDayNumber
import clean.core.time.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The longest a number typed on the weigh-in pad can be, in characters. */
private const val PAD_LENGTH = 6

/**
 * What the number pad's text becomes after [key]: a digit, `.`, or `back`. With [fresh] (nothing pressed yet since the pad opened) a digit or the point
 * starts again and back clears. One point at most, no leading zeros ("05" is "5"), and at most [PAD_LENGTH] characters.
 */
fun padKey(text: String, key: String, fresh: Boolean): String {
    val base = if (fresh && key != "back") "" else text
    return when {
        key == "back" -> if (fresh) "" else base.dropLast(1)
        key == "." -> if ('.' in base) base else (base.ifEmpty { "0" } + ".").take(PAD_LENGTH)
        key.length == 1 && key[0].isDigit() -> when {
            base == "0" -> key
            base.length >= PAD_LENGTH -> base
            else -> base + key
        }
        else -> base
    }
}

/**
 * The value typed on the pad as [text] (a comma counts as the point), kept inside [min]..[max] and rounded to the ruler's step of `1 / perUnit`;
 * null when it is not a number or is empty.
 */
fun padValue(text: String, min: Double, max: Double, perUnit: Int): Double? {
    val typed = text.trim().replace(',', '.').trimEnd('.').takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return null
    return jRound(typed.coerceIn(min, max) * perUnit) / perUnit.toDouble()
}

/** One of the last seven days offered as a chip: its [day] and its words ("Today", "Yesterday", "Mon 16"). */
data class DayChip(val day: LocalDate, val label: String)

/** The last seven days, today first: "Today", "Yesterday", then the weekday and the day of the month. */
fun recentDays(today: LocalDate): List<DayChip> = (0L until 7L).map { back ->
    val day = today.minusDays(back)
    DayChip(day, when (back) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> day.dayOfWeek.englishName(false) + " " + day.day
    })
}

/** A month as rows of seven days from Monday: null before the first and after the last, so every row has seven cells. */
fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
    val cells = ArrayList<LocalDate?>()
    repeat(month.atDay(1).dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber) { cells += null }
    for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

/** Whether this week's pill ("Last week: 3 of 3") has yet to be shown: [shown] is the Monday it was last shown for, if ever. */
fun weekPillDue(shown: String?, today: LocalDate): Boolean = shown != weekStart(today).toString()

/**
 * The words of the pill for the first open of a week, or null for nothing: last week's workouts against its target, with a tick when it was met.
 * Nothing when last week is not in [results] (before the first workout), or it had no workouts and the profile never set a target ([ownTarget]).
 */
fun lastWeekLine(results: List<WeekResult>, today: LocalDate, ownTarget: Boolean): String? {
    val last = results.firstOrNull { it.weekStart == weekStart(today).minusWeeks(1) } ?: return null
    if (last.count == 0 && !ownTarget) return null
    return "Last week: ${last.count} of ${last.target}" + if (last.met) " ✓" else ""
}
