package clean.app

import clean.app.data.DB_VERSION
import clean.app.data.JSONObject
import clean.core.ZipItem
import clean.core.readZipItems
import clean.core.time.DatePattern
import clean.core.time.format
import clean.core.writeZipItems
import kotlinx.datetime.LocalDateTime

/**
 * The backup zip, as bytes, in common code: the layout Android's `Backup.kt` writes (`clean-backup.json`, `clean.db`, then `files/...`), so a
 * backup made on one phone restores on the other. The iPhone's backup code is built on this; `BackupFormatTest` (in `app`) checks it against what
 * Android writes and reads. Layout and rules: docs/data-format.md, "The backup zip".
 */
object BackupFormat {
    const val MANIFEST = "clean-backup.json"
    const val DB = "clean.db"
    const val FILES = "files/"
    private const val FORMAT = "clean-backup"
    private const val VERSION = 1

    /** The folders of the app's storage a backup carries. */
    val FOLDERS = listOf("animations", "photos", "moments", "places", "avatars")

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

    private val CREATED = DatePattern("yyyy-MM-ddTHH:mm:ss")

    /** The zip of [db] (the database file, complete) and [files] (name below `files/` and bytes). */
    fun write(db: ByteArray, files: List<Pair<String, ByteArray>>, now: LocalDateTime, app: String, modifiedMillis: Long): ByteArray {
        val items = ArrayList<ZipItem>()
        items += ZipItem(MANIFEST, manifestJson(DB_VERSION, now.format(CREATED), app).encodeToByteArray())
        items += ZipItem(DB, db)
        for ((name, bytes) in files) items += ZipItem(FILES + name, bytes)
        return writeZipItems(items, modifiedMillis)
    }

    /** What is in a backup zip: the manifest, the database file and the carried files (name below `files/`; only the folders Clean keeps, nothing that climbs out). */
    class Opened(val manifest: BackupManifest, val db: ByteArray, val files: List<Pair<String, ByteArray>>, val hasAnimations: Boolean)

    /** Whether [bytes] is a zip holding a manifest. */
    fun isBackup(bytes: ByteArray): Boolean = runCatching { readZipItems(bytes) { it == MANIFEST }.isNotEmpty() }.getOrDefault(false)

    /** The name below `files/` of a zip entry Clean takes back, or null: only the folders Clean keeps, only from inside `files/`; any other entry, or a path that climbs out, is ignored. */
    fun carriedName(entry: String): String? {
        if (!entry.startsWith(FILES) || entry.endsWith("/")) return null
        val name = entry.removePrefix(FILES)
        val parts = name.split('/')
        return if (parts.size > 1 && parts[0] in FOLDERS && parts.none { it.isEmpty() || it == ".." || it == "." }) name else null
    }

    /** Opens the zip, or throws [Refused] saying why it cannot be restored. [withFiles] false leaves the carried files unread (for looking inside). */
    fun open(bytes: ByteArray, withFiles: Boolean = true): Opened {
        var animations = false
        val all = try {
            readZipItems(bytes) { name ->
                val carried = carriedName(name)
                if (carried != null && carried.startsWith("animations/")) animations = true
                name == MANIFEST || name == DB || (withFiles && carried != null)
            }
        } catch (e: Exception) {
            throw Refused("That file is not a zip")
        }
        val manifest = readManifest(all.firstOrNull { it.name == MANIFEST }?.bytes?.decodeToString() ?: throw Refused("This zip is not a Clean backup"))
        val db = all.firstOrNull { it.name == DB }?.bytes ?: throw Refused("This backup has no database in it")
        val carried = all.mapNotNull { item -> carriedName(item.name)?.let { it to item.bytes } }
        return Opened(manifest, db, carried, animations)
    }

    /** The SQL the counts shown to the user are read with, in the order [contents] reads them. */
    val CONTENT_QUERIES = listOf(
        "SELECT name FROM profile ORDER BY id",
        "SELECT COUNT(*) FROM (SELECT DISTINCT profileId, date FROM workout_row)",
        "SELECT MIN(date), MAX(date) FROM workout_row",
        "SELECT COUNT(*) FROM measurement WHERE file = 'weight.csv'",
        "SELECT COUNT(*) FROM template",
    )

    /** [BackupContents] from a query function giving each row of a statement as its columns as text (null for SQL null); a table an older file lacks counts as empty. */
    fun contents(query: (String) -> List<List<String?>>): BackupContents {
        fun rows(sql: String): List<List<String?>> = try { query(sql) } catch (e: Exception) { emptyList() }
        fun count(sql: String) = rows(sql).firstOrNull()?.firstOrNull()?.toIntOrNull() ?: 0
        val span = rows(CONTENT_QUERIES[2]).firstOrNull()
        return BackupContents(
            rows(CONTENT_QUERIES[0]).mapNotNull { it.firstOrNull() },
            count(CONTENT_QUERIES[1]),
            span?.getOrNull(0)?.take(10), span?.getOrNull(1)?.take(10),
            count(CONTENT_QUERIES[3]),
            count(CONTENT_QUERIES[4]),
        )
    }
}
