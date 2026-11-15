package clean.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back on a root tab goes to Home first; on Home, or with a page open, it does what it did before. */
class BackHomeTest {
    @Test
    fun `back goes home from every other tab`() {
        for (tab in Tab.entries) {
            if (tab == Tab.Home) assertFalse(backGoesHome(true, tab)) else assertTrue(tab.name, backGoesHome(true, tab))
            // A page over the tabs closes first.
            assertFalse(tab.name, backGoesHome(false, tab))
        }
    }
}
