package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Workout tags (N10), the year in review (N14) and swap suggestions (N17). */
class Round7Test {
    private fun w(day: String, exercise: String = "Squat", kg: Int = 100, reps: Int = 5, sets: Int = 2): Workout =
        buildWorkouts((1..sets).map { WorkoutRow("1", "$day 10:00:00", "w", "3600", exercise, it.toString(), "$kg", "$reps", "", "", "", "", "") }).single()

    // --- Tags ---

    @Test fun aTagIsTidiedAndKeepsTheSpellingInUse() {
        assertEquals("Sick day", cleanTag("  Sick   day "))
        assertEquals(TAG_MAX, cleanTag("x".repeat(60)).length)
        assertEquals("", cleanTag("   "))
        assertEquals("Deload", spellTag("deload", listOf("Deload", "Sick")))
        assertEquals("Travel", spellTag("Travel", listOf("Deload")))
    }

    @Test fun tagsComeMostUsedFirstAndOnlyForWorkoutsThatExist() {
        val tags = mapOf("a" to "Deload", "b" to "Sick", "c" to "Deload", "gone" to "Old", "e" to "")
        assertEquals(listOf("Deload", "Sick"), tagsByUse(tags, setOf("a", "b", "c", "e")))
    }

    @Test fun leftOutWorkoutsAreDroppedFromTheStatsOnly() {
        val a = w("2026-03-01"); val b = w("2026-03-03"); val c = w("2026-03-05")
        val all = listOf(a, b, c)
        val tags = mapOf(b.date to "Sick", c.date to "Travel")
        assertEquals(listOf(a, c), statWorkouts(all, tags, setOf("Sick")))
        // A tag that is not switched on leaves nothing out, and with none the very same list comes back.
        assertSame(all, statWorkouts(all, tags, emptySet()))
        assertSame(all, statWorkouts(all, emptyMap(), setOf("Sick")))
    }

    @Test fun aLeftOutWorkoutSetsNoRecord() {
        val a = w("2026-03-01", kg = 100)
        val b = w("2026-03-03", kg = 140)
        val c = w("2026-03-05", kg = 110)
        val withAll = prsOf(listOf(a, b, c), OneRm.DEFAULT)
        val without = prsOf(statWorkouts(listOf(a, b, c), mapOf(b.date to "Sick"), setOf("Sick")), OneRm.DEFAULT)
        assertTrue(recordCount(c, withAll[c.date].orEmpty()) == 0.0)
        assertTrue(recordCount(c, without[c.date].orEmpty()) > 0.0)
        assertNull(without[b.date])
    }

    // --- Year in review ---

    private fun review(workouts: List<Workout>, year: Int) = yearReview(
        workouts, year, { volumeOf(it) }, { _, s -> progressOf(s, OneRm.DEFAULT) }, prsOf(workouts, OneRm.DEFAULT),
    )

    @Test fun theYearsListedAreThoseWithWorkouts() {
        assertEquals(listOf(2026, 2024), yearsWithWorkouts(listOf(w("2024-05-01"), w("2026-01-02"), w("2026-02-02"))))
        assertEquals(emptyList(), yearsWithWorkouts(emptyList()))
    }

    @Test fun theEntryShowsFromDecemberToJanuary() {
        assertTrue(yearReviewSeason(LocalDate.parse("2026-12-01")))
        assertTrue(yearReviewSeason(LocalDate.parse("2027-01-31")))
        assertTrue(!yearReviewSeason(LocalDate.parse("2026-11-30")))
        assertTrue(!yearReviewSeason(LocalDate.parse("2027-02-01")))
        assertEquals(2026, yearInReviewFor(LocalDate.parse("2026-12-15")))
        assertEquals(2026, yearInReviewFor(LocalDate.parse("2027-01-10")))
    }

    @Test fun aYearWithNoWorkoutHasNoReview() {
        assertNull(review(listOf(w("2025-05-01")), 2026))
    }

    @Test fun theYearAddsUpAndNamesItsBests() {
        val all = listOf(
            w("2025-12-20", "Bench Press", kg = 80),
            w("2026-01-05", "Squat", kg = 100), w("2026-01-12", "Squat", kg = 100),
            w("2026-01-19", "Squat", kg = 100), w("2026-03-02", "Bench Press", kg = 90, sets = 3), w("2026-03-09", "Squat", kg = 120),
        )
        val r = assertNotNull(review(all, 2026))
        assertEquals(5, r.totals.workouts)
        assertEquals(3600L * 5, r.totals.seconds)
        assertEquals(11.0, r.totals.sets)
        // January has three workouts of 1000 (3000); March has 1350 + 1200.
        assertEquals(1, r.bestMonth!!.first)
        // The three Mondays of January, then a gap: the run is three weeks.
        assertEquals(3, r.longestStreakWeeks)
        // Squat has 8 working sets, the most trained.
        assertEquals("Squat", r.mostTrained!!.first)
        // Bench had 80 before the year and 90 in it; squat began the year at 100 and ended at 120; bench gains less than that.
        assertEquals(listOf("Squat", "Bench Press"), r.topLifts.map { it.exercise })
        assertTrue(r.topLifts.all { it.gain > 0 })
        assertTrue(r.records > 0.0)
    }

    // --- Swaps ---

    private val use = mapOf(
        "Bench Press (Barbell)" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS, Muscle.TRICEPS)),
        "Bench Press (Dumbbell)" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS, Muscle.TRICEPS)),
        "Chest Fly (Cable)" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS)),
        "Chest Fly (Machine)" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.SHOULDERS)),
        "Close Grip Bench (Smith Machine)" to MuscleUse(setOf(Muscle.TRICEPS, Muscle.CHEST), setOf(Muscle.SHOULDERS)),
        "Squat (Barbell)" to MuscleUse(setOf(Muscle.QUADS)),
    )
    private fun swaps(done: Map<String, Int>, limit: Int = 8) =
        swapSuggestions("Bench Press (Barbell)", use.keys, { use[it] ?: MuscleUse() }, done, limit)

    @Test fun equipmentIsTheLastBrackets() {
        assertEquals("dumbbell", bracketEquipment("Bench Press (Dumbbell)"))
        assertNull(bracketEquipment("Pull Up"))
    }

    @Test fun swapsTrainTheSameMainMusclesAndNeverTheExerciseItself() {
        val names = swaps(emptyMap())
        assertTrue("Bench Press (Barbell)" !in names)
        assertTrue("Squat (Barbell)" !in names)
        assertEquals(setOf("Bench Press (Dumbbell)", "Chest Fly (Cable)", "Chest Fly (Machine)", "Close Grip Bench (Smith Machine)"), names.toSet())
        // The same main muscles and helpers first.
        assertEquals("Bench Press (Dumbbell)", names.first())
    }

    @Test fun exercisesTheUserHasDoneAndEquipmentTheyHaveUsedComeFirst() {
        val names = swaps(mapOf("Chest Fly (Cable)" to 6, "Squat (Barbell)" to 20))
        assertEquals("Chest Fly (Cable)", names.first())
        // Cable is used on something, so a cable exercise outranks a machine one the user never did; machine beats none of that.
        val equipment = swaps(mapOf("Squat (Machine)" to 3, "Pull Up" to 2))
        assertTrue(equipment.indexOf("Chest Fly (Machine)") < equipment.indexOf("Chest Fly (Cable)"))
    }

    @Test fun noMainMuscleKnownGivesNoSuggestions() {
        assertEquals(emptyList(), swapSuggestions("Mystery", use.keys, { MuscleUse() }, emptyMap()))
        assertEquals(2, swaps(emptyMap(), limit = 2).size)
    }
}

/** The scheduled backup's names and what it keeps (N15). */
class AutoBackupNamesTest {
    @Test fun aNameSortsByTimeAndIsRecognised() {
        val name = autoBackupName(LocalDateTime.of(2026, 11, 19, 9, 30))
        assertEquals("Clean auto backup 2026-11-19 0930.zip", name)
        assertTrue(isAutoBackupName(name))
        assertTrue(autoBackupName(LocalDateTime.of(2026, 11, 19, 10, 5)) > name)
        assertTrue(!isAutoBackupName("Clean backup 2026-11-19.zip"))
        assertTrue(!isAutoBackupName("holiday.zip"))
    }

    @Test fun onlyTheNewestFiveAreKeptAndOtherFilesAreNeverNamed() {
        val mine = (1..7).map { autoBackupName(LocalDateTime.of(2026, 11, it, 8, 0)) }
        val names = mine + listOf("holiday.zip", "Clean backup 2026-01-01.zip")
        assertEquals(mine.take(2).reversed(), autoBackupsToDelete(names))
        assertEquals(emptyList(), autoBackupsToDelete(mine.take(5)))
        assertEquals(mine.take(4).reversed(), autoBackupsToDelete(mine.take(6), keep = 2))
    }

    @Test fun theFrequenciesAreReadBack() {
        assertEquals(AutoBackupFrequency.DAILY, AutoBackupFrequency.of("daily"))
        assertEquals(7L, AutoBackupFrequency.WEEKLY.days)
        assertNull(AutoBackupFrequency.of(null))
        assertNull(AutoBackupFrequency.of("off"))
    }
}
