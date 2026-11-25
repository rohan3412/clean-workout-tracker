package clean.app

import clean.core.time.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import clean.app.platform.LocalPlatform
import clean.core.YearReview
import clean.core.progressOf
import clean.core.shortNumber
import clean.core.yearInReviewFor
import clean.core.yearReview
import clean.core.yearReviewSeason
import clean.core.yearsWithWorkouts
import clean.core.volumeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Month

/*
 * Year in review (N14, round 7): one calm page of typography and numbers for a year of training: the workouts, time, volume and sets,
 * the lifts whose estimated 1RM rose most, the best month, the longest run of weeks with a workout, the PRs and the most trained
 * exercise. Worked out in core (`yearReview`) from the stats workouts. Reached from Progress's list of statistics for any year that has
 * a workout, and, from 1 December to 31 January, by a quiet line on Profile.
 */

/** Profile's quiet "Your 2026 ›" line, there only from 1 December to 31 January and only when that year has a workout. */
@Composable
fun YearInReviewLine(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val today = model.today
    val year = yearInReviewFor(today)
    val has = remember(workouts, year) { year in yearsWithWorkouts(workouts) }
    if (!yearReviewSeason(today) || !has) return
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open your $year in review") { model.open(Screen.YearReview(year)) }.minTouch().padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Your $year", Modifier.weight(1f), color = grey)
        Text("›", color = grey)
    }
}

@Composable
fun YearReviewScreen(model: AppModel, year: Int) {
    val workouts by model.workouts.collectAsState()
    val prs by model.prs.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val years = remember(workouts) { yearsWithWorkouts(workouts) }
    var shown by remember(year) { mutableStateOf(year) }
    val review by produceState<YearReview?>(null, workouts, prs, settings, bodyweights, rule, shown) {
        value = withContext(Dispatchers.Default) {
            yearReview(
                workouts, shown, { volumeOf(it, model::loadOf) },
                { exercise, sessions -> progressOf(sessions, rule, model.loadTypeOf(exercise), bodyweights::at) }, prs,
            )
        }
    }
    val platform = LocalPlatform.current
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar("Year in review", model::back) {
            // The year as plain text, to the phone's own share sheet (the same path as a workout's text).
            review?.let { r -> TextButton(onClick = { platform.shareText(yearSummaryText(r, model::volume) { model.unitOf(it) }) }) { Text("Share") } }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxxl)) {
            if (years.size > 1) item(key = "years") {
                LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    items(years, key = { it }) { y -> CleanChip(selected = y == shown, onClick = { shown = y }, label = { Text(y.toString()) }) }
                }
            }
            val r = review
            if (r == null) {
                item { Text("No workouts in $shown.", Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }
            item(key = "head-$shown") {
                val big = MaterialTheme.typography.displaySmall
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.l)) {
                    Text(r.year.toString(), Modifier.semantics { heading() }, style = big, fontWeight = FontWeight.Bold)
                    Text("in training", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "numbers-$shown") {
                // The four figures count up one after another, as on the workout summary.
                val unit = model.profile.collectAsState().value?.unit ?: "kg"
                Column(Modifier.padding(start = Space.l, end = Space.l, bottom = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        Big("Workouts", countUp(true, 0, r.totals.workouts.toDouble()) { roundHalfUp(it).toString() }, Modifier.weight(1f))
                        Big("Time trained", countUp(true, 1, r.totals.seconds.toDouble()) { duration(roundHalfUp(it)) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        Big("Volume, $unit", countUp(true, 2, r.totals.volume) { model.volume(it).substringBeforeLast(' ') }, Modifier.weight(1f))
                        Big("Sets", countUp(true, 3, r.totals.sets) { thousands(roundHalfUp(it)) }, Modifier.weight(1f))
                    }
                }
            }
            if (r.monthVolumes.any { it > 0 }) item(key = "months-$shown") {
                PageSection("Volume by month") {
                    val names = remember { (1..12).map { Month(it).localName(true) } }
                    MonthBars(r.monthVolumes, names) { m -> model.open(Screen.Month(clean.core.zeroPad(r.year.toLong(), 4) + "-" + clean.core.zeroPad(m.toLong(), 2))) }
                    r.bestMonth?.let { (month, volume) -> Text("Most in ${Month(month).localName(true)}: ${model.volume(volume)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (r.topLifts.isNotEmpty()) item(key = "lifts-$shown") {
                PageSection("Strongest gains") {
                    for (lift in r.topLifts) {
                        val unit = model.unitOf(lift.exercise)
                        LabelValue(
                            lift.exercise, "+${showEstimate(lift.gain, unit)} $unit",
                            spoken = "${lift.exercise}: estimated 1RM up ${showEstimate(lift.gain, unit)} $unit, from ${showEstimate(lift.from, unit)} to ${showEstimate(lift.to, unit)}",
                        ) { model.open(Screen.ExerciseDetail(lift.exercise)) }
                        Text(
                            "Estimated 1RM ${showEstimate(lift.from, unit)} to ${showEstimate(lift.to, unit)} $unit",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "bests-$shown") {
                PageSection("The year's bests") {
                    r.bestMonth?.let { (month, volume) ->
                        val name = Month(month).localName(true)
                        LabelValue("Best month", "$name, ${model.volume(volume)}", spoken = "Best month $name, ${model.volume(volume)}") {
                            model.open(Screen.Month(clean.core.zeroPad(r.year.toLong(), 4) + "-" + clean.core.zeroPad(month.toLong(), 2)))
                        }
                    }
                    LabelValue("Best streak", "${plural(r.longestStreakWeeks, "week")} in a row")
                    LabelValue("PRs", shortNumber(r.records)) { model.open(Screen.PersonalBests) }
                }
            }
            if (r.topExercises.isNotEmpty()) item(key = "most-$shown") {
                PageSection("Most trained") {
                    // The most sets of the year are the bar's end; each line opens its exercise.
                    val most = r.topExercises.first().second.coerceAtLeast(1.0)
                    for ((i, e) in r.topExercises.withIndex()) {
                        val (exercise, sets) = e
                        RankedRow(i + 1, exercise, setsText(shortNumber(sets)), (sets / most).toFloat(), "Most trained, number ${i + 1}: $exercise, ${shortNumber(sets)} sets") { model.open(Screen.ExerciseDetail(exercise)) }
                    }
                }
            }
        }
    }
}

/** One large figure with its small grey word under it. */
@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The year as plain text for the share sheet: the four totals, the best month and streak, the PRs, the strongest gains and the most trained exercises. Each exercise's weights are shown in its own unit ([unitOf]). */
fun yearSummaryText(r: YearReview, volume: (Double) -> String, unitOf: (String) -> String): String = buildString {
    appendLine("${r.year} in training")
    appendLine("Workouts: ${r.totals.workouts}")
    appendLine("Time trained: ${duration(r.totals.seconds)}")
    appendLine("Volume: ${volume(r.totals.volume)}")
    appendLine("Sets: ${thousands(roundHalfUp(r.totals.sets))}")
    r.bestMonth?.let { (month, v) -> appendLine("Best month: ${Month(month).localName(true)}, ${volume(v)}") }
    appendLine("Best streak: ${plural(r.longestStreakWeeks, "week")} in a row")
    appendLine("PRs: ${shortNumber(r.records)}")
    if (r.topLifts.isNotEmpty()) {
        appendLine()
        appendLine("Strongest gains (estimated 1RM)")
        for (l in r.topLifts) { val u = unitOf(l.exercise); appendLine("${l.exercise}: ${showEstimate(l.from, u)} to ${showEstimate(l.to, u)} $u") }
    }
    if (r.topExercises.isNotEmpty()) {
        appendLine()
        appendLine("Most trained")
        for ((i, e) in r.topExercises.withIndex()) appendLine("${i + 1}. ${e.first}, ${setsText(shortNumber(e.second))}")
    }
}.trimEnd()
