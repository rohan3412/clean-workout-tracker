package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsNewTest {
    private val text = """
        # Changes

        ## 1.0.2 (2026-11-16)
        - Source code: Settings, Credits opens the code.
        - Just a title

        ## 1.0.1 (2026-11-15)
        - First release: one APK per kind of phone.
    """.trimIndent()

    @Test
    fun `versions newest first with their changes`() {
        val r = parseChanges(text)
        assertEquals(listOf("1.0.2", "1.0.1"), r.map { it.version })
        assertEquals("2026-11-16", r[0].date)
        assertEquals(Change("Source code", "Settings, Credits opens the code."), r[0].changes[0])
        assertEquals(Change("Just a title", ""), r[0].changes[1])
        assertEquals(1, r[1].changes.size)
    }

    @Test
    fun `shown once after an update never on a first run`() {
        val r = parseChanges(text)
        assertEquals("1.0.2", whatsNewDue(r, "1.0.2", null, firstRun = false)?.version)
        assertEquals("1.0.2", whatsNewDue(r, "1.0.2", "1.0.1", firstRun = false)?.version)
        assertNull(whatsNewDue(r, "1.0.2", "1.0.2", firstRun = false))
        assertNull(whatsNewDue(r, "1.0.2", null, firstRun = true))
        // A version with no entry shows nothing.
        assertNull(whatsNewDue(r, "1.0.3", "1.0.2", firstRun = false))
    }
}
