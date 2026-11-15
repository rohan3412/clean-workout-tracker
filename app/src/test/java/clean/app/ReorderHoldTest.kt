package clean.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.Kv
import clean.app.data.Repo
import clean.core.SetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Holding an exercise's name in a workout and dragging moves it, with the name's swipe-to-remove on (the default): the swipe going away
 * as the cards fold must not drop the hold. Run on the real app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ReorderHoldTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)")

    private fun open(): Pair<ActivityScenario<MainActivity>, AppModel> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java] }
        compose.waitUntil(10_000) { model.loaded }
        compose.runOnIdle {
            model.putDraft(Draft("Workout", System.currentTimeMillis(), names.map { DraftBlock(it, listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
            model.open(Screen.Active)
        }
        compose.waitForIdle()
        return scenario to model
    }

    @Test fun `holding a name and dragging it down moves the exercise`() {
        val (scenario, model) = open()
        compose.onAllNodes(hasText(names[0]), useUnmergedTree = true)[0].performTouchInput {
            down(center)
            advanceEventTime(1000)
            // Folded cards are 52 dp tall: a few steps down passes the next two.
            repeat(20) { moveBy(Offset(0f, 12f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(names.drop(1) + names[0], model.draft!!.blocks.map { it.exercise })
        scenario.close()
    }

    @Test fun `swiping a name to the left still removes the exercise`() {
        val (scenario, model) = open()
        compose.onAllNodes(hasText(names[1]), useUnmergedTree = true)[0].performTouchInput { swipeLeft(centerX, left - 200f, 300) }
        compose.waitForIdle()
        assertEquals(listOf(names[0], names[2]), model.draft!!.blocks.map { it.exercise })
        scenario.close()
    }
}
