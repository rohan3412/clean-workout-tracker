package clean.app

import clean.app.android.*

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The profile picture: a picked picture is kept as a centred square under `files/avatars/`, which the backup zip carries; a bad file keeps nothing. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AvatarTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun png(width: Int, height: Int) = ByteArrayOutputStream().also { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test fun `the square is cut from the middle`() {
        assertEquals(Triple(50, 0, 200), AvatarFiles.squareOf(300, 200))
        assertEquals(Triple(0, 100, 200), AvatarFiles.squareOf(200, 400))
    }

    @Test fun `a picked picture is kept as a square and can be removed`() {
        assertNull(AvatarFiles.load(context, 7))
        assertTrue(AvatarFiles.set(context, 7) { ByteArrayInputStream(png(300, 200)) })
        val kept = AvatarFiles.load(context, 7)!!
        assertEquals(200, kept.width)
        assertEquals(200, kept.height)
        assertTrue(AvatarFiles.file(context, 7).path.replace(java.io.File.separatorChar, '/').endsWith("avatars/7.jpg"))
        // (The decoded copy can hold the file open on Windows; the removal is checked on a file nothing has read.)
        assertTrue(AvatarFiles.set(context, 9) { ByteArrayInputStream(png(10, 10)) })
        AvatarFiles.delete(context, 9)
        assertFalse(AvatarFiles.file(context, 9).exists())
    }

    @Test fun `something that is not a picture keeps nothing`() {
        assertFalse(AvatarFiles.set(context, 8) { ByteArrayInputStream(ByteArray(40) { 1 }) })
        assertNull(AvatarFiles.load(context, 8))
    }
}
