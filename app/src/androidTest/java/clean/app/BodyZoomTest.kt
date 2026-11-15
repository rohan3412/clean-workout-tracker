package clean.app

import android.graphics.Region
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import clean.core.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The body figure's pinch-zoom as it sits on the Body tab: in a pager, under a `swipeSteps` wrapper.
 * Written without a device to run it on.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class BodyZoomTest {
    @get:Rule val rule = createComposeRule()

    private var scale = 1f
    private var steps = 0
    private var tapped: Muscle? = null
    private lateinit var pager: PagerState

    private fun show() {
        rule.setContent {
            pager = rememberPagerState { 2 }
            HorizontalPager(pager) { page ->
                if (page == 0) Box(Modifier.testTag("figure").width(400.dp).swipeSteps({ steps++ }, { steps++ })) {
                    BodyFigure(
                        Muscle.entries.associateWith { Color.Gray }, { tapped = it },
                        zoomable = true, onZoom = { scale = it },
                    )
                } else Text("Next page")
            }
        }
    }

    /** Fingers spread outwards on the front figure's middle: its scale ends near 2 and its pan is that point's. */
    private fun pinchOut(centre: Offset) {
        rule.onNodeWithTag("figure").performTouchInput {
            pinch(centre - Offset(30f, 0f), centre - Offset(60f, 0f), centre + Offset(30f, 0f), centre + Offset(60f, 0f))
        }
        rule.waitForIdle()
    }

    @Test fun pinchZoomsAndDoesNotStepOrFlip() {
        show()
        pinchOut(Offset(200f, 300f))
        assertTrue("scale was $scale", scale > 1f)
        assertEquals(0, pager.currentPage)
        assertEquals(0, steps)
    }

    @Test fun oneFingerSwipeAtNormalSizeStillSteps() {
        show()
        rule.onNodeWithTag("figure").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, steps)
        assertEquals(1f, scale, 0f)
    }

    @Test fun doubleTapPutsItBack() {
        show()
        pinchOut(Offset(200f, 300f))
        assertTrue(scale > 1f)
        rule.onNodeWithTag("figure").performTouchInput { doubleClick(Offset(200f, 300f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(1f, scale, 0f)
    }

    @Test fun tapWhileZoomedFindsTheMuscleUnderTheFinger() {
        show()
        // The pinch is about the middle of the front figure, so a figure point p ends up at p * scale + middle * (1 - scale).
        val node = rule.onNodeWithTag("figure")
        val size = node.fetchSemanticsNode().size
        val k = size.width / (2 * 727f)
        val middle = Offset(size.width / 4f, size.height / 2f)
        pinchOut(middle)
        assertTrue("scale was $scale", scale > 1.5f)
        // A point of the front figure that is on screen, with the muscle that is drawn last over it.
        val all = Region(0, 0, 1500, 1400)
        val parts = BODY_FRONT.filter { it.muscle != null && it.head == null }.map { part ->
            part to Region().apply { setPath(PathParser().parsePathString(part.path).toPath().asAndroidPath(), all) }
        }
        var found: Triple<Float, Float, Muscle>? = null
        search@ for (y in 200 until 1300 step 8) for (x in 100 until 640 step 8) {
            val at = Offset(x * k, (y - 95f) * k)
            val on = at * scale + middle * (1 - scale)
            if (on.x < 20f || on.y < 20f || on.x > size.width - 20f || on.y > size.height - 20f) continue
            val top = parts.lastOrNull { (_, region) -> region.contains(x, y) } ?: continue
            found = Triple(on.x, on.y, top.first.muscle!!)
            break@search
        }
        val (x, y, muscle) = found!!
        node.performTouchInput { click(Offset(x, y)) }
        // Taps wait out the double-tap time while zoomed.
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(muscle, tapped)
        assertEquals(1f, scale, 0f)
    }
}
