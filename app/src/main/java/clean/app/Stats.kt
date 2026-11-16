package clean.app

import androidx.compose.ui.graphics.lerp
import clean.app.ui.theme.numbers
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.Look
import clean.app.ui.theme.orange
import clean.app.ui.theme.chartHalo
import clean.app.ui.theme.LocalTextScale
import clean.core.Workout
import clean.core.WeekTotal
import clean.core.ChartPoint
import clean.core.TEMPLATE_COLOR_COUNT
import clean.core.ChartStyle
import clean.core.smoothedPoints
import clean.core.linearFit
import clean.core.monotoneSlopes
import clean.core.sidesLine
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import clean.core.recordCount
import clean.core.progressOf
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import clean.app.ui.theme.LocalCleanColors
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import android.content.pm.ActivityInfo
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import clean.core.secondsByDay
import clean.core.spanText
import clean.core.shortNumber
import clean.core.volumeByDay
import clean.core.weekStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** The weeks the activity chart shows at least, and the weeks its colour scale is taken from. */
private const val WEEKS = 26
private val SINCE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

/** The weigh-ins, oldest first: (day, kg). */
@Composable
internal fun weighIns(model: AppModel): List<Pair<LocalDate, Double>> {
    val measurements by model.measurements.collectAsState()
    return remember(measurements) {
        measurements.filter { it.file == "weight.csv" }.mapNotNull { m -> parseDate(m.date)?.let { d -> m.value.toDoubleOrNull()?.let { d.toLocalDate() to it } } }.reversed()
    }
}

/** The last [WEEKS] weeks, oldest first and ending this week (a week with no workout is zeros), from the first workout's week on: the lines on the tiles. */
@Composable
internal fun recentWeeks(model: AppModel): List<WeekTotal> {
    val all by model.weekTotals.collectAsState()
    return remember(all) {
        val thisMonday = weekStart(LocalDate.now())
        val byMonday = all.associateBy { it.monday }
        (WEEKS - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }.filter { all.isNotEmpty() && it >= all.first().monday }.map { byMonday[it] ?: WeekTotal(it, 0, 0, 0.0, 0.0) }
    }
}

/** A trend line's weeks without the one still going on, whose part-total would read as a fall at the end of the line. */
internal fun List<WeekTotal>.finished(): List<WeekTotal> = if (lastOrNull()?.monday == weekStart(LocalDate.now())) dropLast(1) else this

/** A tile's own margin: none in a half card, whose layout places it, the card margin at full width. */
@Composable
internal fun tileModifier(): Modifier = if (LocalHalfCard.current) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)

/** The count of all workouts; it leads to how training has gone week by week. */
@Composable
internal fun WorkoutsTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    Tile("Workouts", workouts.size.toString(), tileModifier(), link = "chart:Workouts a week") { model.open(Screen.Trends) }
}

/** The workouts of this month; it opens the history. */
@Composable
internal fun MonthTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val month = LocalDate.now().toString().take(7)
    Tile("This month", workouts.count { it.day.startsWith(month) }.toString(), tileModifier()) { model.open(Screen.History) }
}

/** Weeks that met the weekly target in a row; the shield shows while one is held. It opens the trophies, which lead on to the calendar. */
@Composable
internal fun StreakTile(model: AppModel) {
    val consistency by model.consistency.collectAsState()
    Tile(
        "Week streak", (consistency?.streak?.length ?: 0).toString(), tileModifier(), icon = CleanIcons.Shield.takeIf { consistency?.streak?.shield == true }, valueRole = "number", 
    ) { model.open(Screen.Trophies) }
}

/** The latest weigh-in, and how far it has moved over the last 30 days; the "+" logs a new one on the ruler right here. */
@Composable
internal fun WeightTile(model: AppModel) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val today = LocalDate.now()
    val weights = weighIns(model)
    val recent = weights.filter { it.first >= today.minusDays(30) }
    val change = if (recent.size >= 2) recent.last().second - recent.first().second else null
    var logging by remember { mutableStateOf(false) }
    if (logging) WeightSheet(model) { logging = false }
    val current = weights.lastOrNull()?.second
    val moved = change?.let { showWeight(it, unit) }
    Tile(
        "Weight", current?.let { "${showWeight(it, unit)} $unit" } ?: NONE, tileModifier(),
        second = when {
            current == null -> "Log weight"
            moved == null -> null
            moved == "0" -> "no change"
            else -> "${if (change!! > 0) "+" else ""}$moved $unit in 30 days"
        },
        spark = weightLine(weights), link = "chart:weight",
        onAdd = { logging = true }, addLabel = "Log body weight",
    ) { if (current == null) model.logWeight() else model.open(Screen.MeasurementDetail("weight.csv")) }
}

/** What has been lifted this week, with the weekly volumes beneath; it opens the weekly trends. */
@Composable
internal fun VolumeTile(model: AppModel) {
    val weeks = recentWeeks(model)
    val thisMonday = weekStart(LocalDate.now())
    val now = weeks.lastOrNull { it.monday == thisMonday }?.volume ?: 0.0
    Tile("Volume", model.volume(now), tileModifier(), second = if (weeks.size < 2) "Log ${2 - weeks.size} more ${if (weeks.isEmpty()) "weeks" else "week"} of workouts" else "This week", spark = weeks.finished().map { it.volume }, sample = true, link = "chart:Volume a week") { model.open(Screen.Trends) }
}

/**
 * The estimated 1RM of the [rank]th most logged lift of the last twelve weeks (0 the most, 1 the next) as a number with its history
 * beneath; it opens that lift's Charts tab. Lifts with no estimated 1RM to draw (timed, cardio, fewer than two sessions) are passed
 * over, and with none left the card shows nothing.
 */
@Composable
internal fun OneRmTile(model: AppModel, rank: Int) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val pick = remember(workouts, settings, bodyweights, rule) {
        val since = LocalDate.now().minusDays(HOME_LIFT_DAYS.toLong()).toString()
        liftsByFrequency(workouts, since).asSequence()
            .map { name -> name to progressOf(model.sessionsIn(workouts, name), rule, model.loadTypeOf(name), bodyweights::at).mapNotNull { it.estimated1Rm } }
            .filter { it.second.size >= 2 }.drop(rank).firstOrNull()
    }
    if (pick == null) {
        // The first card still says what is coming; the second stays out of the way.
        if (rank == 0) Tile("1RM", NONE, tileModifier(), second = "Log 2 sessions of a lift", sample = true)
        return
    }
    val (name, estimates) = pick
    val unit = model.unitOf(name)
    Tile("1RM", showEstimate(estimates.last(), unit), tileModifier(), second = "$name · $unit", spark = estimates.takeLast(24), link = "chart:1rm", valueRole = "number") { model.open(Screen.ExerciseDetail(name, tab = 2)) }
}

/** The weigh-ins of the last [WEEKS] weeks as one mean a week, oldest first: a weigh-in a day would make the small line a scribble. */
internal fun weightLine(weights: List<Pair<LocalDate, Double>>): List<Double> =
    weights.filter { it.first >= LocalDate.now().minusWeeks(WEEKS.toLong()) }.groupBy { weekStart(it.first) }.toSortedMap().values.map { w -> w.sumOf { it.second } / w.size }

/** PRs set in the last 30 days (the kinds that count); it opens the best lifts and the PRs by month. */
@Composable
internal fun PrsTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    // PRs a week over the last 12 weeks, oldest first.
    val line = remember(workouts, allPrs) {
        val first = weekStart(LocalDate.now()).minusWeeks(11)
        val perWeek = DoubleArray(12)
        for (w in workouts) if (w.day >= first.toString()) perWeek[(ChronoUnit.WEEKS.between(first, weekStart(LocalDate.parse(w.day)))).toInt().coerceIn(0, 11)] += recordCount(w, allPrs[w.date].orEmpty())
        perWeek.toList()
    }
    Tile("PRs, 30 days", shortNumber(recentPrCount(model)), tileModifier(), spark = line) { model.open(Screen.PersonalBests) }
}

/** The PRs set in the last 30 days, as the tile and Progress count them. */
@Composable
internal fun recentPrCount(model: AppModel): Double {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val since = LocalDate.now().minusDays(30).toString()
    return remember(workouts, allPrs, since) { workouts.filter { it.day >= since }.sumOf { w -> recordCount(w, allPrs[w.date].orEmpty()) } }
}

/** The week so far, with the program under it when there is one. The card opens the week's report, the ring the trophies. */
@Composable
internal fun ThisWeekCard(model: AppModel, half: Boolean = false) {
    val workouts by model.workouts.collectAsState()
    val consistency by model.consistency.collectAsState()
    val monday = weekStart(LocalDate.now())
    val week = workouts.filter { it.day >= monday.toString() }
    Column {
        Section("This week", onClick = { model.open(Screen.Week(monday.toString())) }) {
            val now = consistency?.now
            // Half width has no room beside the ring: the words go under it, and the seven days and the program are left to the wide card.
            val body: @Composable () -> Unit = {
                // The ring fills toward the weekly target; it opens the trophies.
                if (now != null) ProgressRing(now.count.toFloat() / now.target, Modifier.size(72.dp).clip(CircleShape).clickable(onClickLabel = "Open trophies") { model.open(Screen.Trophies) }.semantics { contentDescription = "${now.count} of ${now.target} workouts this week" }) {
                    if (now.met) Icon(CleanIcons.Star, "Target hit", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    else Text("${now.count} / ${now.target}", fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    if (now != null) Text(if (now.met) "Target hit" else "${now.target - now.count} to go", fontWeight = FontWeight.Bold, color = if (now.met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (week.isEmpty()) "No workout yet. Tap for the week's report."
                        else "${plural(week.size, "workout")} · ${duration(week.sumOf { it.durationSeconds })} · report ›",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (half) Column(verticalArrangement = Arrangement.spacedBy(Space.s)) { body() }
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) { body() }
                WeekDays(model, monday, week)
            }
        }
        if (!half) CompositionLocalProvider(LocalCardKey provides LocalCardKey.current?.let { "$it:program" }) { ProgramCard(model) }
    }
}

/**
 * Monday to Sunday as seven round marks with their letters: filled in the accent for a day trained, a ring for today, plain for the
 * rest and dimmer once still to come. A trained day opens that day (its workout, or the calendar's page of it when there are several).
 */
@Composable
private fun WeekDays(model: AppModel, monday: LocalDate, week: List<Workout>) {
    val today = LocalDate.now()
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val day = monday.plusDays(i.toLong())
            val done = week.filter { it.day == day.toString() }
            val trained = done.isNotEmpty()
            val future = day.isAfter(today)
            val name = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
            Box(
                Modifier.size(36.dp).reach().clip(CircleShape)
                    .then(if (trained) Modifier.clickable(onClickLabel = "Open $name") { model.open(if (done.size == 1) Screen.WorkoutDetail(done.first().date) else Screen.Calendar(day.toString())) } else Modifier)
                    .background(if (trained) accent else Color.Transparent, CircleShape)
                    .then(if (day == today && !trained) Modifier.border(2.dp, accent, CircleShape) else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = "$name, ${if (trained) "trained" else if (day == today) "today" else if (future) "to come" else "rest"}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = when {
                        trained -> MaterialTheme.colorScheme.onPrimary
                        day == today -> accent
                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The scrolling activity calendar; the card opens the full calendar. */
@Composable
internal fun ActivityCard(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val scaleColours = LocalLook.current.scale
    val today = LocalDate.now()
    var byVolume by remember { mutableStateOf(false) }
    // The chart opens at the present and is dragged back into the past; it drifts back to the present by itself.
    val scroll = rememberScrollState(Int.MAX_VALUE)
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val first = remember(workouts) { workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } }
    // The card opens the full calendar; a day on it still opens that day's workout.
    Section("", onClick = { model.open(Screen.Calendar()) }) {
        // The title brings the chart back to the present when it has been dragged into the past; at the present it opens the calendar, like the rest of the card.
        Row(
            Modifier.fillMaxWidth().clickable {
                if (scroll.value < scroll.maxValue) scope.launch { if (motion) scroll.animateScrollTo(scroll.maxValue, Motion.short()) else scroll.scrollTo(scroll.maxValue) }
                else model.open(Screen.Calendar())
            },
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            SectionTitle("Activity", role = "title")
            if (first != null) Text("since ${first.format(SINCE)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Choice(listOf(false, true), byVolume, { if (it) "Volume" else "Time" }) { byVolume = it }
        val values: Map<LocalDate, Double> = remember(workouts, byVolume) {
            if (byVolume) volumeByDay(workouts, model::loadOf) else secondsByDay(workouts).mapValues { it.value.toDouble() }
        }
        val palette = scaleColours
        // A day opens the full calendar at that day.
        val colors by model.templateColors.collectAsState()
        val dots = remember(workouts, colors) { dayTemplateColors(workouts, colors) }
        Calendar(values, today, first, palette, scroll, dots) { day -> model.open(Screen.Calendar(day.toString())) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (color in listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) + palette) {
                Box(Modifier.padding(horizontal = Space.xxs).size(12.dp).background(color, RoundedCornerShape(3.dp)))
            }
            Text(" More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The body weight chart over the range kept in Settings; the card opens the weight page. Edge to edge it is the latest weigh-in and the
 * span inside the card's margin over a line of weekly means that runs to the card's sides and bottom; the axes, dates and full screen
 * are on the weight page (the full chart's labels would be cut by the card's edge).
 */
@Composable
internal fun BodyWeightCard(model: AppModel) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val weights = weighIns(model)
    // Only the kept range, not its buttons: called here they were drawn outside the card, under it, and through a see-through card.
    val range = model.measurementDays()
    val shown = if (range == 0L) weights else weights.filter { it.first >= LocalDate.now().minusDays(range) }
    val inUnit = shown.map { it.first to (if (unit == "lb") it.second / clean.core.KG_PER_LB else it.second) }
    val line = inUnit.groupBy { weekStart(it.first) }.toSortedMap().values.map { w -> w.sumOf { it.second } / w.size }
    val edge = LocalEdgeCharts.current && line.size >= 2
    val bleed: (@Composable ColumnScope.() -> Unit)? = if (edge) ({ Sparkline(line, Modifier.morphsFromCard("chart:weight"), edge = true, height = 96.dp) }) else null
    Section("Body weight", onClick = { model.open(Screen.MeasurementDetail("weight.csv")) }, bleed = bleed) {
        when {
            weights.size < 2 -> EmptyPreview(
                "Log ${2 - weights.size} more ${if (weights.isEmpty()) "weigh-ins" else "weigh-in"} to see your trend.", "Log weight", { model.logWeight() },
            ) { PreviewSamples.Line(72.dp) }
            shown.size < 2 -> Text("Not enough weigh-ins in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            edge -> {
                val (first, last) = shown.first() to shown.last()
                val change = last.second - first.second
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("${showWeight(last.second, unit)} $unit", style = MaterialTheme.typography.headlineMedium.numbers(), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (change > 0) "+" else ""}${showWeight(change, unit)} $unit since ${first.first.format(SHORT_DAY)}",
                        Modifier.padding(bottom = Space.xs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> Box(Modifier.morphsFromCard("chart:weight")) { LineChart(inUnit, unit) }
        }
    }
}

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** The program under "This week", when there is one: today's place on it, the streak and the shield. It opens the trophies. */
@Composable
private fun ProgramCard(model: AppModel) {
    val standing by model.programStanding.collectAsState()
    val plan by model.programToday.collectAsState()
    val templates by model.templates.collectAsState()
    val ps = standing ?: return
    Section("Program", onClick = { model.open(Screen.Trophies) }) {
        plan?.let { p ->
            val day = p.today.day
            val template = day?.let { d -> templates.firstOrNull { it.id == d.templateId } }
            // Today's line opens the template's page, or the program on a rest day.
            Text(
                when {
                    day == null -> "Rest day"
                    p.done -> "${day.label} · done"
                    else -> "${day.label} · today"
                },
                Modifier.fillMaxWidth().clickable { model.open(if (template != null) Screen.TemplateStats(template.id) else Screen.Program) },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(spanText(ps.streak.length) + " on program", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ps.streak.shield) Icon(CleanIcons.Shield, "Shield held", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A number with its name, opening the page that explains it. With [spark] (oldest first) a small unlabelled line of the page's
 * chart sits under the number; with [onAdd] a "+" at the right does the tile's quick action (for [addLabel]) without opening the page.
 */
@Composable
internal fun Tile(
    label: String, value: String, modifier: Modifier = Modifier, second: String? = null, icon: ImageVector? = null,
    spark: List<Double>? = null, onAdd: (() -> Unit)? = null, addLabel: String = "", sample: Boolean = false, link: String? = null, valueRole: String? = null, onClick: (() -> Unit)? = null,
) {
    val edge = LocalEdgeCharts.current && spark != null && spark.size >= 2
    val outline = tileBorder()
    val tileColour = tileFill()
    val tap = if (onClick != null) cardTap(onClick) else null
    CompositionLocalProvider(LocalOnTile provides true) {
    Card(if (tap != null) modifier.clip(cardShape()).pressable(onClick = tap) else modifier, shape = cardShape(), colors = CardDefaults.cardColors(containerColor = tileColour), border = outline) {
        Column((if (tap != null) Modifier.opensInto(outline, tileColour) else Modifier).fillMaxHeight()) {
            Row(Modifier.padding(start = Space.l, top = Space.l, end = Space.l, bottom = if (edge) Space.s else Space.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Caption(label, role = "title")
                    Row(Modifier.then(if (spark != null && spark.size >= 2 || link == null) Modifier else Modifier.morphsFromCard(link)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        FitValue(value, MaterialTheme.typography.headlineMedium.numbers(), if (tap != null) valueRole else null)
                        if (icon != null) Icon(icon, "Shield held", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (second != null) Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (spark != null && spark.size >= 2 && !edge) Sparkline(spark, Modifier.padding(top = Space.s).then(if (link != null) Modifier.morphsFromCard(link) else Modifier))
                    // Too little to draw yet: a faint sample line says what will appear (see EmptyPreview).
                    else if (sample && (spark == null || spark.size < 2)) {
                        Box(Modifier.graphicsLayer { alpha = 0.3f }.clearAndSetSemantics { }) { Sparkline(PreviewSamples.LINE.map { it.toDouble() }, Modifier.padding(top = Space.s)) }
                    }
                }
                if (onAdd != null) {
                    Box(Modifier.minTouch().clip(CircleShape).clickable(onClickLabel = addLabel, role = Role.Button, onClick = onAdd), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(CleanIcons.Add, addLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            // Edge to edge: the line runs along the card's bottom, left to right, with the card's corners clipping it; in a taller card it sits at the bottom.
            if (edge && spark != null) {
                Spacer(Modifier.weight(1f))
                Sparkline(spark, if (link != null) Modifier.morphsFromCard(link) else Modifier, edge = true)
            }
        }
    }
    }
}

/**
 * A tile's big number on one line: when it does not fit beside what shares its row (a half card's "+", Mono's wider figures, a weight in
 * pounds), it steps down in size, to no less than 70 %, instead of wrapping onto a second line. Nothing is drawn until it fits.
 */
@Composable
private fun FitValue(text: String, style: androidx.compose.ui.text.TextStyle, role: String? = null) {
    var scale by remember(text, style) { mutableStateOf(1f) }
    var fits by remember(text, style) { mutableStateOf(false) }
    val light = LocalLook.current.lightNumbers
    // Linen: a light 34 sp figure with its unit small and muted beside it.
    val shown = if (light) style.copy(fontSize = style.fontSize * (34f / 28f), fontWeight = FontWeight.Light) else style
    val words = if (light) withUnit(text, MaterialTheme.colorScheme.onSurfaceVariant) else androidx.compose.ui.text.AnnotatedString(text)
    val onLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = { if (it.hasVisualOverflow && scale > 0.7f) scale -= 0.05f else fits = true }
    if (role != null) MorphText(
        words, role, Modifier.drawWithContent { if (fits) drawContent() }, style = shown.copy(fontSize = shown.fontSize * scale), fontWeight = if (light) FontWeight.Light else FontWeight.SemiBold,
        maxLines = 1, softWrap = false, onTextLayout = onLayout,
    ) else Text(
        words, Modifier.drawWithContent { if (fits) drawContent() }, style = shown.copy(fontSize = shown.fontSize * scale), fontWeight = if (light) FontWeight.Light else FontWeight.SemiBold,
        maxLines = 1, softWrap = false, onTextLayout = onLayout,
    )
}

/** `102.5 kg` as the figure and, after it, its unit at 45 % of the size in [muted]; text that does not end in a plain word is returned as it is. */
internal fun withUnit(text: String, muted: Color): androidx.compose.ui.text.AnnotatedString {
    val cut = text.lastIndexOf(' ')
    val unit = if (cut > 0) text.substring(cut + 1) else ""
    val figure = if (cut > 0) text.substring(0, cut) else text
    val isUnit = unit.isNotEmpty() && unit.all { it.isLetter() || it == '%' || it == '/' } && figure.firstOrNull()?.let { it.isDigit() || it == '-' || it == '+' || it == '−' } == true
    return androidx.compose.ui.text.buildAnnotatedString {
        if (!isUnit) { append(text); return@buildAnnotatedString }
        append(figure)
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.45f.em, color = muted, fontWeight = FontWeight.Normal))
        append(" $unit")
        pop()
    }
}
/**
 * A small line of [values], oldest first, with no axes, numbers or dots: decoration on a tile, so a screen reader skips it. Edge to
 * edge it runs from side to side above a faint fill to the bottom, and keeps clear of the bottom so the card's corners do not cut it.
 */
@Composable
internal fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, edge: Boolean = false, height: Dp = if (edge) 44.dp else 28.dp) {
    val look = LocalLook.current
    // Linen: a thin dark line over a grey wash that fades to nothing, and the last point an orange dot.
    val fade = look.chartFade
    val color = if (fade) (look.chartLine ?: look.orange) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val dot = look.orange
    Spacer(modifier.fillMaxWidth().height(height).drawWithCache {
        val pad = 2.dp.toPx()
        val padX = if (edge) 0f else pad
        // The last point, Linen's orange dot, stops short of the right edge so the whole dot shows inside the card.
        val padRight = if (fade) 10.dp.toPx() else padX
        val top = if (edge) 4.dp.toPx() else pad
        val bottom = if (edge) 10.dp.toPx() else pad
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0 }
        val xs = FloatArray(values.size) { padX + it.toFloat() / (values.size - 1) * (size.width - padX - padRight) }
        // A level line runs along the middle.
        val ys = FloatArray(values.size) { if (span == null) size.height / 2 else top + (1 - ((values[it] - low) / span).toFloat()) * (size.height - top - bottom) }
        val path = Path()
        path.curveThrough(xs, ys)
        val fill = if (edge) Path().apply { addPath(path); lineTo(xs.last(), size.height); lineTo(0f, size.height); close() }
            else if (fade) Path().apply { addPath(path); lineTo(xs.last(), size.height); lineTo(xs.first(), size.height); close() } else null
        val wash = if (fade) Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = size.height) else null
        // The wash ends short of the card's edge under the dot, so it fades out sideways over its last stretch (and over its first when it
        // does not start at the edge) instead of stopping at a straight wall.
        val sideFade = if (wash != null) {
            val reach = 28.dp.toPx() / (xs.last() - xs.first()).coerceAtLeast(1f)
            val inFrom = if (edge) 0f else reach.coerceAtMost(0.5f)
            Brush.horizontalGradient(
                0f to (if (edge) Color.Black else Color.Transparent), inFrom to Color.Black,
                (1f - reach).coerceAtLeast(inFrom) to Color.Black, 1f to Color.Transparent,
                startX = if (edge) 0f else xs.first(), endX = xs.last(),
            )
        } else null
        val stroke = Stroke(if (fade) look.chartLineWidth.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            if (wash != null) fill?.let {
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
                drawPath(it, wash)
                drawRect(sideFade!!, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                drawContext.canvas.restore()
            } else fill?.let { drawPath(it, color.copy(alpha = 0.12f)) }
            drawPath(path, color, style = stroke)
            if (fade) drawCircle(dot, 3.dp.toPx(), Offset(xs.last(), ys.last()))
        }
    })
}

/**
 * One column per week, Monday at the top; each day shaded in four steps relative to the busiest day of the last [WEEKS] weeks (so
 * an old big day does not pale the recent weeks; older days above it take the top colour). It runs back to the week of the [first]
 * workout, never fewer than [WEEKS] weeks, in a sideways [scroll] that starts at the present. Three seconds after the last touch
 * (and the fling it started) a chart left in the past glides back to the present, unless animations are off.
 */
@Composable
private fun Calendar(values: Map<LocalDate, Double>, today: LocalDate, first: LocalDate?, palette: List<Color>, scroll: ScrollState, dots: Map<LocalDate, Int> = emptyMap(), onTap: (LocalDate) -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val thisMonday = weekStart(today)
    val weeks = maxOf(WEEKS, first?.let { ChronoUnit.WEEKS.between(weekStart(it), thisMonday).toInt() + 1 } ?: 0)
    val start = thisMonday.minusWeeks(weeks - 1L)
    val max = remember(values, thisMonday) { values.filterKeys { it >= thisMonday.minusWeeks(WEEKS - 1L) }.values.maxOrNull() ?: 0.0 }
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val outline = MaterialTheme.colorScheme.onSurface
    // A day's first coloured template, as a dot at the foot of its cell, with a ring of the page's colour so it reads on any shade.
    val dotColors = if (dots.isEmpty()) emptyList() else List(TEMPLATE_COLOR_COUNT) { templateColor(it) }
    val dotRing = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val months = remember { DateTimeFormatter.ofPattern("MMM") }
    val density = LocalDensity.current
    // The month names follow the text size chosen in Settings.
    val textScale = LocalTextScale.current
    val paint = remember(labelColor, density, textScale) { Paint().apply { color = labelColor; textSize = with(density) { 10.sp.toPx() } * textScale; isAntiAlias = true } }
    val activeDays = remember(values, start, today) { values.count { (day, v) -> v > 0 && day >= start && day <= today } }
    // A finger on the chart; the drift back waits until it is lifted.
    var down by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    LaunchedEffect(down, motion) {
        if (down || !motion) return@LaunchedEffect
        snapshotFlow { scroll.isScrollInProgress }.first { !it }
        if (scroll.value >= scroll.maxValue) return@LaunchedEffect
        delay(3000)
        scroll.animateScrollTo(scroll.maxValue, Motion.drift(Motion.DRIFT_CHART))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The cells are as big as when the chart held 26 weeks across the card.
        val cell = maxWidth / WEEKS
        val viewport = with(density) { maxWidth.toPx() }
        // Looked at before the scroll takes the touch, and left unconsumed.
        Box(
            Modifier.fillMaxWidth().pointerInput(Unit) {
                awaitPointerEventScope { while (true) down = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } }
            }.horizontalScroll(scroll),
        ) {
            Canvas(
                Modifier.width(cell * weeks).height(cell * 8.2f).semantics { contentDescription = "Activity chart: ${plural(activeDays, "day")} with a workout since ${prettyDay(start)}" }.pointerInput(start, weeks) {
                    // Positions are the canvas's own, so they are right wherever it has been scrolled to.
                    detectTapGestures { at ->
                        val c = size.width / weeks.toFloat()
                        val week = (at.x / c).toInt()
                        val day = Math.floor(((at.y - c * 1.1f) / c).toDouble()).toInt()
                        if (week in 0 until weeks && day in 0..6) tap(start.plusWeeks(week.toLong()).plusDays(day.toLong()))
                    }
                },
            ) {
                val c = size.width / weeks
                val gap = c * 0.14f
                val top = c * 1.1f
                // Only the columns in view are drawn, and a few before them for a label that starts off the edge.
                val seen = scroll.value
                val from = ((seen / c).toInt() - 3).coerceAtLeast(0)
                val to = (((seen + viewport) / c).toInt() + 1).coerceAtMost(weeks - 1)
                for (week in from..to) {
                    val monday = start.plusWeeks(week.toLong())
                    // Label the column in which a month begins; January carries the year.
                    if (monday.dayOfMonth <= 7) drawContext.canvas.nativeCanvas.drawText(if (monday.monthValue == 1) monday.format(SINCE) else monday.format(months), week * c, c * 0.7f, paint)
                    for (day in 0 until 7) {
                        val date = monday.plusDays(day.toLong())
                        if (date > today) continue
                        val value = values[date] ?: 0.0
                        val color = if (value <= 0 || max <= 0) empty else palette[(ceil(value / max * 4).toInt() - 1).coerceIn(0, 3)]
                        val origin = Offset(week * c + gap / 2, top + day * c + gap / 2)
                        val box = Size(c - gap, c - gap)
                        drawRoundRect(color, origin, box, CornerRadius(c * 0.2f))
                        if (date == today) drawRoundRect(outline, origin, box, CornerRadius(c * 0.2f), style = Stroke(1.5.dp.toPx()))
                        dots[date]?.let { i ->
                            val at = Offset(origin.x + box.width / 2, origin.y + box.height * 0.78f)
                            drawCircle(dotRing, c * 0.2f, at)
                            drawCircle(dotColors[i], c * 0.14f, at)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adds the line through the points as one smooth curve: a monotone cubic ([monotoneSlopes]), so it passes through every point and
 * never rises above or falls below the two it runs between. Every chart draws its line with this. Two points are straight and a
 * repeated x (two points on one day) is a step.
 */
internal fun Path.curveThrough(xs: FloatArray, ys: FloatArray) {
    if (xs.isEmpty()) return
    moveTo(xs[0], ys[0])
    val m = monotoneSlopes(DoubleArray(xs.size) { xs[it].toDouble() }, DoubleArray(ys.size) { ys[it].toDouble() })
    for (k in 1 until xs.size) {
        val h = (xs[k] - xs[k - 1]) / 3f
        if (h <= 0f) lineTo(xs[k], ys[k])
        else cubicTo(xs[k - 1] + h, ys[k - 1] + (m[k - 1] * h).toFloat(), xs[k] - h, ys[k] - (m[k] * h).toFloat(), xs[k], ys[k])
    }
}

/** A line over time through plain values, with no left and right: [SplitLineChart] with every point on its own. */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Double>>,
    unit: String,
    range: ClosedFloatingPointRange<Double>? = null,
    bands: List<Pair<ClosedFloatingPointRange<Double>, Color>> = emptyList(),
    title: String = "",
    height: Dp = 190.dp,
    trend: Boolean = false,
    goal: Double? = null,
    onTap: ((LocalDate) -> Unit)? = null,
) {
    val plain = remember(points) { points.map { ChartPoint(it.first, it.second) } }
    SplitLineChart(plain, unit, range, bands, title, height, trend, goal, onTap = onTap)
}

/**
 * A line over time. The dotted line marks the latest value and is labelled on the y axis, next to
 * the highest and lowest values shown. [bands] are value ranges shaded behind the line, and [range]
 * widens the y axis to take them in. [goal] is a level to reach, drawn as a thin line in the green of
 * things done and taken into the axis. A tap is passed to [onTap] as the day of the nearest point.
 * A point with both sides ([ChartPoint.split]) is two markers joined by a faint stem; the left ones are on a solid line
 * and the right ones on a dashed line, the two running together through plain points. With one in view a key says which
 * line is which, and the plain points then get a small marker too.
 * [imbalance] is right-minus-left in percent: a zero line is the axis, and the wording is about which side leads.
 */
@Composable
fun SplitLineChart(
    points: List<ChartPoint>,
    unit: String,
    range: ClosedFloatingPointRange<Double>? = null,
    bands: List<Pair<ClosedFloatingPointRange<Double>, Color>> = emptyList(),
    title: String = "",
    height: Dp = 190.dp,
    /** Draws the straight line that fits the points best, and says under the chart how fast it rises or falls. */
    trend: Boolean = false,
    goal: Double? = null,
    imbalance: Boolean = false,
    onTap: ((LocalDate) -> Unit)? = null,
) {
    val look = LocalLook.current
    // Linen: a thin dark line over a fading grey wash, the latest point and its value orange.
    val fade = look.chartFade
    val dotColor = look.orange
    val halo = look.chartHalo
    val line = if (fade) look.chartLine ?: dotColor else MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val latestColor = if (fade) dotColor else MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val goalColor = LocalCleanColors.current.done
    val fillAlpha = look.chartFill
    // The axis takes in both ends of every stem, and the zero of an imbalance chart.
    val dataLow = minOf(points.minOf { it.low }, range?.start ?: Double.MAX_VALUE, goal ?: Double.MAX_VALUE, if (imbalance) 0.0 else Double.MAX_VALUE)
    val high = maxOf(points.maxOf { it.high }, range?.endInclusive ?: -Double.MAX_VALUE, goal ?: -Double.MAX_VALUE, if (imbalance) 0.0 else -Double.MAX_VALUE)
    val style = LocalChartStyle.current
    val bars = style == ChartStyle.BARS
    // Bars start at the bottom edge, so the axis goes a little below the lowest figure and that one still shows as a bar.
    val low = if (bars && !imbalance) dataLow - ((high - dataLow).takeIf { it > 0 } ?: maxOf(Math.abs(high), 1.0)) * 0.12 else dataLow
    // The smoothed style draws the moving average and keeps the points themselves as faint dots.
    val smoothed = style == ChartStyle.SMOOTH
    val shown = remember(points, smoothed) { if (smoothed) smoothedPoints(points) else points }
    val latest = points.last().value
    val first = points.first().date
    val days = ChronoUnit.DAYS.between(first, points.last().date).coerceAtLeast(1)
    val hasSplit = remember(points) { points.any { it.split } }
    val tap by rememberUpdatedState(onTap)
    // The trend, as a rise per day and the value on the first day.
    val fit = remember(points, trend) { if (trend) linearFit(points.map { ChronoUnit.DAYS.between(first, it.date).toDouble() to it.value }) else null }
    val trendColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The point last tapped and where it sits, for the small card that names it.
    var picked by remember(points) { mutableStateOf<Pair<Int, Offset>?>(null) }
    Box {
        picked?.let { (index, at) ->
            val point = points[index]
            val day = point.date
            Popup(offset = IntOffset(at.x.toInt(), at.y.toInt()), onDismissRequest = { picked = null }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = SmallShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = if (look.sheetPage) 0.dp else 6.dp, border = if (look.sheetPage) BorderStroke(1.dp, grid) else null,
                    modifier = Modifier.clickable { picked = null; tap?.invoke(day) },
                ) {
                    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
                        Text(
                            when {
                                imbalance -> leadWords(point.value)
                                point.split -> sidesLine("${roundedNumber(point.left!!)} $unit".trim(), "${roundedNumber(point.right!!)} $unit".trim())!!
                                else -> "${roundedNumber(point.value)} $unit".trim()
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(prettyDay(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onTap != null) Text("Open ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    val drawn by drawnIn()
    val textScale = LocalTextScale.current
    val splits = points.count { it.split }
    val lastPoint = points.last()
    val summary = when {
        imbalance -> "${title.ifEmpty { "Imbalance" }}, ${points.size} sessions, latest ${leadWords(latest)}, from ${leadWords(points.minOf { it.value })} to ${leadWords(points.maxOf { it.value })}"
        else -> "${title.ifEmpty { "Chart" }}, ${points.size} points" + (if (splits > 0) ", $splits of them left and right, " + (if (bars) "left filled bar, right outlined bar" else "left solid line, right dashed line") else "") +
            ", from ${shortNumber(points.minOf { it.low })} to ${shortNumber(points.maxOf { it.high })} $unit, " +
            (if (lastPoint.split) "latest left ${shortNumber(lastPoint.left!!)}, right ${shortNumber(lastPoint.right!!)}" else "latest ${shortNumber(latest)}")
    }.replace(" ,", ",")
    Spacer(Modifier.fillMaxWidth().height(height).semantics { contentDescription = summary; role = Role.Image }.pointerInput(points) {
        detectTapGestures { at ->
            val left = 46.dp.toPx()
            val day = first.plusDays(Math.round((at.x - left) / (size.width - left) * days).toLong())
            // The nearest session by day takes the tap at any height, so the whole stem is a target.
            val index = points.indices.minByOrNull { Math.abs(ChronoUnit.DAYS.between(points[it].date, day)) } ?: return@detectTapGestures
            val span = (high - low).takeIf { it > 0 } ?: 1.0
            val x = left + ChronoUnit.DAYS.between(first, points[index].date).toFloat() / days * (size.width - left)
            val y = 10.dp.toPx() + ((high - points[index].high) / span).toFloat() * (size.height - 20.dp.toPx())
            // The card goes a little above and to the left of the point, kept on the chart.
            picked = index to Offset((x - 70.dp.toPx()).coerceAtLeast(0f), (y - 78.dp.toPx()).coerceAtLeast(0f))
        }
    }.drawWithCache {
        // Worked out once for this size: the scale, the label paints and the line itself.
        val left = 46.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        fun y(value: Double) = top + ((high - value) / span).toFloat() * (bottom - top)
        val text = Paint().apply { color = labelColor; textSize = 11.sp.toPx() * textScale; isAntiAlias = true }
        val bold = Paint(text).apply { color = latestColor.toArgb(); isFakeBoldText = true }
        val goalPaint = Paint(text).apply { color = goalColor.toArgb(); isFakeBoldText = true }
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        val latestY = y(latest)
        val near = 13.sp.toPx() * textScale
        val path = Path()
        fun x(p: ChartPoint) = left + ChronoUnit.DAYS.between(first, p.date).toFloat() / days * (size.width - left)
        path.curveThrough(FloatArray(shown.size) { x(shown[it]) }, FloatArray(shown.size) { y(shown[it].value) })
        val marks = if (hasSplit && !bars) SplitMarks(shown, this, ::x, ::y) else null
        val raw = if (smoothed) SplitMarks(points, this, ::x, ::y) else null
        val barMarks = if (bars) BarMarks(points, this, ::x, ::y, if (imbalance) y(0.0) else bottom) else null
        val zeroY = y(0.0)
        val stroke = Stroke((if (fade) look.chartLineWidth else 2.5f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val lastAt = Offset(x(shown.last()), y(shown.last().value))
        val fadeFill = if (fade && marks == null && barMarks == null) Path().apply { addPath(path); lineTo(lastAt.x, bottom); lineTo(x(shown.first()), bottom); close() } else null
        val fadeBrush = if (fadeFill != null) Brush.verticalGradient(listOf(line.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = bottom) else null
        onDrawBehind {
            for ((band, color) in bands) {
                val from = y(minOf(band.endInclusive, high))
                val to = y(maxOf(band.start, low))
                if (to > from) drawRect(color.copy(alpha = fillAlpha), Offset(left, from), Size(size.width - left, to - from))
            }
            fun label(value: Double, paint: Paint) = drawContext.canvas.nativeCanvas.drawText(shortNumber(value), 0f, y(value) + text.textSize / 3, paint)
            drawLine(grid, Offset(left, top), Offset(size.width, top))
            drawLine(grid, Offset(left, bottom), Offset(size.width, bottom))
            // Linen: a faint grid of a few lines between the two edges.
            if (fade) for (k in 1..2) drawLine(grid, Offset(left, top + (bottom - top) * k / 3f), Offset(size.width, top + (bottom - top) * k / 3f))
            // The latest value: a dotted line across the chart and its number on the axis.
            drawLine(latestColor.copy(alpha = if (fade) 0.45f else 1f), Offset(left, latestY), Offset(size.width, latestY), 1.5.dp.toPx(), pathEffect = dashes)
            if (latestY - top > near) label(high, text)
            if (bottom - latestY > near) label(dataLow, text)
            label(latest, bold)
            if (imbalance) {
                // Right minus left: the zero line is the axis, drawn solid and labelled unless another label is there.
                drawLine(trendColor, Offset(left, zeroY), Offset(size.width, zeroY), 1.5.dp.toPx())
                if (zeroY - top > near && bottom - zeroY > near && Math.abs(zeroY - latestY) > near) label(0.0, text)
            }
            if (goal != null) {
                drawLine(goalColor, Offset(left, y(goal)), Offset(size.width, y(goal)), 1.5.dp.toPx())
                if (Math.abs(y(goal) - latestY) > near) label(goal, goalPaint)
            }
            fit?.let { (slope, start) ->
                clipRect(left = left, top = top, bottom = bottom) { drawLine(trendColor, Offset(left, y(start)), Offset(size.width, y(start + slope * days)), 1.5.dp.toPx()) }
            }
            clipRect(right = left + (size.width - left) * drawn) {
                raw?.let { drawRawDots(it, line) }
                if (barMarks != null) drawBars(barMarks, line)
                else if (marks != null) drawSplitMarks(marks, line, linesOnly = smoothed)
                else {
                    fadeFill?.let { drawPath(it, fadeBrush!!) }
                    drawPath(path, line, style = stroke)
                    if (fade && !imbalance) { drawCircle(halo, 6.5.dp.toPx(), lastAt); drawCircle(dotColor, 4.5.dp.toPx(), lastAt) }
                }
            }
        }
    })
    Row(Modifier.fillMaxWidth().padding(start = 46.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(prettyDay(first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val now = if (lastPoint.split) "L ${shortNumber(lastPoint.left!!)} · R ${shortNumber(lastPoint.right!!)}" else shortNumber(latest)
        Text("$now $unit · ${prettyDay(lastPoint.date)}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (hasSplit) SidesKey(line, Modifier.padding(start = 46.dp), bars)
    if (imbalance) Text("Above 0: right leads. Below 0: left leads.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    fit?.let { (slope, _) ->
        val month = Math.round(slope * 30 * 100) / 100.0
        Text(
            if (month == 0.0) "Trend: level" else "Trend: ${if (month > 0) "+" else "\u2212"}${shortNumber(Math.abs(month))} $unit a month".trim(),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (goal != null) Text("Goal ${shortNumber(Math.round(goal * 10) / 10.0)} $unit".trim(), style = MaterialTheme.typography.labelMedium, color = goalColor)
    FullScreenLink(FullChart(title, listOf(ChartSeries(title, unit, null, points, imbalance))))
}

/** One line of a chart. A null [color] is the app's accent; [imbalance] marks right-minus-left percentages. */
class ChartSeries(val label: String, val unit: String, val color: Color?, val points: List<ChartPoint>, val imbalance: Boolean = false)

/** `right leads by 8 %`, `left leads by 3 %`, `even`, for an imbalance chart's value. */
private fun leadWords(percent: Double): String {
    val whole = Math.round(Math.abs(percent)).toInt()
    return if (whole == 0) "even" else "${if (percent > 0) "right" else "left"} leads by $whole %"
}

private fun roundedNumber(value: Double) = shortNumber(Math.round(value * 100) / 100.0)

/** What the two lines are, shown only on a chart that has a left and right session: a solid line and filled dot is the left, a dashed line and ring the right. */
@Composable
private fun SidesKey(color: Color, modifier: Modifier = Modifier, bars: Boolean = false) {
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = if (bars) "Key: filled bar is left, outlined bar is right" else "Key: solid line with filled markers is left, dashed line with rings is right" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        for (right in listOf(false, true)) {
            Canvas(Modifier.size(28.dp, 12.dp)) {
                if (bars) {
                    val box = Size(10.dp.toPx(), size.height)
                    val origin = Offset((size.width - box.width) / 2, 0f)
                    if (right) drawRoundRect(color, origin + Offset(1f, 1f), Size(box.width - 2f, box.height - 2f), CornerRadius(2.dp.toPx()), style = Stroke(2.dp.toPx()))
                    else drawRoundRect(color, origin, box, CornerRadius(2.dp.toPx()))
                    return@Canvas
                }
                val mid = size.height / 2
                val width = 2.5.dp.toPx()
                if (right) drawLine(color, Offset(0f, mid), Offset(size.width, mid), width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                else drawLine(color, Offset(0f, mid), Offset(size.width, mid), width, StrokeCap.Round)
                val at = Offset(size.width / 2, mid)
                if (right) drawCircle(color, 4.5.dp.toPx(), at, style = Stroke(2.dp.toPx())) else drawCircle(color, 4.5.dp.toPx(), at)
            }
            Text(if (right) "Right" else "Left", style = MaterialTheme.typography.labelSmall, color = text)
            if (!right) Spacer(Modifier.width(Space.s))
        }
    }
}

/**
 * The marks of a chart's sessions for one size, made once in the cache: one path of every stem and flat arrays of the
 * markers, so drawing them allocates nothing. A left value is NaN for a plain session.
 */
private class SplitMarks(points: List<ChartPoint>, density: Density, x: (ChartPoint) -> Float, y: (Double) -> Float) {
    val xs = FloatArray(points.size)
    val mid = FloatArray(points.size)
    val leftY = FloatArray(points.size)
    val rightY = FloatArray(points.size)
    val stems = Path()
    /** The left line (solid) and the right line (dashed): a plain session gives both its one value. */
    val leftLine = Path()
    val rightLine = Path()
    val big = with(density) { 4.5.dp.toPx() }
    val dot = with(density) { 2.5.dp.toPx() }
    val halo = with(density) { 8.dp.toPx() }
    val ring = Stroke(with(density) { 2.dp.toPx() })
    val haloRing = Stroke(with(density) { 1.5.dp.toPx() })
    val stem = Stroke(with(density) { 2.dp.toPx() }, cap = StrokeCap.Round)
    val solid = Stroke(with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dashed = Stroke(
        with(density) { 2.5.dp.toPx() }, join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 4.dp.toPx() })),
    )

    init {
        val ls = FloatArray(points.size)
        val rs = FloatArray(points.size)
        points.forEachIndexed { i, p ->
            xs[i] = x(p)
            mid[i] = y(p.value)
            val l = if (p.split) y(p.left!!) else mid[i]
            val r = if (p.split) y(p.right!!) else mid[i]
            ls[i] = l
            rs[i] = r
            if (p.split) {
                leftY[i] = l
                rightY[i] = r
                stems.moveTo(xs[i], l)
                stems.lineTo(xs[i], r)
            } else {
                leftY[i] = Float.NaN
                rightY[i] = Float.NaN
            }
        }
        leftLine.curveThrough(xs, ls)
        rightLine.curveThrough(xs, rs)
    }
}

/**
 * The left line solid and the right line dashed, each through its own markers, with a faint stem between a session's two:
 * a filled circle for the left end, a ring for the right, one ringed marker for equal sides, a small dot for a plain session.
 */
private fun DrawScope.drawSplitMarks(marks: SplitMarks, color: Color, linesOnly: Boolean = false) {
    if (!linesOnly) drawPath(marks.stems, color.copy(alpha = 0.2f), style = marks.stem)
    drawPath(marks.leftLine, color, style = marks.solid)
    drawPath(marks.rightLine, color, style = marks.dashed)
    if (linesOnly) return
    for (i in marks.xs.indices) {
        val x = marks.xs[i]
        val l = marks.leftY[i]
        when {
            l.isNaN() -> drawCircle(color, marks.dot, Offset(x, marks.mid[i]))
            Math.abs(l - marks.rightY[i]) < 1f -> {
                drawCircle(color, marks.big, Offset(x, l))
                drawCircle(color, marks.halo, Offset(x, l), style = marks.haloRing)
            }
            else -> {
                drawCircle(color, marks.big, Offset(x, l))
                drawCircle(color, marks.big, Offset(x, marks.rightY[i]), style = marks.ring)
            }
        }
    }
}

/** One line of a [MultiLineChart] worked out for its size; [raw] holds the points under a smoothed curve. */
private data class Drawn(val path: Path, val color: Color, val marks: SplitMarks?, val raw: SplitMarks?)

/** The points themselves under a smoothed curve: a faint dot of the accent (about 30 %) at each figure, both sides of a split session. */
private fun DrawScope.drawRawDots(marks: SplitMarks, color: Color) {
    val faint = color.copy(alpha = 0.3f)
    for (i in marks.xs.indices) {
        val l = marks.leftY[i]
        if (l.isNaN()) drawCircle(faint, marks.dot, Offset(marks.xs[i], marks.mid[i]))
        else {
            drawCircle(faint, marks.dot, Offset(marks.xs[i], l))
            drawCircle(faint, marks.dot, Offset(marks.xs[i], marks.rightY[i]))
        }
    }
}

/**
 * The bars of a chart for one size: a bar from [base] up to each figure, as wide as the closest two sessions allow, a session
 * done left and right being two half-wide bars side by side (the left filled, the right outlined). A left value is NaN for a plain session.
 */
private class BarMarks(points: List<ChartPoint>, density: Density, x: (ChartPoint) -> Float, y: (Double) -> Float, val base: Float) {
    val xs = FloatArray(points.size) { x(points[it]) }
    val mid = FloatArray(points.size) { y(points[it].value) }
    val leftY = FloatArray(points.size) { if (points[it].split) y(points[it].left!!) else Float.NaN }
    val rightY = FloatArray(points.size) { if (points[it].split) y(points[it].right!!) else Float.NaN }
    /** The width of half a bar. */
    val half: Float
    val corner = with(density) { 3.dp.toPx() }
    val ring = Stroke(with(density) { 1.5.dp.toPx() })

    init {
        val gap = (1 until xs.size).map { xs[it] - xs[it - 1] }.filter { it > 0f }.minOrNull() ?: Float.MAX_VALUE
        half = (gap * 0.4f).coerceIn(with(density) { 2.dp.toPx() }, with(density) { 10.dp.toPx() })
    }
}

private fun DrawScope.drawBars(bars: BarMarks, color: Color) {
    fun bar(x: Float, width: Float, y: Float, outlined: Boolean) {
        val top = minOf(y, bars.base)
        val height = Math.abs(y - bars.base).coerceAtLeast(1f)
        val radius = CornerRadius(minOf(bars.corner, width / 2))
        if (outlined) {
            val inset = bars.ring.width / 2
            drawRoundRect(color, Offset(x + inset, top + inset), Size((width - 2 * inset).coerceAtLeast(1f), (height - 2 * inset).coerceAtLeast(1f)), radius, style = bars.ring)
        } else drawRoundRect(color, Offset(x, top), Size(width, height), radius)
    }
    for (i in bars.xs.indices) {
        // The first and last bars are moved in from the chart's edges so they are drawn whole.
        val x = bars.xs[i].coerceIn(bars.half, maxOf(bars.half, size.width - bars.half))
        if (bars.leftY[i].isNaN()) bar(x - bars.half, bars.half * 2, bars.mid[i], false)
        else {
            bar(x - bars.half, bars.half, bars.leftY[i], false)
            bar(x, bars.half, bars.rightY[i], true)
        }
    }
}

/** How the charts draw their figures (Settings, Appearance, Charts). Sparklines on tiles stay lines. */
val LocalChartStyle = staticCompositionLocalOf { ChartStyle.LINE }

/** A chart to show on the whole screen, turned on its side; [shared] keeps one scale for every line. */
class FullChart(val title: String, val series: List<ChartSeries>, val shared: Boolean = false)

/** How a chart asks for the full screen; null where there is none to give (the full screen itself). */
val LocalFullChart = staticCompositionLocalOf<((FullChart) -> Unit)?> { null }

@Composable
private fun FullScreenLink(chart: FullChart) {
    val open = LocalFullChart.current ?: return
    Text(
        "Full screen", Modifier.fillMaxWidth().clickable { open(chart) }.padding(top = Space.xxs),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End,
    )
}

/** The colours of the lines when a chart has several, on a dark page. */
private val SERIES_COLORS = listOf(Color(0xFF8AB4FF), Color(0xFFFFC53D), Color(0xFF2FD3A6), Color(0xFFFF7AB6))

/** The same four hues deepened for a light page, where the pale ones above all but vanish: each keeps 3:1 against white and Linen's paper. */
private val SERIES_COLORS_LIGHT = listOf(Color(0xFF2B62C4), Color(0xFF946200), Color(0xFF0B7D60), Color(0xFFC2306E))

/** The chart lines' colours under [look]: [SERIES_COLORS] or their light twins, or in Mono four greys of different lightness between the page and the text. */
internal fun seriesColorsOf(look: Look): List<Color> = when {
    look.mono -> listOf(1f, 0.6f, 0.8f, 0.4f).map { lerp(look.page, look.text, it) }
    look.dark -> SERIES_COLORS
    else -> SERIES_COLORS_LIGHT
}

/** [seriesColorsOf] the current look. */
@Composable
fun seriesColors(): List<Color> = seriesColorsOf(LocalLook.current)

/** The latest point as the key writes it: `L 100 · R 92` for a session done left and right, else the number. */
private fun latestShown(p: ChartPoint) = if (p.split) "L ${shortNumber(p.left!!)} · R ${shortNumber(p.right!!)}" else shortNumber(p.value)

private fun latestSpoken(p: ChartPoint) = if (p.split) "left ${shortNumber(p.left!!)}, right ${shortNumber(p.right!!)}" else shortNumber(p.value)

/**
 * Several measures over the same dates in one chart. They have no common scale (kilos, reps), so
 * each line is stretched over the chart's height between its own lowest and highest value; the
 * key underneath gives each line's colour, range and latest value. Good for seeing how they move
 * together, not for reading values off the axis. With [shared] they are one measure (the two sides
 * of a muscle's sets) and keep one scale, so the gap between the lines is the gap in the numbers.
 */
@Composable
fun MultiLineChart(series: List<ChartSeries>, title: String = "", height: Dp = 190.dp, shared: Boolean = false) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val look = LocalLook.current
    val single = look.chartFade && series.size == 1
    val first = series.minOf { it.points.first().date }
    val last = series.maxOf { it.points.last().date }
    val days = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val drawn by drawnIn()
    // Several lines on their own scales cannot be bars side by side and stay readable, so Bars draws "All" as lines; Smoothed averages each line.
    val smoothed = LocalChartStyle.current == ChartStyle.SMOOTH
    Spacer(Modifier.fillMaxWidth().height(height).semantics { contentDescription = "${title.ifEmpty { "Chart" }}:${series.joinToString(", ") { "${it.label}, latest ${latestSpoken(it.points.last())} ${it.unit}".trim() + (if (it.points.any { p -> p.split }) ", ${it.points.count { p -> p.split }} sessions left and right, left solid line, right dashed line" else "") }}"; role = Role.Image }.drawWithCache {
        // Each line is worked out once for this size, not on each frame of its drawing-in.
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val paths = series.map { line ->
            // The line's own scale takes in both ends of every stem; a shared one takes in every line.
            val scale = if (shared) series else listOf(line)
            val low = scale.minOf { s -> s.points.minOf { it.low } }
            val span = (scale.maxOf { s -> s.points.maxOf { it.high } } - low).takeIf { it > 0 } ?: 1.0
            fun y(value: Double) = bottom - ((value - low) / span).toFloat() * (bottom - top)
            fun x(p: ChartPoint) = ChronoUnit.DAYS.between(first, p.date).toFloat() / days * size.width
            val path = Path()
            val shown = if (smoothed) smoothedPoints(line.points) else line.points
            path.curveThrough(FloatArray(shown.size) { x(shown[it]) }, FloatArray(shown.size) { y(shown[it].value) })
            val marks = if (shown.any { it.split }) SplitMarks(shown, this, ::x, ::y) else null
            Drawn(path, if (single) look.chartLine ?: accent else line.color ?: accent, marks, if (smoothed) SplitMarks(line.points, this, ::x, ::y) else null)
        }
        val stroke = Stroke((if (single) look.chartLineWidth else 2.5f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Linen, one line: a wash under it that fades to nothing.
        val only = paths.firstOrNull()
        val wash = if (single && only != null && only.marks == null) {
            val points = series[0].points
            val firstX = ChronoUnit.DAYS.between(first, points.first().date).toFloat() / days * size.width
            val lastX = ChronoUnit.DAYS.between(first, points.last().date).toFloat() / days * size.width
            Path().apply { addPath(only.path); lineTo(lastX, bottom); lineTo(firstX, bottom); close() }
        } else null
        val washBrush = if (wash != null) Brush.verticalGradient(listOf(only!!.color.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = bottom) else null
        onDrawBehind {
            drawLine(grid, Offset(0f, top), Offset(size.width, top))
            drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom))
            if (single) for (k in 1..2) drawLine(grid, Offset(0f, top + (bottom - top) * k / 3f), Offset(size.width, top + (bottom - top) * k / 3f))
            clipRect(right = size.width * drawn) {
                for ((path, color, marks, raw) in paths) {
                    raw?.let { drawRawDots(it, color) }
                    if (marks != null) drawSplitMarks(marks, color, linesOnly = smoothed)
                    else {
                        if (wash != null && washBrush != null) drawPath(wash, washBrush)
                        drawPath(path, color, style = stroke)
                    }
                }
            }
        }
    })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(prettyDay(first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(prettyDay(last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // One key for the whole chart, when any line has a session done left and right.
    if (series.any { s -> s.points.any { it.split } }) SidesKey(MaterialTheme.colorScheme.onSurfaceVariant)
    for (line in series) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.size(12.dp).background(line.color ?: accent, RoundedCornerShape(3.dp)))
            Text(line.label, Modifier.weight(1f))
            Text("${shortNumber(line.points.minOf { it.low })} to ${shortNumber(line.points.maxOf { it.high })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("now ${latestShown(line.points.last())} ${line.unit}".trim(), fontWeight = FontWeight.Bold)
        }
    }
    Text(
        if (shared) "Both lines are on one scale." else "Each line runs from its own lowest value at the bottom to its highest at the top.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FullScreenLink(FullChart(title, series, shared))
}

/** A chart on the whole screen with the phone on its side; going back turns it upright again. */
@Composable
fun FullChartScreen(model: AppModel) {
    val chart = model.fullChart
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    if (chart == null || chart.series.isEmpty()) {
        LaunchedEffect(Unit) { model.back() }
        return
    }
    val style = LocalChartStyle.current
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(chart.title.ifEmpty { "Chart" }, model::back) {
            // Each tap moves to the next style: line, bars, smoothed.
            TextButton(
                onClick = { model.setOption("chart.style", ChartStyle.entries[(style.ordinal + 1) % ChartStyle.entries.size].key) },
                Modifier.semantics { contentDescription = "Chart style, ${style.label}. Tap to change" },
            ) { Text(style.label) }
        }
    }) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().padding(horizontal = Space.l)) {
            val tall = maxHeight
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CompositionLocalProvider(LocalFullChart provides null) {
                    if (chart.series.size == 1) chart.series[0].let { SplitLineChart(it.points, it.unit, imbalance = it.imbalance, height = (tall - 40.dp).coerceAtLeast(160.dp)) }
                    else MultiLineChart(chart.series, height = (tall - 24.dp).coerceAtLeast(160.dp), shared = chart.shared)
                }
            }
        }
    }
}

/**
 * Linen's headline figure over a chart: the latest value in a light 64 sp with its [unit] small and muted beside it. Nothing in
 * the other looks, whose charts say the latest value in their own labels.
 */
@Composable
fun BigNumber(value: String, unit: String, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (!look.lightNumbers) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    MorphText(
        androidx.compose.ui.text.buildAnnotatedString {
            append(value)
            if (unit.isNotEmpty()) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.45f.em, color = muted))
                append(" $unit")
                pop()
            }
        },
        "number", modifier.semantics { contentDescription = "Latest $value $unit".trim() }, onPage = true,
        style = MaterialTheme.typography.displayLarge.numbers().copy(fontSize = (64f * LocalTextScale.current).sp, fontWeight = FontWeight.Light),
        maxLines = 1,
    )
}
