package clean.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeasurementsTest {
    private val weight = "\"Date\";\"Weight (kg)\"\n" +
        "\"2025-03-02 08:10:00\";81.19999694824219\n" +
        "\"2025-03-01 08:05:12\";81.0\n" +
        "\"2025-02-27 21:40:59\";80.5\n"
    private val waist = "\"Date\";\"Waist (cm)\"\n"

    @Test
    fun `file round-trips byte-identical`() {
        val bytes = weight.toByteArray()
        assertContentEquals(bytes, Measurements.serialiseFile(Measurements.parseFile("weight.csv", bytes)))
    }

    @Test
    fun `values keep their original text and order`() {
        val file = Measurements.parseFile("weight.csv", weight.toByteArray())
        assertEquals("Weight (kg)", file.valueHeader)
        assertEquals(
            listOf(
                MeasurementEntry("2025-03-02 08:10:00", "81.19999694824219"),
                MeasurementEntry("2025-03-01 08:05:12", "81.0"),
                MeasurementEntry("2025-02-27 21:40:59", "80.5"),
            ),
            file.entries,
        )
    }

    @Test
    fun `a file with no entries is just the header`() {
        val file = Measurements.parseFile("waist.csv", waist.toByteArray())
        assertEquals(MeasurementFile("waist.csv", "Waist (cm)", emptyList()), file)
        assertContentEquals(waist.toByteArray(), Measurements.serialiseFile(file))
    }

    @Test
    fun `zip keeps every file, in order, and is reproducible`() {
        val files = listOf(
            Measurements.parseFile("weight.csv", weight.toByteArray()),
            Measurements.parseFile("waist.csv", waist.toByteArray()),
        )
        val first = ByteArrayOutputStream().also { Measurements.writeZip(files, it, 1_740_000_000_000) }.toByteArray()
        assertEquals(files, Measurements.readZip(ByteArrayInputStream(first)))
        val second = ByteArrayOutputStream().also { Measurements.writeZip(files, it, 1_740_000_000_000) }.toByteArray()
        assertContentEquals(first, second)
    }

    @Test
    fun `an added or edited entry is written like a new one and the others are untouched`() {
        assertEquals("73.30000305175781", Measurements.newText(73.3))
        assertEquals("33.0", Measurements.newText(33.0))
        assertEquals("80.5", Measurements.newText(80.5))
        val file = Measurements.parseFile("weight.csv", weight.toByteArray())
        // One entry moved to another day and given a new value; one added in between (the file stays newest first).
        val edited = file.copy(entries = listOf(
            file.entries[0],
            MeasurementEntry("2025-03-01 09:00:00", Measurements.newText(81.3)),
            file.entries[1].copy(date = "2025-02-28 07:00:00"),
            file.entries[2],
        ))
        val lines = String(Measurements.serialiseFile(edited)).lines()
        assertEquals("\"2025-03-02 08:10:00\";81.19999694824219", lines[1])
        assertEquals("\"2025-03-01 09:00:00\";81.30000305175781", lines[2])
        // A date edited without touching the value keeps the value's own text.
        assertEquals("\"2025-02-28 07:00:00\";81.0", lines[3])
        assertEquals("\"2025-02-27 21:40:59\";80.5", lines[4])
        assertContentEquals(weight.toByteArray(), Measurements.serialiseFile(Measurements.parseFile("weight.csv", weight.toByteArray())))
    }

    @Test
    fun `files that could not be reproduced exactly are rejected`() {
        assertFailsWith<CsvFormatException>("CRLF") { Measurements.parseFile("w.csv", weight.replace("\n", "\r\n").toByteArray()) }
        assertFailsWith<CsvFormatException>("no final newline") { Measurements.parseFile("w.csv", weight.trimEnd().toByteArray()) }
        assertFailsWith<CsvFormatException>("quoted value") {
            Measurements.parseFile("w.csv", "\"Date\";\"Weight (kg)\"\n\"2025-03-01 08:05:12\";\"81.0\"\n".toByteArray())
        }
        assertFailsWith<CsvFormatException>("unquoted header") { Measurements.parseFile("w.csv", "Date;Weight (kg)\n".toByteArray()) }
    }
}
