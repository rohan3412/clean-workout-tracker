package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The launcher shortcuts: all three, always (since 1.1.0 they are no longer chosen). */
enum class ShortcutKind(val key: String, val label: String) {
    START("start", "Start the next template"),
    RESUME("resume", "Resume workout"),
    WEIGHT("weight", "Log bodyweight"),
}

/** What the launcher shows: all three, Resume only while a workout is in progress. */
fun shortcutsShown(running: Boolean): List<ShortcutKind> = ShortcutKind.entries.filter { it != ShortcutKind.RESUME || running }

/** How a day looks in the seven-day strip. */
enum class DayMark { TRAINED, TODAY, PAST, FUTURE }

/** Monday to Sunday of [today]'s week: a trained day is filled, today (not yet trained) a ring, the rest plain, and dim once to come. [trained] holds `yyyy-MM-dd` days. */
fun weekMarks(today: LocalDate, trained: Set<String>): List<DayMark> {
    val monday = weekStart(today)
    return (0 until 7).map { i ->
        val day = monday.plusDays(i.toLong())
        when {
            day.toString() in trained -> DayMark.TRAINED
            day == today -> DayMark.TODAY
            day.isAfter(today) -> DayMark.FUTURE
            else -> DayMark.PAST
        }
    }
}

/** The widget's colours as stored (kv `widget.colors`): ARGB integers joined by commas. */
fun encodeArgb(colours: List<Int>): String = colours.joinToString(",")

/** [count] colours back from [stored], or null when it is missing or not that many integers. */
fun decodeArgb(stored: String?, count: Int): List<Int>? =
    stored?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == count && stored.split(',').size == count }

/** Whether the workout in progress shows in a notification, on the lock screen too, while the app is behind (kv `workout.notif`; on unless `off`). */
fun workoutNotifOn(stored: String?): Boolean = stored != "off"

private val REST_UNTIL = DatePattern("HH:mm:ss")

/** The workout notification's line under its name: the rest and when it ends while one runs, then the next set; null when there is neither. */
fun workoutNotifText(next: String?, restEnd: LocalTime?): String? =
    listOfNotNull(restEnd?.let { "Resting until ${it.format(REST_UNTIL)}" }, next).joinToString(" · ").ifEmpty { null }
