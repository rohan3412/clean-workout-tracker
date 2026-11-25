package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ConsistencyTest {
    private fun day(text: String) = LocalDate.parse(text)
    private fun w(day: String, time: String = "10:00:00") = Workout("1", "$day $time", "x", 60, emptyList())
    private val start = day("2025-01-06") // a Monday

    /** One week per character, oldest first: X is a met week, anything else a missed one; a final "+" is the week in progress, met. */
    private fun weeks(pattern: String) = pattern.mapIndexed { i, c ->
        val monday = start.plusWeeks(i.toLong())
        when (c) {
            '+' -> WeekMet(monday, true, true, monday)
            '?' -> WeekMet(monday, false, true, null)
            'X' -> WeekMet(monday, true, false, monday)
            else -> WeekMet(monday, false, false, null)
        }
    }

    /** One workout on a Wednesday of each of [count] weeks from [start]. */
    private fun weekly(count: Int) = (0 until count).map { w(start.plusWeeks(it.toLong()).plusDays(2).toString()) }

    @Test
    fun `a week meets its target or misses it`() {
        val log = listOf(w("2025-01-06"), w("2025-01-08"), w("2025-01-14"))
        val results = weekResults(log, listOf(TargetFrom(start, 2)), day("2025-01-29"))
        assertEquals(listOf(2, 1, 0, 0), results.map { it.count })
        assertEquals(listOf(true, false, false, false), results.map { it.met })
        assertEquals(day("2025-01-08"), results[0].metOn)
        assertNull(results[1].metOn)
        assertEquals(listOf(false, false, false, true), results.map { it.current })
    }

    @Test
    fun `no workout still gives the current week`() {
        val results = weekResults(emptyList(), emptyList(), day("2025-01-29"))
        assertEquals(1, results.size)
        assertEquals(DEFAULT_TARGET, results[0].target)
    }

    @Test
    fun `the week in progress neither breaks the run nor extends it unless met`() {
        assertEquals(2, replayStreak(weeks("XX?")).length)
        assertEquals(3, replayStreak(weeks("XX+")).length)
        assertEquals(WeekState.IN_PROGRESS, replayStreak(weeks("XX?")).states.last())
        // Missed last week and nothing yet this week: the run is gone.
        assertEquals(0, replayStreak(weeks("XX.?")).length)
    }

    @Test
    fun `a new target leaves the weeks before it scored as they were`() {
        val log = listOf(w("2025-01-06"), w("2025-01-08"), w("2025-01-14"), w("2025-01-16"), w("2025-01-21"), w("2025-01-23"))
        val old = listOf(TargetFrom(start, 2))
        val changed = withTarget(old, day("2025-01-20"), 3)
        assertEquals(listOf(TargetFrom(start, 2), TargetFrom(day("2025-01-20"), 3)), changed)
        val before = weekResults(log, old, day("2025-01-26"))
        val after = weekResults(log, changed, day("2025-01-26"))
        assertEquals(before.take(2), after.take(2))
        assertEquals(listOf(true, true, true), before.map { it.met })
        assertEquals(listOf(true, true, false), after.map { it.met })
        // Setting it again in the same week replaces the entry.
        assertEquals(listOf(TargetFrom(start, 2), TargetFrom(day("2025-01-20"), 4)), withTarget(changed, day("2025-01-20"), 4))
        // Weeks before the first entry use the first entry's.
        assertEquals(2, targetFor(listOf(TargetFrom(day("2025-02-03"), 2)), start))
        assertEquals(3, targetFor(changed, day("2025-02-03")))
    }

    @Test
    fun `the targets are written and read back`() {
        val list = listOf(TargetFrom(day("2026-10-12"), 3), TargetFrom(day("2026-11-02"), 4))
        assertEquals("2026-10-12:3;2026-11-02:4", encodeTargets(list))
        assertEquals(list, decodeTargets("2026-10-12:3;junk;2026-11-02:4"))
        assertEquals(emptyList(), decodeTargets(null))
    }

    @Test
    fun `the default target is a pace kept in four of five of the last twelve full weeks that had a workout`() {
        // Complete weeks of 2, 4 and 3 workouts, a week with none, and this week's (ignored) one.
        fun many(monday: String, n: Int) = (0 until n).map { w(day(monday).plusDays(it.toLong()).toString()) }
        val log = many("2025-01-06", 2) + many("2025-01-13", 4) + many("2025-01-27", 3) + many("2025-02-03", 6)
        assertEquals(2, defaultTarget(log, day("2025-02-05")))
        assertEquals(DEFAULT_TARGET, defaultTarget(emptyList(), day("2025-02-05")))
        // The lowest fifth of the weeks is let go.
        assertEquals(1, defaultTarget(many("2025-01-06", 1) + many("2025-01-13", 2), day("2025-02-05")))
        assertEquals(2, defaultTarget(many("2024-12-09", 1) + many("2024-12-16", 2) + many("2024-12-23", 2) + many("2025-01-06", 3) + many("2025-01-13", 4), day("2025-02-05")))
        // Never above seven, and weeks older than twelve are not looked at.
        assertEquals(7, defaultTarget(many("2025-01-06", 14), day("2025-01-20")))
        assertEquals(DEFAULT_TARGET, defaultTarget(many("2025-01-06", 5), day("2025-06-02")))
        // With nothing stored, scoring starts from the first workout's week at that pace.
        assertEquals(listOf(TargetFrom(start, 2)), targetsOrDefault(emptyList(), log, day("2025-02-05")))
    }

    @Test
    fun `a shield is banked after eight met weeks`() {
        val seven = replayStreak(weeks("XXXXXXX"))
        assertFalse(seven.shield)
        assertEquals(1, seven.weeksToShield)
        val eight = replayStreak(weeks("XXXXXXXX"))
        assertTrue(eight.shield)
        assertEquals(1, eight.banked)
        assertEquals(8, eight.length)
        // Held at most one: the next eight bank nothing more.
        val sixteen = replayStreak(weeks("XXXXXXXXXXXXXXXX"))
        assertEquals(1, sixteen.banked)
        assertTrue(sixteen.shield)
    }

    @Test
    fun `a missed week with a shield held is forgiven and does not count`() {
        val streak = replayStreak(weeks("XXXXXXXX.X"))
        assertEquals(9, streak.length)
        assertFalse(streak.shield)
        assertEquals(listOf(start.plusWeeks(8)), streak.forgiven)
        assertEquals(WeekState.FORGIVEN, streak.states[8])
        assertEquals(8, streak.runAfter[8])
        assertEquals(9, streak.best)
    }

    @Test
    fun `a second miss right after the forgiven one breaks the run`() {
        val streak = replayStreak(weeks("XXXXXXXX.."))
        assertEquals(0, streak.length)
        assertEquals(8, streak.best)
        assertEquals(start.plusWeeks(7), streak.bestEnd)
        assertEquals(WeekState.MISSED, streak.states[9])
        assertEquals(1, replayStreak(weeks("XXXXXXXX..X")).length)
    }

    @Test
    fun `a new shield needs eight more genuinely met weeks`() {
        val seven = replayStreak(weeks("XXXXXXXX.XXXXXXX"))
        assertFalse(seven.shield)
        assertEquals(15, seven.length)
        assertEquals(1, seven.weeksToShield)
        val eight = replayStreak(weeks("XXXXXXXX.XXXXXXXX"))
        assertTrue(eight.shield)
        assertEquals(16, eight.length)
        assertEquals(2, eight.banked)
        // A miss with no shield held breaks the run and the count toward the next one.
        val broken = replayStreak(weeks("XXXXXXX.XXXXX"))
        assertEquals(5, broken.length)
        assertFalse(broken.shield)
        assertEquals(7, broken.best)
    }

    @Test
    fun `the best streak keeps its end`() {
        val streak = replayStreak(weeks("XXX.XXXXX."))
        assertEquals(0, streak.length)
        assertEquals(5, streak.best)
        assertEquals(start.plusWeeks(8), streak.bestEnd)
        assertEquals(WeekState.MISSED, streak.states[3])
    }

    @Test
    fun `streak badges are earned on the day the week was met and stay earned`() {
        val log = weekly(13)
        val c = consistency(log, listOf(TargetFrom(start, 1)), day("2025-04-03"))
        assertEquals(13, c.streak.length)
        val month = c.awards.first { it.id == "streak-4" }
        assertEquals("1 month streak", month.title)
        assertEquals(day("2025-01-29"), month.earned)
        assertEquals(start.plusWeeks(3), month.weekOf)
        assertEquals(day("2025-04-02"), c.awards.first { it.id == "streak-13" }.earned)
        assertNull(c.awards.first { it.id == "streak-26" }.earned)
        // The streak breaks; the badges stay.
        val later = consistency(log, listOf(TargetFrom(start, 1)), day("2025-06-30"))
        assertEquals(0, later.streak.length)
        assertEquals(day("2025-01-29"), later.awards.first { it.id == "streak-4" }.earned)
        assertEquals(13, later.streak.best)
    }

    @Test
    fun `workout badges are earned on the day of the nth workout`() {
        val log = (0 until 50).map { w(day("2025-01-01").plusDays(it.toLong()).toString()) }
        val c = consistency(log, listOf(TargetFrom(start, 1)), day("2025-02-25"))
        assertEquals(day("2025-01-10"), c.awards.first { it.id == "workouts-10" }.earned)
        assertEquals("2025-01-10 10:00:00", c.awards.first { it.id == "workouts-10" }.workoutDate)
        assertEquals(day("2025-02-19"), c.awards.first { it.id == "workouts-50" }.earned)
        assertNull(c.awards.first { it.id == "workouts-100" }.earned)
        val next = nextMilestones(c.awards, c.streak.length, c.total)
        assertEquals(listOf("streak-13", "workouts-100"), next.map { it.award.id })
        assertEquals(5, next[0].remaining)
        assertEquals(50, next[1].remaining)
        assertEquals(0.5f, next[1].fraction)
    }

    @Test
    fun `the longest break and the usual days`() {
        val log = listOf(w("2025-01-01"), w("2025-01-03"), w("2025-01-20"), w("2025-01-22"))
        assertEquals(LongestBreak(17, "2025-01-03 10:00:00", "2025-01-20 10:00:00"), longestBreak(log))
        assertNull(longestBreak(listOf(w("2025-01-01"))))
        // A Wednesday, a Friday, a Monday and a Wednesday.
        assertEquals(listOf(1, 0, 2, 0, 1, 0, 0), usualDays(log))
    }

    @Test
    fun `a workout is credited with the target it reached`() {
        val targets = listOf(TargetFrom(day("2025-01-06"), 2))
        val log = listOf(w("2025-01-06"), w("2025-01-08"))
        val second = earnedBy(log, log[1], targets, day("2025-01-09"))
        assertTrue(second.targetHit)
        assertEquals(2, second.count)
        assertEquals(2, second.target)
        // A third in the same week reaches nothing new.
        val more = log + w("2025-01-09")
        assertFalse(earnedBy(more, more[2], targets, day("2025-01-09")).any)
    }

    @Test
    fun `a workout is credited with a new badge and a shield`() {
        val one = listOf(TargetFrom(start, 1))
        val four = weekly(4)
        val month = earnedBy(four, four.last(), one, day("2025-01-29"))
        assertEquals(listOf("streak-4"), month.awards.map { it.id })
        assertFalse(month.shield)
        val eight = weekly(8)
        val shield = earnedBy(eight, eight.last(), one, day("2025-02-26"))
        assertTrue(shield.shield)
        assertTrue(shield.awards.isEmpty())
        assertTrue(shield.targetHit)
    }
}
