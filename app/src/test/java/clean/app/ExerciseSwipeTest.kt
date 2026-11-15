package clean.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A swipe on an exercise's name in a workout removes it only in a direction whose set swipe is "delete". */
class ExerciseSwipeTest {
    @Test
    fun `only the delete swipe removes an exercise`() {
        assertTrue(removesExercise("delete"))
        assertFalse(removesExercise("finish"))
        assertFalse(removesExercise("off"))
    }
}
