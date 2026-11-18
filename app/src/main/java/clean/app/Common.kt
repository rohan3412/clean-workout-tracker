package clean.app

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign

import clean.core.parseStrongDate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.orange
import clean.app.ui.theme.LocalTextScale
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableState
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import clean.core.SetItem
import clean.core.SetType
import clean.core.shortNumber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The colours a sticky note's band can have on a dark page; dark, so the white text on them reads. The first is the default. */
val STICKY_COLORS: Map<String, Color> = linkedMapOf(
    "Amber" to Color(0xFF4A3D10),
    "Red" to Color(0xFF581C1F),
    "Green" to Color(0xFF15402A),
    "Teal" to Color(0xFF0F3F45),
    "Blue" to Color(0xFF14315E),
    "Purple" to Color(0xFF38225E),
    "Grey" to Color(0xFF2B2D34),
)

/** The same colours on a light page: pale tints of each hue, so the band reads as a note there and its text turns dark. */
val STICKY_COLORS_LIGHT: Map<String, Color> = linkedMapOf(
    "Amber" to Color(0xFFFBE7A6),
    "Red" to Color(0xFFF8D2CF),
    "Green" to Color(0xFFCDEBD7),
    "Teal" to Color(0xFFC7E8EB),
    "Blue" to Color(0xFFD3E1F8),
    "Purple" to Color(0xFFE3D7F6),
    "Grey" to Color(0xFFE2E3E7),
)

/** A sticky note's band: [name] from the list for a dark or light page, else the user's own colour as they picked it. */
fun stickyBand(name: String, dark: Boolean): Color {
    val named = if (dark) STICKY_COLORS else STICKY_COLORS_LIGHT
    return named[name] ?: parseColor(name) ?: named.values.first()
}

private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val DAY_THIS_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

fun parseDate(date: String): LocalDateTime? = parseStrongDate(date)
fun prettyDate(date: String): String = parseDate(date)?.format(FULL) ?: date
fun prettyDay(date: String): String = parseDate(date)?.let { prettyDay(it.toLocalDate()) } ?: date
/** A day as a line shows it: the year only when it is not this year's, as History's cards do. */
fun prettyDay(day: LocalDate): String = day.format(if (day.year == LocalDate.now().year) DAY_THIS_YEAR else DAY)

fun duration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "${seconds}s"
}

fun clock(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** `40 × 9`, `12 reps`, `0:59`, `9450 m · 30:32`, whatever the row holds. */
fun setValues(set: SetItem, unit: String): String {
    val parts = mutableListOf<String>()
    val w = set.weight
    val r = set.reps
    when {
        w != null && r != null -> parts += "${showWeight(w, unit)} $unit × $r"
        r != null -> parts += "$r reps"
        w != null -> parts += "${showWeight(w, unit)} $unit"
    }
    set.distance?.let { parts += "${shortNumber(it)} m" }
    if (w == null || r == null) set.seconds?.let { parts += clock(it.toLong()) }
    // How hard the set felt, when that was noted.
    return parts.joinToString(" · ") + if (set.row.rpe.isBlank()) "" else " @${set.row.rpe}"
}

fun typeName(type: SetType) = when (type) {
    SetType.NORMAL -> "Normal"
    SetType.WARMUP -> "Warm-up"
    SetType.FAILURE -> "Failure"
    SetType.DROP -> "Drop set"
}

@Composable
fun typeColor(type: SetType): Color = when (type) {
    SetType.NORMAL -> MaterialTheme.colorScheme.onSurface
    SetType.WARMUP -> LocalCleanColors.current.warmup
    SetType.FAILURE -> LocalCleanColors.current.failure
    SetType.DROP -> LocalCleanColors.current.drop
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { MorphText(title, "title", Modifier.semantics { heading() }, onPage = true, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(CleanIcons.Back, "Back") } },
        actions = { actions() },
    )
}

/** The container of a Scaffold: the theme's background. */
@Composable
fun pageContainer(): Color = MaterialTheme.colorScheme.background

/** The big heading at the top of a tab, with that tab's actions above it on the right. */
@Composable
fun Title(text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(end = Space.xs), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
        Text(text, Modifier.padding(start = Space.l, bottom = Space.m).semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, spacing: Dp = Space.s, inner: Dp = Space.l, outer: Dp = Space.s, hero: Boolean = false, onClick: (() -> Unit)? = null, card: Boolean = hero || onClick != null, bleed: (@Composable ColumnScope.() -> Unit)? = null, hold: (() -> List<HoldAction>)? = null, content: @Composable ColumnScope.() -> Unit) {
    // The air in and around a card follows the text size chosen in Settings.
    val scale = LocalTextScale.current
    // D2: a raised card is only for one object that is opened as a whole; everything else is a section on the page, a small grey heading over its content.
    if (!card) {
        Column(Modifier.fillMaxWidth().then(modifier)) {
            val lines = rememberRowLines()
            Column(Modifier.drawRowLines(lines).padding(horizontal = Space.l, vertical = outer * scale), verticalArrangement = Arrangement.spacedBy(spacing * scale)) {
                if (title.isNotEmpty()) Text(
                    title.uppercase(), Modifier.padding(top = Space.xs).semantics { heading(); text = androidx.compose.ui.text.AnnotatedString(title) }, maxLines = 2,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.runtime.CompositionLocalProvider(LocalRowLines provides lines) { content() }
            }
            bleed?.invoke(this)
        }
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    // A card is a step above the page (a soft shadow in Light); the hero (Home's Today) is washed with the accent in Dark.
    val card = cardFill()
    val shape = cardShape()
    val tinted = hero && LocalLook.current.dark
    val fill = if (!tinted) card else lerp(card, primary, 0.1f)
    val edge: BorderStroke? = null
    val tap = if (onClick != null) cardTap(onClick) else null
    Card(
        // A card that opens something is pressed as a whole: it dips, and the ripple keeps to its corners.
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = outer * scale).then(modifier)
            .cardDepth(shape)
            .then(if (tap != null) Modifier.clip(shape).pressable(onClick = tap) else Modifier)
            .holdMenu(shape, hold, fill),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = fill),
        border = edge,
    ) {
        // [spacing] is the gap between the lines of the card; lists of sets ask for a tight one.
        // The card's content, not its surface, is what moves when it opens a page: the surface stays under the growing container.
        Column(if (tap != null) Modifier.opensInto(edge, fill) else Modifier) {
            Column(Modifier.padding(horizontal = Space.l, vertical = inner * scale), verticalArrangement = Arrangement.spacedBy(spacing * scale)) {
                if (title.isNotEmpty()) SectionTitle(title, role = "title")
                content()
            }
            // A chart that runs to the card's left, right and bottom edges; the card's shape clips it.
            bleed?.invoke(this)
        }
    }
}

/** A section's title, in a card or over a group of rows (a month, a folder, a day): one style everywhere, read out as a heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null, role: String? = null) {
    MorphText(androidx.compose.ui.text.AnnotatedString(text), role, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = textAlign)
}

/** A card's fill: solid, one step above the page. [Section] and the tiles share it. */
@Composable
fun cardFill(): Color = MaterialTheme.colorScheme.surfaceContainer

@Composable
fun LabelValue(label: String, value: String, spoken: String? = null, onClick: (() -> Unit)? = null) {
    val lines = LocalRowLines.current
    Row(
        Modifier.fillMaxWidth().then(if (lines != null) Modifier.rowLine(lines) else Modifier).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (lines != null) Modifier.padding(vertical = 6.dp) else Modifier)
            .then(if (spoken != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spoken } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = LocalTextStyle.current)
    }
}

/** A filter chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier)
}

/** Mutually exclusive choices, wrapping onto more lines when they do not fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        for (option in options) CleanChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
    }
}

/** [Choice] whose selected chip's words are the page's side of a [MorphText] with the role [morph]: a card's word that travels into the chip when the card opens this page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MorphChoice(options: List<T>, selected: T, label: (T) -> String, morph: String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        for (option in options) CleanChip(
            selected = option == selected, onClick = { onSelect(option) },
            label = { if (option == selected) MorphText(label(option), morph, onPage = true) else Text(label(option)) },
        )
    }
}

/** Choices of a range or a period (1M / 3M / 1Y / All, Week / Month / Year / All): a segmented control, since it switches what the page shows. */
@Composable
fun <T> RangeChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = Segmented(options, selected, label, onSelect = onSelect)

/** The phone's own date picker and then its time picker; [onPick] gets the choice, with [initial]'s seconds. Not in the future. */
fun pickDateTime(context: android.content.Context, initial: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
    val dialog = android.app.DatePickerDialog(context, { _, year, month, day ->
        android.app.TimePickerDialog(context, { _, hour, minute ->
            onPick(LocalDateTime.of(year, month + 1, day, hour, minute, initial.second))
        }, initial.hour, initial.minute, true).blinking().show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.blinking().show()
}

/** The one glyph for a value there is not. */
const val NONE = "–"

/** `1 workout`, `2 workouts`. */
fun plural(n: Int, word: String) = "$n ${if (n == 1) word else word + "s"}"

/** A count of sets already formatted as a number: `1 set`, `1.5 sets`. */
fun setsText(count: String) = "$count ${if (count == "1") "set" else "sets"}"

/** A volume in the profile's unit, with thousands grouped: `12,400 kg`. */
fun volumeText(kg: Double, unit: String): String =
    "%,d %s".format(Math.round(if (unit == "lb") clean.core.kgToLb(kg) else kg), unit)

/** A volume in the profile's unit: `12,400 kg`. */
fun AppModel.volume(kg: Double): String = volumeText(kg, profile.value?.unit ?: "kg")

/** A rest length: `Off`, or `0:45`, `2:00`. */
fun restLabel(seconds: Int) = if (seconds <= 0) "Off" else clock(seconds.toLong())

/** Seconds (`45`) or minutes and seconds (`1:30`), from 1 s to 30 min; null when it is neither. */
private fun parseRest(text: String): Int? {
    val t = text.trim()
    val seconds = if (':' in t) {
        val (m, s) = t.split(':', limit = 2)
        val minutes = m.toIntOrNull() ?: return null
        val rest = s.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        minutes * 60 + rest
    } else t.toIntOrNull() ?: return null
    return seconds.takeIf { it in 1..1800 }
}

/**
 * A rest length: the [label] with the time it is now ([value], by default [seconds] as a time) over the
 * [options] as chips, a stored length that is not one of them as a chip of its own, and a Custom chip for any
 * other length. [seconds] null means nothing is chosen (a time to set, not a setting); -1 is shown as [unset].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestChoice(
    label: String, seconds: Int?, options: List<Int>, onPick: (Int) -> Unit,
    value: String = seconds?.let { restLabel(it) }.orEmpty(), unset: String = "Same", custom: String = "Custom",
) {
    var typing by remember { mutableStateOf(false) }
    val shown = if (seconds != null && seconds !in options) (options + seconds).sorted() else options
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        LabelValue(label, value)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            for (option in shown) CleanChip(selected = option == seconds, onClick = { onPick(option) }, label = { Text(if (option < 0) unset else restLabel(option)) })
            CleanChip(selected = false, onClick = { typing = true }, label = { Text(custom) })
        }
    }
    if (typing) {
        var text by remember { mutableStateOf("") }
        val parsed = parseRest(text)
        CleanDialog(
            onDismissRequest = { typing = false },
            title = { Text(label) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    text, { text = it }, label = { Text("Seconds or m:ss") }, singleLine = true, isError = text.isNotEmpty() && parsed == null,
                    supportingText = { Text("From 1 second to 30 minutes, like 45 or 1:30.") },
                )
            },
            confirmButton = { TextButton(enabled = parsed != null, onClick = { onPick(parsed!!); typing = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The 1M / 3M / 1Y / All buttons over a measurement's chart. The choice is kept, and it is the same
 * one on the Profile tab's card and on the measurement's own page. Returns the days to show, 0 for all.
 */
@Composable
fun AppModel.measurementRange(): Long {
    var days by remembered("measurementRange", "90")
    RangeChoice(listOf(30L, 90L, 365L, 0L), days.toLongOrNull() ?: 90L, {
        when (it) {
            30L -> "1M"
            90L -> "3M"
            365L -> "1Y"
            else -> "All"
        }
    }) { days = it.toString() }
    return days.toLongOrNull() ?: 90L
}

/** The days [measurementRange] keeps, read without drawing its buttons: for a card that has no room for them (Home's Body weight; the buttons are on the weight page). */
@Composable
fun AppModel.measurementDays(): Long {
    val days by remembered("measurementRange", "90")
    return days.toLongOrNull() ?: 90L
}

/** A colour the user picked from the wheel is stored as `#AARRGGBB`; anything else is a name from one of the lists. */
fun parseColor(text: String?): Color? = text?.takeIf { it.startsWith("#") }?.drop(1)?.toLongOrNull(16)?.let { Color(it) }

fun Color.hex(): String = "#%08X".format(toArgb())

private val HUES = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }

/** The round rainbow button that opens the colour wheel; outlined when the current colour is a custom one. */
@Composable
fun WheelSwatch(size: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).background(Brush.sweepGradient(HUES), CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.requiredSize(48.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton, onClick = onClick).semantics { contentDescription = "Your own colour" })
    }
}

/** Any colour at all: hue around the wheel, stronger towards the rim, with a slider for brightness. */
@Composable
fun ColorPickerDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2].coerceAtLeast(0.05f)) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), saturation, value)
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Canvas(Modifier.size(220.dp).pointerInput(Unit) {
                    fun at(point: Offset) {
                        val d = point - Offset(size.width / 2f, size.height / 2f)
                        hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat() + 360f) % 360f)
                        saturation = (d.getDistance() / (size.width / 2f)).coerceIn(0f, 1f)
                    }
                    awaitEachGesture {
                        at(awaitFirstDown().position)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { at(it.position); it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                    drawCircle(Brush.sweepGradient(HUES))
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                    drawCircle(Color.Black.copy(alpha = 1f - value))
                    val angle = Math.toRadians(hue.toDouble())
                    val radius = size.width / 2f * saturation
                    val mark = center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
                    drawCircle(Color.Black, 11.dp.toPx(), mark, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Color.White, 9.dp.toPx(), mark, style = Stroke(3.dp.toPx()))
                }
                Text("Brightness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value, { value = it }, valueRange = 0.05f..1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Box(Modifier.size(width = 64.dp, height = 36.dp).background(color, SmallShape))
                    Text("#" + color.hex().drop(3), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A choice that is kept: which tab of a page was open, which view of a figure. It reads the stored
 * value (or [default]) and stores each change, so the page opens next time the way it was left.
 */
@Composable
fun AppModel.remembered(key: String, default: String): MutableState<String> {
    val all = options.collectAsState()
    // Only this key's value is watched, so a change to any other option does not redraw the reader.
    val stored by remember(key) { derivedStateOf { all.value["ui.$key"] } }
    // Held here as well, so a tap shows at once and not only when the stored value comes back.
    val local = remember(key) { mutableStateOf<String?>(null) }
    return object : MutableState<String> {
        override var value: String
            get() = local.value ?: stored ?: default
            set(new) {
                local.value = new
                setOption("ui.$key", new)
            }
        override fun component1() = value
        override fun component2(): (String) -> Unit = { value = it }
    }
}

/** An on/off option from Settings, by its key; [default] until the user has set it. */
@Composable
fun AppModel.flag(key: String, default: Boolean = true): Boolean {
    val all = options.collectAsState()
    // Only this key's value is watched, so a change to any other option does not redraw the reader.
    return remember(key, default) { derivedStateOf { all.value[key]?.let { it == "on" } ?: default } }.value
}
