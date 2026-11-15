package clean.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import clean.app.data.Repo
import clean.core.NewBlock
import clean.core.NewSet
import clean.core.SetType
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The round trip through the real database, on the device, with the real export. The files are
 * personal data, so they are copied into the app's private folder by hand (see README.md, "On-device tests") and the
 * tests are skipped when they are not there.
 */
@RunWith(AndroidJUnit4::class)
class RealExportOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = Repo(Room.inMemoryDatabaseBuilder(context, CleanDb::class.java).build())

    private fun original(extension: String): ByteArray {
        val file = File(context.filesDir, "original").listFiles()?.firstOrNull { it.name.endsWith(extension) }
        assumeTrue("no $extension in files/original", file != null)
        return file!!.readBytes()
    }

    @Test
    fun workoutsRoundTripThroughTheDatabase() = runBlocking {
        val bytes = original(".csv")
        val a = repo.createProfile("A")
        val b = repo.createProfile("B")

        val first = repo.previewWorkouts(a, bytes)
        repo.commit(a, first)
        assertArrayEquals(bytes, repo.exportWorkouts(a))
        println("workouts: ${first.workouts} imported, export identical (${bytes.size} bytes)")

        // The other profile sees nothing, and a second import adds nothing.
        assertEquals(0, repo.dao.rowsNow(b).size)
        val again = repo.previewWorkouts(a, bytes)
        assertEquals(0, again.rows.size)
        assertEquals(first.workouts, again.alreadyThere)

        // A workout logged in Clean only appends rows; deleting it restores the file exactly.
        val date = "2099-01-01 10:00:00"
        repo.saveWorkout(a, date, "Test", 600, listOf(NewBlock("Test Lift", listOf(NewSet(SetType.WARMUP, 20.0, 5), NewSet(SetType.NORMAL, 40.0, 8)))))
        val longer = repo.exportWorkouts(a)
        assertTrue(longer.size > bytes.size)
        assertArrayEquals(bytes, longer.copyOf(bytes.size))
        val added = String(longer.copyOfRange(bytes.size, longer.size))
        assertEquals(2, added.lines().count { it.startsWith("\"${first.workouts + 1}\";\"$date\";\"Test\";\"600\";\"Test Lift\";") })
        repo.deleteWorkout(a, date)
        assertArrayEquals(bytes, repo.exportWorkouts(a))
    }

    @Test
    fun measurementsRoundTripThroughTheDatabase() = runBlocking {
        val bytes = original(".zip")
        val stamped = ZipInputStream(bytes.inputStream()).use { it.nextEntry!!.time }
        val a = repo.createProfile("A")

        val first = repo.previewMeasurements(a, bytes)
        repo.commit(a, first)
        assertArrayEquals(bytes, repo.exportMeasurements(a, stamped))
        println("measurements: ${first.newEntries.size} imported, export identical (${bytes.size} bytes)")

        val again = repo.previewMeasurements(a, bytes)
        assertEquals(0, again.newEntries.size)
        assertEquals(first.newEntries.size, again.alreadyThere)
    }
}
