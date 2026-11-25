package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import clean.app.ui.theme.resolveLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chart lines and sticky notes read on Light as well as on Dark. */
@Category(Regression::class)
class LightColoursTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `chart lines keep 3 to 1 against every page and card`() {
        for (theme in listOf("light", "dark")) for (black in listOf(false, true)) {
            val look = resolveLook(theme, pureBlack = black)
            val behind = listOf(look.page, look.raised) + if (look.dark) emptyList() else listOf(Color.White)
            for (line in seriesColorsOf(look)) for (b in behind) {
                assertTrue("$theme/$black: $line on $b is ${contrast(line, b)}", contrast(line, b) >= 3f)
            }
        }
    }

    @Test
    fun `dark keeps its chart colours with or without pure black`() {
        assertEquals(seriesColorsOf(resolveLook("dark")), seriesColorsOf(resolveLook("dark", pureBlack = true)))
        assertTrue(seriesColorsOf(resolveLook("light")) != seriesColorsOf(resolveLook("dark")))
    }

    @Test
    fun `a sticky note reads on its band, and the band is pale on a light page`() {
        for (name in STICKY_COLORS.keys) for (dark in listOf(true, false)) {
            val band = stickyBand(name, dark)
            assertTrue("$name dark=$dark", contrast(band.readableOn(), band) >= 4.5f)
            assertEquals("$name dark=$dark", !dark, band.luminance() > 0.5f)
        }
        // The band is still seen against a white card.
        for (name in STICKY_COLORS.keys) assertTrue(name, contrast(stickyBand(name, false), Color.White) >= 1.15f)
    }

    @Test
    fun `a colour of the users own is kept on both pages`() {
        assertEquals(Color(0xFF123456), stickyBand("#FF123456", dark = false))
        assertEquals(Color(0xFF123456), stickyBand("#FF123456", dark = true))
        assertEquals(STICKY_COLORS_LIGHT.values.first(), stickyBand("", dark = false))
    }
}
