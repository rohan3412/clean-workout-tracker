package clean.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateColorsTest {
    @Test fun modeAndColourParse() {
        assertEquals(TemplateColorMode.OFF, templateColorModeOf(null))
        assertEquals(TemplateColorMode.RANDOM, templateColorModeOf("random"))
        assertEquals(TemplateColorMode.MANUAL, templateColorModeOf("manual"))
        assertNull(parseTemplateColor(null))
        assertNull(parseTemplateColor("12"))
        assertNull(parseTemplateColor("x"))
        assertEquals(NO_TEMPLATE_COLOR, parseTemplateColor("none"))
        assertEquals(3, parseTemplateColor("3"))
        assertEquals("none", encodeTemplateColor(NO_TEMPLATE_COLOR))
        assertEquals("7", encodeTemplateColor(7))
    }

    @Test fun scopeParsesAndCovers() {
        assertEquals(TemplateColorScope(), parseTemplateColorScope(null))
        assertEquals(TemplateColorScope(), parseTemplateColorScope("junk"))
        val folders = parseTemplateColorScope("folders:Push;Pull")
        assertEquals(listOf("Push", "Pull"), folders.items)
        assertTrue(folders.covers(1, "push"))
        assertFalse(folders.covers(1, "Legs"))
        assertFalse(folders.covers(1, ""))
        val some = parseTemplateColorScope("some:4;9")
        assertTrue(some.covers(9, ""))
        assertFalse(some.covers(5, "Push"))
        assertEquals("some:4;9", some.encode())
        assertEquals("all", TemplateColorScope().encode())
        assertEquals(folders, parseTemplateColorScope(folders.encode()))
    }

    @Test fun pickIsLeastUsedAndNeverExcluded() {
        val random = Random(1)
        val first = (0 until TEMPLATE_COLOR_COUNT - 1).fold(emptyList<Int>()) { used, _ -> used + pickTemplateColor(used, 4, random) }
        assertEquals(TEMPLATE_COLOR_COUNT - 1, first.toSet().size)
        assertFalse(4 in first)
        // Every allowed colour has been used once, so the next round starts over.
        val second = (0 until TEMPLATE_COLOR_COUNT - 1).fold(first) { used, _ -> used + pickTemplateColor(used, 4, random) }
        assertEquals(setOf(2), second.groupingBy { it }.eachCount().values.toSet())
    }

    @Test fun pickIgnoresNoColour() {
        val used = (1 until TEMPLATE_COLOR_COUNT).toList() + NO_TEMPLATE_COLOR
        assertEquals(0, pickTemplateColor(used, null))
    }

    @Test fun nearestToAccentGoesRoundTheCircle() {
        assertEquals(0, nearestToAccent(350.0))
        assertEquals(9, nearestToAccent(330.0))
        assertEquals(7, nearestToAccent(214.0))
        assertEquals(20.0, hueDistance(350.0, 10.0), 0.001)
    }

    @Test fun hueOfRgbMatchesTheWheel() {
        assertEquals(0.0, hueOfRgb(1.0, 0.0, 0.0), 0.01)
        assertEquals(120.0, hueOfRgb(0.0, 1.0, 0.0), 0.01)
        assertEquals(240.0, hueOfRgb(0.0, 0.0, 1.0), 0.01)
        assertEquals(300.0, hueOfRgb(1.0, 0.0, 1.0), 0.01)
        assertEquals(0.0, hueOfRgb(0.5, 0.5, 0.5), 0.01)
    }

    @Test fun assignKeepsStoredOnesAndNeverRepeatsEarly() {
        val templates = (1L..9L).map { it to "" }
        val stored = mapOf(1L to 0, 2L to NO_TEMPLATE_COLOR)
        val given = assignTemplateColors(templates, stored, TemplateColorScope(), excluded = 5, Random(7))
        assertFalse(1L in given.keys)
        assertFalse(2L in given.keys)
        assertEquals(7, given.size)
        // Colour 0 was held; the seven given are all different from it and from each other, and none is the excluded one.
        assertEquals(7, given.values.toSet().size)
        assertFalse(0 in given.values)
        assertFalse(5 in given.values)
    }

    @Test fun assignOnlyTouchesTemplatesInScope() {
        val templates = listOf(1L to "Push", 2L to "Legs", 3L to "")
        val given = assignTemplateColors(templates, emptyMap(), parseTemplateColorScope("folders:Push"), null, Random(2))
        assertEquals(setOf(1L), given.keys)
    }
}
