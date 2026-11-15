package clean.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

/**
 * The round-trip rule checked against the real export in `original/`. That folder is not in
 * git, so these tests are skipped where it is absent.
 */
class RealExportRoundTripTest {
    private val original = File("../original")

    private fun find(pattern: Regex): File {
        val file = original.listFiles()?.firstOrNull { pattern.matches(it.name) }
        assumeTrue("nothing matching $pattern in original/", file != null)
        return file!!
    }

    @Test
    fun `workout csv round-trips byte-identical`() {
        val file = find(Regex("""strong\d+\.csv"""))
        val bytes = file.readBytes()
        val rows = WorkoutCsv.parse(bytes)
        val out = WorkoutCsv.serialise(rows)
        println("${file.name}: ${rows.size} rows, ${bytes.size} bytes in, ${out.size} bytes out, identical=${bytes.contentEquals(out)}")
        assertContentEquals(bytes, out)
    }

    @Test
    fun `measurements zip round-trips byte-identical`() {
        val file = find(Regex("""strong_measurements_.*\.zip"""))
        val bytes = file.readBytes()

        // Each CSV inside, compared on its own, so a failure names the file.
        val names = mutableListOf<String>()
        var modified = 0L
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val csv = zip.readBytes()
                assertContentEquals(csv, Measurements.serialiseFile(Measurements.parseFile(entry.name, csv)), entry.name)
                names += entry.name
                modified = entry.time
            }
        }

        // Then the whole archive, stamped with the original's time.
        val files = Measurements.readZip(bytes.inputStream())
        assertEquals(names, files.map { it.name })
        val out = ByteArrayOutputStream().also { Measurements.writeZip(files, it, modified) }.toByteArray()
        println(
            "${file.name}: ${files.size} files, ${files.sumOf { it.entries.size }} entries, " +
                "${bytes.size} bytes in, ${out.size} bytes out, identical=${bytes.contentEquals(out)}",
        )
        assertContentEquals(bytes, out)
    }
}
