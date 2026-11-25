// Kept in package clean.app: WorkManager stores the scheduled backup by this class name, and an installed phone keeps its schedule.
package clean.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import clean.app.android.AutoBackup
import clean.app.data.CleanDb
import clean.app.data.open

/**
 * The periodic job: a backup into the chosen folder. A failure is recorded for Settings to show, and the job is tried again soon (the
 * request's backoff, an hour and growing) up to three times before the next turn of the period, so one bad moment does not cost a day or a week.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = AutoBackup.run(applicationContext)
        // No folder chosen is not a failure to retry; a recorded error is.
        val failed = !ok && CleanDb.open(applicationContext).dao().get(AutoBackup.ERROR) != null
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
