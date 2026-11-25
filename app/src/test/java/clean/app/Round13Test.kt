package clean.app

import org.junit.experimental.categories.Category
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Round 13 on the real app: the one day card on the Calendar page, the hold peek on Progress's History card, the rest bar over the
 * workout and the Undo pill's swipe.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round13Test {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)")
    private lateinit var model: AppModel
    private var window: View? = null

    /** A few weeks of workouts, the last of them today, so today's square has a workout to show. */
    private fun seed() = runBlocking {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("glass.reduce", "on"))
        val rows = ArrayList<WorkoutRow>()
        var n = 0
        for (back in listOf(12L, 9L, 6L, 3L, 0L)) {
            n++
            val date = LocalDate.now().minusDays(back).atTime(7, 30).format(STRONG_DATE_FORMAT)
            for (e in 0 until 3) {
                val exercise = names[e]
                rows += WorkoutRow(n.toString(), date, "Push", "3900", exercise, "W", "20.0", "10", "", "", "", "", "")
                for (s in 1..3) rows += WorkoutRow(n.toString(), date, "Push", "3900", exercise, s.toString(), (40.0 + n * 2.5 + e * 5).toString(), "8", "", "", "", "", "")
            }
        }
        repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
    }

    private fun launch(): ActivityScenario<MainActivity> {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(30_000) { model.workouts.value.isNotEmpty() && model.consistency.value != null }
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)

    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    /** The first node with this text that is in the window (the other tabs are composed off to the side). */
    private fun onScreen(text: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        val all = compose.onAllNodesWithText(text)
        val at = all.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.width > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 800f && it.boundsInRoot.top >= 0f }
        check(at >= 0) { "no '$text' on screen: " + all.fetchSemanticsNodes().map { it.boundsInRoot } }
        return all[at]
    }

    private fun draft() = Draft("Push", System.currentTimeMillis() - 20 * 60_000, names.map { DraftBlock(it, listOf(DraftSet(SetType.WARMUP), DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) })

    @Test fun `a tap on a day opens the one card with volume, PRs and exercises, and a workout in it opens`() {
        val scenario = launch()
        go { model.stack.clear(); model.open(Screen.Calendar()) }
        settle()
        // Today's square is the last of its number on the page.
        val today = LocalDate.now().dayOfMonth.toString()
        val squares = compose.onAllNodesWithText(today)
        squares[squares.fetchSemanticsNodes().lastIndex].performClick()
        settle()
        compose.onNodeWithText("Log a workout").assertExists()
        compose.onNodeWithText("Push").assertExists()
        // "HH:mm · length · volume · N PRs", then the exercises on one line.
        compose.onNodeWithText("07:30", substring = true).assertExists()
        compose.onNodeWithText("PR", substring = true).assertExists()
        compose.onNodeWithText("Bench Press (Barbell), Squat (Barbell), Lat Pulldown (Cable)").assertExists()
        compose.onNodeWithText("Push").performClick()
        settle()
        val opened = model.stack.last()
        assertTrue("opened $opened", opened is Screen.WorkoutDetail)
        scenario.close()
    }

    @Test fun `a hold on a row of Progress's History card peeks at that workout and a tap still opens History`() {
        val scenario = launch()
        go { model.stack.clear(); model.tab = Tab.Progress }
        settle()
        // The row's own words are the name and the date; the peek adds the time of day, which nothing else on the page shows.
        assertEquals(0, compose.onAllNodesWithText("07:30", substring = true).fetchSemanticsNodes().size)
        val row = onScreen("Push")
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertTrue("no peek; row at ${row.fetchSemanticsNode().boundsInRoot}, held=${model.holds.hold}", compose.onAllNodesWithText("07:30", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        settle()
        assertEquals("the peek stayed", 0, compose.onAllNodesWithText("07:30", substring = true).fetchSemanticsNodes().size)
        // The hold did not open anything; a tap on the row opens the card's page as it always did.
        assertNull(model.stack.lastOrNull())
        onScreen("Push").performClick()
        settle()
        assertEquals(Screen.History, model.stack.last())
        scenario.close()
    }

    @Test fun `the rest bar shows on a rest, leaves the last row clear and its buttons work`() {
        val scenario = launch()
        // The test clock must not run ahead through the whole rest on its own.
        compose.mainClock.autoAdvance = false
        go { model.stack.clear(); model.putDraft(draft()); model.open(Screen.Active) }
        settle()
        compose.onAllNodesWithText("−15 s").assertCountEquals(0)
        go { model.startRest(90) }
        settle()
        check(model.restEnd != null) { "no rest" }
        check(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("rest bar"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodes(androidx.compose.ui.test.hasText("Rest", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) { "nothing of the rest shows; stack=${model.stack} draft=${model.draft != null}" }
        compose.onNodeWithTag("rest bar").assertExists()
        compose.onNodeWithText("Skip").assertExists()
        // Scrolled to the end, the last row of the list is above the bar, not under it.
        repeat(6) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.2f) } }
        settle()
        val bar = compose.onNodeWithTag("rest bar").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("Add exercise").fetchSemanticsNode().boundsInRoot
        assertTrue("the last row ($last) is under the bar ($bar)", last.bottom <= bar.top)
        val before = model.restEnd!!
        compose.onNodeWithText("+15 s").performClick(); settle(100)
        val plus = model.restEnd!!
        assertTrue("+15 s: $before -> $plus", plus - before in 14_000..16_000)
        compose.onNodeWithText("−15 s").performClick(); settle(100)
        assertTrue("-15 s", plus - model.restEnd!! in 14_000..16_000)
        compose.onNodeWithText("Skip").performClick(); settle()
        assertNull(model.restEnd)
        compose.onAllNodesWithText("Skip").assertCountEquals(0)
        scenario.close()
    }

    @Test fun `swiping the rest bar down hides it for this rest and the pill brings it back`() {
        val scenario = launch()
        compose.mainClock.autoAdvance = false
        go { model.stack.clear(); model.putDraft(draft()); model.open(Screen.Active); model.startRest(120) }
        settle()
        compose.onNodeWithTag("rest bar").performTouchInput { down(center); moveBy(Offset(0f, 150f)); moveBy(Offset(0f, 250f)); up() }
        settle()
        compose.onAllNodesWithText("Skip").assertCountEquals(0)
        assertNotNull("the rest went on", model.restEnd)
        // The pill, not the top bar's own "Rest 1:59" (which has no tap for this): its label says what a tap does.
        val pill = androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasText("Rest ", substring = true)
        compose.onAllNodes(pill).onFirst().performClick()
        settle()
        compose.onNodeWithTag("rest bar").assertExists()
        scenario.close()
    }

    @Test fun `a swipe drops the Undo pill without taking back, and Undo still takes back`() {
        val scenario = launch()
        // The test clock must not run ahead to the pill's 8 s time-out on its own.
        compose.mainClock.autoAdvance = false
        var taken = 0
        var gone = 0
        go { model.stack.clear(); model.say("Set removed", { gone++ }) { taken++ } }
        settle()
        compose.onNodeWithText("Set removed").assertExists()
        compose.onNodeWithText("Set removed").performTouchInput { down(center); moveBy(Offset(-150f, 0f)); moveBy(Offset(-250f, 0f)); up() }
        settle()
        assertNull("the pill stayed", model.undos)
        assertEquals("the swipe took it back", 0, taken)
        assertEquals("what was kept for an Undo was not let go", 1, gone)
        compose.onAllNodesWithText("Set removed").assertCountEquals(0)
        // A short pull springs back and the pill stays.
        go { model.say("Photo deleted") { taken++ } }
        settle()
        compose.onNodeWithText("Photo deleted").performTouchInput { down(center); moveBy(Offset(-20f, 0f)); up() }
        settle()
        assertNotNull(model.undos)
        compose.onNodeWithText("Undo").performClick()
        settle()
        assertEquals(1, taken)
        assertNull(model.undos)
        assertFalse(compose.onAllNodesWithText("Photo deleted").fetchSemanticsNodes().isNotEmpty())
        scenario.close()
    }
}
