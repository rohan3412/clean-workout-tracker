package clean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Settings search finds sections by their name and their words, and every place it leads to is a section on its page. */
class SettingsIndexTest {
    @Test
    fun `finds by name first, then by words`() {
        assertEquals("Rest timer", searchSettings("rest timer").first().title)
        assertTrue(searchSettings("confetti").any { it.title == "Feedback" })
        assertTrue(searchSettings("colour-blind").any { it.title == "Mix and match" })
        assertTrue(searchSettings("restore").any { it.title == "Backup" })
        assertEquals(emptyList<SettingPlace>(), searchSettings("  "))
        assertEquals(emptyList<SettingPlace>(), searchSettings("xyzzy"))
    }

    @Test
    fun `every place is a keyed item on its page`() {
        val source = File("src/main/java/clean/app/Settings.kt").readText()
        for (p in SETTINGS_INDEX) {
            assertTrue(p.key, Regex("""page == "${Regex.escape(p.page)}"\) item\(key = "${Regex.escape(p.key)}""").containsMatchIn(source))
        }
        assertEquals(SETTINGS_INDEX.size, SETTINGS_INDEX.map { it.key }.toSet().size)
    }
}
