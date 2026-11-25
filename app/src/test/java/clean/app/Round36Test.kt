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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.documentfile.provider.DocumentFile
import clean.core.relativeTime
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
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
 * Round 36: nothing paints the flat ground over the backdrop, and "Back up now". The window has real insets (see Round35Test) so the pixels are the phone's. The exercise
 * page's tab row and the dock's bottom fade are read against the backdrop drawn over the whole screen; the backup button runs a backup into a plain folder through the
 * seam [AutoBackup.openFolder] and the page says when.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round36Test {
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

    private fun cobaltGrid() { setLook("dark", "Cobalt"); background("grid") }

    private fun resetFolder() { AutoBackup.openFolder = { context, tree -> DocumentFile.fromTreeUri(context, android.net.Uri.parse(tree)) } }

    private fun folderOf(name: String): File = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, name).apply { deleteRecursively(); mkdirs() }

    private fun chooseFolder(extra: Map<String, String> = emptyMap()) {
        go { model.setOption(AutoBackupKeys.FOLDER, "content://fake/tree"); for ((k, v) in extra) model.setOption(k, v) }
        repeat(100) { if (model.options.value[AutoBackupKeys.FOLDER] != null) return@repeat; Thread.sleep(40); compose.waitForIdle() }
    }

    private fun openData() {
        go { model.stack.clear(); model.settingsPage = PAGE_BACKUP; model.open(Screen.Settings) }; settle(); pause(6)
    }

    @Test fun theExerciseTabRowSitsOnTheBackdropNotOnFlatGround() {
        launch()
        cobaltGrid()
        go { model.stack.clear(); model.open(Screen.ExerciseDetail("Bench Press (Barbell)")) }; settle(); pause(8)
        val b = shot()
        save(b, "round36-exercise-tabs-cobalt-grid")
        val look = resolveLook("dark", accent = "Cobalt")
        val ref = reference(b, "Cobalt", true, "grid")
        // Under the status bar and the 64 dp bar, 46 of the tab row's 48 dp (the rest is its divider).
        val from = status + 64
        val to = from + 46
        val band = agreement(b, ref, from, to)
        val flat = flatShare(b, look.page.toArgb(), from, to)
        assertTrue("the tab row is the backdrop's own (agrees on $band)", band > .8)
        assertTrue("the tab row is not a flat band (flat share $flat)", flat < .5)
    }

    @Test fun theDockFadeKeepsThePatternInsteadOfCuttingAFlatBand() {
        launch()
        cobaltGrid()
        go { model.stack.clear(); model.tab = Tab.Exercises }; settle(); pause(8)
        val b = shot()
        save(b, "round36-library-cobalt-grid")
        val look = resolveLook("dark", accent = "Cobalt")
        val ref = reference(b, "Cobalt", true, "grid")
        // The strip above the navigation bar, where the page used to soften into the ground at 70 %: the grid still runs there (empty rows between the dock's glass).
        val from = b.height - nav - 20
        val to = b.height - nav
        val flat = flatShare(b, look.page.toArgb(), from, to)
        assertTrue("the fade is not flat ground (flat share $flat)", flat < .9)
        assertTrue("the strip agrees with the backdrop where the dock is not (${agreement(b, ref, from, to, tol = 40)})", agreement(b, ref, from, to, tol = 40) > .3)
    }

    @Test fun whatsNewIsAPageThatCarriesTheBackground() {
        launch()
        cobaltGrid()
        go { model.showWhatsNew() }; settle(800); pause(6)
        compose.onNode(hasTestTag("whatsnew page")).assertExists()
        // A dialog is a window of its own; the picture is best effort.
        runCatching { save(compose.onNode(hasTestTag("whatsnew page")).captureToImage().asAndroidBitmap(), "round36-whatsnew-cobalt-grid") }
    }

    // ---- Back up now ----

    @Test fun backUpNowRunsABackupIntoTheChosenFolderAndSaysSo() {
        launch()
        val dir = folderOf("round36-backup")
        AutoBackup.openFolder = { _, _ -> DocumentFile.fromFile(dir) }
        try {
            chooseFolder()
            openData()
            compose.onNode(hasTestTag("back up now")).performScrollTo().assertIsEnabled()
            save(shot(), "round36-backup-section")
            compose.onNode(hasTestTag("back up now")).performClick()
            repeat(200) { if (dir.list()!!.isNotEmpty() && model.options.value[AutoBackupKeys.LAST] != null) return@repeat; Thread.sleep(50); compose.waitForIdle() }
            pause(4)
            val names = dir.list()!!.toList()
            assertEquals("one backup was written", 1, names.size)
            assertTrue("it is a scheduled backup's own name: $names", clean.core.isAutoBackupName(names.single()))
            assertTrue("it is the manual backup's zip", Backup.isBackup(File(dir, names.single())))
            assertNotNull("the last-backup time was recorded", model.options.value[AutoBackupKeys.LAST])
            assertEquals(null, model.options.value[AutoBackupKeys.ERROR])
            assertTrue("the toast says so: ${model.message}", model.message?.startsWith("Backed up to") == true)
            compose.onNodeWithText("just now").assertExists()
            compose.onNode(hasTestTag("back up now")).assertIsEnabled()
            save(shot(), "round36-backup-done")
        } finally { resetFolder() }
    }

    @Test fun aFolderThatCannotBeOpenedSaysWhy() {
        launch()
        AutoBackup.openFolder = { _, _ -> null }
        try {
            chooseFolder()
            openData()
            compose.onNode(hasTestTag("back up now")).performScrollTo().performClick()
            repeat(200) { if (model.message?.startsWith("Backup failed") == true) return@repeat; Thread.sleep(50); compose.waitForIdle() }
            assertEquals("Backup failed: the backup folder is not there any more", model.message)
            assertEquals("The backup folder is not there any more", model.options.value[AutoBackupKeys.ERROR])
            compose.onNode(hasTestTag("back up now")).assertIsEnabled()
            save(shot(), "round36-backup-failed")
        } finally { resetFolder() }
    }

    @Test fun lastBackupIsSaidRelativeForAWeekAndAsADateAfter() {
        launch()
        val now = LocalDateTime.now()
        fun stored(t: LocalDateTime) = t.format(STRONG_DATE_FORMAT)
        chooseFolder(mapOf(AutoBackupKeys.LAST to stored(now.minusHours(5))))
        openData()
        compose.onNodeWithText("5 hours ago").performScrollTo().assertExists()
        go { model.setOption(AutoBackupKeys.LAST, stored(now.minusDays(12))) }; settle(); pause(4)
        compose.onNodeWithText(prettyDate(stored(now.minusDays(12)))).assertExists()
        assertEquals("3 days ago", relativeTime(now.minusDays(3), now))
    }
}
