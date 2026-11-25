package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgressionTest {
    private val scheme = Progression(8, 12, 2.5)

    private fun session(vararg sets: NewSet) =
        buildWorkouts(rowsFor(1, "2025-01-01 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0].blocks[0].sets

    private fun n(weight: Double, reps: Int, rpe: String = "") = NewSet(SetType.NORMAL, weight, reps, rpe = rpe)

    @Test
    fun `every set at the top of the range goes up a step and back to the bottom`() {
        assertEquals(listOf(Target(62.5, 8), Target(62.5, 8), Target(62.5, 8)), nextFor(session(n(60.0, 12), n(60.0, 12), n(60.0, 13)), scheme))
    }

    @Test
    fun `one set short adds a rep to each short set and the others hold`() {
        assertEquals(listOf(Target(60.0, 12), Target(60.0, 12), Target(60.0, 11)), nextFor(session(n(60.0, 12), n(60.0, 11), n(60.0, 10)), scheme))
    }

    @Test
    fun `a set at RPE 9_5 or to failure repeats and a set at 9 does not`() {
        val last = session(n(60.0, 10, "9.5"), NewSet(SetType.FAILURE, 60.0, 9), n(60.0, 8, "9"), n(60.0, 8, "10"))
        assertEquals(listOf(Target(60.0, 10), Target(60.0, 9), Target(60.0, 9), Target(60.0, 8)), nextFor(last, scheme))
    }

    @Test
    fun `sets of different weights are handled one by one and the step up needs one shared weight`() {
        assertEquals(listOf(Target(60.0, 12), Target(50.0, 12)), nextFor(session(n(60.0, 12), n(50.0, 11)), scheme))
        // All at the top but at two weights: no step up.
        assertEquals(listOf(Target(60.0, 12), Target(50.0, 12)), nextFor(session(n(60.0, 12), n(50.0, 12)), scheme))
    }

    @Test
    fun `no history gives no targets and warm-ups and drop sets are ignored`() {
        assertEquals(emptyList(), nextFor(emptyList(), scheme))
        assertEquals(emptyList(), nextFor(session(NewSet(SetType.WARMUP, 20.0, 8), NewSet(SetType.DROP, 40.0, 8)), scheme))
        // The warm-up at 12 does not make the working set look finished, and the drop set is no target.
        assertEquals(listOf(Target(60.0, 9)), nextFor(session(NewSet(SetType.WARMUP, 20.0, 12), n(60.0, 8), NewSet(SetType.DROP, 40.0, 5)), scheme))
    }

    @Test
    fun `only lifts where heavier is harder get targets`() {
        val last = session(n(30.0, 12))
        assertEquals(listOf(Target(32.5, 8)), nextFor(last, scheme, LoadType.BODYWEIGHT))
        for (type in listOf(LoadType.ASSISTED, LoadType.TIMED, LoadType.CARDIO)) assertEquals(emptyList(), nextFor(last, scheme, type), "$type")
    }

    @Test
    fun `a scheme is stored as text and read back and bad text is no scheme`() {
        assertEquals("8-12@2.5", scheme.encode())
        assertEquals(scheme, Progression.decode("8-12@2.5"))
        assertNull(Progression.decode("12-8@2.5"))
        assertNull(Progression.decode("8-12@0"))
        assertNull(Progression.decode(null))
        assertNull(Progression.decode("junk"))
    }

    // --- Goals ---

    private fun s(date: String, vararg sets: NewSet) = Session("$date 10:00:00", buildWorkouts(rowsFor(1, "$date 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0].blocks[0].sets)

    @Test
    fun `a goal is read from its stored text`() {
        assertEquals(100.0 to 5, parseGoal("100.0x5"))
        assertNull(parseGoal(""))
        assertNull(parseGoal(null))
        assertNull(parseGoal("100"))
    }

    @Test
    fun `a goal is reached on the first session whose estimate gets there`() {
        val sessions = listOf(s("2025-01-01", n(90.0, 5)), s("2025-02-01", n(97.0, 5)), s("2025-03-01", n(100.0, 5)), s("2025-04-01", n(105.0, 5)))
        assertEquals("2025-03-01 10:00:00", goalReachedOn(sessions, 100.0, 5, EPLEY_RULE, LoadType.NORMAL) { null })
        assertNull(goalReachedOn(sessions, 120.0, 5, EPLEY_RULE, LoadType.NORMAL) { null })
        // A heavier set for fewer reps gets there too: 110 x 3 is about 121 against 100 x 5's 116.7.
        assertEquals("2025-01-01 10:00:00", goalReachedOn(listOf(s("2025-01-01", n(110.0, 3))), 100.0, 5, EPLEY_RULE, LoadType.NORMAL) { null })
    }

    @Test
    fun `a goal above the rep limit needs a set of that weight for those reps`() {
        val rule = EPLEY_RULE
        val sessions = listOf(s("2025-01-01", n(40.0, 20)), s("2025-02-01", n(50.0, 15)), s("2025-03-01", n(50.0, 20)))
        assertNull(goalOneRm(45.0, 20, rule, LoadType.NORMAL, null))
        assertEquals("2025-03-01 10:00:00", goalReachedOn(sessions, 45.0, 20, rule, LoadType.NORMAL) { null })
        assertNull(goalReachedOn(sessions, 60.0, 20, rule, LoadType.NORMAL) { null })
        // Assisted: the same help or less is as good.
        assertEquals("2025-01-01 10:00:00", goalReachedOn(listOf(s("2025-01-01", n(30.0, 20))), 40.0, 20, rule, LoadType.ASSISTED) { 80.0 })
    }

    @Test
    fun `warm-ups never meet a goal a set to failure does`() {
        assertNull(goalReachedOn(listOf(s("2025-01-01", NewSet(SetType.WARMUP, 100.0, 5))), 100.0, 5, EPLEY_RULE, LoadType.NORMAL) { null })
        assertNotNull(goalReachedOn(listOf(s("2025-01-01", NewSet(SetType.FAILURE, 100.0, 5))), 100.0, 5, EPLEY_RULE, LoadType.NORMAL) { null })
    }
}
