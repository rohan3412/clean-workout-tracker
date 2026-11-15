package clean.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Writing workouts in other apps' shapes (docs/other-formats.md), from the workouts Clean has built from its rows.
 * Only what the other format has a place for is written; Strong's own file is WorkoutCsv, and is the one that round-trips.
 */

private val HEVY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

private fun csvLine(fields: List<String>, always: Boolean): String =
    fields.joinToString(",", postfix = "\n") { f ->
        if (always || f.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' } || f != f.trim()) "\"" + f.replace("\"", "\"\"") + "\"" else f
    }

/** A number as plain text with at most [scale] decimals and no trailing zeros. */
private fun plain(value: Double, scale: Int = 6): String = BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.signum() == 0) "0" else it.toPlainString() }

private fun SetItem.metres(): String = distance?.let { plain(it / 1000) } ?: ""

/** A workout's sets with their kind, skipping notes and rest rows. */
private fun Block.setsOf(): List<SetItem> = items.filterIsInstance<SetItem>()

/** Hevy's CSV: one row per set; the workout's note is its description. The two sides of a left and right exercise come out as ordinary sets, and the start loses its seconds. */
fun exportHevy(workouts: List<Workout>): String {
    val out = StringBuilder(csvLine(listOf("title", "start_time", "end_time", "description", "exercise_title", "superset_id", "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km", "duration_seconds", "rpe"), true))
    for (w in workouts) {
        val start = parseStrongDate(w.date) ?: continue
        val from = start.format(HEVY_DATE)
        val to = start.plusSeconds(w.durationSeconds).format(HEVY_DATE)
        for (b in w.blocks) {
            val notes = b.notes.joinToString("\n") { it.text }
            b.setsOf().forEachIndexed { i, s ->
                val kind = when (s.type) { SetType.WARMUP -> "warmup"; SetType.FAILURE -> "failure"; SetType.DROP -> "dropset"; SetType.NORMAL -> "normal" }
                out.append(csvLine(listOf(w.name, from, to, w.notes, b.exercise, "", notes, i.toString(), kind, s.row.weightKg, s.row.reps, s.metres(), s.row.seconds.removeSuffix(".0"), s.row.rpe), true))
            }
        }
    }
    return out.toString()
}

private fun clock(seconds: Double): String {
    val t = Math.round(seconds)
    return "%02d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60)
}

private fun shortClock(seconds: Double): String {
    val t = Math.round(seconds)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/**
 * FitNotes' CSV: one row per set, a day's rows being its workout. FitNotes has no set kinds, workout names, durations or RPE, so
 * warm-ups and the rest are ordinary sets there; Category is left empty; a block's notes ride on its first set as the comment.
 */
fun exportFitNotes(workouts: List<Workout>): String {
    val out = StringBuilder(csvLine(listOf("Date", "Exercise", "Category", "Weight (kg)", "Reps", "Distance", "Distance Unit", "Time", "Comment"), false))
    for (w in workouts) for (b in w.blocks) {
        val comment = b.notes.joinToString("\n") { it.text }
        b.setsOf().forEachIndexed { i, s ->
            out.append(csvLine(listOf(w.day, b.exercise, "", s.row.weightKg, s.row.reps, s.metres(), if (s.distance != null) "km" else "", s.seconds?.let(::clock) ?: "", if (i == 0) comment else ""), false))
        }
    }
    return out.toString()
}

/** A plain CSV in GymMane's columns, with the workout's name and start time added at the end so a workout comes back whole. */
fun exportPlainCsv(workouts: List<Workout>, rule: OneRm = OneRm.DEFAULT): String {
    val out = StringBuilder(csvLine(listOf("date", "exercise", "muscle", "set", "reps", "weight_kg", "volume_kg", "est_1rm_kg", "distance_km", "duration_s", "rpe", "workout"), false))
    for (w in workouts) for (b in w.blocks) {
        b.setsOf().forEachIndexed { i, s ->
            val weight = s.weight
            val reps = s.reps
            val volume = if (weight != null && reps != null) plain(weight * reps, 2) else ""
            val oneRm = if (weight != null && reps != null) estimate1Rm(weight, reps, rule, s.rpe)?.let { plain(it, 2) } ?: "" else ""
            out.append(csvLine(listOf(w.date, b.exercise, "", (i + 1).toString(), s.row.reps, s.row.weightKg, volume, oneRm, s.metres(), s.row.seconds.removeSuffix(".0"), s.row.rpe, w.name), false))
        }
    }
    return out.toString()
}

/** Clean's own JSON: every field of every row as the text stored, with a format version. */
fun exportJson(rows: List<WorkoutRow>): String {
    val out = StringBuilder("{\"format\": \"clean-rows\", \"version\": 1, \"rows\": [")
    rows.forEachIndexed { i, r ->
        val fields = listOf(
            "workoutNumber" to r.workoutNumber, "date" to r.date, "workoutName" to r.workoutName, "durationSeconds" to r.durationSeconds,
            "exerciseName" to r.exerciseName, "setOrder" to r.setOrder, "weightKg" to r.weightKg, "reps" to r.reps, "rpe" to r.rpe,
            "distanceMeters" to r.distanceMeters, "seconds" to r.seconds, "notes" to r.notes, "workoutNotes" to r.workoutNotes,
        )
        out.append(if (i == 0) "\n  {" else ",\n  {")
        out.append(fields.joinToString(", ") { (k, v) -> "\"$k\": ${Json.quote(v)}" }).append('}')
    }
    return out.append(if (rows.isEmpty()) "]}\n" else "\n]}\n").toString()
}

private fun lengthText(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m" else ""
}

/** One workout as lines a person can read and paste. [lbs] shows weights in pounds (to the nearest half), as the app does. */
fun exportText(workout: Workout, lbs: Boolean = false): String {
    val out = StringBuilder(workout.name).append('\n')
    val at = parseStrongDate(workout.date)?.format(HEVY_DATE) ?: workout.date
    out.append(listOf(at, lengthText(workout.durationSeconds)).filter { it.isNotEmpty() }.joinToString("  ·  ")).append("\n")
    if (workout.notes.isNotEmpty()) out.append("Note: ").append(workout.notes).append('\n')
    fun weightText(kg: Double) = if (lbs) shortNumber(kgToLb(kg)) + " lb" else shortNumber(kg) + " kg"
    for (b in workout.blocks) {
        out.append('\n').append(b.exercise).append('\n')
        for (n in b.notes) out.append("  Note: ").append(n.text).append('\n')
        var number = 0
        for (s in b.setsOf()) {
            val label = when (s.type) {
                SetType.WARMUP -> "W"
                SetType.FAILURE -> "F"
                SetType.DROP -> "D"
                SetType.NORMAL -> (++number).toString()
            }
            val parts = mutableListOf<String>()
            s.weight?.let { parts += if (it == 0.0 && s.reps != null) "bodyweight" else weightText(it) }
            s.reps?.let { parts += if (parts.isEmpty()) "$it reps" else "x $it" }
            s.distance?.let { parts += shortNumber(it / 1000) + " km" }
            s.seconds?.let { parts += if (s.distance != null || it >= 60) shortClock(it) else shortNumber(it) + " s" }
            if (s.row.rpe.isNotEmpty()) parts += "@" + s.row.rpe
            out.append("  ").append(label).append("  ").append(parts.joinToString(" ")).append('\n')
        }
    }
    return out.toString()
}
