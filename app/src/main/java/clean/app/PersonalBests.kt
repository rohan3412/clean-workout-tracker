package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.core.LoadType
import clean.core.PrKind
import clean.core.Record
import clean.core.recordsBySide
import clean.core.sidesLine
import clean.core.sidesSpoken
import clean.core.sessionsByExercise
import clean.core.shortNumber
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An exercise's best estimated 1RM and the set it came from, with the unit and sign that exercise is read in. For an
 * exercise with left and right history [record] is the weaker side's, and [left] and [right] each side's own.
 */
private class BestLift(val name: String, val unit: String, val sign: String, val record: Record, val left: Record? = null, val right: Record? = null, val sided: Boolean = false)

private val MONTH_HEAD: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Every exercise by its best estimated 1RM, and every PR by the month it was set in. Both are worked out once for the whole
 * history, off the main thread, from the model's PR lists and the same record rules the exercise pages use.
 */
@Composable
fun PersonalBestsScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    var view by model.remembered("personalBests", "Best lifts")
    var query by remember { mutableStateOf("") }
    // The exercise with most working sets over the whole history (it moved here from Progress, 1.1.0).
    val topExercise = remember(workouts) {
        val first = workouts.firstOrNull()?.let { java.time.LocalDate.parse(it.day) }
        first?.let { clean.core.topExercise(workouts, clean.core.DayRange(it, java.time.LocalDate.now())) }
    }
    val lifts by produceState(emptyList<BestLift>(), workouts, settings, bodyweights, rule) {
        value = withContext(Dispatchers.Default) {
            sessionsByExercise(workouts).mapNotNull { (name, sessions) ->
                val type = model.loadTypeOf(name)
                val sign = when (type) {
                    LoadType.BODYWEIGHT -> "+"
                    LoadType.ASSISTED -> "−"
                    else -> ""
                }
                val records = recordsBySide(sessions, rule, type, bodyweights::at)
                records.comparable.estimated1Rm?.let { BestLift(name, model.unitOf(name), sign, it, records.left.estimated1Rm, records.right.estimated1Rm, sessions.any { s -> s.paired }) }
            }.sortedByDescending { it.record.value }
        }
    }
    val months by produceState(emptyList<Pair<String, List<PrRow>>>(), workouts, allPrs) {
        value = withContext(Dispatchers.Default) {
            prRows(workouts, allPrs).groupBy { it.date.take(7) }.entries.sortedByDescending { it.key }.map { (month, rows) -> month to rows.reversed() }
        }
    }
    // The newest month is open to begin with; the others open when touched.
    var opened by remember { mutableStateOf<Set<String>?>(null) }
    val open = opened ?: setOfNotNull(months.firstOrNull()?.first)
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Personal bests", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    topExercise?.let { (name, sets) -> NavRow("Top exercise", "$name, ${setsText(shortNumber(sets))}", { model.open(Screen.ExerciseDetail(name)) }, "progress:stat:exercise") }
                    Segmented(listOf("Best lifts", "By month"), view, { it }) { view = it }
                    if (view == "Best lifts") OutlinedTextField(query, { query = it }, placeholder = { Text("Search exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            if (view == "Best lifts") {
                val shown = lifts.filter { it.name.contains(query.trim(), ignoreCase = true) }
                if (lifts.isEmpty()) item { EmptyPreview("Log a set with weight and reps and your best lifts appear here.", modifier = Modifier.padding(Space.l)) { PreviewSamples.Rows(96.dp) } }
                else if (shown.isEmpty()) item { Section("") { Text("No exercise matches.") } }
                itemsIndexed(shown, key = { _, l -> l.name }) { index, lift ->
                    val r = lift.record
                    val w = r.weight
                    val set = (if (w != null && r.reps != null) "${lift.sign}${showWeight(w, lift.unit)} ${lift.unit} × ${r.reps} · " else "") + prettyDay(r.date)
                    // The line opens the exercise on its PRs.
                    Row(
                        Modifier.settleIn(index + 1).fillMaxWidth().clickable { model.open(Screen.ExerciseDetail(lift.name, tab = 3)) }.padding(horizontal = Space.l, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        Column(Modifier.weight(1f)) {
                            MorphText(androidx.compose.ui.text.AnnotatedString(lift.name), "pr:name", onPage = true, id = lift.name)
                            Text(set, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Done left and right: each side's own best under the weaker side's.
                            if (lift.sided) {
                                fun side(r: Record?) = r?.let { showWeight(Math.round(it.value * 10) / 10.0, lift.unit) }
                                val l = side(lift.left)
                                val rt = side(lift.right)
                                Text(
                                    sidesLine(l, rt).orEmpty(), Modifier.semantics { contentDescription = "${sidesSpoken(l, rt)} ${lift.unit}" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text("${showWeight(Math.round(lift.record.value * 10) / 10.0, lift.unit)} ${lift.unit}", fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            } else {
                if (months.isEmpty()) item { EmptyPreview("Log a workout: a set that beats an exercise's best is a PR, and a first workout sets the marks.", modifier = Modifier.padding(Space.l)) { PreviewSamples.Bars() } }
                for ((month, rows) in months) {
                    val isOpen = month in open
                    item(key = month) {
                        Row(
                            Modifier.fillMaxWidth().clickable { opened = if (isOpen) open - month else open + month }.padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            Caption(runCatching { YearMonth.parse(month).format(MONTH_HEAD) }.getOrDefault(month), Modifier.weight(1f))
                            // A PR of one side of a round counts a half.
                            Text("${prsText(prCount(rows))}  ${if (isOpen) "▾" else "▸"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isOpen) itemsIndexed(rows, key = { i, _ -> "$month#$i" }) { _, row ->
                        Row(
                            Modifier.fillMaxWidth().clickable { model.open(Screen.WorkoutDetail(row.date)) }
                                .then(if (row.side == null) Modifier else Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = "${row.exercise}, ${prettyDay(row.date)}, ${row.sideWord}set ${setValues(row.set, model.unitOf(row.exercise))}, " +
                                        PrKind.entries.filter { it in row.kinds }.joinToString(", ") { it.label }
                                })
                                .padding(horizontal = Space.l, vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.exercise)
                                Text("${prettyDay(row.date)} · ${row.sidePrefix}${setValues(row.set, model.unitOf(row.exercise))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PrBadges(row.kinds)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.xxl)) }
        }
    }
}
