package clean.core

import clean.core.time.*
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Fatigue, strength and the sets of a muscle by side, as the body figure's limbs read them (docs/left-right.md, "Counting rules"). */
class SidesBodyTest {
    private val now = LocalDateTime.parse("2025-06-10T12:00:00")
    private val biceps = { _: String -> MuscleUse(primary = setOf(Muscle.BICEPS)) }

    private var number = 0
    private fun logged(at: LocalDateTime, block: NewBlock) =
        buildWorkouts(rowsFor(++number, STRONG_DATE_FORMAT.format(at), "w", 3600, listOf(block)))[0]

    private fun sets(count: Int, weight: Double = 20.0, reps: Int = 10) = List(count) { NewSet(SetType.NORMAL, weight, reps) }

    @Test
    fun `ordinary work reads the same on both sides as for the muscle`() {
        val log = listOf(
            logged(now.minusDays(40), NewBlock("Curl", sets(1, 100.0, 8))),
            logged(now.minusDays(1), NewBlock("Curl", sets(4, 100.0, 8))),
        )
        val group = fatigue(log, now, biceps)
        val sided = fatigueBySide(log, now, biceps)
        for (m in Muscle.entries) {
            assertEquals(group.getValue(m), sided.getValue(m).left, 0.0)
            assertEquals(group.getValue(m), sided.getValue(m).right, 0.0)
        }
        assertTrue(group.getValue(Muscle.BICEPS) > FATIGUE_RECOVERING)
        val last = lastTrained(log, biceps)
        assertEquals(last, lastTrainedBySide(log, biceps).getValue(Side.LEFT))
        assertEquals(last, lastTrainedBySide(log, biceps).getValue(Side.RIGHT))
        val strength = retainedStrength(log, now, biceps)
        for (m in Muscle.entries) assertEquals(SideCredit(strength.getValue(m), strength.getValue(m)), retainedStrengthBySide(log, now, biceps).getValue(m))
    }

    @Test
    fun `complete rounds tire both sides as the muscle is tired`() {
        val log = listOf(logged(now.minusDays(1), NewBlock("Curl", sets(8, 20.0, 10), paired = true)))
        val sided = fatigueBySide(log, now, biceps).getValue(Muscle.BICEPS)
        assertEquals(fatigue(log, now, biceps).getValue(Muscle.BICEPS), sided.left, 1e-9)
        assertEquals(sided.left, sided.right, 0.0)
    }

    @Test
    fun `work on the left only leaves the right side ready and untrained`() {
        val order = List(4) { Side.LEFT }
        val log = listOf(logged(now.minusHours(3), NewBlock("Curl", sets(4), paired = true, shapes = shapesOf(order))))
        val sided = fatigueBySide(log, now, biceps).getValue(Muscle.BICEPS)
        // Four left sets of 20 x 10 count in full for the left: 4 x 200 x (20 / 26.67)^1.5 against 2000, faded by three hours.
        val stimulus = 4 * 200 * (20 / (20 * (1 + 10 / 30.0))).pow(1.5) / 2000
        assertEquals(1 - exp(-stimulus * 0.5.pow(3.0 / 36)), sided.left, 1e-9)
        assertEquals(0.0, sided.right, 0.0)
        assertEquals("ready", fatigueWord(sided.right))
        val last = lastTrainedBySide(log, biceps)
        assertTrue(Muscle.BICEPS in last.getValue(Side.LEFT))
        assertNull(last.getValue(Side.RIGHT)[Muscle.BICEPS])
        val strength = retainedStrengthBySide(log, now, biceps).getValue(Muscle.BICEPS)
        assertEquals(1.0, strength.left, 0.0)
        assertEquals(STRENGTH_FLOOR, strength.right, 0.0)
        // Every other muscle is as the group function has it.
        assertEquals(0.0, fatigueBySide(log, now, biceps).getValue(Muscle.CHEST).left, 0.0)
    }

    @Test
    fun `a right set after ordinary work tires the right side more`() {
        val log = listOf(
            logged(now.minusDays(1), NewBlock("Curl", sets(3))),
            logged(now.minusHours(2), NewBlock("Curl", sets(3), paired = true, shapes = shapesOf(List(3) { Side.RIGHT }))),
        )
        val sided = fatigueBySide(log, now, biceps).getValue(Muscle.BICEPS)
        assertTrue(sided.right > sided.left)
        assertTrue(sided.left > 0.0)
    }

    @Test
    fun `the shortfall names the lagging side in whole percent of the larger`() {
        assertEquals(SideShortfall(Side.RIGHT, 25), SideCredit(24.0, 18.0).shortfall())
        assertEquals(SideShortfall(Side.LEFT, 25), SideCredit(18.0, 24.0).shortfall())
        assertNull(SideCredit(10.0, 10.0).shortfall())
        assertNull(SideCredit(0.0, 0.0).shortfall())
        assertNull(SideCredit(1000.0, 999.0).shortfall())
        assertEquals("R −25 %", SideCredit(24.0, 18.0).shortfall().phrase())
        assertEquals("L −8 %", SideCredit(12.0, 13.0).shortfall().phrase())
        assertEquals("even", SideCredit(3.0, 3.0).shortfall().phrase())
        assertEquals("right 25 percent less", SideCredit(24.0, 18.0).shortfall().spoken())
    }

    @Test
    fun `sides work and the weeks of a muscle by side`() {
        val plain = logged(LocalDateTime.parse("2025-06-02T10:00:00"), NewBlock("Curl", sets(3)))
        val paired = logged(LocalDateTime.parse("2025-06-10T10:00:00"), NewBlock("Curl", sets(3), paired = true))
        assertFalse(hasSideWork(listOf(plain), null))
        assertTrue(hasSideWork(listOf(plain, paired), null))
        assertFalse(hasSideWork(listOf(plain, paired), LocalDateTime.parse("2025-06-11T00:00:00")))
        val weeks = muscleSideWeeks(listOf(plain, paired), null, biceps, Muscle.BICEPS)
        assertEquals(SideCredit(3.0, 3.0), weeks[LocalDate.parse("2025-06-02")])
        // Three sets left, right, left: the left twice, the right once.
        assertEquals(SideCredit(2.0, 1.0), weeks[LocalDate.parse("2025-06-09")])
    }
}
