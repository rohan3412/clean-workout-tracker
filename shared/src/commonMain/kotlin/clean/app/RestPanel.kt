package clean.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import clean.app.ui.theme.orange
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.GlassWindowShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import clean.app.ui.theme.workoutSkin
import clean.core.parseRestPresets
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What a workout's rows do when a set is ticked: the tick's buzz (light, off with Animations off) and the swipes (left removes, with Undo; right does nothing since round 28: a set is finished by its tick box only). */
class Feel(val tick: String = "light", val swipeRight: String = "off", val swipeLeft: String = "delete")

val LocalFeel = compositionLocalOf { Feel() }

/** The set that comes next in the workout, for the rest to name: the card's exercise, the set's place in it and its typed weight and reps, if any. */
class NextSet(val exercise: String, val number: Int, val values: String?) {
    /** "Next: Bench press · set 3 · 85 × 5". */
    fun words(withExercise: Boolean = true) = "Next: " + listOfNotNull(exercise.takeIf { withExercise }, "set $number", values).joinToString(" · ")
}

fun nextSetOf(draft: Draft): NextSet? {
    val block = nextBlock(draft)?.let { draft.blocks.getOrNull(it) } ?: return null
    val sets = block.items.filterIsInstance<DraftSet>()
    val at = sets.indexOfFirst { !it.done }
    if (at < 0) return null
    val set = sets[at]
    val values = if (set.weight.isNotEmpty() && set.reps.isNotEmpty()) "${set.weight} × ${set.reps}" else null
    return NextSet(block.exercise, at + 1, values)
}

/** The rest lengths as chips; tapping one starts the rest again at that length. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestPresets(presets: List<Int>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        for (seconds in presets) CleanChip(selected = false, onClick = { onPick(seconds) }, label = { Text("$seconds s") }, modifier = Modifier.semantics { contentDescription = "Rest again for $seconds seconds" })
    }
}


/**
 * The rest in progress: a glass bar floating at the bottom of the workout, never over it (the full-screen panel is gone, by the user's word,
 * 2026-11-20). The time (tap it for the rest's options, [onOptions]), the next set in words, -15 s, +15 s and Skip, and a thin line draining
 * along its bottom edge. Swiping it down hides it for this rest, following the finger with resistance; a small pill in its place brings it back.
 * It reports its height ([onBarHeight], pixels; 0 when nothing shows) so the list can leave room for it, and sits [above] the number pad when
 * that is up. The top bar's own time stays, and the glow on the edges when a rest runs out.
 */
@Composable
fun RestDisplay(
    model: AppModel, draft: Draft, onOptions: () -> Unit, modifier: Modifier = Modifier, above: androidx.compose.ui.unit.Dp = 0.dp,
    onBarHeight: (Int) -> Unit = {}, solid: Boolean = LocalSolidGlass.current, content: @Composable () -> Unit,
) {
    val running = model.restEnd != null
    // Per rest: the bar was swiped down.
    var hidden by remember(running) { mutableStateOf(false) }
    val motion = LocalMotion.current
    // The last values of a rest, so the bar can fade out showing what it showed.
    val kept = remember { longArrayOf(0, 1) }
    model.restEnd?.let { kept[0] = it; kept[1] = model.restTotal.coerceAtLeast(1).toLong() }
    val next = remember(draft) { nextSetOf(draft) }
    // D6: when a rest runs out (not when it is skipped), a soft glow of the accent on the screen's edges fades in and out, about 1.2 s, bar up or not.
    val edge = remember { Animatable(0f) }
    val ranOut = remember { model.restRanOut }
    LaunchedEffect(model.restRanOut) {
        if (model.restRanOut == ranOut || !motion) return@LaunchedEffect
        edge.animateTo(1f, Motion.restGlowIn())
        edge.animateTo(0f, Motion.restGlowOut())
    }
    val accent = clean.app.ui.theme.LocalLook.current.orange
    Box(
        modifier.fillMaxSize().drawWithContent {
            drawContent()
            val lit = edge.value
            if (lit > 0f) {
                val d = 56.dp.toPx()
                val c = accent.copy(alpha = 0.45f * lit)
                val clear = androidx.compose.ui.graphics.Color.Transparent
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c, clear), 0f, d), size = androidx.compose.ui.geometry.Size(size.width, d))
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(clear, c), size.height - d, size.height), Offset(0f, size.height - d), androidx.compose.ui.geometry.Size(size.width, d))
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c, clear), 0f, d), size = androidx.compose.ui.geometry.Size(d, size.height))
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, c), size.width - d, size.width), Offset(size.width - d, 0f), androidx.compose.ui.geometry.Size(d, size.height))
            }
        },
    ) {
        content()
        // The bar and the pill are floating glass over the page (a sibling drawn after the page, which is the blur source); [solid] is the app's own setting, not the workout page's quiet one.
        androidx.compose.runtime.CompositionLocalProvider(LocalSolidGlass provides solid) {
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = above).onSizeChanged { onBarHeight(it.height) }) {
            AnimatedVisibility(running && !hidden, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
                RestBar(kept, next, motion, { model.changeRest(it) }, { model.startRest(0) }, { hidden = true }, onOptions)
            }
            // The pill that stands in for the hidden bar.
            AnimatedVisibility(running && hidden, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
                // Reads the clock itself, so only the pill is redrawn each second.
                RestPill(((kept[0] - nowMillis()) / 1000).coerceAtLeast(0)) { hidden = false }
            }
        }
        }
    }
}

/** The bar itself, with E14's swipe: down follows the finger with resistance, then either hides it or springs back. [kept] is the rest's end and its length in seconds. */
@Composable
private fun RestBar(kept: LongArray, next: NextSet?, motion: Boolean, onAdd: (Int) -> Unit, onSkip: () -> Unit, onHide: () -> Unit, onOptions: () -> Unit) {
    val now = nowMillis()
    val left = ((kept[0] - now) / 1000).coerceAtLeast(0)
    val drain = animateFloatAsState(((kept[0] - now) / (kept[1] * 1000f)).coerceIn(0f, 1f), Motion.clock(), label = "rest bar")
    val drag = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var height by remember { mutableStateOf(1) }
    var raw by remember { mutableStateOf(0f) }
    // Round 20: one light tick as the swipe down passes the point where letting go hides the bar.
    val tick = rememberTick()
    var wasFar by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val limit = with(density) { 120.dp.toPx() }
    val line = with(density) { 3.dp.toPx() }
    val away = with(density) { 80.dp.toPx() }
    val track = MaterialTheme.colorScheme.primary
    val compact = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.m, vertical = Space.xs)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s).onSizeChanged { height = it.height.coerceAtLeast(1) }.graphicsLayer { translationY = drag.value }.pointerInput(motion) {
            detectVerticalDragGestures(
                onDragEnd = {
                    val far = drag.value > limit * 0.4f
                    raw = 0f; wasFar = false
                    if (far && !motion) onHide()
                    else scope.launch { if (far) { drag.animateTo(height.toFloat() + away, Motion.leave()); onHide() } else drag.animateTo(0f, Motion.follow()) }
                },
                onDragCancel = { raw = 0f; wasFar = false; scope.launch { drag.animateTo(0f, Motion.follow()) } },
            ) { change, amount ->
                change.consume()
                raw = (raw + amount).coerceAtLeast(0f)
                val shown = if (motion) Motion.rubber(raw, limit) else raw
                val pastIt = shown > limit * 0.4f
                if (pastIt && !wasFar) tick()
                wasFar = pastIt
                scope.launch { drag.snapTo(shown) }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("rest bar").glass(GlassWindowShape, lift = 12.dp).clip(GlassWindowShape)
                .drawBehind { drawRect(track, Offset(0f, size.height - line), androidx.compose.ui.geometry.Size(size.width * drain.value, line)) }
                .padding(start = Space.l, end = Space.s, top = Space.xs, bottom = Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    clock(left), Modifier.weight(1f).clickable(onClickLabel = "Rest options", onClick = onOptions).minTouch().wrapContentHeight(Alignment.CenterVertically)
                        .semantics { contentDescription = "Rest, ${clock(left)} left" },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
                QuietButton("−15 s", { onAdd(-15) }, contentPadding = compact)
                QuietButton("+15 s", { onAdd(15) }, contentPadding = compact)
                MainButton("Skip", onSkip, contentPadding = compact)
            }
            if (next != null) Text(next.words(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RestPill(left: Long, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = Space.s), contentAlignment = Alignment.Center) {
        Surface(Modifier.glass(clean.app.ui.theme.PillShape, lift = 8.dp).minTouch().clickable(onClickLabel = "Show the rest", onClick = onClick), shape = clean.app.ui.theme.PillShape, color = androidx.compose.ui.graphics.Color.Transparent) {
            Text("Rest ${clock(left)}", Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
