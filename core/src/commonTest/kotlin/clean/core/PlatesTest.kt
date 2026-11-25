package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatesTest {
    @Test
    fun nothingStoredIsTheStandardKit() {
        assertEquals(standardKit("kg"), parsePlateKit(null, "kg"))
        assertEquals(standardKit("lb"), parsePlateKit("junk", "lb"))
        assertNull(encodePlateKit(standardKit("kg"), "kg"))
    }

    @Test
    fun aKitRoundTrips() {
        val kit = PlateKit(15.0, mapOf(20.0 to 2, 10.0 to 4, 1.25 to null))
        val text = encodePlateKit(kit, "kg")
        assertEquals("15|20:2,10:4,1.25", text)
        assertEquals(kit, parsePlateKit(text, "kg"))
        // Sizes come back largest first whatever order they were written in; unreadable items are dropped.
        assertEquals(listOf(20.0, 5.0), parsePlateKit("20|5:2,x,20:4", "kg").plates.keys.toList())
    }

    @Test
    fun standardLoadIsAsBefore() {
        val load = loadPlates(142.5, standardKit("kg"))!!
        assertEquals(listOf(25.0 to 2, 10.0 to 1, 1.25 to 1), load.perSide)
        assertEquals(0.0, load.leftOver)
        assertNull(loadPlates(15.0, standardKit("kg")))
    }

    @Test
    fun countsAndTheOwnBarAreRespected() {
        // A 15 kg bar, two 20s and four 10s: 100 kg is 20 + 2 × 10 a side, with 5 kg that cannot be made.
        val kit = PlateKit(15.0, mapOf(20.0 to 2, 10.0 to 4))
        val load = loadPlates(100.0, kit)!!
        assertEquals(listOf(20.0 to 1, 10.0 to 2), load.perSide)
        assertEquals(5.0, load.leftOver)
        // An odd plate is left out: three 5s make one pair.
        assertEquals(listOf(5.0 to 1), loadPlates(35.0, PlateKit(20.0, mapOf(5.0 to 3)))!!.perSide)
        assertEquals(5.0, loadPlates(35.0, PlateKit(20.0, mapOf(5.0 to 3)))!!.leftOver)
    }
}
