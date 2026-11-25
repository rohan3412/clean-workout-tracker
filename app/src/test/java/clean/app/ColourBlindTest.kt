package clean.app

import org.junit.experimental.categories.Category
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
@Category(Regression::class)
class ColourBlindTest {
    private fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test
    fun `off is the usual green to red and on is blue to orange`() {
        val usual = resolveLook("dark").meaning()
        val safe = resolveLook("dark", colourBlindSafe = true).meaning()
        // Round 34: the usual "ready" is the theme's own good (Graphite Dark 2FD3A6, from the proposal).
        assertEquals(Color(0xFF2FD3A6), usual.ready)
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
        for (c in listOf(m.done, m.ready, m.recovering, m.fatigued, m.failure, m.underweight)) assertTrue("$c", contrast(c, look.page) >= 4.5f)
    }

    @Test
    fun `the meaning colours follow the accent only to keep clear of it`() {
        // Round 20: a colour whose hue is far enough from the accent is exactly the set's own; Graphite has no hue and never shifts.
        assertEquals(resolveLook("dark").meaning(), resolveLook("dark", accent = "Graphite").meaning())
        val usual = resolveLook("dark").meaning()
        val moss = resolveLook("dark", accent = "Green").meaning()
        assertNotEquals(usual.ready, moss.ready) // Green's own good is a teal, cooler than its accent
        assertEquals(usual.warmup, moss.warmup)
        assertEquals(usual.fatigued, moss.fatigued)
        // Crimson draws what deletes in the text colour (its accent is red).
        assertEquals(resolveLook("dark", accent = "Crimson").text, resolveLook("dark", accent = "Crimson").meaning().danger)
    }
}
