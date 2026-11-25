package clean.app

import org.junit.experimental.categories.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Library's + and the exercise page's "Add to <workout>": when a card counts as added, and what Undo takes out; and the rest sounds' stored keys. */
@Category(Regression::class)
class AddToWorkoutTest {
    private val bench = DraftBlock("Bench Press (Barbell)", listOf(DraftSet()))
    private val fly = DraftBlock("Cable Fly", listOf(DraftSet()))

    @Test
    fun `no workout holds nothing`() {
        assertFalse((null as Draft?).holds("Cable Fly"))
    }

    @Test
    fun `a card is held once it is in the workout`() {
        val draft = Draft("Push", 0, listOf(bench))
        assertTrue(draft.holds("Bench Press (Barbell)"))
        assertFalse(draft.holds("Cable Fly"))
    }

    @Test
    fun `undo takes out the added card and no other`() {
        val draft = Draft("Push", 0, listOf(bench, fly))
        assertEquals(listOf(bench), draft.withoutCard(fly.id).blocks)
        assertFalse(draft.withoutCard(fly.id).holds("Cable Fly"))
    }

    @Test
    fun `undo with the card already gone changes nothing`() {
        val draft = Draft("Push", 0, listOf(bench))
        assertEquals(draft, draft.withoutCard(fly.id))
    }

    @Test
    fun `rest sounds keep the keys 1_0_4 stored and fall back to the deep bell`() {
        assertEquals(RestTone.Deep, RestTone.of(null))
        assertEquals(RestTone.Deep, RestTone.of("nonsense"))
        assertEquals(listOf("deep", "bright", "gong", "soft", "wood"), RestTone.entries.map { it.key })
        for (tone in RestTone.entries) assertEquals(tone, RestTone.of(tone.key))
        assertEquals("Deep bell", RestTone.entries.first().label)
    }
}
