package clean.core

import clean.core.Head.*
import clean.core.Muscle.*
import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class HeadsTest {
    private fun library(name: String) = (libraryExercise(name) ?: error("no library exercise $name")).muscles
    private fun heads(name: String) = headsFor(name, library(name))

    @Test
    fun `muscle use with heads is stored as a fourth part and old strings read as before`() {
        for (old in listOf("CHEST,SHOULDERS|TRICEPS", "LATS|BICEPS|FOREARMS", "LATS|BICEPS", "ABS|", "|", "CHEST|TRICEPS|")) {
            val use = MuscleUse.decode(old)
            assertTrue(use.heads.isEmpty(), old)
            assertEquals(use, MuscleUse.decode(use.encode()), old)
        }
        assertEquals("CHEST,SHOULDERS|TRICEPS", MuscleUse(setOf(CHEST, SHOULDERS), setOf(TRICEPS)).encode())
        assertEquals("LATS|BICEPS|FOREARMS", MuscleUse(setOf(LATS), setOf(BICEPS), setOf(FOREARMS)).encode())
        // A fourth part on today's reader changed nothing; the groups still come out the same.
        assertEquals(MuscleUse(setOf(CHEST), setOf(TRICEPS)), MuscleUse.decode("CHEST|TRICEPS||UPPER_CHEST:1.0").copy(heads = emptyMap()))

        val with = MuscleUse(setOf(CHEST), setOf(TRICEPS), emptySet(), mapOf(UPPER_CHEST to 1.0, LOWER_CHEST to 0.25))
        assertEquals("CHEST|TRICEPS||UPPER_CHEST:1.0,LOWER_CHEST:0.25", with.encode())
        assertEquals(with, MuscleUse.decode(with.encode()))
        val withMinor = MuscleUse(setOf(CHEST), setOf(TRICEPS), setOf(FOREARMS), mapOf(UPPER_CHEST to 1.0))
        assertEquals(withMinor, MuscleUse.decode(withMinor.encode()))
        assertEquals(with.shares, with.copy(heads = emptyMap()).shares)

        // An unknown head is skipped; so is one whose group is missing, or only minor.
        val skipped = MuscleUse.decode("CHEST|TRICEPS||UPPER_CHEST:1.0,NOT_A_HEAD:1.0,SOLEUS:1.0,LOWER_CHEST:x")
        assertEquals(mapOf(UPPER_CHEST to 1.0), skipped.heads)
        assertEquals(emptyMap(), MuscleUse.decode("TRICEPS|||UPPER_CHEST:1.0").heads)
        assertEquals(emptyMap(), MuscleUse.decode("TRICEPS||CHEST|UPPER_CHEST:1.0").heads)
    }

    @Test
    fun `every head a library exercise gets belongs to a group it trains`() {
        val perHead = LinkedHashMap<Head, Int>()
        var any = 0
        for (e in LIBRARY) {
            val made = headsFor(e.name, e.muscles)
            for (head in made.keys) assertTrue(head.group in e.muscles.primary || head.group in e.muscles.secondary, "${e.name}: $head")
            if (made.isNotEmpty()) any++
            for (head in made.keys) perHead[head] = (perHead[head] ?: 0) + 1
        }
        println("${LIBRARY.size} library exercises, $any get at least one head; " + Head.entries.joinToString(", ") { "${it.name} ${perHead[it] ?: 0}" })
        assertTrue(any > 0)
    }

    @Test
    fun `named cases`() {
        assertEquals(mapOf(SOLEUS to 1.0, GASTROCNEMIUS to MINOR_SHARE), heads("Seated Calf Raise (Machine)"))
        assertEquals(mapOf(GASTROCNEMIUS to 1.0, SOLEUS to MINOR_SHARE), heads("Standing Calf Raise (Machine)"))
        assertEquals(mapOf(GASTROCNEMIUS to 1.0, SOLEUS to MINOR_SHARE), heads("Calf Press on Seated Leg Press"))
        assertTrue(heads("Lateral Leg Raises").isEmpty())
        assertTrue(heads("Incline Row (Dumbbell)").isEmpty())
        assertEquals(mapOf(UPPER_CHEST to 1.0, LOWER_CHEST to MINOR_SHARE), heads("Incline Bench Press (Barbell)"))
        assertEquals(mapOf(LOWER_CHEST to 1.0, UPPER_CHEST to MINOR_SHARE), heads("Decline Chest Fly (Cable)"))
        assertEquals(mapOf(TRICEPS_LATERAL to 1.0, TRICEPS_MEDIAL to SECONDARY_SHARE), heads("Triceps Pushdown (Cable - Rope)"))
        assertEquals(mapOf(BICEPS_SHORT to 1.0), heads("Preacher Curl (EZ Bar)"))
        assertEquals(mapOf(FOREARM_EXTENSORS to 1.0), heads("Reverse Wrist Curl (Barbell)"))
        assertEquals(mapOf(FOREARM_FLEXORS to 1.0), heads("Wrist Curl (Barbell)"))
        assertEquals(mapOf(LOWER_ABS to 1.0), heads("Reverse Crunch"))
        assertEquals(mapOf(UPPER_ABS to 1.0), heads("Crunch"))
        assertEquals(mapOf(BRACHIALIS to 1.0, FOREARM_EXTENSORS to 1.0), heads("Reverse Curl (Barbell)"))
        assertEquals(mapOf(REAR_DELT to 1.0, RHOMBOIDS to SECONDARY_SHARE), heads("Face Pull (Cable)"))
        assertEquals(mapOf(GLUTEUS_MAXIMUS to 1.0), heads("Glute Kickback (Cable)"))
        assertEquals(mapOf(TRICEPS_LATERAL to 1.0, TRICEPS_LONG to SECONDARY_SHARE), heads("Triceps Kickback (Dumbbell)"))
        assertEquals(setOf(BICEPS_FEMORIS, INNER_HAMSTRINGS), heads("Lying Leg Curl (Machine)").keys)
        assertEquals(setOf(RECTUS_FEMORIS, VASTUS_LATERALIS, VASTUS_MEDIALIS), heads("Leg Extension (Machine)").keys)
        // Rule-made variations fall out of the same words.
        assertEquals(mapOf(SOLEUS to 1.0, GASTROCNEMIUS to MINOR_SHARE), headsFor("Standing Calf Raise (Machine) (Seated)", library("Standing Calf Raise (Machine)")))
        // Compounds name none.
        for (name in listOf("Squat (Barbell)", "Deadlift (Barbell)", "Bent Over Row (Barbell)", "Overhead Press (Barbell)", "Pull Up", "Lunge (Dumbbell)", "Leg Press (Machine)")) {
            assertTrue(heads(name).isEmpty(), name)
        }
    }

    @Test
    fun `stored groups still get the rules' heads stored heads win`() {
        val stored = MuscleUse.decode("CALVES|")
        assertEquals(SOLEUS, headsOf("Seated Calf Raise (Machine)", stored).maxByOrNull { it.value }?.key)
        val own = MuscleUse.decode("CALVES|||GASTROCNEMIUS:1.0")
        assertEquals(mapOf(GASTROCNEMIUS to 1.0), headsOf("Seated Calf Raise (Machine)", own))
        // Groups the exercise does not train give no head.
        assertTrue(headsOf("Seated Calf Raise (Machine)", MuscleUse.decode("QUADS|")).isEmpty())
    }

    @Test
    fun `heads never change the group figures and head sets and other work are defined on a small example`() {
        fun w(date: String, exercise: String, n: Int) =
            buildWorkouts(rowsFor(1, date, "w", 60, listOf(NewBlock(exercise, List(n) { NewSet(SetType.NORMAL, 20.0, 10) } + NewSet(SetType.WARMUP, 5.0, 10)))))[0]
        val log = listOf(
            w("2025-03-01 10:00:00", "Seated Calf Raise (Machine)", 3),
            w("2025-03-02 10:00:00", "Standing Calf Raise (Machine)", 2),
            w("2025-03-03 10:00:00", "Calf Mystery", 4),
        )
        val groups = mapOf(
            "Seated Calf Raise (Machine)" to MuscleUse(setOf(CALVES)),
            "Standing Calf Raise (Machine)" to MuscleUse(setOf(CALVES)),
            "Calf Mystery" to MuscleUse(setOf(CALVES), setOf(HAMSTRINGS)),
        )
        val without = { name: String -> groups.getValue(name) }
        val with = { name: String -> groups.getValue(name).let { it.copy(heads = if (name == "Calf Mystery") emptyMap() else headsFor(name, it)) } }
        assertEquals(muscleSets(log, null, without), muscleSets(log, null, with))
        assertEquals(fatigue(log, LocalDateTime.parse("2025-03-03T12:00:00"), without), fatigue(log, LocalDateTime.parse("2025-03-03T12:00:00"), with))
        assertEquals(lastTrained(log, without), lastTrained(log, with))
        val all = muscleSets(log, null, with)
        for (m in Muscle.entries) assertEquals(all[m] ?: 0.0, muscleBreakdown(log, null, with, m).exercises.sumOf { it.counted }, 1e-9)

        val sets = headSets(log, null, with)
        assertEquals(3.0 + 2 * MINOR_SHARE, sets.getValue(SOLEUS), 1e-9)
        assertEquals(3 * MINOR_SHARE + 2.0, sets.getValue(GASTROCNEMIUS), 1e-9)
        // The mystery exercise names no calf head, so all four of its sets are other work; the heads do not add up to the group.
        assertEquals(4.0, otherWork(CALVES, log, null, with), 1e-9)
        assertEquals(9.0, all.getValue(CALVES), 1e-9)
        assertEquals(2.0, otherWork(HAMSTRINGS, log, null, with), 1e-9)
        assertEquals(sets.getValue(SOLEUS), headBreakdown(log, null, with, SOLEUS).sumOf { it.counted }, 1e-9)
        assertEquals(listOf("Seated Calf Raise (Machine)", "Standing Calf Raise (Machine)"), headBreakdown(log, null, with, SOLEUS).map { it.name })
        assertEquals(emptyMap(), headSets(log, LocalDateTime.parse("2025-04-01T00:00:00"), with))
    }

    @Test
    fun `the four new groups`() {
        val tibialis = muscleSets(listOf(buildWorkouts(rowsFor(1, "2025-03-01 10:00:00", "w", 60, listOf(NewBlock("Tibialis Raise (Machine)", listOf(NewSet(SetType.NORMAL, 20.0, 10))))))[0]), null) { library(it) }
        assertEquals(1.0, tibialis[TIBIALIS])
        assertEquals(null, tibialis[CALVES])
        assertEquals(setOf(ROTATOR_CUFF), library("Shoulder External Rotation (Cable)").primary)
        assertEquals(setOf(SERRATUS), library("Scapula Push Up").primary)
        assertTrue(HIP_FLEXORS in library("Hanging Leg Raise").secondary)
        assertEquals(setOf(TIBIALIS), suggest("Tibialis Raise (Band)")?.muscles?.primary)
        assertEquals(setOf(ROTATOR_CUFF), suggest("Cable Rotator Cuff Work")?.muscles?.primary)
        assertEquals(setOf(ROTATOR_CUFF), suggest("Banded Internal Rotation")?.muscles?.primary)
        assertEquals(setOf(SERRATUS), suggest("Serratus Punch")?.muscles?.primary)
        assertEquals(setOf(HIP_FLEXORS), suggest("Standing Hip Flexor Raise")?.muscles?.primary)
        assertEquals(21, Muscle.entries.size)
    }
}
