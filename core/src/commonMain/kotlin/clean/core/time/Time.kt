package clean.core.time

import kotlinx.datetime.number
import kotlinx.datetime.isoDayNumber
import clean.core.currentLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/*
 * java.time's method names on kotlinx-datetime's types, so the date code reads as it always did and runs on iOS too.
 * Everything here is plain calendar arithmetic: no zone, no locale. Zone work goes through kotlinx's TimeZone.
 */

// --- Now ---

fun LocalDateTime.Companion.now(): LocalDateTime = currentLocalDateTime()
fun LocalDate.Companion.now(): LocalDate = currentLocalDateTime().date
fun LocalTime.Companion.now(): LocalTime = currentLocalDateTime().time
fun YearMonth.Companion.now(): YearMonth = YearMonth.from(LocalDate.now())
val DayOfWeek.value: Int get() = ordinal + 1

// --- Making ---

fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)
fun LocalDate.Companion.ofEpochDay(day: Long): LocalDate = LocalDate.fromEpochDays(day)
fun LocalDateTime.Companion.of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalDateTime =
    LocalDateTime(year, month, day, hour, minute, second, nano)
fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
fun LocalTime.Companion.of(hour: Int, minute: Int, second: Int = 0): LocalTime = LocalTime(hour, minute, second)

// --- LocalDate ---

val LocalDate.monthValue: Int get() = month.number
val LocalDateTime.monthValue: Int get() = month.number
fun LocalDate.toEpochDay(): Long = toEpochDays()
fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
fun LocalDate.isEqual(other: LocalDate): Boolean = this == other
fun LocalDate.plusDays(n: Long): LocalDate = LocalDate.fromEpochDays(toEpochDays() + n)
fun LocalDate.minusDays(n: Long): LocalDate = plusDays(-n)
fun LocalDate.plusWeeks(n: Long): LocalDate = plusDays(n * 7)
fun LocalDate.minusWeeks(n: Long): LocalDate = plusDays(-n * 7)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Long): LocalDate = plus(-n, DateTimeUnit.MONTH)
fun LocalDate.plusYears(n: Long): LocalDate = plus(n, DateTimeUnit.YEAR)
fun LocalDate.minusYears(n: Long): LocalDate = plus(-n, DateTimeUnit.YEAR)
fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, monthValue, day)
fun LocalDate.withDayOfYear(day: Int): LocalDate = LocalDate(year, 1, 1).plusDays(day - 1L).also { require(it.year == year) { "Invalid day of year: $day" } }
fun LocalDate.lengthOfMonth(): Int = monthLength(year, monthValue)
fun LocalDate.lengthOfYear(): Int = if (isLeapYear(year)) 366 else 365
fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))
fun LocalDate.atTime(hour: Int, minute: Int, second: Int = 0): LocalDateTime = LocalDateTime(this, LocalTime(hour, minute, second))

fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
fun monthLength(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

// --- LocalDateTime ---

private const val DAY_SECONDS = 86_400L

fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other
fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other

/** Seconds since 1970-01-01T00:00 read as UTC (java's `toEpochSecond(ZoneOffset.UTC)`). */
fun LocalDateTime.toEpochSecondUtc(): Long = date.toEpochDays() * DAY_SECONDS + hour * 3600L + minute * 60L + second

private fun fromEpochSecondUtc(seconds: Long, nano: Int): LocalDateTime {
    val day = seconds.floorDiv(DAY_SECONDS)
    val of = (seconds - day * DAY_SECONDS).toInt()
    return LocalDateTime(LocalDate.fromEpochDays(day), LocalTime(of / 3600, of / 60 % 60, of % 60, nano))
}

fun LocalDateTime.plusSeconds(n: Long): LocalDateTime = fromEpochSecondUtc(toEpochSecondUtc() + n, nanosecond)
fun LocalDateTime.minusSeconds(n: Long): LocalDateTime = plusSeconds(-n)
fun LocalDateTime.plusMinutes(n: Long): LocalDateTime = plusSeconds(n * 60)
fun LocalDateTime.minusMinutes(n: Long): LocalDateTime = plusSeconds(-n * 60)
fun LocalDateTime.plusHours(n: Long): LocalDateTime = plusSeconds(n * 3600)
fun LocalDateTime.minusHours(n: Long): LocalDateTime = plusSeconds(-n * 3600)
fun LocalDateTime.plusDays(n: Long): LocalDateTime = LocalDateTime(date.plusDays(n), time)
fun LocalDateTime.minusDays(n: Long): LocalDateTime = plusDays(-n)
fun LocalDateTime.plusMonths(n: Long): LocalDateTime = LocalDateTime(date.plusMonths(n), time)
fun LocalDateTime.minusMonths(n: Long): LocalDateTime = plusMonths(-n)
fun LocalDateTime.plusYears(n: Long): LocalDateTime = LocalDateTime(date.plusYears(n), time)
fun LocalDateTime.minusYears(n: Long): LocalDateTime = plusYears(-n)
fun LocalDateTime.plusWeeks(n: Long): LocalDateTime = plusDays(n * 7)
fun LocalDateTime.minusWeeks(n: Long): LocalDateTime = plusDays(-n * 7)
fun LocalDateTime.withNano(nano: Int): LocalDateTime = LocalDateTime(date, LocalTime(hour, minute, second, nano))
fun LocalDateTime.withHour(h: Int): LocalDateTime = LocalDateTime(date, LocalTime(h, minute, second, nanosecond))
fun LocalDateTime.withMinute(m: Int): LocalDateTime = LocalDateTime(date, LocalTime(hour, m, second, nanosecond))
fun LocalDateTime.withSecond(s: Int): LocalDateTime = LocalDateTime(date, LocalTime(hour, minute, s, nanosecond))

// --- Between ---

/** java's `Duration.between` for local date-times, as far as Clean uses it. */
class Duration private constructor(val seconds: Long) {
    fun toMinutes(): Long = seconds / 60
    fun toHours(): Long = seconds / 3600
    fun toDays(): Long = seconds / 86_400
    fun toMillis(): Long = seconds * 1000
    fun toSeconds(): Long = seconds
    companion object {
        fun between(from: LocalDateTime, to: LocalDateTime): Duration = Duration(to.toEpochSecondUtc() - from.toEpochSecondUtc())
    }
}

/** java's `ChronoUnit.DAYS.between` and `MONTHS.between` for dates. */
enum class ChronoUnit {
    DAYS, WEEKS, MONTHS;

    fun between(from: LocalDate, to: LocalDate): Long = when (this) {
        DAYS -> to.toEpochDay() - from.toEpochDay()
        WEEKS -> (to.toEpochDay() - from.toEpochDay()) / 7
        MONTHS -> {
            var months = (to.year * 12L + to.monthValue) - (from.year * 12L + from.monthValue)
            if (months > 0 && to.day < from.day) months-- else if (months < 0 && to.day > from.day) months++
            months
        }
    }
}

// --- Names, in English ---

private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** `January`, or with [full] false `Jan`. */
fun Month.englishName(full: Boolean = true): String = MONTH_NAMES[number - 1].let { if (full) it else it.take(3) }

/** `Monday`, or with [full] false `Mon`. */
fun DayOfWeek.englishName(full: Boolean = true): String = DAY_NAMES[isoDayNumber - 1].let { if (full) it else it.take(3) }

/** The Monday on or before [this] (java's `with(previousOrSame(MONDAY))`). */
fun LocalDate.previousOrSameMonday(): LocalDate = minusDays(dayOfWeek.isoDayNumber - 1L)
