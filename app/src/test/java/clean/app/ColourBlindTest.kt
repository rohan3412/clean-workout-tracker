package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.meaning
import clean.app.ui.theme.resolveLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour-blind-safe meaning colours (kv `theme.cbsafe`). */
class ColourBlindTest {
    private fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test
    fun `off is the usual green to red and on is blue to orange`() {
        val usual = resolveLook("dark").meaning()
        val safe = resolveLook("dark", colourBlindSafe = true).meaning()
        assertEquals(Color(0xFF34B454), usual.ready)
        assertEquals(Color(0xFF56B4E9), safe.ready)
        assertEquals(Color(0xFFE69F00), safe.recovering)
        assertEquals(Color(0xFFD55E00), safe.fatigued)
        assertNotEquals(safe.ready, safe.underweight)
        assertEquals(resolveLook("dark"), resolveLook("dark", colourBlindSafe = false))
    }

    @Test
    fun `the light set keeps 4_5 to 1 on the light page`() {
        val look = resolveLook("light", colourBlindSafe = true)
        val m = look.meaning()
        for (c in listOf(m.done, m.ready, m.recovering, m.fatigued, m.failure, m.underweight)) assertTrue(contrast(c, look.page) >= 4.5f)
    }

    @Test
    fun `the meaning colours do not follow the accent`() {
        for (accent in ACCENTS.keys) {
            assertEquals(resolveLook("dark").meaning().done, resolveLook("dark", accent = accent).meaning().done)
            assertEquals(resolveLook("light").meaning().danger, resolveLook("light", accent = accent).meaning().danger)
        }
    }
}
