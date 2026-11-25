package clean.core


/**
 * One line of a Strong workout export. Every field holds the text exactly as it stood in the
 * file: nothing is trimmed, and numbers are not parsed, so `10.0`, `0.0` and empty stay distinct.
 */
data class WorkoutRow(
    val workoutNumber: String,
    val date: String,
    val workoutName: String,
    val durationSeconds: String,
    val exerciseName: String,
    val setOrder: String,
    val weightKg: String,
    val reps: String,
    val rpe: String,
    val distanceMeters: String,
    val seconds: String,
    val notes: String,
    val workoutNotes: String,
)

class CsvFormatException(message: String) : Exception(message)

/**
 * Reads and writes the Strong workout file (see docs/data-format.md).
 *
 * Parsing is strict: UTF-8 without BOM, LF line endings, a newline after the last row, `;`
 * between fields and every field quoted. Anything else is rejected, so a file that parses is
 * a file that [serialise] reproduces byte for byte.
 */
object WorkoutCsv {
    private val COLUMNS = listOf(
        "Workout #", "Date", "Workout Name", "Duration (sec)", "Exercise Name", "Set Order",
        "Weight (kg)", "Reps", "RPE", "Distance (meters)", "Seconds", "Notes", "Workout Notes",
    )

    fun parse(bytes: ByteArray): List<WorkoutRow> {
        val records = parseRecords(decodeUtf8(bytes))
        if (records.firstOrNull() != COLUMNS) throw CsvFormatException("Not a Strong workout file: unexpected header")
        return records.drop(1).mapIndexed { index, f ->
            if (f.size != COLUMNS.size) {
                throw CsvFormatException("Row ${index + 1}: expected ${COLUMNS.size} fields, found ${f.size}")
            }
            WorkoutRow(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12])
        }
    }

    fun serialise(rows: List<WorkoutRow>): ByteArray {
        val out = StringBuilder()
        appendRecord(out, COLUMNS)
        for (r in rows) {
            appendRecord(
                out,
                listOf(
                    r.workoutNumber, r.date, r.workoutName, r.durationSeconds, r.exerciseName, r.setOrder,
                    r.weightKg, r.reps, r.rpe, r.distanceMeters, r.seconds, r.notes, r.workoutNotes,
                ),
            )
        }
        return out.toString().encodeToByteArray()
    }

    // Strong's own escaping is unverified (the real export has no quote, semicolon or newline
    // inside a field), so this is standard CSV: a quote inside a field is doubled.
    private fun appendRecord(out: StringBuilder, fields: List<String>) {
        fields.joinTo(out, separator = ";", postfix = "\n") { "\"" + it.replace("\"", "\"\"") + "\"" }
    }

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var i = 0
        while (i < text.length) {
            val fields = mutableListOf<String>()
            while (true) {
                if (text[i] != '"') throw CsvFormatException("Record ${records.size + 1}: field does not start with a quote")
                i++
                val field = StringBuilder()
                while (true) {
                    if (i >= text.length) throw CsvFormatException("Record ${records.size + 1}: unterminated field")
                    val c = text[i++]
                    if (c != '"') {
                        field.append(c)
                    } else if (text.getOrNull(i) == '"') {
                        field.append('"')
                        i++
                    } else {
                        break
                    }
                }
                fields += field.toString()
                val next = text.getOrNull(i)
                i++
                if (next == '\n') break
                if (next != ';') throw CsvFormatException("Record ${records.size + 1}: expected ';' or a line feed after a field")
                if (i >= text.length) throw CsvFormatException("Record ${records.size + 1}: file ends after a separator")
            }
            records += fields
        }
        return records
    }
}

internal fun decodeUtf8(bytes: ByteArray): String =
    try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (e: Exception) {
        throw CsvFormatException("File is not valid UTF-8")
    }
