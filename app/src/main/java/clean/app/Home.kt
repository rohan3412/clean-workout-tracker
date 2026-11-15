package clean.app

import clean.app.ui.theme.orange
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.layout.onSizeChanged
import clean.app.ui.theme.PillShape
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import clean.app.ui.theme.SmallShape
import clean.core.FATIGUE_RECOVERING
import clean.core.Muscle
import clean.core.Workout
import clean.core.recordCount
import clean.core.volumeOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

internal const val HOME_CHARTS = "home.charts"
internal const val HOME_LAYOUT = "home.layout"

/** The cards Home shows, in order, with their widths: the stored list (see [parseHome]), else the default. */
@Composable
internal fun AppModel.homeEntries(): List<HomeEntry> {
    val all by options.collectAsState()
    val id by activeId.collectAsState()
    val stored = all["home.cards:$id"]
    return remember(stored) { parseHome(stored) }
}

/** The same, read now; for a screen that keeps its own copy while it is open. */
private fun AppModel.homeNow(): List<HomeEntry> = parseHome(options.value["home.cards:${activeId.value}"])

/** Stores the cards Home shows, or goes back to the default with null. */
private fun AppModel.setHome(entries: List<HomeEntry>?) {
    val id = activeId.value ?: return
    setOption("home.cards:$id", entries?.let(::encodeHome))
}

/** The two choices about how Home is drawn: charts to the card edges or inset, and two staggered columns or even rows. Used by Arrange Home and Settings. */
@Composable
fun HomeOptions(model: AppModel) {
    val o by model.options.collectAsState()
    Text("Charts in cards", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Choice(listOf("edge", "inset"), if (o[HOME_CHARTS] == "inset") "inset" else "edge", { if (it == "edge") "Edge to edge" else "Inset" }) { model.setOption(HOME_CHARTS, it) }
    Text("Home layout", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Choice(listOf("stagger", "even"), if (o[HOME_LAYOUT] == "even") "even" else "stagger", { if (it == "stagger") "Staggered" else "Even rows" }) { model.setOption(HOME_LAYOUT, it) }
}

@Composable
fun HomeTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val templates by model.templates.collectAsState()
    val options by model.options.collectAsState()
    val entries = model.homeEntries()
    val segments = remember(entries) { homeSegments(entries) }
    val even = options[HOME_LAYOUT] == "even"
    val haptic = LocalHapticFeedback.current
    val state = rememberLazyListState()
    var listTop by remember { mutableFloatStateOf(0f) }
    fun arrange(held: String?) { model.arrangeAnchor = arrangeAnchorOf(state, listTop, held); model.open(Screen.EditHome) }
    BarEnd(Tab.Home, state)
    val pinned = pinsTitle()
    CompositionLocalProvider(LocalEdgeCharts provides (options[HOME_CHARTS] != "inset")) {
        SheetTab({ HomeHeader(model) }) {
        LazyColumn(Modifier.fillMaxSize().onGloballyPositioned { listTop = it.positionInRoot().y }, state) {
            if (!pinned) item { HomeHeader(model) }
            sheetGap(pinned)
            if (workouts.isEmpty()) item {
                // Nothing to show yet: the two ways to start, in place of cards full of zeros.
                Section("") {
                    EmptyPreview("Log your first workout, or bring your history in from another app, and your progress fills in here.") { PreviewSamples.Line(120.dp) }
                    MainButton("Start a workout", { model.startWorkout() }, Modifier.fillMaxWidth())
                    QuietButton("Import your history", { model.settingsPage = "Data"; model.importNow = true; model.open(Screen.Settings) }, Modifier.fillMaxWidth())
                }
            } else {
                // Holding a card opens Arrange Home.
                val row: @Composable (List<HomeEntry>) -> Unit = { segment ->
                    Box(Modifier.onHold { haptic.performHapticFeedback(HapticFeedbackType.LongPress); arrange(segment.first().card.key) }) {
                        if (segment.first().wide) HomeCardContent(model, segment.first(), templates, workouts)
                        else HalfCards(even) { for (e in segment) Box { HomeCardContent(model, e, templates, workouts) } }
                    }
                }
                segments.firstOrNull()?.let { first -> item(key = first.first().card.key) { row(first) } }
                items(segments.drop(1), key = { it.first().card.key }) { segment -> row(segment) }
                item { QuietButton("Arrange home", { arrange(null) }, Modifier.padding(horizontal = Space.l, vertical = Space.s)) }
            }
            item { BarSpace() }
        }
        }
    }
}

/**
 * Where Arrange should open: the held card ([held], a segment's first card key), else the first card whose top is on screen (else the first card shown at
 * all), with its top's place on screen, so the arrange page can put the same card in the same place instead of jumping to the top.
 */
internal fun arrangeAnchorOf(state: LazyListState, listTop: Float, held: String?): Pair<String, Float>? {
    val cards = state.layoutInfo.visibleItemsInfo.filter { it.key is String }
    val item = cards.firstOrNull { it.key == held } ?: cards.firstOrNull { it.offset >= 0 } ?: cards.firstOrNull() ?: return null
    return item.key as String to listTop + item.offset
}


/** What a card shows, at the width it has been given. */
@Composable
private fun HomeCardContent(model: AppModel, entry: HomeEntry, templates: List<Template>, workouts: List<Workout>) {
    when (entry.card) {
        HomeCard.Today -> TodayCard(model, templates, workouts)
        HomeCard.Week -> ThisWeekCard(model, half = !entry.wide)
        HomeCard.Last -> workouts.lastOrNull()?.let { WorkoutCardOf(model, it) }
        HomeCard.Recovery -> RecoveryCard(model)
        HomeCard.WeightChart -> BodyWeightCard(model)
        HomeCard.OneRm1 -> OneRmTile(model, 0)
        HomeCard.OneRm2 -> OneRmTile(model, 1)
        HomeCard.Streak -> StreakTile(model)
        HomeCard.Weight -> WeightTile(model)
        HomeCard.Volume -> VolumeTile(model)
        HomeCard.Workouts -> WorkoutsTile(model)
        HomeCard.Month -> MonthTile(model)
        HomeCard.Prs -> PrsTile(model)
        HomeCard.MonthVolume -> MonthVolumeTile(model)
        HomeCard.LongestStreak -> LongestStreakTile(model)
        HomeCard.WeekFigure -> WeekFigureCard(model, half = !entry.wide)
        HomeCard.TopMuscles -> TopMusclesCard(model, half = !entry.wide)
        HomeCard.RecentPrs -> RecentPrsCard(model, half = !entry.wide)
        HomeCard.RecentWorkouts -> RecentWorkoutsCard(model)
        HomeCard.ChosenLift -> ChosenLiftTile(model)
        HomeCard.WeeklyBars -> WeeklyBarsCard(model)
        HomeCard.NextWorkout -> NextWorkoutCard(model)
        HomeCard.AvgDuration -> AverageDurationTile(model)
        HomeCard.FatigueGlance -> FatigueGlanceCard(model, half = !entry.wide)
        HomeCard.Goals -> GoalsCard(model, half = !entry.wide)
        HomeCard.YearAgo -> YearAgoCard(model, half = !entry.wide)
    }
}

/** The gap between half cards, across and down. */
internal val HALF_GAP = 12.dp

/**
 * A run of half-width cards, one child each. Staggered, each goes into whichever of the two columns is shorter so far ([placeColumns]),
 * so cards of different heights fit together; even, they pair up left to right and a pair is as tall as its taller card. A card that
 * shows nothing (a 1RM with no lift to show) takes no place.
 */
@Composable
internal fun HalfCards(even: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHalfCard provides true) {
        Layout(content, Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) { measurables, constraints ->
            val gap = HALF_GAP.roundToPx()
            val width = (constraints.maxWidth - gap) / 2
            val x = { col: Int -> col * (width + gap) }
            if (!even) {
                val column = Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)
                val placeables = measurables.map { it.measure(column) }
                val cols = placeColumns(placeables.map { it.height }, gap)
                val used = intArrayOf(0, 0)
                val spots = placeables.mapIndexed { i, p -> if (cols[i] < 0) null else Triple(p, x(cols[i]), used[cols[i]]).also { used[cols[i]] += p.height + gap } }
                layout(constraints.maxWidth, (maxOf(used[0], used[1]) - gap).coerceAtLeast(0)) { for (spot in spots) spot?.let { (p, px, py) -> p.place(px, py) } }
            } else {
                // A pair is as tall as the taller card. The heights are asked first (intrinsics), as a child can be measured only once.
                val heights = measurables.map { it.minIntrinsicHeight(width) }
                val rows = measurables.indices.filter { heights[it] > 0 }.chunked(2)
                var top = 0
                val placed = mutableListOf<Triple<Placeable, Int, Int>>()
                for (pair in rows) {
                    val h = pair.maxOf { heights[it] }
                    pair.forEachIndexed { k, i -> placed += Triple(measurables[i].measure(Constraints.fixed(width, h)), x(k), top) }
                    top += h + gap
                }
                layout(constraints.maxWidth, (top - gap).coerceAtLeast(0)) { for ((p, px, py) in placed) p.place(px, py) }
            }
        }
    }
}

/** Calls [onHold] when a finger stays down on this (or on anything in it) without moving; the press is then used up, so the card under it does not open on release. */
internal fun Modifier.onHold(onHold: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // The wait times out (null) only when the finger has stayed; lifting or moving past the slop ends it with false.
        val stayed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var still = true
            while (still) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                still = change != null && change.pressed && (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
            }
            false
        } == null
        if (stayed) {
            onHold()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

private val DAY_TITLE_SKELETON = "EEEEdMMMM"

/** "Today" over the date in the phone's own order ("Monday, 12 October"), and the week streak as a chip that opens Trophies. */
@Composable
internal fun HomeHeader(model: AppModel) {
    val consistency by model.consistency.collectAsState()
    val date = remember { LocalDate.now() }
    val title = remember(date) {
        val locale = Locale.getDefault()
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_TITLE_SKELETON), locale))
    }
    val streak = consistency?.streak
    val weeks = streak?.length ?: 0
    val shield = streak?.shield == true
    Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.Bottom) {
        val look = clean.app.ui.theme.LocalLook.current
        // Linen: no greeting, the day name over the date in the faint grey.
        if (look.twoToneTitles) Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            val locale = Locale.getDefault()
            Text(date.format(DateTimeFormatter.ofPattern("EEEE", locale)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(date.format(DateTimeFormatter.ofPattern("d MMMM", locale)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = look.faint)
        } else
        Column(Modifier.weight(1f)) {
            Text("Today", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.padding(start = Space.m).clip(SmallShape).background(MaterialTheme.colorScheme.surfaceContainer).pressable { model.open(Screen.Trophies) }
                .semantics(mergeDescendants = true) { contentDescription = "Week streak ${plural(weeks, "week")}${if (shield) ", shield held" else ""}. Open trophies" }
                .minTouch().padding(horizontal = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CleanIcons.Star, null, Modifier.size(18.dp), tint = clean.app.ui.theme.LocalLook.current.orange)
            Text(weeks.toString(), fontWeight = FontWeight.Bold)
            if (shield) Icon(CleanIcons.Shield, null, Modifier.size(16.dp), tint = clean.app.ui.theme.LocalLook.current.orange)
        }
    }
}

/** A workout as its History card, with its volume and PRs worked out; it opens the workout. */
@Composable
internal fun WorkoutCardOf(model: AppModel, workout: Workout) {
    val prs by model.prs.collectAsState()
    // settings and bodyweight are keys only so the volume is worked out again when they change.
    val settings by model.settings.collectAsState()
    val bodyweight by model.bodyweights.collectAsState()
    val volume = remember(workout, settings, bodyweight) { volumeOf(workout, model::loadOf) }
    WorkoutCard(workout, model, volume, recordCount(workout, prs[workout.date].orEmpty()))
}

/** `Chest, triceps and 2 more`: at most [max] muscles named. */
internal fun musclesText(list: List<Muscle>, max: Int = 4): String {
    val names = list.take(max).mapIndexed { i, m -> if (i == 0) m.label else m.label.lowercase() }
    val more = list.size - names.size
    return when {
        more > 0 -> names.joinToString(", ") + " and $more more"
        names.size < 2 -> names.joinToString()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

/** Which muscles are still recovering and which are ready, from the Body page's fatigue figures; it opens that page on Fatigue. */
@Composable
private fun RecoveryCard(model: AppModel) {
    val figures by model.muscleFigures.collectAsState()
    // Fatigue depends on the time now, which nothing stored changes.
    LaunchedEffect(Unit) { model.refreshMuscles() }
    val fatigue = figures?.fatigue ?: return
    val tired = Muscle.entries.filter { (fatigue[it] ?: 0.0) >= FATIGUE_RECOVERING }.sortedByDescending { fatigue[it] }
    val ready = Muscle.entries.filter { it !in tired }
    Section("Recovery", onClick = { model.setOption("ui.bodyMode", "Fatigue"); model.open(Screen.Body) }) {
        if (tired.isEmpty()) Text("Every muscle is ready", fontWeight = FontWeight.Bold)
        else {
            Text("${musclesText(tired)} still recovering", fontWeight = FontWeight.Bold)
            if (ready.isNotEmpty()) Text("${musclesText(ready)} ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Arrange Home: the real Home laid out as it is (same cards, widths and columns), each card inert and gently jiggling. Hold a card and drag it: the others
 * glide to their new places as you pass them, and letting go keeps the order. A chip on a card's top left flips it between wide and half, the cross on its
 * top right hides it; hidden cards wait in a tray below and a tap puts one back at the end. The two options about how the page is drawn sit under the tray. Profile uses the same screen with its own cards (`ArrangeScreen`).
 * The list is kept here while the page is open and written to storage at every change (a drag writes once, when it ends).
 */
@Composable
fun EditHomeScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val templates by model.templates.collectAsState()
    ArrangeScreen(
        model, "Arrange home", HomeCard.entries, HOME_DEFAULT, { model.homeNow() }, { model.setHome(it) }, "Home reset",
    ) { HomeCardContent(model, it, templates, workouts) }
}

/** The arrange page of any [ArrangeCard] page: its cards [all] and [default] list, how the list is [read] and [write]n (null goes back to the default), and what a card [content] shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <C : ArrangeCard> ArrangeScreen(
    model: AppModel, title: String, all: List<C>, default: List<CardEntry<C>>, read: () -> List<CardEntry<C>>, write: (List<CardEntry<C>>?) -> Unit,
    resetNote: String, content: @Composable (CardEntry<C>) -> Unit,
) {
    var shown by remember { mutableStateOf(read()) }
    val hidden = hiddenCards(all, shown)
    val options by model.options.collectAsState()
    val even = options[HOME_LAYOUT] == "even"
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    fun save(list: List<CardEntry<C>>) { shown = list; write(list) }
    // Open where the list was: the anchor card is scrolled to the place it had on Home (or Profile), before the page is first shown.
    val anchor = remember { model.arrangeAnchor.also { model.arrangeAnchor = null } }
    var placed by remember { mutableStateOf(anchor == null) }
    var gridY by remember { mutableFloatStateOf(0f) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    val badgeRoom = with(LocalDensity.current) { EDIT_BADGE_ROOM.toPx() }
    // Leaving: the wiggle settles to still first, then the page goes.
    val motion = LocalMotion.current
    var calm by remember { mutableStateOf(false) }
    fun leave() {
        if (calm) return
        calm = true
        if (!motion) model.back() else scope.launch { kotlinx.coroutines.delay(Motion.jiggleOutMillis()); model.back() }
    }
    androidx.activity.compose.BackHandler(onBack = ::leave)
    // Where each card sits in the grid (its slot, not where it is drawn), and the drag in grid coordinates.
    val slots = remember { HashMap<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    // Right after a move the slots are one frame old; no further move is made until the grid has been laid out again.
    var settling by remember { mutableStateOf(false) }
    var gridTop by remember { mutableFloatStateOf(0f) }
    var viewTop by remember { mutableFloatStateOf(0f) }
    var viewBottom by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shown) { withFrameNanos { }; settling = false }
    LaunchedEffect(Unit) {
        if (anchor == null) return@LaunchedEffect
        withFrameNanos { }
        slots[anchor.first]?.let { slot -> scroll.scrollTo((gridY + slot.top + badgeRoom - (anchor.second - viewportTop)).roundToInt().coerceAtLeast(0)) }
        placed = true
    }
    fun dragBy(delta: Offset) {
        val key = dragging ?: return
        pointer += delta
        val rootY = gridTop + pointer.y
        val step = when { rootY < viewTop + edge -> -24f; rootY > viewBottom - edge -> 24f; else -> 0f }
        if (step != 0f) scope.launch { pointer += Offset(0f, scroll.scrollBy(step)) }
        if (settling) return
        val other = slots.entries.firstOrNull { it.key != key && pointer in it.value }?.key ?: return
        val to = shown.indexOfFirst { it.card.key == other }
        val card = shown.firstOrNull { it.card.key == key }?.card ?: return
        if (to >= 0) { shown = moveEntry(shown, card, to); settling = true }
    }
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(title, ::leave) {
            TextButton({
                val before = shown
                shown = default
                write(null)
                model.say(resetNote) { shown = before; write(before) }
            }) { Text("Reset") }
            TextButton(::leave) { Text("Done") }
        }
    }) { padding ->
        viewportTop = with(LocalDensity.current) { padding.calculateTopPadding().toPx() }
        SettingsFloat(model, Modifier.padding(padding).fillMaxSize().graphicsLayer { alpha = if (placed) 1f else 0f }) {
            CompositionLocalProvider(LocalEdgeCharts provides (options[HOME_CHARTS] != "inset")) {
                Column(
                    Modifier.fillMaxSize().onGloballyPositioned { viewTop = it.positionInRoot().y; viewBottom = viewTop + it.size.height }.verticalScroll(scroll),
                ) {
                    Text(
                        "Hold a card and drag it to move it. The arrows on a card switch it between wide and half; the cross hides it.",
                        Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EditGrid(shown, even, Modifier.onGloballyPositioned { gridTop = it.positionInRoot().y }.onPlaced { gridY = it.positionInParent().y }) {
                        for ((index, entry) in shown.withIndex()) key(entry.card.key) {
                            val lifted = dragging == entry.card.key
                            EditCard(
                                entry, index, calm, lifted, slots, { pointer - grab },
                                onDragStart = { at ->
                                    val slot = slots[entry.card.key] ?: return@EditCard
                                    dragging = entry.card.key; pointer = slot.topLeft + at; grab = at
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = ::dragBy,
                                onDragEnd = { if (dragging != null) { dragging = null; write(shown) } },
                                actions = listOf(
                                    CustomAccessibilityAction("Move up") { save(moveEntry(shown, entry.card, index - 1)); true },
                                    CustomAccessibilityAction("Move down") { save(moveEntry(shown, entry.card, index + 1)); true },
                                ) + (if (entry.card.canHalf) listOf(CustomAccessibilityAction(if (entry.wide) "Make half width" else "Make wide") { save(toggleWidth(shown, entry.card)); true }) else emptyList()) +
                                    CustomAccessibilityAction("Hide") { save(shown - entry); true },
                                onWidth = { save(toggleWidth(shown, entry.card)) },
                                onHide = { save(shown - entry) }, content = content,
                            )
                        }
                    }
                    if (hidden.isNotEmpty()) {
                        Caption("Hidden: tap to add", Modifier.padding(start = Space.xl, top = Space.l, bottom = Space.xs))
                        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (card in hidden) AssistChip({ save(shown + CardEntry(card, card.wide)) }, { Text(card.label) }, Modifier.semantics { contentDescription = "Show ${card.label}. ${card.about}" })
                        }
                    }
                    Section("Look") {
                        HomeOptions(model)
                        Text("These apply to Home and Profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(settingsFloatSpace(model)))
                }
            }
        }
    }
}

/**
 * Every card in one layout, so each can glide to its new place when the order changes: a wide card alone across the page, a run of half cards in two columns
 * (staggered into the shorter, or even in pairs) as on Home. [entries] and the children are in the same order.
 */
@Composable
private fun <C : ArrangeCard> EditGrid(entries: List<CardEntry<C>>, even: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { ms, constraints ->
        val gap = HALF_GAP.roundToPx()
        val side = 16.dp.roundToPx()
        val vertical = 6.dp.roundToPx()
        val width = (constraints.maxWidth - 2 * side - gap) / 2
        val room = EDIT_BADGE_ROOM.roundToPx()
        val spots = arrayOfNulls<Triple<Placeable, Int, Int>>(ms.size)
        var y = 0
        var i = 0
        while (i < ms.size) {
            if (entries[i].wide) {
                val p = ms[i].measure(Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth))
                spots[i] = Triple(p, 0, y); y += p.height; i++
                continue
            }
            var j = i
            while (j < ms.size && !entries[j].wide) j++
            val run = (i until j).toList()
            y += vertical
            if (!even) {
                val column = Constraints(minWidth = width + room, maxWidth = width + room)
                val ps = run.map { ms[it].measure(column) }
                val cols = placeColumns(ps.map { it.height }, gap)
                val used = intArrayOf(0, 0)
                run.forEachIndexed { k, idx -> spots[idx] = Triple(ps[k], side + cols[k] * (width + gap), y + used[cols[k]]); used[cols[k]] += ps[k].height + gap }
                y += (maxOf(used[0], used[1]) - gap).coerceAtLeast(0)
            } else {
                for (pair in run.chunked(2)) {
                    val h = pair.maxOf { ms[it].minIntrinsicHeight(width + room) }
                    pair.forEachIndexed { k, idx -> spots[idx] = Triple(ms[idx].measure(Constraints.fixed(width + room, h)), side + k * (width + gap), y) }
                    y += h + gap
                }
                y -= gap
            }
            y += vertical
            i = j
        }
        layout(constraints.maxWidth, y) { for (s in spots) s?.let { (p, px, py) -> p.place(px, py) } }
    }
}

/**
 * One card in Arrange Home: its real content, inert, with the width chip and the hide cross over it. Hold and drag moves it ([onDragStart] gets the
 * touch point in the card, [onDrag] each movement); [dragAt] is where the lifted card's top left is wanted in the grid. A card that moves (not the lifted
 * one) glides to its new place; with animations off it jumps, and nothing jiggles. Card [index] eases into its wiggle a beat after the one before it, and
 * every card eases back to still once [calm] (the page is closing).
 */
@Composable
private fun <C : ArrangeCard> EditCard(
    entry: CardEntry<C>, index: Int, calm: Boolean, lifted: Boolean, slots: MutableMap<String, Rect>, dragAt: () -> Offset,
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit,
    actions: List<CustomAccessibilityAction>, onWidth: () -> Unit, onHide: () -> Unit, content: @Composable (CardEntry<C>) -> Unit,
) {
    val motion = LocalMotion.current
    val card = entry.card
    val shownAt = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var slot by remember { mutableStateOf<IntOffset?>(null) }
    var started by remember { mutableStateOf(false) }
    val drawn = remember { arrayOfNulls<IntOffset>(1) }
    LaunchedEffect(slot) {
        val to = slot ?: return@LaunchedEffect
        if (!started || !motion) { shownAt.snapTo(to); started = true } else shownAt.animateTo(to, Motion.follow())
    }
    // Let go: start from where the finger left it and settle into the slot.
    LaunchedEffect(lifted) {
        val from = drawn[0]
        if (!lifted && from != null) { drawn[0] = null; shownAt.snapTo(from); slot?.let { if (motion) shownAt.animateTo(it, Motion.follow()) else shownAt.snapTo(it) } }
    }
    // The wiggle grows from still when the page opens, a beat later for each card down the page, and settles back to still when it closes.
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(calm) { wiggle.animateTo(if (calm) 0f else 1f, if (calm) Motion.jiggleOut() else Motion.jiggleIn(index)) }
    val tilt = if (motion && !lifted) {
        val t = rememberInfiniteTransition(label = "jiggle")
        t.animateFloat(-0.5f, 0.5f, Motion.jiggle(card.key.hashCode()), label = "tilt")
    } else null
    var empty by remember { mutableStateOf(false) }
    val room = with(LocalDensity.current) { EDIT_BADGE_ROOM.toPx() }
    Box(
        Modifier
            .onPlaced {
                val at = it.positionInParent().round()
                slots[card.key] = Rect(Offset(at.x.toFloat(), at.y.toFloat()), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                if (slot != at) slot = at
            }
            .offset {
                val at = slot ?: IntOffset.Zero
                if (lifted) dragAt().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }.also { drawn[0] = it } - at
                else shownAt.value - at
            }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { rotationZ = (tilt?.value ?: 0f) * wiggle.value; scaleX = if (lifted) 1.03f else 1f; scaleY = scaleX }
            .clearAndSetSemantics { contentDescription = "${card.label}, ${if (entry.wide) "wide" else "half width"}. ${card.about}"; customActions = actions },
    ) {
        // The card sits inside room for the two buttons on its top right corner (a wide card has that room at its side already).
        Box(Modifier.padding(top = EDIT_BADGE_ROOM, end = if (entry.wide) 0.dp else EDIT_BADGE_ROOM).heightIn(min = 64.dp)) {
            if (empty) Text(card.label, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.onSizeChanged { empty = it.height == 0 }) {
                CompositionLocalProvider(LocalHalfCard provides !entry.wide) { content(entry) }
            }
            // Over the card: no tap reaches it; a hold and drag moves it. The point is given in the whole slot's terms.
            Box(
                Modifier.matchParentSize().pointerInput(card.key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart(it + Offset(0f, room)) }, onDragEnd = onDragEnd, onDragCancel = onDragEnd,
                    ) { change, drag -> change.consume(); onDrag(drag) }
                },
            )
        }
        // Round buttons centred on the card's top right corner, half outside it: the cross, and the width switch just left of it.
        Row(Modifier.align(Alignment.TopEnd).padding(end = EDIT_BADGE_ROOM - EDIT_BADGE_HIT / 2), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            if (card.canHalf) EditBadge(CleanIcons.LeftRight, if (entry.wide) "Make half width" else "Make wide", onWidth)
            EditBadge(CleanIcons.Close, "Hide ${card.label}", onHide)
        }
    }
}

/** Room reserved above and (on a half card) beside a card for its buttons, and the buttons' sizes: 28 dp to see, 44 dp to touch. */
private val EDIT_BADGE_ROOM = 22.dp
private val EDIT_BADGE_HIT = 44.dp
private val EDIT_BADGE_SIZE = 28.dp

/** One round button of Arrange Home: a small disc with an icon, in a larger touch area. */
@Composable
private fun EditBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(EDIT_BADGE_HIT).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(EDIT_BADGE_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}
