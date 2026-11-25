package clean.app

import clean.app.android.*

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Moments: a picked picture is kept scaled down under `files/moments/<profile>/`, a bad file keeps nothing, a removal can be put back. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MomentFilesTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun png(width: Int, height: Int) = ByteArrayOutputStream().also { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test fun `a picture is kept at most 2048 px and listed`() {
        assertTrue(MomentFiles.list(context, 5).isEmpty())
        val a = MomentFiles.add(context, 5) { ByteArrayInputStream(png(3000, 1500)) }
        val b = MomentFiles.add(context, 5) { ByteArrayInputStream(png(100, 50)) }
        assertNotNull(a); assertNotNull(b)
        assertEquals(setOf(a!!.name, b!!.name), MomentFiles.list(context, 5).map { it.name }.toSet())
        val kept = PhotoFiles.decode(MomentFiles.file(context, 5, a.name), 4096, Bitmap.Config.ARGB_8888)!!
        assertTrue(kept.width <= 2048)
        // Another profile has none; incoming.tmp is never left behind.
        assertTrue(MomentFiles.list(context, 6).isEmpty())
        assertFalse(MomentFiles.file(context, 5, "incoming.tmp").exists())
        // (The decoded copy can hold the file open on Windows; the removal is checked on files nothing has read.)
        MomentFiles.add(context, 9) { ByteArrayInputStream(png(10, 10)) }
        MomentFiles.deleteAll(context, 9)
        assertTrue(MomentFiles.list(context, 9).isEmpty())
    }

    @Test fun `something that is not a picture keeps nothing`() {
        assertNull(MomentFiles.add(context, 7) { ByteArrayInputStream(ByteArray(40) { 1 }) })
        assertTrue(MomentFiles.list(context, 7).isEmpty())
    }

    @Test fun `removed pictures come back`() {
        val m = MomentFiles.add(context, 8) { ByteArrayInputStream(png(40, 40)) }!!
        val away = MomentFiles.removeAside(context, 8, listOf(m), "batch")!!
        assertTrue(MomentFiles.list(context, 8).isEmpty())
        MomentFiles.putBack(context, 8, away)
        assertEquals(listOf(m.name), MomentFiles.list(context, 8).map { it.name })
        assertFalse(away.exists())
    }
}
