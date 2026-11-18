package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.meaning
import clean.app.ui.theme.orange
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.workoutSkin
import clean.core.ACCENT_CHOICES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 1.1.0 look: Dark, Light or Phone, a Pure black switch for Dark, and one accent of six. */
class LookTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `the default is Dark with the page not black and the accent blue`() {
        val look = resolveLook()
        assertTrue(look.dark)
        assertFalse(look.pureBlack)
        assertEquals("Blue", look.accentName)
        assertNotEquals(Color.Black, look.page)
    }

    @Test
    fun `Pure black only blacks a dark page, and the cards stay a step above it`() {
        val black = resolveLook("dark", pureBlack = true)
        assertEquals(Color.Black, black.page)
        assertTrue(black.raised.luminance() > black.page.luminance())
        assertTrue(resolveLook("dark").raised.luminance() > resolveLook("dark").page.luminance())
        // Light has nothing to turn black.
        val light = resolveLook("light", pureBlack = true)
        assertFalse(light.pureBlack)
        assertNotEquals(Color.Black, light.page)
    }

    @Test
    fun `Phone follows the system`() {
        assertTrue(resolveLook("phone", systemDark = true).dark)
        assertFalse(resolveLook("phone", systemDark = false).dark)
        assertTrue(resolveLook("dark", systemDark = false).dark)
        assertFalse(resolveLook("light", systemDark = true).dark)
    }

    @Test
    fun `there are six accents, blue first, and an unknown one is blue`() {
        assertEquals(ACCENT_CHOICES, ACCENTS.keys.toList())
        assertEquals(6, ACCENTS.size)
        assertEquals("Blue", resolveLook(accent = "Mono").accentName)
        assertEquals("Blue", resolveLook(accent = "Red").accentName)
    }

    @Test
    fun `every accent keeps 4_5 to 1 against its page and drives the Material primary`() {
        for ((name, _) in ACCENTS) for (theme in listOf("dark", "light")) for (black in listOf(false, true)) {
            val look = resolveLook(theme, black, name)
            assertTrue("$name $theme", contrast(look.orange, look.page) >= 4.5f)
            assertEquals(look.orange, look.colorScheme().primary)
        }
    }

    @Test
    fun `the colour scale is the accent from dim to bright`() {
        for ((name, _) in ACCENTS) {
            val dark = resolveLook("dark", accent = name)
            // Brighter at each step on dark; the top step is the accent itself.
            assertEquals(4, dark.scale.size)
            for (i in 1 until 4) assertTrue("$name dark $i", dark.scale[i].luminance() > dark.scale[i - 1].luminance())
            assertEquals(dark.accent.first, dark.scale.last())
            // On light the steps deepen toward the accent.
            val light = resolveLook("light", accent = name)
            for (i in 1 until 4) assertTrue("$name light $i", light.scale[i].luminance() < light.scale[i - 1].luminance())
            assertEquals(light.accent.second, light.scale.last())
        }
        // A different accent gives a different scale; the old orange ramp is gone.
        assertNotEquals(resolveLook(accent = "Blue").scale, resolveLook(accent = "Green").scale)
    }

    @Test
    fun `the workout fills come from the scheme in both looks`() {
        for (theme in listOf("dark", "light")) {
            val look = resolveLook(theme)
            val scheme = look.colorScheme()
            val meaning = look.meaning()
            val s = look.workoutSkin(scheme, meaning)
            assertEquals(scheme.surfaceVariant, s.boxFill)
            assertEquals(Color.Transparent, s.boxBorder)
            assertEquals(scheme.primary, s.focusBorder)
            assertEquals(meaning.done.copy(alpha = 0.24f), s.rowDone)
            assertEquals(scheme.primary.copy(alpha = 0.30f), s.rowNext)
            assertEquals(meaning.done, s.tickDone)
            assertEquals(scheme.surfaceContainer, s.padFill)
        }
    }
}
