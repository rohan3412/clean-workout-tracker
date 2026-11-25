package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class GoalProgressTest {
    private val today = LocalDate.parse("2025-03-01")

    private fun d(text: String) = LocalDate.parse(text)

    /** A weekly line: [first] on [start], rising [step] a week. */
    private fun weekly(start: String, first: Double, step: Double, count: Int) = (0 until count).map { d(start).plusWeeks(it.toLong()) to first + step * it }

    @Test
    fun `a rising line gives the day it reaches the target`() {
        // 80 kg rising 2 a week from 2025-01-18 is 80 + 2 * 6 = 92 on 03-01; 100 is four more weeks.
        val points = weekly("2025-01-18", 80.0, 2.0, 7)
        assertEquals(d("2025-03-29"), projectDate(points, 100.0, today))
    }

    @Test
    fun `a flat falling or too short line gives no date`() {
        assertNull(projectDate(weekly("2025-01-18", 80.0, 0.0, 7), 100.0, today))
        assertNull(projectDate(weekly("2025-01-18", 80.0, -1.0, 7), 100.0, today))
        // Two points, or three within two weeks of each other.
        assertNull(projectDate(weekly("2025-02-15", 80.0, 2.0, 2), 100.0, today))
        assertNull(projectDate(listOf(d("2025-02-20") to 80.0, d("2025-02-24") to 81.0, d("2025-02-28") to 82.0), 100.0, today))
        // Points older than eight weeks are not used.
        assertNull(projectDate(weekly("2024-10-01", 80.0, 2.0, 7), 100.0, today))
    }

    @Test
    fun `a date more than two years away is not shown`() {
        val slow = weekly("2025-01-18", 80.0, 0.01, 7)
        assertNull(projectDate(slow, 100.0, today))
        assertTrue(projectDate(slow, 80.5, today) != null)
    }

    @Test
    fun `a falling goal runs the other way`() {
        val points = weekly("2025-01-18", 90.0, -1.0, 7)
        // 84 on 03-01, losing a kilo a week: 80 is four weeks on.
        assertEquals(d("2025-03-29"), projectDate(points, 80.0, today, rising = false))
        assertNull(projectDate(weekly("2025-01-18", 90.0, 1.0, 7), 80.0, today, rising = false))
    }

    @Test
    fun `the day reads d MMM with the year when it is not this one`() {
        assertEquals("12 Feb", projectedDay(d("2025-02-12"), today))
        assertEquals("12 Feb 2026", projectedDay(d("2026-02-12"), today))
        assertEquals("On track for about 12 Feb", projectedLine(d("2025-02-12"), today))
    }

    private fun s(date: String, weight: Double, reps: Int) =
        Session("$date 10:00:00", buildWorkouts(rowsFor(1, "$date 10:00:00", "w", 60, listOf(NewBlock("x", listOf(NewSet(SetType.NORMAL, weight, reps))))))[0].blocks[0].sets)

    @Test
    fun `an exercise goal's progress is the best estimate over the goal's capped and whole once met`() {
        val rule = EPLEY_RULE
        val sessions = listOf(s("2025-02-01", 80.0, 5), s("2025-02-15", 85.0, 5), s("2025-03-01", 90.0, 5))
        val p = exerciseGoalProgress(sessions, 120.0, 5, rule, LoadType.NORMAL, { null }, today)!!
        assertEquals(90.0 / 120.0, p.fraction, 1e-9)
        assertEquals(false, p.reached)
        val met = exerciseGoalProgress(sessions, 90.0, 5, rule, LoadType.NORMAL, { null }, today)!!
        assertEquals(1.0, met.fraction)
        assertTrue(met.reached)
        assertNull(met.projected)
        val past = exerciseGoalProgress(sessions, 60.0, 5, rule, LoadType.NORMAL, { null }, today)!!
        assertEquals(1.0, past.fraction)
    }

    @Test
    fun `an exercise goal projects from three sessions spanning two weeks`() {
        val rule = EPLEY_RULE
        val sessions = listOf(s("2025-02-01", 80.0, 5), s("2025-02-15", 85.0, 5), s("2025-03-01", 90.0, 5))
        val p = exerciseGoalProgress(sessions, 120.0, 5, rule, LoadType.NORMAL, { null }, today)!!
        // 5 kg per fortnight; 120 is 30 kg on, six fortnights.
        assertEquals(d("2025-05-24"), p.projected)
        assertNull(exerciseGoalProgress(sessions.take(2), 120.0, 5, rule, LoadType.NORMAL, { null }, today)!!.projected)
    }

    @Test
    fun `a goal above the rep limit is the most reps at the weight with no date and a timed exercise has none`() {
        val rule = EPLEY_RULE
        val sessions = listOf(s("2025-02-01", 50.0, 10), s("2025-03-01", 50.0, 15))
        val p = exerciseGoalProgress(sessions, 50.0, 20, rule, LoadType.NORMAL, { null }, today)!!
        assertEquals(0.75, p.fraction, 1e-9)
        assertNull(p.projected)
        assertNull(exerciseGoalProgress(sessions, 50.0, 20, rule, LoadType.TIMED, { null }, today))
    }

    @Test
    fun `a body weight goal is stored as target start and day and reads back`() {
        val goal = WeightGoal.set(80.0, 90.0, today)!!
        assertEquals("80.0|90.0|2025-03-01", goal.encode())
        assertEquals(goal, WeightGoal.decode(goal.encode()))
        assertNull(WeightGoal.decode(null))
        assertNull(WeightGoal.decode("80|90"))
        assertNull(WeightGoal.decode("80|80|2025-03-01"))
        assertNull(WeightGoal.decode("x|90|2025-03-01"))
        assertNull(WeightGoal.set(90.0, 90.0, today))
    }

    @Test
    fun `losing and gaining weight measure the share of the distance covered`() {
        val lose = WeightGoal(80.0, 90.0, d("2025-01-01"))
        assertTrue(lose.lose)
        assertEquals(0.5, lose.progress(85.0), 1e-9)
        assertEquals(0.0, lose.progress(92.0))
        assertEquals(1.0, lose.progress(78.0))
        val gain = WeightGoal(70.0, 60.0, d("2025-01-01"))
        assertEquals(false, gain.lose)
        assertEquals(0.3, gain.progress(63.0), 1e-9)
        assertEquals(1.0, gain.progress(75.0))
        assertTrue(gain.reached(70.0) && !gain.reached(69.9) && lose.reached(80.0) && !lose.reached(80.1))
    }

    @Test
    fun `a body weight goal projects from the last eight weeks of weigh-ins`() {
        val goal = WeightGoal(80.0, 90.0, d("2025-01-01"))
        val weighIns = weekly("2025-01-18", 90.0, -1.0, 7)
        val p = goal.progressOf(weighIns, today)!!
        assertEquals(0.6, p.fraction, 1e-9)
        assertEquals(d("2025-03-29"), p.projected)
        // Going the wrong way: no date.
        assertNull(goal.progressOf(weekly("2025-01-18", 90.0, 1.0, 7), today)!!.projected)
        // Met: whole, no date. No weigh-ins: nothing.
        val met = goal.progressOf(listOf(d("2025-02-28") to 79.0), today)!!
        assertEquals(1.0, met.fraction)
        assertNull(met.projected)
        assertNull(goal.progressOf(emptyList(), today))
    }
}
