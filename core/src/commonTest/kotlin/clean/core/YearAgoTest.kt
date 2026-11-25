package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/** The "A year ago" card's rules (docs/design.md, Home cards). */
class YearAgoTest {
    private val today = LocalDate.of(2026, 10, 16)
    private val anchor = today.minusDays(365)

    private fun n(weight: Double, reps: Int) = NewSet(SetType.NORMAL, weight, reps)

    private fun day(date: LocalDate, number: Int, vararg blocks: NewBlock) =
        buildWorkouts(rowsFor(number, "$date 10:00:00", "w", 60, blocks.toList()))[0]

    private fun block(name: String, vararg sets: NewSet, paired: Boolean = false) = NewBlock(name, sets.toList(), paired)

    private fun lift(log: List<Workout>) =
        yearAgoLift(today, sessionsByExercise(log)) { _, s -> progressOf(s, EPLEY_RULE) }

    /** A single set of 1 rep is its own 1RM. */
    private fun one(name: String, date: LocalDate, kg: Double, number: Int) = day(date, number, block(name, n(kg, 1)))

    @Test
    fun `picks the lift with the most sessions and compares nearest then with best now`() {
        val log = listOf(
            one("Bench", anchor.minusDays(10), 70.0, 1), one("Bench", anchor.plusDays(2), 80.0, 2), one("Bench", today.minusDays(20), 85.0, 3),
            one("Bench", today.minusDays(2), 92.5, 4),
            one("Squat", anchor, 100.0, 5), one("Squat", today, 110.0, 6),
        )
        val r = assertNotNull(lift(log))
        assertEquals("Bench", r.exercise)
        assertEquals(anchor.plusDays(2), r.thenDate)
        assertEquals(80.0, r.then, 1e-9)
        assertEquals(92.5, r.now, 1e-9)
        assertEquals("+16 %", changePercent(r.change))
        assertEquals(listOf(80.0, 85.0, 92.5), r.series)
    }

    @Test
    fun `ties go A to Z`() {
        val log = listOf(one("Squat", anchor, 100.0, 1), one("Squat", today, 120.0, 2), one("Bench", anchor, 60.0, 3), one("Bench", today, 80.0, 4))
        assertEquals("Bench", lift(log)?.exercise)
    }

    @Test
    fun `window edges are inclusive at 30 days and out at 31`() {
        val inside = listOf(one("Bench", anchor.minusDays(30), 80.0, 1), one("Bench", today.minusDays(30), 100.0, 2))
        assertNotNull(lift(inside))
        val outThen = listOf(one("Bench", anchor.minusDays(31), 80.0, 1), one("Bench", today, 100.0, 2))
        assertNull(lift(outThen))
        val outNow = listOf(one("Bench", anchor, 80.0, 1), one("Bench", today.minusDays(31), 100.0, 2))
        assertNull(lift(outNow))
    }

    @Test
    fun `a lift change under 5 percent shows nothing 5 percent shows a drop shows`() {
        assertNull(lift(listOf(one("Bench", anchor, 100.0, 1), one("Bench", today, 104.0, 2))))
        assertNotNull(lift(listOf(one("Bench", anchor, 100.0, 1), one("Bench", today, 105.0, 2))))
        val down = assertNotNull(lift(listOf(one("Bench", anchor, 100.0, 1), one("Bench", today, 90.0, 2))))
        assertEquals("−10 %", changePercent(down.change))
    }

    @Test
    fun `left and right compare the weaker side`() {
        val then = day(anchor, 1, block("Row", n(100.0, 5), n(80.0, 5), paired = true))
        val now = day(today, 2, block("Row", n(110.0, 5), n(96.0, 5), paired = true))
        val r = assertNotNull(lift(listOf(then, now)))
        assertEquals(80.0 * (1 + 5 / 30.0), r.then, 1e-6)
        assertEquals(96.0 * (1 + 5 / 30.0), r.now, 1e-6)
    }

    @Test
    fun `totals fallback when no lift is in both windows`() {
        val log = listOf(
            one("Bench", anchor, 100.0, 1), one("Bench", anchor.plusDays(3), 100.0, 2),
            one("Squat", today.minusDays(1), 100.0, 3), one("Squat", today.minusDays(5), 100.0, 4), one("Squat", today.minusDays(9), 100.0, 5),
        )
        assertNull(lift(log))
        val t = assertNotNull(yearAgoTotals(today, log) { w -> w.blocks.sumOf { b -> b.sets.sumOf { it.weight ?: 0.0 } } })
        assertEquals(2, t.thenWorkouts)
        assertEquals(3, t.nowWorkouts)
        assertEquals(200.0, t.thenVolume, 1e-9)
        assertEquals(300.0, t.nowVolume, 1e-9)
    }

    @Test
    fun `totals under 20 percent or with nothing a year ago show nothing`() {
        val volume = { w: Workout -> w.blocks.sumOf { b -> b.sets.sumOf { it.weight ?: 0.0 } } }
        assertNull(yearAgoTotals(today, listOf(one("A", anchor, 100.0, 1), one("A", today, 119.0, 2)), volume))
        assertNotNull(yearAgoTotals(today, listOf(one("A", anchor, 100.0, 1), one("A", today, 120.0, 2)), volume))
        assertNull(yearAgoTotals(today, listOf(one("A", today, 120.0, 1)), volume))
    }
}
