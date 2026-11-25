package clean.app

import clean.app.android.*

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.DB_VERSION
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.TemplateEntity
import clean.core.WorkoutCsv
import clean.core.WorkoutRow
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backing up and restoring through the real database, on a small hand-written profile (no personal data, nothing from original/).
 * The phone's own state is backed up first and put back last, so what is on the phone is as it was.
 */
@RunWith(AndroidJUnit4::class)
class BackupOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var pristine: File

    private fun row(number: String, date: String, exercise: String, order: String, weight: String = "") =
        WorkoutRow(number, date, "Push ", "3600", exercise, order, weight, if (weight.isEmpty()) "" else "8", "", "", "", "", "")

    private val bytes = WorkoutCsv.serialise(listOf(
        row("1", "2026-01-01 10:00:00", "Bench Press (Barbell)", "1", "60.0"),
        row("1", "2026-01-01 10:00:00", "Bench Press (Barbell)", "2", "62.5"),
        row("2", "2026-01-05 10:00:00", "Squat (Barbell)", "1", "100.0"),
    ))

    private fun repo() = Repo(CleanDb.open(context))

    private fun backupFile(withAnimations: Boolean = true): File {
        val file = File(context.cacheDir, "test-backup-${System.nanoTime()}.zip")
        file.outputStream().use { Backup.write(context, it, withAnimations) }
        return file
    }

    /** Every row of every table, as text, in storage order. */
    private fun snapshot(): Map<String, List<String>> {
        val db = CleanDb.open(context).openHelper.readableDatabase
        return listOf("profile", "workout_row", "measurement_file", "measurement", "exercise_setting", "kv", "template").associateWith { table ->
            db.query("SELECT * FROM $table ORDER BY rowid").use { c ->
                generateSequence { if (c.moveToNext()) (0 until c.columnCount).joinToString("\u0001") { c.getString(it) ?: "\u0000" } else null }.toList()
            }
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = File(context.cacheDir, "test-zip-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, data) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
        return file
    }

    private fun dbBytes(backup: File): ByteArray = ZipFile(backup).use { z -> z.getInputStream(z.getEntry("clean.db")).readBytes() }

    @Before fun takePhoneState() { pristine = backupFile() }

    @After fun putPhoneStateBack() {
        Backup.apply(context, pristine)
        pristine.delete()
        // Reopened here so the next test (or the app) finds a live database.
        CleanDb.open(context)
    }

    @Test
    fun aBackupRestoredGivesBackEveryTableAndTheSameFile(): Unit = runBlocking {
        val id = repo().createProfile("Backup test")
        repo().commit(id, repo().previewWorkouts(id, bytes))
        repo().dao.put(Kv("prog:$id:Bench Press (Barbell)", "8-12@2.5"))
        repo().dao.put(TemplateEntity(profileId = id, name = "Push", blocks = """[{"exercise":"Bench Press (Barbell)","sets":[""]}]"""))
        val before = snapshot()
        val csv = repo().exportWorkouts(id)
        assertArrayEquals(bytes, csv)

        val backup = backupFile()
        // The manifest says what it should.
        val manifest = ZipFile(backup).use { z -> Backup.readManifest(z.getInputStream(z.getEntry(Backup.MANIFEST)).readBytes().decodeToString()) }
        assertEquals(DB_VERSION, manifest.db)
        val found = Backup.inspect(context, backup)
        assertTrue("Backup test" in found.contents.profiles)
        assertEquals(2, repo().dao.rowsNow(id).map { it.date }.distinct().size)

        // Things change after the backup.
        repo().deleteWorkout(id, "2026-01-01 10:00:00")
        repo().dao.put(Kv("prog:$id:Bench Press (Barbell)", "5-8@5"))
        repo().dao.put(TemplateEntity(profileId = id, name = "Later", blocks = "[]"))
        assertNotEquals(before, snapshot())

        Backup.apply(context, backup)
        assertEquals(before, snapshot())
        assertArrayEquals(csv, repo().exportWorkouts(id))
        backup.delete()
    }

    @Test
    fun aZipWithPathTricksExtractsNothingOutsideTheFilesFolder() {
        val good = dbBytes(pristine)
        val manifest = Backup.manifestJson(DB_VERSION, "2026-10-12T10:00:00", "test").toByteArray()
        // Android refuses to open a zip that has ".." in a name; where it does not, the entries are passed over.
        val climbing = zipOf(Backup.MANIFEST to manifest, "clean.db" to good, "files/../../evil" to byteArrayOf(1), "files/../evil" to byteArrayOf(1), "../escape.txt" to byteArrayOf(1))
        try { Backup.apply(context, climbing) } catch (e: Backup.Refused) { }
        climbing.delete()
        val tricks = zipOf(Backup.MANIFEST to manifest, "clean.db" to good, "evil.txt" to byteArrayOf(1), "files/unknown.txt" to byteArrayOf(1), "files/font-test" to byteArrayOf(2))
        Backup.apply(context, tricks)
        for (outside in listOf(File(context.filesDir, "../../evil"), File(context.filesDir, "../evil"), File(context.filesDir, "../escape.txt"), File(context.filesDir, "evil"), File(context.filesDir, "evil.txt"), File(context.filesDir, "unknown.txt"), File(context.cacheDir, "evil.txt")))
            assertFalse(outside.path, outside.exists())
        // The one entry that is Clean's own kind of file is taken.
        assertTrue(File(context.filesDir, "font-test").exists())
        tricks.delete()
    }

    @Test
    fun aBackupFromANewerCleanIsRefusedAndNothingChanges() {
        val before = snapshot()
        val newer = zipOf(Backup.MANIFEST to Backup.manifestJson(DB_VERSION + 1, "2026-10-12T10:00:00", "test").toByteArray(), "clean.db" to dbBytes(pristine))
        for (attempt in listOf({ Backup.inspect(context, newer) }, { Backup.apply(context, newer) })) {
            try {
                attempt()
                fail("a newer backup was accepted")
            } catch (e: Backup.Refused) {
                assertTrue(e.message!!, "newer version" in e.message!!)
            }
        }
        assertEquals(before, snapshot())
        // And the other ways a zip is not a backup.
        for (json in listOf("", "{}", """{"format":"other","version":1,"db":1}""", """{"format":"clean-backup","version":2,"db":1}""")) {
            try { Backup.readManifest(json); fail("accepted $json") } catch (e: Backup.Refused) { }
        }
        val noDb = zipOf(Backup.MANIFEST to Backup.manifestJson(DB_VERSION, "2026-10-12T10:00:00", "test").toByteArray())
        try { Backup.inspect(context, noDb); fail("a backup without a database was accepted") } catch (e: Backup.Refused) { }
        newer.delete()
        noDb.delete()
        assertEquals(before, snapshot())
    }
}
