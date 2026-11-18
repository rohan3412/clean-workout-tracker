package clean.app

import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import clean.core.setLabels
import clean.core.numbered
import clean.core.exportText
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import clean.core.Block
import clean.core.sideOneRms
import clean.core.progressOf
import clean.app.ui.theme.cardCorner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LocalTextStyle
import clean.core.Round
import clean.core.imbalanceOf
import clean.core.caption
import clean.core.recordCount
import clean.core.halfLetter
import clean.core.roundKind
import clean.core.roundLabels
import clean.core.shortNumber
import clean.core.sidesOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import clean.core.Muscle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import clean.core.LoadType
import clean.core.NoteItem
import clean.core.Side
import clean.core.isSidesMarker
import clean.core.PrKind
import clean.core.OneRm
import clean.core.estimate1Rm
import clean.core.realLoad
import clean.core.RestItem
import clean.core.SetItem
import clean.core.SetType
import clean.core.Workout
import clean.core.volumeOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
private val LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm")

@Composable
fun HistoryScreen(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val prs by model.prs.collectAsState()
    // Read here so the cards redraw when a setting or the bodyweight changes.
    val settings by model.settings.collectAsState()
    val bodyweight by model.bodyweights.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // A workout matches by its name, by any exercise in it, or by a note (on an exercise or on the workout). The searchable text is
    // built once per change of the history, off the main thread; the stored text is not changed, only lower-cased on a copy.
    val searchable by produceState(emptyList<Searchable>(), workouts) { value = withContext(Dispatchers.Default) { workouts.map(::Searchable) } }
    // Matched off the main thread, the query read once per keystroke; a newer keystroke cancels the older search.
    val found by produceState<List<Pair<Workout, String?>>?>(null, searchable, query) {
        val q = query.trim()
        value = if (q.isEmpty()) null else withContext(Dispatchers.Default) {
            val search = clean.core.SearchQuery(q)
            searchable.mapNotNull { ensureActive(); it.match(q, search) }
        }
    }
    val shown = found?.map { it.first } ?: workouts
    // Where a workout matched only through a note, a line of the note with the match.
    val snippets = remember(found) { found?.mapNotNull { (w, snippet) -> snippet?.let { w.date to it } }?.toMap().orEmpty() }
    val byMonth = remember(shown) { shown.reversed().groupBy { it.day.take(7) } }
    // The first cards of the list rise into place when the page opens (Roll.kt).
    val rank = remember(shown) { shown.reversed().withIndex().associate { it.value.date to it.index } }
    val actions: @Composable () -> Unit = {
        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) CleanIcons.Close else CleanIcons.Search, "Search history") }
        IconButton(onClick = { model.open(Screen.Calendar()) }) { Icon(CleanIcons.Calendar, "Calendar") }
    }
    val listState = rememberLazyListState()
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("History", model::back, actions) }) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), listState) {
        if (searching) item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Workout, exercise or note") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l))
        }
        if (searching && query.isNotBlank() && shown.isEmpty()) item { Section("") { Text("No workout matches.") } }
        if (workouts.isEmpty()) item { EmptyPreview("Finished workouts appear here, newest first.", "Start a workout", { model.stack.clear(); model.openWorkoutSheet() }, Modifier.padding(Space.l)) { PreviewSamples.Rows(96.dp) } }
        for ((month, list) in byMonth) {
            item(key = month) {
                Row(Modifier.fillMaxWidth().clickable { model.open(Screen.Month(month)) }.padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
                    Caption(LocalDate.parse("$month-01").format(MONTH))
                    Text(plural(list.size, "workout"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.date }) { workout ->
                // settings and bodyweight are keys only so the volume is worked out again when they change.
                val volume = remember(workout, settings, bodyweight) { volumeOf(workout, model::loadOf) }
                // The card opens its workout's page as a container that grows out of it (a page card, see [LocalCardKey]).
                androidx.compose.runtime.CompositionLocalProvider(LocalCardKey provides "page:history:${workout.date}") {
                    Box(Modifier.settleIn(rank[workout.date] ?: Motion.SETTLE_ROWS)) { WorkoutCard(workout, model, volume, recordCount(workout, prs[workout.date].orEmpty()), snippets[workout.date]) }
                }
            }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }
    if (LocalPageCard.current == "progress:history") RowsBelow(listState, workouts)
    }
    }
}

/**
 * The page's half of the History card's rows that travel below the bottom of the screen while the card opens (the rows whose cards are
 * further down the list than is laid out): each row of the card has a place here, one card's height and gap further on than the one
 * before it, starting under the last card that is laid out, and the row moves there as the container grows (and back up on Back).
 * Empty boxes: they only say where. The rows whose cards are on the screen are matched through their own texts and badges.
 */
@Composable
private fun RowsBelow(state: androidx.compose.foundation.lazy.LazyListState, workouts: List<Workout>) {
    val texts = LocalMorphTexts.current ?: return
    val prefix = "progress:history/row:name/"
    val dates = texts.keys.filter { it.startsWith(prefix) && it.endsWith("/card") }.map { it.removePrefix(prefix).removeSuffix("/card") }
    val order = remember(workouts) { workouts.asReversed().withIndex().associate { it.value.date to it.index } }
    for (date in dates) Box(
        Modifier.layout { measurable, constraints ->
            fun at(key: Any) = (key as? String)?.let { order[it] }
            val seen = state.layoutInfo.visibleItemsInfo.filter { at(it.key) != null }
            val last = seen.maxByOrNull { at(it.key)!! }
            val step = last?.size ?: 96.dp.roundToPx()
            val further = ((order[date] ?: 0) - (last?.let { at(it.key)!! } ?: -1)).coerceAtLeast(1)
            val margin = Space.m.roundToPx()
            val body = measurable.measure(androidx.compose.ui.unit.Constraints.fixed((constraints.maxWidth - 2 * margin).coerceAtLeast(0), 24.dp.roundToPx()))
            layout(body.width, body.height) { body.place(margin, (last?.let { it.offset + it.size } ?: 0) + (further - 1) * step) }
        }.morphs("row:out", date, late = true),
    )
}

/**
 * What History's search looks in for one workout, lower-cased once: the names, and each note with its original text kept
 * for the snippet. [match] gives the workout and, when only a note matched, a line of that note around the match.
 */
private class Searchable(val workout: Workout) {
    // The workout's and exercises' names, found as the exercise search finds them (any word order, shorthand, small slips).
    private val names = (listOf(workout.name) + workout.blocks.map { it.exercise }).distinct().map { clean.core.SearchTarget(it) }
    private val notes = (workout.blocks.flatMap { b -> b.notes.map { it.text } } + workout.notes)
        .filter { it.isNotBlank() }.map { it to it.lowercase() }

    fun match(query: String, search: clean.core.SearchQuery): Pair<Workout, String?>? {
        val q = query.lowercase()
        if (names.any { search.rank(it) != null }) return workout to null
        val (text, _) = notes.firstOrNull { q in it.second } ?: return null
        // A line of the note around the match, on one line.
        val at = text.indexOf(query, ignoreCase = true).coerceAtLeast(0)
        val from = (at - 24).coerceAtLeast(0)
        val to = (at + query.length + 40).coerceAtMost(text.length)
        val line = text.substring(from, to).replace('\n', ' ')
        return workout to (if (from > 0) "…" else "") + line + (if (to < text.length) "…" else "")
    }
}

/** `1 PR`, `1.5 PRs`: a left and right workout's count can end in a half. */
fun prsText(count: Double) = "${shortNumber(count)} ${if (count == 1.0) "PR" else "PRs"}"

/** The width of each of the two estimated-1RM columns on a History card. */
private val SIDE_COLUMN = 44.dp

/**
 * A workout's one-line note: a quiet "Add note" that becomes the note, and a tap edits it in a single-line field
 * (so no newline can be typed). [onValueChange] sees every keystroke; [onDone] is called once when the field is left.
 */
@Composable
fun NoteLine(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Text(
            value.ifEmpty { "Add note" },
            modifier.fillMaxWidth().clickable(onClickLabel = if (value.isEmpty()) "Add a workout note" else "Edit the workout note") { editing = true }.minTouch().wrapContentHeight(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = if (value.isEmpty()) FontStyle.Italic else FontStyle.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    } else {
        val focus = remember { FocusRequester() }
        var had by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focus.requestFocus() }
        OutlinedTextField(
            value, onValueChange, modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                if (it.isFocused) had = true else if (had) { editing = false; onDone() }
            },
            placeholder = { Text("Workout note") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { editing = false; onDone() }),
        )
    }
}

@Composable
fun WorkoutCard(workout: Workout, model: AppModel, volume: Double, records: Double, snippet: String? = null) {
    val rule by model.oneRm.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    // A left and right exercise shows its two sides' estimated 1RM in two narrow columns; their heads show only when the card has one.
    val hasPaired = workout.blocks.any { it.paired && it.workingCount > 0.0 }
    // Every other exercise shows its best estimated 1RM by the chosen formula (2026-11-02, the user's words: "the 1RM should show up in
    // the history of workouts"): in a column of its own, or under R 1RM when the card also has a left and right exercise.
    val estimates = remember(workout, rule, bodyweights) {
        workout.blocks.map { b ->
            if (b.paired) null else progressOf(listOf(clean.core.Session(workout.date, b.sets)), rule, model.loadTypeOf(b.exercise), bodyweights::at).firstOrNull()?.estimated1Rm
        }
    }
    val hasOneRm = estimates.any { it != null }
    // The cards whose rows travel to these: Progress's History card, or Home's Recent workouts card (name and date only, it has no PR count).
    val fromRow = LocalPageCard.current.let { it == "progress:history" || it == "home:recentworkouts" }
    // On the History page the card is also a page card (its own key): while the change to its workout's page runs, its name is the card's
    // title; at all other times it stays what the History card's rows travel to, ready for Back to that card.
    val pageCard = LocalCardKey.current?.takeIf { isPageKey(it) }
    val rowsMoving = LocalMorphing.current
    // A row's side of the History card's change has no card key of its own, whatever card the page's card is.
    @Composable fun asRow(content: @Composable () -> Unit) {
        if (pageCard != null) androidx.compose.runtime.CompositionLocalProvider(LocalCardKey provides null) { content() } else content()
    }
    // What the card's rows travel to is also what the card travels to its page with: both are composed, laid out and registered all the time (a text that is
    // new at the moment of the tap has no place to start from for a frame or two, and is missing), and one is drawn, the row's while the rows move.
    @Composable fun RowOrCard(modifier: Modifier, row: @Composable () -> Unit, card: @Composable () -> Unit) {
        if (!fromRow) { Box(modifier) { card() }; return }
        if (pageCard == null) { Box(modifier) { row() }; return }
        Box(modifier) {
            Box(Modifier.graphicsLayer { alpha = if (rowsMoving()) 0f else 1f }) { card() }
            Box(Modifier.matchParentSize().clearAndSetSemantics { }.graphicsLayer { alpha = if (rowsMoving()) 1f else 0f }) { asRow { row() } }
        }
    }
    Section("", onClick = { model.open(Screen.WorkoutDetail(workout.date)) }, spacing = Space.s, inner = Space.m, outer = Space.xs, hold = workoutHold(model, workout)) {
        Column {
            // Nothing inside the card takes a tap of its own: the lines are too small to aim at, so the whole card opens the workout.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                // On the History page opened from Progress's History card, the name, the PR badge and the date are what that card's rows travel to.
                val title = androidx.compose.ui.text.AnnotatedString(workout.name)
                RowOrCard(
                    Modifier.weight(1f),
                    row = { MorphText(title, "row:name", Modifier.fillMaxWidth(), onPage = true, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, id = workout.date) },
                    card = { MorphText(title, "title", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
                // Going to its workout's page, the PR count travels to the page's PR figure.
                if (records > 0) RowOrCard(Modifier, row = { Badge(prsText(records), Modifier.morphs("row:prs", workout.date)) }, card = { Badge(prsText(records), role = "prs") })
            }
            val day = parseDate(workout.date)?.format(SHORT) ?: workout.date
            // The date, the duration and the volume are each a text that travels (to the page's date line and its bar of totals): the date to its
            // place on the History card when it comes from a row of Progress's History card, on to the workout's page otherwise.
            val small = MaterialTheme.typography.bodySmall
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            val dot = "  ·  "
            Row {
                val dayText = androidx.compose.ui.text.AnnotatedString(day)
                RowOrCard(
                    Modifier,
                    row = { MorphText(dayText, "row:date", onPage = true, style = small, color = quiet, maxLines = 1, id = workout.date) },
                    card = { MorphText(dayText, "date", style = small, color = quiet, maxLines = 1) },
                )
                Text(dot, style = small, color = quiet, maxLines = 1, softWrap = false)
                MorphText(androidx.compose.ui.text.AnnotatedString(duration(workout.durationSeconds)), "duration", style = small, color = quiet, maxLines = 1)
                Text(dot, style = small, color = quiet, maxLines = 1, softWrap = false)
                MorphText(androidx.compose.ui.text.AnnotatedString(model.volume(volume)), "volume", style = small, color = quiet, maxLines = 1)
            }
            if (workout.notes.isNotEmpty()) Text(workout.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (snippet != null) Text(snippet, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column {
            Row {
                Caption("Sets", Modifier.weight(2.5f))
                if (hasPaired) {
                    Caption("L 1RM", Modifier.width(SIDE_COLUMN), TextAlign.End)
                    Caption("R 1RM", Modifier.width(SIDE_COLUMN), TextAlign.End)
                } else if (hasOneRm) Caption("1RM", Modifier.width(SIDE_COLUMN), TextAlign.End)
            }
            Spacer(Modifier.height(Space.xs))
            for ((at, block) in workout.blocks.withIndex()) {
                // A round of a left and right exercise is one set.
                val working = block.workingCount
                if (working == 0.0) continue
                if (block.paired) {
                    val unit = model.unitOf(block.exercise)
                    val (left, right) = remember(workout, block, rule, bodyweights) { sideOneRms(workout.date, block, rule, model.loadTypeOf(block.exercise), bodyweights::at) }
                    fun shown(kg: Double?) = if (kg == null) NONE else showWeight(kg, unit)
                    fun said(side: String, kg: Double?) = if (kg == null) "$side no estimate" else "$side ${showWeight(kg, unit)} ${if (unit == "lb") "pounds" else "kilograms"}"
                    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "${shortNumber(working)} × ${block.exercise}. Estimated 1RM: ${said("left", left)}, ${said("right", right)}" }) {
                        ExerciseLine(working, block.exercise, at, Modifier.weight(2.5f).padding(end = Space.s))
                        for (kg in listOf(left, right)) Text(shown(kg), Modifier.width(SIDE_COLUMN), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, style = LocalTextStyle.current)
                    }
                } else Row {
                    ExerciseLine(working, block.exercise, at, Modifier.weight(1f).padding(end = Space.s))
                    if (hasPaired) Spacer(Modifier.width(SIDE_COLUMN))
                    // The best estimate is the figure the workout's page shows on that exercise's best set.
                    if (hasPaired || hasOneRm) MorphText(
                        androidx.compose.ui.text.AnnotatedString(estimates[at]?.let { showEstimate(it, model.unitOf(block.exercise)) } ?: if (hasPaired) "" else NONE), "est", Modifier.width(SIDE_COLUMN),
                        style = LocalTextStyle.current, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, id = at.toString(), alignment = Alignment.CenterEnd,
                    )
                }
            }
        }
    }
}

/** One exercise of the History card's Sets column: how many sets, then its name, which travels onto the exercise's heading on the workout's page. */
@Composable
private fun ExerciseLine(working: Double, exercise: String, at: Int, modifier: Modifier) {
    Row(modifier) {
        Text("${shortNumber(working)} × ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        MorphText(androidx.compose.ui.text.AnnotatedString(exercise), "ex", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, id = at.toString())
    }
}

/** One set's estimated 1RM in kg on its real load, or null where there is none to give. */
fun estimateOf(set: SetItem, loadType: LoadType, bodyweight: Double?, rule: OneRm): Double? {
    // A warm-up is not an attempt; an estimate beside it is only noise.
    if (set.type == SetType.WARMUP) return null
    val w = set.weight ?: return null
    val r = set.reps ?: return null
    return realLoad(loadType, w, bodyweight)?.let { estimate1Rm(it, r, rule, set.rpe) }
}

/** The PRs one workout set; each opens its exercise. */
@Composable
fun PrListDialog(model: AppModel, workout: Workout, onDismiss: () -> Unit) {
    val prs by model.prs.collectAsState()
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("PRs") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (row in prRows(listOf(workout), prs)) PrLine(model, row) { onDismiss(); model.open(Screen.ExerciseDetail(row.exercise)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A workout's volume taken apart by exercise and by muscle (each exercise's volume times the muscle's share). */
@Composable
fun VolumeDialog(model: AppModel, workout: Workout, onDismiss: () -> Unit) {
    val byExercise = LinkedHashMap<String, Double>()
    for (b in workout.blocks) byExercise[b.exercise] = (byExercise[b.exercise] ?: 0.0) + volumeOf(workout.copy(blocks = listOf(b)), model::loadOf)
    val byMuscle = HashMap<Muscle, Double>()
    for ((exercise, v) in byExercise) for ((muscle, share) in model.musclesOf(exercise).shares) byMuscle[muscle] = (byMuscle[muscle] ?: 0.0) + v * share
    fun kg(v: Double) = model.volume(v)
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volume") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("By exercise", fontWeight = FontWeight.Bold)
                for ((exercise, v) in byExercise.entries.sortedByDescending { it.value }) LabelValue(exercise, kg(v)) { onDismiss(); model.open(Screen.ExerciseDetail(exercise)) }
                Text("By muscle", Modifier.padding(top = Space.s), fontWeight = FontWeight.Bold)
                for ((muscle, v) in byMuscle.entries.sortedByDescending { it.value }) LabelValue(muscle.label, kg(v)) { onDismiss(); model.open(Screen.MuscleDetail(muscle)) }
                Text("Weight times reps over every set but warm-ups. A muscle gets the whole of it where it is a main muscle, half where it supports, a quarter where its part is minor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The width of each of the two estimated-1RM columns at the right of a paired block's rounds. */
private val ROUND_ONE_RM = 52.dp

/** The L and R over the two columns of a paired block's rounds, and with [oneRm] over the two estimated-1RM columns at the right. */
@Composable
fun RoundHeads(oneRm: Boolean = false) {
    // The rows read themselves out in full, so a screen reader skips the heads.
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Spacer(Modifier.width(36.dp))
        Caption("L", Modifier.weight(1f))
        Caption("R", Modifier.weight(1f))
        if (oneRm) {
            Caption("L 1RM", Modifier.width(ROUND_ONE_RM), TextAlign.End)
            Caption("R 1RM", Modifier.width(ROUND_ONE_RM), TextAlign.End)
        }
    }
}

/**
 * One round of a paired [block], drawn as the ordinary set rows are: its [label] (number or kind letter) first, then the
 * left and the right set side by side, a half not done as a dash, and one row of PR badges under it naming the side or
 * sides that earned each kind. [kinds] is the PR kinds of the block's sets in order (what `prsOf` gives for the block).
 * With [estimate] (a set's estimated 1RM in kg) each side's estimate follows at the right in two columns, as an ordinary
 * set's 1RM does. The workout's page and the summary both draw rounds with it.
 */
@Composable
fun RoundRow(model: AppModel, block: Block, round: Round, label: String, kinds: List<Set<PrKind>>, estimate: ((SetItem) -> Double?)? = null, onOpen: () -> Unit) {
    val unit = model.unitOf(block.exercise)
    val plus = if (model.loadTypeOf(block.exercise) == LoadType.BODYWEIGHT) "+" else ""
    val weightWord = if (unit == "lb") "pounds" else "kilograms"
    fun kindsOf(set: SetItem?): Set<PrKind> = set?.let { s -> kinds.getOrNull(block.sets.indexOfFirst { it === s }) }.orEmpty()
    fun shown(set: SetItem): String = (if (set.weight != null && set.reps != null) plus else "") + setValues(set, unit)
    fun said(side: String, set: SetItem?): String {
        val w = set?.weight
        return when {
            set == null -> "$side not done"
            w != null && set.reps != null -> "$side ${showWeight(w, unit)} $weightWord ${set.reps} reps"
            else -> "$side ${setValues(set, unit)}"
        }
    }
    // Halves of one kind are named by the round's label; halves that differ each show their own letter beside the value.
    val type = roundKind(round.left?.type, round.right?.type) ?: SetType.NORMAL
    fun kindWord(set: SetItem?) = when (set?.type) {
        SetType.WARMUP -> "warm-up"
        SetType.FAILURE -> "failure"
        SetType.DROP -> "drop"
        else -> "normal"
    }
    val differ = halfLetter(round, Side.LEFT) != null
    val name = if (differ) "Set $label, left ${kindWord(round.left)}, right ${kindWord(round.right)}" else when (type) {
        SetType.NORMAL -> "Set $label"
        SetType.WARMUP -> "Warm-up set"
        SetType.FAILURE -> "Failure set"
        SetType.DROP -> "Drop set"
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun saidRm(side: String, set: SetItem?): String = set?.let { estimate?.invoke(it) }?.let { ", $side estimated 1RM ${showWeight(Math.round(it).toDouble(), unit)} $weightWord" } ?: ""
    // A half's own kind, beside its value, only when the round's halves differ in kind.
    @Composable
    fun RowScope.halfMark(side: Side) {
        val letter = halfLetter(round, side) ?: return
        val set = if (side == Side.LEFT) round.left else round.right
        Text(letter, Modifier.padding(end = Space.xs), style = MaterialTheme.typography.labelSmall, color = if (set == null || set.type == SetType.NORMAL) muted else typeColor(set.type), fontWeight = FontWeight.Bold)
    }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$name, ${said("left", round.left)}, ${said("right", round.right)}" + (if (round.rightFirst) ", right first" else "") + saidRm("left", round.left) + saidRm("right", round.right) }) {
        Text(label, Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium, color = typeColor(type), fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            halfMark(Side.LEFT)
            Text(round.left?.let(::shown) ?: "–", style = MaterialTheme.typography.bodyMedium, color = if (round.left == null) muted else Color.Unspecified)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            halfMark(Side.RIGHT)
            Text(round.right?.let(::shown) ?: "–", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = if (round.right == null) muted else Color.Unspecified)
            // Only a round done right first says so, quietly.
            if (round.rightFirst) Text("1st", Modifier.padding(start = Space.s), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        if (estimate != null) for (set in listOf(round.left, round.right)) {
            val kg = set?.let(estimate)
            Text(kg?.let { showWeight(Math.round(it).toDouble(), unit) } ?: "", Modifier.width(ROUND_ONE_RM), style = MaterialTheme.typography.bodyMedium, color = muted, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        }
    }
    val earned = sidesOf(kindsOf(round.left), kindsOf(round.right))
    if (earned.isNotEmpty()) PrBadges(earned.mapTo(HashSet()) { it.first }, Modifier.padding(start = 36.dp).clickable(onClick = onOpen), sides = earned.toMap())
}

/** All the rounds of a paired [block] under their L and R heads. */
@Composable
fun RoundRows(model: AppModel, block: Block, kinds: List<Set<PrKind>>, estimate: ((SetItem) -> Double?)? = null, onOpen: () -> Unit) {
    val labels = roundLabels(block.rounds)
    Column {
        RoundHeads(oneRm = estimate != null && block.sets.any { estimate(it) != null })
        for ((i, round) in block.rounds.withIndex()) RoundRow(model, block, round, labels[i], kinds, estimate, onOpen)
    }
}

/** A set's PRs, one badge each, and one more naming whatever it only matched. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrBadges(kinds: Set<PrKind>, modifier: Modifier = Modifier, sides: Map<PrKind, Set<Side>> = emptyMap(), landing: Set<PrKind> = emptySet()) {
    if (kinds.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        // Green for a PR, amber for a best that was equalled; the word is only which kind.
        for (kind in PrKind.entries) if (kind in kinds) {
            // Left and right: the side or sides that earned it follow the kind ("1RM L", "Weight L R").
            val earned = sides[kind].orEmpty().sorted()
            val label = kind.label + earned.joinToString("") { if (it == Side.LEFT) " L" else " R" }
            val spoken = kind.label + if (earned.isEmpty()) "" else " " + earned.joinToString(" and ") { if (it == Side.LEFT) "left" else "right" }
            // Colour alone says matched or new, so the spoken name says it too.
            if (kind.matched) Badge(label, Modifier.landing(kind in landing).semantics { contentDescription = "$spoken, matched" }, MaterialTheme.colorScheme.tertiary)
            else Badge(label, Modifier.landing(kind in landing).semantics { contentDescription = "$spoken, new PR" })
        }
    }
}

/** One figure of a workout in the bar along the bottom of its page: a picture for what it is, and the number. */
@Composable
private fun RowScope.Total(icon: ImageVector, what: String, value: String, onClick: (() -> Unit)?, role: String) {
    Row(
        Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Space.m),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, what, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  ")
        // The card's duration, volume and PR count travel to these.
        MorphText(androidx.compose.ui.text.AnnotatedString(value), role, onPage = true, fontWeight = FontWeight.SemiBold)
    }
}

/** The workout's blocks by card: a block that follows another variation of the same overall exercise belongs to the same card. */
internal fun workoutCards(model: AppModel, workout: Workout): List<List<Int>> {
    val cards = mutableListOf<MutableList<Int>>()
    for (i in workout.blocks.indices) {
        val before = workout.blocks.getOrNull(i - 1)
        val block = workout.blocks[i]
        if (before != null && before.exercise != block.exercise && model.overallOf(before.exercise) == model.overallOf(block.exercise)) cards.last() += i
        else cards += mutableListOf(i)
    }
    return cards
}

/**
 * One card of a finished workout (see [workoutCards]): each exercise's name, its notes and its sets with their PR badges, rounds for left
 * and right. Shared by the workout's page and the summary; [onOptions], when given, puts the exercise's options button beside its name.
 */
@Composable
internal fun WorkoutCardSets(model: AppModel, workout: Workout, card: List<Int>, prs: List<Set<PrKind>>, bodyweights: clean.core.Bodyweights, rule: OneRm, modifier: Modifier = Modifier, onOptions: ((Int) -> Unit)? = null) {
    // The position of each block's first set among all the workout's sets, to find its PRs.
    val firstSet = remember(workout) { workout.blocks.runningFold(0) { n, b -> n + b.sets.size } }
    Column(modifier.fillMaxWidth()) {
          for (blockIndex in card) {
            val block = workout.blocks[blockIndex]
            val unit = model.unitOf(block.exercise)
            val loadType = model.loadTypeOf(block.exercise)
            val plus = if (loadType == LoadType.BODYWEIGHT) "+" else ""
            val bodyweight = bodyweights.at(workout.date)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The History card's line for this exercise travels to this heading.
                MorphText(
                    androidx.compose.ui.text.AnnotatedString(block.exercise), "ex", Modifier.weight(1f).clickable { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) }, onPage = true,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, id = blockIndex.toString(),
                )
                if (block.paired) {
                    // How far one side lagged, when the sides differ; each side's 1RM is in the two columns under its heads.
                    val lag = remember(block, rule, bodyweights) { imbalanceOf(block, workout.date, rule, loadType, bodyweights::at) }
                    if (lag != null) Text(lag.caption(), Modifier.padding(end = Space.xs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (block.sets.any { it.weight != null && it.reps != null }) Text("1RM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (onOptions != null) IconButton(onClick = { onOptions(blockIndex) }, Modifier.size(36.dp)) {
                    Icon(CleanIcons.More, "Options for ${block.exercise}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The one figure that is also on the History card (its estimate of the exercise): the best set's.
            val estimates = block.sets.map { set -> val w = set.weight; val r = set.reps; if (w != null && r != null && set.type != SetType.WARMUP) realLoad(loadType, w, bodyweight)?.let { load -> estimate1Rm(load, r, rule, set.rpe) } else null }
            val bestEstimate = estimates.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
            var setIndex = firstSet[blockIndex]
            // Numbered as the logging screen does, whatever the stored Set Order says: warm-ups and drop sets take no number.
            val setNames = setLabels(block.sets.map { it.type })
            // A paired block is drawn as its rounds: a round is written when its last set comes, and a rest that
            // fell between its halves is held back until after it.
            val kinds = prs.drop(firstSet[blockIndex]).take(block.sets.size)
            val labels = roundLabels(block.rounds)
            var round = 0
            var taken = 0
            var headed = false
            val held = mutableListOf<RestItem>()
            for (item in block.items) {
                when (item) {
                    is NoteItem -> if (item.text.isNotEmpty() && !isSidesMarker(item.text)) Text(item.text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is RestItem -> if (taken > 0) held.add(item) else Text("Rest ${clock((item.seconds ?: 0.0).toLong())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is SetItem -> if (block.paired) {
                        taken++
                        val current = block.rounds.getOrNull(round)
                        if (current != null && taken == listOfNotNull(current.left, current.right).size) {
                            val estimate = { set: SetItem -> estimateOf(set, loadType, bodyweight, rule) }
                            if (!headed) { RoundHeads(oneRm = block.sets.any { estimate(it) != null }); headed = true }
                            RoundRow(model, block, current, labels[round], kinds, estimate) { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) }
                            for (rest in held) Text("Rest ${clock((rest.seconds ?: 0.0).toLong())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            held.clear()
                            round++
                            taken = 0
                        }
                    } else {
                        val mine = prs.getOrNull(setIndex++).orEmpty()
                        val w = item.weight
                        val r = item.reps
                        // The estimate for this set alone, on its real load; blank where there is none to give, and beside a warm-up.
                        val estimate = if (w != null && r != null && item.type != SetType.WARMUP) realLoad(loadType, w, bodyweight)?.let { estimate1Rm(it, r, rule, item.rpe) } else null
                        Row {
                            Text(setNames.getOrElse(setIndex - 1 - firstSet[blockIndex]) { item.row.setOrder }, Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium, color = typeColor(item.type), fontWeight = FontWeight.Bold)
                            Text((if (w != null && r != null) plus else "") + setValues(item, unit), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (estimate != null) MorphText(
                                androidx.compose.ui.text.AnnotatedString(showWeight(Math.round(estimate).toDouble(), unit)), if (setIndex - 1 - firstSet[blockIndex] == bestEstimate) "est" else null,
                                Modifier.clickable { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) }, onPage = true,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, id = blockIndex.toString(), alignment = Alignment.CenterEnd,
                            )
                        }
                        PrBadges(mine, Modifier.padding(start = 36.dp).clickable { model.open(Screen.ExerciseDetail(block.exercise, tab = 0)) })
                    }
                }
            }
          }
    }
}

@Composable
fun WorkoutDetailScreen(model: AppModel, date: String) {
    val workouts by model.workouts.collectAsState()
    val allPrs by model.prs.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val workout = workouts.firstOrNull { it.date == date }
    val prs = allPrs[date].orEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (sharing && workout != null) ShareWorkoutDialog(model, workout) { sharing = false }
    var showPrs by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    // The block (by its place in the workout) whose options are open.
    var optionsFor by remember { mutableStateOf<Int?>(null) }
    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(workout?.name ?: "Workout", model::back) {
            IconButton(onClick = { menu = true }) { Icon(CleanIcons.More, "Workout options") }
            if (menu) Sheet({ menu = false }, workout?.name) {
                SheetRow("Do this workout again", CleanIcons.Play) { menu = false; workout?.let(model::startFrom) }
                SheetRow("Save as template", CleanIcons.Copy) { menu = false; workout?.let(model::saveAsTemplate) }
                SheetRow("Share", CleanIcons.Share) { menu = false; sharing = true }
                SheetRow("Share as a template file", CleanIcons.Share) {
                    menu = false
                    workout?.let { shareFile(context, "${it.name}.clean.json", model.templatesFile(listOf(model.templateFrom(it))).toByteArray(), "application/json") }
                }
                SheetRow("Copy as text", CleanIcons.Copy) {
                    menu = false
                    workout?.let { w ->
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(w.name, exportText(w, model.profile.value?.unit == "lb")))
                        model.message = "Copied"
                    }
                }
                SheetRow("Edit", CleanIcons.Edit) { menu = false; workout?.let(model::editWorkout) }
                SheetRow("Every workout of this name", CleanIcons.History) { menu = false; workout?.let { model.open(Screen.Routine(it.name)) } }
                SheetGap()
                SheetRow("Delete", CleanIcons.Delete, danger = true) { menu = false; confirmDelete = true }
            }
        }
    }) { padding ->
        if (workout == null) return@Scaffold
        val list = rememberLazyListState()
        val haze = dev.chrisbanes.haze.rememberHazeState()
        Box(Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().hazeSource(haze), state = list, contentPadding = PaddingValues(bottom = 76.dp)) {
            item {
                // The name is in the bar above; the day leads to its month.
                MorphText(
                    androidx.compose.ui.text.AnnotatedString(parseDate(workout.date)?.format(LONG) ?: workout.date), "date",
                    Modifier.fillMaxWidth().clickable { model.open(Screen.Month(workout.day.take(7))) }.padding(horizontal = Space.l, vertical = Space.s), onPage = true,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var note by remember(workout.date, workout.notes) { mutableStateOf(workout.notes) }
                NoteLine(note, { note = it }, Modifier.padding(horizontal = Space.l), onDone = { model.setWorkoutNote(workout.date, note) })
            }
            items(workoutCards(model, workout)) { card ->
                // A workout is many exercises, so they are set down plainly, with no card around each: small text, the sets one under another.
                WorkoutCardSets(model, workout, card, prs, bodyweights, rule, Modifier.padding(horizontal = Space.l, vertical = Space.s)) { optionsFor = it }
            }
            item { CompareLastSection(model, workout, workouts) }
        }
        // What the workout came to, afloat over the list while there is more below; at the end it settles into the bottom edge.
        val docked = !list.canScrollForward
        val side = if (docked) 0.dp else 16.dp
        val cardTop = cardCorner()
        val corner = if (docked) 0.dp else cardTop
        val volume = remember(workout) { volumeOf(workout, model::loadOf) }
        val records = recordCount(workout, prs)
        val totalsShape = RoundedCornerShape(topStart = cardTop, topEnd = cardTop, bottomStart = corner, bottomEnd = corner)
        // Glass: the list scrolls under it and is blurred there (solid where there is no blur).
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(start = side, end = side, bottom = side / 2).fillMaxWidth().glass(totalsShape, haze, lift = if (docked) 0.dp else 10.dp),
            shape = totalsShape, color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Row(Modifier.padding(vertical = Space.xs), horizontalArrangement = Arrangement.SpaceEvenly) {
                Total(CleanIcons.TimerLine, "Duration", duration(workout.durationSeconds), null, "duration")
                Total(CleanIcons.DumbbellLine, "Volume", model.volume(volume), { showVolume = true }, "volume")
                Total(CleanIcons.StarLine, "PRs", shortNumber(records), if (records > 0) ({ showPrs = true }) else null, "prs")
            }
        }
        }
    }
    optionsFor?.let { at ->
        val block = workout?.blocks?.getOrNull(at)
        if (workout != null && block != null) Sheet({ optionsFor = null }, block.exercise) {
            // Only the marker row is added or taken away: the sets and everything else stay as they were stored.
            SheetRow(if (block.paired) "Read as ordinary sets" else "Read as left and right", CleanIcons.LeftRight) {
                optionsFor = null
                model.readAsSides(workout.date, block.exercise, workout.blocks.take(at).count { it.exercise == block.exercise }, !block.paired)
            }
        }
    }
    if (workout != null && showPrs) PrListDialog(model, workout) { showPrs = false }
    if (workout != null && showVolume) VolumeDialog(model, workout) { showVolume = false }
    if (confirmDelete) {
        Confirm("Delete workout?", "This removes it from history and from the export. You can undo it for a few seconds.", "Delete", {
            model.back()
            model.deleteWorkout(date)
        }) { confirmDelete = false }
    }
}
