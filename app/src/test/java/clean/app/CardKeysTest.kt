package clean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A card keeps its key through moves and edits, even with the same exercise there twice. */
class CardKeysTest {
    @Test
    fun `same exercise twice gets two keys that move with their cards`() {
        val a = DraftBlock("Bench Press (Barbell)", listOf(DraftSet(clean.core.SetType.NORMAL, "100", "5")))
        val b = DraftBlock("Bench Press (Barbell)", listOf(DraftSet(clean.core.SetType.NORMAL, "80", "10")))
        val before = cardKeys(listOf(a, b))
        assertNotEquals(before[0], before[1])
        // Moved: the keys go with the cards, not with the positions.
        assertEquals(listOf(before[1], before[0]), cardKeys(listOf(b, a)))
        // Edited: a copy keeps its card's key.
        assertEquals(before[0], cardKeys(listOf(a.copy(items = emptyList())))[0])
        // Removing the first leaves the second's key as it was.
        assertEquals(before[1], cardKeys(listOf(b))[0])
    }
}
