package clean.core


/** One dated value. [value] is the original text (`73.30000305175781`, `33.0`), float noise included. */
data class MeasurementEntry(val date: String, val value: String)

/**
 * One CSV from a Strong measurements export, e.g. `weight.csv` with value header `Weight (kg)`.
 * Entries keep file order, which is newest first.
 */
data class MeasurementFile(val name: String, val valueHeader: String, val entries: List<MeasurementEntry>)

/** Reads and writes the Strong measurements export (see docs/data-format.md). Strict, like [WorkoutCsv]. */
object Measurements {
    /** The text of an entry made in Clean: Strong stores 32-bit floats, so 73.3 reads `73.30000305175781` and 33 reads `33.0`. Imported entries keep their own text. */
    fun newText(value: Double): String = value.toFloat().toDouble().toString()

    private val HEADER = Regex("\"Date\";\"([^\"]*)\"")
    private val ROW = Regex("\"([^\"]*)\";([^\";]*)")

    fun parseFile(name: String, bytes: ByteArray): MeasurementFile {
        val text = decodeUtf8(bytes)
        if (!text.endsWith("\n")) throw CsvFormatException("$name: no line feed after the last line")
        val lines = text.dropLast(1).split("\n")
        val header = HEADER.matchEntire(lines[0]) ?: throw CsvFormatException("$name: unexpected header")
        val entries = lines.drop(1).mapIndexed { index, line ->
            val row = ROW.matchEntire(line) ?: throw CsvFormatException("$name row ${index + 1}: expected \"date\";value")
            MeasurementEntry(row.groupValues[1], row.groupValues[2])
        }
        return MeasurementFile(name, header.groupValues[1], entries)
    }

    fun serialiseFile(file: MeasurementFile): ByteArray {
        val out = StringBuilder("\"Date\";\"${file.valueHeader}\"\n")
        for (e in file.entries) out.append('"').append(e.date).append("\";").append(e.value).append('\n')
        return out.toString().encodeToByteArray()
    }

    fun readZip(bytes: ByteArray): List<MeasurementFile> = readZipItems(bytes).map { parseFile(it.name, it.bytes) }

    /** [modifiedMillis] is the timestamp stamped on every entry; Strong uses the moment of export. */
    fun writeZip(files: List<MeasurementFile>, modifiedMillis: Long): ByteArray =
        writeZipItems(files.map { ZipItem(it.name, serialiseFile(it)) }, modifiedMillis)
}
