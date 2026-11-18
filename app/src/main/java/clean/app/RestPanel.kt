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
import clean.app.ui.theme.workoutSkin
import clean.core.DEFAULT_REST_HIDE
import clean.core.REST_RETURN_SECONDS
import clean.core.parseRestPresets
import clean.core.restShowsFull
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What a workout's rows do when a set is ticked: the tick's buzz (light, off with Animations off) and the swipes (right finishes, left removes; the Correct page has no finishing). */
class Feel(val tick: String = "light", val swipeRight: String = "finish", val swipeLeft: String = "delete")

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
 * The rest in progress, the one style since 1.1.0: a full-screen countdown over the list that shrinks into a pill after
 * [DEFAULT_REST_HIDE] seconds (Hide does it at once; a tap on the pill brings it back, and it returns by itself for the last
 * [REST_RETURN_SECONDS]). The panel covers [content]. The top bar's own time stays, and its tap still opens the rest's options ([onOptions]).
 */
@Composable
fun RestDisplay(model: AppModel, draft: Draft, onOptions: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val presets = parseRestPresets(null)
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
        restShowsFull(kept[1] - left, left, DEFAULT_REST_HIDE, reopened) && (!hidden || left <= REST_RETURN_SECONDS)
    }
    val again = { seconds: Int -> reopened = false; hidden = false; model.startRest(seconds) }
    Column(modifier.fillMaxSize()) {
        // The pill that stands in for the hidden full screen.
        AnimatedVisibility(running, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
            val now = nowMillis()
            val left = ((kept[0] - now) / 1000).coerceAtLeast(0)
            if (!fullAt(now)) RestPill(left) { reopened = true; hidden = false }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            // Reads the clock itself, so only this layer is redrawn each second.
            val now = nowMillis()
            FullLayer(running && fullAt(now), motion, ((kept[0] - now) / 1000).coerceAtLeast(0), (kept[0] - now) / (kept[1] * 1000f), next, presets, again, { model.changeRest(it) }, { model.startRest(0) }, { hidden = true; reopened = false }, onOptions)
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
        Modifier.fillMaxSize().glass(androidx.compose.ui.graphics.RectangleShape, lift = 0.dp).pointerInput(Unit) { detectTapGestures { } }.padding(horizontal = Space.xxl, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Rest", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProgressRing(fraction, Modifier.padding(vertical = Space.l).size(260.dp).clickable(onClickLabel = "Rest options", onClick = onOptions), stroke = 10.dp) {
            Text(
                clock(left), Modifier.semantics { contentDescription = "Rest, ${clock(left)} left" }, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,
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
private fun RestPill(left: Long, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = Space.xs), contentAlignment = Alignment.Center) {
        Surface(Modifier.glass(RoundedCornerShape(50), lift = 8.dp).minTouch().clickable(onClickLabel = "Show the rest", onClick = onClick), shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color.Transparent) {
            Text("Rest ${clock(left)}", Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
