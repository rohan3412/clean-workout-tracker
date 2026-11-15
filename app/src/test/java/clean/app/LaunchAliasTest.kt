package clean.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Changing the look switches the launcher icon, which disables the alias the app was opened from. Android closes a task begun by a
 * disabled alias (the "crash" on changing presets), so no alias may start MainActivity: each opens Launch, which has a task of its own.
 */
class LaunchAliasTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        .parse(listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() })

    private fun elements(tag: String) = manifest.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    @Test
    fun `every launcher icon alias opens Launch, never MainActivity`() {
        val aliases = elements("activity-alias").filter { it.getAttributeNS(android, "name").startsWith(".Icon") }
        assertTrue(aliases.size >= 40)
        for (alias in aliases) assertEquals(alias.getAttributeNS(android, "name"), ".Launch", alias.getAttributeNS(android, "targetActivity"))
    }

    @Test
    fun `Launch has a task affinity of its own and stays out of recents`() {
        val launch = elements("activity").single { it.getAttributeNS(android, "name") == ".Launch" }
        assertTrue(launch.getAttributeNS(android, "taskAffinity").let { it.isNotEmpty() && it != "clean.app" })
        assertEquals("true", launch.getAttributeNS(android, "excludeFromRecents"))
        assertEquals("true", launch.getAttributeNS(android, "noHistory"))
    }
}
