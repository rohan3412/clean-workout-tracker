package clean.app

import androidx.compose.ui.graphics.Color
import clean.app.ui.theme.MONO_INK
import clean.app.ui.theme.MonoSettings
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.invertColour
import clean.app.ui.theme.monoInk
import clean.app.ui.theme.monoPaper
import clean.app.ui.theme.monoScale
import clean.app.ui.theme.monoSettingsOf
import clean.app.ui.theme.negated
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.rgbHex
import clean.app.ui.theme.toGrey
import clean.app.ui.theme.monoMeaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mono's sliders, the grain, and the two ways to turn a colour over. */
class MonoTest {
    private fun close(a: Color, b: Color, eps: Float = 0.004f) {
        assertEquals(a.red, b.red, eps); assertEquals(a.green, b.green, eps); assertEquals(a.blue, b.blue, eps); assertEquals(a.alpha, b.alpha, eps)
    }

    @Test
    fun `the paper slider goes from white to light grey`() {
        assertEquals(Color(255, 255, 255), monoPaper(0))
        assertEquals(Color(235, 235, 235), monoPaper(50))
        assertEquals(Color(215, 215, 215), monoPaper(100))
        assertEquals("#D7D7D7", monoPaper(100).rgbHex())
        assertEquals(monoPaper(100), monoPaper(250))
    }

    @Test
    fun `the ink slider goes from the app's ink to dark grey`() {
        assertEquals(MONO_INK, monoInk(0))
        assertEquals("#16181D", monoInk(0).rgbHex())
        assertEquals(Color(23, 23, 23), monoInk(1))
        assertEquals(Color(53, 53, 53), monoInk(50))
        assertEquals(Color(84, 84, 84), monoInk(100))
    }

    @Test
    fun `mono's settings read from the options and fall back`() {
        assertEquals(MonoSettings(), monoSettingsOf(emptyMap()))
        assertEquals(35, MonoSettings().texture)
        val s = monoSettingsOf(mapOf("mono.paper" to "40", "mono.ink" to "250", "mono.texture" to "x", "mono.negative" to "on", "mono.trueInvert" to "off"))
        assertEquals(MonoSettings(paper = 40, ink = 100, texture = 35, negative = true, trueInvert = false), s)
    }

    @Test
    fun `the sliders colour the look and everything else is mixed from the two`() {
        val look = resolveLook("mono", mono = MonoSettings(paper = 50, ink = 50))
        val paper = monoPaper(50)
        val ink = monoInk(50)
        assertTrue(look.mono)
        assertEquals(paper, look.page)
        assertEquals(paper, look.raised)
        assertEquals(ink, look.text)
        assertEquals(ink, look.accent.first)
        assertEquals(ink, look.outline)
        assertEquals(androidx.compose.ui.graphics.lerp(paper, ink, 0.6f), look.muted)
        assertEquals(androidx.compose.ui.graphics.lerp(paper, ink, 0.1f), look.higher)
        assertEquals(0.35f, look.texture)
        assertTrue(look.plexNumbers)
        // The default sliders are what Mono looked like before they existed.
        val plain = resolveLook("mono")
        assertEquals(Color.White, plain.page)
        assertEquals(MONO_INK, plain.text)
        // Other presets are untouched by the sliders and have no grain or Plex.
        assertEquals(resolveLook("light"), resolveLook("light", mono = MonoSettings(paper = 90, ink = 90, negative = true)))
        assertEquals(0f, resolveLook("amoled").texture)
        assertFalse(resolveLook("amoled").plexNumbers)
    }

    @Test
    fun `the colour scale runs from paper to ink and follows the sliders`() {
        val look = resolveLook("mono", mono = MonoSettings(paper = 20, ink = 20))
        assertEquals(monoScale(monoPaper(20), monoInk(20)), look.scale)
        assertEquals(monoInk(20), look.scale.last())
        // Lightest first: each step is darker than the one before.
        for ((a, b) in look.scale.zipWithNext()) assertTrue(a.red > b.red)
        assertNotEquals(resolveLook("mono").scale, look.scale)
        // Mono has no hue, so a chosen palette does not apply while it is on.
        assertEquals(resolveLook("mono").scale, resolveLook("mono", scaleOverride = "Ocean").scale)
    }

    @Test
    fun `true invert flips every channel and keeps alpha`() {
        assertEquals(Color(0f, 0f, 0f), invertColour(Color.White, true))
        close(Color(0.9f, 0.7f, 0.5f, 0.5f), invertColour(Color(0.1f, 0.3f, 0.5f, 0.5f), true))
        // Orange becomes blue.
        val blue = invertColour(Color(0xFFEC6A2E), true)
        assertTrue(blue.blue > blue.red)
        close(Color(0xFFEC6A2E), invertColour(blue, true))
    }

    @Test
    fun `lightness invert turns the lightness over and keeps the hue`() {
        close(Color.Black, invertColour(Color.White, false))
        close(Color.White, invertColour(Color.Black, false))
        close(Color(0.3f, 0.3f, 0.3f), invertColour(Color(0.7f, 0.7f, 0.7f), false))
        // A pure red is half light: it stays itself. A pale red becomes a dark red of the same hue.
        close(Color(1f, 0f, 0f), invertColour(Color(1f, 0f, 0f), false))
        val pale = invertColour(Color(1f, 0.8f, 0.8f), false)
        close(Color(0.2f, 0f, 0f), pale)
        for (c in listOf(Color(0xFFEC6A2E), Color(0xFF2456B8), Color(0xFF1E7A46), Color(0xFFB3261E), Color(0x80123456))) {
            close(c, invertColour(invertColour(c, false), false))
        }
    }

    @Test
    fun `the negative look is dark, inverted all through and turns back`() {
        val paper = resolveLook("mono")
        for (truly in listOf(true, false)) {
            val negative = resolveLook("mono", mono = MonoSettings(negative = true, trueInvert = truly))
            assertTrue(negative.negative)
            assertTrue(negative.dark)
            assertEquals(invertColour(paper.page, truly), negative.page)
            assertEquals(invertColour(paper.text, truly), negative.text)
            assertEquals(paper.scale.map { invertColour(it, truly) }, negative.scale)
            assertEquals(invertColour(paper.outline!!, truly), negative.outline)
            // The launcher icon is the black tile with the light ink.
            assertEquals("black" to "Ink", negative.icon to negative.iconAccent)
            close(paper.page, negative.negated().page)
            close(paper.text, negative.negated().text)
            // The scheme is the paper look's scheme with every colour turned over.
            val scheme = negative.colorScheme()
            assertEquals(invertColour(paper.colorScheme().background, truly), scheme.background)
            assertEquals(invertColour(paper.colorScheme().primary, truly), scheme.primary)
            assertEquals(invertColour(paper.colorScheme().onPrimary, truly), scheme.onPrimary)
        }
        // Not Mono, not negative: the toggle changes nothing else.
        assertFalse(resolveLook("mono").negative)
        assertFalse(resolveLook("amoled", mono = MonoSettings(negative = true)).negative)
    }
}

/** Mono has no hue: the meaning colours, the scheme, the accent and the scale are all greys, and what meant different things differs in lightness. */
class MonoGreyTest {
    /** Grey to within the ink's own faint cool tint (#16181D). */
    private fun isGrey(c: androidx.compose.ui.graphics.Color) = Math.abs(c.red - c.green) < 0.03f && Math.abs(c.green - c.blue) < 0.03f

    @Test
    fun `a colour becomes the grey of its lightness and keeps its alpha`() {
        val grey = androidx.compose.ui.graphics.Color(0xFFEC6A2E).copy(alpha = 0.5f).toGrey()
        assertTrue(isGrey(grey))
        assertEquals(0.5f, grey.alpha, 0.01f)
        assertEquals(androidx.compose.ui.graphics.Color.White.red, androidx.compose.ui.graphics.Color.White.toGrey().red, 0.001f)
    }

    @Test
    fun `the meaning colours are greys and the ones that must differ do`() {
        val m = monoMeaning(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF16181D))
        for (c in listOf(m.done, m.warmup, m.failure, m.drop, m.ready, m.recovering, m.fatigued, m.full, m.underweight)) assertTrue(isGrey(c) || c == m.full)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF16181D), m.danger)
        // Ready is lighter than recovering, which is lighter than fatigued; a PR is darker than a matched set.
        assertTrue(m.ready.red > m.recovering.red && m.recovering.red > m.fatigued.red)
        assertTrue(m.done.red < m.warmup.red)
    }

    @Test
    fun `the Mono look has a grey accent scale and scheme even when a hue was asked for`() {
        val look = resolveLook("mono", accentOverride = "Orange", scaleOverride = "Jet")
        assertTrue(look.mono)
        assertTrue(isGrey(look.accent.first) && isGrey(look.accent.second))
        assertTrue(look.scale.all { isGrey(it) })
        val scheme = look.colorScheme()
        for (c in listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, scheme.errorContainer, scheme.tertiaryContainer)) assertTrue(isGrey(c))
        val negative = resolveLook("mono", mono = clean.app.ui.theme.MonoSettings(negative = true, trueInvert = true)).colorScheme()
        for (c in listOf(negative.primary, negative.tertiary, negative.error)) assertTrue(isGrey(c))
    }
}

