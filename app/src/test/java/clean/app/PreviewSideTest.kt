package clean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The live preview's Before and After pills: untouched, the swaps run; a tap holds the tapped side. */
class PreviewSideTest {
    @Test
    fun `untouched, the automatic swaps decide`() {
        val side = PreviewSide()
        assertNull(side.picked)
        assertNull(side.target)
    }

    @Test
    fun `a tap holds the tapped side, and a second tap moves it`() {
        val side = PreviewSide()
        side.tap(false)
        assertEquals(false, side.picked)
        assertEquals(0f, side.target)
        side.tap(true)
        assertEquals(true, side.picked)
        assertEquals(1f, side.target)
    }
}
