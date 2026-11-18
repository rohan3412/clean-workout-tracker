package clean.core

import java.time.DayOfWeek
import java.time.LocalDateTime

/** When a workout-day reminder goes off unless one is chosen: 18:00, in minutes after midnight. */
const val REMIND_DEFAULT_TIME = 18 * 60

/** `06:30` as minutes after midnight, or null when [text] is not a time of day. */
fun parseClock(text: String?): Int? {
    val parts = text?.split(':') ?: return null
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

/** Minutes after midnight as `06:30`. */
fun clockText(minutes: Int): String = "%02d:%02d".format(Math.floorMod(minutes, 1440) / 60, Math.floorMod(minutes, 1440) % 60)

/** The weekdays chosen for a reminder (kv `remind.days`: ISO numbers, Monday 1, joined by commas). None, the default, means no reminders. */
fun parseRemindDays(stored: String?): Set<DayOfWeek> =
    stored.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSortedSet()

/** The stored text for [days], Monday first; null (key removed, reminders off) when none. */
fun encodeRemindDays(days: Set<DayOfWeek>): String? = days.sorted().joinToString(",") { it.value.toString() }.ifEmpty { null }

/** The next reminder strictly after [now]: the first chosen weekday at [minutes] after midnight; null when no day is chosen. */
fun nextReminder(now: LocalDateTime, days: Set<DayOfWeek>, minutes: Int): LocalDateTime? {
    if (days.isEmpty()) return null
    val today = now.toLocalDate().atStartOfDay().plusMinutes(Math.floorMod(minutes, 1440).toLong())
    return (0..7L).map { today.plusDays(it) }.first { it.isAfter(now) && it.dayOfWeek in days }
}
