package clean.app

import clean.app.data.JSONArray
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import clean.app.data.JSONObject
import clean.core.time.*
import kotlinx.datetime.Month
import clean.core.planIsoRowFix
import clean.core.rowsOfWorkouts
import clean.core.hueOfRgb
import clean.core.HealthEntry
import clean.core.HealthFailure
import clean.core.HealthFailureKind
import clean.core.HealthReport
import clean.core.HealthRequest
import clean.core.decodeLedger
import clean.core.desiredHealth
import clean.core.encodeLedger
import clean.core.healthReportText
import clean.core.label
import clean.core.planHealth
import clean.core.RoundShape
import clean.core.Side
import clean.core.isSidesMarker
import clean.core.pairHolding
import clean.core.sidePairs
import clean.core.startsSidesRest
import clean.core.SetTarget
import clean.core.TemplateBlock
import clean.core.isValidSet
import clean.core.sameShape
import clean.core.tidyTargets
import clean.core.valueIncreases
import clean.core.setsToCompletePairs
import clean.core.STRONG_DATE_FORMAT
import clean.core.Consistency
import clean.core.TargetFrom
import clean.core.decodeTargets
import clean.core.encodeTargets
import clean.core.showWelcome
import clean.core.targetsOrDefault
import clean.core.weekResults
import clean.core.weekStart
import clean.core.withTarget
import androidx.compose.runtime.snapshotFlow
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.withContext
import clean.core.NewRest
import clean.core.sessionsByExercise
import clean.core.Session
import clean.core.volumeOf
import clean.core.sideOneRms
import clean.core.volumeByDay
import clean.core.secondsByDay
import clean.core.SideRecords
import clean.core.SidedProgress
import clean.core.Progress
import clean.core.latestImbalance
import clean.core.recordsBySide
import clean.core.progressBySide
import clean.core.headsOf
import clean.core.otherNames
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import clean.core.variationBase
import clean.core.generatedVariations
import kotlinx.coroutines.withTimeoutOrNull
import clean.core.SetItem
import clean.core.NoteItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import clean.core.libraryVariations
import clean.core.libraryExercise
import clean.app.data.RenameEntry
import clean.core.NO_GROUP
import clean.core.overallName
import clean.core.ProgramStanding
import clean.core.ProgramVersion
import clean.core.ProgramDay
import clean.core.TodayPlan
import clean.core.decodeProgram
import clean.core.encodeProgram
import androidx.compose.ui.graphics.toArgb
import clean.core.workoutNotifOn
import clean.core.workoutNotifText
import clean.core.todayPlan
import clean.core.programStanding
import clean.core.withVersion
import clean.core.Head
import clean.core.Deload
import clean.core.Progression
import clean.core.SetRange
import clean.core.deloadSignals
import clean.core.parseGoal
import clean.core.progressOf
import clean.core.setRangeFor
import clean.core.Muscle
import clean.core.headSets
import clean.core.otherWork
import clean.core.fatigue
import clean.core.lastTrained
import clean.core.muscleSets
import clean.core.retainedStrength
import clean.core.SideCredit
import clean.core.fatigueBySide
import clean.core.hasSideWork
import clean.core.headSetsBySide
import clean.core.lastTrainedBySide
import clean.core.muscleSetsBySide
import clean.core.retainedStrengthBySide
import kotlinx.coroutines.flow.MutableStateFlow
import clean.app.ui.theme.Look
import clean.app.ui.theme.resolveLook
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_BLACK_KEY
import clean.core.LOOK_THEMES
import clean.core.LOOK_THEME_KEY
import clean.core.migrateLook
import clean.core.migrateLookTheme
import clean.core.migrateTwelveThemes
import clean.app.platform.NoticeRest
import clean.app.platform.Platform
import clean.core.ConcurrentCache
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import clean.app.platform.REST_NOTIFICATION_MORE
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import clean.app.data.CleanDb
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.MeasurementFileEntity
import clean.app.data.Profile
import clean.app.data.Repo
import clean.app.data.TemplateEntity
import clean.core.Bodyweights
import clean.core.MuscleUse
import clean.core.Moment
import clean.core.Photo
import clean.core.PhotoFilter
import clean.core.Pic
import clean.core.mergedPics
import clean.core.Pose
import clean.core.realLoad
import clean.core.OneRm
import clean.core.OneRmFormula
import clean.core.PrKind
import clean.core.prsOf
import clean.core.suggest
import clean.core.LoadType
import clean.core.NewBlock
import clean.core.NewItem
import clean.core.NewNote
import clean.core.NewSet
import clean.core.SetType
import clean.core.Workout
import clean.core.buildWorkouts
import clean.core.kgToLb
import clean.core.lbToKg
import clean.core.shortNumber
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// --- The workout being logged ---

sealed interface DraftItem
data class DraftNote(val text: String = "") : DraftItem

/** [weight] is text in the exercise's display unit, exactly as typed. */
data class DraftSet(
    val type: SetType = SetType.NORMAL,
    val weight: String = "",
    val reps: String = "",
    val done: Boolean = false,
    /** The variation this set was done as, when it is not the card's own exercise. */
    val exercise: String? = null,
    /** How hard it felt, 6 to 10 in half steps; empty when not given. */
    val rpe: String = "",
    /** The length of the rest started when this set was ticked, in seconds; 0 when none was. */
    val rest: Int = 0,
    /**
     * When it was ticked, in milliseconds (a small running number for a set loaded from a finished workout), 0 when not ticked or
     * not known: of a left and right round, the half with the smaller number was done first.
     */
    val ticked: Long = 0,
) : DraftItem

/** [superset] is a tag shared with the card next to it when the two are done in turn; it lives in the workout in progress and in templates, not in the CSV. */
data class DraftBlock(
    val exercise: String,
    val items: List<DraftItem>,
    val superset: String? = null,
    /** The values the template this card came from planned, per set by position (kg and reps); they show in the empty boxes. */
    val targets: List<SetTarget?> = emptyList(),
    /**
     * Logged left and right: the sets are drawn as pairs and saved with the block's marker note (docs/left-right.md). The
     * card's own, so turning the exercise's switch off later changes no card and no finished workout.
     */
    val paired: Boolean = false,
    /** The card's own name on screen, kept through moves and edits, so the pad and the glide follow it even when the exercise is there twice. */
    val id: Long = newCardId(),
)

@OptIn(ExperimentalAtomicApi::class)
private val cardIds = AtomicLong(currentMillis() * 1000)

/** A card id no other card has: a count started from the clock, so it also stays clear of the ids in a draft stored before a restart. */
@OptIn(ExperimentalAtomicApi::class)
fun newCardId(): Long = cardIds.incrementAndFetch()

/** The keys the workout screen knows its cards by, one per card in order. */
fun cardKeys(blocks: List<DraftBlock>): List<String> = blocks.map { "card:" + it.id }

/**
 * The card holding the set to do next. Cards are taken in order; within a superset the turn goes
 * to whichever of its exercises has the fewest sets ticked, so they alternate.
 */
fun nextBlock(draft: Draft): Int? {
    var i = 0
    while (i < draft.blocks.size) {
        var end = i
        val tag = draft.blocks[i].superset
        if (tag != null) while (end + 1 < draft.blocks.size && draft.blocks[end + 1].superset == tag) end++
        val open = (i..end).filter { k -> draft.blocks[k].items.any { it is DraftSet && !it.done } }
        if (open.isNotEmpty()) return open.minByOrNull { k -> draft.blocks[k].items.count { it is DraftSet && it.done } }
        i = end + 1
    }
    return null
}

/**
 * Where "Do next" puts a new card (round 33): the current exercise is the first unit, a card or a whole superset, that still has a set not done.
 * Started (a set of the unit done): the new card goes right after the unit. Not started: before it, so the new one is the one to do now.
 * Every card done: at the end.
 */
fun doNextIndex(draft: Draft): Int {
    var i = 0
    while (i < draft.blocks.size) {
        var end = i
        val tag = draft.blocks[i].superset
        if (tag != null) while (end + 1 < draft.blocks.size && draft.blocks[end + 1].superset == tag) end++
        val sets = (i..end).flatMap { k -> draft.blocks[k].items.filterIsInstance<DraftSet>() }
        if (sets.any { !it.done }) return if (sets.any { it.done }) end + 1 else i
        i = end + 1
    }
    return draft.blocks.size
}

/** The card with its last pair made whole: a lone last set gets a partner of its own kind and variation. A no-op for an even count. */
fun DraftBlock.withPairsCompleted(): DraftBlock {
    val last = items.lastOrNull { it is DraftSet } as? DraftSet ?: return this
    return if (setsToCompletePairs(items.count { it is DraftSet }) == 0) this else copy(items = items + DraftSet(last.type, exercise = last.exercise))
}

/**
 * The card with every set given a partner for the other side: a copy of its kind, weight, reps and variation, not ticked.
 * Turning "Left and right" on does this, so a card of three sets becomes three pairs, each set its left half.
 */
fun DraftBlock.withSetsDoubled(): DraftBlock =
    copy(items = items.flatMap { if (it is DraftSet) listOf(it, it.copy(done = false, rpe = "", rest = 0, ticked = 0)) else listOf(it) })

/** The card taken back to ordinary sets: the right half of each pair goes unless it was ticked, which stays as a set of its own. */
fun DraftBlock.withRightHalvesDropped(): DraftBlock {
    val sets = items.indices.filter { items[it] is DraftSet }
    val dropped = sets.chunked(2).mapNotNull { it.getOrNull(1) }.filter { !(items[it] as DraftSet).done }.toSet()
    return copy(items = items.filterIndexed { i, _ -> i !in dropped })
}

/** Whether a workout is still in its first five minutes, when cancelling is at hand because it may have been started by mistake. */
fun Draft.isEarly(now: Long): Boolean = now - startMillis < 5 * 60_000

/** Whether the workout already has a card for [exercise] (the Library then shows "Added" in place of the +). */
fun Draft?.holds(exercise: String): Boolean = this != null && blocks.any { it.exercise == exercise }

/** The workout without the card with [id]: what Undo does after a card was added from the Library. */
fun Draft.withoutCard(id: Long): Draft = copy(blocks = blocks.filter { it.id != id })

/** A new workout was asked for while another runs: what the new one is called, and how to start it once the running one is gone. */
class Clash(val name: String, val action: () -> Unit)

data class Draft(
    val name: String,
    val startMillis: Long,
    val blocks: List<DraftBlock> = emptyList(),
    val lastDoneMillis: Long? = null,
    /** The template this workout was started from, if any. */
    val templateId: Long? = null,
    /** Set when a workout is being entered after the fact: its start (`yyyy-MM-dd HH:mm:ss`) and how long it took. */
    val pastDate: String? = null,
    val pastSeconds: Long? = null,
    /** Set when a finished workout is being corrected: the start of the workout this one replaces. */
    val editOf: String? = null,
    /** The workout's one-line note (the Workout Notes column); empty when none. */
    val note: String = "",
    /** The workout's tag (Clean's own, kept apart from the CSV: kv `tag:<profileId>:<start>` once saved); empty for none, which is the default. */
    val tag: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("start", startMillis)
        lastDoneMillis?.let { put("lastDone", it) }
        templateId?.let { put("template", it) }
        pastDate?.let { put("pastDate", it) }
        pastSeconds?.let { put("pastSeconds", it) }
        editOf?.let { put("editOf", it) }
        if (note.isNotEmpty()) put("note", note)
        if (tag.isNotEmpty()) put("tag", tag)
        put("blocks", JSONArray(blocks.map { b ->
            JSONObject().put("exercise", b.exercise).also { o -> b.superset?.let { o.put("superset", it) }; if (b.targets.isNotEmpty()) o.put("targets", targetsJson(b.targets)); if (b.paired) o.put("paired", true) }.put("id", b.id).put("items", JSONArray(b.items.map { i ->
                when (i) {
                    is DraftNote -> JSONObject().put("note", i.text)
                    is DraftSet -> JSONObject().put("type", i.type.name).put("weight", i.weight).put("reps", i.reps).put("done", i.done).put("rpe", i.rpe).put("rest", i.rest).put("ticked", i.ticked).also { o -> i.exercise?.let { o.put("exercise", it) } }
                }
            }))
        }))
    }.toString()

    companion object {
        fun fromJson(json: String): Draft {
            val o = JSONObject(json)
            val blocks = o.getJSONArray("blocks").objects().map { b ->
                DraftBlock(b.getString("exercise"), b.getJSONArray("items").objects().map { i ->
                    if (i.has("note")) DraftNote(i.getString("note"))
                    else DraftSet(SetType.valueOf(i.getString("type")), i.getString("weight"), i.getString("reps"), i.getBoolean("done"), if (i.has("exercise")) i.getString("exercise") else null, i.optString("rpe"), i.optInt("rest"), i.optLong("ticked"))
                }, if (b.has("superset")) b.getString("superset") else null, targetsOf(b.optJSONArray("targets")), b.optBoolean("paired", false), b.optLong("id").takeIf { it != 0L } ?: newCardId())
            }
            return Draft(o.getString("name"), o.getLong("start"), blocks, if (o.has("lastDone")) o.getLong("lastDone") else null, if (o.has("template")) o.getLong("template") else null,
                if (o.has("pastDate")) o.getString("pastDate") else null, if (o.has("pastSeconds")) o.getLong("pastSeconds") else null,
                if (o.has("editOf")) o.getString("editOf") else null, o.optString("note"), o.optString("tag"),
            )
        }

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    }
}

/** A workout opened for correcting: no set ticked, as the Correct page has no ticks (every set that holds its values counts on Save). */
fun Draft.asCorrection(): Draft = copy(blocks = blocks.map { b -> b.copy(items = b.items.map { if (it is DraftSet) it.copy(done = false, rest = 0) else it }) })

/** The workout in progress with [from] named [to], on its cards and on the sets done as that variation. */
fun Draft.renamed(from: String, to: String): Draft = copy(blocks = blocks.map { b ->
    b.copy(exercise = if (b.exercise == from) to else b.exercise, items = b.items.map { if (it is DraftSet && it.exercise == from) it.copy(exercise = to) else it })
})

/** Targets as stored: one `[weightKg|null, reps|null]` per set, `null` for a set with none. */
private fun targetsJson(targets: List<SetTarget?>): JSONArray = JSONArray(targets.map { t -> t?.let { JSONArray().put(it.weightKg ?: JSONObject.NULL).put(it.reps ?: JSONObject.NULL) } ?: JSONObject.NULL })

/** The targets [targetsJson] wrote; none when the key was absent (an older template or file). */
private fun targetsOf(json: JSONArray?): List<SetTarget?> = json?.let { a ->
    (0 until a.length()).map { i -> a.optJSONArray(i)?.let { SetTarget(if (it.isNull(0)) null else it.getDouble(0), if (it.isNull(1)) null else it.getInt(1)) } }
}.orEmpty()

/**
 * A named list of exercises with planned sets and, optionally, the values to aim for in each (`TemplateBlock` is in
 * core, with the rules for updating values); starting it fills the workout's cards. A block with no values is
 * stored exactly as it was before values existed.
 */
data class Template(val id: Long, val name: String, val blocks: List<TemplateBlock>, val folder: String = "") {
    fun blocksJson(): JSONArray = JSONArray(blocks.map { b ->
        JSONObject().put("exercise", b.exercise).put("sets", JSONArray(b.sets.map { it.letter })).also { o ->
            b.superset?.let { o.put("superset", it) }
            tidyTargets(b.targets).takeIf { it.isNotEmpty() }?.let { o.put("targets", targetsJson(it)) }
        }
    })

    companion object {
        fun blocksOf(json: JSONArray): List<TemplateBlock> = (0 until json.length()).map { i ->
            val b = json.getJSONObject(i)
            val sets = b.getJSONArray("sets")
            TemplateBlock(b.getString("exercise"), (0 until sets.length()).map { n -> SetType.entries.firstOrNull { it.letter == sets.getString(n) } ?: SetType.NORMAL }, if (b.has("superset")) b.getString("superset") else null, targetsOf(b.optJSONArray("targets")))
        }
    }
}

/** Asked when a workout started from [template] was finished in a different shape: [blocks] is what was actually done. */
data class TemplateOffer(val template: Template, val blocks: List<TemplateBlock>)

/** The rep limits Settings offers for the estimated 1RM. */
val ONE_RM_LIMITS = listOf(10, 12, 15, 20)
/** The 1RM formulas Settings offers, the recommended first. */
val ONE_RM_CHOICES = listOf(OneRmFormula.HYBRID, OneRmFormula.BRZYCKI, OneRmFormula.EPLEY)

/** The template folder the "Up next" widget goes round, when it holds any templates. */
const val CURRENT_SPLIT = "Current split"

const val STACK_KEY = "stack"

sealed interface Screen {
    data object Active : Screen
    /** A finished workout being corrected (`AppModel.correction`). */
    data object Correct : Screen
    data object Settings : Screen
    data object ExerciseSetup : Screen
    data class TemplateEdit(val id: Long?) : Screen
    data class WorkoutDetail(val date: String) : Screen
    /** [edit] opens the page already in editing, for an exercise that has just been made. */
    data class ExerciseDetail(val name: String, val edit: Boolean = false, val tab: Int? = null) : Screen
    data object Chart : Screen
    /** What a workout came to, shown when it is finished. */
    data class Summary(val date: String) : Screen
    /** One week of training, by its Monday (`yyyy-MM-dd`). */
    data class Week(val monday: String) : Screen
    data class MeasurementDetail(val file: String) : Screen
    data class MuscleDetail(val muscle: Muscle) : Screen
    data class HeadDetail(val head: Head) : Screen
    data class Routine(val name: String) : Screen
    data class TemplateStats(val id: Long) : Screen
    /** Training week by week. */
    data object Trends : Screen
    data class Month(val month: String) : Screen
    data class PrTimeline(val exercise: String) : Screen
    /** [day] (`yyyy-MM-dd`), when given, is shown highlighted with its month in view. */
    data class Calendar(val day: String? = null) : Screen
    data object Bmi : Screen
    /** Every variation of [exercise]'s overall exercise, taken together. */
    data class Overall(val exercise: String) : Screen
    /** Notes that look like variations, for all exercises or just [exercise]. */
    data class NoteReview(val exercise: String? = null) : Screen
    /** The weekly streak, the badges and the habits behind them. */
    data object Trophies : Screen
    /** The weekly program: seven days of templates and rest. */
    data object Program : Screen
    /** Best lifts and PRs by month. */
    data object PersonalBests : Screen
    /** Every workout, searchable, reached from Progress. */
    data object History : Screen
    /** The body figure with every muscle, reached from Progress. */
    data object Body : Screen
    /** The measurements: BMI, the files and a new one. */
    data object Measurements : Screen
    /** Photos (Round 39: progress photos and snapshots in one timeline); [Photo] is one full screen among those [filter] lets through, [PhotoCompare] two progress photos side by side. */
    data object Photos : Screen
    data class Photo(val name: String, val filter: PhotoFilter = PhotoFilter.ALL) : Screen
    data class PhotoCompare(val pose: Pose?) : Screen
    /** The old Snapshots page: now the Photos page, filtered to the snapshots (kept so a stored route still opens). */
    data object Moments : Screen
    /** The numbers of one year of training (see YearReview.kt). */
    data class YearReview(val year: Int) : Screen
}

/** The page with [from] named [to], for a page that was open on an exercise when it was renamed. */
fun Screen.renamedExercise(from: String, to: String): Screen = when {
    this is Screen.ExerciseDetail && name == from -> copy(name = to, edit = false)
    this is Screen.PrTimeline && exercise == from -> copy(exercise = to)
    this is Screen.Overall && exercise == from -> copy(exercise = to)
    this is Screen.NoteReview && exercise == from -> copy(exercise = to)
    else -> this
}

/** The swipeable tabs, in the order the bar and the pager lay them out. Exercises is the Library. Workout (the plus button's sheet) is not a tab. */
enum class Tab(val label: String) { Home("Home"), Progress("Progress"), Exercises("Library"), Profile("Profile") }

/** Whether Back goes to Home rather than leaving the app: no page is open and the tab on show is not Home. */
fun backGoesHome(noPage: Boolean, tab: Tab): Boolean = noPage && tab != Tab.Home

fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Kilograms shown in [unit]. */
fun showWeight(kg: Double, unit: String): String = shortNumber(if (unit == "lb") kgToLb(kg) else kg)

/** An estimated 1RM as shown: a guess, so to a tenth in the shown unit, not to the hundredth a logged weight may need. */
/** The entries of [file] among [all] (newest first, as the model holds them) as (day, stored value), oldest first; an entry with no date or number is left out. */
fun measurementSeriesOf(all: List<MeasurementEntity>, file: String): List<Pair<LocalDate, Double>> =
    all.filter { it.file == file }.mapNotNull { m ->
        val day = parseDate(m.date)?.toLocalDate() ?: return@mapNotNull null
        m.value.toDoubleOrNull()?.let { day to it }
    }.reversed()

fun showEstimate(kg: Double, unit: String): String = shortNumber(tenth(if (unit == "lb") kgToLb(kg) else kg))

/** [value] to a tenth, for an estimate on a chart or in a line. */
fun tenth(value: Double): Double = roundHalfUp(value * 10) / 10.0

/** A typed weight in [unit] as kilograms; pounds are converted at four decimals, as Strong does. */
fun toKg(value: Double, unit: String): Double = if (unit == "lb") lbToKg(value) else value

fun strongDate(millis: Long): String = localDateTimeOf(millis).format(STRONG_DATE_FORMAT)

@OptIn(ExperimentalCoroutinesApi::class)
class AppModel(val platform: Platform, private val saved: SavedStateHandle) : ViewModel() {
    init { Formats.platform = platform }
    // Declared first: flows below start working on Dispatchers.Default while the model is still being built, and [sessionsIn] (which they
    // call) must find its lock and cache already made (declared further down, they were still null then: a crash at start).
    private val indexLock = SynchronizedObject()
    private var indexed: List<Workout>? = null
    private var index: Map<String, List<Session>> = emptyMap()
    val repo = platform.createRepo()
    private val dao = repo.dao

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)
    /** Set with [loaded]: the first page's figures are worked out, and the rest may start. */
    private val firstPageDone = MutableStateFlow(false)
    /**
     * [state] for figures no first page shows (the Body page's): worked out once the first page's are, so they do not take the
     * phone's cores from them while the app starts, and from then on kept up to date like any other.
     */
    private fun <T> Flow<T>.later(initial: T): StateFlow<T> {
        val source = this
        return firstPageDone.filter { it }.take(1).flatMapLatest { source }.state(initial)
    }
    private fun <T> perProfile(empty: T, query: (Long) -> Flow<T>) =
        activeId.flatMapLatest { id -> if (id == null) flowOf(empty) else query(id) }

    val profiles = dao.profiles().state(emptyList())
    // Room reads the table again after every write to it, a workout's draft included (each tick and each number typed); the map is made
    // and compared off the main thread, so a read that changed nothing it shows costs the screen nothing.
    private val kv = dao.kv().map { list -> list.associate { it.key to it.value } }.distinctUntilChanged().flowOn(Dispatchers.Default).state(emptyMap())
    val activeId: StateFlow<Long?> = kv.map { it["active"]?.toLongOrNull() }.distinctUntilChanged().state(null)
    val profile: StateFlow<Profile?> = combine(profiles, activeId) { all, id -> all.firstOrNull { it.id == id } }.state(null)
    /** Whether the phone is in dark mode, set by MainActivity; read by the look when "Theme" is System. */
    val systemDark = MutableStateFlow(false)
    /**
     * The look (1.1.0): Dark, Light or Phone (kv `look.theme`, default dark), the Pure black switch (`look.black`, Dark only, default off)
     * and one of eight themes (`look.accent` holds its name, default Graphite), with `theme.cbsafe` for the colour-blind safe set. The older keys were turned
     * into these once at start-up (`migrateLook`) and are ignored.
     */
    val look: StateFlow<Look> = combine(kv, systemDark) { o, system ->
        resolveLook(o[LOOK_THEME_KEY] ?: "dark", o[LOOK_BLACK_KEY] == "on", o[LOOK_ACCENT_KEY] ?: "Graphite", system, o["theme.cbsafe"] == "on")
    }.distinctUntilChanged().state(resolveLook())

    /** Round 32: the page's background, `theme` (the theme's own, the default) or a background's id (kv `look.background`), and the photo picked for Photo (`p:` or `m:` and the file name). */
    val background: StateFlow<String> = kv.map { it[LOOK_BACKGROUND_KEY] ?: BACKGROUND_THEME }.distinctUntilChanged().state(BACKGROUND_THEME)
    val backgroundPhoto: StateFlow<String?> = kv.map { it[LOOK_BACKGROUND_PHOTO_KEY] }.distinctUntilChanged().state(null)

    /** Chooses a background by id, or `theme`. */
    fun setBackground(id: String) = setOption(LOOK_BACKGROUND_KEY, id)

    /** Chooses Photo with [pick]: `p:` and a progress photo's file name, or `m:` and a snapshot's. */
    fun setBackgroundPhoto(pick: String) { setOption(LOOK_BACKGROUND_PHOTO_KEY, pick); setOption(LOOK_BACKGROUND_KEY, "photo") }

    /** The stored theme choice: `dark`, `light` or `phone`. */
    val theme: StateFlow<String> = kv.map { o -> o[LOOK_THEME_KEY]?.takeIf { it in LOOK_THEMES } ?: "dark" }.distinctUntilChanged().state("dark")

    // Look and feel, and the auto-fill preference: small values kept under their own keys.
    val textSize: StateFlow<String> = kv.map { it["textSize"] ?: "Medium" }.state("Medium")
    /** Settings, Appearance, Performance (`Performance.kt`): the tier, Full when none was chosen. */
    val performance: StateFlow<Performance> = kv.map { Performance.of(it[Performance.KEY]) }.distinctUntilChanged().state(Performance.Full)
    /** How many working sets and drop sets "Auto-fill sets" lays out. */
    val auto: StateFlow<Triple<Int, Int, Int>> = kv.map {
        Triple(it["auto.sets"]?.toIntOrNull() ?: 2, it["auto.drops"]?.toIntOrNull() ?: 1, it["auto.warm"]?.toIntOrNull() ?: 1)
    }.state(Triple(2, 1, 1))
    /** The colour of every sticky note's band: a name from the list or a colour of the user's own. */
    val stickyColor: StateFlow<String> = kv.map { it["sticky.color"].orEmpty() }.state("")
    /** The estimated 1RM formula, its rep limit and whether RPE counts (Settings, Workout); Hybrid up to 12, RPE off, unless changed. */
    val oneRm: StateFlow<OneRm> = kv.map {
        OneRm(
            it["onerm.formula"]?.let { name -> OneRmFormula.entries.firstOrNull { f -> f.name == name } } ?: OneRm.DEFAULT.formula,
            it["onerm.maxReps"]?.toIntOrNull()?.takeIf { n -> n in ONE_RM_LIMITS } ?: OneRm.DEFAULT.maxReps,
            factorRpe = it["onerm.rpe"] == "on",
        )
    }.distinctUntilChanged().state(OneRm.DEFAULT)

    /** The chart being shown full screen. */
    var fullChart by mutableStateOf<FullChart?>(null)
    /** Set by Settings to have the Workout sheet open the template file picker. */
    var importTemplatesNow by mutableStateOf(false)
    /** Set by the empty Profile card to have Settings open the file picker for an import. */
    var importNow by mutableStateOf(false)

    /** Built-in exercises never logged stay out of the lists until searched for. */

    fun setOption(key: String, value: String?) = viewModelScope.launch { if (value == null) dao.remove(key) else dao.put(Kv(key, value)) }

    /** Everything on screen is derived from this list; nothing computed is stored. */
    val allWorkouts: StateFlow<List<Workout>> = perProfile(emptyList()) { id ->
        dao.rows(id).map { rows -> buildWorkouts(rows.map { it.toRow() }) }.flowOn(Dispatchers.Default)
    }.state(emptyList())

    /** Each workout's tag by its start (kv `tag:<profileId>:<start>`); only workouts that have one. Clean's own, never in the CSV. */
    val workoutTags: StateFlow<Map<String, String>> = combine(kv, activeId) { options, id ->
        val prefix = "tag:$id:"
        options.entries.filter { it.key.startsWith(prefix) && it.value.isNotEmpty() }.associate { it.key.removePrefix(prefix) to it.value }
    }.distinctUntilChanged().state(emptyMap())

    /** The tags switched to "Leave out of stats" (kv `tag.out:<profileId>:<tag>` = `on`). */
    val leftOutTags: StateFlow<Set<String>> = combine(kv, activeId) { options, id ->
        val prefix = "tag.out:$id:"
        options.entries.filter { it.key.startsWith(prefix) && it.value == "on" }.mapTo(HashSet()) { it.key.removePrefix(prefix) }
    }.distinctUntilChanged().state(emptySet())

    /** The tags in use on the active profile's workouts, most used first: what the chips suggest and History filters by. */
    val tagsInUse: StateFlow<List<String>> = combine(allWorkouts, workoutTags) { all, tags ->
        clean.core.tagsByUse(tags, all.mapTo(HashSet()) { it.date })
    }.distinctUntilChanged().state(emptyList())

    /**
     * The workouts every statistic, record, streak and calendar is worked out from: [allWorkouts] less those whose tag is left out of
     * the stats. The one place that rule lives; History and a workout's own page read [allWorkouts]. The same list when nothing is left out.
     */
    val workouts: StateFlow<List<Workout>> = combine(allWorkouts, workoutTags, leftOutTags) { all, tags, out ->
        clean.core.statWorkouts(all, tags, out)
    }.state(emptyList())

    /** Gives a finished workout a tag, or takes it off with an empty one. The spelling of a tag already in use is kept. */
    fun setWorkoutTag(date: String, tag: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val spelt = clean.core.spellTag(tag, tagsInUse.value)
        if (spelt.isEmpty()) dao.remove("tag:$id:$date") else dao.put(Kv("tag:$id:$date", spelt))
    }

    /** Switches "Leave out of stats" for a tag. */
    fun setTagLeftOut(tag: String, out: Boolean) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (out) dao.put(Kv("tag.out:$id:$tag", "on")) else dao.remove("tag.out:$id:$tag")
    }
    val files: StateFlow<List<MeasurementFileEntity>> = perProfile(emptyList()) { dao.files(it) }.state(emptyList())
    val measurements: StateFlow<List<MeasurementEntity>> = perProfile(emptyList()) { dao.measurements(it) }.state(emptyList())
    val settings: StateFlow<Map<String, ExerciseSetting>> =
        perProfile(emptyMap()) { id -> dao.settings(id).map { list -> list.associateBy { it.name } }.distinctUntilChanged().flowOn(Dispatchers.Default) }.state(emptyMap())
    /**
     * The answers of [musclesOf] and [loadTypeOf] for one version of the exercise settings: every pass over the history asks them for
     * each block, and decoding the stored text each time was most of the Body page's work. A new version of the settings starts afresh.
     * Declared before anything that starts work reading it.
     */
    private class ExerciseMemo(val settings: Map<String, ExerciseSetting>) {
        val muscles = ConcurrentCache<String, MuscleUse>()
        val loadTypes = ConcurrentCache<String, LoadType>()
    }
    @kotlin.concurrent.Volatile private var exerciseMemo = ExerciseMemo(emptyMap())
    private fun memo(): ExerciseMemo {
        val now = settings.value
        val m = exerciseMemo
        return if (m.settings === now) m else ExerciseMemo(now).also { exerciseMemo = it }
    }

    val templates: StateFlow<List<Template>> = perProfile(emptyList()) { id ->
        dao.templates(id).map { list -> list.mapNotNull { t -> runCatching { Template(t.id, t.name, Template.blocksOf(JSONArray(t.blocks)), t.folder) }.getOrNull() } }.flowOn(Dispatchers.Default)
    }.state(emptyList())

    /** Each measurement file's entries as (day, stored value), oldest first, worked out off the main thread for the charts of the measurement pages. */
    val measurementSeries: StateFlow<Map<String, List<Pair<LocalDate, Double>>>> = measurements.map { all ->
        all.map { it.file }.distinct().associateWith { measurementSeriesOf(all, it) }
    }.flowOn(Dispatchers.Default).state(emptyMap())

    /** The bodyweight log, for the real load of bodyweight and assisted lifts. */
    val bodyweights: StateFlow<Bodyweights> = measurements.map { all ->
        Bodyweights(all.filter { it.file == "weight.csv" }.mapNotNull { m -> m.value.toDoubleOrNull()?.let { m.date to it } })
    }.state(Bodyweights(emptyList()))

    /** The PRs each set of each workout set: by workout start time, then in the order of the workout's sets. */
    /** What each exercise is logged as: the only part of an exercise's settings the PRs depend on. */
    private val loadTypes = settings.map { all -> all.mapValues { it.value.loadType } }.distinctUntilChanged()
    val prs: StateFlow<Map<String, List<Set<PrKind>>>> = combine(workouts, loadTypes, bodyweights, oneRm) { all, _, weights, rule ->
        prsOf(all, rule, ::loadTypeOf, weights::at)
    }.flowOn(Dispatchers.Default).state(emptyMap())

    /** What a History card shows that is worked out from a whole workout: its volume, each exercise's best estimated 1RM, and each side's for a left and right one. */
    class WorkoutFigures(val volume: Double, val estimates: List<Double?>, val sides: List<Pair<Double?, Double?>?>)

    /** [WorkoutFigures] of every workout by its date, worked out off the main thread whenever the history, the 1RM rule, the bodyweights or the settings change. */
    val workoutFigures: StateFlow<Map<String, WorkoutFigures>> = combine(allWorkouts, oneRm, bodyweights, settings) { all, rule, weights, _ ->
        all.associate { w ->
            w.date to WorkoutFigures(
                volumeOf(w, ::loadOf),
                w.blocks.map { b -> if (b.paired) null else progressOf(listOf(Session(w.date, b.sets)), rule.shown(), loadTypeOf(b.exercise), weights::at).firstOrNull()?.estimated1Rm },
                w.blocks.map { b -> if (b.paired && b.workingCount > 0.0) sideOneRms(w.date, b, rule.shown(), loadTypeOf(b.exercise), weights::at) else null },
            )
        }
    }.flowOn(Dispatchers.Default).state(emptyMap())

    /** Set once the global `goal.week`, `program` and `reward.seen` of before profiles had their own have been moved to the profile that was active. */
    private val adopted = MutableStateFlow(false)

    /** The day it is, changed at midnight and when the app comes back to the front, so "Today" and "Yesterday" never go stale. */
    var today by mutableStateOf(LocalDate.now())
        private set

    fun refreshToday() { LocalDate.now().let { if (it != today) today = it } }

    /** The flows that depend on the day are worked out again when it changes. */
    private val todayFlow = snapshotFlow { today }.distinctUntilChanged()

    /** What each card's line last showed (its number of points), so a point added since is drawn in once ([rememberNewPoint]). Memory only. */
    val pointsSeen = HashMap<String, Int>()

    /**
     * The weekly target and when each took effect (kv `goal.week:<profileId>`), null until the stored options have been read. Until one is
     * stored it is the user's own usual pace from the first workout's week, so that history is not scored against a later pick.
     */
    val targets: StateFlow<List<TargetFrom>?> = combine(
        combine(kv, activeId) { options, id -> if (options.isEmpty() || id == null) null else options["goal.week:$id"].orEmpty() }.distinctUntilChanged(),
        workouts, adopted, todayFlow,
    ) { stored, all, ready, day ->
        if (stored == null || !ready) null else targetsOrDefault(decodeTargets(stored.ifEmpty { null }), all, day)
    }.flowOn(Dispatchers.Default).state(null)

    /** The weekly target, streak, badges and habits; nothing about them is stored, so this is worked out again when the workouts or the target change. */
    val consistency: StateFlow<Consistency?> = combine(workouts, targets, todayFlow) { all, t, day ->
        t?.let { clean.core.consistency(all, it, day) }
    }.flowOn(Dispatchers.Default).state(null)

    /** Each week's totals (the lines on the tiles), worked out off the main thread; volume follows the load types and weigh-ins. */
    val weekTotals: StateFlow<List<clean.core.WeekTotal>> = combine(workouts, loadTypes, bodyweights) { all, _, _ ->
        clean.core.weeklyTotals(all, ::loadOf)
    }.flowOn(Dispatchers.Default).state(emptyList())

    /** The program as stored (kv `program:<profileId>`): versions oldest first, none when no program is followed. */
    val program: StateFlow<List<ProgramVersion>> = combine(kv, activeId) { options, id -> decodeProgram(options["program:$id"]) }.distinctUntilChanged().state(emptyList())

    /** The key of a per-profile option for the active profile, null before there is one. */
    private fun profileKey(base: String): String? = activeId.value?.let { "$base:$it" }

    /** Each week against the program, its streak and badges; null with no program. Worked out again when the workouts, the program or an overall exercise change. */
    val programStanding: StateFlow<ProgramStanding?> = combine(workouts, program, settings, todayFlow) { all, versions, own, day ->
        if (versions.isEmpty()) null else programStanding(versions, all, day, overallLookup(own))
    }.flowOn(Dispatchers.Default).state(null)

    /** Today's place on the program and what to do next; null with no program. */
    val programToday: StateFlow<TodayPlan?> = combine(workouts, program, settings, todayFlow) { all, versions, own, day ->
        if (versions.isEmpty()) null else todayPlan(versions, all, day, overallLookup(own))
    }.flowOn(Dispatchers.Default).state(null)

    /** The double progression scheme of each exercise of the active profile (kv `prog:<profileId>:<exercise name as stored>`). */
    val progressions: StateFlow<Map<String, Progression>> = combine(kv, activeId) { options, id ->
        val prefix = "prog:$id:"
        options.entries.filter { it.key.startsWith(prefix) }.mapNotNull { (key, value) -> Progression.decode(value)?.let { key.removePrefix(prefix) to it } }.toMap()
    }.distinctUntilChanged().state(emptyMap())

    /** The exercises of the active profile done left and right (kv `sides:<profileId>:<exercise name as stored>` = `on`). */
    val sideExercises: StateFlow<Set<String>> = combine(kv, activeId) { options, id ->
        val prefix = "sides:$id:"
        options.entries.filter { it.key.startsWith(prefix) && it.value == "on" }.mapTo(HashSet()) { it.key.removePrefix(prefix) }
    }.distinctUntilChanged().state(emptySet())

    /** Whether the exercise, as stored, is done left and right. */
    fun sidesOf(exercise: String): Boolean = exercise in sideExercises.value

    /**
     * Turns left and right on or off for [exercise]: the remembered switch that new cards start from, and the cards of it in
     * the workout in progress. Turning it on gives every set of a card not paired yet a partner for the other side (three sets
     * become three pairs); turning it off takes the right halves that were not ticked away again, and leaves a ticked one as a
     * set. Finished workouts are never touched: each carries its own marker.
     */
    fun setSides(exercise: String, on: Boolean) {
        val id = activeId.value ?: return
        setOption("sides:$id:$exercise", if (on) "on" else null)
        // The workout in progress and a finished one being corrected both follow.
        for (slot in listOf(liveSlot, correctionSlot)) {
            val d = slot.draft ?: continue
            if (d.blocks.any { it.exercise == exercise && it.paired != on }) slot.put(d.copy(blocks = d.blocks.map {
                if (it.exercise != exercise || it.paired == on) it else if (on) it.withSetsDoubled().copy(paired = true) else it.withRightHalvesDropped().copy(paired = false)
            }))
        }
    }

    /**
     * Marks one block of a finished workout as left and right, or takes the mark off (the nth block of [exercise] in the
     * workout starting at [date]): only the marker row is added or removed, with an Undo.
     */
    fun readAsSides(date: String, exercise: String, nth: Int, on: Boolean) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (!repo.markSides(id, date, exercise, nth, on)) return@launch
        platform.refreshWidgets()
        say(if (on) "Read as left and right" else "Read as ordinary sets") {
            viewModelScope.launch {
                repo.markSides(id, date, exercise, nth, !on)
                platform.refreshWidgets()
            }
        }
    }

    /** Sets or, with null, takes off an exercise's progression. */
    fun setProgression(exercise: String, scheme: Progression?) {
        val id = activeId.value ?: return
        setOption("prog:$id:$exercise", scheme?.encode())
    }

    /** The weekly set range of each muscle for the active profile: its own (kv `target.<MUSCLE>:<id>`), else the default (`target.sets:<id>`, 10 to 20); null where switched off. */
    val setRanges: StateFlow<Map<Muscle, SetRange?>> = combine(kv, activeId) { options, id ->
        Muscle.entries.associateWith { setRangeFor(options["target.${it.name}:$id"], options["target.sets:$id"]) }
    }.distinctUntilChanged().state(Muscle.entries.associateWith { SetRange.DEFAULT })

    /** The default weekly set range, the one a muscle without its own uses. */
    val defaultSetRange: StateFlow<SetRange> = combine(kv, activeId) { options, id -> SetRange.decode(options["target.sets:$id"]) ?: SetRange.DEFAULT }.distinctUntilChanged().state(SetRange.DEFAULT)

    /** Stores a weekly set range as `10-20` or `off` for [muscle], or for the default when it is null; null text takes the muscle's own range away. */
    fun setSetRange(muscle: Muscle?, text: String?) {
        val id = activeId.value ?: return
        setOption("${if (muscle == null) "target.sets" else "target.${muscle.name}"}:$id", text)
    }

    /** Templates passed over with "Not this one" on the Workout tab; for this run of the app only. */
    var skippedTemplates by mutableStateOf(emptySet<Long>())

    private fun overallLookup(own: Map<String, ExerciseSetting>): (String) -> String = { name -> overallName(name) { own[it]?.overall } }

    /** A template as the program saves it: its name and its distinct overall exercises now. */
    fun snapshotOf(template: Template): ProgramDay = ProgramDay(template.name, template.id, template.blocks.map { overallOf(it.exercise) }.distinct())

    /**
     * Follows [days] from the Monday [from]: a version is added (one dated the same Monday is replaced, so no earlier week is
     * re-scored). Badges the new program has already earned are marked seen, so a backdated start does not celebrate them late.
     */
    fun saveProgram(days: List<ProgramDay?>, from: LocalDate) {
        val key = profileKey("program") ?: return
        val versions = withVersion(program.value, ProgramVersion(from, days))
        viewModelScope.launch {
            setOption(key, encodeProgram(versions)).join()
            platform.refreshWidgets()
            val lookup = overallLookup(settings.value)
            val earned = withContext(Dispatchers.Default) { programStanding(versions, workouts.value, LocalDate.now(), lookup).awards.filter { it.earned != null }.map { it.id } }
            markSeen(earned)
        }
    }

    /** Stops following a program; the weekly target and its streak stay. */
    fun stopProgram() {
        val key = profileKey("program") ?: return
        viewModelScope.launch {
            setOption(key, null).join()
            platform.refreshWidgets()
        }
    }

    /** Sets the weekly target from this week on; earlier weeks keep the target they were scored by. */
    fun setWeeklyTarget(n: Int) {
        val now = targets.value ?: return
        val key = profileKey("goal.week") ?: return
        setOption(key, encodeTargets(withTarget(now, weekStart(LocalDate.now()), n)))
    }

    /** Records that these badges (and week and shield celebrations) have been shown, so each is celebrated once. */
    fun markSeen(ids: Collection<String>) {
        // Two celebrations can be marked before the first write has come back through the options, so what was marked is kept here too.
        val id = activeId.value ?: return
        val mine = marked.getOrPut(id) { HashSet() }
        mine += ids
        val seen = rewardSeen().filter { it.isNotEmpty() }
        setOption("reward.seen:$id", (seen + mine).distinct().joinToString(","))
    }
    private val marked = HashMap<Long, HashSet<String>>()

    /** The celebrations already shown for the active profile (kv `reward.seen:<profileId>`). */
    fun rewardSeen(): Set<String> = (profileKey("reward.seen")?.let { options.value[it] }.orEmpty()).split(',').toSet()

    /** The milestone chips already glinted for the active profile (kv `glint.seen:<profileId>`); null until the first run of the glint has set it. */
    fun glintSeen(): Set<String>? = profileKey("glint.seen")?.let { options.value[it] }?.split(',')?.filter { it.isNotEmpty() }?.toSet()

    /** Records the milestone chips that have been glinted (or, the first time, the ones already earned), so each glints once. */
    fun markGlinted(ids: Collection<String>) {
        val key = profileKey("glint.seen") ?: return
        setOption(key, (options.value[key]?.split(',').orEmpty().filter { it.isNotEmpty() } + ids).distinct().joinToString(","))
    }

    /** The week (its Monday) whose ring has closed with its spring for the active profile (kv `ring.closed:<profileId>`). */
    fun ringClosedWeek(): String? = profileKey("ring.closed")?.let { options.value[it] }

    fun markRingClosed(week: String) { profileKey("ring.closed")?.let { setOption(it, week) } }

    /** The day (`YYYY-MM-DD`) the muscles trained today last breathed on the Body card for the active profile (kv `breath.day:<profileId>`). */
    fun breathDay(): String? = profileKey("breath.day")?.let { options.value[it] }

    fun markBreath(day: String) { profileKey("breath.day")?.let { setOption(it, day) } }

    var draft by mutableStateOf<Draft?>(null)
        private set

    /**
     * A finished workout being corrected on the Correct page (`Screen.Correct`), apart from the workout in progress: no clock, rest,
     * banner or notification, and nothing is written to the workout until Save. [Draft.editOf] is the start of the workout it
     * replaces, [Draft.pastDate] and [Draft.pastSeconds] its start and length as edited; its sets are never ticked (every set that
     * holds its values counts). Kept under kv `draft:correct:<profile>`, and the workout as it was under `draft:correct0:<profile>`.
     */
    var correction by mutableStateOf<Draft?>(null)
        private set
    /** The correction as it was opened, to tell whether anything was changed. */
    var correctionFrom by mutableStateOf<Draft?>(null)
        private set

    /** Which workout a card belongs to: the one in progress, or the finished one being corrected. */
    inner class DraftSlot(val correcting: Boolean) {
        val draft: Draft? get() = if (correcting) correction else this@AppModel.draft
        fun put(d: Draft?) { if (correcting) putCorrection(d) else putDraft(d) }
    }
    val liveSlot = DraftSlot(false)
    val correctionSlot = DraftSlot(true)
    /** The correction's writes to the database, newest only (see [putCorrection]). */
    private val correctionWrites = Channel<Pair<Long, Draft?>>(Channel.CONFLATED)
    /** Every small stored option, for the screens that remember how they were left (see [remembered]). */
    val options: StateFlow<Map<String, String>> get() = kv

    // The app opens on Home when it has been closed; the tab is not stored.
    var tab by mutableStateOf(Tab.Home)

    /** The History page over Progress, whatever was open before. */
    fun openHistory(keepTab: Boolean = stack.isEmpty()) { stack.clear(); if (!keepTab) tab = Tab.Progress; open(Screen.History) }

    /** The Body page over Progress, on [mode] (Balance, Fatigue, Strength) when given. Opened from a tab ([keepTab]), Back returns to that tab. */
    fun openBody(mode: String? = null, keepTab: Boolean = stack.isEmpty()) {
        if (mode != null) setOption("ui.bodyMode", mode)
        stack.clear(); if (!keepTab) tab = Tab.Progress; open(Screen.Body)
    }

    /** The time frame of the muscle figures, in days (0 is all time); shared by the Body tab and a muscle's page. */
    private var muscleDaysState by mutableStateOf(30L)
    private val muscleDaysFlow = MutableStateFlow(30L)
    var muscleDays: Long
        get() = muscleDaysState
        set(value) {
            muscleDaysState = value
            muscleDaysFlow.value = value
            setOption("ui.muscleDays", value.toString())
        }

    /** Bumped when a muscle screen is opened: fatigue depends on the time now, which no stored value changes. */
    private val muscleTick = MutableStateFlow(0)
    fun refreshMuscles() { muscleTick.value++ }

    /** Sets worked per muscle over the time frame [muscleDays], worked out off the main thread. */
    val muscleSetCounts: StateFlow<Map<Muscle, Double>> = combine(workouts, settings, muscleDaysFlow, muscleTick) { all, _, days, _ ->
        muscleSets(all, if (days == 0L) null else LocalDateTime.now().minusDays(days), ::musclesOf)
    }.flowOn(Dispatchers.Default).later(emptyMap())

    /**
     * Sets per muscle by side over the time frame, and whether any of the work in it was logged left and right: the Body
     * tab's Sides view is there only then.
     */
    class SideFigures(val sets: Map<Muscle, SideCredit>, val paired: Boolean)

    val muscleSides: StateFlow<SideFigures> = combine(workouts, settings, muscleDaysFlow, muscleTick) { all, _, days, _ ->
        val from = if (days == 0L) null else LocalDateTime.now().minusDays(days)
        SideFigures(muscleSetsBySide(all, from, ::musclesOf), hasSideWork(all, from))
    }.flowOn(Dispatchers.Default).later(SideFigures(emptyMap(), false))

    /** Sets per head over the time frame (and by side), and per group the sets that named none of its heads ("Other work"). */
    class HeadFigures(val sets: Map<Head, Double>, val other: Map<Muscle, Double>, val bySide: Map<Head, SideCredit> = emptyMap())

    val headFigures: StateFlow<HeadFigures> = combine(workouts, settings, muscleDaysFlow, muscleTick) { all, _, days, _ ->
        val from = if (days == 0L) null else LocalDateTime.now().minusDays(days)
        HeadFigures(
            headSets(all, from, ::musclesOf), Head.entries.map { it.group }.distinct().associateWith { otherWork(it, all, from, ::musclesOf) },
            headSetsBySide(all, from, ::musclesOf),
        )
    }.flowOn(Dispatchers.Default).later(HeadFigures(emptyMap(), emptyMap()))

    /** What the Body tab's fatigue and strength views and a muscle's page show, as of [at]. */
    class MuscleFigures(
        val at: LocalDateTime,
        val fatigue: Map<Muscle, Double>,
        val strength: Map<Muscle, Double>,
        val lastTrained: Map<Muscle, LocalDateTime>,
        /** The same three figures by side, which the figure's limbs and the rows' captions read. */
        val fatigueSides: Map<Muscle, SideCredit>,
        val strengthSides: Map<Muscle, SideCredit>,
        val lastTrainedSides: Map<Side, Map<Muscle, LocalDateTime>>,
    )

    val debuggable = platform.isDebuggable

    val muscleFigures: StateFlow<MuscleFigures?> = combine(workouts, settings, bodyweights, muscleTick) { all, _, _, _ ->
        val now = LocalDateTime.now()
        MuscleFigures(
            now,
            fatigue(all, now, ::musclesOf, ::loadOf),
            retainedStrength(all, now, ::musclesOf),
            lastTrained(all, ::musclesOf),
            fatigueBySide(all, now, ::musclesOf, ::loadOf),
            retainedStrengthBySide(all, now, ::musclesOf),
            lastTrainedBySide(all, ::musclesOf),
        )
    }.flowOn(Dispatchers.Default).state(null)
    /** The signals for a lighter week as they stand; worked out again when the log, the goals or the fatigue change. */
    val deload: StateFlow<Deload?> = combine(workouts, settings, bodyweights, oneRm, muscleFigures) { all, own, weights, rule, figures ->
        val goals = own.filter { parseGoal(it.value.goal) != null }.keys
        deloadSignals(all, LocalDate.now(), goals, { name -> progressOf(sessionsIn(all, name), rule, loadTypeOf(name), weights::at) }, figures?.fatigue.orEmpty())
    }.flowOn(Dispatchers.Default).state(null)

    /** The signals when a lighter week is worth suggesting and has not been put off (kv `deload.snooze:<profileId>`, the day "Not now" was pressed, good for 7 days). */
    val deloadSuggestion: StateFlow<Deload?> = combine(deload, kv, activeId) { signals, options, id ->
        val snoozed = options["deload.snooze:$id"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { LocalDate.now() < it.plusDays(7) } == true
        signals?.takeIf { it.suggested && !snoozed }
    }.state(null)

    fun snoozeDeload() {
        profileKey("deload.snooze")?.let { setOption(it, LocalDate.now().toString()) }
    }

    /** The active profile's progress photos, newest first; read from the folder again by [refreshPhotos] and when the profile changes. */
    private val photoTick = MutableStateFlow(0)
    fun refreshPhotos() { photoTick.value++ }
    val photos: StateFlow<List<Photo>> = combine(activeId, photoTick) { id, _ -> id }
        .map { id -> if (id == null) emptyList() else platform.listPhotos(id) }.flowOn(ioDispatcher).state(emptyList())

    /** The active profile's moments (the Snapshots tile and its page), newest first; read from the folder again by [refreshMoments] and when the profile changes. */
    private val momentTick = MutableStateFlow(0)
    fun refreshMoments() { momentTick.value++ }
    val moments: StateFlow<List<Moment>> = combine(activeId, momentTick) { id, _ -> id }
        .map { id -> if (id == null) emptyList() else platform.listMoments(id) }.flowOn(ioDispatcher).state(emptyList())

    /** Round 39: both stores as one list for the Photos section, newest first (a layer over [photos] and [moments]; nothing is stored twice). */
    val pics: StateFlow<List<Pic>> = combine(photos, moments) { p, m -> mergedPics(p, m) }.state(emptyList())

    /** Round 39: pictures taken or chosen that wait for the answer to "What is it?" (their handles, and whether they come from the camera), and which Add is asking (Profile's or the page's). */
    var photoPending by mutableStateOf<Pair<List<String>, Boolean>?>(null)
    var photoAsker by mutableStateOf("")

    /** The pages open over the tabs. Kept in the saved state, so a page is still open after the phone killed the app in the background. */
    val stack = mutableStateListOf<Screen>().apply { saved.get<ArrayList<String>>(STACK_KEY)?.mapNotNullTo(this, ::decodeScreen) }

    init {
        viewModelScope.launch { snapshotFlow { stack.map(::encodeScreen) }.collect { saved[STACK_KEY] = ArrayList(it) } }
    }

    /** A line shown for a moment at the top of the screen. */
    class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)
    var notice by mutableStateOf<Notice?>(null)
    var message: String?
        get() = notice?.text
        set(value) { notice = value?.let { Notice(it) } }

    /** Whether "Back up now" (Settings, Data) is running. Kept here, so leaving the page does not stop a backup half way. */
    var backingUpNow by mutableStateOf(false)
        private set

    /** Runs the scheduled backup into the chosen folder at once and says how it went in the notice; the last-backup time and the error are kept as the scheduled run keeps them. */
    fun backUpNow() {
        if (backingUpNow) return
        backingUpNow = true
        val tree = kv.value[AutoBackupKeys.FOLDER].orEmpty()
        viewModelScope.launch {
            val why = try { platform.backupNow() } catch (e: Exception) { e.message ?: "The backup could not be made" }
            backingUpNow = false
            val folder = if (tree.isEmpty()) null else runCatching { platform.folderName(tree) }.getOrNull()
            message = if (why == null) "Backed up to ${folder ?: "the folder"}" else "Backup failed: ${why.replaceFirstChar { it.lowercase() }}"
        }
    }

    /** A finished workout's toast (B8): "Push saved" at once, then "Push saved · 3 PRs" when the records are worked out; View opens the workout. */
    fun saidSaved(name: String, date: String) {
        val first = Notice("$name saved", "View") { open(Screen.WorkoutDetail(date)) }
        notice = first
        viewModelScope.launch {
            val workout = kotlinx.coroutines.withTimeoutOrNull(5000) { workouts.first { list -> list.any { it.date == date } } }?.firstOrNull { it.date == date } ?: return@launch
            val kinds = kotlinx.coroutines.withTimeoutOrNull(2000) { prs.first { date in it } }?.get(date).orEmpty()
            val count = clean.core.recordCount(workout, kinds)
            if (count > 0 && notice === first) notice = Notice("$name saved · ${shortNumber(count)} PR${if (count == 1.0) "" else "s"}", "View", first.onAction)
        }
    }

    /** What can still be taken back, shown in the one Undo bar, and the clock time that chance ends ([UNDO_MILLIS] after the last step). */
    var undos by mutableStateOf<Undos?>(null)
        private set
    var undoUntil by mutableStateOf(0L)
        private set

    /**
     * Says [text] with an Undo that runs [undo]; the old value it puts back is kept in memory by the caller. A step taken while the bar
     * is showing joins it, and one Undo takes both back. [expire] runs once the chance has passed without an Undo.
     */
    fun say(text: String, expire: () -> Unit = {}, undo: () -> Unit) {
        val step = UndoStep(text, undo, expire)
        undos = undos?.plus(step) ?: Undos(step)
        undoUntil = currentMillis() + UNDO_MILLIS
    }

    /** The Undo button: every step in the bar is taken back, newest first. */
    fun takeBack() {
        val all = undos ?: return
        buzz(Haptic.Reject)
        undos = null
        all.undoAll()
    }

    /** The pill was swiped away: nothing is taken back, and what was kept for an Undo can go, as when its time is up. */
    fun dismissUndo() = undoExpired()

    /** The bar's time is up: what was kept for an Undo can go. */
    fun undoExpired() {
        val all = undos ?: return
        undos = null
        all.expireAll()
    }

    /** The page of Settings to open on (set by whatever sends the user there, read once when Settings opens). */
    var settingsPage: String? = null
    /** Whether the Profile page shows its edit dialog (name, units, height, profiles). */
    var profileEdit by mutableStateOf(false)

    /** Bumped when the active profile's picture is set or removed, so the circle reads its file again. */
    var avatarStamp by mutableStateOf(0)

    /** Keeps the picture [open] reads as the active profile's (centre-cropped square, in the app's files); a line says so when it cannot be read. */
    fun setAvatar(source: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (withContext(ioDispatcher) { platform.setAvatar(id, source) }) avatarStamp++ else message = "That picture could not be read"
    }

    fun removeAvatar() = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        withContext(ioDispatcher) { platform.deleteAvatar(id) }
        avatarStamp++
    }

    /** The pill's words on the first open of a week ("Last week: 3 of 3 ✓"), or null: none due, nothing to say, or it has gone. */
    var weekPill by mutableStateOf<String?>(null)

    /**
     * Once per run, when the profile's weeks are known: if this week's pill has not been shown for the active profile (kv `weekpill:<profileId>` holds the
     * Monday it last was), remembers it as shown and sets [weekPill] to last week's line, when there is one.
     */
    suspend fun checkWeekPill() {
        val id = activeId.first { it != null } ?: return
        val stored = targets.first { it != null } ?: return
        val today = LocalDate.now()
        val key = "weekpill:$id"
        while (!loaded) kotlinx.coroutines.delay(100)
        val all = workouts.value
        if (!clean.core.weekPillDue(kv.value[key], today)) return
        dao.put(Kv(key, weekStart(today).toString()))
        weekPill = clean.core.lastWeekLine(weekResults(all, stored, today), today, kv.value["goal.week:$id"]?.isNotEmpty() == true)
    }
    var clash by mutableStateOf<Clash?>(null)
    /** The item held for its menu (B5), if any. */
    val holds = HoldHost()
    /** Each tab's list keeps where it was scrolled to across tab switches and page round trips (C3). */
    val tabLists: Map<Tab, androidx.compose.foundation.lazy.LazyListState> = Tab.entries.associateWith { androidx.compose.foundation.lazy.LazyListState() }
    /** Activity's one calendar scroll and Time / Volume choice, shared by Home and Progress (and the copy drawn between them while the pager moves), so they always agree. */
    val activityScroll = androidx.compose.foundation.ScrollState(Int.MAX_VALUE)
    var activityByVolume by mutableStateOf(false)
    /** Where each page's Activity block starts (root y, px) wherever its list is scrolled to; NaN when it is not known. Home and Progress report theirs. */
    val activityY = mutableMapOf<Tab, androidx.compose.runtime.MutableFloatState>(Tab.Home to androidx.compose.runtime.mutableFloatStateOf(Float.NaN), Tab.Progress to androidx.compose.runtime.mutableFloatStateOf(Float.NaN))
    /** True while Activity is drawn once above the pager (Home and Progress both at the top, the pager between them); the per-page copies are then hidden. Set by the tab pager. */
    /** Where each page's Activity would start with no gap added (the top of its Activity item, root px), kept from when the page was last at its top; NaN when never seen. The page that starts it higher gets a gap so the two agree. */
    val activityNat = mutableMapOf<Tab, androidx.compose.runtime.MutableFloatState>(Tab.Home to androidx.compose.runtime.mutableFloatStateOf(Float.NaN), Tab.Progress to androidx.compose.runtime.mutableFloatStateOf(Float.NaN))
    /** The height (px) of each page's Activity block, with [activityY]; NaN when it is not known. */
    val activityH = mutableMapOf<Tab, androidx.compose.runtime.MutableFloatState>(Tab.Home to androidx.compose.runtime.mutableFloatStateOf(Float.NaN), Tab.Progress to androidx.compose.runtime.mutableFloatStateOf(Float.NaN))
    var activityOverlay: androidx.compose.runtime.State<Boolean> = mutableStateOf(false)
    /** The day (`YYYY-MM-DD`) of a workout just finished, until Home has shown its calendar square filling in. Memory only. */
    var freshDay by mutableStateOf<String?>(null)
    var templateOffer by mutableStateOf<TemplateOffer?>(null)

    // Declared before init, which starts the job that reads it.
    /** Drafts waiting to be stored. Only the newest matters, so one that is overtaken before it is written is dropped. */
    private val draftWrites = Channel<Pair<Long, Draft?>>(Channel.CONFLATED)
    /** Held while a draft is written and while a workout is saved, so a late write cannot bring a finished draft back. */
    private val draftLock = Mutex()

    /** True once the profile's workouts and measurements have been read, so the first page is not shown empty and then filled. */
    var loaded by mutableStateOf(false)
        private set

    /** True while the welcome questionnaire (`Welcome.kt`) is shown: the active profile is empty and has not finished or skipped it (kv `welcome:<profileId>`). */
    var welcoming by mutableStateOf(false)
        private set

    /** The running version's changes while the What's new pop-up is up (`WhatsNew.kt`, rule `whatsNewDue`); null otherwise. */
    var whatsNew by mutableStateOf<clean.core.Release?>(null)
        private set

    /** Every version's changes (`assets/changes.md`), read at start-up for the What's new page. */
    var allChanges by mutableStateOf<List<clean.core.Release>>(emptyList())
        private set

    /** Opens the What's new page for the running version (Settings, About). */
    fun showWhatsNew() {
        val current = platform.appVersionName
        whatsNew = allChanges.firstOrNull { it.version == current && it.changes.isNotEmpty() } ?: allChanges.firstOrNull { it.changes.isNotEmpty() }
    }

    /** Closes What's new; it does not come back until the next version (kv `whatsnew.seen`, the last version shown). */
    fun closeWhatsNew() {
        val r = whatsNew ?: return
        whatsNew = null
        setOption("whatsnew.seen", r.version)
    }

    /** Decided once at start-up, after the welcome: a version never seen before (a first run, or an app that never recorded one) is only recorded, so What's new shows after an update and never on a fresh start. */
    private suspend fun decideWhatsNew(changes: kotlinx.coroutines.Deferred<List<clean.core.Release>>) {
        val current = platform.appVersionName ?: return
        val seen = dao.get("whatsnew.seen")
        val releases = changes.await()
        allChanges = releases
        whatsNew = clean.core.whatsNewDue(releases, current, seen, firstRun = welcoming || seen == null)
        if (seen == null) dao.put(Kv("whatsnew.seen", current))
    }

    /** Once, on a phone that says it is low on memory or on battery saver, and never on top of the welcome or What's new: offers the Balanced performance tier. */
    private suspend fun suggestPerformance() {
        if (welcoming || whatsNew != null || !platform.lowPowerHint) return
        if (dao.get(Performance.SUGGESTED_KEY) != null || dao.get(Performance.KEY) != null) return
        dao.put(Kv(Performance.SUGGESTED_KEY, "1"))
        notice = Notice("This phone is short on memory or power. Balanced mode makes Clean lighter.", "Switch") { setOption(Performance.KEY, Performance.Balanced.key) }
    }

    private suspend fun welcomeDue(id: Long) = showWelcome(dao.rowCount(id), dao.measurementCount(id), dao.get("welcome:$id") != null)

    /**
     * Ends the welcome questionnaire (finished, skipped, or an import came in) and goes to Home. [target] is the weekly target answered in it, when
     * history was brought in afterwards: the target then applies from this week and the imported weeks keep the user's own usual pace.
     */
    fun finishWelcome(target: Int? = null) {
        val id = activeId.value ?: return
        welcoming = false
        stack.clear()
        tab = Tab.Home
        viewModelScope.launch {
            dao.put(Kv("welcome:$id", "done"))
            if (target != null) {
                val all = buildWorkouts(dao.rowsNow(id).map { it.toRow() })
                val today = LocalDate.now()
                dao.put(Kv("goal.week:$id", encodeTargets(withTarget(targetsOrDefault(emptyList(), all, today), weekStart(today), target))))
            }
        }
    }

    /**
     * Once at start-up, before the first page is drawn: the look of an older version becomes the 1.1.0 one (`migrateLook`, idempotent),
     * and an imported font file left in the app's folder is deleted (the font is Inter everywhere now; the old `font` option is ignored).
     */
    private suspend fun migrateLookAndFont() {
        migrateLook(dao.kv().first().associate { it.key to it.value })?.forEach { (key, value) -> dao.put(Kv(key, value)) }
        // The six accents became eight themes (1.1.0 remake), once, after the first migration has written its accent.
        migrateLookTheme(dao.kv().first().associate { it.key to it.value })?.forEach { (key, value) -> dao.put(Kv(key, value)) }
        // Round 34: the eight themes became twelve (Moss to Green, Ocean to Cobalt, Ice to Lagoon, Plum to Iris) and the fourteen backgrounds ten.
        migrateTwelveThemes(dao.kv().first().associate { it.key to it.value })?.forEach { (key, value) -> dao.put(Kv(key, value)) }
        withContext(ioDispatcher) {
            runCatching { platform.removeLeftoverFonts() }
        }
        dao.remove("font")
    }

    init {
        viewModelScope.launch {
            // The changes file is read while the data is, not after it.
            val changes = async(ioDispatcher) { readChanges(platform) }
            runCatching { migrateLookAndFont() }
            val id = activeId.first { it != null } ?: return@launch
            // Only counted here; the lists themselves arrive through the flows, which are then waited for.
            // Bounded: a profile whose every workout is left out of the stats has rows but an empty stats list, which would wait for ever
            // (and the first page was then only shown by MainActivity's ten-second fallback).
            if (dao.rowCount(id) > 0) { allWorkouts.first { it.isNotEmpty() }; withTimeoutOrNull(3_000) { workouts.first { it.isNotEmpty() } } }
            if (dao.measurementCount(id) > 0) withTimeoutOrNull(3_000) { measurements.first { it.isNotEmpty() } }
            withTimeoutOrNull(5_000) { consistency.first { it != null } }
            welcoming = welcomeDue(id)
            decideWhatsNew(changes)
            suggestPerformance()
            loaded = true
            firstPageDone.value = true
            // Only now, so that nothing competes with reading the data for the first page: the library's
            // tables are read in the background (no tap then has to wait for them), and the launcher
            // shortcuts and widgets are brought up to date.
            withContext(Dispatchers.Default) {
                platform.prepareFeedback()
                suggest("")
                otherNames("")
                platform.refreshWidgets()
            }
        }
        storeDrafts()
        storeCorrections()
        // As the first page is shown after ten seconds at the most (MainActivity), the figures waiting for it start then at the latest.
        viewModelScope.launch { delay(10_000); firstPageDone.value = true }
        viewModelScope.launch {
            // The Today widget is drawn in the look's colours; they are kept (kv `widget.colors`) for a redraw with the app gone, and the widgets are redrawn when they change.
            snapshotFlow { loaded }.first { it }
            // With the theme "phone" the day and the night sets are both kept (`widget.colors` is the day one, `widget.colors.dark` the night one), so the launcher can switch them itself when the phone's mode changes.
            fun coloursOf(l: Look): String {
                val accent = if (l.dark) l.accent.first else l.accent.second
                return clean.core.encodeArgb(listOf(l.page, l.raised, l.text, l.muted, accent, accent.readableOn()).map { it.toArgb() })
            }
            kv.map { o ->
                val theme = o[LOOK_THEME_KEY]
                val black = o[LOOK_BLACK_KEY] == "on"
                val accent = o[LOOK_ACCENT_KEY] ?: "Graphite"
                val safe = o["theme.cbsafe"] == "on"
                if (theme == "phone") coloursOf(resolveLook("light", black, accent, false, safe)) to coloursOf(resolveLook("dark", black, accent, true, safe))
                else coloursOf(resolveLook(theme ?: "dark", black, accent, false, safe)) to null
            }.distinctUntilChanged().collect { (colours, night) ->
                if (dao.get("widget.colors") != colours || dao.get("widget.colors.dark") != night) {
                    dao.put(Kv("widget.colors", colours))
                    if (night == null) dao.remove("widget.colors.dark") else dao.put(Kv("widget.colors.dark", night))
                    platform.refreshWidgets()
                }
            }
        }
        viewModelScope.launch {
            // The three options that used to be global belong to the profile that was in use when they were written.
            val id = activeId.first { it != null }
            for (base in listOf("goal.week", "program", "reward.seen")) {
                val old = dao.get(base) ?: continue
                if (dao.get("$base:$id") == null) {
                    dao.put(Kv("$base:$id", old))
                    dao.remove(base)
                }
            }
            adopted.value = true
        }
        viewModelScope.launch {
            // First run with the consistency feature, for each profile: the target is fixed at the user's usual pace, and the badges
            // already earned are marked seen so that none of them is celebrated late.
            snapshotFlow { loaded }.first { it }
            activeId.filterNotNull().collectLatest { id ->
                adopted.first { it }
                val goal = "goal.week:$id"
                suspend fun rows() = buildWorkouts(dao.rowsNow(id).map { it.toRow() })
                val all = rows()
                val today = LocalDate.now()
                val fixed = targetsOrDefault(decodeTargets(dao.get(goal)), all, today)
                if (dao.get(goal) == null && all.isNotEmpty()) dao.put(Kv(goal, encodeTargets(fixed)))
                // The milestone chips earned before the glint existed are not glinted.
                if (dao.get("glint.seen:$id") == null) {
                    val earned = withContext(Dispatchers.Default) { clean.core.consistency(all, fixed, today).awards.filter { it.earned != null }.map { it.id } }
                    dao.put(Kv("glint.seen:$id", earned.joinToString(",")))
                }
                if (dao.get("reward.seen:$id") == null) {
                    val earned = withContext(Dispatchers.Default) { clean.core.consistency(all, fixed, today).awards.filter { it.earned != null }.map { it.id } }
                    dao.put(Kv("reward.seen:$id", earned.joinToString(",")))
                }
                // A brand-new user's first workout fixes the target in the same way.
                if (all.isEmpty()) {
                    dao.rows(id).first { it.isNotEmpty() }
                    if (dao.get(goal) == null) dao.put(Kv(goal, encodeTargets(targetsOrDefault(emptyList(), rows(), LocalDate.now()))))
                }
            }
        }
        viewModelScope.launch {
            // A profile made later (Settings) is empty and is welcomed too; switching to one with data is not.
            snapshotFlow { loaded }.first { it }
            activeId.filterNotNull().collect { welcoming = welcomeDue(it) }
        }
        viewModelScope.launch {
            if (dao.profilesNow().isEmpty()) switchTo(repo.createProfile("Me"))
            else if (dao.get("active") == null) switchTo(dao.profilesNow().first().id)
        }
        viewModelScope.launch {
            // Once the stored options have loaded: back to the time frame of last time.
            val stored = kv.first { it.isNotEmpty() }
            stored["ui.muscleDays"]?.toLongOrNull()?.let { muscleDaysState = it; muscleDaysFlow.value = it }
        }
        viewModelScope.launch {
            // A rest that was running when the app was closed carries on; one that has ended is dropped without a sound.
            val id = activeId.first { it != null } ?: return@launch
            val stored = dao.get("draft:rest:$id")?.split('|')
            val end = stored?.getOrNull(0)?.toLongOrNull()
            if (end != null && end > currentMillis() && restEnd == null) {
                restEnd = end
                restTotal = stored?.getOrNull(1)?.toIntOrNull() ?: ((end - currentMillis()) / 1000).toInt()
                countDown()
            } else if (stored != null) dao.remove("draft:rest:$id")
        }
        viewModelScope.launch {
            activeId.collect { id ->
                val stored = id?.let { dao.get("draft:$it") }?.let { runCatching { Draft.fromJson(it) }.getOrNull() }
                correction = id?.let { dao.get(repo.correctionKey(it)) }?.let { runCatching { Draft.fromJson(it) }.getOrNull() }
                correctionFrom = id?.let { dao.get(repo.correctionFromKey(it)) }?.let { runCatching { Draft.fromJson(it) }.getOrNull() } ?: correction
                // A correction begun before it had a page of its own (2026-11-02) was the workout in progress: it moves to the Correct page.
                if (id != null && stored?.editOf != null) {
                    draft = null
                    dao.remove("draft:$id")
                    if (correction == null) {
                        val moved = stored.asCorrection()
                        correctionFrom = moved
                        putCorrection(moved)
                    }
                } else draft = stored
            }
        }
        // The Iso-Lateral Row history fix, once per profile, after the start-up work.
        viewModelScope.launch { activeId.collect { id -> if (id != null) fixIsoRowHistoryOnce(id) } }
    }

    /** The card whose own tap is being handled right now, set only for the length of that tap: a page opened inside it grows out of that card. */
    private var pressedCard: String? = null
    /** The card each page was opened from, by the page's name; kept after Back, so a page can shrink into its card and a later Back finds it. */
    private val cardOf = HashMap<String, String>()
    /** Runs [tap] (the card's own click) with [key] as the card pressed; a page it opens grows out of that card (see [opensInto]). Never a row or button inside a card. */
    fun fromCard(key: String?, tap: () -> Unit) {
        pressedCard = key
        try { tap() } finally { pressedCard = null }
    }
    /** The page a card was on, by the page it opened (kept like [cardOf]); none when the card was on the tabs page. */
    private val cardPage = HashMap<String, String>()
    fun cardKeyOf(screen: Screen?): String? = screen?.let { cardOf[it.toString()] }
    fun cardPageOf(screen: Screen?): String? = screen?.let { cardPage[it.toString()] }

    /** What the user last saw of each number, ring or mark that moves to a new value, and which calendars have waved, for this session (see Roll.kt). */
    val seen = HashMap<String, Any?>()
    /** Per page just pushed: whether its first rows still settle in (a page that grew out of a card does not). */
    val settles = HashMap<String, SettleState>()

    /** Whether the Workout sheet (the plus button's "Start a workout") is open over the current page. */
    var workoutSheet by mutableStateOf(false)

    /** Raises the Workout sheet; with a workout running there is nothing to start, so it goes back to that workout. */
    fun openWorkoutSheet() {
        if (draft != null) resumeWorkout() else workoutSheet = true
    }

    /** [grows]: whether a page opened by a card's tap grows out of that card; the workout does only when it is resumed ([resumeWorkout]), never when it starts. */
    fun open(screen: Screen, grows: Boolean = screen != Screen.Active) {
        workoutSheet = false
        val key = pressedCard?.takeIf { grows }
        if (key != null) cardOf[screen.toString()] = key else cardOf.remove(screen.toString())
        val under = stack.lastOrNull()
        if (key != null && under != null) cardPage[screen.toString()] = under.toString() else cardPage.remove(screen.toString())
        settles[screen.toString()] = SettleState(armed = key == null)
        // An exercise's figures start being worked out now, so they are mostly there by the page's first frame.
        if (screen is Screen.ExerciseDetail) viewModelScope.launch {
            exerciseStats(screen.name, workouts.value, loadTypeOf(screen.name), bodyweights.value, oneRm.value)
        }
        if (screen is Screen.Calendar) viewModelScope.launch { calendarData(options.value["ui.calendarMode"] ?: "Time", workouts.value) }
        stack.add(screen)
    }
    fun back() { stack.removeLastOrNull() }

    // --- Profiles ---

    fun switchTo(id: Long) = viewModelScope.launch { dao.put(Kv("active", id.toString())) }
    fun createProfile(name: String) = viewModelScope.launch { switchTo(repo.createProfile(name)) }
    fun update(profile: Profile) = viewModelScope.launch { dao.update(profile) }
    fun deleteProfile(id: Long) = viewModelScope.launch {
        val other = profiles.value.firstOrNull { it.id != id } ?: return@launch
        if (activeId.value == id) dao.put(Kv("active", other.id.toString()))
        repo.deleteProfile(id)
        withContext(ioDispatcher) { platform.deleteProfilePictures(id) }
    }

    // --- Exercises ---

    fun unitOf(exercise: String): String = settings.value[exercise]?.unit ?: profile.value?.unit ?: "kg"

    /**
     * How an exercise is logged and what it trains. What the user has set wins; otherwise the
     * library's definition, or failing that a guess from the words in the name, applies straight
     * away so the body figure and the records work without any setting up.
     */
    fun loadTypeOf(exercise: String): LoadType = memo().let { m ->
        m.loadTypes.getOrPut(exercise) { m.settings[exercise]?.let { runCatching { LoadType.valueOf(it.loadType) }.getOrNull() } ?: suggest(exercise)?.loadType ?: LoadType.NORMAL }
    }

    /** The groups (and any heads) the user has stored for an exercise, else the automatic groups; the rules' heads are not in it, so it is what gets stored. */
    fun storedMusclesOf(exercise: String): MuscleUse = storedMusclesIn(settings.value, exercise)

    private fun storedMusclesIn(own: Map<String, ExerciseSetting>, exercise: String): MuscleUse =
        own[exercise]?.muscles?.takeIf { it.isNotEmpty() }?.let(MuscleUse::decode) ?: suggest(exercise)?.muscles ?: MuscleUse()

    /** [storedMusclesOf] with the heads the exercise names: the stored ones, else the rules' (`headsOf`). */
    fun musclesOf(exercise: String): MuscleUse = memo().let { m ->
        m.muscles.getOrPut(exercise) { storedMusclesIn(m.settings, exercise).let { it.copy(heads = headsOf(exercise, it)) } }
    }


    /**
     * The real load of a logged weight on [date]: bodyweight added or assistance taken off where
     * the exercise says so, using the weigh-in closest to that date. Null when there is none close enough.
     */
    fun loadOf(exercise: String, weight: Double, date: String): Double? {
        val type = loadTypeOf(exercise)
        // Only a lift that counts bodyweight needs the weigh-in looked up.
        return if (type != LoadType.BODYWEIGHT && type != LoadType.ASSISTED) weight else realLoad(type, weight, bodyweights.value.at(date))
    }

    /** What is stored for an exercise the first time anything about it is changed: its current, automatic values. */
    private fun defaults(profileId: Long, exercise: String) =
        ExerciseSetting(profileId, exercise, loadType = loadTypeOf(exercise).name, muscles = storedMusclesOf(exercise).encode())

    fun putSetting(exercise: String, change: (ExerciseSetting) -> ExerciseSetting) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        dao.put(change(settings.value[exercise] ?: defaults(id, exercise)))
    }

    /** Forgets everything stored for an exercise. One the user made and never logged is gone; any other goes back to its automatic values. */
    fun removeSetting(exercise: String, what: String = "Exercise reset") = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val before = settings.value[exercise]
        dao.deleteSetting(id, exercise)
        if (before != null) say(what) { viewModelScope.launch { dao.put(before) } }
    }

    // --- Variations ---

    /** Every exercise name that has been logged. */
    private val logged: StateFlow<Set<String>> = workouts.map { all -> all.flatMapTo(HashSet()) { w -> w.blocks.map { it.exercise } } }.state(emptySet())

    fun hasHistory(exercise: String): Boolean = exercise in logged.value

    /**
     * The overall exercise [exercise] is a variation of: what the user stored for it, else the
     * library's grouping, else itself. [NO_GROUP] is stored when the user takes it out of a group.
     */
    fun overallOf(exercise: String): String = overallName(exercise) { settings.value[it]?.overall }

    /** Every variation of [exercise]'s overall exercise, itself included; empty when it has no sibling. */
    fun variationsOf(exercise: String): List<String> {
        val overall = overallOf(exercise)
        val members = (settings.value.keys + logged.value + libraryVariations(overall) + exercise).filter { overallOf(it) == overall }.distinct().sorted()
        return if (members.size > 1) members else emptyList()
    }

    /** Puts [variation] in [of]'s group. A name never used before starts out logged like [of], with the same muscles. */
    fun addVariation(of: String, variation: String) {
        if (variation == of) return
        val overall = overallOf(of)
        val unit = settings.value[of]?.unit
        val loadType = loadTypeOf(of).name
        val muscles = storedMusclesOf(of).encode()
        val fresh = settings.value[variation] == null && variation !in logged.value
        putSetting(of) { it.copy(overall = overall) }
        putSetting(variation) { if (fresh) it.copy(overall = overall, loadType = loadType, unit = unit, muscles = muscles) else it.copy(overall = overall) }
    }

    /**
     * Every session of [exercise] in [workouts], oldest first. The whole history is sorted into
     * exercises once and kept until the list of workouts changes, so a page asking for several
     * exercises does not go through the history once for each.
     */
    fun sessionsIn(workouts: List<Workout>, exercise: String): List<Session> = synchronized(indexLock) {
        if (indexed !== workouts) {
            index = sessionsByExercise(workouts)
            indexed = workouts
        }
        index[exercise].orEmpty()
    }

    /** What an exercise's page works out from its sessions: the records per side and the chart figures. See [exerciseStats]. */
    class ExerciseStats(
        val sessions: List<Session>, val sided: SideRecords, val hasSides: Boolean,
        val latestGap: Pair<String, clean.core.ImbalanceNote?>?, val sideProgress: List<SidedProgress>,
    ) { val progress: List<Progress> = sideProgress.map { it.comparable } }

    /** The inputs one [ExerciseStats] was made from; a result is the current one while all four are the same objects. */
    private class StatsInputs(val workouts: List<Workout>, val loadType: LoadType, val bodyweights: Bodyweights, val rule: OneRm) {
        fun same(o: StatsInputs) = workouts === o.workouts && loadType == o.loadType && bodyweights === o.bodyweights && rule == o.rule
    }
    private val statsCache = ConcurrentCache<String, Pair<StatsInputs, ExerciseStats>>()

    /** The last [ExerciseStats] of [exercise] if it was made from these same inputs, else null: reopening a page shows it at once. */
    fun cachedExerciseStats(exercise: String, workouts: List<Workout>, loadType: LoadType, bodyweights: Bodyweights, rule: OneRm): ExerciseStats? {
        val hit = statsCache[exercise] ?: return null
        return hit.second.takeIf { hit.first.same(StatsInputs(workouts, loadType, bodyweights, rule)) }
    }

    /** [ExerciseStats] for [exercise], worked out off the main thread (or taken from the cache) and kept for the next time. */
    suspend fun exerciseStats(exercise: String, workouts: List<Workout>, loadType: LoadType, bodyweights: Bodyweights, rule: OneRm): ExerciseStats {
        cachedExerciseStats(exercise, workouts, loadType, bodyweights, rule)?.let { return it }
        return withContext(Dispatchers.Default) {
            val sessions = sessionsIn(workouts, exercise)
            val sided = recordsBySide(sessions, rule, loadType, bodyweights::at)
            val hasSides = sessions.any { it.paired }
            val gap = if (hasSides) latestImbalance(sessions, rule, loadType, bodyweights::at) else null
            ExerciseStats(sessions, sided, hasSides, gap, progressBySide(sessions, rule, loadType, bodyweights::at))
        }.also { statsCache[exercise] = StatsInputs(workouts, loadType, bodyweights, rule) to it }
    }

    /** The contexts of [setContextFor], by exercise (typed in Active.kt, where they are made). */
    val setContextCache = ConcurrentCache<String, Any>()

    init {
        // The cards of a running workout read their exercises' history when the page opens; it is worked out here, off the main thread, as soon as the
        // workout has the exercise, so the cards find it ready (a card added later is the same).
        viewModelScope.launch {
            snapshotFlow { draft?.blocks?.map { b -> b.exercise to b.paired } }.collect { blocks ->
                if (blocks.isNullOrEmpty()) return@collect
                withContext(Dispatchers.Default) {
                    val all = workouts.value
                    val rule = oneRm.value
                    val weights = bodyweights.value
                    val schemes = progressions.value
                    for ((name, paired) in blocks) {
                        exerciseStats(name, all, loadTypeOf(name), weights, rule)
                        setContextFor(name, all, rule, weights, schemes[name], paired)
                    }
                }
            }
        }
    }

    /** What the Calendar page shades and counts by, for one mode: see [calendarData]. */
    class CalendarData(
        val seconds: Map<LocalDate, Double>, val byDay: Map<String, List<Workout>>, val low: Double, val top: Double, val perMonth: Map<String, Int>,
    )
    private val calendarCache = ConcurrentCache<String, Pair<List<Workout>, CalendarData>>()

    /** The last [CalendarData] of [mode] if it was made from this same list of workouts, else null. */
    fun cachedCalendar(mode: String, workouts: List<Workout>): CalendarData? = calendarCache[mode]?.takeIf { it.first === workouts }?.second

    /** [CalendarData] for [mode], worked out off the main thread (or taken from the cache) and kept for the next time. */
    suspend fun calendarData(mode: String, workouts: List<Workout>): CalendarData {
        cachedCalendar(mode, workouts)?.let { return it }
        return withContext(Dispatchers.Default) {
            val seconds: Map<LocalDate, Double> = when (mode) {
                "Volume" -> volumeByDay(workouts, ::loadOf)
                "Sets" -> workouts.groupBy { LocalDate.parse(it.day) }.mapValues { (_, w) -> w.sumOf { x -> x.workingCount } }
                else -> secondsByDay(workouts).mapValues { it.value.toDouble() }
            }
            val sorted = seconds.values.sorted()
            CalendarData(
                seconds, workouts.groupBy { it.day },
                if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.05).toInt()],
                if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.95).toInt()],
                workouts.groupingBy { it.day.take(7) }.eachCount(),
            )
        }.also { calendarCache[mode] = workouts to it }
    }

    fun leaveGroup(exercise: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val before = settings.value[exercise]
        dao.put((before ?: defaults(id, exercise)).copy(overall = NO_GROUP))
        say("$exercise left its group") { viewModelScope.launch { if (before != null) dao.put(before) else dao.deleteSetting(id, exercise) } }
    }

    /**
     * The built-in variations made for [exercise] itself by the library's rules (a grip, one side at a
     * time, a stance), without the ones the user has removed. A whole group can run to a hundred
     * names, so these few are what is offered first.
     */
    fun suggestedVariations(exercise: String): List<String> {
        val overall = overallOf(exercise)
        return generatedVariations(variationBase(exercise) ?: exercise).filter { it != exercise && overallOf(it) == overall }
    }

    /**
     * Carries a card on under another variation: the sets not ticked yet become that variation and
     * the card stays one card. With nothing ticked yet the card itself changes exercise. The CSV
     * gets one block per run of the same variation (see [runsOf]).
     */
    fun switchVariation(index: Int, variation: String, slot: DraftSlot = liveSlot) {
        val d = slot.draft ?: return
        val block = d.blocks.getOrNull(index) ?: return
        val blocks = d.blocks.toMutableList()
        blocks[index] = if (block.items.none { it is DraftSet && it.done }) {
            block.copy(exercise = variation, items = block.items.map { if (it is DraftSet) it.copy(exercise = null) else it })
        } else {
            val target = variation.takeIf { it != block.exercise }
            val changed = block.items.map { if (it is DraftSet && !it.done) it.copy(exercise = target) else it }
            // Everything ticked already: the next set is the one that switches.
            block.copy(items = if (changed.any { it is DraftSet && !it.done }) changed else changed + List(if (block.paired) 2 else 1) { DraftSet(exercise = target) })
        }
        slot.put(d.copy(blocks = blocks))
    }

    /** Links a card with the one below it as a superset, or takes it out of the one it is in. */
    fun toggleSuperset(index: Int, slot: DraftSlot = liveSlot) {
        val d = slot.draft ?: return
        val block = d.blocks.getOrNull(index) ?: return
        val blocks = d.blocks.toMutableList()
        if (block.superset != null) blocks[index] = block.copy(superset = null)
        else {
            val below = d.blocks.getOrNull(index + 1) ?: return
            val tag = below.superset ?: "s${currentMillis()}"
            blocks[index] = block.copy(superset = tag)
            blocks[index + 1] = below.copy(superset = tag)
        }
        slot.put(d.copy(blocks = blocks))
    }

    fun moveExercise(index: Int, by: Int, slot: DraftSlot = liveSlot) {
        val d = slot.draft ?: return
        val to = index + by
        if (index !in d.blocks.indices || to !in d.blocks.indices) return
        slot.put(d.copy(blocks = d.blocks.toMutableList().apply { add(to, removeAt(index)) }))
    }

    /**
     * A card as the blocks it is saved as: one per run of sets done as the same variation, a note going with the sets after it.
     * Left and right: the right half of a round stays with its left half, whatever variation or note stands between them
     * (a note then waits for the next round), so each run starts at a left half.
     */
    private fun runsOf(block: DraftBlock): List<DraftBlock> {
        val runs = mutableListOf<DraftBlock>()
        var notes = listOf<DraftItem>()
        var sets = 0
        for (item in block.items) {
            if (item !is DraftSet) {
                notes = notes + item
                continue
            }
            val rightHalf = block.paired && sets++ % 2 == 1 && runs.isNotEmpty()
            val name = item.exercise ?: block.exercise
            val last = runs.lastOrNull()
            if (rightHalf) runs[runs.lastIndex] = last!!.copy(items = last.items + item.copy(exercise = null))
            else {
                if (last != null && last.exercise == name && notes.isEmpty()) runs[runs.lastIndex] = last.copy(items = last.items + item.copy(exercise = null))
                else runs += DraftBlock(name, notes + item.copy(exercise = null), block.superset, paired = block.paired)
                notes = emptyList()
            }
        }
        // Notes written after the last set stay with it.
        if (notes.isNotEmpty() && runs.isNotEmpty()) runs[runs.lastIndex] = runs.last().let { it.copy(items = it.items + notes) }
        return runs
    }

    // --- Rest timer ---

    /** When the rest in progress ends, in clock milliseconds; null when none is running. */
    var restEnd by mutableStateOf<Long?>(null)
        private set
    /** How many rests have run out by themselves (not skipped) since the app started; the next set's row glows once on each (Active.kt). */
    var restRanOut by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    /** The whole length of the rest in progress, in seconds, for showing how much of it is left. */
    var restTotal by mutableStateOf(0)
        private set
    private var restJob: Job? = null
    val restDefault: StateFlow<Int> = kv.map { it["rest.seconds"]?.toIntOrNull() ?: 120 }.state(120)
    /** Where the "workout in progress" bar shows: `Everywhere`, `Main tabs` or `Off`. */
    /** Whether the Current split switch is on (kv `split.on`, absent = off): Up next then rotates through the Current split folder. */
    val splitOn: StateFlow<Boolean> = kv.map { it["split.on"] == "on" }.distinctUntilChanged().state(false)

    /**
     * The rest after a set of [exercise]: the exercise's own length if it has one (off means off for
     * every set), else the length set for that [type] of set, else the one for all; a warm-up with no length
     * of its own rests half as long as the others. After a hard set
     * (RPE 9 or more, or to failure) the extra time from Settings is added, to any rest that is not off.
     */
    fun restFor(exercise: String, type: SetType = SetType.NORMAL, rpe: String = ""): Int {
        val own = settings.value[exercise]?.rest?.takeIf { it >= 0 }
        if (own == 0) return 0
        val byType = when (type) {
            SetType.WARMUP -> "rest.warmup"
            SetType.FAILURE -> "rest.failure"
            SetType.DROP -> "rest.drop"
            SetType.NORMAL -> null
        }?.let { options.value[it]?.toIntOrNull() }
        val usual = own ?: restDefault.value
        val base = byType ?: if (type == SetType.WARMUP) usual / 2 else usual
        val hard = type == SetType.FAILURE || (parseNumber(rpe) ?: 0.0) >= 9.0
        return if (hard && base > 0) base + (options.value["rest.hard"]?.toIntOrNull() ?: 0) else base
    }

    /**
     * The rest after the first of a left and right pair is ticked, before the other side (kv `rest.sides`, seconds,
     * 60 unless set; 0 is none). An exercise whose own rest is off has none, as for any set.
     */
    fun restBetweenSides(exercise: String): Int {
        if (settings.value[exercise]?.rest == 0) return 0
        return options.value["rest.sides"]?.toIntOrNull() ?: 60
    }

    /** Whether the activity is on screen; set from its onStart and onStop. */
    private var started = true
    private fun restKey() = activeId.value?.let { "draft:rest:$it" }
    /** The in-app buzzes and bell sound only while the app is in front, or when no background alert is wanted, so nothing sounds twice. */
    private fun alertsHere() = started || options.value["rest.background"] != "on"

    /** How a rest ends (kv `feedback.restEnd`; before it was chosen, what the two older switches `rest.sound` and `rest.alert` said). */
    private fun restEndFeedback() = clean.core.restEndFeedback(options.value["feedback.restEnd"], options.value["rest.sound"], options.value["rest.alert"])

    fun startRest(seconds: Int, changing: Boolean = false) {
        restJob?.cancel()
        if (seconds <= 0) {
            restEnd = null
        } else {
            restEnd = currentMillis() + seconds * 1000L
            // Adding time to a rest makes it longer in all; taking time off leaves the bar where it was.
            if (!changing || seconds > restTotal) restTotal = seconds
            countDown()
        }
        storeRest()
        syncAlerts()
    }

    private fun countDown() {
        val end = restEnd ?: return
        restJob = viewModelScope.launch {
            // The last five seconds are counted down, a pulse on each and a longer one on the last, and a bell as the rest ends.
            val seconds = ((end - currentMillis() + 999) / 1000).toInt().coerceAtLeast(0)
            val lead = minOf(5, seconds)
            delay((seconds - lead) * 1000L)
            for (left in lead downTo 1) {
                if (alertsHere() && restEndFeedback().vibrate) platform.pulse(if (left == 1) 600 else 150)
                // E2: three soft ticks, one a second, for the last 3 s (Buzz already pulses through them; off with Haptic ticks).
                else if (left <= 3 && alertsHere()) buzz(Haptic.Tick)
                delay(1000)
            }
            if (alertsHere() && restEndFeedback().sound) platform.bell(RestTone.of(options.value["rest.tone"]))
            // Rest over: a confirm unless the rest is set to Buzz, which pulses by itself.
            if (alertsHere() && !restEndFeedback().vibrate) buzz(Haptic.Confirm)
            restRanOut++
            // Any alarm set for a rest that ends while the app is behind is left to ring; it is cancelled when the app returns.
            restEnd = null
            storeRest()
            syncWorkoutNotice()
            // Back to the workout, wherever in the app the rest was spent.
            if (options.value["rest.return"] != "off" && draft != null && stack.lastOrNull() != Screen.Active) {
                stack.removeAll { it == Screen.Active }
                open(Screen.Active)
            }
        }
    }

    /** The running rest is kept (under a `draft:` key, which the options flow leaves out) so a restarted app carries on with it. */
    private fun storeRest() {
        val key = restKey() ?: return
        val end = restEnd
        val total = restTotal
        viewModelScope.launch { if (end == null) dao.remove(key) else dao.put(Kv(key, "$end|$total")) }
    }

    /** Whether the workout notification is shown now: behind the app, with a workout of today in progress, unless Settings says no. */
    private fun noticeShown(d: Draft?) = !started && d != null && d.pastDate == null && workoutNotifOn(options.value["workout.notif"])

    /** Whether a rest running behind the app is shown inside the workout notification (its countdown and buttons) and not as a notification of its own. */
    private fun restInNotice() = noticeShown(draft) && restEnd != null && options.value["rest.background"] == "on"

    /** Behind the app a rest is carried by an alarm (if Settings asks for one); in front, or with no rest, there is none. */
    private fun syncAlerts() {
        val end = restEnd
        if (!started && end != null && options.value["rest.background"] == "on") platform.scheduleRestAlert(end, restKey(), restTotal, draft?.let { nextSetOf(it) }?.words(), restInNotice()) else platform.cancelRestAlert()
        syncWorkoutNotice()
    }

    /** Behind the app the workout in progress is a notification (unless Settings says no); in front, or with none or one entered after the fact, there is none. A rest carried by an alarm is shown in it, so there is one notification. */
    private fun syncWorkoutNotice() {
        val d = draft
        if (noticeShown(d) && d != null) {
            val next = nextSetOf(d)?.words()
            val end = restEnd
            if (restInNotice() && end != null) platform.postWorkoutNotice(d.name, d.startMillis, workoutNotifText(next, null), setDone = next != null, rest = NoticeRest(end, restKey(), restTotal, next))
            else platform.postWorkoutNotice(d.name, d.startMillis, workoutNotifText(next, end?.let { localTimeOf(it) }), setDone = next != null)
        } else platform.cancelWorkoutNotice()
    }

    /** Today's steps from Health Connect for Home's quiet line; null when the switch is off, the reading is not allowed or there is none. Refreshed whenever the app comes to the front. */
    var steps by mutableStateOf<Long?>(null)
        private set

    init {
        // The workout-day reminder's alarm follows the days and time chosen; set again at each start, in case it was lost.
        viewModelScope.launch {
            options.map { it["remind.days"] to it["remind.time"] }.distinctUntilChanged().collect { (days, time) -> platform.syncWorkoutReminder(days, time) }
        }
        // At midnight the day changes under a screen that is open.
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 500)
                refreshToday()
            }
        }
        // "+15 s" and "Skip" on the rest's notification, while the app is alive behind it.
        platform.onRestNotificationAction = { add -> if (add) changeRest(REST_NOTIFICATION_MORE) else startRest(0) }
        // Home's steps follow the Settings switch.
        viewModelScope.launch { options.map { it["health.steps"] }.distinctUntilChanged().collect { refreshSteps() } }
        startHealthSync()
        // "Set done" on the workout's notification, while the app is alive behind it.
        platform.onSetDoneNotification = { tickNextSet() }
    }

    override fun onCleared() {
        platform.onRestNotificationAction = null
        platform.onSetDoneNotification = null
        super.onCleared()
    }

    fun refreshSteps() {
        viewModelScope.launch {
            steps = if (options.value["health.steps"] == "on") platform.healthStepsToday() else null
        }
    }

    fun onStarted() {
        started = true
        refreshToday()
        refreshSteps()
        syncAlerts()
    }

    fun onStopped() {
        started = false
        syncAlerts()
    }

    // ---- Lock photos (Round 31)

    /**
     * Whether the photos have been opened with the phone's lock since the app last left the screen. **The unlock lasts until the app goes to the background**
     * ([lockPhotos], from the activity's stop; not for a turn of the phone), not for a set time: the person who has just opened their photos is not asked again
     * while they look at them, and anyone who picks the phone up later is.
     */
    var photosUnlocked by mutableStateOf(false)
        private set

    /** Whether "Lock photos" is on (kv `photos.lock`), read where nothing is composed. */
    val photoLockOn: Boolean get() = kv.value["photos.lock"] == "on"

    /** The photos are shut: the lock is on and the phone has not been asked since the app came to the front. */
    val photosShut: Boolean get() = photoLockOn && !photosUnlocked

    fun lockPhotos() { photosUnlocked = false }

    /** Asks the phone's lock (unless the photos are already open or the lock is off) and runs [then] once through. */
    fun unlockPhotos(then: () -> Unit = {}) {
        if (!photosShut) { then(); return }
        platform.authenticate("Show your photos") { ok -> if (ok) { photosUnlocked = true; then() } }
    }

    /**
     * The switch in Settings. Turning it on needs a lock on the phone (said so and left off when there is none) and shuts the photos at once; turning it off
     * asks the phone's lock first.
     */
    fun setPhotoLock(on: Boolean) {
        if (on) {
            if (!platform.canAuthenticate) { message = "Set a screen lock on this phone first, a PIN, pattern, password or fingerprint."; return }
            photosUnlocked = false
            setOption("photos.lock", "on")
        } else platform.authenticate("Turn off Lock photos") { ok -> if (ok) { setOption("photos.lock", "off"); photosUnlocked = true } }
    }

    /** Lengthens or shortens the rest in progress; shortened to nothing it just stops. */
    fun changeRest(bySeconds: Int) {
        val end = restEnd ?: return
        startRest(((end - currentMillis()) / 1000).toInt() + bySeconds, changing = true)
    }

    // --- Stopwatch for timed sets ---

    /** When the stopwatch of a timed set was started, in clock milliseconds; null when none runs. Not stored: it is gone with the app. */
    var stopwatchStart by mutableStateOf<Long?>(null)
        private set
    private var stopwatchBell: Job? = null

    /** Starts the clock if it is not running. [target] (seconds) is what it counts down to, with a bell on reaching it; null just counts up. */
    fun startStopwatch(target: Int? = null) {
        val begun = stopwatchStart ?: currentMillis().also { stopwatchStart = it }
        stopwatchBell?.cancel()
        val left = if (target == null) 0L else begun + target * 1000L - currentMillis()
        stopwatchBell = if (left <= 0) null else viewModelScope.launch {
            delay(left)
            platform.bell(RestTone.of(options.value["rest.tone"]))
        }
    }

    /** Stops the clock and gives the whole seconds it counted. */
    fun stopStopwatch(): Int {
        val begun = stopwatchStart ?: return 0
        stopwatchBell?.cancel()
        stopwatchStart = null
        return ((currentMillis() - begun) / 1000).toInt()
    }

    // --- Notes that are really variations ---

    /** Moves the blocks of [exercise] opening with [note] to [variation] and groups the two. */
    fun moveNoted(exercise: String, note: String, variation: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val moved = repo.moveNoted(id, exercise, note, variation)
        addVariation(exercise, variation)
        message = "$moved ${if (moved == 1) "block" else "blocks"} moved to $variation"
    }

    // --- Renaming and merging an exercise ---

    /** Whether [name] is an exercise this profile already has: logged, or with settings of its own. A built-in one never used does not count. */
    fun exerciseExists(name: String): Boolean = name in logged.value || settings.value.containsKey(name)

    /**
     * Gives every stored trace of [from] the name [to], when the user has asked (`Repo.renameExercise` lists them), and says so. If
     * [to] is an exercise already, this is a merge. The pages open on [from] move to [to]. It can be taken back from Settings, Data.
     */
    fun renameExercise(from: String, to: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (from == to || to.isEmpty()) return@launch
        val merged = exerciseExists(to)
        // A name nothing is known about gets the automatic values the old name had, so the exercise is logged and trains the same as before.
        val automatic = if (merged || settings.value[from] != null || libraryExercise(to) != null) null else {
            ExerciseSetting(id, from, loadType = loadTypeOf(from).name, muscles = storedMusclesOf(from).encode(), overall = overallOf(from).takeIf { it != from }.orEmpty())
        }
        val current = draft
        val inDraft = current != null && current.renamed(from, to) != current
        repo.renameExercise(id, from, to, merged, automatic, inDraft, strongDate(currentMillis()))
        if (inDraft) putDraft(current!!.renamed(from, to))
        correction?.let { putCorrection(it.renamed(from, to)) }
        for (i in stack.indices) stack[i] = stack[i].renamedExercise(from, to)
        platform.refreshWidgets()
        message = if (merged) "“$from” merged into “$to”" else "“$from” renamed to “$to”"
    }

    /** The renames that can still be undone, newest first. */
    suspend fun renameLog(): List<RenameEntry> = activeId.value?.let { repo.renameLog(it) }.orEmpty()

    /** Takes a rename back (see `Repo.undoRename`); rows logged under the new name since stay there. Returns how many rows got the old name back. */
    suspend fun undoRename(entry: RenameEntry): Int {
        val id = activeId.value ?: return 0
        val restored = repo.undoRename(id, entry)
        if (entry.draft) draft?.let { putDraft(it.renamed(entry.to, entry.from)) }
        platform.refreshWidgets()
        message = "“${entry.from}” is back" + if (restored == 0) "" else " on ${plural(restored, "row")}"
        return restored
    }

    // --- Iso-Lateral Row history fix (core/IsoRowFix.kt) ---

    /**
     * The one-time correction of the Iso-Lateral Row history (the user's agreed rules, `planIsoRowFix`), applied once per profile at
     * start-up, with no page of its own (2026-11-02, the user's words: "it should be fixed outside the app"). Guarded by kv
     * `isofix.done:<profile>`, which is written whether or not there was anything to fix, so it never looks at the history again;
     * the plan leaves blocks that are already right alone. The rows it replaced are kept under `draft:isofix:<profile>`
     * (`Repo.undoIsoRowFix` puts them back).
     */
    private suspend fun fixIsoRowHistoryOnce(id: Long) {
        if (dao.get("isofix.done:$id") != null) return
        val rows = dao.rowsNow(id).map { it.toRow() }
        val plan = withContext(Dispatchers.Default) { planIsoRowFix(rowsOfWorkouts(buildWorkouts(rows))) }
        if (plan.isEmpty) {
            dao.put(Kv("isofix.done:$id", "1"))
            return
        }
        repo.applyIsoRowFix(id, plan)
        platform.log("Iso-Lateral Row history fixed once for profile $id: ${plan.workouts.size} workouts")
        platform.refreshWidgets()
    }

    // --- Logging ---

    fun putDraft(new: Draft?) {
        // A superset is two or more cards side by side: a tag left without a neighbour is dropped.
        val value = new?.let { d ->
            d.copy(blocks = d.blocks.mapIndexed { i, b ->
                if (b.superset != null && d.blocks.getOrNull(i - 1)?.superset != b.superset && d.blocks.getOrNull(i + 1)?.superset != b.superset) b.copy(superset = null) else b
            })
        }
        draft = value
        val id = activeId.value ?: return
        draftWrites.trySend(id to value)
    }

    /** Whether the next set after item [at] in the same card is a drop set. */
    private fun dropFollows(items: List<DraftItem>, at: Int): Boolean = items.drop(at + 1).firstOrNull { it is DraftSet }.let { it is DraftSet && it.type == SetType.DROP }

    /** A card changed (or was removed, when [changed] is null): saved, and a set just ticked starts the rest. Both the workout screen and the notification's "Set done" come through here. */
    fun blockChanged(index: Int, block: DraftBlock, changed: DraftBlock?) {
        val current = draft ?: return
        val blocks = current.blocks.toMutableList()
        val ticked = changed != null && changed.items.count { it is DraftSet && it.done } > block.items.count { it is DraftSet && it.done }
        if (changed == null) blocks.removeAt(index) else blocks[index] = changed
        var after = current.copy(blocks = blocks, lastDoneMillis = if (ticked) currentMillis() else current.lastDoneMillis)
        // A set just ticked starts the rest, unless the turn now goes to the other exercise of a superset.
        val partner = nextBlock(after)?.takeIf { it != index && block.superset != null && after.blocks.getOrNull(it)?.superset == block.superset }
        if (ticked && changed != null && partner == null) {
            // The rest depends on the kind of set just ticked; its length is kept with the set, for the export.
            val at = changed.items.indices.lastOrNull { i -> (changed.items[i] as? DraftSet)?.done == true && (block.items.getOrNull(i) as? DraftSet)?.done != true }
            val justDone = at?.let { changed.items[it] } as? DraftSet
            // Left and right: the first side of a pair rests the time between sides, the second the usual rest.
            val pair = if (at != null && changed.paired) pairHolding(changed.items.map { it is DraftSet }, at) else null
            val otherSide = pair?.let { (left, right) -> if (left == at) right else left }
            // Round 28: a drop set follows straight away, so no rest comes before it (rest.drop is the rest after a drop set, which still applies to the last of them).
            val seconds = if (pair == null && at != null && dropFollows(changed.items, at)) 0
            else if (pair != null && startsSidesRest(otherSide != null, (otherSide?.let { changed.items[it] } as? DraftSet)?.done == true)) restBetweenSides(block.exercise)
            else restFor(block.exercise, justDone?.type ?: SetType.NORMAL, justDone?.rpe.orEmpty())
            startRest(seconds)
            if (at != null && seconds > 0) after = after.copy(blocks = after.blocks.toMutableList().also { list ->
                list[index] = changed.copy(items = changed.items.toMutableList().also { it[at] = (it[at] as DraftSet).copy(rest = seconds) })
            })
        }
        // A set or an exercise taken away can be put back for a few seconds.
        if (changed == null) say("Exercise removed") { putDraft(current) }
        else if (changed.items.count { it is DraftSet } < block.items.count { it is DraftSet }) say("Set removed") { putDraft(current) }
        putDraft(after)
    }

    /**
     * "Set done" on the workout's notification: ticks the next set of the card whose turn it is, as tapping its check would (an empty box takes
     * the grey hint it would show: the template's planned value, else the same set of the last session, else the set above), through
     * [blockChanged] so the rest starts, then the notice is posted again with the following set. Nothing happens when the set cannot be
     * ticked (no reps to take) or no workout runs. The progression scheme's aim is not looked at here.
     */
    fun tickNextSet() {
        val d = draft ?: return
        val at = nextBlock(d) ?: return
        val block = d.blocks[at]
        val i = block.items.indexOfFirst { it is DraftSet && !it.done }
        val s = block.items.getOrNull(i) as? DraftSet ?: return
        val name = s.exercise ?: block.exercise
        val load = loadTypeOf(name)
        val unit = unitOf(name)
        val nth = block.items.take(i).count { it is DraftSet }
        val planned = if (load != LoadType.CARDIO) block.targets.getOrNull(nth / if (block.paired) 2 else 1) else null
        val before = workouts.value.lastOrNull { w -> w.blocks.any { it.exercise == name } }?.blocks?.filter { it.exercise == name }?.flatMap { it.sets }?.getOrNull(nth)
        val above = block.items.take(i).lastOrNull { it is DraftSet } as? DraftSet
        val lastWeight = if (load == LoadType.CARDIO) before?.distance?.let { shortNumber(it / 1000) } else before?.weight?.let { entryWeight(it, unit) }
        val lastReps = if (load == LoadType.CARDIO) before?.seconds?.let { shortNumber(it / 60) } else if (load == LoadType.TIMED) before?.seconds?.let { shortNumber(it) } else before?.reps?.toString()
        val hintWeight = planned?.weightKg?.let { entryWeight(it, unit) } ?: lastWeight ?: above?.weight.orEmpty()
        val hintReps = planned?.reps?.toString() ?: lastReps ?: above?.reps.orEmpty()
        val taken = clean.core.acceptHints(s.weight, s.reps, hintWeight, hintReps, load) ?: return
        val items = block.items.toMutableList()
        items[i] = s.copy(weight = taken.weight, reps = taken.reps, done = true, ticked = currentMillis())
        blockChanged(at, block, block.copy(items = items))
        // The rest started above before the draft held the tick; post the notice again with the set that follows.
        syncAlerts()
    }

    /** Changes the correction; it is written away (kv `draft:correct:<profile>`) so a restart keeps it. */
    fun putCorrection(new: Draft?) {
        val value = new?.let { d ->
            d.copy(blocks = d.blocks.mapIndexed { i, b ->
                if (b.superset != null && d.blocks.getOrNull(i - 1)?.superset != b.superset && d.blocks.getOrNull(i + 1)?.superset != b.superset) b.copy(superset = null) else b
            })
        }
        correction = value
        val id = activeId.value ?: return
        correctionWrites.trySend(id to value)
    }

    private fun storeCorrections() = viewModelScope.launch(Dispatchers.Default) {
        for ((id, value) in correctionWrites) {
            if (value == null) {
                dao.remove(repo.correctionKey(id))
                dao.remove(repo.correctionFromKey(id))
            } else if (value === correction) {
                dao.put(Kv(repo.correctionKey(id), value.toJson()))
                correctionFrom?.let { dao.put(Kv(repo.correctionFromKey(id), it.toJson())) }
            }
        }
    }

    /** Whether the correction differs from the workout as it was opened. */
    val correctionChanged: Boolean get() = correction != null && correction != correctionFrom

    /** Drops the correction; the workout stays as it was stored. */
    fun discardCorrection() {
        correctionFrom = null
        putCorrection(null)
    }

    /**
     * Saves the correction in place of the workout it was opened from: every set that holds its values counts as done, the start and
     * length are the edited ones. Returns false (and saves nothing) when no set is left or the end is not after the start.
     */
    fun saveCorrection(): Boolean {
        val d = correction ?: return false
        val id = activeId.value ?: return false
        val from = d.editOf ?: return false
        val date = d.pastDate ?: from
        val seconds = d.pastSeconds ?: 0L
        if (seconds <= 0) return false
        val blocks = newBlocksOf(d.counted().blocks.flatMap { runsOf(it) })
        if (blocks.isEmpty()) return false
        correctionFrom = null
        correction = null
        viewModelScope.launch {
            repo.correctWorkout(id, from, date, d.name.ifBlank { "Workout" }, seconds, blocks, d.note)
            // The tag goes with the workout to its new start.
            if (from != date) dao.remove("tag:$id:$from")
            if (d.tag.isEmpty()) dao.remove("tag:$id:$date") else dao.put(Kv("tag:$id:$date", d.tag))
            platform.refreshWidgets()
        }
        return true
    }

    /** A correction's sets as saved: each one that holds what its exercise logs counts as done, the others are left out. */
    fun Draft.counted(): Draft = copy(blocks = blocks.map { b ->
        b.copy(items = b.items.map { if (it is DraftSet) it.copy(done = isValidSet(loadTypeOf(it.exercise ?: b.exercise), it.weight, it.reps)) else it })
    })

    private fun storeDrafts() = viewModelScope.launch(Dispatchers.Default) {
        var stored: Boolean? = null
        for ((id, value) in draftWrites) {
            // Turned into text and written away from the main thread: this runs on every key of the number pad.
            // A draft that is no longer the current one (the workout was finished meanwhile) is not written back.
            draftLock.withLock { if (value == null) dao.remove("draft:$id") else if (value === draft) dao.put(Kv("draft:$id", value.toJson())) }
            // A workout started, saved or cancelled changes what the home-screen widgets say.
            if (stored != (value != null)) platform.refreshWidgets()
            stored = value != null
        }
    }

    /**
     * Throws the workout in progress away, and the rest running for it. With [undoable] the snackbar offers it back exactly as it was:
     * the same draft, so its clock runs from the same start, and the rest if it has time left.
     */
    fun cancelWorkout(undoable: Boolean = true) {
        val was = draft
        val end = restEnd
        val total = restTotal
        putDraft(null)
        startRest(0)
        if (undoable && was != null) say("Workout cancelled") {
            if (draft != null) return@say
            putDraft(was)
            if (end != null && end > currentMillis()) {
                restEnd = end
                restTotal = total
                countDown()
                storeRest()
                syncAlerts()
            }
        }
    }

    /**
     * Round 30: what the dock pill's hold menu asked of the workout page, "finish" or "cancel". The page does it as its own Finish and Cancel buttons do (the same
     * confirmations, the same saving) once it is open, and clears it.
     */
    var activeRequest by mutableStateOf<String?>(null)

    /** Goes to the workout in progress; [request] is what the page is then to do (see [activeRequest]). */
    fun resumeWorkout(request: String? = null) {
        activeRequest = request
        stack.removeAll { it == Screen.Active }
        // From the dock's clock pill or Home's In progress card (their taps name them) the workout grows out of it and shrinks back into it.
        open(Screen.Active, grows = true)
    }

    fun startWorkout() {
        if (draft == null) putDraft(Draft("Workout", currentMillis()))
        open(Screen.Active)
    }

    /** A new workout from a template: its name, exercises and set types; the boxes show the template's values, else each exercise's last session. */
    fun start(template: Template, alsoPaired: List<Boolean> = emptyList()) {
        if (draft != null) {
            clash = Clash(template.name) { start(template, alsoPaired) }
            return
        }
        putDraft(Draft(template.name, currentMillis(), template.blocks.mapIndexed { i, b ->
            // A left and right exercise has two sets per planned one, and both halves aim for the pair's value.
            val paired = sidesOf(b.exercise) || alsoPaired.getOrNull(i) == true
            val each = if (paired) 2 else 1
            DraftBlock(b.exercise, b.sets.flatMap { type -> List(each) { DraftSet(type) } }, b.superset, b.targets.flatMap { t -> List(each) { t } }, paired)
        }, templateId = template.id.takeIf { it != 0L }))
        stack.clear()
        open(Screen.Active)
    }

    /** The workout again: its blocks logged left and right start paired, as do those of exercises switched on. */
    fun startFrom(workout: Workout) = start(templateOf(workout, 0), workout.blocks.filter { it.sets.isNotEmpty() }.map { it.paired })

    /** A workout entered after the fact, for [date] and lasting [seconds]. Returns false when one already starts then; true when it was started or the workout in progress is being asked about. */
    fun startPast(date: String, seconds: Long): Boolean {
        if (draft != null) {
            clash = Clash("Workout") { startPast(date, seconds) }
            return true
        }
        if (allWorkouts.value.any { it.date == date }) {
            message = "A workout already starts at that time"
            return false
        }
        putDraft(Draft("Workout", currentMillis(), pastDate = date, pastSeconds = seconds))
        stack.clear()
        open(Screen.Active)
        return true
    }

    // --- Templates ---

    private fun templateOf(workout: Workout, id: Long) =
        Template(id, workout.name, workout.blocks.filter { it.sets.isNotEmpty() }.map { b ->
            // A block logged left and right keeps one planned set per round, with its left set's values (the right's for a round with only that).
            val sets = if (b.paired) b.rounds.mapNotNull { it.left ?: it.right } else b.sets
            val load = loadTypeOf(b.exercise)
            TemplateBlock(b.exercise, sets.map { it.type }, targets = tidyTargets(sets.map { targetFor(load, it.weight, if (load == LoadType.TIMED) it.seconds else it.reps?.toDouble()) }))
        })

    /** The values a set plans: its weight in kg and its reps (seconds for a timed hold). None for cardio, whose boxes are not weight and reps, or for an empty set. */
    private fun targetFor(load: LoadType, weightKg: Double?, count: Double?): SetTarget? =
        if (load == LoadType.CARDIO || (weightKg == null && count == null)) null else SetTarget(weightKg, count?.let { roundHalfUp(it).toInt() })

    fun save(template: Template) = viewModelScope.launch {
        activeId.value?.let { dao.put(TemplateEntity(template.id, it, template.name, template.blocksJson().toString(), template.folder)) }
    }
    fun saveAsTemplate(workout: Workout) {
        save(templateOf(workout, 0))
        message = "Saved as a template"
    }
    fun deleteTemplate(id: Long) = viewModelScope.launch {
        val template = templates.value.firstOrNull { it.id == id }
        dao.deleteTemplate(id)
        if (template != null) say("Template deleted") { save(template) }
    }

    /** One template per workout name that has none yet, shaped like the latest workout of that name. Strong's export carries no templates, so this rebuilds them. */
    fun templatesFromHistory() {
        val have = templates.value.mapTo(HashSet()) { it.name }
        val latest = LinkedHashMap<String, Workout>()
        for (w in workouts.value.reversed()) if (w.name !in have && w.name !in latest && w.blocks.any { it.sets.isNotEmpty() }) latest[w.name] = w
        latest.values.forEach { save(templateOf(it, 0)) }
        message = if (latest.isEmpty()) "Every workout name already has a template" else "${latest.size} templates made"
    }

    /** A workout as a template, for sending to someone or saving. */
    fun templateFrom(workout: Workout) = templateOf(workout, 0)

    /** A templates file handed to the app by another one (a shared file opened with Clean), waiting to be looked at. */
    var sharedFile by mutableStateOf<String?>(null)

    fun templatesFile(list: List<Template> = templates.value): String = JSONObject().put("clean_templates", 1)
        .put("templates", JSONArray(list.map { JSONObject().put("name", it.name).put("exercises", it.blocksJson()).also { o -> if (it.folder.isNotEmpty()) o.put("folder", it.folder) } })).toString(2)

    fun readTemplatesFile(text: String): List<Template> {
        val list = JSONObject(text).getJSONArray("templates")
        return (0 until list.length()).map { i -> list.getJSONObject(i).let { Template(0, it.getString("name"), Template.blocksOf(it.getJSONArray("exercises")), it.optString("folder")) } }
    }

    /**
     * Adds [exercise] to the workout in [slot]: at the end (the queue, the default) or, with [afterCurrent], where `doNextIndex` says (round 33; round 28
     * put it after the current one): after the exercise being done when it is started (and after its whole superset), before it when it has no
     * set done, at the end when nothing is left to do.
     */
    fun addExercise(exercise: String, slot: DraftSlot = liveSlot, afterCurrent: Boolean = false) {
        val d = slot.draft ?: return
        val last = workouts.value.lastOrNull { w -> w.blocks.any { it.exercise == exercise } }
            ?.blocks?.filter { it.exercise == exercise }?.flatMap { it.sets }.orEmpty()
        val sets = if (last.isEmpty()) List(3) { DraftSet() } else last.map { DraftSet(it.type) }
        val paired = sidesOf(exercise)
        val block = DraftBlock(exercise, sets, paired = paired).let { if (paired) it.withPairsCompleted() else it }
        if (!afterCurrent) slot.put(d.copy(blocks = d.blocks + block))
        else slot.put(d.copy(blocks = d.blocks.toMutableList().also { it.add(doNextIndex(d), block) }))
    }

    /** A new workout that starts with [names] already in it, in that order (the Workout sheet's "Pick exercises"). */
    fun startWithExercises(names: List<String>) {
        if (names.isEmpty()) return
        if (draft != null) {
            clash = Clash("Workout") { startWithExercises(names) }
            return
        }
        putDraft(Draft("Workout", currentMillis()))
        names.forEach { addExercise(it) }
        stack.clear()
        open(Screen.Active)
    }

    /**
     * The Library's + and an exercise page's "Add to <workout>": adds [exercise] to the running workout and says so in a toast with an Undo that
     * takes that card out again (and only that one). Nothing happens when no workout runs or it already holds the exercise.
     */
    fun addToRunning(exercise: String) {
        val d = draft ?: return
        if (d.holds(exercise)) return
        val before = d.blocks.mapTo(HashSet()) { it.id }
        addExercise(exercise)
        val id = draft?.blocks?.firstOrNull { it.id !in before }?.id ?: return
        // The toast (not the Undo bar, whose steps stack under one word): a second add replaces it, and its Undo takes out its own card.
        notice = Notice("$exercise added to ${d.name}", "Undo") { draft?.let { putDraft(it.withoutCard(id)) } }
    }

    /**
     * Opens a finished workout on the Correct page (`Screen.Correct`), its own page apart from the workout in progress, which may
     * be running meanwhile: its sets and notes as cards, its name, note, start and end to change, and Save puts the corrected
     * workout in its place. It is then written the way Clean writes a workout, so rest-timer rows from Strong are not carried over.
     */
    fun editWorkout(workout: Workout) {
        val blocks = workout.blocks.map { b ->
            val unit = unitOf(b.exercise)
            val type = loadTypeOf(b.exercise)
            fun draftSet(item: SetItem) = when (type) {
                LoadType.CARDIO -> DraftSet(item.type, item.distance?.let { shortNumber(it / 1000) }.orEmpty(), item.seconds?.let { shortNumber(it / 60) }.orEmpty(), true, rpe = item.row.rpe)
                LoadType.TIMED -> DraftSet(item.type, item.weight?.let { entryWeight(it, unit) }.orEmpty(), item.seconds?.let { shortNumber(it) }.orEmpty(), true, rpe = item.row.rpe)
                else -> DraftSet(item.type, item.weight?.let { entryWeight(it, unit) }.orEmpty(), item.reps?.toString().orEmpty(), true, rpe = item.row.rpe)
            }
            if (b.paired) {
                // Every round has both halves in the card: one that was not done is an empty, unticked place, which is never saved.
                // The half done first gets the smaller tick number, so a round stored right first is written back that way.
                val sets = b.rounds.flatMapIndexed { n, r ->
                    val kind = (r.left ?: r.right)!!.type
                    val leftTick = if (r.rightFirst) 2L else 1L
                    listOf(r.left?.let(::draftSet)?.copy(ticked = n * 2L + leftTick) ?: DraftSet(kind), r.right?.let(::draftSet)?.copy(ticked = n * 2L + 3 - leftTick) ?: DraftSet(kind))
                }
                // The marker is the block's own and is not among the notes; notes come before the sets.
                DraftBlock(b.exercise, b.notes.map { DraftNote(it.text) } + sets, paired = true)
            } else DraftBlock(b.exercise, b.items.mapNotNull { item ->
                when (item) {
                    is NoteItem -> if (isSidesMarker(item.text)) null else DraftNote(item.text)
                    is SetItem -> draftSet(item)
                    else -> null
                }
            })
        }
        val opened = Draft(workout.name, currentMillis(), blocks, pastDate = workout.date, pastSeconds = workout.durationSeconds, editOf = workout.date, note = workout.notes, tag = workoutTags.value[workout.date].orEmpty()).asCorrection()
        correctionFrom = opened
        putCorrection(opened)
        stack.removeAll { it == Screen.Correct }
        open(Screen.Correct)
    }

    /** How many sets not ticked yet hold what their exercise logs (`isValidSet`): what finishing asks about. */
    fun validUnfinished(d: Draft): Int = d.blocks.sumOf { b ->
        b.items.count { it is DraftSet && !it.done && isValidSet(loadTypeOf(it.exercise ?: b.exercise), it.weight, it.reps) }
    }

    /** Ticks every set [validUnfinished] counts, as ticking it would (its rest length is kept for the export), but without starting a rest. */
    fun completeUnfinished() {
        val d = draft ?: return
        // Halves completed here are ticked after any already ticked, left before right.
        var order = currentMillis()
        putDraft(d.copy(blocks = d.blocks.map { b ->
            val isSet = b.items.map { it is DraftSet }
            val items = b.items.toMutableList()
            for (i in items.indices) {
                val s = items[i] as? DraftSet ?: continue
                if (s.done || !isValidSet(loadTypeOf(s.exercise ?: b.exercise), s.weight, s.reps)) continue
                // Left and right: the first side of a pair rests the time between sides, the second the usual rest.
                val pair = if (b.paired) pairHolding(isSet, i) else null
                val other = pair?.let { (left, right) -> if (left == i) right else left }
                val seconds = if (pair != null && startsSidesRest(other != null, (other?.let { items[it] } as? DraftSet)?.done == true)) restBetweenSides(b.exercise)
                else restFor(b.exercise, s.type, s.rpe)
                items[i] = s.copy(done = true, rest = seconds, ticked = order++)
            }
            b.copy(items = items)
        }))
    }

    /** Saves the ticked sets and the notes. Returns the saved workout's start time, or null when there was nothing to save. */
    fun finishWorkout(): String? {
        val d = draft ?: return null
        val id = activeId.value ?: return null
        // Each run is a variation's stretch of a card, and carries whether the card was logged left and right.
        val runs = d.blocks.flatMap { runsOf(it) }
        val blocks = newBlocksOf(runs)
        if (blocks.isEmpty()) return null
        // Started from a template and done differently (other exercises, order, sets or set types): offer to update it.
        val done = mutableListOf<TemplateBlock>()
        for (b in runs) {
            val sets = b.items.filterIsInstance<DraftSet>()
            // A left and right card is planned in rounds: a template keeps n sets, not 2n, and a round's values are its left set's.
            val planned = if (b.paired) sets.chunked(2).filter { p -> p.any { it.done } } else sets.filter { it.done }.map { listOf(it) }
            if (planned.isEmpty()) continue
            val unit = unitOf(b.exercise)
            val load = loadTypeOf(b.exercise)
            // A round holds one kind in a template: that of the half whose values it takes (left, else right when only that was done).
            val types = planned.map { p -> (p.firstOrNull { it.done } ?: p.first()).type }
            val targets = planned.map { p ->
                val s = p.firstOrNull { it.done } ?: p.first()
                targetFor(load, parseNumber(s.weight)?.let { toKg(it, unit) }, if (load == LoadType.TIMED) parseNumber(s.reps) else s.reps.trim().toIntOrNull()?.toDouble())
            }
            // A note in the middle of an exercise splits its run; for the template it is still one exercise.
            val last = done.lastOrNull()
            if (last != null && last.exercise == b.exercise) done[done.lastIndex] = last.copy(sets = last.sets + types, targets = last.targets + targets) else done += TemplateBlock(b.exercise, types, b.superset, targets)
        }
        templates.value.firstOrNull { it.id == d.templateId }?.let { if (!sameShape(it.blocks, done) || valueIncreases(it.blocks, done).isNotEmpty()) templateOffer = TemplateOffer(it, done) }
        val now = currentMillis()
        val date = d.pastDate ?: strongDate(d.startMillis)
        viewModelScope.launch {
            startRest(0)
            val seconds = d.pastSeconds ?: ((now - d.startMillis) / 1000)
            // Under the lock the draft writer takes, so none of its writes lands between the save and the clearing of `draft`.
            draftLock.withLock {
                repo.saveWorkout(id, date, d.name, seconds, blocks, d.note)
                if (d.tag.isNotEmpty()) dao.put(Kv("tag:$id:$date", d.tag))
                draft = null
            }
            // Home fills in today's calendar square once when it is next shown (kept in memory only).
            if (date.take(10) == LocalDate.now().toString()) freshDay = date.take(10)
            putDraft(null)
        }
        return date
    }

    /** The blocks a workout's cards are saved as: the ticked sets and the notes, a left and right run as rounds (see [pairedBlocks]). */
    private fun newBlocksOf(runs: List<DraftBlock>): List<NewBlock> {
        // Strong stopped writing a row for each rest; Clean writes them only when asked to (Settings, Data).
        val restRows = options.value["export.rest"] == "on"
        fun newItems(b: DraftBlock, item: DraftSet): List<NewItem> {
            val unit = unitOf(b.exercise)
            return listOf(
                when (loadTypeOf(b.exercise)) {
                    // The two boxes are kilometres and minutes for cardio, weight and seconds for a timed hold.
                    LoadType.CARDIO -> NewSet(item.type, distanceMeters = parseNumber(item.weight)?.times(1000), seconds = parseNumber(item.reps)?.times(60), rpe = item.rpe)
                    LoadType.TIMED -> NewSet(item.type, parseNumber(item.weight)?.let { toKg(it, unit) }, seconds = parseNumber(item.reps), rpe = item.rpe)
                    else -> NewSet(item.type, parseNumber(item.weight)?.let { toKg(it, unit) }, item.reps.trim().toIntOrNull(), rpe = item.rpe)
                },
            ) + if (restRows && item.rest > 0) listOf(NewRest(item.rest)) else emptyList()
        }
        val blocks = runs.flatMap { b ->
            if (b.paired) pairedBlocks(b) { newItems(b, it) }
            else listOfNotNull(
                b.items.flatMap { item ->
                    when (item) {
                        is DraftNote -> if (item.text.isEmpty()) emptyList() else listOf(NewNote(item.text))
                        is DraftSet -> if (item.done) newItems(b, item) else emptyList()
                    }
                }.takeIf { items -> items.any { it is NewSet } }?.let { NewBlock(b.exercise, it) },
            )
        }
        return blocks
    }

    /**
     * A left and right run as one block: each round's ticked halves written in the order they were ticked, and how the rounds
     * are stored, which the marker spells out when it is not plain left, right, left, right (a round with only one half
     * ticked is stored as that one set, with no placeholder).
     */
    private fun pairedBlocks(b: DraftBlock, make: (DraftSet) -> List<NewItem>): List<NewBlock> {
        val items = mutableListOf<NewItem>()
        val shapes = mutableListOf<RoundShape>()
        val rightOf = sidePairs(b.items.map { it is DraftSet }).toMap()
        val rights = rightOf.values.filterNotNull().toSet()
        b.items.forEachIndexed { i, item ->
            if (item is DraftNote) {
                if (item.text.isNotEmpty()) items += NewNote(item.text)
            } else if (i !in rights) {
                val left = (item as DraftSet).takeIf { it.done }
                val right = rightOf[i]?.let { b.items[it] as DraftSet }?.takeIf { it.done }
                // A tick number of 0 is not known, and reads as left first.
                val rightFirst = left != null && right != null && right.ticked in 1 until left.ticked
                shapes += when {
                    left != null && right != null -> if (rightFirst) RoundShape.RIGHT_LEFT else RoundShape.LEFT_RIGHT
                    left != null -> RoundShape.LEFT_ONLY
                    right != null -> RoundShape.RIGHT_ONLY
                    else -> return@forEachIndexed
                }
                for (half in if (rightFirst) listOf(right, left) else listOf(left, right)) if (half != null) items += make(half)
            }
        }
        return listOfNotNull(NewBlock(b.exercise, items, paired = true, shapes = shapes).takeIf { items.any { it is NewSet } })
    }

    // ---- Health Connect (round 27)

    /** Whether finished workouts, weigh-ins and body fat are also written to Health Connect (Settings, Data). */
    private val healthOn get() = options.value["health"] == "on"

    /** The last sync in a line ("Sent 3, removed 1"), as Settings shows it; empty before the first. */
    val healthLast: StateFlow<String> = kv.map { it["health.last"].orEmpty() }.distinctUntilChanged().state("")

    /** Whether the last sync was not allowed to send body fat (permission added in round 27, so an earlier "on" does not have it). */
    val healthBodyFatMissing: StateFlow<Boolean> = kv.map { it["health.missing"]?.contains("body fat") == true }.distinctUntilChanged().state(false)

    private val healthLock = Mutex()

    /**
     * Brings Health Connect in line with the app (round 27), for the active profile: what is missing or changed is written, what Clean
     * wrote and no longer exists is removed (a workout or weigh-in deleted while the switch was off, a deleted profile, a restore, a
     * wipe). What was sent is kept as a list on the phone ([Platform.healthLedger], id and fingerprint), so only the difference is sent;
     * it is read fresh from the database, not from the screens' lists, which lag a profile switch. [force] sends everything again.
     * Null when the switch is off or nothing could be read.
     */
    suspend fun healthCatchUp(force: Boolean = false): HealthReport? = healthLock.withLock {
        val id = activeId.value ?: return@withLock null
        if (!healthOn || !platform.hasHealth) return@withLock null
        val report = if (!platform.healthAvailable()) HealthReport(failure = HealthFailure(HealthFailureKind.OTHER, "not available on this phone"))
        else if (!platform.healthAllowed()) HealthReport(failure = HealthFailure(HealthFailureKind.PERMISSION)) else {
            val unit = profiles.value.firstOrNull { it.id == id }?.unit ?: "kg"
            val rows = dao.rowsNow(id)
            val entries = dao.measurementsNow(id).map { HealthEntry(it.file, it.date, it.value) }
            val ledger = decodeLedger(platform.healthLedger())
            val known = dao.profilesNow().mapTo(HashSet()) { it.id }
            val plan = withContext(Dispatchers.Default) {
                planHealth(desiredHealth(id, unit, buildWorkouts(rows.map { it.toRow() }), entries), ledger, id, known, force)
            }
            val done = if (plan.isEmpty) HealthReport() else platform.healthApply(HealthRequest(plan.write, plan.remove))
            // Only what went through is entered in the list; anything else is tried again at the next sync.
            val now = LinkedHashMap(ledger)
            for (r in plan.write) if (r.id in done.written) now[r.id] = r.fingerprint
            for (r in done.removed) now.remove(r)
            if (!plan.isEmpty) platform.setHealthLedger(encodeLedger(now))
            done
        }
        val text = healthReportText(report)
        // A failure is said once on the screen; every sync's result is in Settings.
        if (report.failure != null && options.value["health.last"] != text) message = text
        dao.put(Kv("health.last", text))
        dao.put(Kv("health.missing", report.notAllowed.joinToString(",") { it.label }))
        report
    }

    /** The Settings button "Send everything logged so far": every record again (same ids, so none doubles). Returns how many Health Connect took. */
    suspend fun sendHistoryToHealth(): Int = healthCatchUp(force = true)?.written?.size ?: 0

    /**
     * Takes away every record Clean wrote and forgets what was sent (the switch off with "Remove them", and before everything is deleted).
     * Says why when Health Connect refuses.
     */
    suspend fun healthRemoveAll(): Boolean {
        val failure = platform.healthRemoveAll()
        if (failure == null) platform.setHealthLedger("")
        val text = if (failure == null) "Removed Clean's records from Health Connect" else "Health Connect refused: ${failure.text}"
        dao.put(Kv("health.last", text))
        if (failure != null) message = text
        return failure == null
    }

    /** From the start: the sync runs a moment after the app's data change, the switch is turned on, a profile is deleted or switched, or the app starts with the switch on. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startHealthSync() {
        viewModelScope.launch {
            snapshotFlow { loaded }.first { it }
            combine(allWorkouts, measurements, profiles, options.map { it["health"] }.distinctUntilChanged()) { _, _, _, _ -> }
                .debounce(2_000)
                .collect { if (healthOn) healthCatchUp() }
        }
    }

    /** Sets a finished workout's note (only its rows' Workout Notes field changes) and offers to take it back. */
    fun setWorkoutNote(date: String, note: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (allWorkouts.value.firstOrNull { it.date == date }?.notes == note) return@launch
        val before = repo.setWorkoutNote(id, date, note) ?: return@launch
        say("Note saved") { viewModelScope.launch { repo.setWorkoutNote(id, date, before) } }
    }

    fun deleteWorkout(date: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val rows = repo.deleteWorkout(id, date)
        platform.refreshWidgets()
        // The rows are kept in memory, as they were stored, for the few seconds the delete can be taken back.
        if (rows.isNotEmpty()) say("Workout deleted") {
            viewModelScope.launch {
                if (!repo.restoreWorkout(id, rows)) return@launch
                platform.refreshWidgets()
            }
        }
    }

    /** What a tap on a home-screen widget asks for: `start`, `week`, `last`, `calendar`, `workout`, `weight` or `template:<id>`. */
    fun widgetAction(action: String) = viewModelScope.launch {
        // The app may only just have started: wait for the profile, and for a workout in progress to be read back, before acting.
        val id = activeId.first { it != null } ?: return@launch
        if (draft == null) dao.get("draft:$id")?.let { stored -> runCatching { Draft.fromJson(stored) }.getOrNull()?.takeIf { it.editOf == null }?.let { draft = it } }
        stack.clear()
        when {
            action == "start" -> startWorkout()
            action == "workout" -> if (draft != null) open(Screen.Active)
            // "Set done" on the notification when the app had to be started for it: tick, then show the workout.
            action == "setdone" -> if (draft != null) { tickNextSet(); open(Screen.Active) }
            action == "weight" -> logWeight()
            action == "calendar" -> open(Screen.Calendar())
            action == "week" -> open(Screen.Week(clean.core.weekStart(LocalDate.now()).toString()))
            action == "last" -> withTimeoutOrNull(3000) { workouts.first { it.isNotEmpty() } }?.lastOrNull()?.let { tab = Tab.Progress; open(Screen.WorkoutDetail(it.date)) }
            // From a link in another app: a tab by its name, or a template by its name.
            // History and Body are pages over Progress now; an unknown name is ignored.
            action.startsWith("tab:") -> when (val name = action.substringAfter(':').lowercase()) {
                "history" -> openHistory(keepTab = false)
                "body" -> openBody(keepTab = false)
                "workout" -> openWorkoutSheet()
                "library" -> tab = Tab.Exercises
                else -> Tab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { tab = it }
            }
            action.startsWith("templateName:") -> {
                if (draft != null) open(Screen.Active)
                else withTimeoutOrNull(3000) { templates.first { it.isNotEmpty() } }?.firstOrNull { it.name.equals(action.substringAfter(':'), ignoreCase = true) }?.let { start(it) }
                    ?: run { message = "No template is called ${action.substringAfter(':')}" }
            }
            action.startsWith("template:") -> {
                if (draft != null) open(Screen.Active)
                else withTimeoutOrNull(3000) { templates.first { it.isNotEmpty() } }?.firstOrNull { it.id.toString() == action.substringAfter(':') }?.let { start(it) }
            }
        }
    }

    // --- Measurements ---

    /** Set by [logWeight]; the weight page opens its Log body weight sheet and clears it. */
    var logWeightNow by mutableStateOf(false)

    /** Goes to the body-weight page with the ruler sheet already open. */
    fun logWeight() {
        logWeightNow = true
        open(Screen.MeasurementDetail("weight.csv"))
    }

    /** Adds an entry dated [date] (now unless given); false, with a line saying so, when the file already has one at that second. */
    suspend fun addMeasurement(file: String, value: Double, date: String = strongDate(currentMillis())): Boolean {
        val id = activeId.value ?: return false
        if (measurements.value.any { it.file == file && it.date == date }) {
            message = "There is already an entry at that time"
            return false
        }
        repo.addMeasurement(id, file, date, value)
        return true
    }

    /**
     * Saves [entry] with a new [date] and, when [value] is given, a new value (written the way a new entry is); with no value the
     * stored text stays exactly as it was. False, with a line saying so, when another entry of the file is at that second.
     */
    suspend fun editMeasurement(entry: MeasurementEntity, date: String, value: Double?): Boolean {
        if (measurements.value.any { it.file == entry.file && it.date == date && it.id != entry.id }) {
            message = "There is already an entry at that time"
            return false
        }
        val text = value?.let(clean.core.Measurements::newText) ?: entry.value
        dao.updateMeasurement(entry.copy(date = date, value = text))
        return true
    }

    fun deleteMeasurement(entry: MeasurementEntity) = viewModelScope.launch {
        dao.deleteMeasurement(entry.id)
        // Health Connect follows (the sync notices the entry is gone, and back if the delete is taken back).
        // Put back with the same id, so it takes its old place among entries of the same moment.
        say("Entry deleted") {
            viewModelScope.launch {
                dao.insertMeasurements(listOf(entry))
            }
        }
    }

    /** A measurement of the user's own, as one more file beside the ones a Strong export has: `forearm.csv` with the heading `Forearm (cm)`. */
    fun addMeasurementKind(label: String, unit: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val file = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "measurement" } + ".csv"
        if (files.value.any { it.name == file }) message = "There is already a measurement called $label"
        else dao.insertFiles(listOf(MeasurementFileEntity(profileId = id, name = file, header = if (unit.isEmpty()) label else "$label ($unit)")))
    }
}
