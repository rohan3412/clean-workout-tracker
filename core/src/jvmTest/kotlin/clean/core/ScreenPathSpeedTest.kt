package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Timings of what the screens work out from the whole history (records, the e1RM series, the calendar's values, the muscle balance,
 * the split, the year review, tags, `allWorkouts` to `workouts`), over a made-up log of three years: about 500 workouts and 15 000 sets.
 * A speed check to compare before and after (docs/build-log.md, Round 10), not a pass-or-fail test: `CLEAN_SPEED=1 ./gradlew :core:test --tests clean.core.ScreenPathSpeedTest --rerun`.
 */
class ScreenPathSpeedTest {
    private fun syntheticRows(): List<WorkoutRow> {
        val names = LIBRARY.filter { it.loadType == LoadType.NORMAL }.map { it.name }.take(60)
        val rows = ArrayList<WorkoutRow>()
        var day = LocalDate.of(2023, 11, 20)
        var n = 0
        while (n < 500) {
            for (offset in listOf(0L, 1L, 3L, 4L)) {
                n++
                val date = day.plusDays(offset).atTime(18, 0).format(STRONG_DATE_FORMAT)
                for (e in 0 until 6) {
                    val exercise = names[(n * 3 + e * 7) % names.size]
                    for (s in 1..5) {
                        val kg = 40.0 + (n / 8) * 1.25 + e * 5
                        rows += WorkoutRow(n.toString(), date, "Day ${n % 4}", "3600", exercise, s.toString(), decimalText(kg), (8 + s % 3).toString(), "", "", "", "", "")
                    }
                }
            }
            day = day.plusWeeks(1)
        }
        return rows
    }

    private fun time(label: String, rounds: Int = 15, block: () -> Any?): Double {
        repeat(8) { block() }
        val samples = DoubleArray(rounds) { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1e6 }
        samples.sort()
        val ms = samples[samples.size / 2]
        println("  %-44s %8.2f ms".format(label, ms))
        return ms
    }

    @Test fun `screen path timings`() {
        val rows = syntheticRows()
        val workouts = buildWorkouts(rows)
        val today = LocalDate.of(2026, 11, 19)
        val now = today.atTime(12, 0)
        val memo = HashMap<String, MuscleUse>()
        val musclesOf: (String) -> MuscleUse = { name -> memo.getOrPut(name) { (suggest(name)?.muscles ?: MuscleUse()).let { it.copy(heads = headsOf(name, it)) } } }
        println("Made-up log: ${rows.size} sets, ${workouts.size} workouts")
        val sessions = sessionsByExercise(workouts)
        val tags = workouts.withIndex().filter { it.index % 3 == 0 }.associate { it.value.date to "Deload" }
        val rule = OneRm.DEFAULT
        val results = LinkedHashMap<String, Double>()
        fun t(label: String, rounds: Int = 15, block: () -> Any?) { results[label] = time(label, rounds, block) }
        t("buildWorkouts (rows to workouts)") { buildWorkouts(rows) }
        t("statWorkouts (tags filter, some left out)") { statWorkouts(workouts, tags, setOf("Deload")) }
        t("sessionsByExercise") { sessionsByExercise(workouts) }
        t("prsOf (whole history)") { prsOf(workouts, rule) }
        t("recordCounts") { recordCounts(workouts, rule) }
        t("recordsOf, every exercise") { sessions.values.map { recordsOf(it, rule) } }
        t("progressOf (e1RM series), every exercise") { sessions.values.map { progressOf(it, rule) } }
        t("progressBySide + chartPoints, every exercise") { sessions.values.map { chartPoints(progressBySide(it, rule)) { p -> p.estimated1Rm } } }
        t("volumeByDay + secondsByDay (calendar)") { volumeByDay(workouts); secondsByDay(workouts) }
        t("weeklyTotals") { weeklyTotals(workouts) }
        t("weekStreak") { weekStreak(workouts, today) }
        val targets = targetsOrDefault(emptyList(), workouts, today)
        t("consistency") { consistency(workouts, targets, today) }
        t("muscleSets (balance, 30 days)") { muscleSets(workouts, now.minusDays(30), musclesOf) }
        t("muscleSets (balance, all time)") { muscleSets(workouts, null, musclesOf) }
        t("trainingSplit (4 weeks)") { trainingSplit(workouts, today.minusWeeks(4).toString()) { musclesOf(it).primary } }
        t("yearsWithWorkouts") { yearsWithWorkouts(workouts) }
        val prs = prsOf(workouts, rule)
        t("yearReview (2025)") { yearReview(workouts, 2025, { volumeOf(it) }, { _, s -> progressOf(s, rule) }, prs) }
        t("periodTotals + topExercise (a year)") {
            val range = periodRange(StatsPeriod.entries.last(), today, LocalDate.parse(workouts.first().day))
            totalsIn(workouts, range) { volumeOf(it) }; topExercise(workouts, range)
        }
        t("fatigue + retainedStrength + lastTrained") { fatigue(workouts, now, musclesOf); retainedStrength(workouts, now, musclesOf); lastTrained(workouts, musclesOf) }
        t("Progress tab: volumes keyed by Workout (data class)") { val v = workouts.associateWith { volumeOf(it) }; workouts.sumOf { v.getValue(it) } }
        t("Progress tab: volumes by position") { val v = workouts.map { volumeOf(it) }; v.sum() }
        val again = buildWorkouts(rows)
        t("StateFlow's == on an unchanged 500-workout list") { workouts == again }
        println("  %-44s %8.2f ms".format("sum of the above", results.values.sum()))
        println("  over 30 ms: " + results.filter { it.value > 30 }.keys)
    }
}
