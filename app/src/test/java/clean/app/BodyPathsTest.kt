package clean.app

import org.junit.experimental.categories.Category
import clean.core.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The figure's outlines know which limb they are: a muscle or head drawn on one side has the same number on the other. */
@Category(Regression::class)
class BodyPathsTest {
    @Test
    fun `each muscle and head has as many outlines on the left as on the right`() {
        for ((name, view) in listOf("front" to BODY_FRONT, "back" to BODY_BACK)) {
            val counts = view.filter { it.side != null }.groupingBy { Triple(it.muscle, it.head, it.side) }.eachCount()
            assertTrue("$name view has sided outlines", counts.isNotEmpty())
            for ((key, n) in counts) {
                val other = if (key.third == Side.LEFT) Side.RIGHT else Side.LEFT
                assertEquals("$name ${key.first} ${key.second}", n, counts[Triple(key.first, key.second, other)])
            }
        }
    }

    @Test
    fun `only the neck's centre, the head and the hair lie across the midline`() {
        val across = (BODY_FRONT + BODY_BACK).filter { it.side == null }
        assertTrue(across.all { it.muscle == null || it.muscle == clean.core.Muscle.NECK })
        assertNotNull(BODY_FRONT.firstOrNull { it.side == Side.RIGHT })
    }
}
