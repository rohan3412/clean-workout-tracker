package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the Google Health names (2026-11-23, `core/LibraryGoogleHealth.kt`): the exercises added from its picker must
 * not disturb what the library already did. Run with the rest of `:core:jvmTest` on CI. The names themselves are checked against
 * `tools/googlehealth/names.txt` in the JVM-only [GoogleHealthNamesTest].
 */
class GoogleHealthMappingTest {
    @Test fun noDuplicateNamesAndEveryOldNameIsStillHere() {
        assertEquals(emptyList(), LIBRARY.groupBy { it.name }.filter { it.value.size > 1 }.keys.toList())
        // The lookup by letter case and hyphens (BY_NORM in Library.kt) must not have two exercises for one spelling.
        assertEquals(emptyList(), LIBRARY.groupBy { it.name.lowercase().replace('-', ' ').replace(Regex("""\s+"""), " ").trim() }.filter { it.value.size > 1 }.keys.toList())
        assertEquals(emptyList(), NAMES_BEFORE.filter { libraryExercise(it) == null })
        assertEquals(emptyList(), NAMES_BEFORE.filter { canonicalName(it) != null })
        assertTrue(LIBRARY.size >= NAMES_BEFORE.size + 500, "only ${LIBRARY.size - NAMES_BEFORE.size} exercises were added")
    }

    @Test fun noExistingOtherNameChangedItsTarget() {
        val changed = ALIAS_SNAPSHOT.lines().filter { it.isNotEmpty() }.mapNotNull { line ->
            val other = line.substringBefore(" => ")
            val was = line.substringAfter(" => ")
            val now = canonicalName(other)
            if (now == was) null else "$other: was $was, now $now"
        }
        assertEquals(emptyList(), changed)
    }

    @Test fun otherNamesLeadToTheRightExercise() {
        val pairs = listOf(
            "Barbell Pendlay row" to "Pendlay Row (Barbell)",
            "Archer press-up" to "Archer Push Up",
            "Banded chest fly" to "Chest Fly (Band)",
            "Cable chop" to "Wood Chop (Cable)",
            "Barbell back squat" to "Squat (Barbell)",
            "Kettlebell single-arm clean" to "Clean (Kettlebell)",
            "Safety bar squat" to "Squat (Safety Bar)",
            "Back squat with safety bar" to "Squat (Safety Bar)",
            "TRX pull-up" to "Pull Up (Suspension)",
            "Barbell Spoto bench press" to "Bench Press - Spoto (Barbell)",
            "Banded woodchops" to "Wood Chop (Band)",
            "Leg abductor" to "Hip Abductor (Machine)",
            "Ab wheel rollout" to "Ab Wheel",
            "Treadmill run" to "Running (Treadmill)",
            "Dumbbell front squat" to "Front Squat (Dumbbell)",
            "Wide-grip cable lat pulldown with bench" to "Lat Pulldown - Wide Grip (Cable)",
        )
        assertEquals(emptyList(), pairs.filter { (google, library) -> (if (libraryExercise(google) != null) google else canonicalName(google)) != library })
    }

    @Test fun anExerciseDoneOneSideAtATimeIsOneEntry() {
        // docs/left-right.md: the side is how a set is logged, not part of the exercise's name.
        assertEquals("Neck Rotation", canonicalName("Neck rotation (right)"))
        assertEquals("Neck Rotation", canonicalName("Neck rotations"))
        assertEquals("Shoulder Stretch", canonicalName("Shoulder stretch (right)"))
        assertEquals("Jumping Bulgarian Split Squat", canonicalName("Jumping Bulgarian split squat (left) with bench"))
        assertEquals("Bulgarian Split Squat Jump", canonicalName("Bulgarian split squat jumps (right) with bench"))
        assertEquals(emptyList(), LIBRARY.filter { it.name.endsWith("(Left)") || it.name.endsWith("(Right)") }.map { it.name })
    }

    @Test fun stretchesAreTimedDrillsAreCardioAndVariationsJoinTheirGroup() {
        assertEquals(LoadType.TIMED, libraryExercise("Camel Pose")?.loadType)
        assertEquals(LoadType.TIMED, libraryExercise("Calf Stretch")?.loadType)
        assertEquals(LoadType.CARDIO, libraryExercise("Shuttle Runs")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, libraryExercise("Jumping Bulgarian Split Squat")?.loadType)
        assertEquals(LoadType.NORMAL, libraryExercise("Squat (Safety Bar)")?.loadType)
        assertEquals("Squat", libraryOverall("Squat (Safety Bar)"))
        assertEquals("Front Squat", libraryOverall("Front Squat (Dumbbell)"))
        assertEquals("Deadlift", libraryOverall("Sumo Deadlift (Dumbbell)"))
        assertEquals("Dip", libraryOverall("Chest Dip - Forward Leaning"))
    }

    @Test fun libraryResultsFindExercisesByGoogleSpellings() {
        val entries = LIBRARY.map { LibraryEntry(it.name, otherNames(it.name)) }
        fun found(query: String): List<String> = rankLibrary(query, entries).let { r -> (r.done + r.more).map { it.name } }
        assertTrue("Hip Abductor (Machine)" in found("leg abductor"), "leg abductor")
        assertTrue("Squat (Safety Bar)" in found("back squat with safety bar"), "back squat with safety bar")
        assertTrue("Neck Rotation" in found("neck rotations"), "neck rotations")
        assertTrue("Archer Push Up" in found("archer press-up"), "archer press-up")
        assertTrue("Camel Pose" in found("camel pose"), "camel pose")
        assertNotNull(libraryExercise("Windshield Wipers"))
        assertNull(libraryExercise("windshield wipers"))
    }
}
