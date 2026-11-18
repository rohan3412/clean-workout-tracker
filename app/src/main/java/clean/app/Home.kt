package clean.app

import clean.app.ui.theme.cardShape
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import clean.core.Workout
import clean.core.recordCount
import clean.core.shortNumber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The date as Home's title and rows show it: the phone's own order, short weekday, day and short month ("Tue 17 Nov"). */
internal fun shortDay(day: LocalDate): String {
    val locale = Locale.getDefault()
    return day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale))
}

/** `1 h 04`, `38 min`: a workout's length as a grey line says it. */
internal fun hoursMinutes(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}

/**
 * Home (F1), fixed: the header, the Up next card (the one raised card), the Weight row, Activity and the last workout, the last three on
 * the page itself. A profile with no workouts keeps its first-run card.
 */
@Composable
fun HomeTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val templates by model.templates.collectAsState()
    val state = model.tabLists.getValue(Tab.Home)
    BarEnd(Tab.Home, state)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state) {
            item { HomeHeader(model) }
            if (workouts.isEmpty()) item {
                // Nothing to show yet: the two ways to start, in place of cards full of zeros.
                Section("", card = true) {
                    EmptyPreview("Log your first workout, or bring your history in from another app, and your progress fills in here.") { PreviewSamples.Line(120.dp) }
                    MainButton("Start a workout", { model.startWorkout() }, Modifier.fillMaxWidth())
                    QuietButton("Import your history", { model.settingsPage = PAGE_DATA; model.importNow = true; model.open(Screen.Settings) }, Modifier.fillMaxWidth())
                }
            } else {
                item(key = "upnext") { CompositionLocalProvider(LocalCardKey provides "home:today") { TodayCard(model, templates, workouts) } }
                item(key = "weight") { WeightRow(model) }
                item(key = "activity") { ActivityRow(model) }
                workouts.lastOrNull()?.let { last -> item(key = "last") { LastWorkoutRow(model, last) } }
            }
            item { BarSpace() }
        }
    }
}

/** "Today" over the date as one short line, and the two round glass buttons: the profile's initial and Settings. */
@Composable
internal fun HomeHeader(model: AppModel) {
    val date = remember { LocalDate.now() }
    val title = remember(date) { shortDay(date) }
    Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Today", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            // B1: one line, letter spacing pulled in about 2 %, line height 110 %.
            val big = MaterialTheme.typography.headlineLarge
            Text(
                title, Modifier.semantics { heading() }, maxLines = 1, fontWeight = FontWeight.Bold,
                style = big.copy(letterSpacing = (-0.02).em, lineHeight = big.fontSize * 1.1f),
            )
        }
        // The profile (its initial) and Settings, two round glass buttons at the top right.
        val initial = model.profile.collectAsState().value?.name?.trim()?.firstOrNull()?.uppercase() ?: "?"
        GlassButton("Profile", { model.open(Screen.Profile) }, Modifier.padding(start = Space.xs)) { Text(initial, fontWeight = FontWeight.SemiBold) }
        GlassButton("Settings", { model.open(Screen.Settings) }) { Icon(CleanIcons.Settings, null, Modifier.size(20.dp)) }
    }
}

/** A small grey uppercase heading on the page, with something at its right. */
@Composable
internal fun PageHeading(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(Modifier.weight(1f).semantics { heading() }) { Caption(text) }
        trailing()
    }
}

/** The round "+" of a row: a 32 dp disc in a 48 dp touch area. */
@Composable
internal fun RoundAdd(label: String, onClick: () -> Unit) {
    Box(Modifier.minTouch().clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(CleanIcons.Add, label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The latest weight large with its unit, how far it moved in 30 days, and a "+" for the ruler; the row opens the weight page. Not a card. */
@Composable
private fun WeightRow(model: AppModel) {
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    val today = LocalDate.now()
    val weights = weighIns(model)
    val recent = weights.filter { it.first >= today.minusDays(30) }
    val change = if (recent.size >= 2) recent.last().second - recent.first().second else null
    val current = weights.lastOrNull()?.second
    val moved = change?.let { showWeight(it, unit) }
    Row(
        Modifier.fillMaxWidth().clickable { if (current == null) model.logWeight() else model.open(Screen.MeasurementDetail("weight.csv")) }
            .padding(start = Space.l, end = Space.s, top = Space.l, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Caption("Weight")
            if (current == null) Text("Log your weight", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            else {
                Text(
                    androidx.compose.ui.text.AnnotatedString("${showWeight(current, unit)} $unit"),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                val line = when {
                    moved == null -> null
                    moved == "0" -> "no change in 30 days"
                    else -> "${if (change!! > 0) "+" else ""}$moved $unit in 30 days"
                }
                if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RoundAdd("Log body weight") { model.logWeight() }
    }
}

/** "Activity", the week and the streak at its right (the streak opens Trophies), and the calendar of the last 17 weeks, which opens the full calendar. */
@Composable
private fun ActivityRow(model: AppModel) {
    val consistency by model.consistency.collectAsState()
    val week = consistency?.now
    val streak = consistency?.streak?.length ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        PageHeading("Activity") {
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            if (week != null) Text("${week.count} of ${week.target} this week", Modifier.clickable { model.open(Screen.Calendar()) }, style = MaterialTheme.typography.bodySmall, color = quiet)
            if (streak > 0) {
                Text("·", style = MaterialTheme.typography.bodySmall, color = quiet)
                Text(
                    "$streak-week streak", Modifier.clickable(onClickLabel = "Open trophies") { model.open(Screen.Trophies) },
                    style = MaterialTheme.typography.bodySmall, color = quiet,
                )
            }
        }
        HomeCalendar(model)
    }
}

/** The last workout as a plain row: its name, then day, length and PRs in grey, and a chevron. It opens the workout. */
@Composable
private fun LastWorkoutRow(model: AppModel, workout: Workout) {
    val prs by model.prs.collectAsState()
    val count = recordCount(workout, prs[workout.date].orEmpty())
    val day = parseDate(workout.date)?.toLocalDate()
    val line = listOfNotNull(
        day?.let { shortDay(it) }, hoursMinutes(workout.durationSeconds).takeIf { workout.durationSeconds > 0 },
        if (count > 0) "${shortNumber(count)} PR${if (count == 1.0) "" else "s"}" else null,
    ).joinToString(" · ")
    val hold = workoutHold(model, workout)
    Row(
        Modifier.fillMaxWidth().clickable { model.open(Screen.WorkoutDetail(workout.date)) }.minTouch().holdMenu(cardShape(), hold).padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Column(Modifier.weight(1f)) {
            Caption("Last workout")
            Text(workout.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
