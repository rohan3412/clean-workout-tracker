package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
