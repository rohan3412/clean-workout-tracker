package clean.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import clean.app.ui.theme.cardShape
import clean.core.NAV_DEFAULT
import clean.core.navOrder

private const val NAV_ORDER = "nav.order"

/**
 * The Navigation page: the order of the four outer tabs (Workout stays in the middle) in a list to
 * drag by the grip, or to move with the Move up / Move down actions. Every change is stored at once and the bar follows. A hold on a tab opens it.
 */
@Composable
fun NavigationScreen(model: AppModel) {
    val options by model.options.collectAsState()
    var shown by remember { mutableStateOf(navOrder(options[NAV_ORDER])) }
    val state = rememberLazyListState()
    val motion = LocalMotion.current
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Right after a swap the list's measurements are one frame old; no further swap is made until it has been laid out again.
    var settling by remember { mutableStateOf(false) }
    fun save(list: List<String>) { shown = list; model.setOption(NAV_ORDER, if (list == NAV_DEFAULT) null else list.joinToString(",")) }
    fun move(from: Int, to: Int) { if (to in shown.indices && from != to) save(shown.toMutableList().also { it.add(to, it.removeAt(from)) }) }
    LaunchedEffect(shown) { withFrameNanos { }; settling = false }
    fun dragBy(delta: Float) {
        val key = dragging ?: return
        dragOffset += delta
        if (settling) return
        val items = state.layoutInfo.visibleItemsInfo
        val me = items.firstOrNull { it.key == key } ?: return
        val centre = (me.offset + me.size / 2 + dragOffset).toInt()
        val other = items.firstOrNull { it.key != key && it.key in shown && centre in it.offset until it.offset + it.size } ?: return
        val from = shown.indexOf(key)
        val to = shown.indexOf(other.key)
        // The held row keeps its place under the finger while the list moves under it.
        val newOffset = if (to > from) other.offset + other.size - me.size else other.offset
        dragOffset += me.offset - newOffset
        settling = true
        move(from, to)
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Tab bar", model::back) }) { padding ->
        SettingsFloat(model, Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(bottom = settingsFloatSpace(model))) {
            item(key = "order-title") {
                Caption("Order of the four outer tabs", Modifier.padding(start = Space.xl, top = Space.m, bottom = Space.xs))
            }
            items(shown, key = { it }) { name ->
                val tab = Tab.entries.first { it.name.lowercase() == name }
                val index = shown.indexOf(name)
                val lifted = dragging == name
                val glide = if (motion && !lifted) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = Motion.follow()) else Modifier
                Row(
                    Modifier.fillMaxWidth().then(glide).padding(horizontal = Space.l, vertical = Space.xs).zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) dragOffset else 0f }
                        .background(MaterialTheme.colorScheme.surfaceContainer, cardShape())
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") { move(index, index - 1); true },
                                CustomAccessibilityAction("Move down") { move(index, index + 1); true },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.minTouch().semantics { contentDescription = "Reorder ${tab.label}" }.pointerInput(name) {
                            detectDragGestures(
                                onDragStart = { dragging = name; dragOffset = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragEnd = { dragging = null; dragOffset = 0f },
                                onDragCancel = { dragging = null; dragOffset = 0f },
                            ) { change, drag -> change.consume(); dragBy(drag.y) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(CleanIcons.Grip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(tab.label, Modifier.weight(1f).padding(vertical = Space.l))
                }
            }
            item(key = "end") {
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Workout stays in the middle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MainButton("Done", model::back, Modifier.fillMaxWidth())
                    QuietButton("Reset to default", { shown = NAV_DEFAULT; model.setOption(NAV_ORDER, null)}, Modifier.fillMaxWidth())
                }
            }
        }
        }
    }
}
