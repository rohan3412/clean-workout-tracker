package clean.core.time

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toJavaMonth

/*
 * For the Android side, which formats with the phone's locale through java.time: the kotlinx types handed to a java formatter.
 * Not part of common code; the iOS side formats with the system.
 */

fun LocalDate.format(formatter: DateTimeFormatter): String = toJavaLocalDate().format(formatter)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = toJavaLocalDateTime().format(formatter)
fun LocalTime.format(formatter: DateTimeFormatter): String = toJavaLocalTime().format(formatter)
fun YearMonth.format(formatter: DateTimeFormatter): String = atDay(1).toJavaLocalDate().format(formatter)
fun DayOfWeek.getDisplayName(style: TextStyle, locale: Locale): String = toJavaDayOfWeek().getDisplayName(style, locale)
fun Month.getDisplayName(style: TextStyle, locale: Locale): String = toJavaMonth().getDisplayName(style, locale)
