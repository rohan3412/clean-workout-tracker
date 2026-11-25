package clean.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** A colour in Oklab's polar form: lightness 0..1, chroma (0 grey, about 0.3 the most a screen shows) and hue in degrees. Perceptual, so equal steps look equal. */
class Oklch(val l: Float, val c: Float, val h: Float)

private fun toLinear(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
private fun fromLinear(v: Double): Float = (if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0).toFloat()

/** [this] as lightness, chroma and hue (the hue of a grey is meaningless, its chroma near 0). */
fun Color.toOklch(): Oklch {
    val r = toLinear(red); val g = toLinear(green); val b = toLinear(blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val lab = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return Oklch(lab.toFloat(), hypot(a, bb).toFloat(), (((atan2(bb, a) * 180 / PI) + 360) % 360).toFloat())
}

/** The colour of lightness [l], chroma [c] and hue [h] degrees; a chroma the screen cannot show is lowered, the hue and lightness kept. */
fun oklch(l: Float, c: Float, h: Float): Color {
    var chroma = c.toDouble()
    val rad = h.toDouble() * PI / 180
    while (true) {
        val a = chroma * cos(rad); val b = chroma * sin(rad)
        val l_ = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m_ = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s_ = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_
        val g = -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_
        val bl = -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_
        if (chroma <= 0.0 || (minOf(r, g, bl) >= -1e-4 && maxOf(r, g, bl) <= 1 + 1e-4)) return Color(fromLinear(r), fromLinear(g), fromLinear(bl))
        chroma -= 0.002
    }
}

/** The distance in degrees round the hue circle between two hues, 0 to 180. */
fun hueGap(a: Float, b: Float): Float = abs(((a - b + 540f) % 360f) - 180f)

/** WCAG contrast of two opaque colours, 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val hi = maxOf(a.luminance(), b.luminance()); val lo = minOf(a.luminance(), b.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
}

/** The perceptual distance of two colours (100 times the Oklab distance; about 2 is the least a person sees, 10 is plainly different). */
fun colourDistance(a: Color, b: Color): Float {
    fun lab(c: Color): DoubleArray {
        val r = toLinear(c.red); val g = toLinear(c.green); val bl = toLinear(c.blue)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)
        return doubleArrayOf(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s, 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
    }
    val x = lab(a); val y = lab(b)
    return (100 * hypot(hypot(x[0] - y[0], x[1] - y[1]), x[2] - y[2])).toFloat()
}

/** A kind of colour-vision deficiency, as the 3 x 3 matrix (Machado et al., full severity) that turns linear RGB into what that eye sees. */
enum class Deficiency(internal val m: DoubleArray) {
    DEUTERANOPIA(doubleArrayOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)),
    PROTANOPIA(doubleArrayOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)),
    TRITANOPIA(doubleArrayOf(1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900)),
}

/** [this] as a person with [deficiency] sees it. */
fun Color.seenWith(deficiency: Deficiency): Color {
    val r = toLinear(red); val g = toLinear(green); val b = toLinear(blue)
    val m = deficiency.m
    return Color(fromLinear(m[0] * r + m[1] * g + m[2] * b), fromLinear(m[3] * r + m[4] * g + m[5] * b), fromLinear(m[6] * r + m[7] * g + m[8] * b))
}
