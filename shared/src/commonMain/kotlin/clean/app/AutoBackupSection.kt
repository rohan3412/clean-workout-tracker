package clean.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import clean.app.platform.LocalPlatform
import clean.app.ui.theme.LocalCleanColors
import clean.core.AUTO_BACKUP_KEEP
import clean.core.AutoBackupFrequency
import clean.core.currentLocalDateTime
import clean.core.relativeTime

/** The kv keys of the scheduled backup (`android/AutoBackup.kt` is what runs it). */
object AutoBackupKeys {
    const val FOLDER = "autobackup.folder"
    const val FREQ = "autobackup.freq"
    const val LAST = "autobackup.last"
    const val ERROR = "autobackup.error"
}

/** How often the "Last backup" line is worked out again while the page is open, so "12 minutes ago" keeps up. */
private const val REFRESH_MILLIS = 60_000L

/**
 * Settings, Backup and sync: the folder with a small "Back up" button at its end (Round 36, moved there in Round 38; the same backup to the same folder at once), the frequency and when it last ran, said relative to now
 * for the first week and as a date after. Quiet: a line each, the error only when there is one.
 */
@androidx.compose.runtime.Composable
fun AutoBackupSection(model: AppModel) {
    val platform = LocalPlatform.current
    val options by model.options.collectAsState()
    val folder = options[AutoBackupKeys.FOLDER].orEmpty()
    val freq = AutoBackupFrequency.of(options[AutoBackupKeys.FREQ])
    val folderName = remember(folder) { if (folder.isEmpty()) null else runCatching { platform.folderName(folder) }.getOrNull() ?: "Chosen folder" }
    val pick = platform.rememberFolderPicker { picked ->
        if (picked != null) {
            // The permission is kept across restarts of the phone; the one for a folder left behind is given back.
            if (folder.isNotEmpty() && folder != picked) runCatching { platform.releaseFolder(folder) }
            model.setOption(AutoBackupKeys.FOLDER, picked)
            model.setOption(AutoBackupKeys.ERROR, null)
            if (freq == null) {
                model.setOption(AutoBackupKeys.FREQ, AutoBackupFrequency.WEEKLY.key)
                platform.scheduleAutoBackup(AutoBackupFrequency.WEEKLY)
            }
        }
    }
    // The clock the "Last backup" line is said against: looked at again every minute while the page is open.
    var now by remember { mutableStateOf(currentLocalDateTime()) }
    LaunchedEffect(Unit) { while (true) { delay(REFRESH_MILLIS); now = currentLocalDateTime() } }
    SettingsGroup(
        "Automatic backup",
        footer = "Writes the same backup (without the exercise animations) to a folder you choose, and keeps the newest $AUTO_BACKUP_KEEP there. It runs on the phone, when the battery is not low.",
    ) {
        // Round 38: "Back up now" is a small button at the end of the Folder row (the rest of the row still picks the folder); it shows its progress itself.
        SettingsRow(
            "Folder", value = if (folder.isEmpty()) "Not chosen" else null, summary = folderName, onClick = { pick() }, chevron = folder.isEmpty(),
            trailing = if (folder.isEmpty()) null else ({
                SmallButton(
                    if (model.backingUpNow) "Backing up" else "Back up", { model.backUpNow() }, Modifier.testTag("back up now"),
                    enabled = !model.backingUpNow, busy = model.backingUpNow, description = if (model.backingUpNow) "Backing up now" else "Back up now",
                )
            }),
        )
        if (folder.isNotEmpty()) {
            val choices = listOf(null) + AutoBackupFrequency.entries
            ChoiceRow("Frequency") {
                Segmented(choices, freq, { it?.label ?: "Off" }) { chosen ->
                    model.setOption(AutoBackupKeys.FREQ, chosen?.key)
                    platform.scheduleAutoBackup(chosen)
                }
            }
            val last = options[AutoBackupKeys.LAST]
            val lastAt = last?.let(::parseDate)
            SettingsRow("Last backup", value = if (last == null) "None yet" else lastAt?.let { relativeTime(it, now) } ?: prettyDate(last))
            options[AutoBackupKeys.ERROR]?.takeIf { it.isNotEmpty() }?.let { NoteRow(it, quiet = true, color = LocalCleanColors.current.danger) }
        }
    }
}
