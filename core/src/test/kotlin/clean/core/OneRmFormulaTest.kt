package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Hybrid as the default, the three formulas offered in Settings, and RPE factored in (2026-10-22). */
class OneRmFormulaTest {
    private val hybrid = OneRm.DEFAULT
    private val brzycki = OneRm(OneRmFormula.BRZYCKI, 12)
    private val epley = OneRm(OneRmFormula.EPLEY, 12)
    private fun near(expected: Double, actual: Double?) = assertEquals(expected, actual!!, 1e-9)

    @Test fun `Hybrid up to 12 reps is the default`() {
        assertEquals(OneRm(OneRmFormula.HYBRID, 12, factorRpe = false), OneRm.DEFAULT)
    }

    @Test fun `Brzycki and Epley as written`() {
        near(100.0 * 36 / 32, estimate1Rm(100.0, 5, brzycki))
        near(100.0 * (1 + 5 / 30.0), estimate1Rm(100.0, 5, epley))
    }

    @Test fun `a single is the weight under every formula`() {
        for (rule in listOf(hybrid, brzycki, epley)) assertEquals(80.0, estimate1Rm(80.0, 1, rule))
    }

    @Test fun `Hybrid is Brzycki under 10 reps and Epley from 10`() {
        for (r in 2..9) near(estimate1Rm(60.0, r, brzycki)!!, estimate1Rm(60.0, r, hybrid))
        for (r in 10..12) near(estimate1Rm(60.0, r, epley)!!, estimate1Rm(60.0, r, hybrid))
        // The two meet at 10: 36 / 27 = 1 + 10 / 30.
        near(estimate1Rm(60.0, 10, brzycki)!!, estimate1Rm(60.0, 10, epley))
    }

    @Test fun `RPE adds the reps left in reserve when switched on`() {
        val on = hybrid.copy(factorRpe = true)
        // 100 x 8 at RPE 8 counts as 10 reps.
        near(estimate1Rm(100.0, 10, hybrid)!!, estimate1Rm(100.0, 8, on, 8.0))
        // A half step counts half a rep.
        near(100.0 * 36 / (37 - 9.5), estimate1Rm(100.0, 8, on, 8.5))
        // A single at RPE 9 is more than the weight.
        near(100.0 * 36 / 35, estimate1Rm(100.0, 1, on, 9.0))
        // RPE 10, no RPE, or one under 5 changes nothing; nor does any RPE with the switch off.
        assertEquals(estimate1Rm(100.0, 8, hybrid), estimate1Rm(100.0, 8, on, 10.0))
        assertEquals(estimate1Rm(100.0, 8, hybrid), estimate1Rm(100.0, 8, on, null))
        assertEquals(estimate1Rm(100.0, 8, hybrid), estimate1Rm(100.0, 8, on, 4.0))
        assertEquals(estimate1Rm(100.0, 8, hybrid), estimate1Rm(100.0, 8, hybrid, 7.0))
        // The rep limit is on the reps done, not the reps counted.
        near(estimate1Rm(100.0, 15, OneRm(OneRmFormula.HYBRID, 15))!!, estimate1Rm(100.0, 12, on, 7.0))
    }

    private fun w(day: String, vararg sets: NewSet) =
        buildWorkouts(rowsFor(1, "$day 10:00:00", "w", 60, listOf(NewBlock("x", sets.toList()))))[0]

    @Test fun `changing the formula or RPE recomputes the PRs from the same sets`() {
        val log = listOf(w("2025-01-01", NewSet(SetType.NORMAL, 100.0, 1)), w("2025-01-02", NewSet(SetType.NORMAL, 90.0, 4, rpe = "8")))
        fun oneRmPr(rule: OneRm) = PrKind.ONE_RM in prsOf(log, rule)["2025-01-02 10:00:00"]!!.flatten()
        // 90 x 4: Epley 102 beats 100; Brzycki 98.2 does not; with RPE 8 it counts as 6 reps, 104.1 under Brzycki.
        assertEquals(true, oneRmPr(epley))
        assertEquals(false, oneRmPr(hybrid))
        assertEquals(true, oneRmPr(hybrid.copy(factorRpe = true)))
        assertNotEquals(recordsOf(sessionsOf(log, "x"), epley).estimated1Rm, recordsOf(sessionsOf(log, "x"), hybrid).estimated1Rm)
    }
}
