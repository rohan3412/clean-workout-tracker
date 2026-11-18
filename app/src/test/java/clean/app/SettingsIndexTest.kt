package clean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Settings search finds sections by their name and their words, and every place it leads to is a section on its page. */
class SettingsIndexTest {
    @Test
    fun `finds by name first, then by words`() {
        assertEquals("Rest", searchSettings("rest").first().title)
        assertTrue(searchSettings("pure black").any { it.key == "ap-black" })
        assertTrue(searchSettings("colour-blind").any { it.title == "Colours" })
        assertTrue(searchSettings("restore").any { it.title == "Backup" })
        assertTrue(searchSettings("current split").any { it.title == "Training" })
        assertTrue(searchSettings("accent").any { it.title == "Accent" })
        assertEquals(emptyList<SettingPlace>(), searchSettings("  "))
        assertEquals(emptyList<SettingPlace>(), searchSettings("xyzzy"))
    }

    @Test
    fun `settings that were cut are not found`() {
        for (word in listOf("confetti", "glow", "mono", "linen", "amoled preset", "day & night", "mix and match", "template colours", "one-handed", "launcher shortcuts", "tab bar", "live preview", "font file", "chart style", "compact")) {
            assertEquals(word, emptyList<SettingPlace>(), searchSettings(word))
        }
    }

    @Test
    fun `there are four pages and no Profile or Lists heading`() {
        assertEquals(setOf(PAGE_WORKOUT, PAGE_APPEARANCE, PAGE_DATA, PAGE_ABOUT), SETTINGS_INDEX.map { it.page }.toSet())
        assertFalse(SETTINGS_INDEX.any { it.title == "Profiles" || it.title == "Lists" })
    }

    @Test
    fun `every place is a keyed item on its page`() {
        val source = File("src/main/java/clean/app/Settings.kt").readText()
        val names = mapOf(PAGE_WORKOUT to "PAGE_WORKOUT", PAGE_APPEARANCE to "PAGE_APPEARANCE", PAGE_DATA to "PAGE_DATA", PAGE_ABOUT to "PAGE_ABOUT")
        for (p in SETTINGS_INDEX) {
            assertTrue(p.key, Regex("""page == ${names.getValue(p.page)}\) item\(key = "${Regex.escape(p.key)}""").containsMatchIn(source))
        }
        assertEquals(SETTINGS_INDEX.size, SETTINGS_INDEX.map { it.key }.toSet().size)
    }

    @Test
    fun `every keyed item on a page has a place in the index`() {
        val source = File("src/main/java/clean/app/Settings.kt").readText()
        val keys = Regex("""page == PAGE_\w+\) item\(key = "([\w-]+)"""").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(keys, SETTINGS_INDEX.map { it.key }.toSet())
    }
}
