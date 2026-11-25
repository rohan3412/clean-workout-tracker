package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests for items in the private issue register that no older test guarded; each KDoc names its id. */
class IssuesCoreTest {
    /**
     * I-048 (2026-10-13): lots of exercises imported from Strong counted as custom ones, without their animation, because
     * the library did not have Strong's spelling ("Chest Fly (Machine)", "Lat Pulldown (Cable)"), and informal names such as
     * the Bulgarian split squat had no place. The names Rohan listed are library names; another app's spelling leads to them.
     */
    @Test fun namesRohanListedAreLibraryNamesNotCustomOnes() {
        for (name in listOf("Chest Fly (Machine)", "Lat Pulldown (Cable)", "Bulgarian Split Squat (Dumbbell)", "Split Squat (Dumbbell)", "Iso-Lateral Row (Machine)")) {
            assertNotNull(libraryExercise(name), "$name is not in the library")
        }
        // Another app's spelling of a built-in exercise leads to the library's own, and the movement keeps its group.
        assertEquals("Chest Fly (Cable)", canonicalName("Seated Chest Flys (Cable)"))
        assertEquals("Lat Pulldown", libraryOverall("Wide Grip Lat Pulldown (Cable)"))
    }
}
