package clean.app

import clean.core.time.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import clean.core.shortNumber
import clean.core.weeklyTotals
import kotlinx.datetime.LocalDate

/**
 * Training week by week: how many workouts, how long, how much volume and how many working sets,
 * each as a line with a trend through it. A point opens that week's report.
 */
@Composable
fun TrendsScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    // settings and bodyweights are keys only so the volume is worked out again when they change.
    val weeks = remember(workouts, settings, bodyweights) { weeklyTotals(workouts, model::loadOf) }
    var range by model.remembered("trendRange", "365")
    val days = range.toLongOrNull() ?: 365L
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Trends", model::back) }) { padding ->
        // Opened by a tile with a chart (Volume), the page starts at that chart, so the tile's line has its own to travel to.
        val from = LocalPageCard.current
        val lines = LocalChartLines.current
        val focus = remember { TREND_TITLES.indexOfFirst { lines?.containsKey("$from/chart:$it/card") == true } }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), rememberLazyListState(initialFirstVisibleItemIndex = if (focus > 0) focus + 1 else 0)) {
            item {
                PageSection("", Modifier.settleIn(0)) {
                    Segmented(listOf(90L, 365L, 0L), days, { if (it == 90L) "3M" else if (it == 365L) "1Y" else "All" }) { range = it.toString() }
                    Text("One point a week, Monday to Sunday. A week with no workout counts as nothing, so a break shows as a dip. The week still running is dashed and left out of the trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val shown = if (days == 0L) weeks else weeks.filter { it.monday >= LocalDate.now().minusDays(days) }
            // The week that is still running is drawn as "so far" and kept out of the trend and the averages (round 23).
            val running = shown.lastOrNull()?.let { isRunningWeek(it.monday, LocalDate.now()) } == true
            val openWeek = { day: LocalDate -> model.open(Screen.Week(day.toString())) }
            val charts = listOf(
                Triple("Workouts a week", "", shown.map { it.monday to it.workouts.toDouble() }),
                Triple("Hours a week", "h", shown.map { it.monday to roundHalfUp(it.seconds / 360.0) / 10.0 }),
                Triple("Volume a week", "kg", shown.map { it.monday to roundHalfUp(it.volume).toDouble() }),
                Triple("Working sets a week", "sets", shown.map { it.monday to it.sets.toDouble() }),
            )
            for ((title, unit, points) in charts) item(key = title) {
                PageSection(title, Modifier.settleIn(1 + TREND_TITLES.indexOf(title))) {
                    if (weeks.size < 3) EmptyPreview("Log ${3 - weeks.size} more ${if (weeks.size == 2) "week" else "weeks"} of workouts to see this.") { PreviewSamples.Line() }
                    else if (points.size < 3) Text("Not enough weeks in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        LineChart(points, unit, trend = true, title = title, onTap = openWeek, morph = "chart:$title", selector = RangeSelector(days, { range = it.toString() }), partialLast = running, trendUnit = TREND_WORDS[title])
                        // The plain average, and the last four weeks against it.
                        val whole = if (running) points.dropLast(1) else points
                        val average = whole.map { it.second }.average()
                        val recent = whole.takeLast(4).map { it.second }.average()
                        LabelValue("Average", "${shortNumber(roundHalfUp(average * 10) / 10.0)} $unit".trim())
                        LabelValue("Last 4 weeks", "${shortNumber(roundHalfUp(recent * 10) / 10.0)} $unit".trim())
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

/** The four charts of Trends, in order (each one's item comes after the range choice). */
private val TREND_TITLES = listOf("Workouts a week", "Hours a week", "Volume a week", "Working sets a week")

/** What each chart counts, in words, for the trend line under it ("−0.03 workouts a week, per month"). */
private val TREND_WORDS = mapOf("Workouts a week" to "workouts a week", "Hours a week" to "h a week", "Volume a week" to "kg a week", "Working sets a week" to "sets a week")

/** Whether the week starting [monday] is the one [today] is in: a week still running, so its totals are not a whole week's. */
fun isRunningWeek(monday: LocalDate, today: LocalDate): Boolean = monday == clean.core.weekStart(today)
