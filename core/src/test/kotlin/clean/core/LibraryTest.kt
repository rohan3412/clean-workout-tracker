package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryTest {
    @Test
    fun `every built-in exercise has a unique name and at least one main muscle`() {
        assertTrue(LIBRARY.size > 1400, "only ${LIBRARY.size} exercises")
        assertEquals(emptyList(), LIBRARY.groupBy { it.name }.filter { it.value.size > 1 }.keys.toList())
        assertEquals(emptyList(), LIBRARY.filter { it.muscles.primary.isEmpty() && it.loadType != LoadType.CARDIO }.map { it.name })
        assertEquals(emptyList(), LIBRARY.filter { it.name != it.name.trim() || it.name.isEmpty() }.map { it.name })
    }

    @Test
    fun `names follow the Name (Equipment) convention and carry their load type`() {
        assertEquals(setOf(Muscle.LATS), libraryExercise("Lat Pulldown (Machine)")?.muscles?.primary)
        assertEquals(LoadType.NORMAL, libraryExercise("Lat Pulldown (Machine)")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, libraryExercise("Pull Up")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, libraryExercise("Pull Up (Weighted)")?.loadType)
        assertEquals(LoadType.ASSISTED, libraryExercise("Pull Up (Assisted)")?.loadType)
        assertEquals(LoadType.ASSISTED, libraryExercise("Triceps Dip (Assisted)")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, libraryExercise("Squat (Bodyweight)")?.loadType)
        assertEquals(LoadType.NORMAL, libraryExercise("Squat (Barbell)")?.loadType)
        assertEquals(LoadType.NORMAL, libraryExercise("Back Extension (Machine)")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, libraryExercise("Back Extension")?.loadType)
        assertEquals(setOf(Muscle.TRICEPS), libraryExercise("Triceps Pushdown (Cable - Straight Bar)")?.muscles?.primary)
        assertEquals(setOf(Muscle.LATS), libraryExercise("Lat Row (Single Arm) (Cable)")?.muscles?.primary)
        assertNull(libraryExercise("lat pulldown (machine)"))
    }

    @Test
    fun `exercises Strong lists are here under Strong's spelling`() {
        val names = listOf(
            "Ab Wheel", "Around the World", "Ball Slams", "Bench Press - Close Grip (Barbell)", "Bench Press - Wide Grip (Barbell)",
            "Bent Over Row - Underhand (Barbell)", "Cable Crunch", "Cable Kickback", "Cable Twist", "Calf Press on Leg Press",
            "Calf Press on Seated Leg Press", "Clean and Jerk (Barbell)", "Cross Body Crunch", "Crunch (Stability Ball)",
            "Deadlift High Pull (Barbell)", "Flat Knee Raise", "Flat Leg Raise", "Hack Squat", "Hack Squat (Barbell)", "High Knee Skips",
            "Incline Bench Press (Cable)", "Incline Row (Dumbbell)", "Jump Shrug (Barbell)", "Jumping Jack", "Kettlebell Turkish Get Up",
            "Kipping Pull Up", "Kneeling Pulldown (Band)", "Knees to Elbows", "Lat Pulldown - Underhand (Band)",
            "Lat Pulldown - Underhand (Cable)", "Lat Pulldown - Wide Grip (Cable)", "Lateral Box Jump", "Oblique Crunch",
            "Overhead Press (Cable)", "Power Clean", "Press Under (Barbell)", "Pull Up (Band)", "Push Press", "Push Up (Band)",
            "Reverse Curl (Band)", "Reverse Grip Concentration Curl (Dumbbell)", "Seated Palms Up Wrist Curl (Dumbbell)",
            "Seated Wide-Grip Row (Cable)", "Side Bend (Band)", "Single Leg Bridge", "Squat Row (Band)", "Step-up",
            "Straight Leg Deadlift (Band)", "Strict Military Press (Barbell)", "Sumo Deadlift High Pull (Barbell)", "Trap Bar Deadlift",
            "Triceps Extension", "Wide Pull Up",
            "Aerobics", "Battle Ropes", "Climbing", "Cycling", "Cycling (Indoor)", "Elliptical Machine", "Hiking", "Jump Rope",
            "Rowing (Machine)", "Running", "Running (Treadmill)", "Skating", "Skiing", "Snowboarding", "Stretching", "Swimming",
            "Walking", "Yoga", "Plank", "Reverse Plank", "Side Plank",
        )
        assertEquals(emptyList(), names.filter { libraryExercise(it) == null })
        assertEquals(LoadType.TIMED, libraryExercise("Plank")?.loadType)
        assertEquals(LoadType.CARDIO, libraryExercise("Running (Treadmill)")?.loadType)
    }

    @Test
    fun `other names lead to the library's name, for a movement and all its equipment variants`() {
        assertEquals("Skullcrusher (Barbell)", canonicalName("Skull Crusher (Barbell)"))
        assertEquals("Skullcrusher (Dumbbell)", canonicalName("skull crusher (DB)"))
        assertEquals("Hip Abductor (Machine)", canonicalName("Hip Abduction (Machine)"))
        assertEquals("Reverse Fly (Cable)", canonicalName("Rear Delt Reverse Fly (Cable)"))
        assertEquals("Trap Bar Deadlift", canonicalName("Deadlift (Trap bar)"))
        assertEquals("Lat Pulldown - Underhand (Cable)", canonicalName("Reverse Grip Lat Pulldown (Cable)"))
        assertEquals("Triceps Pushdown (Cable - Rope)", canonicalName("Triceps Rope Pushdown"))
        assertEquals("Standing Calf Raise (Smith Machine)", canonicalName("Standing Calf Raise (Smith)"))
        assertEquals("Pull Up", canonicalName("pull-up"))
        assertEquals("Step-up", canonicalName("Step Up"))
        assertEquals("Overhead Press (Barbell)", canonicalName("OHP (Barbell)"))
        // Equipment in front of the movement is the same exercise written the other way round.
        assertEquals("Bench Press (Barbell)", canonicalName("Barbell Bench Press"))
        assertEquals("Skullcrusher (EZ Bar)", canonicalName("EZ Bar Skull Crusher"))
        assertEquals("Lateral Raise (Dumbbell)", canonicalName("DB Lateral Raises"))
        // A library name is not an alias of anything, and an unknown name is nobody's alias.
        assertNull(canonicalName("Lat Pulldown (Machine)"))
        assertNull(canonicalName("Lat Row (Single Arm) (Cable) (Kneeling)"))
        assertTrue("Skull Crusher (Barbell)" in otherNames("Skullcrusher (Barbell)"))
        assertTrue("Hip Abduction (Machine)" in otherNames("Hip Abductor (Machine)"))
        assertEquals(LoadType.NORMAL, suggest("Rear Delt Reverse Fly (Machine)")?.loadType)
        assertEquals(setOf(Muscle.SHOULDERS, Muscle.UPPER_BACK), suggest("Rear Delt Reverse Fly (Machine)")?.muscles?.primary)
    }

    @Test
    fun `exercises Hevy lists are here, by name or by alias`() {
        val names = listOf(
            "21s Bicep Curl", "Ab Scissors", "Air Bike", "Assisted Pistol Squats", "Back Extension (Weighted Hyperextension)",
            "Band Pullaparts", "Behind the Back Curl (Cable)", "Belt Squat (Machine)", "Bicep Curl (Suspension)", "Boxing",
            "Burpee Over the Bar", "Butterfly (Pec Deck)", "Cable Twist (Down to up)", "Calf Extension (Machine)", "Chest Fly (Suspension)",
            "Chest Supported Y Raise (Dumbbell)", "Clean and Press", "Deadlift (Trap bar)", "Decline Bench Press (Machine)", "Dragonfly",
            "Dumbbell Snatch", "EZ Bar Biceps Curl", "Farmers Walk", "Feet Up Bench Press (Barbell)", "Front Lever Hold",
            "Front Lever Raise", "Gorilla Row (Kettlebell)", "Handstand Hold", "Hex Press (Dumbbell)", "HIIT", "Hollow Rock",
            "Jack Knife (Suspension)", "Kettlebell Halo", "Knee Raise Parallel Bars", "Landmine 180", "Lateral Band Walks", "Lateral Squat",
            "Low Row (Suspension)", "Lying Neck Curls (Weighted)", "Negative Pull Up", "One Arm Push Up", "Overhead Dumbbell Lunge",
            "Partial Glute Bridge (Barbell)", "Pilates", "Pinwheel Curl (Dumbbell)", "Plate Squeeze (Svend Press)", "Push Up - Close Grip",
            "Rear Delt Reverse Fly (Dumbbell)", "Ring Dip", "Rope Straight Arm Pulldown", "Seated Cable Row - Bar Grip", "Shoulder Taps",
            "Single Leg Standing Calf Raise (Barbell)", "Sissy Squat (Weighted)", "Spiderman", "Sprints", "Squat (Suspension)",
            "Stair Machine (Steps)", "Sternum Pull up (Gironda)", "Sumo Squat (Kettlebell)", "Toe Touch", "Treadmill",
            "Triceps Extension (Suspension)", "Vertical Traction (Machine)", "Warm Up", "Wide-Elbow Triceps Press (Dumbbell)", "Zottman Curl (Dumbbell)",
        )
        assertEquals(emptyList(), names.filter { libraryExercise(it) == null && canonicalName(it) == null })
        // Every alias points at an exercise that exists.
        assertEquals(setOf(Muscle.NECK), libraryExercise("Lying Neck Curls")?.muscles?.primary)
    }

    @Test
    fun `helping muscles are split into supporting and minor`() {
        // Biceps move the load in a pull-up; forearms only hold on.
        val pullUp = libraryExercise("Pull Up")!!.muscles
        assertEquals(setOf(Muscle.LATS), pullUp.primary)
        assertTrue(Muscle.BICEPS in pullUp.secondary)
        assertTrue(Muscle.FOREARMS in pullUp.minor)
        // The lower back works hard in a deadlift but only braces in a squat.
        assertTrue(Muscle.LOWER_BACK in libraryExercise("Deadlift (Barbell)")!!.muscles.primary)
        val squat = libraryExercise("Squat (Barbell)")!!.muscles
        assertTrue(Muscle.LOWER_BACK in squat.minor && Muscle.ABS in squat.minor && Muscle.HAMSTRINGS in squat.secondary)
        assertTrue(Muscle.TRAPS in libraryExercise("Lateral Raise (Dumbbell)")!!.muscles.minor)
        // No muscle sits at two levels in one exercise.
        assertEquals(emptyList(), LIBRARY.filter { e -> (e.muscles.primary intersect e.muscles.secondary).isNotEmpty() || ((e.muscles.primary + e.muscles.secondary) intersect e.muscles.minor).isNotEmpty() }.map { it.name })
    }

    @Test
    fun `a library exercise is suggested as defined, not guessed from its words`() {
        // The word rules alone would call this a bodyweight lift; the library says it is a machine.
        assertEquals(LoadType.NORMAL, suggest("Back Extension (Machine)")?.loadType)
        assertEquals(setOf(Muscle.GLUTES, Muscle.HAMSTRINGS), suggest("Sumo Deadlift (Barbell)")?.muscles?.primary)
    }

    @Test
    fun `the library groups a movement's grips, stances and equipment as variations`() {
        assertEquals("Pull Up", libraryOverall("Pull Up (Wide Grip)"))
        assertEquals("Pull Up", libraryOverall("Chin Up"))
        assertTrue("Lat Pulldown - Wide Grip (Cable)" in libraryVariations("Lat Pulldown"))
        assertTrue("Back Extension - Glute Focus" in libraryVariations("Back Extension"))
        assertEquals(setOf(Muscle.GLUTES), libraryExercise("Back Extension - Glute Focus")?.muscles?.primary)
        // Another app's name for a built-in exercise lands in the same group.
        assertEquals("Lat Pulldown", libraryOverall("Wide Grip Lat Pulldown (Cable)"))
        // An exercise with no sibling is not a group, and neither is a name the library does not know.
        assertEquals(null, libraryOverall("Cable Crossover"))
        assertEquals(null, libraryOverall("Underwater Basket Weaving"))
    }
}
