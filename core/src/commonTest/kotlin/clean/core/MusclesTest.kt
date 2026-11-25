package clean.core

import clean.core.time.*
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class MusclesTest {
    private val uses = mapOf(
        "Bench" to MuscleUse(setOf(Muscle.CHEST), setOf(Muscle.TRICEPS)),
        "Curl" to MuscleUse(setOf(Muscle.BICEPS)),
    )
    private val musclesOf = { name: String -> uses[name] ?: MuscleUse() }

    private fun workout(date: String, exercise: String, vararg sets: NewSet) =
        buildWorkouts(rowsFor(1, date, "w", 60, listOf(NewBlock(exercise, sets.toList()))))[0]

    private val log = listOf(
        workout("2025-01-01 10:00:00", "Curl", NewSet(SetType.NORMAL, 10.0, 10), NewSet(SetType.NORMAL, 10.0, 10)),
        workout("2025-03-01 10:00:00", "Bench", NewSet(SetType.WARMUP, 20.0, 5), NewSet(SetType.NORMAL, 60.0, 8), NewSet(SetType.FAILURE, 60.0, 6), NewSet(SetType.DROP, 40.0, 8)),
    )

    @Test
    fun `muscle use survives being stored as text`() {
        val use = MuscleUse(setOf(Muscle.CHEST, Muscle.SHOULDERS), setOf(Muscle.TRICEPS))
        assertEquals("CHEST,SHOULDERS|TRICEPS", use.encode())
        assertEquals(use, MuscleUse.decode(use.encode()))
        assertEquals(MuscleUse(), MuscleUse.decode(""))
        assertEquals(mapOf(Muscle.CHEST to 1.0, Muscle.SHOULDERS to 1.0, Muscle.TRICEPS to 0.5), use.shares)
        // Three levels: a main muscle is a full set, a supporting one half, a minor one a quarter.
        val three = MuscleUse(setOf(Muscle.LATS), setOf(Muscle.BICEPS), setOf(Muscle.FOREARMS))
        assertEquals("LATS|BICEPS|FOREARMS", three.encode())
        assertEquals(three, MuscleUse.decode(three.encode()))
        assertEquals(mapOf(Muscle.LATS to 1.0, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.25), three.shares)
    }

    @Test
    fun `suggestions come from common words and prefer the specific phrase`() {
        assertEquals(setOf(Muscle.HAMSTRINGS), suggest("Lying Leg Curl (Machine)")?.muscles?.primary)
        assertEquals(setOf(Muscle.BICEPS), suggest("Incline Curl (Dumbbell)")?.muscles?.primary)
        assertEquals(setOf(Muscle.TRICEPS), suggest("Triceps Extension (Cable)")?.muscles?.primary)
        assertEquals(setOf(Muscle.QUADS), suggest("Leg Extension (Machine)")?.muscles?.primary)
        assertEquals(setOf(Muscle.CALVES), suggest("Seated Calf Raise (Plate Loaded)")?.muscles?.primary)
        assertEquals(setOf(Muscle.UPPER_BACK, Muscle.LATS), suggest("Iso-Lateral Row (Machine)")?.muscles?.primary)
        assertEquals(LoadType.ASSISTED, suggest("Pull Up (Assisted)")?.loadType)
        assertEquals(LoadType.BODYWEIGHT, suggest("Pull Up")?.loadType)
        assertEquals(LoadType.NORMAL, suggest("Crunch (Machine)")?.loadType)
        assertNull(suggest("Underwater Basket Weaving"))
    }

    @Test
    fun `sets per muscle count working sets with secondary muscles at a fraction`() {
        val all = muscleSets(log, null, musclesOf)
        assertEquals(3.0, all[Muscle.CHEST])
        assertEquals(1.5, all.getValue(Muscle.TRICEPS), 1e-9)
        assertEquals(2.0, all[Muscle.BICEPS])
        assertNull(muscleSets(log, LocalDateTime.parse("2025-02-01T00:00:00"), musclesOf)[Muscle.BICEPS])
    }

    @Test
    fun `a muscle's breakdown adds up to its set count`() {
        val more = log + workout("2025-03-08 10:00:00", "Bench", NewSet(SetType.NORMAL, 60.0, 8))
        for (from in listOf(null, LocalDateTime.parse("2025-02-01T00:00:00"))) {
            val all = muscleSets(more, from, musclesOf)
            for (muscle in Muscle.entries) {
                val parts = muscleBreakdown(more, from, musclesOf, muscle)
                assertEquals(all[muscle] ?: 0.0, parts.exercises.sumOf { it.counted }, 1e-9)
                assertEquals(all[muscle] ?: 0.0, parts.workouts.sumOf { it.counted }, 1e-9)
            }
        }
        val triceps = muscleBreakdown(more, null, musclesOf, Muscle.TRICEPS)
        assertEquals(listOf(MuscleExercise("Bench", 4, 0.5, "2025-03-08 10:00:00")), triceps.exercises)
        assertEquals(listOf("2025-03-08 10:00:00", "2025-03-01 10:00:00"), triceps.workouts.map { it.date })
    }

    @Test
    fun `retained strength is full for two weeks then halves every four down to the floor`() {
        fun at(day: String): Map<Muscle, Double> = retainedStrength(log, LocalDateTime.parse(day + "T10:00:00"), musclesOf)
        assertEquals(1.0, at("2025-03-15")[Muscle.CHEST])
        assertEquals(0.5.pow(0.5), at("2025-03-29").getValue(Muscle.CHEST), 1e-9)
        assertEquals(STRENGTH_FLOOR, at("2025-06-01")[Muscle.CHEST])
        // Never trained.
        assertEquals(STRENGTH_FLOOR, at("2025-03-02")[Muscle.QUADS])
    }

    @Test
    fun `fatigue is high right after a session and fades with a 36 hour half-life`() {
        fun at(time: String) = fatigue(log, LocalDateTime.parse(time), musclesOf)
        // Stimulus: 60x8 at the session's best (76), 60x6 and 40x8 below it, against a normal session of 2000.
        val best = 60 * (1 + 8 / 30.0)
        val stimulus = (480 * (60 / best).pow(1.5) + 360 * (60 / best).pow(1.5) + 320 * (40 / best).pow(1.5)) / 2000
        val after = at("2025-03-01T10:00:00")
        assertEquals(1 - kotlin.math.exp(-stimulus), after.getValue(Muscle.CHEST), 1e-9)
        assertEquals(1 - kotlin.math.exp(-stimulus * 0.5), after.getValue(Muscle.TRICEPS), 1e-9)
        assertEquals(1 - kotlin.math.exp(-stimulus / 2), at("2025-03-02T22:00:00").getValue(Muscle.CHEST), 1e-9)
        assertEquals(0.0, after[Muscle.QUADS])
        // The curls were two months earlier, outside what fatigue looks at.
        assertEquals(0.0, after[Muscle.BICEPS])
        assertTrue(at("2025-03-10T10:00:00").getValue(Muscle.CHEST) < 0.01)
    }

}
