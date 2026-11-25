package clean.app

import clean.core.time.*
import clean.app.ui.theme.LocalLook
import clean.core.bestRound
import clean.core.bestSetOf
import clean.core.recordCount
import clean.core.roundLine
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import clean.app.ui.theme.LocalCleanColors
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
import kotlinx.datetime.LocalDate

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
    }, bottomBar = {
        // Start is the page's one main button, at the bottom.
        if (template != null) MainButton(
            if (model.draft == null) "Start this workout" else "Resume the workout in progress", { if (model.draft == null) model.start(template) else model.resumeWorkout() },
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.l).heightIn(min = 52.dp),
        )
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

        val seen = remember { HashSet<String>() }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                // Round 24: the template in three figures; each is as tall as its content.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.l, vertical = Space.s).settleIn(0), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Tile("Exercises", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp) { TileNumber(countUpOnce(seen, "exercises", 0, template.blocks.size.toDouble()) { roundHalfUp(it).toString() }) }
                    Tile("Sets", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp) { TileNumber(countUpOnce(seen, "sets", 1, working.toDouble()) { roundHalfUp(it).toString() }) }
                    Tile("Done", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp, onClick = if (list.isEmpty()) null else ({ model.open(Screen.Routine(template.name)) })) {
                        TileNumber(countUpOnce(seen, "done", 2, list.size.toDouble()) { roundHalfUp(it).toString() }, if (list.size == 1) "time" else "times")
                    }
                }
            }
            val usually = clean.core.templateDurations(list)[template.name]
            if (usually != null || template.folder.isNotEmpty()) item {
                Section("Plan", Modifier.settleIn(1)) {
                    usually?.let { LabelValue("Usually takes", clean.core.estimateText(it)) }
                    if (template.folder.isNotEmpty()) LabelValue("Folder", template.folder)
                }
            }
            item {
                Section("Exercises", Modifier.settleIn(2)) {
                    // Here the lines are big enough to aim at: each opens its exercise. The arrow takes the meaning colours (up good, down bad, level quiet).
                    val meaning = LocalCleanColors.current
                    fun tone(trend: String) = when (trend) { "↑" -> meaning.done; "↓" -> meaning.failure; else -> null }
                    for (line in lines) Row(
                        Modifier.fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(line.exercise)) }.padding(vertical = Space.xs)
                            .semantics(mergeDescendants = true) { contentDescription = "${line.sets} × ${line.exercise}, ${line.last}" + trendWords(line.trend)?.let { ", estimated 1RM $it" }.orEmpty() },
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${line.sets} × ${line.exercise}")
                            Text(line.last, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(line.trend, Modifier.padding(start = Space.s), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tone(line.trend) ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lines.any { it.trend.isNotEmpty() }) Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        Text("The arrow compares the estimated 1RM of the exercise's last session with the one before.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            for ((arrow, word) in listOf("↑" to "up", "↓" to "down", "→" to "level")) Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = tone(arrow) ?: MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)) { append(arrow) }
                                    append(" $word")
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (muscles.isNotEmpty()) item {
                Section("What it trains", Modifier.settleIn(3)) {
                    val palette = scaleColours
                    val most = muscles.values.max()
                    BodyFigure(muscles.mapValues { (_, v) -> shade(palette, v / most) }, { model.open(Screen.MuscleDetail(it)) }) { m ->
                        muscles[m]?.let { setsText(shortNumber(roundHalfUp(it * 10) / 10.0)) } ?: "not trained"
                    }
                    for ((muscle, sets) in muscles.entries.sortedByDescending { it.value }) {
                        LabelValue(muscle.label, setsText(shortNumber(roundHalfUp(sets * 10) / 10.0))) { model.open(Screen.MuscleDetail(muscle)) }
                    }
                }
            }
            if (list.isEmpty()) item { Section("History") { Text("No workout has been done under this name yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            else {
                item {
                    Section("History", Modifier.settleIn(4)) {
                        val days = times.mapNotNull { it?.toLocalDate() }
                        LabelValue("Done", if (list.size == 1) "once" else "${list.size} times") { model.open(Screen.Routine(template.name)) }
                        LabelValue("First", prettyDay(list.first().date)) { model.open(Screen.WorkoutDetail(list.first().date)) }
                        LabelValue("Latest", prettyDay(list.last().date)) { model.open(Screen.WorkoutDetail(list.last().date)) }
                        if (days.size >= 2) {
                            val gap = ChronoUnit.DAYS.between(days.first(), days.last()).toDouble() / (days.size - 1)
                            LabelValue("How often", "every ${shortNumber(roundHalfUp(gap * 10) / 10.0)} days")
                            // When it would come round again at that pace.
                            val due = ChronoUnit.DAYS.between(LocalDate.now(), days.last().plusDays(roundHalfUp(gap)))
                            LabelValue("Next", if (due > 0) "due in $due ${if (due == 1L) "day" else "days"}" else if (due == 0L) "due today" else "${-due} ${if (due == -1L) "day" else "days"} overdue")
                        }
                        days.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }?.let { (day, n) ->
                            LabelValue("Usual day", "${day.localName(true)} ($n of ${days.size})")
                        }
                        times.mapNotNull { it?.hour }.sorted().let { hours -> if (hours.isNotEmpty()) LabelValue("Usual start", "around ${clean.core.zeroPad(hours[hours.size / 2].toLong(), 2)}:00") }
                        LabelValue("PRs set", countUpOnce(seen, "prsset", 2, list.sumOf { w -> recordCount(w, allPrs[w.date].orEmpty()) }) { shortNumber(roundHalfUp(it * 2) / 2.0) })
                    }
                }
                item {
                    Section("Time and volume", Modifier.settleIn(5)) {
                        val longest = list.maxBy { it.durationSeconds }
                        val shortest = list.minBy { it.durationSeconds }
                        val heaviest = volumes.indices.maxBy { volumes[it] }
                        LabelValue("Average time", duration(list.sumOf { it.durationSeconds } / list.size))
                        LabelValue("Longest", duration(longest.durationSeconds)) { model.open(Screen.WorkoutDetail(longest.date)) }
                        LabelValue("Shortest", duration(shortest.durationSeconds)) { model.open(Screen.WorkoutDetail(shortest.date)) }
                        LabelValue("Total time", countUpOnce(seen, "totaltime", 3, list.sumOf { it.durationSeconds }.toDouble()) { duration(roundHalfUp(it)) })
                        LabelValue("Average volume", kg(volumes.average()))
                        LabelValue("Most volume", kg(volumes[heaviest])) { model.open(Screen.WorkoutDetail(list[heaviest].date)) }
                        LabelValue("Total volume", countUpOnce(seen, "totalvolume", 4, volumes.sum()) { kg(it) })
                    }
                }
                if (list.size >= 2) item {
                    Section("Last time against the time before", Modifier.settleIn(6)) {
                        val a = list[list.size - 2]
                        val b = list.last()
                        val volume = volumes.last() - volumes[volumes.size - 2]
                        val seconds = b.durationSeconds - a.durationSeconds
                        val sets = { w: clean.core.Workout -> shortNumber(w.workingCount) }
                        LabelValue("Volume", (if (volume >= 0) "+" else "−") + kg(kotlin.math.abs(volume)))
                        LabelValue("Time", (if (seconds >= 0) "+" else "−") + duration(kotlin.math.abs(seconds)))
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

/** What a template line's arrow says, for a screen reader; null when it has none. */
private fun trendWords(trend: String): String? = when (trend) { "↑" -> "up on the session before"; "↓" -> "down on the session before"; "→" -> "level with the session before"; else -> null }
