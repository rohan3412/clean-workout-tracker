package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.app.ui.theme.resolveLook
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_THEME_KEY
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 35: the backdrop runs up under the transparent status bar. Robolectric gives no insets, which hid Round 32's flat band, so these tests give the window a real 24 dp
 * status bar and a 48 dp navigation bar (mdpi: 24 and 48 px) and read the pixels of the band: the same as the backdrop drawn over the whole screen, never flat ground.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round35Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null
    private val status = 24
    private val nav = 48

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 6) { repeat(times) { Thread.sleep(120); compose.waitForIdle() } }

    private fun launch() {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", "off"))
            for (key in listOf(LOOK_BACKGROUND_KEY, LOOK_BACKGROUND_PHOTO_KEY, LOOK_THEME_KEY, LOOK_ACCENT_KEY, "photos.lock", "look.v1", "look.v2", "look.v3")) repo.dao.remove(key)
            repo.dao.insertRows(history(id).map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        applyInsets()
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

    /** A real status bar and navigation bar, as the phone's window gives them. */
    private fun applyInsets() {
        go {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, nav))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(window!!, insets)
        }
        settle(300); pause(3)
    }

    private fun setLook(mode: String, accent: String) {
        go { model.setOption(LOOK_THEME_KEY, mode); model.setOption(LOOK_ACCENT_KEY, accent) }
        repeat(200) { if (model.look.value.dark == (mode == "dark") && model.look.value.themeName == accent) return; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun background(id: String) {
        go { model.setBackground(id) }
        repeat(200) { if (model.background.value == id) return; Thread.sleep(50); compose.waitForIdle() }
    }

    private fun shot(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(w: Int, h: Int, block: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { block() }
        return IntArray(w * h).also { image.readPixels(it, 0, 0, w, h) }
    }

    private fun diff(a: Int, b: Int) = abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))

    /** The share of the pixels in rows [from] until [to] that equal [reference]'s within [tol]. */
    private fun agreement(b: Bitmap, reference: IntArray, from: Int, to: Int, tol: Int = 12): Double {
        var same = 0; var all = 0
        for (y in from until to) for (x in 0 until b.width) { all++; if (diff(b.getPixel(x, y), reference[y * b.width + x]) <= tol) same++ }
        return same.toDouble() / all
    }

    /** The share of the pixels in rows [from] until [to] that are the flat [ground] (within [tol]). */
    private fun flatShare(b: Bitmap, ground: Int, from: Int, to: Int, tol: Int = 3): Double {
        var flat = 0; var all = 0
        for (y in from until to) for (x in 0 until b.width) { all++; if (diff(b.getPixel(x, y), ground) <= tol) flat++ }
        return flat.toDouble() / all
    }

    private fun reference(b: Bitmap, accent: String, dark: Boolean, backgroundId: String): IntArray {
        if (backgroundId == "paper") Paper.warm(dark)
        val look = resolveLook(if (dark) "dark" else "light", accent = accent)
        val spec = BACKGROUNDS.first { it.id == backgroundId }
        return render(b.width, b.height) { Backdrop(spec, look, look.page).draw(this, 1f) }
    }

    @Test fun homeInEmberDarkRunsThePaperUpUnderTheStatusBar() {
        launch()
        go { model.tab = Tab.Home }; setLook("dark", "Ember"); background(BACKGROUND_THEME); settle(); pause(8)
        val b = shot()
        save(b, "round35-home-ember-dark")
        val look = resolveLook("dark", accent = "Ember")
        val ref = reference(b, "Ember", true, "paper")
        val band = agreement(b, ref, 0, status)
        val flat = flatShare(b, look.page.toArgb(), 0, status)
        assertTrue("window ${b.width}x${b.height}", b.width == 400 && b.height == 800)
        assertTrue("the band is the backdrop's own (agrees with it on $band)", band > .97)
        assertTrue("the band is not flat ground (flat share $flat)", flat < .5)
    }

    @Test fun graphiteHomeKeepsItsOldGlowUnderTheStatusBar() {
        launch()
        go { model.tab = Tab.Home }; setLook("dark", "Graphite"); background(BACKGROUND_THEME); settle(); pause(6)
        val b = shot()
        save(b, "round35-home-graphite")
        val look = resolveLook("dark", accent = "Graphite")
        // Before Round 32: the ground, and the ambient light centred 40 dp below the top of the screen (the box's own offset cancelled out).
        val old = render(b.width, b.height) { drawRect(look.page); drawAmbientAt(look.ambient, 0f, 1f) }
        val band = agreement(b, old, 0, status, tol = 3)
        assertTrue("Graphite's band is the old glow ($band)", band > .99)
    }

    @Test fun settingsRunsThePatternBehindTheStatusBarAndTheBackBar() {
        launch()
        setLook("dark", "Ember"); background(BACKGROUND_THEME)
        go { model.stack.clear(); model.settingsPage = null; model.open(Screen.Settings) }; settle(); pause(8)
        val b = shot()
        save(b, "round35-settings-ember-dark")
        val look = resolveLook("dark", accent = "Ember")
        val ref = reference(b, "Ember", true, "paper")
        val bar = status + 64
        assertTrue("the status bar band is the backdrop's (${agreement(b, ref, 0, status)})", agreement(b, ref, 0, status) > .97)
        // The bar holds a title and an icon; behind them the page shows through, not a flat block.
        assertTrue("the BackBar is the backdrop's too (${agreement(b, ref, status, bar)})", agreement(b, ref, status, bar) > .85)
        assertTrue("no flat block behind the bar (${flatShare(b, look.page.toArgb(), 0, bar)})", flatShare(b, look.page.toArgb(), 0, bar) < .5)
    }
}
