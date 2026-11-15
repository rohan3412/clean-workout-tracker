package clean.core

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The Day & night look's fallback when the sun does not rise or set (polar day or night): light from 06:30, dark from 18:30. */
const val DEFAULT_DAY_FROM = 6 * 60 + 30
const val DEFAULT_NIGHT_FROM = 18 * 60 + 30

/** The latitude Day & night assumes until the user sets one: the Netherlands. */
const val DEFAULT_LATITUDE = 52.0

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

/** Whether [now] (minutes after midnight) is in the day that begins at [dayFrom] and ends at [nightFrom]; the day may run past midnight. */
fun isDaytime(now: Int, dayFrom: Int, nightFrom: Int): Boolean =
    if (dayFrom <= nightFrom) now in dayFrom until nightFrom else now >= dayFrom || now < nightFrom

/**
 * Sunrise and sunset on [date] as minutes after local midnight, by NOAA's solar formula, for a place at [latitude] degrees north
 * and [longitude] degrees east in a time zone [utcOffsetMinutes] from UTC. Null when the sun does not rise or set that day.
 */
fun sunTimes(date: LocalDate, latitude: Double, longitude: Double, utcOffsetMinutes: Int): Pair<Int, Int>? {
    val gamma = 2 * PI / 365 * (date.dayOfYear - 1)
    val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    val lat = Math.toRadians(latitude)
    val cosHour = cos(Math.toRadians(90.833)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
    if (cosHour !in -1.0..1.0) return null
    val hourAngle = Math.toDegrees(acos(cosHour))
    fun local(utcMinutes: Double) = Math.floorMod(Math.round(utcMinutes).toInt() + utcOffsetMinutes, 1440)
    return local(720 - 4 * (longitude + hourAngle) - equationOfTime) to local(720 - 4 * (longitude - hourAngle) - equationOfTime)
}

/** How Day & night decides: `times` (set times), `sun` (sunrise and sunset), or `phone` (the phone's own dark mode). */
const val SWITCH_TIMES = "times"
const val SWITCH_SUN = "sun"
const val SWITCH_PHONE = "phone"

/** When the day begins and ends on [date] for the `times` or `sun` rule; the sun's polar days and nights fall back to 06:30 and 18:30. */
fun dayWindow(rule: String, date: LocalDate, latitude: Double, utcOffsetMinutes: Int, dayFrom: Int, nightFrom: Int): Pair<Int, Int> =
    if (rule == SWITCH_SUN) sunTimes(date, latitude, utcOffsetMinutes / 60.0 * 15.0, utcOffsetMinutes) ?: (DEFAULT_DAY_FROM to DEFAULT_NIGHT_FROM)
    else dayFrom to nightFrom
