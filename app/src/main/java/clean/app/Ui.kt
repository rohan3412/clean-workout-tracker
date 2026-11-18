package clean.app

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.abs
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import clean.app.ui.theme.orange
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import clean.core.Muscle
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import clean.app.ui.theme.buttonShape
import clean.app.ui.theme.GlassWindowShape
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.LocalLook
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * The pieces every screen is built from, so that a button, a badge or a menu looks and moves the
 * same everywhere. The rules behind them are in docs/design.md.
 */

/** Whether things move: off when the user has switched animations off in Settings. */
val LocalMotion = staticCompositionLocalOf { true }

/** How far something has dipped under a press: 1 at rest, a little less while a finger is on it. */
@Composable
fun pressScale(source: InteractionSource, to: Float = 0.96f): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed && LocalMotion.current) to else 1f, Motion.press(), label = "press")
}

/** A tap target that dips while pressed, on top of the usual ripple. For cards, tiles and boxes; plain rows keep [clickable]. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    // The dip starts as the finger lands. Inside a scrolling list the usual pressed state is held back a
    // tenth of a second (in case the touch is the start of a scroll), which made taps feel late.
    val motion = LocalMotion.current
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down && motion) 0.96f else 1f, Motion.pressNow(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(enabled = enabled, onClick = onClick)
        // After clickable, so a child that took the press has already used it up and the card stays still.
        .pointerInput(enabled) {
            if (enabled) awaitEachGesture {
                awaitFirstDown(requireUnconsumed = true)
                down = true
                try { waitForUpOrCancellation() } finally { down = false }
            }
        }
}

/**
 * Black or near-white words, whichever reads on [this] as a background: the one rule for text on a coloured band or cell.
 * One threshold, so a note band and a calendar cell agree.
 */
fun Color.readableOn(): Color = if (luminance() > 0.4f) Color.Black else Color(0xFFF2F3F5)

/** At least 48 dp each way, the least a finger can be expected to hit. */
fun Modifier.minTouch(): Modifier = defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)

/** A hit area of 48 dp centred on a smaller thing, which keeps its own size in the layout (the area overlaps its neighbours' air). */
fun Modifier.reach(): Modifier = requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)

private val BUTTON_PADDING = PaddingValues(horizontal = Space.xl, vertical = Space.m)

/** The one thing a page is for: filled with the accent. At most one on a page. */
@Composable
fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null, contentPadding: PaddingValues = BUTTON_PADDING) {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    Button(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled, shape = buttonShape(), contentPadding = contentPadding, interactionSource = source) {
        ButtonLabel(text, summary)
    }
}

/** A button's words, with an optional line under them saying what it does. */
@Composable
private fun ButtonLabel(text: String, summary: String?) {
    if (summary == null) Text(text, fontWeight = FontWeight.SemiBold)
    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontWeight = FontWeight.SemiBold)
        Text(summary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.alpha(0.8f))
    }
}

/** Everything else that is a button: a neutral tint with the accent for its words. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, summary: String? = null, contentPadding: PaddingValues = BUTTON_PADDING) {
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    FilledTonalButton(
        onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled, shape = buttonShape(), contentPadding = contentPadding, interactionSource = source,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.primary),
    ) { ButtonLabel(text, summary) }
}

/** A button that throws something away: red words, no fill, so it never looks like the way forward. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier, shape = buttonShape(), colors = ButtonDefaults.textButtonColors(contentColor = LocalCleanColors.current.danger)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** A small rounded tag: a PR, a count. Green unless told otherwise. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = LocalCleanColors.current.done, role: String? = null) {
    // With a [role] the words travel to the same thing on the page a card opens ([MorphText]); the badge's own tint stays and fades with the card.
    MorphText(
        androidx.compose.ui.text.AnnotatedString(text), role, modifier.background(color.copy(alpha = 0.16f), SmallShape).padding(horizontal = Space.s, vertical = Space.xxs),
        color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
    )
}

/** The small spaced capitals over a column or a group; always muted, so the numbers under it lead. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, align: TextAlign? = null, role: String? = null) {
    if (role != null) {
        MorphText(
            androidx.compose.ui.text.AnnotatedString(text.uppercase()), role, modifier, textAlign = align, maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text.uppercase(), modifier, textAlign = align, maxLines = 1,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** How much of a chart's line is drawn: it runs from 0 to 1 once, when the chart first shows. */
@Composable
fun drawnIn(): State<Float> {
    val motion = LocalMotion.current
    // A page growing out of a card is already drawn: what is only on the page fades in where it rests, nothing draws itself in.
    val growing = LocalPageCard.current != null
    val drawn = remember { Animatable(if (motion && !growing) 0f else 1f) }
    LaunchedEffect(Unit) { drawn.animateTo(1f, Motion.slow()) }
    return drawn.asState()
}

/**
 * A ring filled clockwise from the top to [progress] (0 to 1) in [color], on a quiet track, with [content] in the middle.
 * A change in [progress] sweeps to its new value; [from], when given, is where the first sweep starts (0 for a ring that
 * fills as it appears). Still when animations are off.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Dp = 6.dp,
    from: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = LocalMotion.current
    val target = progress.coerceIn(0f, 1f)
    val sweep = remember { Animatable(if (motion) from ?: target else target) }
    LaunchedEffect(target, motion) { if (motion) sweep.animateTo(target, Motion.slow()) else sweep.snapTo(target) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val arc = color
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val width = stroke.toPx()
            val box = Size(size.width - width, size.height - width)
            val corner = Offset(width / 2, width / 2)
            drawArc(track, 0f, 360f, false, corner, box, style = Stroke(width))
            if (sweep.value > 0f) drawArc(arc, -90f, 360f * sweep.value, false, corner, box, style = Stroke(width, cap = StrokeCap.Round))
        }
        content()
    }
}

/** What a list says when there is nothing in it yet: a line of what belongs here and, if there is one, the way to add it. */
@Composable
fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}, other: String? = null, onOther: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xxxl, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionTitle(title, textAlign = TextAlign.Center)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) QuietButton(action, onAction, Modifier.padding(top = Space.s))
        // A second way in, when there are two.
        if (other != null) QuietButton(other, onOther)
    }
}

/**
 * Hand-written sample figures for [EmptyPreview]: never the user's data. A gently rising line, a few bars, a body's worth of rows.
 * Kept here so the samples stay small and in one place.
 */
object PreviewSamples {
    val LINE = listOf(0.20f, 0.28f, 0.26f, 0.40f, 0.46f, 0.58f, 0.64f, 0.80f)
    val BARS = listOf(0.35f, 0.6f, 0.45f, 0.8f, 0.55f, 0.9f, 0.7f)
    val ROWS = listOf(0.9f, 0.7f, 0.55f, 0.4f, 0.25f)

    /** A rising line with a dot at its end, in the theme's accent (grey in Mono). */
    @Composable
    fun Line(height: Dp = 96.dp) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val pad = 6.dp.toPx()
            val step = (size.width - 2 * pad) / (LINE.size - 1)
            val points = LINE.mapIndexed { i, v -> Offset(pad + i * step, size.height - pad - v * (size.height - 2 * pad)) }
            for (i in 0 until points.size - 1) drawLine(color, points[i], points[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color, 4.dp.toPx(), points.last())
        }
    }

    /** Vertical bars of different heights. */
    @Composable
    fun Bars(height: Dp = 72.dp, bars: List<Float> = BARS) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val gap = 6.dp.toPx()
            val w = (size.width - gap * (bars.size - 1)) / bars.size
            bars.forEachIndexed { i, v -> drawRect(color, Offset(i * (w + gap), size.height * (1 - v)), Size(w, size.height * v)) }
        }
    }

    private val SHADING = mapOf(
        Muscle.CHEST to 0.9, Muscle.SHOULDERS to 0.7, Muscle.TRICEPS to 0.6, Muscle.BICEPS to 0.5, Muscle.LATS to 0.8, Muscle.QUADS to 0.5, Muscle.ABS to 0.3,
    )

    /** The body figure with a few muscles shaded, as a week's training would. */
    @Composable
    fun Figure() {
        val palette = LocalLook.current.scale
        BodyFigure(SHADING.mapValues { (_, v) -> shade(palette, v) }, interactive = false)
    }

    /** Three empty photo frames side by side. */
    @Composable
    fun Frames() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            repeat(3) { Box(Modifier.weight(1f).aspectRatio(3f / 4f).background(MaterialTheme.colorScheme.primary, cardShape())) }
        }
    }

    /** Horizontal bars, longest first: a list of muscles or lifts. */
    @Composable
    fun Rows(height: Dp = 72.dp, rows: List<Float> = ROWS) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val gap = 6.dp.toPx()
            val h = (size.height - gap * (rows.size - 1)) / rows.size
            rows.forEachIndexed { i, v -> drawRect(color, Offset(0f, i * (h + gap)), Size(size.width * v, h)) }
        }
    }
}

/**
 * What a screen or card shows while there is too little to draw: [preview] (sample figures from [PreviewSamples]) faint and
 * out of reach, one line [need] saying how much more to log, and, where it makes sense, a quiet [action]. The preview is cleared
 * from the semantics, so a screen reader hears only the line and the button.
 */
@Composable
fun EmptyPreview(need: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(Modifier.graphicsLayer { alpha = 0.3f }.clearAndSetSemantics { }) { preview() }
        Text(need, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) QuietButton(action, onAction)
    }
}

/**
 * What a swipe does: an icon and a word on the colour that opens up behind the row, and the action itself.
 * [leaves] slides the row off the screen first (a removal); otherwise it springs back (a tick).
 */
class SwipeAction(val word: String, val icon: ImageVector, val color: Color, val leaves: Boolean, val onTrigger: () -> Unit)

/** A row that is removed by swiping it to the left; see [SwipeRow]. */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SwipeRow(left = SwipeAction("Remove", CleanIcons.Delete, LocalCleanColors.current.danger, true, onRemove), modifier = modifier, content = content)
}

/**
 * A row with an action for a swipe to the [left] and one for a swipe to the [right] (either may be null). The colour of
 * the action opens up behind the row as it moves, with its icon and word, and turns solid, with a buzz, once the swipe
 * has gone far enough to act on letting go; short of that the row springs back. A screen reader gets both as custom
 * actions. [buzz] is the haptic at the threshold; by default a long press, in a workout the tick's own (`feedback.tick`).
 */
@Composable
fun SwipeRow(left: SwipeAction?, right: SwipeAction? = null, modifier: Modifier = Modifier, buzz: ((androidx.compose.ui.hapticfeedback.HapticFeedback) -> Unit)? = null, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    var width by remember { mutableStateOf(0) }
    val leftNow by rememberUpdatedState(left)
    val rightNow by rememberUpdatedState(right)
    val buzzNow by rememberUpdatedState(buzz)
    val past = abs(offset.value) > threshold
    LaunchedEffect(past) { if (past) buzzNow?.invoke(haptic) ?: haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Box(modifier.onSizeChanged { width = it.width }) {
        val toLeft = offset.value < 0
        val action = if (toLeft) left else right
        val open = with(density) { abs(offset.value).toDp() }
        if (open > 0.dp && action != null) Box(Modifier.matchParentSize().padding(vertical = Space.xxs), contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.fillMaxHeight().width(open).background(if (past) action.color else action.color.copy(alpha = 0.45f), SmallShape), contentAlignment = Alignment.Center) {
                val grow by animateFloatAsState(if (past) 1.25f else 1f, Motion.short(), label = "bin")
                // Icon and word, so the meaning never rests on the colour.
                if (open > 40.dp) Column(Modifier.graphicsLayer { scaleX = grow; scaleY = grow }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(action.icon, null, tint = action.color.readableOn())
                    if (open > 72.dp) Text(action.word, style = MaterialTheme.typography.labelSmall, color = action.color.readableOn(), maxLines = 1)
                }
            }
        }
        Box(
            // The swipe is the fast way; a screen reader gets the same actions.
            Modifier.semantics {
                customActions = listOfNotNull(
                    left?.let { a -> CustomAccessibilityAction(a.word) { leftNow?.onTrigger?.invoke(); true } },
                    right?.let { a -> CustomAccessibilityAction(a.word) { rightNow?.onTrigger?.invoke(); true } },
                )
            }.offset { IntOffset(offset.value.roundToInt(), 0) }.then(if (left == null && right == null) Modifier else Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val act = if (offset.value < 0) leftNow else rightNow
                            if (act != null && abs(offset.value) > threshold) {
                                if (act.leaves) {
                                    offset.animateTo(if (offset.value < 0) -width.toFloat() else width.toFloat(), Motion.leave())
                                    act.onTrigger()
                                    offset.snapTo(0f)
                                } else {
                                    act.onTrigger()
                                    offset.animateTo(0f, Motion.follow())
                                }
                            } else offset.animateTo(0f, Motion.follow())
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, Motion.follow()) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch {
                        val next = offset.value + amount
                        offset.snapTo(if (next < 0 && leftNow == null || next > 0 && rightNow == null) 0f else next)
                    }
                }
            }),
        ) { content() }
    }
}

/** How many [Sheet] windows are open; the page behind shrinks a little and dims while any is (B6). */
object SheetDepth {
    var open by mutableStateOf(0)
}

/**
 * A menu or a set of choices at the bottom of the screen, in place of a grey box in the middle of it. It blinks in and out in place
 * (its own window, [BlinkWindow]), on the one glass material in its solid form (a window has nothing to blur). A grab handle and the
 * title are its header: a drag down on the header follows the finger and lets it go past a quarter of its height or a quick flick;
 * the content below scrolls. A tap outside, Back or "Close" for a screen reader closes it, and while it is open the page behind
 * shrinks and dims ([SheetDepth]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) { SheetDepth.open++; onDispose { SheetDepth.open-- } }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BlinkWindow()
        val drag = remember { Animatable(0f) }
        var height by remember { mutableStateOf(1) }
        val scope = rememberCoroutineScope()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).statusBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .graphicsLayer { translationY = drag.value }
                    .glass(clean.app.ui.theme.SheetTopShape, haze = null, lift = 16.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { },
                shape = clean.app.ui.theme.SheetTopShape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.m)) {
                    Column(
                        Modifier.fillMaxWidth().draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                            onDragStopped = { v -> if (drag.value > height / 4f || v > 1800f) onDismiss() else drag.animateTo(0f, Motion.follow()) },
                        ),
                    ) {
                        androidx.compose.material3.BottomSheetDefaults.DragHandle(
                            Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Drag down to close"; onClick("Close") { onDismiss(); true } },
                        )
                        if (title != null) Text(title, Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.s).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
    }
}

/** One choice in a [Sheet]: an icon, what it does, and what it is set to now. Something that deletes is [danger] and goes last. */
@Composable
fun SheetRow(text: String, icon: ImageVector? = null, value: String = "", danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        danger -> LocalCleanColors.current.danger
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = Space.xl, vertical = Space.l),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = if (danger || !enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        else Spacer(Modifier.width(Space.xxl))
        Text(text, Modifier.weight(1f), color = tint, style = MaterialTheme.typography.bodyLarge)
        if (value.isNotEmpty()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The thin line that separates groups of rows in a [Sheet]. */
@Composable
fun SheetGap() {
    HorizontalDivider(Modifier.padding(vertical = Space.s), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * A sideways swipe that steps to the previous or the next of something: a tab, a view of a figure.
 * A drag that a list has already taken for scrolling up or down is left alone, and once the swipe
 * is under way nothing beneath it gets a tap from the same touch. A second finger cancels it.
 */
@Composable
fun Modifier.swipeSteps(onPrevious: () -> Unit, onNext: () -> Unit, follow: ((Float?) -> Unit)? = null): Modifier {
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val following by rememberUpdatedState(follow)
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val far = 56.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dx = 0f
            var dy = 0f
            var mine = false
            while (true) {
                val event = awaitPointerEvent()
                // A second finger makes it a pinch, not a swipe.
                if (event.changes.count { it.pressed } > 1) { if (mine) following?.invoke(null); return@awaitEachGesture }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!mine && change.isConsumed) return@awaitEachGesture
                val moved = change.positionChange()
                dx += moved.x
                dy += moved.y
                if (!mine && abs(dx) > slop && abs(dx) > 1.5f * abs(dy)) mine = true
                if (mine) change.consume()
                // Whoever asked sees the swipe as it goes (the distance so far), and null once it is over, after any step.
                if (mine) following?.invoke(dx)
                if (!change.pressed) break
            }
            if (mine && abs(dx) > far) if (dx < 0) next() else previous()
            if (mine) following?.invoke(null)
        }
    }
}

/** How much room a tab's list leaves at its end for the floating tab bar (and the banner docked on it); zero everywhere but inside the tabs. */
val LocalBarInset = staticCompositionLocalOf { 0.dp }

/** Which tabs' lists are scrolled to their end, where the tab bar settles into the bottom edge. */
val LocalBarEnds = staticCompositionLocalOf<MutableMap<Tab, Boolean>> { mutableMapOf() }

/** Tells the tab bar whether [state]'s list is scrolled to its end. */
@Composable
fun BarEnd(tab: Tab, state: LazyListState) {
    val ends = LocalBarEnds.current
    LaunchedEffect(state) { snapshotFlow { !state.canScrollForward }.collect { ends[tab] = it } }
}

/** The empty space at the end of a tab's list that lets its last item scroll clear of the tab bar. */
@Composable
fun BarSpace() {
    Spacer(Modifier.height(LocalBarInset.current + Space.xxl))
}


/** A dialog with AlertDialog's parameters, drawn as glass in its solid form (a dialog is its own window, so nothing can be blurred behind it). Date and time pickers are the platform's and stay as they are. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, modifier = Modifier.glass(GlassWindowShape, haze = null, lift = 18.dp), containerColor = Color.Transparent, tonalElevation = 0.dp, shape = GlassWindowShape,
        confirmButton = { BlinkWindow(); confirmButton() }, dismissButton = dismissButton, title = title, text = text, properties = properties)
}

/**
 * The one Undo bar, wherever something can be taken back: what was done, Undo, and a line under them that drains over the time
 * left ([UNDO_MILLIS], started again by each further step). The line stands still when animations are off.
 */
@Composable
fun UndoBar(model: AppModel, modifier: Modifier = Modifier) {
    val undos = model.undos
    val motion = LocalMotion.current
    AnimatedVisibility(undos != null, modifier, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
        // Kept while the bar fades away, so its words do not vanish first.
        val shown = remember { mutableStateOf(undos) }
        if (undos != null) shown.value = undos
        val left = remember { Animatable(1f) }
        LaunchedEffect(model.undoUntil) {
            val remaining = (model.undoUntil - System.currentTimeMillis()).coerceIn(0, UNDO_MILLIS)
            left.snapTo(remaining / UNDO_MILLIS.toFloat())
            if (motion) left.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing))
        }
        Surface(
            color = Color.Transparent, shape = cardShape(),
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s).glass(cardShape(), haze = null, lift = 10.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(shown.value?.text.orEmpty(), Modifier.weight(1f))
                    TextButton(onClick = model::takeBack) { Text("Undo", fontWeight = FontWeight.SemiBold) }
                }
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    Box(Modifier.fillMaxWidth(left.value).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

// --- Glass: the material of what floats (tab bar, plus button, round buttons, the Workout sheet) ---

/** The accent a running workout washes over the app (D6a), or null when none runs or on the workout page itself. Read by [glass]. */
val LocalRunTint = staticCompositionLocalOf<Color?> { null }

/** The blur source of the page behind the glass in this part of the tree, or null where there is none (glass is then solid). */
val LocalHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** True when glass may not blur: the Reduce transparency switch, the phone's own high-contrast setting, or Android below 12. Provided by the app. */
val LocalSolidGlass = staticCompositionLocalOf { true }

/** How much of the accent a running workout lays over the page: a little more in Dark, where it is harder to see. */
private fun runTint(dark: Boolean) = if (dark) 0.08f else 0.06f

private val GlassBlur = 22.dp
private val GlassBlurStyle = dev.chrisbanes.haze.HazeStyle(backgroundColor = Color.Unspecified, tints = emptyList(), blurRadius = GlassBlur, noiseFactor = 0f)
private val GlassBlurBlock: dev.chrisbanes.haze.HazeEffectScope.() -> Unit = { blurRadius = GlassBlur; noiseFactor = 0f }

/**
 * The look while a workout runs: with [running], the page colour (`background`) takes a little of the accent and [glass] tints toward it.
 * The structure is the same whether on or off, so what is inside keeps its state when a workout starts or ends.
 */
@Composable
fun RunningLook(running: Boolean, content: @Composable () -> Unit) {
    val accent = clean.app.ui.theme.LocalLook.current.orange
    val dark = clean.app.ui.theme.LocalLook.current.dark
    val scheme = MaterialTheme.colorScheme
    val page by androidx.compose.animation.animateColorAsState(
        if (running) accent.copy(alpha = runTint(dark)).compositeOver(scheme.background) else scheme.background, if (LocalMotion.current) tween(300) else snap(), label = "run tint",
    )
    MaterialTheme(colorScheme = scheme.copy(background = page), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        androidx.compose.runtime.CompositionLocalProvider(LocalRunTint provides accent.takeIf { running }, content = content)
    }
}

/**
 * The one glass surface (A1). With [haze] (a source behind it) and blur allowed, the page behind is blurred 22 dp under a tint of 74 %;
 * without, the same material is drawn with an opaque tint (97 %), so text keeps its contrast whatever scrolls behind. The material is
 * neutral: a tint with a faint top-to-bottom light, a lit top edge, a darker bottom edge, a hairline inside and a soft wide shadow ([lift]
 * is its height). While a workout runs the tint takes a little of the accent. Everything that floats is drawn with this.
 */
@Composable
fun Modifier.glass(shape: Shape, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current, lift: Dp = 14.dp): Modifier {
    val dark = clean.app.ui.theme.LocalLook.current.dark
    val blur = haze != null && !LocalSolidGlass.current
    val run = LocalRunTint.current
    val base = if (dark) Color(0xFF1E1F23) else Color.White
    val tint = if (run == null) base else androidx.compose.ui.graphics.lerp(base, run, if (dark) 0.15f else 0.10f)
    // Alpha at the top and the bottom of the tint: the least that keeps 4.5 : 1 on text over the blur, or solid.
    val top = if (!blur) 0.97f else if (dark) 0.74f else 0.85f
    val bottom = if (!blur) 0.97f else if (dark) 0.74f else 0.70f
    val shadow = if (dark) Color.Black.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.16f)
    val lit = if (dark) Color.White.copy(alpha = 0.20f) else Color.White
    val under = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.06f)
    val hair = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    return this
        .shadow(lift, shape, clip = false, ambientColor = shadow.copy(alpha = shadow.alpha * 0.5f), spotColor = shadow)
        .clip(shape)
        .then(if (blur) Modifier.hazeEffect(haze, GlassBlurStyle, GlassBlurBlock) else Modifier)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.verticalGradient(listOf(tint.copy(alpha = top), tint.copy(alpha = bottom))))
            // The faint light from above (9 % to 2 % white in Dark).
            if (dark) drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f))))
            val line = 2.dp.toPx() // half of it is outside the clip: 1 dp shows
            drawOutline(outline, hair, style = Stroke(line * 2f))
            drawOutline(outline, Brush.verticalGradient(0f to lit, 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to under), style = Stroke(line))
        }
}

/** A 34 dp round glass button with a 48 dp touch area; [content] is its icon or letter. It sits in a page, so its glass has no blur behind it. */
@Composable
fun GlassButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(34.dp).glass(CircleShape, haze = null, lift = 0.dp), contentAlignment = Alignment.Center, content = content) }
}

/**
 * A section laid out on the page (1.1.0, D2): a small grey uppercase heading, then the content, with air around it and no card.
 * Numbers, lists and charts sit like this; a card is only for one object that is opened as a whole.
 */
@Composable
fun PageSection(title: String, modifier: Modifier = Modifier, spacing: Dp = Space.m, content: @Composable ColumnScope.() -> Unit) {
    val lines = rememberRowLines()
    Column(modifier.fillMaxWidth().drawRowLines(lines).padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(spacing)) {
        if (title.isNotEmpty()) PageHeading(title)
        androidx.compose.runtime.CompositionLocalProvider(LocalRowLines provides lines) { content() }
    }
}

/** A card's soft wide shadow in Light (B4); in Dark the card is a step lighter than the page instead, so nothing is drawn. */
@Composable
fun Modifier.cardDepth(shape: Shape): Modifier {
    val look = clean.app.ui.theme.LocalLook.current
    return if (!look.dark) shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.16f)) else this
}

/**
 * A segmented control (1.1.0): a quiet track with the chosen option as a raised pill, for a few exclusive choices that switch what
 * the page shows (Week / Month / Year / All, a body view). With [morph] the chosen option's word is the page's side of a [MorphText].
 */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, morph: String? = null, onSelect: (T) -> Unit) {
    val track = MaterialTheme.shapes.medium
    val pill = MaterialTheme.shapes.small
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (option in options) {
            val on = option == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.surfaceVariant, pill) else Modifier)
                    .clip(pill)
                    .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                val ink = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val weight = if (on) FontWeight.SemiBold else FontWeight.Normal
                if (on && morph != null) MorphText(androidx.compose.ui.text.AnnotatedString(label(option)), morph, onPage = true, style = MaterialTheme.typography.labelLarge, color = ink, fontWeight = weight, maxLines = 1)
                else Text(label(option), style = MaterialTheme.typography.labelLarge, color = ink, fontWeight = weight, maxLines = 1)
            }
        }
    }
}

/** A menu of the one glass material in its solid form (menus are their own windows): the same tint, lit top edge and shadow as a sheet, the dialog's corner. */
@Composable
fun CleanMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded, onDismissRequest, modifier.glass(GlassWindowShape, haze = null, lift = 0.dp),
        containerColor = Color.Transparent, tonalElevation = 0.dp, shadowElevation = 12.dp, shape = GlassWindowShape, content = content,
    )
}

// --- D2 hairlines: a thin line between the rows of a list-like section ---

/**
 * The rows of one page section ([Section], [PageSection]) that want a hairline between them. A row ([LabelValue]) reports its place;
 * the section draws a line in the gap between two rows that sit next to each other, so a chart or a figure between rows, a lone row
 * or the section's ends get none. Places are asked for when drawing, so scrolling cannot make them stale.
 */
class RowLines {
    internal val rows = HashMap<Any, androidx.compose.ui.layout.LayoutCoordinates>()
    internal var section: androidx.compose.ui.layout.LayoutCoordinates? = null
}

val LocalRowLines = staticCompositionLocalOf<RowLines?> { null }

@Composable
fun rememberRowLines() = remember { RowLines() }

/** Tells the section where this row is for as long as it is on the screen. */
@Composable
fun Modifier.rowLine(lines: RowLines): Modifier {
    val key = remember { Any() }
    androidx.compose.runtime.DisposableEffect(key) { onDispose { lines.rows.remove(key) } }
    return onGloballyPositioned { lines.rows[key] = it }
}

/** Draws the hairlines for [lines] in the gaps between neighbouring rows (the gap is the section's spacing, so a farther one is not a neighbour). */
@Composable
fun Modifier.drawRowLines(lines: RowLines): Modifier {
    val colour = MaterialTheme.colorScheme.outlineVariant
    return onGloballyPositioned { lines.section = it }.drawBehind {
        val section = lines.section?.takeIf { it.isAttached } ?: return@drawBehind
        val spans = lines.rows.values.filter { it.isAttached }.map { row ->
            val top = section.localPositionOf(row, Offset.Zero).y
            top to top + row.size.height
        }.sortedBy { it.first }
        val limit = 14.dp.toPx()
        for (i in 0 until spans.size - 1) {
            val gap = spans[i + 1].first - spans[i].second
            if (gap in -1f..limit) {
                val y = (spans[i].second + spans[i + 1].first) / 2f
                drawLine(colour, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
    }
}

/**
 * The one line that says what happened (B8): glass, at the top, for four seconds (six with an action such as View). What was said is
 * [AppModel.notice]; [AppModel.say] has its own bar, [UndoBar], in the same material.
 */
@Composable
fun NoticeToast(model: AppModel, modifier: Modifier = Modifier) {
    val notice = model.notice
    val motion = LocalMotion.current
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        kotlinx.coroutines.delay(if (notice.action != null) 6000L else 4000L)
        if (model.notice === notice) model.notice = null
    }
    AnimatedVisibility(notice != null, modifier, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
        // Kept while the toast fades away, so its words do not vanish first.
        val shown = remember { mutableStateOf(notice) }
        if (notice != null) shown.value = notice
        Surface(
            color = Color.Transparent, shape = cardShape(),
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s).glass(cardShape(), haze = null, lift = 10.dp)
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        ) {
            Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(shown.value?.text.orEmpty(), Modifier.weight(1f).padding(vertical = Space.m))
                shown.value?.let { n -> if (n.action != null) TextButton(onClick = { model.notice = null; n.onAction?.invoke() }) { Text(n.action, fontWeight = FontWeight.SemiBold) } }
            }
        }
    }
}
