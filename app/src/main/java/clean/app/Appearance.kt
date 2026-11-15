package clean.app

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import clean.app.ui.theme.monoInk
import clean.app.ui.theme.monoPaper
import clean.app.ui.theme.monoSettingsOf
import clean.app.ui.theme.rgbHex
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.PRESETS
import clean.app.ui.theme.PRESET_LABELS
import clean.app.ui.theme.Part
import clean.app.ui.theme.SavedPreset
import clean.app.ui.theme.SAVED_REF
import clean.app.ui.theme.dayNightChoice
import clean.app.ui.theme.dayNightSaved
import clean.app.ui.theme.matchingSaved
import clean.app.ui.theme.partChoices
import clean.app.ui.theme.words
import clean.core.DEFAULT_DAY_FROM
import clean.core.DEFAULT_LATITUDE
import clean.core.DEFAULT_NIGHT_FROM
import clean.core.SWITCH_PHONE
import clean.core.SWITCH_SUN
import clean.core.SWITCH_TIMES
import clean.core.clockText
import clean.core.dayWindow
import clean.core.parseClock
import clean.core.shortNumber
import java.time.LocalDate
import java.time.ZoneId

/** The Day & night chip's id in `theme.preset`; the four presets' own ids are the keys of `PRESETS`. */
private const val DAYNIGHT = "daynight"
private val BASIC_PARTS = listOf(Part.BACKGROUND, Part.ACCENT, Part.COLOUR_SCALE, Part.CARDS, Part.OUTLINES, Part.SPEED, Part.ICON, Part.TAB_STYLE)
private val INK = Color(0xFF16181D)

/** Quiet explanatory text under a control. */
@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Mono's own settings; cleared, each is Mono's own value again. */
private val MONO_KEYS = listOf("mono.paper", "mono.ink", "mono.texture", "mono.negative", "mono.trueInvert")

/** Chips that fit in one row before "Show more" takes the rest. */
private const val SAVED_ROW = 4

/**
 * The five chips (Light, Dark, AMOLED, Mono, Day & night), then under "Custom" the looks the user saved, and two quiet buttons: save the current look
 * and reset to the default. Choosing a chip is a whole look; every mix and override is cleared (a saved one puts back exactly the keys it kept). A saved
 * preset shows as chosen while the stored look matches it; a hold on it opens Rename and Delete.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PresetSection(model: AppModel) {
    val chosen by model.presetChoice.collectAsState()
    val options by model.options.collectAsState()
    val saved by model.savedPresets.collectAsState()
    val active = matchingSaved(saved, options)
    var confirmReset by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<SavedPreset?>(null) }
    var renaming by remember { mutableStateOf<SavedPreset?>(null) }
    var all by remember { mutableStateOf(false) }
    Section("Preset") {
        Choice(PRESET_LABELS.keys.toList() + DAYNIGHT, if (active == null) chosen else "", { PRESET_LABELS[it] ?: "Day & night" }) { model.choosePreset(it) }
        if (saved.isNotEmpty()) {
            Caption("Custom")
            val shown = if (all) saved else saved.take(SAVED_ROW)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (p in shown) Box {
                    CleanChip(
                        selected = p == active, onClick = {}, label = { Text(p.name) },
                        modifier = Modifier.combinedClickable(onLongClickLabel = "Rename or delete", onLongClick = { menuFor = p }, onClick = { model.applyLook(p.keys) }),
                    )
                    DropdownMenu(menuFor == p, { menuFor = null }) {
                        DropdownMenuItem({ Text("Rename") }, { menuFor = null; renaming = p })
                        DropdownMenuItem({ Text("Delete") }, { menuFor = null; model.deleteSaved(p) })
                    }
                }
                if (saved.size > SAVED_ROW) CleanChip(selected = false, onClick = { all = !all }, label = { Text(if (all) "Show less" else "Show more") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            QuietButton("Save as new preset", { naming = true })
            QuietButton("Reset to preset", { confirmReset = true })
        }
    }
    if (confirmReset) CleanDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset the look?") },
        text = { Text("Goes back to ${PRESET_LABELS[chosen] ?: "Day & night"}'s own look and clears every mix, colour and Mono slider. The preset${if (chosen == DAYNIGHT) ", its day and night choices" else ""} and your saved presets stay. You can undo it afterwards.") },
        confirmButton = { TextButton({ confirmReset = false; model.resetLook() }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
    )
    if (naming) NameDialog("Save as new preset", "", "Save", { naming = false }) { model.saveLook(it) }
    renaming?.let { p -> NameDialog("Rename preset", p.name, "Rename", { renaming = null }, taken = saved.filter { it != p }.map { it.name }) { model.renameSaved(p, it) } }
}

/** A name field in a dialog; [onDone] gets the trimmed name once it is not blank and not in [taken]. */
@Composable
private fun NameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, taken: List<String> = emptyList(), onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val clean = name.trim()
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = clean in taken, supportingText = if (clean in taken) ({ Text("Another preset has this name") }) else null,
            )
        },
        confirmButton = { TextButton(enabled = clean.isNotEmpty() && clean !in taken, onClick = { onDismiss(); onDone(clean) }) { Text(action) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** A row that opens a menu of [options] (id to words) with [current] marked; the row says what it is and what is chosen. Disabled with one option or none. */
@Composable
private fun DropdownRow(
    label: String, current: String, options: List<Pair<String, String>>,
    /** Small squares beside an option (and beside the row, for the one chosen); none for an option not in it. */
    swatches: Map<String, List<Color>> = emptyMap(),
    /** Colours shown on the row in place of the chosen option's own, e.g. the colour the look has now. */
    rowSwatch: List<Color>? = null,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == current }?.second ?: current
    val enabled = options.size > 1
    Box {
        Row(
            Modifier.fillMaxWidth().minTouch()
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .semantics(mergeDescendants = true) { contentDescription = "$label: $shown" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                Text(label)
                Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (rowSwatch ?: swatches[current])?.let { Swatches(it) }
            if (enabled) Icon(CleanIcons.DropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            for ((id, words) in options) DropdownMenuItem(
                { Text(words) }, { open = false; onPick(id) }, Modifier.semantics { if (id == current) contentDescription = "$words, chosen" },
                trailingIcon = swatches[id]?.let { { Swatches(it) } },
            )
        }
    }
}

@Composable
private fun Swatches(colours: List<Color>) {
    Row { for (c in colours) Box(Modifier.padding(horizontal = Space.xxs).size(16.dp).background(c, RoundedCornerShape(4.dp))) }
}

/** The panel under the Day & night chip: which preset by day and by night, and what decides when it is day. */
@Composable
fun DayNightSection(model: AppModel) {
    val chosen by model.presetChoice.collectAsState()
    if (chosen != DAYNIGHT) return
    val o by model.options.collectAsState()
    val context = LocalContext.current
    // The built-in presets first, then the user's saved looks (`saved:<name>`), so a custom look can be the day or the night one.
    val saved = dayNightSaved(model.savedPresets.collectAsState().value)
    val presets = PRESET_LABELS.map { it.key to it.value } + saved.map { SAVED_REF + it.name to it.name }
    fun nameOf(id: String) = presets.firstOrNull { it.first == id }?.second ?: id
    val day = dayNightChoice(o["theme.day"], saved, "light")
    val night = dayNightChoice(o["theme.night"], saved, "dark")
    val rule = o["theme.switch"] ?: SWITCH_TIMES
    val dayFrom = parseClock(o["theme.dayFrom"]) ?: DEFAULT_DAY_FROM
    val nightFrom = parseClock(o["theme.nightFrom"]) ?: DEFAULT_NIGHT_FROM
    val latitude = o["theme.lat"]?.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: DEFAULT_LATITUDE
    fun askTime(key: String, minutes: Int) {
        android.app.TimePickerDialog(context, { _, hour, minute -> model.setOption(key, clockText(hour * 60 + minute)) }, minutes / 60, minutes % 60, true).blinking().show()
    }
    Section("Day & night") {
        DropdownRow("Day preset", day, presets) { model.setOption("theme.day", it) }
        DropdownRow("Night preset", night, presets) { model.setOption("theme.night", it) }
        Text("Switch", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val rules = mapOf(SWITCH_TIMES to "At set times", SWITCH_SUN to "Sunrise & sunset", SWITCH_PHONE to "With the phone")
        Choice(rules.keys.toList(), rule, { rules.getValue(it) }) { model.setOption("theme.switch", it) }
        val dayName = nameOf(day)
        val nightName = nameOf(night)
        when (rule) {
            SWITCH_TIMES -> {
                LabelValue("$dayName from", clockText(dayFrom), "$dayName from ${clockText(dayFrom)}, change") { askTime("theme.dayFrom", dayFrom) }
                LabelValue("$nightName from", clockText(nightFrom), "$nightName from ${clockText(nightFrom)}, change") { askTime("theme.nightFrom", nightFrom) }
                Hint("$dayName from ${clockText(dayFrom)}, $nightName from ${clockText(nightFrom)}.")
            }
            SWITCH_SUN -> {
                var text by remember { mutableStateOf(shortNumber(latitude)) }
                OutlinedTextField(
                    text, { text = it; it.trim().toDoubleOrNull()?.takeIf { v -> v in -90.0..90.0 }?.let { v -> model.setOption("theme.lat", shortNumber(v)) } },
                    label = { Text("Latitude, degrees north") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 60
                val (rise, set) = dayWindow(SWITCH_SUN, LocalDate.now(), latitude, offset, dayFrom, nightFrom)
                Hint("$dayName from sunrise (${clockText(rise)} today), $nightName from sunset (${clockText(set)}). Worked out on the phone from its time zone and this latitude; the app never asks where you are.")
            }
            else -> Hint("$dayName while the phone is in light mode, $nightName while it is in dark mode.")
        }
    }
}

/** One dropdown per part of the look. The options are the values the presets use, grouped by which presets share them. */
@Composable
fun MixSection(model: AppModel, onWheel: (String) -> Unit) {
    val o by model.options.collectAsState()
    val look by model.look.collectAsState()
    var advanced by remember { mutableStateOf(false) }
    val rest = Part.entries - BASIC_PARTS.toSet()
    Section("Mix and match") {
        Hint("Each part starts at the preset's own value. Change any one; the rest stay. Picking a preset resets them.")
        for (part in BASIC_PARTS + if (advanced) rest else emptyList()) {
            val choices = partChoices(part)
            val own = PRESETS.getValue(look.preset).getValue(part)
            val current = o["theme.mix.${part.key}"]?.takeIf { it in part.options } ?: own
            fun pickPart(id: String) = model.setOption("theme.mix.${part.key}", if (id == own) null else id)
            when (part) {
                // The accent and the colour scale: the presets' values, then the named colours and your own, which override the part.
                Part.ACCENT -> {
                    val override = o["accent"]
                    val chosen = when {
                        override == null -> "part:$current"
                        override.startsWith("#") -> "own"
                        else -> "accent:$override"
                    }
                    DropdownRow(
                        part.label, chosen,
                        choices.map { "part:${it.id}" to it.words() } + ACCENTS.keys.map { "accent:$it" to it } + ("own" to "Your own colour…"),
                        swatches = ACCENTS.mapKeys { "accent:${it.key}" }.mapValues { listOf(it.value.first) },
                        rowSwatch = listOf(if (look.dark) look.accent.first else look.accent.second),
                    ) { id ->
                        when {
                            id == "own" -> onWheel("accent")
                            id.startsWith("accent:") -> model.setOption("accent", id.removePrefix("accent:"))
                            else -> { model.setOption("accent", null); pickPart(id.removePrefix("part:")) }
                        }
                    }
                }
                Part.COLOUR_SCALE -> {
                    val override = o["palette"]
                    val chosen = when {
                        override == null -> "part:$current"
                        override.startsWith("#") -> "own"
                        else -> "palette:$override"
                    }
                    DropdownRow(
                        part.label, chosen,
                        choices.map { "part:${it.id}" to it.words() } + PALETTES.keys.map { "palette:$it" to it } + ("own" to "Your own colour…"),
                        swatches = PALETTES.mapKeys { "palette:${it.key}" },
                        rowSwatch = look.scale,
                    ) { id ->
                        when {
                            id == "own" -> onWheel("palette")
                            id.startsWith("palette:") -> model.setOption("palette", id.removePrefix("palette:"))
                            else -> { model.setOption("palette", null); pickPart(id.removePrefix("part:")) }
                        }
                    }
                    Hint("The calendar and the body figure.")
                }
                else -> DropdownRow(part.label, current, choices.map { it.id to it.words() }) { pickPart(it) }
            }
            // The ring on the launcher icon goes with the icon's tile.
            if (part == Part.ICON) {
                val iconAccent = o["theme.iconAccent"]
                DropdownRow(
                    "Icon accent", iconAccent ?: "",
                    listOf("" to "Preset's own") + ACCENTS.keys.map { it to it } + ("Ink" to "Ink"),
                    swatches = ACCENTS.mapValues { listOf(it.value.first) } + ("Ink" to listOf(INK)),
                ) { model.setOption("theme.iconAccent", it.ifEmpty { null }) }
            }
        }
        MonoSwitch(model, "theme.cbsafe", "Colour-blind safe", "Blue to orange instead of green to red.", false)
        QuietButton(if (advanced) "Hide advanced" else "Advanced: ${rest.size} more parts", { advanced = !advanced }, Modifier.fillMaxWidth())
    }
}

/** A labelled slider from 0 to 100 for the option [key], with [shown] beside the label. It follows the finger and writes the option as it moves. */
@Composable
private fun MonoSlider(model: AppModel, key: String, label: String, value: Int, shown: (Int) -> String) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val now = drag ?: value.toFloat()
    LabelValue(label, shown(now.toInt()))
    Slider(
        now, { drag = it; model.setOption(key, it.toInt().toString()) }, Modifier.fillMaxWidth().semantics { contentDescription = "$label, ${shown(now.toInt())}" },
        valueRange = 0f..100f, onValueChangeFinished = { drag = null },
    )
}

/** A row that is a switch for the on/off option [key]; [default] is what it is when nothing is stored. */
@Composable
private fun MonoSwitch(model: AppModel, key: String, title: String, about: String, default: Boolean) {
    val on = model.flag(key, default)
    Row(Modifier.toggleable(on, role = Role.Switch) { model.setOption(key, if (it) "on" else "off") }.minTouch(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Hint(about)
        }
        Switch(on, null)
    }
}

/** Mono's own settings, shown only while the paper background is on: the paper, the ink, the grain and the negative. */
@Composable
fun MonoSection(model: AppModel) {
    val look by model.look.collectAsState()
    if (!look.mono) return
    val o by model.options.collectAsState()
    val settings = monoSettingsOf(o)
    Section("Mono") {
        MonoSlider(model, "mono.paper", "Paper", settings.paper) { monoPaper(it).rgbHex() }
        MonoSlider(model, "mono.ink", "Ink", settings.ink) { monoInk(it).rgbHex() }
        Hint("Everything else is a mix of the two: quiet text is the ink at 60 %, tracks and raised surfaces the ink at 10 %, and the outlines are the ink.")
        MonoSlider(model, "mono.texture", "Paper texture", settings.texture) { "$it %" }
        MonoSwitch(model, "mono.negative", "Negative", "Every colour turned over: a dark look.", false)
        if (settings.negative) MonoSwitch(model, "mono.trueInvert", "True invert", "On, every colour is inverted, so hues flip (orange becomes blue). Off, only the lightness turns over and the hues stay.", true)
        Hint("These stay when you pick another preset.")
        val stored = MONO_KEYS.any { it in o }
        if (stored) QuietButton("Reset to Mono's own", { for (key in MONO_KEYS) model.setOption(key, null) }, Modifier.fillMaxWidth())
    }
}
