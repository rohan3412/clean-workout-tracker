package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.app.ui.theme.orange
import clean.app.ui.theme.resolveLook
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 40: a tap on a jump chip is answered on the heading it leads to, not on the chip: an accent pill the width of the page lights behind that section's first
 * heading once the page is at rest, also where the page cannot scroll any further (its end). Once per tap, never when a page opens, a fade with no sweep in Light,
 * a held pill with no motion when animations are off.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round40Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null; compose.mainClock.autoAdvance = true }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 5) { repeat(times) { Thread.sleep(100); compose.waitForIdle() } }

    private fun launch(motion: Boolean, tier: String? = null) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            for (key in listOf("performance", clean.core.LOOK_ACCENT_KEY, clean.core.LOOK_THEME_KEY, LOOK_BACKGROUND_KEY)) repo.dao.remove(key)
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            if (tier != null) repo.dao.put(Kv("performance", tier))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        go { model.setOption(clean.core.LOOK_THEME_KEY, "dark"); model.setOption(clean.core.LOOK_ACCENT_KEY, "Cobalt") }
        repeat(200) { if (model.look.value.themeName == "Cobalt") return@repeat; Thread.sleep(50); compose.waitForIdle() }
        settle(300); pause(3)
        HeadingShine.started = 0; HeadingShine.swept = 0
    }

    private fun open(page: String) {
        go { model.stack.clear() }; settle(300)
        go { model.settingsPage = page; model.open(Screen.Settings) }; settle(); pause()
    }

    private fun chip(label: String): SemanticsNodeInteraction = compose.onNode(hasTestTag("chip $label"))
    private fun lit() = compose.onAllNodes(hasTestTag("heading shine"), useUnmergedTree = true).fetchSemanticsNodes()

    private fun shot(): Bitmap {
        compose.waitForIdle()
        val view = window!!
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun diff(a: Int, b: Int) = abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))

    @Test fun aTapLightsTheHeadingOfItsSectionOnceAndNothingAtFirst() {
        launch(motion = true)
        open(PAGE_SOUND)
        assertEquals("nothing is lit when the page opens", 0, HeadingShine.started)
        assertTrue(lit().isEmpty())
        chip("Motion").performClick(); settle(1_800); pause()
        assertEquals("one heading lit for the tap", 1, HeadingShine.started)
        assertEquals("with the sweep in Full", 1, HeadingShine.swept)
        assertEquals("one heading carries it, in the section the chip leads to", 1, lit().size)
        settle(1_500)
        assertEquals("and it does not run again by itself", 1, HeadingShine.started)
        chip("Haptics").performClick(); settle(1_800); pause()
        assertEquals(2, HeadingShine.started)
    }

    @Test fun theLastChipLightsItsHeadingWhereThePageCannotScrollToIt() {
        launch(motion = true)
        open(PAGE_SOUND)
        chip("Performance").performClick(); settle(1_800); pause()
        assertEquals(1, HeadingShine.started)
        val heading = lit().single().boundsInRoot
        assertTrue("the lit heading is on the screen ($heading)", heading.top >= 0f && heading.bottom <= 800f)
        assertTrue("it is the page's width, not a chip's (${heading.width})", heading.width > 300f)
    }

    @Test fun withAnimationsOffThePillIsHeldWithNoSweep() {
        launch(motion = false)
        open(PAGE_SOUND)
        chip("Motion").performClick(); settle(200); pause(2)
        assertEquals(1, HeadingShine.started)
        assertEquals("no sweep", 0, HeadingShine.swept)
        val r = lit().single().boundsInRoot
        val b = shot()
        save(b, "round40-heading-lit")
        val inside = b.getPixel((r.right - 12).toInt(), r.center.y.toInt())
        settle(1_500); pause()
        val after = shot().getPixel((r.right - 12).toInt(), r.center.y.toInt())
        val accent = resolveLook("dark", accent = "Cobalt").orange.toArgb()
        assertTrue("the pill is drawn (${inside.toString(16)} vs ${after.toString(16)})", diff(inside, after) > 30)
        assertTrue("and leans to the accent", diff(inside, accent) < diff(after, accent))
        assertEquals("gone after a second", 0, diff(after, shot().getPixel((r.right - 12).toInt(), r.center.y.toInt())))
    }

    @Test fun theLightTierFadesWithNoSweep() {
        launch(motion = true, tier = "light")
        open(PAGE_SOUND)
        chip("Motion").performClick(); settle(1_800); pause()
        assertEquals(1, HeadingShine.started)
        assertEquals("no sweep in Light", 0, HeadingShine.swept)
    }

    @Test fun pictureOfTheHighlightMidway() {
        launch(motion = true)
        open(PAGE_SOUND)
        compose.mainClock.autoAdvance = false
        chip("Performance").performClick()
        compose.mainClock.advanceTimeBy(900)
        save(shot(), "round40-heading-mid")
        compose.mainClock.autoAdvance = true
        settle(1_500)
    }
}
