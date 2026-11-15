package clean.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Steps taken one after another are taken back together by one Undo, newest first. */
class UndosTest {
    @Test
    fun `two steps stack and one undo takes both back in reverse`() {
        val done = mutableListOf<String>()
        val undos = Undos(UndoStep("Set removed", { done += "first" })) + UndoStep("Exercise removed", { done += "second" })
        assertEquals("2 removed", undos.text)
        undos.undoAll()
        assertEquals(listOf("second", "first"), done)
    }

    @Test
    fun `the bar's words`() {
        assertEquals("Photo deleted", Undos(UndoStep("Photo deleted", {})).text)
        assertEquals("2 deleted", (Undos(UndoStep("Photo deleted", {})) + UndoStep("Template deleted", {})).text)
        assertEquals("2 changes", (Undos(UndoStep("Set removed", {})) + UndoStep("Workout cancelled", {})).text)
    }

    @Test
    fun `expiring lets every step go`() {
        val gone = mutableListOf<Int>()
        (Undos(UndoStep("a b", {}, { gone += 1 })) + UndoStep("c b", {}, { gone += 2 })).expireAll()
        assertEquals(listOf(1, 2), gone)
    }
}
