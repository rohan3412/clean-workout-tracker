package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.data.MeasurementEntity
import clean.app.ui.theme.numbers
import clean.core.Milestone
import clean.core.MilestoneKind
import clean.core.agoText
import clean.core.latestReached
import clean.core.milestones
import clean.core.nextMilestone
import clean.core.recordCount
import clean.core.shortNumber
import clean.core.title
import clean.core.volumeOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SINCE_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val SINCE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

internal const val PROFILE_CARDS = "profile.cards"

/** The cards Profile shows, in order, with their widths: the stored list (kv `profile.cards:<profileId>`), else the default. */
@Composable
internal fun AppModel.profileEntries(): List<CardEntry<ProfileCard>> {
    val all by options.collectAsState()
    val id by activeId.collectAsState()
    val stored = all["$PROFILE_CARDS:$id"]
    return remember(stored) { parseProfile(stored) }
}

private fun AppModel.profileNow(): List<CardEntry<ProfileCard>> = parseProfile(options.value["$PROFILE_CARDS:${activeId.value}"])

/** Stores the cards Profile shows, or goes back to the default with null. */
private fun AppModel.setProfileCards(entries: List<CardEntry<ProfileCard>>?) {
    val id = activeId.value ?: return
    setOption("$PROFILE_CARDS:$id", entries?.let { encodeHome(it) })
}

/**
 * Profile: cards the user arranges as on Home (hold one, or "Arrange profile"), under a title with the Settings gear at the top right.
 * Image-led and number-led cards alike come from the stored rows; a card with nothing to show says so or shows nothing.
 */
@Composable
fun ProfileTab(model: AppModel) {
    val profile by model.profile.collectAsState()
    val options by model.options.collectAsState()
    val entries = model.profileEntries()
    val segments = remember(entries) { homeSegments(entries) }
    val even = options[HOME_LAYOUT] == "even"
    val haptic = LocalHapticFeedback.current
    val state = rememberLazyListState()
    var listTop by remember { mutableFloatStateOf(0f) }
    fun arrange(held: String?) { model.arrangeAnchor = arrangeAnchorOf(state, listTop, held); model.open(Screen.EditProfile) }
    BarEnd(Tab.Profile, state)
    val pinned = pinsTitle()
    val twoTone = clean.app.ui.theme.LocalLook.current.twoToneTitles
    val header: @Composable () -> Unit = {
        // Linen sets "Profile" over the name in the faint grey; the other looks title the page with the name.
        Title(if (twoTone) "Profile" else profile?.name ?: "Profile", if (twoTone) profile?.name else null) { HeaderButton(CleanIcons.Settings, "Settings", { model.open(Screen.Settings) }) }
    }
    CompositionLocalProvider(LocalEdgeCharts provides (options[HOME_CHARTS] != "inset")) {
        SheetTab(header) {
        LazyColumn(Modifier.fillMaxSize().onGloballyPositioned { listTop = it.positionInRoot().y }, state) {
            if (!pinned) item { header() }
            sheetGap(pinned)
            // Holding a card opens Arrange profile.
            items(segments, key = { it.first().card.key }) { segment ->
                Box(Modifier.onHold { haptic.performHapticFeedback(HapticFeedbackType.LongPress); arrange(segment.first().card.key) }) {
                    if (segment.first().wide) OpenableProfileCard(model, segment.first())
                    else HalfCards(even) { for (e in segment) Box { OpenableProfileCard(model, e) } }
                }
            }
            item { QuietButton("Arrange profile", { arrange(null) }, Modifier.padding(horizontal = Space.l, vertical = Space.s)) }
            item { BarSpace() }
        }
        }
    }
}

/** Arrange profile: Home's arrange page with Profile's cards. */
@Composable
fun EditProfileScreen(model: AppModel) {
    ArrangeScreen(
        model, "Arrange profile", ProfileCard.entries, PROFILE_DEFAULT, { model.profileNow() }, { model.setProfileCards(it) }, "Profile reset",
    ) { ProfileCardContent(model, it) }
}


/** Names the card so that, if it opens a page, the page grows out of it (see [opensInto]). Only Profile's own list does this, not Arrange profile's copy. */
@Composable
private fun OpenableProfileCard(model: AppModel, entry: CardEntry<ProfileCard>) =
    CompositionLocalProvider(LocalCardKey provides "profile:${entry.card.key}") { ProfileCardContent(model, entry) }

/** What a card shows, at the width it has been given. */
@Composable
private fun ProfileCardContent(model: AppModel, entry: CardEntry<ProfileCard>) {
    val half = !entry.wide
    when (entry.card) {
        ProfileCard.Details -> DetailsCard(model)
        ProfileCard.Totals -> TotalsCard(model, half)
        ProfileCard.Photos -> ProfilePhotosCard(model, half)
        ProfileCard.MemberSince -> MemberSinceTile(model)
        ProfileCard.Streak -> StreakTile(model)
        ProfileCard.Measurements -> MeasurementsCard(model, half)
        ProfileCard.Milestones -> MilestonesCard(model)
        ProfileCard.Trophies -> TrophiesCard(model)
        ProfileCard.Weight -> WeightTile(model)
        ProfileCard.Workouts -> WorkoutsTile(model)
        ProfileCard.RecentPrs -> RecentPrsCard(model, half)
        ProfileCard.Goals -> GoalsCard(model, half)
    }
}

/** This profile's name, units and height, with the ways to edit it or switch to another (both are the Settings Profile page). */
@Composable
private fun DetailsCard(model: AppModel) {
    val profile by model.profile.collectAsState()
    val editProfile = { model.settingsPage = "Profile"; model.open(Screen.Settings) }
    Section("This profile") {
        profile?.let { p ->
            LabelValue("Name", p.name)
            LabelValue("Weights in", p.unit)
            LabelValue("Height", p.heightCm?.takeIf { it > 0 }?.let { "${shortNumber(it)} cm" } ?: NONE)
        }
        NavRow("Edit profile", "", editProfile)
        NavRow("Switch profile", "", editProfile)
    }
}

private class Total(val label: String, val value: String, val spark: List<Double>?, val onClick: () -> Unit)

/** Everything logged: workouts, time trained, volume lifted and the week streak, since the first workout. Wide it is two by two with the weekly lines; half it is a column. */
@Composable
private fun TotalsCard(model: AppModel, half: Boolean) {
    val workouts by model.workouts.collectAsState()
    val consistency by model.consistency.collectAsState()
    // settings and bodyweights are keys only so the volume is worked out again when they change.
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val volume = remember(workouts, settings, bodyweights) { workouts.sumOf { volumeOf(it, model::loadOf) } }
    val first = remember(workouts) { workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } }
    val weeks = recentWeeks(model)
    if (workouts.isEmpty()) return
    val totals = listOf(
        Total("Workouts", workouts.size.toString(), null) { model.open(Screen.History) },
        Total("Time trained", duration(workouts.sumOf { it.durationSeconds }), weeks.finished().map { it.seconds.toDouble() }) { model.open(Screen.Trends) },
        Total("Volume lifted", model.volume(volume), weeks.finished().map { it.volume }) { model.open(Screen.Trends) },
        Total("Week streak", (consistency?.streak?.length ?: 0).toString(), null) { model.open(Screen.Trophies) },
    )
    Section("Lifetime totals") {
        if (first != null) Text("Since ${first.format(SINCE_MONTH)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (half) for (t in totals) TotalCell(t, Modifier.fillMaxWidth())
        else for (pair in totals.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) { for (t in pair) TotalCell(t, Modifier.weight(1f)) }
    }
}

@Composable
private fun TotalCell(total: Total, modifier: Modifier) {
    Column(modifier.clickable(onClick = total.onClick).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Caption(total.label)
        Text(total.value, style = MaterialTheme.typography.titleLarge.numbers(), fontWeight = FontWeight.SemiBold)
        total.spark?.takeIf { it.size >= 2 }?.let { Sparkline(it, Modifier.padding(top = Space.xs)) }
    }
}

/** The month and year of the first workout, and how long ago; it opens the history. */
@Composable
private fun MemberSinceTile(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val first = workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } ?: return
    Tile("Member since", first.format(SINCE_SHORT), tileModifier(), second = agoText(first, LocalDate.now())) { model.open(Screen.History) }
}

/** The latest of each measurement with how far it moved since the one before; it opens the measurements. With none, a way to log the first weight. */
@Composable
private fun MeasurementsCard(model: AppModel, half: Boolean) {
    val files by model.files.collectAsState()
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    // Newest first per file, as the Measurements page reads them.
    val rows = remember(files, measurements) {
        val byFile = measurements.groupBy { it.file }
        files.mapNotNull { f -> byFile[f.name]?.let { Triple(f, it.first(), it.getOrNull(1)) } }
            .sortedBy { if (it.first.name == "weight.csv") 0 else 1 }
    }
    if (rows.isEmpty()) {
        Section("Body measurements") {
            Text("Weight and other measurements show here once logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuietButton("Log body weight", { model.logWeight() })
        }
        return
    }
    Section("Body measurements", onClick = { model.open(Screen.Measurements) }) {
        for ((file, last, before) in rows.take(if (half) 1 else 4)) {
            val (label, fileUnit) = splitHeader(file.header)
            val suffix = if (file.name == "weight.csv") unit else fileUnit
            Row(
                Modifier.fillMaxWidth().clickable { model.open(Screen.MeasurementDetail(file.name)) }.minTouch(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                // The label, the latest value and its day are on the Measurements page's row of the same file: each travels there.
                Column(Modifier.weight(1f)) {
                    MorphText(androidx.compose.ui.text.AnnotatedString(label), "m:label", maxLines = 1, id = file.name)
                    MorphText(androidx.compose.ui.text.AnnotatedString(prettyDay(last.date)), "m:date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, id = file.name)
                }
                Column(horizontalAlignment = Alignment.End) {
                    MorphText(androidx.compose.ui.text.AnnotatedString("${shown(last, unit)} $suffix".trim()), "m:value", style = MaterialTheme.typography.titleMedium.numbers(), id = file.name)
                    changeText(file.name, last, before, unit)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** `+0.4`, `-1.2` or `no change`: the latest value against the one before it, or null when there is none to compare or it is not a number. */
private fun changeText(file: String, last: MeasurementEntity, before: MeasurementEntity?, unit: String): String? {
    val now = last.value.toDoubleOrNull() ?: return null
    val then = before?.value?.toDoubleOrNull() ?: return null
    val delta = now - then
    val text = if (file == "weight.csv") showWeight(Math.abs(delta), unit) else shortNumber(Math.round(Math.abs(delta) * 100) / 100.0)
    return when {
        text == "0" -> "no change"
        delta > 0 -> "+$text"
        else -> "-$text"
    }
}

/** A badge count and the latest earned; it opens the trophies. */
@Composable
private fun TrophiesCard(model: AppModel) {
    val consistency by model.consistency.collectAsState()
    val awards = consistency?.awards.orEmpty()
    val earned = awards.filter { it.earned != null }
    val latest = earned.maxByOrNull { it.earned!! }
    Section("Trophies", onClick = { model.open(Screen.Trophies) }) {
        Text(
            if (awards.isEmpty()) "Badges, the week streak and the program" else "${earned.size} of ${awards.size} badges earned",
            fontWeight = FontWeight.Bold,
        )
        // The latest badge is the first of the earned ones on the Trophies page: its name travels there.
        if (latest != null) Row {
            Text("Latest: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            MorphText(androidx.compose.ui.text.AnnotatedString(latest.title), "badge", color = MaterialTheme.colorScheme.onSurfaceVariant, id = latest.title)
        }
    }
}

/** The three latest milestones reached, each with its day, and the next one with how far along it is. It opens the trophies. */
@Composable
private fun MilestonesCard(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val consistency by model.consistency.collectAsState()
    val prs by model.prs.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val best = consistency?.streak?.best ?: 0
    val all = remember(workouts, prs, settings, bodyweights, best) {
        milestones(workouts.map { it.day }, workouts.map { volumeOf(it, model::loadOf) }, workouts.map { recordCount(it, prs[it.date].orEmpty()) }, best)
    }
    val reached = remember(all) { latestReached(all, 3) }
    val next = remember(all) { nextMilestone(all) }
    Section("Milestones", onClick = { model.open(Screen.Trophies) }) {
        for (m in reached) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Icon(CleanIcons.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(m.title(model::volume), Modifier.weight(1f), maxLines = 1)
            m.reachedOn?.let { Text(prettyDay(java.time.LocalDate.parse(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (reached.isEmpty() && next == null) EmptyPreview("Log your first workout and milestones start to fill in.") { PreviewSamples.Rows(48.dp, PreviewSamples.ROWS.take(2)) }
        else if (reached.isEmpty()) Text("Nothing reached yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (next != null) {
            Caption("Next", Modifier.padding(top = Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(next.title(model::volume), fontWeight = FontWeight.Bold)
                Text(progressText(model, next), style = MaterialTheme.typography.bodySmall.numbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { next.progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {},
            )
        }
    }
}

/** `42 of 50`, `640,000 kg of 1,000,000 kg`, `5 of 8 weeks`. */
private fun progressText(model: AppModel, m: Milestone): String = when (m.kind) {
    MilestoneKind.VOLUME -> "${model.volume(m.current)} of ${model.volume(m.target.toDouble())}"
    MilestoneKind.STREAK -> "${m.current.toLong()} of ${m.target} weeks"
    else -> "${m.current.toLong()} of ${m.target}"
}
