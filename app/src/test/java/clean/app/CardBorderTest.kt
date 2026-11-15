package clean.app

import clean.app.ui.theme.LINEN_HAIRLINE
import clean.app.ui.theme.Part
import clean.app.ui.theme.PRESETS
import clean.app.ui.theme.SavedPreset
import clean.app.ui.theme.decodeSaved
import clean.app.ui.theme.encodeSaved
import clean.app.ui.theme.heroOutlineAccent
import clean.app.ui.theme.lookKeysOf
import clean.app.ui.theme.matchingSaved
import clean.app.ui.theme.resetLookKeys
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.tileOutlined
import clean.app.ui.theme.withSavedLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card border is Mix and match's Outlines part: each preset keeps its own, a choice reaches every card, and it is saved and reset like any part. */
class CardBorderTest {
    private fun outline(preset: String, mix: String? = null) =
        resolveLook(preset, if (mix == null) emptyMap() else mapOf(Part.OUTLINES to mix)).let { Triple(it.outlineWidth, it.outline, it) }

    @Test
    fun `each preset's own border is the one it had`() {
        val text = { p: String -> resolveLook(p).text }
        assertEquals(0f, outline("light").first)
        assertEquals(0f, outline("dark").first)
        assertEquals(1f, outline("amoled").first); assertNull(outline("amoled").second)
        assertEquals(1.5f, outline("mono").first); assertEquals(text("mono"), outline("mono").second)
        assertEquals(1f, outline("linen").first); assertEquals(LINEN_HAIRLINE, outline("linen").second)
        // Day & night is whichever preset is on show, so it is one of the five above.
        assertEquals(setOf("light", "dark", "amoled", "mono", "linen"), PRESETS.keys)
    }

    @Test
    fun `preset's own tiles and Today card are as they were`() {
        // Linen's grey tiles go without the hairline; every other preset's tiles share the card's outline.
        assertFalse(resolveLook("linen").tileOutlined())
        for (p in listOf("light", "dark", "amoled", "mono")) assertTrue(p, resolveLook(p).tileOutlined())
        // AMOLED's tinted Today card keeps its accent outline; Dark is tinted with no outline; the rest are not tinted.
        assertTrue(resolveLook("amoled").heroOutlineAccent())
        for (p in listOf("light", "dark", "mono", "linen")) assertFalse(p, resolveLook(p).heroOutlineAccent())
    }

    @Test
    fun `a chosen border reaches every card`() {
        // Thin or Ink on Linen outline its grey tiles too; only its own hairline leaves them bare.
        assertTrue(outline("linen", "ink").third.tileOutlined())
        assertTrue(outline("linen", "thin").third.tileOutlined())
        // Ink or Hairline on AMOLED: the tinted Today card takes the same outline as the rest, not the accent.
        assertFalse(outline("amoled", "ink").third.heroOutlineAccent())
        assertFalse(outline("amoled", "hairline").third.heroOutlineAccent())
        assertEquals(0f, outline("mono", "none").first)
        assertEquals(1.5f, outline("light", "ink").first)
        // With transparent cards the outline is all there is of a card, and it is kept.
        assertEquals(0f, outline("amoled", "ink").third.cardAlpha)
        assertEquals(resolveLook("amoled").text, outline("amoled", "ink").second)
    }

    @Test
    fun `the border is saved, restored and reset`() {
        val stored = mapOf("theme.preset" to "amoled", "theme.mix.outlines" to "ink", "ui.motion" to "off")
        val saved = SavedPreset("Inked", lookKeysOf(stored))
        assertEquals("ink", saved.keys["theme.mix.outlines"])
        val back = decodeSaved(encodeSaved(listOf(saved))).single()
        assertEquals(saved, back)
        // Restoring a saved look brings the border back over another look's.
        val other = mapOf("theme.preset" to "light", "theme.mix.outlines" to "none", "ui.motion" to "off")
        val restored = withSavedLook(other, back)
        assertEquals("ink", restored["theme.mix.outlines"])
        assertEquals("Inked", matchingSaved(listOf(back), restored)?.name)
        // Reset to preset clears it, keeping the preset.
        assertEquals(mapOf("theme.preset" to "amoled"), resetLookKeys(lookKeysOf(stored)))
        val dayNight = mapOf("theme.preset" to "daynight", "theme.day" to "linen", "theme.mix.outlines" to "thin")
        assertEquals(mapOf("theme.preset" to "daynight", "theme.day" to "linen"), resetLookKeys(dayNight))
        assertEquals(mapOf("theme.preset" to "amoled"), resetLookKeys(emptyMap()))
    }
}
