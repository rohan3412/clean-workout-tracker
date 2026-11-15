package clean.app

import clean.app.ui.theme.LocalLook
import clean.core.bestRound
import clean.core.bestSetOf
import clean.core.recordCount
import clean.core.roundLine
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import clean.core.Muscle
import clean.core.SetType
import clean.core.progressOf
import clean.core.shortNumber
import clean.core.volumeOf
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One exercise of a template with how it has been going: the best set of its last session, and which way its estimated 1RM moved. */
private class TemplateLine(val exercise: String, val sets: Int, val last: String, val trend: String)

/**
 * A template's own page: starting it, what it trains, and what the workouts done under its name
 * add up to. A template and its workouts are tied only by the name, as in Strong's export.
 */
@Composable
fun TemplateScreen(model: AppModel, id: Long) {
    val templates by model.templates.collectAsState()
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val allPrs by model.prs.collectAsState()
    val scaleColours = LocalLook.current.scale
    val template = templates.firstOrNull { it.id == id }
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(template?.name ?: "Template", model::back) { if (template != null) TextButton(onClick = { model.open(Screen.TemplateEdit(id)) }) { Text("Edit") } }
    }) { padding ->
        if (template == null) return@Scaffold
        val list = remember(workouts, template.name) { workouts.filter { it.name == template.name } }
        // settings and bodyweights are keys only so the volume is worked out again when they change.
        val volumes = remember(list, settings, bodyweights) { list.map { volumeOf(it, model::loadOf) } }
        val times = remember(list) { list.map { parseDate(it.date) } }
        val working = template.blocks.sumOf { b -> b.sets.count { it != SetType.WARMUP } }
        // What the template trains: its working sets shared out over each exercise's muscles, as on the Body tab.
        val muscles = remember(template, settings) {
            val sets = HashMap<Muscle, Double>()
            for (b in template.blocks) {
                val n = b.sets.count { it != SetType.WARMUP }
                for ((muscle, share) in model.musclesOf(b.exercise).shares) sets[muscle] = (sets[muscle] ?: 0.0) + n * share
            }
            sets
        }
        val lines = remember(template, workouts, settings, bodyweights, rule) {
            template.blocks.map { b ->
                val sessions = model.sessionsIn(workouts, b.exercise)
                val unit = model.unitOf(b.exercise)
                val best = bestSetOf(sessions.lastOrNull()?.sets.orEmpty())
                // An exercise last done one side at a time is read by its best round.
                val lastBlock = workouts.asReversed().firstNotNullOfOrNull { w -> w.blocks.lastOrNull { it.exercise == b.exercise && it.sets.isNotEmpty() } }
                val round = lastBlock?.takeIf { it.paired }?.let { bestRound(it.rounds) }
                val estimates = progressOf(sessions, rule, model.loadTypeOf(b.exercise), bodyweights::at).mapNotNull { it.estimated1Rm }.takeLast(2)
                val trend = if (estimates.size < 2) "" else if (estimates[1] > estimates[0]) "↑" else if (estimates[1] < estimates[0]) "↓" else "→"
                TemplateLine(b.exercise, b.sets.count { it != SetType.WARMUP }, (if (round != null) "Last time: " + roundLine(round) { setValues(it, unit) } else best?.let { "Last time: " + setValues(it, unit) }) ?: "Not done yet", trend)
            }
        }
        val openOn = { day: LocalDate -> list.lastOrNull { it.day == day.toString() }?.let { model.open(Screen.WorkoutDetail(it.date)) }; Unit }
        fun kg(value: Double) = model.volume(value)

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                MainButton(if (model.draft == null) "Start this workout" else "Resume the workout in progress", { if (model.draft == null) model.start(template) else model.resumeWorkout() }, Modifier.fillMaxWidth().padding(Space.l).heightIn(min = 52.dp))
            }
            item {
                Section("Plan") {
                    LabelValue("Exercises", template.blocks.size.toString())
                    LabelValue("Working sets", working.toString())
                    clean.core.templateDurations(list)[template.name]?.let { LabelValue("Usually takes", clean.core.estimateText(it)) }
                    if (template.folder.isNotEmpty()) LabelValue("Folder", template.folder)
                }
            }
            item {
                Section("Exercises") {
                    // Here the lines are big enough to aim at: each opens its exercise.
                    for (line in lines) LinkRow("${line.sets} × ${line.exercise}", line.last, line.trend) { model.open(Screen.ExerciseDetail(line.exercise)) }
                    if (lines.any { it.trend.isNotEmpty() }) Text(
                        "The arrow is the estimated 1RM of the exercise's last session against the one before.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (muscles.isNotEmpty()) item {
                Section("What it trains") {
                    val palette = scaleColours
                    val most = muscles.values.max()
                    BodyFigure(muscles.mapValues { (_, v) -> shade(palette, v / most) }, { model.open(Screen.MuscleDetail(it)) }) { m ->
                        muscles[m]?.let { setsText(shortNumber(Math.round(it * 10) / 10.0)) } ?: "not trained"
                    }
                    for ((muscle, sets) in muscles.entries.sortedByDescending { it.value }) {
                        LabelValue(muscle.label, setsText(shortNumber(Math.round(sets * 10) / 10.0))) { model.open(Screen.MuscleDetail(muscle)) }
                    }
                }
            }
            if (list.isEmpty()) item { Section("History") { Text("No workout has been done under this name yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            else {
                item {
                    Section("History") {
                        val days = times.mapNotNull { it?.toLocalDate() }
                        LabelValue("Done", if (list.size == 1) "once" else "${list.size} times") { model.open(Screen.Routine(template.name)) }
                        LabelValue("First", prettyDay(list.first().date)) { model.open(Screen.WorkoutDetail(list.first().date)) }
                        LabelValue("Latest", prettyDay(list.last().date)) { model.open(Screen.WorkoutDetail(list.last().date)) }
                        if (days.size >= 2) {
                            val gap = ChronoUnit.DAYS.between(days.first(), days.last()).toDouble() / (days.size - 1)
                            LabelValue("How often", "every ${shortNumber(Math.round(gap * 10) / 10.0)} days")
                            // When it would come round again at that pace.
                            val due = ChronoUnit.DAYS.between(LocalDate.now(), days.last().plusDays(Math.round(gap)))
                            LabelValue("Next", if (due > 0) "due in $due ${if (due == 1L) "day" else "days"}" else if (due == 0L) "due today" else "${-due} ${if (due == -1L) "day" else "days"} overdue")
                        }
                        days.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }?.let { (day, n) ->
                            LabelValue("Usual day", "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())} ($n of ${days.size})")
                        }
                        times.mapNotNull { it?.hour }.sorted().let { hours -> if (hours.isNotEmpty()) LabelValue("Usual start", "around %02d:00".format(hours[hours.size / 2])) }
                        LabelValue("PRs set", shortNumber(list.sumOf { w -> recordCount(w, allPrs[w.date].orEmpty()) }))
                    }
                }
                item {
                    Section("Time and volume") {
                        val longest = list.maxBy { it.durationSeconds }
                        val shortest = list.minBy { it.durationSeconds }
                        val heaviest = volumes.indices.maxBy { volumes[it] }
                        LabelValue("Average time", duration(list.sumOf { it.durationSeconds } / list.size))
                        LabelValue("Longest", duration(longest.durationSeconds)) { model.open(Screen.WorkoutDetail(longest.date)) }
                        LabelValue("Shortest", duration(shortest.durationSeconds)) { model.open(Screen.WorkoutDetail(shortest.date)) }
                        LabelValue("Total time", duration(list.sumOf { it.durationSeconds }))
                        LabelValue("Average volume", kg(volumes.average()))
                        LabelValue("Most volume", kg(volumes[heaviest])) { model.open(Screen.WorkoutDetail(list[heaviest].date)) }
                        LabelValue("Total volume", kg(volumes.sum()))
                    }
                }
                if (list.size >= 2) item {
                    Section("Last time against the time before") {
                        val a = list[list.size - 2]
                        val b = list.last()
                        val volume = volumes.last() - volumes[volumes.size - 2]
                        val seconds = b.durationSeconds - a.durationSeconds
                        val sets = { w: clean.core.Workout -> shortNumber(w.workingCount) }
                        LabelValue("Volume", (if (volume >= 0) "+" else "−") + kg(Math.abs(volume)))
                        LabelValue("Time", (if (seconds >= 0) "+" else "−") + duration(Math.abs(seconds)))
                        LabelValue("Working sets", "${sets(a)} → ${sets(b)}")
                        LabelValue("PRs", "${shortNumber(recordCount(a, allPrs[a.date].orEmpty()))} → ${shortNumber(recordCount(b, allPrs[b.date].orEmpty()))}")
                    }
                }
                item {
                    Section("Volume") {
                        val points = times.zip(volumes).mapNotNull { (t, v) -> t?.let { it.toLocalDate() to v } }
                        if (points.size < 2) Text("Not enough workouts to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LineChart(points, "kg", onTap = openOn)
                    }
                }
                item {
                    Section("Time") {
                        val points = times.zip(list).mapNotNull { (t, w) -> t?.let { it.toLocalDate() to w.durationSeconds / 60.0 } }
                        if (points.size < 2) Text("Not enough workouts to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LineChart(points, "min", onTap = openOn)
                    }
                }
                item {
                    Section("Workouts", spacing = Space.xs) {
                        for ((i, w) in list.withIndex().reversed().take(12)) {
                            LinkRow(prettyDate(w.date), duration(w.durationSeconds), kg(volumes[i])) { model.open(Screen.WorkoutDetail(w.date)) }
                        }
                        if (list.size > 12) TextButton(onClick = { model.open(Screen.Routine(template.name)) }) { Text("All ${list.size} workouts") }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}
