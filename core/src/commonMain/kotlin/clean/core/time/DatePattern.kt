package clean.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * A fixed date pattern, written as java's DateTimeFormatter patterns were, always in English and never from the phone's locale:
 * `yyyy` `yy` `MM` `MMM` `MMMM` `M` `dd` `d` `HH` `mm` `ss`, anything else as it stands. Output is what DateTimeFormatter gave
 * for the same pattern with Locale.ENGLISH; parsing takes the numeric patterns only (fixed widths), as java's SMART style did.
 */
class DatePattern(val pattern: String) {
    private val parts: List<Pair<Char, Int>> = buildList {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            var j = i
            while (j < pattern.length && pattern[j] == c) j++
            add(c to (j - i))
            i = j
        }
    }

    private fun pad(n: Int, width: Int): String = n.toString().padStart(width, '0')

    private fun render(date: LocalDate?, time: LocalTime?): String = buildString {
        for ((c, n) in parts) when (c) {
            'y' -> pad(date!!.year, 4).let { append(if (n == 2) it.takeLast(2) else it) }
            'M' -> when {
                n >= 4 -> append(date!!.month.englishName(true))
                n == 3 -> append(date!!.month.englishName(false))
                else -> append(pad(date!!.monthValue, n))
            }
            'd' -> append(pad(date!!.day, n))
            'H' -> append(pad(time!!.hour, n))
            'm' -> append(pad(time!!.minute, n))
            's' -> append(pad(time!!.second, n))
            else -> append(c.toString().repeat(n))
        }
    }

    fun format(at: LocalDateTime): String = render(at.date, at.time)
    fun format(day: LocalDate): String = render(day, null)
    fun format(time: LocalTime): String = render(null, time)

    /** The date-time in [text], or null when it is not one of this pattern (a day past the end of its month is moved back to it, 24:00:00 is midnight after, as java's SMART style did). */
    fun parseOrNull(text: String): LocalDateTime? {
        var at = 0
        var year = 0; var month = 1; var day = 1; var hour = 0; var minute = 0; var second = 0
        for ((c, n) in parts) {
            when (c) {
                'y', 'M', 'd', 'H', 'm', 's' -> {
                    val width = if (c == 'y') 4 else 2
                    if (n != width || at + width > text.length) return null
                    var v = 0
                    for (i in at until at + width) { val d = text[i] - '0'; if (d !in 0..9) return null; v = v * 10 + d }
                    at += width
                    when (c) { 'y' -> year = v; 'M' -> month = v; 'd' -> day = v; 'H' -> hour = v; 'm' -> minute = v; else -> second = v }
                }
                else -> {
                    if (!text.startsWith(c.toString().repeat(n), at)) return null
                    at += n
                }
            }
        }
        if (at != text.length) return null
        if (year < 1 || month !in 1..12 || day !in 1..31 || hour !in 0..24 || minute !in 0..59 || second !in 0..59) return null
        val midnightAfter = hour == 24
        if (midnightAfter && (minute != 0 || second != 0)) return null
        val date = LocalDate(year, month, minOf(day, monthLength(year, month)))
        return if (midnightAfter) LocalDateTime(date.plusDays(1), LocalTime(0, 0)) else LocalDateTime(date, LocalTime(hour, minute, second))
    }

    fun parse(text: String): LocalDateTime = parseOrNull(text) ?: throw IllegalArgumentException("Text '$text' could not be parsed as '$pattern'")
}

fun LocalDateTime.format(pattern: DatePattern): String = pattern.format(this)
fun LocalDate.format(pattern: DatePattern): String = pattern.format(this)
fun LocalTime.format(pattern: DatePattern): String = pattern.format(this)
fun LocalDateTime.Companion.parse(text: String, pattern: DatePattern): LocalDateTime = pattern.parse(text)

/** A year and a month, as java's YearMonth. */
data class YearMonth(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    init { require(monthValue in 1..12) { "Invalid month: $monthValue" } }
    val month: Month get() = Month(monthValue)
    fun atDay(day: Int): LocalDate = LocalDate(year, monthValue, day)
    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun plusMonths(n: Long): YearMonth = (year * 12L + monthValue - 1 + n).let { YearMonth(it.floorDiv(12).toInt(), (it.mod(12L)).toInt() + 1) }
    fun minusMonths(n: Long): YearMonth = plusMonths(-n)
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.monthValue })
    override fun toString(): String = "${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}"
    companion object {
        fun of(year: Int, month: Int) = YearMonth(year, month)
        fun from(day: LocalDate) = YearMonth(day.year, day.monthValue)
        /** `2026-11`; throws on anything else. */
        fun parse(text: String): YearMonth {
            val m = Regex("([0-9]{4})-([0-9]{2})").matchEntire(text) ?: throw IllegalArgumentException("Text '$text' could not be parsed as a year and month")
            return YearMonth(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
    }
}
