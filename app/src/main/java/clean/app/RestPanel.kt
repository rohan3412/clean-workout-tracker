package clean.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.numbers
import clean.app.ui.theme.workoutSkin
import clean.core.DEFAULT_REST_HIDE
import clean.core.REST_RETURN_SECONDS
import clean.core.parseRestPresets
import clean.core.restShowsFull
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What a workout's rows do when a set is ticked or earns a PR: kv `feedback.tick` (off, light, strong) and `pr.celebrate` (none, glow, confetti). */
class Feel(val tick: String = "light", val celebrate: String = "none", val swipeRight: String = "finish", val swipeLeft: String = "delete")

val LocalFeel = compositionLocalOf { Feel() }

/** The set that comes next in the workout, for the rest to name: the card's exercise, the set's place in it and its typed weight and reps, if any. */
internal class NextSet(val exercise: String, val number: Int, val values: String?) {
    /** "Next: Bench press · set 3 · 85 × 5". */
    fun words(withExercise: Boolean = true) = "Next: " + listOfNotNull(exercise.takeIf { withExercise }, "set $number", values).joinToString(" · ")
}

internal fun nextSetOf(draft: Draft): NextSet? {
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
 * The rest in progress, shown as Settings asks (kv `rest.style`): a full-screen countdown over the list, a card with a ring, a slim
 * bar, or the full screen that shrinks into a pill after a few seconds. The strip styles sit above [content]; the full screen
 * covers it. The top bar's own time stays whatever the style, and its tap still opens the rest's options ([onOptions]).
 */
@Composable
fun RestDisplay(model: AppModel, draft: Draft, onOptions: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val stored by model.options.collectAsState()
    val style = stored["rest.style"]?.takeIf { it in listOf("full", "ring", "bar", "hide") } ?: "full"
    val hideAfter = stored["rest.hideAfter"]?.toIntOrNull() ?: DEFAULT_REST_HIDE
    val presets = parseRestPresets(stored["rest.presets"])
    val running = model.restEnd != null
    // Per rest: the pill was tapped (the full screen is back), or "Hide" was pressed.
    var reopened by remember(running) { mutableStateOf(false) }
    var hidden by remember(running) { mutableStateOf(false) }
    val motion = LocalMotion.current
    // The last values of a rest, so the panels can fade out showing what they showed.
    val kept = remember { longArrayOf(0, 1) }
    model.restEnd?.let { kept[0] = it; kept[1] = model.restTotal.coerceAtLeast(1).toLong() }
    val next = remember(draft) { nextSetOf(draft) }
    val fullAt: (Long) -> Boolean = { now ->
        val left = (kept[0] - now) / 1000
        when (style) {
            "full" -> !hidden
            "hide" -> restShowsFull(kept[1] - left, left, hideAfter, reopened) && (!hidden || left <= REST_RETURN_SECONDS)
            else -> false
        }
    }
    val again = { seconds: Int -> reopened = false; hidden = false; model.startRest(seconds) }
    Column(modifier.fillMaxSize()) {
        // The strip: a ring card, a slim bar, or the pill that stands in for a hidden full screen.
        AnimatedVisibility(running, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
            val now = nowMillis()
            val left = ((kept[0] - now) / 1000).coerceAtLeast(0)
            val fraction = (kept[0] - now) / (kept[1] * 1000f)
            when {
                style == "ring" -> RestRingCard(left, fraction, next, presets, again, { model.changeRest(it) }, { model.startRest(0) }, onOptions)
                style == "bar" -> RestBar(left, fraction, next, onOptions)
                !fullAt(now) -> RestPill(left) { reopened = true; hidden = false }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (style == "full" || style == "hide") {
                // Reads the clock itself, so only this layer is redrawn each second.
                val now = nowMillis()
                FullLayer(running && fullAt(now), motion, ((kept[0] - now) / 1000).coerceAtLeast(0), (kept[0] - now) / (kept[1] * 1000f), next, presets, again, { model.changeRest(it) }, { model.startRest(0) }, { hidden = true; reopened = false }, onOptions)
            }
        }
    }
}

/** The full-screen panel with its arrival and leaving; a function of its own so the animation is not confused with the enclosing scopes. */
@Composable
private fun FullLayer(
    visible: Boolean, motion: Boolean, left: Long, fraction: Float, next: NextSet?, presets: List<Int>,
    onPick: (Int) -> Unit, onAdd: (Int) -> Unit, onSkip: () -> Unit, onHide: () -> Unit, onOptions: () -> Unit,
) {
    AnimatedVisibility(visible, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
        RestFull(left, fraction, next, presets, onPick, onAdd, onSkip, onHide, onOptions)
    }
}

@Composable
private fun RestFull(left: Long, fraction: Float, next: NextSet?, presets: List<Int>, onPick: (Int) -> Unit, onAdd: (Int) -> Unit, onSkip: () -> Unit, onHide: () -> Unit, onOptions: () -> Unit) {
    // The panel takes the touches, so nothing in the list behind it is pressed by mistake.
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }.padding(horizontal = Space.xxl, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Rest", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProgressRing(fraction, Modifier.padding(vertical = Space.l).size(260.dp).clickable(onClickLabel = "Rest options", onClick = onOptions), stroke = 10.dp) {
            Text(
                clock(left), Modifier.semantics { contentDescription = "Rest, ${clock(left)} left" }, style = MaterialTheme.typography.displayLarge.numbers(), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (next != null) Text(next.words(), Modifier.padding(bottom = Space.l), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            QuietButton("−15 s", { onAdd(-15) }, Modifier.weight(1f))
            QuietButton("+15 s", { onAdd(15) }, Modifier.weight(1f))
            MainButton("Skip", onSkip, Modifier.weight(1f))
        }
        RestPresets(presets, onPick, Modifier.padding(top = Space.m))
        TextButton(onClick = onHide, modifier = Modifier.padding(top = Space.xs)) { Text("Hide") }
    }
}

@Composable
private fun RestRingCard(left: Long, fraction: Float, next: NextSet?, presets: List<Int>, onPick: (Int) -> Unit, onAdd: (Int) -> Unit, onSkip: () -> Unit, onOptions: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.xs), shape = cardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh, border = workoutSkin().panelBorder) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                ProgressRing(fraction, Modifier.size(72.dp).clickable(onClickLabel = "Rest options", onClick = onOptions)) {
                    Text(clock(left), Modifier.semantics { contentDescription = "Rest, ${clock(left)} left" }, style = MaterialTheme.typography.titleMedium.numbers(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(next?.words(withExercise = false) ?: "Rest", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = { onAdd(-15) }) { Text("−15 s") }
                        TextButton(onClick = { onAdd(15) }) { Text("+15 s") }
                        TextButton(onClick = onSkip) { Text("Skip", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            RestPresets(presets, onPick)
        }
    }
}

@Composable
private fun RestBar(left: Long, fraction: Float, next: NextSet?, onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Rest options", onClick = onOptions).padding(horizontal = Space.l, vertical = Space.s)) {
        Text(
            "Rest ${clock(left)}" + (next?.let { " · Next: " + (it.values ?: "set ${it.number}") } ?: ""), Modifier.minTouch().wrapContentHeight(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodyLarge.numbers(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        )
        val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.clock(), label = "bar")
        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(shown).height(3.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun RestPill(left: Long, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = Space.xs), contentAlignment = Alignment.Center) {
        Surface(Modifier.minTouch().clickable(onClickLabel = "Show the rest", onClick = onClick), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, border = workoutSkin().panelBorder) {
            Text("Rest ${clock(left)}", Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.labelLarge.numbers(), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * The celebration over a set row that has just earned a PR (kv `pr.celebrate`): a soft glow in the accent that fades, or a short burst
 * of small accent pieces from the tick's side. Only when the row earns it while it is on screen, and never with animations off.
 */
@Composable
fun Modifier.prCelebration(earned: Boolean): Modifier {
    val style = LocalFeel.current.celebrate
    val motion = LocalMotion.current
    val t = remember { Animatable(1f) }
    var was by remember { mutableStateOf(earned) }
    LaunchedEffect(earned) {
        if (earned && !was && motion && style != "none") {
            t.snapTo(0f)
            t.animateTo(1f, if (style == "confetti") Motion.confetti() else Motion.glow())
        }
        was = earned
    }
    val accent = MaterialTheme.colorScheme.primary
    val pieces = remember { List(36) { Piece(Random.Default) } }
    if (!motion || style == "none") return this
    return drawWithContent {
        drawContent()
        val p = t.value
        if (p >= 1f) return@drawWithContent
        if (style == "glow") {
            val fade = (1f - p) * (1f - p)
            drawRoundRect(accent.copy(alpha = 0.30f * fade), size = size, cornerRadius = CornerRadius(8.dp.toPx()))
            drawRoundRect(accent.copy(alpha = 0.9f * fade), size = size, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        } else {
            val origin = Offset(size.width - 28.dp.toPx(), size.height / 2)
            val reach = 130.dp.toPx()
            val ease = 1f - (1f - p) * (1f - p)
            for (piece in pieces) {
                val x = origin.x + cos(piece.angle) * piece.speed * reach * ease
                val y = origin.y + sin(piece.angle) * piece.speed * reach * ease + 90.dp.toPx() * p * p
                drawCircle(accent.copy(alpha = (1f - p * p).coerceIn(0f, 1f) * piece.strength), piece.radius.dp.toPx(), Offset(x, y))
            }
        }
    }
}

/** One piece of confetti: the direction it leaves in, how far it goes, its size and how bright it is. */
private class Piece(random: Random) {
    val angle = random.nextFloat() * 2f * Math.PI.toFloat()
    val speed = 0.35f + random.nextFloat() * 0.65f
    val radius = 1.5f + random.nextFloat() * 2.5f
    val strength = 0.55f + random.nextFloat() * 0.45f
}
