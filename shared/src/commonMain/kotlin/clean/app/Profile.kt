package clean.app

import clean.core.time.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import clean.core.unglinted
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Rect
import clean.app.ui.theme.GlassWindowShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import clean.app.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.orange
import clean.core.Milestone
import clean.core.MilestoneKind
import clean.core.SplitGroup
import clean.core.exerciseGoalProgress
import clean.core.latestReached
import clean.core.milestones
import clean.core.nextMilestone
import clean.core.parseGoal
import clean.core.progressOf
import clean.core.recordCount
import clean.core.title
import clean.core.trainingSplit
import kotlinx.datetime.LocalDate
import clean.core.comparePose
import clean.core.shortNumber
import clean.core.volumeOf

private val SINCE_MONTH = LocalePattern("MMM yyyy")


private val SHORT_DAY = LocalePattern("d MMM")

/** The space between the sections of the page. */
private val SECTION_GAP = 28.dp

/** The lifts the Strength section lists. */
private const val STRENGTH_LIFTS = 4

/** The profile picture last decoded, with the profile and picture version it is for. */
private var AVATAR_KEPT: Pair<String, ImageBitmap>? = null

/**
 * Profile (F4), the fourth tab (1.1.0 remake, stage 4): the gear (Settings) at the top right, who this is, a row of stat chips, then Body,
 * Strength, Goals and milestones and a quiet Switch profile. Everything is optional; a part with nothing in it is one quiet line that adds
 * the first, never an empty box. Every number opens what explains it. Its list keeps its place like the other tabs' (`tabLists`).
 */
@Composable
fun ProfileTab(model: AppModel) {
    if (model.profileEdit) ProfileEditDialog(model) { model.profileEdit = false }
    val state = model.tabLists.getValue(Tab.Profile)
    BarEnd(Tab.Profile, state)
    // Round 17 (13): holding the avatar floats a small card (total training time) beside it; the page is the blur source of that sibling card while it is up.
    var avatarPeek by remember { mutableStateOf<Pair<Rect, String>?>(null) }
    var boxAt by remember { mutableStateOf(Offset.Zero) }
    val peekHaze = dev.chrisbanes.haze.rememberHazeState()
    Box(Modifier.fillMaxSize().onGloballyPositioned { boxAt = it.positionInRoot() }) {
    LazyColumn(Modifier.fillMaxSize().then(if (avatarPeek != null) Modifier.hazeSource(peekHaze) else Modifier), state) {
        item { ProfileTop(model) { avatarPeek = it } }
        item { StatChips(model) }
        item { YearInReviewLine(model) }
        item { PhotosSection(model) }
        item { BodySection(model) }
        item { StrengthSection(model) }
        item { GoalsSection(model) }
        item { SwitchRow(model) }
        item { BarSpace() }
    }
    avatarPeek?.let { (at, text) ->
        Text(
            text, Modifier.placeBy(at.translate(-boxAt), boxAt.x).glass(GlassWindowShape, peekHaze, lift = 8.dp).padding(horizontal = Space.l, vertical = Space.m).testTag("avatar peek"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    }
}

/** The profile's name, units and height are edited, and profiles switched, in a dialog on this page (it was a Settings page before 1.1.0). */
private fun AppModel.editProfile() { profileEdit = true }

/** The initial on a soft gradient of the accent, the name large, and "Training since Apr 2025 · kg · Edit". */
@Composable
private fun ProfileTop(model: AppModel, onPeek: (Pair<Rect, String>?) -> Unit) {
    val profile by model.profile.collectAsState()
    val workouts by model.workouts.collectAsState()
    val first = remember(workouts) { workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } }
    val name = profile?.name.orEmpty().ifBlank { "Me" }
    val accent = LocalLook.current.orange
    // The profile's own picture, when it has one, instead of the initial; the circle opens a small menu to choose or remove it.
    val platform = LocalPlatform.current
    // Kept for the next visit to this page (one picture, at most 512 px square), so the circle is there at once and the file is not decoded again.
    val avatarKey = "${profile?.id}#${model.avatarStamp}"
    val avatar by produceState<ImageBitmap?>(AVATAR_KEPT?.takeIf { it.first == avatarKey }?.second, avatarKey) {
        if (AVATAR_KEPT?.first == avatarKey) { value = AVATAR_KEPT?.second; return@produceState }
        value = profile?.id?.let { id -> withContext(ioDispatcher) { platform.loadAvatar(id) } }
        AVATAR_KEPT = value?.let { avatarKey to it }
    }
    var menu by remember { mutableStateOf(false) }
    // Round 17 (13): holding the circle shows the sum of every workout's length; released, it goes.
    val trainedText = remember(workouts, first) { trainedLine(workouts.sumOf { it.durationSeconds.toLong() }, first) }
    var avatarAt by remember { mutableStateOf(Rect.Zero) }
    val tick = rememberTick()
    val pick = platform.rememberPhotoPicker(false) { picked ->
        picked.firstOrNull()?.let { model.setAvatar(it) }
    }
    if (menu) Sheet({ menu = false }, "Profile picture") {
        SheetRow("Choose photo", CleanIcons.Add) { menu = false; pick() }
        if (avatar != null) SheetRow("Remove photo", CleanIcons.Delete, danger = true) { menu = false; model.removeAvatar() }
    }
    Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f))), CircleShape).litEdge(CircleShape, 0.25f)
                .onGloballyPositioned { avatarAt = it.boundsInRoot() }
                .holdAndSlide(
                    key = trainedText,
                    onHold = { if (trainedText != null) { tick(); onPeek(avatarAt to trainedText) } },
                    onMove = {}, onEnd = { onPeek(null) },
                )
                .clickable(onClickLabel = "Change profile picture") { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            val shown = avatar
            if (shown != null) Image(shown, "Profile picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val big = MaterialTheme.typography.headlineLarge
// The gear (Settings) sits on the name's line at the right; its 48 dp area reaches past the page's edge margin by the disc's own 7 dp.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                    style = big.copy(letterSpacing = (-0.02).em, lineHeight = big.fontSize * 1.1f),
                )
                GlassButton("Settings", { model.open(Screen.Settings) }, Modifier.offset(x = 7.dp)) { Icon(CleanIcons.Settings, null, Modifier.size(20.dp)) }
            }
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    listOfNotNull(first?.let { "Training since ${it.format(SINCE_MONTH)}" }, profile?.unit ?: "kg").joinToString(" · ") + " ·",
                    Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = quiet,
                )
                Text("Edit", Modifier.clickable { model.editProfile() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A section of the page: its title large with an optional [link] at the right (opening [onLink]), then [content]. */
@Composable
private fun ProfileSection(title: String, link: String? = null, onLink: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = SECTION_GAP, start = Space.l, end = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionHeading(title) { if (link != null) SectionLink(link, onLink) }
        content()
    }
}

/** One quiet line that adds the first of something: a grey [label] with a chevron. */
@Composable
private fun AddLine(label: String, onClick: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).minTouch(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = grey)
        Text("›", color = grey)
    }
}

/** A row of the page: [label] at the left, [change] in grey and [value] bold at the right, a hairline under it. */
@Composable
private fun ValueRow(label: String, value: String, change: String = "", onClick: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).minTouch(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, Modifier.weight(1f), maxLines = 1)
        if (change.isNotEmpty()) Text(change, style = MaterialTheme.typography.bodySmall, color = grey, maxLines = 1)
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A thin bar filled to [fraction] in the accent. */
@Composable
private fun ThinBar(fraction: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.toFloat().coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(6.dp),
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), drawStopIndicator = {},
    )
}

/** A small tile with a number and a grey word under it, opening [onClick]. */
@Composable
private fun Chip(number: String, word: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.clip(shape).tile(shape, frosted = true).clickable(onClick = onClick).padding(horizontal = Space.m + 2.dp, vertical = Space.m)
            .semantics(mergeDescendants = true) { contentDescription = "$number $word" },
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Text(number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(word, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Workouts, time trained, total lifted and best streak, in a row that scrolls sideways; with no workout, one quiet line that starts one. */
@Composable
private fun StatChips(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val consistency by model.consistency.collectAsState()
    // settings and bodyweights are keys only so the volume is worked out again when they change.
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val volume = remember(workouts, settings, bodyweights) { workouts.sumOf { volumeOf(it, model::loadOf) } }
    val best = consistency?.streak?.best ?: 0
    if (workouts.isEmpty()) {
        Box(Modifier.padding(horizontal = Space.l)) { AddLine("Start a workout") { model.openWorkoutSheet() } }
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(workouts.size.toString(), if (workouts.size == 1) "workout" else "workouts") { model.open(Screen.History) }
        Chip("${workouts.sumOf { it.durationSeconds } / 3600} h", "trained") { model.open(Screen.Trends) }
        // A lifetime total reads in tonnes (or thousands of pounds) once it is large: "683 t", not "682,830 kg".
        val unit = model.profile.value?.unit ?: "kg"
        val inUnit = if (unit == "lb") clean.core.kgToLb(volume) else volume
        val lifted = if (inUnit < 10_000) model.volume(volume) else if (unit == "lb") thousands(roundHalfUp(inUnit / 1000)) + "k lb" else thousands(roundHalfUp(inUnit / 1000)) + " t"
        Chip(lifted, "lifted") { model.open(Screen.Trends) }
        if (best > 0) Chip("$best wk", "best streak") { model.open(Screen.Trophies) }
    }
}

/** The weight card, each measurement's latest value with its change, Height and BMI, and the newest photos. */
@Composable
private fun BodySection(model: AppModel) {
    val files by model.files.collectAsState()
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    var adding by remember { mutableStateOf(false) }
    if (adding) NewMeasurementDialog(model) { adding = false }
    // Newest first per file, as the Measurements page reads them; weight is the card's.
    val rows = remember(files, measurements) {
        val byFile = measurements.groupBy { it.file }
        files.filter { it.name != "weight.csv" }.mapNotNull { f -> byFile[f.name]?.let { Triple(f, it.first(), it.getOrNull(1)) } }
    }
    val weights = weighIns(model)
    val height = profile?.heightCm?.takeIf { it > 0 }
    val kg = weights.lastOrNull()?.second
    ProfileSection("Body", "Measurements", { model.open(Screen.Measurements) }) {
        WeightCard(model, weights, unit)
        for ((file, last, before) in rows.take(6)) {
            val (label, fileUnit) = splitHeader(file.header)
            val change = before?.let { b ->
                val d = (last.value.toDoubleOrNull() ?: return@let "") - (b.value.toDoubleOrNull() ?: return@let "")
                val text = shortNumber(roundHalfUp(d * 10) / 10.0)
                "${if (d > 0) "+" else ""}$text since ${parseDate(b.date)?.toLocalDate()?.format(SHORT_DAY) ?: b.date}"
            }.orEmpty()
            ValueRow(label, "${shown(last, unit)} $fileUnit".trim(), change) { model.open(Screen.MeasurementDetail(file.name)) }
        }
        if (rows.isEmpty()) AddLine("Add a measurement") { adding = true }
        when {
            height == null -> AddLine("Add your height") { model.editProfile() }
            kg != null -> ValueRow("Height · BMI", "${shortNumber(height)} cm · ${shortNumber(roundHalfUp(bmi(kg, height) * 10) / 10.0)}") { model.open(Screen.Bmi) }
            else -> ValueRow("Height", "${shortNumber(height)} cm") { model.editProfile() }
        }
    }
}

/** Photos (progress photos and snapshots, Round 39), a section of its own above Body with a heading like the others: its fan and the lines under it, and "See all" at the right once there is a picture (see Moments.kt). */
@Composable
private fun PhotosSection(model: AppModel) {
    val pics by model.pics.collectAsState()
    ProfileSection("Photos", if (pics.isEmpty()) null else "See all", { model.open(Screen.Photos) }) { PhotosTile(model) }
}

/** The latest weight with how far it moved and the goal, its chart with the goal as a dashed line, and a round "+"; with no weigh-in, one quiet line. */
@Composable
private fun WeightCard(model: AppModel, weights: List<Pair<LocalDate, Double>>, unit: String) {
    var logging by remember { mutableStateOf(false) }
    // Where the "+" is, read when the sheet opens (not state: it changes as the page scrolls).
    val origin = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    if (logging) WeightSheet(model, origin = origin[0]) { logging = false }
    val current = weights.lastOrNull()
    if (current == null) {
        AddLine("Add your weight") { logging = true }
        return
    }
    val goal = weightGoalOf(model)
    val recent = weights.filter { it.first >= LocalDate.now().minusDays(30) }
    // As Home's tile: from the first weigh-in of the last 30 days; with only one in that time, from the one before it.
    val base = if (recent.size >= 2) recent.first() else weights.getOrNull(weights.size - 2)
    val moved = base?.let { showWeight(current.second - it.second, unit) }
    val change = when {
        base == null -> null
        moved == "0" || moved == "-0" -> "no change since ${base.first.format(SHORT_DAY)}"
        else -> "${if (current.second > base.second) "+" else ""}$moved since ${base.first.format(SHORT_DAY)}"
    }
    Tile("Weight", Modifier.fillMaxWidth(), "profile:weight", onClick = { model.open(Screen.MeasurementDetail("weight.csv")) }) {
        TileNumberAdd("Log body weight", { logging = true }, { origin[0] = it }) { TileNumber(showWeight(current.second, unit), unit, "tile:profile:weight") }
        TileLine(listOfNotNull(change, goal?.let { "goal ${showWeight(it.targetKg, unit)}" }).joinToString(" · "))
        val line = weightLine(weights).takeIf { it.size >= 2 } ?: weights.takeLast(12).map { it.second }
        val reveal = rememberNewPoint(model, "profile:weight", weights.size)
        if (line.size >= 2) Sparkline(line, Modifier.padding(top = Space.xs), height = 64.dp, morph = "chart:weight", wash = true, goal = goal?.targetKg, reveal = reveal)
    }
}

/** One lift of the Strength section: its latest estimated 1RM (kg), that over the latest body weight, and how far its bar fills. */
private class StrengthLift(val name: String, val e1rm: Double, val perBodyweight: Double?, val bar: Double, val peek: List<String>)

private data class Found(val name: String, val e1rm: Double, val goal: Double?, val peek: List<String>)

/**
 * The most logged lifts of the last twelve weeks (as Home picks its 1RM tiles) that have an estimated 1RM, [STRENGTH_LIFTS] of them. A lift's bar is
 * how far it is to its goal when it has one, else its share of the strongest of the four (by x bodyweight when there is a weigh-in, else by kg).
 */
@Composable
private fun strengthLifts(model: AppModel, bodyKg: Double?): List<StrengthLift> {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    return remember(workouts, settings, bodyweights, rule, bodyKg) {
        val today = LocalDate.now()
        val since = today.minusDays(HOME_LIFT_DAYS.toLong()).toString()
        val found = liftsByFrequency(workouts, since).asSequence().mapNotNull { name ->
            val sessions = model.sessionsIn(workouts, name)
            val loadType = model.loadTypeOf(name)
            val progress = progressOf(sessions, rule, loadType, bodyweights::at)
            val at = progress.indexOfLast { it.estimated1Rm != null }
            val e1rm = progress.getOrNull(at)?.estimated1Rm ?: return@mapNotNull null
            // Round 19: what a hold shows: the set behind that estimate, its day and the change since the session before.
            val unit = model.unitOf(name)
            val session = sessions[at]
            val best = session.sets.filter { it.type != clean.core.SetType.WARMUP && it.type != clean.core.SetType.DROP }.mapNotNull { s ->
                val load = s.weight?.let { clean.core.realLoad(loadType, it, bodyweights.at(session.date)) } ?: return@mapNotNull null
                val estimate = s.reps?.let { clean.core.estimate1Rm(load, it, rule, s.rpe) } ?: return@mapNotNull null
                s to estimate
            }.maxByOrNull { it.second }?.first
            val before = progress.take(at).lastOrNull { it.estimated1Rm != null }
            val peek = buildList {
                add(name)
                add("Best set " + (best?.let { setValues(it, unit) } ?: "-") + " · " + prettyDay(session.date))
                add(
                    if (before == null) "First estimate"
                    else {
                        val change = e1rm - before.estimated1Rm!!
                        val moved = showEstimate(change, unit)
                        (if (moved == "0" || moved == "-0") "No change" else (if (change > 0) "+" else "") + moved + " " + unit) + " since " + prettyDay(before.date)
                    },
                )
            }
            val goal = parseGoal(settings[name]?.goal)?.let { (kg, reps) -> exerciseGoalProgress(sessions, kg, reps, rule, loadType, bodyweights::at, today)?.fraction }
            Found(name, e1rm, goal, peek)
        }.take(STRENGTH_LIFTS).toList()
        val strongest = found.maxOfOrNull { (_, e, _, _) -> if (bodyKg != null) e / bodyKg else e } ?: 1.0
        found.map { (name, e, goal, peek) ->
            val per = bodyKg?.let { e / it }
            StrengthLift(name, e, per, goal ?: ((per ?: e) / strongest), peek)
        }
    }
}

/** The four most logged lifts with their estimated 1RM, x bodyweight and a bar; then the month's PRs and the split of the last four weeks by sets. */
@Composable
private fun StrengthSection(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val prs by model.prs.collectAsState()
    val settings by model.settings.collectAsState()
    val weights = weighIns(model)
    val bodyKg = weights.lastOrNull()?.second
    val lifts = strengthLifts(model, bodyKg)
    val today = model.today
    val monthPrs = remember(workouts, prs, today) {
        val from = today.withDayOfMonth(1).toString()
        workouts.filter { it.day >= from }.sumOf { recordCount(it, prs[it.date].orEmpty()) }
    }
    val split = remember(workouts, settings, today) {
        trainingSplit(workouts, today.minusWeeks(4).toString()) { model.musclesOf(it).primary }
    }
    val seen = remember { HashSet<String>() }
    ProfileSection("Strength", "Personal bests", { model.open(Screen.PersonalBests) }) {
        if (workouts.isEmpty()) {
            AddLine("Log your first workout") { model.openWorkoutSheet() }
            return@ProfileSection
        }
        if (lifts.isEmpty()) AddLine("Log a lift twice to see its estimated 1RM") { model.open(Screen.PersonalBests) }
        Column {
            for (lift in lifts) {
                val unit = model.unitOf(lift.name)
                HoldPeekBox("strength peek", {
                    PeekHead(lift.peek[0])
                    PeekLine(lift.peek[1], bold = true)
                    PeekLine(lift.peek[2])
                }, Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(lift.name, tab = 2)) }.padding(vertical = Space.s),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(lift.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        lift.perBodyweight?.let { Text(countUpOnce(seen, "${lift.name}:bw", 1, it) { v -> fixed(v, 2, phoneLocale = false) } + " × bw", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        Text("${countUpOnce(seen, "${lift.name}:e1rm", 0, lift.e1rm) { v -> showEstimate(v, unit) }} $unit", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    ThinBar(lift.bar)
                }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(shortNumber(monthPrs), if (monthPrs == 1.0) "PR this month" else "PRs this month") { model.open(Screen.PersonalBests) }
            if (split != null) {
                val order = SplitGroup.entries.sortedByDescending { split.of(it) }
                val lead = order.first()
                Chip("${lead.label} ${split.of(lead)} %", order.drop(1).joinToString(" · ") { "${it.label.lowercase()} ${split.of(it)}" }) { model.open(Screen.Body) }
            }
        }
    }
}

/** The goals with their bars, the next milestone with its bar and, when any are earned, the latest trophies with the count; each opens what explains it. */
@Composable
private fun GoalsSection(model: AppModel) {
    val goals = goalRows(model)
    val workouts by model.workouts.collectAsState()
    val consistency by model.consistency.collectAsState()
    val prs by model.prs.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val best = consistency?.streak?.best ?: 0
    val next = remember(workouts, prs, settings, bodyweights, best) {
        nextMilestone(milestones(workouts.map { it.day }, workouts.map { volumeOf(it, model::loadOf) }, workouts.map { recordCount(it, prs[it.date].orEmpty()) }, best))
    }
    val earned = remember(consistency) { consistency?.awards.orEmpty().filter { it.earned != null }.sortedByDescending { it.earned } }
    var choosing by remember { mutableStateOf(false) }
    // A newly earned milestone glints once, the first time it is shown (kv `glint.seen`, set for what was earned before it existed).
    val motion = LocalMotion.current
    val glint = remember { androidx.compose.animation.core.Animatable(0f) }
    var glinting by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(earned.isNotEmpty()) {
        if (earned.isEmpty()) return@LaunchedEffect
        val seen = model.glintSeen() ?: return@LaunchedEffect
        val fresh = unglinted(earned.take(5).map { it.id }, seen)
        if (fresh.isEmpty()) return@LaunchedEffect
        glinting = fresh.toSet()
        if (motion) {
            delay(300)
            glint.animateTo(1f, tween(700, easing = Motion.Standard))
        }
        model.markGlinted(fresh)
    }
    if (choosing) SetGoalSheet(model) { choosing = false }
    ProfileSection("Goals and milestones", "Trophies", { model.open(Screen.Trophies) }) {
        if (goals.isNotEmpty() || next != null) {
            val shape = cardShape()
            Column(Modifier.fillMaxWidth().clip(shape).tile(shape, frosted = true).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                for (g in goals) GoalLine(g)
                if (next != null) Column(Modifier.fillMaxWidth().clickable { model.open(Screen.Trophies) }.minTouch(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text("Next milestone: ${next.title(model::volume)}", Modifier.weight(1f))
                        Text(milestoneProgress(model, next), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ThinBar(next.progress)
                }
            }
        }
        if (goals.none { it.name != "Workouts per week" }) AddLine("Set a goal") { choosing = true }
        if (earned.isNotEmpty()) Row(Modifier.fillMaxWidth().clickable { model.open(Screen.Trophies) }, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (a in earned.take(5)) {
                    val shape = MaterialTheme.shapes.medium
                    Box(Modifier.size(52.dp).clip(shape).tile(shape).then(if (a.id in glinting) Modifier.glint { glint.value } else Modifier).semantics { contentDescription = a.title }, contentAlignment = Alignment.Center) {
                        Icon(CleanIcons.Star, null, Modifier.size(22.dp), tint = prColor())
                    }
                }
            }
            Text("${earned.size} ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** `42 of 50`, `640,000 kg of 1,000,000 kg`, `5 of 8 weeks`. */
private fun milestoneProgress(model: AppModel, m: Milestone): String = when (m.kind) {
    MilestoneKind.VOLUME -> "${model.volume(m.current)} of ${model.volume(m.target.toDouble())}"
    MilestoneKind.STREAK -> "${m.current.toLong()} of ${m.target} weeks"
    else -> "${m.current.toLong()} of ${m.target}"
}

/** Switching profile (or adding one) is the dialog that lists them. */
@Composable
private fun SwitchRow(model: AppModel) {
    Column(Modifier.padding(top = SECTION_GAP, start = Space.l, end = Space.l)) {
        Row(Modifier.fillMaxWidth().clickable { model.editProfile() }.minTouch(), verticalAlignment = Alignment.CenterVertically) {
            Text("Switch profile", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
