package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import clean.app.ui.theme.PillShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import clean.app.ui.theme.orange

/**
 * Round 38: the jump chips of a Settings page, one per [labels], under the bar and above the list. [lit] is the chip of the section in view.
 *
 * They are fixed: no band, no strip, no settling. Each is a [GlassChip] floating on the page's own backdrop, and the list's top fade starts below them (they tell
 * [LocalTopFade] their height), so they are never dimmed. The row scrolls sideways only by code, to keep the lit chip wholly in view; it has the page's gutter
 * at both ends, so none is cut at rest.
 *
 * They behave like the dock ([slideAlong], the dock's own gesture): a tap calls [onJump]; a slide along them moves the lit chip from the one that was lit, one
 * chip's width a step, with a tick each ([onLive] says which, null once the finger lifts) and calls [onJump] for it on release; a flick moves one; a vertical
 * start does nothing; a hold shows [peek] for the chip held, on a [PeekPopup], until it lifts.
 */
@Composable
fun JumpChips(labels: List<String>, lit: Int, onLive: (Int?) -> Unit, onJump: (Int) -> Unit, peek: @Composable ColumnScope.(Int) -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current && LocalPerformance.current.flourishes
    val scroll = rememberScrollState()
    val lefts = remember(labels) { FloatArray(labels.size) }
    val rights = remember(labels) { FloatArray(labels.size) }
    var content by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    val gutter = with(LocalDensity.current) { Space.l.toPx() }
    // The lit chip is kept wholly in view, a gutter from the edge: the least the row moves.
    val viewport = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lit, content) {
        if (lit !in labels.indices || content == 0 || viewport.floatValue == 0f) return@LaunchedEffect
        val now = scroll.value
        val target = when {
            lefts[lit] - gutter < now -> lefts[lit] - gutter
            rights[lit] + gutter > now + viewport.floatValue -> rights[lit] + gutter - viewport.floatValue
            else -> now.toFloat()
        }.toInt().coerceIn(0, scroll.maxValue)
        if (target != now) { if (motion) scroll.animateScrollTo(target) else scroll.scrollTo(target) }
    }
    // The fade under the bar starts below these chips, so it does not dim them.
    val fade = LocalTopFade.current
    val me = remember { Any() }
    DisposableEffect(fade) { onDispose { if (fade?.belowOwner === me) { fade.below = 0f; fade.belowOwner = null } } }
    Box(
        modifier.fillMaxWidth().testTag("jump chips").onSizeChanged { viewport.floatValue = it.width.toFloat(); fade?.let { f -> f.below = it.height.toFloat(); f.belowOwner = me } }
            .padding(vertical = Space.s)
            .slideAlong(
                labels, labels.size, { lit.coerceIn(0, labels.size - 1) }, { content.toFloat() / labels.size },
                onFinger = {}, onStep = { onLive(it) }, onEnd = { onLive(null); onJump(it) },
                onHold = { at ->
                    val x = at.x + scroll.value
                    held = at to labels.indices.firstOrNull { x >= lefts[it] && x <= rights[it] + CHIP_GAP_PX }.let { it ?: lefts.indexOfLast { l -> l <= x }.coerceAtLeast(0) }
                },
                onHoldEnd = { held = null },
            ),
    ) {
        Row(Modifier.horizontalScroll(scroll, enabled = false).onSizeChanged { content = it.width }, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(Space.l))
            for ((i, label) in labels.withIndex()) {
                if (i > 0) Spacer(Modifier.width(Space.s))
                GlassChip(
                    i == lit, { onJump(i) }, { Text(label, maxLines = 1) },
                    Modifier.testTag("chip $label").onGloballyPositioned { lefts[i] = it.positionInParent().x; rights[i] = lefts[i] + it.size.width },
                )
            }
            Spacer(Modifier.width(Space.l))
        }
        held?.let { (at, i) -> PeekPopup(at, "chip peek") { PeekHead(labels[i]); peek(i) } }
    }
}

/**
 * Round 40: a pill of the page's glass material for choosing a section: a soft tile with a lit edge and no outline; the chosen one is filled with the accent, its
 * words on-accent. The feedback for a tap is on the heading it leads to ([ShineHeading]), not on the chip.
 */
@Composable
fun GlassChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier.heightIn(min = 36.dp)
            .then(if (selected) Modifier.clip(PillShape).background(accent) else Modifier.tile(PillShape, frosted = true))
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (if (selected) on else MaterialTheme.colorScheme.onSurface),
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ) { label() }
    }
}

/** Test hook: how many times a heading's highlight has started, and how many of those swept. */
object HeadingShine { var started = 0; var swept = 0 }

/** One request to light a section's first heading; the first [SettingsGroup] title that reads it takes it, so a section with several groups lights one. */
class ShineRequest(val id: Int) { private var taken = false; fun take(): Boolean = if (taken) false else { taken = true; true } }

/** Set by Settings around the section a jump chip has just led to. */
val LocalHeadingShine = androidx.compose.runtime.compositionLocalOf<ShineRequest?> { null }

/**
 * Round 40: the heading a jump chip led to is lit once the page comes to rest, so a tap has an answer even where the page cannot scroll (its end): an accent pill
 * the width of the page grows in behind the heading, holds, and fades over about 1.4 s, with a bright band sweeping across it while it is full. With animations
 * off the pill is there for a second and goes at once; in the Light tier it fades with no sweep. Nothing changes when it ends; the layout never moves.
 */
@Composable
fun ShineHeading(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val request = LocalHeadingShine.current
    val mine = remember(request) { request?.take() == true }
    val motion = LocalMotion.current
    val flourish = flourishOn()
    val wash = remember { Animatable(0f) }
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(request) {
        if (!mine) { wash.snapTo(0f); sweep.snapTo(1f); return@LaunchedEffect }
        HeadingShine.started++
        if (!motion) { wash.snapTo(1f); kotlinx.coroutines.delay(1_000); wash.snapTo(0f); return@LaunchedEffect }
        kotlinx.coroutines.coroutineScope {
            launch {
                wash.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                kotlinx.coroutines.delay(520)
                wash.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            }
            if (flourish) launch {
                HeadingShine.swept++
                sweep.snapTo(0f)
                kotlinx.coroutines.delay(120)
                sweep.animateTo(1f, tween(SWEEP_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.fillMaxWidth().testTag(if (mine) "heading shine" else "heading")
            .drawBehind {
                val a = wash.value
                if (a <= 0f) return@drawBehind
                val pad = Space.xs.toPx()
                val top = -pad
                val h = size.height + 2 * pad
                val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
                drawRoundRect(accent.copy(alpha = WASH_ALPHA * a), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, h), r)
                val p = sweep.value
                if (p < 1f) {
                    val w = size.width
                    val x = -0.3f * w + p * 1.6f * w
                    val band = SWEEP_ALPHA * a * (1f - kotlin.math.abs(2f * p - 1f))
                    drawRoundRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.White.copy(alpha = band), 1f to Color.Transparent, startX = x - 0.25f * w, endX = x + 0.25f * w),
                        Offset(0f, top), androidx.compose.ui.geometry.Size(w, h), r,
                    )
                }
            },
    ) { content() }
}

private const val SWEEP_MILLIS = 650
private const val WASH_ALPHA = 0.30f
private const val SWEEP_ALPHA = 0.45f

private const val CHIP_GAP_PX = 16f


/** A section the search or a chip scrolls to is left this far below the top of the list, clear of the top fade. */
val CHIP_BAR_STUCK = 44.dp

/**
 * A section of a page, washed in the accent for a moment when a search has just led to it (Round 37): the wash holds a little, then fades over a second; with
 * animations off it is there for the same time and goes at once. [on] false draws nothing; [done] is called when the wash is over.
 */
@Composable
fun SettingsFlash(on: Boolean, done: () -> Unit, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    val wash = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(on) {
        if (!on) { wash.snapTo(0f); return@LaunchedEffect }
        wash.snapTo(1f)
        kotlinx.coroutines.delay(700)
        if (motion) wash.animateTo(0f, tween(900)) else { kotlinx.coroutines.delay(500); wash.snapTo(0f) }
        done()
    }
    val accent = clean.app.ui.theme.LocalLook.current.orange
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().testTag(if (on) "settings flash" else "settings section")
            .drawBehind { if (wash.value > 0f) drawRect(accent.copy(alpha = 0.14f * wash.value)) },
        content = { content() },
    )
}
