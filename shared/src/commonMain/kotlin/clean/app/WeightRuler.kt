package clean.app

import clean.core.time.*
import kotlinx.datetime.LocalTime
import androidx.compose.animation.AnimatedVisibility
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
import clean.app.platform.LocalPlatform
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
import clean.core.padKey
import clean.core.padValue
import kotlinx.datetime.LocalDateTime
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
fun weightText(value: Double) = fixed(value, 2)

/** What the number pad is typing into on the weigh-in sheet. */
private enum class PadFor { WEIGHT, HOUR, MINUTE }

/**
 * Logs a body weight: the value as a big number (tap it to type on the app's own pad), a ruler to drag under it, when it was (now, or a day and time
 * picked on a small card), and Save. It opens at the last weigh-in, growing out of the "+" at [origin] when it has one. The number is in the profile's
 * unit; the entry is stored in kg like any other.
 */
@Composable
fun WeightSheet(model: AppModel, title: String = "Log body weight", onPick: ((Double) -> Unit)? = null, origin: androidx.compose.ui.geometry.Rect? = null, onRemove: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val last = weighIns(model).lastOrNull()?.second
    val scope = rememberCoroutineScope()
    val spec = remember(unit) { weightRuler(unit) }
    var value by remember { mutableStateOf(last?.let { roundHalfUp((if (unit == "lb") kgToLb(it) else it) * WEIGHT_TICKS) / WEIGHT_TICKS.toDouble() } ?: if (unit == "lb") 155.0 else 70.0) }
    // When: untouched it is the moment Save is pressed; once a day or time is chosen it is that day at that hour and minute.
    val now = remember { LocalDateTime.now() }
    val today = model.today
    var day by remember { mutableStateOf(now.toLocalDate()) }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var touched by remember { mutableStateOf(false) }
    // The pad: what it types into, the text so far, and whether nothing has been pressed yet (the first key replaces).
    var padFor by remember { mutableStateOf<PadFor?>(null) }
    var padText by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(true) }
    fun openPad(target: PadFor) {
        padFor = target
        padText = when (target) { PadFor.WEIGHT -> clean.core.shortNumber(value); PadFor.HOUR -> hour.toString(); PadFor.MINUTE -> minute.toString() }
        fresh = true
    }
    fun donePad() {
        when (padFor) {
            PadFor.WEIGHT -> padValue(padText, spec.min / spec.perUnit.toDouble(), spec.max / spec.perUnit.toDouble(), spec.perUnit)?.let { value = it }
            PadFor.HOUR -> padValue(padText, 0.0, 23.0, 1)?.let { hour = it.toInt(); touched = true }
            PadFor.MINUTE -> padValue(padText, 0.0, 59.0, 1)?.let { minute = it.toInt(); touched = true }
            null -> {}
        }
        padFor = null
    }
    Sheet(onDismiss, title, origin) {
        Column(Modifier.padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
            val target = padFor
            if (target != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom) {
                    Text(padText.ifEmpty { "0" }, Modifier.semantics { contentDescription = "Typing ${target.name.lowercase()}" }, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(when (target) { PadFor.WEIGHT -> unit; PadFor.HOUR -> "hour"; PadFor.MINUTE -> "minute" }, Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TypePad({ key -> padText = padKey(padText, key, fresh); fresh = false }, ::donePad, decimal = target == PadFor.WEIGHT)
            } else {
                Row(
                    Modifier.clickable(onClickLabel = "Type the weight") { openPad(PadFor.WEIGHT) }.minTouch().semantics(mergeDescendants = true) { contentDescription = "Body weight ${weightText(value)} ${unitWord(unit)}" },
                    horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Bottom,
                ) {
                    Text(weightText(value), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(unit, Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Ruler(value, { value = it }, spec, mark = last?.let { if (unit == "lb") kgToLb(it) else it }, detent = true)
                // Backdated entries, kept behind one quiet line: "Now ›", or the day and time once chosen; a tap opens the day chips and time wheels.
                if (onPick == null) {
                    var whenOpen by remember { mutableStateOf(false) }
                    val fold = LocalMotion.current
                    val label = if (touched) weighInDayLabel(today, day) + " · " + clean.core.zeroPad(hour.toLong(), 2) + ":" + clean.core.zeroPad(minute.toLong(), 2) else "Now"
                    Row(
                        Modifier.clickable(onClickLabel = if (whenOpen) "Hide when" else "Change when") { whenOpen = !whenOpen }.minTouch()
                            .semantics(mergeDescendants = true) { contentDescription = "Weighed $label" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (whenOpen) " ‹" else " ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(whenOpen, enter = Motion.foldIn(fold), exit = Motion.foldOut(fold)) {
                        WhenCard(
                            today, day, hour, minute,
                            { day = it; touched = true }, { hour = it; touched = true }, { minute = it; touched = true },
                            { minutes -> openPad(if (minutes) PadFor.MINUTE else PadFor.HOUR) },
                        )
                    }
                }
                MainButton("Save", {
                    if (value <= 0) model.message = "Enter a number"
                    else if (onPick != null) {
                        onPick(if (unit == "lb") value * KG_PER_LB else value)
                        onDismiss()
                    } else scope.launch {
                        // Body weight is stored in kg whatever the profile shows.
                        val stored = if (unit == "lb") value * KG_PER_LB else value
                        val at = if (touched) LocalDateTime.of(day, LocalTime.of(hour, minute, now.second)) else LocalDateTime.now()
                        if (model.addMeasurement("weight.csv", stored, at.format(STRONG_DATE_FORMAT))) { model.buzz(Haptic.Confirm); onDismiss() }
                    }
                }, Modifier.fillMaxWidth().heightIn(min = 52.dp))
                // A goal's sheet is also where the goal is taken away.
                if (onRemove != null) DangerButton("Remove goal", { onRemove(); onDismiss() })
            }
        }
    }
}

private fun unitWord(unit: String) = if (unit == "lb") "pounds" else "kilograms"

/**
 * What a ruler measures: its range in ticks, how many ticks make one unit of the value, which ticks carry a label (and what it says),
 * and the words a screen reader gets. A weight is ticks of 0.05; the welcome questionnaire's height is ticks of a centimetre or an inch.
 */
class RulerSpec(
    val min: Int, val max: Int, val perUnit: Int, val labelEvery: Int, val label: (Int) -> String,
    val name: String, val spoken: (Double) -> String, val step: String,
)

fun weightRuler(unit: String) =
    RulerSpec(RULER_MIN * WEIGHT_TICKS, rulerMax(unit) * WEIGHT_TICKS, WEIGHT_TICKS, WEIGHT_TICKS, { (it / WEIGHT_TICKS).toString() }, "Body weight", { "${weightText(it)} ${unitWord(unit)}" }, "0.05")

/**
 * A horizontal ruler under a fixed marker in the accent: a tick for each step of [spec], longer halfway between the labelled ones. Dragged, it
 * flings and settles on a tick, with a light tick of the phone for each whole unit passed (only with animations on). All of it is one canvas drawn
 * from the scroll position, so there is nothing to compose per tick. For a screen reader it is a slider with an increase and a decrease.
 */
@Composable
fun Ruler(value: Double, onChange: (Double) -> Unit, spec: RulerSpec, mark: Double? = null, detent: Boolean = false) {
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
    // While the finger or the fling moves it, the value follows, with the lightest tick of the phone for each step passed (not with Animations or
    // Haptic ticks off; at most one every 25 ms, so a fast fling is a buzz rather than a hammer).
    val platform = LocalPlatform.current
    val buzz = rememberTick()
    val firm = rememberDetent()
    LaunchedEffect(Unit) {
        var lastTick = 0L
        snapshotFlow { pos.value.roundToInt() }.collect { ticks ->
            if (!moving) return@collect
            changed(ticks / perUnit)
            val now = platform.uptimeMillis()
            // A whole unit (a whole kilo, a whole pound) is a firmer detent than the steps between, and is never held back by the pause between ticks.
            if (motion && detent && ticks % spec.perUnit == 0) { firm(); lastTick = now }
            else if (motion && now - lastTick > 25) { buzz(); lastTick = now }
        }
    }
    val drag = rememberDraggableState { delta -> scope.launch { pos.snapTo(pos.value - delta / step) } }
    val measurer = rememberTextMeasurer(cacheSize = 32)
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
                    // A slow drag stays where it was let go; a quick swipe carries on, gently slowing: a few kilos at a hard flick.
                    if (abs(velocity) > 700f) pos.animateDecay(-velocity / step, exponentialDecay(frictionMultiplier = 1f))
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
        // Where the last weigh-in was: a small faint dot under the ticks.
        if (mark != null) {
            val x = centre + (mark * perUnit - at).toFloat() * step
            if (x > -step && x < size.width + step) drawCircle(accent.copy(alpha = 0.45f), 2.5.dp.toPx(), Offset(x, 62.dp.toPx()))
        }
        drawLine(accent, Offset(centre, 0f), Offset(centre, 44.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
}
