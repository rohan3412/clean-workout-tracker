package clean.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.numbers
import clean.core.changePercent
import clean.core.progressOf
import clean.core.sessionsByExercise
import clean.core.yearAgoLift
import clean.core.yearAgoTotals
import clean.core.FATIGUE_RECOVERING
import clean.core.Muscle
import clean.core.PrKind
import clean.core.muscleSets
import clean.core.nextTemplate
import clean.core.shortNumber
import clean.core.volumeOf
import clean.core.weekStart
import java.time.LocalDate

/*
 * The Home cards that start hidden (added 2026-10-16). Each is built from the pieces the other cards use (Tile, Section, LinkRow, BodyFigure) and opens the
 * page that explains it. A card with nothing to show says so in a quiet line.
 */

/** The sets of each muscle from this Monday to now; the Body page's counts, but always for the current week. */
@Composable
private fun weekMuscleSets(model: AppModel): Map<Muscle, Double> {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    return remember(workouts, settings) {
        val monday = weekStart(LocalDate.now()).toString()
        muscleSets(workouts.filter { it.day >= monday }, null, model::musclesOf)
    }
}

/** What was lifted this calendar month, with the five finished months before it as the line. Opens the weekly trends. */
@Composable
internal fun MonthVolumeTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val months = remember(workouts, settings, bodyweights) {
        val first = LocalDate.now().withDayOfMonth(1)
        (5 downTo 0).map { first.minusMonths(it.toLong()).toString().take(7) }.map { month -> workouts.filter { it.day.startsWith(month) }.sumOf { volumeOf(it, model::loadOf) } }
    }
    Tile("Monthly volume", model.volume(months.last()), tileModifier(), second = "This month", spark = months.dropLast(1).takeIf { m -> m.any { it > 0 } }, sample = true) { model.open(Screen.Trends) }
}

/** The longest run of weeks that met the weekly target, ever. Opens the trophies. */
@Composable
internal fun LongestStreakTile(model: AppModel) {
    val consistency by model.consistency.collectAsState()
    val streak = consistency?.streak
    val best = streak?.best ?: 0
    Tile(
        "Longest streak", best.toString(), tileModifier(),
        second = when {
            best == 0 -> "No week has met the target yet"
            streak?.length == best -> "weeks in a row, now"
            else -> "weeks in a row"
        },
    ) { model.open(Screen.Trophies) }
}

/** The body figure shaded by the sets of this week so far, with the muscles worked in words. Opens the Body page on Balance. */
@Composable
internal fun WeekFigureCard(model: AppModel, half: Boolean) {
    val sets = weekMuscleSets(model)
    val palette = LocalLook.current.scale
    val max = sets.maxOfOrNull { it.value } ?: 0.0
    val colours = remember(sets, palette) { sets.filter { it.value > 0 }.mapValues { (_, v) -> shade(palette, if (max > 0) v / max else 0.0) } }
    val worked = sets.filter { it.value > 0 }.entries.sortedByDescending { it.value }.map { it.key }
    val words = if (worked.isEmpty()) "No sets yet this week" else musclesText(worked, if (half) 3 else 6)
    Section("Trained this week", onClick = { model.openBody("Balance") }) {
        if (worked.isEmpty()) EmptyPreview("Log a workout and the muscles you train this week shade in here.") { PreviewSamples.Figure() }
        else if (half) {
            BodyFigure(colours, interactive = false, description = "Body figure, muscles trained this week")
            Text(words, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Column(Modifier.weight(1f)) { BodyFigure(colours, interactive = false, description = "Body figure, muscles trained this week") }
            Text(words, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The muscles with the most sets this week, as a bar and a number each; a row opens that muscle. */
@Composable
internal fun TopMusclesCard(model: AppModel, half: Boolean) {
    val sets = weekMuscleSets(model)
    val palette = LocalLook.current.scale
    val top = sets.filter { it.value > 0 }.entries.sortedByDescending { it.value }.take(if (half) 3 else 5)
    val max = top.firstOrNull()?.value ?: 1.0
    Section("Top muscles this week", spacing = Space.xs, onClick = { model.openBody("Balance") }) {
        if (top.isEmpty()) EmptyPreview("Log a set this week and your most trained muscles line up here.") { PreviewSamples.Rows() }
        for ((muscle, value) in top) {
            val count = shortNumber(Math.round(value * 10) / 10.0)
            Row(
                Modifier.fillMaxWidth().clickable { model.open(Screen.MuscleDetail(muscle)) }.padding(vertical = Space.xs)
                    .semantics(mergeDescendants = true) { contentDescription = "${muscle.label}, ${setsText(count)}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(muscle.label, maxLines = 1)
                    LinearProgressIndicator(
                        progress = { (value / max).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = shade(palette, value / max), trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {},
                    )
                }
                Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The last PRs, newest first: the exercise, the day, the set and (wide) the kinds. A row opens that exercise's PRs; the card opens the best lifts. */
@Composable
internal fun RecentPrsCard(model: AppModel, half: Boolean) {
    val workouts by model.workouts.collectAsState()
    val prs by model.prs.collectAsState()
    val rows = remember(workouts, prs, half) { prRows(workouts.takeLast(15), prs).reversed().take(if (half) 3 else 5) }
    Section("Recent PRs", spacing = Space.xxs, onClick = { model.open(Screen.PersonalBests) }) {
        if (rows.isEmpty()) EmptyPreview("Beat an exercise's best, or log a first workout of one, to earn a PR.") { PreviewSamples.Rows(56.dp, PreviewSamples.ROWS.take(3)) }
        // An exercise's name is on Personal bests' page too (one line per exercise there), so the first row of each travels into it; a second PR of one lift just fades.
        val seen = HashSet<String>()
        for (row in rows) {
            val kinds = PrKind.entries.filter { it in row.kinds }.joinToString(" · ") { it.label }
            val set = setValues(row.set, model.unitOf(row.exercise))
            LinkRow(
                row.exercise, "${prettyDay(row.date)} · ${row.sidePrefix}$set", if (half) "" else kinds,
                spoken = "${row.exercise}, ${prettyDay(row.date)}, ${row.sideWord}set $set, $kinds",
                titleMorph = if (seen.add(row.exercise)) RowMorph("pr:name", row.exercise) else null,
            ) { model.open(Screen.ExerciseDetail(row.exercise, tab = 3)) }
        }
    }
}

/** The last three workouts as rows: name, day, length and volume. A row opens that workout; the card opens the history. */
@Composable
internal fun RecentWorkoutsCard(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val last = workouts.takeLast(3).reversed()
    val volumes = remember(last, settings, bodyweights) { last.map { volumeOf(it, model::loadOf) } }
    Section("Recent workouts", spacing = Space.xxs, onClick = { model.openHistory() }) {
        if (last.isEmpty()) EmptyPreview("Finish a workout and it shows up here.", "Start workout", { model.stack.clear(); model.tab = Tab.Workout }) { PreviewSamples.Rows(56.dp, PreviewSamples.ROWS.take(3)) }
        last.forEachIndexed { i, w ->
            // The name and the day travel onto the same workout's card on the History page (see WorkoutCard).
            LinkRow(
                w.name, "${prettyDay(w.date)} · ${duration(w.durationSeconds)}", model.volume(volumes[i]),
                titleMorph = RowMorph("row:name", w.date), dateMorph = RowMorph("row:date", w.date),
            ) { model.open(Screen.WorkoutDetail(w.date)) }
        }
    }
}

/**
 * The estimated 1RM of a lift the user picks (kv `home.lift:<profileId>`), as OneRmTile shows one. The card opens that lift's Charts tab; its "+" opens the
 * exercise list to pick or change the lift. With none picked the whole card is the way to pick.
 */
@Composable
internal fun ChosenLiftTile(model: AppModel) {
    val options by model.options.collectAsState()
    val id by model.activeId.collectAsState()
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val name = options["home.lift:$id"]
    var picking by remember { mutableStateOf(false) }
    if (picking) ExercisePicker(model, workouts, { picked -> picking = false; model.setOption("home.lift:$id", picked) }, { picking = false })
    if (name == null) {
        Tile("1RM", NONE, tileModifier(), second = "Choose a lift") { picking = true }
        return
    }
    val estimates = remember(workouts, settings, bodyweights, rule, name) {
        progressOf(model.sessionsIn(workouts, name), rule, model.loadTypeOf(name), bodyweights::at).mapNotNull { it.estimated1Rm }
    }
    val unit = model.unitOf(name)
    Tile(
        "1RM", estimates.lastOrNull()?.let { showEstimate(it, unit) } ?: NONE, tileModifier(), second = if (estimates.size < 2) "Log ${2 - estimates.size} more ${if (estimates.isEmpty()) "sessions" else "session"} of $name" else "$name · $unit",
        spark = estimates.takeLast(24), onAdd = { picking = true }, addLabel = "Change lift", sample = true,
    ) { model.open(Screen.ExerciseDetail(name, tab = 2)) }
}

/** The volume of the last eight weeks as bars, this week in the accent and the rest faint. Opens the weekly trends. */
@Composable
internal fun WeeklyBarsCard(model: AppModel) {
    val weeks = recentWeeks(model).takeLast(8)
    val thisMonday = weekStart(LocalDate.now())
    val bar = MaterialTheme.colorScheme.primary
    val faint = bar.copy(alpha = 0.35f)
    Section("Weekly volume", onClick = { model.open(Screen.Trends) }) {
        if (weeks.isEmpty()) EmptyPreview("Log a workout and each week's volume gets a bar.") { PreviewSamples.Bars() }
        else {
            val now = weeks.lastOrNull { it.monday == thisMonday }?.volume ?: 0.0
            Text("This week ${model.volume(now)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Weekly volume, last ${weeks.size} weeks. This week ${model.volume(now)}" }) {
                val max = weeks.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
                val slot = size.width / weeks.size
                val width = slot * 0.6f
                weeks.forEachIndexed { i, w ->
                    val height = maxOf((w.volume / max).toFloat() * size.height, 2.dp.toPx())
                    drawRoundRect(if (w.monday == thisMonday) bar else faint, Offset(i * slot + (slot - width) / 2, size.height - height), Size(width, height), CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
}

/** The template that comes next, as the Today card works it out (the program's pick, else the rotation), with Start. Opens the template's page. */
@Composable
internal fun NextWorkoutCard(model: AppModel) {
    val templates by model.templates.collectAsState()
    val workouts by model.workouts.collectAsState()
    val plan by model.programToday.collectAsState()
    val skipped = model.skippedTemplates
    val draft = model.draft
    if (draft != null) {
        Section("", onClick = { model.resumeWorkout() }) {
            Caption("In progress")
            Text(draft.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            QuietButton("Resume workout", { model.resumeWorkout() }, Modifier.fillMaxWidth())
        }
        return
    }
    val pick = plan?.candidates?.firstOrNull { it.day.templateId !in skipped }
    val fromProgram = pick?.let { p -> templates.firstOrNull { it.id == p.day.templateId } }
    val rotation = if (fromProgram == null) nextTemplate(templates.map { it.ref() }, workouts, CURRENT_SPLIT, LocalDate.now(), skipped) else null
    val template = fromProgram ?: rotation?.let { n -> templates.firstOrNull { it.id == n.template.id } }
    if (template == null) {
        Section("Next workout") { EmptyPreview("Make a template and the workout that comes next appears here.") { PreviewSamples.Rows(56.dp, PreviewSamples.ROWS.take(3)) } }
        return
    }
    val reason = if (fromProgram != null && pick != null) pickReason(pick) else rotation?.reason?.replaceFirstChar { it.uppercase() }.orEmpty()
    Section("", onClick = { model.open(Screen.TemplateStats(template.id)) }) {
        Caption("Next workout")
        MorphText(androidx.compose.ui.text.AnnotatedString(template.name), "title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(listOf(reason, plural(template.blocks.size, "exercise")).filter { it.isNotEmpty() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        QuietButton("Start", { model.start(template) }, Modifier.fillMaxWidth())
    }
}

/** How long workouts have been over the last 30 days, with the weekly averages as the line. Opens the weekly trends. */
@Composable
internal fun AverageDurationTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val (average, line) = remember(workouts) {
        val today = LocalDate.now()
        val timed = workouts.filter { it.durationSeconds > 0 }
        val recent = timed.filter { it.day >= today.minusDays(30).toString() }
        val weekly = timed.filter { it.day >= weekStart(today).minusWeeks(11).toString() }
            .groupBy { weekStart(LocalDate.parse(it.day)) }.toSortedMap().values.map { w -> w.sumOf { it.durationSeconds }.toDouble() / w.size }
        recent.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.durationSeconds } / r.size } to weekly
    }
    Tile(
        "Avg duration", average?.let { duration(it) } ?: NONE, tileModifier(), second = if (average == null) "Finish a workout to see this" else "last 30 days", spark = line, sample = true,
    ) { model.open(Screen.Trends) }
}

/** The body figure in the fatigue colours, with the tired muscles named under it. Opens the Body page on Fatigue. */
@Composable
internal fun FatigueGlanceCard(model: AppModel, half: Boolean) {
    val figures by model.muscleFigures.collectAsState()
    // Fatigue depends on the time now, which nothing stored changes.
    LaunchedEffect(Unit) { model.refreshMuscles() }
    val logged by model.workouts.collectAsState()
    if (logged.isEmpty()) {
        Section("Fatigue") { EmptyPreview("Log a workout and the muscles that are tired show up here.") { PreviewSamples.Figure() } }
        return
    }
    val fatigue = figures?.fatigue ?: return
    val meaning = LocalCleanColors.current
    val colours = remember(fatigue, meaning) { Muscle.entries.associateWith { fatigueColour(fatigue[it] ?: 0.0, meaning) } }
    val tired = Muscle.entries.filter { (fatigue[it] ?: 0.0) >= FATIGUE_RECOVERING }.sortedByDescending { fatigue[it] }
    val words = if (tired.isEmpty()) "All ready" else "${musclesText(tired, if (half) 2 else 4)} fatigued"
    Section("Fatigue", onClick = { model.openBody("Fatigue") }) {
        if (half) {
            BodyFigure(colours, interactive = false, description = "Body figure, fatigue view")
            Text(words, fontWeight = FontWeight.Bold)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Column(Modifier.weight(1f)) { BodyFigure(colours, interactive = false, description = "Body figure, fatigue view") }
            Text(words, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A lift now against about a year ago (the best estimated 1RM near then and over the last 30 days), or when no lift has both, the volume of the 30 days
 * around a year ago against the last 30 days. It shows only when the change is big enough (5 % for a lift, 20 % for the totals) and otherwise takes no
 * place. Opens the lift's Charts tab, or the weekly trends for the totals.
 */
@Composable
internal fun YearAgoCard(model: AppModel, half: Boolean) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val today = LocalDate.now()
    val lift = remember(workouts, settings, bodyweights, rule, today) {
        yearAgoLift(today, sessionsByExercise(workouts)) { name, sessions -> progressOf(sessions, rule, model.loadTypeOf(name), bodyweights::at) }
    }
    val totals = remember(workouts, settings, bodyweights, today) { if (lift == null) yearAgoTotals(today, workouts) { volumeOf(it, model::loadOf) } else null }
    val change = lift?.change ?: totals?.change ?: return
    val shade = if (change > 0) LocalCleanColors.current.done else MaterialTheme.colorScheme.onSurfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (lift != null) {
        val unit = model.unitOf(lift.exercise)
        val then = showWeight(lift.then, unit)
        val now = showWeight(lift.now, unit)
        val date = prettyDay(lift.thenDate)
        Section(
            "A year ago", onClick = { model.open(Screen.ExerciseDetail(lift.exercise, tab = 2)) },
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "A year ago, ${lift.exercise}. Then $then $unit on $date, now $now $unit, ${changePercent(change)}" },
        ) {
            Text(lift.exercise, maxLines = 1)
            if (half) {
                Text("Then $then $unit", color = muted)
                Text("Now $now $unit", fontWeight = FontWeight.SemiBold)
                Text(date, style = MaterialTheme.typography.bodySmall, color = muted)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Then $then $unit", color = muted)
                    Text("Now $now $unit", fontWeight = FontWeight.SemiBold)
                    Text(date, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (lift.series.size >= 2) Sparkline(lift.series.takeLast(24), Modifier.weight(1f))
            }
            Text(changePercent(change), style = MaterialTheme.typography.titleLarge.numbers(), fontWeight = FontWeight.Bold, color = shade)
        }
    } else if (totals != null) {
        Section(
            "A year ago", onClick = { model.open(Screen.Trends) },
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "A year ago. Then ${totals.thenWorkouts} workouts, ${model.volume(totals.thenVolume)}. Now ${totals.nowWorkouts} workouts, ${model.volume(totals.nowVolume)}. ${changePercent(change)} in volume"
            },
        ) {
            Text("Last 30 days, volume", color = muted)
            Text("Then ${model.volume(totals.thenVolume)}", color = muted)
            Text("Now ${model.volume(totals.nowVolume)}", fontWeight = FontWeight.SemiBold)
            Text(changePercent(change), style = MaterialTheme.typography.titleLarge.numbers(), fontWeight = FontWeight.Bold, color = shade)
        }
    }
}
