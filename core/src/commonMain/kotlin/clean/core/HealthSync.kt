package clean.core

import clean.core.time.toEpochSecondUtc
import kotlin.coroutines.cancellation.CancellationException

/*
 * What Clean sends to Health Connect, worked out as plain data: the records that should be there, the difference from what was sent
 * last, the notes summary, the overlap trimming, the batches and the words for a failure. The calls to Health Connect itself are in
 * the Android shell (`Health.kt`) and stay thin; everything that can be decided without the phone is decided here and tested.
 */

/** The three kinds of record Clean writes. [prefix] is the start of its `clientRecordId`, which has never changed (records written before are updated in place). */
enum class HealthKind(val prefix: String) { WORKOUT("clean:workout:"), WEIGHT("clean:weight:"), BODY_FAT("clean:bodyfat:") }

/** The id a record is known by in Health Connect: the kind, the profile and the moment it was logged, so sending it again replaces it. */
fun healthId(kind: HealthKind, profile: Long, date: String): String = "${kind.prefix}$profile:$date"

fun healthKindOf(id: String): HealthKind? = HealthKind.entries.firstOrNull { id.startsWith(it.prefix) }

/** The profile an id was written for, or null when the id is not one of Clean's. */
fun healthProfileOf(id: String): Long? = healthKindOf(id)?.let { id.removePrefix(it.prefix).substringBefore(':').toLongOrNull() }

/** The longest title Health Connect takes, and the room kept for the notes. */
const val HEALTH_TITLE_MAX = 100
const val HEALTH_NOTES_MAX = 1000

/** Records per call to Health Connect. */
const val HEALTH_BATCH = 1000

/** The waits, in milliseconds, before each retry of a chunk that failed (a rate limit or a passing fault). */
val HEALTH_BACKOFF: List<Long> = listOf(1_000L, 4_000L, 15_000L)

/** A record to write. [fingerprint] changes whenever anything that is sent changes, so an unchanged record is not sent again. */
sealed interface HealthRecord {
    val id: String
    val kind: HealthKind
    val date: String
    val fingerprint: String
}

/** A workout: [seconds] is the length as it will be sent (never past the start of the next workout). */
data class HealthWorkout(override val id: String, override val date: String, val title: String, val notes: String, val seconds: Long) : HealthRecord {
    override val kind get() = HealthKind.WORKOUT
    override val fingerprint get() = fingerprintOf("$date\n$title\n$notes\n$seconds")
}

data class HealthWeight(override val id: String, override val date: String, val kg: Double) : HealthRecord {
    override val kind get() = HealthKind.WEIGHT
    override val fingerprint get() = fingerprintOf("$date\n$kg")
}

data class HealthBodyFat(override val id: String, override val date: String, val percent: Double) : HealthRecord {
    override val kind get() = HealthKind.BODY_FAT
    override val fingerprint get() = fingerprintOf("$date\n$percent")
}

/** FNV-1a over the text's UTF-16 units, in 64 bits, as hex: stable on every platform (String.hashCode is 32 bits). */
fun fingerprintOf(text: String): String {
    var h = -3750763034362895579L // 0xcbf29ce484222325
    for (c in text) {
        h = h xor c.code.toLong()
        h *= 1099511628211L
    }
    return h.toULong().toString(16)
}

/** The name Health Connect gets: the one the app shows, so a blank name is "Workout". */
fun healthTitle(name: String): String = name.ifBlank { "Workout" }.take(HEALTH_TITLE_MAX)

/**
 * One exercise as a short phrase: `3 × 8 @ 60 kg` when every working set was the same, otherwise `4 sets, top 62.5 kg × 5`
 * (the most reps at the heaviest weight). Warm-ups are left out; an exercise with only warm-ups is left out entirely (null).
 * Sets with no weight are `3 × 12`; timed sets `3 × 45 s`; distance-only sets `1 × 5 km`. Nothing is made up: a set with no
 * reps, time or distance counts as a set and no more.
 */
fun exerciseSummary(name: String, sets: List<SetItem>, unit: String): String? {
    val working = sets.filter { it.type != SetType.WARMUP }
    if (working.isEmpty()) return null
    fun weightText(kg: Double) = (if (unit == "lb") shortNumber(kgToLb(kg)) else shortNumber(kg)) + " " + (if (unit == "lb") "lb" else "kg")
    fun effort(s: SetItem): String? = when {
        s.reps != null -> s.reps.toString()
        s.seconds != null && s.seconds > 0 -> shortNumber(s.seconds) + " s"
        s.distance != null && s.distance > 0 -> shortNumber(s.distance / 1000.0) + " km"
        else -> null
    }
    val n = working.size
    val weights = working.map { it.weight?.takeIf { w -> w > 0 } }
    val efforts = working.map(::effort)
    val sameWeight = weights.distinct().size == 1
    val sameEffort = efforts.distinct().size == 1
    val body = if (sameWeight && sameEffort && efforts[0] != null) {
        "$n × ${efforts[0]}" + (weights[0]?.let { " @ ${weightText(it)}" } ?: "")
    } else {
        val top = weights.filterNotNull().maxOrNull()
        val topEffort = if (top == null) null else working.filter { it.weight == top }.mapNotNull { it.reps }.maxOrNull()?.toString()
        val sets = if (n == 1) "1 set" else "$n sets"
        when {
            top == null -> sets
            topEffort == null -> "$sets, top ${weightText(top)}"
            else -> "$sets, top ${weightText(top)} × $topEffort"
        }
    }
    return "$name $body"
}

/**
 * The workout as a few plain words for Health Connect's notes: the workout's own note first, then each exercise (the same exercise
 * twice is one) as in [exerciseSummary], joined by " · ", in the order done. Cut at [HEALTH_NOTES_MAX] with how many were left out.
 */
fun workoutSummary(w: Workout, unit: String): String {
    val order = ArrayList<String>()
    val sets = HashMap<String, MutableList<SetItem>>()
    for (b in w.blocks) {
        if (b.sets.isEmpty()) continue
        if (b.exercise !in sets) order += b.exercise
        sets.getOrPut(b.exercise) { ArrayList() } += b.sets
    }
    val parts = order.mapNotNull { exerciseSummary(it, sets.getValue(it), unit) }
    val note = w.notes.trim()
    val room = HEALTH_NOTES_MAX - (if (note.isEmpty()) 0 else note.length + 1)
    val kept = ArrayList<String>()
    var used = 0
    for (p in parts) {
        val next = used + p.length + (if (kept.isEmpty()) 0 else 3)
        if (next > room - 16) break
        kept += p
        used = next
    }
    val left = parts.size - kept.size
    val list = kept.joinToString(" · ") + if (left > 0) " · +$left more" else ""
    return listOf(note.take(HEALTH_NOTES_MAX), list).filter { it.isNotEmpty() }.joinToString("\n")
}

/**
 * The lengths the workouts are sent with. A workout whose end would run past the next workout's start is shortened to end one second
 * before it (Health Connect can refuse sessions that overlap); one with no length is sent as a minute (a session must end after it starts).
 * Returns each workout's seconds by its start, in the order of [starts] and [seconds] (both by start time, any order).
 */
fun trimmedSeconds(starts: List<Long>, seconds: List<Long>): List<Long> {
    val order = starts.indices.sortedBy { starts[it] }
    val out = LongArray(starts.size)
    for ((rank, i) in order.withIndex()) {
        var length = seconds[i].coerceAtLeast(60)
        val next = order.getOrNull(rank + 1)?.let { starts[it] }
        if (next != null && starts[i] + length >= next) length = (next - starts[i] - 1).coerceAtLeast(1)
        out[i] = length
    }
    return out.toList()
}

/** A measurement as stored: the file (`weight.csv`), the moment and the value's text. */
data class HealthEntry(val file: String, val date: String, val value: String)

/** Every record the profile's data should have in Health Connect: its workouts (with summaries and trimmed lengths), weigh-ins and body fat. */
fun desiredHealth(profile: Long, unit: String, workouts: List<Workout>, entries: List<HealthEntry>): List<HealthRecord> {
    val dated = workouts.mapNotNull { w -> parseStrongDate(w.date)?.let { w to it.toEpochSecondUtc() } }
    val lengths = trimmedSeconds(dated.map { it.second }, dated.map { it.first.durationSeconds })
    val out = LinkedHashMap<String, HealthRecord>()
    for ((i, p) in dated.withIndex()) {
        val w = p.first
        val id = healthId(HealthKind.WORKOUT, profile, w.date)
        out.getOrPut(id) { HealthWorkout(id, w.date, healthTitle(w.name), workoutSummary(w, unit), lengths[i]) }
    }
    for (e in entries) {
        val v = e.value.toDoubleOrNull() ?: continue
        if (parseStrongDate(e.date) == null) continue
        when (e.file) {
            "weight.csv" -> if (v > 0 && v <= 1000) healthId(HealthKind.WEIGHT, profile, e.date).let { out.getOrPut(it) { HealthWeight(it, e.date, v) } }
            "body_fat_percentage.csv" -> if (v > 0 && v <= 100) healthId(HealthKind.BODY_FAT, profile, e.date).let { out.getOrPut(it) { HealthBodyFat(it, e.date, v) } }
        }
    }
    return out.values.toList()
}

/** What to do to bring Health Connect in line: [write] what is missing or changed, [remove] (by id) what Clean wrote that no longer exists. */
data class HealthPlan(val write: List<HealthRecord>, val remove: List<String>) {
    val isEmpty get() = write.isEmpty() && remove.isEmpty()
}

/**
 * The difference between [desired] and what was sent ([ledger]: id to fingerprint). Records of the [active] profile and of any profile
 * not in [profiles] (a deleted one) that are in the ledger but not wanted are removed; other profiles' records are left alone.
 * With [force] everything wanted is written again.
 */
fun planHealth(desired: List<HealthRecord>, ledger: Map<String, String>, active: Long, profiles: Set<Long>, force: Boolean = false): HealthPlan {
    val wanted = desired.mapTo(HashSet()) { it.id }
    val write = desired.filter { force || ledger[it.id] != it.fingerprint }
    val remove = ledger.keys.filter { id ->
        id !in wanted && healthProfileOf(id).let { p -> p != null && (p == active || p !in profiles) }
    }.sorted()
    return HealthPlan(write, remove)
}

/** The ledger as text, one `id<TAB>fingerprint` per line, and back. Lines that are not that are dropped. */
fun encodeLedger(ledger: Map<String, String>): String = ledger.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" }
fun decodeLedger(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (line in text.lineSequence()) {
        val at = line.lastIndexOf('\t')
        if (at > 0 && at < line.length - 1) out[line.substring(0, at)] = line.substring(at + 1)
    }
    return out
}

/** Why a call to Health Connect failed, in the words Settings shows after "Health Connect refused:". */
enum class HealthFailureKind(val words: String) { PERMISSION("permission removed"), RATE_LIMIT("rate limited"), OTHER("failed") }

data class HealthFailure(val kind: HealthFailureKind, val detail: String = "") {
    val text: String get() = if (kind == HealthFailureKind.OTHER && detail.isNotEmpty()) detail.take(60) else kind.words
}

/** Sorts an exception from Health Connect by its class name and message (a missing permission, a rate limit, anything else). */
fun classifyHealthFailure(className: String, message: String?): HealthFailure {
    val m = (message ?: "").lowercase()
    val c = className.lowercase()
    return when {
        "securityexception" in c || "permission" in m -> HealthFailure(HealthFailureKind.PERMISSION)
        "ratelimit" in c || "rate limit" in m || "rate_limit" in m || "quota" in m || "too many" in m -> HealthFailure(HealthFailureKind.RATE_LIMIT)
        else -> HealthFailure(HealthFailureKind.OTHER, message ?: className.substringAfterLast('.'))
    }
}

/** Items that went through, how many did not, and why the first failure happened (null when all went through). */
class ChunkOutcome<T>(val done: List<T>, val failed: Int, val failure: HealthFailure?)

/**
 * Runs [call] over [items] in chunks of [size]. A chunk that fails is tried again after each wait in [backoff] ([pause] does the
 * waiting), alone: chunks that went through are never sent twice. A missing permission is not retried and stops the run; a rate
 * limit that outlasts the waits also stops it; any other fault leaves that chunk and goes on. Cancellation is never swallowed.
 */
suspend fun <T> runHealthChunks(
    items: List<T>, size: Int = HEALTH_BATCH, backoff: List<Long> = HEALTH_BACKOFF, pause: suspend (Long) -> Unit, call: suspend (List<T>) -> Unit,
): ChunkOutcome<T> {
    val done = ArrayList<T>()
    var failure: HealthFailure? = null
    var failed = 0
    var stopped = false
    for (chunk in items.chunked(size)) {
        if (stopped) { failed += chunk.size; continue }
        var attempt = 0
        while (true) {
            try {
                call(chunk)
                done += chunk
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val why = classifyHealthFailure(e::class.simpleName ?: "", e.message)
                if (why.kind != HealthFailureKind.PERMISSION && attempt < backoff.size) {
                    pause(backoff[attempt++])
                    continue
                }
                if (failure == null) failure = why
                failed += chunk.size
                if (why.kind != HealthFailureKind.OTHER) stopped = true
                break
            }
        }
    }
    return ChunkOutcome(done, failed, failure)
}

/** What was asked of Health Connect. */
class HealthRequest(val write: List<HealthRecord>, val remove: List<String>)

/** What it did: the ids written and removed (to update the ledger), how many were not, why, and which kinds it was not allowed to touch. */
data class HealthReport(
    val written: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val failed: Int = 0,
    val failure: HealthFailure? = null,
    val notAllowed: Set<HealthKind> = emptySet(),
)

/** The last sync in one honest line: "Sent 3, removed 1", "Up to date", "Health Connect refused: permission removed". */
fun healthReportText(r: HealthReport): String {
    val sent = r.written.size
    val gone = r.removed.size
    val done = buildList {
        if (sent > 0) add("sent $sent")
        if (gone > 0) add("removed $gone")
    }.joinToString(", ")
    val head = done.replaceFirstChar { it.uppercase() }
    val skipped = if (r.notAllowed.isEmpty()) "" else "not allowed: " + r.notAllowed.joinToString(", ") { it.label }
    val f = r.failure
    val line = when {
        f != null && done.isEmpty() -> (if (f.kind == HealthFailureKind.OTHER) "Health Connect failed: " else "Health Connect refused: ") + f.text
        f != null -> "$head; ${r.failed} not sent: ${f.text}"
        done.isEmpty() -> "Up to date"
        else -> head
    }
    return if (skipped.isEmpty()) line else "$line ($skipped)"
}

val HealthKind.label: String get() = when (this) { HealthKind.WORKOUT -> "workouts"; HealthKind.WEIGHT -> "weight"; HealthKind.BODY_FAT -> "body fat" }
