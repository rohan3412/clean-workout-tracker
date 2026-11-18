package clean.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpNextTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val push = TemplateRef(1, "Push", "")
    private val pull = TemplateRef(2, "Pull", "")
    private val legs = TemplateRef(3, "Legs", "Current split")

    private fun done(name: String, daysAgo: Long) =
        Workout("1", "${today.minusDays(daysAgo)} 18:00:00", name, 3600, emptyList())

    private fun pick(templates: List<TemplateRef>, workouts: List<Workout>, on: Boolean = false, skipped: Set<Long> = emptySet()) =
        upNextTemplate(templates, workouts, on, "Current split", today, skipped)

    @Test fun `the template done longest ago among those used in 28 days`() {
        val next = pick(listOf(push, pull, legs), listOf(done("Push", 3), done("Pull", 20), done("Legs", 40)))
        assertEquals(pull, next?.template)
        assertEquals("last done 20 days ago", next?.reason)
    }

    @Test fun `the split folder is ordinary while the switch is off`() {
        assertEquals(push, pick(listOf(push, legs), listOf(done("Legs", 2), done("Push", 10)))?.template)
    }

    @Test fun `none used in 28 days gives the most recently used`() {
        assertEquals(pull, pick(listOf(push, pull), listOf(done("Push", 90), done("Pull", 40)))?.template)
    }

    @Test fun `a template used exactly 28 days ago still counts`() {
        assertEquals(push, pick(listOf(push, pull), listOf(done("Push", 28), done("Pull", 60)))?.template)
    }

    @Test fun `names must match exactly`() {
        assertEquals(pull, pick(listOf(push, pull), listOf(done("push", 1), done("Pull", 5)))?.template)
    }

    @Test fun `no templates gives none and templates never done give the first`() {
        assertNull(pick(emptyList(), listOf(done("Push", 1))))
        val next = pick(listOf(push, pull), emptyList())
        assertEquals(push, next?.template)
        assertEquals("never done", next?.reason)
    }

    @Test fun `skipped templates are passed over unless all are`() {
        val workouts = listOf(done("Push", 3), done("Pull", 20))
        assertEquals(push, pick(listOf(push, pull), workouts, skipped = setOf(2L))?.template)
        assertEquals(pull, pick(listOf(push, pull), workouts, skipped = setOf(1L, 2L))?.template)
    }

    @Test fun `with the switch on the old rotation decides`() {
        val next = pick(listOf(push, legs), listOf(done("Legs", 2), done("Push", 10)), on = true)
        assertEquals(legs, next?.template)
        assertEquals(true, next?.fromSplit)
    }
}
