package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LookMigrationTest {
    private fun look(vararg pairs: Pair<String, String>) = migratedLook(mapOf(*pairs))

    @Test
    fun presetsMapToThemeAndPureBlack() {
        assertEquals(MigratedLook("dark", true, "Blue"), look("theme.preset" to "amoled"))
        assertEquals(MigratedLook("dark", false, "Blue"), look("theme.preset" to "dark"))
        assertEquals(MigratedLook("light", false, "Orange"), look("theme.preset" to "light"))
        assertEquals(MigratedLook("dark", false, "Blue"), look("theme.preset" to "mono"))
        assertEquals(MigratedLook("dark", false, "Orange"), look("theme.preset" to "linen"))
        assertEquals(MigratedLook("phone", false, "Blue"), look("theme.preset" to "daynight"))
    }

    @Test
    fun noPresetOrAnUnknownOneWasAmoled() {
        assertEquals(MigratedLook("dark", true, "Blue"), look("accent" to "Blue"))
        assertEquals(MigratedLook("dark", true, "Blue"), look("theme.preset" to "saved:Mine"))
    }

    @Test
    fun accentIsKeptOrNearest() {
        assertEquals("Teal", look("theme.preset" to "dark", "accent" to "Teal").accent)
        assertEquals("Purple", look("accent" to "Purple").accent)
        // Red is not one of the six: the nearest by hue.
        assertEquals("Orange", accentFor("Red"))
        // A colour typed by hand: green-ish, magenta-ish, grey.
        assertEquals("Green", accentFor("#FF33CC55"))
        assertEquals("Pink", accentFor("#FFCC2277"))
        assertEquals("Blue", accentFor("#FF888888"))
        assertEquals("Blue", accentFor("Ink"))
        assertEquals("Blue", accentFor(null))
        assertEquals("Blue", accentFor("#zzzzzz"))
        assertEquals("Blue", accentFor("#12"))
    }

    @Test
    fun olderSingleOptionsStillCount() {
        assertEquals(MigratedLook("light", false, "Blue"), look("theme.preset" to "amoled", "theme" to "Light"))
        assertEquals(MigratedLook("phone", true, "Blue"), look("theme.preset" to "amoled", "theme" to "System"))
        assertEquals(MigratedLook("dark", false, "Blue"), look("theme.preset" to "amoled", "background" to "Dark grey"))
        assertEquals(MigratedLook("dark", true, "Blue"), look("theme.preset" to "dark", "background" to "Black"))
        // Day & night keeps its own rule: the `theme` option never applied there.
        assertEquals("phone", look("theme.preset" to "daynight", "theme" to "Dark").theme)
    }

    @Test
    fun migrationWritesOnceAndIsIdempotent() {
        val old = mapOf("theme.preset" to "light", "accent" to "Teal")
        val written = migrateLook(old)!!
        assertEquals(mapOf(LOOK_THEME_KEY to "light", LOOK_BLACK_KEY to "off", LOOK_ACCENT_KEY to "Teal", LOOK_DONE_KEY to "1"), written)
        // Run again on what is stored now: nothing more to write, and a later choice is never overwritten.
        assertNull(migrateLook(old + written))
        assertNull(migrateLook(old + written + (LOOK_THEME_KEY to "dark")))
    }

    @Test
    fun aNewInstallOnlyGetsTheMarker() {
        assertEquals(mapOf(LOOK_DONE_KEY to "1"), migrateLook(emptyMap()))
        assertEquals(mapOf(LOOK_DONE_KEY to "1"), migrateLook(mapOf("active" to "1", "textSize" to "Large")))
    }

    @Test
    fun garbageNeverThrows() {
        assertEquals("dark", look("theme.preset" to "", "theme" to "???", "background" to "#nope", "accent" to "").theme)
        assertEquals(false, look("theme.preset" to "light", "background" to "Black").pureBlack)
    }

    @Test
    fun oldAccentsBecomeThemes() {
        val expected = mapOf("Blue" to "Graphite", "Teal" to "Ice", "Green" to "Moss", "Orange" to "Ember", "Pink" to "Rose", "Purple" to "Plum")
        for ((old, theme) in expected) assertEquals(theme, themeFor(old), old)
        assertEquals("Graphite", themeFor(null))
        assertEquals("Graphite", themeFor("Red"))
        assertEquals("Graphite", themeFor("#FF33CC55"))
        assertEquals("Ocean", themeFor("Ocean"))
        assertEquals("Cobalt", themeFor("Cobalt"))
        assertEquals(12, ACCENT_CHOICES.size)
        assertEquals(8, OLD_THEME_CHOICES.size)
        assertEquals("Graphite", ACCENT_CHOICES.first())
    }

    @Test
    fun theEightThemesBecomeTwelveOnce() {
        // Round 34: Moss to Green, Ocean to Cobalt, Ice to Lagoon, Plum to Iris; the rest keep their names.
        val expected = mapOf("Moss" to "Green", "Ocean" to "Cobalt", "Ice" to "Lagoon", "Plum" to "Iris", "Rose" to "Rose", "Ember" to "Ember", "Crimson" to "Crimson", "Graphite" to "Graphite")
        for ((old, now) in expected) assertEquals(now, twelveThemeFor(old), old)
        for (old in OLD_THEME_CHOICES) assertTrue(twelveThemeFor(old) in ACCENT_CHOICES, old)
        assertEquals("Graphite", twelveThemeFor(null)); assertEquals("Graphite", twelveThemeFor("Mono"))
        // The new names stay (a second look at a migrated value changes nothing).
        for (name in ACCENT_CHOICES) assertEquals(name, twelveThemeFor(name))
        // The backgrounds that are gone: Blueprint is Grid, Marks is Plus, Default is Plain, any other dropped one is Theme's own; a kept id and Theme's own stay.
        val bg = mapOf("blueprint" to "grid", "marks" to "plus", "default" to "plain", "prism" to "theme", "slant" to "theme", "ripple" to "theme", "tide" to "theme", "beams" to "theme", "pulse" to "theme",
            "topo" to "topo", "pools" to "pools", "studio" to "studio", "dots" to "dots", "photo" to "photo", "theme" to "theme")
        for ((old, now) in bg) assertEquals(now, twelveBackgroundFor(old), old)
        assertNull(twelveBackgroundFor(null))
        val written = migrateTwelveThemes(mapOf(LOOK_ACCENT_KEY to "Ocean", "look.background" to "blueprint", LOOK_V2_KEY to "1"))!!
        assertEquals(mapOf(LOOK_ACCENT_KEY to "Cobalt", LOOK_V3_KEY to "1", "look.background" to "grid"), written)
        assertEquals(mapOf(LOOK_ACCENT_KEY to "Graphite", LOOK_V3_KEY to "1"), migrateTwelveThemes(emptyMap()))
        assertNull(migrateTwelveThemes(mapOf(LOOK_ACCENT_KEY to "Cobalt", LOOK_V3_KEY to "1")))
    }

    @Test
    fun themeMigrationRunsOnceAndAfterTheFirst() {
        assertEquals(mapOf(LOOK_ACCENT_KEY to "Ember", LOOK_V2_KEY to "1"), migrateLookTheme(mapOf(LOOK_ACCENT_KEY to "Orange", LOOK_DONE_KEY to "1")))
        assertEquals(mapOf(LOOK_ACCENT_KEY to "Graphite", LOOK_V2_KEY to "1"), migrateLookTheme(emptyMap()))
        // Never again: a later choice of Teal-looking names or anything else is left alone.
        assertNull(migrateLookTheme(mapOf(LOOK_ACCENT_KEY to "Ocean", LOOK_V2_KEY to "1")))
        // From an old install: the first migration's result feeds the second.
        val v1 = migrateLook(mapOf("theme.preset" to "light", "accent" to "Teal"))!!
        assertEquals("Ice", migrateLookTheme(v1)!![LOOK_ACCENT_KEY])
    }
}
