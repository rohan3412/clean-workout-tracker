package clean.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier



import androidx.compose.ui.test.onNodeWithContentDescription

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.CleanTheme
import clean.app.ui.theme.resolveLook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The slide to confirm (B7): an early release springs back, the end confirms, and a screen reader has a plain action. Also draws the track for the design review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h300dp-xhdpi")
class InteractionsTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun draw(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val out = File("build/screens/default").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `slide to confirm`() {
        var confirmed = 0
        compose.setContent {
            CleanTheme(resolveLook(), 1f) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(24.dp)) { SlideToConfirm("Slide to delete everything", "Delete everything", { confirmed++ }) }
                }
            }
        }
        val track = compose.onNodeWithContentDescription("Delete everything")
        draw("20-slide-rest")
        // Halfway and let go: nothing happens and the knob returns.
        track.performTouchInput { down(centerLeft.copy(x = 24.dp.toPx() + 22.dp.toPx())); moveBy(androidx.compose.ui.geometry.Offset(120.dp.toPx(), 0f)) }
        compose.waitForIdle()
        draw("20-slide-half")
        track.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0, confirmed)
        // All the way: confirmed once.
        track.performTouchInput { down(centerLeft.copy(x = 24.dp.toPx() + 22.dp.toPx())); moveBy(androidx.compose.ui.geometry.Offset(width.toFloat(), 0f)); up() }
        compose.waitForIdle()
        assertEquals(1, confirmed)
        // A screen reader's own action.
        track.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, confirmed)
    }
}
