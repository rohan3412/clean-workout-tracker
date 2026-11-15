package clean.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Two tiles of prime sides laid over each other: each repeats, but the pair only lines up again after 251 x 199 pixels in each
 * direction, so no block or repeat can be seen at any size.
 */
private const val TILE_A = 251
private const val TILE_B = 199

/** The grain's strength at a full slider; the slider's 0 to 1 is multiplied by it. */
private const val GRAIN_ALPHA = 0.55f

/**
 * Smooth noise that wraps at [size]: random values on a lattice every [cell] pixels (the lattice wraps, so the tile is seamless),
 * blended between with a smoothstep. [cell] divides [size] only roughly, which is fine because the lattice index wraps by count.
 */
private fun smooth(size: Int, cell: Int, random: Random): FloatArray {
    val n = maxOf(2, size / cell)
    val lattice = FloatArray(n * n) { random.nextFloat() }
    val out = FloatArray(size * size)
    for (y in 0 until size) {
        val fy = y.toFloat() * n / size
        val y0 = fy.toInt() % n
        val y1 = (y0 + 1) % n
        val ty = (fy - fy.toInt()).let { it * it * (3 - 2 * it) }
        for (x in 0 until size) {
            val fx = x.toFloat() * n / size
            val x0 = fx.toInt() % n
            val x1 = (x0 + 1) % n
            val tx = (fx - fx.toInt()).let { it * it * (3 - 2 * it) }
            val top = lattice[y0 * n + x0] * (1 - tx) + lattice[y0 * n + x1] * tx
            val bottom = lattice[y1 * n + x0] * (1 - tx) + lattice[y1 * n + x1] * tx
            out[y * size + x] = top * (1 - ty) + bottom * ty
        }
    }
    return out
}

/**
 * A seamless square of paper, made once from a fixed seed: a soft fine tooth (the average of three dice, so few pixels stand out),
 * small fibre-sized clouds and a faint large mottle. [light] gives light flecks to screen onto a dark page; otherwise dark ones to
 * multiply onto light paper. The values stay close to neutral, so the paper reads as a surface, not as static.
 */
private fun paper(size: Int, seed: Int, light: Boolean): ImageBitmap {
    val random = Random(seed)
    val fibres = smooth(size, 3, random)
    val clouds = smooth(size, 14, random)
    val pixels = IntArray(size * size) { i ->
        val tooth = (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3f
        // 0 is the paper itself, 1 the darkest a fleck gets; centred low so the mean darkening matches the old grain's.
        val depth = (0.5f * tooth + 0.3f * fibres[i] + 0.2f * clouds[i] - 0.25f).coerceIn(0f, 1f) * 0.9f
        val v = (depth * 255).toInt()
        val g = if (light) v else 255 - v
        0xFF000000.toInt() or (g shl 16) or (g shl 8) or g
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private class Paper(val a: ImageBitmap, val b: ImageBitmap)

private val DarkPaper by lazy { Paper(paper(TILE_A, 7, light = false), paper(TILE_B, 11, light = false)) }
private val LightPaper by lazy { Paper(paper(TILE_A, 7, light = true), paper(TILE_B, 11, light = true)) }

/**
 * A fine paper grain over the content when [strength] (0 to 1) is above 0: two seamless paper tiles of prime sides, each at half
 * the strength, blended in after the content is drawn, multiplied onto a light page and screened onto a dark one. Two rects with a
 * repeated shader each, so it costs no more to draw than one; it takes no touch.
 */
fun Modifier.paperGrain(strength: Float, darkPage: Boolean): Modifier = if (strength <= 0f) this else drawWithContent {
    drawContent()
    val paper = if (darkPage) LightPaper else DarkPaper
    val mode = if (darkPage) BlendMode.Screen else BlendMode.Multiply
    for (tile in listOf(paper.a, paper.b)) {
        drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = strength * GRAIN_ALPHA * 0.5f, blendMode = mode)
    }
}
