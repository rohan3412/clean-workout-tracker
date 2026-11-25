package clean.app

import androidx.compose.ui.layout.onSizeChanged
import clean.core.DayRange
import clean.core.time.*
import kotlinx.datetime.Month
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import clean.core.recordCount
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import clean.app.ui.theme.cardShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import clean.core.Muscle
import clean.core.BarHigh
import clean.core.PeriodTotals
import clean.core.allTimeHigh
import clean.core.StatsPeriod
import clean.core.VolumeBucket
import clean.core.Workout
import clean.core.countChange
import clean.core.muscleSets
import clean.core.percentChange
import clean.core.periodRange
import clean.core.previousRange
import clean.core.previousName
import clean.core.comparisonSentence
import clean.core.totalsIn
import clean.core.volumeBuckets
import clean.core.volumeOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.core.shortNumber
import clean.core.weekStart
import dev.chrisbanes.haze.hazeSource
import kotlinx.datetime.LocalDate

/** A line that opens a page, with the page's headline number at the right and a chevron. */
@Composable
fun NavRow(title: String, value: String, onClick: () -> Unit, key: String? = null) {
    // With a key the row is a container that grows into its page (see [opensInto]); without one it is the blink, even inside a card that has a key.
    CompositionLocalProvider(LocalCardKey provides key) {
        Row(
            (if (key != null) Modifier.opensInto(null, androidx.compose.ui.graphics.Color.Transparent, square = true) else Modifier).fillMaxWidth().clickable(onClick = if (key != null) cardTap(onClick) else onClick).minTouch(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphText(androidx.compose.ui.text.AnnotatedString(title), "title", style = MaterialTheme.typography.bodyMedium)
            Text(if (value.isEmpty()) "›" else "$value ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val SHORT_DAY = LocalePattern("d MMM")

/** A page in miniature: its name, a little of what is on it, and the whole card opens it (nothing inside takes a tap of its own). */
@Composable
private fun MiniPage(title: String, value: String, modifier: Modifier, key: String, onClick: () -> Unit, dip: Float = 0.98f, content: @Composable ColumnScope.() -> Unit) = CompositionLocalProvider(LocalCardKey provides key) {
    val fill = cardFill()
    val pressed = remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(modifier.cardDepth(cardShape()).then(if (frostOn()) Modifier.frosted(cardShape(), fill) else Modifier).clip(cardShape()).pressable(dip = dip, pressed = pressed, onClick = cardTap(onClick)), shape = cardShape(), colors = CardDefaults.cardColors(containerColor = if (frostOn()) androidx.compose.ui.graphics.Color.Transparent else fill)) {
        Column(Modifier.opensInto(null, fill, frosted = frostOn()).litEdge(cardShape(), brightNow = { pressed.value }).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                SectionTitle(title, role = "title")
                if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/**
 * Two cards side by side, the first as tall as the second: the second is measured first and sets the height, because the first holds a
 * list, which cannot be asked how tall it would like to be.
 */
@Composable
private fun Twins(modifier: Modifier, gap: Dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = (constraints.maxWidth - gapPx) / 2
        val second = measurables[1].measure(Constraints.fixedWidth(width))
        val first = measurables[0].measure(Constraints.fixed(width, second.height))
        layout(constraints.maxWidth, second.height) {
            first.placeRelative(0, 0)
            second.placeRelative(width + gapPx, 0)
        }
    }
}

/**
 * Every workout as a tiny row, newest first: its name, its PRs when it has any, and its day. The list scrolls inside the card, and three
 * seconds after the last touch it drifts back up to the newest, like the activity chart drifts back to the present.
 */
@Composable
private fun ColumnScope.HistoryRows(model: AppModel, onPeek: (Pair<Workout, Offset>?) -> Unit) {
    val workouts by model.workouts.collectAsState()
    val prs by model.prs.collectAsState()
    val rows = remember(workouts) { workouts.asReversed() }
    val state = rememberLazyListState()
    val motion = LocalMotion.current
    LaunchedEffect(state, motion) {
        while (true) {
            snapshotFlow { !state.isScrollInProgress && state.canScrollBackward }.first { it }
            // Three seconds with no scrolling; a touch in that time starts the wait again.
            if (withTimeoutOrNull(3000) { snapshotFlow { state.isScrollInProgress }.first { it } } != null) continue
            // The rows are all one height, so the way back is known. A finger on the list stops the drift.
            val row = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val gap = state.layoutInfo.mainAxisItemSpacing
            try {
                if (motion && row > 0) state.animateScrollBy(-(state.firstVisibleItemIndex * (row + gap) + state.firstVisibleItemScrollOffset).toFloat(), Motion.drift(Motion.DRIFT_LIST))
                state.scrollToItem(0)
            } catch (stopped: CancellationException) {
                ensureActive()
            }
        }
    }
    // Holding a row peeks at its workout above the finger, sliding on to another row switches it; a tap and the list's scroll go on as they were.
    val tick = rememberTick()
    var listAt by remember { mutableStateOf(Offset.Zero) }
    fun rowAt(at: Offset) = state.layoutInfo.visibleItemsInfo.firstOrNull { at.y >= it.offset && at.y < it.offset + it.size }?.key?.let { k -> rows.firstOrNull { it.date == k } }
    var held by remember { mutableStateOf<Workout?>(null) }
    val peeking = Modifier.onGloballyPositioned { listAt = it.positionInRoot() }.holdAndSlide(
        key = rows,
        onHold = { at -> rowAt(at)?.let { tick(); held = it; onPeek(it to (at + listAt)) } },
        onMove = { at -> val now = rowAt(at) ?: held; if (now != null) { if (now != held) tick(); held = now; onPeek(now to (at + listAt)) } },
        onEnd = { held = null; onPeek(null) },
    )
    LazyColumn(Modifier.weight(1f).fillMaxWidth().then(peeking), state, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        items(rows, key = { it.date }) { w ->
            val count = remember(w, prs) { recordCount(w, prs[w.date].orEmpty()) }
            // Each row is one thing that travels as the card opens (its own page's matching card, or below the bottom of the page), with its
            // name, PR count and date moving separately onto their places on that card (Modifier.morphs, MorphText, role "row:...").
            // A row the list cuts off at the card's bottom stays where it is and fades with the card: travelling, it would show the part the list hides.
            val whole = { state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == w.date }?.let { it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset } ?: false }
            CompositionLocalProvider(LocalMorphEnabled provides whole) {
            Row(Modifier.morphs("row:out", w.date, late = true), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                MorphText(androidx.compose.ui.text.AnnotatedString(w.name), "row:name", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, id = w.date)
                if (count > 0) Row(Modifier.morphs("row:prs", w.date).semantics(mergeDescendants = true) { contentDescription = "${shortNumber(count)} PRs" }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(CleanIcons.StarLine, null, Modifier.size(12.dp), tint = prColor())
                    Text(shortNumber(count), maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = prColor())
                }
                // The date does not travel: it fades with the card, and in on the History page where it stands (Round 21).
                Text(LocalDate.parse(w.day).format(SHORT_DAY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            }
        }
    }
}

/**
 * Progress: History and Body as two cards side by side, each a small picture of its page and opening it (every workout as a
 * tiny row, scrolling inside the card; the figure in the Body page's current view, swiped sideways to the next view), the Activity card, then the pages
 * of statistics with their headline numbers.
 */
@Composable
fun ProgressTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val state = model.tabLists.getValue(Tab.Progress)
    BarEnd(Tab.Progress, state)
    // The workout held on the History card and where the finger is (in the window), shown as the same glass card a day makes on Activity.
    var rowPeek by remember { mutableStateOf<Pair<Workout, Offset>?>(null) }
    var boxAt by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { boxAt = it.positionInRoot() }) {
    // The page is a blur source for the card floating over it only while a row is held (that card is its sibling, so it can blur it).
    val peekHaze = dev.chrisbanes.haze.rememberHazeState()
    LazyColumn(Modifier.fillMaxSize().then(if (rowPeek != null) Modifier.hazeSource(peekHaze) else Modifier), state) {
        item { Title("Progress") }
        item {
            // The two stand as tall as each other; the figure sets the height and the rows take what room there is.
            Twins(Modifier.fillMaxWidth().padding(horizontal = Space.m), 8.dp) {
                MiniPage("History", workouts.size.toString(), Modifier, "progress:history", { model.open(Screen.History) }) {
                    if (workouts.isEmpty()) Text("No workouts yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else HistoryRows(model) { rowPeek = it }
                }
                MiniPage("Body", "", Modifier, "progress:body", { model.open(Screen.Body) }, dip = 1f) { BodyMini(model) }
            }
        }
        // Activity on the page, as on Home: the calendar back to the first workout; its title opens the full calendar, a day on it that day.
        if (workouts.isNotEmpty()) item(key = "activity") { ActivityItem(model, Tab.Progress, state) }
        item { StatisticsSection(model) }
        item { BarSpace() }
    }
    rowPeek?.let { (w, at) -> DayPeek(model, LocalDate.parse(w.day), listOf(w), Modifier.placeAbove(at - boxAt, boxAt.x), peekHaze) }
    }
}


private val DAY_NAME = LocalePattern("EEE d MMM")
private val MONTH_NAME = LocalePattern("MMM yyyy")

/** A bar in words, for a screen reader: the day, the month or the year it covers. */
private fun bucketName(b: VolumeBucket): String = when {
    b.start == b.end -> b.start.format(DAY_NAME)
    b.start.monthValue == 1 && b.end.monthValue == 12 -> b.start.year.toString()
    else -> b.start.format(MONTH_NAME)
}

/** The few words under the chart's two ends. */
private fun endName(period: StatsPeriod, b: VolumeBucket): String = when {
    b.start.monthValue == 1 && b.end.monthValue == 12 -> b.start.year.toString()
    period == StatsPeriod.WEEK -> b.start.format(LocalePattern("EEE"))
    period == StatsPeriod.MONTH -> b.start.format(SHORT_DAY)
    period == StatsPeriod.YEAR -> b.start.format(LocalePattern("MMM"))
    else -> b.start.format(LocalePattern("MMM yy"))
}

/** Volume per bucket as bars; the bucket holding today in the accent, the others in a quieter tone of it, the days to come only a faint stub. */
@Composable
private fun VolumeBars(model: AppModel, buckets: List<VolumeBucket>, spoken: String, high: BarHigh?, onProgress: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    // Another period stretches the bars: each grows or shrinks to its new height while it slides into its new slot (Motion.STRETCH).
    val motion = LocalMotion.current
    var settled by remember { mutableStateOf(buckets) }
    var running by remember { mutableStateOf<List<VolumeBucket>?>(null) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(buckets, motion) {
        if (buckets == settled || !motion) { settled = buckets; return@LaunchedEffect }
        running = buckets
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.bars())
        settled = buckets
    }
    val before = if (motion && buckets != settled) settled else null
    val t = if (before != null && running != buckets) 0f else progress.value
    // Round 15 (5): a bar that has become the all-time high since the user last saw the bars gets a thin accent cap that glints once as they come into view.
    val spot = rememberSpot()
    val glint = rememberHighGlint(high, spot.visible && onProgress)
    // Round 19: holding the bars and sliding across them shows the bar's bucket (its volume, workouts and sets) on a card above the finger; release dismisses.
    var barsWidth by remember { mutableStateOf(0f) }
    var held by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val tick = rememberTick()
    fun barAt(at: Offset): Int? = (at.x / (barsWidth / buckets.size).coerceAtLeast(1f)).toInt().takeIf { it in buckets.indices && !buckets[it].future }
    val workouts by model.workouts.collectAsState()
    Box {
    Spacer(
        Modifier.fillMaxWidth().height(80.dp).then(spot.modifier).onSizeChanged { barsWidth = it.width.toFloat() }.semantics { contentDescription = spoken }.holdAndSlide(
            key = buckets,
            onHold = { at -> barAt(at)?.let { tick(); held = it to at } },
            onMove = { at -> val i = barAt(at); if (i != null) { if (i != held?.first) tick(); held = i to at } else held = held?.let { it.first to at } },
            onEnd = { held = null },
        ).drawWithCache {
            val stub = 3.dp.toPx()
            fun height(list: List<VolumeBucket>, i: Int): Float {
                val b = list.getOrNull(i) ?: return 0f
                val max = list.maxOf { it.volume }
                return if (b.future || b.volume <= 0.0 || max <= 0.0) stub else maxOf(stub, (b.volume / max).toFloat() * size.height)
            }
            fun slot(list: List<VolumeBucket>) = size.width / list.size
            fun gap(list: List<VolumeBucket>) = slot(list) * if (list.size > 14) 0.25f else 0.3f
            fun mix(a: Float, b: Float) = a + (b - a) * t
            onDrawBehind {
                val old = before ?: buckets
                for (i in 0 until maxOf(old.size, buckets.size)) {
                    val now = buckets.getOrNull(i)
                    val b = now ?: old[i]
                    // A bar with no place on one side starts from, or goes down to, nothing, at the slot it would have.
                    val h = mix(if (before == null) height(buckets, i) else height(old, i), height(buckets, i))
                    val s = mix(slot(old), slot(buckets))
                    val g = mix(gap(old), gap(buckets))
                    val tone = when {
                        b.current -> color
                        b.future || b.volume <= 0.0 -> faint
                        else -> quiet
                    }
                    drawRect(tone, Offset(i * s + g / 2, size.height - h), Size((s - g).coerceAtLeast(0f), h))
                    if (high != null && high.index == i && before == null) drawCapGlint(Offset(i * s + g / 2, size.height - h), Size((s - g).coerceAtLeast(0f), 3.dp.toPx()), color, glint())
                }
            }
        },
    )
    held?.let { (i, at) ->
        val b = buckets.getOrNull(i) ?: return@let
        val totals = remember(b, workouts) { totalsIn(workouts, DayRange(b.start, b.end)) { w -> volumeOf(w, model::loadOf) } }
        PeekPopup(at, "bars peek") {
            PeekHead(bucketName(b))
            if (totals.workouts == 0) PeekLine("No workouts")
            else {
                PeekLine(model.volume(b.volume), bold = true)
                PeekLine("${plural(totals.workouts, "workout")} · ${plural(totals.sets.toInt(), "set")}")
            }
        }
    }
    }
}

/**
 * How far the glint of the all-time-high bar has run (0 at rest, up to 1), started once when [high] is a higher volume than the one of its size the
 * user last saw while the bars were on screen ([visible]); what was last seen is kept for the session in [LocalSeen], so a first look (a cold start)
 * and a high that was seen before glint nothing, and so does a cut with animations off.
 */
@Composable
private fun rememberHighGlint(high: BarHigh?, visible: Boolean): () -> Float {
    val store = LocalSeen.current
    val motion = LocalMotion.current
    val glint = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(high?.size, high?.volume, visible, motion) {
        if (high == null || !visible) return@LaunchedEffect
        val key = "high:${high.size}"
        val seen = store.put(key, high.volume) as? Double
        if (motion && seen != null && high.volume > seen) {
            glint.snapTo(0f)
            glint.animateTo(1f, Motion.capGlint())
            glint.snapTo(0f)
        }
    }
    return { glint.value }
}

/** What the statistics section shows for one period, worked out together. */
private class Figures(val now: PeriodTotals, val before: PeriodTotals?, val buckets: List<VolumeBucket>, val high: BarHigh?)

/** One of the four numbers: the figure large (it rolls when it changes) with its small grey name under it. */
@Composable
private fun StatNumber(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        RollingText(value, "stats:$label", MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A row that opens a page, hairline under it; with a key it grows into its page. */
@Composable
private fun StatRow(title: String, value: String, key: String, last: Boolean = false, onClick: () -> Unit) {
    NavRow(title, value, onClick, key)
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Progress's statistics, laid out on the page with no card (1.1.0, D2): a period (Week, Month, Year, All; kept), the four numbers in one row,
 * one sentence against the same elapsed part of the period before, the volume bars, then the rows that open the pages of statistics.
 */
@Composable
private fun StatisticsSection(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val profile by model.profile.collectAsState()
    val consistency by model.consistency.collectAsState()
    val prs by model.prs.collectAsState()
    val unit = profile?.unit ?: "kg"
    var chosen by model.remembered("stats.period", StatsPeriod.MONTH.label)
    val period = StatsPeriod.entries.firstOrNull { it.label == chosen } ?: StatsPeriod.MONTH
    val today = model.today
    val figures = remember(workouts, period, today) {
        val first = workouts.firstOrNull()?.let { LocalDate.parse(it.day) }
        val range = periodRange(period, today, first)
        // Looked up by the workout itself (its place in the list, found by identity), not by its start: two workouts can share one, and a Workout
        // is a data class, whose hash is every set in it. totalsIn walks the list in order, so the place is found by moving on from the last one.
        val volumes = DoubleArray(workouts.size)
        val perDay = HashMap<LocalDate, Double>()
        for ((i, w) in workouts.withIndex()) { val v = volumeOf(w, model::loadOf); volumes[i] = v; LocalDate.parse(w.day).let { d -> perDay[d] = (perDay[d] ?: 0.0) + v } }
        var at = 0
        val volumeAt = { w: Workout ->
            if (workouts.getOrNull(at) !== w) at = workouts.indexOfFirst { it === w }
            if (at >= 0) volumes[at] else 0.0
        }
        val buckets = volumeBuckets(period, today, first, perDay)
        Figures(
            totalsIn(workouts, range, volumeAt),
            previousRange(period, today)?.let { totalsIn(workouts, it) { w -> volumeOf(w, model::loadOf) } },
            buckets, allTimeHigh(buckets, perDay),
        )
    }
    val monday = weekStart(today).toString()
    val month = today.toString().take(7)
    val now = figures.now
    val old = figures.before
    val against = previousName(period, today)
    val years = remember(workouts) { clean.core.yearsWithWorkouts(workouts) }
    val pbs = remember(workouts, prs, month) { workouts.filter { it.day.startsWith(month) }.sumOf { w -> recordCount(w, prs[w.date].orEmpty()) } }
    PageSection("Statistics", big = true) {
        Segmented(StatsPeriod.entries, period, { it.label }) { chosen = it.label }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StatNumber("Workouts", now.workouts.toString(), Modifier.weight(1f))
            StatNumber("Volume, $unit", model.volume(now.volume).substringBeforeLast(' '), Modifier.weight(1.3f))
            StatNumber("Time trained", duration(now.seconds), Modifier.weight(1.2f))
            StatNumber("Sets", shortNumber(now.sets), Modifier.weight(0.9f))
        }
        if (old != null && against != null) Text(comparisonSentence(now, old, against), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (now.workouts == 0) {
            EmptyPreview("No workouts in this period. Log one to fill these in.", "Start workout", { model.stack.clear(); model.openWorkoutSheet() }) { PreviewSamples.Bars(64.dp) }
        } else {
            val buckets = figures.buckets
            if (buckets.isNotEmpty()) {
                val spoken = remember(buckets, unit) {
                    val busiest = buckets.maxByOrNull { it.volume }
                    "Volume lifted, ${buckets.size} bars, " + (busiest?.takeIf { it.volume > 0 }?.let { "busiest ${bucketName(it)}, ${model.volume(it.volume)}" } ?: "none lifted")
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    VolumeBars(model, buckets, spoken, figures.high, model.tab == Tab.Progress && model.stack.isEmpty())
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(endName(period, buckets.first()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (buckets.size > 1) Text(endName(period, buckets.last()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // The pages of statistics: hairline-separated rows, each with its headline at the right.
        Column {
            StatRow("Personal bests", "${shortNumber(pbs)} this month", "progress:stat:bests") { model.open(Screen.PersonalBests) }
            StatRow("Trends", "${workouts.count { it.day >= monday }} this week", "progress:stat:trends") { model.open(Screen.Trends) }
            StatRow("Trophies", "${consistency?.streak?.length ?: 0}-week streak", "progress:stat:trophies") { model.open(Screen.Trophies) }
            StatRow("Calendar", "${workouts.count { it.day.startsWith(month) }} this month", "progress:stat:calendar", last = years.isEmpty()) { model.open(Screen.Calendar()) }
            // Any year with a workout, newest first: its page has the other years as chips.
            if (years.isNotEmpty()) StatRow("Year in review", years.first().toString(), "progress:stat:years", last = true) { model.open(Screen.YearReview(years.first())) }
        }
    }
}
