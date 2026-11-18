package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.PillShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Slide to confirm (B7), for what has no Undo: a danger-coloured track with a knob dragged to its end. Let go early and the knob springs
 * back; let go at the end and [onConfirm] runs. A screen reader gets one plain action named [spoken] ("Delete everything"), so the
 * slide is never the only way (C4).
 */
@Composable
fun SlideToConfirm(label: String, spoken: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val danger = LocalCleanColors.current.danger
    val knob = 44.dp
    val inset = 4.dp
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(1) }
    val density = LocalDensity.current
    val travel = (width - with(density) { (knob + inset * 2).toPx() }).coerceAtLeast(1f)
    Box(
        modifier.fillMaxWidth().height(knob + inset * 2).onSizeChanged { width = it.width }
            .background(danger.copy(alpha = 0.16f), PillShape)
            .semantics(mergeDescendants = true) { contentDescription = spoken; role = Role.Button; onClick(spoken) { onConfirm(); true } },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The words fade as the knob covers them.
        Text(
            label, Modifier.fillMaxWidth().padding(start = knob + inset * 2, end = Space.l).clearAndSetSemantics { }.graphicsLayer { alpha = 1f - (offset.value / travel).coerceIn(0f, 1f) },
            color = danger, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
        Box(
            Modifier.padding(start = inset).offset { IntOffset(offset.value.roundToInt(), 0) }.size(knob).background(danger, CircleShape)
                .draggable(
                    rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, travel)) } }, Orientation.Horizontal,
                    onDragStopped = {
                        if (offset.value >= travel * 0.9f) { offset.snapTo(travel); onConfirm() } else offset.animateTo(0f, Motion.follow())
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(CleanIcons.Forward, null, Modifier.size(24.dp), tint = danger.readableOn()) }
    }
}
