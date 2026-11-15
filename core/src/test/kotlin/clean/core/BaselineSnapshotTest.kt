package clean.core

import java.io.File
import java.util.Locale
import kotlin.test.Test
import org.junit.Assume.assumeTrue

/**
 * Writes plain-text figures computed from the real export in `original/` to `core/build/baseline/`
 * (git-ignored), so a change to records, muscle sets or fatigue can be compared with the run before it.
 * The previous run is kept as `*.prev.txt` and the number of differing lines printed; nothing here fails
 * on a difference. Skipped where `original/` is absent. Nothing from the export is printed.
 */
class BaselineSnapshotTest {
    private val original = File("../original")
    private val out = File("build/baseline")

    @Test
    fun `baseline figures from the real export`() {
        val file = original.listFiles()?.firstOrNull { Regex("""strong\d+\.csv""").matches(it.name) }
        assumeTrue("no workout csv in original/", file != null)
        val workouts = buildWorkouts(WorkoutCsv.parse(file!!.readBytes()))
        assumeTrue("no workouts", workouts.isNotEmpty())
        val musclesOf = { name: String -> suggest(name)?.muscles ?: MuscleUse() }
        val loadTypeOf = { name: String -> suggest(name)?.loadType ?: LoadType.NORMAL }
        fun f(x: Double) = String.format(Locale.ROOT, "%.4f", x)

        val prs = prsOf(workouts, EPLEY_RULE, loadTypeOf)
        val prLines = workouts.map { w ->
            val kinds = (prs[w.date] ?: emptyList()).flatten()
            w.date + PrKind.entries.joinToString("") { k -> "\t${k.name}=${kinds.count { it == k }}" }
        }

        val latest = workouts.maxOf { it.date }
        val end = parseStrongDate(latest)!!
        val all = muscleSets(workouts, null, musclesOf)
        val recent = muscleSets(workouts, end.minusDays(30), musclesOf)
        val setLines = Muscle.entries.map { "${it.name}\tall=${f(all[it] ?: 0.0)}\tlast30=${f(recent[it] ?: 0.0)}" }

        val tired = fatigue(workouts, end.plusDays(1), musclesOf)
        val fatigueLines = Muscle.entries.map { "${it.name}\t${f(tired[it] ?: 0.0)}" }

        out.mkdirs()
        for ((name, lines) in listOf("prs.txt" to prLines, "muscle_sets.txt" to setLines, "fatigue.txt" to fatigueLines)) {
            val now = File(out, name)
            val prev = File(out, name.replace(".txt", ".prev.txt"))
            if (now.exists()) {
                now.copyTo(prev, overwrite = true)
                val before = prev.readLines()
                val differ = (0 until maxOf(before.size, lines.size)).count { before.getOrNull(it) != lines.getOrNull(it) }
                println("$name: $differ lines differ")
            } else {
                println("$name: first run, ${lines.size} lines")
            }
            now.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }
}
