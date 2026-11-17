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
import clean.app.ui.theme.workoutSkin
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.Offset
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
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.onInk
import clean.app.ui.theme.ringTrack
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
    // Linen's main buttons are in the text colour with the page's colour for words (black and white by day); every other look keeps the accent.
    val look = LocalLook.current
    val colors = if (look.inkMainButton) ButtonDefaults.buttonColors(containerColor = look.text, contentColor = look.onInk) else ButtonDefaults.buttonColors()
    Button(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled, shape = buttonShape(), colors = colors, contentPadding = contentPadding, interactionSource = source) {
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
    // Mono: paper with an ink outline, not a grey fill.
    val mono = LocalLook.current.mono
    FilledTonalButton(
        onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled, shape = buttonShape(), contentPadding = contentPadding, interactionSource = source,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (mono) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.primary),
        border = if (mono) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.25f)) else null,
    ) { ButtonLabel(text, summary) }
}

/** A button that throws something away: red words, no fill, so it never looks like the way forward. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier, shape = buttonShape(), colors = ButtonDefaults.textButtonColors(contentColor = LocalCleanColors.current.danger),
        border = if (LocalLook.current.mono) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface) else null) {
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
    // Linen: a dark arc on a white track, as the ring sits in a grey tile.
    val linen = LocalLook.current.sheetPage
    val track = if (linen) (if (LocalOnTile.current) LocalLook.current.ringTrack else LocalLook.current.tile) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val arc = if (linen) LocalLook.current.text else color
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

/**
 * A menu or a set of choices at the bottom of the screen, in place of a grey box in the middle of it. It blinks in and out in place
 * (its own window, [BlinkWindow]); a drag down on its handle follows the finger and lets it go past a quarter of its height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val skin = clean.app.ui.theme.workoutSkin()
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BlinkWindow()
        val drag = remember { Animatable(0f) }
        var height by remember { mutableStateOf(1) }
        val scope = rememberCoroutineScope()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).statusBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Mono: a paper sheet with an ink outline along its top edge (the pad's surface and outline).
            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .graphicsLayer { translationY = drag.value }
                    .then(if (skin.padBorder == Color.Transparent) Modifier else Modifier.border(skin.lineWidth, skin.padBorder, clean.app.ui.theme.SheetTopShape))
                    .clickable(remember { MutableInteractionSource() }, null) { },
                shape = clean.app.ui.theme.SheetTopShape, color = skin.padFill,
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.m)) {
                    androidx.compose.material3.BottomSheetDefaults.DragHandle(
                        Modifier.align(Alignment.CenterHorizontally).draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                            onDragStopped = { if (drag.value > height / 4f) onDismiss() else drag.animateTo(0f, Motion.follow()) },
                        ),
                    )
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (title != null) Text(title, Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.s).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        content()
                    }
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
        val circles = LocalLook.current.iconCircles
        // Linen: the icon sits in a 36 dp grey circle (the row's text still lines up with the plain look's by the same gap).
        if (icon != null && circles) Box(Modifier.size(36.dp).background(LocalLook.current.tile, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (danger || !enabled) tint else LocalLook.current.text)
        }
        else if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = if (danger || !enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        else Spacer(Modifier.width(if (circles) 36.dp else Space.xxl))
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

/**
 * A dialog with AlertDialog's parameters. Under every look but Mono it is the Material AlertDialog (its surface is the scheme's high
 * container: white in Linen); Mono's is a paper surface with a 1 dp ink outline. Date and time pickers are the platform's and stay as they are.
 */
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
    if (!LocalLook.current.mono) {
        androidx.compose.material3.AlertDialog(onDismissRequest = onDismissRequest, confirmButton = { BlinkWindow(); confirmButton() }, dismissButton = dismissButton, title = title, text = text, properties = properties)
        return
    }
    androidx.compose.material3.BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties) {
        BlinkWindow()
        androidx.compose.material3.Surface(
            shape = clean.app.ui.theme.SheetShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
        ) {
            Column(Modifier.padding(Space.xxl)) {
                if (title != null) Box(Modifier.padding(bottom = Space.l)) {
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                }
                if (text != null) Box(Modifier.weight(1f, fill = false).padding(bottom = Space.xxl)) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = cardShape(), border = workoutSkin().panelBorder,
            shadowElevation = 3.dp, modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
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
