package clean.core

import clean.core.time.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {
    private val rows = rowsFor(
        1, "2026-10-12 18:00:00", "Push, day", 3900,
        listOf(
            NewBlock(
                "Bench Press (Barbell)",
                listOf(
                    NewNote("Pause \"slow\""),
                    NewSet(SetType.WARMUP, 40.0, 10),
                    NewSet(SetType.NORMAL, 60.0, 8, rpe = "8"),
                    NewSet(SetType.FAILURE, 60.0, 6, rpe = "9.5"),
                    NewSet(SetType.DROP, 54.4311, 12),
                ),
            ),
            NewBlock("Plank ", listOf(NewSet(SetType.NORMAL, seconds = 90.0))),
            NewBlock("Running", listOf(NewSet(SetType.NORMAL, distanceMeters = 5000.0, seconds = 1500.0))),
        ),
    ) + rowsFor(2, "2026-10-14 07:30:00", "Pull", 1800, listOf(NewBlock("Pull Up", listOf(NewSet(SetType.NORMAL, 0.0, 5), NewSet(SetType.NORMAL, 10.0, 4)))))
    private val workouts = buildWorkouts(rows)

    @Test
    fun `a Hevy export reads back as the same workouts`() {
        val text = exportHevy(workouts)
        assertEquals(ImportSource.HEVY, detectFormat(text))
        assertTrue(text.contains("\"12 Oct 2026, 18:00\",\"12 Oct 2026, 19:05\""))
        assertTrue(text.contains("\"dropset\",\"54.4311\",\"12\""))
        val back = parseForeign(text, lbs = false)
        // Written again from what was read, the file is the same.
        assertEquals(text, exportHevy(buildWorkouts(toRows(back))))
        assertEquals(listOf("Push, day", "Pull"), back.workouts.map { it.name })
        assertEquals(listOf("Bench Press (Barbell)", "Plank ", "Running"), back.workouts[0].blocks.map { it.exercise })
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE, SetType.DROP), back.workouts[0].blocks[0].sets.map { it.type })
        assertEquals(listOf("Pause \"slow\""), back.workouts[0].blocks[0].notes)
        assertEquals(3900L, back.workouts[0].durationSeconds)
        assertEquals(90.0, back.workouts[0].blocks[1].sets.single().seconds)
        assertEquals(5000.0, back.workouts[0].blocks[2].sets.single().distanceMeters)
        assertEquals(listOf("", "8", "9.5", ""), back.workouts[0].blocks[0].sets.map { it.rpe })
    }

    @Test
    fun `a FitNotes export reads back with the sets and their values`() {
        val text = exportFitNotes(workouts)
        assertEquals(ImportSource.FITNOTES, detectFormat(text))
        assertTrue(!needsUnit(text))
        val back = parseForeign(text, lbs = false)
        assertEquals(listOf("2026-10-12T00:00", "2026-10-14T00:00"), back.workouts.map { it.start.toString() })
        // FitNotes has no kinds of set, so every set comes back ordinary; the note rides on the first set.
        val bench = back.workouts[0].blocks[0]
        assertEquals(listOf(40.0, 60.0, 60.0, 54.4311), bench.sets.map { it.weightKg })
        assertEquals(listOf(10, 8, 6, 12), bench.sets.map { it.reps })
        assertEquals(listOf("Pause \"slow\""), bench.notes)
        assertEquals(90.0, back.workouts[0].blocks[1].sets.single().seconds)
        assertEquals(5000.0, back.workouts[0].blocks[2].sets.single().distanceMeters)
        assertEquals(1500.0, back.workouts[0].blocks[2].sets.single().seconds)
        assertEquals(listOf(0.0, 10.0), back.workouts[1].blocks[0].sets.map { it.weightKg })
    }

    @Test
    fun `a plain CSV reads back with its workout names and times`() {
        val text = exportPlainCsv(workouts, EPLEY_RULE)
        assertEquals(ImportSource.GYMMANE, detectFormat(text))
        assertTrue(text.contains(",8,60.0,480,76,"))
        val back = parseForeign(text, lbs = false)
        assertEquals(listOf("2026-10-12T18:00", "2026-10-14T07:30"), back.workouts.map { it.start.toString() })
        assertEquals(listOf("Push, day", "Pull"), back.workouts.map { it.name })
        assertEquals(listOf(40.0, 60.0, 60.0, 54.4311), back.workouts[0].blocks[0].sets.map { it.weightKg })
        assertEquals(listOf("", "8", "9.5", ""), back.workouts[0].blocks[0].sets.map { it.rpe })
    }

    @Test
    fun `the JSON holds every field of every row and parses back to the same rows`() {
        val tricky = rows + WorkoutRow("3", "2026-10-15 09:00:00", "Tab\there \"q\" \\ é\u0001", "10", "Name\nwith line", "Note", "", "", "", "", "", "ünï", "wn")
        val text = exportJson(tricky)
        val json = Json.parse(text) as Map<*, *>
        assertEquals("clean-rows", json["format"])
        assertEquals(1.0, json["version"])
        val back = (json["rows"] as List<*>).map { r ->
            r as Map<*, *>
            fun f(k: String) = r[k] as String
            WorkoutRow(f("workoutNumber"), f("date"), f("workoutName"), f("durationSeconds"), f("exerciseName"), f("setOrder"), f("weightKg"), f("reps"), f("rpe"), f("distanceMeters"), f("seconds"), f("notes"), f("workoutNotes"))
        }
        assertEquals(tricky, back)
        assertEquals("{\"format\": \"clean-rows\", \"version\": 1, \"rows\": []}\n", exportJson(emptyList()))
    }

    @Test
    fun `a workout as text`() {
        assertEquals(
            listOf(
                "Push, day", "12 Oct 2026, 18:00  ·  1h 5m", "",
                "Bench Press (Barbell)", "  Note: Pause \"slow\"", "  W  40 kg x 10", "  1  60 kg x 8 @8", "  F  60 kg x 6 @9.5", "  D  54.43 kg x 12", "",
                "Plank ", "  1  1:30", "",
                "Running", "  1  5 km 25:00", "",
            ).joinToString("\n"),
            exportText(workouts[0]),
        )
        assertTrue(exportText(workouts[1], lbs = true).contains("  2  22 lb x 4"))
        assertTrue(exportText(workouts[1]).contains("  1  bodyweight x 5"))
    }

    // --- FIT ---

    private class Msg(val global: Int, val fields: Map<Int, Long>)

    private fun le(b: ByteArray, at: Int, n: Int) = (0 until n).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xFF) shl (8 * i)) }

    /** A reader for what exportFit writes: definitions, then data messages. */
    private fun decode(b: ByteArray): List<Msg> {
        val end = b.size - 2
        var at = 14
        val defs = HashMap<Int, Pair<Int, List<Triple<Int, Int, Int>>>>()
        val out = mutableListOf<Msg>()
        while (at < end) {
            val header = b[at++].toInt() and 0xFF
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                at += 2 // reserved, architecture
                val global = le(b, at, 2).toInt(); at += 2
                val count = b[at++].toInt()
                val fields = (0 until count).map { i -> Triple(b[at + 3 * i].toInt() and 0xFF, b[at + 3 * i + 1].toInt(), b[at + 3 * i + 2].toInt() and 0xFF) }
                at += 3 * count
                defs[local] = global to fields
            } else {
                val (global, fields) = defs.getValue(local)
                val values = HashMap<Int, Long>()
                for ((number, size, _) in fields) { values[number] = le(b, at, size); at += size }
                out += Msg(global, values)
            }
        }
        assertEquals(end, at)
        return out
    }

    @Test
    fun `the CRC is the one FIT uses`() {
        assertEquals(0xBB3D, fitCrc("123456789".toByteArray()))
    }

    @Test
    fun `a FIT file has a valid header, size and checksum`() {
        val b = exportFit(workouts[0], TimeZone.UTC)
        assertEquals(14, b[0].toInt())
        assertEquals(".FIT", String(b, 8, 4, Charsets.US_ASCII))
        assertEquals((b.size - 16).toLong(), le(b, 4, 4))
        assertEquals(fitCrc(b, 0, 12).toLong(), le(b, 12, 2))
        assertEquals(fitCrc(b, 0, b.size - 2).toLong(), le(b, b.size - 2, 2))
        assertEquals(0, fitCrc(b))
    }

    @Test
    fun `a FIT file has its messages in order with the right values`() {
        val msgs = decode(exportFit(workouts[0], TimeZone.UTC))
        assertEquals(listOf(0, 21, 21, 19, 18, 34), msgs.map { it.global })
        val start = 1_791_828_000L - 631_065_600L // 2026-10-12 18:00:00 UTC
        val (fileId, startEvent, stopEvent) = msgs
        val (lap, session, activity) = msgs.drop(3)
        assertEquals(4L, fileId.fields[0])
        assertEquals(start, fileId.fields[4])
        assertEquals(listOf(0L, 0L), listOf(startEvent.fields[0], startEvent.fields[1]))
        assertEquals(start, startEvent.fields[253])
        assertEquals(4L, stopEvent.fields[1])
        assertEquals(start + 3900, stopEvent.fields[253])
        assertEquals(3_900_000L, lap.fields[7])
        assertEquals(start, lap.fields[2])
        assertEquals(10L, session.fields[5])
        assertEquals(20L, session.fields[6])
        assertEquals(3_900_000L, session.fields[8])
        assertEquals(1L, session.fields[26])
        assertEquals(1L, activity.fields[1])
        assertEquals(start + 3900, activity.fields[253])
    }

    @Test
    fun `FIT times start at the end of 1989 and follow the zone`() {
        fun fitStart(date: String, zone: TimeZone) = decode(exportFit(buildWorkouts(rowsFor(1, date, "x", 60, listOf(NewBlock("A", listOf(NewSet(SetType.NORMAL, 1.0, 1)))))).single(), zone))[0].fields[4]
        assertEquals(0L, fitStart("1989-12-31 00:00:00", TimeZone.UTC))
        assertEquals(86400L, fitStart("1990-01-01 00:00:00", TimeZone.UTC))
        assertEquals(86400L, fitStart("1990-01-01 01:00:00", TimeZone.of("Europe/Amsterdam")))
        val activity = decode(exportFit(buildWorkouts(rowsFor(1, "1990-01-01 01:00:00", "x", 60, listOf(NewBlock("A", listOf(NewSet(SetType.NORMAL, 1.0, 1)))))).single(), TimeZone.of("Europe/Amsterdam"))).last()
        // The local clock reading is one hour on from UTC.
        assertEquals(86400L + 60 + 3600, activity.fields[5])
    }
}
