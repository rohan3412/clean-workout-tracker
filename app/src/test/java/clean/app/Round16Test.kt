package clean.app

import org.junit.experimental.categories.Category
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.app.data.RowEntity
import clean.app.data.open
import clean.core.STRONG_DATE_FORMAT
import clean.core.WorkoutRow
import clean.core.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Round 16 on the real app: a hold peek stays inside the window's side margins, near either edge. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class Round16Test {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: AppModel

    private fun launch(): ActivityScenario<MainActivity> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Rohan")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on"))
            val rows = ArrayList<WorkoutRow>()
            for (n in 0 until 230) {
                val date = LocalDate.now().minusDays(n.toLong()).atTime(6, 0).format(STRONG_DATE_FORMAT)
                for (s in 1..3) rows += WorkoutRow((n + 1).toString(), date, "Push", "3600", "Bench Press (Barbell)", s.toString(), "60.0", "8", "", "", "", "", "")
            }
            repo.dao.insertRows(rows.map { RowEntity.of(id, it) })
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(60_000) { model.loaded && model.workouts.value.isNotEmpty() && model.consistency.value != null }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        return scenario
    }

    private fun peekBounds() = compose.onNode(hasTestTag("day peek"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertInsideMargins(what: String) {
        val b = peekBounds()
        val margin = with(Density(2f)) { Space.l.toPx() }
        assertTrue("$what: the card starts at ${b.left}, before the margin $margin", b.left >= margin - 1f)
        assertTrue("$what: the card ends at ${b.right}, past ${800f - margin}", b.right <= 800f - margin + 1f)
    }

    /** Holds the Activity calendar [fromEdge] px in from the left or right edge of the window, for as long as it takes to peek, and checks the card. */
    private fun holdActivity(left: Boolean) {
        val chart = { compose.onAllNodes(hasContentDescription("Activity chart", substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.first { it.left < 800f && it.right > 0f && it.height > 0f } }
        var tries = 0
        while ((chart().top < 150f || chart().bottom > 1500f) && tries++ < 6) { compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }; compose.waitForIdle() }
        val box = chart()
        val c = 800f / 26f
        // The last column is the week still running: the row of today is the last one with a workout in it (one is seeded for today), whatever day it is.
        val y = box.top + c * (LocalDate.now().dayOfWeek.value - 1 + 1.6f)
        val x = if (left) 50f else 750f
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { down(Offset(x, y)) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertInsideMargins("Activity hold at the ${if (left) "left" else "right"} edge")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test fun anActivityPeekNearTheLeftEdgeStaysInsideTheMargins() { val s = launch(); holdActivity(true); s.close() }

    @Test fun anActivityPeekNearTheRightEdgeStaysInsideTheMargins() { val s = launch(); holdActivity(false); s.close() }

    @Test fun aHistoryRowPeekNearTheLeftEdgeStaysInsideTheMargins() {
        val s = launch()
        go { model.tab = Tab.Progress }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        val row = compose.onAllNodes(androidx.compose.ui.test.hasText("Push"), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.first { it.width > 0f && it.left >= 0f && it.right <= 800f && it.top in 100f..1500f }
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { down(Offset(row.left + 2f, row.center.y)) }
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        assertInsideMargins("History hold at the left edge")
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        s.close()
    }

    private fun go(action: () -> Unit) = compose.runOnIdle(action)
}
