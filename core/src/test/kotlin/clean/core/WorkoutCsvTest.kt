package clean.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkoutCsvTest {
    private val fixture = javaClass.getResourceAsStream("/workouts.csv")!!.readBytes()
    private val header = String(fixture, Charsets.UTF_8).substringBefore("\n") + "\n"

    private fun file(vararg lines: String) = (header + lines.joinToString("") { it + "\n" }).toByteArray()

    @Test
    fun `fixture round-trips byte-identical`() {
        assertContentEquals(fixture, WorkoutCsv.serialise(WorkoutCsv.parse(fixture)))
    }

    @Test
    fun `fixture rows are read in file order`() {
        val rows = WorkoutCsv.parse(fixture)
        assertEquals(18, rows.size)
        assertEquals(
            listOf("Note", "W", "F", "Rest Timer", "2", "D", "Note", "1"),
            rows.take(8).map { it.setOrder },
        )
        assertEquals(listOf("1", "2"), rows.map { it.workoutNumber }.distinct())
        assertEquals("Lat Pulldown (Machine)", rows[14].exerciseName)
    }

    @Test
    fun `text is kept exactly, trailing spaces included`() {
        val rows = WorkoutCsv.parse(fixture)
        assertEquals("Pull Day ", rows[0].workoutName)
        assertEquals("Wide grip ", rows[0].notes)
    }

    @Test
    fun `numbers stay as text so zero and empty differ`() {
        val rows = WorkoutCsv.parse(fixture)
        assertEquals("0.0", rows[8].weightKg)
        assertEquals("", rows[11].weightKg)
        assertEquals("54.4311", rows[14].weightKg)
        assertEquals("120.0", rows[3].seconds)
        assertEquals("5200.0", rows[15].distanceMeters)
    }

    @Test
    fun `quotes, semicolons and line breaks inside a field survive a round trip`() {
        val note = "said \"easy\"; then\nfailed"
        val row = WorkoutCsv.parse(fixture)[0].copy(notes = note)
        val bytes = WorkoutCsv.serialise(listOf(row))
        assertEquals(listOf(row), WorkoutCsv.parse(bytes))
        assertContentEquals(bytes, WorkoutCsv.serialise(WorkoutCsv.parse(bytes)))
    }

    @Test
    fun `a file with only the header is an empty list`() {
        assertEquals(emptyList(), WorkoutCsv.parse(header.toByteArray()))
        assertContentEquals(header.toByteArray(), WorkoutCsv.serialise(emptyList()))
    }

    @Test
    fun `files that could not be reproduced exactly are rejected`() {
        val line = String(fixture, Charsets.UTF_8).lines()[1]
        assertFailsWith<CsvFormatException>("CRLF") { WorkoutCsv.parse((header + line + "\r\n").toByteArray()) }
        assertFailsWith<CsvFormatException>("no final newline") { WorkoutCsv.parse((header + line).toByteArray()) }
        assertFailsWith<CsvFormatException>("BOM") { WorkoutCsv.parse("﻿".toByteArray() + fixture) }
        assertFailsWith<CsvFormatException>("unquoted field") { WorkoutCsv.parse(file(line.replaceFirst("\"1\"", "1"))) }
        assertFailsWith<CsvFormatException>("missing field") { WorkoutCsv.parse(file(line.removeSuffix(";\"\""))) }
        assertFailsWith<CsvFormatException>("wrong header") { WorkoutCsv.parse("\"Date\";\"Weight (kg)\"\n".toByteArray()) }
        assertFailsWith<CsvFormatException>("invalid UTF-8") { WorkoutCsv.parse(fixture + byteArrayOf(0xC3.toByte())) }
    }
}
