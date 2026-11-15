package clean.core

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test

/**
 * Timings of the work the app does with the whole history when it starts and after every saved workout (`AppModel`'s flows):
 * a speed check to compare before and after a change (customization/speed-pass.md), not a pass-or-fail test. It runs over a
 * made-up log of three years, four workouts a week, and over the real export when `original/` is present.
 * `musclesOf` is called the way the app calls it (see `appMusclesOf`).
 */
class DataPathSpeedTest {
    private fun syntheticRows(): List<WorkoutRow> {
        val names = LIBRARY.filter { it.loadType == LoadType.NORMAL }.map { it.name }.take(40)
        val rows = ArrayList<WorkoutRow>()
        var day = LocalDate.of(2023, 10, 2)
        var n = 0
        while (n < 624) {
            for (offset in listOf(0L, 1L, 3L, 4L)) {
                n++
                val start = day.plusDays(offset).atTime(18, 0)
                val date = start.format(STRONG_DATE_FORMAT)
                for (e in 0 until 6) {
                    val exercise = names[(n * 3 + e * 7) % names.size]
                    rows += WorkoutRow(n.toString(), date, "Day ${n % 4}", "3600", exercise, "W", "20.0", "10", "", "", "", "", "")
                    for (s in 1..3) {
                        val kg = 40.0 + (n / 8) * 1.25 + e * 5
                        rows += WorkoutRow(n.toString(), date, "Day ${n % 4}", "3600", exercise, s.toString(), decimalText(kg), (8 + s % 3).toString(), "", "", "", "", "")
                    }
                }
            }
            day = day.plusWeeks(1)
        }
        return rows
    }

    private fun realRows(): List<WorkoutRow>? =
        File("../original").listFiles()?.firstOrNull { Regex("""strong\d+\.csv""").matches(it.name) }?.let { WorkoutCsv.parse(it.readBytes()) }

    /** The median of [rounds] single runs after a warm-up, in ms: the PC is busy with other things now and then, and the median ignores that. */
    private fun time(label: String, rounds: Int, block: () -> Any?): Double {
        repeat(10) { block() }
        val samples = DoubleArray(rounds * 2) { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1e6 }
        samples.sort()
        val ms = samples[samples.size / 2]
        println("  %-34s %8.2f ms".format(label, ms))
        return ms
    }

    private fun run(label: String, rows: List<WorkoutRow>) {
        val stored = HashMap<String, String>()
        // As `AppModel.musclesOf` does it: what is stored for an exercise (most logged exercises have a stored row) is decoded and its
        // heads looked up once per version of the settings, then kept.
        val memo = java.util.concurrent.ConcurrentHashMap<String, MuscleUse>()
        val appMusclesOf: (String) -> MuscleUse = { name ->
            memo.getOrPut(name) {
                val text = stored.getOrPut(name) { suggest(name)?.muscles?.encode().orEmpty() }
                val use = text.takeIf { it.isNotEmpty() }?.let(MuscleUse::decode) ?: MuscleUse()
                use.copy(heads = headsOf(name, use))
            }
        }
        val workouts = buildWorkouts(rows)
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        println("$label: ${rows.size} rows, ${workouts.size} workouts")
        var total = 0.0
        total += time("buildWorkouts", 20) { buildWorkouts(rows) }
        total += time("prsOf", 10) { prsOf(workouts, OneRm.DEFAULT) }
        val targets = targetsOrDefault(emptyList(), workouts, today)
        total += time("targetsOrDefault", 20) { targetsOrDefault(emptyList(), workouts, today) }
        total += time("consistency", 20) { consistency(workouts, targets, today) }
        total += time("weeklyTotals", 20) { weeklyTotals(workouts) }
        total += time("muscleSets (30 days)", 20) { muscleSets(workouts, now.minusDays(30), appMusclesOf) }
        total += time("muscleSetsBySide + hasSideWork", 20) { muscleSetsBySide(workouts, now.minusDays(30), appMusclesOf); hasSideWork(workouts, now.minusDays(30)) }
        total += time("headSets + otherWork + bySide", 10) {
            headSets(workouts, now.minusDays(30), appMusclesOf)
            Head.entries.map { it.group }.distinct().associateWith { otherWork(it, workouts, now.minusDays(30), appMusclesOf) }
            headSetsBySide(workouts, now.minusDays(30), appMusclesOf)
        }
        total += time("muscle figures (6 calls)", 10) {
            fatigue(workouts, now, appMusclesOf); retainedStrength(workouts, now, appMusclesOf); lastTrained(workouts, appMusclesOf)
            fatigueBySide(workouts, now, appMusclesOf); retainedStrengthBySide(workouts, now, appMusclesOf); lastTrainedBySide(workouts, appMusclesOf)
        }
        val sessions = sessionsByExercise(workouts)
        total += time("sessionsByExercise", 20) { sessionsByExercise(workouts) }
        total += time("deloadSignals", 10) {
            deloadSignals(workouts, today, emptySet(), { name -> progressOf(sessions[name].orEmpty(), OneRm.DEFAULT) }, emptyMap())
        }
        println("  %-34s %8.2f ms".format("total", total))
    }

    @Test fun `data path timings`() {
        run("Made-up log", syntheticRows())
        realRows()?.let { run("Real export", it) }
    }
}
