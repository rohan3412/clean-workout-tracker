package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VariationsTest {
    @Test
    fun `a machine row can be done one side at a time and with another grip`() {
        val made = libraryVariations("Iso-Lateral Row (Machine)")
        assertTrue("Iso-Lateral Row (Machine) (Single Arm)" in made, made.toString())
        assertTrue("Iso-Lateral Row (Machine) (Pronated)" in made)
        assertTrue("Iso-Lateral Row (Machine) (Pause)" in made)
    }

    @Test
    fun `a pulldown gets grip widths, written once`() {
        val group = libraryVariations("Lat Pulldown")
        assertTrue("Lat Pulldown (Machine)" in group)
        assertTrue("Lat Pulldown (Machine) (Wide Grip)" in group, group.toString())
        assertTrue("Lat Pulldown (Machine) (Close Grip)" in group)
        // The cable one has a hand-written wide grip, so the rules do not make a second.
        assertTrue("Lat Pulldown - Wide Grip (Cable)" in group)
        assertFalse("Lat Pulldown (Cable) (Wide Grip)" in group)
        // The hand-written ones come first.
        assertTrue(group.indexOf("Lat Pulldown - Wide Grip (Cable)") < group.indexOf("Lat Pulldown (Machine) (Wide Grip)"))
        // Asking with a member or a rule-made variation gives the same group.
        assertEquals(group, libraryVariations("Lat Pulldown (Machine)"))
        assertEquals(group, libraryVariations("Lat Pulldown (Machine) (Wide Grip)"))
    }

    @Test
    fun `a front raise gets its grips and one arm at a time`() {
        val made = generatedVariations("Front Raise (Dumbbell)")
        for (label in listOf("Supinated", "Pronated", "Single Arm")) assertTrue("Front Raise (Dumbbell) ($label)" in made, made.toString())
        assertTrue("Front Raise (Dumbbell) (Supinated)" in libraryVariations("Front Raise"))
    }

    @Test
    fun `a leg exercise gets stances and feet, never a grip or an arm`() {
        val squat = generatedVariations("Squat (Barbell)")
        assertTrue("Squat (Barbell) (Heels Elevated)" in squat, squat.toString())
        // Wide stance and pause are hand-written for the barbell squat.
        assertFalse("Squat (Barbell) (Wide Stance)" in squat)
        assertFalse("Squat (Barbell) (Pause)" in squat)
        val press = generatedVariations("Seated Leg Press (Machine)")
        for (label in listOf("Feet High", "Wide Stance", "Single Leg")) assertTrue("Seated Leg Press (Machine) ($label)" in press, press.toString())
        val calf = generatedVariations("Seated Calf Raise (Machine)")
        assertTrue("Seated Calf Raise (Machine) (Toes Out)" in calf, calf.toString())
        assertEquals(emptyList(), (squat + press + calf + libraryVariations("Leg Extension")).filter { "Grip" in it || "Arm" in it || "nated" in it })
    }

    @Test
    fun `an arm exercise gets no stance or leg`() {
        val curl = generatedVariations("Bicep Curl (Dumbbell)")
        assertTrue("Bicep Curl (Dumbbell) (Single Arm)" in curl, curl.toString())
        assertEquals(emptyList(), curl.filter { "Leg" in it || "Stance" in it || "Feet" in it || "Toes" in it })
        // Dumbbells have no grip width, and the hammer curl is in the library as the neutral grip.
        assertEquals(emptyList(), curl.filter { "Wide" in it || "Neutral" in it })
    }

    @Test
    fun `back extensions and ab work`() {
        val group = libraryVariations("Back Extension")
        assertTrue("Back Extension - Glute Focus" in group)
        assertTrue("Back Extension (Machine) (Lower Back Focus)" in group, group.toString())
        assertFalse("Back Extension (Weighted)" in generatedVariations("Back Extension"))
        assertTrue("Reverse Crunch (Weighted)" in libraryVariations("Reverse Crunch"), libraryVariations("Reverse Crunch").toString())
    }

    @Test
    fun `a rule-made variation belongs with the exercise it was made from`() {
        assertEquals("Lat Pulldown", libraryOverall("Lat Pulldown (Machine) (Wide Grip)"))
        assertEquals("Lat Pulldown (Machine)", variationBase("Lat Pulldown (Machine) (Wide Grip)"))
        // An exercise with no group of its own is the overall exercise of its variations.
        assertNull(libraryOverall("Iso-Lateral Row (Machine)"))
        assertEquals("Iso-Lateral Row (Machine)", libraryOverall("Iso-Lateral Row (Machine) (Single Arm)"))
        // A hand-written name is not a rule-made one.
        assertNull(variationBase("Pull Up (Wide Grip)"))
        assertEquals("Pull Up", libraryOverall("Pull Up (Wide Grip)"))
    }

    @Test
    fun `a name that only looks like a variation is not grouped`() {
        assertNull(libraryOverall("Lat Pulldown (Machine) (My Grip)"))
        assertNull(libraryOverall("Lat Pulldown (Machine) (wide grip)"))
        assertNull(libraryOverall("My Pulldown (Wide Grip)"))
        // A real label, but not one the rules give a squat.
        assertNull(libraryOverall("Squat (Barbell) (Wide Grip)"))
        // Not two labels at once either.
        assertNull(libraryOverall("Lat Pulldown (Machine) (Wide Grip) (Pause)"))
        assertEquals(emptyList(), libraryVariations("Underwater Basket Weaving"))
        assertEquals(emptyList(), generatedVariations("Running"))
    }

    @Test
    fun `muscles and load type come from the exercise it was made from`() {
        val made = suggest("Lat Pulldown (Machine) (Wide Grip)")!!
        assertEquals(libraryExercise("Lat Pulldown (Machine)")!!.muscles, made.muscles)
        assertEquals(LoadType.NORMAL, made.loadType)
        // The words alone would call this a bodyweight lift.
        assertEquals(LoadType.NORMAL, suggest("Back Extension (Machine) (Lower Back Focus)")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, suggest("Reverse Crunch (Weighted)")?.loadType)
        assertEquals(setOf(Muscle.CALVES), suggest("Seated Calf Raise (Machine) (Toes Out)")?.muscles?.primary)
    }

    @Test
    fun `across the whole library nothing is doubled and every variation leads back`() {
        val all = LIBRARY.associate { it.name to generatedVariations(it.name) }
        assertEquals(emptyList(), all.filter { (name, made) -> made.any { variationBase(it) != name } }.keys.toList())
        assertEquals(emptyList(), all.values.filter { it.size != it.toSet().size })
        // None is an exercise the library already has, under that name or another.
        assertEquals(emptyList(), all.values.flatten().filter { libraryExercise(it) != null || canonicalName(it) != null })
        val groups = LIBRARY.map { libraryOverall(it.name) ?: it.name }.distinct()
        assertEquals(emptyList(), groups.filter { g -> libraryVariations(g).let { it.size != it.toSet().size } })
        // Most strength exercises get some, and none gets a wall of them.
        val sizes = all.values.map { it.size }
        assertTrue(sizes.count { it > 0 } > LIBRARY.size * 2 / 3, "only ${sizes.count { it > 0 }} exercises have variations")
        assertTrue(sizes.max() <= 14, "an exercise has ${sizes.max()} variations")
    }
}
