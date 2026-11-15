package clean.core

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Every file here was written by hand from the column names in docs/other-formats.md.
class ImportTest {
    private val hevy = """
"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Bench Press (Barbell)','','Pause ''slow''','0','warmup','40','10','','',''
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Bench Press (Barbell)','','Pause ''slow''','1','normal','60','8','','','8'
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Bench Press (Barbell)','','Pause ''slow''','2','failure','60','6','','','9.5'
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Bench Press (Barbell)','','Pause ''slow''','3','dropset','40','12','','',''
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Plank ','','','0','normal','','','','60',''
'Push, day','12 Oct 2026, 18:00','12 Oct 2026, 19:05','','Plank ','','','1','normal','','','','',''
'Pull','14 Oct 2026, 07:30','14 Oct 2026, 08:00','','Pull Up (Assisted)','','','0','normal','20','5','','',''
""".trimStart().replace('\'', '"')

    @Test
    fun `a Hevy file is recognised and read into workouts`() {
        assertEquals(ImportSource.HEVY, detectFormat(hevy))
        assertTrue(!needsUnit(hevy))
        val import = parseForeign(hevy, lbs = false)
        assertEquals(listOf("Push, day", "Pull"), import.workouts.map { it.name })
        assertEquals(listOf(LocalDateTime.of(2026, 10, 12, 18, 0), LocalDateTime.of(2026, 10, 14, 7, 30)), import.workouts.map { it.start })
        assertEquals(listOf(3900L, 1800L), import.workouts.map { it.durationSeconds })
        val push = import.workouts[0]
        assertEquals(listOf("Bench Press (Barbell)", "Plank "), push.blocks.map { it.exercise })
        assertEquals(listOf("Pause \"slow\""), push.blocks[0].notes)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE, SetType.DROP), push.blocks[0].sets.map { it.type })
        assertEquals(listOf("", "8", "9.5", ""), push.blocks[0].sets.map { it.rpe })
        assertEquals(60.0, push.blocks[1].sets.single().seconds)
        assertEquals(mapOf("no reps, time or distance" to 1), import.skipped)
    }

    @Test
    fun `Hevy rows come out as the rows Clean writes itself`() {
        val rows = toRows(parseForeign(hevy, lbs = false))
        assertEquals(
            listOf(
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Bench Press (Barbell)", "Note", "", "", "", "", "", "Pause \"slow\"", ""),
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Bench Press (Barbell)", "W", "40.0", "10", "", "", "", "", ""),
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Bench Press (Barbell)", "1", "60.0", "8", "8", "", "", "", ""),
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Bench Press (Barbell)", "F", "60.0", "6", "9.5", "", "", "", ""),
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Bench Press (Barbell)", "D", "40.0", "12", "", "", "", "", ""),
                WorkoutRow("1", "2026-10-12 18:00:00", "Push, day", "3900", "Plank ", "1", "", "", "", "", "60.0", "", ""),
                WorkoutRow("2", "2026-10-14 07:30:00", "Pull", "1800", "Pull Up (Assisted)", "1", "20.0", "5", "", "", "", "", ""),
            ),
            rows,
        )
        // And Clean reads them back as the same workouts.
        val back = buildWorkouts(rows)
        assertEquals(listOf("Push, day", "Pull"), back.map { it.name })
        assertEquals(listOf(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE, SetType.DROP), listOf(SetType.NORMAL)), back[0].blocks.map { b -> b.sets.map { it.type } })
        assertEquals(listOf("Pause \"slow\""), back[0].blocks[0].notes.map { it.text })
    }

    @Test
    fun `a Lyfta file asks for the unit, reads a mixed RIR or RPE column and a decimal comma`() {
        val lyfta = "Title,Date,Duration,Exercise,Weight,Reps,Set Type,RIR/RPE\n" +
            "Legs,2026-10-12 10:00:00,1:05:00,Squat (Barbell),\"62,5\",5,Normal,2\n" +
            "Legs,2026-10-12 10:00:00,1:05:00,Squat (Barbell),62.5,5,Normal,8\n" +
            "Legs,2026-10-12 10:00:00,1:05:00,Leg Press,100,10,Warm up,\n"
        assertEquals(ImportSource.LYFTA, detectFormat(lyfta))
        assertTrue(needsUnit(lyfta))
        val kg = parseForeign(lyfta, lbs = false).workouts.single()
        assertEquals(3900L, kg.durationSeconds)
        assertEquals(listOf(62.5, 62.5), kg.blocks[0].sets.map { it.weightKg })
        assertEquals(listOf("8", "8"), kg.blocks[0].sets.map { it.rpe })
        assertEquals(SetType.WARMUP, kg.blocks[1].sets[0].type)
        val lb = parseForeign(lyfta, lbs = true).workouts.single()
        assertEquals(lbToKg(62.5), lb.blocks[0].sets[0].weightKg)
        assertEquals(28.3495, lb.blocks[0].sets[0].weightKg)
    }

    @Test
    fun `a Fitbod file reads its warm-up flag and the time zone suffix on a date`() {
        val fitbod = "Date,Exercise,Reps,Weight(kg),isWarmup\n2026-10-12 08:00:00 +0000,Squat,5,100,false\n2026-10-12 08:00:00 +0000,Squat,8,60,true\n"
        assertEquals(ImportSource.FITBOD, detectFormat(fitbod))
        assertTrue(!needsUnit(fitbod))
        val w = parseForeign(fitbod, lbs = true).workouts.single()
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 0), w.start)
        assertEquals(listOf(SetType.NORMAL, SetType.WARMUP), w.blocks.single().sets.map { it.type })
        assertEquals(listOf(100.0, 60.0), w.blocks.single().sets.map { it.weightKg })
    }

    @Test
    fun `a FitNotes file makes a workout of each day and reads distance and time`() {
        val notes = "Date,Exercise,Category,Weight (kg),Reps,Distance,Distance Unit,Time,Comment\n" +
            "2026-10-12,Bench Press,Chest,60,8,,,,felt good\n" +
            "2026-10-12,Bench Press,Chest,60,7,,,,felt good\n" +
            "2026-10-12,Running,Cardio,,,5,km,00:25:00,\n" +
            "2026-10-13,Squat,Legs,100,5,,,,\n"
        assertEquals(ImportSource.FITNOTES, detectFormat(notes))
        assertTrue(!needsUnit(notes))
        val import = parseForeign(notes, lbs = false)
        assertEquals(listOf(LocalDateTime.of(2026, 10, 12, 0, 0), LocalDateTime.of(2026, 10, 13, 0, 0)), import.workouts.map { it.start })
        val day = import.workouts[0]
        assertEquals(listOf("Bench Press", "Running"), day.blocks.map { it.exercise })
        assertEquals(listOf("felt good"), day.blocks[0].notes)
        val run = day.blocks[1].sets.single()
        assertEquals(5000.0, run.distanceMeters)
        assertEquals(1500.0, run.seconds)
        assertEquals("Workout", day.name)
        // A plain Weight column has no unit.
        assertTrue(needsUnit("Date,Exercise,Category,Weight,Reps\n2026-10-12,Squat,Legs,100,5\n"))
    }

    @Test
    fun `a GymMane file with semicolons and decimal commas`() {
        val gym = "date;exercise;muscle;set;reps;weight_kg;volume_kg;est_1rm_kg;distance_km;duration_s;rpe\n2026-10-12;Squat;Quads;1;5;100,5;502,5;117,3;;;8,5\n"
        assertEquals(ImportSource.GYMMANE, detectFormat(gym))
        val set = parseForeign(gym, lbs = false).workouts.single().blocks.single().sets.single()
        assertEquals(100.5, set.weightKg)
        assertEquals("8.5", set.rpe)
    }

    @Test
    fun `any CSV with a date, an exercise and reps is read, a workout column separates a day, a BOM is ignored`() {
        val csv = "﻿Fecha;Ejercicio;Peso;Repeticiones;Routine\n2026-10-12;Sentadilla;80;5;Mañana\n2026-10-12;Remo;50;8;Tarde\n2026-10-12;Remo;50;8;Tarde\n"
        assertEquals(ImportSource.GENERIC, detectFormat(csv))
        assertTrue(needsUnit(csv))
        val import = parseForeign(csv, lbs = false)
        // Two workouts on one day: the later one starts a minute on, so their start times differ.
        assertEquals(listOf(LocalDateTime.of(2026, 10, 12, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 1)), import.workouts.map { it.start })
        assertEquals(listOf("Mañana", "Tarde"), import.workouts.map { it.name })
        assertEquals(2, import.workouts[1].blocks.single().sets.size)
    }

    @Test
    fun `quoted names with commas and unreadable rows`() {
        val csv = "date,exercise,reps,weight_kg\n2026-10-12,\"Curl, Alternating \",10,12\nnot a date,Squat,5,100\n2026-10-12,,5,100\n"
        val import = parseForeign(csv, lbs = false)
        assertEquals("Curl, Alternating ", import.workouts.single().blocks.single().exercise)
        assertEquals(mapOf("the date could not be read" to 1, "no exercise name" to 1), import.skipped)
    }

    @Test
    fun `an openGym backup is read with its exercises by id`() {
        val start = LocalDateTime.of(2026, 10, 12, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val json = """{"unit":"lb","customEx":[{"id":"c1","n":"My Row"}],"routines":[],"workouts":[{"d":"2026-10-12","start":$start,"end":${start + 3600000},"entries":[
            {"id":"0001","sets":[{"r":10,"w":0,"done":true},{"r":8,"w":20,"done":true,"phase":"warmup"},{"r":5,"w":25,"done":false}]},
            {"id":"c1","sets":[{"r":12,"w":100,"done":true}]},{"id":"zzzz","sets":[{"r":1,"w":1,"done":true}]}]}],"bodyweight":[{"d":"2026-10-11","w":176}]}"""
        assertEquals(ImportSource.OPENGYM, detectFormat(json))
        assertTrue(!needsUnit(json))
        val import = parseForeign(json, lbs = false, zone = ZoneOffset.UTC)
        val w = import.workouts.single()
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 0), w.start)
        assertEquals(3600L, w.durationSeconds)
        assertEquals(listOf(OPENGYM_IDS.getValue("0001"), "My Row"), w.blocks.map { it.exercise })
        assertEquals("3/4 Sit Up", OPENGYM_IDS["0001"])
        assertEquals(listOf(SetType.NORMAL, SetType.WARMUP), w.blocks[0].sets.map { it.type })
        assertEquals(lbToKg(100.0), w.blocks[1].sets.single().weightKg)
        assertEquals(mapOf("sets not ticked" to 1, "exercises not in openGym's list" to 1), import.skipped)
        assertEquals(listOf("2026-10-11 00:00:00" to lbToKg(176.0)), import.weights.map { it.date to it.kg })
    }

    @Test
    fun `an unknown file and a Strong file are not read here`() {
        assertEquals(ImportSource.UNKNOWN, detectFormat("a,b,c\n1,2,3\n"))
        assertEquals(ImportSource.UNKNOWN, detectFormat(""))
        assertEquals(ImportSource.UNKNOWN, detectFormat("{\"a\": 1}"))
        assertFailsWith<CsvFormatException> { readImportFile("a,b,c\n1,2,3\n".toByteArray()) }
        val real = javaClass.getResourceAsStream("/workouts.csv")!!.readBytes()
        assertEquals(ImportSource.STRONG, detectFormat(String(real)))
        assertEquals(ImportSource.STRONG, readImportFile(real).source)
        assertFailsWith<CsvFormatException> { parseForeign(String(real), lbs = false) }
    }

    @Test
    fun `a zip with one recognisable file gives that file as it was`() {
        fun zip(vararg files: Pair<String, String>) = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> for ((n, t) in files) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        }.toByteArray()
        val file = readImportFile(zip("readme.txt" to "hi", "workouts.csv" to hevy))
        assertEquals(ImportSource.HEVY, file.source)
        assertEquals(hevy, String(file.bytes))
        assertFailsWith<CsvFormatException> { readImportFile(zip("other.csv" to "x,y\n1,2\n")) }
        assertFailsWith<CsvFormatException> { readImportFile(zip("a.csv" to hevy, "b.csv" to hevy)) }
    }

    @Test
    fun `what is imported reads back as the same workouts`() {
        val import = parseForeign(hevy, lbs = false)
        val back = buildWorkouts(toRows(import))
        assertEquals(import.workouts.size, back.size)
        for ((a, b) in import.workouts.zip(back)) {
            assertEquals(a.start.format(STRONG_DATE_FORMAT), b.date)
            assertEquals(a.durationSeconds, b.durationSeconds)
            assertEquals(a.blocks.map { it.exercise }, b.blocks.map { it.exercise })
            assertEquals(a.blocks.map { it.sets.map { s -> s.type } }, b.blocks.map { it.sets.map { s -> s.type } })
            assertEquals(a.blocks.map { it.sets.map { s -> s.weightKg } }, b.blocks.map { it.sets.map { s -> s.weight } })
            assertEquals(a.blocks.map { it.sets.map { s -> s.reps } }, b.blocks.map { it.sets.map { s -> s.reps } })
        }
    }

    @Test
    fun `a large file is read quickly`() {
        val big = StringBuilder("date,exercise,reps,weight_kg\n")
        repeat(12000) { big.append("2026-${1 + it % 12}-${1 + it % 28},Squat ${it % 40},5,${50 + it % 50}\n") }
        val start = System.nanoTime()
        val import = parseForeign(big.toString(), lbs = false)
        assertTrue(toRows(import).size > 11000)
        assertTrue((System.nanoTime() - start) / 1e9 < 10)
    }
}
