package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.THEMES
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

/** The 1.1.0 look: Dark, Light or Phone, a Pure black switch for Dark, and one of eight themes. */
@Category(Regression::class)
class LookTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `the default is Dark with the page not black and the theme Graphite`() {
        val look = resolveLook()
        assertTrue(look.dark)
        assertFalse(look.pureBlack)
        assertEquals("Graphite", look.themeName)
        assertEquals(Color(0xFF0E0E0F), look.page)
        assertEquals(Color(0xFFF4F4F5), look.orange)
        assertEquals(Color(0xFF111113), look.ink)
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
    fun `there are twelve themes, Graphite first, and an unknown one is Graphite`() {
        assertEquals(ACCENT_CHOICES, ACCENTS.keys.toList())
        assertEquals(ACCENT_CHOICES, THEMES.keys.toList())
        assertEquals(12, THEMES.size)
        assertEquals("Graphite", resolveLook(accent = "Mono").themeName)
        // The old six are not themes: the migration turns them into one, the look itself falls back.
        assertEquals("Graphite", resolveLook(accent = "Blue").themeName)
        assertEquals("Ember", resolveLook(accent = "Ember").themeName)
    }

    @Test
    fun `each theme has the spec's colours in Dark and in Light`() {
        for (r in Round6Spec.rows) {
            val look = resolveLook(if (r.dark) "dark" else "light", accent = r.name)
            val tag = "${r.name} ${if (r.dark) "dark" else "light"}"
            assertEquals(tag, Color(r.page), look.page); assertEquals(tag, Color(r.card), if (r.dark) look.card else Color.White.also { assertEquals(tag, it, look.card) })
            assertEquals(tag, Color(r.text), look.text); assertEquals(tag, Color(r.muted), look.muted)
            assertEquals(tag, Color(r.accent), look.orange); assertEquals(tag, Color(r.ink), look.ink)
            assertEquals(tag, Color(r.good), look.good); assertEquals(tag, Color(r.bad), look.bad); assertEquals(tag, Color(r.pr), look.pr)
            assertEquals(tag, listOf(r.accent) + r.series, look.series.map { (it.toArgb().toLong() and 0xFFFFFFFFL) })
            assertEquals(tag, if (r.dark) THEMES.getValue(r.name).dark.accent else THEMES.getValue(r.name).light.accent, look.orange)
            if (r.dark) {
                // The light of the theme: white 7 % for Graphite, the accent at 15 % for the rest.
                assertEquals(tag, if (r.name == "Graphite") Color.White else look.accent.first, look.ambient.copy(alpha = 1f))
                assertTrue(tag, look.ambient.alpha in 0.069f..0.161f)
                // Cards and tiles are the tint over the ground, the card a step above the tile, both above the page.
                assertTrue(tag, look.card.luminance() > look.tile.luminance() && look.tile.luminance() > look.page.luminance())
                assertEquals(look.card, look.raised)
                assertTrue(tag, look.higher.luminance() > look.raised.luminance())
            }
        }
    }

    @Test
    fun `Graphite's scale is four greys up to near-white and Light has white cards on a tinted page`() {
        val scale = resolveLook("dark", accent = "Graphite").scale
        assertEquals(4, scale.size)
        for (c in scale) assertEquals(c.red, c.blue, 0.02f)
        assertTrue(scale.last().luminance() > 0.85f)
        val light = resolveLook("light", accent = "Cobalt")
        assertEquals(Color.White, light.card)
        assertNotEquals(Color.White, light.page)
        assertEquals(Color.White, light.ink)
    }

    @Test
    fun `Crimson draws what deletes in the text colour, the others in red`() {
        val crimson = resolveLook("dark", accent = "Crimson")
        assertEquals(crimson.text, crimson.meaning().danger)
        assertNotEquals(resolveLook("dark", accent = "Ember").text, resolveLook("dark", accent = "Ember").meaning().danger)
    }

    @Test
    fun `every accent keeps its proposal contrast against its page and drives the Material primary`() {
        // Round 34: the proposal sets the accent's floor at 3 : 1 and measures 4.26 (Ember in Light) at the least; Dark is 5.36 or more. With Pure black the page is black, which only helps.
        for ((name, _) in ACCENTS) for (theme in listOf("dark", "light")) for (black in listOf(false, true)) {
            val look = resolveLook(theme, black, name)
            assertTrue("$name $theme", contrast(look.orange, look.page) >= if (theme == "dark") 5.3f else 4.25f)
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
        assertNotEquals(resolveLook(accent = "Graphite").scale, resolveLook(accent = "Green").scale)
    }

    @Test
    fun `the workout fills come from the scheme in both looks`() {
        for (theme in listOf("dark", "light")) {
            val look = resolveLook(theme)
            val scheme = look.colorScheme()
            val meaning = look.meaning()
            val s = look.workoutSkin(scheme, meaning)
            assertTrue(s.boxFill.luminance() != scheme.surfaceVariant.luminance())
            assertEquals(Color.Transparent, s.boxBorder)
            assertEquals(scheme.primary, s.focusBorder)
            // Quiet: a done row is the accent at 8 %, the current row the accent at 11 % in Dark and 9 % in Light (round 28), the tick a filled accent circle with the theme's ink.
            assertEquals(scheme.primary.copy(alpha = 0.08f), s.rowDone)
            assertEquals(scheme.primary.copy(alpha = if (look.dark) 0.11f else 0.09f), s.rowNext)
            assertEquals(scheme.primary, s.tickDone)
            assertEquals(look.ink, s.checkDone)
            assertEquals(scheme.surfaceContainer, s.padFill)
        }
    }
}
