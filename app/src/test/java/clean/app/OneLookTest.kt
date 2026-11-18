package clean.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Guards that keep the screens on the shared pieces of one look instead of styles typed in place. */
class OneLookTest {
    /** The screens' sources: the app's files outside `ui/theme`, where the look's own values live. */
    private val sources: Map<String, String> =
        listOf(File("src/main/java/clean/app"), File("app/src/main/java/clean/app")).first { it.exists() }
            .listFiles()!!.filter { it.extension == "kt" }.associate { it.name to it.readText() }

    private fun count(text: String, needle: String) = text.split(needle).size - 1

    @Test
    fun `section titles go through SectionTitle`() {
        // A title-medium bold line is a section title unless it is one of these names or numbers, which have their own place.
        val allowed = mapOf(
            "Common.kt" to 1,     // SectionTitle itself
            "Active.kt" to 2,     // an exercise's name in a workout, in the accent and draggable; the value in a number box
            "Workouts.kt" to 2,   // a workout card's name, as the card's title and as the row the History card's row travels from
            "Share.kt" to 1,      // a figure on the share picture
            "Photos.kt" to 2,     // captions over a full-screen photo and a comparison
        )
        val found = sources.mapValues { count(it.value, "typography.titleMedium, fontWeight = FontWeight.Bold") }.filterValues { it > 0 }
        assertEquals(allowed, found)
    }

    @Test
    fun `no screen types a card corner`() {
        // A corner of 12 dp or more is a card's, a sheet's or the tab bar's: cards take cardShape(), cardCorner() or cardTopShape() from the look.
        val typed = Regex("""(RoundedCornerShape\(|topStart = |topEnd = )(\d+)(\.\d+)?\.dp""")
        val found = sources.flatMap { (name, text) -> typed.findAll(text).filter { it.groupValues[2].toInt() >= 12 }.map { "$name: ${it.value}" } }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `spacing comes from the steps`() {
        // Inside a padding, a gap or a spacer, a number from 2 to 32 dp is a step of Space; smaller is a hairline, larger a size.
        val start = Regex("""(?<![A-Za-z0-9_])(padding|spacedBy|PaddingValues)\(|Spacer\(Modifier\.(height|width|size)\(""")
        val literal = Regex("""(?<![A-Za-z0-9_.])(\d+)(\.\d+)?\.dp""")
        val found = mutableListOf<String>()
        for ((name, text) in sources) for (m in start.findAll(text)) {
            var depth = 1
            var end = m.range.last + 1
            while (depth > 0 && end < text.length) { if (text[end] == '(') depth++ else if (text[end] == ')') depth--; end++ }
            literal.findAll(text.substring(m.range.last + 1, end)).filter { it.groupValues[1].toInt() in 2..32 }.forEach { found += "$name: ${m.value}…${it.value}" }
        }
        for ((name, text) in sources) Regex("""(spacing|inner|outer)(: Dp)? = (\d+)\.dp""").findAll(text)
            .filter { it.groupValues[3].toInt() in 2..32 }.forEach { found += "$name: ${it.value}" }
        assertEquals(emptyList<String>(), found)
    }
}
