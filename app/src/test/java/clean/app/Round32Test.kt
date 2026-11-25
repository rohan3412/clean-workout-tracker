package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import clean.app.ui.theme.resolveLook
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_THEME_KEY
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 32: page backgrounds. Theme's own follows the theme and a pick sticks; Plain (Round 34's name for Default) is the old background to the pixel; every background draws in every theme in Dark and
 * Light and its pattern covers the page beyond the light; Photo falls back while locked or missing; the Settings row; the contrast floor of the frosted glass.
 * (Tests keep `glass.reduce` on: Haze cannot blur in Robolectric, so cards are in their solid form here; the frosted tint is checked by arithmetic.)
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round32Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 6) { repeat(times) { Thread.sleep(120); compose.waitForIdle() } }

    private fun launch(history: Boolean = false) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", "off"))
            for (key in listOf(LOOK_BACKGROUND_KEY, LOOK_BACKGROUND_PHOTO_KEY, LOOK_THEME_KEY, LOOK_ACCENT_KEY, "photos.lock")) repo.dao.remove(key)
            if (history) repo.dao.insertRows(history(id).map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
    }

    private fun history(id: Long): List<WorkoutRow> {
        val rows = ArrayList<WorkoutRow>()
        for (w in 0 until 4) {
            val date = LocalDate.now().minusDays(2L + (3 - w) * 4L).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for ((e, exercise) in listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)").withIndex()) {
                for (s in 1..3) rows += WorkoutRow((w + 1).toString(), date, "Push", "3600", exercise, s.toString(), decimalText(50.0 + w * 2.5 + e * 10), (8 + s % 2).toString(), "", "", "", "", "")
            }
        }
        return rows
    }

    private fun setLook(mode: String, accent: String) {
        go { model.setOption(LOOK_THEME_KEY, mode); model.setOption(LOOK_ACCENT_KEY, accent) }
        repeat(200) { if (model.look.value.dark == (mode == "dark") && model.look.value.themeName == accent) return; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun background(id: String) {
        go { model.setBackground(id) }
        repeat(200) { if (model.background.value == id) return; Thread.sleep(50); compose.waitForIdle() }
    }

    // ---- Drawing without the app ----

    private fun render(w: Int, h: Int, density: Float = 1f, block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { block() }
        return IntArray(w * h).also { image.readPixels(it, 0, 0, w, h) }
    }

    private val looks = listOf("dark", "light").flatMap { mode -> THEMES.keys.map { mode to it } }

    @Test fun defaultIsPixelIdenticalToTheOldBackground() {
        for ((mode, theme) in looks) {
            val look = resolveLook(mode, accent = theme)
            val old = render(420, 840, 2.625f) { drawRect(look.page); drawAmbient(look.ambient, 24.dp) }
            val now = render(420, 840, 2.625f) { Backdrop(DEFAULT_BACKGROUND, look, look.page).draw(this, density, 24.dp.toPx()) }
            assertTrue("$theme $mode: Default differs from the old page", old.contentEquals(now))
        }
    }

    @Test fun everyBackgroundDrawsInEveryThemeInDarkAndLight() {
        assertEquals("Round 34: ten", 10, BACKGROUNDS.size)
        for (dark in listOf(true, false)) Paper.warm(dark)
        for ((mode, theme) in looks) {
            val look = resolveLook(mode, accent = theme)
            for (spec in BACKGROUNDS) {
                val px = render(360, 760) { Backdrop(spec, look, look.page, if (spec.photo) STAND_IN_PHOTO else null).draw(this, 1f) }
                assertTrue("${spec.id} in $theme $mode drew nothing", px.any { it != look.page.toArgb() })
            }
        }
    }

    @Test fun aPatternCoversTheWholePageNotOnlyWhereTheLightFalls() {
        Paper.warm(true)
        val look = resolveLook("dark", accent = "Graphite")
        for (spec in BACKGROUNDS.filter { it.pattern != null }) {
            val bare = Background(spec.id, spec.name, "", "", lights = spec.lights, mask = spec.mask)
            val withP = render(360, 760) { Backdrop(spec, look, look.page).draw(this, 1f) }
            val without = render(360, 760) { Backdrop(bare, look, look.page).draw(this, 1f) }
            val m = spec.mask ?: BgSpot(.5f, .5f, 1f, 1f)
            var far = 0
            var differing = 0
            for (y in 0 until 760) for (x in 0 until 360) {
                val dx = (x / 360f - m.x) / m.rx
                val dy = (y / 760f - m.y) / m.ry
                if (spec.mask == null || dx * dx + dy * dy > 1.3f * 1.3f) { far++; if (withP[y * 360 + x] != without[y * 360 + x]) differing++ }
            }
            if (spec.mask != null) assertTrue("${spec.id}: some page lies beyond the light", far > 200)
            assertTrue("${spec.id}: the pattern shows beyond the light ($differing of $far)", differing > 20)
        }
    }

    @Test fun grainIsAFineNoiseAndTheVignetteDarkensTheEdges() {
        val look = resolveLook("dark", accent = "Ember")
        val studio = BACKGROUNDS.first { it.id == "studio" }
        val px = render(360, 760) { Backdrop(studio, look, look.page).draw(this, 1f) }
        val corner = px[5 * 360 + 5]
        val middle = px[300 * 360 + 180]
        val distinct = (0 until 400).map { px[400 + it] }.toSet().size
        assertTrue("noise varies along a row ($distinct values)", distinct > 3)
        assertTrue("the corner is not brighter than the middle", android.graphics.Color.red(corner) <= android.graphics.Color.red(middle) + 6)
    }

    // ---- Photo ----

    @Test fun photoFallsBackToDefaultWhileShutOrMissingAndNotWhileLoading() {
        assertTrue(photoFallsBack(shut = true, loaded = true, bitmap = Any()))
        assertTrue(photoFallsBack(shut = false, loaded = true, bitmap = null))
        assertFalse(photoFallsBack(shut = false, loaded = false, bitmap = null))
        assertFalse(photoFallsBack(shut = false, loaded = true, bitmap = Any()))
    }

    private fun shotPage(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    /** The mean difference per channel of the top 40 % of two pictures. */
    private fun topDifference(a: Bitmap, b: Bitmap): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until (a.height * .4).toInt() step 3) for (x in 0 until a.width step 3) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            sum += kotlin.math.abs(android.graphics.Color.red(p) - android.graphics.Color.red(q)) + kotlin.math.abs(android.graphics.Color.green(p) - android.graphics.Color.green(q)) + kotlin.math.abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(q))
            n += 3
        }
        return sum / n
    }

    private fun makeMoment(): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val at = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0)
        val name = clean.core.momentOf(at, "%08x".format(1)).name
        val file = MomentFiles.file(context, model.activeId.value!!, name).also { it.parentFile!!.mkdirs() }
        val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(0xFF3B8A5B.toInt())
        val paint = android.graphics.Paint().apply { color = 0xFFF0C040.toInt() }
        canvas.drawCircle(150f, 100f, 90f, paint)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        go { model.refreshMoments() }
        compose.waitUntil(10_000) { model.moments.value.isNotEmpty() }
        return name
    }

    @Test fun aMissingPhotoShowsDefaultAndALockedOneDoesToo() {
        launch(history = true)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        setLook("dark", "Graphite")
        background("plain"); pause()
        val plain = shotPage()
        go { model.setBackgroundPhoto("m:gone.jpg") }; pause(10)
        assertEquals("photo", model.background.value)
        assertTrue("a missing photo shows Default (${topDifference(plain, shotPage())})", topDifference(plain, shotPage()) < 0.5)
        val name = makeMoment()
        go { model.setBackgroundPhoto("m:$name") }; pause(20)
        assertTrue("the photo shows when it is there", topDifference(plain, shotPage()) > 3.0)
        val phone = model.platform as AndroidPlatform
        phone.testHasLock = true
        phone.testLock = { _, cb -> cb(false) }
        go { model.setPhotoLock(true) }; compose.waitUntil(5_000) { model.photoLockOn }; pause(10)
        assertTrue("shut photos show Default (${topDifference(plain, shotPage())})", topDifference(plain, shotPage()) < 0.5)
    }

    // ---- Theme's own ----

    @Test fun themesOwnFollowsTheThemeAndAPickSticks() {
        launch()
        assertEquals("a fresh install follows the theme", BACKGROUND_THEME, model.background.value)
        for ((theme, pair) in THEME_BACKGROUND) {
            setLook("dark", theme)
            assertEquals("$theme's own", pair.first, resolveBackground(model.background.value, model.look.value.themeName).id)
        }
        assertEquals(setOf("plain", "plus", "pools", "paper", "corner", "topo", "dots", "grid", "studio"), THEME_BACKGROUND.values.map { it.first }.toSet())
        setLook("dark", "Ember")
        background("dots")
        for (theme in listOf("Green", "Rose", "Graphite")) {
            setLook("dark", theme)
            assertEquals("a pick stays across a theme change ($theme)", "dots", resolveBackground(model.background.value, model.look.value.themeName).id)
        }
        background(BACKGROUND_THEME)
        setLook("dark", "Green")
        assertEquals("topo", resolveBackground(model.background.value, model.look.value.themeName).id)
        assertEquals("an unknown id follows the theme", "topo", resolveBackground("nothing", "Green").id)
    }

    @Test fun theBackgroundIsKeptInKv() {
        launch()
        background("dots")
        assertEquals("dots", model.options.value[LOOK_BACKGROUND_KEY])
    }

    // ---- Settings ----

    private fun openAppearance() {
        go { model.stack.clear(); model.settingsPage = PAGE_LOOK; model.open(Screen.Settings) }; settle()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("ap-black"); settle(300)
    }

    @Test fun theSettingsRowShowsThemesOwnFirstAndAppliesATapAtOnce() {
        launch()
        setLook("dark", "Cobalt")
        openAppearance()
        compose.onNode(hasTestTag("bg:theme")).assertExists()
        compose.onNode(hasTestTag("bg:plain")).assertExists()
        assertTrue("Background is found by search", searchSettings("background").any { it.key == "ap-black" })
        compose.onNode(hasTestTag("bg:pools")).performClick(); settle(300)
        compose.waitUntil(5_000) { model.background.value == "pools" }
        compose.onNode(hasTestTag("bg:theme")).performClick(); settle(300); pause(3)
        compose.waitUntil(5_000) { model.background.value == BACKGROUND_THEME }
        val name = makeMoment()
        compose.onNode(hasTestTag("bg-row")).performScrollToKey("photo"); settle(300) // Round 34: Photo is the last of ten tiles now
        compose.onNode(hasTestTag("bg:photo")).performClick(); settle(400)
        compose.onNode(hasTestTag("bg:photo-choice"), useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Snapshot"), useUnmergedTree = true).performClick(); settle(400)
        compose.waitUntil(5_000) { model.background.value == "photo" && model.backgroundPhoto.value == "m:$name" }
    }

    // ---- The contrast floor of frosted glass ----

    @Test fun textOnFrostedGlassKeepsFourPointFiveAgainstTheWorstOfEveryBackground() {
        val table = StringBuilder("background theme mode tint contrast\n")
        for (pure in listOf(false, true)) for ((mode, theme) in looks) {
            if (pure && mode == "light") continue
            val look = resolveLook(mode, pure, theme)
            for (spec in BACKGROUNDS) {
                val tint = glassTint(spec, look, look.page)
                val worst = frostContrast(spec, look, look.page, tint)
                table.append("${spec.id} $theme $mode${if (pure) " black" else ""} ${"%.2f".format(tint)} ${"%.2f".format(worst)}\n")
                assertTrue("${spec.id} $theme $mode: ${"%.2f".format(worst)} at tint $tint", worst >= FROST_CONTRAST)
                assertTrue("still frosted: tint $tint", tint <= FROST_MAX + 0.001f)
            }
        }
        File("build/screens/default").apply { mkdirs() }.resolve("round32-contrast.txt").writeText(table.toString())
    }

    @Test fun aRealPhotoNeedsOnlyAsMuchTintAsItsOwnBrightestPartAsks() {
        for ((mode, theme) in looks) {
            val look = resolveLook(mode, accent = theme)
            val spec = BACKGROUNDS.first { it.photo }
            val dim = ImageBitmap(8, 8).also { img -> androidx.compose.ui.graphics.Canvas(img).drawRect(0f, 0f, 8f, 8f, androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color(0xFF5A5A5A) }) }
            val any = glassTint(spec, look, look.page)
            val mine = glassTint(spec, look, look.page, dim)
            assertTrue("$theme $mode: a mid-grey photo ($mine) is no more tinted than any photo ($any)", mine <= any + 0.001f)
            assertTrue(frostContrast(spec, look, look.page, mine, dim) >= FROST_CONTRAST)
        }
    }

    @Test fun frostedGlassOverAPlainPageIsTheCardsOwnColour() {
        for ((mode, theme) in looks) {
            val look = resolveLook(mode, accent = theme)
            for (fill in listOf(look.card, look.tile)) for (t in listOf(.6f, .8f)) {
                val glass = frostTone(fill, look.page, t).copy(alpha = t).compositeOver(look.page)
                val within = if (mode == "dark") 1.5f / 255f else 0.04f
                assertTrue("$theme $mode", kotlin.math.abs(glass.red - fill.red) <= within && kotlin.math.abs(glass.green - fill.green) <= within && kotlin.math.abs(glass.blue - fill.blue) <= within)
            }
        }
    }

    // ---- The tour ----

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun shot(name: String) { pause(3); save(shotPage(), name) }
    private fun attempt(name: String, step: () -> Unit) {
        try { step() } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("$name.failed.txt").writeText(e.toString()) }
    }

    // The tour of the fourteen backgrounds is gone with them: Round34Test's tour draws the ten (round34-*.png).
}
