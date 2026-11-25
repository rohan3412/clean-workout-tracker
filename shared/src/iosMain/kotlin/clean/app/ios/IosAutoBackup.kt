@file:OptIn(ExperimentalForeignApi::class)

package clean.app.ios

import clean.app.AutoBackupKeys
import clean.app.data.CleanDb
import clean.app.data.DB_FILE
import clean.app.data.Kv
import clean.app.data.open
import clean.core.AUTO_BACKUP_KEEP
import clean.core.AutoBackupFrequency
import clean.core.STRONG_DATE_FORMAT
import clean.core.autoBackupName
import clean.core.autoBackupsToDelete
import clean.core.currentLocalDateTime
import clean.core.time.Duration
import clean.core.time.format
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDateTime

/**
 * The scheduled backup on the iPhone (Settings, Data, "Automatic backup"): the same zip as the manual backup, without the animations, written
 * into the folder the user picked (kept as a bookmark in kv `autobackup.folder`), the newest [AUTO_BACKUP_KEEP] kept and only files with the
 * scheduled backup's own name ever deleted. iOS runs a background refresh task whenever it likes, so it is also run when the app goes
 * to the background if one is due. The last success and the last error are kept in the database, as on Android.
 */
object IosAutoBackup {
    /** One backup into the folder [tree] names. Blocking. Throws with a sentence fit to show when the folder cannot be written. */
    fun backupInto(tree: String, app: String, now: LocalDateTime, keep: Int = AUTO_BACKUP_KEEP): String {
        val name = autoBackupName(now)
        return IosHandles.access(tree) { dir ->
            val path = dir.path ?: throw IllegalStateException("The backup folder is not there any more")
            if (!Fs.isDirectory(path)) throw IllegalStateException("The backup folder cannot be written to")
            val existing = Fs.list(path)
            // Two backups in the same minute would share a name: the second is not written.
            if (name in existing) return@access name
            if (!Fs.write(Fs.join(path, name), IosBackup.build(app, false, now))) throw IllegalStateException("The backup file could not be made in that folder")
            for (old in autoBackupsToDelete(existing.toSet() + name, keep)) Fs.delete(Fs.join(path, old))
            name
        } ?: throw IllegalStateException("The backup folder is not there any more")
    }

    /** Runs a backup into the chosen folder and records how it went. */
    suspend fun run(app: String): Boolean = attempt(app) == null

    /** [run], saying why it failed: null when the backup was made, else the sentence kept as the error (the "Back up now" button shows it). */
    suspend fun attempt(app: String): String? {
        val dao = CleanDb.open(Fs.join(Fs.root, DB_FILE)).dao()
        val tree = dao.get(AutoBackupKeys.FOLDER)?.takeIf { it.isNotEmpty() } ?: return "No backup folder is chosen"
        return try {
            val now = currentLocalDateTime()
            backupInto(tree, app, now)
            dao.put(Kv(AutoBackupKeys.LAST, now.format(STRONG_DATE_FORMAT)))
            dao.remove(AutoBackupKeys.ERROR)
            null
        } catch (e: Exception) {
            val why = e.message ?: "The backup could not be made"
            dao.put(Kv(AutoBackupKeys.ERROR, why))
            why
        }
    }

    /** The frequency chosen (null: off), and whether a backup is due by it. */
    suspend fun due(): Pair<AutoBackupFrequency?, Boolean> {
        val dao = CleanDb.open(Fs.join(Fs.root, DB_FILE)).dao()
        val freq = AutoBackupFrequency.of(dao.get(AutoBackupKeys.FREQ))
        if (freq == null || dao.get(AutoBackupKeys.FOLDER).isNullOrEmpty()) return freq to false
        val last = dao.get(AutoBackupKeys.LAST)?.let { STRONG_DATE_FORMAT.parseOrNull(it) } ?: return freq to true
        // An hour of slack, so a backup made at 02:00 yesterday is due at 01:30 today.
        return freq to (Duration.between(last, currentLocalDateTime()).toMillis() >= freq.days * 86_400_000L - 3_600_000L)
    }
}
