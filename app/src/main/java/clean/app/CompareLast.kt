package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalCleanColors
import clean.core.Verdict
import clean.core.Workout
import clean.core.compareWithLast

/**
 * "Compared with last time": each exercise's best set against the same exercise's session before this workout
 * (rule in docs/domain.md). A sign and a word carry the direction; colour only backs them up. A row opens the exercise's History.
 */
@Composable
fun CompareLastSection(model: AppModel, workout: Workout, workouts: List<Workout>) {
    val rule by model.oneRm.collectAsState()
    val result = remember(workouts, workout, rule) { compareWithLast(workout, { model.sessionsIn(workouts, it) }, model::loadTypeOf, rule) }
    if (result.rows.isEmpty()) return
    val up = LocalCleanColors.current.done
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Section("Compared with last time") {
        Text(
            if (result.compared == 0) "First time for all of these." else "${result.improved} of ${result.compared} improved",
            fontWeight = FontWeight.Bold,
        )
        for (row in result.rows) {
            val unit = model.unitOf(row.exercise)
            val words = row.words { showWeight(it, unit) + " $unit" }
            val mark = when (row.verdict) { Verdict.UP -> "▲"; Verdict.DOWN -> "▼"; Verdict.SAME -> "="; Verdict.FIRST -> "★" }
            val colour = if (row.verdict == Verdict.UP) up else quiet
            val now = setValues(row.today, unit)
            val then = row.before?.let { setValues(it, unit) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { model.open(Screen.ExerciseDetail(row.exercise, tab = 1)) }
                    .clearAndSetSemantics { contentDescription = "${row.exercise}: $now" + (then?.let { ", last time $it" } ?: "") + ", $words" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                    Text(row.exercise, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(if (then == null) now else "$now  vs  $then", style = MaterialTheme.typography.bodySmall, color = quiet)
                }
                Text(mark, Modifier.width(18.dp), color = colour)
                Text(words, fontWeight = FontWeight.SemiBold, color = colour)
            }
        }
    }
}
