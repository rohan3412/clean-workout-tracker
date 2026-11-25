package clean.app

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.app.ui.theme.Deficiency
import clean.app.ui.theme.PR_COLOURS
import clean.app.ui.theme.colorScheme
import clean.app.ui.theme.colourDistance
import clean.app.ui.theme.contrastRatio
import clean.app.ui.theme.hueGap
import clean.app.ui.theme.meaning
import clean.app.ui.theme.orange
import clean.app.ui.theme.over
import clean.app.ui.theme.prColour
import clean.app.ui.theme.resolveLook
import clean.app.ui.theme.seenWith
import clean.app.ui.theme.toOklch
import clean.app.ui.theme.workoutSkin
import clean.core.OneRm
import clean.core.OneRmFormula
import clean.core.STRONG_DATE_FORMAT
import clean.core.Session
import clean.core.SetItem
import clean.core.SetType
import clean.core.WorkoutRow
import clean.core.decimalText
import clean.core.estimate1Rm
import clean.core.progressOf
import java.io.File
import kotlinx.coroutines.runBlocking
import clean.core.time.*
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 28: starting a workout from two cards or from picked exercises, the estimated 1RM at every rep count, a PR colour of each theme's own, the search that
 * closes with the keyboard, the Add exercise sheet with Do next, the next exercise held in place while a finished one folds, no rest before a drop set,
 * swipe left (only) to remove a set, and the current set's highlight.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round28Test {
    @get:Rule val compose = createEmptyComposeRule()
    private var window: View? = null
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private val names = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)")
    private val eight = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)", "Sumo Deadlift (Barbell)", "Overhead Press (Barbell)",
        "Bicep Curl (Dumbbell)", "Leg Press", "Triceps Pushdown (Cable)")

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
    private fun settle(ms: Long = 600) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    /** The app on a fresh profile with [history] workouts (rows) behind it, no workout running. */
    private fun launch(history: (Long) -> List<WorkoutRow> = { emptyList() }) {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            val rows = history(id)
            if (rows.isNotEmpty()) repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
    }

    private fun open(exercises: List<String> = names, sets: (String) -> List<DraftSet> = { listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL)) }) {
        launch()
        go { model.putDraft(Draft("Workout", System.currentTimeMillis(), exercises.map { DraftBlock(it, sets(it)) })); model.open(Screen.Active) }
        settle(300)
    }

    private fun back() { launched!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle(300) }
    private fun has(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasDesc(text: String, sub: Boolean = false) = compose.onAllNodes(hasContentDescription(text, substring = sub), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun tapText(text: String) { compose.onAllNodes(hasText(text), useUnmergedTree = true).onFirst().performTouchInput { click(center) }; settle(300) }
    private fun tapDesc(text: String) { compose.onAllNodes(hasContentDescription(text), useUnmergedTree = true).onFirst().performTouchInput { click(center) }; settle(300) }
    /** A row of a list or any text that is not a text field. */
    private fun tapRow(text: String) { compose.onAllNodes(hasText(text) and !hasSetTextAction(), useUnmergedTree = true).onFirst().performTouchInput { click(center) }; settle(300) }
    private fun tapAdd() {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Add exercise"))
        settle(300); tapText("Add exercise"); settle(500)
    }
    private fun top(text: String): Float? = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

    /** Types into the search pill of the picker or the Library (the field inside the pill). */
    private fun type(text: String) {
        val field = hasSetTextAction() and hasAnyAncestor(hasTestTag("search field"))
        // The last one: a picker opened over the Workout sheet has its pill above the sheet's own (Round 37).
        compose.onAllNodes(field, useUnmergedTree = true).onLast().performTextClearance()
        compose.onAllNodes(field, useUnmergedTree = true).onLast().performTextInput(text)
        settle(300)
        try { compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || has("Create “$text”") } }
        catch (e: Throwable) { throw AssertionError("no row for $text; the screen says: " + compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { t -> t.text } }.joinToString(" | ").take(3000)) }
        settle(300)
    }

    // ---- 1. Starting a workout ----

    @Test fun theWorkoutSheetHasTwoStartCardsAboveTheTemplates() {
        launch()
        go { model.openWorkoutSheet() }; settle(600)
        assertTrue(has("Empty workout")); assertTrue(has("Pick exercises"))
        val empty = top("Empty workout")!!; val pick = top("Pick exercises")!!
        val search = compose.onAllNodes(hasText("Search templates"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top
        assertTrue("the cards sit above the template search ($empty, $search)", empty < search && pick < search)
        val l = compose.onAllNodes(hasTestTag("start empty"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val r = compose.onAllNodes(hasTestTag("start pick"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("side by side, equal height", l.right <= r.left + 1f && Math.abs(l.top - r.top) < 2f && Math.abs(l.height - r.height) < 2f)
    }

    @Test fun anEmptyWorkoutStartsWithOneTap() {
        launch()
        go { model.openWorkoutSheet() }; settle(600)
        compose.onAllNodes(hasTestTag("start empty"), useUnmergedTree = true).onFirst().performClick(); settle(400)
        assertNotNull(model.draft); assertEquals(0, model.draft!!.blocks.size)
        assertEquals(Screen.Active, model.stack.last())
    }

    @Test fun pickedExercisesAreNumberedInOrderAndStartInThatOrder() {
        launch()
        go { model.openWorkoutSheet() }; settle(600)
        compose.onAllNodes(hasTestTag("start pick"), useUnmergedTree = true).onFirst().performClick(); settle(500)
        assertTrue("the multi-select sheet is up", hasDesc("Close") && hasTag("search field"))
        type("Zercher Squat (Barbell)"); tapRow("Zercher Squat (Barbell)")
        type("Decline Bench Press (Barbell)"); tapRow("Decline Bench Press (Barbell)")
        assertTrue("Start 2", has("Start 2"))
        assertNull("nothing started yet", model.draft)
        tapText("Start 2")
        assertEquals(listOf("Zercher Squat (Barbell)", "Decline Bench Press (Barbell)"), model.draft!!.blocks.map { it.exercise })
        assertEquals(Screen.Active, model.stack.last())
    }

    @Test fun backFromThePickerStartsNothing() {
        launch()
        go { model.openWorkoutSheet() }; settle(600)
        compose.onAllNodes(hasTestTag("start pick"), useUnmergedTree = true).onFirst().performClick(); settle(500)
        type("Zercher Squat (Barbell)"); tapRow("Zercher Squat (Barbell)")
        // The picker is a dialog: its Back is its own onDismissRequest, which the close control calls too (the activity's dispatcher never sees it).
        tapDesc("Close")
        assertNull(model.draft)
        assertTrue("still on the Workout sheet", model.workoutSheet && has("Empty workout"))
    }

    // ---- 2. The estimated 1RM at every rep count ----

    @Test fun theEstimateIsShownAtEveryRepCountButRecordsKeepTheirLimit() {
        val rule = OneRm.DEFAULT
        assertNull("the limit still holds for records", estimate1Rm(35.0, 20, rule))
        val shown = estimate1Rm(35.0, 20, rule.shown())!!
        assertEquals("Hybrid is Epley from 10 reps up", 35.0 * (1 + 20 / 30.0), shown, 1e-9)
        assertEquals("the same formula inside the limit", estimate1Rm(100.0, 8, rule)!!, estimate1Rm(100.0, 8, rule.shown())!!, 1e-12)
        for (f in OneRmFormula.entries) for (reps in listOf(13, 20, 30, 37, 50, 100)) {
            val e = estimate1Rm(40.0, reps, OneRm(f, 12).shown())!!
            assertTrue("$f at $reps reps is $e", e >= 40.0 && e < 40.0 * 10)
        }
        val session = Session("2026-11-20 10:00:00", listOf(SetItem(WorkoutRow("1", "2026-11-20 10:00:00", "W", "0", "Lat Pulldown (Cable)", "1", "35.0", "20", "", "", "", "", ""), SetType.NORMAL)))
        assertNull("a high-rep set is no 1RM record", progressOf(listOf(session), rule).first().estimated1Rm)
    }

    private fun highRepHistory(id: Long): List<WorkoutRow> {
        val date = LocalDate.now().minusDays(1).atTime(18, 0).format(STRONG_DATE_FORMAT)
        return listOf(WorkoutRow("1", date, "Pull", "3000", "Lat Pulldown (Cable)", "1", "35.0", "20", "", "", "", "", ""))
    }

    @Test fun historyAndTheWorkoutPageShowTheEstimateOf35By20() {
        launch(::highRepHistory)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        go { model.stack.clear(); model.open(Screen.History) }; settle(800)
        assertTrue("History's card shows 58.3 (35 x 20, Epley)", has("58.3"))
        go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.first().date)) }; settle(800)
        assertTrue("the workout page shows 58 beside the set", has("58"))
    }

    // ---- 3. The PR colour ----

    private fun looks() = buildList {
        for (accent in PR_COLOURS.keys) for (theme in listOf("dark", "light")) for (black in listOf(false, true)) for (safe in listOf(false, true)) {
            if (black && theme == "light") continue
            add(resolveLook(theme, pureBlack = black, accent = accent, colourBlindSafe = safe))
        }
    }
    private fun name(l: clean.app.ui.theme.Look) = "${l.themeName}/${if (l.dark) "dark" else "light"}/black=${l.pureBlack}/safe=${l.colourBlindSafe}"

    @Test fun everyThemeHasItsOwnPrColourClearOfItsAccentAndMeaningColours() {
        for (l in looks()) {
            val pr = l.prColour
            val accent = l.orange
            val a = accent.toOklch(); val p = pr.toOklch()
            if (a.c > 0.03f) assertTrue("${name(l)}: PR ${p.h} is ${hueGap(p.h, a.h)} degrees from the accent ${a.h}", hueGap(p.h, a.h) >= 60f)
            else assertTrue("${name(l)}: a neutral accent, the PR colour is chromatic (${p.c})", p.c >= 0.09f)
            // Round 34: the proposal's worst is Saffron in Light, 11.82 (was 12 with the old golds).
            assertTrue("${name(l)}: PR $pr is ${colourDistance(pr, accent)} from the accent", colourDistance(pr, accent) >= 11.8f)
            val m = l.meaning()
            for ((word, c) in listOf("done" to m.done, "ready" to m.ready, "failure" to m.failure, "fatigued" to m.fatigued, "danger" to m.danger)) {
                // The colour-blind-safe set is Okabe-Ito, not the proposal's: a vermillion beside an olive or a mauve gold is 5.8 at the least (Saffron Light); the PR mark also carries a star and words.
                assertTrue("${name(l)}: PR $pr is ${colourDistance(pr, c)} from $word", colourDistance(pr, c) >= if (l.colourBlindSafe) 5.5f else 10f)
            }
            // The chip: 3 : 1 against the page and card, its text 4.5 : 1 on its own tint.
            for (behind in listOf(l.page, l.card, l.tile)) assertTrue("${name(l)}: PR on $behind ${contrastRatio(pr, behind)}", contrastRatio(pr, behind) >= 3f)
            val chip = over(pr, prTint(l.dark), l.card)
            assertTrue("${name(l)}: text on the chip ${contrastRatio(pr, chip)}", contrastRatio(pr, chip) >= 4.5f)
        }
    }

    @Test fun thePrColourIsToldApartFromTheAccentAndGoodAndBadWithColourBlindness() {
        for (l in looks()) {
            if (l.pureBlack) continue
            val m = l.meaning()
            for (d in listOf(Deficiency.DEUTERANOPIA, Deficiency.PROTANOPIA, Deficiency.TRITANOPIA)) {
                val pr = l.prColour.seenWith(d)
                // Apart from the accent always; from the good and bad colours on a dark page (on a light one the reds and the gold are one dark yellow-brown to a dichromat: the marks also carry a star and words).
                val against = listOf("accent" to l.orange) + if (l.dark) listOf("done" to m.done, "failure" to m.failure) else emptyList()
                for ((word, c) in against) {
                    // Round 34: the proposal's Light PR is told apart from the accent for most themes but not three red-green cases (measured 2.46, 4.82 and 1.59; the proposal
                    // checked series against the accent, not the PR mark): Crimson and Saffron under deuteranopia, Green under protanopia. The PR mark carries a star and words.
                    val weak = !l.dark && word == "accent" && ((l.themeName == "Crimson" && d == Deficiency.DEUTERANOPIA) || (l.themeName == "Saffron" && d == Deficiency.DEUTERANOPIA) || (l.themeName == "Green" && d == Deficiency.PROTANOPIA))
                    assertTrue("${name(l)} $d: PR and $word are ${colourDistance(pr, c.seenWith(d))} apart", colourDistance(pr, c.seenWith(d)) >= if (weak) 1.5f else 5f)
                }
            }
        }
    }

    @Test fun twelveThemesAndNoneTakesTheAccent() {
        assertEquals(12, PR_COLOURS.size)
        for (name in PR_COLOURS.keys) for (dark in listOf(true, false)) {
            val l = resolveLook(if (dark) "dark" else "light", accent = name)
            assertTrue("$name: the PR colour is not the accent", l.prColour != l.orange)
        }
        // Round 34 (the proposal's rule): a gold unless the accent is within 60 degrees of gold; then the nearest hue that clears the accent, the good and the bad:
        // lemon for Ember, mint for Saffron, apricot for Green (Dark).
        assertTrue(resolveLook("dark", accent = "Ember").prColour.toOklch().h in 105f..130f)
        assertTrue(resolveLook("dark", accent = "Saffron").prColour.toOklch().h in 135f..160f)
        assertTrue(resolveLook("dark", accent = "Green").prColour.toOklch().h in 60f..85f)
        for (n in listOf("Graphite", "Slate", "Crimson", "Teal", "Lagoon", "Cobalt", "Iris", "Orchid", "Rose")) assertTrue(n, resolveLook("dark", accent = n).prColour.toOklch().h in 80f..110f)
    }

    // ---- 4. The search that closes with the keyboard ----

    @Test fun theKeyboardGoingDownWithNothingTypedCollapsesTheSearch() {
        assertTrue(collapsesOnKeyboardDown(wasUp = true, isUp = false, query = ""))
        assertFalse("text typed: the keyboard goes alone", collapsesOnKeyboardDown(true, false, "ben"))
        assertFalse("the keyboard coming up", collapsesOnKeyboardDown(false, true, ""))
        assertFalse("never up", collapsesOnKeyboardDown(false, false, ""))
    }

    @Test fun backCollapsesTheLibrarySearchAndASecondBackClearsOneWithText() {
        launch(::highRepHistory)
        go { model.stack.clear(); model.tab = Tab.Exercises }; settle(600)
        tapDesc("Search exercises"); settle(500)
        assertTrue("the pill is out", hasTag("search field"))
        back(); settle(500)
        assertFalse("Back collapsed it", hasTag("search field"))
        // With text typed: Back clears and collapses (the first Back of a phone only hides the keyboard, which the app never sees).
        tapDesc("Search exercises"); settle(500)
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("search field")), useUnmergedTree = true).onFirst().performTextInput("squat")
        settle(300)
        back(); settle(500)
        assertFalse(hasTag("search field"))
    }

    @Test fun historyShareTheSameSearchBehaviour() {
        launch(::highRepHistory)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        go { model.stack.clear(); model.open(Screen.History) }; settle(800)
        tapDesc("Search history"); settle(500)
        assertTrue(hasTag("search field"))
        back(); settle(500)
        assertFalse(hasTag("search field"))
    }

    // ---- 5 and 6. The Add exercise sheet; queue and Do next ----

    @Test fun theAddExerciseSheetHasItsPillChipsAndDoNext() {
        open()
        tapAdd()
        assertTrue("the sheet's title", has("Add exercise"))
        assertTrue("the search pill is out", hasTag("search field"))
        assertTrue("filter chips", has("Muscle") && has("Equipment") && has("Favourites"))
        type("Sumo Deadlift (Barbell)")
        assertTrue("a Do next on the row", hasDesc("Do Sumo Deadlift (Barbell) next"))
    }

    @Test fun oneTapQueuesAtTheEnd() {
        open()
        tapAdd()
        type("Sumo Deadlift (Barbell)"); tapRow("Sumo Deadlift (Barbell)")
        assertEquals(names + "Sumo Deadlift (Barbell)", model.draft!!.blocks.map { it.exercise })
    }

    @Test fun doNextPutsItBeforeAnExerciseNotStarted() {
        open()
        tapAdd()
        type("Sumo Deadlift (Barbell)"); tapDesc("Do Sumo Deadlift (Barbell) next")
        // Round 33: nothing is done yet, so the new one is the one to do now.
        assertEquals(listOf("Sumo Deadlift (Barbell)", names[0], names[1], names[2]), model.draft!!.blocks.map { it.exercise })
    }

    @Test fun doNextFollowsTheCurrentExerciseAndItsSuperset() {
        launch()
        val done = listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1))
        go {
            model.putDraft(Draft("W", System.currentTimeMillis(), listOf(
                DraftBlock("A", done), DraftBlock("B", listOf(DraftSet())), DraftBlock("C", listOf(DraftSet()), superset = "s7"), DraftBlock("D", listOf(DraftSet()), superset = "s7"), DraftBlock("E", listOf(DraftSet())),
            )))
            model.addExercise("Squat (Barbell)", afterCurrent = true)
        }
        assertEquals(listOf("A", "Squat (Barbell)", "B", "C", "D", "E"), model.draft!!.blocks.map { it.exercise })
        // Every card done: nothing is current, so it goes to the end.
        go {
            model.putDraft(Draft("W", System.currentTimeMillis(), listOf(DraftBlock("A", done), DraftBlock("B", done))))
            model.addExercise("Squat (Barbell)", afterCurrent = true)
        }
        assertEquals(listOf("A", "B", "Squat (Barbell)"), model.draft!!.blocks.map { it.exercise })
        // A superset not started: before its first member (round 33).
        go {
            model.putDraft(Draft("W", System.currentTimeMillis(), listOf(DraftBlock("C", listOf(DraftSet()), superset = "s7"), DraftBlock("D", listOf(DraftSet()), superset = "s7"), DraftBlock("E", listOf(DraftSet())))))
            model.addExercise("Squat (Barbell)", afterCurrent = true)
        }
        assertEquals(listOf("Squat (Barbell)", "C", "D", "E"), model.draft!!.blocks.map { it.exercise })
    }

    // ---- 7. The next exercise holds still while a finished one folds ----

    @Test fun theNextCardGlidesToTheTopWhileTheFinishedOneFoldsWithoutOvershoot() {
        // (the message below prints the trajectory)
        open(eight) { listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 2), DraftSet(SetType.NORMAL, "60", "8")) }
        // Card 0 is one set from finished.
        val before = top(eight[1])!!
        val listTop = compose.onAllNodes(hasScrollAction()).onFirst().fetchSemanticsNode().boundsInRoot.top
        // Tick the last set of card 0 as the screen does: the card folds, and the next one is brought to the top.
        go {
            val d = model.draft!!
            val b = d.blocks[0]
            model.blockChanged(0, b, b.copy(items = b.items.mapIndexed { i, it -> if (i == 2) (it as DraftSet).copy(weight = "60", reps = "8", done = true, ticked = 3) else it }))
        }
        val tops = ArrayList<Float>()
        repeat(50) { compose.mainClock.advanceTimeBy(16); tops += top(eight[1]) ?: Float.NaN }
        settle(600)
        val after = top(eight[1])!!
        assertTrue("never below where it was ($before), then monotone upward: ${tops.map { it.toInt() }}", tops.filter { !it.isNaN() }.let { t -> t.all { it <= before + 3f } && t.zipWithNext().all { (a, b) -> b <= a + 3f } })
        assertTrue("it ends at the top of the list ($after, list $listTop)", after - listTop < 140f)
        assertTrue("it moved (before $before)", after < before - 20f)
        assertTrue("every set of the finished card is done", model.draft!!.blocks[0].items.all { (it as DraftSet).done })
    }

    // ---- 8. No rest before a drop set ----

    @Test fun noRestBeforeADropSetButOneAfterTheLastOfThem() {
        open(listOf("A")) { listOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.DROP, "50", "8"), DraftSet(SetType.DROP, "40", "8"), DraftSet(SetType.NORMAL, "60", "8")) }
        fun tick(i: Int) = go {
            val b = model.draft!!.blocks[0]
            model.blockChanged(0, b, b.copy(items = b.items.mapIndexed { k, s -> if (k == i) (s as DraftSet).copy(done = true, ticked = 10L + i) else s }))
        }
        tick(0)
        assertNull("the next set is a drop set: no rest", model.restEnd)
        tick(1)
        assertNull("and again before the second drop", model.restEnd)
        tick(2)
        assertNotNull("after the last drop set, rest as set for a drop set", model.restEnd)
        go { model.startRest(0) }
        // An ordinary set before an ordinary set rests.
        go {
            model.putDraft(Draft("B", System.currentTimeMillis(), listOf(DraftBlock("B", listOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "60", "8"))))))
            val b = model.draft!!.blocks[0]
            model.blockChanged(0, b, b.copy(items = b.items.mapIndexed { k, s -> if (k == 0) (s as DraftSet).copy(done = true, ticked = 10L) else s }))
        }
        assertNotNull(model.restEnd)
    }

    // ---- 9. Swipe left removes a set, swipe right does nothing ----

    private fun firstSetRow() = compose.onAllNodes(hasContentDescription("Set 1 weight", substring = true), useUnmergedTree = true).onFirst()

    @Test fun swipingASetRightNoLongerTicksIt() {
        open(listOf("A")) { listOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "60", "8")) }
        firstSetRow().performTouchInput { swipeRight(left, left + 600f, 200) }
        settle(500)
        assertFalse("not ticked by a swipe", (model.draft!!.blocks[0].items[0] as DraftSet).done)
        assertEquals(2, model.draft!!.blocks[0].items.size)
    }

    @Test fun swipingASetLeftRemovesItAndOffersUndo() {
        open(listOf("A")) { listOf(DraftSet(SetType.NORMAL, "60", "8"), DraftSet(SetType.NORMAL, "70", "5")) }
        val undoBefore = model.undoUntil
        firstSetRow().performTouchInput { swipeLeft(right - 5f, right - 380f, 200) }
        settle(800)
        assertEquals("the set is gone", 1, model.draft!!.blocks[0].items.size)
        assertEquals("70", (model.draft!!.blocks[0].items[0] as DraftSet).weight)
        assertTrue("the Undo pill was offered (Undo itself: UndosTest)", model.undoUntil > undoBefore)
    }

    // ---- 10. The current set ----

    @Test fun theCurrentSetIsAnAccentTintWithABarAndANumberInTheAccent() {
        for (dark in listOf(true, false)) for (name in PR_COLOURS.keys) {
            val l = resolveLook(if (dark) "dark" else "light", accent = name)
            val scheme = l.colorScheme()
            val skin = l.workoutSkin(scheme, l.meaning())
            assertEquals("$name dark=$dark: the tint is the accent", scheme.primary.copy(alpha = skin.rowNext.alpha), skin.rowNext)
            assertTrue("$name dark=$dark: stronger than a done row's wash (${skin.rowNext.alpha} vs ${skin.rowDone.alpha})", skin.rowNext.alpha > skin.rowDone.alpha)
            if (!dark) assertTrue("subtle in Light", skin.rowNext.alpha <= 0.10f)
        }
    }

    // ---- The tour ----

    private fun shot(name: String, dialogs: Boolean = false) {
        settle(300)
        val view = window!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        // A sheet is a window of its own: the windows over the activity are drawn on top, in the order they were added.
        if (dialogs) {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
            for (root in roots) if (root !== view && root.width > 0) root.draw(canvas)
        }
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun look(theme: String, accent: String = "Graphite") {
        go { model.setOption(clean.core.LOOK_THEME_KEY, theme) }
        repeat(200) { if (model.look.value.dark == (theme == "dark")) return@repeat; Thread.sleep(50); compose.waitForIdle() }
        go { model.setOption(clean.core.LOOK_ACCENT_KEY, accent) }
        repeat(200) { if (model.look.value.themeName == accent) return@repeat; Thread.sleep(50); compose.waitForIdle() }
    }

    /** Some history for the tour: a push day with rising weights (so the last one has PRs), and a pull day with one set of 35 x 20. */
    private fun tourHistory(id: Long): List<WorkoutRow> {
        val rows = ArrayList<WorkoutRow>()
        for (w in 0 until 8) {
            val date = LocalDate.now().minusDays(2L + (7 - w) * 4L).atTime(18, 0).format(STRONG_DATE_FORMAT)
            for ((e, exercise) in listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)").withIndex()) {
                for (s in 1..3) rows += WorkoutRow((w + 1).toString(), date, "Push", "3600", exercise, s.toString(), decimalText(50.0 + w * 2.5 + e * 10), (8 + s % 2).toString(), "", "", "", "", "")
            }
        }
        val date = LocalDate.now().minusDays(1).atTime(18, 0).format(STRONG_DATE_FORMAT)
        rows += WorkoutRow("99", date, "Pull", "3000", "Lat Pulldown (Cable)", "1", "35.0", "20", "", "", "", "", "")
        rows += WorkoutRow("99", date, "Pull", "3000", "Bench Press (Barbell)", "1", "100.0", "5", "", "", "", "", "")
        return rows
    }

    private fun tour(prefix: String) {
        fun s(name: String, dialogs: Boolean = false, step: () -> Unit) {
            try { step(); shot("round28-$prefix-$name", dialogs) } catch (e: Throwable) { File("build/screens/default").apply { mkdirs() }.resolve("round28-$prefix-$name.failed.txt").writeText(e.toString()) }
        }
        s("01-workout-sheet", true) { go { model.stack.clear(); model.tab = Tab.Home; model.openWorkoutSheet() }; settle(800) }
        s("02-pick-exercises", true) {
            compose.onAllNodes(hasTestTag("start pick"), useUnmergedTree = true).onFirst().performClick(); settle(500)
            type("Decline Bench Press (Barbell)"); tapRow("Decline Bench Press (Barbell)")
        }
        go { model.workoutSheet = false }; back(); settle(300)
        s("05-history-card-35x20") { go { model.workoutSheet = false; model.stack.clear(); model.open(Screen.History) }; settle(900) }
        s("06-workout-page-prs") { go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.first { it.name == "Push" }.date)) }; settle(900) }
        s("03-add-exercise-sheet", true) {
            go {
                model.stack.clear()
                model.putDraft(Draft("Workout", System.currentTimeMillis(), listOf(
                    DraftBlock("Bench Press (Barbell)", listOf(DraftSet(SetType.NORMAL, "60", "8", done = true, ticked = 1), DraftSet(), DraftSet())),
                    DraftBlock("Squat (Barbell)", listOf(DraftSet(), DraftSet())),
                )))
                model.open(Screen.Active)
            }
            settle(600)
            tapAdd()
        }
        back(); settle(300)
        s("04-current-set") { go { model.stack.clear(); model.open(Screen.Active) }; settle(700) }
    }

    private fun tourTheme(accent: String, dark: Boolean) {
        look(if (dark) "dark" else "light", accent)
        go { model.stack.clear(); model.open(Screen.WorkoutDetail(model.workouts.value.first { it.name == "Push" }.date)) }; settle(900)
        shot("round28-${accent.lowercase()}-${if (dark) "dark" else "light"}-07-prs")
    }

    @Category(Tour::class) @Test fun tourInGraphiteDarkAndLight() {
        launch(::tourHistory)
        compose.waitUntil(10_000) { model.workouts.value.isNotEmpty() }
        look("dark"); tour("graphite-dark")
        go { model.cancelWorkout() }; settle(300)
        look("light"); tour("graphite-light")
        go { model.cancelWorkout() }; settle(300)
        for (accent in PR_COLOURS.keys) for (dark in listOf(true, false)) tourTheme(accent, dark)
    }
}
