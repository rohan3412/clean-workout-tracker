package clean.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the Google Health names (2026-11-23): every name read from Rohan's screen recording of that app's exercise
 * picker (`tools/googlehealth/names.txt`) is a library exercise or leads to one. See also [GoogleHealthMappingTest].
 */
class GoogleHealthNamesTest {
    private val names = File("../tools/googlehealth/names.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() }

    @Test fun theListHasNoDuplicatesAndIsAlphabetical() {
        assertTrue(names.size >= 1000, "only ${names.size} names")
        assertEquals(emptyList(), names.groupBy { it }.filter { it.value.size > 1 }.keys.toList())
        assertEquals(names.sortedWith(compareBy({ it.lowercase() }, { it })), names)
    }

    @Test fun everyNameIsALibraryExerciseOrLeadsToOne() {
        val missing = names.filter { name -> libraryExercise(name) == null && canonicalName(name)?.let { libraryExercise(it) } == null }
        assertEquals(emptyList(), missing)
    }
}
