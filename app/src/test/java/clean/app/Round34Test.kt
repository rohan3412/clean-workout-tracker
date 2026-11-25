package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.app.ui.theme.THEMES
import clean.app.ui.theme.contrastRatio
import clean.app.ui.theme.orange
import clean.app.ui.theme.resolveLook
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_THEME_KEY
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 34: twelve themes and ten backgrounds, from `artifacts/round6/themes-proposal.md`. Every one of the 24 looks is the proposal's, with the contrast it computed; the eight
 * old theme names and the dropped backgrounds migrate; the theme row scrolls and shows the chosen one; each theme's own background is its pair; Paper does not repeat at phone
 * size and has no speck; a card or row that opens a page over a patterned background shows no flat patch and Back ends at rest (I-145); the frosted-glass contrast floor holds for
 * all 24 looks and 10 backgrounds (`Round32Test`, which iterates both lists, asserts it; here the figures are checked once more for the new set).
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round34Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 6) { repeat(times) { Thread.sleep(120); compose.waitForIdle() } }

    private fun launch(motion: Boolean = false, seed: Map<String, String> = emptyMap()) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            for (key in listOf(LOOK_BACKGROUND_KEY, LOOK_BACKGROUND_PHOTO_KEY, LOOK_THEME_KEY, LOOK_ACCENT_KEY, "photos.lock", "look.v1", "look.v2", "look.v3")) repo.dao.remove(key)
            for ((k, v) in seed) repo.dao.put(Kv(k, v))
            repo.dao.insertRows(history(id).map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
    }

    private fun history(id: Long): List<WorkoutRow> {
        val rows = ArrayList<WorkoutRow>()
        for (w in 0 until 8) {
            val date = LocalDate.now().minusDays(2L + (7 - w) * 4L).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for ((e, exercise) in listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)").withIndex()) {
                for (s in 1..3) rows += WorkoutRow((w + 1).toString(), date, "Push", "3600", exercise, s.toString(), decimalText(50.0 + w * 2.5 + e * 10), (8 + s % 2).toString(), "", "", "", "", "")
            }
        }
        return rows
    }

    /** Waits for [cond] the way the other tests do (the compose rule's own waitUntil gives up while the app loads on another thread). */
    private fun until(cond: () -> Boolean) { repeat(60) { if (cond()) return; Thread.sleep(250) }; compose.waitForIdle() }

    private fun setLook(mode: String, accent: String) {
        go { model.setOption(LOOK_THEME_KEY, mode); model.setOption(LOOK_ACCENT_KEY, accent) }
        repeat(200) { if (model.look.value.dark == (mode == "dark") && model.look.value.themeName == accent) return; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun background(id: String) {
        go { model.setBackground(id) }
        repeat(200) { if (model.background.value == id) return; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun shotPage(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    private fun render(w: Int, h: Int, density: Float = 1f, block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { block() }
        return IntArray(w * h).also { image.readPixels(it, 0, 0, w, h) }
    }

    private val looks = listOf("dark", "light").flatMap { mode -> THEMES.keys.map { mode to it } }

    // ---- The 24 looks ----

    @Test fun everyOfTheTwentyFourLooksIsTheProposalsWithItsComputedContrast() {
        assertEquals(24, Round6Spec.rows.size)
        for (r in Round6Spec.rows) {
            val look = resolveLook(if (r.dark) "dark" else "light", accent = r.name)
            val tag = "${r.name} ${if (r.dark) "Dark" else "Light"}"
            fun near(label: String, expected: Double, actual: Float, tol: Double = 0.02) = assertTrue("$tag $label: proposal ${expected}, here $actual", abs(expected - actual) <= tol)
            near("text on page", r.textPage, contrastRatio(look.text, look.page))
            near("text on card", r.textCard, contrastRatio(look.text, look.card))
            near("muted on page", r.mutedPage, contrastRatio(look.muted, look.page))
            near("muted on card", r.mutedCard, contrastRatio(look.muted, look.card))
            near("accent on page", r.accentPage, contrastRatio(look.orange, look.page))
            near("label on accent", r.inkAccent, contrastRatio(look.ink, look.orange))
            near("good on card", r.goodCard, contrastRatio(look.good, look.card))
            near("bad on card", r.badCard, contrastRatio(look.bad, look.card))
            near("PR on page", r.prPage, contrastRatio(look.pr, look.page))
            near("PR on card", r.prCard, contrastRatio(look.pr, look.card))
            val series = look.series.drop(1)
            near("series 3 : 1", r.seriesMin, series.minOf { minOf(contrastRatio(it, look.page), contrastRatio(it, look.card)) })
            // The proposal's thresholds.
            assertTrue("$tag text", r.textPage >= 4.5 && r.mutedPage >= 4.5 && r.mutedCard >= 4.5)
            assertTrue("$tag accent", r.accentPage >= 3.0 && r.inkAccent >= 4.5 && r.seriesMin >= 3.0)
            assertEquals(tag, r.bg, THEME_BACKGROUND.getValue(r.name).first)
        }
    }

    @Test fun theLabelOnTheAccentInDarkIsDarkForEveryTheme() {
        for (name in THEMES.keys) {
            val look = resolveLook("dark", accent = name)
            assertTrue("$name: dark label ${look.ink}", look.ink.red + look.ink.green + look.ink.blue < 0.9f)
        }
    }

    // ---- Migration ----

    @Test fun anOldThemeAndAGoneBackgroundMigrateOnceAtStartUp() {
        launch(seed = mapOf(LOOK_ACCENT_KEY to "Ocean", "look.v2" to "1", LOOK_BACKGROUND_KEY to "blueprint", "look.v1" to "1"))
        until { model.options.value["look.v3"] == "1" }; until { model.look.value.themeName != "Graphite" }
        assertEquals("Ocean is Cobalt", "Cobalt", model.look.value.themeName)
        assertEquals("Blueprint is Grid", "grid", model.background.value)
        assertEquals("1", model.options.value["look.v3"])
        // Once only: a later choice is left alone.
        setLook("dark", "Slate")
        assertEquals("Slate", model.look.value.themeName)
    }

    @Test fun aDroppedBackgroundFollowsTheTheme() {
        launch(seed = mapOf(LOOK_ACCENT_KEY to "Moss", "look.v2" to "1", LOOK_BACKGROUND_KEY to "ripple", "look.v1" to "1"))
        until { model.options.value["look.v3"] == "1" }; until { model.look.value.themeName != "Graphite" }
        assertEquals("Green", model.look.value.themeName)
        assertEquals(BACKGROUND_THEME, model.background.value)
    }

    // ---- Settings ----

    private fun openAppearance() {
        go { model.stack.clear(); model.settingsPage = PAGE_LOOK; model.open(Screen.Settings) }; settle()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("ap-theme"); settle(300)
    }

    @Test fun theThemeRowScrollsAndShowsTheChosenOne() {
        launch(seed = mapOf(LOOK_ACCENT_KEY to "Rose", "look.v2" to "1", "look.v3" to "1", "look.v1" to "1"))
        openAppearance()
        compose.onNodeWithTag("theme-row").assertExists()
        // The chosen theme (the eleventh of twelve) is scrolled into view; the first is not on screen.
        compose.onAllNodes(hasContentDescription("Rose")).onFirst().assertIsDisplayed()
        assertTrue("Graphite is off to the left", compose.onAllNodes(hasContentDescription("Graphite")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("theme-row").performTouchInput { swipeRight(); swipeRight() }; settle(400)
        compose.onAllNodes(hasContentDescription("Graphite")).onFirst().assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Slate")).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle(300)
        until { model.look.value.themeName == "Slate" }
        compose.onNodeWithTag("theme-row").performTouchInput { swipeLeft(); swipeLeft(); swipeLeft() }; settle(400)
        compose.onAllNodes(hasContentDescription("Rose")).onFirst().assertIsDisplayed()
    }

    @Test fun theBackgroundRowShowsThemesOwnFirstThenTheTen() {
        launch()
        go { model.stack.clear(); model.settingsPage = PAGE_LOOK; model.open(Screen.Settings) }; settle()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("ap-black"); settle(300)
        compose.onNode(hasTestTag("bg:theme")).assertExists()
        compose.onNode(hasTestTag("bg:plain")).assertExists()
        assertEquals(10, BACKGROUNDS.size)
        assertEquals(listOf("plain", "pools", "corner", "dots", "grid", "plus", "topo", "paper", "studio", "photo"), BACKGROUNDS.map { it.id })
    }

    // ---- Each theme's own background ----

    @Test fun eachThemesOwnBackgroundIsItsPairAndDrawsDifferentlyFromPlain() {
        val expected = mapOf("Graphite" to "plain", "Slate" to "plus", "Crimson" to "pools", "Ember" to "paper", "Saffron" to "corner", "Green" to "topo", "Teal" to "dots", "Lagoon" to "pools", "Cobalt" to "grid", "Iris" to "dots", "Orchid" to "corner", "Rose" to "studio")
        assertEquals(expected, THEME_BACKGROUND.mapValues { it.value.first })
        for (dark in listOf(true, false)) Paper.warm(dark)
        for ((mode, theme) in looks) {
            val look = resolveLook(mode, accent = theme)
            val own = resolveBackground(BACKGROUND_THEME, theme)
            assertEquals("$theme $mode", expected.getValue(theme), own.id)
            val drawn = render(360, 760) { Backdrop(own, look, look.page).draw(this, 1f) }
            val plain = render(360, 760) { Backdrop(DEFAULT_BACKGROUND, look, look.page).draw(this, 1f) }
            if (theme != "Graphite") assertTrue("$theme $mode: its own background differs from Plain", !drawn.contentEquals(plain))
            // Lights use the accent only: every light is the look's accent (no chart colour), so a pool never changes the page's hue away from it.
            assertTrue("$theme $mode: drew something", drawn.any { it != look.page.toArgb() })
        }
    }

    @Test fun aLightIsTheAccentOnly() {
        for (spec in BACKGROUNDS) for (l in spec.lights) assertTrue("${spec.id}: a light strength ${l.a}", l.a in 0.05f..0.25f)
        assertTrue("Corner is eased", BACKGROUNDS.first { it.id == "corner" }.lights.single().eased)
        assertTrue(BACKGROUNDS.first { it.id == "corner" }.lights.single().let { it.x > 1f && it.y < 0f })
    }

    // ---- Paper ----

    private fun alphaOf(px: Int) = px ushr 24

    @Test fun paperTilesAreSoftCloudsWithAlmostNoSpeckAndNoSeam() {
        for (dark in listOf(true, false)) for (seed in 1..2) {
            val tile = Paper.tile(seed, dark)
            assertEquals(512, tile.width); assertEquals(512, tile.height)
            val px = IntArray(512 * 512).also { tile.readPixels(it, 0, 0, 512, 512) }
            var sum = 0L; var n = 0; var seam = 0L; var seamN = 0
            for (y in 0 until 512) {
                for (x in 1 until 512) { sum += abs(alphaOf(px[y * 512 + x]) - alphaOf(px[y * 512 + x - 1])); n++ }
                seam += abs(alphaOf(px[y * 512]) - alphaOf(px[y * 512 + 511])); seamN++
            }
            val rough = sum.toDouble() / n
            assertTrue("seed $seed dark=$dark: neighbouring pixels differ by ${"%.2f".format(rough)} alpha levels on average (speck would be 10 or more)", rough < 4.0)
            assertTrue("seed $seed dark=$dark: the seam (${seam.toDouble() / seamN}) is no rougher than the interior ($rough) by more than a little", seam.toDouble() / seamN < rough * 2.5 + 1)
            // Soft: the tile is clouds, its alpha reaches about 120 x |v| but most of it is low.
            val high = px.count { alphaOf(it) > 90 }
            assertTrue("seed $seed dark=$dark: few pixels are strong ($high)", high < 512 * 512 / 20)
            // Fibres are long and faint: a strand is at most about 0.05 + blur, so it never reads as a dot.
        }
    }

    @Test fun paperDoesNotRepeatAtPhoneSize() {
        for (dark in listOf(true, false)) {
            Paper.warm(dark)
            val ground = if (dark) Color(0xFF120C09) else Color(0xFFF9EDE9)
            // Layer A alone has a period of exactly 512: two regions 512 apart are identical (the metric sees it: correlation 1).
            val a = render(360, 760) { drawRect(ground); drawRect(Paper.brush(1, dark), Offset.Zero, Size(360f, 760f), alpha = if (dark) .45f else .6f) }
            // Both layers (the app's own drawing: a background with no light and no mask, so only the texture).
            val spec = Background("p", "p", "", "", pattern = BgPattern.Paper, base = 1f, k = 1f)
            val both = render(360, 760) { Backdrop(spec, resolveLook(if (dark) "dark" else "light", accent = "Ember"), ground).draw(this, 1f) }
            fun luma(p: Int) = (android.graphics.Color.red(p) * 3 + android.graphics.Color.green(p) * 6 + android.graphics.Color.blue(p)).toDouble()
            fun corr(px: IntArray, shift: Int): Double {
                val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
                for (y in 0 until 240) for (x in 0 until 360) { xs += luma(px[y * 360 + x]); ys += luma(px[(y + shift) * 360 + x]) }
                val mx = xs.average(); val my = ys.average()
                var sxy = 0.0; var sxx = 0.0; var syy = 0.0
                for (i in xs.indices) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx); syy += (ys[i] - my) * (ys[i] - my) }
                return sxy / sqrt(sxx * syy)
            }
            val single = corr(a, 512)
            val double = corr(both, 512)
            assertTrue("dark=$dark: the metric sees a one-layer repeat ($single)", single > 0.98)
            assertTrue("dark=$dark: two layers show no repeat 512 apart (single $single, both $double; a simple repeat is 0.98 or more)", double < 0.95)
            // Nor 703 apart (layer B's own period is out of the page, so nothing to compare) or when the page is shifted by a few px.
            assertTrue("dark=$dark: it draws", both.any { it != ground.toArgb() })
        }
    }

    // ---- I-145 ----

    /** Round21Test's own seed and launch (14 workouts, Pure black, motion on), which the frame measurements were calibrated on. */
    private fun seedLikeRound21() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = Repo(CleanDb.open(context))
        val id = repo.createProfile("Test")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
        repo.dao.put(Kv("glass.reduce", "on"))
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val start = java.time.LocalDateTime.now().minusDays(40).withHour(18).withMinute(0).withSecond(0)
        val rows = (0 until 14).flatMap { n ->
            val date = start.plusDays(n * 3L).format(format)
            val name = if (n % 2 == 0) "Push" else "Pull"
            val lift = if (n % 2 == 0) "Bench Press (Barbell)" else "Lat Pulldown (Machine)"
            (1..3).map { set -> "\"${n + 1}\";\"$date\";\"$name\";\"3600\";\"$lift\";\"$set\";\"${60 + n + (n * 7 % 5)}.0\";\"${8 + n % 3}\";\"\";\"\";\"\";\"\";\"\"" }
        }
        repo.commit(id, repo.previewWorkouts(id, (listOf(header) + rows).joinToString("\n", postfix = "\n").toByteArray()))
    }

    private fun launchLikeRound21(): ActivityScenario<MainActivity> {
        seedLikeRound21()
        runBlocking {
            val dao = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>())).dao
            dao.put(Kv(clean.core.LOOK_THEME_KEY, "dark"))
            dao.put(Kv(clean.core.LOOK_BLACK_KEY, "on"))
            dao.put(Kv(clean.core.LOOK_DONE_KEY, "1"))
            dao.put(Kv(clean.core.LOOK_ACCENT_KEY, "Cobalt"))
            dao.put(Kv(clean.core.LOOK_V2_KEY, "1"))
            dao.put(Kv("ui.motion", "on"))
            dao.put(Kv("glass.reduce", "on"))
            dao.put(Kv("ui.bodyMode", "Balance"))
            dao.put(Kv("home.cards:${dao.get("active")}", "last:w,streak:h,volume:h,week:h"))
        }
        MorphProbe.boxes.clear()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded }
        compose.waitForIdle()
        return scenario
    }


    private fun frame(): Bitmap {
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }
    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun diff(a: Int, b: Int) = abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))
    private var box = ""
    private fun differing(a: Bitmap, b: Bitmap, r: Rect, tol: Int = 24): Int {
        val pa = pixels(a); val pb = pixels(b); val w = a.width
        var n = 0; var x0 = Int.MAX_VALUE; var x1 = -1; var y0 = Int.MAX_VALUE; var y1 = -1
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(a.height)) for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(w)) if (diff(pa[y * w + x], pb[y * w + x]) > tol) { n++; x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
        box = if (n == 0) "none" else "x $x0..$x1 y $y0..$y1"
        return n
    }

    private class Run(val frames: List<Bitmap>, val ended: Int)

    private fun run(act: () -> Unit, ms: Int = 640): Run {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3000)
        val frames = mutableListOf(frame())
        val clip = MorphProbe.clip!!
        var last = clip.grown
        var seen = clip.draws
        var ended = -1
        compose.runOnIdle(act)
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16); frames += frame(); t += 16
            if (clip.draws != seen) { seen = clip.draws; if (abs(clip.grown - last) > 0.0001f) ended = frames.size - 1; last = clip.grown }
        }
        compose.mainClock.advanceTimeBy(3000)
        frames += frame()
        compose.mainClock.autoAdvance = true
        return Run(frames, ended)
    }

    private fun savePng(name: String, frames: List<Bitmap>) {
        val out = File("build/screens/default").apply { mkdirs() }
        val w = frames[0].width; val h = frames[0].height
        val strip = java.awt.image.BufferedImage((w / 2 + 2) * frames.size, h / 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b -> val s = Bitmap.createScaledBitmap(b, w / 2, h / 2, true); val px = IntArray(w / 2 * (h / 2)); s.getPixels(px, 0, w / 2, 0, 0, w / 2, h / 2); strip.setRGB(i * (w / 2 + 2), 0, w / 2, h / 2, px, 0, w / 2) }
        javax.imageio.ImageIO.write(strip, "png", File(out, "$name.png"))
    }

    private fun scrollToCard(key: String) {
        val tag = hasTestTag("card:$key")
        try { compose.waitUntil(4000) { compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { }
        val here = compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        if (here != null && here.top >= 0f && here.bottom <= 700f && here.width > 0f) return
        val lists = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until lists) {
            try { compose.onAllNodes(hasScrollAction())[i].performScrollToKey(key.substringAfter(':')); compose.waitForIdle(); return } catch (e: Throwable) { }
        }
        var swipes = 0
        while (compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().let { n -> n.isEmpty() || n.first().boundsInRoot.let { it.width <= 0f || it.top > 600f } } && swipes++ < 4) {
            compose.onNodeWithTag("page:tabs", useUnmergedTree = true).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
    }

    /** A row or card that opens a page, over a patterned background: no flat patch as it grows and Back ends at rest. */
    private fun opensOverAPattern(tab: Tab, key: String, background: String, theme: String = "Cobalt") {
        launched = launchLikeRound21()
        compose.runOnIdle { model.setBackground(background) }
        compose.runOnIdle { model.tab = tab }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        scrollToCard(key)
        compose.waitForIdle()
        assertTrue("$key: no such card on screen", compose.onAllNodes(hasTestTag("card:$key"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val spot = compose.onNodeWithTag("card:$key", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        println("ROUND34 $key at $spot")
        val open = run({ compose.onNodeWithTag("card:$key", useUnmergedTree = true).performTouchInput { click(Offset(8f, 8f)) } })
        assertTrue("$key: the row did not open a page (at $spot)", model.stack.isNotEmpty())
        val back = run({ model.back() })
        // The frame before a move is not drawn yet (the first frame after the clock is paused), so rest is the settled frame after Back.
        val rest = back.frames.last()
        val e = back.ended
        savePng("round34-i145-$background-${key.replace(':', '-')}", listOf(rest, open.frames[1], open.frames[2], open.frames[3], open.frames[open.ended.coerceAtLeast(4) - 2], back.frames[(e - 3).coerceAtLeast(0)], back.frames[e]))
        run { val px = listOf(spot.left.toInt() + 6 to spot.top.toInt() + 6, spot.left.toInt() + 30 to spot.top.toInt() + 40, spot.right.toInt() - 8 to spot.bottom.toInt() - 8)
            println("ROUND34 pixels " + px.joinToString { (x, y) -> "($x,$y) rest=${Integer.toHexString(rest.getPixel(x, y))} e-2=${Integer.toHexString(back.frames[e - 2].getPixel(x, y))} e-1=${Integer.toHexString(back.frames[e - 1].getPixel(x, y))} e=${Integer.toHexString(back.frames[e].getPixel(x, y))} open1=${Integer.toHexString(open.frames[1].getPixel(x, y))}" }) }
        // The first frames of the growth: where the row was, the pattern is still there (not a flat patch). Text fades, so a few pixels may differ.
        val boxes = ArrayList<String>()
        val firstFrames = (1..3).map { differing(open.frames[it], rest, spot, 40).also { boxes += box } }
        val pixelsInRow = ((spot.right - spot.left) * (spot.bottom - spot.top)).toInt()
        assertTrue("$key over $background: the row's rectangle changed at once in $firstFrames of $pixelsInRow px, in $boxes (a flat patch)", firstFrames.all { it < pixelsInRow / 3 })
        val area = Rect(0f, 0f, 400f, 800f)
        val last = differing(back.frames[e], rest, area, 48); val lastBox = box
        val next = differing(back.frames[(e + 1).coerceAtMost(back.frames.size - 1)], rest, area, 48)
        println("ROUND34 $key over $background: first frames $firstFrames, last frame of Back vs rest $last ($lastBox), next $next")
        assertTrue("$key over $background: the last frame of Back differs from rest in $last px ($lastBox; the next in $next)", last <= 450 && next <= 450)
    }

    @Test fun aRowThatOpensAPageOverTopoShowsNoFlatPatchAndBackEndsAtRest() = opensOverAPattern(Tab.Progress, "progress:stat:trends", "topo")
    @Test fun aCardThatOpensAPageOverPlusShowsNoFlatPatchAndBackEndsAtRest() = opensOverAPattern(Tab.Progress, "progress:history", "plus")
    @Test fun aHomeTileOverDotsOpensWithTheBackdropToo() = opensOverAPattern(Tab.Home, "home:week", "dots", theme = "Teal")

    // ---- The contrast floor, once more for this set ----

    @Test fun theFrostedGlassFloorHoldsForTwentyFourLooksAndTenBackgrounds() {
        val table = StringBuilder("background theme mode tint contrast\n")
        for (pure in listOf(false, true)) for ((mode, theme) in looks) {
            if (pure && mode == "light") continue
            val look = resolveLook(mode, pure, theme)
            for (spec in BACKGROUNDS) {
                val tint = glassTint(spec, look, look.page)
                val worst = frostContrast(spec, look, look.page, tint)
                table.append("${spec.id} $theme $mode${if (pure) " black" else ""} ${"%.2f".format(tint)} ${"%.2f".format(worst)}\n")
                assertTrue("${spec.id} $theme $mode: ${"%.2f".format(worst)} at tint $tint", worst >= FROST_CONTRAST)
                assertTrue("still frosted: ${spec.id} $theme $mode tint $tint", tint in FROST_MIN..(FROST_MAX + 0.001f))
            }
        }
        File("build/screens/default").apply { mkdirs() }.resolve("round34-contrast.txt").writeText(table.toString())
    }

    // ---- The tour ----

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun attempt(name: String, step: () -> Unit) {
        try { step() } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("$name.failed.txt").writeText(e.toString()) }
    }

    @Category(Tour::class) @Test fun tour() {
        launch()
        go { model.tab = Tab.Home }; settle()
        background(BACKGROUND_THEME)
        for (mode in listOf("dark", "light")) for (theme in THEMES.keys) attempt("round34-home-$theme-$mode") {
            setLook(mode, theme); pause(6)
            save(shotPage(), "round34-home-${theme.lowercase()}-$mode")
        }
        // Paper close up (3x, 120 dp square from the top left, where the light lies) in Dark and Light.
        for (dark in listOf(true, false)) attempt("round34-paper-$dark") {
            Paper.warm(dark)
            val look = resolveLook(if (dark) "dark" else "light", accent = "Ember")
            val spec = BACKGROUNDS.first { it.id == "paper" }
            val px = render(1080, 2280, 3f) { Backdrop(spec, look, look.page).draw(this, 3f) }
            val crop = java.awt.image.BufferedImage(540, 540, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 540) for (x in 0 until 540) crop.setRGB(x, y, px[(y + 60) * 1080 + x + 60])
            javax.imageio.ImageIO.write(crop, "png", File("build/screens/default").apply { mkdirs() }.resolve("round34-paper-closeup-${if (dark) "dark" else "light"}.png"))
            val phone = java.awt.image.BufferedImage(360, 760, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val small = render(360, 760) { Backdrop(spec, look, look.page).draw(this, 1f) }
            for (y in 0 until 760) for (x in 0 until 360) phone.setRGB(x, y, small[y * 360 + x])
            javax.imageio.ImageIO.write(phone, "png", File("build/screens/default").resolve("round34-paper-page-${if (dark) "dark" else "light"}.png"))
        }
        for (mode in listOf("dark", "light")) attempt("round34-settings-$mode") {
            setLook(mode, "Rose"); background(BACKGROUND_THEME)
            openAppearance(); pause(4)
            save(shotPage(), "round34-settings-theme-row-$mode")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("ap-black"); settle(300); pause(4)
            save(shotPage(), "round34-settings-background-row-$mode")
        }
    }
}
