package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import clean.app.data.CleanDb
import clean.app.data.open
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
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class ReorderHoldTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val names = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)")
    private val eight = listOf("Bench Press (Barbell)", "Lat Pulldown (Cable)", "Squat (Barbell)", "Deadlift (Barbell)", "Overhead Press (Barbell)",
        "Bicep Curl (Dumbbell)", "Leg Press", "Triceps Pushdown (Cable)")
    private var window: android.view.View? = null

    private fun frame(): android.graphics.Bitmap {
        val view = window!!
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun save(name: String, frames: List<android.graphics.Bitmap>) {
        val w = frames.first().width / 2
        val h = frames.first().height / 2
        val strip = java.awt.image.BufferedImage(w * frames.size, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { i, b ->
            val small = android.graphics.Bitmap.createScaledBitmap(b, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            strip.setRGB(i * w, 0, w, h, pixels, 0, w)
        }
        java.io.File("build/motion-frames").mkdirs()
        javax.imageio.ImageIO.write(strip, "png", java.io.File("build/motion-frames/$name.png"))
    }

    /**
     * Where an exercise's name is (its text), or null when it is not on the screen. A card the list has composed ahead (prefetched) but not
     * placed yet is in the tree with no size at the origin; it is not on the screen, so it is left out (that made "folded order" fail).
     */
    private fun nameTop(name: String) = compose.onAllNodes(hasText(name), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?.boundsInRoot?.takeIf { it.width > 0f && it.height > 0f }?.top

    private fun open(exercises: List<String> = names): Pair<ActivityScenario<MainActivity>, AppModel> {
        runBlocking {
            val repo = Repo(CleanDb.open(ApplicationProvider.getApplicationContext()))
            val id = repo.createProfile("Test")
            repo.dao.put(Kv("active", id.toString()))
            repo.dao.put(Kv("welcome:$id", "done"))
            repo.dao.put(Kv("glass.reduce", "on")) // Haze cannot blur in Robolectric: solid glass
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppModel
        scenario.onActivity { model = ViewModelProvider(it)[AppModel::class.java]; window = it.window.decorView }
        compose.waitUntil(10_000) { model.loaded }
        compose.runOnIdle {
            model.putDraft(Draft("Workout", System.currentTimeMillis(), exercises.map { DraftBlock(it, listOf(DraftSet(SetType.NORMAL), DraftSet(SetType.NORMAL))) }))
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

    /** Holds [name] (scrolled to [where]) and checks its name has not moved and the others are folded in order around it. */
    private fun holdStays(label: String, name: String, scrollTo: Int) {
        val (scenario, model) = open(eight)
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(scrollTo)
        compose.waitForIdle()
        val before = nameTop(name)!!
        println("$label start " + eight.map { n -> nameTop(n)?.toInt() })
        val shots = mutableListOf(frame())
        compose.mainClock.autoAdvance = false
        compose.onAllNodes(hasText(name), useUnmergedTree = true)[0].performTouchInput {
            down(center)
            advanceEventTime(1000)
            move()
        }
        repeat(30) {
            compose.mainClock.advanceTimeBy(16)
            if (it % 6 == 0) shots += frame()
            if (it % 5 == 0) println("$label t=${it * 16} " + eight.map { n -> nameTop(n)?.toInt() })
        }
        val after = nameTop(name)!!
        shots += frame()
        println("$label before=$before after=$after")
        assertEquals("$label: the held name stays put", before, after, 1f)
        // The others are folded around it in order: those before above, those after below, 8 dp-ish rows apart.
        val order = eight.mapNotNull { n -> nameTop(n)?.let { n to it } }
        assertEquals("$label: folded order", order.map { it.first }.sortedBy { eight.indexOf(it) }, order.sortedBy { it.second }.map { it.first })
        val step = order.zipWithNext { a, b -> b.second - a.second }
        println("$label rows: $order")
        assert(step.all { it in 40f..90f }) { "$label: folded rows are even: $step" }
        compose.onAllNodes(hasText(name), useUnmergedTree = true)[0].performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("$label: letting go of a hold opens no page", listOf<Screen>(Screen.Active), model.stack.toList())
        shots += frame()
        save(label, shots)
        scenario.close()
    }

    @Test fun `holding a name near the bottom keeps it there and the folded list gathers around it`() = holdStays("hold-bottom", eight[7], 6)

    @Test fun `holding a name near the top keeps it there and the folded list gathers around it`() = holdStays("hold-top", eight[1], 0)

    @Test fun `holding the middle name keeps it there`() = holdStays("hold-middle", eight[3], 3)

    @Test fun `a tap on the list closes the number pad and the tap still reaches what is under it`() {
        val (scenario, model) = open(eight)
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("weight", substring = true, ignoreCase = true), useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        val shots = mutableListOf(frame())
        val before = model.draft
        fun padUp() = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close keyboard"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertEquals("the pad is up", true, padUp())
        // Round 27: on the second exercise's name, which opens its page: the tap closes the pad and is not taken from the name.
        val screen = model.stack.toList()
        compose.onAllNodes(hasText(eight[1]), useUnmergedTree = true)[0].performTouchInput { click(center) }
        compose.waitForIdle()
        shots += frame()
        save("pad-tap-away", shots)
        assertEquals("the pad closed", false, padUp())
        assertEquals("the name opened its page as a tap on it always does", screen.size + 1, model.stack.size)
        assertEquals("the draft is as it was", before, model.draft)
        scenario.close()
    }

    @Test fun `a swipe on the list leaves the number pad up and still scrolls`() {
        val (scenario, _) = open(eight)
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("weight", substring = true, ignoreCase = true), useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        fun padUp() = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close keyboard"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertEquals("the pad is up", true, padUp())
        val before = nameTop(eight[1])
        val node = compose.onAllNodes(hasText(eight[0]), useUnmergedTree = true)[0]
        node.performTouchInput { down(center) }
        // Round 27: the pad is left alone while the finger is down and moving: only a tap closes it, when the finger lifts.
        compose.mainClock.advanceTimeBy(300)
        assertEquals("still up at touch down", true, padUp())
        node.performTouchInput {
            repeat(10) { moveBy(Offset(0f, -30f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals("a scroll does not close the pad", true, padUp())
        val after = nameTop(eight[1])
        assert(before != null && (after == null || after < before!! - 100)) { "the list scrolled under the swipe: $before -> $after" }
        scenario.close()
    }
}
