package clean.core

import clean.core.time.*
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** The period the Statistics card on the Progress page adds up. */
enum class StatsPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

/** Days from [start] to [end], both included. */
data class DayRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(day: LocalDate) = day >= start && day <= end
}

/**
 * The period up to [today]: this Monday, the first of this month or of this year, or (All) the day of the [first] workout, to today.
 * With no workout, All is today alone.
 */
fun periodRange(period: StatsPeriod, today: LocalDate, first: LocalDate?): DayRange = when (period) {
    StatsPeriod.WEEK -> DayRange(weekStart(today), today)
    StatsPeriod.MONTH -> DayRange(today.withDayOfMonth(1), today)
    StatsPeriod.YEAR -> DayRange(today.withDayOfYear(1), today)
    StatsPeriod.ALL -> DayRange(first?.takeIf { it <= today } ?: today, today)
}

/**
 * The same elapsed part of the period before, so a half-done month is not set against a whole one: Oct 1 to 16 against Sep 1 to 16,
 * Monday to Wednesday against last Monday to Wednesday. A shorter earlier period (Mar 31 against February) ends with its own last
 * day. All has no earlier period.
 */
fun previousRange(period: StatsPeriod, today: LocalDate): DayRange? {
    val now = periodRange(period, today, null)
    val elapsed = ChronoUnit.DAYS.between(now.start, today)
    val start = when (period) {
        StatsPeriod.WEEK -> now.start.minusWeeks(1)
        StatsPeriod.MONTH -> now.start.minusMonths(1)
        StatsPeriod.YEAR -> now.start.minusYears(1)
        StatsPeriod.ALL -> return null
    }
    val whole = when (period) {
        StatsPeriod.WEEK -> start.plusDays(6)
        StatsPeriod.MONTH -> start.withDayOfMonth(start.lengthOfMonth())
        else -> start.withDayOfYear(start.lengthOfYear())
    }
    val end = start.plusDays(elapsed)
    return DayRange(start, if (end > whole) whole else end)
}

/** What the four hero numbers add up over a range. [sets] are working sets as counted (a round of left and right is one). */
data class PeriodTotals(val workouts: Int, val volume: Double, val seconds: Long, val sets: Double)

fun totalsIn(workouts: List<Workout>, range: DayRange, volumeOf: (Workout) -> Double): PeriodTotals {
    var count = 0
    var volume = 0.0
    var seconds = 0L
    var sets = 0.0
    for (w in workouts) {
        if (LocalDate.parse(w.day) !in range) continue
        count++
        volume += volumeOf(w)
        seconds += w.durationSeconds
        sets += w.workingCount
    }
    return PeriodTotals(count, volume, seconds, sets)
}

/** `+3`, `−2` or `same`, for a count. */
fun countChange(now: Double, before: Double): String {
    val diff = jRound((now - before) * 10) / 10.0
    return if (diff == 0.0) "same" else (if (diff > 0) "+" else "−") + shortNumber(abs(diff))
}

/** `+12 %`, `−12 %`, `same` (also when it rounds to nothing), or `new` when there was nothing before. */
fun percentChange(now: Double, before: Double): String {
    if (before <= 0.0) return if (now > 0.0) "new" else "same"
    val percent = jRound((now - before) / before * 100)
    return if (percent == 0L) "same" else (if (percent > 0) "+" else "−") + abs(percent) + " %"
}

/** One bar of the volume chart: the days [start] to [end], the volume lifted in them, whether it is still to come or holds today. */
data class VolumeBucket(val start: LocalDate, val end: LocalDate, val volume: Double, val future: Boolean, val current: Boolean)

/**
 * The bars of the volume chart: Week is Monday to Sunday by day, Month the days of the month, Year the twelve months, All the months
 * from the first workout (the years, when that is more than 24 months). Buckets after [today] are [VolumeBucket.future] and empty.
 * [perDay] is the volume of each day trained; All with no [first] workout has no bars.
 */
fun volumeBuckets(period: StatsPeriod, today: LocalDate, first: LocalDate?, perDay: Map<LocalDate, Double>): List<VolumeBucket> {
    val spans: List<DayRange> = when (period) {
        StatsPeriod.WEEK -> weekStart(today).let { m -> (0L..6L).map { DayRange(m.plusDays(it), m.plusDays(it)) } }
        StatsPeriod.MONTH -> today.withDayOfMonth(1).let { s -> (0L until s.lengthOfMonth().toLong()).map { DayRange(s.plusDays(it), s.plusDays(it)) } }
        StatsPeriod.YEAR -> (1..12).map { m -> LocalDate.of(today.year, m, 1).let { DayRange(it, it.withDayOfMonth(it.lengthOfMonth())) } }
        StatsPeriod.ALL -> {
            if (first == null || first > today) return emptyList()
            val months = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), today.withDayOfMonth(1)).toInt() + 1
            if (months <= 24) (0 until months).map { i -> first.withDayOfMonth(1).plusMonths(i.toLong()).let { DayRange(it, it.withDayOfMonth(it.lengthOfMonth())) } }
            else (first.year..today.year).map { y -> DayRange(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)) }
        }
    }
    return spans.map { s ->
        val future = s.start > today
        val volume = if (future) 0.0 else perDay.entries.sumOf { (d, v) -> if (d in s) v else 0.0 }
        VolumeBucket(s.start, s.end, volume, future, today in s)
    }
}

/** The [n] exercises with the most working sets (a round of left and right counting one) in [range], most first, with that many; ties go to the name first in order. */
fun topExercises(workouts: List<Workout>, range: DayRange, n: Int): List<Pair<String, Double>> {
    val sets = HashMap<String, Double>()
    for (w in workouts) {
        if (LocalDate.parse(w.day) !in range) continue
        for (b in w.blocks) if (b.workingCount > 0) sets[b.exercise] = (sets[b.exercise] ?: 0.0) + b.workingCount
    }
    return sets.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).take(n).map { it.key to it.value }
}

/** The exercise with the most working sets in [range], with that many (the first of [topExercises]). */
fun topExercise(workouts: List<Workout>, range: DayRange): Pair<String, Double>? = topExercises(workouts, range, 1).firstOrNull()

/** What the period before is called in a sentence: "last week", the month's name, "last year". All has none. */
fun previousName(period: StatsPeriod, today: LocalDate): String? {
    val before = previousRange(period, today) ?: return null
    return when (period) {
        StatsPeriod.WEEK -> "last week"
        StatsPeriod.MONTH -> before.start.month.englishName()
        else -> "last year"
    }
}

/**
 * One grey sentence setting a period against the same elapsed part of the one before: "1 fewer workout than October, volume down 8 %".
 * The volume part is left out when the earlier period lifted nothing; a rise from nothing in workouts reads as a plain count.
 */
fun comparisonSentence(now: PeriodTotals, before: PeriodTotals, name: String): String {
    val diff = now.workouts - before.workouts
    val count = when {
        diff > 0 -> "$diff more workout${if (diff == 1) "" else "s"} than $name"
        diff < 0 -> "${-diff} fewer workout${if (diff == -1) "" else "s"} than $name"
        else -> "Same number of workouts as $name"
    }
    if (before.volume <= 0.0) return count
    val percent = jRound((now.volume - before.volume) / before.volume * 100)
    val volume = when {
        percent > 0 -> "volume up $percent %"
        percent < 0 -> "volume down ${-percent} %"
        else -> "volume the same"
    }
    return "$count, $volume"
}

/** What one bar of the volume chart covers, for comparing it with every other such stretch of the log: a day, a month or a year. */
enum class BarSize { DAY, MONTH, YEAR }

/** The bar at [index] of the volume chart holds the best [size] of the whole log, with [volume] lifted in it. */
data class BarHigh(val index: Int, val size: BarSize, val volume: Double)

/**
 * The bar of [buckets] that is the all-time high for its size, if there is one: the biggest bar shown, when it lifted more than every other day (or
 * month, or year, by what the bars cover) in [perDay], the volume of each day trained, over the whole log. A log with nothing else to beat has none,
 * and a tie is not a high.
 */
fun allTimeHigh(buckets: List<VolumeBucket>, perDay: Map<LocalDate, Double>): BarHigh? {
    val index = buckets.indices.filter { !buckets[it].future && buckets[it].volume > 0.0 }.maxByOrNull { buckets[it].volume } ?: return null
    val bucket = buckets[index]
    val size = when {
        bucket.start == bucket.end -> BarSize.DAY
        bucket.start.year == bucket.end.year && bucket.start.monthValue == bucket.end.monthValue -> BarSize.MONTH
        else -> BarSize.YEAR
    }
    fun key(day: LocalDate): Int = when (size) { BarSize.DAY -> day.toEpochDay().toInt(); BarSize.MONTH -> day.year * 12 + day.monthValue; BarSize.YEAR -> day.year }
    val own = key(bucket.start)
    val totals = HashMap<Int, Double>()
    for ((day, v) in perDay) totals[key(day)] = (totals[key(day)] ?: 0.0) + v
    val best = totals.filterKeys { it != own }.values.maxOrNull() ?: return null
    return if (best > 0.0 && bucket.volume > best) BarHigh(index, size, bucket.volume) else null
}
