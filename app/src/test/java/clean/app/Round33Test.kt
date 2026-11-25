package clean.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.android.*
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.open
import clean.core.SetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 33: the reorder drag scrolls the list only near the edges of what is seen (the top 56 dp of the list, the bottom 40 dp above the number pad or
 * the list's end), and Do next puts the new exercise before the current one when it has no set done, after it (and its superset) when it has.
 * The window is 400 x 800 dp at 1 dp = 1 px, so a position in the root is also in dp.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class Round33Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: AppModel
    private var launched: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeTheApp() { runCatching { launched?.close() }; launched = null }

    private fun launch() {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
            repo.dao.put(Kv("whatsnew.seen", "1.1.0"))
            repo.dao.put(Kv("ui.motion", "off"))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        launched = scenario
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded }
    }

    // ---- 1. The edge zones ----

    private val names = (1..25).map { "Lift %02d".format(it) }

    private fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeBy(16) }

    private fun listNode() = compose.onAllNodes(hasScrollToIndexAction())[0]
    private fun scrolled() = listNode().fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!!.value()

    /** Where the zones should be, in the root's px: the list's top, and the bottom of what is seen (the list's end, or the top of the number pad). */
    private fun edges(padUp: Boolean): Pair<Float, Float> {
        val list = listNode().fetchSemanticsNode().boundsInRoot
        val pad = if (padUp) compose.onNode(hasTestTag("number pad"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot else null
        if (pad != null) assertTrue("the pad covers the end of the list: ${pad.top} vs ${list.bottom}", pad.top < list.bottom - 100f)
        return list.top to (pad?.top ?: list.bottom)
    }

    private fun zones(padUp: Boolean) {
        launch()
        compose.runOnIdle {
            model.putDraft(Draft("Workout", System.currentTimeMillis(), names.map { DraftBlock(it, listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
            model.open(Screen.Active)
        }
        compose.waitForIdle()
        if (padUp) {
            compose.onAllNodes(hasContentDescription("weight", substring = true, ignoreCase = true), useUnmergedTree = true)[0].performClick()
            compose.waitForIdle()
        }
        val (top, bottom) = edges(padUp)
        val name = compose.onAllNodes(hasText(names[0]), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val x = name.center.x
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        root.performTouchInput {
            down(Offset(x, name.center.y))
            advanceEventTime(1000)
            move()
        }
        frames(20)
        val tag = if (padUp) "pad up" else "no pad"
        println("$tag: list top=$top visible bottom=$bottom start=${scrolled()}")

        /** Puts the finger at [y] and lets 25 frames pass: how far the list scrolled meanwhile. */
        fun at(y: Float, label: String): Float {
            root.performTouchInput { moveTo(Offset(x, y)) }
            val before = scrolled()
            frames(25)
            val after = scrolled()
            println("$tag: $label at y=$y: $before -> $after")
            return after - before
        }
        val bottomZone = REORDER_BOTTOM_ZONE.value
        val topZone = REORDER_TOP_ZONE.value
        // The finger the list sees trails the injected one by the drag's touch slop (about 16 dp here) in the direction it moves, so a probe is placed that much past the zone it means to be inside of.
        val slop = 18f
        // The old zone began 72 dp above the list's full bottom: with the pad up that is behind the pad, so the first two checks fail on the old code.
        assertEquals("$tag: above the bottom zone the list holds still", 0f, at(bottom - bottomZone - 5f, "just above the bottom zone"), 0f)
        assertTrue("$tag: just inside the bottom zone the list scrolls down", at(bottom - bottomZone + slop + 12f, "just inside the bottom zone") > 0f)
        assertTrue("$tag: at the visible edge it scrolls on", at(bottom - 2f, "at the visible edge") > 0f)
        // Out of the bottom zone: still; then into the top zone: the list scrolls back up.
        assertEquals("$tag: out of the zones the list holds still", 0f, at((top + bottom) / 2f, "the middle"), 0f)
        assertEquals("$tag: below the top zone the list holds still", 0f, at(top + topZone + 3f, "just below the top zone"), 0f)
        assertTrue("$tag: just inside the top zone the list scrolls up", at(top + topZone - slop - 12f, "just inside the top zone") < 0f)
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun theBottomZoneIsAtTheListsEndAndTheTopZoneJustBelowItsTop() = zones(padUp = false)

    @Test fun withTheNumberPadUpTheBottomZoneIsAtTheTopOfThePad() = zones(padUp = true)

    @Test fun thePushIsMeasuredFromTheVisibleEdge() {
        val top = 56f
        val bottom = 40f
        assertEquals(0f, reorderPush(300f, 700f, top, bottom), 0f)
        assertEquals(-1f, reorderPush(0f, 700f, top, bottom), 0f)
        assertEquals(-0.5f, reorderPush(28f, 700f, top, bottom), 0.001f)
        assertEquals(0f, reorderPush(659f, 700f, top, bottom), 0f)
        assertEquals(0.5f, reorderPush(680f, 700f, top, bottom), 0.001f)
        assertEquals(1f, reorderPush(900f, 700f, top, bottom), 0f)
        // With the pad up the visible bottom is higher, and so is the zone.
        assertEquals(0.5f, reorderPush(380f, 400f, top, bottom), 0.001f)
    }

    // ---- 2. Where Do next inserts ----

    private fun sets(done: Int, of: Int) = List(of) { DraftSet(SetType.NORMAL, "60", "8", done = it < done, ticked = if (it < done) it + 1L else 0L) }
    private fun block(name: String, done: Int, of: Int = 3, superset: String? = null) = DraftBlock(name, sets(done, of), superset = superset)
    private fun draft(vararg blocks: DraftBlock) = Draft("W", 0L, blocks.toList())
    private fun inserted(draft: Draft) = draft.blocks.toMutableList().also { it.add(doNextIndex(draft), block("NEW", 0)) }.map { it.exercise }

    @Test fun doNextGoesAfterTheCurrentExerciseWhenItIsStarted() {
        assertEquals(listOf("A", "B", "NEW", "C"), inserted(draft(block("A", 3), block("B", 1), block("C", 0))))
    }

    @Test fun doNextGoesBeforeTheCurrentExerciseWhenNoSetIsDone() {
        assertEquals(listOf("A", "NEW", "B", "C"), inserted(draft(block("A", 3), block("B", 0), block("C", 0))))
        assertEquals(listOf("NEW", "A", "B"), inserted(draft(block("A", 0), block("B", 0))))
    }

    @Test fun doNextGoesToTheEndWhenEveryCardIsDone() {
        assertEquals(listOf("A", "B", "NEW"), inserted(draft(block("A", 3), block("B", 3))))
        assertEquals(listOf("NEW"), inserted(draft()))
    }

    @Test fun aSupersetIsOneUnit() {
        // Started (a set of either member done): after the whole superset, though the first member may be done.
        assertEquals(listOf("A", "B", "NEW", "C"), inserted(draft(block("A", 3, superset = "s1"), block("B", 1, superset = "s1"), block("C", 0))))
        assertEquals(listOf("A", "B", "NEW", "C"), inserted(draft(block("A", 1, superset = "s1"), block("B", 0, superset = "s1"), block("C", 0))))
        // Not started: before its first member, not between the two.
        assertEquals(listOf("X", "NEW", "A", "B", "C"), inserted(draft(block("X", 3), block("A", 0, superset = "s1"), block("B", 0, superset = "s1"), block("C", 0))))
        // A finished superset is passed over.
        assertEquals(listOf("A", "B", "C", "NEW", "D"), inserted(draft(block("A", 3, superset = "s1"), block("B", 3, superset = "s1"), block("C", 2), block("D", 0))))
    }

    @Test fun addExerciseWithAfterCurrentFollowsTheRule() {
        launch()
        fun go(d: Draft): List<String> {
            compose.runOnIdle { model.putDraft(d); model.addExercise("Squat (Barbell)", afterCurrent = true) }
            return model.draft!!.blocks.map { it.exercise }
        }
        assertEquals(listOf("A", "B", "Squat (Barbell)", "C"), go(draft(block("A", 3), block("B", 2), block("C", 0))))
        assertEquals(listOf("A", "Squat (Barbell)", "B", "C"), go(draft(block("A", 3), block("B", 0), block("C", 0))))
        assertEquals(listOf("A", "B", "Squat (Barbell)"), go(draft(block("A", 3), block("B", 3))))
        assertEquals(listOf("A", "B", "Squat (Barbell)", "C"), go(draft(block("A", 1, superset = "s"), block("B", 0, superset = "s"), block("C", 0))))
        // Without afterCurrent the queue's end, as before.
        compose.runOnIdle { model.putDraft(draft(block("A", 0), block("B", 1))); model.addExercise("Squat (Barbell)") }
        assertEquals(listOf("A", "B", "Squat (Barbell)"), model.draft!!.blocks.map { it.exercise })
    }
}
