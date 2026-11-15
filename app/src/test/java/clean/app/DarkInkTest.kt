package clean.app

import clean.app.ui.theme.LINEN_HAIRLINE
import clean.app.ui.theme.LINEN_HAIRLINE_DARK
import clean.app.ui.theme.Part
import clean.app.ui.theme.chartHalo
import clean.app.ui.theme.onInk
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.ringTrack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Words on an ink fill, the hairline, the ring's track and the chart halo read on dark looks too; Linen by day is unchanged. */
class DarkInkTest {
    @Test
    fun `linen by day keeps white on black and its hairline`() {
        val look = resolveLook("linen")
        assertEquals(Color.White, look.onInk)
        assertEquals(Color.White, look.ringTrack)
        assertEquals(Color.White, look.chartHalo)
        assertEquals(LINEN_HAIRLINE, look.divider)
    }

    @Test
    fun `ink main buttons on dark looks have dark words`() {
        for (preset in listOf("dark", "amoled")) {
            val look = resolveLook(preset, mapOf(Part.MAIN_BUTTONS to "ink", Part.OUTLINES to "hairline"))
            assertTrue(preset, look.dark)
            // The button is the near-white text; its words must be far from it.
            assertTrue(preset, abs(look.text.luminance() - look.onInk.luminance()) > 0.5f)
            assertEquals(LINEN_HAIRLINE_DARK, look.divider)
            assertEquals(look.page, look.ringTrack)
        }
    }

}
