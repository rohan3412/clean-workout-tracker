package clean.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.core.Muscle
import clean.core.PickKind
import clean.core.Pick
import clean.core.ProgramDay
import clean.core.SuggestTemplate
import clean.core.TemplateRef
import clean.core.Workout
import clean.core.fatigueWord
import clean.core.firstFittingWeek
import clean.core.nextTemplate
import clean.core.suggestedWeek
import clean.core.versionFor
import clean.core.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun weekdayName(index: Int): String = DayOfWeek.of(index % 7 + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())

fun Template.ref() = TemplateRef(id, name, folder)

/** The first plan offered when there is none: the week the last eight weeks show (see [suggestedWeek]). */
private fun prefill(model: AppModel, templates: List<Template>, workouts: List<Workout>): List<ProgramDay?> {
    val ids = suggestedWeek(workouts, templates.map { SuggestTemplate(it.ref(), model.snapshotOf(it).exercises) }, CURRENT_SPLIT, LocalDate.now(), model::overallOf)
    return ids.map { id -> templates.firstOrNull { it.id == id }?.let(model::snapshotOf) }
}

/**
 * The weekly program: a template or rest for each day. A week counts as on program when every training day has been done
 * by a different workout that week, on any day, with about a tenth of its exercises added or left out.
 */
@Composable
fun ProgramScreen(model: AppModel) {
    val templates by model.templates.collectAsState()
    val workouts by model.workouts.collectAsState()
    val versions by model.program.collectAsState()
    val thisWeek = weekStart(LocalDate.now())
    var days by remember { mutableStateOf(versionFor(versions, thisWeek)?.days ?: versions.lastOrNull()?.days ?: prefill(model, templates, workouts)) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var backdate by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }

    // The plan as it would be saved now: a template that still exists is snapshotted again, one that is gone keeps its stored snapshot.
    val final = days.map { d -> d?.let { templates.firstOrNull { t -> t.id == it.templateId }?.let(model::snapshotOf) ?: it } }
    val fitting = remember(final, workouts) {
        if (final.all { it == null }) null else firstFittingWeek(final, workouts, LocalDate.now(), model::overallOf)?.takeIf { it < thisWeek }
    }

    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar("Program", model::back) {
            TextButton(enabled = final.any { it != null }, onClick = {
                model.saveProgram(final, if (backdate && fitting != null) fitting else thisWeek)
                model.back()
            }) { Text("Save") }
        }
    }) { padding ->
        SettingsFloat(model, Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = settingsFloatSpace(model))) {
            item {
                Text(
                    "Your week as templates and rest days. A week is on program when every training day was done by a different workout that week, on any day, with about a tenth of its exercises swapped.",
                    Modifier.padding(Space.l), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(7) { slot ->
                val day = final[slot]
                val live = day != null && templates.any { it.id == day.templateId }
                Row(
                    Modifier.fillMaxWidth().clickable { picking = slot }.heightIn(min = 56.dp).padding(horizontal = Space.l, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(weekdayName(slot), Modifier.width(112.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(if (day == null) "Rest" else if (live) day.label else "${day.label} (template removed)", fontWeight = if (day == null) FontWeight.Normal else FontWeight.Bold)
                        if (day != null) Text(plural(day.exercises.size, "exercise"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Caption("Starts")
                    if (fitting == null) Text("This week", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Choice(listOf(false, true), backdate, { if (it) "From ${prettyDay(fitting)} (the first week this plan fits)" else "This week" }) { backdate = it }
                    if (fitting != null) Text("Starting earlier counts the weeks since then that already followed this plan toward the streak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (versions.isNotEmpty()) item { DangerButton("Stop following a program", { stopping = true }, Modifier.fillMaxWidth().padding(horizontal = Space.l)) }
        }
        }
    }

    picking?.let { slot ->
        Sheet({ picking = null }, weekdayName(slot)) {
            // The current split first, then the other folders by name, then the templates in none.
            val groups = templates.groupBy { it.folder }.toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { !it.equals(CURRENT_SPLIT, ignoreCase = true) }.thenBy { it.lowercase() })
            for ((folder, list) in groups) {
                if (groups.size > 1) Caption(folder.ifEmpty { "No folder" }, Modifier.padding(horizontal = Space.xl, vertical = Space.xs))
                for (t in list) SheetRow(t.name, value = plural(model.snapshotOf(t).exercises.size, "exercise")) {
                    days = days.toMutableList().also { it[slot] = model.snapshotOf(t) }
                    picking = null
                }
            }
            SheetGap()
            SheetRow("Rest") { days = days.toMutableList().also { it[slot] = null }; picking = null }
        }
    }
    if (stopping) Confirm(
        "Stop following a program?", "The program streak and its badges go with it. The weekly target and its streak stay.", "Stop",
        { model.stopProgram(); model.back() }, { stopping = false },
    )
}

/** "Chest and triceps ready", or "Quads still recovering": the main muscles of [template] against the fatigue figures; null when there are none to say. */
@Composable
private fun readiness(model: AppModel, template: Template): String? {
    val figures by model.muscleFigures.collectAsState()
    val fatigue = figures?.fatigue ?: return null
    val mains = template.blocks.flatMap { model.musclesOf(it.exercise).primary }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(3)
    if (mains.isEmpty()) return null
    val tired = mains.filter { fatigueWord(fatigue[it] ?: 0.0) != "ready" }
    fun names(list: List<Muscle>) = list.mapIndexed { i, m -> if (i == 0) m.label else m.label.lowercase() }.let { if (it.size < 2) it.joinToString() else it.dropLast(1).joinToString(", ") + " and " + it.last() }
    return if (tired.isEmpty()) "${names(mains)} ready" else "${names(tired)} still recovering"
}

internal fun pickReason(pick: Pick): String = when (pick.kind) {
    PickKind.TODAY, PickKind.NEXT_WEEK -> "${weekdayName(pick.date.dayOfWeek.value - 1)} on your program"
    PickKind.EARLIER -> "Moved from ${weekdayName(pick.date.dayOfWeek.value - 1)}"
    PickKind.LATER -> "Brought forward from ${weekdayName(pick.date.dayOfWeek.value - 1)}"
}

/**
 * Today on the Home tab, its hero card: with a program, its template for today (or the next one, on a rest day); without,
 * the template that comes next in the rotation, else a quick start. The card opens the template's page; only its one main
 * button (Start workout; a workout in progress takes the card over with Resume workout) and "Not this one" take their own taps.
 */
@Composable
fun TodayCard(model: AppModel, templates: List<Template>, workouts: List<Workout>) {
    val plan by model.programToday.collectAsState()
    // Fatigue depends on the time now, which nothing stored changes.
    LaunchedEffect(Unit) { model.refreshMuscles() }
    val skipped = model.skippedTemplates
    val today = LocalDate.now()
    val current = plan

    // A workout in progress is what today is about, whatever was planned.
    model.draft?.let { draft ->
        Section("", hero = true, onClick = { model.resumeWorkout() }) {
            Caption("In progress")
            Text(draft.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(plural(draft.blocks.size, "exercise"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            MainButton("Resume workout", { model.resumeWorkout() }, Modifier.fillMaxWidth().heightIn(min = 52.dp))
        }
        return
    }

    if (current == null) {
        val next = nextTemplate(templates.map { it.ref() }, workouts, CURRENT_SPLIT, today, skipped)
        val template = next?.let { n -> templates.firstOrNull { it.id == n.template.id } }
        if (next == null || template == null) TodayRest(model, "Quick start", "An empty workout; add exercises as you go.", null)
        else TodayBody(model, "Up next", template, next.reason.replaceFirstChar { it.uppercase() }, templates.size > 1) { start ->
            if (start) model.start(template) else model.skippedTemplates = skipped + template.id
        }
        return
    }

    val open = current.candidates.filter { it.day.templateId !in skipped }.ifEmpty { current.candidates }
    val pick = open.firstOrNull()
    val pickTemplate = pick?.let { p -> templates.firstOrNull { it.id == p.day.templateId } }
    val todayDay = current.today.day
    if (todayDay != null && !current.done && pick != null) {
        val reason = pickReason(pick)
        if (pickTemplate == null) TodayRest(model, "${pick.day.label} (template removed)", reason, null)
        else TodayBody(model, "Today", pickTemplate, reason, current.candidates.size > 1) { start ->
            if (start) model.start(pickTemplate) else model.skippedTemplates = skipped + pickTemplate.id
        }
        return
    }
    val head = if (todayDay != null) "${todayDay.label} · done" else "Rest day"
    val next = pick?.let { p ->
        val day = weekdayName(p.date.dayOfWeek.value - 1)
        if (p.kind == PickKind.EARLIER) "Still to do: ${p.day.label}, from $day" else "Next: ${p.day.label}, $day"
    }
    TodayRest(model, head, next, pickTemplate)
}

@Composable
private fun TodayBody(model: AppModel, caption: String, template: Template, reason: String, canSkip: Boolean, onTap: (start: Boolean) -> Unit) {
    Section("", hero = true, onClick = { model.open(Screen.TemplateStats(template.id)) }) {
        Caption(caption)
        val colors by model.templateColors.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            colors.ofId(template.id)?.let { TemplateDot(it, size = 12.dp) }
            MorphText(androidx.compose.ui.text.AnnotatedString(template.name), "title", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        val workouts by model.workouts.collectAsState()
        val estimate = remember(workouts, template.name) { clean.core.templateDurations(workouts)[template.name] }
        Text(listOfNotNull(reason, plural(template.blocks.size, "exercise"), estimate?.let { clean.core.estimateText(it) }).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        DeloadHint(model)
        readiness(model, template)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            MainButton("Start workout", { onTap(true) }, Modifier.weight(1f).heightIn(min = 52.dp))
            if (canSkip) QuietButton("Not this one", { onTap(false) })
        }
    }
}

/**
 * A rest day, a day already done, or no template at all: what comes next, and the same main button, which starts the
 * next template anyway or an empty workout. The card opens the program (or, with no templates, the Workout tab).
 */
@Composable
private fun TodayRest(model: AppModel, head: String, next: String?, template: Template?) {
    Section("", hero = true, onClick = { if (model.program.value.isEmpty()) model.tab = Tab.Workout else model.open(Screen.Program) }) {
        Caption("Today")
        Text(head, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (next != null) Text(next, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DeloadHint(model)
        MainButton(
            "Start workout", { if (template != null) model.start(template) else model.startWorkout() },
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    }
}
