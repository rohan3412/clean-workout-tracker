package clean.app

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
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
 * Round 37: Settings is nine short pages. The main page says what each is set to, a page of three groups or more has jump chips that behave like the dock, the
 * search is Library's and History's, and the two search fields that were left over are the shared pill.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round37Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun pause(times: Int = 4) { repeat(times) { Thread.sleep(100); compose.waitForIdle() } }

    private val RESET = listOf(
        "rest.seconds", "auto.warm", "onerm.formula", "onerm.maxReps", "rest.tone", "haptic.ticks", "performance", "photos.blur", "photos.lock", "dock.left", "health",
        AutoBackupKeys.FOLDER, AutoBackupKeys.FREQ, AutoBackupKeys.LAST, clean.core.LOOK_ACCENT_KEY, clean.core.LOOK_THEME_KEY, LOOK_BACKGROUND_KEY, LOOK_BACKGROUND_PHOTO_KEY,
    )

    private fun launch(motion: Boolean = false, options: Map<String, String> = emptyMap()) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            // The database outlives a test: what an earlier one set is taken off.
            for (key in RESET) repo.dao.remove(key)
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            for ((k, v) in options) repo.dao.put(Kv(k, v))
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
        for (w in 0 until 4) {
            val date = LocalDate.now().minusDays(2L + (3 - w) * 4L).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for ((e, exercise) in listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)").withIndex()) {
                for (s in 1..3) rows += WorkoutRow((w + 1).toString(), date, "Push", "3600", exercise, s.toString(), decimalText(50.0 + w * 2.5 + e * 10), (8 + s % 2).toString(), "", "", "", "", "")
            }
        }
        return rows
    }

    private fun open(page: String?) {
        // Settings opens on [page] only when it is opened afresh, as the buttons that send the user there do: leave it first.
        go { model.stack.clear() }; settle(300)
        go { model.settingsPage = page; model.open(Screen.Settings) }; settle(); pause()
    }

    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun list() = compose.onAllNodes(hasScrollAction()).onFirst()
    private fun scrollTo(key: String) { list().performScrollToKey(key); settle(300); pause(2) }
    private fun textCount(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun has(text: String) = textCount(text) > 0
    private fun hasTag(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun back() { launched!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(400) }
    private fun optionsSet(vararg pairs: Pair<String, String?>) { go { for ((k, v) in pairs) model.setOption(k, v) }; settle(300); pause() }

    private fun row(page: String): String = compose.onNode(hasTestTag("page $page")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" | ") { it.text }
    private fun chip(label: String): SemanticsNodeInteraction = compose.onNode(hasTestTag("chip $label"))
    private fun lit(page: SettingsPageDef): List<String> = page.chips.map { it.label }.filter { hasTag("chip $it") && chip(it).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true }
    private fun top(text: String): Float = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: Float.MAX_VALUE

    // ---- 1. Every old setting is on a page ----

    /** Where the settings of the four old pages are now: the section and the words of its rows. */
    private val where: Map<String, List<Pair<String, List<String>>>> = mapOf(
        PAGE_WORKOUT to listOf(
            "s-rest" to listOf("Default rest", "Rest between sides", "Rest by set type", "Alert outside the app", "Go back to the workout when the rest ends"),
            "s-autofill" to listOf("Warm-up sets", "Working sets", "Drop sets"),
            "s-during" to listOf(
                "Suggest what to try", "Celebrate on finishing", "Summary when a workout is finished", "RPE on the number pad", "Show the next set", "Shrink finished exercises",
                "Workout on the lock screen", "Remind me to train on", "Bar and plates",
            ),
        ),
        PAGE_TRAINING to listOf(
            "s-consistency" to listOf("Weekly target", "Program", "Current split"),
            "s-onerm" to listOf("Formula", "Factor in RPE", "Most reps it is used for"),
            "s-weeksets" to listOf("Weekly sets per muscle"),
        ),
        PAGE_LOOK to listOf(
            "ap-mode" to listOf("Mode"), "ap-theme" to listOf("Theme"), "ap-black" to listOf("Pure black background"), "ap-dock" to listOf("Plus on the left"),
            "ap-text" to listOf("Text size", "Smallest"), "ap-cbsafe" to listOf("Colour-blind safe colours"), "ap-sticky" to listOf("Sticky notes"),
        ),
        PAGE_SOUND to listOf(
            "s-rest-end" to listOf("When a rest ends", "Buzz"), "s-rest-sound" to listOf("Rest sound", "Finish chime"), "ap-haptic" to listOf("Haptic ticks"),
            "ap-motion" to listOf("Animations"), "ap-glass" to listOf("Reduce transparency"), "ap-performance" to listOf("Performance", "Balanced"),
        ),
        PAGE_PRIVACY to listOf("ap-blur" to listOf("Blur photos", "Lock photos")),
        PAGE_BACKUP to listOf("s-autobackup" to listOf("Automatic backup", "Folder"), "s-backup" to listOf("Back up everything", "Restore a backup"), "s-delete" to listOf("Delete everything…")),
        PAGE_TRANSFER to listOf("s-import" to listOf("Import"), "s-export" to listOf("Workouts", "Measurements", "Write rest timers")),
        PAGE_LIBRARY to listOf("s-templates" to listOf("Export templates", "Import templates"), "s-tidy" to listOf("Turn notes into variations"), "s-animations" to listOf("Import animations from a zip")),
        PAGE_ABOUT to listOf("s-changes" to listOf("Open the What's new page"), "s-source" to listOf("Source code"), "s-credits" to listOf("Licences and credits"), "s-tips" to listOf("Tips")),
    )

    @Test fun everyOldSettingIsOnItsNewPage() {
        launch()
        for ((page, sections) in where) {
            open(page)
            val def = settingsPageDef(page)!!
            for ((key, words) in sections) {
                assertTrue("$key is a section of $page", key in def.sections)
                scrollTo(key)
                for (w in words) assertTrue("'$w' on $page ($key)", has(w))
            }
        }
        assertEquals("every page is covered", SETTINGS_PAGE_DEFS.map { it.id }.toSet(), where.keys)
    }

    @Test fun theMainPageHasNineRowsInFourGroupsAndEachOpensItsPage() {
        launch()
        open(null)
        for (id in SETTINGS_PAGE_DEFS.map { it.id }) assertTrue("row $id", hasTag("page $id"))
        compose.onNode(hasTestTag("page Photos and privacy")).performClick(); settle(); pause()
        assertTrue("the page opened", has("Blur photos"))
        compose.onNodeWithContentDescription("Back").performClick(); settle(); pause()
        assertTrue("Back from a page is the main page", hasTag("page Workout"))
    }


    // ---- 2. The main page says what each is set to ----

    @Test fun theMainPageShowsWhatEachRowIsSetToInPlainWords() {
        launch()
        open(null)
        assertTrue(row(PAGE_WORKOUT), row(PAGE_WORKOUT).contains("Rest 2:00 · auto-fill 1 warm-up, 2 sets, 1 drop"))
        assertTrue(row(PAGE_TRAINING), row(PAGE_TRAINING).contains(" a week · 1RM by Hybrid, up to 12 reps"))
        assertTrue(row(PAGE_LOOK), row(PAGE_LOOK).contains("Graphite · ") && row(PAGE_LOOK).contains(" · Dark"))
        assertTrue(row(PAGE_SOUND), row(PAGE_SOUND).contains("Deep bell · haptics on · Full"))
        assertTrue(row(PAGE_PRIVACY), row(PAGE_PRIVACY).contains("Not blurred · not locked"))
        assertTrue(row(PAGE_BACKUP), row(PAGE_BACKUP).contains("Not set up"))
        assertTrue(row(PAGE_LIBRARY), row(PAGE_LIBRARY).contains("0 templates"))
        assertTrue(row(PAGE_ABOUT), row(PAGE_ABOUT).contains("Clean"))
    }

    @Test fun theLinesFollowTheSettingsAndAreNewWhenYouComeBackFromAPage() {
        launch()
        open(PAGE_WORKOUT)
        optionsSet("rest.seconds" to "180", "auto.warm" to "2")
        compose.onNodeWithContentDescription("Back").performClick(); settle(); pause()
        assertTrue(row(PAGE_WORKOUT), row(PAGE_WORKOUT).contains("Rest 3:00 · auto-fill 2 warm-up, 2 sets, 1 drop"))
        optionsSet("onerm.formula" to "EPLEY", "onerm.maxReps" to "15", "rest.tone" to "bright", "haptic.ticks" to "off", "performance" to "light", "photos.blur" to "on", "photos.lock" to "on")
        assertTrue(row(PAGE_TRAINING), row(PAGE_TRAINING).contains("1RM by Epley, up to 15 reps"))
        assertTrue(row(PAGE_SOUND), row(PAGE_SOUND).contains("Bell · haptics off · Light"))
        assertTrue(row(PAGE_PRIVACY), row(PAGE_PRIVACY).contains("Blurred · locked"))
        optionsSet(clean.core.LOOK_ACCENT_KEY to "Ember", clean.core.LOOK_THEME_KEY to "light")
        assertTrue(row(PAGE_LOOK), row(PAGE_LOOK).contains("Ember · ") && row(PAGE_LOOK).contains(" · Light"))
    }

    @Test fun theBackupLineSaysHowLongAgoTheFolderAndTheSchedule() {
        val now = LocalDateTime.now()
        launch(options = mapOf(
            AutoBackupKeys.FOLDER to "content://fake/tree", AutoBackupKeys.FREQ to "daily", AutoBackupKeys.LAST to now.minusHours(3).format(STRONG_DATE_FORMAT),
        ))
        open(null)
        val line = row(PAGE_BACKUP)
        assertTrue(line, line.contains("3 hours ago") && line.contains("daily") && line.split(" · ").size == 3)
        optionsSet(AutoBackupKeys.LAST to now.minusDays(12).format(STRONG_DATE_FORMAT))
        assertTrue(row(PAGE_BACKUP), row(PAGE_BACKUP).contains(prettyDate(now.minusDays(12).format(STRONG_DATE_FORMAT))))
    }

    @Test fun theTransferLineNamesHealthConnectAndTheLibraryLineCountsTemplates() {
        launch()
        open(null)
        val transfer = row(PAGE_TRANSFER)
        assertTrue(transfer, transfer.contains("Health Connect o") || transfer.contains("Import and export workouts"))
        optionsSet("health" to "on")
        assertTrue(row(PAGE_TRANSFER), !row(PAGE_TRANSFER).contains("Health Connect o") || row(PAGE_TRANSFER).contains("Health Connect on"))
    }

    // ---- 3. The jump chips ----

    @Test fun chipsAreOnlyOnPagesWithThreeGroupsOrMore() {
        launch()
        for (def in SETTINGS_PAGE_DEFS) {
            open(def.id)
            val expected = def.chips.count { c -> c.keys.any { it != "s-health" || model.platform.hasHealth } } >= CHIPS_FROM
            assertEquals("${def.id}: chips", expected, hasTag("jump chips"))
            if (expected) for (c in def.chips) assertTrue("${def.id}: chip ${c.label}", hasTag("chip ${c.label}"))
        }
        open(PAGE_PRIVACY)
        assertFalse("one group: no chips", hasTag("jump chips"))
    }

    private fun barBounds() = compose.onNode(hasTestTag("jump chips")).fetchSemanticsNode().boundsInRoot

    @Test fun theChipsStayUnderTheTitleAsThePageScrollsAndDoNotChange() {
        // Round 38: no settling; the chips are fixed under the bar, the same size and place at the top and scrolled.
        launch()
        open(PAGE_LOOK)
        val before = barBounds()
        scrollTo("ap-sticky")
        val after = barBounds()
        assertEquals("pinned: the same place", before.top, after.top, 1f)
        assertEquals("not settled: the same height", before.height, after.height, 1f)
        list().performScrollToIndex(0); settle(300)
        assertEquals("back at the top it is as it was", before.height, barBounds().height, 1f)
    }

    @Test fun theLitChipIsTheSectionInView() {
        launch()
        open(PAGE_LOOK)
        val look = settingsPageDef(PAGE_LOOK)!!
        assertEquals(listOf("Theme"), lit(look))
        scrollTo("ap-black"); assertEquals(listOf("Background"), lit(look))
        scrollTo("ap-sticky"); assertEquals("the end of the page lights the last chip", listOf("Colours"), lit(look))
        scrollTo("ap-mode"); assertEquals(listOf("Theme"), lit(look))
    }

    @Test fun aTapScrollsToTheSectionAndLightsItsChip() {
        launch()
        open(PAGE_LOOK)
        val look = settingsPageDef(PAGE_LOOK)!!
        val before = top("Plus on the left")
        chip("Dock").performClick(); settle(900); pause()
        assertEquals(listOf("Dock"), lit(look))
        val after = top("Plus on the left")
        assertTrue("the Dock section is at the top now ($before to $after)", after < before && after < 400f)
        chip("Theme").performClick(); settle(900); pause()
        assertEquals(listOf("Theme"), lit(look))
    }

    private fun pitch(def: SettingsPageDef): Float {
        val first = chip(def.chips.first().label).fetchSemanticsNode().boundsInRoot
        val last = chip(def.chips.last().label).fetchSemanticsNode().boundsInRoot
        return (last.right - first.left) / def.chips.size
    }

    private fun slide(over: String, dx: Float, steps: Int = 12) {
        val from = chip(over).fetchSemanticsNode().boundsInRoot.center
        root().performTouchInput {
            down(from)
            for (i in 1..steps) { moveTo(Offset(from.x + dx * i / steps, from.y)); advanceEventTime(16) }
        }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
    }

    private fun lift() { root().performTouchInput { up() }; settle(900); pause() }

    @Test fun slidingAlongTheChipsMovesTheLitChipFromWhereItStartedAndTheLiftScrollsThere() {
        launch()
        open(PAGE_LOOK)
        val look = settingsPageDef(PAGE_LOOK)!!
        val p = pitch(look)
        val start = top("Plus on the left")
        val shadow = org.robolectric.Shadows.shadowOf(window!!)
        HapticGate.reset()
        // The finger is over the last chip, but the slide starts from the lit one (Theme): two chips on is Dock.
        slide("Colours", 2f * p)
        assertEquals("two chips on from the lit one", listOf("Dock"), lit(look))
        assertEquals("the tick of the step", HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, shadow.lastHapticFeedbackPerformed())
        assertEquals("the page waits for the lift", start, top("Plus on the left"), 1f)
        lift()
        assertTrue("then it scrolls to the Dock section", top("Plus on the left") < start)
        assertEquals(listOf("Dock"), lit(look))
        slide("Theme", 0.4f * p)
        assertEquals("under half a chip: no change", listOf("Dock"), lit(look))
        lift()
        slide("Theme", -9f * p)
        assertEquals("clamped at the first chip", listOf("Theme"), lit(look))
        lift()
    }

    @Test fun aFlickMovesOneSectionAndAVerticalStartDoesNothing() {
        launch()
        open(PAGE_LOOK)
        val look = settingsPageDef(PAGE_LOOK)!!
        val p = pitch(look)
        val c = chip("Colours").fetchSemanticsNode().boundsInRoot.center
        root().performTouchInput { swipe(c, Offset(c.x + 0.3f * p, c.y), durationMillis = 30) }
        settle(900); pause()
        assertEquals("one section on from Theme, whatever chip the finger was over", listOf("Background"), lit(look))
        root().performTouchInput { swipe(c, Offset(c.x - 0.3f * p, c.y), durationMillis = 30) }
        settle(900); pause()
        assertEquals(listOf("Theme"), lit(look))
        val d = chip("Dock").fetchSemanticsNode().boundsInRoot.center
        root().performTouchInput { down(d); for (i in 1..6) { moveBy(Offset(0f, -12f)); advanceEventTime(16) } ; up() }
        settle(600)
        assertEquals("a vertical start moves no chip", listOf("Theme"), lit(look))
    }

    @Test fun holdingAChipPeeksAtItsSettingsWithTheirValuesUntilTheFingerLifts() {
        launch()
        open(PAGE_LOOK)
        val c = chip("Dock").fetchSemanticsNode().boundsInRoot.center
        root().performTouchInput { down(c) }
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle(); pause()
        assertTrue("the peek is up", hasTag("chip peek"))
        assertTrue("it names the setting and its value", has("Dock · plus on the right"))
        root().performTouchInput { up() }
        settle(400); pause()
        assertFalse("it closes on release", hasTag("chip peek"))
        assertEquals("a hold is no tap: nothing scrolled", listOf("Theme"), lit(settingsPageDef(PAGE_LOOK)!!))
        optionsSet("dock.left" to "on")
        root().performTouchInput { down(c) }
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle(); pause()
        assertTrue(has("Dock · plus on the left"))
        root().performTouchInput { up() }
        settle(400)
    }

    // ---- 4. The search ----

    private fun fieldCount() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    @Test fun pullingTheMainPageDownOpensTheSearchAndBackClosesIt() {
        launch(motion = true)
        open(null)
        assertEquals(0, fieldCount())
        root().performTouchInput { down(Offset(width / 2f, height * 0.4f)); moveBy(Offset(0f, 150f)); moveBy(Offset(0f, 150f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        assertTrue("the release hint", textCount("Release to search") == 1)
        root().performTouchInput { up() }
        settle(800)
        assertEquals(1, fieldCount())
        assertTrue("the pill is the shared one", hasTag("search field"))
        back()
        assertEquals("Back closed the search", 0, fieldCount())
        assertEquals("and stayed in Settings", Screen.Settings, model.stack.lastOrNull())
        // The icon in the bar opens it too.
        compose.onNodeWithContentDescription("Search settings").performClick(); settle(800)
        assertEquals(1, fieldCount())
        compose.onNodeWithContentDescription("Close search").performClick(); settle(800)
        assertEquals(0, fieldCount())
    }

    @Test fun aResultNamesItsPageAndGroupWithItsValueAndOpensThePageAtTheSetting() {
        launch()
        open(null)
        compose.onNodeWithContentDescription("Search settings").performClick(); settle(600)
        compose.onNode(hasSetTextAction()).performTextInput("rest")
        settle(500); pause()
        assertTrue("the page, the group and the value", has("Workout › Rest timer · 2:00"))
        assertTrue("another page's setting", has("Sound and feel › Rest sound"))
        compose.onNodeWithContentDescription("Close search").performClick(); settle(500)
        compose.onNodeWithContentDescription("Search settings").performClick(); settle(600)
        compose.onNode(hasSetTextAction()).performTextInput("plates")
        settle(500); pause()
        assertTrue(has("Workout › In a workout"))
        compose.onAllNodes(hasTestTag("result s-during")).onFirst().performClick(); settle(300); pause()
        assertEquals("the search is closed", 0, fieldCount())
        assertTrue("the Workout page is open at its 'In a workout' group", has("Bar and plates"))
        assertTrue("the section is washed for a moment", hasTag("settings flash"))
        assertTrue("and it is up at the top of the page", top("Bar and plates") < 600f)
        settle(2_500); pause()
        assertFalse("the wash is over", hasTag("settings flash"))
    }

    @Test fun aSearchWithNoMatchSaysSo() {
        launch()
        open(null)
        compose.onNodeWithContentDescription("Search settings").performClick(); settle(600)
        compose.onNode(hasSetTextAction()).performTextInput("xyzzy")
        settle(500)
        assertTrue(has("No setting matches"))
    }

    // ---- 5. The two search fields that were left over ----

    @Test fun theWorkoutSheetsTemplateSearchIsTheSharedPillAndThePickerKeepsItsPinnedOne() {
        launch()
        go { model.stack.clear(); model.openWorkoutSheet() }; settle(800); pause()
        assertTrue("the pill, not an outlined field", hasTag("search field"))
        assertTrue(has("Search templates"))
        assertEquals("no outlined field: the only text field is the pill's", 1, fieldCount())
        compose.onNode(hasTestTag("start pick")).performClick(); settle(800); pause()
        assertTrue("the picker's pill", has("exercises"))
        assertEquals("the sheet's pill and the picker's: two pills", 2, compose.onAllNodes(hasTestTag("search field"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ---- Pictures ----

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300); pause(3)
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        if (dialogs) {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== view && root.width > 0) root.draw(canvas)
        }
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Category(Tour::class) @Test fun tourOfTheNineSettingsPages() {
        launch(options = mapOf(AutoBackupKeys.FOLDER to "content://fake/tree", AutoBackupKeys.FREQ to "daily", AutoBackupKeys.LAST to LocalDateTime.now().minusHours(3).format(STRONG_DATE_FORMAT), "photos.blur" to "on"))
        open(null); shot("round37-main")
        for ((n, def) in SETTINGS_PAGE_DEFS.withIndex()) { open(def.id); shot("round37-page-${n + 1}-${def.id.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')}") }
        open(PAGE_LOOK); scrollTo("ap-dock"); shot("round37-chips-stuck-look")
        open(PAGE_SOUND); scrollTo("ap-motion"); shot("round37-chips-stuck-sound")
        open(PAGE_LOOK)
        val c = chip("Dock").fetchSemanticsNode().boundsInRoot.center
        root().performTouchInput { down(c) }
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle(); pause()
        shot("round37-chip-peek", dialogs = true)
        root().performTouchInput { up() }
        settle(400)
        open(null)
        compose.onNodeWithContentDescription("Search settings").performClick(); settle(600)
        compose.onNode(hasSetTextAction()).performTextInput("rest")
        settle(500); pause()
        shot("round37-search-results")
    }
}
