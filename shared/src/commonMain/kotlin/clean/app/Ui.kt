package clean.app

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import clean.app.ui.theme.prColour
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import clean.app.ui.theme.buttonShape
import clean.app.ui.theme.GlassWindowShape
import clean.app.ui.theme.PillShape
import androidx.compose.ui.text.style.TextOverflow
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
    val motion = LocalMotion.current
    val scale = remember { Animatable(1f) }
    // Follows the interactions directly, so a press moves the scale in the draw phase and recomposes nothing.
    LaunchedEffect(source, motion, to) {
        source.interactions.collect {
            when (it) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> scale.animateTo(if (motion) to else 1f, Motion.press())
                is androidx.compose.foundation.interaction.PressInteraction.Release, is androidx.compose.foundation.interaction.PressInteraction.Cancel -> scale.animateTo(1f, Motion.press())
            }
        }
    }
    return scale.asState()
}

/** A tap target that dips while pressed, on top of the usual ripple. For cards, tiles and boxes; plain rows keep [clickable]. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, dip: Float = 0.96f, pressed: androidx.compose.runtime.MutableState<Boolean>? = null, onClick: () -> Unit): Modifier {
    // The dip starts as the finger lands. Inside a scrolling list the usual pressed state is held back a
    // tenth of a second (in case the touch is the start of a scroll), which made taps feel late.
    // The scale lives in an Animatable read in the draw phase, so a press recomposes nothing (the card under it included).
    val motion = LocalMotion.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val isDown = remember { booleanArrayOf(false) }
    val set = { down: Boolean ->
        pressed?.value = down
        isDown[0] = down
        // The dip begins on the next frame, and not at all for a touch that is already up by then (as when it was a state read by composition).
        scope.launch {
            if (down) androidx.compose.runtime.withFrameNanos { }
            if (isDown[0] == down) scale.animateTo(if (down && motion) dip else 1f, Motion.pressNow())
        }
        Unit
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(enabled = enabled, onClick = onClick)
        // After clickable, so a child that took the press has already used it up and the card stays still.
        .pointerInput(enabled) {
            if (enabled) awaitEachGesture {
                awaitFirstDown(requireUnconsumed = true)
                set(true)
                try { waitForUpOrCancellation() } finally { set(false) }
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

/**
 * Round 38: a small button for the end of a row (Back up, beside the folder it backs up to): the accent's words on a pill of the accent at 14 %, which stands
 * out from a card where [QuietButton]'s neutral tint would not. While [busy] a small spinner turns before the words (still, with Animations off, as a ring).
 * [description] is what a screen reader says when it differs from the words.
 */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, description: String? = null) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier.heightIn(min = 36.dp).clip(PillShape).background(accent.copy(alpha = if (enabled) 0.14f else 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Space.m)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (busy) {
            if (LocalMotion.current) androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = accent, strokeWidth = 2.dp)
            else androidx.compose.material3.CircularProgressIndicator({ 0.7f }, Modifier.size(14.dp), color = accent, strokeWidth = 2.dp)
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) accent else accent.copy(alpha = 0.5f))
    }
}

/**
 * A button that throws something away: red words, no fill, so it never looks like the way forward. In Crimson, whose accent is red, red keeps
 * its one meaning for the accent's sake: the words take the text colour (see `Look.meaning()`) and a bin icon says what the button does.
 */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bin = clean.app.ui.theme.LocalLook.current.themeName == "Crimson"
    TextButton(onClick, modifier, shape = buttonShape(), colors = ButtonDefaults.textButtonColors(contentColor = LocalCleanColors.current.danger)) {
        if (bin) Icon(CleanIcons.Delete, null, Modifier.padding(end = Space.xs).size(20.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The colour of every PR mark (round 28, replacing round 20's "PR = accent"): the theme's own PR colour (`PR_COLOURS`, `Look.prColour`), a gold for most themes and a violet in Ember, always clear of the accent, of the
 * meaning colours and of the goal colour. Chips, stars, counts and the finish summary's lines all read it from here; [PrChip] and [Badge] draw it at [prTint] for their fill.
 */
@Composable
fun prColor(): Color = LocalLook.current.prColour

/** The colour of a goal or a target range (a goal line, a band, "within the weekly target"): the accent, like a PR, since both mean "the thing you aim for". */
@Composable
fun goalColor(): Color = MaterialTheme.colorScheme.primary

/** How strong the tint behind a PR chip or badge is: 12 % on a dark page, 8 % on a light one: kept light so the chip text stays within a hair of the accent's own contrast (Crimson Dark is 4.4 : 1 on its card). */
fun prTint(dark: Boolean): Float = if (dark) 0.12f else 0.08f

/** A PR chip: a star and the count in the PR colour on its tint ("3 PRs"). */
@Composable
fun PrChip(text: String, modifier: Modifier = Modifier) {
    val color = prColor()
    Row(
        modifier.background(color.copy(alpha = prTint(LocalLook.current.dark)), SmallShape).padding(horizontal = Space.s, vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(CleanIcons.Star, null, Modifier.size(12.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** A small rounded tag: a PR, a count. In the PR colour unless told otherwise. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = prColor(), role: String? = null) {
    // With a [role] the words travel to the same thing on the page a card opens ([MorphText]); the badge's own tint stays and fades with the card.
    MorphText(
        androidx.compose.ui.text.AnnotatedString(text), role, modifier.background(color.copy(alpha = prTint(LocalLook.current.dark)), SmallShape).padding(horizontal = Space.s, vertical = Space.xxs),
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
    val motion = flourishOn()
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
    /** A ring that closes (reaches 1) does so with one soft overshoot: a spring that swells it a little past its size and settles. */
    closeSpring: Boolean = false,
    /** How a change of [progress] is followed; a ring that counts down in real time runs on [Motion.clock] so it drains evenly instead of in a burst each second. */
    steady: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // Light draws no ring in; a ring that counts a rest down keeps its clock.
    val motion = flourishOn() || (steady && LocalMotion.current)
    val target = progress.coerceIn(0f, 1f)
    val sweep = remember { Animatable(if (motion) from ?: target else target) }
    LaunchedEffect(target, motion) {
        if (!motion) sweep.snapTo(target)
        else if (closeSpring && target >= 1f && sweep.value < 1f) sweep.animateTo(1f, Motion.closeRing())
        else sweep.animateTo(target, if (steady) Motion.clock() else Motion.slow())
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val arc = color
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer { (1f + (sweep.value - 1f).coerceAtLeast(0f) * 1.2f).let { scaleX = it; scaleY = it } }) {
            val width = stroke.toPx()
            val box = Size(size.width - width, size.height - width)
            val corner = Offset(width / 2, width / 2)
            drawArc(track, 0f, 360f, false, corner, box, style = Stroke(width))
            if (sweep.value > 0f) drawArc(arc, -90f, 360f * sweep.value.coerceAtMost(1f), false, corner, box, style = Stroke(width, cap = StrokeCap.Round))
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
 * Told where a swipe row is, in px (0 at rest), as it moves; a card of set rows provides one so the highlight on its current set is redrawn and goes with the row.
 */
val LocalSwipeWatch = staticCompositionLocalOf<((Float) -> Unit)?> { null }

/** How far a row must go, as a share of its width, for letting go to act; and the speed (dp/s) and least distance (dp) of a fling that acts early. */
const val SWIPE_ACT = 0.4f
const val SWIPE_FLING = 1000
const val SWIPE_FLING_MIN = 32

/**
 * A row with an action for a swipe to the [left] and one for a swipe to the [right] (either may be null). The row follows the finger 1:1 (Round 30:
 * the rubber band of round 6 is gone). The colour of the action opens up behind the row as it moves, with its icon and word, and turns solid, with a
 * buzz, once the row is past [SWIPE_ACT] of its width: letting go there acts. A fling in the direction of an action ([SWIPE_FLING] dp/s, at least
 * [SWIPE_FLING_MIN] dp from the start) acts early. Short of both the row springs back. A removal slides the row fully off before it acts. A screen reader
 * gets both as custom actions. [haptic] is the buzz at the threshold, a tick; null in a workout with animations off (`feel.tick`). [inset] is where the
 * colour sits around the row's box, so it can have the shape of what the row draws.
 */
@Composable
fun SwipeRow(
    left: SwipeAction?, right: SwipeAction? = null, modifier: Modifier = Modifier, haptic: Haptic? = Haptic.Tick,
    inset: PaddingValues = PaddingValues(vertical = Space.xxs), content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val buzz = rememberHaptic()
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0) }
    val leftNow by rememberUpdatedState(left)
    val rightNow by rememberUpdatedState(right)
    val hapticNow by rememberUpdatedState(haptic)
    val watchNow by rememberUpdatedState(LocalSwipeWatch.current)
    // How far letting go acts: a share of the row's width (110 dp until it is measured).
    fun threshold() = if (width > 0) width * SWIPE_ACT else with(density) { 110.dp.toPx() }
    // [raw] is how far the finger has gone; the speed is read from it, since the row moves under the finger and its own position does not.
    var raw by remember { mutableStateOf(0f) }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    // What the drag changes is read as low as it can be: these only change at a threshold, so a drag recomposes nothing; the width follows in layout.
    val past by remember { derivedStateOf { abs(offset.value) > threshold() } }
    val toLeft by remember { derivedStateOf { offset.value < 0 } }
    val show = with(density) { 40.dp.toPx() }
    // 0 nothing, 1 the colour only, 2 with the icon (Round 31: the word is gone, the icon is the meaning).
    val level by remember { derivedStateOf { val o = abs(offset.value); if (o <= 0f) 0 else if (o > show) 2 else 1 } }
    LaunchedEffect(past) { if (past) hapticNow?.let { buzz(it) } }
    Box(modifier.keepSystemBackOff(left != null, right != null).onSizeChanged { width = it.width }) {
        val action = if (toLeft) left else right
        // Round 31: the colour is the ground behind the row, as wide as the row has moved away and in the row's shape; its icon sits at the far edge, where the row came from.
        if (level > 0 && action != null) Box(Modifier.matchParentSize().padding(inset), contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(
                Modifier.fillMaxHeight().layout { measurable, c ->
                    val w = abs(offset.value).roundToInt().coerceIn(c.minWidth, c.maxWidth)
                    val p = measurable.measure(c.copy(minWidth = w, maxWidth = w))
                    layout(w, p.height) { p.place(0, 0) }
                }.background(if (past) action.color else action.color.copy(alpha = 0.45f), SmallShape),
                contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val grow by animateFloatAsState(if (past) 1.25f else 1f, Motion.short(), label = "bin")
                // The icon alone, so the meaning never rests on the colour.
                if (level >= 2) Icon(action.icon, null, Modifier.padding(horizontal = Space.l).graphicsLayer { scaleX = grow; scaleY = grow }, tint = action.color.readableOn())
            }
        }
        Box(
            // The swipe is the fast way; a screen reader gets the same actions.
            Modifier.semantics {
                customActions = listOfNotNull(
                    left?.let { a -> CustomAccessibilityAction(a.word) { leftNow?.onTrigger?.invoke(); true } },
                    right?.let { a -> CustomAccessibilityAction(a.word) { rightNow?.onTrigger?.invoke(); true } },
                )
            }.offset { watchNow?.invoke(offset.value); IntOffset(offset.value.roundToInt(), 0) }.then(if (left == null && right == null) Modifier else Modifier.pointerInput(Unit) {
                // What letting go does: past the threshold or flung, the action (a removal slides the row off first); otherwise the row springs back.
                // A touch the system takes away (the edge's back gesture) is judged the same way, so a swipe that was well on its way is not lost.
                fun finish(speed: Float, cancelled: Boolean) {
                    tracker.resetTracking()
                    raw = 0f
                    scope.launch {
                        val act = if (offset.value < 0) leftNow else rightNow
                        val far = abs(offset.value) > threshold()
                        val flung = !cancelled && abs(offset.value) > SWIPE_FLING_MIN.dp.toPx() && abs(speed) > SWIPE_FLING.dp.toPx() && (speed < 0) == (offset.value < 0)
                        if (act != null && (far || flung)) {
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
                }
                detectHorizontalDragGestures(
                    onDragEnd = { finish(tracker.calculateVelocity().x, false) },
                    onDragCancel = { finish(0f, true) },
                ) { change, amount ->
                    change.consume()
                    raw = (raw + amount).coerceIn(-width.toFloat(), width.toFloat())
                    val blocked = raw < 0 && leftNow == null || raw > 0 && rightNow == null
                    if (blocked) { raw = 0f; tracker.resetTracking() } else tracker.addPosition(change.uptimeMillis, Offset(raw, 0f))
                    scope.launch { offset.snapTo(raw) }
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
fun Sheet(onDismiss: () -> Unit, title: String? = null, origin: androidx.compose.ui.geometry.Rect? = null, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) { SheetDepth.open++; onDispose { SheetDepth.open-- } }
    // With an [origin] (the window bounds of the button that opened it) and animations on, the sheet grows out of that button and shrinks back into it.
    val grow = origin != null && LocalMotion.current
    val drag = remember { Animatable(0f) }
    val open = remember { Animatable(if (grow) 0f else 1f) }
    var height by remember { mutableStateOf(1) }
    var at by remember { mutableStateOf(Offset.Zero) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (grow) open.animateTo(1f, tween(Motion.CARD_TO_PAGE * 3 / 5, easing = Motion.Emphasized)) }
    // Closing runs the shrink first (when it grew), then lets the page go.
    val close: () -> Unit = {
        if (!grow) onDismiss()
        else if (!closing) {
            closing = true
            scope.launch { launch { drag.animateTo(0f, Motion.follow()) }; open.animateTo(0f, tween(Motion.CARD_TO_PAGE * 2 / 5, easing = Motion.Emphasized)); onDismiss() }
        }
    }
    androidx.compose.ui.window.Dialog(close, fullScreenDialogProperties()) {
        if (grow) CutWindow() else BlinkWindow()
        Box(
            Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.32f * open.value)) }.clickable(remember { MutableInteractionSource() }, null, onClick = close).statusBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .onGloballyPositioned { at = it.positionInWindow() }
                    .graphicsLayer {
                        translationY = drag.value
                        if (origin != null && open.value < 1f) {
                            shape = GrowShape(origin.translate(-at), open.value)
                            clip = true
                        }
                    }
                    .glass(clean.app.ui.theme.SheetTopShape, haze = null, lift = 16.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { },
                shape = clean.app.ui.theme.SheetTopShape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.m).graphicsLayer { alpha = ((open.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                    Column(
                        Modifier.fillMaxWidth().draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                            onDragStopped = { v -> if (drag.value > height / 4f || v > 1800f) close() else drag.animateTo(0f, Motion.follow()) },
                        ),
                    ) {
                        androidx.compose.material3.BottomSheetDefaults.DragHandle(
                            Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Drag down to close"; onClick("Close") { close(); true } },
                        )
                        if (title != null) Text(title, Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.s).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
    }
}

/** The clip of a sheet growing out of a button: the button's rounded rectangle ([from], in the sheet's own coordinates) opening to the whole sheet as [progress] goes 0 to 1. */
private class GrowShape(val from: androidx.compose.ui.geometry.Rect, val progress: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        fun mix(a: Float, b: Float) = a + (b - a) * progress
        val rect = androidx.compose.ui.geometry.Rect(mix(from.left, 0f), mix(from.top, 0f), mix(from.right, size.width), mix(from.bottom, size.height))
        val half = minOf(from.width, from.height) / 2f
        val top = androidx.compose.ui.geometry.CornerRadius(mix(half, 24f * density.density))
        val bottom = androidx.compose.ui.geometry.CornerRadius(mix(half, 0f))
        return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(rect, top, top, bottom, bottom))
    }
}

/** One choice in a [Sheet]: an icon, what it does, and what it is set to now. Something that deletes is [danger] and goes last. */
@Composable
fun SheetRow(text: String, icon: ImageVector? = null, value: String = "", danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lightAlpha(0.4f, 0.75f))
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
 * The one Undo pill, wherever something can be taken back: what was done and Undo in a small glass pill that hugs its words (long words
 * are cut with an ellipsis), with a thin line along its bottom edge that drains over the time left ([UNDO_MILLIS], started again by each
 * further step; it stands still when animations are off). Swiping it away, sideways either way or up, follows the finger with resistance
 * ([Motion.rubber]); past a short way it leaves and the chance to take back is dropped ([AppModel.dismissUndo]), short of that it springs back.
 * Only the pill takes touches, so the page around it stays usable. A screen reader hears its words (a polite live region).
 */
@Composable
fun UndoBar(model: AppModel, modifier: Modifier = Modifier, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current) {
    val undos = model.undos
    val motion = LocalMotion.current
    AnimatedVisibility(undos != null, modifier, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
        // Kept while the pill fades away, so its words do not vanish first.
        val shown = remember { mutableStateOf(undos) }
        if (undos != null) shown.value = undos
        val left = remember { Animatable(1f) }
        LaunchedEffect(model.undoUntil) {
            val remaining = (model.undoUntil - currentMillis()).coerceIn(0, UNDO_MILLIS)
            left.snapTo(remaining / UNDO_MILLIS.toFloat())
            if (motion) left.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing))
        }
        val dragX = remember { Animatable(0f) }
        val dragY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val limit = with(density) { 120.dp.toPx() }
        val reach = with(density) { 56.dp.toPx() }
        val away = with(density) { 480.dp.toPx() }
        val line = with(density) { 2.dp.toPx() }
        val inset = with(density) { 14.dp.toPx() }
        val lift = with(density) { 4.dp.toPx() }
        var rawX by remember { mutableStateOf(0f) }
        var rawY by remember { mutableStateOf(0f) }
        // Round 20: one light tick as the swipe passes the point where letting go dismisses the pill.
        val tick = rememberTick()
        var wasFar by remember { mutableStateOf(false) }
        val bar = MaterialTheme.colorScheme.primary
        Surface(
            color = Color.Transparent, shape = PillShape,
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s).widthIn(max = 360.dp)
                .graphicsLayer {
                    translationX = dragX.value; translationY = dragY.value
                    alpha = 1f - 0.5f * (kotlin.math.hypot(dragX.value, dragY.value) / away).coerceIn(0f, 1f)
                }
                .pointerInput(motion) {
                    detectDragGestures(
                        onDragEnd = {
                            val far = abs(dragX.value) > reach * 0.9f || -dragY.value > reach * 0.7f
                            val sx = rawX
                            val sy = rawY
                            rawX = 0f; rawY = 0f; wasFar = false
                            if (!far) scope.launch { launch { dragX.animateTo(0f, Motion.follow()) }; dragY.animateTo(0f, Motion.follow()) }
                            else if (!motion) model.dismissUndo()
                            else scope.launch {
                                // Off in the direction it was going: up when it was pulled up more than sideways.
                                if (-sy > abs(sx)) dragY.animateTo(-away, Motion.leave()) else dragX.animateTo(if (sx < 0) -away else away, Motion.leave())
                                model.dismissUndo()
                            }
                        },
                        onDragCancel = { rawX = 0f; rawY = 0f; wasFar = false; scope.launch { launch { dragX.animateTo(0f, Motion.follow()) }; dragY.animateTo(0f, Motion.follow()) } },
                    ) { change, amount ->
                        change.consume()
                        rawX += amount.x
                        rawY = (rawY + amount.y).coerceAtMost(0f)
                        val pastIt = abs(rawX) > reach * 0.9f || -rawY > reach * 0.7f
                        if (pastIt && !wasFar) tick()
                        wasFar = pastIt
                        val x = if (rawX < 0) -Motion.rubber(-rawX, limit) else Motion.rubber(rawX, limit)
                        val y = -Motion.rubber(-rawY, limit)
                        scope.launch { dragX.snapTo(if (motion) x else rawX); dragY.snapTo(if (motion) y else rawY) }
                    }
                }
                .glass(PillShape, haze, lift = 10.dp)
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        ) {
            Row(
                Modifier.clip(PillShape).drawBehind {
                    // Inside the pill, on its straight bottom edge (inset from the round ends, a little above the edge), so it can never reach past it.
                    val run = (size.width - 2 * inset).coerceAtLeast(0f) * left.value
                    if (run > 0f) drawRoundRect(bar, Offset(inset, size.height - lift - line), Size(run, line), androidx.compose.ui.geometry.CornerRadius(line / 2))
                }.padding(start = Space.l, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shown.value?.text.orEmpty(), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = model::takeBack) { Text("Undo", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// --- Glass: the material of what floats (tab bar, plus button, round buttons, the Workout sheet) ---

/** The accent a running workout washes over the app (D6a), or null when none runs or on the workout page itself. Read by [glass]. */
val LocalRunTint = staticCompositionLocalOf<Color?> { null }

/** The blur source of the page behind the glass in this part of the tree, or null where there is none (glass is then solid). */
val LocalHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** Whether a light haptic tick marks a segmented control changing and a calendar day tapped (kv `haptic.ticks`, on when absent). */
val LocalHapticTicks = staticCompositionLocalOf { true }

/** A diagonal sheen that sweeps once across what it is on as [progress] goes from 0 to 1; nothing is drawn at 0 and at 1. */
fun Modifier.glint(progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val p = progress()
    if (p > 0f && p < 1f) {
        // The band is a stripe of the diagonal x + y = k, moved from before the top left corner to past the bottom right.
        val band = size.height
        val k = -band + p * (size.width + size.height + 2 * band)
        val sheen = Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
            start = Offset((k - band) / 2, (k - band) / 2), end = Offset((k + band) / 2, (k + band) / 2),
        )
        drawRect(sheen)
    }
}

/**
 * The cap of an all-time-high bar glinting once, at [p] (0 to 1; nothing is drawn at 0 and at 1): a thin cap in [color] over [size] at [at] that
 * fades in and out, and a bright band that runs across it from the left, the sheen of [glint] at the cap's scale.
 */
fun DrawScope.drawCapGlint(at: Offset, size: Size, color: Color, p: Float) {
    if (p <= 0f || p >= 1f || size.width <= 0f) return
    drawRect(color.copy(alpha = kotlin.math.sin(p * 3.1415927f)), at, size)
    val band = size.width.coerceAtMost(48.dp.toPx()).coerceAtLeast(size.height * 4f)
    val k = at.x - band + p * (size.width + 2 * band)
    val sheen = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), startX = k - band / 2, endX = k + band / 2)
    clipRect(at.x, at.y, at.x + size.width, at.y + size.height) { drawRect(sheen, at, size) }
}

/**
 * A tiny four-dot burst around a star that has just been turned on (not one that is shown already on): the dots fly out about 8 dp and fade in 350 ms.
 * Nothing with animations off.
 */
@Composable
fun Modifier.starBurst(on: Boolean): Modifier {
    val motion = LocalMotion.current
    val t = remember { Animatable(1f) }
    var was by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !was && motion) { t.snapTo(0f); t.animateTo(1f, tween(Motion.STAR_BURST, easing = Motion.Decelerate)) }
        was = on
    }
    val color = MaterialTheme.colorScheme.tertiary
    return drawWithContent {
        drawContent()
        val p = t.value
        if (p < 1f) for (k in 0 until 4) {
            val angle = ((45.0 + 90.0 * k) * 0.017453292519943295)
            val reach = 11.dp.toPx() + 8.dp.toPx() * p
            drawCircle(color.copy(alpha = 1f - p), 1.8.dp.toPx(), center + Offset((kotlin.math.cos(angle) * reach).toFloat(), (kotlin.math.sin(angle) * reach).toFloat()))
        }
    }
}

/** True when glass may not blur: the Reduce transparency switch, the phone's own high-contrast setting, or Android below 12. Provided by the app. */
val LocalSolidGlass = staticCompositionLocalOf { true }

/** How much of the accent a running workout lays over the page: a little more in Dark, where it is harder to see. */
private fun runTint(dark: Boolean) = if (dark) 0.08f else 0.06f

val GlassBlur = 22.dp
val GlassBlurStyle = dev.chrisbanes.haze.HazeStyle(backgroundColor = Color.Unspecified, tints = emptyList(), blurRadius = GlassBlur, noiseFactor = 0f)
// The blur works on a half-size copy of what is behind (inputScale): the same look at a quarter of the pixels, so the dock and the
// glass cost little on every frame of a scroll or a swipe (round 10).
@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
val GlassBlurBlock: dev.chrisbanes.haze.HazeEffectScope.() -> Unit = { blurRadius = GlassBlur; noiseFactor = 0f; inputScale = dev.chrisbanes.haze.HazeInputScale.Fixed(0.5f) }

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
 * The one floating material (A1; round 16: one material). Content is opaque; what floats over it is this, in two forms of the same tone.
 * With [haze] (a source behind it) and blur allowed, the page behind is blurred 22 dp under a tint of 90 %: the ground with 8 % of the accent
 * mixed in (the dock's own tone). Without a source, or with Reduce transparency ([LocalSolidGlass]), or in a window of its own (a popup,
 * a menu, a dialog, a sheet: nothing there can see the page), the opaque form is drawn: the tone the blur reads on average over the
 * ground (that tint at 90 % over the ground), 100 % opaque, so nothing shows through. Both have the same lit top edge, darker bottom edge,
 * hairline inside and soft wide shadow ([lift] is its height). While a workout runs the tint takes a little of the accent.
 * [accent] only adds the dock's thin outline in the accent; it is the dock's identity and does not change the material.
 */
@Composable
fun Modifier.glass(shape: Shape, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current, lift: Dp = 14.dp, accent: Color? = null): Modifier {
    val look = clean.app.ui.theme.LocalLook.current
    val dark = look.dark
    val blur = haze != null && !LocalSolidGlass.current
    val tone = glassTone(look, LocalRunTint.current, blur)
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
            drawOutline(outline, tone)
            val line = 2.dp.toPx() // half of it is outside the clip: 1 dp shows
            drawOutline(outline, hair, style = Stroke(line * 2f))
            drawOutline(outline, Brush.verticalGradient(0f to lit, 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to under), style = Stroke(line))
            // The dock's thin outline in the accent (half of the stroke is outside the clip: 1 dp shows), drawn over the lit edge so the top keeps it: 35 % in Dark,
            // 45 % in Light, where the near-black accent then gives the dock a 3 : 1 edge against the page.
            if (accent != null) drawOutline(outline, accent.copy(alpha = if (dark) 0.35f else 0.45f), style = Stroke(line))
        }
}

/** [dark] in Dark and [light] in Light: for the alphas that dim a colour, which need more of it on a light ground to stay legible (round 22). */
@Composable
fun lightAlpha(dark: Float, light: Float): Float = if (clean.app.ui.theme.LocalLook.current.dark) dark else light

/** The glass's fill: the ground with 8 % of the accent (the dock's tone), 90 % over a blur; opaque, the same tone as the blur reads over the ground. */
private fun glassTone(look: clean.app.ui.theme.Look, run: Color?, blur: Boolean): Color {
    val base = androidx.compose.ui.graphics.lerp(look.page, look.orange, 0.08f)
    val tint = if (run == null) base else androidx.compose.ui.graphics.lerp(base, run, if (look.dark) 0.15f else 0.10f)
    return if (blur) tint.copy(alpha = 0.90f) else clean.app.ui.theme.over(tint, 0.90f, look.page)
}

/** A 34 dp round button with a 48 dp touch area; [content] is its icon or letter. It sits in the page (content layer), so it is a quiet tile-toned disc, not glass (round 16). */
@Composable
fun GlassButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(34.dp).tile(CircleShape), contentAlignment = Alignment.Center, content = content) }
}

/**
 * A section laid out on the page (1.1.0, D2): a heading, then the content, with air around it and no card.
 * Numbers, lists and charts sit like this; a card is only for one object that is opened as a whole.
 * The heading follows the round 11 rule (docs/design.md): a section of a tab page (Home, Progress, Profile) is a [SectionHeading], so [big];
 * a group on a page that was opened (Settings, Trophies, Trends) is a small grey uppercase label.
 */
@Composable
fun PageSection(title: String, modifier: Modifier = Modifier, spacing: Dp = Space.m, big: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val lines = rememberRowLines()
    Column(
        modifier.fillMaxWidth().drawRowLines(lines).padding(start = Space.l, end = Space.l, top = if (big) Space.xxl else Space.m, bottom = Space.m),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title.isNotEmpty()) { if (big) SectionHeading(title) else PageHeading(title) }
        androidx.compose.runtime.CompositionLocalProvider(LocalRowLines provides lines) { content() }
    }
}

/**
 * The heading of a section of a tab page (Activity, Statistics, Snapshots, Body, Strength, Goals): large, semibold, at the left, with [trailing]
 * (a grey [SectionLink], a "since" note) at the right. One style, so the three tabs read the same. Small grey capitals are for a label inside a
 * tile or over a group of rows on a page that was opened, not for a section of a tab.
 */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        trailing()
    }
}

/** The quiet grey words at the right of a [SectionHeading] that open the page the section belongs to. */
@Composable
fun SectionLink(text: String, onClick: () -> Unit) {
    Box(Modifier.clickable(role = Role.Button, onClick = onClick).minTouch().padding(horizontal = Space.xs), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- Round 14: Settings readability (docs/design.md) ---

/** The least height of a row in a [SettingsGroup]: above the 48 dp a finger needs, and the same for every one-line row. */
private val GROUP_ROW_MIN = 56.dp

/**
 * A group of Settings rows (round 14): a [SectionTitle] above, much more air over it than under it so it belongs to what follows, then the
 * rows in one card with a hairline, inset from the left, between them, then the group's [footer], the quiet small words that explain it.
 * Every child of [content] is one row ([SettingsRow], [SettingsSwitch], [ActionRow], [ChoiceRow], [NoteRow]); a child that draws nothing
 * gets no line. [title] null is a group with no heading (the list of pages).
 */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable () -> Unit) {
    val shape = cardShape()
    val line = MaterialTheme.colorScheme.outlineVariant
    var marks by remember { mutableStateOf(IntArray(0)) }
    Column(modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xxl)) {
        if (title != null) ShineHeading(Modifier.padding(bottom = Space.s)) { SectionTitle(title, Modifier.padding(start = Space.l)) }
        androidx.compose.ui.layout.Layout(
            content,
            Modifier.fillMaxWidth().cardDepth(shape).clip(shape).background(cardFill()).drawBehind {
                for (y in marks) drawLine(line, Offset(Space.l.toPx(), y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1.dp.toPx())
            },
        ) { measurables, constraints ->
            val placed = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
            val lines = ArrayList<Int>()
            var y = 0
            for (p in placed) {
                if (p.height > 0 && y > 0) lines += y
                y += p.height
            }
            val next = lines.toIntArray()
            if (!next.contentEquals(marks)) marks = next
            layout(constraints.maxWidth, y) {
                var top = 0
                for (p in placed) { p.placeRelative(0, top); top += p.height }
            }
        }
        if (footer != null) GroupFooter(footer)
    }
}

/** The words under a [SettingsGroup]: small and quiet, aligned with the rows' text, with a gap of their own from the card. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = Space.l, end = Space.l, top = Space.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One row of a [SettingsGroup]: the [title] in the body style, an optional short [summary] under it, a muted [value] and then [trailing] (a switch,
 * a button) at the right, and a chevron when it opens something. [leading] is an icon box before the words. At least 56 dp tall, 12 dp between
 * the words and what is at the right, so a long summary never runs under a switch.
 */
@Composable
fun SettingsRow(
    title: String, modifier: Modifier = Modifier, summary: String? = null, value: String? = null, onClick: (() -> Unit)? = null, summaryLines: Int = Int.MAX_VALUE,
    chevron: Boolean = false, enabled: Boolean = true, titleColor: Color = Color.Unspecified, titleWeight: FontWeight? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val colour = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().heightIn(min = GROUP_ROW_MIN)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = titleWeight, color = if (enabled) colour else quiet.copy(alpha = lightAlpha(0.6f, 0.75f)))
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = quiet, maxLines = summaryLines, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = quiet, textAlign = TextAlign.End)
        trailing?.invoke()
        if (chevron) Icon(CleanIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

/** A [SettingsRow] that is one switch: the whole row is the switch and reads out as one; the drawn [Switch] only shows the state. */
@Composable
fun SettingsSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true) {
    SettingsRow(
        title, modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange), summary = summary, enabled = enabled,
        trailing = { Switch(checked, null, enabled = enabled) },
    )
}

/**
 * A row that does something when pressed (back up, import, delete): its words in the accent, semibold, as a [QuietButton]'s are; [danger] makes
 * them the danger red (the text colour and a bin in Crimson, whose accent is red), as [DangerButton]. Buttons inside a group are these rows,
 * since a tinted button has nothing to stand out from on a card.
 */
@Composable
fun ActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true, danger: Boolean = false) {
    val bin = danger && clean.app.ui.theme.LocalLook.current.themeName == "Crimson"
    val colour = if (!danger) MaterialTheme.colorScheme.primary else if (bin) MaterialTheme.colorScheme.onSurface else LocalCleanColors.current.danger
    SettingsRow(
        title, modifier, summary = summary, onClick = onClick, enabled = enabled, titleColor = colour, titleWeight = FontWeight.SemiBold,
        trailing = if (bin) ({ Icon(CleanIcons.Delete, null, Modifier.size(20.dp), tint = colour) }) else null,
    )
}

/** A row whose control is under its words: chips, a segmented control, boxes. The [title] (with the current [value] at the right) and [summary] are optional. */
@Composable
fun ChoiceRow(title: String?, modifier: Modifier = Modifier, value: String? = null, summary: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (title != null) Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** A row of plain words in a [SettingsGroup] (a tip, a credit); [quiet] sets it small and grey. */
@Composable
fun NoteRow(text: String, modifier: Modifier = Modifier, quiet: Boolean = false, color: Color = Color.Unspecified) {
    Text(
        text, modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
        style = if (quiet) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (color != Color.Unspecified) color else if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * A card's depth (Lit): a soft wide shadow, in Dark black and deep, in Light faint. The lit top edge is [litEdge], drawn over the card's
 * own surface.
 */
@Composable
fun Modifier.cardDepth(shape: Shape): Modifier {
    val look = clean.app.ui.theme.LocalLook.current
    return if (!look.dark) shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.16f))
    else shadow(20.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.35f))
}

private fun outlinePath(outline: androidx.compose.ui.graphics.Outline): androidx.compose.ui.graphics.Path = when (outline) {
    is androidx.compose.ui.graphics.Outline.Generic -> outline.path
    is androidx.compose.ui.graphics.Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
    is androidx.compose.ui.graphics.Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(outline.rect) }
}

/** A 1 dp lit edge along the top of a surface of [shape]: white at [alpha] (Dark) fading out within the first third; in Light a faint dark hairline instead. */
@Composable
fun Modifier.litEdge(shape: Shape, alpha: Float = 0.09f, bright: Boolean = false, brightNow: (() -> Boolean)? = null): Modifier {
    val dark = clean.app.ui.theme.LocalLook.current.dark
    // The outline and both brushes are made once per size; [brightNow] is read when drawing, so a press recomposes nothing.
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outlinePath(outline)
        val width = 2.dp.toPx()
        fun brush(edge: Color) = Brush.verticalGradient(0f to edge, 0.35f to Color.Transparent, 1f to Color.Transparent)
        val plain = brush(if (dark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = 0.05f))
        val lit = brush(if (dark) Color.White.copy(alpha = alpha * 2.4f) else Color.Black.copy(alpha = 0.12f))
        onDrawWithContent {
            drawContent()
            clipPath(path) { drawOutline(outline, if (brightNow?.invoke() ?: bright) lit else plain, style = Stroke(width)) }
        }
    }
}

/**
 * The theme's ambient light (Lit): one radial gradient, an ellipse the screen's width plus 40 dp each side and 540 dp tall, its top 230 dp above
 * the screen's top (so its centre is 40 dp below it), fading to nothing. Drawn behind a whole page of tabs, so it stays put while a list scrolls.
 * [top] is how far the drawn area starts below the top of the screen (the status bar).
 */
fun Modifier.ambientLight(light: Color, top: Dp = 0.dp): Modifier = drawBehind { drawAmbient(light, top) }

/** The ambient light's gradient itself, so [topFadeMask] can paint the same thing over a page's top edge. */
fun DrawScope.drawAmbient(light: Color, top: Dp) = drawAmbientAt(light, top.toPx(), 1.dp.toPx())

/** [drawAmbient] with [top] in pixels and [dp] pixels to a dp, so a small preview can draw the same light at its own scale. */
fun DrawScope.drawAmbientAt(light: Color, top: Float, dp: Float) {
    val height = 540f * dp
    val width = size.width + 80f * dp
    val centre = Offset(size.width / 2f, 40f * dp - top)
    val radius = height / 2f
    withTransform({ scale(width / height, 1f, pivot = centre) }) {
        drawCircle(Brush.radialGradient(listOf(light, light.copy(alpha = 0f)), centre, radius), radius, centre)
    }
}

/**
 * A tile (Lit): the smaller card on Home and Profile. The tint at 4.5 % over the page in Dark (white in Light), a lit edge of 8 %, no shadow.
 * Put this in place of the card's fill, before the padding.
 */
@Composable
fun Modifier.tile(shape: Shape, pressed: Boolean = false, pressedNow: (() -> Boolean)? = null, frosted: Boolean = false): Modifier {
    val look = clean.app.ui.theme.LocalLook.current
    return (if (frosted && frostOn()) frosted(shape, look.tile) else background(look.tile, shape)).litEdge(shape, 0.08f, pressed, pressedNow)
}

/** Whether cards are frosted here: inside the tabs, with a blur allowed (not Reduce transparency, not Balanced or Light). */
@Composable
fun frostOn(): Boolean = LocalFrost.current != null && !LocalSolidGlass.current

/**
 * Round 32: a card's fill as frosted glass over the page's background. The blur is of the background alone (`canDrawArea`: the area of zIndex -1, not the cards
 * and lists above it), so a scroll changes nothing it reads; the card's own tone [fill] covers it at the background's least safe tint ([glassTint]) so text keeps 4.5 : 1.
 * Where frost is off it is the plain opaque [fill], as before.
 */
@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@Composable
fun Modifier.frosted(shape: Shape, fill: Color): Modifier {
    val frost = LocalFrost.current
    if (frost == null || LocalSolidGlass.current) return background(fill, shape)
    val tone = frostTone(fill, MaterialTheme.colorScheme.background, frost.tint).copy(alpha = frost.tint)
    return clip(shape).hazeEffect(frost.haze, GlassBlurStyle) { GlassBlurBlock(); canDrawArea = { it.zIndex < 0f } }.background(tone, shape)
}

/**
 * A segmented control (1.1.0): a quiet track with the chosen option as a raised pill, for a few exclusive choices that switch what
 * the page shows (Week / Month / Year / All, a body view). With [morph] the chosen option's word is the page's side of a [MorphText].
 */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, morph: String? = null, onSelect: (T) -> Unit) {
    // The thumb is concentric with the track: its corner is the track's minus the inset (a fully round track would give a fully round thumb).
    val inset = 3.dp
    val gap = 2.dp
    val track = cardShape()
    val pill = androidx.compose.foundation.shape.RoundedCornerShape(clean.app.ui.theme.CardCorner - inset)
    val tick = rememberTick()
    val motion = LocalMotion.current
    // D12: one thumb glides to the chosen option; while it moves fast it squashes a little (wider, flatter), then settles.
    val at = selected.let { s -> options.indexOf(s) }
    val pos = remember { Animatable(at.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(at) {
        if (at < 0) return@LaunchedEffect
        if (motion) pos.animateTo(at.toFloat(), tween(260, easing = FastOutSlowInEasing)) else pos.snapTo(at.toFloat())
    }
    // The chosen option's pill is the page's raised step in Dark and plain white in Light on a track of 8 % ink, where the raised step is as dark as the track
    // itself in Graphite (the pill and its track were 1.02 : 1); the words still say it too (heavier and in the text colour).
    val look = clean.app.ui.theme.LocalLook.current
    val higher = if (look.dark) look.higher else Color.White
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (look.dark) 0.05f else 0.08f), track).padding(inset)) {
        if (at >= 0) Box(Modifier.matchParentSize()) {
            Box(
                Modifier.layout { measurable, c ->
                    val g = gap.roundToPx()
                    val w = ((c.maxWidth - (options.size - 1) * g) / options.size).coerceAtLeast(0)
                    val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, c.maxHeight))
                    layout(c.maxWidth, c.maxHeight) { p.place(roundHalfUp((w + g) * pos.value), 0) }
                }.graphicsLayer {
                    val speed = if (motion) (abs(pos.velocity) / 6f).coerceIn(0f, 1f) else 0f
                    scaleX = 1f + 0.08f * speed; scaleY = 1f - 0.06f * speed
                }.background(higher, pill).litEdge(pill, 0.10f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (option in options) {
                val on = option == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp)
                        .clip(pill)
                        .selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton) { if (!on) tick(); onSelect(option) },
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
    val rows = HashMap<Any, androidx.compose.ui.layout.LayoutCoordinates>()
    var section: androidx.compose.ui.layout.LayoutCoordinates? = null
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
fun NoticeToast(model: AppModel, modifier: Modifier = Modifier, haze: dev.chrisbanes.haze.HazeState? = LocalHaze.current) {
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
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s).glass(cardShape(), haze, lift = 10.dp)
                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        ) {
            Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(shown.value?.text.orEmpty(), Modifier.weight(1f).padding(vertical = Space.m))
                shown.value?.let { n -> if (n.action != null) TextButton(onClick = { model.notice = null; n.onAction?.invoke() }) { Text(n.action, fontWeight = FontWeight.SemiBold) } }
            }
        }
    }
}

// --- The soft top edge: content fades into the ground under the status bar instead of ending on a hard line ---

/** How far a page has been scrolled from its top, gathered from every scroller inside it (a nested-scroll connection). Read by [BackBar]'s fade. */
class TopFade : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    var offset by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Round 38: the height of what sits under the bar and must not be dimmed (the Settings jump chips); the bar's fade starts this far below it. [belowOwner] is what set it. */
    var below by androidx.compose.runtime.mutableFloatStateOf(0f)
    var belowOwner: Any? = null
    override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
        if (consumed.y != 0f) offset = (offset - consumed.y).coerceAtLeast(0f)
        return Offset.Zero
    }
    /** 0 at the top of the page, 1 once it has moved [px]: the fade is not there at rest, and grows with the scroll, so a title is never dimmed before it slides under. */
    fun strength(px: Float): Float = (offset / px).coerceIn(0f, 1f)
}

/** The scroll of the page being drawn, for its bar's fade; null outside a page. */
val LocalTopFade = staticCompositionLocalOf<TopFade?> { null }

/** The backdrop of the page being drawn, so its bar can fade into it (Round 35); null outside a page. */
val LocalPageBackdrop = staticCompositionLocalOf<PageBackdrop?> { null }

/** The fade's greatest height below the bar or the status bar; it is as tall as the page has scrolled until then. */
val TOP_FADE = 40.dp

/**
 * Fades the top of this content out to what is behind it, over [TOP_FADE] times [strength] (0 to 1, read when drawing): a tab page's list under
 * the status bar. Not a mask of the page (that sent the whole page through an offscreen layer): the strip is painted over the page, as a layer
 * the size of the strip only, with what is behind the page: the [ground] and the [ambient] light (as [ambientLight] draws it, [ambientTop] below
 * the screen's top). [shiftX] is how far this page lies from the pager's left edge, since the light stays put while pages swipe. The band is as tall as the
 * content has scrolled (up to [TOP_FADE]), so what is still wholly below the status bar is never dimmed, and a title is normal until it slides under it.
 */
fun Modifier.topFadeMask(
    backdrop: Backdrop, ambientTop: Dp, shiftX: Density.() -> Float = { 0f }, strength: Density.() -> Float,
): Modifier = drawWithContent {
    drawContent()
    val s = strength().coerceIn(0f, 1f)
    if (s * TOP_FADE.toPx() >= 1f) {
        val h = TOP_FADE.toPx() * s
        val strip = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, h)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(strip, androidx.compose.ui.graphics.Paint())
            // The backdrop starts at the top of the screen, [ambientTop] above this page, so the strip is cut from it there (Round 35).
            translate(left = -shiftX()) { inset(0f, -ambientTop.toPx(), 0f, 0f) { backdrop.draw(this, density) } }
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = 0f, endY = h),
                size = strip.size, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
            canvas.restore()
        }
    }
}

/** [topFadeMask] for a tab's list: nothing at the top, growing with the scroll to the full band, which it is once the list is past its first item. */
fun Modifier.topFadeMask(state: LazyListState, backdrop: Backdrop, ambientTop: Dp, shiftX: Density.() -> Float = { 0f }): Modifier =
    topFadeMask(backdrop, ambientTop, shiftX) { if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / TOP_FADE.toPx() }
