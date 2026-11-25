package clean.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Search speed over the whole built-in library (about 1600 exercises, with their other names and muscles), typed a letter at a time:
 * every keystroke ranks the whole list. The budget is for this PC's JVM; a phone is several times slower, so it is kept well under a frame.
 */
class ExerciseSearchSpeedTest {
    @Test fun `ranking the whole library per keystroke is fast`() {
        val targets = LIBRARY.map { e -> SearchTarget(e.name, otherNames(e.name), e.muscles.primary.map { it.label } + "Machine") }
        val typed = listOf("lateral raise dumbell", "iso lat row", "trciep pushdwn", "ohp", "incline chest press machine")
        fun run(): Int {
            var hits = 0
            for (q in typed) for (n in 1..q.length) {
                val query = SearchQuery(q.take(n))
                for (t in targets) if (query.rank(t) != null) hits++
            }
            return hits
        }
        repeat(3) { run() }
        val keystrokes = typed.sumOf { it.length }
        val start = System.nanoTime()
        val rounds = 5
        repeat(rounds) { run() }
        val perKey = (System.nanoTime() - start) / 1e6 / (rounds * keystrokes)
        println("Search: ${targets.size} exercises, %.3f ms per keystroke".format(perKey))
        assertTrue(perKey < 1.5, "%.3f ms per keystroke".format(perKey))
    }
}
