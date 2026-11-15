package clean.app

import clean.app.ui.theme.toGrey
import clean.app.ui.theme.onInk
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.staticCompositionLocalOf
import clean.app.ui.theme.numbers
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign

import clean.core.parseStrongDate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.heroOutlineAccent
import clean.app.ui.theme.tileOutlined
import clean.app.ui.theme.orange
import clean.app.ui.theme.LocalTextScale
import clean.app.ui.theme.presetScale
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

/** A colour of the user's or the app's own as the look shows it: its grey of the same lightness in Mono, which has no hue. */
@Composable
fun Color.inLook(): Color = if (LocalLook.current.mono) toGrey() else this

/** Activity calendar colours, least to most. The first entry is the default. */
val PALETTES: Map<String, List<Color>> = linkedMapOf(
    "Ember" to listOf(Color(0xFF5C2A12), Color(0xFF9A4A14), Color(0xFFDD7A1A), Color(0xFFFFC53D)),
    "Ocean" to listOf(Color(0xFF123A5C), Color(0xFF1B62A0), Color(0xFF2E93E0), Color(0xFF7FD0FF)),
    "Violet" to listOf(Color(0xFF3A1F5C), Color(0xFF6236A0), Color(0xFF9257E0), Color(0xFFCDA6FF)),
    "Jet" to listOf(Color(0xFF1F4BFF), Color(0xFF12D6C6), Color(0xFFFFE21A), Color(0xFFFF3A1A)),
    "Viridis" to listOf(Color(0xFF443A83), Color(0xFF31688E), Color(0xFF35B779), Color(0xFFFDE725)),
    "Green" to listOf(Color(0xFF1F5C2E), Color(0xFF2A8A40), Color(0xFF34B454), Color(0xFF4CE06F)),
    "Mono" to listOf(Color(0xFF555555), Color(0xFF858585), Color(0xFFB5B5B5), Color(0xFFEFEFEF)),
)

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
    val look = LocalLook.current
    if (look.sheetPage) RegisterSheetBar()
    TopAppBar(
        title = { Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (look.twoToneTitles) FontWeight.SemiBold else null) },
        navigationIcon = { if (look.sheetPage) HeaderButton(CleanIcons.Back, "Back", onBack) else IconButton(onClick = onBack) { Icon(CleanIcons.Back, "Back") } },
        actions = { if (look.sheetPage) HeaderActions { actions() } else actions() },
        // On the page colour in Linen, where the sheet begins under the bar.
        colors = if (look.sheetPage) TopAppBarDefaults.topAppBarColors(containerColor = look.page, scrolledContainerColor = look.page) else TopAppBarDefaults.topAppBarColors(),
    )
}

/**
 * The big heading at the top of a tab, with that tab's actions above it on the right. Linen sets it in two tones, [text] in the
 * text colour and the optional [second] line under it in the faint grey, with the actions on the same row at the right.
 */
@Composable
fun Title(text: String, second: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val look = LocalLook.current
    if (look.twoToneTitles) {
        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.m, bottom = Space.l), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (second != null) Text(second, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = look.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        return
    }
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(end = Space.xs), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
        Text(text, Modifier.padding(start = Space.l, bottom = Space.m).semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, spacing: Dp = Space.s, inner: Dp = Space.l, outer: Dp = Space.s, hero: Boolean = false, onClick: (() -> Unit)? = null, bleed: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // The air in and around a card follows the text size chosen in Settings.
    val scale = LocalTextScale.current
    val look = LocalLook.current
    val primary = MaterialTheme.colorScheme.primary
    // A card is a step above the page as the look says: see-through or solid, with or without an outline and a soft shadow; the hero (Home's Today) is washed with the accent when the look tints it.
    val card = cardFill()
    val shape = cardShape()
    val tinted = hero && look.heroTint > 0f
    val fill = if (!tinted) card else if (look.cardAlpha < 1f) primary.copy(alpha = look.heroTint) else lerp(card, primary, look.heroTint)
    Card(
        // A card that opens something is pressed as a whole: it dips, and the ripple keeps to its corners.
        modifier = Modifier.fillMaxWidth().then(if (LocalHalfCard.current) Modifier else Modifier.padding(horizontal = Space.l, vertical = outer * scale)).then(modifier)
            .then(if (look.shadow) Modifier.shadow(3.dp, shape, clip = false) else Modifier)
            .then(if (onClick != null) Modifier.clip(shape).pressable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = fill),
        border = if (hero && look.heroOutlineAccent()) BorderStroke(look.outlineWidth.dp, primary.copy(alpha = 0.35f)) else cardBorder(),
    ) {
        // [spacing] is the gap between the lines of the card; lists of sets ask for a tight one.
        Column {
            Column(Modifier.padding(horizontal = Space.l, vertical = inner * scale), verticalArrangement = Arrangement.spacedBy(spacing * scale)) {
                if (title.isNotEmpty()) SectionTitle(title)
                content()
            }
            // A chart that runs to the card's left, right and bottom edges; the card's shape clips it.
            bleed?.invoke(this)
        }
    }
}

/** A section's title, in a card or over a group of rows (a month, a folder, a day): one style everywhere, read out as a heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = textAlign)
}

/** A card's fill as the look has it: solid, see-through or transparent. [Section] and the tiles share it. */
@Composable
fun cardFill(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalLook.current.cardAlpha)

/** A stat tile's fill: the card's, or Linen's soft grey step under the white sheet (which then has no hairline, see [tileBorder]). */
@Composable
fun tileFill(): Color = LocalLook.current.let { if (it.tile == it.raised) cardFill() else it.tile }

/** A stat tile's outline: the card's, except Linen's hairline, which a tile in its own grey goes without. */
@Composable
fun tileBorder(): BorderStroke? = if (LocalLook.current.tileOutlined()) cardBorder() else null

/** A card's outline as the look has it, or none. */
@Composable
fun cardBorder(): BorderStroke? {
    val look = LocalLook.current
    return if (look.outlineWidth > 0f) BorderStroke(look.outlineWidth.dp, look.outline ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) else null
}

/** Whether the card being drawn is one of Home's half-width cards: the layout then places it, so it adds no margin of its own. */
val LocalHalfCard = staticCompositionLocalOf { false }

/** Whether charts in Home's cards run to the card's edges (Settings, Appearance, Home) rather than keep the card's padding. */
val LocalEdgeCharts = staticCompositionLocalOf { false }

@Composable
fun LabelValue(label: String, value: String, spoken: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (spoken != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spoken } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = LocalTextStyle.current.numbers())
    }
}

/**
 * A filter chip. In Linen it is fully round, outlined by a hairline, and orange with white words when selected; elsewhere it is
 * Material's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (!look.sheetPage) { FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier); return }
    FilterChip(
        selected = selected, onClick = onClick, label = label, modifier = modifier, shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = look.orange, selectedLabelColor = Color.White),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = look.divider ?: look.muted, selectedBorderColor = Color.Transparent),
    )
}

/** Mutually exclusive choices, wrapping onto more lines when they do not fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        for (option in options) CleanChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
    }
}

/**
 * Choices of a range or a period (1M / 3M / 1Y / All, Week / Month / Year / All): the chips everywhere but Linen, where the selected
 * one is a black pill with white words and the others plain quiet words, no outlines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> RangeChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val look = LocalLook.current
    if (!look.sheetPage) { Choice(options, selected, label, onSelect); return }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        for (option in options) {
            val on = option == selected
            Box(
                Modifier.minTouch().clip(CircleShape).background(if (on) look.text else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(option) }.padding(horizontal = Space.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = if (on) look.onInk else look.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

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

/** The calendar colours for a stored choice: a named map, or a ramp from dark up to a colour of the user's own. */
fun paletteOf(name: String): List<Color> =
    PALETTES[name] ?: presetScale(name) ?: parseColor(name)?.let { c -> listOf(0.45f, 0.63f, 0.82f, 1f).map { lerp(Color.Black, c, it) } } ?: PALETTES.values.first()

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
