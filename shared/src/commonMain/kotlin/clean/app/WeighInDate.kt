package clean.app

import clean.core.time.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.cardShape
import clean.core.monthGrid
import clean.core.recentDays
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CHIP_DAY= clean.core.time.DatePattern("d MMM")

/** A day as the chips name it: Today, Yesterday, "Mon 16" within the last week, else "3 Mar". */
fun weighInDayLabel(today: LocalDate, day: LocalDate): String = recentDays(today).firstOrNull { it.day == day }?.label ?: day.format(CHIP_DAY)

/**
 * When a weigh-in is dated (replacing the phone's date and time dialogs): the last seven days as chips (Today, Yesterday, then "Mon 16") and an
 * Earlier chip that opens a month to pick from, and under them the time as an hour and a minute that scroll. [onType] asks the sheet to type the
 * hour (`false`) or the minute (`true`) on its number pad. Days after today cannot be chosen.
 */
@Composable
fun WhenCard(
    today: LocalDate, day: LocalDate, hour: Int, minute: Int,
    onDay: (LocalDate) -> Unit, onHour: (Int) -> Unit, onMinute: (Int) -> Unit, onType: (Boolean) -> Unit,
    startOpen: Boolean = false, modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    val recent = remember(today) { recentDays(today) }
    var earlier by remember { mutableStateOf(startOpen) }
    var month by remember { mutableStateOf(YearMonth.from(day)) }
    val away = recent.none { it.day == day }
    Column(modifier.fillMaxWidth().clip(cardShape()).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            // The days scroll; Earlier stays in view at the end.
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (chip in recent) CleanChip(!earlier && chip.day == day, { tick(); earlier = false; onDay(chip.day) }, { Text(chip.label) })
            }
            CleanChip(earlier || away, { tick(); earlier = !earlier; month = YearMonth.from(day) }, { Text(if (away && !earlier) day.format(CHIP_DAY) else "Earlier") })
        }
        if (earlier) MonthPicker(today, day, month, { month = it }) { tick(); earlier = false; onDay(it) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Wheel(hour, 24, "Hour", { onHour(it) }) { onType(false) }
            Text(":", Modifier.padding(horizontal = Space.xs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Wheel(minute, 60, "Minute", { onMinute(it) }) { onType(true) }
        }
    }
}

/** A month to pick a day from: arrows for the month either side (none past this month), weekdays from Monday, days after [today] dimmed and not tappable. */
@Composable
private fun MonthPicker(today: LocalDate, chosen: LocalDate, month: YearMonth, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    val last = YearMonth.from(today)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Earlier month") { onMonth(month.minusMonths(1)) }, contentAlignment = Alignment.Center) { Icon(CleanIcons.ChevronLeft, "Earlier month", Modifier.size(20.dp)) }
            Text(
                month.month.englishName(true) + " " + month.year, Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            val later = month < last
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(later, onClickLabel = "Later month") { onMonth(month.plusMonths(1)) }, contentAlignment = Alignment.Center) {
                Icon(CleanIcons.ChevronRight, "Later month", Modifier.size(20.dp), tint = if (later) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (d in listOf("M", "T", "W", "T", "F", "S", "S")) Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (week in monthGrid(month)) Row(Modifier.fillMaxWidth()) {
            for (d in week) {
                if (d == null) { Box(Modifier.weight(1f).height(44.dp)); continue }
                val future = d > today
                val on = d == chosen
                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) { Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(!future, onClickLabel = "Pick") { onDay(d) }
                        .semantics { contentDescription = d.dayOfMonth.toString() + " " + d.month.englishName(true); if (on) stateDescription = "selected" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (on || d == today) FontWeight.Bold else FontWeight.Normal,
                        color = when { on -> MaterialTheme.colorScheme.onPrimary; future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f); d == today -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface },
                    )
                } }
            }
        }
    }
}

/**
 * A number that scrolls: the value with its neighbours above and below, a drag up or down moving it one for every 32 dp (a quick flick carries on a few
 * more, ticking), wrapping round at [count]. A tap on the value asks to type it. For a screen reader it has an increase and a decrease.
 */
@Composable
private fun Wheel(value: Int, count: Int, name: String, onChange: (Int) -> Unit, onType: () -> Unit) {
    val tick = rememberTick()
    val step = with(LocalDensity.current) { 32.dp.toPx() }
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var carried by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    fun move(by: Int) { change((latest + by).mod(count)); tick() }
    Column(
        Modifier.width(56.dp).clip(cardShape()).draggable(
            rememberDraggableState { delta ->
                carried += delta
                // Dragging down brings the earlier numbers to the middle.
                while (abs(carried) >= step) { val by = if (carried > 0) -1 else 1; carried -= if (carried > 0) step else -step; move(by) }
            },
            Orientation.Vertical,
            onDragStopped = { velocity ->
                carried = 0f
                val extra = (abs(velocity) / (step * 5)).toInt().coerceAtMost(12)
                val by = if (velocity > 0) -1 else 1
                scope.launch { for (i in 0 until extra) { delay(30L + i * 12L); move(by) } }
            },
        ).semantics(mergeDescendants = true) {
            contentDescription = name
            stateDescription = value.toString().padStart(2, '0')
            customActions = listOf(CustomAccessibilityAction("Increase $name") { move(1); true }, CustomAccessibilityAction("Decrease $name") { move(-1); true })
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (offset in -1..1) {
            val shown = (value + offset).mod(count)
            Box(Modifier.height(32.dp).fillMaxWidth().then(if (offset == 0) Modifier.clickable(onClickLabel = "Type the ${name.lowercase()}", onClick = onType) else Modifier), contentAlignment = Alignment.Center) {
                Text(
                    shown.toString().padStart(2, '0'), style = if (offset == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (offset == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (offset == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}
