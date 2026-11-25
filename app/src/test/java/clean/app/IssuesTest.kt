package clean.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for items in the private issue register that no older test guarded (the ids are in each test's KDoc). They check what the app does,
 * from the model and the semantics tree, not how it looks.
 */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class IssuesTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf("Bench Press (Barbell)", "Squat (Barbell)", "Lat Pulldown (Cable)")
    private lateinit var model: AppModel

    /** Workouts every third day for two weeks; the last of them today when [today] is true. */
    private fun seed(today: Boolean = true) = runBlocking {
        val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
        val id = repo.createProfile("Rohan")
        repo.dao.put(Kv("active", id.toString()))
        repo.dao.put(Kv("welcome:$id", "done"))
        repo.dao.put(Kv("glass.reduce", "on"))
        val rows = ArrayList<WorkoutRow>()
        var n = 0
        for (back in listOf(12L, 9L, 6L, 3L, 0L).filter { today || it != 0L }) {
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

    private fun launch(today: Boolean = true): ActivityScenario<MainActivity> {
        seed(today)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        return scenario
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)

    private fun settle(ms: Long = 2_000) { compose.waitForIdle(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    private fun shown(text: String, ignoreCase: Boolean = false) = compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()

    /** A template named Push, so Home's card has something to say is up next. */
    private fun addTemplate() {
        val bench = clean.core.TemplateBlock("Bench Press (Barbell)", List(3) { SetType.NORMAL })
        go { model.save(Template(0, "Push", listOf(bench))) }
        var waited = 0
        while (model.templates.value.isEmpty() && waited++ < 400) { Thread.sleep(25); compose.waitForIdle() }
        assertTrue("the template was not saved", model.templates.value.isNotEmpty())
        settle()
    }

    /**
     * I-011 (2026-10-08, 2026-10-11): a workout shows its cancel button at the top for its first five minutes, then moves
     * it to the bottom. The switch is `Draft.isEarly`.
     */
    @Test fun cancelIsAtTheTopOnlyForTheFirstFiveMinutes() {
        val draft = Draft("Push", startMillis = 1_000_000L)
        assertTrue(draft.isEarly(1_000_000L))
        assertTrue(draft.isEarly(1_000_000L + 5 * 60_000 - 1))
        assertFalse(draft.isEarly(1_000_000L + 5 * 60_000))
        assertFalse(draft.isEarly(1_000_000L + 20 * 60_000))
    }

    /** I-069 (2026-11-18): the dock has four tabs and Profile is the last of them. */
    @Test fun theDockHasFourTabsWithProfileLast() {
        assertEquals(listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile), Tab.entries.toList())
        assertEquals("Profile", Tab.entries.last().label)
    }

    /** I-090 (2026-11-19): Home says "Up next" once a workout is logged on today's date. */
    @Test fun homeSaysUpNextOnceAWorkoutIsLoggedToday() {
        val scenario = launch(today = true)
        addTemplate()
        go { model.stack.clear(); model.tab = Tab.Home }
        settle()
        assertTrue("no 'Up next' on Home with a workout today", shown("Up next", ignoreCase = true))
        scenario.close()
    }

    /** I-090 (2026-11-19): before today's workout the card is about today, so it does not say "Up next". */
    @Test fun homeSaysTodayBeforeTheDaysWorkout() {
        val scenario = launch(today = false)
        addTemplate()
        go { model.stack.clear(); model.tab = Tab.Home }
        settle()
        assertFalse("'Up next' with no workout today", shown("Up next", ignoreCase = true))
        assertTrue("no 'Today' caption", shown("Today", ignoreCase = true))
        scenario.close()
    }

    /**
     * I-078 (2026-11-18): with a day's card open on the Calendar page, one tap on another day opens that day's card
     * (it used to take a tap to close the card and a second to open the other).
     */
    @Test fun aTapOnAnotherDayMovesTheCalendarCardInOneTap() {
        val scenario = launch()
        go { model.stack.clear(); model.open(Screen.Calendar()) }
        settle()
        fun tapDay(back: Long) {
            val squares = compose.onAllNodesWithText(LocalDate.now().minusDays(back).dayOfMonth.toString())
            squares[squares.fetchSemanticsNodes().lastIndex].performClick()
            settle()
        }
        val today = prettyDay(LocalDate.now())
        val earlier = prettyDay(LocalDate.now().minusDays(3))
        tapDay(0)
        assertTrue("no card for today", shown(today))
        tapDay(3)
        assertTrue("one tap did not open the card for $earlier", shown(earlier))
        assertFalse("today's card stayed", shown(today))
        scenario.close()
    }

    /** I-065 (2026-11-16): a History card has no "Best set" column; it keeps the sets and the 1RM. */
    @Test fun historyCardsHaveNoBestSetColumn() {
        val scenario = launch()
        go { model.stack.clear(); model.open(Screen.History) }
        settle()
        assertTrue("the History page shows no workout", shown("Push"))
        assertFalse("a 'Best set' column came back", shown("Best set", ignoreCase = true))
        assertTrue("the 1RM column is gone", shown("1RM"))
        scenario.close()
    }

    /**
     * I-055 (2026-10-21): on the workout's number pad the close key is under 7, the zero under 8 and the point under 9.
     */
    @Test fun theNumberPadKeepsCloseUnderSevenZeroUnderEightAndThePointUnderNine() {
        val scenario = launch()
        go {
            model.stack.clear()
            model.putDraft(Draft("Push", System.currentTimeMillis(), listOf(DraftBlock("Bench Press (Barbell)", listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))))))
            model.open(Screen.Active)
        }
        settle()
        // Home is composed under the workout and has its own weigh-in button; a set's weight box says "Set 1 weight".
        compose.onAllNodes(hasContentDescription("Set 1 weight", substring = true), useUnmergedTree = true)[0].performClick()
        settle()
        fun centreX(text: String): Float {
            // The pad is the lowest thing on the screen: of the keys with this label, the lowest.
            val keys = compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            return keys.maxByOrNull { it.top }!!.center.x
        }
        val close = compose.onAllNodes(hasContentDescription("Close keyboard"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.center.x
        assertEquals("0 is not under 8", centreX("8"), centreX("0"), 2f)
        assertEquals("the point is not under 9", centreX("9"), centreX("."), 2f)
        assertEquals("the close key is not under 7", centreX("7"), close, 2f)
        scenario.close()
    }
}
