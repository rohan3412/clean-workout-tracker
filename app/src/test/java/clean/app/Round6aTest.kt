package clean.app

import org.junit.experimental.categories.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 6a's pure parts: the rubber band of a swipe (D7, E14) and the plus's one spring (E19). */
@Category(Regression::class)
class Round6aTest {
    @Test fun rubberStartsAtOneToOneAndNeverPassesItsLimit() {
        assertEquals(0f, Motion.rubber(0f, 100f), 0f)
        assertEquals(1f, Motion.rubber(1f, 100f), 0.01f)
        var last = 0f
        for (raw in 1..400 step 50) {
            val shown = Motion.rubber(raw.toFloat(), 100f)
            assertTrue(shown > last && shown < 100f)
            assertTrue(shown < raw) // always heavier than the finger
            last = shown
        }
    }

    @Test fun swipeThresholdIsReachable() {
        // A 110 dp threshold with the row's limit of 2.5 x it: reached with a finger a little over 1.3 x the threshold.
        val t = 110f
        assertTrue(Motion.rubber(t * 1.5f, t * 2.5f) > t)
        assertTrue(Motion.rubber(t * 1.1f, t * 2.5f) < t)
    }

    @Test fun plusBumpIsSmall() {
        assertEquals(1.08f, Motion.PLUS_BUMP, 0f)
    }
}
