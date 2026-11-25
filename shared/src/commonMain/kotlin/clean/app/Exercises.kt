package clean.app

import clean.core.time.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import clean.app.ui.theme.LocalTextScale
import clean.app.ui.theme.cardShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.produceState
import clean.app.platform.LocalPlatform
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.IntrinsicSize
import clean.app.ui.theme.SmallShape
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
import clean.app.ui.theme.orange
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.datetime.LocalDate
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
    expanded: Boolean, suggested: List<String> = emptyList(), stillWanted: () -> Unit = {},
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
    // Replacing an exercise: the swaps worth trying come first (same main muscles, what the user has done, equipment they have used).
    if (suggested.isNotEmpty() && !anyFilterOn(muscle, equipment, performed, onlyFavourites)) {
        val byName = all.associateBy { it.name }
        val swaps = suggested.mapNotNull { byName[it] }
        if (swaps.isNotEmpty()) {
            list += ListRow("h:suggested", "Suggested", null, null)
            swaps.mapTo(list) { ListRow("s:${it.name}", null, it, null) }
        }
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

private fun anyFilterOn(muscle: Muscle?, equipment: String?, performed: Boolean, onlyFavourites: Boolean) = muscle != null || equipment != null || performed || onlyFavourites

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
    /** Round 17 (9): what a hold on a row shows above its actions: the exercise's last session as lines (the day, then its sets); none in a picker. */
    rowPeek: ((String) -> List<String>?)? = null,
    /**
     * Ticked names, when several can be picked: each row then has a tick box that [onTick] turns, and while any is ticked a tap on a
     * row ticks it too (with none, a tap still picks the one).
     */
    ticked: List<String>? = null,
    onTick: (String) -> Unit = {},
    /** What sits at the right end of each exercise's row (the Library's + while a workout runs); none by default, and never in a picker. */
    trailing: (@Composable (String) -> Unit)? = null,
    /** Swaps to put first under "Suggested" when nothing is typed or filtered (the picker of Replace exercise). */
    suggested: List<String> = emptyList(),
    /** The list's search (see `SearchOpener`): an icon in the Library's header that opens into the field, and a field that stays out in a picker (Round 28, `pinned`); both are the one [SearchBox] since Round 37. */
    search: SearchOpener,
    /** Round 28: a tap always ticks (the multi-select of "Pick exercises"); by default a tap picks that one exercise until something is ticked. */
    tapTicks: Boolean = false,
) {
    val query = search.query
    fun setQuery(q: String) { search.query = q }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    var onlyFavourites by remember { mutableStateOf(false) }
    var localPerformed by remember { mutableStateOf(false) }
    var pickingMuscle by remember { mutableStateOf(false) }
    var pickingEquipment by remember { mutableStateOf(false) }
    val platform = LocalPlatform.current
    val thumbs = remember { platform.animationCount() > 0 }
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
        remember { Made("", listRows(all, "", null, muscle, equipment, hiding, performed, onlyFavourites, favourites, musclesOf, expanded, suggested)) },
        all, query, muscle, equipment, hiding, performed, onlyFavourites, favourites, index, expanded, suggested,
    ) {
        val q = query.trim()
        val idx = index
        if (q.isNotEmpty() && idx == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val rows = listRows(all, q, idx, muscle, equipment, hiding, performed, onlyFavourites, favourites, musclesOf, expanded, suggested) { ensureActive() }
            Made(q, rows, if (q.isNotEmpty() && idx != null && rows.none { it.exercise != null }) nearestName(q, idx.entries) else null)
        }
    }
    val rows = made.rows
    val letters = remember(rows) { rows.filter { it.heading != null && it.letter != null }.map { it.letter!! } }
    val state = listState ?: rememberLazyListState()
    if (tab != null) BarEnd(tab, state)
    val scope = rememberCoroutineScope()
    // Round 17 (12), shared with History in round 20: on the Library, pulling the list down past its top opens the search.
    val motionOn = LocalMotion.current
    val pull = rememberPullToSearch(state, search, enabled = !search.open)
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
        SearchBox(search, "Search ${thousandsComma(all.size.toLong())} exercises", Modifier.padding(horizontal = Space.l, vertical = Space.xs))
        // The chips wrap onto a second line instead of running off the screen edge.
        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            CleanChip(muscle != null, { pickingMuscle = true }, { Text(muscle?.label ?: "Muscle") })
            CleanChip(equipment != null, { pickingEquipment = true }, { Text(equipment ?: "Equipment") })
            CleanChip(performed, { setPerformed(!performed) }, { Text("Performed") })
            CleanChip(onlyFavourites, { onlyFavourites = !onlyFavourites }, { Text("Favourites") })
            if (anyFilter) CleanChip(false, { muscle = null; equipment = null; onlyFavourites = false; setPerformed(false) }, { Text("Clear all") })
        }
        Box(Modifier.fillMaxSize()) {
            PullHint(pull, Modifier.align(Alignment.TopCenter))
            LazyColumn(Modifier.fillMaxSize().padding(end = Space.xxl).pulled(pull), state = state) {
                if (nothingFound) {
                    item(key = "none") {
                        Text("No exercise called “$name”", Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    made.suggestion?.let { near ->
                        item(key = "near") {
                            Text("Did you mean $near?", Modifier.fillMaxWidth().clickable { setQuery(near) }.padding(Space.l), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                items(rows, key = { it.key }, contentType = { if (it.link != null) "link" else if (it.exercise == null) "heading" else "exercise" }) { row ->
                  // A search that changes the rows slides each one to its new place instead of jumping.
                  Column(Modifier.fillMaxWidth().then(if (LocalMotion.current) Modifier.animateItem() else Modifier)) {
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
                        Row((if (rowKey != null) Modifier.opensInto(null, androidx.compose.ui.graphics.Color.Transparent, square = true) else Modifier).fillMaxWidth().then(Modifier.clickable { if (ticked != null && (tapTicks || ticked.isNotEmpty())) onTick(exercise.name) else open() }).holdMenu(cardShape(), rowActions?.let { f -> { f(exercise.name) } }, peek = rowPeek?.let { f -> { f(exercise.name) } }).padding(start = Space.l, end = if (ticked != null) Space.xs else Space.l, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
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
                        if (ticked != null) PickMark(ticked.indexOf(exercise.name) + 1, { onTick(exercise.name) }, Modifier.semantics { contentDescription = "Pick ${exercise.name}" })
                        trailing?.invoke(exercise.name)
                        }
                        }
                        HorizontalDivider()
                    }
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
                val tick = rememberTick()
                // The letter under the finger and how far down the rail it is: a bubble left of the rail shows it large while it is held.
                var held by remember { mutableStateOf<Pair<Char, Float>?>(null) }
                held?.let { (letter, y) ->
                    Box(
                        Modifier.align(Alignment.TopEnd).offset { androidx.compose.ui.unit.IntOffset(-(22.dp + Space.s).roundToPx(), (y - 22.dp.toPx()).roundToInt()) }
                            .size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clearAndSetSemantics { testTag = "letter bubble" },
                        contentAlignment = Alignment.Center,
                    ) { Text(letter.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                }
                Column(
                    // A drag-only shortcut: a screen reader goes through the list itself, so the rail is left out of what it reads.
                    Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight().padding(bottom = LocalBarInset.current).clearAndSetSemantics { testTag = "letter rail" }.pointerInput(letters, rows, offset) {
                        var last: Char? = null
                        fun jump(y: Float) {
                            val letter = letters[(y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
                            val index = rows.indexOfFirst { it.heading != null && it.letter == letter }
                            if (index >= 0) scope.launch { state.scrollToItem(index + offset) }
                            if (letter != last) { last = letter; tick() }
                            held = letter to y.coerceIn(0f, size.height.toFloat())
                        }
                        // Round 30: the rail takes a touch over only when it is a drag that is clearly vertical once past the touch slop, or a press held
                        // for the long-press time (a tap on a letter jumps on lift). A touch that moves sideways first, such as the back swipe from the right
                        // edge, is never consumed: it goes on to the system and the page.
                        awaitEachGesture {
                            last = null
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            // What the touch turned out to be before the rail took it: 1 a vertical drag, 3 a tap, 0 not for the rail; held to the long-press time: 2.
                            val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var verdict = -1
                                while (verdict < 0) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    verdict = if (change == null || change.isConsumed) 0 else if (!change.pressed) 3 else {
                                        val d = change.position - down.position
                                        if (abs(d.x) <= slop && abs(d.y) <= slop) -1 else if (abs(d.y) > abs(d.x)) 1 else 0
                                    }
                                }
                                verdict
                            }
                            val kind = decided ?: 2
                            if (kind == 0) return@awaitEachGesture
                            if (kind == 3) { jump(down.position.y); held = null; return@awaitEachGesture }
                            jump(down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { jump(it.position.y); it.consume() }
                            } while (event.changes.any { it.pressed })
                            held = null
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
        if (choosing) ExercisePicker(model, workouts, onPick = { copyFrom = it; choosing = false }, onDismiss = { choosing = false }, title = "Start as a copy of")
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
    val search = rememberSearchOpener()
    val searchScope = rememberCoroutineScope()
    val header: @Composable () -> Unit = {
        Title("Library") {
            IconButton(onClick = { searchScope.launch { search.openAndFocus() } }) { Icon(CleanIcons.Search, "Search exercises") }
            IconButton(onClick = { creating = true }) { Icon(CleanIcons.Add, "New exercise") }
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        header()
        val running = model.draft
        if (running != null) RunningBand(model, running)
        // Keyed on the settings so the list follows a change made on an exercise's page.
        val musclesOf = remember(settings) { { name: String -> model.musclesOf(name) } }
        ExerciseList(
            all, musclesOf, onClick = { model.open(Screen.ExerciseDetail(it)) }, unitOf = model::unitOf,
            // A search that finds nothing offers to make the exercise, as the New exercise button does.
            onCreate = { made -> model.putSetting(made) { it }; model.open(Screen.ExerciseDetail(made, edit = true)) }, createWhenEmpty = true,
            favourites = settings.filterValues { it.favourite }.keys, hideLibrary = hide, tab = clean.app.Tab.Exercises, search = search,
            listState = model.tabLists.getValue(clean.app.Tab.Exercises),
            rowActions = { name ->
                val starred = settings[name]?.favourite == true
                listOf(
                    HoldAction("Open", CleanIcons.Forward) { model.open(Screen.ExerciseDetail(name)) },
                    HoldAction(if (starred) "Unfavourite" else "Favourite", if (starred) CleanIcons.Star else CleanIcons.StarLine) { model.putSetting(name) { it.copy(favourite = !starred) } },
                    HoldAction("Edit", CleanIcons.Edit) { model.open(Screen.ExerciseDetail(name, edit = true)) },
                )
            },
            rowPeek = { name -> lastSessionPeek(model.sessionsIn(workouts, name).lastOrNull(), model.unitOf(name)) },
            performedOnly = show == "performed", onPerformedOnly = { show = if (it) "performed" else "all" },
            trailing = running?.let { d -> @Composable { name: String -> AddToWorkout(model, name, d) } },
        )
    }
    }
}

/** The Library's band while a workout runs: "Push is running · 24:13 · Back to workout ›"; a tap goes back to the workout. */
@Composable
private fun RunningBand(model: AppModel, draft: Draft) {
    val look = LocalLook.current
    val seconds = (nowMillis() - draft.startMillis) / 1000
    val shape = cardShape()
    Row(
        Modifier.padding(start = Space.l, end = Space.l, bottom = Space.s).fillMaxWidth().heightIn(min = 48.dp).glass(shape, lift = 6.dp).clip(shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { model.resumeWorkout() }
            .clearAndSetSemantics { contentDescription = "${draft.name} is running. Back to workout" }
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Box(Modifier.size(8.dp).background(look.orange, CircleShape))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)); append(draft.name); pop()
                append(" is running · ${clock(seconds)}")
            },
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text("Back to workout ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The + at the right of a Library row while a workout runs; once the exercise is in the workout it is the accent's "✓ Added" chip. */
@Composable
private fun AddToWorkout(model: AppModel, exercise: String, draft: Draft) {
    val look = LocalLook.current
    if (draft.holds(exercise)) {
        Row(
            Modifier.background(look.orange.copy(alpha = 0.16f), CircleShape).padding(horizontal = Space.m, vertical = Space.s)
                .semantics(mergeDescendants = true) { contentDescription = "$exercise is in ${draft.name}" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(CleanIcons.Check, null, Modifier.size(16.dp), tint = look.orange)
            Text("Added", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = look.orange)
        }
    } else GlassButton("Add $exercise to ${draft.name}", { model.addToRunning(exercise) }) { Icon(CleanIcons.Add, null, Modifier.size(18.dp)) }
}

/**
 * The exercise page's button while a workout runs and the page was not opened from it: "Add to Push" (the accent), or, when the card is
 * already there, a quiet "In Push".
 */
@Composable
private fun AddToWorkoutBar(model: AppModel, exercise: String, draft: Draft) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), contentAlignment = Alignment.Center) {
        if (draft.holds(exercise)) Row(Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = "$exercise is in ${draft.name}" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Icon(CleanIcons.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("In ${draft.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        else MainButton("Add to ${draft.name}", { model.addToRunning(exercise) }, Modifier.fillMaxWidth().heightIn(min = 56.dp))
    }
}

/** Round 24: the muscles an exercise trains as chips under a small label; the main ones are the filled chips ([main]) and the supporting and minor ones outlined. A chip opens that muscle's page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleChips(label: String, muscles: Set<Muscle>, main: Boolean, onOpen: (Muscle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Caption(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            for (m in muscles) CleanChip(selected = main, onClick = { onOpen(m) }, label = { Text(m.label) })
        }
    }
}

val EXERCISE_TABS = listOf("Info", "History", "Charts", "PRs")
private const val INFO = 0
private const val HISTORY = 1
private const val CHARTS = 2
private const val PRS = 3

/** How many weights the PRs tab's ladder shows before "Show all". */
private const val LADDER_ROWS = 10
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
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    // The sessions, each side's records and the chart figures are worked out off the main thread and kept per exercise in the model (reopening is
    // instant); until they are there the page shows its bar and tabs over an empty body, and the body fades in.
    var stats by remember { mutableStateOf(model.cachedExerciseStats(name, workouts, loadType, bodyweights, rule)) }
    LaunchedEffect(name, workouts, loadType, bodyweights, rule) { stats = model.exerciseStats(name, workouts, loadType, bodyweights, rule) }
    val ready = stats != null
    val sessions = stats?.sessions.orEmpty()
    // Each side's records, and the comparable figure (the weaker side's) that the rows and headers show; an exercise never done left and right has one.
    val sided = stats?.sided ?: remember(rule, loadType) { recordsBySide(emptyList(), rule, loadType, bodyweights::at) }
    val records = sided.comparable
    val hasSides = stats?.hasSides ?: false
    val latestGap = stats?.latestGap
    // Each session's chart figures; a left and right session also carries each side's, for the charts that draw a split.
    val sideProgress = stats?.sideProgress.orEmpty()
    val progress = stats?.progress.orEmpty()
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

    fun weight(kg: Double) = "${showWeight(kg, unit)} $unit"
    fun shown(kg: Double) = if (unit == "lb") kg / KG_PER_LB else kg

    // The page in four tabs. It opens on the one asked for (History, from a workout in progress), else on the one last used.
    var current by remember { mutableStateOf(startTab ?: model.options.value["ui.exerciseTab"]?.toIntOrNull() ?: INFO) }
    val tab = if (editing) INFO else current.coerceIn(0, EXERCISE_TABS.lastIndex)
    val allPrs by model.prs.collectAsState()
    // The figures that count up on this page play once per open (round 23).
    val seen = remember { HashSet<String>() }
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
            chart == "Est. 1RM" -> goalOneRm(kg, reps, rule, loadType, bodyweights.at(strongDate(currentMillis())))?.let(::shown)
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
    // The body fades in when the figures arrive after the page (at once when they were kept); without motion it is simply there.
    val arrive = remember { androidx.compose.animation.core.Animatable(if (ready) 1f else 0f) }
    LaunchedEffect(ready) { if (ready && arrive.value < 1f) { if (motion) arrive.animateTo(1f, Motion.short()) else arrive.snapTo(1f) } }
    val running = model.draft
    Scaffold(containerColor = pageContainer(), bottomBar = {
        // Not on the page of a card opened from the running workout (it opens on History).
        if (running != null && startTab != WORKOUT_EXERCISE_TAB && !editing) AddToWorkoutBar(model, name, running)
    }, topBar = {
      Column {
        BackBar(name, model::back) {
            // A favourite sits above everything else in the list and in pickers.
            val starred = settings[name]?.favourite == true
            IconButton(onClick = { model.putSetting(name) { it.copy(favourite = !starred) } }, Modifier.starBurst(starred)) {
                Icon(if (starred) CleanIcons.Star else CleanIcons.StarLine, if (starred) "Remove from favourites" else "Add to favourites", tint = if (starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            }
            // Editing happens on the Info tab.
            TextButton(onClick = { editing = !editing; if (editing) current = INFO }) { Text(if (editing) "Done" else "Edit") }
        }
        // A clear container (Round 36): the page's backdrop shows through the tabs, as it does through the bar above them; the indicator and the divider stay.
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, indicator = { positions -> StretchIndicator(underline, positions) }) {
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
                    alpha = (1f - (abs(off) / (width / 3f)).coerceIn(0f, 1f) * 0.8f) * arrive.value
                },
            state = listState,
        ) {
            // The body is not there until the figures are: its place is kept, so nothing jumps when it arrives.
            if (!ready) { item { Spacer(Modifier.fillParentMaxHeight()) }; return@LazyColumn }
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
                    val platform = LocalPlatform.current
                    val info by produceState<ExerciseInfo?>(null, name) { value = ExerciseInfos.of(platform, name) }
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
                            if (use.primary.isNotEmpty()) MuscleChips("Main muscles", use.primary, true, openMuscle)
                            if (use.secondary.isNotEmpty()) MuscleChips("Supporting", use.secondary, false, openMuscle)
                            if (use.minor.isNotEmpty()) MuscleChips("Minor", use.minor, false, openMuscle)
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
                            ThinBar((most.toFloat() / reps).coerceIn(0f, 1f), goalColor(), 8.dp)
                            Text(
                                if (most >= reps) "Reached: a set of ${weight(kg)} for $most reps."
                                else "Most reps at ${weight(kg)} or ${if (loadType == LoadType.ASSISTED) "less help" else "more"}: $most of $reps (${roundHalfUp(most * 100f / reps)}%).",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // How far the best estimated 1RM has come towards the goal's.
                            val target = (realLoad(loadType, kg, bodyweights.at(strongDate(currentMillis()))) ?: kg).let { estimate1Rm(it, reps, rule) ?: it }
                            val best = records.estimated1Rm?.value ?: 0.0
                            ThinBar((best / target).toFloat().coerceIn(0f, 1f), goalColor(), 8.dp)
                            Text(
                                if (best >= target) "Reached: your best estimated 1RM is ${weight(roundHalfUp(best * 10) / 10.0)}, the goal's is ${weight(roundHalfUp(target * 10) / 10.0)}."
                                else "Best estimated 1RM ${weight(roundHalfUp(best * 10) / 10.0)} of ${weight(roundHalfUp(target * 10) / 10.0)} (${roundHalfUp(best / target * 100)}%).",
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
                } else item {
                    // Round 24: a gentle nudge, not a form.
                    Section("Notes", Modifier.settleNext()) {
                        Text("No notes yet. A note on $name in a workout, a grip or a cue or how a set felt, collects here, newest first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                  if (sessions.size < 2) EmptyState(
                      if (sessions.isEmpty()) "Nothing to chart yet" else "One more session",
                      "Log ${2 - sessions.size} more ${if (sessions.size == 1) "session" else "sessions"} of $name to see its chart.",
                  )
                  else {
                    Choice(listOf(-1) + charts.indices + (if (hasImbalance) listOf(IMBALANCE_CHART) else emptyList()), chart, { if (it == IMBALANCE_CHART) "Imbalance" else if (it < 0) "All" else charts[it].first }) { chart = it }
                    RangeChoice(listOf(90L, 365L, 0L), range, { if (it == 0L) "All" else if (it == 90L) "3M" else "1Y" }) { range = it }
                    val (_, chartUnit, value) = charts[chart.coerceIn(charts.indices)]
                    val from = if (range == 0L) null else LocalDate.now().minusDays(range)
                    val points = chartPoints(sideProgress, from, value)
                    val openDay = { day: LocalDate -> sessions.lastOrNull { it.date.startsWith(day.toString()) }?.let { model.open(Screen.WorkoutDetail(it.date)) }; Unit }
                    // Holding a point names the workout it comes from.
                    val workoutNameOn = { day: LocalDate -> sessions.lastOrNull { it.date.startsWith(day.toString()) }?.let { s -> workouts.lastOrNull { it.date == s.date }?.name } }
                    if (chart == IMBALANCE_CHART) {
                        val gaps = imbalanceSeries(sideProgress, from)
                        if (gaps == null || gaps.points.size < 2) Text("Not enough sessions with both sides in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else SplitLineChart(gaps.points, "%", title = "$name · Imbalance by ${gaps.measure}", imbalance = true, onTap = openDay, names = workoutNameOn, selector = RangeSelector(range, { range = it }))
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
                        goal = goalLine(charts[chart.coerceIn(charts.indices)].first), onTap = openDay, names = workoutNameOn, selector = RangeSelector(range, { range = it }),
                        morph = if (charts[chart.coerceIn(charts.indices)].first == "Est. 1RM") "chart:1rm" else null,
                    )
                  }
                }
            } }
            if (tab == PRS && sessions.isEmpty()) item {
                EmptyState("No records yet", "Log a set of $name and its records appear here.")
            }
            // Round 23: the PRs tab as the main tabs read: one hero (the estimated 1RM and its line), the records as tiles that peek at their workout, the PRs lately.
            if (tab == PRS && records.estimated1Rm != null) item {
                val e1 = records.estimated1Rm!!
                val history = progress.mapNotNull { it.estimated1Rm }.map(::shown)
                val setIn = byDate[e1.date]
                HoldPeekBox("workout peek", setIn?.let { w -> { WorkoutPeek(model, w) } }) {
                    Section("", Modifier.settleNext(), hero = true, onClick = { model.open(Screen.WorkoutDetail(e1.date)) }, spacing = Space.s) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Caption("Est. 1RM") }
                            PrChip(prettyDay(e1.date))
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Text(countUpOnce(seen, "e1rm", 0, shown(e1.value)) { shortNumber(roundHalfUp(it * 10) / 10.0) }, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(unit, Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e1.weight != null && e1.reps != null) Text("from $sign${showWeight(e1.weight!!, unit)} $unit × ${e1.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (history.size >= 2) Sparkline(history, Modifier.padding(top = Space.xs), height = 56.dp, wash = true)
                    }
                }
            }
            if (tab == PRS && sessions.isNotEmpty()) item {
                fun one(kg: Double) = shortNumber(roundHalfUp(shown(kg) * 10) / 10.0)
                fun setOf(r: Record) = if (r.weight != null && r.reps != null) "$sign${showWeight(r.weight!!, unit)} $unit × ${r.reps}" else null
                class Cell(val label: String, val record: Record, val left: Record?, val right: Record?, val number: (Record) -> String, val unit: String?, val detail: (Record) -> String?)
                val cells = listOfNotNull(
                    records.weight?.let { Cell(when (loadType) { LoadType.BODYWEIGHT -> "Most added weight"; LoadType.ASSISTED -> "Least assistance"; else -> "Heaviest weight" }, it, sided.left.weight, sided.right.weight, { r -> sign + showWeight(r.value, unit) }, unit) { r -> r.reps?.let { n -> "× $n" } } },
                    records.reps?.let { Cell("Most reps", it, sided.left.reps, sided.right.reps, { r -> r.reps.toString() }, "reps") { r -> r.weight?.let { w -> "at $sign${showWeight(w, unit)} $unit" } } },
                    records.longest?.let { Cell("Longest hold", it, sided.left.longest, sided.right.longest, { r -> shortNumber(r.value) }, "s") { r -> r.weight?.takeIf { w -> w > 0 }?.let { w -> "at $sign${showWeight(w, unit)} $unit" } } },
                    records.estimated1Rm?.let { Cell("Est. 1RM", it, sided.left.estimated1Rm, sided.right.estimated1Rm, { r -> one(r.value) }, unit) { r -> setOf(r) } },
                    records.setVolume?.let { Cell("Best set volume", it, sided.left.setVolume, sided.right.setVolume, { r -> one(r.value) }, unit) { r -> setOf(r) } },
                    records.sessionVolume?.let { Cell("Best session volume", it, sided.left.sessionVolume, sided.right.sessionVolume, { r -> one(r.value) }, unit) { null } },
                )
                Section("Records", Modifier.settleNext()) {
                    for (pair in cells.chunked(2)) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        for (c in pair) {
                            val sides = if (hasSides && (c.left != null || c.right != null)) sidesLine(c.left?.let(c.number), c.right?.let(c.number)) else null
                            RecordTile(
                                c.label, c.number(c.record), c.unit, c.detail(c.record), prettyDay(c.record.date), sides, Modifier.weight(1f).fillMaxHeight(),
                                peek = byDate[c.record.date]?.let { w -> { WorkoutPeek(model, w) } }, onClick = { model.open(Screen.WorkoutDetail(c.record.date)) },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (tab == PRS && sessions.size > 1) item {
                // The last three records broken, newest first; the card opens every one.
                val lately = remember(workouts, allPrs, name) { prRows(workouts, allPrs).filter { it.exercise == name }.takeLast(3).reversed() }
                if (lately.isNotEmpty()) Section("PR history", Modifier.settleNext(), onClick = { model.open(Screen.PrTimeline(name)) }, card = true) {
                    for (row in lately) PrLine(model, row) { model.open(Screen.WorkoutDetail(row.date)) }
                    Text("Every record broken ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tab == PRS && records.repsAtWeight.isNotEmpty()) {
                item {
                    Section("Best reps at each weight", Modifier.settleNext()) {
                        // A ladder: each weight with a bar of its best reps; the heaviest ten, the rest on request.
                        var all by remember { mutableStateOf(false) }
                        val top = records.repsAtWeight.maxOf { it.reps ?: 0 }.coerceAtLeast(1)
                        for (r in (if (all) records.repsAtWeight else records.repsAtWeight.take(LADDER_ROWS))) {
                            HoldPeekBox("workout peek", byDate[r.date]?.let { w -> { WorkoutPeek(model, w) } }, Modifier.fillMaxWidth()) {
                                LadderRow(weight(r.weight ?: 0.0), r.reps ?: 0, top, prettyDay(r.date)) { model.open(Screen.WorkoutDetail(r.date)) }
                            }
                            // Each side's own best at the weight, where the exercise has been done left and right.
                            if (hasSides) {
                                val l = sided.left.repsAtWeight.firstOrNull { it.weight == r.weight }?.reps?.toString()
                                val rt = sided.right.repsAtWeight.firstOrNull { it.weight == r.weight }?.reps?.toString()
                                val text = sidesLine(l, rt)
                                if (text != null) Text(text, Modifier.semantics { contentDescription = "${weight(r.weight ?: 0.0)}, ${sidesSpoken(l, rt)} reps" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (records.repsAtWeight.size > LADDER_ROWS) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${records.repsAtWeight.size}") }
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
                    // The best estimated 1RM run back through the chosen formula, as a row of small chips.
                    val best = records.estimated1Rm!!.value
                    Section("Rep maxes", Modifier.settleNext()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (n in listOf(1, 2, 3, 5, 8, 10)) Column(
                                Modifier.background(LocalLook.current.tile, SmallShape).padding(horizontal = Space.m, vertical = Space.s)
                                    .semantics(mergeDescendants = true) { contentDescription = "${n}RM, about ${showWeight(roundHalfUp(weightForReps(best, n, rule) * 10) / 10.0, unit)} $unit" },
                            ) {
                                Text("${n}RM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("≈ " + showWeight(roundHalfUp(weightForReps(best, n, rule) * 10) / 10.0, unit), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("In $unit, worked out from the best estimated 1RM by the ${rule.formula.label} formula.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (tab == PRS && (loadType == LoadType.BODYWEIGHT || loadType == LoadType.ASSISTED)) item {
                Text(
                    if (records.missingBodyweight) "Estimated 1RM and volume use your real load (bodyweight $sign the logged weight). Sets with no weigh-in within $BODYWEIGHT_WINDOW_DAYS days of the workout are left out of them."
                    else "Estimated 1RM and volume use your real load (bodyweight $sign the logged weight).",
                    Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tab == HISTORY && sessions.isEmpty()) item {
                // Offered as the Library offers it: into the workout in progress, or into a new one.
                val running = model.draft
                EmptyState(
                    "Not logged yet", "Log a set of $name and every session shows here.",
                    if (running?.holds(name) == true) null else "Add to workout",
                    { if (running == null) model.startWorkout(); model.addToRunning(name) },
                )
            }
            if (tab == HISTORY && sessions.isNotEmpty()) item {
                // Three figures over the sessions: how many, how long ago the last was, and the best estimated 1RM.
                val lastDay = parseDate(sessions.last().date)?.toLocalDate()
                val ago = lastDay?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0) }
                val best = records.estimated1Rm
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Tile("Sessions", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp) { TileNumber(sessions.size.toString()) }
                    Tile("Last done", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp) {
                        if (ago == null) TileNumber(NONE)
                        else if (ago == 0L) TileNumber("Today")
                        else TileNumber(ago.toString(), if (ago == 1L) "day ago" else "days ago")
                    }
                    Tile(if (best != null) "Best 1RM" else "Sets", Modifier.weight(1f).fillMaxHeight(), minHeight = 0.dp) {
                        if (best != null) TileNumber(shortNumber(roundHalfUp(shown(best.value) * 10) / 10.0), unit)
                        else TileNumber(sessions.sumOf { it.workingSets }.toString())
                    }
                }
            }
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
                                val estimate = if (w != null && r != null) realLoad(loadType, w, bodyweight)?.let { estimate1Rm(it, r, rule.shown(), set.rpe) } else null
                                Row {
                                    Text(labels[i], Modifier.width(36.dp), style = small, color = typeColor(set.type), fontWeight = FontWeight.Bold)
                                    Text((if (w != null && r != null) sign else "") + setValues(set, unit), Modifier.weight(1f), style = small)
                                    Text(estimate?.let { "1RM " + showWeight(roundHalfUp(it).toDouble(), unit) } ?: "", style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (addingVariation) ExercisePicker(model, workouts, onPick = { model.addVariation(name, it); addingVariation = false }, onDismiss = { addingVariation = false }, title = "Add a variation")
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

/**
 * Round 28: the mark at the end of a row in a multi-select picker: an empty ring until the exercise is picked, then its place in the order it will be
 * added in, in the accent ([order] is 1 for the first, 0 for not picked). Tapping it takes the exercise out again and the numbers close up.
 */
@Composable
fun PickMark(order: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val on = order > 0
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.size(48.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(26.dp).background(if (on) primary else Color.Transparent, CircleShape).border(1.5.dp, if (on) primary else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Text(order.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
    }
}
