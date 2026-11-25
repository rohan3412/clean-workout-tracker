package clean.app

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.contentDescription
import clean.core.time.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.max
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.SmallShape
import clean.core.Workout
import clean.core.anniversaryLine
import clean.core.percentChange
import clean.core.progressOf
import clean.core.recordCount
import clean.core.shortNumber
import clean.core.volumeByDay
import clean.core.weekStart
import kotlinx.datetime.LocalDate

/** The date as Home's title and rows show it: the phone's own order, short weekday, day and short month ("Tue 17 Nov"). */
fun shortDay(day: LocalDate): String {
    return bestDayFormat("EEEdMMM", day)
}

/** `1 h 04`, `38 min`: a workout's length as a grey line says it. */
fun hoursMinutes(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} h ${clean.core.zeroPad((minutes % 60).toLong(), 2)}" else "$minutes min"
}

/**
 * Home, fixed: the date, the Up next card, Activity on the page (the calendar back to the first workout), then a two-column grid of tiles,
 * each opening its page (Weight, This week, the two most logged lifts' 1RM, Volume this week, the last workout). A profile with no workouts
 * keeps its first-run card.
 */
@Composable
fun HomeTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val templates by model.templates.collectAsState()
    val state = model.tabLists.getValue(Tab.Home)
    BarEnd(Tab.Home, state)
    // D11: only the first time Home is up after the app starts, its cards settle in; once the first moments are over, never again in this run.
    LaunchedEffect(Unit) { delay(1500); ColdStart.armed = false }
    // E17: pulling down at the top moves the whole list down together (no scale, nothing overlaps), with rubber-band resistance and one tick at the threshold; release springs it back.
    val motion = LocalMotion.current
    val tick = rememberTick()
    val limit = with(LocalDensity.current) { PULL_LIMIT.toPx() }
    val pull = remember { PullDown(limit) }
    val connection = remember(state, motion) { pull.connection(state, motion, tick) }
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        LazyColumn(Modifier.fillMaxSize().graphicsLayer { translationY = pull.shown }, state) {
            item {
                // On the day a year or more on from the first logged workout the weekday line says so (core `anniversaryLine`).
                val anniversary = remember(workouts, model.today) { anniversaryLine(workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() }, model.today) }
                HomeHeader(model.today, model.steps, Modifier.coldIn(0), anniversary)
            }
            if (workouts.isEmpty()) item {
                // Nothing to show yet: the two ways to start, in place of cards full of zeros.
                Section("", card = true) {
                    EmptyPreview("Log your first workout, or bring your history in from another app, and your progress fills in here.") { PreviewSamples.Line(120.dp) }
                    MainButton("Start a workout", { model.startWorkout() }, Modifier.fillMaxWidth())
                    QuietButton("Import your history", { model.settingsPage = PAGE_TRANSFER; model.importNow = true; model.open(Screen.Settings) }, Modifier.fillMaxWidth())
                }
            } else {
                item(key = "upnext") { Box(Modifier.coldIn(1)) { CompositionLocalProvider(LocalCardKey provides "home:today") { TodayCard(model, templates, workouts) } } }
                item(key = "activity") { Box(Modifier.coldIn(2)) { ActivityItem(model, Tab.Home, state) } }
                item(key = "tiles") { HomeTiles(model, workouts.last()) }
            }
            item { BarSpace() }
        }
    }
}

/** The weekday small and grey over the date large ("Wednesday", "18 November"); on an [anniversary] the weekday line goes on with it ("Friday · 1 year of training today"). */
@Composable
fun HomeHeader(date: LocalDate, steps: Long?, modifier: Modifier = Modifier, anniversary: String? = null) {
    val weekday = remember(date) { date.dayOfWeek.localName(true) }
    val title = remember(date) { bestDayFormat("dMMMM", date) }
    Row(modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                if (anniversary == null) weekday else "$weekday · $anniversary", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // B1: one line, letter spacing pulled in about 2 %, line height 110 %.
            val big = MaterialTheme.typography.headlineLarge
            Text(
                title, Modifier.semantics { heading() }, maxLines = 1, fontWeight = FontWeight.Bold,
                style = big.copy(letterSpacing = (-0.02).em, lineHeight = big.fontSize * 1.1f),
            )
        }
        // Today's steps from Health Connect, one quiet line at the end of the header; nothing at all when switched off or there is no figure.
        if (steps != null) Text(
            "${thousands(steps)} steps", Modifier.padding(bottom = Space.xs), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
    }
}

/** A small grey uppercase heading on the page, with something at its right. */
@Composable
fun PageHeading(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(Modifier.weight(1f).semantics { heading() }) { Caption(text) }
        trailing()
    }
}

/** The round "+" of a row: a 36 dp disc in a 48 dp touch area, its icon 20 dp. */
@Composable
fun RoundAdd(label: String, onClick: () -> Unit, onBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null) {
    Box(Modifier.minTouch().clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).then(if (onBounds == null) Modifier else Modifier.onGloballyPositioned { onBounds(it.boundsInWindow()) }).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(CleanIcons.Add, label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private val SHORT_DAY = LocalePattern("d MMM")

/** The days of history that decide a lift is "logged most": twelve weeks. */
const val HOME_LIFT_DAYS = 84

/** The exercises done in [workouts] on or after [since] (`YYYY-MM-DD`), most sessions first, then A to Z. */
fun liftsByFrequency(workouts: List<Workout>, since: String): List<String> {
    val counts = HashMap<String, Int>()
    for (w in workouts) if (w.day >= since) for (name in w.blocks.filter { it.sets.isNotEmpty() }.map { it.exercise }.distinct()) counts[name] = (counts[name] ?: 0) + 1
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
}

/** A lift's estimated 1RM (kg) by day, oldest first. */
private class Lift(val name: String, val points: List<Pair<LocalDate, Double>>)

/**
 * The two most logged lifts of the last twelve weeks that have an estimated 1RM to draw (at least two sessions with one; timed and cardio
 * lifts are passed over).
 */
@Composable
private fun homeLifts(model: AppModel): List<Lift> {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    return remember(workouts, settings, bodyweights, rule) {
        val since = LocalDate.now().minusDays(HOME_LIFT_DAYS.toLong()).toString()
        liftsByFrequency(workouts, since).asSequence().map { name ->
            Lift(name, progressOf(model.sessionsIn(workouts, name), rule, model.loadTypeOf(name), bodyweights::at).mapNotNull { p -> p.estimated1Rm?.let { e -> parseDate(p.date)?.toLocalDate()?.let { it to e } } })
        }.filter { it.points.size >= 2 }.take(2).toList()
    }
}

/** Two tiles to a row, each as tall as the other; a lone last one keeps its half. */
@Composable
private fun HomeTiles(model: AppModel, last: Workout) {
    val lifts = homeLifts(model)
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    tiles += { WeightTile(model, it) }
    tiles += { ThisWeekTile(model, it) }
    if (lifts.isEmpty()) tiles += { OneRmTile(model, null, 0, it) }
    else lifts.forEachIndexed { i, lift -> tiles += { OneRmTile(model, lift, i, it) } }
    tiles += { VolumeTile(model, it) }
    tiles += { LastTile(model, last, it) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        for ((r, row) in tiles.chunked(2).withIndex()) Row(Modifier.coldIn(3 + r).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            for (tile in row) tile(Modifier.weight(1f).fillMaxHeight())
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** The latest weight with its unit, how far it moved, a small chart and a "+" for the ruler; the tile opens the weight page. */
@Composable
private fun WeightTile(model: AppModel, modifier: Modifier) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val today = model.today
    val weights = weighIns(model)
    val current = weights.lastOrNull()
    val recent = weights.filter { it.first >= today.minusDays(30) }
    // From the first weigh-in of the last 30 days; with only one in that time, from the one before it.
    val base = if (recent.size >= 2) recent.first() else weights.getOrNull(weights.size - 2)
    var logging by remember { mutableStateOf(false) }
    // Where the "+" is, read when the sheet opens (not state: it changes as the page scrolls).
    val origin = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    if (logging) WeightSheet(model, origin = origin[0]) { logging = false }
    Tile(
        "Weight", modifier, "home:weight",
        onClick = { if (current == null) model.logWeight() else model.open(Screen.MeasurementDetail("weight.csv")) },
    ) {
        if (current == null) {
            TileNumberAdd("Log body weight", { logging = true }, { origin[0] = it }) { TileLine("Log your first weigh-in") }
            return@Tile
        }
        TileNumberAdd("Log body weight", { logging = true }, { origin[0] = it }) { TileNumber(showWeight(current.second, unit), unit, "tile:home:weight") }
        if (base != null) {
            val change = current.second - base.second
            val moved = showWeight(change, unit)
            TileLine(if (moved == "0" || moved == "-0") "no change since ${base.first.format(SHORT_DAY)}" else "${if (change > 0) "+" else ""}$moved since ${base.first.format(SHORT_DAY)}")
        }
        val line = weightLine(weights).takeIf { it.size >= 2 } ?: weights.takeLast(12).map { it.second }
        val reveal = rememberNewPoint(model, "home:weight", weights.size)
        if (line.size >= 2) Sparkline(line, Modifier.padding(top = Space.xs), height = 40.dp, morph = "chart:weight", wash = true, reveal = reveal)
    }
}

/** The ring of this week's workouts against the weekly target, "1 of 3", and the streak; the tile opens the trophies. */
@Composable
private fun ThisWeekTile(model: AppModel, modifier: Modifier) {
    val consistency by model.consistency.collectAsState()
    val now = consistency?.now
    val streak = consistency?.streak?.length ?: 0
    val workouts by model.workouts.collectAsState()
    // Round 19: holding the tile shows which days of this week were trained (Monday to Sunday) and the streak.
    val peek: (@Composable ColumnScope.() -> Unit)? = if (now == null) null else ({
        val monday = weekStart(model.today)
        val trained = workouts.filter { it.day >= monday.toString() }.map { ChronoUnit.DAYS.between(monday, LocalDate.parse(it.day)).toInt() }.toSet()
        PeekHead("This week")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) Column(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "${weekdayName(i)} ${if (i in trained) "trained" else "not trained"}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.size(14.dp).background(if (i in trained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape))
            }
        }
        PeekLine(if (trained.isEmpty()) "Nothing trained yet" else "Trained " + trained.sorted().joinToString(", ") { weekdayName(it).take(3) }, bold = true)
        PeekLine(if (streak > 0) "$streak-week streak" else "No streak yet")
    })
    Tile("This week", modifier, "home:week", onClick = { model.open(Screen.Trophies) }, peekTag = "week peek", peek = peek) {
        if (now == null) {
            TileLine("Counting your week")
            return@Tile
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            // The ring shows the count the user last saw until Home is on screen again, then draws the new segment from the old fraction to the
            // new one, once, with the calendar square's fill (D5); never while a page covers Home, and cut with animations off.
            val motion = LocalMotion.current
            val onHome = model.tab == Tab.Home && model.stack.isEmpty()
            val week = weekStart(model.today).toString()
            var held by remember(week) { mutableIntStateOf(now.count) }
            LaunchedEffect(now.count, onHome, motion) {
                if (now.count == held) return@LaunchedEffect
                if (motion) {
                    if (!onHome) return@LaunchedEffect
                    delay(Motion.RING_AFTER.toLong())
                }
                held = now.count
            }
            // A week that is complete springs shut once: when it becomes complete, or the first time Home shows it complete (kv `ring.closed`).
            val complete = held >= now.target
            val replay = remember(week) { complete && model.ringClosedWeek() != week }
            LaunchedEffect(complete, week) { if (complete) model.markRingClosed(week) }
            ProgressRing(held.toFloat() / now.target, Modifier.size(46.dp).morphs("ring").clearAndSetSemantics { }, from = if (replay) 0.7f else null, closeSpring = true)
            Box(Modifier.weight(1f)) { TileNumber("${now.count} of ${now.target}", null, "tile:home:week") }
        }
        TileLine(if (streak > 0) "$streak-week streak" else "No streak yet")
    }
}

/** The estimated 1RM of a lift as a number, how far it moved over 8 weeks and its small chart; the tile opens that lift's charts. With no lift to show, one quiet line. */
@Composable
private fun OneRmTile(model: AppModel, lift: Lift?, rank: Int, modifier: Modifier) {
    if (lift == null) {
        Tile("1RM", modifier) { TileLine("Log 2 sessions of a lift") }
        return
    }
    val unit = model.unitOf(lift.name)
    val points = lift.points
    val current = points.last()
    // Eight weeks back, when the lift has that much history; else from its first session.
    val windowStart = LocalDate.now().minusWeeks(8)
    val old = points.lastOrNull { it.first <= windowStart }
    val base = old ?: points.first()
    val change = current.second - base.second
    val moved = showEstimate(change, unit)
    Tile(lift.name, modifier, "home:onerm$rank", onClick = { model.open(Screen.ExerciseDetail(lift.name, tab = 2)) }) {
        TileNumber(showEstimate(current.second, unit), "$unit 1RM", "tile:home:onerm$rank")
        val span = if (old != null) "in 8 weeks" else "since ${base.first.format(SHORT_DAY)}"
        TileLine(if (moved == "0" || moved == "-0") "no change $span" else "${if (change > 0) "+" else ""}$moved $span")
        Sparkline(points.takeLast(24).map { it.second }, Modifier.padding(top = Space.xs), height = 40.dp, morph = "chart:1rm", wash = true)
    }
}

/** What has been lifted this week, set against the same days of last week; the tile opens the weekly trends. */
@Composable
private fun VolumeTile(model: AppModel, modifier: Modifier) {
    val workouts by model.workouts.collectAsState()
    val today = model.today
    val (now, before) = remember(workouts, today) {
        val perDay = volumeByDay(workouts, model::loadOf)
        val monday = weekStart(today)
        val sofar = ChronoUnit.DAYS.between(monday, today)
        fun sum(from: LocalDate) = (0..sofar).sumOf { perDay[from.plusDays(it)] ?: 0.0 }
        sum(monday) to sum(monday.minusWeeks(1))
    }
    val text = model.volume(now)
    Tile("Volume this week", modifier, "home:volume", onClick = { model.open(Screen.Trends) }) {
        TileNumber(text.substringBeforeLast(' '), text.substringAfterLast(' '), "tile:home:volume")
        TileLine(
            when {
                // Before the week's first workout a "−100 %" reads as a failure; say what last week had by now instead.
                before > 0.0 && now == 0.0 -> "${model.volume(before)} by now last week"
                before > 0.0 -> percentChange(now, before).let { if (it == "same") "same as last week" else "$it on last week" }
                now > 0.0 -> "nothing lifted last week"
                else -> "nothing lifted yet"
            },
        )
    }
}

/** The last workout: its name, then day and length in grey, and the PRs it set in green; the tile opens it. */
@Composable
private fun LastTile(model: AppModel, workout: Workout, modifier: Modifier) {
    val prs by model.prs.collectAsState()
    val count = remember(workout, prs) { recordCount(workout, prs[workout.date].orEmpty()) }
    val day = parseDate(workout.date)?.toLocalDate()
    val line = listOfNotNull(day?.let { shortDay(it) }, hoursMinutes(workout.durationSeconds).takeIf { workout.durationSeconds > 0 }).joinToString(" · ")
    Tile("Last workout", modifier, "home:last", workoutHold(model, workout), onClick = { model.open(Screen.WorkoutDetail(workout.date)) }) {
        Text(workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (line.isNotEmpty()) TileLine(line)
        if (count > 0) {
            PrChip("${shortNumber(count)} PR${if (count == 1.0) "" else "s"}")
        }
    }
}

/** D11: true from the app's start until Home's first moments are over; Home's cards settle in only while it is. */
/** The cold-start stagger runs once per card per process: [done] keeps a card that has risen from rising again when Home comes back (Back from a page within the first moments). */
object ColdStart { var armed = true; val done = mutableSetOf<Int>() }

/** D11: this item (the [index]th on Home) rises 8 dp and fades in 40 ms after the one before it, on a cold start only and for the first six. */
@Composable
fun Modifier.coldIn(index: Int): Modifier {
    val on = LocalMotion.current && ColdStart.armed && index < 6 && index !in ColdStart.done
    val a = remember { ColdStart.done += index; Animatable(if (on) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (a.value < 1f) {
            delay(index * 40L)
            a.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * rise }
}

/** E17: how far a pull down at the top of Home goes (past this a tick says so), and how far the header may stretch at most. */
private val PULL_LIMIT = 72.dp

/** The pull of E17: [raw] is the finger's travel (halved); [shown] is what the header is drawn with, a rubber band that tires toward 1.5 x [limit]. */
class PullDown(private val limit: Float) {
    var raw by mutableFloatStateOf(0f)
    val shown: Float get() = limit * 1.5f * (1f - exp(-raw / (limit * 1.5f)))

    fun connection(state: LazyListState, motion: Boolean, tick: () -> Unit) = object : NestedScrollConnection {
        var ticked = false
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!motion || source != NestedScrollSource.UserInput || available.y <= 0f || state.canScrollBackward) return Offset.Zero
            raw += available.y * 0.5f
            if (!ticked && raw >= limit) { ticked = true; tick() }
            return Offset(0f, available.y)
        }
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (raw <= 0f || available.y >= 0f) return Offset.Zero
            val used = max(available.y, -raw)
            raw += used
            if (raw < limit * 0.5f) ticked = false
            return Offset(0f, used)
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (raw > 0f) animate(raw, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> raw = v.coerceAtLeast(0f) }
            raw = 0f; ticked = false
            return Velocity.Zero
        }
    }
}
