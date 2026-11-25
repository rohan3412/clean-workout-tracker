@file:OptIn(kotlin.time.ExperimentalTime::class)

package clean.app

import clean.app.platform.Platform
import clean.core.time.YearMonth
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/*
 * Dates, numbers and the clock as the phone shows them. The phone's language comes through the Platform that AppModel is given
 * (set here once, so the plain functions below, which are not in a composition, can reach it).
 */

object Formats {
    lateinit var platform: Platform
    val ready: Boolean get() = ::platform.isInitialized
}

/** The wall clock, in milliseconds since 1970. */
fun currentMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** [millis] as the phone's local date and time. */
fun localDateTimeOf(millis: Long): LocalDateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

fun localTimeOf(millis: Long): LocalTime = localDateTimeOf(millis).time

/** A date pattern shown in the phone's language, written with java.time's letters (`EEE d MMM`). */
class LocalePattern(val pattern: String)

fun LocalDate.format(p: LocalePattern): String = Formats.platform.formatLocalized(p.pattern, this)
fun LocalDateTime.format(p: LocalePattern): String = Formats.platform.formatLocalized(p.pattern, this)
fun LocalTime.format(p: LocalePattern): String = Formats.platform.formatLocalized(p.pattern, LocalDateTime(LocalDate(1970, 1, 1), this))
fun YearMonth.format(p: LocalePattern): String = Formats.platform.formatLocalized(p.pattern, atDay(1))

/** The phone's best pattern for [skeleton] (`dMMMM`), applied to [day]. */
fun bestDayFormat(skeleton: String, day: LocalDate): String = Formats.platform.formatBestPattern(skeleton, day)

fun DayOfWeek.localName(full: Boolean = true): String = Formats.platform.weekdayName(this, full)
fun Month.localName(full: Boolean = true): String = Formats.platform.monthName(this, full)

/** [value] with the phone's thousands separator. */
fun thousands(value: Long): String = Formats.platform.formatThousands(value)

/** [value] with a comma between thousands whatever the phone's language. */
fun thousandsComma(value: Long): String {
    val digits = (if (value < 0) -value else value).toString()
    val out = StringBuilder()
    for ((i, c) in digits.withIndex()) { if (i > 0 && (digits.length - i) % 3 == 0) out.append(','); out.append(c) }
    return (if (value < 0) "-" else "") + out
}

/** `%.{decimals}f` in the phone's language, or with a plain dot when [phoneLocale] is false. */
fun fixed(value: Double, decimals: Int, phoneLocale: Boolean = true): String = Formats.platform.formatFixed(value, decimals, phoneLocale)

/** java's `Math.round`: halves go up (Kotlin's `roundToLong` goes to even). */
fun roundHalfUp(value: Double): Long = clean.core.jRound(value)
fun roundHalfUp(value: Float): Int = clean.core.jRound(value)
