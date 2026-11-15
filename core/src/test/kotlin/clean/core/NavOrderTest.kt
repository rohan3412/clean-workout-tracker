package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NavOrderTest {
    @Test
    fun noneOrEmptyIsTheDefault() {
        assertEquals(NAV_DEFAULT, navOrder(null))
        assertEquals(NAV_DEFAULT, navOrder(""))
    }

    @Test
    fun aStoredOrderIsKept() {
        assertEquals(listOf("profile", "home", "exercises", "progress"), navOrder("profile,home,exercises,progress"))
        assertEquals(listOf("profile", "home", "exercises", "progress"), navOrder(" Profile , HOME,exercises,progress"))
    }

    @Test
    fun unknownNamesWorkoutAndRepeatsAreDropped() {
        assertEquals(listOf("exercises", "home", "progress", "profile"), navOrder("exercises,workout,nonsense,home,exercises,progress,profile"))
    }

    @Test
    fun missingTabsGoBackAfterTheOthersInDefaultOrder() {
        assertEquals(listOf("profile", "home", "progress", "exercises"), navOrder("profile,home"))
    }

    @Test
    fun workoutIsThirdOfFive() {
        assertEquals(listOf("home", "progress", "workout", "exercises", "profile"), withMiddle(NAV_DEFAULT, "workout"))
    }
}
