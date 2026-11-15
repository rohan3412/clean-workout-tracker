package clean.core

import java.time.LocalDate

/** The launcher shortcuts on offer (kv `shortcuts`: the chosen ones, in order, joined by commas). */
enum class ShortcutKind(val key: String, val label: String) {
    START("start", "Start the next template"),
    RESUME("resume", "Resume workout"),
    WEIGHT("weight", "Log bodyweight"),
}

val SHORTCUTS_DEFAULT = ShortcutKind.entries.toList()

/** The chosen shortcuts in order; all three when nothing is stored, none for an empty value. Unknown and repeated names are dropped. */
fun parseShortcuts(stored: String?): List<ShortcutKind> {
    if (stored == null) return SHORTCUTS_DEFAULT
    return stored.split(',').mapNotNull { part -> ShortcutKind.entries.firstOrNull { it.key == part.trim() } }.distinct()
}

/** The stored text for [chosen]: null (key removed) for the default. */
fun encodeShortcuts(chosen: List<ShortcutKind>): String? = if (chosen == SHORTCUTS_DEFAULT) null else chosen.joinToString(",") { it.key }

/** What the launcher shows: the chosen ones, Resume only while a workout is in progress. */
fun shortcutsShown(chosen: List<ShortcutKind>, running: Boolean): List<ShortcutKind> = chosen.filter { it != ShortcutKind.RESUME || running }

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

/** Whether the rest notification is the small form (kv `rest.notif`: `compact`; anything else, and nothing stored, is the expanded one). */
fun restNotifCompact(stored: String?): Boolean = stored == "compact"

/** The widget's colours as stored (kv `widget.colors`): ARGB integers joined by commas. */
fun encodeArgb(colours: List<Int>): String = colours.joinToString(",")

/** [count] colours back from [stored], or null when it is missing or not that many integers. */
fun decodeArgb(stored: String?, count: Int): List<Int>? =
    stored?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == count && stored.split(',').size == count }

/** Whether the workout in progress shows in a notification, on the lock screen too, while the app is behind (kv `workout.notif`; on unless `off`). */
fun workoutNotifOn(stored: String?): Boolean = stored != "off"

private val REST_UNTIL = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

/** The workout notification's line under its name: the rest and when it ends while one runs, then the next set; null when there is neither. */
fun workoutNotifText(next: String?, restEnd: java.time.LocalTime?): String? =
    listOfNotNull(restEnd?.let { "Resting until ${it.format(REST_UNTIL)}" }, next).joinToString(" · ").ifEmpty { null }
