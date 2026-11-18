package clean.app

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

import androidx.compose.ui.semantics.clearAndSetSemantics
import clean.app.ui.theme.LocalTextScale
import clean.app.ui.theme.cardShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import clean.core.setLabels
import clean.core.numbered
import clean.core.stalled
import clean.core.PrKind
import clean.core.SetItem
import clean.core.Side
import clean.core.caption
import clean.core.detail
import clean.core.imbalanceOf
import clean.core.latestImbalance
import clean.core.recordsBySide
import clean.core.roundsLogged
import clean.core.roundsText
import clean.core.sentence
import clean.core.sidesLine
import clean.core.sidesSpoken
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.TabPosition
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import clean.core.goalOneRm
import clean.core.goalReachedOn
import clean.core.exerciseGoalProgress
import clean.core.parseGoal
import clean.core.projectedLine
import clean.core.estimate1Rm
import clean.core.weightForReps
import clean.core.SetType
import clean.core.shortNumber
import clean.core.realLoad
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import clean.core.libraryExercise
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import clean.app.ui.theme.LocalLook
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.core.BODYWEIGHT_WINDOW_DAYS
import clean.core.KG_PER_LB
import clean.core.LIBRARY
import clean.core.Progress
import clean.core.chartPoints
import clean.core.imbalanceSeries
import clean.core.progressBySide
import clean.core.LoadType
import clean.core.Muscle
import clean.core.MuscleUse
import clean.core.Record
import clean.core.suggest
import clean.core.Workout
import clean.core.otherNames
import clean.core.SearchTarget
import clean.core.SearchQuery
import clean.core.LibraryEntry
import clean.core.rankLibrary
import clean.core.nearestName
import clean.core.recordsOf
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** [own] marks an exercise the user made or changed that has not been logged yet. */
data class ExerciseSummary(
    val name: String, val sessions: Int, val lastDate: String, val own: Boolean = false,
    /** Sessions in the last 12 weeks. */
    val recent: Int = 0,
    /** The best working set ever logged (kg, reps, seconds), for the grey line under the name. */
    val bestWeight: Double? = null, val bestReps: Int? = null, val bestSeconds: Double? = null,
)

/** Whether [a] is a better set than [b]: the higher Epley figure, then more reps, then longer. */
private fun betterSet(a: SetItem, b: SetItem?): Boolean {
    if (b == null) return true
    val ka = (a.weight ?: 0.0) * (1 + (a.reps ?: 0) / 30.0)
    val kb = (b.weight ?: 0.0) * (1 + (b.reps ?: 0) / 30.0)
    if (ka != kb) return ka > kb
    if ((a.reps ?: 0) != (b.reps ?: 0)) return (a.reps ?: 0) > (b.reps ?: 0)
    return (a.seconds ?: 0.0) > (b.seconds ?: 0.0)
}

/** "100 × 6", "12 reps" or "45s": the best set as the Library's grey line shows it; null when the sets hold no figure. */
fun bestSetLine(e: ExerciseSummary, unit: String): String? {
    val w = e.bestWeight?.takeIf { it > 0 }
    val r = e.bestReps?.takeIf { it > 0 }
    return when {
        w != null && r != null -> "${showWeight(w, unit)} × $r"
        w != null -> showWeight(w, unit)
        r != null -> "$r reps"
        e.bestSeconds != null && e.bestSeconds > 0 -> duration(e.bestSeconds.toLong())
        else -> null
    }
}

private val EQUIPMENT = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Other")

/** Which of the equipment filters an exercise falls under, read from the equipment in its name. */
private fun equipmentOf(name: String): String {
    val n = name.lowercase()
    return when {
        "barbell" in n || "ez bar" in n || "trap bar" in n -> "Barbell"
        "dumbbell" in n -> "Dumbbell"
        "cable" in n -> "Cable"
        "machine" in n || "plate loaded" in n -> "Machine"
        "band" in n || "kettlebell" in n || "suspension" in n || "plate" in n || "ball" in n -> "Other"
        suggest(name)?.loadType.let { it == LoadType.BODYWEIGHT || it == LoadType.ASSISTED } -> "Bodyweight"
        else -> "Other"
    }
}

/** Every exercise that has been logged, most used first. */
fun exerciseSummaries(workouts: List<Workout>): List<ExerciseSummary> {
    val count = HashMap<String, Int>()
    val recent = HashMap<String, Int>()
    val last = HashMap<String, String>()
    val best = HashMap<String, SetItem>()
    val since = LocalDate.now().minusWeeks(12).toString()
    for (w in workouts) {
        for (name in w.blocks.mapTo(HashSet()) { it.exercise }) {
            count[name] = (count[name] ?: 0) + 1
            if (w.date >= since) recent[name] = (recent[name] ?: 0) + 1
            last[name] = w.date
        }
        for (b in w.blocks) for (set in b.sets) if (set.type != SetType.WARMUP && betterSet(set, best[b.exercise])) best[b.exercise] = set
    }
    return count.map { (name, n) ->
        val b = best[name]
        ExerciseSummary(name, n, last.getValue(name), recent = recent[name] ?: 0, bestWeight = b?.weight, bestReps = b?.reps, bestSeconds = b?.seconds)
    }.sortedWith(compareByDescending<ExerciseSummary> { it.sessions }.thenBy { it.name })
}

/** The user's exercises, most used first, then every built-in exercise they have not logged yet. */
fun allExercises(workouts: List<Workout>, own: Collection<String> = emptyList()): List<ExerciseSummary> {
    val used = exerciseSummaries(workouts)
    val names = used.mapTo(HashSet()) { it.name }
    // [own] are exercises the user made or changed that have not been logged yet.
    val made = own.filter { names.add(it) }.map { ExerciseSummary(it, 0, "", own = true) }
    return used + made + LIBRARY.filter { it.name !in names }.map { ExerciseSummary(it.name, 0, "") }
}

/** One row of the exercise list: a heading, an exercise or a link ("Show all 23"). [key] is unique across the list. */
private class ListRow(val key: String, val heading: String?, val exercise: ExerciseSummary?, val letter: Char?, val link: String? = null)

private fun letterOf(name: String): Char = name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** A list made for [query]; [suggestion] is the nearest name when nothing matched. */
private class Made(val query: String, val rows: List<ListRow>, val suggestion: String? = null)

/** What search reads of every exercise, worked out once per list: the entries `rankLibrary` ranks and the summary of each name. */
private class Index(val entries: List<LibraryEntry>, val byName: Map<String, ExerciseSummary>)

/** How many of the most recent exercises Recent shows before "Show all". */
private const val RECENT_SHOWN = 5

/** Whether Recent shows every performed exercise instead of five; kept while the app runs (Library and pickers alike). */
private var recentExpanded by mutableStateOf(false)

/**
 * The rows of the exercise list: with nothing typed, Favourites, Recent (five, or all) and A to Z under letters; with [q] typed, the two
 * groups of `rankLibrary` ("You've done these", "More in the library"). Run off the main thread; [stillWanted] throws when the query it
 * was made for has been replaced, which stops it.
 */
private fun listRows(
    all: List<ExerciseSummary>, q: String, index: Index?, muscle: Muscle?, equipment: String?,
    hiding: Boolean, performed: Boolean, onlyFavourites: Boolean, favourites: Set<String>, musclesOf: (String) -> MuscleUse,
    expanded: Boolean, stillWanted: () -> Unit = {},
): List<ListRow> {
    val search = SearchQuery(q)
    val list = mutableListOf<ListRow>()
    // A search is one ranked list in two groups: no letters.
    if (!search.blank && index != null) {
        val result = rankLibrary(
            search, index.entries,
            keep = { e ->
                (muscle == null || muscle in musclesOf(e.name).primary) && (equipment == null || equipmentOf(e.name) == equipment) &&
                    (!performed || e.performed) && (!onlyFavourites || e.favourite)
            },
            stillWanted = stillWanted,
        )
        if (result.done.isNotEmpty()) list += ListRow("h:done", "You've done these", null, null)
        result.done.mapTo(list) { ListRow("a:${it.name}", null, index.byName.getValue(it.name), null) }
        if (result.more.isNotEmpty()) {
            if (result.done.isNotEmpty()) list += ListRow("h:more", "More in the library", null, null)
            result.more.mapTo(list) { ListRow("a:${it.name}", null, index.byName.getValue(it.name), null) }
        }
        return list
    }
    val shown = all.filter { e ->
        (muscle == null || muscle in musclesOf(e.name).primary) &&
            (equipment == null || equipmentOf(e.name) == equipment) &&
            (!performed || e.sessions > 0) &&
            (!onlyFavourites || e.name in favourites) &&
            (!hiding || e.sessions > 0 || e.own)
    }
    val starred = shown.filter { it.name in favourites }.sortedBy { it.name.lowercase() }
    if (starred.isNotEmpty()) {
        list += ListRow("h:favourites", "Favourites", null, null)
        starred.mapTo(list) { ListRow("f:${it.name}", null, it, null) }
    }
    val done = shown.filter { it.sessions > 0 }.sortedByDescending { it.lastDate }
    if (done.isNotEmpty()) {
        list += ListRow("h:recent", "Recent", null, null)
        (if (expanded) done else done.take(RECENT_SHOWN)).mapTo(list) { ListRow("r:${it.name}", null, it, null) }
        if (done.size > RECENT_SHOWN) list += ListRow("h:recent-more", null, null, null, link = if (expanded) "Show fewer" else "Show all ${done.size}")
    }
    var letter: Char? = null
    // The names are lower-cased once for the sort, not once per comparison.
    for (e in shown.map { it to it.name.lowercase() }.sortedWith(compareBy<Pair<ExerciseSummary, String>> { letterOf(it.first.name) == '#' }.thenBy { it.second }).map { it.first }) {
        val l = letterOf(e.name)
        if (l != letter) {
            letter = l
            list += ListRow("h:$l", l.toString(), null, l)
        }
        list += ListRow("a:${e.name}", null, e, l)
    }
    return list
}

/**
 * The Library's list (and the pickers'): search box, the filter chips Muscle, Equipment, Performed and Favourites, and the rows; at
 * rest Favourites, Recent and A to Z with a letter rail on the right that jumps to a letter and marks the one in view; while typing
 * one ranked list. [musclesOf] gives the muscles an exercise trains, for the filter and the grey line under a name never done.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseList(
    all: List<ExerciseSummary>,
    musclesOf: (String) -> MuscleUse,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: ((String) -> Unit)? = null,
    /** Offer [onCreate] only when nothing matches what was typed; by default it is offered whenever the typed name is not an exercise. */
    createWhenEmpty: Boolean = false,
    leading: @Composable () -> Unit = {},
    favourites: Set<String> = emptySet(),
    hideLibrary: Boolean = false,
    /** The Performed chip, when its state is kept by the caller (the Library remembers it); null keeps it here. */
    performedOnly: Boolean? = null,
    onPerformedOnly: ((Boolean) -> Unit)? = null,
    /** The unit an exercise's best set is shown in. */
    unitOf: (String) -> String = { "kg" },
    /** The tab this list is the page of, when it is one: it tells the tab bar when it is at its end. */
    tab: clean.app.Tab? = null,
    /** The Library keeps the scroll position of its list here; a picker keeps its own. */
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    /** What a hold on a row offers (B5); none in a picker. */
    rowActions: ((String) -> List<HoldAction>)? = null,
    /**
     * Ticked names, when several can be picked: each row then has a tick box that [onTick] turns, and while any is ticked a tap on a
     * row ticks it too (with none, a tap still picks the one).
     */
    ticked: List<String>? = null,
    onTick: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    var onlyFavourites by remember { mutableStateOf(false) }
    var localPerformed by remember { mutableStateOf(false) }
    var pickingMuscle by remember { mutableStateOf(false) }
    var pickingEquipment by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val thumbs = remember { Animations.count(context) > 0 }
    var showAll by remember { mutableStateOf(false) }
    val performed = performedOnly ?: localPerformed
    fun setPerformed(on: Boolean) { if (onPerformedOnly != null) onPerformedOnly(on) else localPerformed = on }
    val anyFilter = muscle != null || equipment != null || performed || onlyFavourites
    // With nothing typed or filtered, built-in exercises never used are left out. "Performed" already leaves out what was never logged, so the note and its count are not needed.
    val hiding = hideLibrary && !performed && !showAll && query.isBlank() && muscle == null && equipment == null && !onlyFavourites
    val hidden = if (hiding) all.count { it.sessions == 0 && !it.own } else 0
    val expanded = recentExpanded
    // What search reads of each exercise (other names, so "skull crusher" finds Skullcrusher and another app's name finds ours; main
    // muscles; equipment; what the user has done with it), worked out once per list, off the main thread.
    val index by produceState<Index?>(null, all, musclesOf, favourites) {
        value = withContext(Dispatchers.Default) {
            Index(
                all.map { e -> LibraryEntry(e.name, otherNames(e.name), musclesOf(e.name).primary.map { it.label } + equipmentOf(e.name), e.name in favourites, e.sessions, e.recent, e.lastDate) },
                all.associateBy { it.name },
            )
        }
    }
    // The list is made off the main thread and the newest keystroke wins (a new query cancels the one still running); until it is
    // ready the list as it was stays on screen. The first list, with nothing typed, is made at once so the page never opens empty.
    val made by produceState(
        remember { Made("", listRows(all, "", null, muscle, equipment, hiding, performed, onlyFavourites, favourites, musclesOf, expanded)) },
        all, query, muscle, equipment, hiding, performed, onlyFavourites, favourites, index, expanded,
    ) {
        val q = query.trim()
        val idx = index
        if (q.isNotEmpty() && idx == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val rows = listRows(all, q, idx, muscle, equipment, hiding, performed, onlyFavourites, favourites, musclesOf, expanded) { ensureActive() }
            Made(q, rows, if (q.isNotEmpty() && idx != null && rows.none { it.exercise != null }) nearestName(q, idx.entries) else null)
        }
    }
    val rows = made.rows
    val letters = remember(rows) { rows.filter { it.heading != null && it.letter != null }.map { it.letter!! } }
    val state = listState ?: rememberLazyListState()
    if (tab != null) BarEnd(tab, state)
    val scope = rememberCoroutineScope()
    val name = query.trim()
    // Nothing found for what is typed (the list is the one made for it): say so, offer the nearest name and, below, to create it.
    val nothingFound = name.isNotEmpty() && made.query == name && rows.none { it.exercise != null }
    val creating = onCreate != null && name.isNotEmpty() && all.none { it.name == name } && (!createWhenEmpty || nothingFound)
    // One extra item sits above the rows when a new name can be created.
    val offset = if (creating && !nothingFound) 1 else 0
    val current by remember(rows, offset) { derivedStateOf { rows.getOrNull(state.firstVisibleItemIndex - offset)?.letter } }

    if (pickingMuscle) Sheet({ pickingMuscle = false }, "Muscle") {
        SheetRow("Any muscle", if (muscle == null) CleanIcons.Check else null) { muscle = null; pickingMuscle = false }
        for (m in Muscle.entries) SheetRow(m.label, if (muscle == m) CleanIcons.Check else null) { muscle = m; pickingMuscle = false }
    }
    if (pickingEquipment) Sheet({ pickingEquipment = false }, "Equipment") {
        SheetRow("Any equipment", if (equipment == null) CleanIcons.Check else null) { equipment = null; pickingEquipment = false }
        for (e in EQUIPMENT) SheetRow(e, if (equipment == e) CleanIcons.Check else null) { equipment = e; pickingEquipment = false }
    }

    Column(modifier) {
        Row(Modifier.padding(horizontal = Space.s, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            leading()
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search ${String.format(java.util.Locale.US, "%,d", all.size)} exercises") }, singleLine = true,
                modifier = Modifier.weight(1f).padding(horizontal = Space.s),
            )
        }
        // The chips wrap onto a second line instead of running off the screen edge.
        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            CleanChip(muscle != null, { pickingMuscle = true }, { Text(muscle?.label ?: "Muscle") })
            CleanChip(equipment != null, { pickingEquipment = true }, { Text(equipment ?: "Equipment") })
            CleanChip(performed, { setPerformed(!performed) }, { Text("Performed") })
            CleanChip(onlyFavourites, { onlyFavourites = !onlyFavourites }, { Text("Favourites") })
            if (anyFilter) CleanChip(false, { muscle = null; equipment = null; onlyFavourites = false; setPerformed(false) }, { Text("Clear all") })
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = Space.xxl), state = state) {
                if (nothingFound) {
                    item(key = "none") {
                        Text("No exercise called “$name”", Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    made.suggestion?.let { near ->
                        item(key = "near") {
                            Text("Did you mean $near?", Modifier.fillMaxWidth().clickable { query = near }.padding(Space.l), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (creating) {
                    item(key = "create") {
                        Text("Create “$name”", Modifier.fillMaxWidth().clickable { onCreate!!(name) }.padding(Space.l), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                if (name.isEmpty() && anyFilter && rows.none { it.exercise != null }) item(key = "nofilter") {
                    Text("No exercise fits these filters.", Modifier.fillMaxWidth().padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(rows, key = { it.key }) { row ->
                    val exercise = row.exercise
                    if (row.link != null) {
                        Text(row.link, Modifier.fillMaxWidth().clickable { recentExpanded = !recentExpanded }.padding(Space.l), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                    } else if (exercise == null) {
                        Caption(row.heading.orEmpty(), Modifier.fillMaxWidth().padding(start = Space.l, top = Space.l, bottom = Space.xs))
                    } else {
                        // On the Library tab each row is a container that grows into the exercise's page; a picker (no tab) keeps the plain row.
                        val rowKey = if (tab != null) "exercises:${row.key}" else null
                        CompositionLocalProvider(LocalCardKey provides rowKey) {
                        val open = cardTap { onClick(exercise.name) }
                        Row((if (rowKey != null) Modifier.opensInto(null, androidx.compose.ui.graphics.Color.Transparent, square = true) else Modifier).fillMaxWidth().then(Modifier.clickable { if (!ticked.isNullOrEmpty()) onTick(exercise.name) else open() }).holdMenu(cardShape(), rowActions?.let { f -> { f(exercise.name) } }).padding(start = Space.l, end = if (ticked != null) Space.xs else Space.l, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        // With animations on the phone, each exercise that has one shows a still of it.
                        if (thumbs) ExerciseThumb(exercise.name)
                        Column(Modifier.weight(1f)) {
                            // The name wraps and is never cut.
                            MorphText(androidx.compose.ui.text.AnnotatedString(exercise.name), "title", style = MaterialTheme.typography.bodyLarge)
                            // Done: its last day and best set. Never done: its main muscle and equipment.
                            Text(
                                if (exercise.sessions > 0) listOfNotNull(prettyDay(exercise.lastDate), bestSetLine(exercise, unitOf(exercise.name))).joinToString(" · ")
                                else listOfNotNull(musclesOf(exercise.name).primary.firstOrNull()?.label, equipmentOf(exercise.name).takeIf { it != "Other" }).joinToString(" · ").ifEmpty { "Not logged yet" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (exercise.name in favourites) Icon(CleanIcons.StarLine, "Favourite", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        if (ticked != null) androidx.compose.material3.Checkbox(exercise.name in ticked, { onTick(exercise.name) }, Modifier.semantics { contentDescription = "Pick ${exercise.name}" })
                        }
                        }
                        HorizontalDivider()
                    }
                }
                if (hidden > 0) item(key = "hidden") {
                    Text(
                        "$hidden built-in exercises you have not used are hidden. Search to find one, or tap here to list them all.",
                        Modifier.fillMaxWidth().clickable { showAll = true }.padding(Space.l),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "bar") { BarSpace() }
            }
            // The letter rail: touch or drag along it to jump; the letter in view is highlighted.
            if (letters.size > 1) {
                val textScale = LocalTextScale.current
                Column(
                    // A drag-only shortcut: a screen reader goes through the list itself, so the rail is left out of what it reads.
                    Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight().padding(bottom = LocalBarInset.current).clearAndSetSemantics {}.pointerInput(letters, rows, offset) {
                        fun jump(y: Float) {
                            val letter = letters[(y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
                            val index = rows.indexOfFirst { it.heading != null && it.letter == letter }
                            if (index >= 0) scope.launch { state.scrollToItem(index + offset) }
                        }
                        awaitEachGesture {
                            jump(awaitFirstDown().position.y)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { jump(it.position.y); it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for (letter in letters) {
                        val on = letter == current
                        Text(
                            letter.toString(), fontSize = 10.sp * textScale, lineHeight = 11.sp * textScale,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (on) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = Space.xs) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExercisesTab(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val all = remember(workouts, settings) { allExercises(workouts, settings.keys) }
    val hide = true // Built-in exercises stay out of the list until searched for (the old Lists choice is fixed since 1.1.0).
    // The Performed chip (at least one logged session in this profile), remembered under the old key.
    var show by model.remembered("exercises.show", "all")
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        var name by remember { mutableStateOf("") }
        // An exercise to start as a copy of: logged the same way, with the same muscles.
        var copyFrom by remember { mutableStateOf<String?>(null) }
        var choosing by remember { mutableStateOf(false) }
        if (choosing) ExercisePicker(model, workouts, onPick = { copyFrom = it; choosing = false }, onDismiss = { choosing = false })
        val exists = all.any { it.name == name.trim() }
        CleanDialog(
            onDismissRequest = { creating = false },
            title = { Text("New exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("Lat Pulldown (Rope)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (exists) "There is already an exercise with this name."
                        else if (copyFrom != null) "Starts out logged like $copyFrom, with its muscles. Its page opens next, ready to change."
                        else "How it is logged and which muscles it trains are worked out from the name. Its page opens next, ready to change.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!exists) TextButton(onClick = { choosing = true }) { Text(if (copyFrom == null) "Start as a copy of…" else "Copy of $copyFrom") }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val made = name.trim()
                    // Storing its automatic values is what makes it exist before it is first logged.
                    val from = copyFrom
                    if (!exists) model.putSetting(made) { s ->
                        if (from == null) s else s.copy(loadType = model.loadTypeOf(from).name, unit = settings[from]?.unit, muscles = model.storedMusclesOf(from).encode())
                    }
                    creating = false
                    model.open(Screen.ExerciseDetail(made, edit = !exists))
                }) { Text(if (exists) "Open" else "Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
    val header: @Composable () -> Unit = { Title("Library") { IconButton(onClick = { creating = true }) { Icon(CleanIcons.Add, "New exercise") } } }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        header()
        // Keyed on the settings so the list follows a change made on an exercise's page.
        val musclesOf = remember(settings) { { name: String -> model.musclesOf(name) } }
        ExerciseList(
            all, musclesOf, onClick = { model.open(Screen.ExerciseDetail(it)) }, unitOf = model::unitOf,
            // A search that finds nothing offers to make the exercise, as the New exercise button does.
            onCreate = { made -> model.putSetting(made) { it }; model.open(Screen.ExerciseDetail(made, edit = true)) }, createWhenEmpty = true,
            favourites = settings.filterValues { it.favourite }.keys, hideLibrary = hide, tab = clean.app.Tab.Exercises,
            listState = model.tabLists.getValue(clean.app.Tab.Exercises),
            rowActions = { name ->
                val starred = settings[name]?.favourite == true
                listOf(
                    HoldAction("Open", CleanIcons.Forward) { model.open(Screen.ExerciseDetail(name)) },
                    HoldAction(if (starred) "Unfavourite" else "Favourite", if (starred) CleanIcons.Star else CleanIcons.StarLine) { model.putSetting(name) { it.copy(favourite = !starred) } },
                    HoldAction("Edit", CleanIcons.Edit) { model.open(Screen.ExerciseDetail(name, edit = true)) },
                )
            },
            performedOnly = show == "performed", onPerformedOnly = { show = if (it) "performed" else "all" },
        )
    }
    }
}

/** A line of muscle names on the exercise page; each name opens that muscle's page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleLine(label: String, muscles: Set<Muscle>, onOpen: (Muscle) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (muscles.isEmpty()) Text(NONE)
        FlowRow(Modifier.weight(1f).padding(start = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End)) {
            for (m in muscles) Text(m.label, Modifier.clickable { onOpen(m) })
        }
    }
}

val EXERCISE_TABS = listOf("Info", "History", "Charts", "PRs")
private const val INFO = 0
private const val HISTORY = 1
private const val CHARTS = 2
private const val PRS = 3
/** The Charts tab's choice for the imbalance chart (-1 is "All"). */
private const val IMBALANCE_CHART = -2

private fun loadTypeLabel(type: LoadType) = when (type) {
    LoadType.NORMAL -> "Weight"
    LoadType.BODYWEIGHT -> "Bodyweight +"
    LoadType.ASSISTED -> "Assisted"
    LoadType.TIMED -> "Timed"
    LoadType.CARDIO -> "Cardio"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(model: AppModel, name: String, startEditing: Boolean = false, startTab: Int? = null) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val profile by model.profile.collectAsState()
    val unit = settings[name]?.unit ?: profile?.unit ?: "kg"
    // Read through the model so the automatic values apply; keyed on the settings so an edit shows at once.
    val loadType = remember(settings, name) { model.loadTypeOf(name) }
    val use = remember(settings, name) { model.musclesOf(name) }
    val sessions = remember(workouts, name) { model.sessionsIn(workouts, name) }
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    // Each side's records, and the comparable figure (the weaker side's) that the rows and headers show; an exercise never done left and right has one.
    val sided = remember(sessions, loadType, bodyweights, rule) { recordsBySide(sessions, rule, loadType, bodyweights::at) }
    val records = sided.comparable
    val hasSides = remember(sessions) { sessions.any { it.paired } }
    val latestGap = remember(sessions, loadType, bodyweights, rule) { if (hasSides) latestImbalance(sessions, rule, loadType, bodyweights::at) else null }
    // Each session's chart figures; a left and right session also carries each side's, for the charts that draw a split.
    val sideProgress = remember(sessions, loadType, bodyweights, rule) { progressBySide(sessions, rule, loadType, bodyweights::at) }
    val progress = remember(sideProgress) { sideProgress.map { it.comparable } }
    val variations = remember(settings, name) { model.variationsOf(name) }
    var addingVariation by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var showRest by remember { mutableStateOf(false) }
    // Nothing on this page changes the exercise until Edit is pressed.
    var editing by remember { mutableStateOf(startEditing) }
    // How a logged weight reads for this kind of lift: added to bodyweight, or taken off it.
    val sign = when (loadType) {
        LoadType.BODYWEIGHT -> "+"
        LoadType.ASSISTED -> "−"
        else -> ""
    }

    @Composable
    fun line(label: String, record: Record?, left: Record? = null, right: Record? = null, value: (Record) -> String) {
        if (record == null) return
        Column {
            // A PR opens the workout it was set in.
            Row(Modifier.fillMaxWidth().clickable { model.open(Screen.WorkoutDetail(record.date)) }, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(prettyDay(record.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value(record), style = MaterialTheme.typography.titleMedium)
            }
            // The figure above is the weaker side's; under it each side's own, each opening the workout it was set in.
            if (hasSides && (left != null || right != null)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                for ((letter, word, r) in listOf(Triple("L", "Left", left), Triple("R", "Right", right))) {
                    val shown = r?.let(value) ?: NONE
                    Text(
                        "$letter $shown",
                        Modifier.weight(1f).then(if (r != null) Modifier.clickable { model.open(Screen.WorkoutDetail(r.date)) } else Modifier)
                            .semantics { contentDescription = "$word side, ${if (r == null) "none" else shown}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    fun weight(kg: Double) = "${showWeight(kg, unit)} $unit"
    fun from(r: Record) = if (r.weight != null && r.reps != null) " ($sign${showWeight(r.weight!!, unit)} × ${r.reps})" else ""
    fun shown(kg: Double) = if (unit == "lb") kg / KG_PER_LB else kg

    // The page in four tabs. It opens on the one asked for (History, from a workout in progress), else on the one last used.
    var current by remember { mutableStateOf(startTab ?: model.options.value["ui.exerciseTab"]?.toIntOrNull() ?: INFO) }
    val tab = if (editing) INFO else current.coerceIn(0, EXERCISE_TABS.lastIndex)
    val allPrs by model.prs.collectAsState()
    val byDate = remember(workouts) { workouts.associateBy { it.date } }
    // The goal as kilos and reps, when one is set.
    val goal = parseGoal(settings[name]?.goal)
    // The first session that met it, and the double progression scheme if there is one.
    val goalReached = remember(sessions, goal, loadType, bodyweights, rule) { goal?.let { (kg, reps) -> goalReachedOn(sessions, kg, reps, rule, loadType, bodyweights::at) } }
    val goalProgress = remember(sessions, goal, loadType, bodyweights, rule) { goal?.let { (kg, reps) -> exerciseGoalProgress(sessions, kg, reps, rule, loadType, bodyweights::at, LocalDate.now()) } }
    val scheme = model.progressions.collectAsState().value[name]
    val sides = name in model.sideExercises.collectAsState().value
    val listState = rememberLazyListState()
    // A date the History tab is to scroll to once it is showing (the imbalance line on the Info tab).
    var jumpTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, jumpTo) {
        val date = jumpTo ?: return@LaunchedEffect
        if (tab != HISTORY) return@LaunchedEffect
        listState.scrollToItem(sessions.reversed().indexOfFirst { it.date == date }.coerceAtLeast(0))
        jumpTo = null
    }
    var progressing by remember { mutableStateOf(false) }
    val plateau = true
    // The goal as a level on the chart it applies to: the estimated 1RM it stands for, or, past the rep limit, the weight.
    fun goalLine(chart: String): Double? {
        val (kg, reps) = goal ?: return null
        return when {
            chart == "Est. 1RM" -> goalOneRm(kg, reps, rule, loadType, bodyweights.at(strongDate(System.currentTimeMillis())))?.let(::shown)
            (chart == "Top weight" || chart == "Least help") && reps > rule.maxReps -> shown(kg)
            else -> null
        }
    }
    val notes = remember(workouts, name) {
        workouts.reversed().flatMap { w -> w.blocks.filter { it.exercise == name }.flatMap { b -> b.notes.filter { it.text.isNotBlank() }.map { w.date to it.text } } }
    }
    // The tabs' underline glides and stretches between tabs and follows a swipe, and the content slides with the finger (Motion.pill, Motion.slide).
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    var drag by remember { mutableStateOf<Float?>(null) }
    var width by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    val stepped = remember { BooleanArray(1) }
    val underline = rememberStretch(tab) { drag?.let { (tab - it / width).coerceIn(0f, EXERCISE_TABS.lastIndex.toFloat()) } }
    var shown by remember { androidx.compose.runtime.mutableIntStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab == shown) return@LaunchedEffect
        val dir = if (tab > shown) 1 else -1
        shown = tab
        if (!motion) { slide.snapTo(0f); return@LaunchedEffect }
        slide.snapTo(dir * width / 3f)
        slide.animateTo(0f, Motion.slide())
    }
    Scaffold(containerColor = pageContainer(), topBar = {
      Column {
        BackBar(name, model::back) {
            // A favourite sits above everything else in the list and in pickers.
            val starred = settings[name]?.favourite == true
            IconButton(onClick = { model.putSetting(name) { it.copy(favourite = !starred) } }) {
                Icon(if (starred) CleanIcons.Star else CleanIcons.StarLine, if (starred) "Remove from favourites" else "Add to favourites", tint = if (starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            }
            // Editing happens on the Info tab.
            QuietButton(if (editing) "Done" else "Edit", { editing = !editing; if (editing) current = INFO }, Modifier.padding(end = Space.s), contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s))
        }
        TabRow(selectedTabIndex = tab, indicator = { positions -> StretchIndicator(underline, positions) }) {
            EXERCISE_TABS.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { editing = false; current = i; if (startTab == null) model.setOption("ui.exerciseTab", i.toString()) }, text = { Text(title) }) }
        }
      }
    }) { padding ->
        // A sideways swipe goes to the tab before or after.
        fun select(i: Int) {
            if (i !in EXERCISE_TABS.indices) return
            stepped[0] = true
            editing = false
            current = i
            if (startTab == null) model.setOption("ui.exerciseTab", i.toString())
        }
        // Opening a tab of the page settles its first cards in, as a page does; the tab the page opened on is the page's own (Roll.kt).
        val firstTab = remember { tab }
        val tabSettle = remember(tab) { if (tab == firstTab) null else SettleState(armed = true) }
        LaunchedEffect(tabSettle) { if (tabSettle != null) { kotlinx.coroutines.delay(Motion.settleWindowMillis()); tabSettle.armed = false } }
        androidx.compose.runtime.CompositionLocalProvider(LocalSettle provides (tabSettle ?: LocalSettle.current)) {
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().onSizeChanged { width = it.width.coerceAtLeast(1) }
                .swipeSteps(onPrevious = { select(tab - 1) }, onNext = { select(tab + 1) }, follow = { dx ->
                    // The content goes with the finger (less where there is no tab to go to); letting go without a step brings it back.
                    if (dx != null) {
                        if (drag == null) stepped[0] = false
                        drag = dx
                    } else {
                        val d = drag ?: 0f
                        drag = null
                        if (motion && d != 0f) scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            // A step brings the next tab's content in from the side it is going; no step brings this one back to rest.
                            if (stepped[0]) { slide.snapTo(if (d < 0f) width / 3f else -width / 3f); slide.animateTo(0f, Motion.slide()) }
                            else { slide.snapTo(sideward(d, tab)); slide.animateTo(0f, Motion.follow()) }
                        }
                    }
                })
                .graphicsLayer {
                    if (!motion) return@graphicsLayer
                    val off = drag?.let { sideward(it, tab) } ?: slide.value
                    translationX = off
                    alpha = 1f - (abs(off) / (width / 3f)).coerceIn(0f, 1f) * 0.8f
                },
            state = listState,
        ) {
            if (tab == INFO && editing) {
                item {
                    Section("How this exercise is logged", Modifier.settleNext()) {
                        Choice(LoadType.entries, loadType, ::loadTypeLabel) { type -> model.putSetting(name) { it.copy(loadType = type.name) } }
                        Choice(listOf(null, "kg", "lb"), settings[name]?.unit, { it ?: "Profile unit" }) { u -> model.putSetting(name) { it.copy(unit = u) } }
                    }
                }
                item {
                    Section("Goal", Modifier.settleNext()) {
                        Text("A set to work towards. The Info tab then shows how close your best estimated 1RM is to it. Leave both empty for none.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var goalWeight by remember { mutableStateOf(goal?.first?.let { entryWeight(it, unit) }.orEmpty()) }
                        var goalReps by remember { mutableStateOf(goal?.second?.toString().orEmpty()) }
                        fun store() {
                            val kg = parseNumber(goalWeight)?.let { toKg(it, unit) }
                            val reps = goalReps.trim().toIntOrNull()
                            if (kg != null && reps != null && reps > 0) model.putSetting(name) { it.copy(goal = "${kg}x$reps") }
                            else if (goalWeight.isBlank() && goalReps.isBlank()) model.putSetting(name) { it.copy(goal = "") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            OutlinedTextField(goalWeight, { goalWeight = it; store() }, label = { Text("Weight ($unit)") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(goalReps, { goalReps = it; store() }, label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Section("Muscles", Modifier.settleNext()) {
                        Text("Tap a muscle to step it through main (a full set), supporting (half), minor (a quarter) and off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (muscle in Muscle.entries) {
                                val main = muscle in use.primary
                                val support = muscle in use.secondary
                                val minor = muscle in use.minor
                                CleanChip(
                                    selected = main || support || minor,
                                    onClick = {
                                        // Main, then supporting, then minor, then off.
                                        val next = when {
                                            main -> use.copy(primary = use.primary - muscle, secondary = use.secondary + muscle)
                                            support -> use.copy(secondary = use.secondary - muscle, minor = use.minor + muscle)
                                            minor -> use.copy(minor = use.minor - muscle)
                                            else -> use.copy(primary = use.primary + muscle)
                                        }
                                        // Only heads the user stored are kept; the rules' heads are never written.
                                        val stored = model.storedMusclesOf(name).heads.filterKeys { it.group in next.primary || it.group in next.secondary }
                                        model.putSetting(name) { it.copy(muscles = next.copy(heads = stored).encode()) }
                                    },
                                    label = { Text(if (support) "${muscle.label} (support)" else if (minor) "${muscle.label} (minor)" else muscle.label) },
                                )
                            }
                        }
                    }
                }
                item {
                    Section("Variations", Modifier.settleNext()) {
                        Text(
                            "Exercises grouped here are variations of one overall exercise. Each keeps its own history and PRs, and a workout can switch between them from the exercise's options.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(variations.filter { it != name }.joinToString(", ").ifEmpty { "None yet." })
                        TextButton(onClick = { addingVariation = true }) { Text("Add a variation") }
                        TextButton(onClick = { model.open(Screen.NoteReview(name)) }) { Text("Find variations in this exercise's notes") }
                        if (variations.isNotEmpty()) TextButton(onClick = { model.leaveGroup(name) }) { Text("Take this exercise out of the group") }
                    }
                }
                item {
                    Section("Name", Modifier.settleNext()) {
                        Text(
                            "Two names for one exercise, or a typo: change the name on every set logged under it, or merge it into the exercise it should have been. It can be undone from Settings, Data.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { renaming = true }) { Text("Rename or merge…") }
                    }
                }
                if (settings[name] != null) item {
                    // An exercise that exists only because the user made it can be deleted; anything else can be put back as it was.
                    val own = sessions.isEmpty() && libraryExercise(name) == null
                    Section(if (own) "Delete" else "Reset") {
                        Text(
                            if (own) "This exercise has never been logged, so deleting it removes it completely."
                            else "Puts back the automatic way of logging, muscles and grouping, and removes its sticky note. Logged workouts are not touched.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { model.removeSetting(name, if (own) "Exercise deleted" else "Exercise reset"); if (own) model.back() else editing = false }) {
                            Text(if (own) "Delete this exercise" else "Reset this exercise", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (tab == INFO) {
                item {
                    // The steps for a built-in exercise, read from the bundled file; nothing for one of the user's own.
                    val context = LocalContext.current
                    val info by produceState<ExerciseInfo?>(null, name) { value = ExerciseInfos.of(context, name) }
                    info?.let { how ->
                        Section("How to do it", spacing = Space.s) {
                            ExerciseAnimation(how.media)
                            how.steps.forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    Text("${i + 1}", Modifier.width(18.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(step, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (variations.size > 1) {
                    item {
                        // The roll-up: every variation of the overall exercise with its best estimated 1RM.
                        Section("Variations of ${model.overallOf(name)}", Modifier.settleNext()) {
                            LabelValue("All variations together", "history and charts ›") { model.open(Screen.Overall(name)) }
                            // The ones that have been done, with their best; the rest of the built-in ones on request.
                            val done = variations.filter { it == name || model.hasHistory(it) }
                            val bests = remember(workouts, done, bodyweights, settings, rule) { done.associateWith { recordsOf(model.sessionsIn(workouts, it), rule, model.loadTypeOf(it), bodyweights::at).estimated1Rm } }
                            for (v in done) {
                                val u = model.unitOf(v)
                                val best = bests[v]
                                Row(Modifier.fillMaxWidth().clickable(enabled = v != name) { model.open(Screen.ExerciseDetail(v)) }.padding(vertical = Space.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(v, Modifier.weight(1f), color = if (v == name) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                                    Text(best?.let { "1RM ${showEstimate(it.value, u)} $u" } ?: NONE, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // The built-in ones made for this exercise come first; each can be taken off the list.
                            val own = model.suggestedVariations(name).filter { it !in done }
                            val rest = own + (variations - done.toSet() - own.toSet())
                            if (rest.isNotEmpty()) {
                                Text(if (showRest) "Not done yet" else "${rest.size} more not done yet ›", Modifier.clickable { showRest = !showRest }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (showRest) for (v in rest) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(v, Modifier.weight(1f).clickable { model.open(Screen.ExerciseDetail(v)) }.padding(vertical = Space.xs), color = MaterialTheme.colorScheme.primary)
                                    Box(Modifier.size(26.dp).reach().clip(CircleShape).clickable { model.leaveGroup(v) }, contentAlignment = Alignment.Center) {
                                        Icon(CleanIcons.Close, "Remove this variation", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Section("", Modifier.settleNext()) {
                        // Other names the exercise goes by, in other apps or in the gym; search finds it under these too.
                        val others = otherNames(name)
                        if (others.isNotEmpty()) {
                            Column {
                                Text("Also called", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(others.joinToString(", "))
                            }
                        }
                        // A lift that counts bodyweight leads to the weigh-ins it depends on.
                        LabelValue(
                            "Logged as", loadTypeLabel(loadType) + if (loadType == LoadType.CARDIO) "" else ", $unit",
                            onClick = if (loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED) ({ model.open(Screen.MeasurementDetail("weight.csv")) }) else null,
                        )
                        scheme?.let { LabelValue("Progression", "${it.minReps}\u2013${it.maxReps} reps, +${showWeight(it.step, unit)} $unit") { progressing = true } }
                        // One tap turns it on or off; a workout's card draws its sets as pairs while it is on.
                        LabelValue("Left and right", if (sides) "On" else "Off") { model.setSides(name, !sides) }
                        if (hasSides) {
                            // The latest session with both sides to compare; it opens that workout on the History tab.
                            latestGap?.let { (date, gap) ->
                                val said = gap?.sentence(name) ?: "$name: both sides even"
                                LabelValue("Imbalance", (gap?.detail() ?: "Even") + " ›", spoken = "Imbalance, $said, on ${prettyDay(date)}. Opens the history") { editing = false; current = HISTORY; jumpTo = date }
                            }
                            val rounds = roundsLogged(sessions)
                            LabelValue("Rounds logged", roundsText(rounds), spoken = "Rounds logged, ${roundsText(rounds)}")
                        }
                        if (!use.isEmpty) {
                            val openMuscle = { m: Muscle -> model.open(Screen.MuscleDetail(m)) }
                            MuscleLine("Main muscles", use.primary, openMuscle)
                            if (use.secondary.isNotEmpty()) MuscleLine("Supporting", use.secondary, openMuscle)
                            if (use.minor.isNotEmpty()) MuscleLine("Minor", use.minor, openMuscle)
                        } else if (loadType != LoadType.CARDIO) {
                            Text("No muscles set. Press Edit to choose them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (goal != null) item {
                    val (kg, reps) = goal
                    Section("Goal", Modifier.settleNext()) {
                        LabelValue("Working towards", "$sign${weight(kg)} × $reps")
                        goalReached?.let { day -> LabelValue("Reached on", prettyDay(day) + " ›") { model.open(Screen.WorkoutDetail(day)) } }
                        if (reps > rule.maxReps) {
                            // Too many reps for an estimate: a set must be at least that weight for at least those reps.
                            // A left and right exercise has to do it on both sides: the weaker side's most.
                            fun mostOn(side: Side?) = sessions.flatMap { (if (side == null) it else it.forSide(side)).sets }
                                .filter { it.type != SetType.WARMUP && it.weight != null && (if (loadType == LoadType.ASSISTED) it.weight!! <= kg else it.weight!! >= kg) }
                                .maxOfOrNull { it.reps ?: 0 } ?: 0
                            val most = if (hasSides) minOf(mostOn(Side.LEFT), mostOn(Side.RIGHT)) else mostOn(null)
                            LinearProgressIndicator(progress = { (most.toFloat() / reps).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
                            Text(
                                if (most >= reps) "Reached: a set of ${weight(kg)} for $most reps."
                                else "Most reps at ${weight(kg)} or ${if (loadType == LoadType.ASSISTED) "less help" else "more"}: $most of $reps (${Math.round(most * 100f / reps)}%).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // How far the best estimated 1RM has come towards the goal's.
                            val target = (realLoad(loadType, kg, bodyweights.at(strongDate(System.currentTimeMillis()))) ?: kg).let { estimate1Rm(it, reps, rule) ?: it }
                            val best = records.estimated1Rm?.value ?: 0.0
                            LinearProgressIndicator(progress = { (best / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
                            Text(
                                if (best >= target) "Reached: your best estimated 1RM is ${weight(Math.round(best * 10) / 10.0)}, the goal's is ${weight(Math.round(target * 10) / 10.0)}."
                                else "Best estimated 1RM ${weight(Math.round(best * 10) / 10.0)} of ${weight(Math.round(target * 10) / 10.0)} (${Math.round(best / target * 100)}%).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Where the last eight weeks' trend reaches it, when it says anything.
                        goalProgress?.projected?.let { day -> Text(projectedLine(day, LocalDate.now()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (plateau && stalled(progress)) item {
                    Section("Stalled", Modifier.settleNext()) {
                        Text("None of the last five sessions set a new best estimated 1RM. A change of reps, weight or variation may get it moving.", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (notes.isNotEmpty()) item {
                    // Every note written on this exercise, newest first; each opens its workout.
                    Section("Notes", Modifier.settleNext()) { for ((date, text) in notes.take(40)) LinkRow(text, prettyDay(date), "") { model.open(Screen.WorkoutDetail(date)) } }
                }
            }
            if (tab == CHARTS) item { Box {
                // Each chart is one value per session; only the ones this kind of exercise has are offered.
                val charts: List<Triple<String, String, (Progress) -> Double?>> = when (loadType) {
                    LoadType.TIMED -> listOf(
                        Triple("Longest hold", "s", { p: Progress -> p.longestSeconds }),
                        Triple("Total time", "s", { p: Progress -> p.totalSeconds }),
                    )
                    LoadType.CARDIO -> listOf(
                        Triple("Distance", "km", { p: Progress -> p.totalMeters?.div(1000) }),
                        Triple("Time", "min", { p: Progress -> p.totalSeconds?.div(60) }),
                    )
                    else -> listOf(
                        Triple("Est. 1RM", unit, { p: Progress -> p.estimated1Rm?.let { tenth(shown(it)) } }),
                        Triple(if (loadType == LoadType.ASSISTED) "Least help" else "Top weight", unit, { p: Progress -> p.topWeight?.let(::shown) }),
                        Triple("Volume", unit, { p: Progress -> p.volume?.let(::shown) }),
                        Triple("Most reps", "reps", { p: Progress -> p.maxReps?.toDouble() }),
                    )
                }
                // -1 is "All": every measure in one chart.
                var chart by remember(loadType) { mutableStateOf(-1) }
                var range by remember { mutableStateOf(0L) }
                // The imbalance chart is offered once a session has both sides to compare; it is not part of "All".
                val hasImbalance = remember(sideProgress) { imbalanceSeries(sideProgress) != null }
                Section("Progress", Modifier.settleNext()) {
                  if (sessions.size < 2) EmptyPreview("Log ${2 - sessions.size} more ${if (sessions.isEmpty()) "sessions" else "session"} of $name to see its chart.") { PreviewSamples.Line() }
                  else {
                    Choice(listOf(-1) + charts.indices + (if (hasImbalance) listOf(IMBALANCE_CHART) else emptyList()), chart, { if (it == IMBALANCE_CHART) "Imbalance" else if (it < 0) "All" else charts[it].first }) { chart = it }
                    RangeChoice(listOf(90L, 365L, 0L), range, { if (it == 0L) "All" else if (it == 90L) "3M" else "1Y" }) { range = it }
                    val (_, chartUnit, value) = charts[chart.coerceIn(charts.indices)]
                    val from = if (range == 0L) null else LocalDate.now().minusDays(range)
                    val points = chartPoints(sideProgress, from, value)
                    val openDay = { day: LocalDate -> sessions.lastOrNull { it.date.startsWith(day.toString()) }?.let { model.open(Screen.WorkoutDetail(it.date)) }; Unit }
                    if (chart == IMBALANCE_CHART) {
                        val gaps = imbalanceSeries(sideProgress, from)
                        if (gaps == null || gaps.points.size < 2) Text("Not enough sessions with both sides in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else SplitLineChart(gaps.points, "%", title = "$name · Imbalance by ${gaps.measure}", imbalance = true, onTap = openDay)
                    } else if (chart < 0) {
                        val series = seriesColors()
                        val lines = charts.mapIndexed { i, (label, lineUnit, measure) ->
                            ChartSeries(label, lineUnit, series[i % series.size], chartPoints(sideProgress, from, measure))
                        }.filter { it.points.size >= 2 }
                        if (lines.isEmpty()) Text("Not enough sessions in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else MultiLineChart(lines, name, morph = "chart:1rm", morphLabel = "Est. 1RM")
                    } else if (points.size < 2) Text("Not enough sessions in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else SplitLineChart(
                        points, chartUnit, trend = true, title = "$name · ${charts[chart.coerceIn(charts.indices)].first}",
                        goal = goalLine(charts[chart.coerceIn(charts.indices)].first), onTap = openDay,
                        morph = if (charts[chart.coerceIn(charts.indices)].first == "Est. 1RM") "chart:1rm" else null,
                    )
                  }
                }
            } }
            if (tab == PRS) item {
                Section("PRs", Modifier.settleNext()) {
                    if (sessions.isEmpty()) Text("Not logged yet.")
                    line(
                        when (loadType) {
                            LoadType.BODYWEIGHT -> "Most added weight"
                            LoadType.ASSISTED -> "Least assistance"
                            else -> "Heaviest weight"
                        },
                        records.weight, sided.left.weight, sided.right.weight,
                    ) { "$sign${weight(it.value)} × ${it.reps}" }
                    line("Most reps", records.reps, sided.left.reps, sided.right.reps) { "${it.reps}" + (it.weight?.let { w -> " at ${weight(w)}" } ?: "") }
                    line("Longest hold", records.longest, sided.left.longest, sided.right.longest) { "${shortNumber(it.value)} s" + (it.weight?.takeIf { w -> w > 0 }?.let { w -> " at $sign${weight(w)}" } ?: "") }
                    line("Estimated 1RM", records.estimated1Rm, sided.left.estimated1Rm, sided.right.estimated1Rm) { weight(it.value) + from(it) }
                    line("Best set volume", records.setVolume, sided.left.setVolume, sided.right.setVolume) { weight(it.value) + from(it) }
                    line("Best session volume", records.sessionVolume, sided.left.sessionVolume, sided.right.sessionVolume) { weight(it.value) }
                    if (sessions.size > 1) LabelValue("PR history", "every record broken ›") { model.open(Screen.PrTimeline(name)) }
                    if (loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED) {
                        Text(
                            if (records.missingBodyweight) "Estimated 1RM and volume use your real load (bodyweight $sign the logged weight). Sets with no weigh-in within $BODYWEIGHT_WINDOW_DAYS days of the workout are left out of them."
                            else "Estimated 1RM and volume use your real load (bodyweight $sign the logged weight).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (tab == PRS && records.repsAtWeight.isNotEmpty()) {
                item {
                    Section("Best reps at each weight", Modifier.settleNext()) {
                        for (r in records.repsAtWeight) {
                            LabelValue(weight(r.weight ?: 0.0), "${r.reps} reps · ${prettyDay(r.date)}") { model.open(Screen.WorkoutDetail(r.date)) }
                            // Each side's own best at the weight, where the exercise has been done left and right.
                            if (hasSides) {
                                val l = sided.left.repsAtWeight.firstOrNull { it.weight == r.weight }?.reps?.toString()
                                val rt = sided.right.repsAtWeight.firstOrNull { it.weight == r.weight }?.reps?.toString()
                                val text = sidesLine(l, rt)
                                if (text != null) Text(text, Modifier.semantics { contentDescription = "${weight(r.weight ?: 0.0)}, ${sidesSpoken(l, rt)} reps" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (tab == PRS && records.longestAtWeight.isNotEmpty()) {
                item {
                    Section("Longest hold at each weight", Modifier.settleNext()) {
                        for (r in records.longestAtWeight) {
                            LabelValue(weight(r.weight ?: 0.0), "${shortNumber(r.value)} s · ${prettyDay(r.date)}") { model.open(Screen.WorkoutDetail(r.date)) }
                            if (hasSides) {
                                val l = sided.left.longestAtWeight.firstOrNull { it.weight == r.weight }?.let { "${shortNumber(it.value)} s" }
                                val rt = sided.right.longestAtWeight.firstOrNull { it.weight == r.weight }?.let { "${shortNumber(it.value)} s" }
                                val text = sidesLine(l, rt)
                                if (text != null) Text(text, Modifier.semantics { contentDescription = "${weight(r.weight ?: 0.0)}, ${sidesSpoken(l, rt)}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (tab == PRS && records.estimated1Rm != null) {
                item {
                    // The best estimated 1RM run back through the chosen formula.
                    val best = records.estimated1Rm!!.value
                    Section("Rep maxes", Modifier.settleNext()) {
                        for (n in listOf(1, 2, 3, 5, 8, 10)) LabelValue("${n}RM", "≈ " + weight(Math.round(weightForReps(best, n, rule) * 10) / 10.0))
                        Text("Worked out from the best estimated 1RM by the ${rule.formula.label} formula.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (tab == HISTORY && sessions.isEmpty()) item { Section("") { Text("Not logged yet.") } }
            // One card per workout, laid out like the History tab's: its name and date, then this exercise's sets.
            if (tab == HISTORY) items(sessions.reversed(), key = { it.date }) { session ->
                val workout = byDate[session.date]
                // This exercise's sets sit among all the workout's sets; their PRs are found by position.
                val kinds: List<Set<PrKind>> = workout?.blocks?.flatMap { b -> b.sets.map { b.exercise } }.orEmpty().withIndex().filter { it.value == name }.map { allPrs[session.date]?.getOrNull(it.index).orEmpty() }
                // This exercise's blocks of the workout: one logged left and right is drawn as its rounds.
                val mine = workout?.blocks?.filter { it.exercise == name }.orEmpty()
                val bodyweight = bodyweights.at(session.date)
                // Looked at between sets, so many sessions should fit on the screen: small text, little air inside and between.
                val small = MaterialTheme.typography.bodyMedium
                CompositionLocalProvider(LocalCardKey provides "page:exercise:$name:${session.date}") {
                Section("", onClick = { model.open(Screen.WorkoutDetail(session.date)) }, spacing = Space.xs, inner = Space.s, outer = Space.xs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MorphText(workout?.name ?: "Workout", "title", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(prettyDay(session.date), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        @Composable
                        fun setRows(sets: List<SetItem>, setKinds: List<Set<PrKind>>) {
                            // Numbered as the logging screen does: warm-ups and drop sets take no number.
                            val labels = setLabels(sets.map { it.type })
                            for ((i, set) in sets.withIndex()) {
                                val w = set.weight
                                val r = set.reps
                                val estimate = if (w != null && r != null) realLoad(loadType, w, bodyweight)?.let { estimate1Rm(it, r, rule, set.rpe) } else null
                                Row {
                                    Text(labels[i], Modifier.width(36.dp), style = small, color = typeColor(set.type), fontWeight = FontWeight.Bold)
                                    Text((if (w != null && r != null) sign else "") + setValues(set, unit), Modifier.weight(1f), style = small)
                                    Text(estimate?.let { "1RM " + showWeight(Math.round(it).toDouble(), unit) } ?: "", style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                PrBadges(setKinds.getOrNull(i).orEmpty(), Modifier.padding(start = 36.dp))
                            }
                        }
                        if (mine.isEmpty()) setRows(session.sets, kinds)
                        else {
                            var at = 0
                            for (block in mine) {
                                val own = kinds.drop(at).take(block.sets.size)
                                at += block.sets.size
                                if (!block.paired) setRows(block.sets, own)
                                else {
                                    RoundRows(model, block, own, { estimateOf(it, loadType, bodyweight, rule) }) { model.open(Screen.WorkoutDetail(session.date)) }
                                    // How far one side lagged, as on the workout's page.
                                    imbalanceOf(block, session.date, rule, loadType, bodyweights::at)?.let { gap ->
                                        Text(gap.caption(), Modifier.padding(start = 36.dp).semantics { contentDescription = gap.sentence(name) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        }
    }
    if (addingVariation) ExercisePicker(model, workouts, onPick = { model.addVariation(name, it); addingVariation = false }, onDismiss = { addingVariation = false })
    if (progressing) ProgressionDialog(model, name, unit, sessions) { progressing = false }
    if (renaming) RenameDialog(model, name) { renaming = false }
}

/** How far the content of the exercise page follows a drag of [dx] px on tab [tab]: all of it, and less where there is no tab to go to. */
private fun sideward(dx: Float, tab: Int): Float = if ((tab == 0 && dx > 0f) || (tab == EXERCISE_TABS.lastIndex && dx < 0f)) dx * 0.3f else dx

/** The tab row's underline: the same one as the default, but it glides and stretches between tabs and follows a swipe ([span], see TabStretch.kt). */
@Composable
private fun StretchIndicator(span: () -> Span, positions: List<TabPosition>) {
    androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
        Modifier.layout { measurable, constraints ->
            val s = span()
            val left = s.left { positions[it.coerceIn(positions.indices)].left.toPx() }
            val right = s.right { positions[it.coerceIn(positions.indices)].right.toPx() }
            val w = (right - left).roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left.roundToInt(), constraints.maxHeight - placeable.height) }
        },
    )
}
