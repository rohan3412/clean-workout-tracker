package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDateTime

/** How many scheduled backups are kept in the chosen folder; older ones are deleted after each new one. */
const val AUTO_BACKUP_KEEP = 5

/** How often the scheduled backup runs. [key] is what Settings stores; [days] is the period. */
enum class AutoBackupFrequency(val key: String, val label: String, val days: Long) {
    DAILY("daily", "Daily", 1), WEEKLY("weekly", "Weekly", 7);

    companion object {
        /** The frequency stored under [key]; null (the backup is off) for anything else. */
        fun of(key: String?): AutoBackupFrequency? = entries.firstOrNull { it.key == key }
    }
}

private const val PREFIX = "Clean auto backup "
private val STAMP = DatePattern("yyyy-MM-dd HHmm")
private val NAME = Regex("""Clean auto backup \d{4}-\d{2}-\d{2} \d{4}( \(\d+\))?\.zip""")

/** The name of the backup made at [now]: `Clean auto backup 2026-11-19 0930.zip`, which sorts by time. */
fun autoBackupName(now: LocalDateTime): String = PREFIX + now.format(STAMP) + ".zip"

/** Whether [name] is one a scheduled backup made (so anything else the user keeps in the folder is never touched). */
fun isAutoBackupName(name: String): Boolean = NAME.matches(name)

/**
 * The names to delete so that only the newest [keep] scheduled backups remain. Only names [isAutoBackupName] accepts count; the
 * newest are those that sort last, as the stamp in the name sorts by time.
 */
fun autoBackupsToDelete(names: Collection<String>, keep: Int = AUTO_BACKUP_KEEP): List<String> =
    names.filter(::isAutoBackupName).sortedDescending().drop(keep)
