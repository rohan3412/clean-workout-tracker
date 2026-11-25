package clean.core

import kotlinx.datetime.isoDayNumber
import clean.core.time.*
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.datetime.LocalDate

/** Stored as an exercise's overall exercise when the user has taken it out of its group. */
const val NO_GROUP = "-"

/**
 * The overall exercise [exercise] is a variation of: what the user stored for it ([stored] gives that), else
 * the library's grouping, else itself.
 */
fun overallName(exercise: String, stored: (String) -> String?): String = when (val own = stored(exercise).orEmpty()) {
    "" -> libraryOverall(exercise) ?: exercise
    NO_GROUP -> exercise
    else -> own
}

// --- Which template is next ---

/** A template as the rotation sees it. */
data class TemplateRef(val id: Long, val name: String, val folder: String)

/** The template that comes next, why, and whether it was picked from the current split folder. */
data class NextTemplate(val template: TemplateRef, val reason: String, val fromSplit: Boolean)

internal fun agoText(day: String, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(LocalDate.parse(day), today)) {
    0L -> "today"
    1L -> "yesterday"
    else -> "$days days ago"
}

/**
 * The template that has waited longest. The templates in the folder [split] are the rotation: the one never done,
 * else the one done longest ago. With that folder empty it is every template that has been used. A template is
 * "done" when a workout carries its name. [skipped] templates are passed over (the whole rotation again if that leaves none).
 */
fun nextTemplate(templates: List<TemplateRef>, workouts: List<Workout>, split: String, today: LocalDate, skipped: Set<Long> = emptySet()): NextTemplate? {
    val lastDone = workouts.associate { it.name to it.day }
    val inSplit = templates.filter { it.folder.equals(split, ignoreCase = true) }
    val pool = inSplit.ifEmpty { templates.filter { it.name in lastDone } }
    val open = pool.filter { it.id !in skipped }.ifEmpty { pool }
    val next = open.minByOrNull { lastDone[it.name] ?: "" } ?: return null
    return NextTemplate(next, lastDone[next.name]?.let { "last done ${agoText(it, today)}" } ?: "never done", inSplit.isNotEmpty())
}

// --- The program ---

/** How much of a training day's exercises may differ (added or removed) and still be that day. */
const val PROGRAM_MARGIN = 0.10

/** The margin never rounds below this many exercises, so a short day can still lose or gain one (ours, not the user's 10%). */
const val PROGRAM_MIN_CHANGES = 1

/** The share of a day's exercises that must be in the workout, so a margin on a small day cannot turn it into a different one (ours). */
const val PROGRAM_MIN_KEPT = 0.5

/** A training day: [label] is the template's name when the plan was saved, [exercises] its distinct overall exercises then. */
data class ProgramDay(val label: String, val templateId: Long, val exercises: List<String>)

/** Seven slots, Monday first, a day or null for rest; in force from the Monday [from]. */
data class ProgramVersion(val from: LocalDate, val days: List<ProgramDay?>)

/** 0.30000000000000004 must not round up. */
private fun ceilOf(x: Double): Int = ceil(x - 1e-9).toInt()

/** How a workout's distinct overall exercises compare with a training day's. */
data class DayMatch(val kept: Int, val added: Int, val removed: Int, val n: Int) {
    val different: Int get() = max(added, removed)
    val matches: Boolean get() = different <= max(PROGRAM_MIN_CHANGES, ceilOf(PROGRAM_MARGIN * n)) && kept >= ceilOf(PROGRAM_MIN_KEPT * n)
}

fun matchDay(day: ProgramDay, overalls: Set<String>): DayMatch {
    val planned = day.exercises.toSet()
    val kept = planned.count { it in overalls }
    return DayMatch(kept, overalls.size - kept, planned.size - kept, planned.size)
}

/** The distinct overall exercises of a workout: variations are one, a card split into runs counts once, a card with no set is nothing. */
fun workoutOveralls(workout: Workout, overallOf: (String) -> String): Set<String> =
    workout.blocks.filter { it.sets.isNotEmpty() }.mapTo(LinkedHashSet()) { overallOf(it.exercise) }

enum class SlotState { HIT, MOVED, MISSED, REST_KEPT, REST_TRAINED, UPCOMING }

/** One day of a week: its date, the planned [day] (null for rest), how it went, and the workout that met it with how it differed. */
data class SlotResult(val slot: Int, val date: LocalDate, val day: ProgramDay?, val state: SlotState, val workout: String?, val match: DayMatch?)

data class ProgramWeek(val monday: LocalDate, val slots: List<SlotResult>, val met: Boolean, val current: Boolean, val metOn: LocalDate?) {
    fun toMet() = WeekMet(monday, met, current, metOn)
}

private class Candidate(val date: String, val day: LocalDate, val overalls: Set<String>)

private class Assignment(val workoutOf: IntArray, val matched: Int, val score: Int, val same: Int)

/**
 * Matches training days to different workouts, as many as possible; among equals the best overlap, then the most
 * days done on their own weekday. Few days and few workouts, so it simply tries them all.
 */
private fun assign(days: List<ProgramDay?>, monday: LocalDate, workouts: List<Candidate>): Assignment {
    val slots = days.indices.filter { days[it] != null }
    val fits = Array(days.size) { s -> days[s]?.let { d -> Array(workouts.size) { w -> matchDay(d, workouts[w].overalls).takeIf { it.matches } } } }
    var best = Assignment(IntArray(days.size) { -1 }, -1, 0, 0)
    val current = IntArray(days.size) { -1 }
    val used = BooleanArray(workouts.size)
    fun go(k: Int, matched: Int, score: Int, same: Int) {
        if (matched + (slots.size - k) < best.matched) return
        if (k == slots.size) {
            val better = matched > best.matched || matched == best.matched && (score > best.score || score == best.score && same > best.same)
            if (better) best = Assignment(current.copyOf(), matched, score, same)
            return
        }
        val s = slots[k]
        for (w in workouts.indices) {
            val m = fits[s]?.get(w) ?: continue
            if (used[w]) continue
            used[w] = true
            current[s] = w
            go(k + 1, matched + 1, score + m.kept - m.added - m.removed, same + if (workouts[w].day == monday.plusDays(s.toLong())) 1 else 0)
            current[s] = -1
            used[w] = false
        }
        go(k + 1, matched, score, same)
    }
    go(0, 0, 0, 0)
    return best
}

private fun evaluateWeek(monday: LocalDate, days: List<ProgramDay?>, inWeek: List<Candidate>, today: LocalDate): ProgramWeek {
    val a = assign(days, monday, inWeek)
    val current = monday == weekStart(today)
    val slots = days.indices.map { s ->
        val date = monday.plusDays(s.toLong())
        val day = days[s]
        if (day == null) {
            val state = when {
                date > today -> SlotState.UPCOMING
                inWeek.any { it.day == date } -> SlotState.REST_TRAINED
                else -> SlotState.REST_KEPT
            }
            return@map SlotResult(s, date, null, state, null, null)
        }
        val w = a.workoutOf[s].takeIf { it >= 0 }?.let { inWeek[it] }
        when {
            w != null -> SlotResult(s, date, day, if (w.day == date) SlotState.HIT else SlotState.MOVED, w.date, matchDay(day, w.overalls))
            date >= today && current -> SlotResult(s, date, day, SlotState.UPCOMING, null, null)
            else -> SlotResult(s, date, day, SlotState.MISSED, null, null)
        }
    }
    val training = slots.filter { it.day != null }
    val met = training.isNotEmpty() && training.all { it.workout != null }
    return ProgramWeek(monday, slots, met, current, if (met) training.maxOf { LocalDate.parse(it.workout!!.take(10)) } else null)
}

private fun candidatesByWeek(workouts: List<Workout>, overallOf: (String) -> String): Map<LocalDate, List<Candidate>> {
    val names = HashMap<String, String>()
    val of = { name: String -> names.getOrPut(name) { overallOf(name) } }
    return workouts.sortedBy { it.date }.groupBy({ weekStart(LocalDate.parse(it.day)) }) { Candidate(it.date, LocalDate.parse(it.day), workoutOveralls(it, of)) }
}

/** The version in force in the week of [monday]: the latest dated on or before it; null before the first. */
fun versionFor(versions: List<ProgramVersion>, monday: LocalDate): ProgramVersion? = versions.filter { it.from <= monday }.maxByOrNull { it.from }

/**
 * Every week from the first version's to the current one, oldest first, each against the version in force in it. A week is
 * on program when every training day is met by a different workout of that Monday to Sunday week, on any day, in any order.
 * Only the snapshots in [versions] and the workouts are read, so a template edited or deleted later re-scores nothing.
 */
fun programWeeks(versions: List<ProgramVersion>, workouts: List<Workout>, today: LocalDate, overallOf: (String) -> String): List<ProgramWeek> {
    val first = versions.minOfOrNull { it.from } ?: return emptyList()
    val thisWeek = weekStart(today)
    val byWeek = candidatesByWeek(workouts, overallOf)
    val weeks = ArrayList<ProgramWeek>()
    var monday = weekStart(first)
    while (monday <= thisWeek) {
        weeks += evaluateWeek(monday, versionFor(versions, monday)!!.days, byWeek[monday].orEmpty(), today)
        monday = monday.plusWeeks(1)
    }
    return weeks
}

/** The program's weeks with the streak and the badges read from them by the same code as the weekly target's. */
class ProgramStanding(val weeks: List<ProgramWeek>, val streak: Streak, val awards: List<Award>) {
    val now: ProgramWeek? get() = weeks.lastOrNull()
}

fun programStanding(versions: List<ProgramVersion>, workouts: List<Workout>, today: LocalDate, overallOf: (String) -> String): ProgramStanding {
    val weeks = programWeeks(versions, workouts, today, overallOf)
    val met = weeks.map { it.toMet() }
    val streak = replayStreak(met)
    return ProgramStanding(weeks, streak, programBadges(met, streak))
}

/**
 * The earliest Monday from which these [days] would have been on program in every complete week up to last week
 * (so a streak can start there), or null when last week was not.
 */
fun firstFittingWeek(days: List<ProgramDay?>, workouts: List<Workout>, today: LocalDate, overallOf: (String) -> String): LocalDate? {
    val earliest = workouts.minOfOrNull { weekStart(LocalDate.parse(it.day)) } ?: return null
    val byWeek = candidatesByWeek(workouts, overallOf)
    var monday = weekStart(today).minusWeeks(1)
    var found: LocalDate? = null
    while (monday >= earliest && evaluateWeek(monday, days, byWeek[monday].orEmpty(), today).met) {
        found = monday
        monday = monday.minusWeeks(1)
    }
    return found
}

// --- A first plan ---

/** A template as the pre-fill sees it: [exercises] are its distinct overall exercises. */
data class SuggestTemplate(val ref: TemplateRef, val exercises: List<String>)

/** How many complete weeks before this one the pre-fill looks at (ours). */
const val SUGGEST_WEEKS = 8

/** Monday, Wednesday, Friday, then Tuesday, Thursday, Saturday: where the fallback lays the split's templates (slot 0 is Monday). */
private val SPREAD = listOf(0, 2, 4, 1, 3, 5)

/**
 * The template id for each weekday of a first plan (Monday first, null for rest), read from the last [SUGGEST_WEEKS] complete
 * weeks. A weekday trains when it was trained in at least half of the weeks that had any workout; its template is the one named
 * like the workout done most often that day (ties to the most recent), else the one that best overlaps that day's latest
 * workout provided it would [DayMatch.matches], else rest. With no training day at all: the [split] folder's templates in
 * [nextTemplate] order on [SPREAD], and with none, a week of rest.
 */
fun suggestedWeek(workouts: List<Workout>, templates: List<SuggestTemplate>, split: String, today: LocalDate, overallOf: (String) -> String): List<Long?> {
    val first = weekStart(today).minusWeeks(SUGGEST_WEEKS.toLong())
    val thisWeek = weekStart(today)
    val recent = workouts.filter { weekStart(LocalDate.parse(it.day)).let { m -> m >= first && m < thisWeek } }
    val active = recent.map { weekStart(LocalDate.parse(it.day)) }.distinct().size
    val byDay = (0..6).map { d -> recent.filter { LocalDate.parse(it.day).dayOfWeek.isoDayNumber - 1 == d } }
    val week = (0..6).map { d ->
        val here = byDay[d]
        if (active == 0 || here.map { weekStart(LocalDate.parse(it.day)) }.distinct().size * 2 < active) return@map null
        val name = here.groupBy { it.name }.entries.maxWithOrNull(compareBy<Map.Entry<String, List<Workout>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.date } })!!.key
        templates.firstOrNull { it.ref.name == name }?.ref?.id ?: run {
            val latest = here.maxBy { it.date }
            val overalls = workoutOveralls(latest, overallOf)
            templates.map { it to matchDay(ProgramDay(it.ref.name, it.ref.id, it.exercises), overalls) }.filter { it.second.matches }
                .maxWithOrNull(compareBy<Pair<SuggestTemplate, DayMatch>> { it.second.kept - it.second.added - it.second.removed }.thenBy { it.second.kept })?.first?.ref?.id
        }
    }
    if (week.any { it != null }) return week
    val refs = templates.map { it.ref }.filter { it.folder.equals(split, ignoreCase = true) }
    val out = ArrayList<Long>()
    while (out.size < SPREAD.size) {
        val next = nextTemplate(refs, workouts, split, today, out.toSet())?.template ?: break
        if (next.id in out) break
        out += next.id
    }
    return List(7) { slot -> SPREAD.indexOf(slot).takeIf { it in out.indices }?.let { out[it] } }
}

// --- Today ---

enum class PickKind { TODAY, EARLIER, LATER, NEXT_WEEK }

/** A training day that could be started now. [kind]: today's own, an earlier day of this week not yet done, a later one, or next week's first. */
data class Pick(val day: ProgramDay, val slot: Int, val date: LocalDate, val kind: PickKind)

/**
 * What today is on the program: [today] its slot, [done] whether that training day is already met, and [candidates]
 * the training days still to do, in the order to offer them: today's, earlier ones of this week, later ones, else next week's first.
 */
class TodayPlan(val today: SlotResult, val done: Boolean, val candidates: List<Pick>)

fun todayPlan(versions: List<ProgramVersion>, workouts: List<Workout>, today: LocalDate, overallOf: (String) -> String): TodayPlan? {
    val monday = weekStart(today)
    val version = versionFor(versions, monday) ?: return null
    val week = evaluateWeek(monday, version.days, candidatesByWeek(workouts.filter { it.day >= monday.toString() }, overallOf)[monday].orEmpty(), today)
    val slot = week.slots[today.dayOfWeek.isoDayNumber - 1]
    val open = week.slots.filter { it.day != null && it.workout == null }
    fun pick(s: SlotResult, kind: PickKind) = Pick(s.day!!, s.slot, s.date, kind)
    val list = open.filter { it.date == today }.map { pick(it, PickKind.TODAY) } +
        open.filter { it.date < today }.map { pick(it, PickKind.EARLIER) } +
        open.filter { it.date > today }.map { pick(it, PickKind.LATER) }
    val ahead = if (list.isNotEmpty()) list else {
        val next = monday.plusWeeks(1)
        versionFor(versions, next)?.days?.withIndex()?.firstOrNull { it.value != null }?.let { listOf(Pick(it.value!!, it.index, next.plusDays(it.index.toLong()), PickKind.NEXT_WEEK)) }.orEmpty()
    }
    return TodayPlan(slot, slot.day != null && slot.workout != null, ahead)
}

// --- A finished workout ---

/** What a finished workout did for the program: the day it met, how many exercises differed, whether it completed the week, and the badges it earned. */
class ProgramOutcome(val label: String, val different: Int, val weekCompleted: Boolean, val awards: List<Award>, val monday: LocalDate)

fun programOutcome(versions: List<ProgramVersion>, workouts: List<Workout>, workout: Workout, today: LocalDate, overallOf: (String) -> String): ProgramOutcome? {
    val monday = weekStart(LocalDate.parse(workout.day))
    val with = programStanding(versions, workouts, today, overallOf)
    val week = with.weeks.firstOrNull { it.monday == monday } ?: return null
    val slot = week.slots.firstOrNull { it.workout == workout.date } ?: return null
    val without = programStanding(versions, workouts.filter { it.date != workout.date }, today, overallOf)
    val had = without.awards.filter { it.earned != null }.mapTo(HashSet()) { it.id }
    // The week is completed by the last of the workouts that met it.
    val completed = week.met && week.slots.mapNotNull { it.workout }.max() == workout.date
    return ProgramOutcome(slot.day!!.label, slot.match?.different ?: 0, completed, with.awards.filter { it.earned != null && it.id !in had }, monday)
}

// --- Saying how long ---

/** A streak of weeks in years, months and weeks, kept in step with the badge ladder (4, 13, 26 and 52 weeks). */
data class Span(val years: Int, val months: Int, val weeks: Int)

fun spanOf(weeks: Int): Span {
    // Twelve months to 52 weeks, so it says 3, 6 and 12 months exactly where the ladder does; the + 4 makes the fourth week the first month.
    val months = (weeks * 12 + 4) / 52
    return Span(months / 12, months % 12, weeks - jRound(months * 52 / 12.0).toInt())
}

/** `1 year, 3 months, 1 week`; zero parts are left out, and 0 weeks is "0 weeks". */
fun spanText(weeks: Int): String {
    val s = spanOf(weeks)
    fun part(n: Int, word: String) = if (n == 0) null else "$n $word${if (n == 1) "" else "s"}"
    return listOfNotNull(part(s.years, "year"), part(s.months, "month"), part(s.weeks, "week")).joinToString(", ").ifEmpty { "0 weeks" }
}

/** Whole months for "about N months": [Span] without the odd weeks. */
fun approxMonths(weeks: Int): Int = spanOf(weeks).let { it.years * 12 + it.months }

// --- Storing it ---

private fun escape(s: String) = buildString {
    for (c in s) when (c) {
        '\\' -> append("\\\\")
        '|' -> append("\\p")
        ';' -> append("\\s")
        ',' -> append("\\c")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        else -> append(c)
    }
}

private fun unescape(s: String) = buildString {
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length) {
            append(when (val c = s[i + 1]) { 'p' -> '|'; 's' -> ';'; 'c' -> ','; 'n' -> '\n'; 'r' -> '\r'; else -> c })
            i += 2
        } else {
            append(s[i])
            i++
        }
    }
}

/**
 * `2026-10-12|Push,12,Bench Press,Fly||Rest...;2026-11-02|...`: versions with `;`, a version's date and seven days with `|`,
 * a day's label, template id and exercises with `,` (a rest day is empty). `\` escapes `\ | ; ,` and line breaks inside names.
 */
fun encodeProgram(versions: List<ProgramVersion>): String = versions.sortedBy { it.from }.joinToString(";") { v ->
    (listOf(v.from.toString()) + v.days.map { d -> d?.let { (listOf(escape(it.label), it.templateId.toString()) + it.exercises.map(::escape)).joinToString(",") }.orEmpty() }).joinToString("|")
}

/** The versions [encodeProgram] wrote, oldest first; one that cannot be read is dropped, and a later one dated like an earlier one replaces it. */
fun decodeProgram(text: String?): List<ProgramVersion> = text.orEmpty().split(';').mapNotNull { part ->
    runCatching {
        val fields = part.split('|')
        require(fields.size == 8)
        ProgramVersion(LocalDate.parse(fields[0]), fields.drop(1).map { field ->
            if (field.isEmpty()) null else field.split(',').let { f -> ProgramDay(unescape(f[0]), f[1].toLong(), f.drop(2).map(::unescape)) }
        })
    }.getOrNull()
}.associateBy { it.from }.values.sortedBy { it.from }

/** [versions] with [version] added; one already dated the same Monday is replaced, so no earlier week is re-scored. */
fun withVersion(versions: List<ProgramVersion>, version: ProgramVersion): List<ProgramVersion> =
    (versions.filter { it.from != version.from } + version).sortedBy { it.from }
