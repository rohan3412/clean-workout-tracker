package clean.app

import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
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
import clean.app.ui.theme.LocalTextScale
import clean.core.Workout
import clean.core.WeekTotal
import clean.core.ChartPoint
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












/** The PRs set in the last 30 days, as the tile and Progress count them. */
@Composable
internal fun recentPrCount(model: AppModel): Double {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val since = LocalDate.now().minusDays(30).toString()
    return remember(workouts, allPrs, since) { workouts.filter { it.day >= since }.sumOf { w -> recordCount(w, allPrs[w.date].orEmpty()) } }
}



/** The Activity calendar as Home shows it: the last [weeks] weeks across the width, no scrolling. A day opens the full calendar at that day. */
@Composable
internal fun HomeCalendar(model: AppModel, weeks: Int = 17) {
    val workouts by model.workouts.collectAsState()
    val values = remember(workouts) { secondsByDay(workouts).mapValues { it.value.toDouble() } }
    val spot = rememberSpot()
    val wave = rememberWave("home-activity", spot.visible)
    Box(spot.modifier) { Calendar(values, LocalDate.now(), null, LocalLook.current.scale, rememberScrollState(), wave, weeks) { day -> model.open(Screen.Calendar(day.toString())) } }
}

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")


/**
 * A number with its name, opening the page that explains it. With [spark] (oldest first) a small unlabelled line of the page's
 * chart sits under the number; with [onAdd] a "+" at the right does the tile's quick action (for [addLabel]) without opening the page.
 */
@Composable
internal fun Tile(
    label: String, value: String, modifier: Modifier = Modifier, second: String? = null, icon: ImageVector? = null,
    spark: List<Double>? = null, onAdd: (() -> Unit)? = null, addLabel: String = "", sample: Boolean = false, link: String? = null, valueRole: String? = null, onClick: (() -> Unit)? = null,
) {
    val tileColour = cardFill()
    val tap = if (onClick != null) cardTap(onClick) else null
    Card(if (tap != null) modifier.clip(cardShape()).pressable(onClick = tap) else modifier, shape = cardShape(), colors = CardDefaults.cardColors(containerColor = tileColour)) {
        Column((if (tap != null) Modifier.opensInto(null, tileColour) else Modifier).fillMaxHeight()) {
            Row(Modifier.padding(start = Space.l, top = Space.l, end = Space.l, bottom = Space.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Caption(label, role = "title")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        FitValue(value, MaterialTheme.typography.headlineMedium, if (tap != null) valueRole else null, "tile:${LocalCardKey.current ?: ""}:$label")
                        if (icon != null) Icon(icon, "Shield held", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (second != null) Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (spark != null && spark.size >= 2) Sparkline(spark, Modifier.padding(top = Space.s), morph = link)
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
        }
    }
}

/**
 * A tile's big number on one line: when it does not fit beside what shares its row (a half card's "+", Mono's wider figures, a weight in
 * pounds), it steps down in size, to no less than 70 %, instead of wrapping onto a second line. Nothing is drawn until it fits.
 */
@Composable
private fun FitValue(text: String, style: androidx.compose.ui.text.TextStyle, role: String? = null, rollKey: String? = null) {
    // A figure that changed while its card was away shows the old one until the card is seen, then its digits roll to the new (Roll.kt).
    val seen = rememberSeen(rollKey, text)
    val text = seen.shown
    var scale by remember(text, style) { mutableStateOf(1f) }
    var fits by remember(text, style) { mutableStateOf(false) }
    val shown = style
    val words = androidx.compose.ui.text.AnnotatedString(text)
    val roll = rememberRoll(words)
    val rolling = roll.from != null
    val onLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = { if (it.hasVisualOverflow && scale > 0.7f) scale -= 0.05f else fits = true }
    Box(seen.spot) {
        if (role != null) MorphText(
            words, role, Modifier.drawWithContent { if (fits && !rolling) drawContent() }, style = shown.copy(fontSize = shown.fontSize * scale), fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, onTextLayout = onLayout,
        ) else Text(
            words, Modifier.drawWithContent { if (fits && !rolling) drawContent() }, style = shown.copy(fontSize = shown.fontSize * scale), fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, onTextLayout = onLayout,
        )
        roll.from?.let { RollOverlay(it, words, roll.progress, shown.copy(fontSize = shown.fontSize * scale), FontWeight.SemiBold) }
    }
}

/**
 * A small line of [values], oldest first, with no axes, numbers or dots: decoration on a tile, so a screen reader skips it. Edge to
 * edge it runs from side to side above a faint fill to the bottom, and keeps clear of the bottom so the card's corners do not cut it.
 */
@Composable
internal fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, edge: Boolean = false, height: Dp = if (edge) 44.dp else 28.dp, morph: String? = null) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val density = LocalDensity.current
    val pad = with(density) { 2.dp.toPx() }
    val padX = if (edge) 0f else pad
    val top = if (edge) with(density) { 4.dp.toPx() } else pad
    val bottom = if (edge) with(density) { 10.dp.toPx() } else pad
    val low = values.min()
    val span = (values.max() - low).takeIf { it > 0 }
    fun line(size: Size): Pair<FloatArray, FloatArray> = FloatArray(values.size) { padX + it.toFloat() / (values.size - 1) * (size.width - 2 * padX) } to
        // A level line runs along the middle.
        FloatArray(values.size) { if (span == null) size.height / 2 else top + (1 - ((values[it] - low) / span).toFloat()) * (size.height - top - bottom) }
    val lineWidth = with(density) { 2.dp.toPx() }
    val travel = rememberChartMorph(
        morph, color, lineWidth, null, with(density) { 3.dp.toPx() },
        // A line with no tint still says where its floor is, so the other side's tint keeps its shape as it goes (or comes) to nothing.
        wash = { size -> ChartWash(size.height, top, if (edge) 0.12f else 0f, if (edge) 0.12f else 0f, 0f, edge) },
    ) { line(it) }
    Spacer(modifier.fillMaxWidth().height(height).then(travel.modifier).drawWithCache {
        val (xs, ys) = line(size)
        val path = Path()
        path.curveThrough(xs, ys)
        val fill = if (edge) Path().apply { addPath(path); lineTo(xs.last(), size.height); lineTo(0f, size.height); close() } else null
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            // While the card opens or closes, the tint travels with the line (see [ChartWash]).
            if (!travel.hidden()) fill?.let { drawPath(it, color.copy(alpha = 0.12f)) }
            // While the card opens or closes, the one line that travels to the page's chart stands in for this one.
            if (!travel.hidden()) {
                drawPath(path, color, style = stroke)
            }
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
private fun Calendar(values: Map<LocalDate, Double>, today: LocalDate, first: LocalDate?, palette: List<Color>, scroll: ScrollState, wave: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null, span: Int? = null, onTap: (LocalDate) -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val thisMonday = weekStart(today)
    // [span]: exactly that many weeks, fitted to the width (Home's), instead of the scrolling history.
    val weeks = span ?: maxOf(WEEKS, first?.let { ChronoUnit.WEEKS.between(weekStart(it), thisMonday).toInt() + 1 } ?: 0)
    val start = thisMonday.minusWeeks(weeks - 1L)
    val max = remember(values, thisMonday) { values.filterKeys { it >= thisMonday.minusWeeks(WEEKS - 1L) }.values.maxOrNull() ?: 0.0 }
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val outline = MaterialTheme.colorScheme.onSurface
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
        val cell = maxWidth / (span ?: WEEKS)
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
                        // The first time the card is seen, the squares come in from the top left of what is in view (Roll.kt).
                        val came = if (wave == null) 1f else Motion.waveCell(wave.value, (week - (seen / c).toInt()).coerceAtLeast(0) + day)
                        val color = (if (value <= 0 || max <= 0) empty else palette[(ceil(value / max * 4).toInt() - 1).coerceIn(0, 3)]).let { it.copy(alpha = it.alpha * came) }
                        val full = Size(c - gap, c - gap)
                        val box = if (came >= 1f) full else Size(full.width * (0.6f + 0.4f * came), full.height * (0.6f + 0.4f * came))
                        val origin = Offset(week * c + gap / 2 + (full.width - box.width) / 2, top + day * c + gap / 2 + (full.height - box.height) / 2)
                        drawRoundRect(color, origin, box, CornerRadius(c * 0.2f))
                        if (date == today) drawRoundRect(outline.copy(alpha = outline.alpha * came), origin, box, CornerRadius(c * 0.2f), style = Stroke(1.5.dp.toPx()))
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
    morph: String? = null,
    onTap: ((LocalDate) -> Unit)? = null,
) {
    val plain = remember(points) { points.map { ChartPoint(it.first, it.second) } }
    SplitLineChart(plain, unit, range, bands, title, height, trend, goal, onTap = onTap, morph = morph)
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
    /** The role of the line that travels from a card to this chart on its page ([rememberChartMorph]); only a plain line travels. */
    morph: String? = null,
    onTap: ((LocalDate) -> Unit)? = null,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val latestColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val goalColor = LocalCleanColors.current.done
    // The tint of a band: stronger on a dark page.
    val fillAlpha = if (LocalLook.current.dark) 0.22f else 0.07f
    // The axis takes in both ends of every stem, and the zero of an imbalance chart.
    val dataLow = minOf(points.minOf { it.low }, range?.start ?: Double.MAX_VALUE, goal ?: Double.MAX_VALUE, if (imbalance) 0.0 else Double.MAX_VALUE)
    val high = maxOf(points.maxOf { it.high }, range?.endInclusive ?: -Double.MAX_VALUE, goal ?: -Double.MAX_VALUE, if (imbalance) 0.0 else -Double.MAX_VALUE)
    val low = dataLow
    val latest = points.last().value
    val first = points.first().date
    val days = ChronoUnit.DAYS.between(first, points.last().date).coerceAtLeast(1)
    val hasSplit = remember(points) { points.any { it.split } }
    // Another range stretches the line along its dates from the old frame to the new (ChartStretch.kt): the axes, the values on them and the dates ease together.
    val target = Frame(first.toEpochDay().toDouble(), days.toDouble(), listOf(low, high, dataLow, latest))
    val stretch = rememberStretch(target, listOf(points), listOf(title, unit, imbalance))
    val frame = stretch.at(target)
    val moving = stretch.moving
    val drawPoints = if (moving) stretch.union(0, points) else points
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
                    shape = SmallShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp,
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
    val density = LocalDensity.current
    // The plain line (no bars, sides or imbalance) is the one that can travel from a card: its points as drawn below.
    val travel = rememberChartMorph(
        if (hasSplit || imbalance) null else morph, line, with(density) { 2.5f.dp.toPx() },
        null, with(density) { 4.5.dp.toPx() },
        wash = { size -> ChartWash(size.height - with(density) { 10.dp.toPx() }, with(density) { 10.dp.toPx() }, 0f, 0f, 0f, false) },
    ) { size ->
        val left = with(density) { 46.dp.toPx() }
        val top = with(density) { 10.dp.toPx() }
        val bottom = size.height - top
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        FloatArray(points.size) { left + ChronoUnit.DAYS.between(first, points[it].date).toFloat() / days * (size.width - left) } to
            FloatArray(points.size) { top + ((high - points[it].value) / span).toFloat() * (bottom - top) }
    }
    val splits = points.count { it.split }
    val lastPoint = points.last()
    val summary = when {
        imbalance -> "${title.ifEmpty { "Imbalance" }}, ${points.size} sessions, latest ${leadWords(latest)}, from ${leadWords(points.minOf { it.value })} to ${leadWords(points.maxOf { it.value })}"
        else -> "${title.ifEmpty { "Chart" }}, ${points.size} points" + (if (splits > 0) ", $splits of them left and right, " + "left solid line, right dashed line" else "") +
            ", from ${shortNumber(points.minOf { it.low })} to ${shortNumber(points.maxOf { it.high })} $unit, " +
            (if (lastPoint.split) "latest left ${shortNumber(lastPoint.left!!)}, right ${shortNumber(lastPoint.right!!)}" else "latest ${shortNumber(latest)}")
    }.replace(" ,", ",")
    Spacer(Modifier.fillMaxWidth().height(height).then(travel.modifier).semantics { contentDescription = summary; role = Role.Image }.pointerInput(points) {
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
        val span = (frame.ys[1] - frame.ys[0]).takeIf { it > 0 } ?: 1.0
        fun y(value: Double) = top + ((frame.ys[1] - value) / span).toFloat() * (bottom - top)
        val text = Paint().apply { color = labelColor; textSize = 11.sp.toPx() * textScale; isAntiAlias = true }
        val bold = Paint(text).apply { color = latestColor.toArgb(); isFakeBoldText = true }
        val goalPaint = Paint(text).apply { color = goalColor.toArgb(); isFakeBoldText = true }
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        val latestY = y(latest)
        val near = 13.sp.toPx() * textScale
        val path = Path()
        fun x(p: ChartPoint) = left + ((p.date.toEpochDay() - frame.x0) / frame.xSpan).toFloat() * (size.width - left)
        path.curveThrough(FloatArray(drawPoints.size) { x(drawPoints[it]) }, FloatArray(drawPoints.size) { y(drawPoints[it].value) })
        val marks = if (hasSplit) SplitMarks(drawPoints, this, ::x, ::y) else null
        val zeroY = y(0.0)
        val stroke = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            for ((band, color) in bands) {
                val from = y(minOf(band.endInclusive, frame.ys[1]))
                val to = y(maxOf(band.start, frame.ys[0]))
                if (to > from) drawRect(color.copy(alpha = fillAlpha), Offset(left, from), Size(size.width - left, to - from))
            }
            fun label(value: Double, paint: Paint) = drawContext.canvas.nativeCanvas.drawText(shortNumber(value), 0f, y(value) + text.textSize / 3, paint)
            drawLine(grid, Offset(left, top), Offset(size.width, top))
            drawLine(grid, Offset(left, bottom), Offset(size.width, bottom))
            // The latest value: a dotted line across the chart and its number on the axis.
            drawLine(latestColor, Offset(left, latestY), Offset(size.width, latestY), 1.5.dp.toPx(), pathEffect = dashes)
            // The values on the axis: while the range changes the old ones fade out where they are on the moving scale and the new fade in.
            fun axis(top2: Double, low2: Double, now2: Double, alpha: Float) {
                text.alpha = (255 * alpha).toInt(); bold.alpha = (255 * alpha).toInt()
                val atY = y(now2)
                if (atY - top > near) label(top2, text)
                if (bottom - atY > near) label(low2, text)
                label(now2, bold)
            }
            val before = stretch.from
            if (before != null) {
                axis(before.ys[1], before.ys[2], before.ys[3], 1f - stretch.t)
                axis(high, dataLow, latest, stretch.t)
            } else axis(high, dataLow, latest, 1f)
            if (imbalance) {
                // Right minus left: the zero line is the axis, drawn solid and labelled unless another label is there.
                drawLine(trendColor, Offset(left, zeroY), Offset(size.width, zeroY), 1.5.dp.toPx())
                if (zeroY - top > near && bottom - zeroY > near && Math.abs(zeroY - latestY) > near) label(0.0, text)
            }
            if (goal != null) {
                drawLine(goalColor, Offset(left, y(goal)), Offset(size.width, y(goal)), 1.5.dp.toPx())
                if (Math.abs(y(goal) - latestY) > near) label(goal, goalPaint)
            }
            if (!moving) fit?.let { (slope, start) ->
                clipRect(left = left, top = top, bottom = bottom) { drawLine(trendColor, Offset(left, y(start)), Offset(size.width, y(start + slope * days)), 1.5.dp.toPx()) }
            }
            // While the range changes the line is clipped to the plot, so the part outside the new range slides off the left edge and the older part comes in from it.
            clipRect(left = if (moving) left else 0f, right = left + (size.width - left) * drawn) {
                if (marks != null) drawSplitMarks(marks, line)
                else {
                    // While a card opens into this page, the line travelling from the card's chart stands in for this one.
                    if (!travel.hidden()) drawPath(path, line, style = stroke)
                }
            }
        }
    })
    // The dates under the chart cross over when the range changes.
    Crossfade(first, Modifier.fillMaxWidth(), animationSpec = if (LocalMotion.current) Motion.stretch() else snap(), label = "range dates") { shownFirst ->
        Row(Modifier.fillMaxWidth().padding(start = 46.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(prettyDay(shownFirst), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val now = if (lastPoint.split) "L ${shortNumber(lastPoint.left!!)} · R ${shortNumber(lastPoint.right!!)}" else shortNumber(latest)
            Text("$now $unit · ${prettyDay(lastPoint.date)}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (hasSplit) SidesKey(line, Modifier.padding(start = 46.dp))
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
private fun SidesKey(color: Color, modifier: Modifier = Modifier) {
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Key: solid line with filled markers is left, dashed line with rings is right" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        for (right in listOf(false, true)) {
            Canvas(Modifier.size(28.dp, 12.dp)) {
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
private fun DrawScope.drawSplitMarks(marks: SplitMarks, color: Color) {
    drawPath(marks.stems, color.copy(alpha = 0.2f), style = marks.stem)
    drawPath(marks.leftLine, color, style = marks.solid)
    drawPath(marks.rightLine, color, style = marks.dashed)
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

/** One line of a [MultiLineChart] worked out for its size. */
private data class Drawn(val path: Path, val color: Color, val marks: SplitMarks?)

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

/** The same four hues deepened for a light page, where the pale ones above all but vanish: each keeps 3:1 against white. */
private val SERIES_COLORS_LIGHT = listOf(Color(0xFF2B62C4), Color(0xFF946200), Color(0xFF0B7D60), Color(0xFFC2306E))

/** The chart lines' colours under [look]: [SERIES_COLORS] or their light twins. */
internal fun seriesColorsOf(look: Look): List<Color> = if (look.dark) SERIES_COLORS else SERIES_COLORS_LIGHT

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
fun MultiLineChart(series: List<ChartSeries>, title: String = "", height: Dp = 190.dp, shared: Boolean = false, morph: String? = null, morphLabel: String? = null) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val first = series.minOf { it.points.first().date }
    val last = series.maxOf { it.points.last().date }
    val days = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val drawn by drawnIn()
    // Another range stretches every line along its dates, each on its own scale (ChartStretch.kt).
    val target = Frame(first.toEpochDay().toDouble(), days.toDouble(), series.flatMap { line -> val scale = if (shared) series else listOf(line); listOf(scale.minOf { s -> s.points.minOf { it.low } }, scale.maxOf { s -> s.points.maxOf { it.high } }) })
    val stretch = rememberStretch(target, series.map { it.points }, listOf(title, series.map { it.label }))
    val frame = stretch.at(target)
    val moving = stretch.moving
    // The line named [morphLabel], when plain, is the one that travels from a card ([rememberChartMorph]).
    val density = LocalDensity.current
    val travelling = series.indexOfFirst { it.label == morphLabel }.takeIf { it >= 0 && series[it].points.none { p -> p.split } }
    val travel = rememberChartMorph(
        if (travelling == null) null else morph, travelling?.let { series[it].color } ?: accent,
        with(density) { 2.5f.dp.toPx() },
    ) { size ->
        val line = series[travelling!!]
        val scale = if (shared) series else listOf(line)
        val top = with(density) { 10.dp.toPx() }
        val bottom = size.height - top
        val low = scale.minOf { s -> s.points.minOf { it.low } }
        val span = (scale.maxOf { s -> s.points.maxOf { it.high } } - low).takeIf { it > 0 } ?: 1.0
        val shown = line.points
        FloatArray(shown.size) { ChronoUnit.DAYS.between(first, shown[it].date).toFloat() / days * size.width } to
            FloatArray(shown.size) { bottom - ((shown[it].value - low) / span).toFloat() * (bottom - top) }
    }
    Spacer(Modifier.fillMaxWidth().height(height).then(travel.modifier).semantics { contentDescription = "${title.ifEmpty { "Chart" }}:${series.joinToString(", ") { "${it.label}, latest ${latestSpoken(it.points.last())} ${it.unit}".trim() + (if (it.points.any { p -> p.split }) ", ${it.points.count { p -> p.split }} sessions left and right, left solid line, right dashed line" else "") }}"; role = Role.Image }.drawWithCache {
        // Each line is worked out once for this size, not on each frame of its drawing-in.
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val paths = series.mapIndexed { index, line ->
            // The line's own scale takes in both ends of every stem; a shared one takes in every line (worked out in [target]).
            val low = frame.ys[2 * index]
            val span = (frame.ys[2 * index + 1] - low).takeIf { it > 0 } ?: 1.0
            fun y(value: Double) = bottom - ((value - low) / span).toFloat() * (bottom - top)
            fun x(p: ChartPoint) = ((p.date.toEpochDay() - frame.x0) / frame.xSpan).toFloat() * size.width
            val own = stretch.union(index, line.points)
            val path = Path()
            val shown = own
            path.curveThrough(FloatArray(shown.size) { x(shown[it]) }, FloatArray(shown.size) { y(shown[it].value) })
            val marks = if (shown.any { it.split }) SplitMarks(shown, this, ::x, ::y) else null
            Drawn(path, line.color ?: accent, marks)
        }
        val stroke = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            drawLine(grid, Offset(0f, top), Offset(size.width, top))
            drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom))
            clipRect(left = if (moving) 0f else -size.width, right = size.width * drawn) {
                for ((i, drawnLine) in paths.withIndex()) {
                    val (path, color, marks) = drawnLine
                    if (marks != null) drawSplitMarks(marks, color)
                    else {
                        // The line travelling from a card stands in for this one while the card opens or closes.
                        if (i != travelling || !travel.hidden()) drawPath(path, color, style = stroke)
                    }
                }
            }
        }
    })
    // The dates under the chart cross over when the range changes.
    Crossfade(first to last, Modifier.fillMaxWidth(), animationSpec = if (LocalMotion.current) Motion.stretch() else snap(), label = "range dates") { (shownFirst, shownLast) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(prettyDay(shownFirst), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(prettyDay(shownLast), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(chart.title.ifEmpty { "Chart" }, model::back)
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
