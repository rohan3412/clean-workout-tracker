package clean.app

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import clean.core.KG_PER_LB
import clean.core.STRONG_DATE_FORMAT
import clean.core.kgToLb
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The ruler steps in twentieths: 0.05 of a kg or lb. */
private const val WEIGHT_TICKS = 20

/** Smallest and largest weight the ruler shows, in kg or lb; a typed number may go beyond them. */
private const val RULER_MIN = 20
private fun rulerMax(unit: String) = if (unit == "lb") 880 else 400

/** A weight with the two decimals the ruler steps in, so the number does not change width as it is dragged. */
internal fun weightText(value: Double) = "%.2f".format(value)

/**
 * Logs a body weight: the value as a big number (tap it to type instead), a ruler to drag under it, the date (today, or one picked),
 * and Save. It opens at the last weigh-in. The number is in the profile's unit; the entry is stored in kg like any other.
 */
@Composable
fun WeightSheet(model: AppModel, title: String = "Log body weight", onPick: ((Double) -> Unit)? = null, onDismiss: () -> Unit) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val last = weighIns(model).lastOrNull()?.second
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(last?.let { Math.round((if (unit == "lb") kgToLb(it) else it) * WEIGHT_TICKS) / WEIGHT_TICKS.toDouble() } ?: if (unit == "lb") 155.0 else 70.0) }
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var at by remember { mutableStateOf<LocalDateTime?>(null) }
    fun commit() {
        parseNumber(text)?.takeIf { it > 0 }?.let { value = it }
        typing = false
    }
    Sheet(onDismiss, title) {
        Column(Modifier.padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
            if (typing) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    text, { text = it }, singleLine = true, suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.width(220.dp).focusRequester(focus),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
            } else {
                Row(
                    Modifier.clickable(onClickLabel = "Type the weight") { text = clean.core.shortNumber(value); typing = true }.minTouch().semantics(mergeDescendants = true) { contentDescription = "Body weight ${weightText(value)} ${unitWord(unit)}" },
                    horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom,
                ) {
                    Text(weightText(value), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(unit, Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Ruler(value, { value = it; typing = false }, weightRuler(unit))
            // Backdated entries: the phone's own date and time pickers.
            if (onPick == null) Row(
                Modifier.fillMaxWidth().clickable { pickDateTime(context, at ?: LocalDateTime.now()) { at = it } }.minTouch(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Date", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(at?.let { prettyDate(it.format(STRONG_DATE_FORMAT)) } ?: "Today")
            }
            MainButton("Save", {
                val v = if (typing) parseNumber(text) else value
                if (v == null || v <= 0) model.message = "Enter a number"
                else if (onPick != null) {
                    onPick(if (unit == "lb") v * KG_PER_LB else v)
                    onDismiss()
                } else scope.launch {
                    // Body weight is stored in kg whatever the profile shows.
                    val stored = if (unit == "lb") v * KG_PER_LB else v
                    if (model.addMeasurement("weight.csv", stored, (at ?: LocalDateTime.now()).format(STRONG_DATE_FORMAT))) onDismiss()
                }
            }, Modifier.fillMaxWidth().heightIn(min = 52.dp))
        }
    }
}

private fun unitWord(unit: String) = if (unit == "lb") "pounds" else "kilograms"

/**
 * What a ruler measures: its range in ticks, how many ticks make one unit of the value, which ticks carry a label (and what it says),
 * and the words a screen reader gets. A weight is ticks of 0.05; the welcome questionnaire's height is ticks of a centimetre or an inch.
 */
internal class RulerSpec(
    val min: Int, val max: Int, val perUnit: Int, val labelEvery: Int, val label: (Int) -> String,
    val name: String, val spoken: (Double) -> String, val step: String,
)

internal fun weightRuler(unit: String) =
    RulerSpec(RULER_MIN * WEIGHT_TICKS, rulerMax(unit) * WEIGHT_TICKS, WEIGHT_TICKS, WEIGHT_TICKS, { (it / WEIGHT_TICKS).toString() }, "Body weight", { "${weightText(it)} ${unitWord(unit)}" }, "0.05")

/**
 * A horizontal ruler under a fixed marker in the accent: a tick for each step of [spec], longer halfway between the labelled ones. Dragged, it
 * flings and settles on a tick, with a light tick of the phone for each whole unit passed (only with animations on). All of it is one canvas drawn
 * from the scroll position, so there is nothing to compose per tick. For a screen reader it is a slider with an increase and a decrease.
 */
@Composable
internal fun Ruler(value: Double, onChange: (Double) -> Unit, spec: RulerSpec) {
    val min = spec.min
    val max = spec.max
    val perUnit = spec.perUnit.toDouble()
    val motion = LocalMotion.current
    val step = with(LocalDensity.current) { 10.dp.toPx() }
    fun clamp(ticks: Double) = ticks.toFloat().coerceIn(min.toFloat(), max.toFloat())
    val pos = remember { Animatable(clamp(value * perUnit)).also { it.updateBounds(min.toFloat(), max.toFloat()) } }
    var moving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)
    // A number typed, or a step from a screen reader, moves the ruler to it.
    LaunchedEffect(value) { if (!moving && abs(pos.value - clamp(value * perUnit)) > 0.5f) pos.snapTo(clamp(value * perUnit)) }
    // While the finger or the fling moves it, the value follows, with the lightest tick of the phone for each whole unit (a kilo, a pound, a centimetre) passed;
    // there is none with Animations off.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        var lastTick = 0L
        var lastWhole = Math.floorDiv(pos.value.roundToInt(), spec.perUnit)
        snapshotFlow { pos.value.roundToInt() }.collect { ticks ->
            val whole = Math.floorDiv(ticks, spec.perUnit)
            if (!moving) { lastWhole = whole; return@collect }
            changed(ticks / perUnit)
            val now = SystemClock.uptimeMillis()
            if (whole != lastWhole) {
                lastWhole = whole
                if (motion && now - lastTick > 30) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastTick = now }
            }
        }
    }
    val drag = rememberDraggableState { delta -> scope.launch { pos.snapTo(pos.value - delta / step) } }
    val measurer = rememberTextMeasurer()
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val label = MaterialTheme.typography.labelMedium.copy(color = tick)
    val ticks = (value * perUnit).roundToInt()
    fun nudge(by: Int) { changed((ticks + by).coerceIn(min, max) / perUnit) }
    Canvas(
        Modifier.fillMaxWidth().height(76.dp).semantics {
            contentDescription = spec.name
            stateDescription = spec.spoken(value)
            progressBarRangeInfo = ProgressBarRangeInfo(ticks.toFloat(), min.toFloat()..max.toFloat(), 0)
            setProgress { target -> changed(target.roundToInt().coerceIn(min, max) / perUnit); true }
            customActions = listOf(
                CustomAccessibilityAction("Increase by ${spec.step}") { nudge(1); true },
                CustomAccessibilityAction("Decrease by ${spec.step}") { nudge(-1); true },
            )
        }.draggable(
            drag, Orientation.Horizontal,
            onDragStarted = { pos.stop(); moving = true },
            onDragStopped = { velocity ->
                if (motion) {
                    pos.animateDecay(-velocity / step, exponentialDecay(frictionMultiplier = 1.5f))
                    // Coasts, then settles exactly on a step of the ruler.
                    pos.animateTo(pos.value.roundToInt().toFloat(), Motion.follow())
                } else pos.snapTo(pos.value.roundToInt().toFloat())
                changed(pos.value.roundToInt() / perUnit)
                moving = false
            },
        ),
    ) {
        val centre = size.width / 2
        val at = pos.value
        val from = maxOf(floor(at - centre / step).toInt(), min)
        val to = minOf(ceil(at + centre / step).toInt(), max)
        for (i in from..to) {
            val x = centre + (i - at) * step
            val whole = i % spec.labelEvery == 0
            val half = i % (spec.labelEvery / 2) == 0
            val height = (if (whole) 30.dp else if (half) 22.dp else 14.dp).toPx()
            drawLine(tick.copy(alpha = if (whole) 0.9f else if (half) 0.7f else 0.4f), Offset(x, 0f), Offset(x, height), (if (whole) 2.dp else 1.dp).toPx())
            if (whole) {
                val text = measurer.measure(spec.label(i), label)
                drawText(text, topLeft = Offset(x - text.size.width / 2f, height + 4.dp.toPx()))
            }
        }
        drawLine(accent, Offset(centre, 0f), Offset(centre, 44.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
}
