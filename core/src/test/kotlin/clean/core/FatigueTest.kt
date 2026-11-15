package clean.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fatigue model counts kg x reps against a normal session (see `fatigue` in Muscles.kt). Every session here is made by hand. */
class FatigueTest {
    private val uses = mapOf(
        "Press" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.TRICEPS)),
        "Incline" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.TRICEPS)),
        "Dip" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.TRICEPS)),
        "Plank" to MuscleUse(setOf(Muscle.ABS)),
        "Curl" to MuscleUse(setOf(Muscle.BICEPS), emptySet(), setOf(Muscle.FOREARMS)),
        "Run" to MuscleUse(setOf(Muscle.QUADS)),
        "Pull Up" to MuscleUse(setOf(Muscle.LATS)),
    )
    private val musclesOf = { name: String -> uses[name] ?: MuscleUse() }
    private val now = LocalDateTime.parse("2025-06-10T12:00:00")

    private var number = 0
    private fun session(at: LocalDateTime, exercise: String, vararg sets: NewSet) =
        buildWorkouts(rowsFor(++number, STRONG_DATE_FORMAT.format(at), "w", 60, listOf(NewBlock(exercise, sets.toList()))))[0]

    private fun sets(count: Int, weight: Double, reps: Int, type: SetType = SetType.NORMAL, rpe: String = "") =
        Array(count) { NewSet(type, weight, reps, rpe = rpe) }

    /** What one set of [load] x [reps] gives in a session whose best estimated 1RM is [best]. */
    private fun stim(load: Double, reps: Int, best: Double) = load * reps * Math.pow(minOf(1.0, load / best), 1.5)

    private fun epley(load: Double, reps: Int) = load * (1 + reps / 30.0)

    private fun level(workouts: List<Workout>, muscle: Muscle, at: LocalDateTime = now) = fatigue(workouts, at, musclesOf).getValue(muscle)

    @Test
    fun `a hard session of fifteen to twenty sets reaches the top level and stays there for a day`() {
        // One exercise, 15 sets of 80 x 10 at the session's best: 15 x 800 x (80 / 106.67)^1.5 = 7794 against the normal 2000.
        val one = listOf(session(now, "Press", *sets(15, 80.0, 10)))
        assertEquals(1 - Math.exp(-15 * stim(80.0, 10, epley(80.0, 10)) / 2000), level(one, Muscle.CHEST), 1e-9)
        assertEquals("fatigued", fatigueWord(level(one, Muscle.CHEST)))
        // Still fatigued the next day.
        assertEquals("fatigued", fatigueWord(level(one, Muscle.CHEST, now.plusHours(24))))
        // 18 sets over three exercises in one workout, the last of them 20 hours ago.
        val three = listOf(
            buildWorkouts(rowsFor(99, STRONG_DATE_FORMAT.format(now.minusHours(20)), "w", 60, listOf("Press", "Incline", "Dip").map { NewBlock(it, sets(6, 60.0, 12).toList()) }))[0],
        )
        assertEquals("fatigued", fatigueWord(level(three, Muscle.CHEST)))
        assertEquals("fatigued", fatigueWord(level(three, Muscle.TRICEPS)))
        // Fifteen sets of pull ups with no weight logged: each a third of a normal session.
        val pull = listOf(session(now.minusHours(12), "Pull Up", *Array(15) { NewSet(SetType.NORMAL, null, 10) }))
        assertEquals("fatigued", fatigueWord(level(pull, Muscle.LATS)))
    }

    @Test
    fun `a session is weighted by how close each set was to the session's best`() {
        // Best Epley 100 x (1 + 8/30) = 126.67; each set counts its kg x reps times (load / 126.67)^1.5.
        val log = listOf(session(now, "Press", *sets(2, 100.0, 8), *sets(2, 50.0, 8)))
        val best = epley(100.0, 8)
        val total = 2 * stim(100.0, 8, best) + 2 * stim(50.0, 8, best)
        assertEquals(1 - Math.exp(-total / 2000), level(log, Muscle.CHEST), 1e-9)
    }

    @Test
    fun `a single light set is ready`() {
        // 50 x 8 = 400 against 2000: 0.2, a level of 0.18.
        assertEquals("ready", fatigueWord(level(listOf(session(now, "Press", NewSet(SetType.NORMAL, 50.0, 8))), Muscle.CHEST)))
    }

    @Test
    fun `a muscle's normal session moves down after a light one, never up`() {
        // A light session 10 days ago (one set, s = 520) sets the normal to 2000 + (s - 2000) / 3; the same set today counts s over that.
        val s = stim(80.0, 10, epley(80.0, 10))
        val faded = Math.pow(0.5, 240 / 36.0)
        val log = listOf(session(now.minusDays(10), "Curl", NewSet(SetType.NORMAL, 80.0, 10)), session(now, "Curl", NewSet(SetType.NORMAL, 80.0, 10)))
        assertEquals(1 - Math.exp(-(s / 2000 * faded + s / (2000 + (s - 2000) / 3))), level(log, Muscle.BICEPS), 1e-9)
        // A heavy session leaves it where it was.
        val heavy = listOf(session(now.minusDays(10), "Curl", *sets(10, 80.0, 10)), session(now, "Curl", NewSet(SetType.NORMAL, 80.0, 10)))
        assertEquals(1 - Math.exp(-(10 * s / 2000 * faded + s / 2000)), level(heavy, Muscle.BICEPS), 1e-9)
    }

    @Test
    fun `warm-ups never count and a supporting muscle gets half`() {
        val log = listOf(session(now, "Press", *sets(3, 40.0, 10, SetType.WARMUP), *sets(2, 100.0, 5)))
        // Only the two 100 x 5 sets: the chest gets them in full, the triceps at half share.
        val total = 2 * stim(100.0, 5, epley(100.0, 5)) / 2000
        assertEquals(1 - Math.exp(-total), level(log, Muscle.CHEST), 1e-9)
        assertEquals(1 - Math.exp(-total / 2), level(log, Muscle.TRICEPS), 1e-9)
    }

    @Test
    fun `one more working set never lowers any muscle`() {
        for (base in 1..4) for (extra in 1..3) {
            val a = fatigue(listOf(session(now, "Press", *sets(base, 80.0, 8))), now, musclesOf)
            val b = fatigue(listOf(session(now, "Press", *sets(base + extra, 80.0, 8))), now, musclesOf)
            for (m in Muscle.entries) assertTrue(b.getValue(m) >= a.getValue(m) - 1e-12, "$m with $base + $extra sets")
        }
    }

    @Test
    fun `levels fall with time and readyAt says when a muscle is ready`() {
        val log = listOf(session(now, "Press", *sets(2, 100.0, 8)))
        val start = level(log, Muscle.CHEST)
        assertEquals(1 - Math.exp(-2 * stim(100.0, 8, epley(100.0, 8)) / 2000), start, 1e-9)
        assertTrue(level(log, Muscle.CHEST, now.plusHours(12)) < start)
        assertTrue(level(log, Muscle.CHEST, now.plusHours(24)) < level(log, Muscle.CHEST, now.plusHours(12)))
        val ready = assertNotNull(readyAt(start, now))
        assertTrue(level(log, Muscle.CHEST, ready.minusMinutes(1)) >= FATIGUE_RECOVERING)
        assertTrue(level(log, Muscle.CHEST, ready.plusMinutes(1)) < FATIGUE_RECOVERING)
        assertNull(readyAt(0.1, now))
    }

    @Test
    fun `sessions older than thirty days are forgotten`() {
        assertEquals(0.0, level(listOf(session(now.minusDays(31), "Press", *sets(20, 100.0, 8))), Muscle.CHEST))
    }
}
