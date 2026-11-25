package clean.app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.orange

/**
 * Settings, Appearance, Background (Round 32): a strip of live previews, "Theme's own" first (showing the theme's pair) and then the ten. Each preview is the real
 * background drawn small ([drawBackdropScaled]). A tap applies at once; Photo opens the app's own choice of snapshots and progress photos.
 */
@Composable
fun BackgroundStrip(model: AppModel) {
    val look = LocalLook.current
    val setting by model.background.collectAsState()
    val shut = model.photosShut()
    val (_, picked) = rememberBackgroundPhoto(model, model.backgroundPhoto.collectAsState().value, shut)
    var choosing by remember { mutableStateOf(false) }
    // Paper's preview needs its tiles; they are made off the main thread, once per mode.
    androidx.compose.runtime.LaunchedEffect(look.dark) { kotlinx.coroutines.withContext(ioDispatcher) { Paper.warm(look.dark) } }
    val own = resolveBackground(BACKGROUND_THEME, look.themeName)
    val chosen = if (setting == BACKGROUND_THEME) BACKGROUND_THEME else BACKGROUNDS.firstOrNull { it.id == setting }?.id ?: BACKGROUND_THEME
    fun backdrop(spec: Background) = Backdrop(spec, look, look.page, if (spec.photo) picked ?: STAND_IN_PHOTO else null)
    LazyRow(Modifier.fillMaxWidth().testTag("bg-row"), contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        item(key = BACKGROUND_THEME) { BackgroundTile("Theme's own", "Theme's own (${own.name})", chosen == BACKGROUND_THEME, remember(own, look, picked) { backdrop(own) }, "bg:theme") { model.setBackground(BACKGROUND_THEME) } }
        items(BACKGROUNDS, key = { it.id }) { spec ->
            BackgroundTile(spec.name, spec.name, chosen == spec.id, remember(spec, look, picked) { backdrop(spec) }, "bg:${spec.id}") {
                if (spec.photo) model.unlockPhotos { choosing = true } else model.setBackground(spec.id)
            }
        }
    }
    if (choosing) BackgroundPhotoChoice(model, { choosing = false }) { model.setBackgroundPhoto(it); choosing = false }
}

@Composable
private fun BackgroundTile(label: String, description: String, selected: Boolean, backdrop: Backdrop, tag: String, onClick: () -> Unit) {
    val shape = cardShape()
    val ring = if (selected) LocalLook.current.orange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(
        Modifier.width(72.dp).clip(MaterialTheme.shapes.small).testTag(tag)
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton; contentDescription = description; onClick { onClick(); true } }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Box(Modifier.size(width = 66.dp, height = 132.dp).clip(shape).drawBackdropScaled(backdrop).border(if (selected) 2.dp else 1.dp, ring, shape))
        Text(
            label, style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The app's own photo choice for the Photo background: the snapshots, then the progress photos, as small squares. */
@Composable
private fun BackgroundPhotoChoice(model: AppModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val moments by model.moments.collectAsState()
    val photos by model.photos.collectAsState()
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a photo") },
        text = {
            if (moments.isEmpty() && photos.isEmpty()) Text("No photos yet. Add some in Photos on Profile, then choose one here.")
            else LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 360.dp).testTag("bg:photo-choice"), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (moments.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Snapshots", style = MaterialTheme.typography.labelLarge) }
                items(moments, key = { "m:" + it.name }) { m ->
                    MomentThumb(model, m, 240, Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small).clickable { onPick("m:" + m.name) }.semantics { contentDescription = "Snapshot" })
                }
                if (photos.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Progress photos", style = MaterialTheme.typography.labelLarge) }
                items(photos, key = { "p:" + it.name }) { p ->
                    PhotoThumb(model, p, 240, Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small).clickable { onPick("p:" + p.name) }.semantics { contentDescription = "Progress photo" })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
