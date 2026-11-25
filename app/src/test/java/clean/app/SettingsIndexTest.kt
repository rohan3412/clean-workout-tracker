package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Settings search finds sections by their name and their words, and every place it leads to is a section on its page (nine pages since Round 37). */
@Category(Regression::class)
class SettingsIndexTest {
    private val pages = listOf(PAGE_WORKOUT, PAGE_TRAINING, PAGE_LOOK, PAGE_SOUND, PAGE_PRIVACY, PAGE_BACKUP, PAGE_TRANSFER, PAGE_LIBRARY, PAGE_ABOUT)

    @Test
    fun `finds by name first, then by words`() {
        assertEquals("Rest timer", searchSettings("rest").first().title)
        assertTrue(searchSettings("pure black").any { it.key == "ap-black" })
        assertTrue(searchSettings("colour-blind").any { it.title == "Colour-blind safe colours" })
        assertTrue(searchSettings("restore").any { it.title == "Restore a backup" })
        assertTrue(searchSettings("current split").any { it.title == "Current split" && it.page == PAGE_TRAINING })
        assertTrue(searchSettings("accent").any { it.title == "Theme" })
        for (name in clean.app.ui.theme.THEMES.keys) assertTrue(name, searchSettings(name.lowercase()).any { it.key == "ap-theme" })
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
    fun `there are nine pages and no Profile or Lists heading`() {
        assertEquals(pages, SETTINGS_PAGE_DEFS.map { it.id })
        assertEquals(pages.toSet(), SETTINGS_INDEX.map { it.page }.toSet())
        assertFalse(SETTINGS_INDEX.any { it.title == "Profiles" || it.title == "Lists" })
        assertEquals(pages, SETTINGS_MAIN_GROUPS.flatten())
    }

    @Test
    fun `every place is a keyed section on its page`() {
        val source = File("../shared/src/commonMain/kotlin/clean/app/Settings.kt").readText()
        for (p in SETTINGS_INDEX) {
            assertTrue(p.key, Regex(""""${Regex.escape(p.key)}" -> section\("${Regex.escape(p.key)}"""").containsMatchIn(source))
            assertTrue("${p.key} is on ${p.page}", p.key in settingsPageDef(p.page)!!.sections)
        }
    }

    @Test
    fun `every section of a page has a place in the index and every chip leads to one`() {
        val source = File("../shared/src/commonMain/kotlin/clean/app/Settings.kt").readText()
        val keys = Regex(""""([\w-]+)" -> section\("""").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(keys, SETTINGS_INDEX.map { it.key }.toSet())
        assertEquals(keys, SETTINGS_PAGE_DEFS.flatMap { it.sections }.toSet())
        assertEquals("a section is on one page", SETTINGS_PAGE_DEFS.flatMap { it.sections }.size, keys.size)
        for (page in SETTINGS_PAGE_DEFS) for (chip in page.chips) for (k in chip.keys) assertTrue("${chip.label}: $k", k in page.sections)
    }
}
