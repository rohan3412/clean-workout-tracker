package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestFeelTest {
    @Test
    fun presetsReadOrFallBack() {
        assertEquals(listOf(45, 75, 100, 240), parseRestPresets("45, 75,100,240"))
        assertEquals(DEFAULT_REST_PRESETS, parseRestPresets(null))
        assertEquals(DEFAULT_REST_PRESETS, parseRestPresets("60,x,120,180"))
        assertEquals(DEFAULT_REST_PRESETS, parseRestPresets("0,90,120,180"))
        assertEquals(DEFAULT_REST_PRESETS, parseRestPresets("1,2,3,4,5"))
        assertEquals(listOf(30, 60), parseRestPresets("30,60"))
    }

    @Test
    fun oldSwitchesMigrate() {
        assertEquals(RestEndFeedback.BOTH, restEndFeedback(null, null, null))
        assertEquals(RestEndFeedback.VIBRATE, restEndFeedback(null, "off", "on"))
        assertEquals(RestEndFeedback.SOUND, restEndFeedback(null, "on", "off"))
        assertEquals(RestEndFeedback.OFF, restEndFeedback(null, "off", "off"))
        // A stored choice wins over the old switches.
        assertEquals(RestEndFeedback.SOUND, restEndFeedback("sound", "off", "on"))
        assertTrue(RestEndFeedback.BOTH.sound && RestEndFeedback.BOTH.vibrate)
    }

    @Test
    fun hideTiming() {
        assertTrue(restShowsFull(2, 60, 5, false))
        assertFalse(restShowsFull(5, 60, 5, false))
        assertTrue(restShowsFull(30, 10, 5, false))
        assertFalse(restShowsFull(30, 11, 5, false))
        assertTrue(restShowsFull(30, 40, 5, true))
    }
}
