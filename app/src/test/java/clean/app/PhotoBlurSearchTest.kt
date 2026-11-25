package clean.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

/** Round 29 fixes: blurred progress photos are cut to a few pixels whatever the decoder gives; the search pill closes as the keyboard starts down. */
@RunWith(AndroidJUnit4::class)
@Category(Regression::class)
@org.robolectric.annotation.Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PhotoBlurSearchTest {
    @Test fun aBlurredThumbnailIsAFewPixelsWhateverSizeItWasDecodedAt() {
        // A decoder that stops at an eighth hands back 256 px for a 2048 px photo; the thumbnail must still be tiny.
        val decoded = ImageBitmap(256, 192)
        val small = shrunk(decoded, BLUR_SIDE)
        assertEquals(BLUR_SIDE, small.width)
        assertEquals(BLUR_SIDE * 3 / 4, small.height)
        val tall = shrunk(ImageBitmap(48, 64), BLUR_SIDE)
        assertEquals(BLUR_SIDE, tall.height)
        assertEquals(BLUR_SIDE * 3 / 4, tall.width)
    }

    @Test fun theKeyboardCountsAsDownAsSoonAsItStartsToFall() {
        val watch = KeyboardWatch()
        assertFalse(watch.up(0))
        for (h in listOf(100, 400, 800, 900)) assertTrue("rising: up", watch.up(h))
        assertTrue("a few px down is still up", watch.up(880))
        assertFalse("below 85 % of its height: going down", watch.up(700))
        assertFalse("stays down while it falls", watch.up(600))
        assertFalse(watch.up(200))
        assertFalse(watch.up(0))
        assertTrue("up again on the next rise", watch.up(300))
    }
}
