package clean.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import clean.app.platform.LocalPlatform
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.Look
import clean.app.ui.theme.contrastRatio
import clean.app.ui.theme.orange
import dev.chrisbanes.haze.HazeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.withContext

/**
 * The page's background. Round 32 built the engine (fourteen, from `artifacts/round5/backgrounds-studio.html`); Round 34 replaced the set with the ten of
 * `artifacts/round6/themes-proposal.md` (its `BGS`, `P` and `background()`), each a light, an optional pattern and optional extras designed together, so frosted
 * cards have something to show. Kept in kv `look.background`: `theme` ("Theme's own", the default: [THEME_BACKGROUND]) or a background's id; the chosen photo in
 * `look.background.photo`. Lights use the accent only.
 */
const val LOOK_BACKGROUND_KEY = "look.background"
const val LOOK_BACKGROUND_PHOTO_KEY = "look.background.photo"
const val BACKGROUND_THEME = "theme"

/**
 * A light: an ellipse at (x, y), as fractions of the page, [rx] and [ry] wide and tall, [a] strong at its centre, in the accent. [eased] falls off over five steps
 * (a, .72 a at 22 %, .42 a at 45 %, .16 a at 70 %, none at the edge) so that no ring shows in a wide glow.
 */
class BgLight(val x: Float, val y: Float, val rx: Float, val ry: Float, val a: Float, val eased: Boolean = false)

/** Where a pattern is drawn stronger: the radial mask of the mockup, an ellipse at (x, y) with radii [rx], [ry] as fractions of the page. */
class BgSpot(val x: Float, val y: Float, val rx: Float, val ry: Float)

/** [coverage] is the share of the page a pattern's strokes cover (more near a crossing, rounded up): what is left of a line once the glass has blurred it with its surroundings. */
enum class BgPattern(val coverage: Float) {
    Dots(.03f), Grid(.12f), Plus(.04f), Contours(.06f), Paper(1f),
}

/**
 * One background. A pattern covers the whole page at [base] and, where [mask] says the light falls, at [k] (so it is never visible only in the light).
 */
class Background(
    val id: String, val name: String, val note: String, val why: String,
    val lights: List<BgLight> = emptyList(), val pattern: BgPattern? = null, val mask: BgSpot? = null, val base: Float = 1f, val k: Float = 1f,
    val grain: Boolean = false, val photo: Boolean = false, val plain: Boolean = false,
) {
    private val cache = ArrayList<PatternGeometry>()

    /** The pattern's paths for a page of [w] by [h] design units, built once per size (none for Paper, which is a texture). */
    internal fun geometry(w: Float, h: Float): PatternGeometry {
        cache.firstOrNull { it.w == w && it.h == h }?.let { return it }
        val made = PatternGeometry(w, h, pattern?.let { buildParts(it, w, h) } ?: emptyList())
        if (cache.size >= 3) cache.removeAt(0)
        cache += made
        return made
    }
}

internal class PatternPart(val path: Path, val factor: Float, val width: Float)
internal class PatternGeometry(val w: Float, val h: Float, val parts: List<PatternPart>)

private val TOP = BgLight(.5f, -.05f, .75f, .42f, .16f)

/** The ten, in the order of the strip. Plain draws the app's own soft top glow (what Clean had before), not [TOP]. */
val BACKGROUNDS: List<Background> = listOf(
    Background("plain", "Plain", "The page and the soft top glow, nothing else.",
        "The Quiet choice and Graphite's own: exactly what Clean shows today, so the default changes for nobody.", plain = true),
    Background("pools", "Pools", "Two soft pools of the accent: the top left and the right edge. No pattern.",
        "Colour as faint light. The cards pick up a different tint as they scroll past each pool, which is what makes the frost visible. Crimson's and Lagoon's own: red light, and light on water.",
        lights = listOf(BgLight(.08f, .04f, .78f, .38f, .19f), BgLight(1.02f, .56f, .62f, .3f, .11f))),
    Background("corner", "Corner", "One wide glow from the top-right corner, eased over five steps so no ring shows.",
        "One lamp off to the side, away from where titles start, so the left of every page stays the calmest. Saffron's and Orchid's own.",
        lights = listOf(BgLight(1.05f, -.04f, 1.15f, .62f, .2f, eased = true))),
    Background("dots", "Dots", "A dot grid, 18 dp apart, over the whole page, brighter under the top glow.",
        "Its dots keep the rhythm of the Activity calendar's squares, so it feels part of the app. Teal's and Iris's own.",
        lights = listOf(TOP), pattern = BgPattern.Dots, mask = BgSpot(.5f, -.05f, .75f, .42f), base = 1f, k = 2.2f),
    Background("grid", "Grid", "A fine square grid of hairlines, 20 dp, over the whole page, brighter under the top light.",
        "The old Blueprint made finer and fainter: its cells are smaller than any row and its lines weaker than a row divider, so it reads as a tone behind a list. Cobalt's own: blue on a blueprint.",
        lights = listOf(TOP), pattern = BgPattern.Grid, mask = BgSpot(.5f, -.1f, 1f, .75f), base = 1f, k = 2.6f),
    Background("plus", "Plus", "Small plus marks every 28 dp, brighter in a light from the top-left corner.",
        "Registration marks: precise without being a full grid. Slate's own.",
        lights = listOf(BgLight(0f, 0f, .9f, .45f, .2f)), pattern = BgPattern.Plus, mask = BgSpot(0f, 0f, .9f, .5f), base = 1f, k = 2.2f),
    Background("topo", "Topo", "Contour lines over the whole page, stronger in a pool of light at the top left.",
        "The one wave, the better of the old two. Contours are irregular, so they read as land rather than as a repeating ripple, and they are the one organic shape among the patterns. Only Green has it as its own: green land on a map.",
        lights = listOf(BgLight(.1f, .05f, .78f, .4f, .17f), BgLight(1f, .6f, .55f, .26f, .07f)), pattern = BgPattern.Contours, mask = BgSpot(.1f, .05f, .95f, .55f), base = .8f, k = 1.9f),
    Background("paper", "Paper", "A paper surface that does not repeat, stronger under a light from the top left.",
        "Two cached tiles of different seeds, one at 1.373 x the other's scale and offset, so the seams and periods never line up across a page. Ember's own: deep orange light on paper reads like a lamp over a page, warmer and calmer than chalk dust, and it keeps the orange as light rather than as a pattern.",
        lights = listOf(BgLight(.2f, 0f, .9f, .5f, .17f)), pattern = BgPattern.Paper, mask = BgSpot(.2f, 0f, 1.05f, .65f), base = 1f, k = 1.8f),
    Background("studio", "Studio", "One light from the top, a fine film grain over the whole page, and slightly darker edges.",
        "The grain you liked, unchanged. Grain shows frosted glass better than any shape: the blur smooths it away inside the card and leaves it sharp around it. Rose's own: a soft pink under film grain, like a photograph.",
        lights = listOf(BgLight(TOP.x, TOP.y, TOP.rx, TOP.ry, .2f)), grain = true),
    Background("photo", "Photo", "A snapshot or progress photo you choose, blurred and darkened.",
        "The personal one, unchanged: blurred until no face or detail is left, under a ground scrim, with no pattern on top.", photo = true),
)

/** "Theme's own": each theme's background, and why that pair (the proposal's table). */
val THEME_BACKGROUND: Map<String, Pair<String, String>> = linkedMapOf(
    "Graphite" to ("plain" to "The default, unchanged; every hue on screen carries a meaning."),
    "Slate" to ("plus" to "The one neutral besides Graphite: registration marks on a steel blue-grey."),
    "Crimson" to ("pools" to "Red light as two soft pools that tint the frost as cards pass."),
    "Ember" to ("paper" to "A deep orange glow on paper reads as a lamp over a page: warm and tactile, and the orange stays as light rather than a pattern."),
    "Saffron" to ("corner" to "One golden lamp off to the side, away from where titles start."),
    "Green" to ("topo" to "Green with contour lines reads as land on a map: the one wave."),
    "Teal" to ("dots" to "Dots every 18 dp, the calendar's rhythm, in a cool blue-green."),
    "Lagoon" to ("pools" to "Light on water: two pools of cyan."),
    "Cobalt" to ("grid" to "Blue on a fine grid looks like a blueprint: precise."),
    "Iris" to ("dots" to "Soft violet dots in the calendar's rhythm."),
    "Orchid" to ("corner" to "One magenta lamp from the top-right corner."),
    "Rose" to ("studio" to "A soft pink under film grain, like a photograph."),
)

val DEFAULT_BACKGROUND: Background = BACKGROUNDS.first()

/** The background a stored choice stands for in [theme]: `theme` (or anything unknown) is the theme's own. */
fun resolveBackground(setting: String?, theme: String): Background {
    val id = if (setting == null || setting == BACKGROUND_THEME) THEME_BACKGROUND[theme]?.first else setting
    return BACKGROUNDS.firstOrNull { it.id == id } ?: BACKGROUNDS.firstOrNull { it.id == THEME_BACKGROUND[theme]?.first } ?: DEFAULT_BACKGROUND
}

// ---- Patterns: the mockup's `P`, as paths in design units (1 unit = 1 dp on the page) ----

private fun buildParts(p: BgPattern, w: Float, h: Float): List<PatternPart> {
    fun lines(f: Path.() -> Unit) = Path().apply(f)
    fun Path.line(x1: Float, y1: Float, x2: Float, y2: Float) { moveTo(x1, y1); lineTo(x2, y2) }
    fun Path.ring(cx: Float, cy: Float, r: Float) = addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    fun Path.poly(pts: List<Offset>) { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
    return when (p) {
        BgPattern.Dots -> listOf(PatternPart(lines { var x = 8f; while (x < w) { var y = 8f; while (y < h) { ring(x, y, 1.3f); y += 18f }; x += 18f } }, .22f, 0f))
        BgPattern.Grid -> listOf(PatternPart(lines { var x = 0f; while (x <= w) { line(x + .5f, 0f, x + .5f, h); x += 20f }; var y = 0f; while (y < h) { line(0f, y + .5f, w, y + .5f); y += 20f } }, .045f, .6f))
        BgPattern.Plus -> listOf(PatternPart(lines { var x = 14f; while (x < w) { var y = 14f; while (y < h) { line(x - 3, y, x + 3, y); line(x, y - 3, x, y + 3); y += 28f }; x += 28f } }, .18f, 1.2f))
        BgPattern.Contours -> {
            val strong = Path(); val soft = Path()
            for (i in 0 until 26) {
                val y0 = -40f + i * 34f
                val pts = ArrayList<Offset>(); var x = 0f
                while (x <= w + 40f) { pts += Offset(x, y0 + 22f * sin(x / 70f + i * .55f) + 12f * sin(x / 31f + i * 1.3f)); x += 20f }
                (if (i % 4 == 0) strong else soft).poly(pts)
            }
            listOf(PatternPart(soft, .09f, 1.1f), PatternPart(strong, .16f, 1.1f))
        }
        BgPattern.Paper -> emptyList()
    }
}

// ---- Grain: a small noise bitmap made once and tiled ----

private object Grain {
    private fun tile(low: Int, high: Int): ImageBitmap {
        val side = 128
        val image = ImageBitmap(side, side)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        var seed = 12345L
        fun next(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return ((seed shr 8) and 0xFFFF).toFloat() / 65535f }
        val levels = 16
        val buckets = Array(levels) { ArrayList<Offset>() }
        for (y in 0 until side) for (x in 0 until side) {
            val v = (next() + next()) / 2f
            buckets[(v * (levels - 1) + .5f).toInt().coerceIn(0, levels - 1)] += Offset(x + .5f, y + .5f)
        }
        val paint = Paint().apply { strokeWidth = 1f; strokeCap = StrokeCap.Square; isAntiAlias = false }
        for (l in 0 until levels) {
            val grey = low + (high - low) * l / (levels - 1)
            paint.color = Color(grey, grey, grey)
            canvas.drawPoints(PointMode.Points, buckets[l], paint)
        }
        return image
    }
    /** Grey noise round mid-grey, for Overlay over a dark page. */
    val dark: ShaderBrush by lazy { ShaderBrush(ImageShader(tile(58, 198), TileMode.Repeated, TileMode.Repeated)) }
    /** Light-grey noise, for Multiply over a light page. */
    val light: ShaderBrush by lazy { ShaderBrush(ImageShader(tile(200, 255), TileMode.Repeated, TileMode.Repeated)) }
}

/** A stand-in picture for the Photo preview when no photo is chosen: soft colour shapes, blurred as a photo is. Made once. */
val STAND_IN_PHOTO: ImageBitmap by lazy {
    val s = 0.2f
    val image = ImageBitmap(72, 152)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val paint = Paint()
    paint.color = Color(0xFF1F1C1A); canvas.drawRect(0f, 0f, 72f, 152f, paint)
    for ((c, r) in listOf(
        0xFF6B4A2F to floatArrayOf(-40f, -20f, 260f, 300f), 0xFF2C3A4F to floatArrayOf(180f, 40f, 240f, 280f), 0xFFA3714A to floatArrayOf(40f, 260f, 220f, 180f),
        0xFF1F2A24 to floatArrayOf(200f, 380f, 220f, 320f), 0xFF7D5B44 to floatArrayOf(-60f, 520f, 260f, 260f), 0xFF3B2F4A to floatArrayOf(160f, 640f, 240f, 200f),
    )) { paint.color = Color(c); canvas.drawOval(Rect(r[0] * s, r[1] * s, (r[0] + r[2]) * s, (r[1] + r[3]) * s), paint) }
    softBlurred(image)
}

// ---- Drawing ----

/**
 * A background ready to draw: the [spec] in [look] over [ground] (the page colour, which a running workout tints), with [photo] (blurred) for Photo.
 * [draw] works in design units: [unit] pixels to one unit, so the page draws at 1 dp to a unit and a small preview at a fraction. Its paths are built once per size
 * ([Background.geometry]) and the whole thing sits in one cached layer ([Modifier.drawBackdrop]), so nothing is redrawn while a page scrolls.
 */
class Backdrop(val spec: Background, val look: Look, val ground: Color, val photo: ImageBitmap? = null) {
    /** Everything but Default is cached as a texture: it has blend modes and masks that must not run again each frame. */
    val layered: Boolean get() = !spec.plain

    fun draw(scope: DrawScope, unit: Float, top: Float = 0f) = with(scope) {
        drawRect(ground)
        if (spec.photo) { drawPhoto(); return@with }
        if (spec.plain) { drawAmbientAt(look.ambient, top, unit); return@with }
        val dark = look.dark
        val w = size.width / unit
        val h = size.height / unit
        val lightScale = if (dark) 1f else .7f
        val ink = look.orange
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            for (l in spec.lights) {
                val a = (l.a * lightScale).coerceIn(0f, 1f)
                val centre = Offset(l.x * w, l.y * h)
                val brush = if (l.eased) Brush.radialGradient(
                    0f to ink.copy(alpha = a), .22f to ink.copy(alpha = a * .72f), .45f to ink.copy(alpha = a * .42f), .7f to ink.copy(alpha = a * .16f), 1f to ink.copy(alpha = 0f),
                    center = centre, radius = l.ry * h,
                ) else Brush.radialGradient(listOf(ink.copy(alpha = a), ink.copy(alpha = 0f)), centre, l.ry * h)
                withTransform({ scale(l.rx * w / (l.ry * h), 1f, pivot = centre) }) { drawCircle(brush, l.ry * h, centre) }
            }
            if (spec.pattern != null) {
                val geo = spec.geometry(w, h)
                val tone = if (dark) 1f else .9f
                fun parts(strength: Float) { if (spec.pattern == BgPattern.Paper) { drawPaper(strength, dark, w, h); return }; for (p in geo.parts) drawPath(p.path, ink.copy(alpha = (p.factor * strength * tone).coerceIn(0f, 1f)), style = if (p.width == 0f) Fill else Stroke(p.width)) }
                parts(spec.base)
                val m = spec.mask
                if (m != null && spec.k > spec.base) {
                    val centre = Offset(m.x * w, m.y * h)
                    val sx = m.rx * w / (m.ry * h)
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
                        parts(spec.k - spec.base)
                        withTransform({ scale(sx, 1f, pivot = centre) }) {
                            val brush = Brush.radialGradient(0f to Color.Black, .55f to Color.Black.copy(alpha = .45f), 1f to Color.Transparent, center = centre, radius = m.ry * h)
                            drawRect(brush, Offset(centre.x - centre.x / sx, 0f), Size(w / sx, h), blendMode = BlendMode.DstIn)
                        }
                        canvas.restore()
                    }
                }
            }
        }
        if (spec.grain) {
            drawRect(if (dark) Grain.dark else Grain.light, alpha = if (dark) .55f else .35f, blendMode = if (dark) BlendMode.Overlay else BlendMode.Multiply)
            // The vignette: an ellipse at 50 % 40 % reaching the farthest corner, clear to 55 % of the way and then darker.
            val centre = Offset(size.width * .5f, size.height * .4f)
            val rx = max(centre.x, size.width - centre.x) * sqrt(2f)
            val ry = max(centre.y, size.height - centre.y) * sqrt(2f)
            val sx = rx / ry
            withTransform({ scale(sx, 1f, pivot = centre) }) {
                val edge = Color.Black.copy(alpha = if (dark) .55f else .08f)
                drawRect(Brush.radialGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to edge, center = centre, radius = ry), Offset(centre.x - centre.x / sx, 0f), Size(size.width / sx, size.height))
            }
        }
    }

    /**
     * Paper: layer A (seed 1) at 512 design units and layer B (seed 2) at 703 offset by 137 x 61, at the opacities of the proposal (A .45 / .60, B .30 / .42 in Dark / Light),
     * each times [strength]. Nothing is drawn until the tiles have been made ([Paper.warm]; a draw never stalls to make them).
     */
    private fun DrawScope.drawPaper(strength: Float, dark: Boolean, w: Float, h: Float) {
        if (!Paper.ready(dark)) return
        drawRect(Paper.brush(1, dark), Offset.Zero, Size(w, h), alpha = (strength * if (dark) .45f else .6f).coerceIn(0f, 1f))
        val s = Paper.SCALE_B
        withTransform({ translate(Paper.OFFSET_B_X, Paper.OFFSET_B_Y); scale(s, s, Offset.Zero) }) {
            drawRect(Paper.brush(2, dark), Offset(-Paper.OFFSET_B_X / s, -Paper.OFFSET_B_Y / s), Size(w / s, h / s), alpha = (strength * if (dark) .3f else .42f).coerceIn(0f, 1f))
        }
    }

    /** The blurred photo, cropped to fill, a little more colourful and darkened by the ground (0.45 in Dark, 0.6 in Light); the ground alone until it is ready. */
    private fun DrawScope.drawPhoto() {
        val image = photo
        if (image != null) {
            val s = max(size.width / image.width, size.height / image.height)
            val dw = (image.width * s).toInt() + 1
            val dh = (image.height * s).toInt() + 1
            drawImage(
                image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(((size.width - dw) / 2f).toInt(), ((size.height - dh) / 2f).toInt()), IntSize(dw, dh),
                filterQuality = FilterQuality.High, colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.2f) }),
            )
            drawRect(ground.copy(alpha = if (look.dark) .45f else .6f))
        }
    }
}

/**
 * The background of a page that opens over the tabs (Round 34, I-145): the ground and the same [Backdrop] the tabs draw, in the same place (below the status bar), so a
 * card or row that grows into a page over a patterned background shows the pattern, not a flat patch, and the last frame of Back is rest. The page registers it from
 * inside the running-workout tint ([RegisterPageBackdrop]); [fallback] is the ground until then.
 */
class PageBackdrop(private val fallback: Color) {
    var backdrop by androidx.compose.runtime.mutableStateOf<Backdrop?>(null)
    /** The size the page's backdrop was last drawn at (the whole screen), so a bar's fade can draw the same backdrop in its own coordinates. */
    var width = 0f
    var height = 0f

    /** The whole screen, status bar included (Round 35): the same backdrop the tabs draw, from the top of the screen. */
    fun draw(scope: DrawScope) = with(scope) {
        width = size.width; height = size.height
        val b = backdrop
        if (b == null) { drawRect(fallback); return@with }
        b.draw(this, density)
    }

    /**
     * A band [h] tall at [y] (in a scope whose origin is the page's top-left) that fades from the page's backdrop to clear downward: what a bar's fade paints below
     * itself, so the pattern runs on unbroken. Without a backdrop yet it is the fallback ground.
     */
    fun drawFade(scope: DrawScope, y: Float, h: Float) = with(scope) {
        val strip = Rect(0f, y, size.width, y + h)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(strip, Paint())
            val b = backdrop
            if (b == null) drawRect(fallback) else inset(0f, 0f, size.width - width, size.height - height) { b.draw(this, density) }
            drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = y, endY = y + h), Offset(0f, y), strip.size, blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    }
}

/** Gives [holder] the backdrop of the current choice in the tint this page is drawn in. */
@Composable
fun RegisterPageBackdrop(model: AppModel, holder: PageBackdrop) {
    val backdrop = rememberBackdrop(model, androidx.compose.material3.MaterialTheme.colorScheme.background)
    androidx.compose.runtime.SideEffect { holder.backdrop = backdrop }
}

/** Draws [backdrop] behind this at 1 dp to a design unit, in a layer that is kept until it changes. Put it on something that starts at the top of the screen (see [fullBleedTop]). */
fun Modifier.drawBackdrop(backdrop: Backdrop): Modifier =
    graphicsLayer { compositingStrategy = if (backdrop.layered) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawBehind { backdrop.draw(this, density) }

/**
 * Round 35: fills its parent and reaches [top] (the status bar's height) above it, to the top of the screen, so the backdrop drawn in it runs up under the transparent
 * status bar. The tabs' content sits below the status bar in a padded box; this is the one thing in it that does not.
 */
fun Modifier.fullBleedTop(top: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, c ->
    val t = top.roundToPx()
    val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth, c.maxHeight + t))
    layout(c.maxWidth, c.maxHeight) { p.place(0, -t) }
}

/** [drawBackdrop] for a preview: [width] design units across whatever size it is given (360, as the page is designed). */
fun Modifier.drawBackdropScaled(backdrop: Backdrop): Modifier =
    graphicsLayer { compositingStrategy = if (backdrop.layered) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawBehind { backdrop.draw(this, size.width / 360f) }

/**
 * The blurred photo of the Photo background: [name] is `p:` and a progress photo's file name or `m:` and a snapshot's. Null while it loads (`loaded` false), when it
 * is gone, or while the photos are shut, so the page never shows what a lock holds back.
 */
@Composable
internal fun rememberBackgroundPhoto(model: AppModel, pick: String?, shut: Boolean): Pair<Boolean, ImageBitmap?> {
    val platform = LocalPlatform.current
    val id = model.activeId.collectAsState().value
    val count = model.photos.collectAsState().value.size + model.moments.collectAsState().value.size
    val state = produceState(false to (null as ImageBitmap?), pick, id, shut, count) {
        if (shut || pick == null || id == null) { value = true to null; return@produceState }
        val file = if (pick.startsWith("m:")) platform.momentPath(id, pick.drop(2)) else platform.photoPath(id, pick.drop(2))
        val image = withContext(ioDispatcher) { runCatching { platform.decodeStored(file, BLUR_DECODE, light = true)?.let { softBlurred(it) } }.getOrNull() }
        value = true to image
    }
    return state.value
}

/** Photo shows Default instead while the photos are shut (nothing leaks before an unlock) or once the picked one has loaded as missing. */
fun photoFallsBack(shut: Boolean, loaded: Boolean, bitmap: Any?): Boolean = shut || (loaded && bitmap == null)

/** The page's background for the stored choice in the current look: Photo falls back to Default while the photos are shut, or when the picked one is gone. */
@Composable
fun rememberBackdrop(model: AppModel, ground: Color): Backdrop {
    val look = LocalLook.current
    val setting by model.background.collectAsState()
    val pick by model.backgroundPhoto.collectAsState()
    var spec = resolveBackground(setting, look.themeName)
    var image: ImageBitmap? = null
    if (spec.photo) {
        val shut = model.photosShut()
        val (loaded, bitmap) = rememberBackgroundPhoto(model, pick, shut)
        if (photoFallsBack(shut, loaded, bitmap)) spec = DEFAULT_BACKGROUND else image = bitmap
    }
    // Paper's tiles are made off the main thread, once per mode, when it is first wanted; it draws nothing until they are there.
    if (spec.pattern == BgPattern.Paper) androidx.compose.runtime.LaunchedEffect(look.dark) { withContext(ioDispatcher) { Paper.warm(look.dark) } }
    return remember(spec, look, ground, image) { Backdrop(spec, look, ground, image) }
}

// ---- Frosted cards ----

/** What a frosted card needs: the blur source of the page behind it (the background alone) and how much of its own tone covers that blur. */
class Frost(val haze: HazeState, val tint: Float)

/** Set inside the four tabs only (the pages that open over them keep opaque cards). */
val LocalFrost = staticCompositionLocalOf<Frost?> { null }

/** The colour frosted glass is painted in so that, at [tint], over a page that is just [ground], it is the card's [fill] again: a card on a plain page looks as it always did. */
fun frostTone(fill: Color, ground: Color, tint: Float): Color {
    fun c(f: Float, g: Float) = ((f - (1f - tint) * g) / tint).coerceIn(0f, 1f)
    return Color(c(fill.red, ground.red), c(fill.green, ground.green), c(fill.blue, ground.blue), 1f)
}

/**
 * The most extreme colours the page can put behind a card, unblurred and at their strongest (so a bound, not an average): the ground with every light at full
 * strength, and with the pattern's strongest line over that; for Photo a white and a black photo under the scrim. The brightest matters in Dark, the darkest in Light.
 */
fun worstBehind(spec: Background, look: Look, ground: Color, photo: ImageBitmap? = null): List<Color> {
    if (spec.photo) {
        val scrim = if (look.dark) .45f else .6f
        return photoExtremes(photo).map { ground.copy(alpha = scrim).compositeOver(it) }
    }
    if (spec.plain) return listOf(look.ambient.compositeOver(ground))
    val lightScale = if (look.dark) 1f else .7f
    var lit = ground
    for (l in spec.lights) lit = look.orange.copy(alpha = l.a * lightScale).compositeOver(lit)
    if (spec.pattern == null) return listOf(lit)
    if (spec.pattern == BgPattern.Paper) {
        // The tile's colours at their likely strongest over the lit ground, both layers, at the mask's strength: white in Dark; the warm light and the shade in Light.
        val dark = look.dark
        val a = (spec.k * if (dark) .45f else .6f).coerceIn(0f, 1f) * Paper.PEAK_ALPHA
        val b = (spec.k * if (dark) .3f else .42f).coerceIn(0f, 1f) * Paper.PEAK_ALPHA
        fun over(c: IntArray) = Color(c[0], c[1], c[2]).let { c0 -> c0.copy(alpha = a).compositeOver(lit).let { c0.copy(alpha = b).compositeOver(it) } }
        return listOf(lit, over(Paper.hi(dark)), over(Paper.lo(dark)))
    }
    val strongest = buildParts(spec.pattern, 360f, 760f).maxOf { it.factor } * spec.k * (if (look.dark) 1f else .9f) * spec.pattern.coverage
    return listOf(lit, look.orange.copy(alpha = strongest.coerceIn(0f, 1f)).compositeOver(lit))
}

/** The brightest and darkest colours a photo can put behind the scrim: white and black for any photo, or the extremes of this blurred one. */
private fun photoExtremes(photo: ImageBitmap?): List<Color> {
    if (photo == null) return listOf(Color.White, Color.Black)
    val px = IntArray(photo.width * photo.height)
    photo.readPixels(px, 0, 0, photo.width, photo.height)
    val colours = px.map { Color(it) }
    return listOf(colours.maxBy { it.luminance() }, colours.minBy { it.luminance() })
}

/** The fills a card or tile of this look can have, which text sits on: the card, the tile and Home's accent-tinted hero. */
fun cardFills(look: Look): List<Color> = listOf(look.card, look.tile, androidx.compose.ui.graphics.lerp(look.card, look.orange, .1f))

/** The text colours on a card, which must keep 4.5 : 1: text, quiet text and the accent as text. */
fun cardInks(look: Look): List<Color> = listOf(look.text, look.muted, look.orange)

/**
 * The weakest contrast of any ink on any fill, with frosted glass at [tint] over the worst of the background, scaled so that 4.5 means "as good as it must be": an ink
 * must keep 4.5 : 1, or 97 % of what it has on the plain card today where that is already lower (Crimson's accent in Dark is 4.4 : 1 on its card, Round 20).
 */
fun frostContrast(spec: Background, look: Look, ground: Color, tint: Float, photo: ImageBitmap? = null): Float {
    var least = Float.MAX_VALUE
    val fills = cardFills(look)
    for (behind in worstBehind(spec, look, ground, photo)) for (fill in fills) {
        val glass = frostTone(fill, ground, tint).copy(alpha = tint).compositeOver(behind)
        for (ink in cardInks(look)) {
            val need = minOf(FROST_CONTRAST, contrastRatio(ink, fill) * .97f)
            least = minOf(least, contrastRatio(ink, glass) / need * FROST_CONTRAST)
        }
    }
    return least
}

/** The least tint (the share of the card's own tone over the blur) at which text keeps [FROST_CONTRAST] against the worst of this background, never below [FROST_MIN]. */
fun glassTint(spec: Background, look: Look, ground: Color, photo: ImageBitmap? = null): Float {
    var t = FROST_MIN
    while (t < FROST_MAX && frostContrast(spec, look, ground, t, photo) < FROST_CONTRAST) t += .01f
    return t
}

const val FROST_CONTRAST = 4.5f
const val FROST_MIN = .55f
const val FROST_MAX = .985f
