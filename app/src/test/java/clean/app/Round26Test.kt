package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.ExerciseSetting
import clean.app.data.Kv
import clean.app.data.MeasurementEntity
import clean.app.data.Profile
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.TemplateEntity
import clean.app.data.open
import clean.app.setWeightGoal
import clean.core.ChartPoint
import clean.core.PrKind
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.time.*
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 26: the performance tiers, the haptic vocabulary and the What's new page.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round26Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: android.view.View? = null
    private lateinit var model: AppModel
    private var profileId = 0L
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private val names = listOf(
        "Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)", "Overhead Press (Barbell)", "Bicep Curl (Dumbbell)",
        "Triceps Pushdown (Cable)", "Romanian Deadlift (Barbell)", "Lateral Raise (Dumbbell)",
    )
    private val bench = names[0]

    /** A profile with thirty weeks of three workouts a week, one in the week still running, a height and three kinds of measurement. */
    private fun launchRich(unit: String = "kg", measurements: Boolean = true, seenVersion: String? = null): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            profileId = id
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            if (seenVersion != null) repo.dao.put(Kv("whatsnew.seen", seenVersion))
            repo.dao.put(Kv("ui.motion", "on"))
            repo.dao.put(Kv("home.cards:$id", "last:w,streak:h,volume:h,week:h"))
            val rows = ArrayList<WorkoutRow>()
            val first = LocalDate.now().minusWeeks(30)
            var n = 0
            fun add(day: LocalDate, hour: Int) {
                n++
                val date = day.atTime(hour, 0).format(STRONG_DATE_FORMAT)
                val name = listOf("Push", "Pull", "Legs")[n % 3]
                for (e in 0 until 4) {
                    val exercise = names[(n + e * 3) % names.size]
                    rows += WorkoutRow(n.toString(), date, name, "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                    for (s in 1..3) rows += WorkoutRow(n.toString(), date, name, "3900", exercise, s.toString(), decimalText(40.0 + n * 0.5 + e * 5), (8 + s % 3).toString(), "", "", "", "", "")
                }
            }
            for (week in 0 until 30) for (offset in listOf(0L, 2L, 4L)) {
                val day = first.plusWeeks(week.toLong()).plusDays(offset)
                if (day < LocalDate.now()) add(day, 18)
            }
            // The week still running has a workout in it (the Monday, or today when it is Monday), earlier in the day than any other.
            add(clean.core.weekStart(LocalDate.now()), 7)
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
            for (t in listOf("Push", "Pull", "Legs")) {
                val blocks = names.take(6).joinToString(",") { "{\"exercise\":\"$it\",\"sets\":[\"\",\"\",\"\"]}" }
                repo.dao.put(TemplateEntity(0, id, t, "[$blocks]"))
            }
            repo.dao.update(Profile(id, "Rohan", unit, 178.0))
            repo.dao.put(ExerciseSetting(id, bench, goal = "100.0x5"))
            if (measurements) {
                repo.dao.insertMeasurements((0 until 4).flatMap { k ->
                    val at = LocalDate.now().minusWeeks(k * 3L).atTime(8, 0).format(STRONG_DATE_FORMAT)
                    listOf(MeasurementEntity(0, id, "waist.csv", at, decimalText(84.0 - k)), MeasurementEntity(0, id, "chest.csv", at, decimalText(101.0 + k * 0.5)))
                })
                repo.dao.insertMeasurements((0 until 30).map { MeasurementEntity(0, id, "weight.csv", LocalDate.now().minusWeeks(it.toLong()).atTime(8, 0).format(STRONG_DATE_FORMAT), decimalText(82.0 - it * 0.1)) })
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun open(screen: Screen) { go { model.stack.clear(); model.open(screen) }; settle() }
    private fun textCount(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + node.children.flatMap { texts(it) }

    private fun peek(tag: String): List<String>? =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { texts(it) }

    /** Looks for something with [find]; when it is not on screen, scrolls each vertical list of the page to item 1, 2, 3 ... until it is. */
    private fun <T> scrollFind(find: () -> T): T {
        runCatching { return find() }
        val lists = compose.onAllNodes(SemanticsMatcher("lazy list") { it.config.contains(SemanticsActions.ScrollToIndex) }, useUnmergedTree = true).fetchSemanticsNodes().sortedByDescending { it.boundsInRoot.height }
        for (index in 1..12) {
            for (list in lists) {
                val moved = runCatching { compose.runOnIdle { list.config[SemanticsActions.ScrollToIndex].action!!.invoke(index) } }
                if (moved.isFailure) continue
                settle(400)
                runCatching { return find() }
            }
        }
        return find()
    }

    /** Scrolls the page until [text] is on it. */
    /** Waits (in real time: the figures of a page are made off the main thread) for [text], then scrolls the page until it is on it. */
    private fun see(text: String) {
        repeat(60) { if (textCount(text) > 0) return; Thread.sleep(100); compose.waitForIdle() }
        scrollFind { check(textCount(text) > 0) { "no '$text' on the page; texts: " + compose.onAllNodes(SemanticsMatcher("t") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }.distinct().take(60) } }
    }

    private fun boundsOfText(text: String): Rect { see(text); return scrollFind {
        compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.firstOrNull { it.width > 0f && it.top >= 0f && it.top < 1700f }
            ?: throw AssertionError("no text '$text' on screen")
    } }

    private fun holdAt(at: Offset, tag: String): List<String> {
        root().performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        return peek(tag) ?: throw AssertionError("no card '$tag' after a hold at $at")
    }

    private fun release() { root().performTouchInput { up() }; compose.waitForIdle(); compose.mainClock.advanceTimeBy(400); compose.waitForIdle() }

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
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

    private fun look(theme: String) {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.dark == (theme == "dark")) { "the look did not change to $theme" }
    }

    private fun theme(name: String) {
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, name) }
        repeat(200) { if (model.look.value.themeName == name) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        check(model.look.value.themeName == name) { "the theme did not change to $name" }
    }

    private val top: Screen? get() = model.stack.lastOrNull()


    // ---- Round 26 ----

    private fun allText() = compose.onAllNodes(SemanticsMatcher("any text") { it.config.contains(SemanticsProperties.Text) }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { texts(it) }


    private fun scrollTo(key: String) {
        val tag = hasTestTag("card:$key")
        try { compose.waitUntil(4000) { compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { }
        val here = compose.onAllNodes(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        if (here != null && here.top >= 0f && here.bottom <= 700f && here.width > 0f) return
        val lists = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until lists) {
            try { compose.onAllNodes(hasScrollAction())[i].performScrollToKey(key.substringAfter(':')); compose.waitForIdle(); return } catch (e: Throwable) { }
        }
    }

    /** Sets options (null removes one) and waits until the model has them. */
    private fun options(vararg pairs: Pair<String, String?>) {
        go { for ((k, v) in pairs) model.setOption(k, v) }
        compose.waitUntil(10_000) { pairs.all { (k, v) -> model.options.value[k] == v } }
        settle(600)
    }

    /** Taps the first button or chip showing these words (the words' own box, in the unmerged tree, as the person's finger would). */
    private fun tap(text: String) { compose.onAllNodes(hasText(text), useUnmergedTree = true).onFirst().performClick(); compose.waitForIdle() }

    private fun frame(): Bitmap {
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    /** Brings Settings, Appearance's Performance group fully into view. */
    private fun showPerformance() {
        see("Performance")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("ap-performance"); settle(400)
    }

    /** Waits in real time (the model's writes happen off the main thread) for [done], idling the UI between looks. */
    private fun until(what: String, done: () -> Boolean) {
        repeat(100) { if (done()) return; Thread.sleep(100); compose.waitForIdle() }
        check(done()) { "never: $what" }
    }

    private fun shadow() = org.robolectric.Shadows.shadowOf(window!!)

    // ---- The tiers ----

    @Test fun eachTierSwitchesTheRightEffectsOff() {
        // solid glass, travelling chart lines, card growth, flourishes
        assertEquals(listOf(false, true, true, true), Performance.Full.let { listOf(it.solidGlass, it.morphLines, it.sharedCards, it.flourishes) })
        assertEquals(listOf(true, false, true, true), Performance.Balanced.let { listOf(it.solidGlass, it.morphLines, it.sharedCards, it.flourishes) })
        assertEquals(listOf(true, false, false, false), Performance.Light.let { listOf(it.solidGlass, it.morphLines, it.sharedCards, it.flourishes) })
        assertEquals("Full is the default", Performance.Full, Performance.of(null))
        assertEquals(Performance.Full, Performance.of("nonsense"))
        assertEquals(Performance.Light, Performance.of("light"))
    }

    @Test fun theTierIsAFloorAndReduceTransparencyStillWins() {
        val s = launchRich()
        options("glass.reduce" to "on", Performance.KEY to null)
        assertEquals(Performance.Full, MorphProbe.performance)
        assertTrue("Reduce transparency alone makes glass solid at Full", MorphProbe.solidGlass)
        options("glass.reduce" to null)
        assertFalse("Full with nothing reduced blurs", MorphProbe.solidGlass)
        options(Performance.KEY to "balanced")
        assertEquals(Performance.Balanced, MorphProbe.performance)
        assertTrue("Balanced draws opaque glass", MorphProbe.solidGlass)
        options(Performance.KEY to "light")
        assertTrue("Light draws opaque glass", MorphProbe.solidGlass)
        options("glass.reduce" to "on")
        assertTrue("Reduce transparency on top of Light", MorphProbe.solidGlass)
        options(Performance.KEY to null)
        assertTrue("back at Full the switch still holds", MorphProbe.solidGlass)
        s.close()
    }

    @Test fun lightStaysAnimatedAndAnimationsOffStillCutsEveryTier() {
        val s = launchRich()
        for (tier in Performance.entries) {
            options(Performance.KEY to tier.key, "ui.motion" to "on")
            assertTrue("$tier keeps motion on", MorphProbe.moving)
            options("ui.motion" to "off")
            assertFalse("$tier with Animations off", MorphProbe.moving)
        }
        s.close()
    }

    /** Opens Home's weight card and reports how many frames the container and the travelling line were drawn in. */
    private fun openWeightCard(): Pair<Int, Int> {
        go { model.stack.clear(); model.tab = Tab.Home }
        settle(1_500)
        scrollTo("home:weight")
        val clip = MorphProbe.clip!!
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        val clipBefore = clip.draws; val lineBefore = MorphProbe.chartDraws
        compose.onNodeWithTag("card:home:weight", useUnmergedTree = true).performTouchInput { click(Offset(8f, 8f)) }
        for (t in 0 until 640 step 16) { compose.mainClock.advanceTimeBy(16); frame() }
        val result = (clip.draws - clipBefore) to (MorphProbe.chartDraws - lineBefore)
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        assertTrue("the card did not open its page", model.stack.isNotEmpty())
        return result
    }

    @Test fun cardsGrowAndChartLinesTravelAtFullOnlyTheLineGoesAtBalancedAndNeitherAtLight() {
        val s = launchRich()
        options(Performance.KEY to null)
        val full = openWeightCard()
        assertTrue("Full: the container grows (${full.first} frames)", full.first > 0)
        assertTrue("Full: the line travels (${full.second} frames)", full.second > 0)
        go { model.back() }; settle()
        options(Performance.KEY to "balanced")
        val balanced = openWeightCard()
        assertTrue("Balanced: the container still grows (${balanced.first})", balanced.first > 0)
        assertEquals("Balanced: the line only cross-fades", 0, balanced.second)
        go { model.back() }; settle()
        options(Performance.KEY to "light")
        val light = openWeightCard()
        assertEquals("Light: no card grows into its page", 0, light.first)
        assertEquals("Light: no travelling line", 0, light.second)
        s.close()
    }

    @Test fun theSettingsChoiceSetsTheTierAndFullStoresNothing() {
        val s = launchRich()
        go { model.stack.clear(); model.settingsPage = PAGE_SOUND; model.open(Screen.Settings) }; settle()
        showPerformance()
        tap("Balanced"); settle(600)
        until("tier Balanced") { model.performance.value == Performance.Balanced }
        options(Performance.KEY to "light")
        options(Performance.KEY to null)
        until("tier Full") { model.performance.value == Performance.Full }
        compose.waitUntil(5_000) { model.options.value[Performance.KEY] == null }
        s.close()
    }

    // ---- The haptic vocabulary ----

    /** A platform that is the real one in everything but its buzzes and its clock, which it records and sets. */
    private class RecordingPlatform(private val real: clean.app.platform.Platform) : clean.app.platform.Platform by real {
        val calls = mutableListOf<Haptic>()
        var now = 10_000L
        override fun haptic(kind: Haptic) { calls += kind }
        override fun uptimeMillis(): Long = now
    }

    @Test fun eachBuzzOfTheVocabularyIsDifferentAndHasAFallBackOnOlderPhones() {
        for (sdk in listOf(26, 28, 30, 34, 35)) {
            val constants = Haptic.entries.map { AndroidHaptics.constantFor(it, sdk) }
            if (sdk >= 34) assertEquals("five buzzes, five effects at $sdk", 5, constants.toSet().size)
            else assertTrue("at $sdk the tick is not the heavy buzz", AndroidHaptics.constantFor(Haptic.Tick, sdk) != AndroidHaptics.constantFor(Haptic.Heavy, sdk))
        }
        assertEquals(android.view.HapticFeedbackConstants.CONFIRM, AndroidHaptics.constantFor(Haptic.Confirm, 30))
        assertEquals(android.view.HapticFeedbackConstants.REJECT, AndroidHaptics.constantFor(Haptic.Reject, 30))
        assertEquals(android.view.HapticFeedbackConstants.VIRTUAL_KEY, AndroidHaptics.constantFor(Haptic.Confirm, 29))
        assertEquals(android.view.HapticFeedbackConstants.LONG_PRESS, AndroidHaptics.constantFor(Haptic.Heavy, 26))
        assertEquals(android.view.HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, AndroidHaptics.constantFor(Haptic.Tick, 34))
        assertEquals(android.view.HapticFeedbackConstants.CLOCK_TICK, AndroidHaptics.constantFor(Haptic.Tick, 26))
        assertEquals(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE, AndroidHaptics.constantFor(Haptic.Tick, 27))
    }

    @Test fun noEventFiresTwoBuzzesAtOnce() {
        val s = launchRich()
        val fake = RecordingPlatform(model.platform)
        HapticGate.reset()
        fake.buzz(Haptic.Confirm)                       // a set ticked...
        fake.now += 40; fake.buzz(Haptic.Tick)          // ...a tick of the same moment is dropped
        fake.now += 40; fake.buzz(Haptic.Heavy)         // ...and its PR replaces it
        fake.now += 40; fake.buzz(Haptic.Confirm)       // a weaker one after the heavy is dropped
        assertEquals(listOf(Haptic.Confirm, Haptic.Heavy), fake.calls)
        fake.now += HapticGate.WINDOW_MS + 1; fake.buzz(Haptic.Tick)
        assertEquals("after the window a tick is its own event", listOf(Haptic.Confirm, Haptic.Heavy, Haptic.Tick), fake.calls)
        HapticGate.reset()
        s.close()
    }

    @Test fun theModelsBuzzesReachThePhoneAsTheVocabularyAndHonourHapticTicks() {
        val s = launchRich()
        HapticGate.reset()
        go { model.buzz(Haptic.Confirm) }
        assertEquals(android.view.HapticFeedbackConstants.CONFIRM, shadow().lastHapticFeedbackPerformed())
        HapticGate.reset()
        options("haptic.ticks" to "off")
        go { model.buzz(Haptic.Heavy) }
        assertEquals("nothing with Haptic ticks off", android.view.HapticFeedbackConstants.CONFIRM, shadow().lastHapticFeedbackPerformed())
        options("haptic.ticks" to null)
        HapticGate.reset()
        go { model.buzz(Haptic.Heavy) }
        assertEquals(android.view.HapticFeedbackConstants.LONG_PRESS, shadow().lastHapticFeedbackPerformed())
        s.close()
    }

    @Test fun undoIsAReject() {
        val s = launchRich()
        HapticGate.reset()
        go { model.say("Something removed") { }; model.takeBack() }
        assertEquals(android.view.HapticFeedbackConstants.REJECT, shadow().lastHapticFeedbackPerformed())
        s.close()
    }

    // ---- What's new ----

    @Test fun theBundledNotesHaveSixHighlightsForTheLifterAndNameNoOneElse() {
        val s = launchRich()
        val releases = readChanges(model.platform)
        val now = releases.first()
        assertEquals("1.1.0", now.version)
        assertEquals(WHATS_NEW_HIGHLIGHTS, highlightsOf(now).size)
        assertTrue("the rest are under See everything", now.changes.size > WHATS_NEW_HIGHLIGHTS)
        assertEquals("a picture of its own for each highlight", highlightsOf(now).size, highlightsOf(now).map { highlightIcon(it.title) }.toSet().size)
        val all = releases.flatMap { r -> r.changes.map { it.title + " " + it.text } }.joinToString(" ").lowercase()
        // The notes ship and go public; the words are built here so this file does not carry them.
        assertFalse(listOf("cla" + "ude", "anthro" + "pic").any { it in all })
        s.close()
    }

    @Test fun whatsNewShowsOnceAfterAnUpdateAndContinueClosesIt() {
        val s = launchRich(seenVersion = "1.0.4")
        compose.waitUntil(20_000) { model.whatsNew != null }
        see("Clean 1.1.0"); see("Continue"); see("See everything")
        assertEquals(6, highlightsOf(model.whatsNew!!).size)
        for (headline in highlightsOf(model.whatsNew!!).map { it.title }) see(headline)
        tap("See everything"); settle(600)
        see("1.0.4"); see("Back to the highlights")
        tap("Continue"); settle(600)
        assertNull(model.whatsNew)
        assertEquals(0, textCount("Back to the highlights"))
        compose.waitUntil(5_000) { model.options.value["whatsnew.seen"] == "1.1.0" }
        assertNull("not again for the version seen", clean.core.whatsNewDue(model.allChanges, "1.1.0", model.options.value["whatsnew.seen"], firstRun = false))
        s.close()
    }

    @Test fun aFreshStartRecordsTheVersionAndShowsNothing() {
        val s = launchRich()
        assertNull(model.whatsNew)
        compose.waitUntil(5_000) { model.options.value["whatsnew.seen"] == "1.1.0" }
        s.close()
    }

    @Test fun whatsNewIsReachableFromSettingsAbout() {
        val s = launchRich(seenVersion = "1.1.0")
        assertNull(model.whatsNew)
        go { model.stack.clear(); model.settingsPage = PAGE_ABOUT; model.open(Screen.Settings) }; settle()
        see("Open the What's new page")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("s-changes"); settle(400)
        tap("Open the What's new page"); settle(600)
        assertNotNull(model.whatsNew)
        see("Clean 1.1.0")
        tap("Continue"); settle(600)
        assertNull(model.whatsNew)
        s.close()
    }

    // ---- The tour ----

    private fun tour(prefix: String) {
        fun s(name: String, dialogs: Boolean = false, step: () -> Unit) {
            try { step(); shot("round26-$prefix-$name", dialogs) } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round26-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        s("01-settings-performance-full") { go { model.stack.clear() }; settle(); go { model.settingsPage = PAGE_SOUND; model.open(Screen.Settings) }; settle(); showPerformance() }
        s("02-settings-performance-balanced") { tap("Balanced"); settle(600) }
        s("03-settings-performance-light") { options(Performance.KEY to "light") }
        s("04-whats-new", dialogs = true) { go { model.showWhatsNew() }; settle(800); see("Continue") }
        s("05-whats-new-everything", dialogs = true) { tap("See everything"); settle(800) }
        s("06-settings-about") { tap("Continue"); settle(600); go { model.stack.clear() }; settle(); go { model.settingsPage = PAGE_ABOUT; model.open(Screen.Settings) }; settle(); see("Open the What's new page"); compose.onAllNodes(hasScrollAction()).onFirst().performScrollToKey("s-changes"); settle(400) }
        go { model.setOption(Performance.KEY, null) }
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        val s = launchRich(seenVersion = "1.1.0")
        look("dark"); theme("Graphite")
        tour("graphite-dark")
        look("light"); theme("Graphite")
        tour("graphite-light")
        s.close()
    }
}
