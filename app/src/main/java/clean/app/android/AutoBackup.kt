package clean.app.android

import clean.app.*
import clean.core.time.*
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.Kv
import clean.core.AUTO_BACKUP_KEEP
import clean.core.AutoBackupFrequency
import clean.core.STRONG_DATE_FORMAT
import clean.core.autoBackupName
import clean.core.autoBackupsToDelete
import kotlinx.datetime.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The scheduled backup (N15, round 7; Settings, Data, "Automatic backup"). The user picks a folder once (Storage Access Framework,
 * `OpenDocumentTree`, with a persistable permission) and a frequency; WorkManager then runs [AutoBackupWorker] on the phone, daily or
 * weekly, which writes the same zip as the manual backup (`Backup.write`, without the imported animations, which are large and can be
 * brought back from their own zip) into the folder and keeps the newest [AUTO_BACKUP_KEEP]. Only files with the scheduled backup's own name
 * are ever deleted. The last success and the last error are kept in the database (kv) and shown quietly. Nothing here goes online.
 *
 * kv keys: `autobackup.folder` (the tree's Uri), `autobackup.freq` (`daily` or `weekly`; absent is off), `autobackup.last` (the start of the
 * last good backup, as a stored date), `autobackup.error` (what went wrong last time; absent when the last run was good).
 */
object AutoBackup {
    const val FOLDER = AutoBackupKeys.FOLDER
    const val FREQ = AutoBackupKeys.FREQ
    const val LAST = AutoBackupKeys.LAST
    const val ERROR = AutoBackupKeys.ERROR
    private const val WORK = "auto-backup"

    /** Starts, changes or stops the schedule to match the frequency ([frequency] null is off). */
    fun schedule(context: Context, frequency: AutoBackupFrequency?) {
        val work = WorkManager.getInstance(context)
        if (frequency == null) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build()
        // Replaced only when the period changed, so opening Settings never pushes the next backup back.
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * One backup into [dir]: the zip, then the older ones beyond [keep] taken away. Returns the name written. Blocking; call it off the main
     * thread. Throws with a sentence fit to show when the folder cannot be written.
     */
    fun backupInto(context: Context, dir: DocumentFile, now: LocalDateTime = LocalDateTime.now(), keep: Int = AUTO_BACKUP_KEEP): String {
        if (!dir.exists() || !dir.isDirectory || !dir.canWrite()) throw IllegalStateException("The backup folder cannot be written to")
        // Two backups in the same minute would share a name: the second is not written.
        val name = autoBackupName(now)
        val existing = dir.listFiles().mapNotNull { f -> f.name?.let { it to f } }.toMap()
        if (name in existing) return name
        // Named without the extension, which most providers add for the type; where one does not, the file is renamed to the full name.
        val file = dir.createFile("application/zip", name.removeSuffix(".zip")) ?: throw IllegalStateException("The backup file could not be made in that folder")
        if (file.name != name) file.renameTo(name)
        try {
            context.contentResolver.openOutputStream(file.uri, "wt")!!.use { Backup.write(context, it, withAnimations = false, now = now) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        for (old in autoBackupsToDelete(existing.keys + name, keep)) existing[old]?.delete()
        return name
    }

    /** How the chosen folder (the tree's Uri as stored) is opened. A seam for tests, which give a plain folder where the phone gives a document tree. */
    internal var openFolder: (Context, String) -> DocumentFile? = { context, tree -> DocumentFile.fromTreeUri(context, Uri.parse(tree)) }

    /** Runs a backup into the chosen folder and records how it went. Blocking. */
    suspend fun run(context: Context): Boolean = attempt(context) == null

    /**
     * [run], saying why it failed: null when the backup was made, else the sentence kept as the error (Round 36: the "Back up now" button shows it). With no folder
     * chosen nothing is recorded and the sentence says so.
     */
    suspend fun attempt(context: Context): String? {
        val dao = CleanDb.open(context).dao()
        val tree = dao.get(FOLDER)?.takeIf { it.isNotEmpty() } ?: return "No backup folder is chosen"
        return try {
            val dir = openFolder(context, tree) ?: throw IllegalStateException("The backup folder is not there any more")
            val now = LocalDateTime.now()
            withContext(Dispatchers.IO) { backupInto(context, dir, now) }
            dao.put(Kv(LAST, now.withNano(0).format(STRONG_DATE_FORMAT)))
            dao.remove(ERROR)
            null
        } catch (e: Exception) {
            val why = e.message ?: "The backup could not be made"
            dao.put(Kv(ERROR, why))
            why
        }
    }
}
