package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WelcomeTest {
    @Test
    fun `only an empty profile that has not finished it is welcomed`() {
        assertTrue(showWelcome(0, 0, false))
        assertFalse(showWelcome(141, 0, false))
        assertFalse(showWelcome(0, 3, false))
        assertFalse(showWelcome(0, 0, true))
    }

    @Test
    fun `height converts between inches and centimetres and back`() {
        assertEquals(177.8, inchesToCm(70), 1e-9)
        for (inches in 48..96) assertEquals(inches, cmToInches(inchesToCm(inches)))
        assertEquals(70, cmToInches(178.0))
        assertEquals(5 to 10, feetAndInches(70))
        assertEquals(4 to 0, feetAndInches(48))
    }
}
