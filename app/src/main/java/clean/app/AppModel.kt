package clean.app

import clean.core.ChartStyle
import clean.core.planIsoRowFix
import clean.core.rowsOfWorkouts
import clean.core.NO_TEMPLATE_COLOR
import clean.core.TemplateColorMode
import clean.core.assignTemplateColors
import clean.core.chartStyleOf
import clean.core.encodeTemplateColor
import clean.core.hueOfRgb
import clean.core.nearestToAccent
import clean.core.parseTemplateColor
import clean.core.parseTemplateColorScope
import clean.core.templateColorModeOf
import clean.core.RoundShape
import clean.core.navOrder
import clean.core.withMiddle
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
import clean.core.weekStart
import clean.core.withTarget
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import kotlinx.coroutines.withContext
import clean.core.NewRest
import clean.core.sessionsByExercise
import clean.core.Session
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
import clean.core.restNotifCompact
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
import clean.app.ui.theme.DEFAULT_PRESET
import clean.app.ui.theme.LOOK_KEYS
import clean.app.ui.theme.Look
import clean.app.ui.theme.SavedPreset
import clean.app.ui.theme.decodeSaved
import clean.app.ui.theme.SAVED_REF
import clean.app.ui.theme.dayNightChoice
import clean.app.ui.theme.withSavedLook
import clean.app.ui.theme.encodeSaved
import clean.app.ui.theme.lookKeysOf
import clean.app.ui.theme.resetLookKeys
import clean.app.ui.theme.Part
import clean.app.ui.theme.PRESETS
import clean.core.DEFAULT_DAY_FROM
import clean.core.DEFAULT_LATITUDE
import clean.core.DEFAULT_NIGHT_FROM
import clean.core.SWITCH_PHONE
import clean.core.SWITCH_TIMES
import clean.core.dayWindow
import clean.core.isDaytime
import clean.core.parseClock
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.monoSettingsOf
import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import clean.core.Photo
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

private val cardIds = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000)

/** A card id no other card has: a count started from the clock, so it also stays clear of the ids in a draft stored before a restart. */
fun newCardId(): Long = cardIds.incrementAndGet()

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
                if (o.has("editOf")) o.getString("editOf") else null, o.optString("note"),
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

private const val STACK_KEY = "stack"

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
    /** Which cards Home shows and in what order. */
    data object EditHome : Screen
    /** Which cards Profile shows and in what order. */
    data object EditProfile : Screen
    /** The tab bar: the tab names and the order of the four outer tabs. */
    data object Navigation : Screen
    /** Which launcher shortcuts the app icon offers, and their order. */
    data object Shortcuts : Screen
    /** Progress photos, a timeline; [Photo] is one full screen among those of [pose] (all when null), [PhotoCompare] two side by side. */
    data object Photos : Screen
    data class Photo(val name: String, val pose: Pose?) : Screen
    data class PhotoCompare(val pose: Pose?) : Screen
}

/** The page with [from] named [to], for a page that was open on an exercise when it was renamed. */
fun Screen.renamedExercise(from: String, to: String): Screen = when {
    this is Screen.ExerciseDetail && name == from -> copy(name = to, edit = false)
    this is Screen.PrTimeline && exercise == from -> copy(exercise = to)
    this is Screen.Overall && exercise == from -> copy(exercise = to)
    this is Screen.NoteReview && exercise == from -> copy(exercise = to)
    else -> this
}

enum class Tab(val label: String) { Home("Home"), Progress("Progress"), Workout("Workout"), Exercises("Exercises"), Profile("Profile") }

/** The five tabs in the order the bar and the pager lay them out: the stored order of the outer four (kv `nav.order`), Workout third. */
fun tabOrderOf(stored: String?): List<Tab> =
    withMiddle(navOrder(stored).map { name -> Tab.entries.first { it.name.lowercase() == name } }, Tab.Workout)

/** Whether Back goes to Home rather than leaving the app: no page is open and the tab on show is not Home. */
fun backGoesHome(noPage: Boolean, tab: Tab): Boolean = noPage && tab != Tab.Home

fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Kilograms shown in [unit]. */
fun showWeight(kg: Double, unit: String): String = shortNumber(if (unit == "lb") kgToLb(kg) else kg)

/** An estimated 1RM as shown: a guess, so to a tenth in the shown unit, not to the hundredth a logged weight may need. */
fun showEstimate(kg: Double, unit: String): String = shortNumber(tenth(if (unit == "lb") kgToLb(kg) else kg))

/** [value] to a tenth, for an estimate on a chart or in a line. */
fun tenth(value: Double): Double = Math.round(value * 10) / 10.0

/** A typed weight in [unit] as kilograms; pounds are converted at four decimals, as Strong does. */
fun toKg(value: Double, unit: String): Double = if (unit == "lb") lbToKg(value) else value

fun strongDate(millis: Long): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(STRONG_DATE_FORMAT)

@OptIn(ExperimentalCoroutinesApi::class)
class AppModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    val repo = Repo(CleanDb.open(app))
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
    /** The minute of the clock, so that Day & night can flip while the app is open. */
    private val minuteTicks: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now / 60_000)
            delay(60_000 - now % 60_000)
        }
    }
    /** The preset to draw now: the chosen one, or for Day & night the day or night choice by its rule (a built-in id or `saved:<name>`). */
    private fun presetNow(o: Map<String, String>, systemDark: Boolean, minute: Long): String {
        val chosen = o["theme.preset"] ?: DEFAULT_PRESET
        if (chosen != "daynight") return chosen
        val saved = decodeSaved(o["theme.saved"])
        val day = dayNightChoice(o["theme.day"], saved, "light")
        val night = dayNightChoice(o["theme.night"], saved, "dark")
        val rule = o["theme.switch"] ?: SWITCH_TIMES
        if (rule == SWITCH_PHONE) return if (systemDark) night else day
        val zone = java.time.ZoneId.systemDefault()
        val at = java.time.Instant.ofEpochSecond(minute * 60).atZone(zone)
        val (from, to) = dayWindow(
            rule, at.toLocalDate(), o["theme.lat"]?.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: DEFAULT_LATITUDE, at.offset.totalSeconds / 60,
            parseClock(o["theme.dayFrom"]) ?: DEFAULT_DAY_FROM, parseClock(o["theme.nightFrom"]) ?: DEFAULT_NIGHT_FROM,
        )
        return if (isDaytime(at.hour * 60 + at.minute, from, to)) day else night
    }
    /** The look [o] makes with [preset] as its base: its mixes and the older single options as overrides. */
    private fun lookOf(o: Map<String, String>, preset: String, system: Boolean): Look = resolveLook(
        preset,
        Part.entries.mapNotNull { part -> o["theme.mix.${part.key}"]?.let { part to it } }.toMap(),
        accentOverride = o["accent"], scaleOverride = o["palette"], modeOverride = o["theme"], backgroundOverride = o["background"], systemDark = system,
        iconAccentOverride = o["theme.iconAccent"], mono = monoSettingsOf(o), colourBlindSafe = o["theme.cbsafe"] == "on",
    )
    /** The look (`theme.preset` with its `theme.mix.*` parts, the older accent / palette / background / theme options as overrides). A preset is AMOLED, the app's own look, until another is chosen. Day & night on a saved look draws that look exactly, as if it were chosen. */
    val look: StateFlow<Look> = combine(kv, systemDark, minuteTicks) { o, system, minute ->
        val now = presetNow(o, system, minute)
        val saved = if (now.startsWith(SAVED_REF)) decodeSaved(o["theme.saved"]).firstOrNull { it.name == now.removePrefix(SAVED_REF) } else null
        if (saved == null) lookOf(o, now, system)
        else withSavedLook(o, saved).let { lookOf(it, it["theme.preset"] ?: DEFAULT_PRESET, system) }
    }.distinctUntilChanged().state(resolveLook(DEFAULT_PRESET))
    /** The chip chosen on the Appearance page: a preset, or `daynight`. */
    val presetChoice: StateFlow<String> = kv.map { it["theme.preset"] ?: DEFAULT_PRESET }.distinctUntilChanged().state(DEFAULT_PRESET)

    /** The colour scale's name: the user's choice of palette, else the preset's. */
    val palette: StateFlow<String> = look.map { it.scaleName }.distinctUntilChanged().state(PALETTES.keys.first())

    /** The mode choice shown in Settings: what was set, else the mode the preset is in. */
    val theme: StateFlow<String> = combine(kv, look) { o, l -> o["theme"] ?: if (l.dark) "Dark" else "Light" }.distinctUntilChanged().state("Dark")

    /** Picks a preset as a whole: every part swapped in and every older override is cleared. */
    fun choosePreset(id: String) = viewModelScope.launch {
        dao.put(Kv("theme.preset", id))
        dao.removePrefix("theme.mix.")
        for (key in listOf("accent", "palette", "background", "theme", "theme.iconAccent")) dao.remove(key)
    }

    /** Puts the look back exactly as [keys]: every key that makes up the look is cleared first, so nothing of the old one is left over. */
    fun applyLook(keys: Map<String, String>) = viewModelScope.launch {
        dao.removePrefix("theme.mix.")
        for (key in LOOK_KEYS) dao.remove(key)
        for ((key, value) in keys) dao.put(Kv(key, value))
    }

    /**
     * Back to the chosen preset's own look (not the app's default, 2026-10-16): every mix, override and Mono slider is cleared; the preset,
     * and for Day & night its day and night presets and switch, stay. Undo puts the old look back.
     */
    fun resetLook() {
        val before = lookKeysOf(kv.value)
        applyLook(resetLookKeys(before))
        say("Look reset to the preset") { applyLook(before) }
    }

    /** The looks the user saved (kv `theme.saved`, for the whole app). */
    val savedPresets: StateFlow<List<SavedPreset>> = kv.map { decodeSaved(it["theme.saved"]) }.distinctUntilChanged().state(emptyList())
    private fun storeSaved(list: List<SavedPreset>) = setOption("theme.saved", if (list.isEmpty()) null else encodeSaved(list))

    /** Saves the look as it is now under [name]; an existing preset of that name is replaced. */
    fun saveLook(name: String) = storeSaved(savedPresets.value.filter { it.name != name } + SavedPreset(name, lookKeysOf(kv.value)))
    /** Renames a saved look; Day & night keeps following it under the new name. */
    fun renameSaved(preset: SavedPreset, name: String) {
        storeSaved(savedPresets.value.map { if (it == preset) it.copy(name = name) else it })
        for (key in listOf("theme.day", "theme.night")) if (kv.value[key] == SAVED_REF + preset.name) setOption(key, SAVED_REF + name)
    }
    fun deleteSaved(preset: SavedPreset) {
        val before = savedPresets.value
        storeSaved(before - preset)
        say("Deleted ${preset.name}") { storeSaved(before) }
    }

    // Look and feel, and the auto-fill preference: small values kept under their own keys.
    val accent: StateFlow<String> = kv.map { it["accent"] ?: "Blue" }.state("Blue")
    /** The dark theme's background: `Black`, `Dark grey`, or a colour of the user's own. */
    val background: StateFlow<String> = kv.map { it["background"] ?: "Black" }.state("Black")
    val textSize: StateFlow<String> = kv.map { it["textSize"] ?: "Medium" }.state("Medium")
    /** An imported font as `path|name`, or null for the default. */
    val font: StateFlow<String?> = kv.map { it["font"] }.state(null)
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
    /** The tab an exercise's page opens on when it is reached from a workout in progress (History unless changed). */
    val workoutExerciseTab: StateFlow<Int> = kv.map { it["workout.exerciseTab"]?.toIntOrNull() ?: 1 }.state(1)

    /** The chart being shown full screen. */
    var fullChart by mutableStateOf<FullChart?>(null)
    /** Set by Settings to have the Workout tab open the template file picker. */
    var importTemplatesNow by mutableStateOf(false)
    /** Set by the empty Profile card to have Settings open the file picker for an import. */
    var importNow by mutableStateOf(false)

    /** Built-in exercises never logged stay out of the lists until searched for. */

    fun setOption(key: String, value: String?) = viewModelScope.launch { if (value == null) dao.remove(key) else dao.put(Kv(key, value)) }

    /** Everything on screen is derived from this list; nothing computed is stored. */
    val workouts: StateFlow<List<Workout>> = perProfile(emptyList()) { id ->
        dao.rows(id).map { rows -> buildWorkouts(rows.map { it.toRow() }) }.flowOn(Dispatchers.Default)
    }.state(emptyList())
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
        val muscles = java.util.concurrent.ConcurrentHashMap<String, MuscleUse>()
        val loadTypes = java.util.concurrent.ConcurrentHashMap<String, LoadType>()
    }
    @Volatile private var exerciseMemo = ExerciseMemo(emptyMap())
    private fun memo(): ExerciseMemo {
        val now = settings.value
        val m = exerciseMemo
        return if (m.settings === now) m else ExerciseMemo(now).also { exerciseMemo = it }
    }

    val templates: StateFlow<List<Template>> = perProfile(emptyList()) { id ->
        dao.templates(id).map { list -> list.mapNotNull { t -> runCatching { Template(t.id, t.name, Template.blocksOf(JSONArray(t.blocks)), t.folder) }.getOrNull() } }.flowOn(Dispatchers.Default)
    }.state(emptyList())

    /** How charts draw their figures (Settings, Appearance, Charts). */
    val chartStyle: StateFlow<ChartStyle> = kv.map { chartStyleOf(it["chart.style"]) }.distinctUntilChanged().state(ChartStyle.LINE)

    /** The profile's template colours: the mode, and the colour each template shows (see `TemplateColors.kt`). */
    val templateColors: StateFlow<TemplateColors> = combine(kv, templates, activeId) { o, list, id -> TemplateColors.of(o, list, id) }.distinctUntilChanged().state(TemplateColors.OFF)

    /** Sets the colour of a template by hand: an index into the ten, or null for none. Stored under the active profile; Random leaves a "none" alone. */
    fun setTemplateColor(templateId: Long, index: Int?) = viewModelScope.launch {
        val profile = activeId.value ?: return@launch
        dao.put(Kv("tpl.color:$profile:$templateId", encodeTemplateColor(index ?: NO_TEMPLATE_COLOR)))
    }

    init {
        // Random gives a colour to each template in scope that has none yet, once, and the kv keeps it; nothing is rewritten while they stay.
        viewModelScope.launch {
            combine(kv, templates, activeId) { o, list, id -> Triple(o, list, id) }.collect { (o, list, id) ->
                if (id == null || list.isEmpty() || templateColorModeOf(o["tpl.colors:$id"]) != TemplateColorMode.RANDOM) return@collect
                val stored = list.mapNotNull { t -> parseTemplateColor(o["tpl.color:$id:${t.id}"])?.let { t.id to it } }.toMap()
                val now = look.value
                val accent = if (now.dark) now.accent.first else now.accent.second
                val given = assignTemplateColors(
                    list.map { it.id to it.folder }, stored, parseTemplateColorScope(o["tpl.colors.scope:$id"]),
                    nearestToAccent(hueOfRgb(accent.red.toDouble(), accent.green.toDouble(), accent.blue.toDouble())),
                )
                for ((templateId, color) in given) dao.put(Kv("tpl.color:$id:$templateId", encodeTemplateColor(color)))
            }
        }
    }

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

    /** Set once the global `goal.week`, `program` and `reward.seen` of before profiles had their own have been moved to the profile that was active. */
    private val adopted = MutableStateFlow(false)

    /**
     * The weekly target and when each took effect (kv `goal.week:<profileId>`), null until the stored options have been read. Until one is
     * stored it is the user's own usual pace from the first workout's week, so that history is not scored against a later pick.
     */
    val targets: StateFlow<List<TargetFrom>?> = combine(
        combine(kv, activeId) { options, id -> if (options.isEmpty() || id == null) null else options["goal.week:$id"].orEmpty() }.distinctUntilChanged(),
        workouts, adopted,
    ) { stored, all, ready ->
        if (stored == null || !ready) null else targetsOrDefault(decodeTargets(stored.ifEmpty { null }), all, LocalDate.now())
    }.flowOn(Dispatchers.Default).state(null)

    /** The weekly target, streak, badges and habits; nothing about them is stored, so this is worked out again when the workouts or the target change. */
    val consistency: StateFlow<Consistency?> = combine(workouts, targets) { all, t ->
        t?.let { clean.core.consistency(all, it, LocalDate.now()) }
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
    val programStanding: StateFlow<ProgramStanding?> = combine(workouts, program, settings) { all, versions, own ->
        if (versions.isEmpty()) null else programStanding(versions, all, LocalDate.now(), overallLookup(own))
    }.flowOn(Dispatchers.Default).state(null)

    /** Today's place on the program and what to do next; null with no program. */
    val programToday: StateFlow<TodayPlan?> = combine(workouts, program, settings) { all, versions, own ->
        if (versions.isEmpty()) null else todayPlan(versions, all, LocalDate.now(), overallLookup(own))
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
        refreshWidgets(getApplication())
        say(if (on) "Read as left and right" else "Read as ordinary sets") {
            viewModelScope.launch {
                repo.markSides(id, date, exercise, nth, !on)
                refreshWidgets(getApplication())
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
            refreshWidgets(getApplication())
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
            refreshWidgets(getApplication())
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

    val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
        .map { id -> if (id == null) emptyList() else PhotoFiles.list(app, id) }.flowOn(Dispatchers.IO).state(emptyList())

    /** The pages open over the tabs. Kept in the saved state, so a page is still open after the phone killed the app in the background. */
    val stack = mutableStateListOf<Screen>().apply { saved.get<ArrayList<String>>(STACK_KEY)?.mapNotNullTo(this, ::decodeScreen) }

    init {
        viewModelScope.launch { snapshotFlow { stack.map(::encodeScreen) }.collect { saved[STACK_KEY] = ArrayList(it) } }
    }

    /** A line shown for a moment at the top of the screen. */
    class Notice(val text: String)
    var notice by mutableStateOf<Notice?>(null)
    var message: String?
        get() = notice?.text
        set(value) { notice = value?.let { Notice(it) } }

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
        undoUntil = System.currentTimeMillis() + UNDO_MILLIS
    }

    /** The Undo button: every step in the bar is taken back, newest first. */
    fun takeBack() {
        val all = undos ?: return
        undos = null
        all.undoAll()
    }

    /** The bar's time is up: what was kept for an Undo can go. */
    fun undoExpired() {
        val all = undos ?: return
        undos = null
        all.expireAll()
    }

    /** The page of Settings to open on (set by whatever sends the user there, read once when Settings opens). */
    var settingsPage: String? = null
    var clash by mutableStateOf<Clash?>(null)
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

    init {
        viewModelScope.launch {
            val id = activeId.first { it != null } ?: return@launch
            // Only counted here; the lists themselves arrive through the flows, which are then waited for.
            if (dao.rowCount(id) > 0) workouts.first { it.isNotEmpty() }
            if (dao.measurementCount(id) > 0) measurements.first { it.isNotEmpty() }
            consistency.first { it != null }
            welcoming = welcomeDue(id)
            loaded = true
            firstPageDone.value = true
            // Only now, so that nothing competes with reading the data for the first page: the library's
            // tables are read in the background (no tap then has to wait for them), and the launcher
            // shortcuts and widgets are brought up to date.
            withContext(Dispatchers.Default) {
                Feedback.init(getApplication())
                suggest("")
                otherNames("")
                refreshWidgets(getApplication())
            }
        }
        storeDrafts()
        storeCorrections()
        // As the first page is shown after ten seconds at the most (MainActivity), the figures waiting for it start then at the latest.
        viewModelScope.launch { delay(10_000); firstPageDone.value = true }
        viewModelScope.launch {
            // The launcher icon follows the look: one alias is enabled at a time (IconAliases), switched only when the choice changes.
            snapshotFlow { loaded }.first { it }
            look.map { it.icon to it.iconAccent }.distinctUntilChanged().collect { (tile, accent) ->
                val wanted = IconAliases.key(tile, accent)
                if ((dao.get("theme.iconApplied") ?: IconAliases.DEFAULT) != wanted) {
                    val applied = dao.get("theme.iconApplied")
                    withContext(Dispatchers.IO) { IconAliases.switch(getApplication(), wanted, applied) }
                    dao.put(Kv("theme.iconApplied", wanted))
                    // Android drops the dynamic shortcuts when the launcher activity changes; they are set again once it has.
                    delay(2000)
                    refreshWidgets(getApplication())
                }
            }
        }
        viewModelScope.launch {
            // The Today widget is drawn in the look's colours; they are kept (kv `widget.colors`) for a redraw with the app gone, and the widgets are redrawn when they change.
            snapshotFlow { loaded }.first { it }
            look.map { l ->
                val accent = if (l.dark) l.accent.first else l.accent.second
                clean.core.encodeArgb(listOf(l.page, l.raised, l.text, l.muted, accent, accent.readableOn()).map { it.toArgb() })
            }.distinctUntilChanged().collect { colours ->
                if (dao.get("widget.colors") != colours) {
                    dao.put(Kv("widget.colors", colours))
                    refreshWidgets(getApplication())
                }
            }
        }
        viewModelScope.launch {
            // Presets arrived with AMOLED as the look everyone already had; the older accent, background, palette and theme options stay as they are and now act as overrides of it.
            if (dao.get("theme.preset") == null) dao.put(Kv("theme.preset", DEFAULT_PRESET))
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
            if (end != null && end > System.currentTimeMillis() && restEnd == null) {
                restEnd = end
                restTotal = stored?.getOrNull(1)?.toIntOrNull() ?: ((end - System.currentTimeMillis()) / 1000).toInt()
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

    fun open(screen: Screen) { stack.add(screen) }
    fun back() { stack.removeLastOrNull() }
    /** The card Arrange Home or Arrange profile should open on and where its top was on screen (px from the window's top), so the page keeps the place the list was scrolled to; taken once by the arrange page. */
    var arrangeAnchor: Pair<String, Float>? = null

    // --- Profiles ---

    fun switchTo(id: Long) = viewModelScope.launch { dao.put(Kv("active", id.toString())) }
    fun createProfile(name: String) = viewModelScope.launch { switchTo(repo.createProfile(name)) }
    fun update(profile: Profile) = viewModelScope.launch { dao.update(profile) }
    fun deleteProfile(id: Long) = viewModelScope.launch {
        val other = profiles.value.firstOrNull { it.id != id } ?: return@launch
        if (activeId.value == id) dao.put(Kv("active", other.id.toString()))
        repo.deleteProfile(id)
        withContext(Dispatchers.IO) { PhotoFiles.deleteAll(getApplication(), id) }
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
        val members = (settings.value.keys + logged.value + libraryVariations(overall) + exercise).filterTo(sortedSetOf()) { overallOf(it) == overall }
        return if (members.size > 1) members.toList() else emptyList()
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

    private var indexed: List<Workout>? = null
    private var index: Map<String, List<Session>> = emptyMap()

    /**
     * Every session of [exercise] in [workouts], oldest first. The whole history is sorted into
     * exercises once and kept until the list of workouts changes, so a page asking for several
     * exercises does not go through the history once for each.
     */
    @Synchronized
    fun sessionsIn(workouts: List<Workout>, exercise: String): List<Session> {
        if (indexed !== workouts) {
            index = sessionsByExercise(workouts)
            indexed = workouts
        }
        return index[exercise].orEmpty()
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
            val tag = below.superset ?: "s${System.currentTimeMillis()}"
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
    /** The whole length of the rest in progress, in seconds, for showing how much of it is left. */
    var restTotal by mutableStateOf(0)
        private set
    private var restJob: Job? = null
    val restDefault: StateFlow<Int> = kv.map { it["rest.seconds"]?.toIntOrNull() ?: 120 }.state(120)
    /** Where the "workout in progress" bar shows: `Everywhere`, `Main tabs` or `Off`. */
    val bannerScope: StateFlow<String> = kv.map { it["banner"] ?: "Everywhere" }.state("Everywhere")

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
            restEnd = System.currentTimeMillis() + seconds * 1000L
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
            val seconds = ((end - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
            val lead = minOf(5, seconds)
            delay((seconds - lead) * 1000L)
            for (left in lead downTo 1) {
                if (alertsHere() && restEndFeedback().vibrate) Feedback.pulse(if (left == 1) 600 else 150)
                delay(1000)
            }
            if (alertsHere() && restEndFeedback().sound) Feedback.bell(RestTone.of(options.value["rest.tone"]))
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

    /** Behind the app a rest is carried by an alarm (if Settings asks for one); in front, or with no rest, there is none. */
    private fun syncAlerts() {
        val end = restEnd
        val app = getApplication<Application>()
        if (!started && end != null && options.value["rest.background"] == "on") RestAlert.schedule(app, end, restKey(), restTotal, draft?.let { nextSetOf(it) }?.words(), restNotifCompact(options.value["rest.notif"])) else RestAlert.cancel(app)
        syncWorkoutNotice()
    }

    /** Behind the app the workout in progress is a notification (unless Settings says no); in front, or with none or one entered after the fact, there is none. */
    private fun syncWorkoutNotice() {
        val app = getApplication<Application>()
        val d = draft
        if (!started && d != null && d.pastDate == null && workoutNotifOn(options.value["workout.notif"])) {
            val rest = restEnd?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime() }
            WorkoutNotice.post(app, d.name, d.startMillis, workoutNotifText(nextSetOf(d)?.words(), rest))
        } else WorkoutNotice.cancel(app)
    }

    init {
        // The workout-day reminder's alarm follows the days and time chosen; set again at each start, in case it was lost.
        viewModelScope.launch {
            options.map { it["remind.days"] to it["remind.time"] }.distinctUntilChanged().collect { (days, time) -> WorkoutReminder.sync(getApplication(), days, time) }
        }
        // "+15 s" and "Skip" on the rest's notification, while the app is alive behind it.
        RestAlert.onAction = { add -> if (add) changeRest(RestAlert.MORE) else startRest(0) }
    }

    override fun onCleared() {
        RestAlert.onAction = null
        super.onCleared()
    }

    fun onStarted() {
        started = true
        syncAlerts()
    }

    fun onStopped() {
        started = false
        syncAlerts()
    }

    /** Lengthens or shortens the rest in progress; shortened to nothing it just stops. */
    fun changeRest(bySeconds: Int) {
        val end = restEnd ?: return
        startRest(((end - System.currentTimeMillis()) / 1000).toInt() + bySeconds, changing = true)
    }

    // --- Stopwatch for timed sets ---

    /** When the stopwatch of a timed set was started, in clock milliseconds; null when none runs. Not stored: it is gone with the app. */
    var stopwatchStart by mutableStateOf<Long?>(null)
        private set
    private var stopwatchBell: Job? = null

    /** Starts the clock if it is not running. [target] (seconds) is what it counts down to, with a bell on reaching it; null just counts up. */
    fun startStopwatch(target: Int? = null) {
        val begun = stopwatchStart ?: System.currentTimeMillis().also { stopwatchStart = it }
        stopwatchBell?.cancel()
        val left = if (target == null) 0L else begun + target * 1000L - System.currentTimeMillis()
        stopwatchBell = if (left <= 0) null else viewModelScope.launch {
            delay(left)
            Feedback.bell(RestTone.of(options.value["rest.tone"]))
        }
    }

    /** Stops the clock and gives the whole seconds it counted. */
    fun stopStopwatch(): Int {
        val begun = stopwatchStart ?: return 0
        stopwatchBell?.cancel()
        stopwatchStart = null
        return ((System.currentTimeMillis() - begun) / 1000).toInt()
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
        repo.renameExercise(id, from, to, merged, automatic, inDraft, strongDate(System.currentTimeMillis()))
        if (inDraft) putDraft(current!!.renamed(from, to))
        correction?.let { putCorrection(it.renamed(from, to)) }
        for (i in stack.indices) stack[i] = stack[i].renamedExercise(from, to)
        refreshWidgets(getApplication())
        message = if (merged) "“$from” merged into “$to”" else "“$from” renamed to “$to”"
    }

    /** The renames that can still be undone, newest first. */
    suspend fun renameLog(): List<RenameEntry> = activeId.value?.let { repo.renameLog(it) }.orEmpty()

    /** Takes a rename back (see `Repo.undoRename`); rows logged under the new name since stay there. Returns how many rows got the old name back. */
    suspend fun undoRename(entry: RenameEntry): Int {
        val id = activeId.value ?: return 0
        val restored = repo.undoRename(id, entry)
        if (entry.draft) draft?.let { putDraft(it.renamed(entry.to, entry.from)) }
        refreshWidgets(getApplication())
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
        android.util.Log.i("Clean", "Iso-Lateral Row history fixed once for profile $id: ${plan.workouts.size} workouts")
        refreshWidgets(getApplication())
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
            if (healthOn) {
                if (from != date) Health.removeWorkout(getApplication(), id, from)
                Health.writeWorkout(getApplication(), id, date, d.name, seconds)
            }
            refreshWidgets(getApplication())
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
            if (stored != (value != null)) refreshWidgets(getApplication())
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
            if (end != null && end > System.currentTimeMillis()) {
                restEnd = end
                restTotal = total
                countDown()
                storeRest()
                syncAlerts()
            }
        }
    }

    /** Goes to the workout in progress. */
    fun resumeWorkout() {
        stack.removeAll { it == Screen.Active }
        open(Screen.Active)
    }

    fun startWorkout() {
        if (draft == null) putDraft(Draft("Workout", System.currentTimeMillis()))
        open(Screen.Active)
    }

    /** A new workout from a template: its name, exercises and set types; the boxes show the template's values, else each exercise's last session. */
    fun start(template: Template, alsoPaired: List<Boolean> = emptyList()) {
        if (draft != null) {
            clash = Clash(template.name) { start(template, alsoPaired) }
            return
        }
        putDraft(Draft(template.name, System.currentTimeMillis(), template.blocks.mapIndexed { i, b ->
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
        if (workouts.value.any { it.date == date }) {
            message = "A workout already starts at that time"
            return false
        }
        putDraft(Draft("Workout", System.currentTimeMillis(), pastDate = date, pastSeconds = seconds))
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
        if (load == LoadType.CARDIO || (weightKg == null && count == null)) null else SetTarget(weightKg, count?.let { Math.round(it).toInt() })

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
    var sharedFile by mutableStateOf<android.net.Uri?>(null)

    fun templatesFile(list: List<Template> = templates.value): String = JSONObject().put("clean_templates", 1)
        .put("templates", JSONArray(list.map { JSONObject().put("name", it.name).put("exercises", it.blocksJson()).also { o -> if (it.folder.isNotEmpty()) o.put("folder", it.folder) } })).toString(2)

    fun readTemplatesFile(text: String): List<Template> {
        val list = JSONObject(text).getJSONArray("templates")
        return (0 until list.length()).map { i -> list.getJSONObject(i).let { Template(0, it.getString("name"), Template.blocksOf(it.getJSONArray("exercises")), it.optString("folder")) } }
    }

    /** A new card for [exercise], shaped like the last time it was done: same sets and types, values left to fill in. */
    fun addExercise(exercise: String, slot: DraftSlot = liveSlot) {
        val d = slot.draft ?: return
        val last = workouts.value.lastOrNull { w -> w.blocks.any { it.exercise == exercise } }
            ?.blocks?.filter { it.exercise == exercise }?.flatMap { it.sets }.orEmpty()
        val sets = if (last.isEmpty()) List(3) { DraftSet() } else last.map { DraftSet(it.type) }
        val paired = sidesOf(exercise)
        val block = DraftBlock(exercise, sets, paired = paired)
        slot.put(d.copy(blocks = d.blocks + if (paired) block.withPairsCompleted() else block))
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
        val opened = Draft(workout.name, System.currentTimeMillis(), blocks, pastDate = workout.date, pastSeconds = workout.durationSeconds, editOf = workout.date, note = workout.notes).asCorrection()
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
        var order = System.currentTimeMillis()
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
        val now = System.currentTimeMillis()
        val date = d.pastDate ?: strongDate(d.startMillis)
        viewModelScope.launch {
            startRest(0)
            val seconds = d.pastSeconds ?: ((now - d.startMillis) / 1000)
            // Under the lock the draft writer takes, so none of its writes lands between the save and the clearing of `draft`.
            draftLock.withLock {
                repo.saveWorkout(id, date, d.name, seconds, blocks, d.note)
                draft = null
            }
            if (healthOn) Health.writeWorkout(getApplication(), id, date, d.name, seconds)
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

    /** Whether finished workouts and weigh-ins are also written to Health Connect (Settings, Data). */
    private val healthOn get() = options.value["health"] == "on"

    /** Everything logged so far, written to Health Connect; sending something twice replaces it. Returns how many records it took. */
    suspend fun sendHistoryToHealth(): Int {
        val id = activeId.value ?: return 0
        if (!Health.allowed(getApplication())) return 0
        val weighIns = measurements.value.filter { it.file == "weight.csv" }
        // Counted one by one: only what Health Connect took is reported.
        var sent = 0
        for (w in workouts.value) if (Health.writeWorkout(getApplication(), id, w.date, w.name, w.durationSeconds)) sent++
        for (m in weighIns) if (m.value.toDoubleOrNull()?.let { Health.writeWeight(getApplication(), id, m.date, it) } == true) sent++
        return sent
    }

    /** Sets a finished workout's note (only its rows' Workout Notes field changes) and offers to take it back. */
    fun setWorkoutNote(date: String, note: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        if (workouts.value.firstOrNull { it.date == date }?.notes == note) return@launch
        val before = repo.setWorkoutNote(id, date, note) ?: return@launch
        say("Note saved") { viewModelScope.launch { repo.setWorkoutNote(id, date, before) } }
    }

    fun deleteWorkout(date: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val gone = workouts.value.firstOrNull { it.date == date }
        val rows = repo.deleteWorkout(id, date)
        if (healthOn) Health.removeWorkout(getApplication(), id, date)
        refreshWidgets(getApplication())
        // The rows are kept in memory, as they were stored, for the few seconds the delete can be taken back.
        if (rows.isNotEmpty()) say("Workout deleted") {
            viewModelScope.launch {
                if (!repo.restoreWorkout(id, rows)) return@launch
                if (healthOn && gone != null) Health.writeWorkout(getApplication(), id, date, gone.name, gone.durationSeconds)
                refreshWidgets(getApplication())
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
            action == "weight" -> logWeight()
            action == "calendar" -> open(Screen.Calendar())
            action == "week" -> open(Screen.Week(clean.core.weekStart(java.time.LocalDate.now()).toString()))
            action == "last" -> withTimeoutOrNull(3000) { workouts.first { it.isNotEmpty() } }?.lastOrNull()?.let { tab = Tab.Progress; open(Screen.WorkoutDetail(it.date)) }
            // From a link in another app: a tab by its name, or a template by its name.
            // History and Body are pages over Progress now; an unknown name is ignored.
            action.startsWith("tab:") -> when (val name = action.substringAfter(':').lowercase()) {
                "history" -> openHistory(keepTab = false)
                "body" -> openBody(keepTab = false)
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
    suspend fun addMeasurement(file: String, value: Double, date: String = strongDate(System.currentTimeMillis())): Boolean {
        val id = activeId.value ?: return false
        if (measurements.value.any { it.file == file && it.date == date }) {
            message = "There is already an entry at that time"
            return false
        }
        repo.addMeasurement(id, file, date, value)
        if (healthOn && file == "weight.csv") Health.writeWeight(getApplication(), id, date, value)
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
        if (healthOn && entry.file == "weight.csv") {
            if (date != entry.date) Health.removeWeight(getApplication(), entry.profileId, entry.date)
            text.toDoubleOrNull()?.let { Health.writeWeight(getApplication(), entry.profileId, date, it) }
        }
        return true
    }

    fun deleteMeasurement(entry: MeasurementEntity) = viewModelScope.launch {
        dao.deleteMeasurement(entry.id)
        // Put back with the same id, so it takes its old place among entries of the same moment.
        say("Entry deleted") { viewModelScope.launch { dao.insertMeasurements(listOf(entry)) } }
    }

    /** A measurement of the user's own, as one more file beside the ones a Strong export has: `forearm.csv` with the heading `Forearm (cm)`. */
    fun addMeasurementKind(label: String, unit: String) = viewModelScope.launch {
        val id = activeId.value ?: return@launch
        val file = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "measurement" } + ".csv"
        if (files.value.any { it.name == file }) message = "There is already a measurement called $label"
        else dao.insertFiles(listOf(MeasurementFileEntity(profileId = id, name = file, header = if (unit.isEmpty()) label else "$label ($unit)")))
    }
}
