package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.Part
import clean.app.ui.theme.PRESETS
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.joinNames
import clean.app.ui.theme.partChoices
import clean.app.ui.theme.words
import clean.app.ui.theme.resolveLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four presets give the values of the spec's table; a mix changes one part; AMOLED is the look the app had before presets. */
class PresetsTest {
    @Test
    fun `every preset has a valid value for every part`() {
        for ((id, parts) in PRESETS) for (part in Part.entries) assertTrue("$id $part", parts[part] in part.options)
    }

    @Test
    fun `light preset`() {
        val look = resolveLook("light")
        assertFalse(look.dark)
        assertEquals(Color(0xFFEFEFED), look.page)
        assertEquals(Color.White, look.raised)
        assertEquals(Color(0xFF121212), look.text)
        assertEquals(Color(0xFF8C8C89), look.muted)
        assertEquals(Color(0xFFEC6A2E), look.accent.first)
        assertEquals(1f, look.cardAlpha)
        assertEquals(0f, look.outlineWidth)
        assertTrue(look.shadow)
        assertEquals(0f, look.heroTint)
        assertTrue(look.blackTabBar)
        assertTrue(look.roundButtons)
        assertEquals(0.07f, look.chartFill)
        assertEquals(1f, look.speed)
    }

    @Test
    fun `dark preset`() {
        val look = resolveLook("dark")
        assertTrue(look.dark)
        assertEquals(Color(0xFF121316), look.page)
        assertEquals(Color(0xFF1C1E24), look.raised)
        assertEquals(Color(0xFF8AB4FF), look.accent.first)
        assertEquals(1f, look.cardAlpha)
        assertEquals(0f, look.outlineWidth)
        assertFalse(look.shadow)
        assertEquals(0.1f, look.heroTint)
        assertFalse(look.blackTabBar)
        assertFalse(look.roundButtons)
        assertEquals(0.22f, look.chartFill)
    }

    @Test
    fun `amoled preset is today's look`() {
        val look = resolveLook("amoled")
        assertTrue(look.dark)
        assertEquals(Color.Black, look.page)
        assertEquals(0f, look.cardAlpha)
        assertEquals(1f, look.outlineWidth)
        assertEquals(null, look.outline)
        assertEquals(0.1f, look.heroTint)
        assertEquals(0.22f, look.chartFill)
        assertEquals("Ember", look.scaleName)
        assertEquals(PALETTES.getValue("Ember"), look.scale)
        assertEquals(resolveLook("nonsense"), look)
    }

    @Test
    fun `mono preset`() {
        val look = resolveLook("mono")
        assertFalse(look.dark)
        assertEquals(Color(0xFFFFFFFF), look.page)
        assertEquals(Color(0xFF16181D), look.text)
        assertEquals(Color(0xFF16181D), look.accent.first)
        assertEquals(1.5f, look.outlineWidth)
        assertEquals(0f, look.heroTint)
        assertEquals(160f / 300f, look.speed)
        assertEquals(0.07f, look.chartFill)
    }

    @Test
    fun `linen preset`() {
        val look = resolveLook("linen")
        assertFalse(look.dark)
        assertEquals(Color(0xFFF1EFEA), look.page)
        assertEquals(Color.White, look.raised)
        assertEquals(Color(0xFFF3F1ED), look.tile)
        assertEquals(Color(0xFF1C1C1A), look.text)
        assertEquals(Color(0xFF8A8883), look.muted)
        assertEquals(Color(0xFFA9A7A1), look.faint)
        assertEquals(Color(0xFFE8642A), look.accent.first)
        assertEquals(Color(0xFFE8642A), look.accent.second)
        assertEquals(1f, look.cardAlpha)
        assertEquals(1f, look.outlineWidth)
        assertEquals(Color(0xFFE6E3DD), look.outline)
        assertEquals(Color(0xFFE6E3DD), look.colorScheme().outlineVariant)
        assertFalse(look.shadow)
        assertEquals(0f, look.heroTint)
        assertTrue(look.blackTabBar)
        assertTrue(look.roundButtons)
        assertEquals(26f, look.corner)
        assertTrue(look.inter)
        assertTrue(look.lightNumbers)
        assertFalse(look.plexNumbers)
        assertTrue(look.inkMainButton)
        assertTrue(look.sheetPage)
        assertTrue(look.twoToneTitles)
        assertTrue(look.iconCircles)
        assertTrue(look.chartFade)
        assertEquals(Color(0xFF1C1C1A), look.chartLine)
        assertEquals(1.5f, look.chartLineWidth)
        assertEquals(0.18f, look.texture)
        assertEquals(1f, look.speed)
        assertEquals("linen" to "Orange", look.let { it.icon to it.iconAccent })
        assertEquals("orange", look.scaleName)
    }

    @Test
    fun `the four older presets keep today's values for the new fields`() {
        for (id in listOf("light", "dark", "amoled", "mono")) {
            val look = resolveLook(id)
            assertEquals(id, 18f, look.corner)
            assertFalse(id, look.inter || look.lightNumbers || look.inkMainButton || look.sheetPage || look.twoToneTitles || look.iconCircles || look.chartFade)
            assertEquals(id, null, look.chartLine)
            assertEquals(id, null, look.divider)
            assertEquals(id, look.muted, look.faint)
            assertEquals(id, look.raised, look.tile)
        }
        assertEquals(0f, resolveLook("light").texture)
        assertEquals(0.35f, resolveLook("mono").texture)
        assertEquals(null, resolveLook("amoled").outline)
        assertEquals(resolveLook("mono").text, resolveLook("mono").outline)
    }

    @Test
    fun `a mix changes one part only`() {
        val plain = resolveLook("light")
        val mixed = resolveLook("light", mapOf(Part.BUTTONS to "card"))
        assertFalse(mixed.roundButtons)
        assertEquals(plain.copy(roundButtons = false), mixed)
        // An id that is not one of the part's is ignored.
        assertEquals(plain, resolveLook("light", mapOf(Part.BUTTONS to "nonsense")))
    }

    @Test
    fun `overrides win over the preset`() {
        val look = resolveLook("amoled", accentOverride = "Teal", scaleOverride = "Ocean", modeOverride = "Light", backgroundOverride = "Dark grey")
        assertFalse(look.dark)
        assertEquals(ACCENTS.getValue("Teal"), look.accent)
        assertEquals(PALETTES.getValue("Ocean"), look.scale)
        assertEquals(Color(0xFF121316), resolveLook("amoled", backgroundOverride = "Dark grey").page)
    }

    // The scheme the app built before presets, for accent Blue, background Black, theme Dark (captured from the old code).
    @Test
    fun `amoled dark scheme equals the one before presets`() {
        val s = resolveLook("amoled", accentOverride = "Blue", modeOverride = "Dark", backgroundOverride = "Black").colorScheme()
        val blue = Color(0xFF8AB4FF)
        assertEquals(blue, s.primary)
        assertEquals(Color(0xFF0B0D12), s.onPrimary)
        assertEquals(lerp(Color.Black, blue, 0.3f), s.primaryContainer)
        assertEquals(lerp(Color.Black, blue, 0.24f), s.secondaryContainer)
        assertEquals(Color.Black, s.background)
        assertEquals(Color.Black, s.surface)
        assertEquals(Color(0x12 / 255f, 0x13 / 255f, 0x16 / 255f), s.surfaceContainer)
        assertEquals(Color(0xFF121316), s.surfaceContainerLow)
        assertEquals(Color(0xFF1C1E24), s.surfaceVariant)
        assertEquals(Color(0xFF1C1E24), s.surfaceContainerHighest)
        assertEquals(Color(0xFFF2F3F5), s.onSurface)
        assertEquals(Color(0xFFA0A4AE), s.onSurfaceVariant)
        assertEquals(Color(0xFFF2F3F5), s.inverseSurface)
        assertEquals(Color(0xFF2456B8), s.inversePrimary)
        assertEquals(Color(0xFF6A6F7B), s.outline)
        assertEquals(Color(0xFF3A3D46), s.outlineVariant)
    }

    @Test
    fun `part choices group the presets that share a value`() {
        val background = partChoices(Part.BACKGROUND)
        assertEquals(listOf("light", "slate", "black", "paper", "linen"), background.map { it.id })
        val font = partChoices(Part.FONT)
        assertEquals(listOf("roboto", "inter"), font.map { it.id })
        assertEquals(listOf("light", "dark", "amoled", "mono"), font.first().presets)
        assertEquals("Roboto, the phone's font (Light, Dark, AMOLED and Mono)", font.first().words())
        assertEquals("Inter (Linen)", font.last().words())
        val shadows = partChoices(Part.SHADOWS)
        assertEquals("None (Dark, AMOLED, Mono and Linen)", shadows.first { it.id == "none" }.words())
        assertEquals("A", joinNames(listOf("A")))
        assertEquals("A and B", joinNames(listOf("A", "B")))
    }

    @Test
    fun `the icon follows the tile and the accent`() {
        assertEquals("black" to "Blue", resolveLook("amoled").let { it.icon to it.iconAccent })
        assertEquals("white" to "Orange", resolveLook("light").let { it.icon to it.iconAccent })
        assertEquals("paper" to "Ink", resolveLook("mono").let { it.icon to it.iconAccent })
        assertEquals("Teal", resolveLook("amoled", accentOverride = "Teal").iconAccent)
        assertEquals("Ink", resolveLook("amoled", iconAccentOverride = "Ink").iconAccent)
        assertEquals("Red", resolveLook("amoled", accentOverride = "#FFE53935").iconAccent)
        assertEquals("dark", resolveLook("amoled", mixes = mapOf(Part.ICON to "dark")).icon)
    }
}

/** Saved presets survive any name, and match the stored look only when every key is the same. */
class SavedPresetsTest {
    @Test
    fun `encode and decode keep odd names and values`() {
        val list = listOf(
            clean.app.ui.theme.SavedPreset("Night, \"dark\" & = ö 日本", mapOf("theme.preset" to "dark", "accent" to "#FF8800", "theme.mix.cards" to "a,b c")),
            clean.app.ui.theme.SavedPreset("Plain", mapOf("theme.preset" to "amoled")),
        )
        assertEquals(list, clean.app.ui.theme.decodeSaved(clean.app.ui.theme.encodeSaved(list)))
        assertEquals(emptyList<clean.app.ui.theme.SavedPreset>(), clean.app.ui.theme.decodeSaved(null))
    }

    @Test
    fun `a saved preset matches only the exact stored look`() {
        val saved = listOf(clean.app.ui.theme.SavedPreset("A", mapOf("theme.preset" to "dark", "accent" to "Blue")))
        val same = mapOf("theme.preset" to "dark", "accent" to "Blue", "ui.motion" to "off", "theme.saved" to "x")
        assertEquals("A", clean.app.ui.theme.matchingSaved(saved, same)?.name)
        assertEquals(null, clean.app.ui.theme.matchingSaved(saved, same + ("theme.mix.cards" to "glass")))
        assertEquals(null, clean.app.ui.theme.matchingSaved(saved, mapOf("theme.preset" to "dark")))
    }

    @Test
    fun `day and night take a saved look by name, never one that is itself day and night`() {
        val list = listOf(
            clean.app.ui.theme.SavedPreset("Warm", mapOf("theme.preset" to "light", "accent" to "Orange")),
            clean.app.ui.theme.SavedPreset("Loop", mapOf("theme.preset" to "daynight")),
        )
        assertEquals(listOf("Warm"), clean.app.ui.theme.dayNightSaved(list).map { it.name })
        assertEquals("saved:Warm", clean.app.ui.theme.dayNightChoice("saved:Warm", list, "light"))
        assertEquals("mono", clean.app.ui.theme.dayNightChoice("mono", list, "light"))
        assertEquals("light", clean.app.ui.theme.dayNightChoice("saved:Loop", list, "light"))
        assertEquals("dark", clean.app.ui.theme.dayNightChoice("saved:Gone", list, "dark"))
        assertEquals("dark", clean.app.ui.theme.dayNightChoice(null, list, "dark"))
        val options = mapOf("theme.preset" to "daynight", "theme.day" to "saved:Warm", "theme.mix.cards" to "clear", "ui.motion" to "off")
        assertEquals(mapOf("ui.motion" to "off", "theme.preset" to "light", "accent" to "Orange"), clean.app.ui.theme.withSavedLook(options, list[0]))
    }

    @Test
    fun `tab bar style - words for linen, icons for the rest, swappable`() {
        assertTrue(resolveLook("linen").tabWords)
        for (id in listOf("light", "dark", "amoled", "mono")) assertFalse(id, resolveLook(id).tabWords)
        assertTrue(resolveLook("light", mapOf(Part.TAB_STYLE to "words")).tabWords)
        assertFalse(resolveLook("linen", mapOf(Part.TAB_STYLE to "icons")).tabWords)
        assertEquals(listOf("icons", "words"), partChoices(Part.TAB_STYLE).map { it.id })
    }
}
