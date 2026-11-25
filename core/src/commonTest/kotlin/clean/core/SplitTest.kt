package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitTest {
    private val primary = mapOf(
        "Bench" to setOf(Muscle.CHEST), "Row" to setOf(Muscle.LATS, Muscle.UPPER_BACK), "Squat" to setOf(Muscle.QUADS, Muscle.GLUTES),
        "Dead" to setOf(Muscle.HAMSTRINGS), "Crunch" to setOf(Muscle.ABS), "Mixed" to setOf(Muscle.CHEST, Muscle.LATS),
    )

    private fun workout(date: String, exercise: String, vararg sets: SetType) =
        buildWorkouts(rowsFor(1, date, "w", 60, listOf(NewBlock(exercise, sets.map { NewSet(it, 50.0, 8) }))))[0]

    private fun split(vararg w: Workout, since: String = "2025-01-01") = trainingSplit(w.toList(), since) { primary[it].orEmpty() }

    @Test
    fun `sets are shared out by main muscle and warm-ups do not count`() {
        val n = SetType.NORMAL
        val s = split(
            workout("2025-02-01 10:00:00", "Bench", SetType.WARMUP, n, n, n, n),
            workout("2025-02-02 10:00:00", "Row", n, n, n, n),
            workout("2025-02-03 10:00:00", "Squat", n, n),
        )
        assertEquals(TrainingSplit(40, 40, 20), s)
    }

    @Test
    fun `an exercise across two groups splits its sets and abs count for nothing`() {
        val n = SetType.NORMAL
        val s = split(workout("2025-02-01 10:00:00", "Mixed", n, n, n, n), workout("2025-02-02 10:00:00", "Crunch", n, n, n))
        assertEquals(TrainingSplit(50, 50, 0), s)
    }

    @Test
    fun `only the window counts the percents add up to 100 and nothing gives null`() {
        val n = SetType.NORMAL
        val s = split(
            workout("2024-12-01 10:00:00", "Squat", n, n, n), workout("2025-02-01 10:00:00", "Bench", n), workout("2025-02-02 10:00:00", "Row", n),
            workout("2025-02-03 10:00:00", "Squat", n),
        )!!
        assertEquals(100, s.push + s.pull + s.legs)
        assertEquals(34, s.of(SplitGroup.PUSH))
        assertNull(split(workout("2025-02-01 10:00:00", "Crunch", n)))
        assertNull(split())
    }
}
