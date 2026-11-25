package clean.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Round 34: the Paper texture, ported from `artifacts/round6/themes-proposal.md` ("Paper: the algorithm") and its `paperTile()`. Paper is formation (cloudy tone from
 * uneven pulp), a few faint long fibres and only the gentlest tooth; the first version was mostly per-pixel noise and read as dust.
 *
 * A tile is 512 x 512 px, seamless, grey in Dark (white and black: a sheen) and warm in Light, made once per seed and mode from a Park-Miller generator
 * (`st = seed * 9973 + 7`, `st = st * 16807 mod 2147483647`). Four octaves of periodic value noise (3, 6, 12 and 24 cells across, amplitudes .5, .28, .15, .07; bilinear
 * with smoothstep weights, so no blur pass) are summed, flattened by `sign(v) |v|^1.4`, given a per-pixel tooth of 0.025, and each pixel is the light or shade colour
 * with an alpha of `min(255, |v| 120)` (so no blend mode is needed). Then 220 fibres, 28 to 96 px long, leaning about 20 degrees, drawn at all nine wrapping offsets so the
 * tile has no seam. Layer A is seed 1 at 512 design units, layer B seed 2 at 703 (1.373 x) offset by 137 x 61, so the two sets of seams never line up across a page.
 *
 * Two differences from the mockup, both because common code has no mask filter: the fibres' 0.8 px blur is a stroke 0.8 px wider with the same alpha (they are antialiased,
 * and 0.018 to 0.05 strong, so the strand is as soft), and a pixel's alpha is rounded to a whole 8-bit step as the mockup's ImageData is.
 */
object Paper {
    const val SIZE = 512
    const val FIBRES = 220
    /** Layer B: its scale (703 / 512) and where its tile starts, in design units. */
    const val SCALE_B = 703f / 512f
    const val OFFSET_B_X = 137f
    const val OFFSET_B_Y = 61f

    private class Octave(val cells: Int, val amp: Double, val lat: FloatArray)

    private val tiles = Array(4) { i -> lazy { make(seed = i % 2 + 1, dark = i >= 2) } }
    private val brushes = Array(4) { i -> lazy { ShaderBrush(ImageShader(tiles[i].value, TileMode.Repeated, TileMode.Repeated)) } }
    private fun index(seed: Int, dark: Boolean) = (seed - 1) + if (dark) 2 else 0

    /** Bumped when a mode's tiles have been made, so a draw that skipped Paper is redrawn. */
    var madeCount by mutableIntStateOf(0)
        private set
    private val made = BooleanArray(2)

    /** Whether the tiles for Dark ([dark]) or Light have been made (a draw never makes them: it would stall a frame). */
    fun ready(dark: Boolean): Boolean { madeCount; return made[if (dark) 1 else 0] }

    /** Makes both tiles of a mode (about a million noise samples), off the main thread when called from one. */
    fun warm(dark: Boolean) {
        if (made[if (dark) 1 else 0]) return
        tiles[index(1, dark)].value; tiles[index(2, dark)].value; brushes[index(1, dark)].value; brushes[index(2, dark)].value
        made[if (dark) 1 else 0] = true
        madeCount++
    }

    fun tile(seed: Int, dark: Boolean): ImageBitmap = tiles[index(seed, dark)].value
    fun brush(seed: Int, dark: Boolean): ShaderBrush = brushes[index(seed, dark)].value

    /** The tile's pixel colours: [hi] where the noise is above zero, [lo] below. */
    fun hi(dark: Boolean) = if (dark) intArrayOf(255, 255, 255) else intArrayOf(255, 250, 240)
    fun lo(dark: Boolean) = if (dark) intArrayOf(0, 0, 0) else intArrayOf(150, 130, 110)

    /** The strongest alpha a pixel of a tile is likely to reach (the noise's sum rarely nears 1): 0.26, for the contrast floor. */
    const val PEAK_ALPHA = 0.26f

    private fun make(seed: Int, dark: Boolean): ImageBitmap {
        val n = SIZE
        var st = seed * 9973L + 7L
        fun rnd(): Double { st = st * 16807L % 2147483647L; return st / 2147483647.0 }
        val octaves = listOf(3 to .5, 6 to .28, 12 to .15, 24 to .07).map { (cells, amp) -> Octave(cells, amp, FloatArray(cells * cells) { (rnd() * 2 - 1).toFloat() }) }
        fun smooth(t: Double) = t * t * (3 - 2 * t)
        val hi = hi(dark); val lo = lo(dark)
        // Buckets of pixels by colour side and alpha: one drawPoints call each (as Grain does), source-over on a clear bitmap, which keeps each pixel's own alpha.
        val buckets = arrayOfNulls<ArrayList<Offset>>(512)
        for (y in 0 until n) for (x in 0 until n) {
            var v = 0.0
            for (o in octaves) {
                val c = o.cells
                val fx = x.toDouble() / n * c; val fy = y.toDouble() / n * c
                val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
                val tx = smooth(fx - x0); val ty = smooth(fy - y0)
                fun l(i: Int, j: Int) = o.lat[(j % c) * c + (i % c)].toDouble()
                val a = l(x0, y0) + (l(x0 + 1, y0) - l(x0, y0)) * tx
                val b = l(x0, y0 + 1) + (l(x0 + 1, y0 + 1) - l(x0, y0 + 1)) * tx
                v += o.amp * (a + (b - a) * ty)
            }
            v = (if (v > 0) 1.0 else if (v < 0) -1.0 else 0.0) * abs(v).pow(1.4) + (rnd() * 2 - 1) * .025
            val alpha = min(255.0, abs(v) * 120).roundToInt()
            if (alpha == 0) continue
            val key = alpha * 2 + if (v > 0) 1 else 0
            (buckets[key] ?: ArrayList<Offset>().also { buckets[key] = it }) += Offset(x + .5f, y + .5f)
        }
        val image = ImageBitmap(n, n)
        val canvas = Canvas(image)
        val points = Paint().apply { strokeWidth = 1f; strokeCap = StrokeCap.Square; isAntiAlias = false }
        for (key in buckets.indices) {
            val list = buckets[key] ?: continue
            val c = if (key and 1 == 1) hi else lo
            points.color = Color(c[0], c[1], c[2], key / 2)
            canvas.drawPoints(PointMode.Points, list, points)
        }
        // The fibres, after the pixels, each at all nine offsets so the tile wraps.
        val pen = Paint().apply { style = PaintingStyle.Stroke; strokeCap = StrokeCap.Round; isAntiAlias = true }
        for (i in 0 until FIBRES) {
            val x = rnd() * n; val y = rnd() * n; val len = 28 + rnd() * 68; val ang = .35 + (rnd() - .5) * 1.05; val bend = (rnd() - .5) * 28
            val ex = x + cos(ang) * len; val ey = y + sin(ang) * len
            val light = rnd() < .7
            val alpha = ((.018 + rnd() * .032) * 1000).roundToInt() / 1000f
            val c = if (light) hi else lo
            pen.color = Color(c[0], c[1], c[2]).copy(alpha = alpha)
            pen.strokeWidth = (.7 + rnd() * .7).toFloat() + .8f
            val cx = (x + ex) / 2 - sin(ang) * bend; val cy = (y + ey) / 2 + cos(ang) * bend
            for (dx in intArrayOf(-n, 0, n)) for (dy in intArrayOf(-n, 0, n)) {
                canvas.drawPath(Path().apply { moveTo((x + dx).toFloat(), (y + dy).toFloat()); quadraticTo((cx + dx).toFloat(), (cy + dy).toFloat(), (ex + dx).toFloat(), (ey + dy).toFloat()) }, pen)
            }
        }
        return image
    }
}
