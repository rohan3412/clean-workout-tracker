package clean.core

import kotlinx.datetime.isoDayNumber
import clean.core.time.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The weekly target when nothing says otherwise. */
const val DEFAULT_TARGET = 3

/** Genuinely met weeks in a row that bank one shield. */
const val SHIELD_EVERY = 8

/** A weekly target that applies from the week of [from] (a Monday) on. */
data class TargetFrom(val from: LocalDate, val target: Int)

/** `2026-10-12:3;2026-11-02:4`, oldest first. */
fun encodeTargets(targets: List<TargetFrom>): String = targets.sortedBy { it.from }.joinToString(";") { "${it.from}:${it.target}" }

/** The list [encodeTargets] wrote, oldest first; an entry that cannot be read is dropped. */
fun decodeTargets(text: String?): List<TargetFrom> = text.orEmpty().split(';').mapNotNull { part ->
    val date = runCatching { LocalDate.parse(part.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
    part.substringAfter(':', "").toIntOrNull()?.let { TargetFrom(date, it.coerceIn(1, 7)) }
}.sortedBy { it.from }

/**
 * The target that scores the week starting [monday]: the latest entry dated on or before it, else (for
 * weeks before the first entry) the first entry's. [DEFAULT_TARGET] when there are none.
 */
fun targetFor(targets: List<TargetFrom>, monday: LocalDate): Int {
    val sorted = targets.sortedBy { it.from }
    return (sorted.lastOrNull { it.from <= monday } ?: sorted.firstOrNull())?.target ?: DEFAULT_TARGET
}

/** [targets] with [target] in force from the week of [monday]; an entry already dated that week is replaced, so earlier weeks are not re-scored. */
fun withTarget(targets: List<TargetFrom>, monday: LocalDate, target: Int): List<TargetFrom> =
    (targets.filter { it.from != monday } + TargetFrom(monday, target.coerceIn(1, 7))).sortedBy { it.from }

/**
 * A pace the user already keeps: the number of workouts a week reached in at least four of every five
 * of the last 12 complete weeks that had a workout, clamped to 1..7; [DEFAULT_TARGET] when there is no
 * such week. The median was tried and set half the weeks up to miss, which wiped out an existing streak.
 */
fun defaultTarget(workouts: List<Workout>, today: LocalDate): Int {
    val thisWeek = weekStart(today)
    val counts = workouts.groupBy { weekStart(LocalDate.parse(it.day)) }
        .filterKeys { it < thisWeek && it >= thisWeek.minusWeeks(12) }
        .values.map { it.size }.sorted()
    if (counts.isEmpty()) return DEFAULT_TARGET
    return counts[counts.size / 5].coerceIn(1, 7)
}

/**
 * The targets to score by: [stored] when there are any, else the user's own usual pace
 * ([defaultTarget]) from the first workout's week, so that history is not scored against a number picked later.
 */
fun targetsOrDefault(stored: List<TargetFrom>, workouts: List<Workout>, today: LocalDate): List<TargetFrom> {
    if (stored.isNotEmpty()) return stored
    val first = workouts.minOfOrNull { weekStart(LocalDate.parse(it.day)) } ?: weekStart(today)
    return listOf(TargetFrom(first, defaultTarget(workouts, today)))
}

/** One week against its target. [current] is the week in progress; [metOn] is the day the target was reached, if it was. */
data class WeekResult(val weekStart: LocalDate, val count: Int, val target: Int, val met: Boolean, val current: Boolean, val metOn: LocalDate?)

/**
 * Every week from the first workout's to the current one (just the current one when there is no workout),
 * oldest first, each scored against the target in force in it.
 */
fun weekResults(workouts: List<Workout>, targets: List<TargetFrom>, today: LocalDate): List<WeekResult> {
    val thisWeek = weekStart(today)
    val byWeek = workouts.groupBy { weekStart(LocalDate.parse(it.day)) }.filterKeys { it <= thisWeek }
    var week = byWeek.keys.minOrNull() ?: thisWeek
    val results = mutableListOf<WeekResult>()
    while (week <= thisWeek) {
        val days = byWeek[week].orEmpty().map { LocalDate.parse(it.day) }.sorted()
        val target = targetFor(targets, week).coerceAtLeast(1)
        val met = days.size >= target
        results += WeekResult(week, days.size, target, met, week == thisWeek, if (met) days[target - 1] else null)
        week = week.plusWeeks(1)
    }
    return results
}

/**
 * What the streak, the shield and the badge ladder read of a week, whatever made it a good one (the
 * target of workouts, or later a week on program). [metOn] is when it became met.
 */
data class WeekMet(val monday: LocalDate, val met: Boolean, val current: Boolean, val metOn: LocalDate?)

fun WeekResult.toMet() = WeekMet(weekStart, met, current, metOn)

enum class WeekState { MET, MISSED, FORGIVEN, IN_PROGRESS }

/**
 * The streak, replayed from the first week (nothing about it is stored). After every [SHIELD_EVERY] weeks in a
 * row that were genuinely met one shield is banked, one held at most; a missed week with a shield held is
 * forgiven (the shield is spent, the run goes on, and the forgiven week counts for nothing toward the run
 * or toward the next shield). The week in progress adds to the run once met and never breaks it.
 */
data class Streak(
    /** The run of met weeks up to now. */
    val length: Int,
    val shield: Boolean,
    /** Genuinely met weeks since the shield was last banked or spent, or since the run began. */
    val toward: Int,
    /** How many shields have been banked in all, spent or not. */
    val banked: Int,
    val forgiven: List<LocalDate>,
    val best: Int,
    /** The Monday of the week that ended the best run (the latest, if several are as long). */
    val bestEnd: LocalDate?,
    /** Per input week. */
    val states: List<WeekState>,
    /** The run's length after each input week. */
    val runAfter: List<Int>,
) {
    val weeksToShield: Int get() = SHIELD_EVERY - toward
}

fun replayStreak(weeks: List<WeekMet>): Streak {
    var run = 0
    var toward = 0
    var held = false
    var banked = 0
    var best = 0
    var bestEnd: LocalDate? = null
    val forgiven = mutableListOf<LocalDate>()
    val states = mutableListOf<WeekState>()
    val runAfter = mutableListOf<Int>()
    for (week in weeks) {
        val state = when {
            week.met -> {
                run++
                toward++
                if (toward == SHIELD_EVERY) {
                    if (!held) { held = true; banked++ }
                    toward = 0
                }
                if (run >= best) { best = run; bestEnd = week.monday }
                if (week.current) WeekState.IN_PROGRESS else WeekState.MET
            }
            week.current -> WeekState.IN_PROGRESS
            held -> {
                held = false
                toward = 0
                forgiven += week.monday
                WeekState.FORGIVEN
            }
            else -> {
                run = 0
                toward = 0
                WeekState.MISSED
            }
        }
        states += state
        runAfter += run
    }
    return Streak(run, held, toward, banked, forgiven, best, bestEnd, states, runAfter)
}

enum class Ladder { STREAK, WORKOUTS, PROGRAM }

/**
 * A badge on a ladder. [earned] is null while it is out of reach; [weekOf] (a streak badge) and
 * [workoutDate] (a total-workouts badge) are what earned it, for a tap to open.
 */
data class Award(val id: String, val title: String, val ladder: Ladder, val goal: Int, val earned: LocalDate?, val weekOf: LocalDate?, val workoutDate: String?)

val STREAK_LADDER = listOf(4 to "1 month streak", 13 to "3 months streak", 26 to "6 months streak", 52 to "1 year streak")
val WORKOUT_LADDER = listOf(10, 50, 100, 250, 500)
val PROGRAM_LADDER = listOf(4 to "1 month on program", 13 to "3 months on program", 26 to "6 months on program", 52 to "1 year on program")

/**
 * Every badge, earned or not, streak ladder first. A badge is earned the first time its goal is reached and
 * stays earned, on the day the week that reached it was met (for the workouts ladder, the day of that workout).
 */
fun badges(workouts: List<Workout>, weeks: List<WeekMet>, streak: Streak): List<Award> {
    val streakAwards = STREAK_LADDER.map { (goal, title) ->
        val at = streak.runAfter.indexOfFirst { it >= goal }
        val week = weeks.getOrNull(at)
        Award("streak-$goal", title, Ladder.STREAK, goal, week?.let { it.metOn ?: it.monday.plusDays(6) }, week?.monday, null)
    }
    val ordered = workouts.sortedBy { it.date }
    val workoutAwards = WORKOUT_LADDER.map { goal ->
        val workout = ordered.getOrNull(goal - 1)
        Award("workouts-$goal", "$goal workouts", Ladder.WORKOUTS, goal, workout?.let { LocalDate.parse(it.day) }, null, workout?.date)
    }
    return streakAwards + workoutAwards
}

/** The program ladder, read from the program's own streak the way [badges] reads the target's. */
fun programBadges(weeks: List<WeekMet>, streak: Streak): List<Award> = PROGRAM_LADDER.map { (goal, title) ->
    val week = weeks.getOrNull(streak.runAfter.indexOfFirst { it >= goal })
    Award("program-$goal", title, Ladder.PROGRAM, goal, week?.let { it.metOn ?: it.monday.plusDays(6) }, week?.monday, null)
}

/** The first badge of a ladder not yet earned, and how far off it is. */
data class NextMilestone(val award: Award, val remaining: Int, val fraction: Float)

fun nextMilestones(awards: List<Award>, streakLength: Int, totalWorkouts: Int, programLength: Int = 0): List<NextMilestone> =
    Ladder.entries.mapNotNull { ladder ->
        val award = awards.firstOrNull { it.ladder == ladder && it.earned == null } ?: return@mapNotNull null
        val have = when (ladder) { Ladder.STREAK -> streakLength; Ladder.WORKOUTS -> totalWorkouts; Ladder.PROGRAM -> programLength }
        NextMilestone(award, (award.goal - have).coerceAtLeast(0), (have.toFloat() / award.goal).coerceIn(0f, 1f))
    }

/** The longest gap between two workouts in a row: [days] between them, and the dates of the workouts either side. */
data class LongestBreak(val days: Int, val from: String, val to: String)

fun longestBreak(workouts: List<Workout>): LongestBreak? {
    val ordered = workouts.sortedBy { it.date }
    var best: LongestBreak? = null
    for ((a, b) in ordered.zipWithNext()) {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(a.day), LocalDate.parse(b.day)).toInt()
        if (days > (best?.days ?: -1)) best = LongestBreak(days, a.date, b.date)
    }
    return best
}

/** Workouts on each weekday, Monday first. */
fun usualDays(workouts: List<Workout>): List<Int> {
    val counts = IntArray(7)
    for (w in workouts) counts[LocalDate.parse(w.day).dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber]++
    return counts.toList()
}

/** Everything the Profile tab, the Trophies page, the Summary and the widget say about consistency. */
class Consistency(
    val weeks: List<WeekResult>,
    val streak: Streak,
    val awards: List<Award>,
    val total: Int,
    /** Weeks in a row with any workout, however few. */
    val weeksTrained: Int,
    val longestBreak: LongestBreak?,
    val usualDays: List<Int>,
) {
    /** The week in progress; always there. */
    val now: WeekResult get() = weeks.last()
}

fun consistency(workouts: List<Workout>, targets: List<TargetFrom>, today: LocalDate): Consistency {
    val weeks = weekResults(workouts, targets, today)
    val streak = replayStreak(weeks.map { it.toMet() })
    return Consistency(
        weeks, streak, badges(workouts, weeks.map { it.toMet() }, streak), workouts.size,
        weekStreak(workouts, today), longestBreak(workouts), usualDays(workouts),
    )
}

/** What one finished workout earned: compared with the log as it would be without it. */
data class Earned(val targetHit: Boolean, val count: Int, val target: Int, val awards: List<Award>, val shield: Boolean) {
    val any: Boolean get() = targetHit || awards.isNotEmpty() || shield
}

fun earnedBy(workouts: List<Workout>, workout: Workout, targets: List<TargetFrom>, today: LocalDate): Earned {
    val with = consistency(workouts, targets, today)
    val without = consistency(workouts.filter { it.date != workout.date }, targets, today)
    val monday = weekStart(LocalDate.parse(workout.day))
    val week = with.weeks.firstOrNull { it.weekStart == monday }
    val weekWithout = without.weeks.firstOrNull { it.weekStart == monday }
    val hit = week != null && week.met && weekWithout?.met != true
    val had = without.awards.filter { it.earned != null }.mapTo(HashSet()) { it.id }
    return Earned(
        hit, week?.count ?: 0, week?.target ?: DEFAULT_TARGET,
        with.awards.filter { it.earned != null && it.id !in had },
        with.streak.banked > without.streak.banked,
    )
}
