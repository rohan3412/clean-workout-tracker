package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.SheetTopShape
import kotlinx.coroutines.launch

/**
 * A page of choices in the glass material (round 28): the one shape for a picker that is a long list, as [Sheet] is for a short set of rows. A tall sheet
 * (92 % of the screen) with a grab handle, a [title] and a close control as its header, then the [content], which fills the rest and scrolls itself (a
 * list, with its search pill and chips above it), and an optional [bottom] bar over the list's foot for the action that finishes the choice ("Add 2",
 * "Start 3"). A tap outside, a pull down on the header, the close control and Back all close it; the keyboard pushes it up. The page behind
 * shrinks and dims as it does for a [Sheet] ([SheetDepth]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(title: String, onDismiss: () -> Unit, bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    DisposableEffect(Unit) { SheetDepth.open++; onDispose { SheetDepth.open-- } }
    val drag = remember { Animatable(0f) }
    var height by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismiss, fullScreenDialogProperties()) {
        BlinkWindow()
        Box(
            Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.32f)) }.clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .semantics { contentDescription = "Close" },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                Modifier.fillMaxWidth().widthIn(max = 640.dp).fillMaxHeight(0.92f).statusBarsPadding().imePadding().onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .graphicsLayer { translationY = drag.value }
                    .glass(SheetTopShape, haze = null, lift = 16.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { }.semantics { paneTitle = title },
                shape = SheetTopShape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                        Column(
                            Modifier.fillMaxWidth().draggable(
                                rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                                onDragStopped = { v -> if (drag.value > height / 4f || v > 1800f) onDismiss() else drag.animateTo(0f, Motion.follow()) },
                            ),
                        ) {
                            androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Drag down to close" })
                            Row(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                IconButton(onClick = onDismiss) { Icon(CleanIcons.Close, "Close") }
                            }
                        }
                        content()
                    }
                    if (bottom != null) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { bottom() }
                }
            }
        }
    }
}
