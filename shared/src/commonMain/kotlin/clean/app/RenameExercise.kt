package clean.app

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import clean.app.data.RenameEntry
import kotlinx.coroutines.launch

/**
 * "Rename or merge…" on an exercise's page. A name that is an exercise already merges this one into it; any other renames it.
 * Either way the exercise-name text of the stored rows changes (and what is keyed by the name), at the user's word, and the
 * dialog says how many; it can be taken back from Settings, Data.
 */
@Composable
fun RenameDialog(model: AppModel, name: String, onDismiss: () -> Unit) {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    var text by rememberSaveable { mutableStateOf(name) }
    // Every exercise there is, to offer as a name; the user's own and the library's.
    val all = remember(workouts, settings) { allExercises(workouts, settings.keys) }
    val listed = remember(all) { all.mapTo(HashSet()) { it.name } }
    // A name picked from the list is used exactly as stored; one typed is used without the spaces round it.
    val target = if (text in listed) text else text.trim()
    val ready = target.isNotEmpty() && target != name
    val merging = ready && model.exerciseExists(target)
    val sessions = model.sessionsIn(workouts, name).size
    val suggestions = remember(text, all) {
        val q = text.trim()
        if (q.isEmpty()) emptyList() else all.filter { it.name != name && it.name.contains(q, ignoreCase = true) }.take(6)
    }
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename or merge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(text, { text = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                for (s in suggestions) Text(s.name, Modifier.fillMaxWidth().clickable { text = s.name }.padding(vertical = Space.s), color = MaterialTheme.colorScheme.primary)
                if (ready) Text(
                    if (merging) "This merges ${plural(sessions, "session")} of “$name” into “$target”. Their records are combined."
                    else "Rename “$name” to “$target” in ${plural(sessions, "workout")}.",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    "Only the exercise name on the logged sets changes (by exact match, so a name differing by a space or a capital is another exercise); no weight, rep or date does. " +
                        "Settings, Data lists renamed exercises, where it can be undone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = { model.renameExercise(name, target); onDismiss() }) { Text(if (merging) "Merge" else "Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Settings, Data: the renames and merges that can still be undone. Nothing is shown while there are none. */
@Composable
fun RenamedExercises(model: AppModel) {
    var log by remember { mutableStateOf<List<RenameEntry>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var undoing by remember { mutableStateOf<RenameEntry?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tick) { log = model.renameLog() }
    if (log.isEmpty()) return
    SettingsGroup("Renamed exercises", footer = "Undo puts the old name back on the sets it was changed on.") {
        for (entry in log) SettingsRow(
            "${entry.from} → ${entry.to}",
            summary = "${if (entry.merged) "merged · " else ""}${plural(entry.workouts, "session")} · ${prettyDay(entry.at)}",
            trailing = { TextButton(onClick = { undoing = entry }) { Text("Undo") } },
        )
    }
    undoing?.let { entry ->
        Confirm(
            "Undo this?",
            "“${entry.from}” goes back on the ${plural(entry.rows.size, "row")} it was taken from, and the settings, templates and program as they were. " +
                "Anything logged as “${entry.to}” since stays as “${entry.to}”.",
            "Undo", { scope.launch { model.undoRename(entry); tick++ } },
        ) { undoing = null }
    }
}
