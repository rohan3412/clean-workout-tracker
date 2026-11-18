package clean.app

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
import java.time.format.DateTimeFormatter
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
import clean.core.PeriodTotals
import clean.core.StatsPeriod
import clean.core.VolumeBucket
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.core.shortNumber
import clean.core.weekStart
import java.time.LocalDate

/** A line that opens a page, with the page's headline number at the right and a chevron. */
@Composable
internal fun NavRow(title: String, value: String, onClick: () -> Unit, key: String? = null) {
    // With a key the row is a container that grows into its page (see [opensInto]); without one it is the blink, even inside a card that has a key.
    CompositionLocalProvider(LocalCardKey provides key) {
        Row(
            (if (key != null) Modifier.opensInto(null, cardFill(), square = true) else Modifier).fillMaxWidth().clickable(onClick = if (key != null) cardTap(onClick) else onClick).minTouch(),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphText(androidx.compose.ui.text.AnnotatedString(title), "title", style = MaterialTheme.typography.bodyMedium)
            Text(if (value.isEmpty()) "›" else "$value ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** A page in miniature: its name, a little of what is on it, and the whole card opens it (nothing inside takes a tap of its own). */
@Composable
private fun MiniPage(title: String, value: String, modifier: Modifier, key: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) = CompositionLocalProvider(LocalCardKey provides key) {
    val fill = cardFill()
    Card(modifier.cardDepth(cardShape()).clip(cardShape()).pressable(onClick = cardTap(onClick)), shape = cardShape(), colors = CardDefaults.cardColors(containerColor = fill)) {
        Column(Modifier.opensInto(null, fill).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
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
private fun ColumnScope.HistoryRows(model: AppModel) {
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
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state, verticalArrangement = Arrangement.spacedBy(Space.s)) {
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
                    Icon(CleanIcons.StarLine, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(shortNumber(count), maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                MorphText(androidx.compose.ui.text.AnnotatedString(LocalDate.parse(w.day).format(SHORT_DAY)), "row:date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, id = w.date)
            }
            }
        }
    }
}

/**
 * Progress: History and Body as two cards side by side, each a small picture of its page and opening it (every workout as a
 * tiny row, scrolling inside the card; the figure in the Body page's current view, swiped sideways to the next view), then the pages
 * of statistics with their headline numbers.
 */
@Composable
fun ProgressTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val state = model.tabLists.getValue(Tab.Progress)
    BarEnd(Tab.Progress, state)
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state) {
        item { Title("Progress") }
        item {
            // The two stand as tall as each other; the figure sets the height and the rows take what room there is.
            Twins(Modifier.fillMaxWidth().padding(horizontal = Space.m), 8.dp) {
                MiniPage("History", workouts.size.toString(), Modifier, "progress:history", { model.open(Screen.History) }) {
                    if (workouts.isEmpty()) Text("No workouts yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else HistoryRows(model)
                }
                MiniPage("Body", "", Modifier, "progress:body", { model.open(Screen.Body) }) { BodyMini(model) }
            }
        }
        item { StatisticsSection(model) }
        item { BarSpace() }
    }
    }
}


private val DAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val MONTH_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

/** A bar in words, for a screen reader: the day, the month or the year it covers. */
private fun bucketName(b: VolumeBucket): String = when {
    b.start == b.end -> b.start.format(DAY_NAME)
    b.start.monthValue == 1 && b.end.monthValue == 12 -> b.start.year.toString()
    else -> b.start.format(MONTH_NAME)
}

/** The few words under the chart's two ends. */
private fun endName(period: StatsPeriod, b: VolumeBucket): String = when {
    b.start.monthValue == 1 && b.end.monthValue == 12 -> b.start.year.toString()
    period == StatsPeriod.WEEK -> b.start.format(DateTimeFormatter.ofPattern("EEE"))
    period == StatsPeriod.MONTH -> b.start.format(SHORT_DAY)
    period == StatsPeriod.YEAR -> b.start.format(DateTimeFormatter.ofPattern("MMM"))
    else -> b.start.format(DateTimeFormatter.ofPattern("MMM yy"))
}

/** Volume per bucket as bars; the bucket holding today in the accent, the others in a quieter tone of it, the days to come only a faint stub. */
@Composable
private fun VolumeBars(buckets: List<VolumeBucket>, spoken: String) {
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
        progress.animateTo(1f, Motion.stretch())
        settled = buckets
    }
    val before = if (motion && buckets != settled) settled else null
    val t = if (before != null && running != buckets) 0f else progress.value
    Spacer(
        Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = spoken }.drawWithCache {
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
                }
            }
        },
    )
}

/** What the statistics section shows for one period, worked out together. */
private class Figures(val now: PeriodTotals, val before: PeriodTotals?, val buckets: List<VolumeBucket>)

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
    val today = remember { LocalDate.now() }
    val figures = remember(workouts, period) {
        val first = workouts.firstOrNull()?.let { LocalDate.parse(it.day) }
        val range = periodRange(period, today, first)
        val volumes = workouts.associateWith { volumeOf(it, model::loadOf) }
        val perDay = HashMap<LocalDate, Double>()
        for ((w, v) in volumes) perDay.merge(LocalDate.parse(w.day), v, Double::plus)
        Figures(
            totalsIn(workouts, range) { volumes.getValue(it) },
            previousRange(period, today)?.let { totalsIn(workouts, it) { w -> volumeOf(w, model::loadOf) } },
            volumeBuckets(period, today, first, perDay),
        )
    }
    val monday = weekStart(today).toString()
    val month = today.toString().take(7)
    val now = figures.now
    val old = figures.before
    val against = previousName(period, today)
    val pbs = remember(workouts, prs, month) { workouts.filter { it.day.startsWith(month) }.sumOf { w -> recordCount(w, prs[w.date].orEmpty()) } }
    PageSection("Statistics") {
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
                    VolumeBars(buckets, spoken)
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
            StatRow("Calendar", "${workouts.count { it.day.startsWith(month) }} this month", "progress:stat:calendar", last = true) { model.open(Screen.Calendar()) }
        }
    }
}
