package clean.core

import clean.core.time.*
import kotlin.math.abs

/** A weigh-in counts for a workout only if it is within this many days of it, before or after. */
const val BODYWEIGHT_WINDOW_DAYS = 7L

/**
 * The real load of a logged weight on a given day, or null when it cannot be known: a bodyweight
 * or assisted lift with no weigh-in near that day. Nothing is ever guessed in its place.
 */
typealias LoadOf = (exercise: String, weight: Double, date: String) -> Double?

/** The weight as logged, for when load types do not matter. */
val LOGGED_LOAD: LoadOf = { _, weight, _ -> weight }

fun realLoad(type: LoadType, weight: Double, bodyweight: Double?): Double? = when (type) {
    LoadType.NORMAL, LoadType.TIMED, LoadType.CARDIO -> weight
    LoadType.BODYWEIGHT -> bodyweight?.plus(weight)
    // Help at or above bodyweight moved nothing.
    LoadType.ASSISTED -> bodyweight?.let { maxOf(0.0, it - weight) }
}

/** The bodyweight log, as (date, kg) in Strong's date format. */
class Bodyweights(entries: List<Pair<String, Double>>) {
    private val sorted = entries.mapNotNull { (date, kg) -> seconds(date)?.let { it to kg } }.sortedBy { it.first }
    // Asked from the screen and from background work at once, and "no weigh-in near" is remembered too (as NaN).
    private val cache = ConcurrentCache<String, Double>()

    /** The weigh-in closest to [date], if one lies within [BODYWEIGHT_WINDOW_DAYS] of it. */
    fun at(date: String): Double? = cache.getOrPut(date) { find(date) ?: Double.NaN }.takeIf { !it.isNaN() }

    private fun find(date: String): Double? {
        val t = seconds(date) ?: return null
        val closest = sorted.minByOrNull { abs(it.first - t) } ?: return null
        return if (abs(closest.first - t) <= BODYWEIGHT_WINDOW_DAYS * 86_400) closest.second else null
    }

    private companion object {
        fun seconds(date: String): Long? = parseStrongDate(date)?.toEpochSecondUtc()
    }
}
