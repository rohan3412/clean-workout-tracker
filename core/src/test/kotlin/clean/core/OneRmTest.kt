package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneRmTest {
    private fun e(formula: OneRmFormula, weight: Double, reps: Int) = estimate1Rm(weight, reps, OneRm(formula, 20))!!

    @Test
    fun `each formula gives the published figure at 100 x 5 and 100 x 10`() {
        // Epley: w (1 + r/30); Brzycki: w 36/(37 - r); Lander: 100 w/(101.3 - 2.67123 r); Lombardi: w r^0.10;
        // Mayhew: 100 w/(52.2 + 41.9 e^(-0.055 r)); O'Conner: w (1 + 0.025 r); Wathan: 100 w/(48.8 + 53.8 e^(-0.075 r));
        // Average: the mean of Epley, Brzycki and Lombardi.
        val table = listOf(
            Triple(OneRmFormula.EPLEY, 116.6667, 133.3333),
            Triple(OneRmFormula.BRZYCKI, 112.5, 133.3333),
            Triple(OneRmFormula.LANDER, 113.7089, 134.0704),
            Triple(OneRmFormula.LOMBARDI, 117.4619, 125.8925),
            Triple(OneRmFormula.MAYHEW, 119.0107, 130.9343),
            Triple(OneRmFormula.OCONNER, 112.5, 125.0),
            Triple(OneRmFormula.WATHAN, 116.5825, 134.7467),
            Triple(OneRmFormula.AVERAGE, 115.5429, 130.8531),
            Triple(OneRmFormula.HYBRID, 112.5, 133.3333),
        )
        for ((formula, five, ten) in table) {
            assertEquals(five, e(formula, 100.0, 5), 0.001, "$formula x 5")
            assertEquals(ten, e(formula, 100.0, 10), 0.001, "$formula x 10")
        }
    }

    @Test
    fun `a single is the weight itself, more reps and more weight give more, and above the limit there is nothing`() {
        for (formula in OneRmFormula.entries) {
            val rule = OneRm(formula, 20)
            assertEquals(87.5, estimate1Rm(87.5, 1, rule), "$formula at one rep")
            for (reps in 2..20) {
                assertTrue(e(formula, 100.0, reps) > e(formula, 100.0, reps - 1), "$formula rising with reps at $reps")
                assertTrue(e(formula, 101.0, reps) > e(formula, 100.0, reps), "$formula rising with weight at $reps")
            }
            assertNull(estimate1Rm(100.0, 11, OneRm(formula, 10)))
            assertNotNull(estimate1Rm(100.0, 10, OneRm(formula, 10)))
            assertNull(estimate1Rm(0.0, 5, rule))
            assertNull(estimate1Rm(100.0, 0, rule))
        }
    }

    @Test
    fun `Epley up to 12 reproduces the old figures exactly`() {
        for (weight in listOf(2.5, 20.0, 37.5, 60.0, 82.5, 100.0, 142.5, 200.0)) {
            assertEquals(weight, estimate1Rm(weight, 1, EPLEY_RULE))
            for (reps in 2..12) assertEquals(weight * (1 + reps / 30.0), estimate1Rm(weight, reps, EPLEY_RULE), "$weight x $reps")
            assertNull(estimate1Rm(weight, 13, EPLEY_RULE))
        }
    }

    @Test
    fun `the formula run backwards gives the weight for a number of reps`() {
        for (formula in OneRmFormula.entries) {
            val rule = OneRm(formula, 20)
            assertEquals(120.0, weightForReps(120.0, 1, rule))
            for (reps in listOf(2, 3, 5, 8, 10)) {
                val weight = weightForReps(120.0, reps, rule)
                assertTrue(weight < 120.0)
                assertEquals(120.0, estimate1Rm(weight, reps, rule)!!, 1e-9, "$formula x $reps")
            }
        }
    }

    @Test
    fun `auto-fill keeps its twelve-rep ceiling`() {
        // 60 x 15 is far above what 80 asks for; the target still lands within twelve reps.
        val target = nextTarget(Target(60.0, 15), 80.0, 2.5)
        assertTrue(target.reps <= MAX_REPS_FOR_1RM)
        assertTrue(estimate1Rm(target.weight, target.reps, EPLEY_RULE)!! > 80.0)
    }
}
