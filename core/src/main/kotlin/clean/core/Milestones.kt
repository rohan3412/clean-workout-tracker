package clean.core

/** What a milestone counts. */
enum class MilestoneKind { WORKOUTS, VOLUME, PRS, STREAK }

/**
 * A round number reached, or still ahead: [target] of [kind] (workouts, kg lifted, PRs, weeks of the longest streak). [current] is where the
 * count stands now; [reachedOn] is the day the count passed [target] (`YYYY-MM-DD`), or null when it has not, or when the day is not known
 * (a streak keeps no date).
 */
data class Milestone(val kind: MilestoneKind, val target: Long, val current: Double, val reachedOn: String?) {
    val reached: Boolean get() = current >= target
    /** How far along, 0 to 1. */
    val progress: Double get() = (current / target).coerceIn(0.0, 1.0)
}

val WORKOUT_MILESTONES = listOf(1L, 10, 25, 50, 100, 250, 500, 1000, 2000)
val VOLUME_MILESTONES_KG = listOf(10_000L, 100_000, 250_000, 500_000, 1_000_000, 2_500_000, 5_000_000, 10_000_000)
val PR_MILESTONES = listOf(1L, 10, 50, 100, 250, 500, 1000)
val STREAK_MILESTONES = listOf(2L, 4, 8, 13, 26, 52)

/**
 * Every milestone, from the rows alone (nothing is stored). The lists are per workout, oldest first and of equal length: [days] the day of
 * each, [volumes] its volume in kg, [prs] its PR count (see `recordCount`). [bestStreak] is the longest run of weeks that met the weekly target.
 */
fun milestones(days: List<String>, volumes: List<Double>, prs: List<Double>, bestStreak: Int): List<Milestone> {
    fun cumulative(values: List<Double>) = values.runningFold(0.0) { a, b -> a + b }.drop(1)
    fun counted(kind: MilestoneKind, targets: List<Long>, running: List<Double>) = targets.map { t ->
        val at = running.indexOfFirst { it >= t }
        Milestone(kind, t, running.lastOrNull() ?: 0.0, if (at >= 0) days.getOrNull(at) else null)
    }
    return counted(MilestoneKind.WORKOUTS, WORKOUT_MILESTONES, days.indices.map { it + 1.0 }) +
        counted(MilestoneKind.VOLUME, VOLUME_MILESTONES_KG, cumulative(volumes)) +
        counted(MilestoneKind.PRS, PR_MILESTONES, cumulative(prs)) +
        STREAK_MILESTONES.map { Milestone(MilestoneKind.STREAK, it, bestStreak.toDouble(), null) }
}

/** The [count] most recently reached milestones with a known day, newest first; milestones reached on the same day keep the order of the list. */
fun latestReached(all: List<Milestone>, count: Int): List<Milestone> =
    all.withIndex().filter { it.value.reachedOn != null }.sortedWith(compareByDescending<IndexedValue<Milestone>> { it.value.reachedOn }.thenByDescending { it.index })
        .take(count).map { it.value }

/**
 * The milestone to aim at: of those not reached, the one nearest to done by its share of the target; a tie goes to the smaller target.
 * Null when every milestone has been reached.
 */
fun nextMilestone(all: List<Milestone>): Milestone? =
    all.filter { !it.reached }.sortedWith(compareByDescending<Milestone> { it.progress }.thenBy { it.target }).firstOrNull()

private fun ordinal(n: Long): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1L -> "${n}st"
    n % 10 == 2L -> "${n}nd"
    n % 10 == 3L -> "${n}rd"
    else -> "${n}th"
}

/** The milestone in words: `100th workout`, `52-week streak`, `1st PR`; a volume is worded by [volume] (the profile's unit), as `1,000,000 kg lifted`. */
fun Milestone.title(volume: (Double) -> String): String = when (kind) {
    MilestoneKind.WORKOUTS -> if (target == 1L) "First workout" else "${ordinal(target)} workout"
    MilestoneKind.VOLUME -> "${volume(target.toDouble())} lifted"
    MilestoneKind.PRS -> if (target == 1L) "First PR" else "${ordinal(target)} PR"
    MilestoneKind.STREAK -> "$target-week streak"
}
