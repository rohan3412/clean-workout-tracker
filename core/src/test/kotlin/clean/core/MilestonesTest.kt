package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MilestonesTest {
    private fun of(kind: MilestoneKind, all: List<Milestone>, target: Long) = all.first { it.kind == kind && it.target == target }

    @Test
    fun `a count is reached on the day it passes the target`() {
        val days = (1..12).map { "2026-09-%02d".format(it) }
        val all = milestones(days, List(12) { 1000.0 }, List(12) { 1.0 }, 3)
        assertEquals("2026-09-01", of(MilestoneKind.WORKOUTS, all, 1).reachedOn)
        assertEquals("2026-09-10", of(MilestoneKind.WORKOUTS, all, 10).reachedOn)
        assertNull(of(MilestoneKind.WORKOUTS, all, 25).reachedOn)
        // Ten thousand kg is the tenth workout; a hundred thousand is not reached.
        assertEquals("2026-09-10", of(MilestoneKind.VOLUME, all, 10_000).reachedOn)
        assertNull(of(MilestoneKind.VOLUME, all, 100_000).reachedOn)
        assertEquals("2026-09-10", of(MilestoneKind.PRS, all, 10).reachedOn)
    }

    @Test
    fun `a streak is reached by the best run and keeps no day`() {
        val all = milestones(emptyList(), emptyList(), emptyList(), 5)
        assertTrue(of(MilestoneKind.STREAK, all, 4).reached)
        assertNull(of(MilestoneKind.STREAK, all, 4).reachedOn)
        assertTrue(!of(MilestoneKind.STREAK, all, 8).reached)
    }

    @Test
    fun `latest reached is newest first and skips undated ones`() {
        val days = (1..30).map { "2026-09-%02d".format(it) }
        val all = milestones(days, List(30) { 5000.0 }, List(30) { 0.0 }, 8)
        val latest = latestReached(all, 3)
        // 25th workout and 100,000 kg (the 20th) and 10th workout and 10,000 kg (the 2nd) ...
        assertEquals(listOf("2026-09-25", "2026-09-20", "2026-09-10"), latest.map { it.reachedOn })
        assertEquals(MilestoneKind.WORKOUTS, latest[0].kind)
    }

    @Test
    fun `next is the unreached one nearest done`() {
        val days = (1..8).map { "2026-09-%02d".format(it) }
        val all = milestones(days, List(8) { 100.0 }, List(8) { 1.0 }, 1)
        val next = nextMilestone(all)!!
        // 8 of 10 workouts is 0.8; 8 of 10 PRs is 0.8 too and the tie goes to the smaller target, which is equal, so the first listed.
        assertEquals(10L, next.target)
        assertEquals(0.8, next.progress, 1e-9)
        assertNull(nextMilestone(listOf(Milestone(MilestoneKind.WORKOUTS, 1, 5.0, "2026-09-01"))))
    }

    @Test
    fun `titles read as words`() {
        val v = { kg: Double -> "%,d kg".format(kg.toLong()) }
        assertEquals("100th workout", Milestone(MilestoneKind.WORKOUTS, 100, 0.0, null).title(v))
        assertEquals("First workout", Milestone(MilestoneKind.WORKOUTS, 1, 0.0, null).title(v))
        assertEquals("1,000,000 kg lifted", Milestone(MilestoneKind.VOLUME, 1_000_000, 0.0, null).title(v))
        assertEquals("52-week streak", Milestone(MilestoneKind.STREAK, 52, 0.0, null).title(v))
        assertEquals("250th PR", Milestone(MilestoneKind.PRS, 250, 0.0, null).title(v))
    }
}
