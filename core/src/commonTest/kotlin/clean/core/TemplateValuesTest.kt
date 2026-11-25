package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateValuesTest {
    private val n = SetType.NORMAL

    private fun block(name: String, vararg sets: Pair<SetType, SetTarget?>) = TemplateBlock(name, sets.map { it.first }, null, sets.map { it.second })
    private fun t(weight: Double?, reps: Int?) = SetTarget(weight, reps)

    @Test
    fun `an unticked set is valid when it has what its exercise logs typed`() {
        assertTrue(isValidSet(LoadType.NORMAL, "80", "5"))
        assertFalse(isValidSet(LoadType.NORMAL, "", "5"))
        assertFalse(isValidSet(LoadType.NORMAL, "80", ""))
        assertFalse(isValidSet(LoadType.NORMAL, "", ""))
        // Bodyweight, assisted and timed sets may have no weight.
        assertTrue(isValidSet(LoadType.BODYWEIGHT, "", "12"))
        assertTrue(isValidSet(LoadType.ASSISTED, "20", "8"))
        assertFalse(isValidSet(LoadType.BODYWEIGHT, "10", ""))
        assertTrue(isValidSet(LoadType.TIMED, "", "45"))
        assertFalse(isValidSet(LoadType.TIMED, "20", ""))
        // Cardio wants kilometres and minutes both.
        assertTrue(isValidSet(LoadType.CARDIO, "5", "30"))
        assertFalse(isValidSet(LoadType.CARDIO, "5", ""))
        assertFalse(isValidSet(LoadType.CARDIO, "", "30"))
        // A decimal comma is a number; a fraction of a rep is not.
        assertTrue(isValidSet(LoadType.NORMAL, "82,5", "5"))
        assertFalse(isValidSet(LoadType.NORMAL, "80", "5.5"))
    }

    @Test
    fun `more weight or the same weight for more reps is an increase`() {
        assertTrue(isIncrease(t(80.0, 5), t(82.5, 3)))
        assertTrue(isIncrease(t(80.0, 5), t(80.0, 6)))
        assertFalse(isIncrease(t(80.0, 5), t(77.5, 8)))
        assertFalse(isIncrease(t(80.0, 5), t(80.0, 5)))
        assertFalse(isIncrease(t(80.0, 5), t(80.0, 4)))
        // Bodyweight: no weight on either side, reps decide.
        assertTrue(isIncrease(t(null, 10), t(null, 11)))
        assertFalse(isIncrease(t(null, 10), t(null, 10)))
    }

    @Test
    fun `a missing target is beaten by any value`() {
        assertTrue(isIncrease(null, t(60.0, null)))
        assertTrue(isIncrease(t(null, null), t(null, 8)))
        assertFalse(isIncrease(null, null))
        assertFalse(isIncrease(null, t(null, null)))
        assertFalse(isIncrease(t(80.0, 5), null))
    }

    @Test
    fun `increases are counted per set by position and kind`() {
        val template = listOf(
            block("Bench Press", SetType.WARMUP to t(40.0, 8), n to t(80.0, 5), n to t(80.0, 5)),
            block("Row", n to t(60.0, 8)),
        )
        val done = listOf(
            // Warm-up the same, first working set heavier, second lower, a fourth set beyond the template.
            block("Bench Press", SetType.WARMUP to t(40.0, 8), n to t(82.5, 3), n to t(77.5, 6), n to t(100.0, 10)),
            // Kind differs at position 0: skipped even though it is heavier.
            block("Row", SetType.DROP to t(70.0, 8)),
            // Not in the template at all.
            block("Squat", n to t(100.0, 5)),
        )
        assertEquals(listOf(ValueIncrease(0, 0, 1)), valueIncreases(template, done))
    }

    @Test
    fun `repeated exercises are matched in order of appearance`() {
        val template = listOf(block("Curl", n to t(10.0, 10)), block("Press", n to t(40.0, 8)), block("Curl", n to t(8.0, 12)))
        val done = listOf(block("Curl", n to t(10.0, 10)), block("Curl", n to t(8.0, 13)))
        assertEquals(listOf(ValueIncrease(2, 1, 0)), valueIncreases(template, done))
    }

    @Test
    fun `updating values writes only the increases and keeps the shape`() {
        val template = listOf(block("Bench Press", n to t(80.0, 5), n to null, n to t(80.0, 5)))
        val done = listOf(block("Bench Press", n to t(82.5, 5), n to t(70.0, 8), n to t(75.0, 5)))
        val updated = withValuesUpdated(template, done)
        // The first set went up, the second had no values (any value beats it), the third was lower and stays.
        assertEquals(listOf(t(82.5, 5), t(70.0, 8), t(80.0, 5)), updated[0].targets)
        assertEquals(template[0].sets, updated[0].sets)
    }

    @Test
    fun `updating the template takes the shape of the workout and keeps what it was not beaten by`() {
        val template = listOf(block("Bench Press", n to t(80.0, 5), n to t(80.0, 5)), block("Row", n to t(60.0, 8)))
        val done = listOf(
            block("Bench Press", n to t(77.5, 6), n to t(85.0, 3), n to t(70.0, 10)),
            block("Fly", n to t(10.0, 12)),
        )
        val updated = withStructureUpdated(template, done)
        assertEquals(listOf("Bench Press", "Fly"), updated.map { it.exercise })
        assertEquals(listOf(t(80.0, 5), t(85.0, 3), t(70.0, 10)), updated[0].targets)
        assertEquals(listOf(t(10.0, 12)), updated[1].targets)
    }

    @Test
    fun `a template with no values holds an empty list`() {
        assertEquals(emptyList(), tidyTargets(listOf(null, SetTarget(null, null), null)))
        assertEquals(listOf(null, t(10.0, null)), tidyTargets(listOf(null, t(10.0, null), null)))
    }

    @Test
    fun `the shape change counts exercises and sets of the exercises that stay`() {
        val before = listOf(block("A", n to null, n to null), block("B", n to null), block("C", n to null))
        val after = listOf(block("A", n to null, n to null, n to null, n to null), block("C"), block("D", n to null, n to null))
        assertEquals(ShapeChange(addedExercises = 1, removedExercises = 1, addedSets = 2, removedSets = 1), shapeChange(before, after))
        assertFalse(sameShape(before, after))
        assertTrue(sameShape(before, before.map { it.copy(targets = listOf(t(1.0, 1))) }))
        assertFalse(sameShape(before, before.reversed()))
    }
}
