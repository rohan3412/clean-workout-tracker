package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseSearchTest {
    private val names = listOf(
        "Incline Chest Press (Machine)", "Incline Bench Press (Dumbbell)", "Chest Fly (Machine)", "Chest Press (Machine)",
        "Triceps Pushdown (Cable - Straight Bar)", "Lateral Raise (Dumbbell)", "Lateral Raise (Cable)", "Iso-Lateral Row (Machine)",
        "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Romanian Deadlift (Barbell)", "Bicep Curl (Dumbbell)",
        "Upright Row (Cable)", "Bench Press (Barbell)",
    )
    private val targets = names.map { SearchTarget(it) }

    /** The names that match, best first (ties by name, as no history is given here). */
    private fun find(query: String) = targets.mapNotNull { t -> searchRank(query, t)?.let { it to t.name } }.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }

    @Test fun `words match in any order and ignore case and punctuation`() {
        assertEquals(setOf("Incline Chest Press (Machine)", "Incline Bench Press (Dumbbell)"), find("press incline").take(2).toSet())
        assertTrue("Incline Chest Press (Machine)" in find("PRESS   incline"))
        assertEquals(listOf("Iso-Lateral Row (Machine)"), find("isolateral"))
        assertEquals("Iso-Lateral Row (Machine)", find("iso lat row").first())
        assertEquals(listOf("Triceps Pushdown (Cable - Straight Bar)"), find("pushdown straight"))
        assertEquals(listOf("Triceps Pushdown (Cable - Straight Bar)"), find("push down straight"))
    }

    @Test fun `gym shorthand`() {
        assertEquals(listOf("Lateral Raise (Dumbbell)"), find("db lateral"))
        assertEquals(listOf("Lateral Raise (Dumbbell)"), find("lat raise db"))
        assertEquals("Overhead Press (Barbell)", find("ohp").first())
        assertEquals(listOf("Romanian Deadlift (Barbell)"), find("rdl"))
        assertEquals(listOf("Triceps Pushdown (Cable - Straight Bar)"), find("tri pushdown"))
        assertEquals("Bicep Curl (Dumbbell)", find("bi curl").first())
        assertEquals("Chest Fly (Machine)", find("pec deck").first())
        assertTrue("Bench Press (Barbell)" in find("bb bench"))
    }

    @Test fun `a slip of a letter in a longer word`() {
        assertEquals("Lateral Raise (Dumbbell)", find("laterl raise dumbell").first())
        assertEquals(listOf("Lateral Raise (Dumbbell)"), find("lateral raise dumbell"))
        assertTrue("Triceps Pushdown (Cable - Straight Bar)" in find("trciep"))
        assertTrue("Incline Chest Press (Machine)" in find("inclien"))
        // Short words must be typed as they are.
        assertNull(searchRank("rwo", SearchTarget("Upright Row (Cable)")))
    }

    @Test fun `muscles and equipment match too, after the names`() {
        val pushdown = SearchTarget("Triceps Pushdown (Cable - Straight Bar)", extra = listOf("Triceps", "Cable"))
        val dip = SearchTarget("Dip", extra = listOf("Chest", "Bodyweight"))
        assertEquals(6, searchRank("chest", dip))
        assertNotNull(searchRank("cable triceps", pushdown))
        assertTrue(searchRank("chest", SearchTarget("Chest Fly (Machine)"))!! < searchRank("chest", dip)!!)
    }

    @Test fun `other names are searched`() {
        val skull = SearchTarget("Skullcrusher (Barbell)", otherNames = listOf("Lying Triceps Extension (Barbell)"))
        assertNotNull(searchRank("lying extension", skull))
        assertNotNull(searchRank("skull crusher", skull))
    }

    @Test fun `ranking exact, then start, then whole words, then the rest`() {
        assertEquals(0, searchRank("chest press machine", SearchTarget("Chest Press (Machine)")))
        assertEquals(1, searchRank("chest pr", SearchTarget("Chest Press (Machine)")))
        assertEquals(2, searchRank("press chest", SearchTarget("Incline Chest Press (Machine)")))
        assertEquals(3, searchRank("down", SearchTarget("Triceps Pushdown (Cable - Straight Bar)")))
        assertEquals("Chest Press (Machine)", find("chest press").first())
        assertEquals(listOf("Chest Fly (Machine)", "Chest Press (Machine)", "Incline Chest Press (Machine)"), find("chest"))
        assertNull(searchRank("squat", SearchTarget("Chest Press (Machine)")))
        assertEquals(0, searchRank("  ", SearchTarget("Chest Press (Machine)")))
    }
}
