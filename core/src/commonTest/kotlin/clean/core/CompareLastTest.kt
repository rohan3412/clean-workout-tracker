package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** The "Compared with last time" rule (docs/domain.md). */
class CompareLastTest {
    private fun n(weight: Double?, reps: Int?) = NewSet(SetType.NORMAL, weight, reps)

    private fun day(day: String, vararg blocks: NewBlock) =
        buildWorkouts(rowsFor(1, "$day 10:00:00", "w", 60, blocks.toList()))[0]

    private fun block(vararg sets: NewSet, name: String = "x", paired: Boolean = false) = NewBlock(name, sets.toList(), paired)

    private fun compare(log: List<Workout>, at: Int, type: LoadType = LoadType.NORMAL) =
        compareWithLast(log[at], { sessionsOf(log, it) }, { type })

    private val kg = { d: Double -> "${shortNumber(d)} kg" }

    @Test
    fun `weighted lift that went up in weight and down in reps`() {
        val log = listOf(day("2025-01-01", block(n(60.0, 8))), day("2025-01-08", block(n(62.5, 7))))
        val c = compare(log, 1)
        assertEquals(Verdict.UP, c.rows[0].verdict)
        assertEquals("+2.5 kg, −1 rep", c.rows[0].words(kg))
        assertEquals(1, c.improved)
        assertEquals(1, c.compared)
    }

    @Test
    fun `more reps at the same weight`() {
        val log = listOf(day("2025-01-01", block(n(60.0, 8))), day("2025-01-08", block(n(60.0, 10))))
        assertEquals("+2 reps", compare(log, 1).rows[0].words(kg))
    }

    @Test
    fun `same and down`() {
        val log = listOf(day("2025-01-01", block(n(60.0, 8))), day("2025-01-08", block(n(60.0, 8))), day("2025-01-15", block(n(57.5, 8))))
        assertEquals(Verdict.SAME, compare(log, 1).rows[0].verdict)
        assertEquals("same", compare(log, 1).rows[0].words(kg))
        val down = compare(log, 2)
        assertEquals(Verdict.DOWN, down.rows[0].verdict)
        assertEquals("down −2.5 kg", down.rows[0].words(kg))
        assertEquals(0, down.improved)
    }

    @Test
    fun `a first time is listed but not counted`() {
        val log = listOf(day("2025-01-01", block(n(60.0, 8), name = "a")), day("2025-01-08", block(n(65.0, 8), name = "a"), block(n(20.0, 8), name = "b")))
        val c = compareWithLast(log[1], { sessionsOf(log, it) }, { LoadType.NORMAL })
        assertEquals(listOf("a", "b"), c.rows.map { it.exercise })
        assertEquals(Verdict.FIRST, c.rows[1].verdict)
        assertEquals("First time", c.rows[1].words(kg))
        assertEquals(1, c.compared)
        assertEquals(1, c.improved)
    }

    @Test
    fun `bodyweight compares reps`() {
        val log = listOf(day("2025-01-01", block(n(null, 10))), day("2025-01-08", block(n(null, 12))))
        val c = compare(log, 1, LoadType.BODYWEIGHT)
        assertEquals(Verdict.UP, c.rows[0].verdict)
        assertEquals("+2 reps", c.rows[0].words(kg))
    }

    @Test
    fun `timed compares seconds and cardio distance`() {
        val timed = listOf(day("2025-01-01", NewBlock("x", listOf(NewSet(SetType.NORMAL, seconds = 60.0)))), day("2025-01-08", NewBlock("x", listOf(NewSet(SetType.NORMAL, seconds = 75.0)))))
        assertEquals("+15 s", compare(timed, 1, LoadType.TIMED).rows[0].words(kg))
        val cardio = listOf(
            day("2025-01-01", NewBlock("x", listOf(NewSet(SetType.NORMAL, distanceMeters = 5000.0, seconds = 1800.0)))),
            day("2025-01-08", NewBlock("x", listOf(NewSet(SetType.NORMAL, distanceMeters = 5000.0, seconds = 1700.0)))),
        )
        val c = compare(cardio, 1, LoadType.CARDIO)
        assertEquals(Verdict.UP, c.rows[0].verdict)
        assertEquals("−100 s", c.rows[0].words(kg))
    }

    @Test
    fun `an old workout compares with the session before it not the latest`() {
        val log = listOf(day("2025-01-01", block(n(50.0, 8))), day("2025-01-08", block(n(55.0, 8))), day("2025-01-15", block(n(80.0, 8))))
        val c = compare(log, 1)
        assertEquals(Verdict.UP, c.rows[0].verdict)
        assertEquals("+5 kg", c.rows[0].words(kg))
    }

    @Test
    fun `warm-ups are not the best set`() {
        val log = listOf(day("2025-01-01", block(n(60.0, 8))), day("2025-01-08", NewBlock("x", listOf(NewSet(SetType.WARMUP, 100.0, 5), n(60.0, 8)))))
        assertEquals(Verdict.SAME, compare(log, 1).rows[0].verdict)
    }

    @Test
    fun `left and right compare the weaker side`() {
        // Before: left 50x8, right 40x8 (weaker 40). Today: left 45x8, right 45x8 (weaker 45): up although the best single set went down.
        val log = listOf(
            day("2025-01-01", block(n(50.0, 8), n(40.0, 8), paired = true)),
            day("2025-01-08", block(n(45.0, 8), n(45.0, 8), paired = true)),
        )
        val c = compare(log, 1)
        assertEquals(Verdict.UP, c.rows[0].verdict)
        assertEquals("+5 kg", c.rows[0].words(kg))
    }
}
