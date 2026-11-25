package clean.app

import org.junit.experimental.categories.Category
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.core.STRONG_DATE_FORMAT
import clean.core.Session
import clean.core.SetItem
import clean.core.SetType
import clean.core.WorkoutRow
import clean.core.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
 * Round 17 (eight small delights) on the real app and, for the numbers behind them, as plain functions: 9 the Library hold peeks at the last
 * session, 10 "Did you mean" under a search that finds nothing, 11 a new weight PR counting up on the summary, 12 pulling the Library down opens
 * its search, 13 holding the avatar, 15 the Snapshots cards leaning, 17 the ▲ of a set that beat last time, 19 the Finish button breathing.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round17Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: AppModel
    private var window: View? = null

    private fun row(weight: Double?, reps: Int?, type: String = "Normal") =
        SetItem(WorkoutRow("1", "2026-01-01 10:00:00", "W", "3600", "Bench Press (Barbell)", "1", weight?.toString().orEmpty(), reps?.toString().orEmpty(), "", "", "", "", ""), if (type == "Warm") SetType.WARMUP else SetType.NORMAL)

    // ---- the numbers behind them ----

    @Test fun lastSessionPeekListsTheDayAndWorkingSets() {
        val s = Session("2026-01-01 10:00:00", listOf(row(20.0, 10, "Warm"), row(60.0, 8), row(60.0, 7)))
        val peek = lastSessionPeek(s, "kg")!!
        assertEquals(listOf("60 kg × 8", "60 kg × 7"), peek.drop(1))
        assertEquals(prettyDay("2026-01-01 10:00:00"), peek.first())
        assertNull(lastSessionPeek(null, "kg"))
        val many = lastSessionPeek(Session("2026-01-01 10:00:00", List(9) { row(50.0, 5) }), "kg")!!
        assertEquals("+3 more", many.last())
        assertEquals(8, many.size)
    }

    @Test fun repsBeatLastTimeOnlyAtTheSameWeightOrMore() {
        val before = row(60.0, 8)
        assertTrue(beatsLastReps(60.0, 9, before))
        assertTrue(beatsLastReps(62.5, 9, before))
        assertFalse(beatsLastReps(60.0, 8, before))
        assertFalse(beatsLastReps(57.5, 12, before))
        assertFalse(beatsLastReps(60.0, null, before))
        assertFalse(beatsLastReps(60.0, 9, null))
    }

    @Test fun thePreviousBestIsTheHeaviestWorkingWeightBeforeTheDay() {
        val a = Session("2026-01-01 10:00:00", listOf(row(100.0, 1, "Warm"), row(60.0, 8)))
        val b = Session("2026-01-08 10:00:00", listOf(row(70.0, 5)))
        val c = Session("2026-01-15 10:00:00", listOf(row(90.0, 3)))
        assertEquals(70.0, previousBestWeight(listOf(a, b, c), "2026-01-15 10:00:00")!!, 1e-9)
        assertNull(previousBestWeight(listOf(a, b, c), "2026-01-01 10:00:00"))
    }

    @Test fun aRolledWeightClimbsInHalvesAndLandsExactly() {
        var last = 60.0
        for (i in 0..10) {
            val w = rolledWeight(60.0, 100.0, i / 10f, "kg")
            assertTrue(w >= last)
            assertEquals(0.0, w * 2 - Math.floor(w * 2), 1e-9)
            last = w
        }
        assertEquals(100.0, rolledWeight(60.0, 100.0, 1f, "kg"), 0.0)
        assertEquals(60.0, rolledWeight(60.0, 100.0, 0f, "kg"), 0.0)
    }

    @Test fun theTrainedLineAddsUpEveryWorkout() {
        val first = LocalDate(2026, 4, 3)
        assertEquals("412 h trained since Apr 2026", trainedLine(412 * 3600L + 600, first))
        assertEquals("1,203 h trained since Apr 2026", trainedLine(1203 * 3600L, first))
        assertEquals("20 min trained since Apr 2026", trainedLine(1200, first))
        assertNull(trainedLine(0, first))
        assertNull(trainedLine(3600, null))
    }

    @Test fun theTiltFollowsTheScrollAndNeverPassesOne() {
        assertEquals(0f, tiltStep(0f, 0f), 0f)
        var t = 0f
        repeat(30) { t = tiltStep(t, 500f) }
        assertEquals(1f, t, 1e-4f)
        repeat(30) { t = tiltStep(t, -500f) }
        assertEquals(-1f, t, 1e-4f)
        assertTrue(tiltStep(0f, 10f) > 0f && tiltStep(0f, 10f) < 0.2f)
    }

    @Test fun finishBreathesOnlyWithEverySetTicked() {
        fun block(vararg done: Boolean) = DraftBlock("Bench Press (Barbell)", done.map { DraftSet(weight = "60", reps = "8", done = it) })
        assertFalse(everySetDone(emptyList()))
        assertFalse(everySetDone(listOf(DraftBlock("Bench Press (Barbell)", emptyList()))))
        assertTrue(everySetDone(listOf(block(true, true), block(true))))
        assertFalse(everySetDone(listOf(block(true, false))))
        assertFalse(everySetDone(listOf(block(true), block(true, false))))
    }

    // ---- on the real app ----

    private val lifts = listOf("Bench Press (Barbell)", "Squat (Barbell)")

    private fun launch(motion: Boolean = true): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            repo.dao.put(Kv("weekpill:$id", clean.core.weekStart(LocalDate.now()).toString()))
            repo.dao.put(Kv("ui.motion", if (motion) "on" else "off"))
            val rows = ArrayList<WorkoutRow>()
            for (n in 0 until 6) {
                val date = LocalDate.now().minusDays(n * 3L + 3).atTime(18, 0).format(STRONG_DATE_FORMAT)
                for (lift in lifts) for (s in 1..3) rows += WorkoutRow((n + 1).toString(), date, "Push", "3600", lift, s.toString(), "60.0", "8", "", "", "", "", "")
            }
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        settle()
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun root() = compose.onAllNodes(isRoot()).onFirst()
    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
    private fun textCount(text: String) = compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun frame(): Bitmap {
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
        val bitmap = frame()
        // A menu is a window of its own: the windows over the activity are drawn on top.
        if (dialogs) {
            val canvas = android.graphics.Canvas(bitmap)
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== window && root.width > 0) root.draw(canvas)
        }
        val out = java.io.File("build/screens/default").apply { mkdirs() }
        java.io.File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun rowRect(text: String): Rect =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width > 0f && it.left >= 0f && it.right <= 800f && it.top in 100f..1500f }

    // 9
    @Test fun holdingALibraryRowPeeksAtTheLastSessionAndKeepsTheMenu() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        val at = rowRect("Bench Press (Barbell)")
        root().performTouchInput { down(Offset(at.left + 40f, at.center.y)) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertTrue("no peek on the hold", count("hold peek") == 1)
        assertTrue("last session's sets are not listed", textCount("60 kg × 8") >= 1)
        for (action in listOf("Open", "Edit")) assertTrue("the menu lost $action", textCount(action) >= 1)
        shot("round17-library-hold-peek", dialogs = true)
        root().performTouchInput { up() }
        s.close()
    }

    // 10
    /** A misspelling that finds nothing in the built-in library but has a nearest name (worked out with the same two functions the list uses). */
    private fun typoWithNearest(): Pair<String, String> {
        val entries = clean.core.LIBRARY.map { clean.core.LibraryEntry(it.name, clean.core.otherNames(it.name)) }
        for (q in listOf("bnechh", "sqautt", "deadliftt", "pulldwonn", "bicpe curll", "lateral rasie", "bnech", "pulldwon")) {
            val r = clean.core.rankLibrary(q, entries)
            val near = clean.core.nearestName(q, entries)
            if (r.done.isEmpty() && r.more.isEmpty() && near != null) return q to near
        }
        throw AssertionError("no typo to try")
    }

    @Test fun aSearchThatFindsNothingOffersTheNearestNameAndATapFillsTheSearch() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        val (typo, _) = typoWithNearest()
        // Round 20: the search is an icon until tapped.
        compose.onNodeWithContentDescription("Search exercises").performClick(); settle()
        compose.onNode(hasSetTextAction()).performTextInput(typo)
        var waited = 0
        while (textCount("Did you mean") == 0 && waited++ < 400) { Thread.sleep(25); compose.waitForIdle(); compose.mainClock.advanceTimeBy(50) }
        val shown = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString { t -> t.text } }.filter { it.startsWith("No exercise") || it.startsWith("Did") || it.startsWith("Create") || it.startsWith("Search") }
        val said = shown.firstOrNull { it.startsWith("Did you mean ") }
        assertNotNull("no suggestion for $typo, on screen: $shown", said)
        val near = said!!.removePrefix("Did you mean ").removeSuffix("?")
        assertEquals("the Bench Press done most often wins the tie", "Bench Press (Barbell)", near)
        shot("round17-did-you-mean")
        compose.onAllNodesWithText("Did you mean", substring = true, useUnmergedTree = true).onFirst().performClick()
        settle()
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)!!.text
        assertEquals(near, field)
        s.close()
    }

    // 12
    @Test fun pullingTheLibraryDownShowsAHintAndOpensTheSearchOnRelease() {
        val s = launch()
        go { model.tab = Tab.Exercises }
        settle()
        val list = rowRect("Bench Press (Barbell)")
        // A short pull: the hint says so, and letting go does not focus the search.
        root().performTouchInput { down(Offset(list.center.x, list.center.y)); moveBy(Offset(0f, 60f)); moveBy(Offset(0f, 60f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        assertTrue("no hint while pulling", textCount("Pull to search") == 1)
        root().performTouchInput { up() }
        settle(500)
        assertEquals("the hint stayed", 0, count("pull hint"))
assertEquals("the search stayed closed", 0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        // A long pull: the hint changes its words, and letting go focuses the search.
        root().performTouchInput { down(Offset(list.center.x, list.center.y)); moveBy(Offset(0f, 150f)); moveBy(Offset(0f, 150f)) }
        compose.mainClock.advanceTimeBy(100); compose.waitForIdle()
        assertTrue("no release hint", textCount("Release to search") == 1)
        shot("round17-pull-to-search")
        root().performTouchInput { up() }
        settle(500)
        assertTrue("the search was not focused", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true)
        assertEquals(0, count("pull hint"))
        s.close()
    }

    // 13
    private val avatar = SemanticsMatcher("the avatar") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change profile picture" }

    @Test fun holdingTheAvatarShowsTheTotalTrainingTimeUntilRelease() {
        val s = launch()
        go { model.tab = Tab.Profile }
        settle()
        val at = compose.onNode(avatar, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        root().performTouchInput { down(at.center) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertEquals(1, count("avatar peek"))
        // 6 workouts of an hour each.
        assertTrue("wrong words", textCount("6 h trained since ") == 1)
        val card = compose.onNode(hasTestTag("avatar peek"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("card outside the margins: $card", card.left >= 32f - 1f && card.right <= 800f - 32f + 1f)
        shot("round17-avatar-hold")
        root().performTouchInput { up() }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        assertEquals(0, count("avatar peek"))
        s.close()
    }

    @Test fun aTapOnTheAvatarStillOpensItsMenu() {
        val s = launch()
        go { model.tab = Tab.Profile }
        settle()
        compose.onNode(avatar, useUnmergedTree = true).performClick()
        settle()
        assertTrue(textCount("Choose photo") >= 1)
        assertEquals(0, count("avatar peek"))
        s.close()
    }

    // 17 and 19
    private fun startWith(vararg sets: DraftSet) {
        go {
            model.startWorkout()
            model.addExercise("Bench Press (Barbell)")
        }
        settle()
        go {
            val d = model.draft!!
            model.blockChanged(0, d.blocks[0], d.blocks[0].copy(items = sets.toList()))
        }
        settle()
    }

    private fun tick(index: Int, reps: String, weight: String = "60") = go {
        val d = model.draft!!
        val b = d.blocks[0]
        model.blockChanged(0, b, b.copy(items = b.items.mapIndexed { i, it -> if (i == index) (it as DraftSet).copy(weight = weight, reps = reps, done = true) else it }))
    }

    /** Whether [tag] showed up at any frame within [ms] after [act], the clock paused and stepped 16 ms at a time. */
    private fun appears(tag: String, ms: Int, act: () -> Unit): Boolean {
        compose.mainClock.autoAdvance = false
        act()
        var seen = false
        var t = 0
        while (t < ms) { compose.mainClock.advanceTimeBy(16); if (count(tag) > 0) seen = true; t += 16 }
        compose.mainClock.advanceTimeBy(2_000)
        val after = count(tag)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the mark did not go", 0, after)
        return seen
    }

    @Test fun aTriangleFloatsUpFromASetThatBeatLastTimeOnlyWhenItIsTicked() {
        val s = launch()
        startWith(DraftSet(), DraftSet(), DraftSet())
        assertEquals(0, count("beat rise"))
        assertFalse("equal reps floated a mark", appears("beat rise", 900) { tick(0, "8") })
        assertTrue("more reps did not float a mark", appears("beat rise", 900) { tick(1, "9") })
        assertFalse("a lighter weight with more reps floated a mark", appears("beat rise", 900) { tick(2, "12", "50") })
        s.close()
    }

    @Test fun noTriangleWithAnimationsOff() {
        val s = launch(motion = false)
        startWith(DraftSet(), DraftSet())
        assertFalse(appears("beat rise", 900) { tick(0, "12") })
        s.close()
    }

    // Compose's test rule cancels infinite animations (so a screen can go idle), so the pulse itself cannot be stepped frame by frame here: the tests
    // follow what switches it, the draft the Finish button reads, through the real screen, and that the button stays on it.
    private fun finishShown() = compose.onAllNodes(hasText("Finish"), useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.width > 0f }

    private fun change(edit: (List<DraftItem>) -> List<DraftItem>) = go {
        val b = model.draft!!.blocks[0]
        model.blockChanged(0, b, b.copy(items = edit(b.items)))
    }

    @Test fun theFinishButtonBreathesOnceEverySetIsDoneAndStopsWhenOneIsUndoneOrAdded() {
        val s = launch()
        startWith(DraftSet(weight = "60", reps = "8"), DraftSet(weight = "60", reps = "8"))
        assertTrue(finishShown())
        assertFalse(everySetDone(model.draft!!.blocks))
        tick(0, "8"); tick(1, "8")
        settle(300)
        assertTrue(everySetDone(model.draft!!.blocks))
        assertTrue(finishShown())
        shot("round17-finish-breath")
        change { items -> items.mapIndexed { i, it -> if (i == 1) (it as DraftSet).copy(done = false) else it } }
        settle(300)
        assertFalse("still breathing after an undo", everySetDone(model.draft!!.blocks))
        tick(1, "8")
        settle(300)
        assertTrue(everySetDone(model.draft!!.blocks))
        change { it + DraftSet() }
        settle(300)
        assertFalse("still breathing after a set was added", everySetDone(model.draft!!.blocks))
        assertTrue(finishShown())
        s.close()
    }

    @Test fun theFinishButtonIsPlainWithAnimationsOff() {
        val s = launch(motion = false)
        startWith(DraftSet(weight = "60", reps = "8"))
        tick(0, "8")
        settle(300)
        assertTrue(everySetDone(model.draft!!.blocks))
        assertTrue(finishShown())
        s.close()
    }

    // 15
    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun differing(a: Bitmap, b: Bitmap, r: Rect): Int {
        val pa = pixels(a); val pb = pixels(b)
        var n = 0
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(a.height)) for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(a.width)) {
            val p = pa[y * a.width + x]; val q = pb[y * a.width + x]
            if (Math.abs((p shr 16 and 255) - (q shr 16 and 255)) + Math.abs((p shr 8 and 255) - (q shr 8 and 255)) + Math.abs((p and 255) - (q and 255)) > 12) n++
        }
        return n
    }

    /** Drags the Profile page up in steps, holding the finger on it, and counts how many pixels of the Snapshots tile differ between the last step and the page at rest in the same place. */
    private fun tiltedPixels(): Int {
        val heading = compose.onAllNodesWithText("Photos", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width > 0f && it.top > 0f }
        compose.mainClock.autoAdvance = false
        root().performTouchInput { down(Offset(400f, 1500f)) }
        for (i in 1..3) { root().performTouchInput { moveBy(Offset(0f, -60f)) }; compose.mainClock.advanceTimeBy(16) }
        val moving = frame()
        val tile = compose.onAllNodesWithText("Photos", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.width > 0f && it.top > 0f }
        compose.mainClock.advanceTimeBy(2_000)
        val rest = frame()
        root().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return differing(moving, rest, Rect(0f, tile.bottom + 8f, 760f, tile.bottom + 8f + 320f)).also { assertTrue(heading.top > tile.top) }
    }

    @Test fun theSnapshotsCardsLeanWithTheScrollAndSettleFlat() {
        val s = launch()
        go { model.tab = Tab.Profile }
        settle()
        val n = tiltedPixels()
        shot("round17-profile-after-scroll")
        assertTrue("the cards did not lean while scrolling: $n", n > 10)
        s.close()
    }

    @Test fun theSnapshotsCardsStayFlatWithAnimationsOff() {
        val s = launch(motion = false)
        go { model.tab = Tab.Profile }
        settle()
        assertEquals(0, tiltedPixels())
        s.close()
    }

    // 11
    private fun addPrWorkout(kg: Int): String {
        val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""
        val date = java.time.LocalDateTime.now().withHour(7).withMinute(0).withSecond(0).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val bytes = (listOf(header, "\"99\";\"$date\";\"Push\";\"3600\";\"Bench Press (Barbell)\";\"1\";\"$kg.0\";\"10\";\"\";\"\";\"\";\"\";\"\"") ).joinToString("\n", postfix = "\n").toByteArray()
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext<android.app.Application>()))
            val id = model.activeId.value!!
            repo.commit(id, repo.previewWorkouts(id, bytes))
        }
        return date
    }

    private val prText = Regex("""(\d+(?:\.\d)?) kg × 10""")

    /** Every weight the PR line of the summary showed in [ms] after [act]. */
    private fun prWeights(ms: Int, act: () -> Unit): List<Double> {
        compose.mainClock.autoAdvance = false
        act()
        val out = ArrayList<Double>()
        var t = 0
        while (t < ms) {
            compose.mainClock.advanceTimeBy(16)
            for (n in compose.onAllNodes(hasText("kg × 10", substring = true), useUnmergedTree = true).fetchSemanticsNodes()) {
                val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                if (" · " in text) prText.find(text)?.let { out += it.groupValues[1].toDouble() }
            }
            t += 16
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return out
    }

    @Test fun aNewWeightPrCountsUpFromThePreviousBestOnceOnTheSummary() {
        val s = launch()
        val date = addPrWorkout(100)
        var w = 0
        while (model.workouts.value.none { it.date == date } && w++ < 400) { Thread.sleep(50); compose.waitForIdle() }
        assertTrue("workout not in: $date vs ${model.workouts.value.takeLast(3).map { it.date }}", model.workouts.value.any { it.date == date })
        compose.waitUntil(30_000) { model.prs.value.keys.any { it == date } }
        settle()
        val first = prWeights(3_000) { model.open(Screen.Summary(date)) }
        assertTrue("never shown: $first", first.isNotEmpty())
        assertEquals(100.0, first.last(), 0.0)
        assertTrue("did not start at the old best: ${first.distinct()}", first.first() in 60.0..70.0)
        assertTrue("did not count up: ${first.distinct()}", first.distinct().size >= 4)
        assertEquals("not climbing", first, first.sorted())
        shot("round17-pr-rolled")
        // Once: the summary opened again shows the weight at once.
        go { model.back() }
        settle()
        val again = prWeights(1_500) { model.open(Screen.Summary(date)) }
        assertEquals(listOf(100.0), again.distinct())
        s.close()
    }

    @Test fun aPrDoesNotCountUpWithAnimationsOff() {
        val s = launch(motion = false)
        val date = addPrWorkout(100)
        var w = 0
        while (model.workouts.value.none { it.date == date } && w++ < 400) { Thread.sleep(50); compose.waitForIdle() }
        assertTrue("workout not in: $date vs ${model.workouts.value.takeLast(3).map { it.date }}", model.workouts.value.any { it.date == date })
        compose.waitUntil(30_000) { model.prs.value.keys.any { it == date } }
        settle()
        assertEquals(listOf(100.0), prWeights(1_500) { model.open(Screen.Summary(date)) }.distinct())
        s.close()
    }
}
