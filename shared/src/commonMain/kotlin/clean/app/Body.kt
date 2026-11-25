package clean.app

import androidx.compose.foundation.layout.ColumnScope
import clean.core.time.*
import androidx.compose.runtime.saveable.rememberSaveable
import clean.app.ui.theme.LocalLook
import clean.core.fatigueWord
import clean.core.Side
import clean.core.SideCredit
import clean.core.phrase
import clean.core.shortfall
import clean.core.spoken
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import kotlinx.datetime.LocalDate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import clean.app.ui.theme.CleanColors
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.SmallShape
import clean.app.ui.theme.GlassWindowShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import clean.app.platform.HitRegion
import clean.app.platform.LocalPlatform
import clean.core.STRONG_DATE_FORMAT
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import clean.app.data.MeasurementEntity
import clean.core.FATIGUE_FATIGUED
import clean.core.FATIGUE_RECOVERING
import clean.core.KG_PER_LB
import clean.core.STANDARD_MEASUREMENTS
import clean.app.data.MeasurementFileEntity
import clean.core.LoadType
import clean.core.Head
import clean.core.Muscle
import clean.core.RangeState
import clean.core.weeklySetState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import clean.core.STRENGTH_FLOOR
import clean.core.shortNumber
import kotlinx.datetime.LocalDateTime
import kotlin.math.sqrt

/** `Weight (kg)` -> `Weight` and `kg`; headers without a unit keep an empty one. */
fun splitHeader(header: String): Pair<String, String> {
    val open = header.lastIndexOf('(')
    return if (open > 0 && header.endsWith(")")) header.take(open).trim() to header.substring(open + 1, header.length - 1) else header to ""
}

/** Stored text for the screen. Body weight follows the profile's unit; everything else is shown as stored. */
fun shown(entry: MeasurementEntity, profileUnit: String): String {
    val value = entry.value.toDoubleOrNull() ?: return entry.value
    return if (entry.file == "weight.csv") showWeight(value, profileUnit) else shortNumber(value)
}

/** The Body page: the figure with every muscle in each of its views, reached from Progress. */
@Composable
fun BodyScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val unset = remember(workouts, settings) { exerciseSummaries(workouts).count { model.musclesOf(it.name).isEmpty && model.loadTypeOf(it.name) != LoadType.CARDIO } }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Body", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (unset > 0) {
                item {
                    PageSection("Muscles to set") {
                        Text("$unset of your exercises are not in the library and their names gave no clue, so they are missing from the figure.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MainButton("Set them", { model.open(Screen.ExerciseSetup) })
                    }
                }
            }
            // The muscle with most sets in the time frame (it moved here from Progress, 1.1.0).
            item {
                val sets by model.muscleSetCounts.collectAsState()
                sets.entries.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { (m, n) ->
                    Box(Modifier.padding(horizontal = Space.l)) { NavRow("Most trained muscle", "${m.label}, ${setsText(shortNumber(roundHalfUp(n * 10) / 10.0))}", { model.open(Screen.MuscleDetail(m)) }, "progress:stat:muscle") }
                }
            }
            item { MuscleCard(model) }
            item { Spacer(Modifier.height(Space.xxl)) }
        }
    }
}

/** Names a new kind of measurement and its unit; it gets a file of its own and goes into the export's zip with the rest. */
@Composable
fun NewMeasurementDialog(model: AppModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("cm") }
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("New measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Forearm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.addMeasurementKind(name.trim(), unit.trim()); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The measurements page: BMI, then a card for each measurement with its latest value, how far it moved since the entry before and its line, and a + in the bar
 * for a new kind. The ones of a Strong export are "Body", the ones the user made are "Your own"; a body one with no entry yet is a quiet row under "Not logged yet".
 */
@Composable
fun MeasurementsScreen(model: AppModel) {
    val files by model.files.collectAsState()
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val series by model.measurementSeries.collectAsState()
    val unit = profile?.unit ?: "kg"
    val latest = remember(measurements) { measurements.groupBy { it.file }.mapValues { it.value.first() } }
    var adding by rememberSaveable { mutableStateOf(false) }
    val standard = remember { STANDARD_MEASUREMENTS.map { it.first }.toSet() }
    val logged = files.filter { latest[it.name] != null }
    val body = logged.filter { it.name in standard }
    val own = files.filter { it.name !in standard }
    val notYet = files.filter { it.name in standard && latest[it.name] == null }
    var index = 0
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar("Measurements", model::back) { IconButton(onClick = { adding = true }) { Icon(CleanIcons.Add, "New measurement") } }
    }) { padding ->
    LazyColumn(Modifier.padding(padding).fillMaxSize()) {
        item {
            // Worked out from the latest weigh-in and the profile's height; not a file in the export.
            val kg = latest["weight.csv"]?.value?.toDoubleOrNull()
            val height = profile?.heightCm?.takeIf { it > 0 }
            val bands = bmiBands(LocalCleanColors.current)
            val value = if (kg != null && height != null) bmi(kg, height) else null
            val band = value?.let { bmiBandOf(it, bands) }
            Section("", Modifier.settleIn(0), hero = true, onClick = { model.open(Screen.Bmi) }, spacing = Space.m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Caption("BMI")
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            Text(if (value != null) shortNumber(roundHalfUp(value * 10) / 10.0) else NONE, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                            if (band != null) Text(band.second, Modifier.padding(bottom = Space.xs), style = MaterialTheme.typography.titleMedium, color = band.third)
                        }
                    }
                    Icon(CleanIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (value != null) BmiBandBar(value, bands, spoken = "${shortNumber(roundHalfUp(value * 10) / 10.0)}, ${band!!.second}")
                else Text(if (height == null) "Add your height in Settings and your weigh-ins give a BMI." else "Log a body weight and your BMI appears here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (latest.isEmpty()) item {
            EmptyState("Nothing measured yet", "Log your weight or a measurement and it appears here with its line.", "Log weight", { model.logWeight() }, "New measurement", { adding = true })
        }
        @Composable
        fun card(file: MeasurementFileEntity, at: Int) {
            val (label, fileUnit) = splitHeader(file.header)
            val last = latest[file.name]
            val isWeight = file.name == "weight.csv"
            val values = series[file.name].orEmpty()
            val shownUnit = if (isWeight) unit else fileUnit
            Section("", Modifier.settleIn(at), onClick = { model.open(Screen.MeasurementDetail(file.name)) }, card = true, spacing = Space.s) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    // What Profile's Body measurements card shows of this file travels here from there (it is only the card's words when this page grew out of it).
                    Column(Modifier.weight(1f)) {
                        MorphText(androidx.compose.ui.text.AnnotatedString(label), "m:label", onPage = true, style = MaterialTheme.typography.bodyLarge, id = file.name)
                        if (last != null) MorphText(androidx.compose.ui.text.AnnotatedString(prettyDay(last.date)), "m:date", onPage = true, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, id = file.name)
                        else Text("No entries yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (last == null) Text(NONE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Column(horizontalAlignment = Alignment.End) {
                        MorphText(androidx.compose.ui.text.AnnotatedString("${shown(last, unit)} $shownUnit".trim()), "m:value", onPage = true, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, id = file.name)
                        measurementChange(values, isWeight && unit == "lb", shownUnit)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (values.size >= 2) Sparkline(values.takeLast(24).map { it.second }, Modifier.padding(top = Space.xs), height = 32.dp)
            }
        }
        if (body.isNotEmpty()) {
            item { Section("Body") {} }
            for (file in body) { val at = ++index; item(key = file.id) { card(file, at) } }
        }
        if (own.isNotEmpty()) {
            item { Section("Your own") {} }
            for (file in own) { val at = ++index; item(key = file.id) { card(file, at) } }
        }
        if (notYet.isNotEmpty()) item {
            Section("Not logged yet") {
                for (file in notYet) LabelValue(splitHeader(file.header).first, NONE, onClick = { model.open(Screen.MeasurementDetail(file.name)) })
            }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
    }
    // Anything else worth tracking: it gets a file of its own and goes into the export's zip with the rest.
    if (adding) NewMeasurementDialog(model) { adding = false }
}

/** How far the newest entry of [values] (oldest first, as stored) moved from the one before: "+0.4 kg since 3 Nov", "−1 cm since 3 Nov", "no change"; null with one entry. */
internal fun measurementChange(values: List<Pair<LocalDate, Double>>, poundsShown: Boolean, unit: String): String? {
    if (values.size < 2) return null
    val (day, before) = values[values.size - 2]
    val delta = (values.last().second - before).let { if (poundsShown) it / KG_PER_LB else it }
    val moved = signed(delta, 2)
    return if (moved == "0") "no change" else "$moved ${unit} since ${prettyDay(day)}".replace("  ", " ")
}

/** The names of up to three muscles at [min] or more, the highest first, for the figure's spoken summary. */
private fun topMuscles(values: Map<Muscle, Double>, min: Double): String =
    values.entries.filter { it.value >= min }.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key.label }

/** The Fatigue view's colour for a level: red when fatigued, amber while recovering, green when ready. */
fun fatigueColour(level: Double, meaning: CleanColors) =
    if (level > FATIGUE_FATIGUED) meaning.fatigued else if (level >= FATIGUE_RECOVERING) meaning.recovering else meaning.ready

/** The Strength view's colour: gold at full; the calendar colours, strongest first, on the way down to the floor. */
fun strengthColour(retained: Double, meaning: CleanColors, palette: List<Color>) =
    if (retained >= 1.0) meaning.full else palette[((retained - STRENGTH_FLOOR) / (1 - STRENGTH_FLOOR) * 4).toInt().coerceIn(0, 3)]

/** What a [SideCredit] holds for one [side]. */
fun SideCredit.of(side: Side) = if (side == Side.LEFT) left else right

fun sideWord(side: Side) = if (side == Side.LEFT) "left" else "right"

/** A set count to one decimal, with no trailing zero. */
private fun oneDecimal(value: Double) = shortNumber(roundHalfUp(value * 10) / 10.0)

/** How far apart the sides are as a share of the larger, 0 when even; what the Sides view orders its rows by. */
private fun gapShare(c: SideCredit): Double = maxOf(c.left, c.right).let { if (it <= 0.0) 0.0 else kotlin.math.abs(c.left - c.right) / it }

private val MINI_VIEWS = listOf("Balance", "Fatigue", "Strength")

/**
 * Round 15 (8): how far the one breath of the muscles trained today has run (0 to 1, 0 at rest), started when the card is in view ([visible]) with
 * some of them, the first time on each day (kept as kv `breath.day:<profileId>`, so not again at the next start of the app that day). Nothing with
 * animations off or when none trained today.
 */
@Composable
private fun rememberBreath(model: AppModel, muscles: Set<Muscle>, visible: Boolean): () -> Float {
    val motion = flourishOn()
    val breath = remember { androidx.compose.animation.core.Animatable(0f) }
    val today = model.today.toString()
    LaunchedEffect(muscles.isNotEmpty(), visible, motion, today) {
        if (!motion || !visible || muscles.isEmpty() || model.breathDay() == today) return@LaunchedEffect
        model.markBreath(today)
        breath.snapTo(0f)
        breath.animateTo(1f, Motion.breath())
        breath.snapTo(0f)
    }
    return { breath.value }
}

/**
 * The figure small, for Progress, in the colours of the view the Body page is on (Sides reads as Balance). A sideways swipe on it goes to
 * the view before or after, which the Body page then opens on; it takes no tap, the card around it opens the page.
 */
@Composable
fun BodyMini(model: AppModel) {
    var mode by model.remembered("bodyMode", "Balance")
    val palette = LocalLook.current.scale
    val meaning = LocalCleanColors.current
    val figures by model.muscleFigures.collectAsState()
    val sets by model.muscleSetCounts.collectAsState()
    LaunchedEffect(Unit) { model.refreshMuscles() }
    val colours = when (mode) {
        "Fatigue" -> Muscle.entries.associateWith { fatigueColour(figures?.fatigue?.get(it) ?: 0.0, meaning) }
        "Strength" -> {
            val last = figures?.lastTrained ?: emptyMap()
            (figures?.strength ?: emptyMap()).filterKeys { it in last }.mapValues { (_, v) -> strengthColour(v, meaning, palette) }
        }
        else -> sets.maxOfOrNull { it.value }.let { max -> sets.mapValues { (_, v) -> shade(palette, if (max != null && max > 0) v / max else 0.0) } }
    }
    val view = if (mode in MINI_VIEWS) mode else "Balance"
    val at = MINI_VIEWS.indexOf(view)
    // Round 15 (8): the muscles trained today breathe once, the first time this card is in view each day.
    val today = model.today
    val trainedToday = remember(figures, today) { figures?.lastTrained?.filterValues { it.date == today }?.keys.orEmpty() }
    val spot = rememberSpot()
    val breath = rememberBreath(model, trainedToday, spot.visible && model.tab == Tab.Progress && model.stack.isEmpty())
    // A swipe tells the figure which way it went, so the colours slide in from that side; a change from anywhere else does not.
    val slide = remember { ViewSlide() }
    Column(
        Modifier.then(spot.modifier).slideRoom(slide, Space.m).swipeSteps(
            onPrevious = { MINI_VIEWS.getOrNull(at - 1)?.let { slide.dir = -1; mode = it } },
            onNext = { MINI_VIEWS.getOrNull(at + 1)?.let { slide.dir = 1; mode = it } },
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        BodyFigure(colours, interactive = false, slide = slide, description = "Body figure, $view view", breathing = trainedToday, breath = breath)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // The word travels into the selected chip of the Body page when the card opens it.
            MorphText(androidx.compose.ui.text.AnnotatedString(view), "view", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // One dot per view, the current one in the accent: there is more to the side.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                for (i in MINI_VIEWS.indices) Box(Modifier.size(6.dp).background(if (i == at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f), CircleShape))
            }
        }
    }
}

@Composable
fun MuscleCard(model: AppModel, limit: Int? = null, onAll: (() -> Unit)? = null) {
    // On Progress the rows are the first few of the view and "All muscles" opens the Body page.
    fun <T> List<T>.top() = if (limit != null) take(limit) else this
    var mode by model.remembered("bodyMode", "Balance")
    val days = model.muscleDays
    val open = { muscle: Muscle -> model.open(Screen.MuscleDetail(muscle)) }
    val palette = LocalLook.current.scale
    val meaning = LocalCleanColors.current
    val figures by model.muscleFigures.collectAsState()
    val headData by model.headFigures.collectAsState()
    val sideData by model.muscleSides.collectAsState()
    // Worked out off the main thread; fresh figures are asked for each time the card comes on screen.
    LaunchedEffect(Unit) { model.refreshMuscles() }

    // The Sides view is there only while some of the work in the time frame was logged left and right.
    val modes = listOf("Balance", "Fatigue", "Strength") + if (sideData.paired) listOf("Sides") else emptyList()
    val view = if (mode in modes) mode else "Balance"

    // The group whose heads are shown, in its row and on the figure; one at a time, and none in a new view.
    var openGroup by remember { mutableStateOf<Muscle?>(null) }
    // A group's row opens into its heads when it has any with sets; in Balance each is shaded against the group's largest,
    // in the other views the heads wear their group's colour.
    fun opens(group: Muscle, lines: List<HeadLine>): Opening? {
        if (lines.none { it.head != null }) return null
        return Opening(lines, openGroup == group, { openGroup = if (openGroup == group) null else group }, { head -> if (head != null) model.open(Screen.HeadDetail(head)) else open(group) })
    }
    fun opening(group: Muscle, groupColour: Color, balance: Boolean): Opening? =
        opens(group, headLines(headData, group) { if (balance) shade(palette, it) else groupColour })
    fun headColours(groupColour: Color, balance: Boolean): Map<Head, Color> =
        openGroup?.let { group -> headLines(headData, group) { if (balance) shade(palette, it) else groupColour }.mapNotNull { l -> l.head?.let { it to l.colour } }.toMap() } ?: emptyMap()
    // Fatigue and strength by side: the open group's heads wear the colour their group has on each side.
    fun headSideColours(sideColour: (Muscle, Side) -> Color?): Map<Pair<Head, Side>, Color> = openGroup?.let { group ->
        Head.entries.filter { it.group == group }.flatMap { head -> Side.entries.mapNotNull { side -> sideColour(group, side)?.let { (head to side) to it } } }.toMap()
    } ?: emptyMap()
    val openHead = { head: Head -> model.open(Screen.HeadDetail(head)) }
    // The hold card's extra line, in every view (Round 18).
    val lastTrainedLine = { m: Muscle -> figures?.lastTrained?.get(m)?.let { "Last trained ${prettyDay(it.toLocalDate())}" } ?: "Never trained" }
    val headTip = { head: Head -> headData.sets[head]?.let { setsText(shortNumber(roundHalfUp(it * 10) / 10.0)) } ?: "not trained" }

    // A sideways swipe on the figure goes to the view before or after.
    // It tells the figure which way it went, so the colours slide in from that side (a tap on a chip does not).
    val slide = remember { ViewSlide() }
    val swipe = Modifier.slideRoom(slide, Space.l).swipeSteps(
        onPrevious = { modes.getOrNull(modes.indexOf(view) - 1)?.let { slide.dir = -1; mode = it } },
        onNext = { modes.getOrNull(modes.indexOf(view) + 1)?.let { slide.dir = 1; mode = it } },
    )
    LaunchedEffect(view) { openGroup = null }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Segmented(modes, view, { it }, morph = "view") { mode = it }
        when (view) {
            "Balance" -> {
                val sets by model.muscleSetCounts.collectAsState()
                val max = sets.values.maxOrNull() ?: 0.0
                Box(swipe) {
                    BodyFigure(
                        sets.mapValues { (_, v) -> shade(palette, v / max) }, open,
                        headColours = headColours(palette.first(), true), open = openGroup, onHead = openHead, headTip = headTip, zoomable = true, slide = slide, peekMore = lastTrainedLine,
                        description = topMuscles(sets, 0.01).let { if (it.isEmpty()) "Body figure: nothing trained" else "Body figure: most worked $it" },
                    ) { m -> sets[m]?.let { setsText(shortNumber(roundHalfUp(it * 10) / 10.0)) } ?: "not trained" }
                }
                // The time frame sits under the figure, so the figure stays where it is from one view to the next.
                Segmented(listOf(7L, 30L, 90L, 0L), days, { if (it == 0L) "All" else if (it == 7L) "Week" else "${it}d" }) { model.muscleDays = it }
                Legend("Less", palette, "More", blended = true)
                val ranked = Muscle.entries.sortedByDescending { sets[it] ?: 0.0 }
                val ranges by model.setRanges.collectAsState()
                for (muscle in ranked.top()) {
                    val v = sets[muscle] ?: 0.0
                    // Each bar is the colour its muscle has on the figure.
                    val colour = if (max > 0) shade(palette, v / max) else palette.first()
                    MuscleRow(
                        muscle.label, if (max > 0) (v / max).toFloat() else 0f, if (v > 0) setsText(shortNumber(roundHalfUp(v * 10) / 10.0)) else "not trained", colour, opening(muscle, colour, true),
                        mark = if (days == 7L) ranges[muscle]?.let { weeklySetState(v, it) } else null,
                    ) { open(muscle) }
                }
            }
            "Fatigue" -> {
                val levels = figures?.fatigue ?: emptyMap()
                val bySide = figures?.fatigueSides ?: emptyMap()
                // Every muscle has a colour: red when fatigued, amber while recovering, green when ready.
                fun colour(v: Double) = fatigueColour(v, meaning)
                // A limb reads its own side's level; for ordinary work the two sides are the muscle's.
                fun levelOf(m: Muscle, side: Side) = bySide[m]?.of(side) ?: levels[m] ?: 0.0
                val limbs = Muscle.entries.flatMap { m -> Side.entries.map { (m to it) to colour(levelOf(m, it)) } }.toMap()
                Box(swipe) {
                    BodyFigure(
                        Muscle.entries.associateWith { colour(levels[it] ?: 0.0) }, open,
                        headColours = headColours(openGroup?.let { colour(levels[it] ?: 0.0) } ?: meaning.ready, false), open = openGroup, onHead = openHead, headTip = headTip, zoomable = true, slide = slide, peekMore = lastTrainedLine,
                        sideColours = limbs, headSideColours = headSideColours { g, s -> colour(levelOf(g, s)) },
                        sideTip = { m, s -> fatigueWord(levelOf(m, s)) },
                        description = topMuscles(levels, FATIGUE_RECOVERING).let { if (it.isEmpty()) "Body figure: every muscle ready" else "Body figure: most tired $it" },
                    ) { m ->
                        val v = levels[m] ?: 0.0
                        fatigueWord(v)
                    }
                }
                Legend("Fatigued", listOf(meaning.fatigued), "")
                Legend("Recovering", listOf(meaning.recovering), "")
                Legend("Ready", listOf(meaning.ready), "")
                Text("An estimate from the weight and reps of recent sets, against what has been a normal session for each muscle. It fades over about a day and a half.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val worst = { m: Muscle -> maxOf(levels[m] ?: 0.0, levelOf(m, Side.LEFT), levelOf(m, Side.RIGHT)) }
                for (muscle in Muscle.entries.filter { worst(it) >= 0.05 }.sortedByDescending(worst).top()) {
                    val v = levels[muscle] ?: 0.0
                    val l = fatigueWord(levelOf(muscle, Side.LEFT))
                    val r = fatigueWord(levelOf(muscle, Side.RIGHT))
                    // The row keeps the muscle's figure; a line under it says so only when the sides read differently.
                    MuscleRow(
                        muscle.label, v.toFloat(), fatigueWord(v), colour(v), opening(muscle, colour(v), false),
                        note = if (l != r) "L $l · R $r" else null,
                        spoken = if (l != r) "${muscle.label}, ${fatigueWord(v)}, left $l, right $r" else null,
                    ) { open(muscle) }
                }
            }
            "Strength" -> {
                val strength = figures?.strength ?: emptyMap()
                val bySide = figures?.strengthSides ?: emptyMap()
                val last = figures?.lastTrained ?: emptyMap()
                val now = figures?.at ?: LocalDateTime.now()
                fun colour(v: Double) = strengthColour(v, meaning, palette)
                fun levelOf(m: Muscle, side: Side) = bySide[m]?.of(side) ?: strength[m] ?: STRENGTH_FLOOR
                val limbs = Muscle.entries.filter { it in last }.flatMap { m -> Side.entries.map { (m to it) to colour(levelOf(m, it)) } }.toMap()
                Box(swipe) {
                    BodyFigure(
                        strength.filterKeys { it in last }.mapValues { (_, v) -> colour(v) }, open,
                        headColours = headColours(openGroup?.let { colour(strength[it] ?: STRENGTH_FLOOR) } ?: meaning.full, false), open = openGroup, onHead = openHead, headTip = headTip, zoomable = true, slide = slide, peekMore = lastTrainedLine,
                        sideColours = limbs, headSideColours = headSideColours { g, s -> if (g in last) colour(levelOf(g, s)) else null },
                        sideTip = { m, s -> "${roundHalfUp(levelOf(m, s) * 100)}% retained" },
                    ) { m -> "${roundHalfUp((strength[m] ?: STRENGTH_FLOOR) * 100)}% retained" }
                }
                Legend("Full", listOf(meaning.full) + palette.reversed(), "Faded")
                Text("Retained strength: full for two weeks after a muscle is trained, then fading until it is trained again. A model, not a measurement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val faded = { m: Muscle -> minOf(strength[m] ?: 0.0, levelOf(m, Side.LEFT), levelOf(m, Side.RIGHT)) }
                for (muscle in Muscle.entries.sortedBy(faded).filter { faded(it) < 1.0 }.top()) {
                    val v = strength[muscle] ?: STRENGTH_FLOOR
                    if (faded(muscle) >= 1.0) continue
                    val weeks = last[muscle]?.let { Duration.between(it, now).toDays() / 7 }
                    val l = roundHalfUp(levelOf(muscle, Side.LEFT) * 100)
                    val r = roundHalfUp(levelOf(muscle, Side.RIGHT) * 100)
                    MuscleRow(
                        muscle.label, v.toFloat(), weeks?.let { "$it weeks since training" } ?: "never trained", colour(v), opening(muscle, colour(v), false),
                        note = if (muscle in last && l != r) "L $l% · R $r%" else null,
                        spoken = if (muscle in last && l != r) "${muscle.label}, ${weeks ?: 0} weeks since training, left $l percent, right $r percent" else null,
                    ) { open(muscle) }
                }
            }
            else -> {
                // One shared scale over both sides of every muscle, so a difference between the limbs is visible.
                val credits = sideData.sets.filterValues { it.left + it.right > 0 }
                val max = credits.values.maxOfOrNull { maxOf(it.left, it.right) } ?: 0.0
                fun shadeOf(v: Double) = if (max > 0) shade(palette, v / max) else palette.first()
                val ranked = credits.entries.sortedWith(compareByDescending<Map.Entry<Muscle, SideCredit>> { gapShare(it.value) }.thenByDescending { it.value.left + it.value.right })
                val biggest = ranked.firstOrNull()?.takeIf { it.value.shortfall() != null }
                val limbs = credits.flatMap { (m, c) -> Side.entries.map { (m to it) to shadeOf(c.of(it)) } }.toMap()
                // The open group's heads, two bars each, against the group's largest head side.
                val headLines = openGroup?.let { headSideLines(headData, it) { v -> shade(palette, v) } } ?: emptyList()
                Box(swipe) {
                    BodyFigure(
                        credits.mapValues { (_, c) -> shadeOf((c.left + c.right) / 2) }, open,
                        headColours = headLines.mapNotNull { l -> l.head?.let { it to l.colour } }.toMap(), open = openGroup, onHead = openHead, headTip = headTip, zoomable = true, slide = slide, peekMore = lastTrainedLine,
                        sideColours = limbs,
                        headSideColours = headLines.flatMap { l -> l.sided?.let { s -> listOf((l.head!! to Side.LEFT) to s.leftColour, (l.head to Side.RIGHT) to s.rightColour) } ?: emptyList() }.toMap(),
                        sideTip = { m, s -> credits[m]?.let { setsText(oneDecimal(it.of(s))) } ?: "not trained" },
                        headSideTip = { h, s -> headData.bySide[h]?.let { setsText(oneDecimal(it.of(s))) } ?: "not trained" },
                        description = biggest?.let { (m, c) ->
                            val short = c.shortfall()!!
                            "Body figure, sides: largest gap ${sideWord(short.lagging)} ${m.label.lowercase()}, ${short.percent} percent less"
                        } ?: "Body figure, sides: no difference",
                    ) { m -> credits[m]?.let { "left ${oneDecimal(it.left)} · right ${oneDecimal(it.right)}" } ?: "not trained" }
                }
                Segmented(listOf(7L, 30L, 90L, 0L), days, { if (it == 0L) "All" else if (it == 7L) "Week" else "${it}d" }) { model.muscleDays = it }
                Legend("Less", palette, "More", blended = true)
                Text("Gap: how far the lagging side is below the larger one, in % of the larger side.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                biggest?.let { (m, c) ->
                    val short = c.shortfall()!!
                    Text(
                        "Biggest gap: ${sideWord(short.lagging)} ${m.label.lowercase()} −${short.percent} %",
                        Modifier.fillMaxWidth().minTouch().clickable { open(m) }.padding(vertical = Space.xs), color = MaterialTheme.colorScheme.primary,
                    )
                }
                for ((muscle, c) in ranked.top()) {
                    val short = c.shortfall()
                    MuscleRow(
                        muscle.label, 0f, "L ${oneDecimal(c.left)} · R ${oneDecimal(c.right)}", shadeOf(maxOf(c.left, c.right)),
                        opens(muscle, headSideLines(headData, muscle) { v -> shade(palette, v) }),
                        bars = SideBars((c.left / max).toFloat(), (c.right / max).toFloat(), shadeOf(c.left), shadeOf(c.right)),
                        note = "(${short.phrase()})",
                        spoken = "${muscle.label}, left ${setsText(oneDecimal(c.left))}, right ${setsText(oneDecimal(c.right))}, ${short.spoken()}",
                    ) { open(muscle) }
                }
            }
        }
        if (onAll != null) Text("All muscles ›", Modifier.fillMaxWidth().minTouch().clickable(onClick = onAll).padding(vertical = Space.m), color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The colour for a muscle at [share] of the most-worked muscle's sets: a smooth blend along the
 * palette. The square root spreads out the low end, so a muscle with one set and one with twenty
 * read differently even when the top muscle has two hundred.
 */
fun shade(palette: List<Color>, share: Double): Color {
    val x = sqrt(share.coerceIn(0.0, 1.0)).toFloat() * (palette.size - 1)
    val i = x.toInt().coerceAtMost(palette.size - 2)
    return lerp(palette[i], palette[i + 1], x - i)
}

@Composable
fun Legend(start: String, colors: List<Color>, end: String, blended: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("$start ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (blended) Box(Modifier.padding(horizontal = Space.xxs).size(width = 64.dp, height = 12.dp).background(Brush.horizontalGradient(colors), clean.app.ui.theme.LegendShape))
        else for (color in colors) Box(Modifier.padding(horizontal = Space.xxs).size(12.dp).background(color, clean.app.ui.theme.LegendShape))
        if (end.isNotEmpty()) Text(" $end", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One head under its group on the Body tab, or the group's other work when [head] is null; [sided] in the Sides view. */
private class HeadLine(val head: Head?, val sets: Double, val fraction: Float, val colour: Color, val sided: SidedLine? = null)

/** A head's two sides: each as a share of the group's largest head side, with the colour that side has on the figure. */
private class SidedLine(val credit: SideCredit, val left: Float, val right: Float, val leftColour: Color, val rightColour: Color)

/** A row's two thin bars in the Sides view: left and right as shares of the larger side of any muscle, each in its side's colour on the figure. */
private class SideBars(val left: Float, val right: Float, val leftColour: Color, val rightColour: Color)

/** What opens a group's row: its heads, whether they show, the chevron, and a tap on a line (null is "Other work"). */
private class Opening(val lines: List<HeadLine>, val expanded: Boolean, val toggle: () -> Unit, val open: (Head?) -> Unit)

/**
 * The heads of [group] with sets in the time frame, each coloured by [colourOf] its share of the
 * group's largest head, then the group's other work if there is any. Empty when no head has sets.
 */
private fun headLines(figures: AppModel.HeadFigures, group: Muscle, colourOf: (Double) -> Color): List<HeadLine> {
    val mine = Head.entries.filter { it.group == group }.mapNotNull { head -> figures.sets[head]?.takeIf { it > 0 }?.let { head to it } }
    val top = mine.maxOfOrNull { it.second } ?: return emptyList()
    val other = figures.other[group] ?: 0.0
    return mine.map { (head, v) -> HeadLine(head, v, (v / top).toFloat(), colourOf(v / top)) } +
        if (other > 0) listOf(HeadLine(null, other, (other / top).toFloat().coerceAtMost(1f), Color.Transparent)) else emptyList()
}

/**
 * The heads of [group] with sets on either side, for the Sides view: each side shaded by [colourOf] its share of the group's
 * largest head side (one scale for both), then the group's other work if there is any.
 */
private fun headSideLines(figures: AppModel.HeadFigures, group: Muscle, colourOf: (Double) -> Color): List<HeadLine> {
    val mine = Head.entries.filter { it.group == group }.mapNotNull { head -> figures.bySide[head]?.takeIf { it.left + it.right > 0 }?.let { head to it } }
    val top = mine.maxOfOrNull { maxOf(it.second.left, it.second.right) } ?: return emptyList()
    val other = figures.other[group] ?: 0.0
    return mine.map { (head, c) ->
        val l = c.left / top
        val r = c.right / top
        HeadLine(head, c.left + c.right, maxOf(l, r).toFloat(), colourOf((l + r) / 2), SidedLine(c, l.toFloat(), r.toFloat(), colourOf(l), colourOf(r)))
    } + if (other > 0) listOf(HeadLine(null, other, (other / top).toFloat().coerceAtMost(1f), Color.Transparent)) else emptyList()
}

@Composable
fun ThinBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(height), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
}

/**
 * A muscle's row: its name opens its page, the chevron (when it has heads) opens the row into them. With [bars] the one bar is
 * a pair, left over right; a [note] is a quiet line under the value; [spoken] is the row as one sentence for a screen reader.
 */
@Composable
private fun MuscleRow(
    label: String, fraction: Float, value: String, color: Color, opening: Opening? = null, mark: RangeState? = null,
    bars: SideBars? = null, note: String? = null, spoken: String? = null, onClick: () -> Unit,
) {
    val within = goalColor()
    Column {
        Row(
            Modifier.clickable(onClick = onClick).then(if (spoken != null) Modifier.semantics(mergeDescendants = true) { contentDescription = spoken } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.width(96.dp))
            if (bars != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                ThinBar(bars.left, bars.leftColour, 4.dp)
                ThinBar(bars.right, bars.rightColour, 4.dp)
            } else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(6.dp), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
            // In the week view a tick follows the value when the muscle is within its weekly target, and a quiet + when over it.
            Text(
                buildAnnotatedString {
                    append(value)
                    when (mark) {
                        RangeState.WITHIN -> withStyle(SpanStyle(color = within, fontWeight = FontWeight.Bold)) { append(" \u2713") }
                        RangeState.OVER -> append(" +")
                        else -> {}
                    }
                },
                Modifier.width(100.dp).padding(start = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            // The chevron has its own press; every row keeps the room so the bars line up.
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (opening != null) Box(Modifier.minTouch().clickable(onClick = opening.toggle), contentAlignment = Alignment.Center) {
                    Icon(if (opening.expanded) CleanIcons.ChevronUp else CleanIcons.ChevronDown, if (opening.expanded) "Hide parts" else "Show parts", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (note != null) Text(note, Modifier.fillMaxWidth().padding(end = 48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (opening != null) {
            val motion = LocalMotion.current
            AnimatedVisibility(opening.expanded, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
                Column {
                    for (line in opening.lines) {
                        val sided = line.sided
                        Row(
                            Modifier.clickable { opening.open(line.head) }.padding(start = Space.l).then(
                                if (sided != null && line.head != null) Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = "${line.head.label}, left ${setsText(oneDecimal(sided.credit.left))}, right ${setsText(oneDecimal(sided.credit.right))}"
                                } else Modifier,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(line.head?.label ?: "Other work", Modifier.width(80.dp).padding(vertical = Space.xs), style = MaterialTheme.typography.bodyMedium, color = if (line.head != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            // Each head's bar is the colour it has on the figure; other work has no part of the figure.
                            if (sided != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                ThinBar(sided.left, sided.leftColour, 3.dp)
                                ThinBar(sided.right, sided.rightColour, 3.dp)
                            } else if (line.head != null) LinearProgressIndicator(progress = { line.fraction }, modifier = Modifier.weight(1f).height(4.dp), color = line.colour, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                            else Spacer(Modifier.weight(1f))
                            Text(
                                sided?.let { "L ${oneDecimal(it.credit.left)} · R ${oneDecimal(it.credit.right)}" } ?: setsText(shortNumber(roundHalfUp(line.sets * 10) / 10.0)),
                                Modifier.width(100.dp).padding(start = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- The figure: MuscleMap's outlines (BodyPaths.kt), one or more per muscle ---

/** A part of the figure ready to draw and to hit-test, in the coordinates of its view. */
private class Outline(val muscle: Muscle?, val head: Head?, val side: Side?, val path: Path, val region: HitRegion)

/** What is pressed when it is one limb of a muscle or head: the [subject] (a Muscle or a Head) and the [side] of it. */
private class SidedTap(val subject: Any, val side: Side)

/** The key of what a tap or hold names, so a hold knows it is still on the same muscle, head or limb. */
private fun Any.peekKey(): Any = if (this is SidedTap) subject to side else this

/** The outline under a finger ([part]), what it names ([subject]: a Muscle, a Head or a [SidedTap]) and whether a tap on it shows a tip first ([shows]). */
private class BodyHit(val part: Outline, val subject: Any, val shows: Boolean)

/**
 * Keeps a popup of [size] inside the window with the page's side margin: [at] is a point in the anchor (the figure), [centered] puts the popup
 * above it with its middle on the point (a hold's card, below the point when there is no room above), otherwise [at] less ([dx], [dy]) is its corner (a tap's tip).
 */
class InsideWindow(val at: Offset, val centered: Boolean, val margin: Int, val dx: Float, val dy: Float, val above: Float, val below: Float) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): IntOffset {
        val point = Offset(anchorBounds.left + at.x, anchorBounds.top + at.y)
        val x: Float
        var y: Float
        if (centered) {
            x = point.x - popupContentSize.width / 2f
            y = point.y - popupContentSize.height - above
            if (y < margin) y = point.y + below
        } else { x = point.x - dx; y = point.y - dy }
        val maxX = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        val maxY = (windowSize.height - margin - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x.toInt().coerceIn(margin, maxX), y.toInt().coerceIn(margin, maxY))
    }
}

private fun outlines(parts: List<BodyPart>): List<Outline> {
    return parts.map { part ->
        val path = PathParser().parsePathString(part.path).toPath()
        Outline(part.muscle, part.head, part.side, path, Formats.platform.hitRegion(path, 1500, 1400))
    }
}

// Parsed once, the first time a figure is shown.
private val FRONT: List<Outline> by lazy { outlines(BODY_FRONT) }
private val BACK: List<Outline> by lazy { outlines(BODY_BACK) }

// The body of each view as one shape (every muscle's outline, not the heads): colour sliding sideways shows only inside it.

/** The colours a figure shows: each muscle's, and each limb's where it has its own. */
data class SlideLayer(val colours: Map<Muscle, Color>, val limbs: Map<Pair<Muscle, Side>, Color>)

/**
 * What a figure is told about a change of view. A swipe sets [dir] (1 when it went to the next view, so the colours move left, -1 to
 * the one before) just before the colours change; the figure takes it and clears it. [last] is the colours the figure showed last,
 * which the page's figure (a new one for each view) could not otherwise know. A change of view without a swipe leaves [dir] 0.
 */
class ViewSlide {
    var dir = 0
    var last: SlideLayer? = null
    /** The card (or section) the figure is in, in the window: the coloured figure enters from its edge and is drawn nowhere outside it. Set by [slideRoom]. */
    var room: Rect? = null
}

/** Notes the card the figure in this box sits in (the box and the card's padding [pad] around it) for [slide]: the colours slide in from that card's edge. */
fun Modifier.slideRoom(slide: ViewSlide, pad: Dp): Modifier = composed {
    val px = with(LocalDensity.current) { pad.toPx() }
    onGloballyPositioned { slide.room = it.boundsInRoot().inflate(px) }
}

/** One slide in progress: the colours that are leaving, the way they go, and how far along it is. */
private class SlideRun(val from: SlideLayer, val dir: Int) { val p = androidx.compose.animation.core.Animatable(0f) }

// Each view is 727 wide and 1280 tall, starting at y 95; the back view's own x starts at 718.
private const val VIEW_WIDTH = 727f
private const val VIEW_HEIGHT = 1280f
private const val VIEW_TOP = 95f
private const val BACK_LEFT = 718f

/** How far the figure is pinched in and where: a point p of the figure is drawn at p * scale + pan. */
private class Zoom {
    var scale by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var job: Job? = null

    /** The pan that keeps the zoomed figure over its box of [width] by [height]. */
    fun clamp(pan: Offset, scale: Float, width: Float, height: Float) =
        Offset(pan.x.coerceIn(width * (1 - scale), 0f), pan.y.coerceIn(height * (1 - scale), 0f))

    fun snap() { job?.cancel(); scale = 1f; pan = Offset.Zero }
}

private const val MAX_ZOOM = 3f

/** How far toward white a muscle trained today goes at the top of its breath. */
private const val BREATH_BRIGHTEN = 0.4f

/**
 * Front and back figures; muscles in [colors] are shaded, the rest stay neutral. A tap on a muscle's
 * outline shows a small card by the finger with its name and what [tip] says about it; tapping the
 * card calls [onTap]. Without a [tip] the tap goes straight to [onTap].
 *
 * With [open] a group, its heads are drawn over it in [headColours] (a head without a colour is not
 * drawn) and every other group steps back to 40%. Only then do heads take taps: [onHead], through
 * [headTip] in the same way as a muscle's.
 *
 * A part that lies on one side of the body is shaded from [sideColours] (or [headSideColours] for a head) where it has an
 * entry, so the two limbs of a muscle can read differently; otherwise from [colors] as before. A tap on such a limb names the
 * side ("Left biceps") and says what [sideTip] (or [headSideTip]) says about it.
 *
 * With [zoomable] two fingers pinch and pan the figure (up to 3x); while it is zoomed one finger pans it
 * and a double tap puts it back. With one finger at normal size the figure takes nothing but taps, so the
 * list, the pager and a sideways swipe on it work as before. [onZoom] is told the scale (for tests).
 */
@Composable
fun BodyFigure(
    colors: Map<Muscle, Color>, onTap: ((Muscle) -> Unit)? = null,
    headColours: Map<Head, Color> = emptyMap(), open: Muscle? = null, onHead: ((Head) -> Unit)? = null, headTip: ((Head) -> String)? = null,
    sideColours: Map<Pair<Muscle, Side>, Color> = emptyMap(), headSideColours: Map<Pair<Head, Side>, Color> = emptyMap(),
    sideTip: ((Muscle, Side) -> String)? = null, headSideTip: ((Head, Side) -> String)? = null,
    zoomable: Boolean = false, onZoom: ((Float) -> Unit)? = null,
    /** False for a figure that is only a picture (Progress's small one): it takes no tap, so the card around it gets them. */
    interactive: Boolean = true,
    /** What a screen reader says for the figure; the muscles themselves are reached through the rows beside it. */
    description: String = "Body figure",
    /** Set by a swipe between views: when the colours change after one, the old ones slide out and the new ones in from the side, the grey body staying still. */
    slide: ViewSlide? = null,
    /** Muscles that brighten and settle back as [breath] (0 to 1, read while drawing) runs from 0 to 1: the ones trained today, once. */
    breathing: Set<Muscle> = emptySet(), breath: () -> Float = { 0f },
    /** Round 18: more lines for the hold card of a muscle (or of a head's group), such as when it was last trained; null for none. */
    peekMore: ((Muscle) -> String?)? = null,
    tip: ((Muscle) -> String)? = null,
) {
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    val inert = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val tap by rememberUpdatedState(onTap)
    val describe by rememberUpdatedState(tip)
    val tapHead by rememberUpdatedState(onHead)
    val describeHead by rememberUpdatedState(headTip)
    val opened by rememberUpdatedState(open)
    val headsShown by rememberUpdatedState(headColours)
    val sideShown by rememberUpdatedState(sideColours)
    val headSideShown by rememberUpdatedState(headSideColours)
    val describeSide by rememberUpdatedState(sideTip)
    val describeHeadSide by rememberUpdatedState(headSideTip)
    // What is pressed is a Muscle or a Head, and where.
    var shown by remember { mutableStateOf<Pair<Any, Offset>?>(null) }
    // Round 18: what a hold on the figure shows (the same kinds of thing, and the finger's place), until the finger lifts.
    var peeked by remember { mutableStateOf<Pair<Any, Offset>?>(null) }
    val more by rememberUpdatedState(peekMore)
    val tick = rememberTick()
    // The colours ease over when something opens or changes; without animations they snap.
    val motion = LocalMotion.current
    val spec = if (motion) Motion.short<Color>() else snap()
    val heads = Head.entries.associateWith { animateColorAsState(headColours[it] ?: Color.Transparent, spec, label = "head").value }
    // A swipe slides the colours in from the side: one run for each change of colours, started in composition so its first frame is
    // already the old colours (no frame of the new ones first). Without a swipe, or without animations, the colours change as they did.
    val layer = SlideLayer(colors, sideColours)
    val travel = remember(layer) {
        val before = slide?.last
        val way = slide?.dir ?: 0
        if (slide != null) { slide.last = layer; slide.dir = 0 }
        if (motion && way != 0 && before != null && before != layer) SlideRun(before, way) else null
    }
    LaunchedEffect(travel) { travel?.p?.animateTo(1f, Motion.viewSlide()) }
    // After a change of colours that is not a swipe (a workout was done, the figures refreshed), each muscle eases from its old colour to the new one (Roll.kt has the same pattern for numbers).
    var settledLayer by remember { mutableStateOf(layer) }
    var easingTo by remember { mutableStateOf<SlideLayer?>(null) }
    val ease = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(layer, motion) {
        if (layer == settledLayer) return@LaunchedEffect
        if (!motion || travel != null) { settledLayer = layer; return@LaunchedEffect }
        easingTo = layer
        ease.snapTo(0f)
        ease.animateTo(1f, Motion.short())
        settledLayer = layer
    }
    val easeFrom = if (layer != settledLayer && travel == null && motion) settledLayer else null
    val easeAt = if (easeFrom != null && easingTo != layer) 0f else ease.value
    val dim by animateFloatAsState(if (open != null) 0.4f else 1f, if (motion) Motion.short() else snap(), label = "dim")
    // Gone with the figure, so it starts at normal size again when the view changes or the page is left.
    val zoom = remember { Zoom() }
    // Where the figure is in the window, for the colours that slide in from the card's edge.
    val origin = remember { FloatArray(2) }
    val scope = rememberCoroutineScope()
    val zoomed = zoom.scale > 1f
    if (onZoom != null) LaunchedEffect(zoom.scale) { onZoom(zoom.scale) }
    fun unzoom() {
        if (!motion) { zoom.snap(); return }
        zoom.job = scope.launch {
            val from = zoom.scale
            val pan = zoom.pan
            animate(1f, 0f, animationSpec = Motion.short()) { f, _ -> zoom.scale = 1 + (from - 1) * f; zoom.pan = pan * f }
        }
    }
    // Back through the zoom to the figure as drawn at normal size; the outline under [pos] and what a tap or hold names (a limb by its side).
    fun hitAt(pos: Offset, width: Int): BodyHit? {
        val at = (pos - zoom.pan) / zoom.scale
        val scale = width / (2 * VIEW_WIDTH)
        val back = at.x / scale >= VIEW_WIDTH
        val x = (at.x / scale - if (back) VIEW_WIDTH - BACK_LEFT else 0f).toInt()
        val y = (at.y / scale + VIEW_TOP).toInt()
        // Later outlines are drawn on top, so the last match is the one under the finger. A head
        // is under it only when its group is open and it is drawn.
        val part = (if (back) BACK else FRONT).lastOrNull {
            it.muscle != null && it.region.contains(x, y) && (it.head == null || (it.muscle == opened && it.head in headsShown))
        } ?: return null
        val target: Any = part.head ?: part.muscle!!
        // A limb that has its own colour and tip is named by its side.
        val limb = part.side?.takeIf { s ->
            if (part.head != null) describeHeadSide != null && (part.head to s) in headSideShown else describeSide != null && (part.muscle!! to s) in sideShown
        }
        val shows = limb != null || (if (part.head != null) describeHead != null else describe != null)
        return BodyHit(part, if (limb != null) SidedTap(target, limb) else target, shows)
    }
    var canvasWidth by remember { mutableStateOf(0) }
    Box {
        // A double tap would delay every single tap, so it is listened for only while there is something to undo.
        // Only the live figure glides between pages; a small copy sits inside a card, which opens into its page as a whole.
        // The same figure on a card and on the page that card opens: it moves and grows between the two, as one thing.
        Canvas(Modifier.morphs("figure", same = true).onGloballyPositioned { val at = it.positionInRoot(); origin[0] = at.x; origin[1] = at.y }.semantics { contentDescription = description; role = Role.Image }.fillMaxWidth().aspectRatio(2 * VIEW_WIDTH / VIEW_HEIGHT).onSizeChanged { canvasWidth = it.width }.then(
            // Round 18: holding on a muscle shows its card above the finger, sliding on updates it, lifting dismisses; the held touch is not a tap.
            if (!interactive) Modifier else Modifier.holdAndSlide(
                key = zoomed,
                onHold = { at -> val hit = hitAt(at, canvasWidth); if (hit != null) { tick(); peeked = hit.subject to at } },
                onMove = { at -> val hit = hitAt(at, canvasWidth); if (hit != null) { if (hit.subject.peekKey() != peeked?.first?.peekKey()) tick(); peeked = hit.subject to at } else peeked = null },
                onEnd = { peeked = null },
            ),
        ).pointerInput(zoomed, interactive) {
            if (!interactive) return@pointerInput
            detectTapGestures(onDoubleTap = if (zoomed) { _ -> unzoom() } else null) { tapped ->
                val hit = hitAt(tapped, size.width)
                if (hit == null) shown = null
                else if (hit.shows) shown = hit.subject to tapped
                else {
                    zoom.snap()
                    if (hit.part.head != null) tapHead?.invoke(hit.part.head) else tap?.invoke(hit.part.muscle!!)
                }
            }
        }.then(if (!zoomable) Modifier else Modifier.pointerInput(Unit) {
            // Read on the initial pass, ahead of the list this figure scrolls in and of the tap detector, so a second
            // finger is never lost to a scroll the first one began. Nothing is taken while one finger is down at
            // normal size; from a second finger on, everything is, until all are up.
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pinching = false
                var panning = false
                var drift = Offset.Zero
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val fingers = event.changes.filter { it.pressed }
                    if (fingers.size > 1 && !pinching) { pinching = true; zoom.job?.cancel() }
                    if (pinching) {
                        // Only fingers that were already down say how the hand has moved.
                        val moved = fingers.filter { it.previousPressed }
                        if (moved.size > 1) {
                            val before = moved.fold(Offset.Zero) { a, c -> a + c.previousPosition } / moved.size.toFloat()
                            val now = moved.fold(Offset.Zero) { a, c -> a + c.position } / moved.size.toFloat()
                            val spreadBefore = moved.sumOf { hypot(it.previousPosition.x - before.x, it.previousPosition.y - before.y).toDouble() }.toFloat()
                            val spreadNow = moved.sumOf { hypot(it.position.x - now.x, it.position.y - now.y).toDouble() }.toFloat()
                            val to = if (spreadBefore > 0f) (zoom.scale * spreadNow / spreadBefore).coerceIn(1f, MAX_ZOOM) else zoom.scale
                            // The point under the fingers stays under them.
                            val pan = now - (before - zoom.pan) * (to / zoom.scale)
                            zoom.scale = to
                            zoom.pan = zoom.clamp(pan, to, size.width.toFloat(), size.height.toFloat())
                        }
                        event.changes.forEach { it.consume() }
                    } else if (zoom.scale > 1f && fingers.size == 1) {
                        val change = fingers.first()
                        drift += change.positionChange()
                        if (drift.getDistance() > slop) panning = true
                        if (panning) {
                            zoom.pan = zoom.clamp(zoom.pan + change.positionChange(), zoom.scale, size.width.toFloat(), size.height.toFloat())
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        })) {
            val scale = size.width / (2 * VIEW_WIDTH)
            val run = travel
            val t = run?.p?.value ?: 1f
            val sliding = run != null && t < 1f
            for ((left, parts) in listOf(0f to FRONT, VIEW_WIDTH - BACK_LEFT to BACK)) {
                withTransform({
                    translate(zoom.pan.x, zoom.pan.y)
                    scale(zoom.scale, zoom.scale, Offset.Zero)
                    scale(scale, scale, Offset.Zero)
                    translate(left, -VIEW_TOP)
                }) {
                    for (part in parts) {
                        if (part.head != null) {
                            val colour = heads.getValue(part.head)
                            if (part.muscle == open && colour.alpha > 0f) {
                                // A head's colour on its own side fades in with the head's.
                                val own = part.side?.let { headSideShown[part.head!! to it] }
                                drawPath(part.path, if (own != null) own.copy(alpha = own.alpha * colour.alpha) else colour)
                            }
                        } else {
                            val own = part.muscle?.let { m -> part.side?.let { sideShown[m to it] } }
                            // While the colours slide, every muscle is the grey of an untrained one and the colours are drawn over it below.
                            val now = part.muscle?.let { if (sliding) neutral else own ?: colors[it] ?: neutral } ?: inert
                            val fill = if (easeFrom != null && part.muscle != null && !sliding) androidx.compose.ui.graphics.lerp(part.side?.let { easeFrom.limbs[part.muscle!! to it] } ?: easeFrom.colours[part.muscle!!] ?: neutral, now, easeAt) else now
                            val lit = if (!sliding && part.muscle != null && part.muscle in breathing) breath() else 0f
                            val bright = if (lit > 0f) androidx.compose.ui.graphics.lerp(fill, Color.White, BREATH_BRIGHTEN * kotlin.math.sin(lit * 3.1415927f)) else fill
                            drawPath(part.path, if (open != null && part.muscle != null && part.muscle != open) bright.copy(alpha = bright.alpha * dim) else bright)
                        }
                    }
                }
            }
            if (run != null && sliding) {
                // The coloured figure comes in from the side of the card it is in and slides over the grey body to land on it, drawn above everything in the
                // card, not clipped to the body; the leaving colours go out the other way over the card. Nothing is drawn outside the card.
                val card = slide?.room?.let { Rect(it.left - origin[0], it.top - origin[1], it.right - origin[0], it.bottom - origin[1]) } ?: Rect(0f, 0f, size.width, size.height)
                val toRight = card.right
                val toLeft = card.left - size.width
                clipRect(card.left, card.top, card.right, card.bottom) {
                    for ((shown, shift) in listOf(run.from to (if (run.dir > 0) toLeft else toRight) * t, layer to (if (run.dir > 0) toRight else toLeft) * (1f - t))) {
                        translate(left = shift) {
                            for ((left, parts) in listOf(0f to FRONT, VIEW_WIDTH - BACK_LEFT to BACK)) {
                                withTransform({
                                    translate(zoom.pan.x, zoom.pan.y)
                                    scale(zoom.scale, zoom.scale, Offset.Zero)
                                    scale(scale, scale, Offset.Zero)
                                    translate(left, -VIEW_TOP)
                                }) {
                                    for (part in parts) {
                                        val m = part.muscle
                                        if (part.head != null || m == null) continue
                                        val c = part.side?.let { shown.limbs[m to it] } ?: shown.colours[m]
                                        if (c != null) drawPath(part.path, c)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        shown?.let { (pressed, at) ->
            val limb = pressed as? SidedTap
            val target = limb?.subject ?: pressed
            val d = androidx.compose.ui.platform.LocalDensity.current
            val place = remember(at, d) { with(d) { InsideWindow(at, false, Space.l.roundToPx(), 56.dp.toPx(), 84.dp.toPx(), 0f, 0f) } }
            Popup(popupPositionProvider = place, onDismissRequest = { shown = null }, properties = PopupProperties(focusable = true)) {
                Surface(
                    shape = SmallShape, color = Color.Transparent,
                    modifier = Modifier.glass(SmallShape, haze = null, lift = 8.dp).clickable { shown = null; zoom.snap(); if (target is Head) tapHead?.invoke(target) else tap?.invoke(target as Muscle) },
                ) {
                    Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
                        val name = if (target is Head) target.label else (target as Muscle).label
                        Text(if (limb != null) sideWord(limb.side).replaceFirstChar { it.uppercase() } + " " + name.lowercase() else name, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                limb != null && target is Head -> describeHeadSide?.invoke(target, limb.side)
                                limb != null -> describeSide?.invoke(target as Muscle, limb.side)
                                target is Head -> describeHead?.invoke(target)
                                else -> describe?.invoke(target as Muscle)
                            }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Open ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        // The hold card: a window of its own (it can float outside the figure's card), so the opaque form of the glass; no actions, so no focus and no taps.
        peeked?.let { (pressed, at) ->
            val limb = pressed as? SidedTap
            val target = limb?.subject ?: pressed
            val d = androidx.compose.ui.platform.LocalDensity.current
            val place = remember(at, d) { with(d) { InsideWindow(at, true, Space.l.roundToPx(), 0f, 0f, 28.dp.toPx(), 36.dp.toPx()) } }
            Popup(popupPositionProvider = place, properties = PopupProperties(focusable = false)) {
                Column(Modifier.width(DAY_CARD_WIDTH).testTag("muscle peek").glass(GlassWindowShape, haze = null, lift = 8.dp).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    val name = if (target is Head) target.label else (target as Muscle).label
                    Text(if (limb != null) sideWord(limb.side).replaceFirstChar { it.uppercase() } + " " + name.lowercase() else name, fontWeight = FontWeight.Bold)
                    val line = when {
                        limb != null && target is Head -> describeHeadSide?.invoke(target, limb.side)
                        limb != null -> describeSide?.invoke(target as Muscle, limb.side)
                        target is Head -> describeHead?.invoke(target)
                        else -> describe?.invoke(target as Muscle)
                    }
                    val group = if (target is Head) target.group else target as Muscle
                    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                    if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = quiet)
                    more?.invoke(group)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = quiet) }
                }
            }
        }
    }
}

/** The few exercises nothing could be worked out for: not in the library, and no clue in the name. Each opens its page to set by hand. */
@Composable
fun ExerciseSetupScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val unset = remember(workouts, settings) {
        exerciseSummaries(workouts).filter { model.musclesOf(it.name).isEmpty && model.loadTypeOf(it.name) != LoadType.CARDIO }
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Muscles to set", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    if (unset.isEmpty()) "Every exercise has its muscles." else "Open an exercise and press Edit to choose its muscles.",
                    Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(unset, key = { it.name }) { exercise ->
                Column(Modifier.fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(exercise.name)) }.padding(horizontal = Space.l, vertical = Space.m)) {
                    Text(exercise.name)
                    Text(plural(exercise.sessions, "session"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

private val SINCE_DAY = LocalePattern("d MMM")

@Composable
fun MeasurementDetailScreen(model: AppModel, fileName: String) {
    val files by model.files.collectAsState()
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val workouts by model.workouts.collectAsState()
    val file = files.firstOrNull { it.name == fileName }
    val (label, fileUnit) = splitHeader(file?.header ?: fileName)
    val isWeight = fileName == "weight.csv"
    val unit = if (isWeight) profile?.unit ?: "kg" else fileUnit
    val weightGoal = weightGoalOf(model)
    var text by rememberSaveable { mutableStateOf("") }
    // When the new entry is dated; null is now, whenever Add is pressed.
    var at by rememberSaveable { mutableStateOf<LocalDateTime?>(null) }
    var editing by remember { mutableStateOf<MeasurementEntity?>(null) }
    val scope = rememberCoroutineScope()
    val platform = LocalPlatform.current
    // Body weight is logged on the ruler sheet; a page opened to log one has it open at once.
    var logging by remember { mutableStateOf(isWeight && model.logWeightNow) }
    LaunchedEffect(Unit) { model.logWeightNow = false }
    // The last workout of each day, for the dates that lead to one.
    val byDay = remember(workouts) { workouts.associateBy { it.day } }
    val latest = measurements.firstOrNull { it.file == fileName }
    // Oldest first for the line; body weight in the profile's unit.
    val series by model.measurementSeries.collectAsState()
    val stored = series[fileName] ?: remember(measurements, fileName) { measurementSeriesOf(measurements, fileName) }
    val points = remember(stored, isWeight, unit) { if (isWeight && unit == "lb") stored.map { it.first to it.second / KG_PER_LB } else stored }
    val entries = remember(measurements, fileName) { measurements.filter { it.file == fileName } }
    // How far the newest moved: from the first entry of the last 30 days, or with only one in that time, from the one before it.
    val since = remember(points) {
        val recent = points.filter { it.first >= LocalDate.now().minusDays(30) }
        (if (recent.size >= 2) recent.first() else points.getOrNull(points.size - 2))?.let { it.first to points.last().second - it.second }
    }
    // Where the "+" is, read when its sheet opens (not state: only the sheet needs it).
    val origin = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    var adding by remember { mutableStateOf(false) }
    if (logging) WeightSheet(model, origin = origin[0]) { logging = false }
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(label, model::back) {
            IconButton(
                onClick = { if (isWeight) logging = true else adding = true },
                Modifier.onGloballyPositioned { origin[0] = it.boundsInWindow() },
            ) { Icon(CleanIcons.Add, if (isWeight) "Log weight" else "Add entry") }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (latest != null) item {
                // The newest value large with how far it moved, then the goal as one quiet line: the number a card showing it travels to.
                Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.s, bottom = Space.m)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        MorphText(
                            androidx.compose.ui.text.AnnotatedString(shown(latest, profile?.unit ?: "kg")), "number", onPage = true,
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                        if (unit.isNotEmpty()) Text(unit, Modifier.padding(bottom = Space.xs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (since != null) {
                        val moved = shortNumber(since.second)
                        Text(
                            if (moved == "0" || moved == "-0") "no change since ${since.first.format(SINCE_DAY)}" else "${if (since.second > 0) "+" else ""}$moved since ${since.first.format(SINCE_DAY)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isWeight) WeightGoalSection(model, unit)
                }
            }
            item {
                if (points.size >= 2) Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    // The chart covers the time frame chosen; the list under it is always every entry.
                    val selector = model.measurementRangeSelector()
                    val range = selector.days
                    val shown = if (range == 0L) points else points.filter { it.first >= LocalDate.now().minusDays(range) }
                    if (shown.size < 2) Text("Not enough entries in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        val goalShown = if (isWeight) weightGoal?.let { if (unit == "lb") it.targetKg / KG_PER_LB else it.targetKg } else null
                        // The axis is padded, and never narrower than a few percent of the value, so a change of 0.4 kg does not read as a cliff (round 23).
                        LineChart(
                            shown, unit, range = paddedRange(shown.map { it.second } + listOfNotNull(goalShown)), goal = goalShown,
                            // Each file has its own key: the weight keeps the one Home and Profile give it.
                            morph = measurementMorphKey(fileName), selector = selector,
                        )
                    }
                }
                else if (isWeight) EmptyPreview(
                    "Log ${2 - points.size} more ${if (points.isEmpty()) "weigh-ins" else "weigh-in"} to see your trend.", "Log weight", { model.logWeight() },
                    Modifier.padding(horizontal = Space.l),
                ) { PreviewSamples.Line() }
            }
            if (points.size >= 2) item {
                // The lowest, highest and average of every entry, each with the day it was (the lowest and highest).
                val low = points.minBy { it.second }
                val high = points.maxBy { it.second }
                val average = points.map { it.second }.average()
                val say = { v: Double -> shortNumber(roundHalfUp(v * 10) / 10.0) }
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Tile("Lowest", Modifier.weight(1f)) { TileNumber(say(low.second)); TileLine("$unit · ${prettyDay(low.first)}".trim(' ', '·')) }
                    Tile("Highest", Modifier.weight(1f)) { TileNumber(say(high.second)); TileLine("$unit · ${prettyDay(high.first)}".trim(' ', '·')) }
                    Tile("Average", Modifier.weight(1f)) { TileNumber(say(average)); TileLine("${points.size} entries") }
                }
            }
            items(entries, key = { it.id }) { entry ->
                // Round 19: holding the row shows the change since the entry before it and, for body weight with a goal, the distance to the goal.
                val newer = entry.value.toDoubleOrNull()
                val older = entries.getOrNull(entries.indexOf(entry) + 1)
                val peek: @Composable ColumnScope.() -> Unit = {
                    val profileUnit = profile?.unit ?: "kg"
                    fun amount(v: Double) = if (isWeight) showWeight(v, profileUnit) else shortNumber(v)
                    PeekHead(prettyDate(entry.date))
                    PeekLine("${shown(entry, profileUnit)} $unit".trim(), bold = true)
                    val before = older?.value?.toDoubleOrNull()
                    if (newer != null && older != null && before != null) {
                        val moved = amount(newer - before)
                        PeekLine((if (moved == "0" || moved == "-0") "No change" else (if (newer > before) "+" else "") + moved + (if (unit.isEmpty()) "" else " $unit")) + " since ${prettyDay(older.date)}")
                    } else PeekLine("First entry")
                    val goal = if (isWeight) weightGoal else null
                    if (goal != null && newer != null) PeekLine(if (goal.reached(newer)) "Goal reached" else "${amount(kotlin.math.abs(newer - goal.targetKg))} $unit to goal")
                }
                HoldPeekBox("entry peek", if (newer == null) null else peek, Modifier.fillMaxWidth()) {
                // The row opens the entry to change its value or date; its date, when a workout was done that day, opens the workout.
                Row(Modifier.fillMaxWidth().clickable { editing = entry }.padding(start = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    // A date with a workout on it opens that workout.
                    val workout = byDay[entry.date.take(10)]
                    // The day in the short form, the time after it dimmed.
                    Text(
                        entryDay(entry.date, LocalPlatform.current.use24HourClock), Modifier.weight(1f).clickable(enabled = workout != null) { workout?.let { model.open(Screen.WorkoutDetail(it.date)) } },
                    )
                    val value = "${shown(entry, profile?.unit ?: "kg")} $unit".trim()
                    // The latest entry is the number a card showing the latest weigh-in travels to.
                    Text(value)
                    IconButton(onClick = { model.deleteMeasurement(entry) }) { Icon(CleanIcons.Delete, "Delete entry") }
                }
                }
                HorizontalDivider()
            }
        }
    }
    if (adding) Sheet({ adding = false }, "New entry", origin[0]) {
        Column(Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            OutlinedTextField(
                text, { text = it },
                label = { Text("Value${if (unit.isEmpty()) "" else " ($unit)"}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            // Backdated entries: the phone's own date and time pickers.
            LabelValue("Date", at?.let { prettyDate(it.format(STRONG_DATE_FORMAT)) } ?: "Now") { platform.pickDateTime(at ?: LocalDateTime.now()) { at = it } }
            MainButton("Add", {
                val value = parseNumber(text)
                if (value == null) model.message = "Enter a number"
                else scope.launch {
                    if (model.addMeasurement(fileName, value, (at ?: LocalDateTime.now()).format(STRONG_DATE_FORMAT))) {
                        model.buzz(Haptic.Confirm)
                        text = ""
                        at = null
                        adding = false
                    }
                }
            }, Modifier.fillMaxWidth())
        }
    }
    editing?.let { entry ->
        // What the box shows for the stored value; left as it is, the stored text is kept exactly.
        val original = shown(entry, profile?.unit ?: "kg")
        var typed by remember(entry) { mutableStateOf(original) }
        var moved by remember(entry) { mutableStateOf<LocalDateTime?>(parseDate(entry.date)) }
        val value = parseNumber(typed)
        CleanDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    OutlinedTextField(
                        typed, { typed = it }, label = { Text(if (unit.isEmpty()) label else "$label ($unit)") }, singleLine = true, isError = value == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                    LabelValue("Date", prettyDate(moved?.format(STRONG_DATE_FORMAT) ?: entry.date)) { platform.pickDateTime(moved ?: LocalDateTime.now()) { moved = it } }
                }
            },
            confirmButton = {
                TextButton(enabled = value != null, onClick = {
                    scope.launch {
                        val changed = if (typed == original) null else value?.let { if (isWeight && unit == "lb") it * KG_PER_LB else it }
                        if (model.editMeasurement(entry, moved?.format(STRONG_DATE_FORMAT) ?: entry.date, changed)) editing = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    DangerButton("Delete", { model.deleteMeasurement(entry); editing = null })
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
