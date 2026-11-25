@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.numberWithDouble
import platform.Foundation.numberWithLongLong
import platform.Foundation.timeZoneForSecondsFromGMT

/** The phone's language and clock, from Foundation: the same patterns the Android app gets from java.time and `DateFormat`. */
object IosFormats {
    fun uptimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    /** Whether the phone's own time format has no am/pm (a formatter's `j` template resolves to `H` or `h`). */
    fun use24HourClock(): Boolean = !(NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale.currentLocale)?.contains('a') ?: false)

    private fun formatter(pattern: String): NSDateFormatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        // The wall-clock reading is carried as if it were UTC, so the formatter adds no zone of its own.
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        dateFormat = pattern
    }

    private fun seconds(at: LocalDateTime): Double = at.toInstant(TimeZone.UTC).toEpochMilliseconds() / 1000.0

    fun format(pattern: String, at: LocalDateTime): String = formatter(pattern).stringFromDate(NSDate.dateWithTimeIntervalSince1970(seconds(at)))

    fun format(pattern: String, day: LocalDate): String = format(pattern, LocalDateTime(day.year, day.month, day.day, 0, 0, 0, 0))

    /** [day] in the phone's own best pattern for the [skeleton] (`EEEdMMM`). */
    fun bestPattern(skeleton: String, day: LocalDate): String =
        format(NSDateFormatter.dateFormatFromTemplate(skeleton, 0uL, NSLocale.currentLocale) ?: skeleton, day)

    fun weekdayName(day: DayOfWeek, full: Boolean): String {
        val f = NSDateFormatter().apply { locale = NSLocale.currentLocale }
        val names = if (full) f.weekdaySymbols else f.shortWeekdaySymbols
        // Sunday is first in Foundation's lists.
        return names[day.isoDayNumber % 7] as String
    }

    fun monthName(month: Month, full: Boolean): String {
        val f = NSDateFormatter().apply { locale = NSLocale.currentLocale }
        val names = if (full) f.monthSymbols else f.shortMonthSymbols
        return names[month.ordinal] as String
    }

    fun thousands(value: Long): String = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = true
        maximumFractionDigits = 0uL
    }.stringFromNumber(NSNumber.numberWithLongLong(value)) ?: value.toString()

    /** [value] to [decimals] places (`%.2f`), rounded half up as `String.format` does; [phoneLocale] false gives the plain dot. */
    fun fixed(value: Double, decimals: Int, phoneLocale: Boolean): String = NSNumberFormatter().apply {
        locale = if (phoneLocale) NSLocale.currentLocale else NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = false
        minimumFractionDigits = decimals.toULong()
        maximumFractionDigits = decimals.toULong()
        roundingMode = NSNumberFormatterRoundHalfUp
    }.stringFromNumber(NSNumber.numberWithDouble(value)) ?: value.toString()

    /** A wall-clock reading as the `NSDate` the phone's calendar gives it. */
    fun date(at: LocalDateTime): NSDate {
        val parts = NSDateComponents()
        parts.year = at.year.toLong(); parts.month = (at.month.ordinal + 1).toLong(); parts.day = at.day.toLong()
        parts.hour = at.hour.toLong(); parts.minute = at.minute.toLong(); parts.second = at.second.toLong()
        return NSCalendar.currentCalendar.dateFromComponents(parts) ?: NSDate()
    }

    /** The wall-clock reading of [date] in the phone's calendar. */
    fun reading(date: NSDate): LocalDateTime {
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond
        val c = NSCalendar.currentCalendar.components(units, fromDate = date)
        return LocalDateTime(c.year.toInt(), c.month.toInt(), c.day.toInt(), c.hour.toInt(), c.minute.toInt(), c.second.toInt(), 0)
    }
}
