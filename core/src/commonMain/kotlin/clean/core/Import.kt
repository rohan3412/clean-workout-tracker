package clean.core

import clean.core.time.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/*
 * Reading the exports of other apps (docs/other-formats.md). Everything here ends as rows in Clean's own
 * format through rowsFor, so the rest of the app needs to know nothing about where a workout came from.
 * Strong's own file never comes through here: WorkoutCsv reads it exactly. Written from the column names
 * and set-kind words the apps' files are known by; none of it has been tried on a real export yet.
 */

enum class ImportSource(val label: String) {
    STRONG("Strong export"), HEVY("Hevy export"), LYFTA("Lyfta export"), FITBOD("Fitbod export"), FITNOTES("FitNotes CSV"),
    GYMMANE("GymMane CSV"), OPENGYM("openGym backup"), GENERIC("A CSV file"), UNKNOWN("Unknown file"),
}

class ForeignSet(
    val type: SetType, val weightKg: Double?, val reps: Int?, val seconds: Double?, val distanceMeters: Double?,
    /** `6` to `10` in half steps as Clean writes it, or empty. */
    val rpe: String,
)

/** One exercise's run of sets, with the notes that go before them. */
class ForeignBlock(val exercise: String, val notes: List<String>, val sets: List<ForeignSet>)

class ForeignWorkout(val start: LocalDateTime, val name: String, val durationSeconds: Long, val blocks: List<ForeignBlock>)

/** A weigh-in: [date] in Strong's format, [kg] as a number. */
class ForeignWeight(val date: String, val kg: Double)

/** [skipped] maps a reason to how many rows (or sets, or workouts) were left out for it. */
class ForeignImport(val source: ImportSource, val workouts: List<ForeignWorkout>, val weights: List<ForeignWeight>, val skipped: Map<String, Int>)

/** A file the user picked, found to be something Clean can read. [bytes] are exactly the file's (out of a zip if it was in one). */
class ImportFile(val source: ImportSource, val bytes: ByteArray) {
    val text: String get() = decodeUtf8(bytes)
}

/**
 * The file in [bytes], or the single recognisable file inside it when it is a zip (Hevy and Strong ship zips).
 * Throws with a line to show the user when there is none or more than one.
 */
fun readImportFile(bytes: ByteArray): ImportFile {
    val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    if (!isZip) {
        val source = detectFormat(decodeUtf8(bytes))
        if (source == ImportSource.UNKNOWN) {
            throw CsvFormatException("Clean does not know this file. It needs a date, an exercise and reps in columns, or to be an export from Strong, Hevy, Lyfta, Fitbod, FitNotes, GymMane or openGym.")
        }
        return ImportFile(source, bytes)
    }
    val found = mutableListOf<ImportFile>()
    for (entry in readZipItems(bytes) { it.endsWith(".csv", true) || it.endsWith(".json", true) }) {
        val data = entry.bytes
        val source = runCatching { detectFormat(decodeUtf8(data)) }.getOrDefault(ImportSource.UNKNOWN)
        if (source != ImportSource.UNKNOWN) found += ImportFile(source, data)
    }
    return when (found.size) {
        1 -> found[0]
        0 -> throw CsvFormatException("That zip holds no file Clean knows how to read")
        else -> throw CsvFormatException("That zip holds ${found.size} files Clean could read; pick the one you want out of it")
    }
}

// --- A tolerant CSV reader ---

/** Records of a foreign CSV: `,` or `;` between fields (whichever the first line uses more), quotes doubled, any line ending, a BOM ignored. Cell text is kept exactly. */
internal fun readCsv(text: String, limit: Int = Int.MAX_VALUE): List<List<String>> {
    val s = text.removePrefix("﻿")
    var commas = 0
    var semicolons = 0
    var quoted = false
    for (c in s) {
        if (c == '"') quoted = !quoted
        else if (!quoted && (c == '\n' || c == '\r')) break
        else if (!quoted && c == ',') commas++
        else if (!quoted && c == ';') semicolons++
    }
    val separator = if (semicolons > commas) ';' else ','
    val records = mutableListOf<List<String>>()
    var fields = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    var inQuotes = false
    var pending = false // something has been read since the last line ended
    fun endCell() { fields += cell.toString(); cell.setLength(0) }
    fun endLine() {
        if (pending) { endCell(); records += fields }
        fields = mutableListOf()
        pending = false
    }
    while (i < s.length && records.size < limit) {
        val c = s[i++]
        when {
            inQuotes && c == '"' -> if (s.getOrNull(i) == '"') { cell.append('"'); i++ } else inQuotes = false
            inQuotes -> cell.append(c)
            c == '"' -> { inQuotes = true; pending = true }
            c == separator -> { endCell(); pending = true }
            c == '\r' -> { if (s.getOrNull(i) == '\n') i++; endLine() }
            c == '\n' -> endLine()
            else -> { cell.append(c); pending = true }
        }
    }
    if (records.size < limit) endLine()
    return records
}

private class Table(val header: List<String>, val rows: List<List<String>>) {
    /** The first column whose heading is one of [names] (in the order of [names]), or -1. */
    fun col(vararg names: String): Int {
        for (n in names) header.indexOf(n).let { if (it >= 0) return it }
        return -1
    }
}

private fun tableOf(text: String, limit: Int = Int.MAX_VALUE): Table? {
    val records = readCsv(text, limit)
    if (records.isEmpty()) return null
    return Table(records[0].map { it.trim().lowercase() }, records.drop(1))
}

// --- Column names ---

private val DATE_COLUMNS = arrayOf("date", "fecha", "datum", "data", "day", "dia", "timestamp", "start_time", "time")
private val EXERCISE_COLUMNS = arrayOf("exercise", "exercise_title", "exercise name", "exercise_name", "ejercicio", "übung", "esercizio", "exercice", "name")
private val REPS_COLUMNS = arrayOf("reps", "rep", "repetitions", "repeticiones", "wiederholungen", "répétitions", "ripetizioni")
private val KG_COLUMNS = arrayOf("weight_kg", "weight (kg)", "weight kg", "weight(kg)")
private val LB_COLUMNS = arrayOf("weight_lbs", "weight (lbs)", "weight (lb)", "weight lbs", "weight lb", "weight_lb", "weight(lbs)", "weight(lb)")
private val PLAIN_WEIGHT_COLUMNS = arrayOf("weight", "peso", "gewicht", "poids")
private val WORKOUT_COLUMNS = arrayOf("workout", "workout name", "workout_name", "routine", "plan", "training", "title")
private val SECONDS_COLUMNS = arrayOf("duration_seconds", "duration_s", "seconds")
private val NOTE_COLUMNS = arrayOf("exercise_notes", "comment", "notes", "note")

/** Which app's file this text is, by the names in its first line (docs/other-formats.md). */
fun detectFormat(text: String): ImportSource {
    val t = text.removePrefix("﻿")
    if (t.trimStart().startsWith("{")) {
        val json = runCatching { Json.parse(t) as? Map<*, *> }.getOrNull()
        return if (json?.get("workouts") is List<*> && json["routines"] is List<*>) ImportSource.OPENGYM else ImportSource.UNKNOWN
    }
    val h = (tableOf(t, 1) ?: return ImportSource.UNKNOWN).header.toSet()
    fun has(vararg names: String) = names.all { it in h }
    return when {
        has("exercise name", "set order") -> ImportSource.STRONG
        has("exercise_title", "start_time") -> ImportSource.HEVY
        has("exercise", "set type", "title") && ("rir/rpe" in h || h.any { it.startsWith("recordlevel") }) -> ImportSource.LYFTA
        has("exercise", "reps", "iswarmup") -> ImportSource.FITBOD
        has("weight_kg") && ("est_1rm_kg" in h || "volume_kg" in h) -> ImportSource.GYMMANE
        has("exercise", "category") && h.any { it.startsWith("weight") } -> ImportSource.FITNOTES
        DATE_COLUMNS.any { it in h } && EXERCISE_COLUMNS.any { it in h } && REPS_COLUMNS.any { it in h } -> ImportSource.GENERIC
        else -> ImportSource.UNKNOWN
    }
}

/** Whether the weights in this file have no unit, so the user must say kg or lb. */
fun needsUnit(text: String): Boolean {
    val source = detectFormat(text)
    if (source == ImportSource.STRONG || source == ImportSource.OPENGYM || source == ImportSource.UNKNOWN) return false
    val t = tableOf(text, 1) ?: return false
    return t.col(*KG_COLUMNS) < 0 && t.col(*LB_COLUMNS) < 0 && t.col(*PLAIN_WEIGHT_COLUMNS) >= 0
}

// --- Reading values ---

private fun number(s: String?): Double? {
    val t = s?.trim() ?: return null
    if (t.isEmpty()) return null
    return (if ('.' !in t) t.replace(',', '.') else t).toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** `1:05:00`, `65:00`, `1h 5m`, `3905` or `45.5` as seconds. */
private fun clockSeconds(s: String?): Double? {
    val t = s?.trim()?.lowercase() ?: return null
    if (t.isEmpty()) return null
    if (t.any { it == 'h' || it == 'm' || it == 's' }) {
        val parts = Regex("(\\d+(?:[.,]\\d+)?)\\s*([hms])").findAll(t).toList()
        if (parts.isEmpty()) return null
        return parts.sumOf { m ->
            val unit = when (m.groupValues[2]) {
                "h" -> 3600
                "m" -> 60
                else -> 1
            }
            number(m.groupValues[1])!! * unit
        }
    }
    if (':' in t) {
        val p = t.split(':').map { number(it) ?: return null }
        return when (p.size) {
            3 -> p[0] * 3600 + p[1] * 60 + p[2]
            2 -> p[0] * 60 + p[1]
            else -> null
        }
    }
    return number(t)
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/** A date, and whether it carried a time of day: `2026-10-12 18:00:00`, `2026-10-12T18:00`, `2026-10-12`, `12 Oct 2026, 18:00`. */
private fun foreignDate(s: String): Pair<LocalDateTime, Boolean>? {
    val t = s.trim()
    fun make(y: Int, mo: Int, d: Int, h: String, mi: String, sec: String): Pair<LocalDateTime, Boolean>? = runCatching {
        val day = LocalDate.of(y, mo, d)
        if (h.isEmpty()) day.atStartOfDay() to false else day.atTime(h.toInt(), mi.toInt(), sec.takeIf { it.isNotEmpty() }?.toInt() ?: 0) to true
    }.getOrNull()
    Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?").find(t)?.let {
        val g = it.groupValues
        return make(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4], g[5], g[6])
    }
    Regex("^(\\d{1,2})\\s+([A-Za-z]{3})[A-Za-z]*\\.?,?\\s+(\\d{4})(?:,?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?").find(t)?.let {
        val g = it.groupValues
        val month = MONTHS.indexOf(g[2].lowercase()) + 1
        return if (month == 0) null else make(g[3].toInt(), month, g[1].toInt(), g[4], g[5], g[6])
    }
    return null
}

/** The effort as Clean keeps it (6 to 10 in half steps), from an RPE, a RIR, or a column that holds either. */
private fun effort(rpe: String?, rir: String?, mixed: String?): String {
    val value = number(rpe) ?: number(rir)?.let { 10 - it } ?: number(mixed)?.let { if (it >= 6) it else 10 - it } ?: return ""
    val half = jRound(value * 2) / 2.0
    if (half < 6 || half > 10) return ""
    return if (half % 1.0 == 0.0) half.toInt().toString() else half.toString()
}

private fun setType(kind: String?, warmupFlag: String?): SetType {
    val k = kind?.lowercase() ?: ""
    return when {
        warmupFlag?.trim()?.lowercase() == "true" || k.contains("warm") -> SetType.WARMUP
        k.contains("fail") -> SetType.FAILURE
        k.contains("drop") -> SetType.DROP
        else -> SetType.NORMAL
    }
}

// --- Reading a CSV ---

private class Draft(var start: LocalDateTime, var hasTime: Boolean, val name: String, val duration: Long) {
    val blocks = mutableListOf<Pair<String, MutableList<ForeignSet>>>()
    val notes = mutableListOf<MutableList<String>>()
}

/**
 * Reads [text] (a file [detectFormat] knows) into workouts. [lbs] says what an unlabelled weight column is in. Workouts come out
 * in date order with unique start times, since Clean keys a workout by its start. [zone] places openGym's epoch times.
 */
fun parseForeign(text: String, lbs: Boolean, zone: TimeZone = TimeZone.currentSystemDefault()): ForeignImport {
    val t = text.removePrefix("﻿")
    return when (val source = detectFormat(t)) {
        ImportSource.OPENGYM -> parseOpenGym(t, zone)
        ImportSource.STRONG -> throw CsvFormatException("A Strong export is read as it is, not through here")
        ImportSource.UNKNOWN -> throw CsvFormatException("Clean does not know this file")
        else -> parseTable(source, tableOf(t)!!, lbs)
    }
}

private const val DEFAULT_NAME = "Workout"

private fun parseTable(source: ImportSource, t: Table, lbs: Boolean): ForeignImport {
    val date = t.col(*DATE_COLUMNS)
    val exercise = t.col(*EXERCISE_COLUMNS)
    val reps = t.col(*REPS_COLUMNS)
    val kg = t.col(*KG_COLUMNS)
    val lb = t.col(*LB_COLUMNS)
    val plain = t.col(*PLAIN_WEIGHT_COLUMNS)
    val workout = t.col(*WORKOUT_COLUMNS)
    val kindCol = t.col("set_type", "set type")
    val warmup = t.col("iswarmup")
    val rpe = t.col("rpe")
    val rir = t.col("rir")
    val mixed = t.col("rir/rpe")
    val seconds = if (source == ImportSource.FITNOTES) t.col("time") else t.col(*SECONDS_COLUMNS)
    val km = t.col("distance_km")
    val miles = t.col("distance_miles")
    val distance = t.col("distance")
    val distanceUnit = t.col("distance unit")
    val note = t.col(*NOTE_COLUMNS)
    val duration = t.col("duration")
    val end = t.col("end_time")
    val hevy = source == ImportSource.HEVY
    val skipped = LinkedHashMap<String, Int>()
    fun skip(reason: String) { skipped[reason] = (skipped[reason] ?: 0) + 1 }

    val drafts = LinkedHashMap<String, Draft>()
    for (r in t.rows) {
        fun cell(i: Int): String? = if (i >= 0) r.getOrNull(i) else null
        val parsed = cell(date)?.let(::foreignDate)
        if (parsed == null) { skip("the date could not be read"); continue }
        val name = cell(exercise)
        if (name.isNullOrBlank()) { skip("no exercise name"); continue }

        val weightKg = number(cell(kg)) ?: number(cell(lb))?.let(::lbToKg) ?: number(cell(plain))?.let { if (lbs) lbToKg(it) else it }
        val repCount = number(cell(reps))?.let { jRound(it).toInt() }
        val secs = clockSeconds(cell(seconds))
        val metres = number(cell(km))?.times(1000) ?: number(cell(miles))?.times(1609.344) ?: number(cell(distance))?.let {
            val unit = cell(distanceUnit)?.trim()?.lowercase() ?: "km"
            it * if (unit.startsWith("mi")) 1609.344 else if (unit == "m" || unit.startsWith("meter") || unit.startsWith("metre")) 1.0 else 1000.0
        }
        if ((repCount == null || repCount == 0) && secs == null && metres == null) { skip("no reps, time or distance"); continue }
        val set = ForeignSet(setType(cell(kindCol), cell(warmup)), weightKg, repCount?.takeIf { it > 0 }, secs, metres, effort(cell(rpe), cell(rir), cell(mixed)))

        val title = cell(workout)
        val key = if (hevy) "${title}\u0000${cell(date)}" else "${parsed.first.toLocalDate()}\u0000${title ?: ""}"
        val draft = drafts.getOrPut(key) {
            val length = if (hevy) foreignDate(cell(end) ?: "")?.let { Duration.between(parsed.first, it.first).seconds.coerceAtLeast(0) } else clockSeconds(cell(duration))?.toLong()
            Draft(parsed.first, parsed.second, title?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME, length ?: 0)
        }
        // Rows of one workout that carry clock times give it the earliest of them; without any it starts at midnight.
        if (parsed.second && (!draft.hasTime || parsed.first < draft.start)) { draft.start = parsed.first; draft.hasTime = true }
        val last = draft.blocks.lastOrNull()
        if (last == null || last.first != name) {
            draft.blocks.add(name to mutableListOf())
            draft.notes.add(mutableListOf())
        }
        draft.blocks.last().second += set
        cell(note)?.takeIf { it.isNotBlank() && it !in draft.notes.last() }?.let { draft.notes.last().add(it) }
    }

    val used = HashSet<LocalDateTime>()
    val workouts = drafts.values.sortedBy { it.start }.map { d ->
        var start = d.start
        while (!used.add(start)) start = start.plusMinutes(1)
        ForeignWorkout(start, d.name, d.duration, d.blocks.mapIndexed { i, b -> ForeignBlock(b.first, d.notes[i], b.second) })
    }
    return ForeignImport(source, workouts, emptyList(), skipped)
}

// --- openGym's backup ---

private fun parseOpenGym(text: String, zone: TimeZone): ForeignImport {
    val root = Json.parse(text) as Map<*, *>
    val pounds = (root["unit"] as? String)?.lowercase()?.startsWith("lb") == true
    val custom = (root["customEx"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.associate { it["id"].toString() to (it["n"] as? String ?: "") }
    val skipped = LinkedHashMap<String, Int>()
    fun skip(reason: String) { skipped[reason] = (skipped[reason] ?: 0) + 1 }
    fun kgOf(w: Double) = if (pounds) lbToKg(w) else w

    class Found(val start: LocalDateTime, val length: Long, val name: String, val blocks: List<ForeignBlock>)
    val found = mutableListOf<Found>()
    for (w in (root["workouts"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }) {
        val startMs = (w["start"] as? Double)?.takeIf { it > 0 }
        val endMs = (w["end"] as? Double)?.takeIf { it > 0 }
        val start = startMs?.let { Instant.fromEpochMilliseconds(it.toLong()).toLocalDateTime(zone) } ?: (w["d"] as? String)?.let(::foreignDate)?.first
        if (start == null) { skip("workouts without a date"); continue }
        val blocks = mutableListOf<ForeignBlock>()
        for (entry in (w["entries"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }) {
            val id = entry["id"].toString()
            val name = custom[id]?.takeIf { it.isNotEmpty() } ?: OPENGYM_IDS[id]
            if (name == null) { skip("exercises not in openGym's list"); continue }
            val sets = mutableListOf<ForeignSet>()
            for (s in (entry["sets"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }) {
                if (s["done"] != true) { skip("sets not ticked"); continue }
                val reps = (s["r"] as? Double)?.let { jRound(it).toInt() }?.takeIf { it > 0 }
                if (reps == null) { skip("no reps, time or distance"); continue }
                val warm = s["phase"] == "warmup" || s["warmup"] == true
                sets += ForeignSet(if (warm) SetType.WARMUP else SetType.NORMAL, (s["w"] as? Double)?.let(::kgOf), reps, null, null, "")
            }
            if (sets.isEmpty()) continue
            val before = blocks.lastOrNull()
            if (before != null && before.exercise == name) blocks[blocks.size - 1] = ForeignBlock(name, before.notes, before.sets + sets)
            else blocks += ForeignBlock(name, emptyList(), sets)
        }
        if (blocks.isNotEmpty()) {
            val length = if (startMs != null && endMs != null) ((endMs - startMs) / 1000).toLong().coerceAtLeast(0) else 0L
            found += Found(start, length, w["name"] as? String ?: DEFAULT_NAME, blocks)
        }
    }
    val used = HashSet<LocalDateTime>()
    val workouts = found.sortedBy { it.start }.map { f ->
        var start = f.start
        while (!used.add(start)) start = start.plusMinutes(1)
        ForeignWorkout(start, f.name, f.length, f.blocks)
    }
    val weights = (root["bodyweight"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.mapNotNull { e ->
        val day = (e["d"] as? String)?.let(::foreignDate)?.first?.toLocalDate()
        val w = e["w"] as? Double
        if (day == null || w == null) null else ForeignWeight(day.atStartOfDay().format(STRONG_DATE_FORMAT), kgOf(w))
    }
    return ForeignImport(ImportSource.OPENGYM, workouts, weights, skipped)
}

// --- Into Clean's rows ---

/** The rows Clean would have written for these workouts logged in the app, Workout # counting from 1 in date order. */
fun toRows(import: ForeignImport): List<WorkoutRow> = import.workouts.flatMapIndexed { index, w ->
    rowsFor(
        index + 1, w.start.format(STRONG_DATE_FORMAT), w.name, w.durationSeconds,
        w.blocks.map { b ->
            NewBlock(b.exercise, b.notes.map(::NewNote) + b.sets.map { NewSet(it.type, it.weightKg, it.reps, it.seconds, it.distanceMeters, it.rpe) })
        },
    )
}
