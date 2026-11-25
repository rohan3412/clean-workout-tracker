@file:OptIn(ExperimentalForeignApi::class)

package clean.app.ios

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import clean.app.BackupContents
import clean.app.BackupFormat
import clean.app.BackupFormat.Refused
import clean.app.BackupInspected
import clean.app.currentMillis
import clean.app.data.CleanDb
import clean.app.data.DB_FILE
import clean.app.data.DB_VERSION
import clean.app.BackupManifest
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Backing up, restoring and wiping on the iPhone: the same zip as Android's (`BackupFormat`), the database read through the bundled SQLite
 * on a copy. Restoring reads the zip entry by entry from its file ([FileZip]), so a backup with animations is never held whole.
 */
object IosBackup {
    private val dbPath get() = Fs.join(Fs.root, DB_FILE)

    private fun rows(conn: SQLiteConnection, sql: String): List<List<String?>> {
        val statement = conn.prepare(sql)
        try {
            val out = ArrayList<List<String?>>()
            val columns = statement.getColumnCount()
            while (statement.step()) out += (0 until columns).map { if (statement.isNull(it)) null else statement.getText(it) }
            return out
        } finally {
            statement.close()
        }
    }

    private fun deleteDb(path: String) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) Fs.delete(path + suffix)
    }

    /** What the database file at [path] holds; throws [Refused] when it is newer than this app or cannot be read. Opened read-write only because it is a thrown-away copy. */
    private fun contentsOf(path: String): BackupContents {
        val conn = try { BundledSQLiteDriver().open(path) } catch (e: Exception) { throw Refused("The database in this backup could not be read") }
        try {
            val version = try { rows(conn, "PRAGMA user_version").firstOrNull()?.firstOrNull()?.toIntOrNull() ?: 0 } catch (e: Exception) { throw Refused("The database in this backup could not be read") }
            if (version > DB_VERSION) throw Refused("This backup was made by a newer version of Clean")
            return BackupFormat.contents { sql -> rows(conn, sql) }
        } finally {
            conn.close()
        }
    }

    /** What the phone holds now. */
    fun current(): BackupContents {
        if (!Fs.exists(dbPath)) return BackupContents(emptyList(), 0, null, null, 0, 0)
        val conn = BundledSQLiteDriver().open(dbPath)
        try {
            return BackupFormat.contents { sql -> rows(conn, sql) }
        } finally {
            conn.close()
        }
    }

    fun animationsBytes(): Long = Fs.folderBytes(Fs.join(Fs.root, "animations"))

    /** The backup zip as bytes. Blocking. The log of the database is moved into its file first, so the one file is complete. */
    fun build(app: String, withAnimations: Boolean, now: kotlinx.datetime.LocalDateTime): ByteArray {
        val conn = BundledSQLiteDriver().open(dbPath)
        try { rows(conn, "PRAGMA wal_checkpoint(TRUNCATE)") } finally { conn.close() }
        val db = Fs.read(dbPath) ?: throw IllegalStateException("The database could not be read")
        val files = ArrayList<Pair<String, ByteArray>>()
        for (folder in BackupFormat.FOLDERS) {
            if (folder == "animations" && !withAnimations) continue
            for (relative in Fs.files(Fs.join(Fs.root, folder))) {
                val name = "$folder/$relative"
                files += name to (Fs.read(Fs.join(Fs.root, folder, relative)) ?: continue)
            }
        }
        return BackupFormat.write(db, files, now, app, currentMillis())
    }

    fun isBackup(path: String): Boolean = runCatching {
        val zip = FileZip(path)
        try { zip.entries.any { it.name == BackupFormat.MANIFEST } } finally { zip.close() }
    }.getOrDefault(false)

    private fun <R> openZip(path: String, use: (FileZip) -> R): R {
        val zip = try { FileZip(path) } catch (e: Exception) { throw Refused("That file is not a zip") }
        try { return use(zip) } finally { zip.close() }
    }

    private fun manifestOf(zip: FileZip) = BackupFormat.readManifest(
        (zip.entries.firstOrNull { it.name == BackupFormat.MANIFEST } ?: throw Refused("This zip is not a Clean backup")).let { zip.bytes(it).decodeToString() },
    )

    private fun dbEntry(zip: FileZip) = zip.entries.firstOrNull { it.name == BackupFormat.DB } ?: throw Refused("This backup has no database in it")

    /** What the zip at [path] holds, or a [Refused] saying why it cannot be restored. Changes nothing. */
    fun inspect(path: String): BackupInspected = openZip(path) { zip ->
        val manifest = manifestOf(zip)
        val temp = Fs.join(Fs.tmp, "backup-check.db")
        try {
            Fs.write(temp, zip.bytes(dbEntry(zip)))
            val contents = contentsOf(temp)
            BackupInspected(manifest, contents, zip.entries.any { BackupFormat.carriedName(it.name)?.startsWith("animations/") == true })
        } finally {
            deleteDb(temp)
        }
    }

    /**
     * Replaces the phone's database and files with the backup's. The manifest and the database are read and checked first, so a zip that is
     * not a backup leaves the phone as it was. The database is closed here; the app is restarted after.
     */
    fun apply(path: String) = openZip(path) { zip ->
        manifestOf(zip)
        val db = zip.bytes(dbEntry(zip))
        val check = Fs.join(Fs.tmp, "restore-check.db")
        try {
            Fs.write(check, db)
            contentsOf(check)
        } finally {
            deleteDb(check)
        }
        val carried = zip.entries.filter { !it.isDirectory }.mapNotNull { e -> BackupFormat.carriedName(e.name)?.let { it to e } }
        val hasAnimations = carried.any { it.first.startsWith("animations/") }
        CleanDb.close()
        deleteDb(dbPath)
        if (!Fs.write(dbPath, db)) throw Refused("The database could not be written")
        // The photos and places belong to the database being put back, so the phone's own go either way; a backup without animations leaves the phone's alone.
        for (folder in BackupFormat.FOLDERS) if (folder != "animations" || hasAnimations) Fs.delete(Fs.join(Fs.root, folder))
        for ((name, entry) in carried) Fs.write(Fs.join(Fs.root, name), zip.bytes(entry))
    }

    /** Takes everything the app keeps off the phone: the database, animations, photos, moments, places and the cache. Then restart. */
    fun wipe() {
        CleanDb.close()
        deleteDb(dbPath)
        for (folder in BackupFormat.FOLDERS) Fs.delete(Fs.join(Fs.root, folder))
        Fs.delete(IosSavedState.file)
        for (name in Fs.list(Fs.tmp)) Fs.delete(Fs.join(Fs.tmp, name))
        for (name in Fs.list(Fs.cache)) Fs.delete(Fs.join(Fs.cache, name))
    }
}
