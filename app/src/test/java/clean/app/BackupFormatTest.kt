package clean.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import clean.app.android.Backup
import clean.app.data.CleanDb
import clean.app.data.DB_VERSION
import clean.app.data.Repo
import clean.app.data.open
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup moves a person's data between an Android phone and an iPhone, so both must write and read the same zip. Android's `Backup`
 * (java.util.zip) and the common `BackupFormat` (core's zip, which on iOS is zlib) are checked against each other here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupFormatTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun seed(): Long {
        CleanDb.close() // one database per test: the shared instance is bound to the first test's folder
        return runBlocking { Repo(CleanDb.open(context)).createProfile("Rohan") }
    }

    @Test fun commonReaderReadsAndroidsBackup() {
        val id = seed()
        val photo = File(context.filesDir, "photos/$id/20261012-093000_front.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val out = ByteArrayOutputStream()
        Backup.write(context, out, withAnimations = true)
        val opened = BackupFormat.open(out.toByteArray())
        assertEquals(DB_VERSION, opened.manifest.db)
        assertTrue(opened.db.decodeToString(0, 15).startsWith("SQLite format 3"))
        assertEquals(listOf("photos/$id/${photo.name}"), opened.files.map { it.first })
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), opened.files[0].second)
        assertTrue(BackupFormat.isBackup(out.toByteArray()))
    }

    @Test fun snapshotBackupHoldsTheLatestWritesAndLeavesNothingBehind() {
        seed()
        runBlocking { Repo(CleanDb.open(context)).createProfile("Second") }
        val out = ByteArrayOutputStream()
        Backup.write(context, out, withAnimations = false)
        // The VACUUM INTO file is a temporary: gone once the zip is written.
        assertTrue(!File(context.cacheDir, "backup-snapshot.db").exists())
        val file = File(context.cacheDir, "snap.zip").apply { writeBytes(out.toByteArray()) }
        assertTrue(Backup.isBackup(file))
        val inspected = Backup.inspect(context, file)
        assertEquals(listOf("Rohan", "Second"), inspected.contents.profiles)
        assertEquals(DB_VERSION, inspected.manifest.db)
        // The common reader opens it as well, and the live database is untouched and still usable.
        assertTrue(BackupFormat.open(out.toByteArray()).db.decodeToString(0, 15).startsWith("SQLite format 3"))
        assertEquals(2, Backup.current(context).profiles.size)
    }

    @Test fun androidReadsTheCommonWriter() {
        val id = seed()
        CleanDb.open(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val db = context.getDatabasePath("clean.db").readBytes()
        val bytes = BackupFormat.write(db, listOf("photos/$id/a.jpg" to byteArrayOf(9, 8, 7), "avatars/$id.jpg" to ByteArray(0)), LocalDateTime(2026, 11, 20, 10, 0, 0, 0), "1.2.3", 1_700_000_000_000L)
        val file = File(context.cacheDir, "from-common.zip").apply { writeBytes(bytes) }
        ZipFile(file).use { zip ->
            assertEquals(listOf("clean-backup.json", "clean.db", "files/photos/$id/a.jpg", "files/avatars/$id.jpg"), zip.entries().asSequence().map { it.name }.toList())
            assertEquals("""{"format":"clean-backup","version":1,"db":$DB_VERSION,"created":"2026-11-20T10:00:00","app":"1.2.3"}""", zip.getInputStream(zip.getEntry("clean-backup.json")).readBytes().decodeToString())
        }
        assertTrue(Backup.isBackup(file))
        val inspected = Backup.inspect(context, file)
        assertEquals(listOf("Rohan"), inspected.contents.profiles)
        assertEquals("1.2.3", inspected.manifest.app)
    }

    @Test fun manifestsAreTheSame() {
        assertEquals(Backup.manifestJson(9, "2026-01-02T03:04:05", "1.0"), BackupFormat.manifestJson(9, "2026-01-02T03:04:05", "1.0"))
    }

    @Test fun templateFoldersKeepTheSortedMapMerge() {
        val groups = linkedMapOf("push" to listOf(1), "Pull" to listOf(2), "Push" to listOf(3), "" to listOf(4))
        val sorted = groups.sortedMergedBy(compareBy<String> { it.isEmpty() }.thenBy { it.lowercase() })
        // What `toSortedMap(comparator)` gave: one entry for "push" and "Push" (the first key, the later value), the no-folder group last.
        assertEquals(listOf("Pull", "push", ""), sorted.keys.toList())
        assertEquals(listOf(3), sorted["push"])
        assertEquals(java.util.TreeMap<String, List<Int>>(compareBy<String> { it.isEmpty() }.thenBy { it.lowercase() }).apply { putAll(groups) }.toList(), sorted.toList())
    }
}
