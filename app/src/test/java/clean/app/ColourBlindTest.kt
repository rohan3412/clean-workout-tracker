package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
        val usual = resolveLook("amoled").meaning()
        val safe = resolveLook("amoled", colourBlindSafe = true).meaning()
        assertEquals(Color(0xFF34B454), usual.ready)
        assertEquals(Color(0xFF56B4E9), safe.ready)
        assertEquals(Color(0xFFE69F00), safe.recovering)
        assertEquals(Color(0xFFD55E00), safe.fatigued)
        assertNotEquals(safe.ready, safe.underweight)
        assertEquals(resolveLook("amoled"), resolveLook("amoled", colourBlindSafe = false))
    }

    @Test
    fun `the light set keeps 4_5 to 1 on the light page`() {
        val look = resolveLook("light", modeOverride = "Light", colourBlindSafe = true)
        val m = look.meaning()
        for (c in listOf(m.done, m.ready, m.recovering, m.fatigued, m.failure, m.underweight)) assertTrue(contrast(c, Color(0xFFF6F7FA)) >= 4.5f)
    }

    @Test
    fun `Mono ignores it`() {
        assertEquals(resolveLook("mono").meaning().ready, resolveLook("mono", colourBlindSafe = true).meaning().ready)
        assertEquals(resolveLook("mono").meaning().fatigued, resolveLook("mono", colourBlindSafe = true).meaning().fatigued)
    }
}
