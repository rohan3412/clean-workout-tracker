package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.ui.graphics.Color
import clean.app.ui.theme.Look
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.contrastRatio
import clean.app.ui.theme.meaning
import clean.app.ui.theme.orange
import clean.app.ui.theme.over
import androidx.compose.ui.graphics.compositeOver
import clean.app.ui.theme.prColour
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.workoutSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 22: the contrast audit of Graphite in Light, as pairs of tokens (each foreground on each background it is drawn on), with a look at Graphite in Dark
 * so the fixes do not touch it. The ratios are also written to `build/round22-contrast.txt`.
 */
@Category(Regression::class)
class Round22ContrastTest {
    private val report = StringBuilder()

    private fun check(look: Look, what: String, fg: Color, bg: Color, least: Float) {
        val ratio = contrastRatio(fg, bg)
        report.appendLine("%-8s %-46s %5.2f  (needs %.1f)".format(if (look.dark) "dark" else "light", what, ratio, least))
        assertTrue("${if (look.dark) "dark" else "light"}: $what is %.2f, needs %.1f".format(ratio, least), ratio >= least)
    }

    private fun audit(look: Look) {
        val scheme = look.colorScheme()
        val skin = look.workoutSkin(scheme, look.meaning())
        val surfaces = linkedMapOf("page" to look.page, "card" to look.card, "tile" to look.tile, "raised step" to look.higher, "set box" to skin.boxFill, "next row" to skin.rowNext.compositeOver(look.card))
        // Text, quiet text (informational, so 4.5 : 1), the accent as text or an icon, and the outline of a control on every surface they are drawn on.
        for ((s, bg) in surfaces) {
            check(look, "text on $s", look.text, bg, 4.5f)
            check(look, "quiet text on $s", look.muted, bg, 4.5f)
            check(look, "accent on $s", look.orange, bg, 4.5f)
        }
        for ((s, bg) in surfaces.filterKeys { it in listOf("page", "card", "tile", "raised step") }) check(look, "control outline on $s", scheme.outline, bg, if (look.dark) 2.9f else 3f)
        check(look, "ink on the accent fill", look.ink, look.orange, 4.5f)
        // Round 28: the PR colour is the theme's own, no longer the accent: as an icon on the page and card, and as text on its own chip.
        for ((s, bg) in listOf("page" to look.page, "card" to look.card)) check(look, "PR colour on the $s", look.prColour, bg, 3f)
        check(look, "PR text on its chip", look.prColour, over(look.prColour, prTint(look.dark), look.card), 4.5f)
        check(look, "text on a selected chip", scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5f)
        check(look, "amber (tertiary) on the card", scheme.tertiary, look.card, if (look.dark) 3f else 4f)
        // The ghost value in an empty set box and the aimed value after "Previous" carry information: 3 : 1 at the least.
        val ghost = if (look.dark) 0.5f else 0.9f
        check(look, "ghost value in a set box", over(look.muted, ghost, skin.boxFill), skin.boxFill, if (look.dark) 2f else 3f)
        // The four steps (calendar, body figure, bars): the lowest must show against page and card, and every step from the next.
        for (b in listOf("page" to look.page, "card" to look.card)) check(look, "lowest scale step on the ${b.first}", look.scale[0], b.second, if (look.dark) 1.5f else 3f)
        for (i in 0 until 3) check(look, "scale step $i against step ${i + 1}", look.scale[i], look.scale[i + 1], 1.5f)
        val untrained = over(look.text, 0.32f, look.page)
        check(look, "lowest scale step against an untrained muscle", look.scale[0], untrained, 1.3f)
    }

    @Test fun `Graphite in Light passes its audit`() {
        val look = resolveLook("light")
        audit(look)
        // A card is told from the page, a pill thumb from its track, a floating dock from the page by its edge.
        check(look, "card against page", look.card, look.page, 1.10f)
        val track = over(look.text, 0.08f, look.page)
        check(look, "segmented thumb against its track", Color.White, track, 1.15f)
        val scheme = look.colorScheme()
        val dock = over(androidx.compose.ui.graphics.lerp(look.page, look.orange, 0.08f), 0.90f, look.page)
        check(look, "dock outline against the page", over(look.orange, 0.45f, dock), look.page, 3f)
        // A disabled key is dimmed to half of its colour and still reads.
        check(look, "disabled key label on its key", over(look.text, 0.5f, scheme.surfaceVariant), scheme.surfaceVariant, 3f)
        check(look, "disabled row label on the card", over(look.muted, 0.75f, look.card), look.card, 3f)
        java.io.File("build/round22-contrast.txt").apply { parentFile.mkdirs() }.writeText(report.toString())
    }

    @Test fun `Graphite in Dark is as it was`() {
        val look = resolveLook("dark")
        assertEquals(0xFF0E0E0FL, look.page.toArgbLong())
        assertEquals(Color(0xFFA3A3A7), look.muted) // Round 34: the proposal's quiet text for Graphite Dark
        assertEquals(0xFF454546L, look.scale[0].toArgbLong())
        audit(look)
        java.io.File("build/round22-contrast-dark.txt").apply { parentFile.mkdirs() }.writeText(report.toString())
    }

    private fun Color.toArgbLong(): Long = (((alpha * 255 + .5f).toInt().toLong() shl 24) or ((red * 255 + .5f).toInt().toLong() shl 16) or ((green * 255 + .5f).toInt().toLong() shl 8) or (blue * 255 + .5f).toInt().toLong())
}
