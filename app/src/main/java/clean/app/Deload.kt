package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import clean.core.DeloadSignal

/**
 * One quiet line when a lighter week might help (`deloadSignals` in core), under the reason on the Today card and at the top of the
 * week report. It opens the signals behind it, each leading to what it is about; "Not now" puts it off for a week. Nothing when
 * it is not suggested or has been put off.
 */
@Composable
fun DeloadHint(model: AppModel, modifier: Modifier = Modifier) {
    val suggestion by model.deloadSuggestion.collectAsState()
    var open by remember { mutableStateOf(false) }
    val found = suggestion ?: return
    Text("A lighter week might help ›", modifier.clickable { open = true }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (open) Sheet({ open = false }, "A lighter week might help") {
        Text(
            "What in your log points to it. None of these is a rule; “Not now” hides this for a week.",
            Modifier.padding(start = Space.xl, end = Space.xl, bottom = Space.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (finding in found.findings) when (finding.signal) {
            DeloadSignal.STALLED -> {
                Text(finding.reason, Modifier.padding(horizontal = Space.xl, vertical = Space.s))
                for (exercise in finding.exercises) SheetRow(exercise) { open = false; model.open(Screen.ExerciseDetail(exercise, tab = 0)) }
            }
            DeloadSignal.FATIGUED -> SheetRow(finding.reason, CleanIcons.Body) {
                open = false
                model.openBody("Fatigue")
            }
            DeloadSignal.VOLUME_RISE, DeloadSignal.NO_LIGHT_WEEK -> SheetRow(finding.reason, CleanIcons.History) { open = false; model.open(Screen.Trends) }
        }
        QuietButton("Not now", { model.snoozeDeload(); open = false }, Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.m))
    }
}
