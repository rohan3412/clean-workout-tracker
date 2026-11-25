package clean.app

import clean.core.time.*
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import kotlinx.datetime.LocalDate

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
                item { PageSection("") { Text("Working it out…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                return@LazyColumn
            }
            val streak = c.streak
            val thisWeek = c.now
            val program = standing
            // With a program the streak that leads is the program's, and the weekly target's goes second.
            val lead = program?.streak ?: streak
            item {
                PageSection("Streak", Modifier.settleIn(0)) {
                    MorphText(
                        androidx.compose.ui.text.AnnotatedString(if (program != null) spanText(lead.length) else plural(lead.length, "week") + if (lead.length >= 4) " · about ${plural(approxMonths(lead.length), "month")}" else ""),
                        "number", onPage = true, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    )
                    if (program != null) Text("on program · ${plural(lead.length, "week")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Icon(CleanIcons.ShieldLine, null, Modifier.size(18.dp), tint = if (lead.shield) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                PageSection("This week on program", Modifier.settleIn(1)) {
                    // Round 19: holding the week and sliding along it names each day's state.
                    val slots = program.now?.slots.orEmpty()
                    val spots = remember { CellSpots<Int>() }
                    HoldOverCells(spots, "slot peek", { index ->
                        val slot = slots.first { it.slot == index }
                        PeekHead(weekdayName(index))
                        PeekLine(slot.day?.label ?: "Rest", bold = true)
                        PeekLine(slotWords(slot.state).replaceFirstChar { it.uppercase() })
                    }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        for (slot in slots) {
                            Column(Modifier.weight(1f).then(spots.mark(slot.slot)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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
                    }
                    Text(
                        "Green: done on its day. Amber: done on another day. Red: not done. Grey: rest, or still to come. A dot: trained on a rest day.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PageSection("Last $CELLS weeks", Modifier.settleIn(2)) {
                    val byMonday = if (program != null) {
                        program.weeks.withIndex().associate { (i, week) ->
                            val training = week.slots.filter { it.day != null }
                            val done = training.count { it.workout != null }
                            week.monday to Cell(week.met, done.toFloat() / training.size.coerceAtLeast(1), program.streak.states[i], done, training.size, true)
                        }
                    } else {
                        c.weeks.withIndex().associate { (i, week) -> week.weekStart to Cell(week.met, week.count.toFloat() / week.target, streak.states[i], week.count, week.target, false) }
                    }
                    val mondays = (0 until CELLS).map { weekStart(today).minusWeeks((CELLS - 1 - it).toLong()) }
                    // Round 19: holding the grid and sliding over it shows each week's count against its target.
                    val spots = remember { CellSpots<LocalDate>() }
                    HoldOverCells(spots, "week peek", { monday ->
                        PeekHead("Week of ${prettyDay(monday)}")
                        val cell = byMonday[monday]
                        if (cell == null) PeekLine("No training yet")
                        else {
                            PeekLine(if (cell.program) "${cell.count} of ${cell.target} program days" else "${cell.count} of ${cell.target} workouts", bold = true)
                            PeekLine(cell.stateWord().replaceFirstChar { it.uppercase() })
                        }
                    }) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            for (row in mondays.chunked(CELLS / 2)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    for (monday in row) WeekCell(byMonday[monday], prettyDay(monday), Modifier.weight(1f).then(spots.mark(monday)), now = monday == weekStart(today)) { model.open(Screen.Week(monday.toString())) }
                                }
                            }
                        }
                    }
                    Text(
                        (if (program != null) "Star: week on program." else "Star: target hit.") + " Ring: missed, filled as far as it got. Shield: forgiven. Outline: this week.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PageSection("Badges", Modifier.settleIn(3)) {
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
                    // Round 24: every badge not yet earned is dimmed and shows how far off it is; the next of each ladder first.
                    val unearned = awards.filter { it.earned == null }.sortedBy { a -> next.indexOfFirst { it.award.id == a.id }.let { if (it < 0) Int.MAX_VALUE else it } }
                    for (a in unearned) {
                        val have = when (a.ladder) { Ladder.STREAK -> streak.length; Ladder.WORKOUTS -> c.total; Ladder.PROGRAM -> program?.streak?.length ?: 0 }
                        UnearnedRow(a.title, (a.goal - have).coerceAtLeast(0), if (a.ladder == Ladder.WORKOUTS) "workout" else "week", (have.toFloat() / a.goal).coerceIn(0f, 1f)) { open(a) }
                    }
                }
            }
            item {
                PageSection("Habits", Modifier.settleIn(4)) {
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

/** A badge not yet earned: a dimmed star and name, how many are still to go and a thin bar of how far along it is. A tap opens the page its ladder leads to. */
@Composable
private fun UnearnedRow(title: String, remaining: Int, unit: String, fraction: Float, onClick: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xs).semantics(mergeDescendants = true) { contentDescription = "$title, not earned yet, ${plural(remaining, unit)} to go" },
        horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CleanIcons.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row {
                Text(title, Modifier.weight(1f), color = dim)
                Text("${plural(remaining, unit)} to go", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ThinBar(fraction, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), 4.dp)
        }
    }
}

/** What a cell of the grid needs of a week: met, how far it got (0 to 1) and how the streak read it. */
private class Cell(val met: Boolean, val fraction: Float, val state: WeekState, val count: Int, val target: Int, val program: Boolean) {
    /** The week in words, for the card and a screen reader. */
    fun stateWord(): String = when {
        met -> "target hit"
        state == WeekState.FORGIVEN -> "forgiven"
        state == WeekState.IN_PROGRESS -> "this week, ${roundHalfUp(fraction * 100)} percent of the target"
        else -> "missed, ${roundHalfUp(fraction * 100)} percent of the target"
    }
}

/** Where the cells of a grid are in the window, so a hold can tell which one the finger is over. */
private class CellSpots<K : Any> {
    private val rects = HashMap<K, androidx.compose.ui.geometry.Rect>()
    fun mark(key: K): Modifier = Modifier.onGloballyPositioned { rects[key] = it.boundsInWindow() }
    fun at(window: Offset): K? = rects.entries.firstOrNull { window in it.value }?.key
}

/** Holding [content] and sliding over its cells shows [card] for the cell under the finger (one tick as it moves to another); release dismisses. A tap on a cell is unchanged. */
@Composable
private fun <K : Any> HoldOverCells(spots: CellSpots<K>, tag: String, card: @Composable ColumnScope.(K) -> Unit, content: @Composable () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var held by remember { mutableStateOf<Pair<K, Offset>?>(null) }
    val tick = rememberTick()
    Box(
        Modifier.onGloballyPositioned { origin = it.positionInWindow() }.holdAndSlide(
            key = spots,
            onHold = { at -> spots.at(at + origin)?.let { tick(); held = it to at } },
            onMove = { at -> val k = spots.at(at + origin); if (k != null) { if (k != held?.first) tick(); held = k to at } else held = held?.let { it.first to at } },
            onEnd = { held = null },
        ),
    ) {
        content()
        held?.let { (k, at) -> PeekPopup(at, tag) { card(k) } }
    }
}

/** One week of the grid: a star when met, a ring filled as far as it got when not, a shield when forgiven. A gap where there was no training yet. */
@Composable
private fun WeekCell(cell: Cell?, week: String, modifier: Modifier, now: Boolean = false, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val current = cell?.state == WeekState.IN_PROGRESS
    val box = modifier.aspectRatio(1f).clip(SmallShape)
        .then(if (current) Modifier.border(1.5.dp, accent, SmallShape) else Modifier)
    if (cell == null) {
        Box(box, contentAlignment = Alignment.Center) { Box(Modifier.size(4.dp).background(muted.copy(alpha = 0.3f), CircleShape)) }
        return
    }
    val state = cell.stateWord()
    Box(box.clickable(onClick = onClick).semantics { contentDescription = "Week of $week: $state" }.padding(Space.xs), contentAlignment = Alignment.Center) {
        // This week's ring is what Home's This week tile's ring travels to (and back): the one ring, as the body figure is one figure.
        Box(Modifier.fillMaxSize().then(if (now) Modifier.morphs("ring") else Modifier), contentAlignment = Alignment.Center) {
            when {
                cell.met -> Icon(CleanIcons.Star, null, Modifier.fillMaxSize(), tint = accent)
                cell.state == WeekState.FORGIVEN -> Icon(CleanIcons.ShieldLine, null, Modifier.fillMaxSize(), tint = muted)
                else -> ProgressRing(cell.fraction, Modifier.fillMaxSize(), if (current) accent else muted.copy(alpha = 0.7f), 2.5.dp)
            }
        }
    }
}

/** One day of the week on program: the accent on its day, amber on another, red not done, quiet for rest and what is still to come. */
@Composable
private fun SlotCell(state: SlotState, day: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalCleanColors.current
    val quiet = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val color = when (state) {
        SlotState.HIT -> goalColor()
        SlotState.MOVED -> colors.warmup
        SlotState.MISSED -> colors.danger
        SlotState.REST_KEPT, SlotState.REST_TRAINED, SlotState.UPCOMING -> quiet
    }
    val said = slotWords(state)
    Box(modifier.aspectRatio(1f).clip(SmallShape).background(color).clickable(onClick = onClick).semantics { contentDescription = "$day: $said" }, contentAlignment = Alignment.Center) {
        if (state == SlotState.REST_TRAINED) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
    }
}

/** A program day's state in words, for the card and a screen reader. */
private fun slotWords(state: SlotState): String = when (state) {
    SlotState.HIT -> "done on its day"
    SlotState.MOVED -> "done on another day"
    SlotState.MISSED -> "not done"
    SlotState.REST_KEPT -> "rest"
    SlotState.REST_TRAINED -> "rest day, trained"
    SlotState.UPCOMING -> "still to come"
}
