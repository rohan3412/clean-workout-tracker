package clean.app

import androidx.compose.ui.graphics.Color
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.meaning
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.workoutSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workout's fills and outlines come from the look: Mono is paper and ink, Linen grey tiles and a black tick, the rest as they always were. */
class SkinTest {
    private fun skin(preset: String) = resolveLook(preset).let { it.workoutSkin(it.colorScheme(), it.meaning()) }

    @Test
    fun `mono boxes, ticks and keys are outlined in ink`() {
        val look = resolveLook("mono")
        val s = skin("mono")
        assertEquals(look.text, s.boxBorder)
        assertEquals(look.text, s.focusBorder)
        assertEquals(look.text, s.tickIdleBorder)
        assertEquals(look.text, s.keyBorder)
        assertEquals(look.page, s.boxFill)
        assertEquals(look.text, s.tickDone)
        assertEquals(look.page, s.checkDone)
        assertTrue(s.focusWidth > s.lineWidth)
        assertTrue(s.rowDone.alpha < 0.2f)
    }

    @Test
    fun `negative mono follows the inverted scheme`() {
        val mono = resolveLook("mono")
        val neg = resolveLook("mono", mono = clean.app.ui.theme.MonoSettings(negative = true))
        val s = neg.workoutSkin(neg.colorScheme(), neg.meaning())
        assertNotEquals(skin("mono").boxFill, s.boxFill)
        assertEquals(neg.colorScheme().background, s.boxFill)
        assertEquals(neg.colorScheme().onSurface, s.boxBorder)
        assertTrue(neg.negative && mono.mono)
    }

    @Test
    fun `mono dialogs are paper`() {
        val look = resolveLook("mono")
        assertEquals(look.page, look.colorScheme().surfaceContainerHigh)
        assertEquals(look.page, look.colorScheme().surfaceContainerHighest)
    }

    @Test
    fun `linen uses tiles, a faint orange tint and a black tick`() {
        val look = resolveLook("linen")
        val s = skin("linen")
        assertEquals(look.tile, s.boxFill)
        assertEquals(Color.Transparent, s.boxBorder)
        assertEquals(look.tile, s.tickIdle)
        assertEquals(look.text, s.tickDone)
        assertEquals(Color.White, s.checkDone)
        assertEquals(look.orange(), s.rowDone.copy(alpha = 1f))
        assertNotEquals(look.meaning().done, s.tickDone)
        assertEquals(Color.White, look.colorScheme().surfaceContainerHigh)
        assertNull(s.panelBorder)
    }

    @Test
    fun `light, dark and amoled keep the values the workout always had`() {
        for (id in listOf("light", "dark", "amoled")) {
            val look = resolveLook(id)
            val scheme = look.colorScheme()
            val meaning = look.meaning()
            val s = look.workoutSkin(scheme, meaning)
            assertEquals(id, scheme.surfaceVariant, s.boxFill)
            assertEquals(id, Color.Transparent, s.boxBorder)
            assertEquals(id, scheme.primary, s.focusBorder)
            assertEquals(id, meaning.done.copy(alpha = 0.24f), s.rowDone)
            assertEquals(id, scheme.primary.copy(alpha = 0.30f), s.rowNext)
            assertEquals(id, scheme.surfaceVariant.copy(alpha = 0.35f), s.rowIdleHalf)
            assertEquals(id, scheme.surfaceVariant, s.tickIdle)
            assertEquals(id, meaning.done, s.tickDone)
            assertEquals(id, Color.Black, s.checkDone)
            assertEquals(id, scheme.surfaceContainer, s.padFill)
            assertEquals(id, scheme.surfaceVariant, s.keyFill)
            assertEquals(id, Color.Transparent, s.keyBorder)
            assertNull(id, s.panelBorder)
        }
    }

    private fun clean.app.ui.theme.Look.orange(): Color = if (dark) accent.first else accent.second
}
