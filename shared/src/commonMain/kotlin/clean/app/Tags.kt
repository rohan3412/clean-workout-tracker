package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import clean.core.TAG_MAX
import clean.core.cleanTag

/*
 * Workout tags (N10, round 7). A workout has none unless the user adds one on purpose: nothing here ever asks. A tag is a short word
 * kept in Clean's own table (kv `tag:<profileId>:<start>`), never in the Strong CSV, so the export is untouched. A tag can be
 * switched to "Leave out of stats" (kv `tag.out:<profileId>:<tag>`); the workouts under it then drop out of every statistic and
 * record (`AppModel.workouts`) but stay in History. Docs: docs/data-format.md, "Workout tags".
 */

/**
 * The quiet line that adds or shows a workout's tag, set like the note line under the name. A tap opens [TagSheet].
 * [tag] is what the workout has now; [onChange] is given the new tag, empty to take it off.
 */
@Composable
fun TagLine(model: AppModel, tag: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val out by model.leftOutTags.collectAsState()
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        if (tag.isEmpty()) "Add tag" else if (tag in out) "$tag  ·  left out of stats" else tag,
        modifier.clickable(onClickLabel = if (tag.isEmpty()) "Add a workout tag" else "Change the workout tag") { open = true }.minTouch().wrapContentHeight(Alignment.CenterVertically),
        style = MaterialTheme.typography.bodyMedium, color = quiet, fontStyle = if (tag.isEmpty()) FontStyle.Italic else FontStyle.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    if (open) TagSheet(model, tag, { open = false }, onChange)
}

/** Chooses a tag: typed, or one of those already used (most used first). With a tag set, it can be taken off and left out of the stats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSheet(model: AppModel, current: String, onDismiss: () -> Unit, onChange: (String) -> Unit) {
    val used by model.tagsInUse.collectAsState()
    val out by model.leftOutTags.collectAsState()
    var typed by remember { mutableStateOf("") }
    fun pick(text: String) {
        val tag = cleanTag(text)
        if (tag.isNotEmpty()) { onChange(tag); onDismiss() }
    }
    Sheet(onDismiss, "Tag") {
        OutlinedTextField(
            typed, { typed = it.take(TAG_MAX) }, Modifier.fillMaxWidth().padding(horizontal = Space.xl), singleLine = true,
            placeholder = { Text("New tag") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { pick(typed) }),
        )
        if (used.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            for (tag in used) CleanChip(selected = tag == current, onClick = { pick(tag) }, label = { Text(tag) })
        }
        if (current.isNotEmpty()) {
            val on = current in out
            Row(
                Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { model.setTagLeftOut(current, it) }.padding(horizontal = Space.xl).minTouch(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = Space.m)) {
                    Text("Leave out of stats")
                    Text("Workouts tagged $current drop out of the stats and records; they stay in History.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(on, null)
            }
            SheetRow("Remove tag", CleanIcons.Close) { onChange(""); onDismiss() }
        }
    }
}

/** History's row of tag chips: only there when some workout has a tag. [selected] is the tag filtered by, null for all; a tap on it again clears it. */
@Composable
fun TagFilter(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (tags.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(tags, key = { it }) { tag -> CleanChip(selected = tag == selected, onClick = { onSelect(if (tag == selected) null else tag) }, label = { Text(tag) }) }
    }
}
