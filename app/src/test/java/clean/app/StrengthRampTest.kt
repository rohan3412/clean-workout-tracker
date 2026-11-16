package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import clean.app.ui.theme.LIGHT_FULL
import clean.app.ui.theme.LINEN_FULL
import clean.app.ui.theme.meaning
import clean.app.ui.theme.resolveLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Strength view's five steps, "Full" then the colour scale strongest first. */
class StrengthRampTest {
    @Test
    fun `Linen's full is the orange ramp one step deeper`() {
        val look = resolveLook("linen")
        assertEquals(LINEN_FULL, look.meaning().full)
        val steps = listOf(look.meaning().full) + look.scale.reversed()
        // Darkest first, each step lighter than the one before.
        for (i in 1 until steps.size) assertTrue(steps[i].luminance() > steps[i - 1].luminance())
        // Every step is orange: red the largest channel, blue the smallest.
        for (c in steps) assertTrue(c.red > c.green && c.green > c.blue)
    }

    @Test
    fun `Light's full is near black past the orange ramp`() {
        val look = resolveLook("light")
        assertEquals(LIGHT_FULL, look.meaning().full)
        val steps = listOf(look.meaning().full) + look.scale.reversed()
        for (i in 1 until steps.size) assertTrue(steps[i].luminance() > steps[i - 1].luminance())
        for (c in steps) assertTrue(c.red > c.green && c.green > c.blue)
    }

    @Test
    fun `other presets keep their full`() {
        assertEquals(Color(0xFFF0C419), resolveLook("amoled").meaning().full)
        assertEquals(Color(0xFFF0C419), resolveLook("dark").meaning().full)
    }
}
