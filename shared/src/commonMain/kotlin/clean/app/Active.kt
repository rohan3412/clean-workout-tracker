package clean.app

import clean.core.time.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import clean.core.setLabels
import clean.core.numbered
import clean.core.roundCounts
import clean.core.roundKind
import clean.core.acceptHints
import clean.core.carriedSets
import clean.core.setsAbove
import clean.core.focusAfter
import clean.core.focusStart
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow

import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import clean.core.WARMUP_SCHEMES
import clean.core.warmupSets
import clean.core.PrKind
import clean.core.parseRestPresets
import androidx.compose.runtime.produceState
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import clean.app.ui.theme.PillShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import clean.app.ui.theme.SheetTopShape
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.orange
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.workoutSkin
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import clean.core.weightStep
import clean.core.nextTarget
import clean.core.Progression
import clean.core.nextFor
import clean.core.parseGoal
import clean.core.goalReachedOn
import clean.core.exerciseGoalProgress
import clean.core.Target
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import clean.app.platform.LocalPlatform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import clean.core.BODYWEIGHT_WINDOW_DAYS
import clean.core.ExerciseRecords
import clean.core.LoadType
import clean.core.SetType
import clean.core.Workout
import clean.core.decimalText
import clean.core.OneRm
import clean.core.PrSet
import clean.core.Side
import clean.core.SideRecords
import clean.core.SidedBests
import clean.core.nextForSides
import clean.core.prBestsBySide
import clean.core.kgToLb
import clean.core.recordsBySide
import clean.core.sidesOf
import clean.core.pairHolding
import clean.core.setsToAddPair
import clean.core.sidePairs
import clean.core.startsSidesRest
import clean.core.shortNumber

/** The tab an exercise opens on when it is reached from a workout in progress: History. */
const val WORKOUT_EXERCISE_TAB = 1

/**
 * A set row (or a round) with the swipes: right finishes, left removes.
 * Finishing is the box's own tick, so hints are accepted and the rest starts as for a tap; on a set already done the
 * same swipe un-ticks it, like the checkbox. The buzz at the threshold is the tick's.
 */
@Composable
private fun SetSwipe(done: Boolean, onTick: () -> Unit, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val feel = LocalFeel.current
    val colors = LocalCleanColors.current
    fun action(kind: String) = when (kind) {
        "finish" -> SwipeAction(if (done) "Not done" else "Done", CleanIcons.Check, colors.done, false, onTick)
        "delete" -> SwipeAction("Remove", CleanIcons.Delete, colors.danger, true, onRemove)
        else -> null
    }
    // The colour has the row's shape: it starts where the row's own edge does (the row's highlight and tint are drawn there).
    SwipeRow(left = action(feel.swipeLeft), right = action(feel.swipeRight), haptic = if (feel.tick != "off") Haptic.Tick else null, inset = PaddingValues(horizontal = Space.xs), content = content)
}

/** Whether a swipe set to [kind] removes an exercise when made on its name: only "delete" does. */
fun removesExercise(kind: String) = kind == "delete"

/** [content] in a [SwipeRow], which takes no sideways drag while it has no swipe to make (focus mode, or both swipes off). */
@Composable
private fun OptionalSwipe(left: SwipeAction?, right: SwipeAction?, content: @Composable () -> Unit) {
    val tick = LocalFeel.current.tick
    SwipeRow(left = left, right = right, haptic = if (tick != "off") Haptic.Tick else null, content = content)
}

/**
 * What focus mode shows of the set to do next in a card, and what its Done button does: the same tick the row's own box does.
 * [side] is "Left" or "Right" for a half of a pair; [weight] and [reps] are the typed values, else the hint the row shows in grey.
 */
private class FocusInfo(
    val number: Int, val total: Int, val side: String?, val weight: String, val reps: String, val weightTyped: Boolean, val repsTyped: Boolean,
    val weightUnit: String, val repsUnit: String, val last: String?, val spoken: String, val onEdit: (Boolean) -> Unit, val onDone: () -> Unit,
    /** "+" before the weight of a weighted bodyweight exercise and "−" before an assisted one, as the hint and the goal line write it. */
    val weightSign: String = "",
)

/** A card hands its next set to the page that holds it (focus mode), which draws it large above the card. */
private class FocusSlot {
    var info by mutableStateOf<FocusInfo?>(null)
}

/** A stored weight as text for an entry box: exact in kg, nearest half pound in lb. */
fun entryWeight(kg: Double, unit: String): String =
    if (unit == "lb") shortNumber(kgToLb(kg)) else decimalText(kg).removeSuffix(".0")

/** The workout the cards below belong to; null is the workout in progress (`AppModel.liveSlot`). */
private val LocalDraftSlot = staticCompositionLocalOf<AppModel.DraftSlot?> { null }

/** The number pad's keys acting on the set it is on, in the workout of [slot]; [keys] are its cards' ids, in order. */
private class PadActions(val pad: Pad, val slot: AppModel.DraftSlot, val keys: () -> List<String>) {
    /** Changes the set the pad is on. */
    fun set(change: (DraftSet) -> DraftSet) {
        val t = pad.target ?: return
        val current = slot.draft ?: return
        val at = keys().indexOf(t.card)
        val block = current.blocks.getOrNull(at) ?: return
        val item = block.items.getOrNull(t.item) as? DraftSet ?: return
        val items = block.items.toMutableList().also { it[t.item] = change(item) }
        slot.put(current.copy(blocks = current.blocks.toMutableList().also { it[at] = block.copy(items = items) }))
    }

    fun edit(change: (String) -> String) {
        val reps = pad.target?.reps ?: return
        set { s -> if (reps) s.copy(reps = change(s.reps).take(8)) else s.copy(weight = change(s.weight).take(8)) }
    }

    fun key(key: String) {
        val fresh = pad.fresh
        val meta = pad.meta
        when (key) {
            "back" -> edit { if (fresh) "" else it.dropLast(1) }
            "." -> if (meta.decimal) edit { old -> (if (fresh) "" else old).let { if ('.' in it) it else it.ifEmpty { "0" } + "." } }
            // A step up or down starts from what the box shows, which for an empty one is last time's value.
            "+", "-" -> edit { old ->
                val now = parseNumber(old.ifEmpty { meta.hint }) ?: 0.0
                shortNumber((now + if (key == "+") meta.step else -meta.step).coerceAtLeast(0.0))
            }
            // The value of the same box in the set above.
            "same" -> if (meta.above.isNotEmpty()) edit { meta.above }
            else -> edit { (if (fresh) "" else it) + key }
        }
        // After a step (or "Same") the next digit still starts afresh; after anything typed it adds on.
        pad.fresh = key == "+" || key == "-" || key == "same"
    }

    /** Weight, then reps, then the next set's weight, into the next exercise; after the last box the pad closes. */
    fun next() {
        val t = pad.target ?: return
        val current = slot.draft ?: return
        if (!t.reps) return pad.focus(t.card, t.item, true)
        val keys = keys()
        val at = keys.indexOf(t.card)
        val later = current.blocks.getOrNull(at)?.items.orEmpty().withIndex().firstOrNull { it.index > t.item && it.value is DraftSet }
        val first = current.blocks.getOrNull(at + 1)?.items?.indexOfFirst { it is DraftSet } ?: -1
        when {
            later != null -> pad.focus(t.card, later.index, false)
            first >= 0 -> pad.focus(keys[at + 1], first, false)
            else -> pad.close()
        }
    }
}

@Composable
fun ActiveScreen(model: AppModel) {
    val draft = model.draft
    if (draft == null) {
        // Only while this page is the one on top: as it slides away it must not take the page under it along.
        LaunchedEffect(Unit) { if (model.stack.lastOrNull() == Screen.Active) model.back() }
        return
    }
    // Asked once, at the first workout, for the notification that shows it on the lock screen (Android 13 and later); refused, it stays off.
    val platform = LocalPlatform.current
    val askNotice = platform.rememberNotificationPermissionRequest { granted ->
        if (!granted) model.setOption("workout.notif", "off")
    }
    LaunchedEffect(Unit) {
        val o = model.options.value
        if (clean.core.workoutNotifOn(o["workout.notif"]) && o["workout.notif.asked"] == null && !platform.notificationsPermitted) {
            model.setOption("workout.notif.asked", "yes")
            askNotice()
        }
    }
    val workouts by model.workouts.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    // Read once and flipped when the five minutes are up; the ticking clock is read only where a time is shown,
    // so the whole screen is not redrawn every second.
    val early by produceState(draft.isEarly(currentMillis()), draft.startMillis) {
        val left = draft.startMillis + 5 * 60_000 - currentMillis()
        if (left > 0) delay(left)
        value = false
    }
    val highlight = model.flag("workout.highlight")
    val next = if (highlight) nextBlock(draft) else null
    // What was just removed can be put back from the app's one Undo bar.
    val offerUndo = { what: String, restore: () -> Unit -> model.say(what) { restore() } }
    // After a set is ticked the card with the next one comes into view.
    val stored by model.options.collectAsState()
    // Layout: one exercise at a time (the All / Focus switch in the top bar) or the whole list.
    val focus = (stored["workout.focus"] ?: "off") == "on"
    val list = rememberLazyListState()
    val moving = LocalMotion.current
    // D10: the next exercise eases to the top on the glide curve (not the stock scroll); a finger on the list wins over it, and a card already at the top stays.
    LaunchedEffect(next, focus) {
        if (focus || next == null || draft.lastDoneMillis == null) return@LaunchedEffect
        if (!moving) { list.scrollToItem(next + 1); return@LaunchedEffect }
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == next + 1 }
        if (item != null) {
            // Round 28: the next card becomes the list's anchor where it stands now, so the finished card above it folding (its height shrinking over the same frames) cannot carry it anywhere: it is then eased to the top by the scroll alone, with no lag and no overshoot below.
            list.requestScrollToItem(next + 1, -item.offset)
            list.animateScrollBy((item.offset).toFloat(), Motion.glide())
        }
        else list.animateScrollToItem(next + 1)
    }
    // Focus mode: a page per exercise, then a last page for adding one. It opens on the first exercise with a set to do,
    // and moves on by itself when the one on show is finished.
    val open = draft.blocks.map { b -> b.items.any { it is DraftSet && !it.done } }
    val pager = rememberPagerState(focusStart(open)) { draft.blocks.size + 1 }
    if (focus) {
        LaunchedEffect(Unit) { pager.scrollToPage(focusStart(open)) }
        val animated = LocalMotion.current
        val here = pager.currentPage
        val hereOpen = open.getOrNull(here) == true
        var wasOpen by remember(here) { mutableStateOf(hereOpen) }
        LaunchedEffect(here, hereOpen) {
            val to = if (wasOpen && !hereOpen) focusAfter(open, here) else null
            wasOpen = hereOpen
            if (to != null) {
                // A beat to see the tick, then the next exercise's page in place (no slide).
                if (animated) delay(Motion.SLOW.toLong())
                pager.scrollToPage(to)
            }
        }
        // An exercise just added is the one to show.
        var count by remember { mutableStateOf(draft.blocks.size) }
        LaunchedEffect(draft.blocks.size) {
            if (draft.blocks.size > count) pager.scrollToPage(draft.blocks.size - 1)
            count = draft.blocks.size
        }
    }
    var restOptions by remember { mutableStateOf(false) }
    if (restOptions) {
        if (model.restEnd == null) restOptions = false
        else CleanDialog(
            onDismissRequest = { restOptions = false },
            // The ticking clock is read in here, so the screen behind is not redrawn each second.
            title = { Text("Rest ${clock(((model.restEnd ?: 0L) - nowMillis()).coerceAtLeast(0) / 1000)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Choice(listOf(-30, -15, 15, 30, 60), 0, { (if (it > 0) "+" else "−") + (if (kotlin.math.abs(it) == 60) "1 min" else "${kotlin.math.abs(it)} s") }) { model.changeRest(it) }
                    // An exact time left, whatever it is now.
                    RestChoice("Set the time left", null, emptyList(), { model.startRest(it, changing = true) }, custom = "Set…")
                    // Start the rest again at a usual length.
                    Text("Rest again for", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RestPresets(parseRestPresets(null), { model.startRest(it) })
                    Text("How long the rest is to begin with is set per exercise (Rest timer, in its options) and for all exercises in Settings, which also holds these lengths.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { restOptions = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { model.startRest(0); restOptions = false }) { Text("Skip rest") } },
        )
    }
    // No unlocking the phone between sets.
    val screenPlatform = LocalPlatform.current
    DisposableEffect(Unit) {
        screenPlatform.keepScreenOn(true)
        onDispose { screenPlatform.keepScreenOn(false) }
    }

    // Holding an exercise's name folds every card down to its name; dragging then moves it among
    // the others, and letting go unfolds them again. The held card is drawn where the finger is
    // (`fingerY` against its slot in the list), so it does not jump when it swaps with a neighbour,
    // and it swaps when its centre passes the neighbour's centre.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    // The card still settling into its slot after the finger lifted, and how far it is from it.
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    var settleOffset by remember { mutableStateOf(0f) }
    var fingerY by remember { mutableStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    val density = LocalDensity.current
    // Where the finger took hold of the card, from the card's top (the middle of the name row until a card says), and how near an edge the list scrolls.
    val grabDefault = with(density) { 34.dp.toPx() }
    var grabPx by remember { mutableStateOf(grabDefault) }
    val topZonePx = with(density) { REORDER_TOP_ZONE.toPx() }
    val bottomZonePx = with(density) { REORDER_BOTTOM_ZONE.toPx() }
    // The number pad and the rest bar float over the list's end, so the list's visible bottom is above them.
    val pad = remember { Pad() }
    var restBarPx by remember { mutableStateOf(0) }
    // A folded card's name sits this far lower in its row than the unfolded card's (the row is 52 dp, the unfolded one 48), so the folded card starts that much higher.
    val foldedLift = with(density) { 2.dp.toPx() }
    // Room above and below the list while it is folded, so the folded list can sit around the held name wherever that is on the screen.
    var roomTop by remember { mutableStateOf(0f) }
    var roomBottom by remember { mutableStateOf(0f) }
    val scrollPx = with(density) { 12.dp.toPx() }
    /** The list's entry for the card at [index]; the item before the cards is the title. */
    fun slot(index: Int) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index + 1 }
    /** Where an entry's top is on the screen (its offset counts from the list's padded start). */
    fun top(item: androidx.compose.foundation.lazy.LazyListItemInfo) = item.offset + list.layoutInfo.beforeContentPadding
    /** How far the held card is drawn from its slot. */
    fun heldOffset(): Float = dragIndex?.let { i -> slot(i)?.let { fingerY - grabPx - top(it) } } ?: settleOffset
    fun dragBy(dy: Float) { if (dragIndex != null) fingerY += dy }
    /** Gives the list [top] and [bottom] of room at its ends, every card staying where it is on the screen. */
    fun reroom(top: Float, bottom: Float) {
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return
        val before = list.layoutInfo.beforeContentPadding
        val padding = before - roomTop + top
        roomTop = top
        roomBottom = bottom
        list.requestScrollToItem(first.index, (padding - (first.offset + before)).coerceAtLeast(0f).toInt())
    }
    /** Cuts the room given at the ends down to what is needed: a blank above the title or below the last entry, when they are on the screen. */
    fun trimRoom() {
        val info = list.layoutInfo
        val low = info.beforeContentPadding - roomTop
        val title = info.visibleItemsInfo.firstOrNull { it.index == 0 }
        val footer = info.visibleItemsInfo.firstOrNull { it.index == draft.blocks.size + 1 }
        reroom(
            if (title != null) (top(title) - low).coerceAtLeast(0f) else 0f,
            if (footer != null) (info.viewportSize.height - (top(footer) + footer.size)).toFloat().coerceAtLeast(0f) else 0f,
        )
    }
    fun drop() {
        val i = dragIndex ?: return
        // The cards unfold with the dropped one where it was let go (as far as the list's ends allow).
        val held = fingerY - grabPx
        val room = list.layoutInfo.viewportSize.height.toFloat()
        val low = list.layoutInfo.beforeContentPadding - roomTop
        roomTop = room
        roomBottom = room
        list.requestScrollToItem(i + 1, (low + room - held).coerceAtLeast(0f).toInt())
        settleOffset = 0f
        dragIndex = null
        dropIndex = i
        settling = scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            trimRoom()
            if (motion) Animatable(settleOffset).animateTo(0f, Motion.short()) { settleOffset = value }
            settleOffset = 0f
            dropIndex = null
            // The room kept above the title (and below the last card) so nothing jumped while folded is given back: the list glides
            // up to its normal top. Kept, it left a blank half screen above the workout's title until the page was left (the user's
            // screenshot, 2026-11-19).
            val fromTop = roomTop
            val fromBottom = roomBottom
            if (fromTop > 0f || fromBottom > 0f) {
                if (motion) Animatable(1f).animateTo(0f, Motion.short()) { roomTop = fromTop * value; roomBottom = fromBottom * value }
                roomTop = 0f
                roomBottom = 0f
            }
        }
    }
    LaunchedEffect(dragIndex != null) {
        var frames = 0
        while (dragIndex != null) {
            withFrameNanos { }
            val i = dragIndex ?: break
            // Once the folded list is laid out, the room it was given is cut down to what it needs, with the held card staying where it is.
            if (++frames == 2) trimRoom()
            // Near the top or bottom the list scrolls, faster the nearer the edge; the card stays under the finger.
            val viewport = list.layoutInfo.viewportSize.height
            val push = reorderPush(fingerY, viewport - pad.height - restBarPx.toFloat(), topZonePx, bottomZonePx)
            if (push != 0f) list.scrollBy(push.coerceIn(-1f, 1f) * scrollPx)
            // One step per frame, when the card's centre has passed the neighbour's centre.
            val me = slot(i) ?: continue
            val centre = fingerY - grabPx + me.size / 2f
            val below = slot(i + 1)
            val above = slot(i - 1)
            val by = when {
                below != null && centre > top(below) + below.size / 2f -> 1
                above != null && centre < top(above) + above.size / 2f -> -1
                else -> 0
            }
            if (by != 0) {
                model.moveExercise(i, by)
                haptic(Haptic.Tick)
                dragIndex = i + by
            }
        }
    }

    // A card is known by its own id, so it glides when the order changes and the pad stays on it, even with an exercise there twice.
    val keys = remember(draft.blocks) { cardKeys(draft.blocks) }

    // Weights and reps are typed on the app's own number pad. It closes on Back, and when the phone's keyboard comes up for a name or a note.
    BackHandler(enabled = pad.target != null) { pad.close() }
    // The inset is read outside composition, so its animation does not redraw the whole screen.
    val ime = WindowInsets.ime
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) > 0 }.distinctUntilChanged().collect { if (it) pad.close() }
    }
    val keysNow by rememberUpdatedState(keys)
    val padActions = remember(pad) { PadActions(pad, model.liveSlot) { keysNow } }

    val summary = model.flag("workout.summary")
    fun finish() {
        val name = model.draft?.name ?: "Workout"
        val saved = model.finishWorkout()
        if (saved != null) {
            // D16: the optional soft chime (the PR chime, quieter), off unless switched on beside the rest sound.
            model.buzz(Haptic.Heavy)
            if (model.options.value["finish.chime"] == "on") model.platform.chime(0.5f)
            model.back()
            // What the workout came to, or just a word that it is saved.
            if (summary) model.open(Screen.Summary(saved))
            model.saidSaved(name, saved)
        } else {
            confirm = "discard"
        }
    }

    // Round 30: the dock pill's Finish and Cancel open this page and ask for its own buttons' work, after the page has grown out of the pill.
    val request = model.activeRequest
    val grew = LocalMotion.current
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        if (grew) delay(Motion.CARD_TO_PAGE.toLong())
        model.activeRequest = null
        if (model.stack.lastOrNull() != Screen.Active) return@LaunchedEffect
        if (request == "cancel") confirm = "cancel" else if (request == "finish") { if (model.validUnfinished(draft) > 0) confirm = "finish" else finish() }
    }

    /** A card changed (or was removed, when [changed] is null): saved, and a set just ticked starts the rest (the model's own, so the notification's "Set done" does the same). */
    fun blockChanged(index: Int, block: DraftBlock, changed: DraftBlock?) = model.blockChanged(index, block, changed)

    val feel = Feel(if (LocalMotion.current) "light" else "off")
    val rowDialogs = remember { RowDialogs() }
    // What floats (the number pad, the rest panel) is glass over the list, which is the blur source and scrolls under the pad.
    val haze = dev.chrisbanes.haze.rememberHazeState()
    val padDp = with(LocalDensity.current) { pad.height.toDp() }
    // The room at the list's ends is read by the list when it measures, not here: the settle of the folded list's room (a few frames, every
    // time a card is dropped) then re-measures the list and nothing else, instead of recomposing this whole screen each frame.
    val padNow = rememberUpdatedState(padDp)
    // The rest bar floats over the list's bottom, so the list leaves room for it (0 while no bar shows).
    val restBarDp = with(LocalDensity.current) { restBarPx.toDp() }
    val barNow = rememberUpdatedState(restBarDp)
    val listRoom = remember(density) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
            override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
            override fun calculateTopPadding() = with(density) { roomTop.toDp() }
            override fun calculateBottomPadding() = with(density) { roomBottom.toDp() } + padNow.value + barNow.value
        }
    }
    // A keyboard plugged into the phone types into the box the pad is on (C4): digits, point, Backspace; Enter is Next.
    val keyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(pad.target != null) { if (pad.target != null) runCatching { keyFocus.requestFocus() } }
    // The page itself is quiet (no blur on the number pad); the rest bar and pill are the floating layer and follow the app's own setting.
    val appSolid = LocalSolidGlass.current
    CompositionLocalProvider(LocalFeel provides feel, LocalPad provides pad, LocalRowDialogs provides rowDialogs, LocalHaze provides haze, LocalSolidGlass provides true) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.closePadOnTap(pad).imePadding().focusRequester(keyFocus).focusable().onPreviewKeyEvent { e ->
            if (pad.target == null || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false
            else {
                val code = e.key
                val key = padKeyOf(code)
                when {
                    key != null -> { padActions.key(key); true }
                    code == Key.Enter || code == Key.NumPadEnter -> { padActions.next(); true }
                    else -> false
                }
            }
        }, containerColor = pageGround(),
        topBar = {
            val now = nowMillis()
            Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = model::back) { Icon(CleanIcons.ChevronDown, "Minimise workout") }
                // For the first five minutes cancelling is at hand up here, for a workout started by mistake; after that it is at the bottom.
                if (early) DangerButton("Cancel", { confirm = "cancel" })
                val rest = model.restEnd?.let { (it - now) / 1000 }?.takeIf { it > 0 }
                // The rest in progress; a tap on it gives the ways to change it.
                if (rest != null) Text(
                    "Rest ${clock(rest)}", Modifier.weight(1f).clickable { restOptions = true }.minTouch().wrapContentHeight(Alignment.CenterVertically),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
                else Text(
                    draft.lastDoneMillis?.let { "Since last set  ${clock((now - it) / 1000)}" } ?: "",
                    Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Back and forth between one exercise at a time and the whole list.
                QuietButton(
                    if (focus) "All" else "Focus", { model.setOption("workout.focus", if (focus) "off" else "on") },
                    Modifier.padding(end = Space.s).semantics { contentDescription = if (focus) "Show all exercises" else "Focus on one exercise" },
                    contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
                )
                // Sets that hold values but were not ticked are asked about; empty and incomplete ones go without asking.
                MainButton(
                    "Finish", { if (model.validUnfinished(draft) > 0) confirm = "finish" else finish() },
                    Modifier.padding(end = Space.s).breathing(everySetDone(draft.blocks)), contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
                )
            }
            // The rest draining away, as a line under the bar.
            val end = model.restEnd
            if (end != null && model.restTotal > 0) {
                // Read when drawing: a width read in composition recomposed the whole top bar each frame of the rest.
                val left = animateFloatAsState(((end - now) / (model.restTotal * 1000f)).coerceIn(0f, 1f), Motion.clock(), label = "rest")
                val bar = MaterialTheme.colorScheme.primary
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant).drawBehind { drawRect(bar, size = androidx.compose.ui.geometry.Size(size.width * left.value, size.height)) })
            }
            }
        },
    ) { padding ->
        RestDisplay(model, draft, { restOptions = true }, Modifier.padding(padding), above = padDp, onBarHeight = { restBarPx = it }, solid = appSolid) {
        BoxWithConstraints(Modifier.fillMaxSize().hazeSource(haze)) {
        if (focus) Box(Modifier.fillMaxSize().padding(bottom = padDp + restBarDp)) { FocusPages(
            model, workouts, draft, keys, pad, next, pager, offerUndo,
            { i, b, changed -> blockChanged(i, b, changed) }, { picking = true }, if (early) null else ({ confirm = "cancel" }),
        ) }
        else LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = listRoom) {
            item { WorkoutTitle(model, draft) }
            itemsIndexed(draft.blocks, key = { i, _ -> keys[i] }) { index, block ->
                // Kept between redraws while nothing about it changes, so a key press redraws only the card it changed.
                val collapsed = dragIndex != null
                val lifted = dragIndex == index
                // Held from the pick-up until it has settled; it is not moved by the list's own placement.
                val held = (dragIndex ?: dropIndex) == index
                val last = draft.blocks.lastIndex
                val isNext = next == index
                val reorder = remember(index, collapsed, lifted, held, last, isNext) {
                    Reorder(
                        collapsed = collapsed, lifted = lifted, held = held, canUp = index > 0, canDown = index < last,
                        move = { model.moveExercise(index, it) },
                        start = { grab ->
                            haptic(Haptic.Tick)
                            settling?.cancel()
                            settleOffset = 0f
                            dropIndex = null
                            // The card is held where it was touched, so it does not jump as it is picked up. Its name stays where it is
                            // on the screen: the folded list is laid out around it, above it the cards before it and below it those after.
                            grabPx = (grab ?: grabDefault) + foldedLift
                            fingerY = (slot(index)?.let { top(it) } ?: 0) + grabPx - foldedLift
                            val room = list.layoutInfo.viewportSize.height.toFloat()
                            val low = list.layoutInfo.beforeContentPadding - roomTop
                            roomTop = room
                            roomBottom = room
                            list.requestScrollToItem(index + 1, (low + room - (fingerY - grabPx)).coerceAtLeast(0f).toInt())
                            dragIndex = index
                        },
                        drag = ::dragBy, end = ::drop, offset = ::heldOffset,
                        next = isNext, superset = { model.toggleSuperset(index) },
                    )
                }
                val glide = if (motion && !held) Modifier.animateItem(fadeInSpec = Motion.gap(), fadeOutSpec = Motion.gap(), placementSpec = Motion.part()) else Modifier
                BlockCard(model, workouts, block, reorder, keys[index], pad, glide, onSwitch = { model.switchVariation(index, it) }, onUndo = offerUndo) { changed -> blockChanged(index, block, changed) }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    QuietButton("Add exercise", { picking = true }, Modifier.fillMaxWidth())
                    if (!early) DangerButton("Cancel workout", { confirm = "cancel" }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
        }
        }
    }
    // The number pad floats over the bottom of the list (glass over the blur of the rows scrolling under it).
    AnimatedVisibility(pad.target != null, Modifier.align(Alignment.BottomCenter).imePadding(), enter = Motion.appear(LocalMotion.current), exit = Motion.vanish(LocalMotion.current)) {
        val rpeOn = model.flag("workout.rpe")
        val rpeNow = pad.target?.let { t -> (draft.blocks.getOrNull(keys.indexOf(t.card))?.items?.getOrNull(t.item) as? DraftSet)?.rpe }.orEmpty()
        DisposableEffect(pad) { onDispose { pad.height = 0 } }
        Box(Modifier.testTag("number pad").onGloballyPositioned { pad.bounds = it.boundsInWindow() }.onSizeChanged { pad.height = it.height }) {
            Keypad(pad.meta, if (rpeOn) rpeNow else null, padActions::key, { value -> padActions.set { it.copy(rpe = value) } }, padActions::next, onHide = pad::close)
        }
    }
    RowDialogHost(rowDialogs)
    }
    }

    if (picking) ExercisePicker(
        model, workouts, onPick = { model.addExercise(it); picking = false }, onDismiss = { picking = false },
        onPickMany = { names -> names.forEach { model.addExercise(it) }; picking = false },
        title = "Add exercise", onPickNext = { model.addExercise(it, afterCurrent = true); picking = false },
    )
    when (val c = confirm) {
        null -> {}
        "cancel" -> if (draft.blocks.any { b -> b.items.any { it is DraftSet && it.done } }) CleanDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Cancel workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("The sets you ticked and everything entered in this workout are thrown away.")
                    SlideToConfirm("Slide to discard", "Discard workout", { confirm = null; model.cancelWorkout() }, Modifier.fillMaxWidth())
                    TextButton(onClick = { confirm = null }, modifier = Modifier.fillMaxWidth()) { Text("Keep it") }
                }
            },
            confirmButton = {},
        ) else Confirm("Cancel workout?", "Everything entered in this workout is thrown away.", "Discard", { model.cancelWorkout() }) { confirm = null }
        "discard" -> Confirm(
            if (c == "cancel") "Cancel workout?" else "Nothing to save",
            if (c == "cancel") "Everything entered in this workout is thrown away." else "No set is ticked, so there is nothing to save. Discard this workout?",
            "Discard",
            { model.cancelWorkout() },
        ) { confirm = null }
        else -> CleanDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Finish workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("There are some valid sets in this workout that have not been marked as complete. Invalid or empty sets will be removed.")
                    MainButton("Complete unfinished sets", { confirm = null; model.completeUnfinished(); finish() }, Modifier.fillMaxWidth())
                    QuietButton("Discard unfinished sets", { confirm = null; finish() }, Modifier.fillMaxWidth())
                    TextButton(onClick = { confirm = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {},
        )
    }
}

/** The workout's name, to rename, over the time it has run (or the day it is logged for). */
@Composable
private fun WorkoutTitle(model: AppModel, draft: Draft) {
    // A workout opened from the banner has the banner's name travelling into it and, on Back, out of it again ([MorphText]). The words must
    // already be there when Back is pressed, so they stay a text, and become the name's field on a tap, until it is the one being edited.
    val card = LocalPageCard.current
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
        val nameStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        if (card != null && !editing) MorphText(
            androidx.compose.ui.text.AnnotatedString(draft.name), "name", Modifier.clickable(onClickLabel = "Rename workout") { editing = true }, onPage = true, style = nameStyle, maxLines = 1,
        ) else if (card != null) {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draft.name, androidx.compose.ui.text.TextRange(draft.name.length))) }
            BasicTextField(
                value = field,
                onValueChange = { field = it; if (it.text != draft.name) model.putDraft(draft.copy(name = it.text)) },
                modifier = Modifier.focusRequester(focus),
                singleLine = true,
                textStyle = nameStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else BasicTextField(
            value = draft.name,
            onValueChange = { model.putDraft(draft.copy(name = it)) },
            singleLine = true,
            textStyle = nameStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
        val clockText = draft.pastDate?.let { "Logging for " + prettyDate(it) } ?: clock((nowMillis() - draft.startMillis) / 1000)
        MorphText(androidx.compose.ui.text.AnnotatedString(clockText), "clock", onPage = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The workout has no menu of its own: the tag, always optional, is set down quietly beside the note, on one line.
        NoteLine(draft.note, { model.putDraft(draft.copy(note = it)) }) { TagLine(model, draft.tag) { model.putDraft(draft.copy(tag = it)) } }
    }
}

/**
 * Focus mode: the workout one exercise to a page, swiped between, with a last page for adding an exercise or cancelling.
 * A page is the exercise's next set in large type with a Done button, over the exercise's ordinary card.
 * Reordering by dragging is the list's; here the card's menu still moves an exercise.
 */
@Composable
private fun FocusPages(
    model: AppModel, workouts: List<Workout>, draft: Draft, keys: List<String>, pad: Pad, next: Int?, pager: PagerState,
    onUndo: (String, () -> Unit) -> Unit, onChange: (Int, DraftBlock, DraftBlock?) -> Unit,
    onAdd: () -> Unit, onCancel: (() -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        WorkoutTitle(model, draft)
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { keys.getOrNull(it) ?: "end" }) { page ->
            val block = draft.blocks.getOrNull(page)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (block != null) {
                    val slot = remember { FocusSlot() }
                    FocusHeader(model, block, slot.info, page + 1, draft.blocks.size)
                    val last = draft.blocks.lastIndex
                    val isNext = next == page
                    val reorder = remember(page, last, isNext) {
                        Reorder(
                            collapsed = false, lifted = false, canUp = page > 0, canDown = page < last, move = { model.moveExercise(page, it) },
                            start = {}, drag = {}, end = {}, held = false, offset = { 0f }, next = isNext, superset = { model.toggleSuperset(page) },
                        )
                    }
                    BlockCard(model, workouts, block, reorder, keys[page], pad, Modifier, onSwitch = { model.switchVariation(page, it) }, onUndo = onUndo, slot = slot) { changed -> onChange(page, block, changed) }
                }
                Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    QuietButton("Add exercise", onAdd, Modifier.fillMaxWidth())
                    if (block == null && onCancel != null) DangerButton("Cancel workout", onCancel, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}

/** The top of a focus page: the exercise, which set is next, its weight × reps in large type, last time, and Done. */
@Composable
private fun FocusHeader(model: AppModel, block: DraftBlock, info: FocusInfo?, position: Int, count: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Caption("Exercise $position of $count")
        Text(
            block.exercise,
            Modifier.clickable { model.open(Screen.ExerciseDetail(block.exercise, tab = WORKOUT_EXERCISE_TAB)) }.minTouch().wrapContentHeight(Alignment.CenterVertically).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
        )
        if (info == null) {
            Text(if (block.items.any { it is DraftSet }) "All sets done" else "No sets yet", Modifier.padding(vertical = Space.m), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text("Set ${info.number} of ${info.total}${info.side?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @Composable
        fun Value(text: String, typed: Boolean, unit: String, reps: Boolean, spoken: String) {
            Row(Modifier.clickable(onClickLabel = "Edit") { info.onEdit(reps) }.minTouch().semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.Bottom) {
                Text(
                    text.ifEmpty { NONE }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (typed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lightAlpha(0.5f, 0.9f)),
                )
                if (unit.isNotEmpty()) Text(" $unit", Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Value(if (info.weight.isEmpty()) "" else info.weightSign + info.weight, info.weightTyped, info.weightUnit, false, "Weight, ${if (info.weight.isEmpty()) "empty" else "${info.weightSign}${info.weight}"} ${info.weightUnit}")
            Text("  ×  ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Value(info.reps, info.repsTyped, info.repsUnit, true, "${if (info.repsUnit.isEmpty()) "Reps" else "Time"}, ${info.reps.ifEmpty { "empty" }} ${info.repsUnit.ifEmpty { "reps" }}")
        }
        info.last?.let { Text("Last time $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // The same tick as the row's own box; a taller button when one-handed.
        MainButton(
            "Done", info.onDone,
            Modifier.fillMaxWidth().padding(top = Space.s).heightIn(min = 56.dp).semantics { contentDescription = "Done, ${info.spoken}" },
        )
    }
}

/** How deep the zone at the list's top is: a finger inside it scrolls the list up, faster the nearer the edge (round 33: 72 dp before). */
val REORDER_TOP_ZONE = 56.dp
/** How deep the zone at the bottom of what is visible is (round 33: 72 dp before, measured from the list's full height, under the number pad and the rest bar). */
val REORDER_BOTTOM_ZONE = 40.dp

/**
 * The scroll push for a held card whose finger is [fingerY] from the list's top: -1 to 1, negative up. [visibleBottom] is where the list's end is
 * really seen: its height less the number pad and the rest bar that float over it, not its full height.
 */
fun reorderPush(fingerY: Float, visibleBottom: Float, topZone: Float, bottomZone: Float): Float = when {
    fingerY < topZone -> -(topZone - fingerY) / topZone
    fingerY > visibleBottom - bottomZone -> (fingerY - (visibleBottom - bottomZone)) / bottomZone
    else -> 0f
}.coerceIn(-1f, 1f)

/** What a card needs to be moved among the others: from its menu, or by holding its name and dragging. */
private class Reorder(
    val collapsed: Boolean, val lifted: Boolean, val canUp: Boolean, val canDown: Boolean,
    /** [start] is given how far under the card's top the finger took hold, when the card knows. */
    val move: (Int) -> Unit, val start: (Float?) -> Unit, val drag: (Float) -> Unit, val end: () -> Unit,
    /** The card is the one picked up, or still settling after it was let go, and how far it is drawn from its slot. */
    val held: Boolean, val offset: () -> Float,
    /** Whether this card holds the set to do next, and how to link it with the card below as a superset. */
    val next: Boolean, val superset: () -> Unit,
)

/** What a set is read against: its variation's unit, way of logging, sessions and PRs. */
class SetContext(
    val unit: String, val loadType: LoadType, val sessions: List<clean.core.Session>, val sided: SideRecords, val bests: SidedBests,
    /** What its progression scheme (if it has one) aims for in each working set, from the last session; the left half's in a left and right card. */
    val targets: List<Target> = emptyList(),
    /** The right half's targets in a left and right card, from the last session's right sets. */
    val rightTargets: List<Target> = emptyList(),
) {
    /** The comparable figure: for each record the weaker side's (the only figure of an ordinary exercise). */
    val records: ExerciseRecords get() = sided.comparable

    /** One step of weight for this exercise: the smallest jump ever logged, or 5 for pounds. Worked out once, when first asked for. */
    val step: Double by lazy { if (unit == "lb") 5.0 else weightStep(sessions) }
}

/** What one [SetContext] was made from; the context is the current one while all of these are the same. */
private class SetContextEntry(
    val workouts: List<Workout>, val rule: OneRm, val bodyweights: clean.core.Bodyweights, val scheme: Progression?, val paired: Boolean,
    val loadType: LoadType, val unit: String, val context: SetContext,
)

/**
 * What the sets of [exercise] (a card's own or a variation in it) are read against, kept per exercise in the model: the cards of a workout asked for
 * it on the main thread when the page opened. [AppModel] works the ones of a workout's cards out ahead, off the main thread, as the workout changes.
 */
fun AppModel.setContextFor(
    exercise: String, workouts: List<Workout>, rule: OneRm, bodyweights: clean.core.Bodyweights, scheme: Progression?, paired: Boolean,
): SetContext {
    val type = loadTypeOf(exercise)
    val unit = unitOf(exercise)
    (setContextCache[exercise] as? SetContextEntry)?.let { e ->
        if (e.workouts === workouts && e.rule == rule && e.bodyweights === bodyweights && e.scheme == scheme && e.paired == paired && e.loadType == type && e.unit == unit) return e.context
    }
    val done = sessionsIn(workouts, exercise)
    val last = done.lastOrNull()
    // Left and right: each side aims from its own last sets, and the weight goes up when both reached the top.
    val (left, right) = scheme?.let { sc ->
        if (paired) nextForSides(last?.forSide(Side.LEFT)?.sets.orEmpty(), last?.forSide(Side.RIGHT)?.sets.orEmpty(), sc, type)
        else nextFor(carriedSets(last, false, Side.LEFT), sc, type) to emptyList()
    } ?: (emptyList<Target>() to emptyList())
    val made = SetContext(unit, type, done, recordsBySide(done, rule, type, bodyweights::at), prBestsBySide(done, type, bodyweights::at, rule), left, right)
    setContextCache[exercise] = SetContextEntry(workouts, rule, bodyweights, scheme, paired, type, unit, made)
    return made
}

/** A variation's name without the overall exercise in front: `Pull Up (Wide Grip)` is `Wide Grip`. */
fun shortVariation(name: String, overall: String): String {
    val rest = name.removePrefix(overall).trim().removePrefix("-").trim()
    return when {
        rest.isEmpty() -> "Standard"
        rest.startsWith("(") && rest.endsWith(")") && rest.count { it == '(' } == 1 -> rest.substring(1, rest.length - 1)
        else -> rest
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockCard(model: AppModel, workouts: List<Workout>, block: DraftBlock, reorder: Reorder, id: String, pad: Pad, modifier: Modifier, onSwitch: (String) -> Unit, onUndo: (String, () -> Unit) -> Unit, slot: FocusSlot? = null, onChange: (DraftBlock?) -> Unit) {
    // The workout this card belongs to: the one in progress, or a finished one on the Correct page.
    val owner = LocalDraftSlot.current ?: model.liveSlot
    val correcting = owner.correcting
    // Keyed on the settings so the card redraws when the exercise's unit, load type or sticky note is saved.
    val settings by model.settings.collectAsState()
    val unit = remember(settings, block.exercise) { model.unitOf(block.exercise) }
    val loadType = remember(settings, block.exercise) { model.loadTypeOf(block.exercise) }
    val sticky = settings[block.exercise]?.note.orEmpty()
    val stickyColor by model.stickyColor.collectAsState()
    val auto by model.auto.collectAsState()
    val sessions = remember(workouts, block.exercise) { model.sessionsIn(workouts, block.exercise) }
    val previous = carriedSets(sessions.lastOrNull(), block.paired, Side.LEFT)
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val schemes by model.progressions.collectAsState()
    // Done left and right: the sets are drawn as pairs, one row each, though they are stored as ordinary sets (the card's own marker).
    val paired = block.paired
    val sided = remember(sessions, loadType, bodyweights, rule) {
        model.cachedExerciseStats(block.exercise, workouts, loadType, bodyweights, rule)?.sided ?: recordsBySide(sessions, rule, loadType, bodyweights::at)
    }
    val records = sided.comparable
    // Today's bodyweight, if there is a weigh-in close enough; a bodyweight or assisted lift needs it.
    val bodyweight = remember(bodyweights) { bodyweights.at(strongDate(currentMillis())) }
    var menu by remember { mutableStateOf(false) }
    var editingSticky by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var explaining by remember { mutableStateOf(false) }
    var progressing by remember { mutableStateOf(false) }
    val variations = remember(settings, block.exercise) { model.variationsOf(block.exercise) }
    val overall = remember(settings, block.exercise) { model.overallOf(block.exercise) }
    // Every variation in this card, the card's own first, each with what its sets are read against.
    val names = remember(block) { (listOf(block.exercise) + block.items.filterIsInstance<DraftSet>().mapNotNull { it.exercise }).distinct() }
    val contexts = remember(settings, workouts, bodyweights, names, rule, schemes, paired) {
        names.associateWith { n -> model.setContextFor(n, workouts, rule, bodyweights, schemes[n], paired) }
    }
    // The variations done before or in use in this card, worked out once for the chips and every set's menu.
    val quick = remember(variations, names, workouts) { variations.filter { model.hasHistory(it) || it in names } }
    val reorderNow by rememberUpdatedState(reorder)
    val finished = block.items.any { it is DraftSet } && block.items.all { it !is DraftSet || it.done }
    var unfolded by remember(finished) { mutableStateOf(false) }
    val compact = model.flag("workout.compact")
    val focusManager = LocalFocusManager.current
    // The set to do next, when it is in this card.
    val nextItem = if (reorder.next) block.items.indexOfFirst { it is DraftSet && !it.done } else -1
    // The one highlight on the next set, gliding from row to row; and where a set has just come into the card (Undo, Add set).
    val skin = workoutSkin()
    val nextGlide = remember { Glide(nextItem) }
    val glideMotion = LocalMotion.current
    LaunchedEffect(nextItem, glideMotion, block.items.size) { nextGlide.go(nextItem, glideMotion) }
    // When a rest runs out the next set's row glows once in the accent (in over 150 ms, out over 600); the rest panel's edge light is its own.
    val glowColour = LocalLook.current.orange
    val ranOutBefore = remember { model.restRanOut }
    LaunchedEffect(model.restRanOut) { if (model.restRanOut != ranOutBefore && nextItem >= 0 && glideMotion) nextGlide.flash() }
    val arrivals = remember { Arrivals(block.items) }
    remember(block.items) { arrivals.update(block.items) }
    fun rowAt(index: Int) = Modifier.onGloballyPositioned { nextGlide.rows[index] = it }
    var resting by remember { mutableStateOf(false) }
    var warming by remember { mutableStateOf(false) }
    var platesFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    /** The set the stopwatch sheet is open for, and the seconds it took last time. */
    var timing by remember { mutableStateOf<Pair<Int, Int?>?>(null) }

    /**
     * Lays out the preferred working and drop sets in place of the sets not ticked yet, filled with
     * the weight and reps to aim for: one that beats the best estimated 1RM, from the best set of
     * the last session. Warm-ups come first, starting at half the working weight and stepping up
     * evenly; a drop set is a fifth lighter. With no history the sets are left empty.
     */
    fun autoFill() {
        val last = sessions.lastOrNull()
        val ctx = contexts.getValue(block.exercise)
        val step = weightStep(sessions)
        /** What one side's sets are laid out with: the weight and reps to aim for, a drop's weight, the warm-up weights, and the scheme's targets. */
        class Plan(val weight: String, val reps: String, val dropWeight: String, val warm: List<String>, val progressed: List<Target>)
        fun plan(series: List<clean.core.SetItem>, e1rm: Double?, progressed: List<Target>): Plan {
            val best = series
                .filter { it.type != SetType.WARMUP && it.type != SetType.DROP && it.weight != null && (it.reps ?: 0) >= 1 }
                .maxByOrNull { it.weight!! * (1 + it.reps!! / 30.0) }
            if (best == null || loadType == LoadType.TIMED || loadType == LoadType.CARDIO) return Plan("", "", "", List(auto.third) { "" }, progressed)
            val from = Target(best.weight!!, best.reps!!)
            // Only a plain loaded lift goes up in weight by rule; where bodyweight is part of the load it is one more rep.
            val target = progressed.firstOrNull() ?: if (loadType == LoadType.NORMAL) nextTarget(from, e1rm, step, rule) else Target(from.weight, from.reps + 1)
            return Plan(
                entryWeight(target.weight, unit), target.reps.toString(),
                entryWeight(if (loadType == LoadType.NORMAL) roundHalfUp(target.weight * 0.8 / step) * step else target.weight, unit),
                // One warm-up is half the working weight; more of them step up evenly from there.
                List(auto.third) { i -> entryWeight(if (loadType == LoadType.NORMAL) roundHalfUp(target.weight * (0.5 + 0.5 * i / auto.third) / step) * step else target.weight, unit) },
                progressed,
            )
        }
        // With a progression scheme its targets set the working sets, set by set (the last one repeats if the layout has more sets).
        // Left and right: each side is laid out from its own last sets and its own targets.
        val left = if (paired) plan(last?.forSide(Side.LEFT)?.sets.orEmpty(), sided.left.estimated1Rm?.value, ctx.targets)
        else plan(carriedSets(last, false, Side.LEFT), records.estimated1Rm?.value, ctx.targets)
        val right = if (paired) plan(last?.forSide(Side.RIGHT)?.sets.orEmpty(), sided.right.estimated1Rm?.value, ctx.rightTargets) else left
        model.message = if (left.weight.isEmpty() && right.weight.isEmpty()) "Nothing to go on yet, so the sets are empty"
        else if (!paired || (left.weight == right.weight && left.reps == right.reps)) "Aiming for ${left.weight} $unit × ${left.reps}"
        else "Aiming for ${left.weight} $unit × ${left.reps} left, ${right.weight} $unit × ${right.reps} right"
        // Left and right: a pair with either side ticked stays whole, and each layout set is laid out as a pair.
        val holding = if (paired) sidePairs(block.items.map { it is DraftSet }).filter { (l, r) -> listOfNotNull(l, r).any { (block.items[it] as DraftSet).done } }.flatMap { listOfNotNull(it.first, it.second) }.toSet() else emptySet()
        val kept = block.items.filterIndexed { i, it -> it !is DraftSet || it.done || i in holding }
        fun round(l: DraftSet, r: DraftSet) = if (paired) listOf(l, r) else listOf(l)
        fun working(p: Plan, i: Int) = p.progressed.getOrNull(minOf(i, p.progressed.lastIndex))?.let { DraftSet(SetType.NORMAL, entryWeight(it.weight, unit), it.reps.toString()) } ?: DraftSet(SetType.NORMAL, p.weight, p.reps)
        // Sets already filled in by hand are replaced; they can be put back for a few seconds.
        val before = owner.draft.takeIf { block.items.any { it is DraftSet && !it.done && (it.weight.isNotEmpty() || it.reps.isNotEmpty()) } }
        onChange(block.copy(items = kept +
            List(auto.third) { i -> round(DraftSet(SetType.WARMUP, left.warm[i], left.reps), DraftSet(SetType.WARMUP, right.warm[i], right.reps)) }.flatten() +
            List(auto.first) { i -> round(working(left, i), working(right, i)) }.flatten() +
            List(auto.second) { round(DraftSet(SetType.DROP, left.dropWeight, left.reps), DraftSet(SetType.DROP, right.dropWeight, right.reps)) }.flatten()))
        if (before != null) onUndo("Sets replaced") { owner.put(before) }
    }

    fun replace(index: Int, item: DraftItem?) {
        val items = block.items.toMutableList()
        if (item == null) items.removeAt(index) else items[index] = item
        onChange(block.copy(items = items))
    }

    val linked = MaterialTheme.colorScheme.tertiary
    // 1 while picked up, 0 otherwise: the card grows a little and throws a shadow.
    val lift by animateFloatAsState(if (reorder.lifted) 1f else 0f, if (LocalMotion.current) Motion.short() else snap(), label = "lift")
    val liftShadow = with(LocalDensity.current) { 8.dp.toPx() }
    val card = cardShape()
    // Where the card and its name are laid out, to know how far under the card's top a finger took hold of the name.
    // Plain holders, not state: nothing is redrawn when the card scrolls.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(2) }
    val nameSource = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth()
            // The held card is drawn at the finger, over its neighbours.
            .zIndex(if (reorder.held) 1f else 0f)
            .graphicsLayer { translationY = if (reorder.held) reorder.offset() else 0f }
            .onGloballyPositioned { placed[0] = it }
            .padding(top = Space.s, bottom = Space.s)
            // The exercises of a superset are joined by a line down their left edge.
            .drawBehind { if (block.superset != null) drawRect(linked, Offset(4.dp.toPx(), 0f), Size(3.dp.toPx(), size.height)) },
    ) {
        if (block.superset != null) Caption("Superset", Modifier.padding(start = Space.l))
        // A swipe on the name removes the exercise (with Undo) in the direction the set swipes use for removing; not in focus mode,
        // where a sideways swipe turns the page. It stays in place while the cards are folded to reorder: the hold owns the finger
        // then, and taking the swipe's detector away mid-hold would end the hold.
        val feel = LocalFeel.current
        val removeSwipe = SwipeAction("Remove", CleanIcons.Delete, LocalCleanColors.current.danger, true) { if (!reorderNow.collapsed) onChange(null) }
        val swipeLeft = removeSwipe.takeIf { removesExercise(feel.swipeLeft) && slot == null }
        val swipeRight = removeSwipe.takeIf { removesExercise(feel.swipeRight) && slot == null }
        OptionalSwipe(swipeLeft, swipeRight) {
        Row(
            Modifier.then(if (reorder.collapsed) Modifier.height(52.dp).padding(horizontal = Space.s) else Modifier)
                .graphicsLayer { scaleX = 1f + 0.03f * lift; scaleY = scaleX; shadowElevation = liftShadow * lift; shape = card }
                .then(if (reorder.collapsed) Modifier.background(if (reorder.lifted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, card) else Modifier)
                .padding(start = Space.l, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.exercise,
                Modifier.weight(1f).onGloballyPositioned { placed[1] = it }.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(onDragStart = { touch ->
                        val card = placed[0]?.takeIf { it.isAttached }
                        val name = placed[1]?.takeIf { it.isAttached }
                        reorderNow.start(if (card != null && name != null) card.localPositionOf(name, touch).y else null)
                    }, onDragEnd = { reorderNow.end() }, onDragCancel = { reorderNow.end() }) { change, amount ->
                        change.consume()
                        reorderNow.drag(amount.y)
                    }
                // No ripple while the cards are folded for reordering: the press that picked the card up would stay drawn behind the name, and its release is not a tap.
                }.clickable(nameSource, if (reorder.collapsed) null else LocalIndication.current) { if (!reorderNow.collapsed) model.open(Screen.ExerciseDetail(block.exercise, tab = WORKOUT_EXERCISE_TAB)) },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { menu = true }) { Icon(CleanIcons.More, "Exercise options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        }
        if (menu) Sheet({ menu = false }, block.exercise) {
            // A note goes above the sets it is about; a card already opening with one gets no second.
            SheetRow("Add note", CleanIcons.Notes) {
                menu = false
                if (block.items.firstOrNull() !is DraftNote) onChange(block.copy(items = listOf(DraftNote()) + block.items))
            }
            // Tapping the note itself edits it, so the menu only needs to add one or take it away.
            if (sticky.isEmpty()) SheetRow("Add sticky note", CleanIcons.Pin) { menu = false; editingSticky = true }
            else SheetRow("Remove sticky note", CleanIcons.Pin) {
                menu = false
                val old = sticky
                model.putSetting(block.exercise) { it.copy(note = "") }
                onUndo("Sticky note removed") { model.putSetting(block.exercise) { it.copy(note = old) } }
            }
            SheetRow("Auto-fill sets", CleanIcons.Bolt) { menu = false; autoFill() }
            SheetRow("Warm-up sets", CleanIcons.Up) { menu = false; warming = true }
            SheetRow("Progression", CleanIcons.Up, value = schemes[block.exercise]?.let { "${it.minReps}–${it.maxReps} reps" } ?: "Off") { menu = false; progressing = true }
            SheetRow("Left and right", CleanIcons.LeftRight, value = if (paired) "On" else "Off") {
                menu = false
                val before = owner.draft
                val was = paired
                model.setSides(block.exercise, !was)
                onUndo(if (was) "Left and right off" else "Left and right on") { model.setSides(block.exercise, was); owner.put(before) }
            }
            // Correcting a finished workout has no rest to time.
            if (!correcting) SheetRow("Rest timer", CleanIcons.TimerLine, value = restLabel(model.restFor(block.exercise))) { menu = false; resting = true }
            SheetGap()
            SheetRow("Switch variation", CleanIcons.Swap) { menu = false; switching = true }
            SheetRow("Replace exercise", CleanIcons.Refresh) { menu = false; replacing = true }
            SheetRow("Move up", CleanIcons.Up, enabled = reorder.canUp) { menu = false; reorder.move(-1) }
            SheetRow("Move down", CleanIcons.Down, enabled = reorder.canDown) { menu = false; reorder.move(1) }
            SheetRow(if (block.superset == null) "Superset with the next exercise" else "Take out of the superset", CleanIcons.Link, enabled = block.superset != null || reorder.canDown) { menu = false; reorder.superset() }
            SheetGap()
            SheetRow("Remove exercise", CleanIcons.Delete, danger = true) { menu = false; onChange(null) }
        }
        // While an exercise is being dragged only the names show; the rest folds away and unfolds again.
        val fold = LocalMotion.current
        AnimatedVisibility(
            !reorder.collapsed,
            enter = Motion.appear(fold),
            exit = Motion.vanish(fold),
        ) {
        Column {
            // An exercise with every set ticked takes little room: its sets in a line or two, opening up again when touched.
            val folded = compact && finished && !unfolded
            AnimatedVisibility(folded, enter = Motion.foldIn(fold, Motion.FOLD / 3), exit = Motion.foldOut(fold)) {
                // One short line per set, its number or letter in the set type's colour as in the full rows.
                Column(Modifier.fillMaxWidth().clickable { unfolded = true }.padding(horizontal = Space.l, vertical = Space.xxs)) {
                    var n = 0
                    for (set in block.items.filterIsInstance<DraftSet>()) {
                        if (set.type.numbered) n++
                        val c = contexts[set.exercise ?: block.exercise] ?: contexts.getValue(block.exercise)
                        Row {
                            Text(if (set.type == SetType.NORMAL) n.toString() else set.type.letter, Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall, color = typeColor(set.type), fontWeight = FontWeight.Bold)
                            Text(
                                when (c.loadType) {
                                    LoadType.CARDIO -> "${set.weight} km, ${set.reps} min"
                                    LoadType.TIMED -> "${set.weight.ifEmpty { "0" }} ${c.unit}, ${set.reps} s"
                                    else -> "${set.weight.ifEmpty { "0" }} ${c.unit} × ${set.reps}"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
                }
            }
            if (folded && slot != null) SideEffect { slot.info = null }
            AnimatedVisibility(!folded, enter = Motion.foldIn(fold), exit = Motion.foldOut(fold)) {
            CompositionLocalProvider(LocalSwipeWatch provides nextGlide::watch, LocalNextGlide provides nextGlide) {
            Column(Modifier.onGloballyPositioned { nextGlide.container = it }.drawBehind { nextGlide.draw(this, skin.rowNext, glowColour) }) {
            // A goal not reached yet, quietly under the name; it leads to the goal on the exercise's page.
            val goal = remember(settings, block.exercise) { parseGoal(settings[block.exercise]?.goal) }
            val goalMet = remember(sessions, goal, loadType, bodyweights, rule) { goal?.let { (kg, reps) -> goalReachedOn(sessions, kg, reps, rule, loadType, bodyweights::at) } }
            if (goal != null && goalMet == null) {
                val goalShare = remember(sessions, goal, loadType, bodyweights, rule) { exerciseGoalProgress(sessions, goal.first, goal.second, rule, loadType, bodyweights::at, LocalDate.now())?.fraction }
                val sign = when (loadType) { LoadType.BODYWEIGHT -> "+"; LoadType.ASSISTED -> "−"; else -> "" }
                Text(
                    "Goal $sign${showWeight(goal.first, unit)} × ${goal.second}" + (goalShare?.let { " · ${roundHalfUp(it * 100)} %" } ?: ""),
                    Modifier.clickable { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) }.padding(horizontal = Space.l, vertical = Space.xxs),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // With no progression scheme of its own, one quiet line of what to try: last time's best set and the set that
            // beats the best estimated 1RM (reps first, then weight, as auto-fill aims). It leads to setting a scheme.
            if (!correcting && model.flag("workout.hint") && !paired && schemes[block.exercise] == null && (loadType == LoadType.NORMAL || loadType == LoadType.BODYWEIGHT)) {
                val hint = remember(sessions, records, loadType) {
                    val best = carriedSets(sessions.lastOrNull(), false, Side.LEFT)
                        .filter { it.type != SetType.WARMUP && it.type != SetType.DROP && it.weight != null && (it.reps ?: 0) >= 1 }
                        .maxByOrNull { it.weight!! * (1 + it.reps!! / 30.0) } ?: return@remember null
                    val from = Target(best.weight!!, best.reps!!)
                    from to if (loadType == LoadType.NORMAL) nextTarget(from, records.estimated1Rm?.value, weightStep(sessions), rule) else Target(from.weight, from.reps + 1)
                }
                if (hint != null) {
                    fun text(t: Target) = "${if (loadType == LoadType.BODYWEIGHT) "+" else ""}${entryWeight(t.weight, unit)} $unit × ${t.reps}"
                    Text(
                        "Last time ${text(hint.first)}. Try ${text(hint.second)}",
                        Modifier.clickable { progressing = true }.padding(horizontal = Space.l, vertical = Space.xxs),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The variations done before, a tap away: the sets not ticked yet carry on as the one tapped.
            val current = block.items.filterIsInstance<DraftSet>().let { sets -> (sets.firstOrNull { !it.done } ?: sets.lastOrNull())?.exercise } ?: block.exercise
            // With only one variation done there is nothing to switch between, so no row: "Switch variation" in the exercise's menu is the way to another or a new one.
            if (quick.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (v in quick) CleanChip(selected = v == current, onClick = { if (v != current) onSwitch(v) }, label = { Text(shortVariation(v, overall)) })
                // The way to another variation, or to a new one, without going through the menu.
                CleanChip(selected = false, onClick = { switching = true }, label = { Text("+") })
            }
            if (sticky.isNotEmpty()) {
                // A name from the list, or a colour of the user's own; the text turns dark on a light band.
                val band = stickyBand(stickyColor, LocalLook.current.dark)
                Text(
                    sticky, fontWeight = FontWeight.Bold, color = band.readableOn(),
                    modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs).fillMaxWidth().clip(cardShape()).background(band).clickable { editingSticky = true }.padding(horizontal = Space.l, vertical = Space.s),
                )
            }
            val cardio = loadType == LoadType.CARDIO
            val timed = loadType == LoadType.TIMED
            if ((loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED) && bodyweight == null) {
                Column(Modifier.padding(horizontal = Space.l)) {
                    Text(
                        "No weigh-in in the last $BODYWEIGHT_WINDOW_DAYS days. Without one these sets get no volume, estimated 1RM or PRs beyond the weight itself.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                    QuietButton("Log weight now", { model.logWeight() })
                }
            }
            Row(Modifier.padding(start = Space.s, end = Space.m, top = Space.s, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Caption("Set", Modifier.width(44.dp).clickable { explaining = true }, TextAlign.Center)
                if (paired) {
                    // Two halves with the boxes' units under their names; the previous values sit under each half's boxes.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SIDES_GAP)) {
                        for (side in listOf("Left", "Right")) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Caption(side)
                            Text(
                                if (cardio) "km × min" else if (timed) "$unit × sec" else "$unit × reps", maxLines = 1,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Caption("Previous", Modifier.weight(1f), TextAlign.Center)
                    Caption(if (cardio) "km" else unit, Modifier.width(84.dp), TextAlign.Center)
                    Caption(if (cardio) "min" else if (timed) "sec" else "reps", Modifier.width(84.dp), TextAlign.Center)
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { Icon(CleanIcons.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // How many sets of each variation have come so far, to line each up with its own last session.
            val seen = HashMap<String, Int>()
            // And of working sets, to line each up with its own target.
            val worked = HashMap<String, Int>()
            // Each set's view as it was drawn, so a tick can read the hints of an earlier set (the half it completes).
            val views = HashMap<Int, SetView>()
            var shownName = block.exercise
            var position = 0
            var lastSet: DraftSet? = null
            // Left and right: each left set with its right, and the rights, which are drawn with the left before them.
            val rightOf = if (paired) sidePairs(block.items.map { it is DraftSet }).mapNotNull { (l, r) -> r?.let { l to it } }.toMap() else emptyMap()
            val rights = rightOf.values.toSet()
            val leftOf = rightOf.entries.associate { (l, r) -> r to l }
            val live = liveRecords(block, contexts, bodyweight, rule)
            // A set ticked now that earns a new estimated 1RM gets a chime and a heavy buzz, once (once per round when either half does);
            // those that had already earned one when the card appeared are not celebrated again.
            val celebrated = remember { live.filterValues { PrKind.ONE_RM in it }.keys.map { leftOf[it] ?: it }.toMutableSet() }
            val motion = LocalMotion.current
            LaunchedEffect(live) {
                val fresh = live.filterValues { PrKind.ONE_RM in it }.keys.map { leftOf[it] ?: it }.toSet().filter { celebrated.add(it) }
                if (fresh.isNotEmpty() && motion) {
                    model.buzz(Haptic.Heavy)
                    model.platform.chime()
                }
            }
            var setNumber = 0
            // Focus mode: the first set not ticked (a half of a pair, or a set), for the page above the card.
            var focusInfo: FocusInfo? = null
            val rowsTotal = block.items.count { it is DraftSet } - rights.size
            fun focusOf(v: SetView, side: String?, set: DraftSet, last: String?, onEdit: (Boolean) -> Unit, onDone: () -> Unit) = FocusInfo(
                setNumber, rowsTotal, side, set.weight.ifEmpty { v.hintWeight }, set.reps.ifEmpty { v.hintReps }, set.weight.isNotEmpty(), set.reps.isNotEmpty(),
                if (v.c.loadType == LoadType.CARDIO) "km" else v.c.unit, when (v.c.loadType) { LoadType.CARDIO -> "min"; LoadType.TIMED -> "s"; else -> "" },
                last, v.spoken + (side?.let { " " + it.lowercase() } ?: ""), onEdit, onDone,
                when (v.c.loadType) { LoadType.BODYWEIGHT -> "+"; LoadType.ASSISTED -> "−"; else -> "" },
            )
            /**
             * What the set at [index] is read against and shows, counting it in the numbering unless it is the [partner]
             * a pair's left set was counted with, whose target it shares.
             */
            fun viewOf(index: Int, item: DraftSet, partner: SetView? = null, other: DraftSet? = null): SetView {
                if (partner == null) {
                    setNumber++
                    // A round is numbered unless every half it has is a warm-up; a round whose halves differ shows its number, each half its own letter.
                    if (roundCounts(item.type, other?.type)) position++
                }
                // Each set is read against its own variation: its unit, how it is logged, its last session and its PRs.
                val name = item.exercise ?: block.exercise
                val c = contexts[name] ?: contexts.getValue(block.exercise)
                // Left and right: round k reads against last time's own side of round k (a session of ordinary sets gives both halves its set k);
                // an ordinary card reads a left and right session as one set per round, the heavier side's (carriedSets).
                val lastSession = c.sessions.lastOrNull()
                val series = carriedSets(lastSession, paired, if (partner == null) Side.LEFT else Side.RIGHT)
                val before = series.getOrNull(if (partner == null) (seen[name] ?: 0) else (seen[name] ?: 1) - 1)
                if (partner == null) seen[name] = (seen[name] ?: 0) + 1
                val shown = name != shownName
                shownName = name
                // What an empty box will take when the set is ticked: last time's value, else the set above.
                // For cardio the boxes are kilometres and minutes; for a timed hold, weight and seconds.
                val cardio = c.loadType == LoadType.CARDIO
                val timed = c.loadType == LoadType.TIMED
                val lastWeight = if (cardio) before?.distance?.let { shortNumber(it / 1000) } else before?.weight?.let { entryWeight(it, c.unit) }
                val lastReps = if (cardio) before?.seconds?.let { shortNumber(it / 60) } else if (timed) before?.seconds?.let { shortNumber(it) } else before?.reps?.toString()
                // With a progression scheme, a working set's boxes show what to aim for, set by set, in place of last time.
                // Left and right: each half aims for its own side's target of last time.
                val aim = if (item.type != SetType.NORMAL && item.type != SetType.FAILURE) null
                else if (partner != null) c.rightTargets.getOrNull((worked[name] ?: 1) - 1)
                else c.targets.getOrNull((worked[name] ?: 0)).also { worked[name] = (worked[name] ?: 0) + 1 }
                // Without a scheme, a workout started from a template shows the template's value for this set in place of last time's. Left and right: one value per pair, on both halves.
                val planned = if (c.loadType != LoadType.CARDIO && schemes[name] == null) block.targets.getOrNull(block.items.take(index).count { it is DraftSet } / if (paired) 2 else 1) else null
                val hintWeight = aim?.let { entryWeight(it.weight, c.unit) } ?: planned?.weightKg?.let { entryWeight(it, c.unit) } ?: lastWeight ?: lastSet?.weight.orEmpty()
                val hintReps = aim?.reps?.toString() ?: planned?.reps?.toString() ?: lastReps ?: lastSet?.reps.orEmpty()
                // The box of this set the number pad is on, if any; the pad is told its step and what the empty box stands for.
                val target = pad.target?.takeIf { it.card == id && it.item == index }
                // The same box of the set above, for the pad's "Same" key.
                // In a left and right card, a side with no value in that box (or no such side) hands over to the other side of the round above.
                val aboveSets = target?.let { setsAbove(block.items.map { x -> x is DraftSet }, index, paired) }.orEmpty().mapNotNull { block.items[it] as? DraftSet }
                val meta = target?.let {
                    if (it.reps) PadMeta(1.0, hintReps, cardio || timed, aboveSets.map { s -> s.reps }.firstOrNull { v -> v.isNotEmpty() }.orEmpty())
                    else PadMeta(if (cardio) 0.5 else c.step, hintWeight, true, aboveSets.map { s -> s.weight }.firstOrNull { v -> v.isNotEmpty() }.orEmpty())
                }
                lastSet = item
                return SetView(
                    index, item, name, c, (roundKind(item.type, other?.type) ?: SetType.NORMAL).let { if (it == SetType.NORMAL) position.toString() else it.letter }, "Set $setNumber",
                    before, aim, lastWeight, lastReps, hintWeight, hintReps, if (target == null) 0 else if (target.reps) 2 else 1, meta, shown,
                ).also { views[index] = it }
            }
            block.items.forEachIndexed { index, item ->
                when (item) {
                    is DraftNote -> {
                        position = 0
                        TextField(
                            value = item.text,
                            onValueChange = { replace(index, DraftNote(it)) },
                            placeholder = { Text("Write a note") },
                            shape = SmallShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.xs),
                            trailingIcon = { IconButton(onClick = { replace(index, null) }) { Icon(CleanIcons.Close, "Remove note") } },
                        )
                    }
                    is DraftSet -> {
                        // The right half of a pair is drawn with the left before it.
                        if (index in rights) return@forEachIndexed
                        val v = viewOf(index, item, other = rightOf[index]?.let { block.items[it] as DraftSet })
                        val name = v.name
                        val c = v.c
                        val unit = c.unit
                        val loadType = c.loadType
                        val records = c.records
                        val cardio = loadType == LoadType.CARDIO
                        val timed = loadType == LoadType.TIMED
                        val before = v.before
                        val aim = v.aim
                        val lastWeight = v.lastWeight
                        val lastReps = v.lastReps
                        val hintWeight = v.hintWeight
                        val hintReps = v.hintReps
                        if (v.newVariation) {
                            Text(shortVariation(name, overall), Modifier.padding(start = Space.xl, top = Space.s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        v.padMeta?.let { meta -> SideEffect { if (pad.meta != meta) pad.meta = meta } }
                        val tag = before?.type?.takeIf { it != SetType.NORMAL && it != item.type }
                        val tagColor = tag?.let { typeColor(it) } ?: Color.Unspecified
                        val rightAt = rightOf[index]
                        if (paired) {
                            val right = rightAt?.let { viewOf(it, block.items[it] as DraftSet, partner = v) }
                            right?.padMeta?.let { meta -> SideEffect { if (pad.meta != meta) pad.meta = meta } }
                            fun onBoth(change: (DraftSet) -> DraftSet) {
                                onChange(block.copy(items = block.items.mapIndexed { i, other -> if (i == index || i == rightAt) change(other as DraftSet) else other }))
                            }
                            // One half: its boxes and tick, and last time's matching set as a quiet line under them.
                            fun half(side: String, w: SetView, mixed: Boolean) = Half(
                                set = w.item, side = side, spoken = "${w.spoken} ${side.lowercase()}", mixed = mixed,
                                previous = w.before?.let { setValues(it, unit) }, hintWeight = w.hintWeight, hintReps = w.hintReps,
                                highlight = w.index == nextItem, focus = w.focus, bounds = rowAt(w.index),
                                onFocus = { reps -> focusManager.clearFocus(); pad.focus(id, w.index, reps) },
                                onPrevious = if (w.before == null) null else ({ replace(w.index, w.item.copy(weight = w.lastWeight.orEmpty(), reps = w.lastReps.orEmpty())) }),
                                onTick = {
                                    val s = w.item
                                    if (s.done) replace(w.index, s.copy(done = false, rest = 0, ticked = 0))
                                    else {
                                        val taken = acceptHints(s.weight, s.reps, w.hintWeight, w.hintReps, loadType)
                                        if (taken == null) model.message = if (cardio) "Enter the minutes first" else if (timed) "Enter the seconds first" else "Enter the reps first"
                                        else {
                                            val now = currentMillis()
                                            val items = block.items.toMutableList()
                                            items[w.index] = s.copy(weight = taken.weight, reps = taken.reps, done = true, ticked = now)
                                            // Moving on: a round before this one with only one half ticked gets the other half ticked, as ticking it would.
                                            val rounds = sidePairs(block.items.map { it is DraftSet })
                                            val halves = rounds.getOrNull(rounds.indexOfFirst { it.first == w.index || it.second == w.index } - 1)?.let { listOfNotNull(it.first, it.second) }.orEmpty()
                                            val missing = halves.singleOrNull { !(block.items[it] as DraftSet).done }
                                            val gap = missing?.let { views[it] }
                                            val old = missing?.let { block.items[it] as DraftSet }
                                            val missed = if (old != null && gap != null) acceptHints(old.weight, old.reps, gap.hintWeight, gap.hintReps, gap.c.loadType) else null
                                            var auto: DraftSet? = null
                                            if (missing != null && halves.size == 2 && gap != null && old != null && missed != null) {
                                                val doneOne = block.items[halves.first { it != missing }] as DraftSet
                                                auto = old.copy(weight = missed.weight, reps = missed.reps, done = true, ticked = maxOf(now, doneOne.ticked + 1))
                                                items[missing] = auto
                                            }
                                            onChange(block.copy(items = items))
                                            if (auto != null && old != null) onUndo("Other side ticked too") {
                                                val d = owner.draft ?: return@onUndo
                                                val at = d.blocks.indexOfFirst { it.items.getOrNull(missing!!) == auto }
                                                if (at >= 0) owner.put(d.copy(blocks = d.blocks.mapIndexed { i, b -> if (i == at) b.copy(items = b.items.toMutableList().also { it[missing!!] = old }) else b }))
                                            }
                                        }
                                    }
                                },
                            )
                            val mixed = right != null && right.item.type != item.type
                            val leftHalf = half("Left", v, mixed)
                            val rightHalf = right?.let { half("Right", it, mixed) }
                            val nextHalf = if (!leftHalf.set.done) leftHalf else rightHalf?.takeIf { !it.set.done }
                            if (slot != null && focusInfo == null && nextHalf != null) focusInfo = focusOf(if (nextHalf === leftHalf) v else right!!, nextHalf.side, nextHalf.set, nextHalf.previous, nextHalf.onFocus, nextHalf.onTick)
                            PairRow(
                                label = v.label, spoken = v.spoken, type = if (mixed) null else item.type, rpe = item.rpe, unit = unit, loadType = loadType,
                                left = leftHalf, right = rightHalf,
                                // One badge row for the round: each kind once, with the side or sides that earned it.
                                records = sidesOf(live[index].orEmpty(), rightAt?.let { live[it] }.orEmpty()),
                                onRecord = {
                                    model.message = listOfNotNull(
                                        records.weight?.let { "Best weight before: ${showWeight(it.value, unit)} $unit × ${it.reps}" },
                                        records.estimated1Rm?.let { "best 1RM before: ${showWeight(roundHalfUp(it.value * 10) / 10.0, unit)} $unit" },
                                    ).joinToString(", ")
                                },
                                variations = quick.filter { it != name }.map { it to shortVariation(it, overall) },
                                onVariation = { variation -> onBoth { it.copy(exercise = variation.takeIf { it != block.exercise }) } },
                                onPlates = if ("barbell" in name.lowercase() && !cardio && !timed) ({ platesFor = item.weight.ifEmpty { hintWeight } to unit }) else null,
                                onType = { type -> onBoth { it.copy(type = type) } },
                                onHalfType = { second, type -> replace(if (second) rightAt!! else index, (block.items[if (second) rightAt!! else index] as DraftSet).copy(type = type)) },
                                // Both sets go: the swipe and the menu's twin of it.
                                onRemove = { onChange(block.copy(items = block.items.filterIndexed { i, _ -> i != index && i != rightAt })) },
                                arrival = arrivals.token(index), items = block.items,
                            )
                            return@forEachIndexed
                        }
                        val tickIt: () -> Unit = {
                            if (item.done) replace(index, item.copy(done = false, rest = 0))
                            else {
                                // Each empty box takes the grey hint it shows, then the set is ticked as if typed.
                                val taken = acceptHints(item.weight, item.reps, hintWeight, hintReps, loadType)
                                if (taken == null) model.message = if (cardio) "Enter the minutes first" else if (timed) "Enter the seconds first" else "Enter the reps first"
                                else replace(index, item.copy(weight = taken.weight, reps = taken.reps, done = true))
                            }
                        }
                        val editIt = { reps: Boolean -> focusManager.clearFocus(); pad.focus(id, index, reps) }
                        if (slot != null && focusInfo == null && !item.done) focusInfo = focusOf(v, null, item, before?.let { setValues(it, unit) }, editIt, tickIt)
                        SetRow(
                            set = item,
                            label = v.label,
                            spoken = v.spoken,
                            unit = unit,
                            loadType = loadType,
                            // Last time's set at this position, with its type in brackets when it is not this set's own.
                            previous = buildAnnotatedString {
                                if (before == null) append(NONE) else {
                                    append(setValues(before, unit))
                                    if (tag != null) withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) { append(" [${tag.letter}]") }
                                }
                                if (aim != null) withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lightAlpha(0.7f, 0.9f)))) { append("  → $hintWeight × $hintReps") }
                            },
                            aimed = aim != null,
                            hintWeight = hintWeight,
                            hintReps = hintReps,
                            record = live[index].orEmpty(),
                            beatsLast = item.type != SetType.WARMUP && (loadType == LoadType.NORMAL || loadType == LoadType.BODYWEIGHT) &&
                                beatsLastReps(parseNumber(item.weight)?.let { toKg(it, unit) }, item.reps.toIntOrNull(), before),
                            beatsBest = !item.done && (loadType == LoadType.NORMAL || loadType == LoadType.BODYWEIGHT) &&
                                records.weight?.value?.let { best -> parseNumber(item.weight)?.let { toKg(it, unit) > best + 1e-9 } } == true,
                            bounds = rowAt(index), current = index == nextItem,
                            arrival = arrivals.token(index), items = block.items,
                            focus = v.focus,
                            onFocus = editIt,
                            variations = quick.filter { it != name }.map { it to shortVariation(it, overall) },
                            onVariation = { v -> replace(index, item.copy(exercise = v.takeIf { it != block.exercise })) },
                            onPlates = if ("barbell" in name.lowercase() && !cardio && !timed) ({ platesFor = item.weight.ifEmpty { hintWeight } to unit }) else null,
                            // Tapping last time's set copies it into the boxes.
                            // The next set of a timed exercise can be timed with a stopwatch, which fills its seconds.
                            onTimer = if (timed && index == nextItem) ({ timing = index to parseNumber(hintReps)?.toInt()?.takeIf { it > 0 } }) else null,
                            timerRunning = model.stopwatchStart != null,
                            onPrevious = if (before == null) null else ({ replace(index, item.copy(weight = lastWeight.orEmpty(), reps = lastReps.orEmpty())) }),
                            onRecord = {
                                model.message = listOfNotNull(
                                    records.weight?.let { "Best weight before: ${showWeight(it.value, unit)} $unit × ${it.reps}" },
                                    records.estimated1Rm?.let { "best 1RM before: ${showWeight(roundHalfUp(it.value * 10) / 10.0, unit)} $unit" },
                                ).joinToString(", ")
                            },
                            onChange = { replace(index, it) },
                            onTick = tickIt,
                        )
                    }
                }
            }
            if (slot != null) SideEffect { slot.info = focusInfo }
            // A new set carries on as the variation of the one before it. Left and right: a pair is added, or the missing half.
            QuietButton(
                "Add set", {
                    val last = block.items.filterIsInstance<DraftSet>().lastOrNull()
                    val count = if (paired) setsToAddPair(block.items.count { it is DraftSet }) else 1
                    onChange(block.copy(items = block.items + List(count) { DraftSet(type = if (count == 1 && paired) last?.type ?: SetType.NORMAL else SetType.NORMAL, exercise = last?.exercise) }))
                },
                Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
            )
        }
        }
        }
        }
        }
    }

    if (replacing) ExercisePicker(model, workouts, replacing = block.exercise, onPick = {
        // A card logged left and right stays so; an exercise switched on starts paired.
        val replaced = block.copy(exercise = it, targets = emptyList(), paired = block.paired || model.sidesOf(it))
        onChange(if (replaced.paired) replaced.withPairsCompleted() else replaced)
        replacing = false
    }, onDismiss = { replacing = false })
    if (switching) {
        var label by remember { mutableStateOf("") }
        var choosing by remember { mutableStateOf(false) }
        var showAll by remember { mutableStateOf(false) }
        val created = "$overall (${label.trim()})"
        CleanDialog(
            onDismissRequest = { switching = false },
            title = { Text("Switch variation") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "The sets not ticked yet carry on under the variation you pick, which keeps its own previous values and PRs.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The ones already done come first, then the few built in for this exercise; the rest of a big group waits behind a line.
                    val done = variations.filter { it != block.exercise && model.hasHistory(it) }
                    val own = remember(settings, block.exercise) { model.suggestedVariations(block.exercise) }.filter { it !in done }
                    val rest = variations.filter { it != block.exercise && it !in done && it !in own }
                    for (v in done) TextButton(onClick = { onSwitch(v); switching = false }) { Text(v) }
                    for (v in if (showAll) own + rest else own) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSwitch(v); switching = false }, modifier = Modifier.weight(1f)) {
                                Text(v, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // A built-in variation that is of no use can be taken off the list.
                            IconButton(onClick = { model.leaveGroup(v) }) { Icon(CleanIcons.Close, "Remove this variation", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) }
                        }
                    }
                    if (rest.isNotEmpty()) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "${rest.size} more of $overall") }
                    OutlinedTextField(label, { label = it }, label = { Text("New variation") }, placeholder = { Text("Wide Grip") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = Space.s))
                    if (label.isNotBlank()) Text(created, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { choosing = true }) { Text("Choose an existing exercise") }
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank(), onClick = { model.addVariation(block.exercise, created); onSwitch(created); switching = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { switching = false }) { Text("Cancel") } },
        )
        if (choosing) ExercisePicker(model, workouts, onPick = { model.addVariation(block.exercise, it); onSwitch(it); choosing = false; switching = false }, onDismiss = { choosing = false }, title = "Add a variation")
    }
    if (warming) {
        // The working weight is the next working set's, else the heaviest of last time; it can be typed over.
        val guess = remember {
            block.items.filterIsInstance<DraftSet>().firstOrNull { !it.done && it.type != SetType.WARMUP }?.weight?.ifEmpty { null }
                ?: sessions.lastOrNull()?.sets?.filter { it.type != SetType.WARMUP }?.mapNotNull { it.weight }?.maxOrNull()?.let { entryWeight(it, unit) }.orEmpty()
        }
        var text by remember { mutableStateOf(guess) }
        var scheme by model.remembered("warmupScheme", "3 sets")
        val step = if (unit == "lb") 5.0 else weightStep(sessions)
        val ladder = parseNumber(text)?.let { warmupSets(it, step, WARMUP_SCHEMES[scheme] ?: WARMUP_SCHEMES.getValue("3 sets")) }.orEmpty()
        val barbell = "barbell" in block.exercise.lowercase()
        val replaces = block.items.any { it is DraftSet && it.type == SetType.WARMUP && !it.done }
        CleanDialog(
            onDismissRequest = { warming = false },
            title = { Text("Warm-up sets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Working weight ($unit)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Choice(WARMUP_SCHEMES.keys.toList(), scheme, { it }) { scheme = it }
                    if (ladder.isEmpty()) Text("Enter a working weight heavy enough to have something under it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (set in ladder) Column {
                        Text("${shortNumber(set.weight)} $unit × ${set.reps}", fontWeight = FontWeight.Bold)
                        if (barbell) Text(plateText(set.weight, unit, model.plateKit(unit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Weights are rounded to this exercise's step of ${shortNumber(step)} $unit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = ladder.isNotEmpty(), onClick = {
                    // They take the place of the warm-ups not ticked yet (a ticked one is work done, and stays); the old ones can be put back for a few seconds.
                    val before = owner.draft
                    // Left and right: a pair with one side ticked stays whole.
                    val holding = if (paired) sidePairs(block.items.map { it is DraftSet }).filter { (l, r) -> listOfNotNull(l, r).any { (block.items[it] as DraftSet).done } }.flatMap { listOfNotNull(it.first, it.second) }.toSet() else emptySet()
                    // Left and right: a pair is a warm-up only when both its halves are, so a pair with one working half stays whole.
                    fun warmOf(list: List<DraftItem>): List<Boolean> {
                        val pairs = if (paired) sidePairs(list.map { it is DraftSet }) else emptyList()
                        return list.mapIndexed { i, it ->
                            if (it !is DraftSet) false
                            else if (!paired) it.type == SetType.WARMUP
                            else pairs.first { p -> p.first == i || p.second == i }.let { p -> listOfNotNull(p.first, p.second).all { j -> (list[j] as DraftSet).type == SetType.WARMUP } }
                        }
                    }
                    val warm = warmOf(block.items)
                    val stays = block.items.mapIndexed { i, it -> it !is DraftSet || !warm[i] || it.done || i in holding }
                    val items = block.items.filterIndexed { i, _ -> stays[i] }
                    // Above the first set that is not a warm-up, under any note that opens the card and any warm-up already done.
                    val warmLeft = warmOf(items)
                    val at = items.indices.firstOrNull { items[it] is DraftSet && !warmLeft[it] } ?: items.size
                    // Left and right: each warm-up is a pair.
                    onChange(block.copy(items = items.take(at) + ladder.flatMap { w -> List(if (paired) 2 else 1) { DraftSet(SetType.WARMUP, shortNumber(w.weight), w.reps.toString()) } } + items.drop(at)))
                    if (before != null && items.size < block.items.size) onUndo("Warm-up sets replaced") { owner.put(before) }
                    warming = false
                }) { Text(if (replaces) "Replace" else "Add") }
            },
            dismissButton = { TextButton(onClick = { warming = false }) { Text("Cancel") } },
        )
    }
    if (progressing) ProgressionDialog(model, block.exercise, unit, sessions) { progressing = false }
    if (resting) {
        CleanDialog(
            onDismissRequest = { resting = false },
            title = { Text("Rest after a set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("For ${block.exercise}. “Default” follows the length set in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val own = settings[block.exercise]?.rest ?: -1
                    RestChoice(
                        "Rest after a set", own, listOf(-1, 0, 30, 60, 90, 120, 180, 300),
                        { seconds -> model.putSetting(block.exercise) { it.copy(rest = seconds) } },
                        value = if (own < 0) "Default · ${restLabel(model.restDefault.value)}" else restLabel(own), unset = "Default",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { resting = false }) { Text("Done") } },
        )
    }
    timing?.let { (at, last) ->
        // The clock lives in the model, so it keeps running when the sheet is put away and opened again.
        var down by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { model.startStopwatch() }
        Sheet({ timing = null }, "Timer") {
            // Read in here, so only the sheet is redrawn each second.
            val begun = model.stopwatchStart ?: currentMillis()
            val elapsed = ((nowMillis() - begun) / 1000).coerceAtLeast(0)
            Text(
                if (down && last != null) (if (elapsed < last) clock(last - elapsed) else "+" + clock(elapsed - last)) else clock(elapsed),
                Modifier.fillMaxWidth().padding(vertical = Space.l), textAlign = TextAlign.Center, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,
            )
            if (last != null) Column(Modifier.padding(horizontal = Space.l)) {
                Choice(listOf(false, true), down, { if (it) "Count down from last time" else "Count up" }) { down = it; model.startStopwatch(if (it) last else null) }
            }
            MainButton("Stop and fill", {
                val seconds = model.stopStopwatch()
                (block.items.getOrNull(at) as? DraftSet)?.let { replace(at, it.copy(reps = seconds.toString())) }
                timing = null
            }, Modifier.fillMaxWidth().padding(Space.l))
        }
    }
    var editingPlates by remember { mutableStateOf<String?>(null) }
    platesFor?.let { (text, plateUnit) ->
        val total = parseNumber(text)
        val stored by model.options.collectAsState()
        val kit = remember(stored, plateUnit) { model.plateKit(plateUnit) }
        CleanDialog(
            onDismissRequest = { platesFor = null },
            title = { Text(if (total == null) "Plates" else "${shortNumber(total)} $plateUnit") },
            text = { Text(if (total == null) "Enter a weight first." else plateText(total, plateUnit, kit)) },
            confirmButton = { TextButton(onClick = { platesFor = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { editingPlates = plateUnit }) { Text("Bar and plates") } },
        )
    }
    editingPlates?.let { PlateKitDialog(model, it) { editingPlates = null } }
    if (explaining) {
        CleanDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Set types") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Tap a set's number to change its type. Swipe a set to the left to remove it.")
                    for (type in SetType.entries) {
                        Text(
                            when (type) {
                                SetType.NORMAL -> "1, 2, 3  A normal working set."
                                SetType.WARMUP -> "W  Warm-up: left out of set counts, volume and PRs."
                                SetType.FAILURE -> "F  Taken to failure: counts like a normal set."
                                SetType.DROP -> "D  Drop set: counts in sets and volume, but sets no PRs."
                            },
                            color = typeColor(type),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("Close") } },
        )
    }
    if (editingSticky) {
        var text by remember { mutableStateOf(sticky) }
        CleanDialog(
            onDismissRequest = { editingSticky = false },
            title = { Text("Sticky note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Stays on ${block.exercise} in every workout until you change it: a seat setting, a cue. Clear it to remove it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { model.putSetting(block.exercise) { it.copy(note = text.trim()) }; editingSticky = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingSticky = false }) { Text("Cancel") } },
        )
    }
}

/** The rep range and weight step of an exercise's double progression; "Off" takes it away. Used from a workout's card and from the exercise's page. */
@Composable
fun ProgressionDialog(model: AppModel, exercise: String, unit: String, sessions: List<clean.core.Session>, onDismiss: () -> Unit) {
    val current = model.progressions.collectAsState().value[exercise]
    var low by remember { mutableStateOf((current?.minReps ?: 8).toString()) }
    var high by remember { mutableStateOf((current?.maxReps ?: 12).toString()) }
    var stepText by remember { mutableStateOf(current?.let { showWeight(it.step, unit) } ?: if (unit == "lb") "5" else shortNumber(weightStep(sessions))) }
    val scheme = low.trim().toIntOrNull()?.let { min -> high.trim().toIntOrNull()?.let { max -> parseNumber(stepText)?.let { Progression(min, max, toKg(it, unit)) } } }
        ?.takeIf { it.minReps in 1..it.maxReps && it.step > 0 }
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progression") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    "For $exercise. Add reps up to the top of the range; when every set gets there at one weight, the next workout goes up by the step and starts at the bottom again. A set to failure or at RPE 9.5 or more repeats.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    OutlinedTextField(low, { low = it }, label = { Text("From reps") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(high, { high = it }, label = { Text("To reps") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(stepText, { stepText = it }, label = { Text("Step ($unit)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = scheme != null, onClick = { model.setProgression(exercise, scheme); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { model.setProgression(exercise, null); onDismiss() }) { Text("Off") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * What the ticked sets of a card earn against the history of the variation each was done as, by the
 * same rule as a finished workout (`SidedBests.judge`), so a badge shown now is the badge saved with the
 * workout. By position in [block]. Cardio earns none. In a left and right card each half is judged
 * against its own side's history.
 */
private fun liveRecords(block: DraftBlock, contexts: Map<String, SetContext>, bodyweight: Double?, rule: OneRm): Map<Int, Set<PrKind>> {
    val kinds = HashMap<Int, Set<PrKind>>()
    val sides = HashMap<Int, Side>()
    if (block.paired) for ((left, right) in sidePairs(block.items.map { it is DraftSet })) {
        sides[left] = Side.LEFT
        right?.let { sides[it] = Side.RIGHT }
    }
    val ticked = block.items.withIndex().filter { (it.value as? DraftSet)?.done == true }
    for ((name, mine) in ticked.groupBy { (it.value as DraftSet).exercise ?: block.exercise }) {
        val c = contexts[name] ?: contexts.getValue(block.exercise)
        if (c.loadType == LoadType.CARDIO) continue
        // The boxes are read as they are saved: a timed set's second box is seconds, any other's is reps.
        val sets = mine.map { (_, item) ->
            val set = item as DraftSet
            val kg = parseNumber(set.weight)?.let { toKg(it, c.unit) }
            if (c.loadType == LoadType.TIMED) PrSet(set.type, kg, null, parseNumber(set.reps)) else PrSet(set.type, kg, set.reps.trim().toIntOrNull(), null, set.rpe.toDoubleOrNull())
        }
        c.bests.judge(sets, mine.map { sides[it.index] }, c.loadType, bodyweight, rule, commit = false).forEachIndexed { i, set -> kinds[mine[i].index] = set }
    }
    return kinds
}

@Composable
private fun SetRow(
    set: DraftSet,
    label: String,
    /** How a screen reader names the set: "Set 2". */
    spoken: String,
    unit: String,
    loadType: LoadType,
    previous: AnnotatedString,
    /** Whether [previous] ends with a target, which may need a second line. */
    aimed: Boolean,
    hintWeight: String,
    hintReps: String,
    /** What the set has earned, once ticked. */
    record: Set<PrKind>,
    /** E3: the weight typed is above the exercise's best, so the number tints in the accent while it is typed. */
    beatsBest: Boolean,
    /** Round 17 (17): the set as it is ticked has more reps than last time's same set at the same weight or more: a ▲ floats up from it when it is ticked. */
    beatsLast: Boolean = false,
    /** Where the card's next-set highlight finds this row. */
    bounds: Modifier,
    /** Round 28: this is the set to do now: its number is the accent. */
    current: Boolean = false,
    /** Non-zero when the set has just come into the card: its gap opens and it slides in. */
    arrival: Int,
    /** The card's items as they are now, to know when a removed row has gone from the card. */
    items: Any,
    /** Which of this row's boxes the number pad is on: 0 for neither, 1 for the weight, 2 for the reps. */
    focus: Int,
    onFocus: (Boolean) -> Unit,
    variations: List<Pair<String, String>>,
    onVariation: (String) -> Unit,
    onPlates: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    /** A stopwatch for this set, shown beside last time's set; the accent while the clock runs. */
    onTimer: (() -> Unit)?,
    timerRunning: Boolean,
    onRecord: () -> Unit,
    onChange: (DraftSet?) -> Unit,
    onTick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = LocalCleanColors.current
    val motion = LocalMotion.current
    // A badge earned while this row is on the screen lands with the bounce and a shimmer; those it opened with just are there.
    val landing = rememberLanding(record)
    // A set just ticked: its box bounces and the phone gives a short buzz.
    val pop = remember { Animatable(1f) }
    var was by remember { mutableStateOf(set.done) }
    // Round 17 (17): the ▲ of a set that beat last time's reps, once, as it is ticked: it floats up from the reps box and fades.
    val rise = remember { Animatable(1f) }
    val beatsNow by rememberUpdatedState(beatsLast)
    val haptic = rememberHaptic()
    val feel = LocalFeel.current
    LaunchedEffect(set.done) {
        if (set.done && !was) {
            if (feel.tick != "off") haptic(Haptic.Confirm)
            if (motion && beatsNow) launch {
                rise.snapTo(0f)
                rise.animateTo(1f, Motion.beatRise())
            }
            if (motion) {
                pop.snapTo(0.75f)
                pop.animateTo(1f, Motion.reward())
            }
        }
        was = set.done
    }
    // The set to do next is lit by the card's one gliding highlight; one that is done has the green of its tick spread over it from the tick.
    val skin = workoutSkin()
    val box = animateColorAsState(if (set.done) skin.tickDone else skin.tickIdle, Motion.quick(), label = "tick")
    val gap = rememberRowGap(arrival, items)
    val removeRow = rememberRemoval(gap, items, current) { onChange(null) }
    val host = LocalRowDialogs.current
    // Where the row and its number are, for the dialog that grows from the number.
    val places = remember { arrayOfNulls<LayoutCoordinates>(2) }
    // What a tap on the number offers; the same in the phone's menu (the Correct page) and in the dialog of the workout.
    val typeItems: @Composable (() -> Unit) -> Unit = { close ->
        for (type in SetType.entries) {
            DropdownMenuItem({ Text(typeName(type), color = typeColor(type)) }, { close(); onChange(set.copy(type = type)) })
        }
        if (variations.isNotEmpty()) {
            // This set alone as another variation.
            HorizontalDivider()
            for ((name, short) in variations) DropdownMenuItem({ Text("As $short") }, { close(); onVariation(name) })
        }
        if (onPlates != null) {
            HorizontalDivider()
            DropdownMenuItem({ Text("Plates for this weight") }, { close(); onPlates() })
        }
        // The swipe's twin, for whoever cannot swipe.
        HorizontalDivider()
        DropdownMenuItem({ Text("Remove set", color = colors.danger) }, { close(); removeRow(false) })
    }
    Box(Modifier.gapped(gap)) {
    // Swiping the row to the left removes the set.
    SetSwipe(set.done, onTick, { removeRow(true) }) {
    Column {
    Row(
        Modifier.padding(horizontal = Space.xs).then(bounds).onGloballyPositioned { places[0] = it }.prEdge(landing.isNotEmpty()).doneTint(set.done, skin.rowDone).padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The set's number or letter; tapping it changes the type or removes the set.
        val typeWords = typeName(set.type).lowercase()
        Box(
            Modifier.width(44.dp).requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp).onGloballyPositioned { places[1] = it }
                .clickable(onClickLabel = "Change type") { if (host != null) host.show(places[1], places[0], "Change type", typeItems) else menu = true }
                .semantics { contentDescription = "$spoken, $typeWords${if (set.rpe.isEmpty()) "" else ", RPE ${set.rpe}"}. Change type" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (current && !set.done && set.type == SetType.NORMAL) MaterialTheme.colorScheme.primary else typeColor(set.type), fontWeight = FontWeight.Bold)
                if (set.rpe.isNotEmpty()) Text("@${set.rpe}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CleanMenu(menu, { menu = false }) { typeItems { menu = false } }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(previous, Modifier.clickable(enabled = onPrevious != null) { onPrevious?.invoke() }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (aimed) 2 else 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                if (onTimer != null) Box(Modifier.minTouch().clickable(onClick = onTimer), contentAlignment = Alignment.Center) {
                    Icon(CleanIcons.TimerLine, "Time this set", tint = if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(record.isNotEmpty(), enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
                PrBadges(record, Modifier.clickable(onClick = onRecord), landing = landing)
            }
        }
        // What each box holds, in words: its set, which box, the value and its unit.
        val (weightWord, repsWord) = when (loadType) {
            LoadType.CARDIO -> "distance" to "time"
            LoadType.TIMED -> "weight" to "time"
            else -> "weight" to "reps"
        }
        val weightUnit = if (loadType == LoadType.CARDIO) "kilometres" else if (unit == "lb") "pounds" else "kilograms"
        val repsUnit = if (loadType == LoadType.CARDIO) "minutes" else if (loadType == LoadType.TIMED) "seconds" else "reps"
        PadField(set.weight, hintWeight, focus == 1, Modifier.width(84.dp).padding(horizontal = Space.xs), "$spoken $weightWord, ${if (set.weight.isEmpty()) "empty" else "${set.weight} $weightUnit"}", tint = if (beatsBest) MaterialTheme.colorScheme.primary else null) { onFocus(false) }
        Box(Modifier.width(84.dp).padding(horizontal = Space.xs)) {
            PadField(set.reps, hintReps, focus == 2, Modifier.fillMaxWidth(), "$spoken $repsWord, ${if (set.reps.isEmpty()) "empty" else "${set.reps} $repsUnit"}") { onFocus(true) }
            // Round 17 (17): the ▲ rises about a row's height from the box and fades; not in the layout, so nothing moves.
            if (rise.value < 1f) Text(
                "▲", Modifier.align(Alignment.TopCenter).testTag("beat rise").graphicsLayer { translationY = -28.dp.toPx() * rise.value; alpha = 1f - rise.value },
                color = colors.done, style = MaterialTheme.typography.labelMedium,
            )
        }
        // The whole cell is the checkbox; the press ripple stays on the box drawn in it. Correcting a finished workout has no ticks:
        // the place stays, empty, so the boxes line up as in a workout.
        val tickSource = remember { MutableInteractionSource() }
        val noTicks = LocalDraftSlot.current?.correcting == true
        Box(
            Modifier.width(44.dp).requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp).padGround()
                .then(if (noTicks) Modifier else Modifier.toggleable(set.done, tickSource, null, role = Role.Checkbox) { onTick() }
                    .semantics { contentDescription = spoken; stateDescription = if (set.done) "done" else "not done" }),
            contentAlignment = Alignment.Center,
        ) {
            if (!noTicks) Surface(
                color = Color.Transparent,
                shape = SmallShape,
                border = if (skin.tickIdleBorder == Color.Transparent) null else BorderStroke(skin.lineWidth, skin.tickIdleBorder),
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(width = 40.dp, height = 36.dp).clip(SmallShape).drawBehind { drawRect(box.value) }.indication(tickSource, LocalIndication.current),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(CleanIcons.Check, null, tint = if (set.done) skin.checkDone else skin.checkIdle)
                }
            }
        }
    }
    }
    }
    }
}

/**
 * The next-set highlight of a card, drawn behind its rows as one rectangle: when the set to do next changes it glides from the row it
 * was on to the new one (`Motion.glide`), fades in where there was none and out when there is no next set. Rows say where they are
 * ([rows], by the index of the set); the card's own coordinates are [container].
 */
private class Glide(initial: Int) {
    var container: LayoutCoordinates? = null
    val rows = HashMap<Int, LayoutCoordinates>()
    private val progress = Animatable(1f)
    private val alpha = Animatable(if (initial >= 0) 1f else 0f)
    /** The one glow when a rest runs out: 0 at rest, up to 1 and back. */
    private val glow = Animatable(0f)
    suspend fun flash() { glow.animateTo(1f, androidx.compose.animation.core.tween(Motion.GLOW_IN, easing = androidx.compose.animation.core.LinearEasing)); glow.animateTo(0f, androidx.compose.animation.core.tween(Motion.GLOW_OUT, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    private var shown = initial
    private var from: Rect? = null
    private var last: Rect? = null

    /** Round 30: how far the swiping row has gone (read in the draw, so the highlight is drawn again as the row moves; the row's own place is read live). */
    private var swipe by androidx.compose.runtime.mutableFloatStateOf(0f)
    fun watch(dx: Float) { swipe = dx }
    /**
     * The row the highlight is on is being removed: the highlight goes with it (at once when a swipe has already taken the row off the screen, else fading
     * as the row does), and the set that becomes current is lit by [go] fading in where it stands, so it never jumps back to the old place.
     */
    suspend fun drop(instant: Boolean) { if (instant) alpha.snapTo(0f) else alpha.animateTo(0f, Motion.quick()) }

    private fun rect(index: Int): Rect? {
        val c = container?.takeIf { it.isAttached } ?: return null
        val r = rows[index]?.takeIf { it.isAttached } ?: return null
        return c.localBoundingBoxOf(r, false)
    }

    suspend fun go(target: Int, motion: Boolean) {
        if (target < 0) {
            if (motion) alpha.animateTo(0f, Motion.quick()) else alpha.snapTo(0f)
            return
        }
        if (target == shown && alpha.value > 0f) {
            alpha.animateTo(1f, Motion.quick())
        } else if (alpha.value > 0f && shown >= 0) {
            from = last
            shown = target
            alpha.snapTo(1f)
            progress.snapTo(0f)
            if (motion) progress.animateTo(1f, Motion.glide()) else progress.snapTo(1f)
        } else {
            from = null
            shown = target
            progress.snapTo(1f)
            if (motion) alpha.animateTo(1f, Motion.quick()) else alpha.snapTo(1f)
        }
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, color: Color, accent: Color) = with(scope) {
        swipe // read, so a swipe draws it again
        val a = alpha.value
        if (a <= 0f || shown < 0) return@with
        val to = rect(shown) ?: last ?: return@with
        val f = from
        val p = progress.value
        val r = if (f == null || p >= 1f) to else Rect(
            f.left + (to.left - f.left) * p, f.top + (to.top - f.top) * p, f.right + (to.right - f.right) * p, f.bottom + (to.bottom - f.bottom) * p,
        )
        last = r
        translate(r.left, r.top) {
            drawRoundRect(color, size = r.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), alpha = a)
            // Round 28: a leading accent bar marks the set to do now (the number in its row is the accent too).
            drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(0f, 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), (r.height - 12.dp.toPx()).coerceAtLeast(0f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()), alpha = a)
            val g = glow.value
            if (g > 0f) {
                drawRoundRect(accent.copy(alpha = 0.22f * g), size = r.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                drawRoundRect(accent.copy(alpha = 0.8f * g), size = r.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/**
 * A done set's tint, spread across the row from the tick's end (the right) over `Motion.SWEEP`; un-ticking fades it as it always did.
 * With animations off it is there or not.
 */
@Composable
private fun Modifier.doneTint(done: Boolean, color: Color): Modifier {
    val motion = LocalMotion.current
    val reveal = remember { Animatable(if (done) 1f else 0f) }
    val fade = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        if (done) {
            fade.snapTo(1f)
            if (motion) reveal.animateTo(1f, Motion.sweep()) else reveal.snapTo(1f)
        } else {
            if (motion) fade.animateTo(0f, Motion.quick()) else fade.snapTo(0f)
            reveal.snapTo(0f)
        }
    }
    return drawBehind {
        if (fade.value > 0f) clipRect(left = size.width * (1f - reveal.value)) {
            drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), alpha = fade.value)
        }
    }
}

private val LocalNextGlide = staticCompositionLocalOf<Glide?> { null }

/** A set row's own height: closed to nothing when it is removed (the rows below glide up) and opened again when it comes back. */
private class RowGap {
    var open by androidx.compose.runtime.mutableFloatStateOf(1f)
    var hidden by mutableStateOf(false)
    var entered = 0
    /** The items of the card when the row was taken out: the row stays shut until the card shows other ones, which takes a frame or two, so it never reopens over the old rows. */
    var awaiting: Any? = null
    var removing = false
}

/** [arrival] is non-zero for a row that has just come into the card: its gap opens and it slides in. */
@Composable
private fun rememberRowGap(arrival: Int, items: Any): RowGap {
    val motion = LocalMotion.current
    val gap = remember { RowGap() }
    if (gap.awaiting != null && gap.awaiting !== items) { gap.awaiting = null; gap.open = 1f; gap.hidden = false; gap.removing = false }
    if (arrival != 0 && arrival != gap.entered) {
        gap.entered = arrival
        if (motion) gap.open = 0f
    }
    LaunchedEffect(arrival) {
        if (arrival != 0 && motion && gap.open < 1f) {
            try { androidx.compose.animation.core.animate(gap.open, 1f, animationSpec = Motion.gap()) { v, _ -> gap.open = v } } finally { gap.open = 1f }
        }
    }
    return gap
}

/**
 * Removing the row: it fades (at once when [hide], a swipe having taken it off the screen already) while its height closes, then [remove]
 * is called and the row below stands where it was.
 */
@Composable
private fun rememberRemoval(gap: RowGap, items: Any, highlighted: Boolean = false, remove: () -> Unit): (Boolean) -> Unit {
    val itemsNow by rememberUpdatedState(items)
    val glide = LocalNextGlide.current
    val highlightedNow by rememberUpdatedState(highlighted)
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(remove)
    val motion = LocalMotion.current
    return { hide ->
        if (!gap.removing) {
            gap.removing = true
            gap.hidden = hide
            scope.launch {
                // Round 30: the highlight of the current set goes with its row; the next current set is lit as it comes into the place.
                if (highlightedNow) launch { glide?.drop(hide) }
                if (motion) androidx.compose.animation.core.animate(gap.open, 0f, animationSpec = Motion.gap()) { v, _ -> gap.open = v }
                gap.awaiting = itemsNow
                latest()
            }
        }
    }
}

private fun Modifier.gapped(gap: RowGap): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * gap.open).toInt()) { placeable.placeRelative(0, 0) }
}.graphicsLayer {
    clip = gap.open < 1f
    alpha = if (gap.hidden) 0f else gap.open
    translationX = -(1f - gap.open) * size.width * 0.2f
}

/** Where a set (or a round) has just come into a card, found by comparing its items with the last ones: the rows there open their gap. */
private class Arrivals(private var items: List<DraftItem>) {
    private var from = -1
    private var to = -1
    private var serial = 0

    fun update(now: List<DraftItem>): Arrivals {
        val before = items
        items = now
        val n = now.size - before.size
        if (n in 1..2) {
            val k = (now.size - n downTo 0).firstOrNull { k -> now.subList(0, k) == before.subList(0, k) && now.subList(k + n, now.size) == before.subList(k, before.size) }
            if (k != null) { from = k; to = k + n; serial++ }
        }
        return this
    }

    fun token(index: Int) = if (index in from until to) serial else 0
}

/** What the workout screen shows as a dialog over itself, grown from the thing that was tapped (`RowDialogHost`). */
private class RowDialog(val anchor: Rect, val row: Rect, val title: String, val content: @Composable (() -> Unit) -> Unit)

private class RowDialogs {
    var current by mutableStateOf<RowDialog?>(null)
    var open by mutableStateOf(false)

    /** Opens a dialog from [anchor] (the number that was tapped) for the set row [row], which stays lit and in view. */
    fun show(anchor: LayoutCoordinates?, row: LayoutCoordinates?, title: String, content: @Composable (() -> Unit) -> Unit) {
        val a = anchor?.takeIf { it.isAttached }?.boundsInWindow()
        val r = row?.takeIf { it.isAttached }?.boundsInWindow() ?: a ?: return
        current = RowDialog(a ?: r, r, title, content)
        open = true
    }

    fun close() { open = false }
}

private val LocalRowDialogs = staticCompositionLocalOf<RowDialogs?> { null }

/**
 * The dialogs of a set row (a set's "Change type") drawn inside the workout rather than in a window: a scrim with the row cut out of it
 * and outlined, so the set the dialog belongs to stays visible and lit, and a card that grows from the tapped number to the middle of the
 * screen (or just under or over the row when the middle would cover it), clipped to its growing bounds, and shrinks back on close. Back
 * and a tap outside close it. Without animations it is there or not.
 */
@Composable
private fun RowDialogHost(host: RowDialogs) {
    val dialog = host.current ?: return
    val motion = LocalMotion.current
    val p = remember(dialog) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(dialog, host.open) {
        if (host.open) { if (motion) p.animateTo(1f, Motion.dialog()) else p.snapTo(1f) }
        else {
            if (motion) p.animateTo(0f, Motion.dialog()) else p.snapTo(0f)
            if (host.current === dialog) host.current = null
        }
    }
    BackHandler(host.open) { host.close() }
    val scheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cardRect = remember { arrayOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val row = dialog.row.translate(-origin)
        val anchor = dialog.anchor.translate(-origin)
        // The scrim, with the row's place cut out and ringed.
        Box(
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = 0.32f * p.value))
                    val m = 3.dp.toPx()
                    val tl = Offset(row.left - m, row.top - m)
                    val sz = Size(row.width + 2 * m, row.height + 2 * m)
                    val radius = androidx.compose.ui.geometry.CornerRadius(11.dp.toPx())
                    drawRoundRect(Color.Black, tl, sz, radius, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    drawRoundRect(scheme.primary.copy(alpha = p.value), tl, sz, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                .clickable(remember { MutableInteractionSource() }, null) { host.close() },
        )
        Box(
            Modifier.fillMaxSize()
                .drawWithContent {
                    val q = p.value
                    val c = cardRect[0]
                    fun mix(a: Float, b: Float) = a + (b - a) * q
                    val box = Rect(mix(anchor.left, c.left), mix(anchor.top, c.top), mix(anchor.right, c.right), mix(anchor.bottom, c.bottom))
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(box, androidx.compose.ui.geometry.CornerRadius(mix(8.dp.toPx(), 24.dp.toPx()))))
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                .layout { measurable, constraints ->
                    val margin = 24.dp.roundToPx()
                    val maxW = (constraints.maxWidth - 2 * margin).coerceIn(0, 360.dp.roundToPx())
                    val gap = 12.dp.roundToPx()
                    val wanted = measurable.maxIntrinsicHeight(maxW).coerceAtMost(constraints.maxHeight - 2 * margin)
                    val below = row.bottom.toInt() + gap
                    val roomBelow = constraints.maxHeight - margin - below
                    val roomAbove = row.top.toInt() - gap - margin
                    // Centred, unless that would cover the set: then just under it, or over it; with no room for the whole card either side it takes the larger room and scrolls.
                    var height = wanted
                    var y = (constraints.maxHeight - wanted) / 2
                    if (y < row.bottom + gap && y + wanted > row.top - gap) {
                        if (wanted <= roomBelow) y = below
                        else if (wanted <= roomAbove) y = row.top.toInt() - gap - wanted
                        else if (roomBelow >= roomAbove) { height = roomBelow.coerceAtLeast(0); y = below }
                        else { height = roomAbove.coerceAtLeast(0); y = margin }
                    }
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints(maxWidth = maxW, maxHeight = height))
                    val x = (constraints.maxWidth - placeable.width) / 2
                    cardRect[0] = Rect(x.toFloat(), y.toFloat(), (x + placeable.width).toFloat(), (y + placeable.height).toFloat())
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                },
        ) {
            Surface(
                Modifier.glass(clean.app.ui.theme.SheetShape, haze = null, lift = 16.dp).clickable(remember { MutableInteractionSource() }, null) { }
                    .semantics { paneTitle = dialog.title },
                shape = clean.app.ui.theme.SheetShape, color = Color.Transparent,
            ) {
                Column(Modifier.graphicsLayer { alpha = ((p.value - 0.2f) / 0.5f).coerceIn(0f, 1f) }.verticalScroll(rememberScrollState()).padding(vertical = Space.l)) {
                    Text(dialog.title, Modifier.padding(horizontal = Space.xl, vertical = Space.s).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    dialog.content { host.close() }
                }
            }
        }
    }
}

/** The gap between a pair's two halves; the column heads over them use the same one. */
private val SIDES_GAP = 4.dp

/** What one set of a card was read against and shows, worked out once for its row. */
private class SetView(
    val index: Int, val item: DraftSet, val name: String, val c: SetContext, val label: String, val spoken: String,
    val before: clean.core.SetItem?, val aim: Target?, val lastWeight: String?, val lastReps: String?,
    val hintWeight: String, val hintReps: String,
    /** Which of its boxes the number pad is on: 0 for neither, 1 for the weight, 2 for the reps. */
    val focus: Int, val padMeta: PadMeta?,
    /** Whether it is the first set done as a variation other than the one before it. */
    val newVariation: Boolean,
)

/** One side of a left and right pair: a set with its own boxes and tick. */
private class Half(
    val set: DraftSet, val side: String,
    /** Whether this round's halves differ in kind: each half then shows its own kind. */
    val mixed: Boolean,
    /** How a screen reader names it: "Set 2 left". */
    val spoken: String,
    /** Last time's matching set, as text; null when there was none. */
    val previous: String?,
    val hintWeight: String, val hintReps: String, val highlight: Boolean, val focus: Int,
    /** Where the card's next-set highlight finds this half. */
    val bounds: Modifier,
    val onFocus: (Boolean) -> Unit, val onPrevious: (() -> Unit)?, val onTick: () -> Unit,
)

/**
 * A left and right pair as one row: the round's number or letter (its kind, when both halves share one; a round whose halves differ shows its number and each half its own letter), then the two
 * halves side by side, and under them one row of badges: what the round earned, each kind once with the side or sides
 *  * that earned it. A pair is removed, re-typed and given a variation as a whole, or a half is re-typed on its own (a hold on the half, its side mark, or the round menu). [right] is null for a last set
 * left without its partner.
 */
@Composable
private fun PairRow(
    label: String, spoken: String, type: SetType?, rpe: String, unit: String, loadType: LoadType,
    left: Half, right: Half?, records: List<Pair<PrKind, Set<Side>>>, onRecord: () -> Unit,
    variations: List<Pair<String, String>>, onVariation: (String) -> Unit, onPlates: (() -> Unit)?, onType: (SetType) -> Unit, onHalfType: (Boolean, SetType) -> Unit, onRemove: () -> Unit,
    /** Non-zero when the round has just come into the card: its gap opens and it slides in. */
    arrival: Int = 0,
    items: Any = Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // The side whose own kind is being changed, if any.
    var sideType by remember { mutableStateOf<Side?>(null) }
    val colors = LocalCleanColors.current
    val motion = LocalMotion.current
    val gap = rememberRowGap(arrival, items)
    val landing = rememberLanding(records.mapTo(HashSet()) { it.first })
    val removeRow = rememberRemoval(gap, items, left.highlight || right?.highlight == true, onRemove)
    val host = LocalRowDialogs.current
    // Where the round and its number are, for the dialogs that grow from the number.
    val places = remember { arrayOfNulls<LayoutCoordinates>(2) }
    // The side whose kind is changed on its own: a dialog of the workout, or the window one where there is none (the Correct page).
    fun askSide(side: Side) {
        val half = if (side == Side.LEFT) left else right ?: left
        if (host != null) host.show(places[1], places[0], "${if (side == Side.LEFT) "Left" else "Right"} side: change type") { close ->
            SideTypeChoices(half) { close(); onHalfType(side == Side.RIGHT, it) }
        } else sideType = side
    }
    val typeItems: @Composable (() -> Unit) -> Unit = { close ->
        for (t in SetType.entries) DropdownMenuItem({ Text(typeName(t), color = typeColor(t)) }, { close(); onType(t) })
        // One side on its own (a hold on a half does the same).
        if (right != null) {
            HorizontalDivider()
            DropdownMenuItem({ Text("Left side type…") }, { close(); askSide(Side.LEFT) })
            DropdownMenuItem({ Text("Right side type…") }, { close(); askSide(Side.RIGHT) })
        }
        if (variations.isNotEmpty()) {
            HorizontalDivider()
            for ((name, short) in variations) DropdownMenuItem({ Text("As $short") }, { close(); onVariation(name) })
        }
        if (onPlates != null) {
            HorizontalDivider()
            DropdownMenuItem({ Text("Plates for this weight") }, { close(); onPlates() })
        }
        HorizontalDivider()
        DropdownMenuItem({ Text("Remove set", color = colors.danger) }, { close(); removeRow(false) })
    }
    // A swipe acts on the round as one half at a time: the first half not done, else (all done) the last one is un-ticked.
    val swipeHalf = if (!left.set.done) left else if (right != null && !right.set.done) right else right ?: left
    Box(Modifier.gapped(gap)) {
    SetSwipe(swipeHalf.set.done, swipeHalf.onTick, { removeRow(true) }) {
      Column {
        Row(Modifier.padding(horizontal = Space.xs).onGloballyPositioned { places[0] = it }.prEdge(landing.isNotEmpty()).padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            val typeWords = type?.let { typeName(it).lowercase() } ?: "left ${typeName(left.set.type).lowercase()}, right ${right?.set?.type?.let(::typeName)?.lowercase()}"
            Box(
                Modifier.width(44.dp).requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp).onGloballyPositioned { places[1] = it }
                    .clickable(onClickLabel = "Change type") { if (host != null) host.show(places[1], places[0], "Change type", typeItems) else menu = true }
                    .semantics { contentDescription = "$spoken, $typeWords${if (rpe.isEmpty()) "" else ", RPE $rpe"}. Change type" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = type?.let { typeColor(it) } ?: Color.Unspecified, fontWeight = FontWeight.Bold)
                    if (rpe.isNotEmpty()) Text("@$rpe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CleanMenu(menu, { menu = false }) { typeItems { menu = false } }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SIDES_GAP)) {
                SideHalf(left, unit, loadType, Modifier.weight(1f)) { askSide(Side.LEFT) }
                if (right != null) SideHalf(right, unit, loadType, Modifier.weight(1f)) { askSide(Side.RIGHT) } else Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(records.isNotEmpty(), enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
            PrBadges(
                records.mapTo(HashSet()) { it.first }, Modifier.padding(start = 56.dp, end = Space.m, bottom = Space.xs).clickable(onClick = onRecord),
                sides = records.toMap(),
                landing = landing,
            )
        }
      }
    }
    }
    sideType?.let { side ->
        val half = if (side == Side.LEFT) left else right ?: left
        val name = if (side == Side.LEFT) "Left" else "Right"
        CleanDialog(
            onDismissRequest = { sideType = null },
            title = { Text("$name side: change type") },
            text = { Column { SideTypeChoices(half) { sideType = null; onHalfType(side == Side.RIGHT, it) } } },
            confirmButton = { TextButton(onClick = { sideType = null }) { Text("Cancel") } },
        )
    }
}

/** The kinds one half of a round can be, the one it is now marked. */
@Composable
private fun SideTypeChoices(half: Half, onPick: (SetType) -> Unit) {
    for (t in SetType.entries) TextButton(onClick = { onPick(t) }, Modifier.fillMaxWidth().minTouch()) {
        Text(typeName(t) + if (t == half.set.type) " (now)" else "", Modifier.fillMaxWidth(), color = typeColor(t))
    }
}

/**
 * One half of a pair: its weight and reps boxes and its tick over a quiet line holding the side's letter and last
 * time's matching set. Done it is tinted green, next to do it takes the accent, else a faint container; the tick pops.
 */
@Composable
private fun SideHalf(h: Half, unit: String, loadType: LoadType, modifier: Modifier, onChangeType: () -> Unit) {
    val motion = LocalMotion.current
    val pop = remember { Animatable(1f) }
    var was by remember { mutableStateOf(h.set.done) }
    val haptic = rememberHaptic()
    val feel = LocalFeel.current
    LaunchedEffect(h.set.done) {
        if (h.set.done && !was) {
            if (feel.tick != "off") haptic(Haptic.Confirm)
            if (motion) {
                pop.snapTo(0.75f)
                pop.animateTo(1f, Motion.reward())
            }
        }
        was = h.set.done
    }
    val skin = workoutSkin()
    // The faint tile of a half that is neither next (the card's gliding highlight lights that one) nor done (its tint spreads over the tile, which goes after).
    val tint = animateColorAsState(
        if (h.highlight || h.set.done) Color.Transparent else skin.rowIdleHalf,
        animationSpec = if (h.set.done) Motion.afterSweep() else Motion.quick(), label = "half",
    )
    val box = animateColorAsState(if (h.set.done) skin.tickDone else skin.tickIdle, Motion.quick(), label = "tick")
    val (weightWord, repsWord) = when (loadType) {
        LoadType.CARDIO -> "distance" to "time"
        LoadType.TIMED -> "weight" to "time"
        else -> "weight" to "reps"
    }
    val weightUnit = if (loadType == LoadType.CARDIO) "kilometres" else if (unit == "lb") "pounds" else "kilograms"
    val repsUnit = if (loadType == LoadType.CARDIO) "minutes" else if (loadType == LoadType.TIMED) "seconds" else "reps"
    // A hold on the half (its padding and its quiet line) changes its kind on its own; the boxes and the tick keep their taps.
    Column(
        modifier.drawBehind { if (tint.value.alpha > 0f) drawOutline(SmallShape.createOutline(size, layoutDirection, this), tint.value) }.then(h.bounds).doneTint(h.set.done, skin.rowDone)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onLongClickLabel = "Change type", onLongClick = onChangeType, onClick = {})
            .padding(horizontal = Space.xs, vertical = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PadField(h.set.weight, h.hintWeight, h.focus == 1, Modifier.weight(1.2f).padding(end = Space.xxs), "${h.spoken} $weightWord, ${if (h.set.weight.isEmpty()) "empty" else "${h.set.weight} $weightUnit"}") { h.onFocus(false) }
            PadField(h.set.reps, h.hintReps, h.focus == 2, Modifier.weight(1f).padding(end = Space.xxs), "${h.spoken} $repsWord, ${if (h.set.reps.isEmpty()) "empty" else "${h.set.reps} $repsUnit"}") { h.onFocus(true) }
            // The whole cell is the checkbox; the press ripple stays on the box drawn in it. None on the Correct page.
            val tickSource = remember { MutableInteractionSource() }
            val noTicks = LocalDraftSlot.current?.correcting == true
            Box(
                Modifier.width(40.dp).requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp).padGround()
                    .then(if (noTicks) Modifier else Modifier.toggleable(h.set.done, tickSource, null, role = Role.Checkbox) { h.onTick() }
                        .semantics { contentDescription = h.spoken; stateDescription = if (h.set.done) "done" else "not done" }),
                contentAlignment = Alignment.Center,
            ) {
                if (!noTicks) Surface(
                    color = Color.Transparent,
                    shape = SmallShape,
                    border = if (skin.tickIdleBorder == Color.Transparent) null else BorderStroke(skin.lineWidth, skin.tickIdleBorder),
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(width = 36.dp, height = 36.dp).clip(SmallShape).drawBehind { drawRect(box.value) }.indication(tickSource, LocalIndication.current),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(CleanIcons.Check, null, tint = if (h.set.done) skin.checkDone else skin.checkIdle)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            // The side letter is also this half's own kind control: it adds the kind's letter when the round's halves differ, and a tap changes it.
            // A small slot in the line; the 48 dp hit area is centred on it and overlaps the air around, so the row keeps its height.
            Box(Modifier.size(width = 24.dp, height = 16.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.reach().clickable(onClickLabel = "Change type", onClick = onChangeType)
                        .semantics { contentDescription = "${h.side}, ${h.spoken.removeSuffix(" " + h.side.lowercase()).lowercase()}, ${typeName(h.set.type).lowercase()}, change type" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row {
                        Text(h.side.take(1), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (h.mixed) Text(h.set.type.letter.ifEmpty { "N" }, Modifier.padding(start = Space.xxs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (h.set.type == SetType.NORMAL) MaterialTheme.colorScheme.onSurfaceVariant else typeColor(h.set.type))
                    }
                }
            }
            Text(
                h.previous ?: NONE, Modifier.padding(start = Space.s).clickable(enabled = h.onPrevious != null) { h.onPrevious?.invoke() },
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Which box the number pad is typing into: a card (by its key), a set in it, and its weight or its reps box. */
private data class PadTarget(val card: String, val item: Int, val reps: Boolean)

/** What the pad needs to know about that box: how big a step is, what an empty box stands for, and whether a decimal point is allowed. */
private data class PadMeta(val step: Double, val hint: String, val decimal: Boolean, val above: String = "")

/** The number pad's state: open on a box, or closed. */
private class Pad {
    var target by mutableStateOf<PadTarget?>(null)
    var meta by mutableStateOf(PadMeta(1.0, "", false))
    /** Until a key is pressed in a box just entered, the first digit replaces what is there. */
    var fresh = true

    /** Where the pad itself is drawn and where each weight or reps box is, in the window; a tap anywhere else closes the pad. */
    var bounds: Rect? = null
    /** The pad's height in px while it is up (zero otherwise): the list leaves this much room at its end, so it scrolls under the pad. */
    var height by mutableStateOf(0)
    val fields = HashMap<Any, Rect>()

    /** The one way the pad goes away: no box is left in edit mode. What was typed stays in the box. */
    fun close() {
        target = null
        fresh = true
    }

    fun focus(card: String, item: Int, reps: Boolean) {
        target = PadTarget(card, item, reps)
        fresh = true
    }
}

/** The pad of the workout screen on show, for its boxes to say where they are. */
private val LocalPad = staticCompositionLocalOf<Pad?> { null }

/**
 * With the number pad up, a tap anywhere outside the pad and the pad's own ground (the number boxes and the set's tick, see
 * [padGround]) closes it, when the finger lifts. Nothing is consumed, so the thing under the finger still gets its tap: a button
 * closes the pad and does its work. A touch that moves past the slop (a scroll, a swipe), holds, or has a second finger is not a
 * tap and leaves the pad up. Seen at the Initial pass, so it sees the touch before anything under it can take it.
 */
private fun Modifier.closePadOnTap(pad: Pad): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    this.onGloballyPositioned { origin = it.positionInWindow() }.pointerInput(pad) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (pad.target == null) return@awaitEachGesture
            val at = down.position + origin
            if (pad.bounds?.contains(at) == true || pad.fields.values.any { it.contains(at) }) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                val far = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                val long = change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                if (event.changes.size > 1 || far || long) return@awaitEachGesture
                if (!change.pressed) {
                    pad.close()
                    return@awaitEachGesture
                }
            }
        }
    }
}

/** Marks a spot as the number pad's own ground: a tap on it does not close the pad (the number boxes; the set's tick box). */
private fun Modifier.padGround(): Modifier = composed {
    val pad = LocalPad.current
    val me = remember { Any() }
    if (pad != null) DisposableEffect(pad) { onDispose { pad.fields.remove(me) } }
    onGloballyPositioned { if (pad != null) pad.fields[me] = it.boundsInWindow() }
}

/** A weight or reps box. Tapping it opens the app's own number pad, not the phone's keyboard. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadField(value: String, placeholder: String, focused: Boolean, modifier: Modifier, description: String, tint: Color? = null, onClick: () -> Unit) {
    // Once the pad is up (it takes its place at once), the box being typed into is brought back above it.
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val padFor = LocalPad.current
    LaunchedEffect(focused) {
        if (focused) {
            // Once the pad has its height (the frame after it appears), the box is brought clear of the whole pad, which floats over the list.
            androidx.compose.runtime.withFrameNanos { }
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + (padFor?.height ?: 0).toFloat()))
        }
    }
    val skin = workoutSkin()
    Box(
        modifier.onSizeChanged { size = it }.padGround().bringIntoViewRequester(requester).clip(SmallShape).background(skin.boxFill)
            .border(if (focused) skin.focusWidth else skin.lineWidth, if (focused) skin.focusBorder else skin.boxBorder, SmallShape)
            .clickable(onClickLabel = "Edit", onClick = onClick).semantics { contentDescription = description }.padding(vertical = Space.s, horizontal = Space.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.ifEmpty { placeholder }, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lightAlpha(0.5f, 0.9f)) else tint ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The number pad for weights and reps: digits on the left; on the right, putting it away, a step
 * up and a step down (a weight step, or one rep), and on to the next box.
 */
@Composable
private fun Keypad(meta: PadMeta, rpe: String?, onKey: (String) -> Unit, onRpe: (String) -> Unit, onNext: () -> Unit, onHide: () -> Unit) {
    val haptic = rememberHaptic()
    // [rpe] is the set's value ("" for none); null when the user has switched RPE off.
    var picking by remember { mutableStateOf(false) }
    val skin = workoutSkin()
    @Composable
    fun RowScope.Key(label: String, side: Boolean = false, main: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, spoken: String = label, width: Float = 1f, onClick: () -> Unit) {
        val back = when {
            main -> MaterialTheme.colorScheme.primary
            side -> skin.keyFill
            else -> Color.Transparent
        }
        val front = if (main) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        val line = if (main || skin.keyBorder == Color.Transparent) Color.Transparent else skin.keyBorder.copy(alpha = if (enabled) 1f else 0.25f)
        Box(
            // 54 dp less the 3 dp air on each side: every key is at least 48 dp to the finger.
            Modifier.weight(width).height(54.dp).padding(Space.xs).clip(cardShape()).background(back).border(skin.lineWidth, line, cardShape())
                .pressable(enabled, dip = 0.92f) { haptic(Haptic.Tick); onClick() }
                .semantics { role = Role.Button; if (spoken != label) contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Icon(icon, label, tint = front)
            else Text(label, style = if (side || main) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = if (enabled) front else front.copy(alpha = lightAlpha(0.25f, 0.5f)))
        }
    }
    // Glass over the list that scrolls under it (solid where there is no blur source).
    Surface(color = Color.Transparent, shape = SheetTopShape, modifier = Modifier.glass(SheetTopShape, LocalHaze.current, lift = 10.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.s, vertical = Space.s)) {
            val step = shortNumber(meta.step)
            // How hard the set felt: the scale opens above the keys in two rows, and the value chosen is tapped again to clear it.
            if (picking && rpe != null) for (line in RPE_VALUES.chunked(5)) Row(Modifier.padding(bottom = Space.xs)) {
                for (value in line) {
                    val on = value == rpe
                    Text(
                        value, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(Space.xxs).clip(SmallShape).background(if (on) MaterialTheme.colorScheme.primary else skin.keyFill)
                            .border(skin.lineWidth, if (on || skin.keyBorder == Color.Transparent) Color.Transparent else skin.keyBorder, SmallShape)
                            .selectable(on, role = Role.RadioButton) { onRpe(if (on) "" else value) }.semantics { contentDescription = "RPE $value" }
                            .heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                // The shorter second row keeps the first row's widths.
                repeat(5 - line.size) { Spacer(Modifier.weight(1f).padding(Space.xxs)) }
            }
            // Five columns: the digits (the close key under 7), then a column of steps and effort, then Next, Same and Delete, each a key of its own.
            Row {
                for (d in "123") Key(d.toString()) { onKey(d.toString()) }
                Key("−$step", side = true, spoken = "Down $step") { onKey("-") }
                Key("Next", main = true, onClick = onNext)
            }
            Row {
                for (d in "456") Key(d.toString()) { onKey(d.toString()) }
                Key("+$step", side = true, spoken = "Up $step") { onKey("+") }
                Key("Same", side = true, enabled = meta.above.isNotEmpty(), spoken = "Same as the set above") { onKey("same") }
            }
            Row {
                for (d in "789") Key(d.toString()) { onKey(d.toString()) }
                // RPE when switched on; its place stays empty when it is off.
                if (rpe != null) Key(rpe.ifEmpty { "RPE" }, side = true, spoken = if (rpe.isEmpty()) "Effort, RPE" else "Effort, RPE $rpe") { picking = !picking }
                else Spacer(Modifier.weight(1f))
                Key("⌫", side = true, spoken = "Delete") { onKey("back") }
            }
            // The close key under 7, 0 under 8, the point under 9; the two columns to the right are empty on this row.
            Row {
                Key("Hide", side = true, icon = CleanIcons.ChevronDown, spoken = "Close keyboard", onClick = onHide)
                Key("0") { onKey("0") }
                Key(".", enabled = meta.decimal, spoken = "Decimal point") { onKey(".") }
                Spacer(Modifier.weight(2f))
            }
        }
    }
}

/** The scale for how hard a set felt, as Strong has it: 6 to 10 in half steps. */
private val RPE_VALUES = listOf("6", "6.5", "7", "7.5", "8", "8.5", "9", "9.5", "10")

/**
 * The chooser over the user's exercises and the library (round 28, replacing the card in the middle of the screen): a [PickerSheet] with the search pill
 * always out at the top (the Library's pill, [SearchOpener.pinned]), the filter chips, then the Library's list: Favourites, Recent with the last day and
 * best set of each, and A to Z. A name that matches nothing can be created.
 *
 * One tap on a row picks that exercise ([onPick]: in a workout it is added to the end, the queue). With [onPickNext] each row also has a small "Do next"
 * button that picks it to go right after the exercise being done now. With [onPickMany] every row has a ring, and ticking one starts a multi-select: a
 * tap on a row then ticks it, the rings show the order they will be added in, and the bar at the foot ([confirm], "Add 2") hands over the names in that
 * order; [multiOnly] makes a tap tick from the start (the Workout sheet's "Pick exercises", where there is no Do next).
 */
@Composable
fun ExercisePicker(
    model: AppModel, workouts: List<Workout>, onPick: (String) -> Unit, onDismiss: () -> Unit, onPickMany: ((List<String>) -> Unit)? = null, replacing: String? = null,
    title: String = if (replacing != null) "Replace exercise" else "Choose an exercise", onPickNext: ((String) -> Unit)? = null,
    multiOnly: Boolean = false, confirm: String = "Add",
) {
    val settings by model.settings.collectAsState()
    val all = remember(workouts, settings) { allExercises(workouts, settings.keys) }
    // The muscles the list shows are the user's own where set, as everywhere else.
    val musclesOf = remember(settings) { { name: String -> model.musclesOf(name) } }
    // Replacing [replacing]: the exercises worth swapping it for, from the library and the user's own (clean.core.swapSuggestions).
    val suggested = remember(replacing, all, settings) {
        if (replacing == null) emptyList() else clean.core.swapSuggestions(replacing, all.map { it.name }, musclesOf, all.filter { it.sessions > 0 }.associate { it.name to it.sessions })
    }
    // With [onPickMany] several can be ticked, in the order they are to be added.
    val ticked = remember { mutableStateListOf<String>() }
    val search = rememberSearchOpener().also { it.pinned = true; it.open = true }
    val nextButton: (@Composable (String) -> Unit)? = if (onPickNext != null) { name -> DoNext(name) { onPickNext(name) } } else null
    PickerSheet(
        title, onDismiss,
        bottom = if (onPickMany != null && ticked.isNotEmpty()) ({
            MainButton("$confirm ${ticked.size}", { onPickMany(ticked.toList()) }, Modifier.padding(Space.l).fillMaxWidth())
        }) else null,
    ) {
        ExerciseList(
            all, musclesOf, onClick = onPick, onCreate = onPick, modifier = Modifier.weight(1f),
            favourites = settings.filterValues { it.favourite }.keys, hideLibrary = true, unitOf = model::unitOf,
            ticked = if (onPickMany != null) ticked else null, onTick = { if (!ticked.remove(it)) ticked.add(it) }, tapTicks = multiOnly,
            suggested = suggested, search = search,
            trailing = nextButton,
        )
    }
}

/** The small "Do next" at the end of a picker row: the exercise goes right after the one being done now instead of to the end. */
@Composable
private fun DoNext(exercise: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 36.dp).tile(PillShape).clip(PillShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "Do $exercise next" }.padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(CleanIcons.Forward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Do next", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
/**
 * Correcting a finished workout (`AppModel.correction`, opened from its page's Edit): its own page, apart from any workout in
 * progress. The name, the note, the start and the end can be changed, and the cards are the workout's own with no ticks: every
 * set that holds its values counts. There is no clock, rest, banner or notification. Save puts the corrected workout in place of
 * the old one; leaving with changes asks first.
 */
@Composable
fun CorrectScreen(model: AppModel) {
    val draft = model.correction
    if (draft == null) {
        // Only while this page is the one on top: as it goes it must not take the page under it along.
        LaunchedEffect(Unit) { if (model.stack.lastOrNull() == Screen.Correct) model.back() }
        return
    }
    val slot = model.correctionSlot
    val workouts by model.workouts.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val offerUndo = { what: String, restore: () -> Unit -> model.say(what) { restore() } }
    val stored by model.options.collectAsState()
    val keys = remember(draft.blocks) { cardKeys(draft.blocks) }
    val keysNow by rememberUpdatedState(keys)
    val pad = remember { Pad() }
    val actions = remember(pad) { PadActions(pad, slot) { keysNow } }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) > 0 }.distinctUntilChanged().collect { if (it) pad.close() }
    }
    fun leave() {
        if (model.correctionChanged) leaving = true
        else {
            model.discardCorrection()
            model.back()
        }
    }
    BackHandler(enabled = pad.target != null) { pad.close() }
    BackHandler(enabled = pad.target == null) { leave() }
    val start = draft.pastDate?.let { clean.core.parseStrongDate(it) }
    val seconds = draft.pastSeconds ?: 0L
    val timesOk = start != null && seconds > 0

    fun save() {
        pad.close()
        if (model.saveCorrection()) {
            model.back()
            model.message = "Workout corrected"
        } else model.message = if (!timesOk) "The end must be after the start" else "No set holds its values, so there is nothing to save"
    }

    /** A card changed (or was removed, when [changed] is null); a set or an exercise taken away can be put back for a few seconds. */
    fun blockChanged(index: Int, block: DraftBlock, changed: DraftBlock?) {
        val current = slot.draft ?: return
        val blocks = current.blocks.toMutableList()
        if (changed == null) blocks.removeAt(index) else blocks[index] = changed
        if (changed == null) offerUndo("Exercise removed") { slot.put(current) }
        else if (changed.items.count { it is DraftSet } < block.items.count { it is DraftSet }) offerUndo("Set removed") { slot.put(current) }
        slot.put(current.copy(blocks = blocks))
    }

    // A swipe that would tick a set does nothing here; one that removes still removes.
    val feel = Feel(if (LocalMotion.current) "light" else "off")
    CompositionLocalProvider(LocalFeel provides feel, LocalDraftSlot provides slot, LocalPad provides pad) {
        Scaffold(
            modifier = Modifier.closePadOnTap(pad).imePadding(), containerColor = pageGround(),
            topBar = {
                // The page's own bar, as every page has; Back asks first when something was changed.
                androidx.compose.runtime.CompositionLocalProvider(LocalPageBackdrop provides null) { BackBar("Correct workout", ::leave) {
                    MainButton("Save", ::save, Modifier.padding(end = Space.s), enabled = timesOk, contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s))
                } }
            },
            bottomBar = {
                AnimatedVisibility(pad.target != null, enter = Motion.appear(LocalMotion.current), exit = Motion.vanish(LocalMotion.current)) {
                    val rpeOn = model.flag("workout.rpe")
                    val rpeNow = pad.target?.let { t -> (draft.blocks.getOrNull(keys.indexOf(t.card))?.items?.getOrNull(t.item) as? DraftSet)?.rpe }.orEmpty()
                    Box(Modifier.onGloballyPositioned { pad.bounds = it.boundsInWindow() }) {
                        Keypad(pad.meta, if (rpeOn) rpeNow else null, actions::key, { value -> actions.set { it.copy(rpe = value) } }, actions::next, onHide = pad::close)
                    }
                }
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item { CorrectHeader(model, draft) }
                itemsIndexed(draft.blocks, key = { i, _ -> keys[i] }) { index, block ->
                    val last = draft.blocks.lastIndex
                    // Moved from the card's menu; there is no holding and dragging here.
                    val reorder = remember(index, last) {
                        Reorder(
                            collapsed = false, lifted = false, canUp = index > 0, canDown = index < last, move = { model.moveExercise(index, it, slot) },
                            start = {}, drag = {}, end = {}, held = false, offset = { 0f }, next = false, superset = { model.toggleSuperset(index, slot) },
                        )
                    }
                    BlockCard(model, workouts, block, reorder, keys[index], pad, Modifier, onSwitch = { model.switchVariation(index, it, slot) }, onUndo = offerUndo) { changed -> blockChanged(index, block, changed) }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        QuietButton("Add exercise", { picking = true }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(120.dp))
                    }
                }
            }
        }
    }
    if (picking) ExercisePicker(
        model, workouts, onPick = { model.addExercise(it, slot); picking = false }, onDismiss = { picking = false },
        onPickMany = { names -> names.forEach { model.addExercise(it, slot) }; picking = false },
        title = "Add exercise",
    )
    if (leaving) Confirm("Discard changes?", "The workout stays as it was saved.", "Discard", {
        model.discardCorrection()
        model.back()
    }) { leaving = false }
}

/** The Correct page's top: the name and note, the start and end (the phone's pickers) and the time between them. */
@Composable
private fun CorrectHeader(model: AppModel, draft: Draft) {
    val platform = LocalPlatform.current
    val start = draft.pastDate?.let { clean.core.parseStrongDate(it) }
    val seconds = draft.pastSeconds ?: 0L
    val end = start?.plusSeconds(seconds)
    fun put(newStart: LocalDateTime, newEnd: LocalDateTime) = model.putCorrection(
        draft.copy(pastDate = clean.core.STRONG_DATE_FORMAT.format(newStart), pastSeconds = Duration.between(newStart, newEnd).seconds),
    )
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
        BasicTextField(
            value = draft.name,
            onValueChange = { model.putCorrection(draft.copy(name = it)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
        NoteLine(draft.note, { model.putCorrection(draft.copy(note = it)) }) { TagLine(model, draft.tag) { model.putCorrection(draft.copy(tag = it)) } }
        if (start != null && end != null) {
            // Moving the start keeps the end where it is, so the length follows; moving the end changes only the length.
            LabelValue("Start", prettyDate(clean.core.STRONG_DATE_FORMAT.format(start))) { platform.pickDateTime(start) { put(it, end) } }
            LabelValue("End", prettyDate(clean.core.STRONG_DATE_FORMAT.format(end))) { platform.pickDateTime(end) { put(start, it) } }
            if (seconds > 0) LabelValue("Duration", duration(seconds))
            else Text("The end must be after the start.", Modifier.padding(vertical = Space.xs), style = MaterialTheme.typography.bodySmall, color = LocalCleanColors.current.danger)
        }
    }
}

/** What a hardware key types into the number pad: a digit, the point (comma too) or Backspace as the pad's own key names; null for any other key. */
fun padKeyOf(key: Key): String? = when (key) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    Key.Period, Key.NumPadDot, Key.Comma, Key.NumPadComma -> "."
    Key.Backspace -> "back"
    else -> null
}
