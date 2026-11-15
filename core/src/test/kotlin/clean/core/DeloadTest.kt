package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeloadTest {
    private val today = LocalDate.parse("2025-06-18") // a Wednesday; the week in progress began on Monday 2025-06-16
    private val monday = weekStart(today)
    private var number = 0

    /** One workout in the week [back] weeks ago (0 is this one), each exercise with that many working sets. */
    private fun week(back: Int, sets: Int, vararg exercises: String): Workout {
        val day = monday.minusWeeks(back.toLong())
        val blocks = (exercises.toList().ifEmpty { listOf("Press") }).map { e -> NewBlock(e, List(sets) { NewSet(SetType.NORMAL, 50.0, 8) }) }
        return buildWorkouts(rowsFor(++number, "$day 10:00:00", "w", 60, blocks))[0]
    }

    /** Twenty weeks of the same load, with some weeks (by weeks back) set to another number of sets. */
    private fun log(overrides: Map<Int, Int> = emptyMap(), base: Int = 12, weeks: Int = 20) = (1..weeks).map { week(it, overrides[it] ?: base) }
    private fun lighter(back: Int) = mapOf(back to 6)

    private val rested = Muscle.entries.associateWith { 0.0 }
    private fun tired(n: Int) = Muscle.entries.withIndex().associate { (i, m) -> m to if (i < n) 0.9 else 0.0 }

    private fun run(workouts: List<Workout>, goals: Set<String> = emptySet(), progress: (String) -> List<Progress> = { emptyList() }, fatigue: Map<Muscle, Double> = rested) =
        deloadSignals(workouts, today, goals, progress, fatigue)

    private fun kinds(d: Deload) = d.findings.map { it.signal }

    /** Eight sessions with no new best: stalled. */
    private val stuck = List(8) { Progress("2025-01-0${it + 1}", 50.0, 100.0, null, null, null, null, null) }

    @Test
    fun `a steady log with a lighter week lately shows nothing`() {
        val d = run(log(lighter(4)))
        assertEquals(emptyList(), kinds(d))
        assertTrue(!d.suggested)
    }

    @Test
    fun `no lighter week in eight weeks is a signal, and a light week among them clears it`() {
        assertEquals(listOf(DeloadSignal.NO_LIGHT_WEEK), kinds(run(log())))
        assertEquals(emptyList(), kinds(run(log(lighter(8)))))
        // Seven weeks of 10 and one of x: the mean is (70 + x) / 8, so 70% of it is 6.7 at most when x = 6 or 7.
        fun withWeek(x: Int) = (1..8).map { week(it, if (it == 3) x else 10) } + (9..14).map { week(it, 10) }
        assertEquals(emptyList(), kinds(run(withWeek(6))))
        assertEquals(listOf(DeloadSignal.NO_LIGHT_WEEK), kinds(run(withWeek(7))))
    }

    @Test
    fun `with under eight weeks of history there is nothing to compare`() {
        assertEquals(emptyList(), kinds(run((1..5).map { week(it, 12) })))
        assertEquals(emptyList(), kinds(run(emptyList())))
    }

    @Test
    fun `three weeks 25 percent above the eight before each is a rise, a near miss and two are not`() {
        // The eight weeks before each week include the other risen ones, so 14 clears (13.75 needed in the first) and 13 does not.
        fun rise(last: List<Int>) = last.withIndex().map { (i, sets) -> week(i + 1, sets) } + (4..11).map { week(it, 10) }
        assertTrue(DeloadSignal.VOLUME_RISE in kinds(run(rise(listOf(14, 14, 14)))))
        assertTrue(DeloadSignal.VOLUME_RISE !in kinds(run(rise(listOf(13, 13, 13)))))
        assertTrue(DeloadSignal.VOLUME_RISE !in kinds(run(rise(listOf(20, 20, 10)))))
        assertTrue(DeloadSignal.VOLUME_RISE in kinds(run(rise(listOf(20, 20, 20)))))
    }

    @Test
    fun `a rise needs eleven complete weeks of history`() {
        val short = (1..3).map { week(it, 20) } + (4..10).map { week(it, 10) }
        assertTrue(DeloadSignal.VOLUME_RISE !in kinds(run(short)))
    }

    @Test
    fun `four fatigued muscles is a signal and three is not`() {
        assertTrue(DeloadSignal.FATIGUED in kinds(run(log(lighter(2)), fatigue = tired(4))))
        assertTrue(DeloadSignal.FATIGUED !in kinds(run(log(lighter(2)), fatigue = tired(3))))
        // Recovering is not fatigued.
        assertTrue(DeloadSignal.FATIGUED !in kinds(run(log(lighter(2)), fatigue = Muscle.entries.associateWith { 0.4 })))
    }

    @Test
    fun `three stalled exercises among the goals is a signal and two is not`() {
        val names = listOf("A", "B", "C")
        val stalledNames: (String) -> List<Progress> = { if (it in names) stuck else emptyList() }
        val d = run(log(lighter(2)), goals = names.toSet(), progress = stalledNames)
        assertTrue(DeloadSignal.STALLED in kinds(d))
        assertEquals(names, d.findings.first { it.signal == DeloadSignal.STALLED }.exercises)
        assertTrue(DeloadSignal.STALLED !in kinds(run(log(lighter(2)), goals = setOf("A", "B"), progress = stalledNames)))
    }

    @Test
    fun `only the ten most trained exercises are looked at when there is no goal`() {
        // Ex1 has the most sets, Ex11 the fewest.
        val workouts = (1..11).map { i -> week(1, 12 - i, "Ex$i") }
        fun with(vararg stalled: String) = run(workouts, progress = { if (it in stalled) stuck else emptyList() })
        assertTrue(DeloadSignal.STALLED in kinds(with("Ex8", "Ex9", "Ex10")))
        assertTrue(DeloadSignal.STALLED !in kinds(with("Ex9", "Ex10", "Ex11")))
    }

    @Test
    fun `two signals suggest a lighter week and one does not`() {
        assertTrue(!run(log(lighter(2))).suggested)
        val one = run(log())
        assertEquals(1, one.findings.size)
        assertTrue(!one.suggested)
        val two = run(log(), fatigue = tired(5))
        assertEquals(setOf(DeloadSignal.NO_LIGHT_WEEK, DeloadSignal.FATIGUED), kinds(two).toSet())
        assertTrue(two.suggested)
    }
}
