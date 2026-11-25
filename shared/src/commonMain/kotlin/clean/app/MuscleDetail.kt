package clean.app

import clean.core.time.*
import clean.app.ui.theme.LocalLook
import clean.core.fatigueWord
import clean.core.Side
import clean.core.SideCredit
import clean.core.headSideWeeks
import clean.core.muscleSideWeeks
import androidx.compose.ui.graphics.Color
import clean.core.shortfall
import clean.core.spoken
import clean.core.ChartPoint
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import clean.app.platform.LocalPlatform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import clean.core.Head
import clean.core.MINOR_SHARE
import clean.core.Muscle
import clean.core.SetRange
import clean.app.ui.theme.LocalCleanColors
import androidx.compose.material3.OutlinedTextField
import clean.core.MuscleExercise
import clean.core.headBreakdown
import clean.core.headSetsBySide
import clean.core.muscleSetsBySide
import clean.core.SECONDARY_SHARE
import clean.core.FATIGUE_RECOVERING
import clean.core.readyAt
import clean.core.muscleBreakdown
import clean.core.shortNumber
import clean.core.weekStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** A set count for the screen: whole where it is whole, otherwise to the quarter set. */
private fun sets(value: Double) = shortNumber(roundHalfUp(value * 100) / 100.0)

/** Whether an exercise's sets were counted other than one each, which is so only for left and right work (a round is one set). */
private fun MuscleExercise.hasSides() = weighted != sets.toDouble()

/** A quiet line under a figure that says how the two sides read, `L fatigued · R ready`, with its sentence for a screen reader. */
@Composable
private fun QuietLine(text: String, spoken: String) {
    Text(text, Modifier.semantics { contentDescription = spoken }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * What the two sides of a muscle or head were worked in the time frame, only where some of it was logged left and right: the
 * counts and the gap, a chart of sets per week on one scale, and the left and right exercises that fed it (each opens its page).
 */
@Composable
private fun SidesSection(model: AppModel, credit: SideCredit, weeks: Map<LocalDate, SideCredit>, exercises: List<MuscleExercise>, now: LocalDateTime, noun: String) {
    val short = credit.shortfall()
    Section("Left and right") {
        LabelValue(
            "Sets", "Left ${sets(credit.left)} · Right ${sets(credit.right)}",
            "Left ${setsText(sets(credit.left))}, right ${setsText(sets(credit.right))}",
        )
        LabelValue(
            "Gap", short?.let { "${if (it.lagging == Side.LEFT) "Left" else "Right"} ${it.percent} % less" } ?: "Even",
            if (short == null) "Even" else short.spoken(),
        )
        // One point per week, Monday to Sunday, weeks without work as zero; both lines on one scale.
        val first = weeks.keys.minOrNull()
        val monday = first?.let { weekStart(now.toLocalDate()) }
        val dates = if (first == null || monday == null) emptyList() else (0..ChronoUnit.WEEKS.between(first, monday)).map { first.plusWeeks(it) }
        if (dates.size < 2) Text("Not enough weeks in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val title = "Sets per week, left and right"
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MultiLineChart(
                listOf(
                    ChartSeries("Left", "sets", null, dates.map { ChartPoint(it, weeks[it]?.left ?: 0.0) }),
                    ChartSeries("Right", "sets", seriesColors()[1], dates.map { ChartPoint(it, weeks[it]?.right ?: 0.0) }),
                ),
                title, shared = true,
            )
        }
        val fed = exercises.filter { it.hasSides() }
        if (fed.isNotEmpty()) {
            Text("Open exercises", Modifier.padding(top = Space.s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExerciseParts(model, fed, noun, explain = false)
        }
    }
}

/**
 * Where one muscle's set count comes from: the exercises that add up to it and the workouts they
 * were done in, over the same time frame as the Body tab (the two stay in step).
 */
@Composable
fun MuscleDetailScreen(model: AppModel, muscle: Muscle) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val figures by model.muscleFigures.collectAsState()
    val bodyMode by model.remembered("bodyMode", "Balance")
    // Worked out off the main thread; fresh figures are asked for each time the page opens.
    LaunchedEffect(Unit) { model.refreshMuscles() }
    val days = model.muscleDays
    val musclesOf = remember(settings) { { name: String -> model.musclesOf(name) } }
    val now = remember(workouts) { LocalDateTime.now() }
    val from = if (days == 0L) null else now.minusDays(days)
    val all by model.muscleSetCounts.collectAsState()
    val palette = LocalLook.current.scale
    val headData by model.headFigures.collectAsState()
    // The heads of this group with sets in the time frame, shaded against the largest like the Body tab's.
    val headsHere = Head.entries.filter { it.group == muscle }.mapNotNull { head -> headData.sets[head]?.takeIf { it > 0 }?.let { head to it } }
    val top = headsHere.maxOfOrNull { it.second } ?: 1.0
    val otherWork = headData.other[muscle] ?: 0.0
    val parts = remember(workouts, settings, days, muscle) { muscleBreakdown(workouts, from, musclesOf, muscle) }
    val total = all[muscle] ?: 0.0
    // What each side was worked, only where some of the work was left and right (otherwise the sides are the same).
    val sideCredit = remember(workouts, settings, days, muscle, parts) {
        if (parts.exercises.none { it.hasSides() }) null else muscleSetsBySide(workouts, from, musclesOf)[muscle]
    }
    val sideWeeks = remember(workouts, settings, days, muscle, sideCredit) {
        if (sideCredit == null) emptyMap() else muscleSideWeeks(workouts, from, musclesOf, muscle)
    }
    val last = figures?.lastTrained?.get(muscle)
    val tired = figures?.fatigue?.get(muscle) ?: 0.0
    val strength = figures?.strength?.get(muscle) ?: 0.0
    val tiredSides = figures?.fatigueSides?.get(muscle)
    val strengthSides = figures?.strengthSides?.get(muscle)
    val meaning = LocalCleanColors.current
    // The figure's limbs in the Body tab's view, one per side; the two sides are told apart only where they read differently.
    val limbs: Map<Pair<Muscle, Side>, Color> = when {
        bodyMode == "Sides" && sideCredit != null -> {
            val larger = maxOf(sideCredit.left, sideCredit.right).takeIf { it > 0 } ?: 1.0
            Side.entries.associate { (muscle to it) to shade(palette, sideCredit.of(it) / larger) }
        }
        bodyMode == "Fatigue" && tiredSides != null && fatigueWord(tiredSides.left) != fatigueWord(tiredSides.right) ->
            Side.entries.associate { (muscle to it) to fatigueColour(tiredSides.of(it), meaning) }
        bodyMode == "Strength" && last != null && strengthSides != null && roundHalfUp(strengthSides.left * 100) != roundHalfUp(strengthSides.right * 100) ->
            Side.entries.associate { (muscle to it) to strengthColour(strengthSides.of(it), meaning, palette) }
        else -> emptyMap()
    }
    // In the Sides view an open group's heads are shaded per side too, against the largest head side.
    val headLimbs: Map<Pair<Head, Side>, Color> = if (bodyMode != "Sides" || sideCredit == null) emptyMap() else {
        val larger = headsHere.maxOfOrNull { (head, _) -> headData.bySide[head]?.let { maxOf(it.left, it.right) } ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
        headsHere.flatMap { (head, _) -> headData.bySide[head]?.let { c -> Side.entries.map { (head to it) to shade(palette, c.of(it) / larger) } } ?: emptyList() }.toMap()
    }
    // The length of the period in weeks; for "All", from the first workout logged.
    val span = if (days != 0L) days / 7.0 else
        workouts.firstOrNull()?.let { parseDate(it.date) }?.let { Duration.between(it, now).toDays() / 7.0 }?.coerceAtLeast(1.0) ?: 1.0
    // One point per week, Monday to Sunday, weeks without the muscle included as zero.
    val weekly: List<Pair<LocalDate, Double>> = remember(parts) {
        val byWeek = HashMap<LocalDate, Double>()
        for (w in parts.workouts) {
            val monday = parseDate(w.date)?.toLocalDate()?.let(::weekStart) ?: continue
            byWeek[monday] = (byWeek[monday] ?: 0.0) + w.counted
        }
        val first = byWeek.keys.minOrNull() ?: return@remember emptyList()
        val weeks = ChronoUnit.WEEKS.between(first, weekStart(now.toLocalDate()))
        (0..weeks).map { first.plusWeeks(it) }.map { it to (byWeek[it] ?: 0.0) }
    }

    // The weekly target of this muscle (null when switched off for it), and the dialog that changes it.
    val range = model.setRanges.collectAsState().value[muscle]
    var targeting by remember { mutableStateOf(false) }
    if (targeting) {
        var low by remember { mutableStateOf((range ?: model.defaultSetRange.value).min.toString()) }
        var high by remember { mutableStateOf((range ?: model.defaultSetRange.value).max.toString()) }
        val typed = SetRange.decode("${low.trim()}-${high.trim()}")
        CleanDialog(
            onDismissRequest = { targeting = false },
            title = { Text("${muscle.label}: sets a week") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Counted as on the Body page: a supporting muscle gets half a set for each set, a minor one a quarter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        OutlinedTextField(low, { low = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(high, { high = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { model.setSetRange(muscle, null); targeting = false }) { Text("Use the default, ${model.defaultSetRange.value.min} to ${model.defaultSetRange.value.max}") }
                    TextButton(onClick = { model.setSetRange(muscle, "off"); targeting = false }) { Text("No target") }
                }
            },
            confirmButton = { TextButton(enabled = typed != null, onClick = { model.setSetRange(muscle, typed?.encode()); targeting = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { targeting = false }) { Text("Cancel") } },
        )
    }

    var explaining by remember { mutableStateOf(false) }
    if (explaining) {
        val recent = remember(workouts, settings, muscle) { muscleBreakdown(workouts, now.minusDays(7), musclesOf, muscle).workouts }
        CleanDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Fatigue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("An estimate from the weight and reps of recent sets, against what has been a normal session for this muscle. It fades by half every 36 hours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (recent.isEmpty()) Text("Not trained in the last 7 days.")
                    for (w in recent) LinkRow(w.name, prettyDate(w.date), setsText(sets(w.counted))) { explaining = false; model.open(Screen.WorkoutDetail(w.date)) }
                }
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("Close") } },
        )
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(muscle.label, model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Section("") {
                    RangeChoice(listOf(7L, 30L, 90L, 0L), days, { if (it == 0L) "All" else if (it == 7L) "Week" else "${it}d" }) { model.muscleDays = it }
                    // Only this muscle is lit, with its heads over it; tapping another one on the figure goes to it.
                    BodyFigure(
                        mapOf(muscle to MaterialTheme.colorScheme.primary), { if (it != muscle) model.open(Screen.MuscleDetail(it)) },
                        headColours = headsHere.associate { (head, v) -> head to shade(palette, v / top) },
                        open = muscle.takeIf { m -> Head.entries.any { it.group == m } }, onHead = { model.open(Screen.HeadDetail(it)) },
                        headTip = { head -> headData.sets[head]?.let { setsText(sets(it)) } ?: "not trained" },
                        sideColours = limbs, headSideColours = headLimbs,
                        sideTip = { _, side ->
                            when (bodyMode) {
                                "Sides" -> setsText(sets(sideCredit?.of(side) ?: 0.0))
                                "Fatigue" -> fatigueWord(tiredSides?.of(side) ?: tired)
                                else -> "${roundHalfUp((strengthSides?.of(side) ?: strength) * 100)}% retained"
                            }
                        },
                        headSideTip = { head, side -> headData.bySide[head]?.let { setsText(sets(it.of(side))) } ?: "not trained" },
                        zoomable = true,
                    ) { m -> all[m]?.let { setsText(sets(it)) } ?: "not trained" }
                    LabelValue("Sets", if (total > 0) sets(total) else "not trained yet", valueColor = if (total > 0) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (total > 0) {
                        // The ranking itself is the Body tab.
                        LabelValue("Rank", "${all.values.count { it > total } + 1} of ${Muscle.entries.size} muscles") { model.openBody() }
                        LabelValue("Per week", sets(total / span))
                    }
                }
            }
            item {
                Section("Now") {
                    LabelValue("Last trained", last?.let { "${prettyDay(it.toLocalDate())} · ${Duration.between(it, now).toDays()} days ago" } ?: "never", valueColor = if (last == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    LabelValue("Fatigue", fatigueWord(tired)) { explaining = true }
                    if (tiredSides != null && fatigueWord(tiredSides.left) != fatigueWord(tiredSides.right)) {
                        val l = fatigueWord(tiredSides.left)
                        val r = fatigueWord(tiredSides.right)
                        QuietLine("L $l · R $r", "Left $l, right $r")
                    }
                    if (bodyMode == "Fatigue" && maxOf(tired, tiredSides?.left ?: 0.0, tiredSides?.right ?: 0.0) >= FATIGUE_RECOVERING) figures?.let { f ->
                        // The slower side decides, and is named when the sides differ.
                        val left = tiredSides?.let { readyAt(it.left, f.at) }
                        val right = tiredSides?.let { readyAt(it.right, f.at) }
                        val (ready, slower) = when {
                            tiredSides == null -> readyAt(tired, f.at) to null
                            left == right -> left to null
                            left == null -> right to Side.RIGHT
                            right == null || left > right -> left to Side.LEFT
                            else -> right to Side.RIGHT
                        }
                        ready?.let { LabelValue("Ready", readyText(it, f.at, LocalPlatform.current.use24HourClock) + (slower?.let { s -> " (${sideWord(s)})" } ?: "")) }
                    }
                    if (last == null) LabelValue("Retained strength", NONE, valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LabelValue("Retained strength", "${roundHalfUp(strength * 100)}%")
                    if (last != null && strengthSides != null && roundHalfUp(strengthSides.left * 100) != roundHalfUp(strengthSides.right * 100)) {
                        val l = roundHalfUp(strengthSides.left * 100)
                        val r = roundHalfUp(strengthSides.right * 100)
                        QuietLine("L $l% · R $r%", "Left $l percent, right $r percent")
                    }
                }
            }
            item {
                Section("Sets per week") {
                    if (weekly.size < 2) Text("Not enough weeks in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LineChart(
                        weekly, "sets", range = range?.let { it.min.toDouble()..it.max.toDouble() },
                        bands = range?.let { listOf(it.min.toDouble()..it.max.toDouble() to goalColor()) }.orEmpty(),
                        selector = RangeSelector(days, { model.muscleDays = it }),
                    ) { week -> model.open(Screen.Month(week.toString().take(7))) }
                    // Tapping it changes this muscle's range.
                    Text(
                        range?.let { "Target ${it.min} to ${it.max} a week" } ?: "No weekly target",
                        Modifier.clickable { targeting = true }.padding(vertical = Space.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sideCredit != null) item { SidesSection(model, sideCredit, sideWeeks, parts.exercises, now, "muscle") }
            if (headsHere.isNotEmpty()) item {
                Section("Heads") {
                    for ((head, v) in headsHere) LabelValue(head.label, setsText(sets(v))) { model.open(Screen.HeadDetail(head)) }
                    if (otherWork > 0) LabelValue("Other work", setsText(sets(otherWork)))
                }
            }
            item {
                Section("What makes up these sets") {
                    ExerciseParts(model, parts.exercises, "muscle")
                }
            }
            item {
                Section("Workouts") {
                    if (parts.workouts.isEmpty()) Text("None in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (w in parts.workouts) {
                        Row(
                            Modifier.fillMaxWidth().clickable { model.open(Screen.WorkoutDetail(w.date)) }.padding(vertical = Space.xs),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name)
                                Text(prettyDate(w.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(setsText(sets(w.counted)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** The exercises behind a set count, each with what it counted for and a tap to its page; [noun] is what the count is of. */
@Composable
private fun ColumnScope.ExerciseParts(model: AppModel, exercises: List<MuscleExercise>, noun: String, explain: Boolean = true) {
    if (exercises.isEmpty()) Text("No exercise in this period trains this $noun.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (explain) Text(
        "A set counts in full where this is a main $noun, as half where it supports and as a quarter where its part is minor. Warm-ups are left out." +
            if (exercises.any { it.hasSides() }) " A left and right round counts as one set." else "",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (e in exercises) {
        // Left and right work: the sets as logged, then as counted (a round is one set).
        val counted = plural(e.sets, "set") + if (e.hasSides()) " as ${shortNumber(e.weighted)}" else ""
        val level = when (e.share) {
            SECONDARY_SHARE -> "$counted × ½ · supporting"
            MINOR_SHARE -> "$counted × ¼ · minor"
            else -> "$counted · main"
        }
        Row(
            Modifier.fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(e.name)) }.padding(vertical = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.name, color = MaterialTheme.colorScheme.primary)
                Text("$level · last ${prettyDay(e.lastDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(sets(e.counted), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One head of a group: where its sets come from, over the same time frame as the Body tab. */
@Composable
fun HeadDetailScreen(model: AppModel, head: Head) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val headData by model.headFigures.collectAsState()
    val all by model.muscleSetCounts.collectAsState()
    val days = model.muscleDays
    val from = if (days == 0L) null else LocalDateTime.now().minusDays(days)
    val musclesOf = remember(settings) { { name: String -> model.musclesOf(name) } }
    val exercises = remember(workouts, settings, days, head) { headBreakdown(workouts, from, musclesOf, head) }
    val total = headData.sets[head] ?: 0.0
    val sideCredit = remember(workouts, settings, days, head, exercises) {
        if (exercises.none { it.hasSides() }) null else headSetsBySide(workouts, from, musclesOf)[head]
    }
    val sideWeeks = remember(workouts, settings, days, head, sideCredit) {
        if (sideCredit == null) emptyMap() else headSideWeeks(workouts, from, musclesOf, head)
    }
    val primary = MaterialTheme.colorScheme.primary
    val palette = LocalLook.current.scale
    val bodyMode by model.remembered("bodyMode", "Balance")
    // In the Sides view the head's two limbs are shaded by their own side's sets, the larger one in full.
    val limbs: Map<Pair<Head, Side>, Color> = if (bodyMode != "Sides" || sideCredit == null) emptyMap() else {
        val larger = maxOf(sideCredit.left, sideCredit.right).takeIf { it > 0 } ?: 1.0
        Side.entries.associate { (head to it) to shade(palette, sideCredit.of(it) / larger) }
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(head.label, model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = Space.l, vertical = Space.xs)) {
                    Text(head.group.label, Modifier.clickable { model.open(Screen.MuscleDetail(head.group)) }, color = primary)
                    Text(" › ${head.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Section("") {
                    RangeChoice(listOf(7L, 30L, 90L, 0L), days, { if (it == 0L) "All" else if (it == 7L) "Week" else "${it}d" }) { model.muscleDays = it }
                    // The group's shape in a quiet tint with this head over it; a head the figure has no outline for shows the group alone.
                    BodyFigure(
                        mapOf(head.group to primary.copy(alpha = 0.35f)), { if (it != head.group) model.open(Screen.MuscleDetail(it)) },
                        headColours = mapOf(head to primary), open = head.group,
                        onHead = { if (it != head) model.open(Screen.HeadDetail(it)) },
                        headTip = { h -> headData.sets[h]?.let { setsText(sets(it)) } ?: "not trained" },
                        headSideColours = limbs,
                        headSideTip = { h, side -> headData.bySide[h]?.let { setsText(sets(it.of(side))) } ?: "not trained" },
                        zoomable = true,
                    ) { m -> all[m]?.let { setsText(sets(it)) } ?: "not trained" }
                    LabelValue("Sets", if (total > 0) sets(total) else "not trained yet", valueColor = if (total > 0) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sideCredit != null) item { SidesSection(model, sideCredit, sideWeeks, exercises, LocalDateTime.now(), "head") }
            item {
                Section("What makes up these sets") {
                    ExerciseParts(model, exercises, "head")
                }
            }
        }
    }
}

/** "in about 3 hours" when close, else "by Thu 14:00" in the device's clock style. */
private fun readyText(at: LocalDateTime, now: LocalDateTime, use24Hour: Boolean): String {
    val hours = roundHalfUp(Duration.between(now, at).toMinutes() / 60.0).coerceAtLeast(1)
    if (Duration.between(now, at).toHours() < 6) return "in about $hours ${if (hours == 1L) "hour" else "hours"}"
    val pattern = if (use24Hour) "EEE HH:mm" else "EEE h:mm a"
    return "by " + at.format(LocalePattern(pattern))
}
