package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import clean.core.PrKind
import kotlinx.coroutines.launch

/*
 * Things that arrive as a reward (stage F4, 2026-11-16): a PR badge earned live lands with the bounce and one shimmer, and a badge,
 * shield or streak earned on the summary flips in like a coin. Both do nothing with animations off.
 */

/** The kinds in [kinds] that were not there when this row first appeared: the ones earned while it is on the screen, which land. */
@Composable
fun rememberLanding(kinds: Set<PrKind>): Set<PrKind> {
    val there = remember { kinds }
    return kinds - there
}

/**
 * A PR badge that has just been earned: it scales in with [Motion.reward]'s bounce and one light highlight sweeps across it once
 * ([Motion.shimmer]). [on] false (a badge that was already there) is no change.
 */
@Composable
fun Modifier.landing(on: Boolean): Modifier {
    if (!on || !LocalMotion.current) return this
    val pop = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, Motion.reward()) }
        sweep.animateTo(1f, Motion.shimmer())
    }
    return graphicsLayer {
        scaleX = pop.value; scaleY = pop.value
        alpha = pop.value.coerceIn(0f, 1f)
        // The highlight is drawn only where the badge is, so it needs a layer of its own.
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val s = sweep.value
        if (s > 0f && s < 1f) {
            val band = size.width * 0.5f
            val x = -band + (size.width + 2f * band) * s
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent), startX = x - band / 2f, endX = x + band / 2f),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

/** A badge, shield or streak flipping in like a coin: it spins round its upright axis from small and see-through to full, as [progress] goes from 0 to 1. */
fun Modifier.coin(progress: () -> Float): Modifier = graphicsLayer {
    val t = progress()
    rotationY = 360f * (1f - t)
    cameraDistance = 12f * density
    val size = 0.4f + 0.6f * t
    scaleX = size; scaleY = size
    alpha = t.coerceIn(0f, 1f)
}

/**
 * D2: a set row that has just become a PR gets one accent shimmer along its top and bottom edges, left to right, about 600 ms
 * ([Motion.prEdge]). [on] is what [rememberLanding] says, so a row that already had its PR when it appeared stays still, and
 * with animations off nothing is drawn.
 */
@Composable
fun Modifier.prEdge(on: Boolean): Modifier {
    if (!LocalMotion.current) return this
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) { sweep.snapTo(0f); sweep.animateTo(1f, Motion.prEdge()) }
    }
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        val s = sweep.value
        if (s > 0f && s < 1f) {
            val band = size.width * 0.4f
            val x = -band + (size.width + 2f * band) * s
            val brush = Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.9f), Color.Transparent), startX = x - band / 2f, endX = x + band / 2f)
            val line = 2.dp.toPx()
            drawRect(brush, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, line))
            drawRect(brush, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - line), size = androidx.compose.ui.geometry.Size(size.width, line))
        }
    }
}
