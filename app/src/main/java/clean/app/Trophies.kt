package clean.app

import clean.app.ui.theme.numbers
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.SmallShape
import clean.core.Award
import clean.core.Ladder
import clean.core.SlotState
import clean.core.WeekState
import clean.core.approxMonths
import clean.core.nextMilestones
import clean.core.spanText
import clean.core.weekStart
import java.time.LocalDate

/** The weeks shown as cells, two rows of 13. */
private const val CELLS = 26

/**
 * Consistency in one place: the streak and its shield, the last half year week by week, the badges
 * and how far off the next ones are, and the habits behind them. Everything on it leads to the
 * week, workout or page that explains it.
 */
@Composable
fun TrophiesScreen(model: AppModel) {
    val loaded by model.consistency.collectAsState()
    val standing by model.programStanding.collectAsState()
    val today = LocalDate.now()
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Trophies", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            val c = loaded
            if (c == null) {
                item { Section("") { Text("Working it out…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                return@LazyColumn
            }
            val streak = c.streak
            val thisWeek = c.now
            val program = standing
            // With a program the streak that leads is the program's, and the weekly target's goes second.
            val lead = program?.streak ?: streak
            item {
                Section("Streak", Modifier.settleIn(0)) {
                    MorphText(
                        androidx.compose.ui.text.AnnotatedString(if (program != null) spanText(lead.length) else plural(lead.length, "week") + if (lead.length >= 4) " · about ${plural(approxMonths(lead.length), "month")}" else ""),
                        "number", onPage = true, style = MaterialTheme.typography.headlineMedium.numbers(), fontWeight = FontWeight.SemiBold,
                    )
                    if (program != null) Text("on program · ${plural(lead.length, "week")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Icon(CleanIcons.Shield, null, Modifier.size(18.dp), tint = if (lead.shield) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (lead.shield) "Shield ready: one missed week will be forgiven" else "Next shield in ${plural(lead.weeksToShield, "week")}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val end = lead.bestEnd
                    if (end != null) {
                        LabelValue(
                            "Best streak",
                            plural(lead.best, "week") + if (lead.length == lead.best) " · now" else " · ended ${prettyDay(minOf(end.plusDays(6), today))}",
                        ) { model.open(Screen.Week(end.toString())) }
                    }
                    if (program != null) LabelValue("Weekly target streak", plural(streak.length, "week")) { model.open(Screen.Settings) }
                    LabelValue("Weekly target", "${thisWeek.target} a week") { model.open(Screen.Settings) }
                    LabelValue("Program", if (program != null) "Edit" else "Follow one") { model.open(Screen.Program) }
                }
            }
            if (program != null) item {
                Section("This week on program", Modifier.settleIn(1)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        for (slot in program.now?.slots.orEmpty()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Text("MTWTFSS"[slot.slot].toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SlotCell(slot.state, weekdayName(slot.slot), Modifier.fillMaxWidth()) {
                                    // A matched day opens its workout; any other the program.
                                    val workout = slot.workout
                                    if (workout != null) model.open(Screen.WorkoutDetail(workout)) else model.open(Screen.Program)
                                }
                                Text(slot.day?.label ?: "Rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text(
                        "Green: done on its day. Amber: done on another day. Red: not done. Grey: rest, or still to come. A dot: trained on a rest day.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("Last $CELLS weeks", Modifier.settleIn(2)) {
                    val byMonday = if (program != null) {
                        program.weeks.withIndex().associate { (i, week) ->
                            val training = week.slots.filter { it.day != null }
                            week.monday to Cell(week.met, training.count { it.workout != null }.toFloat() / training.size.coerceAtLeast(1), program.streak.states[i])
                        }
                    } else {
                        c.weeks.withIndex().associate { (i, week) -> week.weekStart to Cell(week.met, week.count.toFloat() / week.target, streak.states[i]) }
                    }
                    val mondays = (0 until CELLS).map { weekStart(today).minusWeeks((CELLS - 1 - it).toLong()) }
                    for (row in mondays.chunked(CELLS / 2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            for (monday in row) WeekCell(byMonday[monday], prettyDay(monday), Modifier.weight(1f)) { model.open(Screen.Week(monday.toString())) }
                        }
                    }
                    Text(
                        (if (program != null) "Star: week on program." else "Star: target hit.") + " Ring: missed, filled as far as it got. Shield: forgiven. Outline: this week.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("Badges", Modifier.settleIn(3)) {
                    val awards = c.awards + program?.awards.orEmpty()
                    val earned = awards.filter { it.earned != null }.sortedByDescending { it.earned }
                    if (earned.isEmpty()) Text("None yet. A month of weeks that meet the target earns the first, and so do ten workouts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fun open(award: Award) {
                        val week = award.weekOf
                        val workout = award.workoutDate
                        when {
                            week != null -> model.open(Screen.Week(week.toString()))
                            workout != null -> model.open(Screen.WorkoutDetail(workout))
                            // Out of reach still: the ladder's own page.
                            award.ladder == Ladder.STREAK || award.ladder == Ladder.PROGRAM -> model.open(Screen.Week(thisWeek.weekStart.toString()))
                            else -> model.open(Screen.Trends)
                        }
                    }
                    for (a in earned) TrophyRow(CleanIcons.Star, a.title, prettyDay(a.earned!!), true, morph = true) { open(a) }
                    val next = nextMilestones(awards, streak.length, c.total, program?.streak?.length ?: 0)
                    for (n in next) {
                        val unit = if (n.award.ladder == Ladder.WORKOUTS) "workout" else "week"
                        Column(Modifier.fillMaxWidth().clickable { open(n.award) }, verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            Text("${n.award.title} · ${plural(n.remaining, unit)} to go", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), SmallShape)) {
                                Box(Modifier.fillMaxWidth(n.fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary, SmallShape))
                            }
                        }
                    }
                    // The rest of each ladder, shown dimmed rather than hidden.
                    for (a in awards.filter { it.earned == null && next.none { n -> n.award.id == it.id } }) TrophyRow(CleanIcons.Star, a.title, "", false) { open(a) }
                }
            }
            item {
                Section("Habits", Modifier.settleIn(4)) {
                    LabelValue("Weeks trained in a row", c.weeksTrained.toString()) { model.open(Screen.Calendar()) }
                    c.longestBreak?.let { gap ->
                        LabelValue("Longest break", plural(gap.days, "day")) { model.open(Screen.WorkoutDetail(gap.to)) }
                        Text("${prettyDay(gap.from)} to ${prettyDay(gap.to)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Caption("Usual days")
                    val most = c.usualDays.max().coerceAtLeast(1)
                    Row(
                        Modifier.fillMaxWidth().clickable { model.open(Screen.Calendar()) }
                            .semantics { contentDescription = "Usual days: " + c.usualDays.withIndex().joinToString(", ") { (i, n) -> "${weekdayName(i)} $n" } },
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        for ((i, n) in c.usualDays.withIndex()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    val color = MaterialTheme.colorScheme.primary
                                    Box(Modifier.fillMaxWidth().height((56.dp * n / most).coerceAtLeast(2.dp)).background(if (n == most) color else color.copy(alpha = 0.45f), SmallShape))
                                }
                                Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A badge: a star, its name, and the day it was earned. Not yet earned, it is dimmed and has no day. */
@Composable
private fun TrophyRow(icon: ImageVector, title: String, day: String, earned: Boolean, morph: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        val ink = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        if (morph) MorphText(androidx.compose.ui.text.AnnotatedString(title), "badge", Modifier.weight(1f), onPage = true, color = ink, id = title) else Text(title, Modifier.weight(1f), color = ink)
        if (day.isNotEmpty()) Text(day, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a cell of the grid needs of a week: met, how far it got (0 to 1) and how the streak read it. */
private class Cell(val met: Boolean, val fraction: Float, val state: WeekState)

/** One week of the grid: a star when met, a ring filled as far as it got when not, a shield when forgiven. A gap where there was no training yet. */
@Composable
private fun WeekCell(cell: Cell?, week: String, modifier: Modifier, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val current = cell?.state == WeekState.IN_PROGRESS
    val box = modifier.aspectRatio(1f).clip(SmallShape)
        .then(if (current) Modifier.border(1.5.dp, accent, SmallShape) else Modifier)
    if (cell == null) {
        Box(box, contentAlignment = Alignment.Center) { Box(Modifier.size(4.dp).background(muted.copy(alpha = 0.3f), CircleShape)) }
        return
    }
    val state = when {
        cell.met -> "target hit"
        cell.state == WeekState.FORGIVEN -> "forgiven"
        current -> "this week, ${Math.round(cell.fraction * 100)} percent of the target"
        else -> "missed, ${Math.round(cell.fraction * 100)} percent of the target"
    }
    Box(box.clickable(onClick = onClick).semantics { contentDescription = "Week of $week: $state" }.padding(Space.xs), contentAlignment = Alignment.Center) {
        when {
            cell.met -> Icon(CleanIcons.Star, null, Modifier.fillMaxSize(), tint = accent)
            cell.state == WeekState.FORGIVEN -> Icon(CleanIcons.Shield, null, Modifier.fillMaxSize(), tint = muted)
            else -> ProgressRing(cell.fraction, Modifier.fillMaxSize(), if (current) accent else muted.copy(alpha = 0.7f), 2.5.dp)
        }
    }
}

/** One day of the week on program: green done on its day, amber on another, red not done, quiet for rest and what is still to come. */
@Composable
private fun SlotCell(state: SlotState, day: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalCleanColors.current
    val quiet = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val color = when (state) {
        SlotState.HIT -> colors.done
        SlotState.MOVED -> colors.warmup
        SlotState.MISSED -> colors.danger
        SlotState.REST_KEPT, SlotState.REST_TRAINED, SlotState.UPCOMING -> quiet
    }
    val said = when (state) {
        SlotState.HIT -> "done on its day"
        SlotState.MOVED -> "done on another day"
        SlotState.MISSED -> "not done"
        SlotState.REST_KEPT -> "rest"
        SlotState.REST_TRAINED -> "rest day, trained"
        SlotState.UPCOMING -> "still to come"
    }
    Box(modifier.aspectRatio(1f).clip(SmallShape).background(color).clickable(onClick = onClick).semantics { contentDescription = "$day: $said" }, contentAlignment = Alignment.Center) {
        if (state == SlotState.REST_TRAINED) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
    }
}
