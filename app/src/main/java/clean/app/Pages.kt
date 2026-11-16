package clean.app

import androidx.compose.foundation.layout.PaddingValues
import clean.app.ui.theme.LocalLook
import clean.core.records
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import clean.core.Muscle
import clean.core.fatigueWord
import androidx.compose.ui.graphics.graphicsLayer
import clean.core.Earned
import clean.core.LoadType
import clean.core.SetType
import clean.core.Award
import clean.core.ProgramOutcome
import clean.core.earnedBy
import clean.core.programOutcome
import clean.core.weekStart
import clean.app.ui.theme.LocalCleanColors
import clean.core.RangeState
import clean.core.goalReachedOn
import clean.core.parseGoal
import clean.core.weeklySetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import clean.app.ui.theme.SmallShape
import clean.core.recordsOf
import clean.core.progressOf
import clean.core.Progress
import clean.core.NoteItem
import clean.core.KG_PER_LB
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import clean.core.volumeByDay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.core.PrKind
import clean.core.Side
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import clean.core.imbalanceOf
import clean.core.sentence
import clean.core.SetItem
import clean.core.Workout
import clean.core.muscleSets
import clean.core.secondsByDay
import clean.core.shortNumber
import clean.core.volumeOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** What a [LinkRow]'s title (or the first part of its line, the date) is on the page its card opens: the same role and [id] there make it one text that travels (see [MorphText]). */
class RowMorph(val role: String, val id: String)

/**
 * A row that leads somewhere: a title with a line under it, and a value on the right. [titleMorph] makes the title travel into the same
 * words on the page the row's card opens; [dateMorph] does that for the line's first part, the words before its first " · ".
 */
@Composable
fun LinkRow(title: String, subtitle: String, value: String, spoken: String? = null, modifier: Modifier = Modifier, titleMorph: RowMorph? = null, dateMorph: RowMorph? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).then(if (spoken != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spoken } else Modifier).padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (titleMorph != null) MorphText(androidx.compose.ui.text.AnnotatedString(title), titleMorph.role, id = titleMorph.id) else Text(title)
            if (subtitle.isNotEmpty()) {
                val small = MaterialTheme.typography.bodySmall
                val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                val cut = subtitle.indexOf(" · ")
                if (dateMorph != null && cut > 0) Row {
                    MorphText(androidx.compose.ui.text.AnnotatedString(subtitle.substring(0, cut)), dateMorph.role, style = small, color = quiet, id = dateMorph.id)
                    Text(subtitle.substring(cut), style = small, color = quiet)
                } else Text(subtitle, style = small, color = quiet)
            }
        }
        Text(value, Modifier.padding(start = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One set that set PRs. A set of a left and right block has its [side] and counts [weight] 0.5 in a count of PRs. */
class PrRow(val date: String, val exercise: String, val set: SetItem, val kinds: Set<PrKind>, val side: Side? = null, val weight: Double = 1.0) {
    /** `L ` or `R ` to put before the set, nothing for an ordinary set. */
    val sidePrefix: String get() = when (side) { Side.LEFT -> "L "; Side.RIGHT -> "R "; null -> "" }

    /** The side in words, `left ` or `right `, for a screen reader. */
    val sideWord: String get() = when (side) { Side.LEFT -> "left "; Side.RIGHT -> "right "; null -> "" }
}

/** The PRs of [rows] as counted: each set's kinds times what the set weighs, so both sides of a round make one. */
fun prCount(rows: List<PrRow>): Double = rows.sumOf { it.weight * it.kinds.records() }

/** Every PR-setting set in [workouts], oldest first, from the model's per-workout PR lists. */
fun prRows(workouts: List<Workout>, prs: Map<String, List<Set<PrKind>>>): List<PrRow> = workouts.flatMap { w ->
    val mine = prs[w.date].orEmpty()
    w.blocks.flatMap { b -> b.sets.mapIndexed { i, set -> Triple(b, set, b.setSides[i]) } }
        .mapIndexedNotNull { i, (b, set, side) ->
            mine.getOrNull(i)?.filterTo(HashSet()) { !it.matched }?.takeIf { it.isNotEmpty() }?.let { PrRow(w.date, b.exercise, set, it, side, b.weightOf(set)) }
        }
}

@Composable
fun PrLine(model: AppModel, row: PrRow, onClick: () -> Unit) {
    val kinds = PrKind.entries.filter { it in row.kinds }.joinToString(" · ") { it.label }
    val set = setValues(row.set, model.unitOf(row.exercise))
    LinkRow(
        row.exercise, "${prettyDay(row.date)} · ${row.sidePrefix}$set", kinds,
        spoken = if (row.side == null) null else "${row.exercise}, ${prettyDay(row.date)}, ${row.sideWord}set $set, $kinds",
        onClick = onClick,
    )
}

/** Every workout done under one name: how its volume and length have moved, and what it is made of. */
@Composable
fun RoutineScreen(model: AppModel, name: String) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val list = remember(workouts, name) { workouts.filter { it.name == name } }
    // settings and bodyweights are keys only so the volume is worked out again when they change.
    val volumes = remember(list, settings, bodyweights) { list.map { volumeOf(it, model::loadOf) } }
    val days = remember(list) { list.map { parseDate(it.date)?.toLocalDate() } }
    val openOn = { day: LocalDate -> list.lastOrNull { it.day == day.toString() }?.let { model.open(Screen.WorkoutDetail(it.date)) }; Unit }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(name, model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) item { Section("") { Text("No workout has this name.") } }
            else {
                item {
                    Section("") {
                        LabelValue("Workouts", list.size.toString())
                        LabelValue("First", prettyDay(list.first().date)) { model.open(Screen.WorkoutDetail(list.first().date)) }
                        LabelValue("Latest", prettyDay(list.last().date)) { model.open(Screen.WorkoutDetail(list.last().date)) }
                        LabelValue("Average time", duration(list.sumOf { it.durationSeconds } / list.size))
                        LabelValue("Average volume", model.volume(volumes.average()))
                    }
                }
                item {
                    Section("Volume") {
                        val points = days.zip(volumes).mapNotNull { (d, v) -> d?.let { it to v } }
                        if (points.size < 2) Text("Not enough workouts to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LineChart(points, "kg", onTap = openOn)
                    }
                }
                item {
                    Section("Time") {
                        val points = days.zip(list).mapNotNull { (d, w) -> d?.let { it to w.durationSeconds / 60.0 } }
                        if (points.size < 2) Text("Not enough workouts to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else LineChart(points, "min", onTap = openOn)
                    }
                }
                item {
                    Section("Exercises") {
                        val counts = list.flatMap { w -> w.blocks.map { it.exercise }.distinct() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                        for ((exercise, n) in counts) LinkRow(exercise, "", "in $n of ${list.size}") { model.open(Screen.ExerciseDetail(exercise)) }
                    }
                }
                item {
                    Section("Workouts") {
                        for ((i, w) in list.withIndex().reversed()) {
                            LinkRow(prettyDate(w.date), duration(w.durationSeconds), model.volume(volumes[i])) { model.open(Screen.WorkoutDetail(w.date)) }
                        }
                    }
                }
            }
        }
    }
}

/** One calendar month, given as `yyyy-MM`: its totals, sets per muscle, PRs and workouts. */
@Composable
fun MonthScreen(model: AppModel, month: String) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val allPrs by model.prs.collectAsState()
    val list = remember(workouts, month) { workouts.filter { it.day.startsWith(month) } }
    val volume = remember(list, settings, bodyweights) { list.sumOf { volumeOf(it, model::loadOf) } }
    val sets = remember(list, settings) { muscleSets(list, null) { model.musclesOf(it) }.entries.sortedByDescending { it.value } }
    val prs = remember(list, allPrs) { prRows(list, allPrs) }
    val title = runCatching { YearMonth.parse(month).format(MONTH_TITLE) }.getOrDefault(month)
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(title, model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Section("") {
                    LabelValue("Workouts", list.size.toString())
                    LabelValue("Time", duration(list.sumOf { it.durationSeconds }))
                    LabelValue("Volume", model.volume(volume))
                    LabelValue("PRs", shortNumber(prCount(prs)))
                }
            }
            if (sets.isNotEmpty()) item {
                Section("Sets per muscle") {
                    for ((muscle, n) in sets) LabelValue(muscle.label, shortNumber(Math.round(n * 10) / 10.0)) { model.open(Screen.MuscleDetail(muscle)) }
                }
            }
            if (prs.isNotEmpty()) item {
                Section("PRs") { for (row in prs.reversed()) PrLine(model, row) { model.open(Screen.WorkoutDetail(row.date)) } }
            }
            item {
                Section("Workouts") {
                    if (list.isEmpty()) Text("None this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (w in list.reversed()) LinkRow(w.name, prettyDate(w.date), duration(w.durationSeconds)) { model.open(Screen.WorkoutDetail(w.date)) }
                }
            }
        }
    }
}

/** Every time a record of [exercise] was broken, newest first. */
@Composable
fun PrTimelineScreen(model: AppModel, exercise: String) {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val rows = remember(workouts, allPrs, exercise) { prRows(workouts, allPrs).filter { it.exercise == exercise }.reversed() }
    val unit = model.unitOf(exercise)
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("PR history", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Section(exercise) {
                    if (rows.isEmpty()) Text("No record has been broken yet. An exercise's first workout sets the marks to beat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // A PR of one side of a round counts a half, so the total can end in one.
                    else LabelValue("PRs", shortNumber(prCount(rows)), spoken = "PRs, ${prsText(prCount(rows))}")
                    for (row in rows) {
                        val kinds = PrKind.entries.filter { it in row.kinds }.joinToString(" · ") { it.label }
                        val set = setValues(row.set, unit)
                        LinkRow(
                            prettyDay(row.date), row.sidePrefix + set, kinds,
                            spoken = if (row.side == null) null else "${prettyDay(row.date)}, ${row.sideWord}set $set, $kinds",
                        ) { model.open(Screen.WorkoutDetail(row.date)) }
                    }
                }
            }
        }
    }
}

/**
 * The activity calendar at full size: every month from the first workout down to this one, the
 * newest at the bottom. It opens on [selected]'s month (a day tapped on the small calendar), that
 * day outlined, or else on this month. A day shows what was done on it, a month title opens its page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(model: AppModel, selected: String?) {
    val workouts by model.workouts.collectAsState()
    val palette = LocalLook.current.scale
    var mode by model.remembered("calendarMode", "Time")
    val weekTotals = model.flag("calendar.weeks")
    // What a day is shaded by.
    val seconds: Map<LocalDate, Double> = remember(workouts, mode) {
        when (mode) {
            "Volume" -> volumeByDay(workouts, model::loadOf)
            "Sets" -> workouts.groupBy { LocalDate.parse(it.day) }.mapValues { (_, w) -> w.sumOf { x -> x.workingCount } }
            else -> secondsByDay(workouts).mapValues { it.value.toDouble() }
        }
    }
    val byDay = remember(workouts) { workouts.groupBy { it.day } }
    val templateColors by model.templateColors.collectAsState()
    val dots = remember(workouts, templateColors) { dayTemplateColors(workouts, templateColors) }
    // The day a past workout is being added to.
    var logging by remember { mutableStateOf<LocalDate?>(null) }
    // Shaded between a short day and a long one (the 5th and 95th percentiles), so ordinary days spread over the whole scale.
    val sorted = remember(seconds) { seconds.values.sorted() }
    val low = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.05).toInt()]
    val top = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.95).toInt()]
    val months = remember(workouts) {
        val first = workouts.firstOrNull()?.let { runCatching { YearMonth.parse(it.day.take(7)) }.getOrNull() } ?: YearMonth.now()
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= YearMonth.now() }.toList()
    }
    val today = LocalDate.now()
    val marked = remember(selected) { selected?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    // The day whose workouts are showing in a small card.
    var open by remember { mutableStateOf<LocalDate?>(null) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (marked?.let { months.indexOf(YearMonth.from(it)) }?.takeIf { it >= 0 } ?: months.lastIndex).coerceAtLeast(0))
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    logging?.let { day ->
        var time by remember(day) { mutableStateOf("18:00") }
        var minutes by remember(day) { mutableStateOf("60") }
        val start = runCatching { java.time.LocalTime.parse(time.trim()) }.getOrNull()
        val length = minutes.trim().toLongOrNull()?.takeIf { it > 0 }
        CleanDialog(
            onDismissRequest = { logging = null },
            title = { Text("Log a workout on ${prettyDay(day)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("For a session that was not logged at the time. You enter its sets as usual; it is saved under this date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(time, { time = it }, label = { Text("Started at (HH:mm)") }, singleLine = true, isError = start == null)
                    OutlinedTextField(minutes, { minutes = it }, label = { Text("Lasted (minutes)") }, singleLine = true, isError = length == null)
                }
            },
            confirmButton = {
                TextButton(enabled = start != null && length != null, onClick = {
                    // Closed whether it started, or the workout in progress is being asked about.
                    if (model.startPast("$day ${start!!.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}", length!! * 60)) logging = null
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { logging = null }) { Text("Cancel") } },
        )
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Calendar", model::back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = Space.l)) {
            // The scale stays put while the months scroll.
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Choice(listOf("Time", "Volume", "Sets"), mode, { it }) { mode = it } }
                Text("Less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.size(width = 84.dp, height = 12.dp).background(Brush.horizontalGradient(palette), RoundedCornerShape(3.dp)))
                Text(" More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val perMonth = remember(workouts) { workouts.groupingBy { it.day.take(7) }.eachCount() }
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(months, key = { it.toString() }) { month ->
                    val count = perMonth[month.toString()] ?: 0
                    Row(Modifier.fillMaxWidth().clickable { model.open(Screen.Month(month.toString())) }.padding(top = Space.l, bottom = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle(month.format(MONTH_TITLE))
                        Text(plural(count, "workout"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        for (d in "MTWTFSS") Text(d.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (weekTotals) Spacer(Modifier.width(54.dp))
                    }
                    // Monday first; blanks before the 1st and after the last day fill the weeks out.
                    val cells: List<LocalDate?> = List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                    for (week in cells.chunked(7)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            for (day in week) {
                                if (day == null) { Spacer(Modifier.weight(1f)); continue }
                                val value = seconds[day] ?: 0.0
                                val share = if (top > low) ((value - low).toDouble() / (top - low)).coerceIn(0.0, 1.0) else 1.0
                                // shade() takes a square root, which is undone here: this scale is already spread out.
                                val color = if (value <= 0) empty else shade(palette, share * share)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).padding(Space.xxs).background(color, SmallShape)
                                        .border(if (day == marked) 2.5.dp else 0.dp, if (day == marked) MaterialTheme.colorScheme.onSurface else Color.Transparent, SmallShape)
                                        // A tap shows the day; holding it adds a workout that was not logged at the time.
                                        .combinedClickable(enabled = day <= today, onClick = { open = day }, onLongClick = { logging = day }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                        color = if (value <= 0) MaterialTheme.colorScheme.onSurfaceVariant else color.readableOn(),
                                    )
                                    dots[day]?.let { TemplateDot(it, Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xs), size = 6.dp) }
                                    // What was done that day; there can be more than one workout.
                                    if (open == day) DropdownMenu(true, { open = null }) {
                                        Text(prettyDay(day), Modifier.padding(horizontal = Space.m, vertical = Space.xs), fontWeight = FontWeight.Bold)
                                        val done = byDay[day.toString()].orEmpty()
                                        if (done.isEmpty()) Text("No workout", Modifier.padding(horizontal = Space.m, vertical = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        for (w in done) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(w.name)
                                                        Text("${w.date.drop(11).take(5)} · ${duration(w.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = { open = null; model.open(Screen.WorkoutDetail(w.date)) },
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            if (weekTotals) {
                                // The whole week's workouts and time, Monday to Sunday: a week cut in two by the end of a month
                                // shows the same total on both of its rows.
                                val monday = clean.core.weekStart(week.firstNotNullOf { it })
                                val done = (0L..6L).flatMap { byDay[monday.plusDays(it).toString()].orEmpty() }
                                Column(Modifier.width(54.dp), horizontalAlignment = Alignment.End) {
                                    if (done.isNotEmpty()) {
                                        Text("${done.size}×", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(duration(done.sumOf { it.durationSeconds }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.padding(Space.m)) }
            }
        }
    }
}

/** Every variation of [exercise]'s overall exercise taken together: one history, one set of charts, and each variation's best. */
@Composable
fun OverallScreen(model: AppModel, exercise: String) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val overall = remember(settings, exercise) { model.overallOf(exercise) }
    val members = remember(settings, workouts, exercise) { model.variationsOf(exercise).ifEmpty { listOf(exercise) } }
    val unit = model.unitOf(exercise)
    fun shown(kg: Double) = if (unit == "lb") kg / KG_PER_LB else kg
    // Per variation: its sessions, its PRs and one line of progress per session.
    val per = remember(members, workouts, bodyweights, settings, rule) {
        members.associateWith { m ->
            val done = model.sessionsIn(workouts, m)
            val type = model.loadTypeOf(m)
            Triple(done, recordsOf(done, rule, type, bodyweights::at), progressOf(done, rule, type, bodyweights::at))
        }
    }
    val done = members.filter { per.getValue(it).first.isNotEmpty() }
    // Per workout: which variations were done and how they went.
    val byDate = remember(per) {
        val map = java.util.TreeMap<String, MutableList<Pair<String, Progress>>>()
        for ((m, triple) in per) for (line in triple.third) map.getOrPut(line.date) { mutableListOf() } += m to line
        map
    }
    var chart by remember { mutableStateOf(0) }
    var only by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(overall, model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Section("") {
                    LabelValue("Variations done", "${done.size} of ${members.size}")
                    LabelValue("Workouts", byDate.size.toString())
                    byDate.keys.firstOrNull()?.let { first -> LabelValue("First", prettyDay(first)) { model.open(Screen.WorkoutDetail(first)) } }
                    byDate.keys.lastOrNull()?.let { last -> LabelValue("Latest", prettyDay(last)) { model.open(Screen.WorkoutDetail(last)) } }
                }
            }
            item {
                Section("All variations together") {
                    Choice(listOf(0, 1), chart, { if (it == 0) "Best est. 1RM" else "Volume" }) { chart = it }
                    // One variation's line on the same chart, a chip away.
                    if (done.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        CleanChip(selected = only == null, onClick = { only = null }, label = { Text("All") })
                        for (m in done) CleanChip(selected = only == m, onClick = { only = m }, label = { Text(shortVariation(m, overall)) })
                    }
                    val points = byDate.entries.mapNotNull { (date, all) ->
                        val lines = all.filter { only == null || it.first == only }
                        val day = parseDate(date)?.toLocalDate() ?: return@mapNotNull null
                        val value = if (chart == 0) lines.mapNotNull { it.second.estimated1Rm }.maxOrNull() else lines.mapNotNull { it.second.volume }.takeIf { it.isNotEmpty() }?.sum()
                        value?.let { day to shown(it) }
                    }
                    if (points.size < 2) Text("Not enough workouts to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LineChart(points, unit) { day -> byDate.keys.lastOrNull { it.startsWith(day.toString()) }?.let { model.open(Screen.WorkoutDetail(it)) } }
                }
            }
            item {
                Section("Variations") {
                    for (m in done.sortedByDescending { per.getValue(it).first.size }) {
                        val (sessions, records, _) = per.getValue(m)
                        val u = model.unitOf(m)
                        LinkRow(m, "${sessions.size} sessions · last ${prettyDay(sessions.last().date)}", records.estimated1Rm?.let { "1RM ${showEstimate(it.value, u)} $u" } ?: NONE) { model.open(Screen.ExerciseDetail(m)) }
                    }
                    val rest = members - done.toSet()
                    if (rest.isNotEmpty()) Text("${rest.size} more not done yet: ${rest.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Section("History") {
                    if (byDate.isEmpty()) Text("Not logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for ((date, lines) in byDate.descendingMap()) LinkRow(prettyDate(date), lines.joinToString(", ") { it.first }, "") { model.open(Screen.WorkoutDetail(date)) }
                }
            }
        }
    }
}

/** A note that opens blocks of one exercise: [key] is how it is matched, [text] how it was first written. */
private class NoteGroup(val exercise: String, val key: String, val text: String, val blocks: Int, val last: String)

/** What the note would be called as a variation of [overall]: `Wide grip` under `Pull Up` is `Pull Up (Wide Grip)`. */
private fun noteVariationName(overall: String, text: String) =
    "$overall (${text.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }})"

/**
 * Notes that may really be variations ("Wide grip"), gathered so the sets under each can be moved
 * to a variation of their own. Nothing is guessed: the user picks which notes and where they go.
 */
@Composable
fun NoteReviewScreen(model: AppModel, exercise: String?) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val groups = remember(workouts, exercise) {
        val map = LinkedHashMap<Pair<String, String>, NoteGroup>()
        for (w in workouts) for (b in w.blocks) {
            if ((exercise != null && b.exercise != exercise) || b.sets.isEmpty()) continue
            // The block's own marker is among the opening notes but is not one of the user's.
            for (item in b.items.takeWhile { it is NoteItem }.filter { it in b.notes }) {
                val text = (item as NoteItem).text
                val key = text.trim().lowercase()
                if (key.isEmpty()) continue
                val old = map[b.exercise to key]
                map[b.exercise to key] = NoteGroup(b.exercise, key, old?.text ?: text.trim(), (old?.blocks ?: 0) + 1, w.date)
            }
        }
        map.values.toList()
    }
    // The variation a note already is: one the exercise has history under whose name is the note, with or without its
    // overall exercise in front. Those are the safe ones to move, so they come first; then the shortest notes, since
    // "Wide grip" is far more likely a variation than a sentence; then the most often written.
    val existing = remember(groups, settings, workouts) {
        groups.associateWith { g ->
            val overall = model.overallOf(g.exercise)
            val candidate = noteVariationName(overall, g.text).lowercase()
            model.variationsOf(g.exercise).firstOrNull { v ->
                v != g.exercise && model.hasHistory(v) &&
                    (v.lowercase() == candidate || v.lowercase().removePrefix(overall.lowercase()).trim().removeSurrounding("(", ")").trim() == g.key)
            }
        }
    }
    val sorted = remember(existing) {
        existing.keys.sortedWith(compareBy<NoteGroup> { existing[it] == null }.thenBy { it.key.length }.thenByDescending { it.blocks })
    }
    var moving by remember { mutableStateOf<NoteGroup?>(null) }
    moving?.let { g ->
        val overall = model.overallOf(g.exercise)
        var name by remember(g) { mutableStateOf(existing[g] ?: noteVariationName(overall, g.text)) }
        CleanDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move to a variation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("${g.blocks} ${if (g.blocks == 1) "block" else "blocks"} of ${g.exercise} noted “${g.text}” become this exercise. The note stays on them. From then on they are exported under the new name.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, label = { Text("Variation") }, modifier = Modifier.fillMaxWidth())
                    for (v in model.variationsOf(g.exercise).filter { it != g.exercise && model.hasHistory(it) }) Text(v, Modifier.clickable { name = v }.padding(vertical = Space.xs), color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && name.trim() != g.exercise, onClick = { model.moveNoted(g.exercise, g.key, name.trim()); moving = null }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Notes to variations", model::back) }) { padding ->
        SettingsFloat(model, Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = settingsFloatSpace(model))) {
            item {
                Section("") {
                    Text(
                        "Each line is a note that opens sets of an exercise, with how often it was written. Tap one that is really a variation, such as a grip, to give those sets their own exercise with its own history and PRs. Leave the others alone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (groups.isEmpty()) Text("No notes found.")
                    for (g in sorted) LinkRow("“${g.text}”", "${g.exercise} · " + (existing[g]?.let { "is already $it · " } ?: "") + "last ${prettyDay(g.last)}", "${g.blocks}×") { moving = g }
                }
            }
        }
        }
    }
}

/** What a workout came to, shown when it is finished: next to the last one of the same name, with its PRs and muscles. */
@Composable
fun SummaryScreen(model: AppModel, date: String) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val allPrs by model.prs.collectAsState()
    val rule by model.oneRm.collectAsState()
    val workout = workouts.firstOrNull { it.date == date }
    val scaleColours = LocalLook.current.scale
    val celebrate = model.flag("reward.celebrate")
    val motion = LocalMotion.current
    val targets by model.targets.collectAsState()
    // What this workout earned that has not been celebrated yet: decided once, when the workout and the targets are in.
    var reward by remember(date) { mutableStateOf<Earned?>(null) }
    var decided by remember(date) { mutableStateOf(false) }
    LaunchedEffect(workout != null, targets != null) {
        val done = workout
        val goals = targets
        if (done == null || goals == null || decided) return@LaunchedEffect
        decided = true
        if (!celebrate) return@LaunchedEffect
        val earned = withContext(Dispatchers.Default) { earnedBy(workouts, done, goals, LocalDate.now()) }
        val seen = model.rewardSeen()
        val monday = weekStart(LocalDate.parse(done.day)).toString()
        val fresh = earned.copy(
            targetHit = earned.targetHit && "week:$monday" !in seen,
            awards = earned.awards.filter { it.id !in seen },
            shield = earned.shield && "shield:$monday" !in seen,
        )
        if (!fresh.any) return@LaunchedEffect
        reward = fresh
        model.markSeen(fresh.awards.map { it.id } + listOfNotNull("week:$monday".takeIf { fresh.targetHit }, "shield:$monday".takeIf { fresh.shield }))
        if (motion) {
            Feedback.doubleTick()
            Feedback.chime()
        }
    }
    // The program day this workout met, if it met one, and what it earned there that has not been celebrated yet.
    val versions by model.program.collectAsState()
    var onProgram by remember(date) { mutableStateOf<ProgramOutcome?>(null) }
    var programFresh by remember(date) { mutableStateOf<Pair<List<Award>, Boolean>>(emptyList<Award>() to false) }
    LaunchedEffect(workout != null, versions.isNotEmpty()) {
        val done = workout
        if (done == null || versions.isEmpty() || onProgram != null) return@LaunchedEffect
        val outcome = withContext(Dispatchers.Default) { programOutcome(versions, workouts, done, LocalDate.now(), model::overallOf) } ?: return@LaunchedEffect
        onProgram = outcome
        if (!celebrate) return@LaunchedEffect
        val seen = model.rewardSeen()
        val week = "pweek:${outcome.monday}"
        val fresh = outcome.awards.filter { it.id !in seen } to (outcome.weekCompleted && week !in seen)
        if (fresh.first.isEmpty() && !fresh.second) return@LaunchedEffect
        programFresh = fresh
        model.markSeen(fresh.first.map { it.id } + listOfNotNull(week.takeIf { fresh.second }))
        if (motion) {
            Feedback.doubleTick()
            Feedback.chime()
        }
    }
    // A goal this workout was the first to reach, said once (reward.seen `goal:<exercise>:<goal>`).
    var goalLines by remember(date) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(workout != null) {
        val done = workout
        if (done == null || !celebrate) return@LaunchedEffect
        val seen = model.rewardSeen()
        val rule = model.oneRm.value
        val fresh = withContext(Dispatchers.Default) {
            done.blocks.map { it.exercise }.distinct().mapNotNull { name ->
                val text = settings[name]?.goal.orEmpty()
                val (kg, reps) = parseGoal(text) ?: return@mapNotNull null
                // Commas separate the ids in the stored list.
                val id = "goal:${name.replace(',', ' ')}:$text"
                if (id in seen || goalReachedOn(model.sessionsIn(workouts, name), kg, reps, rule, model.loadTypeOf(name), bodyweights::at) != done.date) null
                else id to (name to "$name ${showWeight(kg, model.unitOf(name))} ${model.unitOf(name)} \u00d7 $reps")
            }
        }
        if (fresh.isEmpty()) return@LaunchedEffect
        goalLines = fresh.map { it.second }
        model.markSeen(fresh.map { it.first })
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(workout?.name ?: "Workout", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (workout == null) {
                item { Section("") { Text("Saving…") } }
                return@LazyColumn
            }
            val before = workouts.lastOrNull { it.name == workout.name && it.date < workout.date }
            val volume = volumeOf(workout, model::loadOf)
            val volumeBefore = before?.let { volumeOf(it, model::loadOf) }
            // A round of a left and right exercise is one set.
            val sets = workout.workingCount
            val prs = prRows(listOf(workout), allPrs)
            fun change(now: Double, then: Double?, unit: String, step: Double = 1.0) =
                if (then == null) "" else (now - then).let { " (" + (if (it >= 0) "+" else "−") + shortNumber(Math.round(Math.abs(it) / step) * step) + " $unit)" }
            reward?.let { earned -> item { CelebrationBlock(model, earned) } }
            if (goalLines.isNotEmpty()) item {
                Section("") {
                    for ((exercise, line) in goalLines) {
                        Text("Goal reached: $line", Modifier.fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(exercise, tab = 0)) }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            onProgram?.let { o ->
                item {
                    androidx.compose.runtime.CompositionLocalProvider(LocalCardKey provides "page:summary:program") { Section("", onClick = { model.open(Screen.Trophies) }) {
                        Text(if (o.different == 0) "${o.label}, on program" else "${o.label}, ${plural(o.different, "exercise")} different", fontWeight = FontWeight.Bold)
                        if (programFresh.second) Text("Week on program", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        for (award in programFresh.first) Text("New badge: ${award.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } }
                }
            }
            // Sets of bodyweight and assisted lifts that have no load because no weigh-in is near the workout.
            val unweighed = workout.blocks.sumOf { b ->
                val type = model.loadTypeOf(b.exercise)
                if (type != LoadType.BODYWEIGHT && type != LoadType.ASSISTED) 0.0
                else b.sets.filter { it.type != SetType.WARMUP && model.loadOf(b.exercise, it.weight ?: 0.0, workout.date) == null }.sumOf { b.weightOf(it) }
            }
            item {
                Section("Done") {
                    Text(prettyDate(workout.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var note by remember(workout.date, workout.notes) { mutableStateOf(workout.notes) }
                    NoteLine(note, { note = it }, onDone = { model.setWorkoutNote(workout.date, note) })
                    LabelValue("Time", duration(workout.durationSeconds) + change(workout.durationSeconds / 60.0, before?.durationSeconds?.div(60.0), "min"))
                    LabelValue("Volume", model.volume(volume) + change(volume, volumeBefore, "kg")) { model.open(Screen.WorkoutDetail(workout.date)) }
                    LabelValue("Working sets", shortNumber(sets) + change(sets, before?.workingCount, "sets", 0.5))
                    LabelValue("PRs", shortNumber(prCount(prs)))
                    Text(if (before == null) "The first workout under this name." else "In brackets: against ${prettyDay(before.date)}, the last time you did ${workout.name}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (unweighed > 0) item {
                Text(
                    "No recent weigh-in, so ${setsText(shortNumber(unweighed))} ${if (unweighed == 1.0) "has" else "have"} no volume or 1RM. Log your weight ›",
                    Modifier.fillMaxWidth().clickable { model.logWeight() }.padding(horizontal = Space.xxxl, vertical = Space.s),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The exercises and their sets, as the workout's page lists them, straight after the totals.
            item {
                Section("Exercises") {
                    val sets = allPrs[workout.date].orEmpty()
                    for (card in workoutCards(model, workout)) WorkoutCardSets(model, workout, card, sets, bodyweights, rule, Modifier.padding(vertical = Space.xs))
                }
            }
            if (prs.isNotEmpty()) item { Section("PRs") { for (row in prs) PrLine(model, row) { model.open(Screen.ExerciseDetail(row.exercise)) } } }
            item { CompareLastSection(model, workout, workouts) }
            // Exercises done one side at a time: their rounds, and how far one side lagged.
            val paired = workout.blocks.withIndex().filter { it.value.paired && it.value.sets.isNotEmpty() }
            if (paired.isNotEmpty()) item {
                val firstSet = workout.blocks.runningFold(0) { n, b -> n + b.sets.size }
                Section("Left and right") {
                    for ((at, block) in paired) {
                        val open = { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) }
                        Text(block.exercise, Modifier.fillMaxWidth().clickable(onClick = open), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        RoundRows(model, block, allPrs[workout.date].orEmpty().drop(firstSet[at]).take(block.sets.size), onOpen = open)
                        val lag = imbalanceOf(block, workout.date, rule, model.loadTypeOf(block.exercise), bodyweights::at)
                        if (lag != null) Text(lag.sentence(block.exercise), Modifier.fillMaxWidth().clickable(onClick = open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Section("Muscles worked") {
                    // The figure is the week so far (Monday on), this workout included; the list under it is this workout.
                    val week = remember(workouts, workout.day) {
                        val monday = weekStart(LocalDate.parse(workout.day))
                        muscleSets(workouts.filter { weekStart(LocalDate.parse(it.day)) == monday }, null) { model.musclesOf(it) }
                    }
                    if (week.isNotEmpty()) {
                        val palette = scaleColours
                        val meaning = LocalCleanColors.current
                        // The figure can also show fatigue and strength as the Body page works them out; it starts on the sets.
                        var view by rememberSaveable { mutableStateOf("Training") }
                        val figures by model.muscleFigures.collectAsState()
                        LaunchedEffect(Unit) { model.refreshMuscles() }
                        Choice(listOf("Training", "Fatigue", "Strength"), view, { it }) { view = it }
                        val muscleOpen = { m: Muscle -> model.open(Screen.MuscleDetail(m)) }
                        when (view) {
                            "Fatigue" -> {
                                val levels = figures?.fatigue.orEmpty()
                                BodyFigure(Muscle.entries.associateWith { fatigueColour(levels[it] ?: 0.0, meaning) }, muscleOpen) { m -> fatigueWord(levels[m] ?: 0.0) }
                                Legend("Fatigued", listOf(meaning.fatigued), "")
                                Legend("Recovering", listOf(meaning.recovering), "")
                                Legend("Ready", listOf(meaning.ready), "")
                            }
                            "Strength" -> {
                                val strength = figures?.strength.orEmpty()
                                val last = figures?.lastTrained.orEmpty()
                                BodyFigure(strength.filterKeys { it in last }.mapValues { (_, v) -> strengthColour(v, meaning, palette) }, muscleOpen) { m ->
                                    strength[m]?.takeIf { m in last }?.let { "${Math.round(it * 100)}% retained" } ?: "not trained"
                                }
                                Legend("Full", listOf(meaning.full) + palette.reversed(), "Faded")
                            }
                            else -> {
                                val most = week.values.max()
                                BodyFigure(week.mapValues { (_, v) -> shade(palette, v / most) }, muscleOpen) { m ->
                                    week[m]?.let { setsText(shortNumber(Math.round(it * 10) / 10.0)) + " this week" } ?: "not trained this week"
                                }
                                Text("The figure is shaded by sets this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val muscles = muscleSets(listOf(workout), null) { model.musclesOf(it) }.entries.sortedByDescending { it.value }
                    if (muscles.isEmpty()) Text("None of these exercises has muscles set.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for ((muscle, n) in muscles) LabelValue(muscle.label, setsText(shortNumber(Math.round(n * 10) / 10.0))) { model.open(Screen.MuscleDetail(muscle)) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    MainButton("Done", model::back, Modifier.fillMaxWidth())
                    QuietButton("Open the workout", { model.back(); model.open(Screen.WorkoutDetail(workout.date)) }, Modifier.fillMaxWidth())
                    var sharing by remember { mutableStateOf(false) }
                    QuietButton("Share", { sharing = true }, Modifier.fillMaxWidth())
                    if (sharing) ShareWorkoutDialog(model, workout) { sharing = false }
                }
            }
        }
    }
}

/**
 * What a finished workout earned: the ring completing for a week's target, a badge scaling in, a shield, one line
 * each. It opens the trophies. Still, and silent, when animations are off.
 */
@Composable
private fun CelebrationBlock(model: AppModel, earned: Earned) {
    val motion = LocalMotion.current
    val pop = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion) pop.animateTo(1f, Motion.reward()) }
    val accent = MaterialTheme.colorScheme.primary
    val scaled = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
    androidx.compose.runtime.CompositionLocalProvider(LocalCardKey provides "page:summary:celebration") { Section("", onClick = { model.open(Screen.Trophies) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                if (earned.targetHit) ProgressRing(1f, Modifier.size(72.dp), from = 0f) { Icon(CleanIcons.Star, null, Modifier.size(32.dp).then(scaled), tint = accent) }
                if (earned.awards.isNotEmpty()) Icon(CleanIcons.Star, null, Modifier.size(40.dp).then(scaled), tint = accent)
                if (earned.shield) Icon(CleanIcons.Shield, null, Modifier.size(40.dp).then(scaled), tint = accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (earned.targetHit) Text("Target hit: ${earned.count} of ${earned.target} this week", fontWeight = FontWeight.Bold)
                for (award in earned.awards) Text("New badge: ${award.title}", fontWeight = FontWeight.Bold)
                if (earned.shield) Text("Shield earned", fontWeight = FontWeight.Bold)
            }
        }
    } }
}

/** One week of training (Monday to Sunday): totals, each muscle against what has been usual, PRs and the workouts. */
@Composable
fun WeekScreen(model: AppModel, monday: String) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val allPrs by model.prs.collectAsState()
    var start by remember { mutableStateOf(runCatching { LocalDate.parse(monday) }.getOrDefault(LocalDate.now())) }
    val end = start.plusDays(7)
    val list = remember(workouts, start) { workouts.filter { it.day >= start.toString() && it.day < end.toString() } }
    // What has been usual: the eight weeks before, averaged.
    val earlier = remember(workouts, start) { workouts.filter { it.day >= start.minusWeeks(8).toString() && it.day < start.toString() } }
    val musclesOf = remember(settings) { { n: String -> model.musclesOf(n) } }
    val now = remember(list, settings) { muscleSets(list, null, musclesOf) }
    val usual = remember(earlier, settings) { muscleSets(earlier, null, musclesOf).mapValues { it.value / 8 } }
    val volume = remember(list, settings, bodyweights) { list.sumOf { volumeOf(it, model::loadOf) } }
    val prs = remember(list, allPrs) { prRows(list, allPrs) }
    val ranges by model.setRanges.collectAsState()
    val colors = LocalCleanColors.current
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Week of ${prettyDay(start)}", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { DeloadHint(model, Modifier.padding(horizontal = Space.l, vertical = Space.xs)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { start = start.minusWeeks(1) }) { Text("‹ Week before") }
                    TextButton(onClick = { start = start.plusWeeks(1) }, enabled = end <= LocalDate.now()) { Text("Week after ›") }
                }
            }
            item {
                Section("") {
                    LabelValue("Workouts", list.size.toString())
                    LabelValue("Time", duration(list.sumOf { it.durationSeconds }))
                    LabelValue("Volume", model.volume(volume))
                    LabelValue("PRs", shortNumber(prCount(prs)))
                }
            }
            item {
                Section("Sets per muscle") {
                    Text("This week, and in brackets the weekly average of the eight weeks before. Amber is under the weekly target, green within it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val muscles = (now.keys + usual.keys).sortedByDescending { now[it] ?: 0.0 }
                    if (muscles.isEmpty()) Text("Nothing logged in these weeks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (muscle in muscles) {
                        val n = now[muscle] ?: 0.0
                        val u = usual[muscle] ?: 0.0
                        Row(Modifier.fillMaxWidth().clickable { model.open(Screen.MuscleDetail(muscle)) }.padding(vertical = Space.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(muscle.label)
                            // Against the muscle's weekly target: under is amber, within is green, over is quiet.
                            val color = when (ranges[muscle]?.let { weeklySetState(n, it) }) {
                                RangeState.UNDER -> colors.warmup
                                RangeState.WITHIN -> colors.done
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text("${shortNumber(Math.round(n * 10) / 10.0)}  (${shortNumber(Math.round(u * 10) / 10.0)})", color = color)
                        }
                    }
                }
            }
            if (prs.isNotEmpty()) item { Section("PRs") { for (row in prs.reversed()) PrLine(model, row) { model.open(Screen.WorkoutDetail(row.date)) } } }
            item {
                Section("Workouts") {
                    if (list.isEmpty()) Text("None this week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (w in list.reversed()) LinkRow(w.name, prettyDate(w.date), duration(w.durationSeconds)) { model.open(Screen.WorkoutDetail(w.date)) }
                }
            }
        }
    }
}
