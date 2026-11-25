package clean.app.android

import clean.app.*

import clean.core.time.*
import java.time.format.DateTimeFormatter
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.DB_VERSION
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.datetime.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess
import org.json.JSONObject

/**
 * Backing up, restoring and wiping the whole app (Settings, Data). A backup is one zip: a small manifest, the
 * database file itself (so every table, option, template and program is in it, and whatever is added later
 * with them) and the app's own files the database refers to. Restoring puts the database file back and lets
 * Room's migrations bring an older one up to date. No screen is needed for any of this; the restart is apart.
 * Layout and rules: docs/data-format.md, "The backup zip".
 */
object Backup {
    const val MANIFEST = "clean-backup.json"
    private const val DB = "clean.db"
    private const val FILES = "files/"
    private const val FORMAT = "clean-backup"
    private const val VERSION = 1

    /** The folders of `filesDir` a backup carries (an imported font, a `font-…` file, was carried until 1.1.0 and is ignored now). */
    private val FOLDERS = listOf("animations", "photos", "moments", "places", "avatars")

    /** Raised with a sentence fit to show the user. */
    class Refused(message: String) : Exception(message)

    fun manifestJson(db: Int, created: String, app: String): String =
        JSONObject().put("format", FORMAT).put("version", VERSION).put("db", db).put("created", created).put("app", app).toString()

    /** Reads the manifest, refusing one that is not Clean's or comes from a newer Clean. */
    fun readManifest(json: String): BackupManifest {
        val o = try { JSONObject(json) } catch (e: Exception) { throw Refused("This zip is not a Clean backup") }
        val version = o.optInt("version", 0)
        val db = o.optInt("db", 0)
        if (o.optString("format") != FORMAT || version < 1 || db < 1) throw Refused("This zip is not a Clean backup")
        if (version > VERSION || db > DB_VERSION) throw Refused("This backup was made by a newer version of Clean")
        return BackupManifest(db, o.optString("created"), o.optString("app"))
    }

    // --- Backing up ---

    /** The files a backup carries, by the name they have inside the zip. */
    private fun carried(context: Context, withAnimations: Boolean): List<Pair<String, File>> {
        val root = context.filesDir
        val inFolders = FOLDERS.filter { withAnimations || it != "animations" }.flatMap { File(root, it).walkTopDown().filter { f -> f.isFile }.toList() }
        return inFolders.map { FILES + it.relativeTo(root).invariantSeparatorsPath to it }
    }

    /** The size of the imported animations, which are most of a big backup. */
    fun animationsBytes(context: Context): Long = File(context.filesDir, "animations").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * A consistent copy of the live database in the cache, taken with `VACUUM INTO` through the open database (Android 11 and later, SQLite
     * 3.27): a write that lands between a checkpoint and a file copy can no longer tear it. Null below Android 11 or when it fails, and
     * [write] then copies the file as it always did.
     */
    private fun snapshot(context: Context): File? {
        if (Build.VERSION.SDK_INT < 30) return null
        val file = File(context.cacheDir, "backup-snapshot.db")
        deleteDb(file)
        return try {
            CleanDb.open(context).openHelper.writableDatabase.execSQL("VACUUM INTO '${file.path.replace("'", "''")}'")
            file
        } catch (e: Exception) {
            deleteDb(file)
            null
        }
    }

    /** Writes the backup zip to [out]. Blocking: call it off the main thread. */
    fun write(context: Context, out: OutputStream, withAnimations: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val snap = snapshot(context)
        try {
            val dbFile = snap ?: run {
                // Everything still in the write-ahead log is moved into the database file first, so the one file is complete.
                CleanDb.open(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                context.getDatabasePath(DB)
            }
            val app = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestJson(DB_VERSION, now.withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), app).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DB))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                for ((name, file) in carried(context, withAnimations)) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally {
            if (snap != null) deleteDb(snap)
        }
    }

    // --- Looking inside a backup ---

    /** Copies a picked file into the cache, where a zip can be opened by name. */
    fun copyToCache(context: Context, from: InputStream): File {
        val file = File(context.cacheDir, "picked.zip")
        from.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    fun isBackup(file: File): Boolean = runCatching { ZipFile(file).use { it.getEntry(MANIFEST) != null } }.getOrDefault(false)

    private fun openZip(file: File): ZipFile = try { ZipFile(file) } catch (e: Exception) { throw Refused("That file is not a zip") }

    private fun manifestOf(zip: ZipFile): BackupManifest {
        val entry = zip.getEntry(MANIFEST) ?: throw Refused("This zip is not a Clean backup")
        return readManifest(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
    }

    private fun extract(zip: ZipFile, entry: ZipEntry, to: File) {
        to.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input -> to.outputStream().use { input.copyTo(it) } }
    }

    /** The counts shown to the user, read with [query] (a backup's file, or the live database). A table an older file lacks counts as empty. */
    fun contents(query: (String) -> Cursor): BackupContents {
        fun <T> rows(sql: String, read: (Cursor) -> T): List<T> =
            try { query(sql).use { c -> generateSequence { if (c.moveToNext()) read(c) else null }.toList() } } catch (e: Exception) { emptyList() }
        fun count(sql: String) = rows(sql) { it.getInt(0) }.firstOrNull() ?: 0
        val span = rows("SELECT MIN(date), MAX(date) FROM workout_row") { it.getString(0) to it.getString(1) }.firstOrNull()
        return BackupContents(
            rows("SELECT name FROM profile ORDER BY id") { it.getString(0) },
            count("SELECT COUNT(*) FROM (SELECT DISTINCT profileId, date FROM workout_row)"),
            span?.first?.take(10), span?.second?.take(10),
            count("SELECT COUNT(*) FROM measurement WHERE file = 'weight.csv'"),
            count("SELECT COUNT(*) FROM template"),
        )
    }

    /** What the phone holds now, for the line that says what a restore or a wipe replaces. Blocking. */
    fun current(context: Context): BackupContents = contents { CleanDb.open(context).openHelper.readableDatabase.query(it) }

    /** Opens a copy of a backup's database and counts what is in it; the copy is opened read-write only because it is thrown away. */
    private fun read(db: File): BackupContents = try {
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            if (it.version > DB_VERSION) throw Refused("This backup was made by a newer version of Clean")
            contents { sql -> it.rawQuery(sql, null) }
        }
    } catch (e: Refused) {
        throw e
    } catch (e: Exception) {
        throw Refused("The database in this backup could not be read")
    }

    private fun deleteDb(db: File) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(db.path + suffix).delete()
    }

    /** What the zip at [file] holds, or a [Refused] saying why it cannot be restored. Changes nothing. Blocking. */
    fun inspect(context: Context, file: File): BackupInspected = openZip(file).use { zip ->
        val manifest = manifestOf(zip)
        val entry = zip.getEntry(DB) ?: throw Refused("This backup has no database in it")
        val temp = File(context.cacheDir, "backup-check.db")
        try {
            extract(zip, entry, temp)
            val contents = read(temp)
            val names = zip.entries().asSequence().map { it.name }.toList()
            BackupInspected(manifest, contents, names.any { it.startsWith(FILES + "animations/") && !it.endsWith("/") })
        } finally {
            deleteDb(temp)
        }
    }

    // --- Restoring and wiping ---

    /**
     * Replaces the phone's database and files with the backup's. It is all extracted into the cache and checked first, so a
     * zip that fails halfway leaves the phone as it was. The database is closed here; the app has to be [restart]ed afterwards.
     * Only the database and the files Clean keeps (animations, photos, moments, places, avatars) are taken, and only from inside
     * `files/`: any other entry, or a path that climbs out, is ignored. A backup without animations leaves the phone's alone.
     */
    fun apply(context: Context, file: File) {
        val stage = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val stagedDb = File(stage, DB)
            val stagedFiles = File(stage, "files").apply { mkdirs() }.canonicalFile
            openZip(file).use { zip ->
                manifestOf(zip)
                extract(zip, zip.getEntry(DB) ?: throw Refused("This backup has no database in it"), stagedDb)
                // Looked at in a copy, so the file that goes in is exactly the one that was in the zip.
                File(stage, "check.db").let { copy -> stagedDb.copyTo(copy); try { read(copy) } finally { deleteDb(copy) } }
                for (entry in zip.entries()) {
                    if (entry.isDirectory || !entry.name.startsWith(FILES)) continue
                    val target = File(stagedFiles, entry.name.removePrefix(FILES)).canonicalFile
                    if (!target.path.startsWith(stagedFiles.path + File.separator)) continue
                    val parts = target.relativeTo(stagedFiles).invariantSeparatorsPath.split('/')
                    // A `font-*` file in an older backup is ignored: the font is Inter everywhere since 1.1.0.
                    if (parts.size > 1 && parts[0] in FOLDERS) extract(zip, entry, target)
                }
            }
            CleanDb.close()
            val live = context.getDatabasePath(DB)
            deleteDb(live)
            move(stagedDb, live)
            // The photos and places belong to the database being put back, so the phone's own go either way (and a font file left by an older version goes).
            val root = context.filesDir
            root.listFiles { f -> f.name.startsWith("font-") }?.forEach { it.delete() }
            for (name in FOLDERS) if (name != "animations" || File(stagedFiles, name).exists()) File(root, name).deleteRecursively()
            for (f in stagedFiles.listFiles().orEmpty()) move(f, File(root, f.name))
        } finally {
            stage.deleteRecursively()
        }
    }

    /** Takes everything the app keeps off the phone: the database, animations, photos, moments, places and the cache. Then [restart]. */
    fun wipe(context: Context) {
        CleanDb.close()
        deleteDb(context.getDatabasePath(DB))
        val root = context.filesDir
        root.listFiles { f -> f.name.startsWith("font-") }?.forEach { it.delete() }
        for (name in FOLDERS) File(root, name).deleteRecursively()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun move(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyRecursively(to, overwrite = true)
            from.deleteRecursively()
        }
    }

    /** Starts the app again in a new process, so everything is read afresh (and Room migrates an older file). Does not return. */
    fun restart(context: Context): Nothing {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        exitProcess(0)
    }
}
