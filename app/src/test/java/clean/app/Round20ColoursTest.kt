package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.ui.graphics.Color
import clean.app.ui.theme.ACCENTS
import clean.app.ui.theme.Deficiency
import clean.app.ui.theme.Look
import clean.app.ui.theme.MEANING_HUE_GAP
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.colourDistance
import clean.app.ui.theme.contrastRatio
import clean.app.ui.theme.hueGap
import clean.app.ui.theme.meaning
import clean.app.ui.theme.orange
import clean.app.ui.theme.over
import clean.app.ui.theme.prColour
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.seenWith
import clean.app.ui.theme.toOklch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 20 (items 10, 11, 13): the PR colour, the per-theme chart colours and the meaning colours kept clear of the accent, over every theme, Dark and Light, Pure black and the colour-blind-safe set. */
@Category(Regression::class)
class Round20ColoursTest {
    private fun looks(): List<Look> = buildList {
        for (accent in ACCENTS.keys) for (theme in listOf("dark", "light")) for (black in listOf(false, true)) for (safe in listOf(false, true)) {
            if (black && theme == "light") continue
            add(resolveLook(theme, pureBlack = black, accent = accent, colourBlindSafe = safe))
        }
    }
    private fun name(l: Look) = "${l.themeName}/${if (l.dark) "dark" else "light"}/black=${l.pureBlack}/safe=${l.colourBlindSafe}"

    @Test fun `every theme has five chart colours that start with its accent`() {
        for (l in looks()) {
            assertEquals(name(l), 5, l.series.size)
            assertEquals(name(l), l.orange, l.series.first())
        }
    }

    @Test fun `chart colours keep 3 to 1 against page, card and tile and are told apart`() {
        for (l in looks()) {
            val behind = listOf(l.page, l.card, l.tile) + if (l.dark) emptyList() else listOf(Color.White)
            for (c in l.series) for (b in behind) assertTrue("${name(l)}: $c on $b is ${contrastRatio(c, b)}", contrastRatio(c, b) >= 3f)
            for (i in l.series.indices) for (j in i + 1 until l.series.size) {
                val a = l.series[i]; val b = l.series[j]
                assertTrue("${name(l)}: $a and $b are ${colourDistance(a, b)} apart", colourDistance(a, b) >= 9f)
                for (d in listOf(Deficiency.DEUTERANOPIA, Deficiency.PROTANOPIA)) {
                    val seen = colourDistance(a.seenWith(d), b.seenWith(d))
                    assertTrue("${name(l)}: $a and $b are $seen apart with $d", seen >= 6f)
                }
                val trit = colourDistance(a.seenWith(Deficiency.TRITANOPIA), b.seenWith(Deficiency.TRITANOPIA))
                assertTrue("${name(l)}: $a and $b are $trit apart with tritanopia", trit >= 4f)
            }
        }
    }

    @Test fun `no meaning colour sits within the hue gap of a chromatic accent and all keep contrast against the card`() {
        for (l in looks()) {
            val m = l.meaning()
            val accent = l.orange.toOklch()
            for ((word, c) in listOf("done" to m.done, "warmup" to m.warmup, "failure" to m.failure, "drop" to m.drop, "danger" to m.danger, "ready" to m.ready,
                "recovering" to m.recovering, "fatigued" to m.fatigued, "full" to m.full, "underweight" to m.underweight)) {
                val lch = c.toOklch()
                if (accent.c > 0.03f && lch.c > 0.03f) assertTrue("${name(l)}: $word is ${hueGap(lch.h, accent.h)} degrees from the accent", hueGap(lch.h, accent.h) >= MEANING_HUE_GAP - 1f)
                if (word == "danger" && l.themeName == "Crimson") continue
                assertTrue("${name(l)}: $word $c on the card is ${contrastRatio(c, l.card)}", contrastRatio(c, l.card) >= if (l.dark) 3f else 4.0f)
            }
        }
    }

    @Test fun `Green and Ember shift the colours that sat on their accent and Graphite shifts nothing`() {
        val graphite = resolveLook("dark").meaning()
        val moss = resolveLook("dark", accent = "Green").meaning()
        val ember = resolveLook("dark", accent = "Ember").meaning()
        assertNotEquals(graphite.done, moss.done); assertNotEquals(graphite.ready, moss.ready)
        assertNotEquals(graphite.recovering, ember.recovering); assertNotEquals(graphite.warmup, ember.warmup)
        assertEquals(graphite.fatigued, moss.fatigued)
        // Green's good colour (the proposal's own, a teal) lies on the cool side of the accent, Ember's warm ones toward yellow.
        assertTrue(moss.done.toOklch().h > resolveLook("dark", accent = "Green").orange.toOklch().h)
        assertTrue(ember.recovering.toOklch().h > ember.warmup.toOklch().h - 40f)
    }

    /** Round 28: a PR is no longer the accent: the theme's own PR colour (`prColour`), whose text on its chip keeps 4.5 : 1 (Round28Test asserts the rest). */
    @Test fun `the PR chip reads in every theme`() {
        for (l in looks()) {
            if (l.colourBlindSafe || l.pureBlack) continue
            val chip = over(l.prColour, clean.app.prTint(l.dark), l.card)
            assertTrue("${name(l)}: ${contrastRatio(l.prColour, chip)}", contrastRatio(l.prColour, chip) >= 4.5f)
        }
    }

    /** Round 22: the amber of a matched best and of the favourite star (`colorScheme.tertiary`) follows the same rule as the meaning colours. */
    @Test fun `tertiary sits clear of a chromatic accent and keeps its contrast, in every theme and look`() {
        for (l in looks()) {
            val t = l.colorScheme().tertiary
            val accent = l.orange.toOklch()
            val lch = t.toOklch()
            if (accent.c > 0.03f && lch.c > 0.03f) assertTrue("${name(l)}: tertiary $t is ${hueGap(lch.h, accent.h)} degrees from the accent", hueGap(lch.h, accent.h) >= MEANING_HUE_GAP - 1f)
            assertTrue("${name(l)}: tertiary $t on the card is ${contrastRatio(t, l.card)}", contrastRatio(t, l.card) >= if (l.dark) 3f else 4.0f)
            // Still an amber or a yellow-olive, for "matched" and "favourite": a warm hue between orange and green.
            // (Round 34: Saffron's accent sits on the amber, so its amber turns to the orange-brown side, hue 46 to 50.)
            if (lch.c > 0.03f) assertTrue("${name(l)}: tertiary $t has hue ${lch.h}", lch.h in 45f..125f)
        }
    }

    @Test fun `Ember moves tertiary off its orange and the other themes keep the amber`() {
        for (dark in listOf(true, false)) {
            val plain = resolveLook(if (dark) "dark" else "light", accent = "Graphite").colorScheme().tertiary
            assertNotEquals(plain, resolveLook(if (dark) "dark" else "light", accent = "Ember").colorScheme().tertiary)
            for (theme in listOf("Crimson", "Green", "Cobalt", "Iris", "Lagoon", "Rose")) assertEquals("$theme dark=$dark", plain, resolveLook(if (dark) "dark" else "light", accent = theme).colorScheme().tertiary)
        }
    }
}
