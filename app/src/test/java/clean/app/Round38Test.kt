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
 * Round 38: the jump chips are glass pills that float on the backdrop under the bar, never dimmed or settled or clipped (Round 40 moved the tap's highlight to the heading it leads to); "Back up now" is a small button in the Folder row; snapshots can be added with the camera. Real status-bar insets as in Round 35.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round38Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null
    private val status = 24
    private val nav = 48

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null; compose.mainClock.autoAdvance = true }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 5) { repeat(times) { Thread.sleep(100); compose.waitForIdle() } }

    private val RESET = listOf(
        "rest.seconds", "performance", "photos.blur", "photos.lock", "health", AutoBackupKeys.FOLDER, AutoBackupKeys.FREQ, AutoBackupKeys.LAST, AutoBackupKeys.ERROR,
        clean.core.LOOK_ACCENT_KEY, clean.core.LOOK_THEME_KEY, LOOK_BACKGROUND_KEY, LOOK_BACKGROUND_PHOTO_KEY,
    )

    private fun launch(motion: Boolean = false) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            for (key in RESET) repo.dao.remove(key)
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            repo.dao.insertRows(history(id).map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        applyInsets()
        go { model.setOption(clean.core.LOOK_THEME_KEY, "dark"); model.setOption(clean.core.LOOK_ACCENT_KEY, "Cobalt"); model.setBackground("grid") }
        repeat(200) { if (model.look.value.themeName == "Cobalt" && model.background.value == "grid") return@repeat; Thread.sleep(50); compose.waitForIdle() }
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

    private fun open(page: String?) {
        go { model.stack.clear() }; settle(300)
        go { model.settingsPage = page; model.open(Screen.Settings) }; settle(); pause()
    }

    private fun list() = compose.onAllNodes(hasScrollToKeyAction()).onFirst()
    private fun scrollTo(key: String) { list().performScrollToKey(key); settle(300); pause(2) }
    private fun hasTag(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun chip(label: String): SemanticsNodeInteraction = compose.onNode(hasTestTag("chip $label"))
    private fun bounds(label: String) = chip(label).fetchSemanticsNode().boundsInRoot
    private fun isLit(label: String) = chip(label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
    private fun litOf(page: String) = settingsPageDef(page)!!.chips.map { it.label }.filter { hasTag("chip $it") && isLit(it) }

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

    private fun reference(b: Bitmap): IntArray {
        val look = resolveLook("dark", accent = "Cobalt")
        val spec = BACKGROUNDS.first { it.id == "grid" }
        return render(b.width, b.height) { Backdrop(spec, look, look.page).draw(this, 1f) }
    }

    /** A pixel well inside the chip's left end, away from its words. */
    private fun fillOf(b: Bitmap, label: String): Int { val r = bounds(label); return b.getPixel((r.left + 10).toInt(), r.center.y.toInt()) }

    // ---- 1. The chips ----

    @Test fun pinnedChipsFloatOnTheBackdropWithNoBand() {
        launch()
        open(PAGE_SOUND); scrollTo("ap-performance")
        val b = shot()
        save(b, "round38-sound-scrolled")
        val ref = reference(b)
        val bar = compose.onNode(hasTestTag("jump chips")).fetchSemanticsNode().boundsInRoot
        val chips = settingsPageDef(PAGE_SOUND)!!.chips.map { bounds(it.label) }
        var same = 0; var all = 0
        for (y in bar.top.toInt() until bar.bottom.toInt()) for (x in 0 until b.width) {
            if (chips.any { x >= it.left - 4 && x <= it.right + 4 }) continue
            all++; if (diff(b.getPixel(x, y), ref[y * b.width + x]) <= 12) same++
        }
        assertTrue("between and beside the chips the backdrop shows ($same of $all)", all > 0 && same.toDouble() / all > .95)
        val ground = resolveLook("dark", accent = "Cobalt").page.toArgb()
        var flat = 0
        for (y in bar.top.toInt() until bar.bottom.toInt()) for (x in 0 until b.width) if (diff(b.getPixel(x, y), ground) <= 3) flat++
        assertTrue("the strip is not flat ground ($flat)", flat.toDouble() / ((bar.bottom - bar.top) * b.width) < .6)
    }

    @Test fun chipsAreNotDimmedByTheTopFadeOnceThePageHasScrolled() {
        launch()
        open(PAGE_LOOK)
        val lit0 = litOf(PAGE_LOOK).single()
        val rest = fillOf(shot(), lit0)
        scrollTo("ap-sticky")
        val b = shot()
        save(b, "round38-look-scrolled")
        val lit1 = litOf(PAGE_LOOK).single()
        assertTrue("another chip is lit now ($lit0, $lit1)", lit0 != lit1)
        assertTrue("the lit chip has the same fill scrolled as at rest (${rest.toString(16)} / ${fillOf(b, lit1).toString(16)})", diff(rest, fillOf(b, lit1)) <= 6)
    }

    @Test fun noChipIsClippedAtRestAndTheLitChipIsAlwaysWhollyInView() {
        launch()
        for (def in SETTINGS_PAGE_DEFS) {
            open(def.id)
            if (!hasTag("jump chips")) continue
            val labels = def.chips.map { it.label }.filter { hasTag("chip $it") }
            val first = bounds(labels.first()); val last = bounds(labels.last())
            val fits = last.right - first.left + 2 * 16f <= 400f
            val width = last.right - first.left
            for (l in labels) {
                val r = bounds(l)
                if (fits) assertTrue("${def.id}: $l whole inside the screen ($r)", r.left >= 0f && r.right <= 400f)
            }
            assertTrue("${def.id}: first chip has the gutter (${first.left}, row $width)", first.left >= 16f - 1f)
            val lit = labels.first { isLit(it) }
            assertTrue("${def.id}: the lit chip $lit is whole", bounds(lit).left >= 0f && bounds(lit).right <= 400f)
            // The last chip, lit by a tap, is whole too (scrolled into view, with the gutter at the end).
            chip(labels.last()).performClick(); settle(900); pause()
            val e = bounds(labels.last())
            assertTrue("${def.id}: the last chip is whole once lit ($e)", e.left >= 0f && e.right <= 400f - 15f)
        }
    }

    @Test fun theLitChipIsFilledWithTheAccentAndTheOthersAreNot() {
        launch()
        open(PAGE_SOUND)
        val b = shot()
        val lit = litOf(PAGE_SOUND).single()
        val other = settingsPageDef(PAGE_SOUND)!!.chips.map { it.label }.first { it != lit }
        val fill = fillOf(b, lit)
        val look = resolveLook("dark", accent = "Cobalt")
        assertTrue("lit fill is the accent (${fill.toString(16)} vs ${look.orange.toArgb().toString(16)})", diff(fill, look.orange.toArgb()) <= 40)
        assertTrue("the others are not (${fillOf(b, other).toString(16)})", diff(fillOf(b, other), look.orange.toArgb()) > 150)
    }

    // ---- 2. Back up now ----

    private fun resetFolder() { AutoBackup.openFolder = { context, tree -> DocumentFile.fromTreeUri(context, android.net.Uri.parse(tree)) } }
    private fun folderOf(name: String): File = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, name).apply { deleteRecursively(); mkdirs() }

    @Test fun backUpNowIsASmallButtonInTheFolderRowAndRunsABackup() {
        launch()
        val dir = folderOf("round38-backup")
        AutoBackup.openFolder = { _, _ -> DocumentFile.fromFile(dir) }
        try {
            go { model.setOption(AutoBackupKeys.FOLDER, "content://fake/tree") }
            repeat(100) { if (model.options.value[AutoBackupKeys.FOLDER] != null) return@repeat; Thread.sleep(40); compose.waitForIdle() }
            open(PAGE_BACKUP)
            assertTrue("no separate Back up now row any more", compose.onAllNodes(hasText("Back up now")).fetchSemanticsNodes().isEmpty())
            val folder = compose.onNodeWithText("Folder").fetchSemanticsNode().boundsInRoot
            val button = compose.onNode(hasTestTag("back up now")).fetchSemanticsNode().boundsInRoot
            assertTrue("the button is in the Folder row, at its right end ($folder / $button)", button.center.y in folder.top - 20f..folder.bottom + 40f && button.right > 300f)
            save(shot(), "round38-backup")
            compose.onNode(hasTestTag("back up now")).assertIsEnabled().performClick()
            repeat(200) { if (dir.list()!!.isNotEmpty() && model.options.value[AutoBackupKeys.LAST] != null) return@repeat; Thread.sleep(50); compose.waitForIdle() }
            pause(4)
            assertEquals("one backup was written", 1, dir.list()!!.size)
            assertNotNull(model.options.value[AutoBackupKeys.LAST])
            assertTrue("the toast says so: ${model.message}", model.message?.startsWith("Backed up to") == true)
            compose.onNode(hasTestTag("back up now")).assertIsEnabled()
        } finally { resetFolder() }
    }

    // ---- 3. Taking a snapshot ----

    @Test fun addingASnapshotOffersTheCameraAndTheGallery() {
        launch()
        go { model.stack.clear(); model.open(Screen.Moments) }; settle(); pause()
        compose.onNodeWithText("Add photo").performClick(); settle(); pause()
        assertTrue("Take a photo is offered", compose.onAllNodes(hasText("Take a photo")).fetchSemanticsNodes().isNotEmpty())
        assertTrue("Choose from the phone is offered", compose.onAllNodes(hasText("Choose from the phone")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun theProfileFansAddPhotoOffersTheSameChoice() {
        launch()
        go { model.stack.clear(); model.tab = Tab.Profile }; settle(); pause()
        val nodes = compose.onAllNodes(hasText("Add photo")).fetchSemanticsNodes()
        assertTrue("an Add photo pill is there", nodes.isNotEmpty())
        compose.onAllNodes(hasText("Add photo")).onFirst().performScrollTo().performClick(); settle(); pause()
        assertTrue("Take a photo is offered", compose.onAllNodes(hasText("Take a photo")).fetchSemanticsNodes().isNotEmpty())
    }

    // ---- Pictures ----

    @Category(Tour::class) @Test fun tourOfTheNewChips() {
        launch(motion = true)
        open(PAGE_SOUND); save(shot(), "round38-sound-rest")
        scrollTo("ap-performance"); save(shot(), "round38-sound-scrolled-tour")
        open(PAGE_LOOK); scrollTo("ap-dock"); save(shot(), "round38-look-scrolled-tour")
        open(PAGE_SOUND)
        compose.mainClock.autoAdvance = false
        chip("Motion").performClick()
        compose.mainClock.advanceTimeBy(220)
        save(shot(), "round38-shimmer-mid")
        compose.mainClock.autoAdvance = true
        settle(1_000)
    }
}
