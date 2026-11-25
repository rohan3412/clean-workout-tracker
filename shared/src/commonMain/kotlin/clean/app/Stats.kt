package clean.app

import clean.core.time.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.style.TextOverflow
import clean.app.ui.theme.GlassWindowShape
import clean.app.ui.theme.PillShape
import clean.core.volumeOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.Look
import clean.app.ui.theme.orange
import clean.app.ui.theme.LocalTextScale
import clean.core.Workout
import androidx.compose.ui.unit.Constraints
import clean.core.WeekTotal
import clean.core.ChartPoint
import clean.core.linearFit
import clean.core.monotoneSlopes
import clean.core.monthsInView
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
import clean.app.platform.LabelPaint
import clean.app.platform.LocalPlatform
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import clean.core.secondsByDay
import clean.core.spanText
import clean.core.shortNumber
import clean.core.volumeByDay
import dev.chrisbanes.haze.hazeSource
import clean.core.weekStart
import kotlinx.datetime.LocalDate
import kotlin.math.ceil

/** The weeks the activity chart shows at least, and the weeks its colour scale is taken from. */
private const val WEEKS = 26
private val SINCE = LocalePattern("MMM yyyy")

/** The weigh-ins, oldest first: (day, kg). */
@Composable
fun weighIns(model: AppModel): List<Pair<LocalDate, Double>> {
    val measurements by model.measurements.collectAsState()
    return remember(measurements) {
        measurements.filter { it.file == "weight.csv" }.mapNotNull { m -> parseDate(m.date)?.let { d -> m.value.toDoubleOrNull()?.let { d.toLocalDate() to it } } }.reversed()
    }
}












/** The weigh-ins of the last [WEEKS] weeks as one mean a week, oldest first: a weigh-in a day would make the small line a scribble. */
fun weightLine(weights: List<Pair<LocalDate, Double>>): List<Double> =
    weights.filter { it.first >= LocalDate.now().minusWeeks(WEEKS.toLong()) }.groupBy { weekStart(it.first) }.toList().sortedBy { it.first }.map { it.second }.map { w -> w.sumOf { it.second } / w.size }

/** The PRs set in the last 30 days, as the tile and Progress count them. */
@Composable
fun recentPrCount(model: AppModel): Double {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val since = LocalDate.now().minusDays(30).toString()
    return remember(workouts, allPrs, since) { workouts.filter { it.day >= since }.sumOf { w -> recordCount(w, allPrs[w.date].orEmpty()) } }
}

/**
 * Activity, the same on Home and on Progress, on the page with no card: the title with "since <month>" at its right, a Time / Volume switch,
 * the calendar scrolling back to the first workout (it opens at the present), and under it how far back it goes and the colour scale. The
 * scroll and the Time / Volume choice live in the model, so both pages show one and the same thing. A sideways drag on the calendar grid
 * scrolls it and never changes tab; anywhere else on the block it pages Home and Progress. The title brings the chart back to the present when it was dragged into the past, and otherwise
 * opens the full calendar; a day opens the calendar at it. [overlay] is the copy drawn above the pager while it moves between Home and
 * Progress: the same pixels, with no touch of its own.
 */
@Composable
fun ActivityBody(model: AppModel, tab: Tab, overlay: Boolean = false) {
    val workouts by model.workouts.collectAsState()
    val palette = LocalLook.current.scale
    val today = model.today
    val byVolume = model.activityByVolume
    val scroll = model.activityScroll
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val first = remember(workouts) { workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    // Today's square fills in once when Home is shown again after a workout was finished.
    val fresh = remember { Animatable(1f) }
    val onHome = model.tab == Tab.Home && model.stack.isEmpty()
    if (tab == Tab.Home && !overlay) LaunchedEffect(model.freshDay, onHome, motion) {
        if (model.freshDay == null) return@LaunchedEffect
        // The square is emptied the moment the workout is saved, while the workout page still covers Home, so it is never seen filled first.
        if (motion) fresh.snapTo(0f)
        if (!onHome) return@LaunchedEffect
        if (motion) {
            delay(250)
            fresh.animateTo(1f, tween(500))
        }
        model.freshDay = null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(
            Modifier.fillMaxWidth().then(
                if (overlay) Modifier else Modifier.clickable {
                    if (scroll.value < scroll.maxValue) scope.launch { if (motion) scroll.animateScrollTo(scroll.maxValue, Motion.short()) else scroll.scrollTo(scroll.maxValue) }
                    else model.open(Screen.Calendar())
                },
            ),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("Activity", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (first != null) Text("since ${first.format(SINCE)}", Modifier.padding(bottom = Space.xxs), style = MaterialTheme.typography.bodySmall, color = quiet)
        }
        Segmented(listOf(false, true), byVolume, { if (it) "Volume" else "Time" }, Modifier.width(184.dp)) { model.activityByVolume = it }
        val values: Map<LocalDate, Double> = remember(workouts, byVolume) {
            if (byVolume) volumeByDay(workouts, model::loadOf) else secondsByDay(workouts).mapValues { it.value.toDouble() }
        }
        val byDay = remember(workouts) { workouts.groupBy { it.day } }
        // The calendar is there whole from the first frame: no wave of squares coming in from the left (the user, round 9: "it should be fully loaded").
        val spot = rememberSpot()
        Box(spot.modifier.then(if (overlay) Modifier else Modifier.nestedScroll(SwallowSideways))) {
            Calendar(model, values, byDay, today, first, palette, scroll, null, live = !overlay, fresh = { fresh.value }) { day -> model.open(Screen.Calendar(day.toString())) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Less ", style = MaterialTheme.typography.labelMedium, color = quiet)
            for (color in palette) Box(Modifier.padding(horizontal = Space.xxs).size(11.dp).background(color, clean.app.ui.theme.LegendShape))
            Text(" More", style = MaterialTheme.typography.labelMedium, color = quiet)
        }
    }
}

/**
 * Activity as an item of a tab's list (Home's and Progress's are this one): padded as a section on the page, it reports where it starts (and how tall it is) wherever the list is scrolled to
 * (the pager draws one copy of it above both pages while they slide, at the y between the two) and is hidden while that copy is up.
 */
@Composable
fun ActivityItem(model: AppModel, tab: Tab, state: androidx.compose.foundation.lazy.LazyListState) {
    val y = model.activityY.getValue(tab)
    val height = model.activityH.getValue(tab)
    val nat = model.activityNat.getValue(tab)
    val otherTab = if (tab == Tab.Home) Tab.Progress else Tab.Home
    val other = model.activityNat.getValue(otherTab)
    DisposableEffect(Unit) { onDispose { y.floatValue = Float.NaN; height.floatValue = Float.NaN } }
    // The page whose Activity would start higher than the other's has a gap above it, so the two start at the same y (at the text size where they already agree, no gap).
    // Each page's natural y is kept from the last run (kv `activity.nat.<tab>`), so the gap is right on the first frame instead of the
    // content dropping once the other page is first laid out about a second after start.
    fun stored(t: Tab) = model.options.value["activity.nat.${t.name}"]?.toFloatOrNull() ?: Float.NaN
    val mine = if (nat.floatValue.isNaN()) stored(tab) else nat.floatValue
    val theirs = if (other.floatValue.isNaN()) stored(otherTab) else other.floatValue
    val gap = if (mine.isNaN() || theirs.isNaN()) 0f else (theirs - mine).coerceAtLeast(0f)
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned {
            if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
                val at = it.positionInRoot().y
                nat.floatValue = at
                if (abs(at - stored(tab)).let { d -> d.isNaN() || d > 0.5f }) model.setOption("activity.nat.${tab.name}", at.toString())
            }
        },
    ) {
        Spacer(Modifier.height(with(LocalDensity.current) { gap.toDp() }))
        Column(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.s)
                .onGloballyPositioned { if (it.isAttached) { y.floatValue = it.positionInRoot().y; height.floatValue = it.size.height.toFloat() } }
                .graphicsLayer { alpha = if (model.activityOverlay.value) 0f else 1f },
        ) { ActivityBody(model, tab) }
    }
}

/** Takes what a sideways scroll has left over, so the pager under Activity never takes it: the end of the calendar stops the drag, it does not change tab. */
private val SwallowSideways = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

/**
 * A fluid fling, close to the phone's own: a friction 1.2 times the plain one and a start speed capped only at 8000 px/s, so the
 * calendar coasts freely (the user, round 5: "it should be more fluid"; the earlier jump came from a second scroller, now gone).
 */
private val GentleFling = object : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        var last = 0f
        var left = initialVelocity
        AnimationState(0f, initialVelocity.coerceIn(-8000f, 8000f)).animateDecay(exponentialDecay(frictionMultiplier = 1.2f)) {
            val wanted = value - last
            val used = scrollBy(wanted)
            last = value
            left = velocity
            // Stopped by an end of the scroll: nothing is left to carry on with.
            if (abs(wanted - used) > 0.5f) { left = 0f; cancelAnimation() }
        }
        return left
    }
}

/** How wide the one day card is, wherever it shows (a tap on the Calendar page, a hold on Activity or History). */
val DAY_CARD_WIDTH = 248.dp

/**
 * One workout of a day card, the same everywhere so the cards never drift apart: its name, "HH:mm · length · volume · N PRs", and its
 * exercises on one line.
 */
@Composable
fun DayWorkoutLines(model: AppModel, w: Workout, modifier: Modifier = Modifier) {
    val prs by model.prs.collectAsState()
    val count = recordCount(w, prs[w.date].orEmpty())
    Column(modifier) {
        Text(w.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${w.date.drop(11).take(5)} · ${duration(w.durationSeconds)} · ${model.volume(volumeOf(w, model::loadOf))} · ${prsText(count)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            w.blocks.filter { it.sets.isNotEmpty() }.map { it.exercise }.distinct().joinToString(", "),
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What was done on [day], in a small glass card with no actions, for a hold: each workout as [DayWorkoutLines]. [haze] is the source of a sibling it floats over (it blurs it), or none (opaque). */
@Composable
fun DayPeek(model: AppModel, day: LocalDate, done: List<Workout>, modifier: Modifier = Modifier, haze: dev.chrisbanes.haze.HazeState? = null) {
    Column(modifier.width(DAY_CARD_WIDTH).testTag("day peek").glass(GlassWindowShape, haze, lift = 8.dp).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(prettyDay(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (w in done.take(2)) DayWorkoutLines(model, w)
        if (done.size > 2) Text("+${done.size - 2} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Holding (a long press that has not moved) calls [onHold] with the finger's place, then [onMove] for each move while it is held, then [onEnd] on
 * release. Nothing is consumed before the hold, so a tap and a scroll go on as they were; once held, the touch is this one's alone (no scroll, no tap).
 */
fun Modifier.holdAndSlide(key: Any?, onHold: (Offset) -> Unit, onMove: (Offset) -> Unit, onEnd: () -> Unit): Modifier = composed {
    val hold by rememberUpdatedState(onHold)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val broken = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var again = true
                while (again) {
                    val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) again = false
                }
                true
            }
            if (broken != null) return@awaitEachGesture
            hold(down.position)
            var held = true
            while (held) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (c == null) break
                c.consume()
                if (c.pressed) move(c.position) else held = false
            }
            end()
        }
    }
}

/**
 * Round 19: a hold's small glass card in a window of its own, above the finger at [at] (in the coordinates of the layout this is composed in, which is the
 * item held), kept inside the window with the page's side margin ([InsideWindow]); no focus, no taps. A window cannot blur the page, so the opaque form.
 */
@Composable
fun PeekPopup(at: Offset, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val place = remember(at, d) { with(d) { InsideWindow(at, true, Space.l.roundToPx(), 0f, 0f, 28.dp.toPx(), 36.dp.toPx()) } }
    Popup(popupPositionProvider = place, properties = PopupProperties(focusable = false)) {
        Column(Modifier.width(DAY_CARD_WIDTH).testTag(tag).glass(GlassWindowShape, haze = null, lift = 8.dp).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs), content = content)
    }
}

/** A peek's small grey heading. */
@Composable
fun PeekHead(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** One line of a peek: grey, or [bold] in the text colour. */
@Composable
fun PeekLine(text: String, bold: Boolean = false) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (bold) FontWeight.Bold else null)

/** The History peek's content, the same as the day card of a hold on Activity or History: the day, then the workout as [DayWorkoutLines]. */
@Composable
fun ColumnScope.WorkoutPeek(model: AppModel, w: Workout) {
    PeekHead(prettyDay(LocalDate.parse(w.day)))
    DayWorkoutLines(model, w)
}

/**
 * Wraps [content] so that holding it ([holdAndSlide], the one hold) shows [peek] on a [PeekPopup] above the finger until release; a tap and a scroll inside go on as they
 * were and a hold is not a tap. With a null [peek] it is only a Box.
 */
@Composable
fun HoldPeekBox(tag: String, peek: (@Composable ColumnScope.() -> Unit)?, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    if (peek == null) { Box(modifier, content = content); return }
    var at by remember { mutableStateOf<Offset?>(null) }
    val tick = rememberTick()
    Box(modifier.holdAndSlide(key = Unit, onHold = { tick(); at = it }, onMove = { at = it }, onEnd = { at = null })) {
        content()
        at?.let { PeekPopup(it, tag, peek) }
    }
}

private val SHORT_DAY = LocalePattern("d MMM")


/**
 * A tile (Lit): a smaller card on Home, the tint at 4.5 % with a lit edge, at least 132 dp tall. [label] is its small grey caption, with [action]
 * (a round button) at the right; the whole tile opens a page, growing out of the card [key]. In a row the tiles take the row's height.
 */
@Composable
fun Tile(
    label: String, modifier: Modifier = Modifier, key: String? = null, hold: (() -> List<HoldAction>)? = null, action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, 
    /** Round 19: a card shown above the finger while the tile is held (a window of its own); a tap is unchanged, a hold is not a tap. */
    peekTag: String = "tile peek", peek: (@Composable ColumnScope.() -> Unit)? = null,
    /** Round 24: 0 makes the tile as tall as its content (a strip of short figures); the default keeps the 132 dp the Home tiles share. */
    minHeight: Dp = 132.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalCardKey provides key) {
        var peeked by remember { mutableStateOf<Offset?>(null) }
        val peekTick = rememberTick()
        val shape = cardShape()
        val fill = LocalLook.current.tile
        val tap = onClick?.let { cardTap(it) }
        val pressed = remember { mutableStateOf(false) }
        Box(modifier.heightIn(min = minHeight).clip(shape).then(if (tap != null) Modifier.pressable(dip = 0.98f, pressed = pressed, onClick = tap) else Modifier).holdMenu(shape, hold, fill).then(if (peek == null) Modifier else Modifier.holdAndSlide(Unit, { peekTick(); peeked = it }, { peeked = it }, { peeked = null })).tile(shape, pressedNow = { pressed.value }, frosted = true)) {
            peeked?.let { PeekPopup(it, peekTag, peek!!) }
            Column((if (tap != null) Modifier.opensInto(null, fill) else Modifier).fillMaxSize().padding(Space.l), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label.isNotEmpty() || action != null) Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Caption(label, role = "title") }
                    action?.invoke()
                }
                content()
            }
        }
    }
}

/** A tile's number large, with its unit small and grey beside it; the figure rolls when it changes (see [FitValue]). */
@Composable
fun TileNumber(value: String, unit: String? = null, rollKey: String? = null, role: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(Modifier.weight(1f, fill = false)) { FitValue(value, MaterialTheme.typography.headlineMedium, role, rollKey) }
        if (unit != null) Text(unit, Modifier.padding(bottom = Space.xs), maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The grey line of a tile under its number. */
@Composable
fun TileLine(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)

/** A tile's number with the round "+" on its line, right-aligned and centred on the number (36 dp disc in a 48 dp touch area, 6 dp of which reach past the tile's padding). */
@Composable
fun TileNumberAdd(label: String, onClick: () -> Unit, onBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null, number: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { number() }
        Box(Modifier.requiredSize(48.dp).offset(x = 6.dp)) { RoundAdd(label, onClick, onBounds) }
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
 * A small line of [values], oldest first, with no axes or numbers: decoration on a tile, so a screen reader skips it. Edge to edge it runs from side
 * to side above a faint fill to the bottom; with [wash] a soft wash of the accent fades under the line and a dot marks its end.
 */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, edge: Boolean = false, height: Dp = if (edge) 44.dp else 28.dp, morph: String? = null, wash: Boolean = false, goal: Double? = null, reveal: () -> Float = { 1f }) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val density = LocalDensity.current
    val pad = with(density) { 2.dp.toPx() }
    val padX = if (edge) 0f else pad
    val top = if (edge) with(density) { 4.dp.toPx() } else pad
    val bottom = if (edge) with(density) { 10.dp.toPx() } else pad
    val low = minOf(values.min(), goal ?: Double.MAX_VALUE)
    val span = (maxOf(values.max(), goal ?: Double.MIN_VALUE) - low).takeIf { it > 0 }
    fun line(size: Size): Pair<FloatArray, FloatArray> = FloatArray(values.size) { padX + it.toFloat() / (values.size - 1) * (size.width - 2 * padX) } to
        // A level line runs along the middle.
        FloatArray(values.size) { if (span == null) size.height / 2 else top + (1 - ((values[it] - low) / span).toFloat()) * (size.height - top - bottom) }
    val lineWidth = with(density) { 2.dp.toPx() }
    val travel = rememberChartMorph(
        morph, color, lineWidth, if (wash) color else null, with(density) { 3.dp.toPx() },
        // A line with no tint still says where its floor is, so the other side's tint keeps its shape as it goes (or comes) to nothing.
        wash = { size -> if (wash) ChartWash(size.height, 0f, 0.26f, 0f, 0f, edge) else ChartWash(size.height, top, if (edge) 0.12f else 0f, if (edge) 0.12f else 0f, 0f, edge) },
    ) { line(it) }
    Spacer(modifier.fillMaxWidth().height(height).then(travel.modifier).drawWithCache {
        val (xs, ys) = line(size)
        val path = Path()
        path.curveThrough(xs, ys)
        val fill = if (edge || wash) Path().apply { addPath(path); lineTo(xs.last(), size.height); lineTo(xs.first(), size.height); close() } else null
        val washBrush = Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f)))
        val dot = 3.dp.toPx()
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val goalY = goal?.let { if (span == null) size.height / 2 else top + (1 - ((it - low) / span).toFloat()) * (size.height - top - bottom) }
        val dashed = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        onDrawBehind {
            // The goal, a faint dashed line across the whole chart.
            if (goalY != null) drawLine(color.copy(alpha = 0.45f), Offset(0f, goalY), Offset(size.width, goalY), dashed.width, pathEffect = dashed.pathEffect)
            // A new point: the last segment draws in from the one before it, left to right ([reveal] goes from 0 to 1).
            val shown = reveal()
            val body: DrawScope.() -> Unit = {
                // While the card opens or closes, the tint travels with the line (see [ChartWash]).
                if (!travel.hidden()) fill?.let { if (wash) drawPath(it, washBrush) else drawPath(it, color.copy(alpha = 0.12f)) }
                // While the card opens or closes, the one line that travels to the page's chart stands in for this one.
                if (!travel.hidden()) drawPath(path, color, style = stroke)
            }
            if (shown < 1f && xs.size >= 2) {
                val to = xs[xs.size - 2] + shown * (xs.last() - xs[xs.size - 2])
                clipRect(left = -stroke.width, top = -stroke.width, right = to, bottom = size.height + stroke.width) { body() }
            } else body()
            if (!travel.hidden() && wash) drawCircle(color.copy(alpha = color.alpha * shown), dot, Offset(xs.last(), ys.last()))
        }
    })
}

/**
 * The Sparkline's `reveal` for a card whose line gets a new point: 1 at rest, and 0 to 1 over about 500 ms when [count] (the number of weigh-ins)
 * is higher than the last time this [card] was shown, once for each new one. What each card last showed is kept in the model, so a point added
 * on another page is drawn in when the card next appears. Still with animations off.
 */
@Composable
fun rememberNewPoint(model: AppModel, card: String, count: Int): () -> Float {
    val motion = LocalMotion.current
    val grow = remember { Animatable(1f) }
    LaunchedEffect(count) {
        val seen = model.pointsSeen.put(card, count)
        if (seen != null && count > seen && motion) {
            grow.snapTo(0f)
            delay(150)
            grow.animateTo(1f, tween(500, easing = Motion.Standard))
        }
    }
    return { grow.value }
}

/**
 * One column per week, Monday at the top; each day shaded in four steps relative to the busiest day of the last [WEEKS] weeks (so
 * an old big day does not pale the recent weeks; older days above it take the top colour). It runs back to the week of the [first]
 * workout, never fewer than [WEEKS] weeks, in a sideways [scroll] that starts at the present. Three seconds after the last touch
 * (and the fling it started) a chart left in the past glides back to the present, unless animations are off. While it moves a small glass
 * pill above it names the months in view, and fades 600 ms after it stops. Holding a day peeks at its workout in a card by the finger (sliding
 * on to another day switches it); [live] is false for the copy drawn over the pager, which takes no touch. [fresh] (0 to 1) is how far today's
 * square has filled in.
 */
@Composable
private fun Calendar(
    model: AppModel, values: Map<LocalDate, Double>, byDay: Map<String, List<Workout>>, today: LocalDate, first: LocalDate?, palette: List<Color>, scroll: ScrollState,
    wave: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null, live: Boolean = true, fresh: () -> Float = { 1f }, onTap: (LocalDate) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val tick = rememberTick()
    val thisMonday = weekStart(today)
    val weeks = maxOf(WEEKS, first?.let { ChronoUnit.WEEKS.between(weekStart(it), thisMonday).toInt() + 1 } ?: 0)
    val start = thisMonday.minusWeeks(weeks - 1L)
    val max = remember(values, thisMonday) { values.filterKeys { it >= thisMonday.minusWeeks(WEEKS - 1L) }.values.maxOrNull() ?: 0.0 }
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val outline = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val months = remember { LocalePattern("MMM") }
    val density = LocalDensity.current
    // The month names follow the text size chosen in Settings.
    val textScale = LocalTextScale.current
    val platform = LocalPlatform.current
    val paint = remember(labelColor, density, textScale) { platform.labelPaint(labelColor, with(density) { 10.sp.toPx() } * textScale, false) }
    // What the draw below needs per square, made once: the value of each day (week by week, Monday first) and the labels of the months' first columns.
    val cells = remember(values, start, weeks) { DoubleArray(weeks * 7) { values[start.plusDays(it.toLong())] ?: 0.0 } }
    val todayAt = remember(start, today) { ChronoUnit.DAYS.between(start, today).toInt() }
    val labels = remember(start, weeks) { Array(weeks) { w -> start.plusWeeks(w.toLong()).let { m -> if (m.dayOfMonth <= 7) (if (m.monthValue == 1) m.format(SINCE) else m.format(months)) else null } } }
    val activeDays = remember(values, start, today) { values.count { (day, v) -> v > 0 && day >= start && day <= today } }
    // A finger on the chart; the drift back waits until it is lifted.
    var down by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    // [down] is read inside the effect, so a touch on the chart does not recompose it.
    if (live) LaunchedEffect(motion) {
        snapshotFlow { down }.collectLatest { touching ->
            if (touching || !motion) return@collectLatest
            snapshotFlow { scroll.isScrollInProgress }.first { !it }
            if (scroll.value >= scroll.maxValue) return@collectLatest
            delay(5000)
            scroll.animateScrollTo(scroll.maxValue, Motion.driftSlow(Motion.DRIFT_CHART))
        }
    }
    // The pill with the months in view shows while the calendar moves (a drag, a fling or the drift back) and for 600 ms after.
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.collectLatest { now ->
            if (now) moving = true else { delay(600); moving = false }
        }
    }
    val pill = animateFloatAsState(if (moving) 1f else 0f, tween(300), label = "months pill")
    val pillShown by remember { derivedStateOf { pill.value > 0f } }
    // The day held and where the finger is, in the chart's own view (x from its left edge, not from the start of the weeks).
    var peek by remember { mutableStateOf<Pair<LocalDate, Offset>?>(null) }
    // The chart is a blur source for the day card floating over it, only while the card is up (a sibling, so it can be blurred; the page's own source is its ancestor).
    val chartHaze = dev.chrisbanes.haze.rememberHazeState()
    var chartX by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { chartX = it.positionInWindow().x }) {
        // The cells are as big as when the chart held 26 weeks across the card.
        val cell = maxWidth / WEEKS
        val viewport = with(density) { maxWidth.toPx() }
        fun dayAt(at: Offset, c: Float): LocalDate? {
            val week = (at.x / c).toInt()
            val day = kotlin.math.floor(((at.y - c * 1.1f) / c).toDouble()).toInt()
            return if (at.x >= 0 && week in 0 until weeks && day in 0..6) start.plusWeeks(week.toLong()).plusDays(day.toLong()).takeIf { it <= today } else null
        }
        // Looked at before the scroll takes the touch, and left unconsumed.
        Box(
            Modifier.fillMaxWidth().then(if (peek != null) Modifier.hazeSource(chartHaze) else Modifier).then(if (live) Modifier.pointerInput(Unit) {
                awaitPointerEventScope { while (true) down = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed } }
            } else Modifier).horizontalScroll(scroll, flingBehavior = GentleFling),
        ) {
            Canvas(
                Modifier.width(cell * weeks).height(cell * 8.2f).semantics { contentDescription = "Activity chart: ${plural(activeDays, "day")} with a workout since ${prettyDay(start)}" }
                    .then(
                        if (!live) Modifier else Modifier.pointerInput(start, weeks) {
                            // Positions are the canvas's own, so they are right wherever it has been scrolled to.
                            detectTapGestures { at -> dayAt(at, viewport / WEEKS)?.let { tick(); tap(it) } }
                        }.holdAndSlide(
                            key = start to weeks,
                            onHold = { at -> dayAt(at, viewport / WEEKS)?.let { tick(); peek = it to Offset(at.x - scroll.value, at.y) } },
                            onMove = { at ->
                                val day = dayAt(at, viewport / WEEKS)
                                if (day != null && day != peek?.first) tick()
                                peek = if (day != null) day to Offset(at.x - scroll.value, at.y) else peek?.let { it.first to Offset(at.x - scroll.value, at.y) }
                            },
                            onEnd = { peek = null },
                        ),
                    ),
            ) {
                val c = size.width / weeks
                val gap = c * 0.14f
                val top = c * 1.1f
                // Only the columns in view are drawn, and a few before them for a label that starts off the edge.
                val seen = scroll.value
                val from = ((seen / c).toInt() - 3).coerceAtLeast(0)
                val to = (((seen + viewport) / c).toInt() + 1).coerceAtMost(weeks - 1)
                for (week in from..to) {
                    // Label the column in which a month begins; January carries the year.
                    labels[week]?.let { paint.draw(drawContext.canvas, it, week * c, c * 0.7f) }
                    for (day in 0 until 7) {
                        val at = week * 7 + day
                        if (at > todayAt) continue
                        val value = cells[at]
                        // The first time the card is seen, the squares come in from the top left of what is in view (Roll.kt).
                        val came = if (wave == null) 1f else Motion.waveCell(wave.value, (week - (seen / c).toInt()).coerceAtLeast(0) + day)
                        // Today's square, once, after a workout was finished: it grows from 60 % and its colour eases in from the empty square's.
                        val filled = if (at == todayAt && value > 0) fresh() else 1f
                        val target = if (value <= 0 || max <= 0) empty else palette[(ceil(value / max * 4).toInt() - 1).coerceIn(0, 3)]
                        val color = (if (filled < 1f) lerp(empty, target, filled) else target).let { it.copy(alpha = it.alpha * came) }
                        val full = Size(c - gap, c - gap)
                        val shrink = if (came < 1f) 0.6f + 0.4f * came else 0.6f + 0.4f * filled
                        val box = if (shrink >= 1f) full else Size(full.width * shrink, full.height * shrink)
                        val origin = Offset(week * c + gap / 2 + (full.width - box.width) / 2, top + day * c + gap / 2 + (full.height - box.height) / 2)
                        drawRoundRect(color, origin, box, CornerRadius(c * 0.2f))
                        if (at == todayAt) drawRoundRect(outline.copy(alpha = outline.alpha * came), origin, box, CornerRadius(c * 0.2f), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        // The months in view, above the chart.
        // Composed only while the pill shows at all: its fade runs in the layer, not in composition.
        if (pillShown) {
            val c = viewport / WEEKS
            val label by remember(scroll, start, weeks, viewport) {
                derivedStateOf {
                    val a = start.plusWeeks(((scroll.value / c).toInt()).coerceIn(0, weeks - 1).toLong())
                    val b = minOf(today, start.plusWeeks((((scroll.value + viewport) / c).toInt()).coerceIn(0, weeks - 1).toLong()).plusDays(6))
                    monthsInView(a, b, today.year)
                }
            }
            Text(
                label, Modifier.align(Alignment.TopEnd).offset(y = (-36).dp).graphicsLayer { alpha = pill.value }.glass(PillShape, haze = null, lift = 0.dp).padding(horizontal = Space.m, vertical = Space.xs),
                style = MaterialTheme.typography.labelMedium, maxLines = 1,
            )
        }
        // The held day's workout, above the finger.
        peek?.let { (day, at) ->
            val done = byDay[day.toString()].orEmpty()
            if (done.isNotEmpty()) DayPeek(model, day, done, Modifier.placeAbove(at, chartX), chartHaze)
        }
    }
}

/**
 * Adds the line through the points as one smooth curve: a monotone cubic ([monotoneSlopes]), so it passes through every point and
 * never rises above or falls below the two it runs between. Every chart draws its line with this. Two points are straight and a
 * repeated x (two points on one day) is a step.
 */
fun Path.curveThrough(xs: FloatArray, ys: FloatArray) {
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
    /** The page's own range control for this chart: a double tap sets it to its 3 months, and again back to what it was; none on a chart without one. */
    selector: RangeSelector? = null,
    /** Round 23: the last point is a period still running (this week so far): drawn dashed with a hollow dot, left out of the trend and of the latest value. */
    partialLast: Boolean = false,
    /** What the trend's figure counts, in words, when [unit] does not say ("workouts a week"); the trend reads "per month" after it. */
    trendUnit: String? = null,
    onTap: ((LocalDate) -> Unit)? = null,
) {
    val plain = remember(points) { points.map { ChartPoint(it.first, it.second) } }
    SplitLineChart(plain, unit, range, bands, title, height, trend, goal, onTap = onTap, morph = morph, selector = selector, partialLast = partialLast, trendUnit = trendUnit)
}

/**
 * A line over time. The dotted line marks the latest value and is labelled on the y axis, next to
 * the highest and lowest values shown. [bands] are value ranges shaded behind the line, and [range]
 * widens the y axis to take them in. [goal] is a level to reach, drawn as a thin line in the accent
 * and taken into the axis. A tap is passed to [onTap] as the day of the nearest point.
 * A point with both sides ([ChartPoint.split]) is two markers joined by a faint stem; the left ones are on a solid line
 * and the right ones on a dashed line, the two running together through plain points. With one in view a key says which
 * line is which, and the plain points then get a small marker too.
 * [imbalance] is right-minus-left in percent: a zero line is the axis, and the wording is about which side leads.
 * A double tap switches between all of [allPoints] and their last three months (the line and the axes stretch between them, [rememberStretch]),
 * on a chart that runs back further than that; "Last 3 months" says so under the chart, and the choice is kept for this chart for the session.
 */
@Composable
fun SplitLineChart(
    allPoints: List<ChartPoint>,
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
    /** The page's own range control for this chart: a double tap sets it to its 3 months, and again back to what it was; none on a chart without one. */
    selector: RangeSelector? = null,
    onTap: ((LocalDate) -> Unit)? = null,
    /** The name of the workout a point comes from, shown with its date while a point is held (an exercise's chart). */
    names: ((LocalDate) -> String?)? = null,
    /** Round 23: see [LineChart]. */
    partialLast: Boolean = false,
    trendUnit: String? = null,
) {
    // Round 15 (7): all the points, or only the last three months of them.
    val recentFrom = allPoints.last().date.minusMonths(3)
    val recentPoints = remember(allPoints) { allPoints.filter { it.date >= recentFrom } }
    // On a page with its own range control ([selector]) the double tap drives that control, so the two always agree; only a chart without one switches by itself.
    val canRecent = selector == null && recentPoints.size >= 2 && allPoints.first().date < recentFrom
    var recent by rememberSaveable { mutableStateOf(false) }
    val showRecent = recent && canRecent
    val canSwitch = canRecent || selector != null
    val isRecent = if (selector != null) selector.days == selector.recentDays else showRecent
    // The option that was chosen before the double tap, to go back to.
    var before by rememberSaveable { mutableStateOf(0L) }
    fun switchRange() {
        if (selector == null) recent = !showRecent
        else if (selector.days != selector.recentDays) { before = selector.days; selector.set(selector.recentDays) }
        else selector.set(before)
    }
    // A lambda, not a function reference: two references to the same local function are equal, so the old one (and its old selector) would be kept.
    val switchNow by rememberUpdatedState({ switchRange() })
    val points = if (showRecent) recentPoints else allPoints
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val latestColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val goalColor = goalColor()
    val pageColor = MaterialTheme.colorScheme.background
    // The tint of a band: stronger on a dark page.
    val fillAlpha = if (LocalLook.current.dark) 0.22f else 0.07f
    // The axis takes in both ends of every stem, and the zero of an imbalance chart.
    val dataLow = minOf(points.minOf { it.low }, range?.start ?: Double.MAX_VALUE, goal ?: Double.MAX_VALUE, if (imbalance) 0.0 else Double.MAX_VALUE)
    val high = maxOf(points.maxOf { it.high }, range?.endInclusive ?: -Double.MAX_VALUE, goal ?: -Double.MAX_VALUE, if (imbalance) 0.0 else -Double.MAX_VALUE)
    val low = dataLow
    // A period still running is not "the latest value": the dotted line and the axis label stay on the last whole one.
    val partial = partialLast && points.size >= 2
    val latest = (if (partial) points[points.size - 2] else points.last()).value
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
    val fit = remember(points, trend, partial) { if (trend) trendOf(points, partial) else null }
    val trendColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The point last tapped and where it sits, for the small card that names it.
    var picked by remember(points) { mutableStateOf<Pair<Int, Offset>?>(null) }
    // A point held (a long press, then sliding along the chart): highlighted, with its card above it until the finger is lifted.
    var scrub by remember(points) { mutableStateOf<Int?>(null) }
    var chartWidth by remember { mutableFloatStateOf(0f) }
    val scrubTick = rememberTick()
    val namesOf by rememberUpdatedState(names)
    val cardAt = { index: Int, size: Size, density: Density ->
        val span = (high - low).takeIf { it > 0 } ?: 1.0
        val left = with(density) { 46.dp.toPx() }
        val x = left + ChronoUnit.DAYS.between(first, points[index].date).toFloat() / days * (size.width - left)
        val y = with(density) { 10.dp.toPx() } + ((high - points[index].high) / span).toFloat() * (size.height - with(density) { 20.dp.toPx() })
        // The card goes a little above and to the left of the point, kept on the chart.
        Offset((x - with(density) { 70.dp.toPx() }).coerceAtLeast(0f), (y - with(density) { 78.dp.toPx() }).coerceAtLeast(0f))
    }
    Box {
        picked?.let { (index, at) ->
            val point = points[index]
            val day = point.date
            Popup(offset = IntOffset(at.x.toInt(), at.y.toInt()), onDismissRequest = { picked = null }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = SmallShape, color = Color.Transparent,
                    modifier = Modifier.glass(SmallShape, haze = null, lift = 8.dp).clickable { picked = null; tap?.invoke(day) },
                ) { PointTip(point, unit, imbalance, null, onTap != null) }
            }
        }
        scrub?.let { index ->
            val density = LocalDensity.current
            val at = cardAt(index, Size(chartWidth, with(density) { height.toPx() }), density)
            val point = points[index]
            Popup(offset = IntOffset(at.x.toInt(), at.y.toInt()), properties = PopupProperties(focusable = false)) {
                Surface(Modifier.glass(SmallShape, haze = null, lift = 8.dp), shape = SmallShape, color = Color.Transparent) {
                    PointTip(point, unit, imbalance, namesOf?.invoke(point.date), false)
                }
            }
        }
    }
    val drawn by drawnIn()
    val platform = LocalPlatform.current
    val textScale = LocalTextScale.current
    val density = LocalDensity.current
    // The plain line (no bars, sides or imbalance) is the one that can travel from a card: its points as drawn below.
    val travel = rememberChartMorph(
        if (hasSplit || imbalance || partial) null else morph, line, with(density) { 2.5f.dp.toPx() },
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
    // The nearest session by day to a place across the chart, at any height, so the whole stem is a target.
    fun nearest(x: Float): Int {
        val left = with(density) { 46.dp.toPx() }
        val day = first.plusDays(roundHalfUp((x - left) / (chartWidth - left) * days).toLong())
        return points.indices.minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(points[it].date, day)) } ?: 0
    }
    fun scrubTo(x: Float) {
        val index = nearest(x)
        if (index != scrub) { scrub = index; scrubTick() }
    }
    Spacer(Modifier.fillMaxWidth().height(height).then(travel.modifier).onSizeChanged { chartWidth = it.width.toFloat() }.semantics {
        contentDescription = summary; role = Role.Image
        if (canSwitch) customActions = listOf(CustomAccessibilityAction(if (isRecent) "Show the whole range" else "Show the last 3 months") { switchRange(); true })
    }.pointerInput(points, canSwitch) {
        // A double tap would delay every single tap, so it is listened for only on a chart that has a range to switch to.
        detectTapGestures(onDoubleTap = if (canSwitch) { _ -> switchNow(); scrubTick() } else null) { at ->
            val left = 46.dp.toPx()
            val day = first.plusDays(roundHalfUp((at.x - left) / (size.width - left) * days).toLong())
            val index = points.indices.minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(points[it].date, day)) } ?: return@detectTapGestures
            picked = index to cardAt(index, Size(size.width.toFloat(), size.height.toFloat()), this)
        }
    }.holdAndSlide(
        key = points,
        onHold = { at -> scrubTo(at.x) },
        onMove = { at -> scrubTo(at.x) },
        onEnd = { scrub = null },
    ).drawWithCache {
        // Worked out once for this size: the scale, the label paints and the line itself.
        val left = 46.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val span = (frame.ys[1] - frame.ys[0]).takeIf { it > 0 } ?: 1.0
        fun y(value: Double) = top + ((frame.ys[1] - value) / span).toFloat() * (bottom - top)
        val text = platform.labelPaint(labelColor, 11.sp.toPx() * textScale, false)
        val bold = platform.labelPaint(latestColor.toArgb(), text.textSize, true)
        val goalPaint = platform.labelPaint(goalColor.toArgb(), text.textSize, true)
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        val latestY = y(latest)
        val near = 13.sp.toPx() * textScale
        val path = Path()
        fun x(p: ChartPoint) = left + ((p.date.toEpochDay() - frame.x0) / frame.xSpan).toFloat() * (size.width - left)
        // A period still running is not part of the solid line: it is a dashed last segment ending in a hollow dot (not while the range moves).
        val solid = if (partial && !moving && drawPoints.size >= 3) drawPoints.dropLast(1) else drawPoints
        path.curveThrough(FloatArray(solid.size) { x(solid[it]) }, FloatArray(solid.size) { y(solid[it].value) })
        val soFar = solid.size < drawPoints.size
        val marks = if (hasSplit) SplitMarks(drawPoints, this, ::x, ::y) else null
        val zeroY = y(0.0)
        val stroke = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            for ((band, color) in bands) {
                val from = y(minOf(band.endInclusive, frame.ys[1]))
                val to = y(maxOf(band.start, frame.ys[0]))
                if (to > from) drawRect(color.copy(alpha = fillAlpha), Offset(left, from), Size(size.width - left, to - from))
            }
            fun label(value: Double, paint: LabelPaint) = paint.draw(drawContext.canvas, shortNumber(value), 0f, y(value) + text.textSize / 3)
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
                if (zeroY - top > near && bottom - zeroY > near && kotlin.math.abs(zeroY - latestY) > near) label(0.0, text)
            }
            if (goal != null) {
                drawLine(goalColor, Offset(left, y(goal)), Offset(size.width, y(goal)), 1.5.dp.toPx(), pathEffect = dashes)
                if (kotlin.math.abs(y(goal) - latestY) > near) label(goal, goalPaint)
            }
            if (!moving) fit?.let { (slope, start) ->
                clipRect(left = left, top = top, bottom = bottom) { drawLine(trendColor, Offset(left, y(start)), Offset(size.width, y(start + slope * days)), 1.5.dp.toPx()) }
            }
            // The point held, marked on the line.
            scrub?.let { index ->
                val p = points[index]
                val at = Offset(x(p), y(p.value))
                drawLine(grid, Offset(at.x, top), Offset(at.x, bottom), 1.dp.toPx())
                drawCircle(line.copy(alpha = 0.25f), 9.dp.toPx(), at)
                drawCircle(line, 4.5.dp.toPx(), at)
            }
            // While the range changes the line is clipped to the plot, so the part outside the new range slides off the left edge and the older part comes in from it.
            clipRect(left = if (moving) left else 0f, right = left + (size.width - left) * drawn + (if (drawn >= 1f) 6.dp.toPx() else 0f)) {
                if (marks != null) drawSplitMarks(marks, line)
                else {
                    // While a card opens into this page, the line travelling from the card's chart stands in for this one.
                    if (!travel.hidden()) drawPath(path, line, style = stroke)
                    if (soFar) {
                        val a = solid.last()
                        val b = drawPoints.last()
                        drawLine(line, Offset(x(a), y(a.value)), Offset(x(b), y(b.value)), 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
                        drawCircle(pageColor, 4.5.dp.toPx(), Offset(x(b), y(b.value)))
                        drawCircle(line, 4.5.dp.toPx(), Offset(x(b), y(b.value)), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    })
    // The dates under the chart cross over when the range changes.
    Crossfade(first, Modifier.fillMaxWidth(), animationSpec = if (LocalMotion.current) Motion.stretch() else snap(), label = "range dates") { shownFirst ->
        Row(Modifier.fillMaxWidth().padding(start = 46.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(prettyDay(shownFirst), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRecent) Text("Last 3 months", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            val now = if (lastPoint.split) "L ${shortNumber(lastPoint.left!!)} · R ${shortNumber(lastPoint.right!!)}" else shortNumber(if (partial) lastPoint.value else latest)
            Text(if (partial) "$now $unit so far, week of ${prettyDay(lastPoint.date)}".replace("  ", " ") else "$now $unit · ${prettyDay(lastPoint.date)}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (hasSplit) SidesKey(line, Modifier.padding(start = 46.dp))
    if (imbalance) Text("Above 0: right leads. Below 0: left leads.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // The goal is named by its teal level on the axis (and by the page that set it); the trend and the way to the full screen share one quiet line.
    FullScreenLink(FullChart(title, listOf(ChartSeries(title, unit, null, allPoints, imbalance, names)))) {
        Column {
            fit?.let { (slope, _) -> Text(trendLine(slope, unit, trendUnit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (partial) Text("Dashed: this week so far, left out of the trend.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The straight line through [points] (a rise per day and the value on the first day), as [linearFit] makes it; with [partialLast] the last point, a period still
 * running, is left out so it cannot pull the line down. Null with fewer than three whole points.
 */
fun trendOf(points: List<ChartPoint>, partialLast: Boolean = false): Pair<Double, Double>? {
    if (points.isEmpty()) return null
    val first = points.first().date
    val whole = if (partialLast && points.size >= 2) points.dropLast(1) else points
    return linearFit(whole.map { ChronoUnit.DAYS.between(first, it.date).toDouble() to it.value })
}

/**
 * What the trend says under a chart: "Trend: +1.5 kg a month", "Trend: −0.03 workouts a week, per month" with a [unitWords] that names what is counted, "Trend: level".
 * [slope] is the rise per day. The minus is the app's own (U+2212) and nothing is left double-spaced when the unit is empty.
 */
fun trendLine(slope: Double, unit: String, unitWords: String? = null): String {
    val month = roundHalfUp(slope * 30 * 100) / 100.0
    if (month == 0.0) return "Trend: level"
    val amount = (if (month > 0) "+" else "−") + shortNumber(kotlin.math.abs(month))
    val per = when {
        unitWords != null -> "$amount $unitWords, per month"
        unit.isEmpty() -> "$amount a month"
        else -> "$amount $unit a month"
    }
    return "Trend: $per"
}

/** What a chart's small card says about one point: its figure, the workout's name when it has one, the day, and "Open" when a tap opens the day. */
@Composable
private fun PointTip(point: ChartPoint, unit: String, imbalance: Boolean, name: String?, open: Boolean) {
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
        Text(
            when {
                imbalance -> leadWords(point.value)
                point.split -> sidesLine("${roundedNumber(point.left!!)} $unit".trim(), "${roundedNumber(point.right!!)} $unit".trim())!!
                else -> "${roundedNumber(point.value)} $unit".trim()
            },
            fontWeight = FontWeight.Bold,
        )
        if (name != null) Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(prettyDay(point.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) Text("Open ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** One line of a chart. A null [color] is the app's accent; [imbalance] marks right-minus-left percentages. */
class ChartSeries(val label: String, val unit: String, val color: Color?, val points: List<ChartPoint>, val imbalance: Boolean = false, val names: ((LocalDate) -> String?)? = null)

/** `right leads by 8 %`, `left leads by 3 %`, `even`, for an imbalance chart's value. */
private fun leadWords(percent: Double): String {
    val whole = roundHalfUp(kotlin.math.abs(percent)).toInt()
    return if (whole == 0) "even" else "${if (percent > 0) "right" else "left"} leads by $whole %"
}

private fun roundedNumber(value: Double) = shortNumber(roundHalfUp(value * 100) / 100.0)

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
            kotlin.math.abs(l - marks.rightY[i]) < 1f -> {
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
private fun FullScreenLink(chart: FullChart, lead: @Composable () -> Unit = {}) {
    val open = LocalFullChart.current
    // [lead] (a chart's trend, say) at the left, the way to the full screen at the right, both quiet grey.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { lead() }
        if (open != null) Text(
            "Full screen", Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { open(chart) }.minTouch().wrapContentHeight(Alignment.CenterVertically).padding(start = Space.s),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The chart lines' colours under [look] (round 20): the accent, then the theme's own family, one list per theme and per Dark or Light (`SERIES`, Look.kt). */
fun seriesColorsOf(look: Look): List<Color> = look.series

/** [seriesColorsOf] the current look. */
@Composable
fun seriesColors(): List<Color> = seriesColorsOf(LocalLook.current)

/** The point of [points] nearest in days to [day]. */
private fun nearestPoint(points: List<ChartPoint>, day: LocalDate): ChartPoint = points.minBy { kotlin.math.abs(ChronoUnit.DAYS.between(it.date, day)) }

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
    // Round 19: holding the chart and sliding along it scrubs the dates; one card gives the date and each line's value at the nearest date of that line.
    var scrub by remember(series) { mutableStateOf<Pair<LocalDate, Offset>?>(null) }
    var chartWidth by remember { mutableFloatStateOf(0f) }
    val scrubTick = rememberTick()
    fun scrubTo(at: Offset) {
        val day = first.plusDays(roundHalfUp(at.x / chartWidth.coerceAtLeast(1f) * days).toLong().coerceIn(0L, days))
        val near = series.flatMap { it.points }.minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(it.date, day)) }?.date ?: day
        if (near != scrub?.first) scrubTick()
        scrub = near to at
    }
    Box {
        scrub?.let { (day, at) ->
            PeekPopup(at, "chart peek") {
                PeekHead(prettyDay(day))
                for (line in series) {
                    val p = nearestPoint(line.points, day)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Box(Modifier.size(8.dp).background(line.color ?: accent, CircleShape))
                        Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${latestShown(p)} ${line.unit}".trim(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
    Spacer(Modifier.fillMaxWidth().height(height).then(travel.modifier).onSizeChanged { chartWidth = it.width.toFloat() }.semantics { contentDescription = "${title.ifEmpty { "Chart" }}:${series.joinToString(", ") { "${it.label}, latest ${latestSpoken(it.points.last())} ${it.unit}".trim() + (if (it.points.any { p -> p.split }) ", ${it.points.count { p -> p.split }} sessions left and right, left solid line, right dashed line" else "") }}"; role = Role.Image }.holdAndSlide(
        key = series, onHold = { scrubTo(it) }, onMove = { scrubTo(it) }, onEnd = { scrub = null },
    ).drawWithCache {
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
            // The date held: a line across the chart and a dot on each line at its nearest session.
            scrub?.let { (day, _) ->
                val at = ((day.toEpochDay() - frame.x0) / frame.xSpan).toFloat() * size.width
                drawLine(grid, Offset(at, top), Offset(at, bottom), 1.dp.toPx())
                for ((i, line) in series.withIndex()) {
                    val p = nearestPoint(line.points, day)
                    val low = frame.ys[2 * i]
                    val span = (frame.ys[2 * i + 1] - low).takeIf { it > 0 } ?: 1.0
                    val x = ((p.date.toEpochDay() - frame.x0) / frame.xSpan).toFloat() * size.width
                    drawCircle(line.color ?: accent, 4.5.dp.toPx(), Offset(x, bottom - ((p.value - low) / span).toFloat() * (bottom - top)))
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
            Box(Modifier.size(12.dp).background(line.color ?: accent, clean.app.ui.theme.LegendShape))
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
    val platform = LocalPlatform.current
    DisposableEffect(Unit) {
        platform.landscape(true)
        onDispose { platform.landscape(false) }
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
                    if (chart.series.size == 1) chart.series[0].let { SplitLineChart(it.points, it.unit, imbalance = it.imbalance, names = it.names, height = (tall - 40.dp).coerceAtLeast(160.dp)) }
                    else MultiLineChart(chart.series, height = (tall - 24.dp).coerceAtLeast(160.dp), shared = chart.shared)
                }
            }
        }
    }
}

/**
 * Where a floating card may sit across: from the page's side margin to the other one, in this parent's own coordinates. [parentX] is how far the
 * parent's left edge is from the window's (the card is kept inside the window, not just inside its parent, which may be narrower or wider).
 */
private fun Density.sideRange(parentX: Float, window: Int, width: Int, parentWidth: Int): ClosedFloatingPointRange<Float> {
    val margin = Space.l.toPx()
    val low = margin - parentX
    val high = (window - margin - parentX - width).coerceAtMost(parentWidth - width.toFloat())
    return low..maxOf(low, high)
}

/** Places this under [rect] (in the parent's own coordinates), centred on it and kept inside the window's side margins; above it when there is no room below. */
@Composable
fun Modifier.placeBy(rect: androidx.compose.ui.geometry.Rect, parentX: Float = 0f): Modifier {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    return layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            val range = sideRange(parentX, window, p.width, constraints.maxWidth)
            val x = (rect.center.x - p.width / 2f).coerceIn(range.start, range.endInclusive)
            val gap = 4.dp.toPx()
            val below = rect.bottom + gap
            val y = if (below + p.height <= constraints.maxHeight) below else (rect.top - gap - p.height).coerceAtLeast(0f)
            p.place(x.roundToInt(), y.roundToInt())
        }
    }
}

/** Places this above a finger at [at] (in the parent's own coordinates), centred on it and kept inside the window's side margins; below the finger when there is no room above it. */
@Composable
fun Modifier.placeAbove(at: Offset, parentX: Float = 0f): Modifier {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    return layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            val above = at.y - p.height - 28.dp.toPx()
            val y = if (above >= 0f || constraints.maxHeight == Constraints.Infinity) above else minOf(at.y + 36.dp.toPx(), (constraints.maxHeight - p.height).coerceAtLeast(0).toFloat())
            val range = sideRange(parentX, window, p.width, constraints.maxWidth)
            p.place((at.x - p.width / 2f).coerceIn(range.start, range.endInclusive).roundToInt(), y.roundToInt())
        }
    }
}

/**
 * A page's own range control (3M / 1Y / All and the like) seen from the chart it drives: its [days] now (0 is all), the option that is its 3 months
 * ([recentDays]) and how to choose an option. A double tap on the chart sets it to the 3 months and, again, back to the option it had.
 */
class RangeSelector(val days: Long, val set: (Long) -> Unit, val recentDays: Long = 90L)
