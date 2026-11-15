package clean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private fun keys(list: List<HomeEntry>) = list.map { it.stored }

    @Test fun `no stored list is the default`() {
        assertEquals(HOME_DEFAULT, parseHome(null))
        assertEquals("today:w,onerm1:h,streak:h,week:h,onerm2:h,weight:h,volume:h,last:w", encodeHome(HOME_DEFAULT))
    }

    @Test fun `widths are stored per key and read back`() {
        val list = parseHome("today:w,week:w,onerm1:h,last:w")
        assertEquals(listOf("today:w", "week:w", "onerm1:h", "last:w"), keys(list))
        assertEquals("today:w,week:w,onerm1:h,last:w", encodeHome(list))
    }

    @Test fun `a key without a width takes the card's own`() {
        assertEquals(listOf("week:h", "recovery:w", "prs:h"), keys(parseHome("week:h,recovery,prs")))
        assertEquals(listOf("recovery:w"), keys(parseHome("recovery:x")))
    }

    @Test fun `a card that cannot be half is always wide`() {
        assertEquals(listOf("today:w", "weightchart:w"), keys(parseHome("today:h,weightchart:h")))
    }

    @Test fun `a list from before widths is kept as it was with glance as its tiles`() {
        val list = parseHome("today,week,glance,last,activity")
        assertEquals(listOf("today:w", "week:w", "workouts:h", "month:h", "streak:h", "weight:h", "last:w"), keys(list))
        // The old weigh-in chart card keeps its place as the chart card.
        assertEquals(listOf("weightchart:w"), keys(parseHome("weight")))
    }

    @Test fun `unknown and repeated keys are dropped and an empty list shows none`() {
        assertEquals(listOf("today:w", "week:h"), keys(parseHome("today:w,nonsense:h,week:h,today:h,week:w")))
        assertTrue(parseHome("").isEmpty())
    }

    @Test fun `the Activity card, now on Progress, is dropped from a stored list without a gap`() {
        assertEquals(listOf("today:w", "week:h", "prs:h"), keys(parseHome("today:w,activity:w,week:h,prs:h")))
        assertEquals(listOf("today:w"), keys(parseHome("today,activity")))
    }

    @Test fun `a card moves to a place and the others keep their order and widths`() {
        val list = parseHome("today:w,onerm1:h,streak:h,last:w")
        assertEquals(listOf("onerm1:h", "streak:h", "last:w", "today:w"), keys(moveEntry(list, HomeCard.Today, 3)))
        assertEquals(listOf("last:w", "today:w", "onerm1:h", "streak:h"), keys(moveEntry(list, HomeCard.Last, 0)))
        assertEquals(listOf("today:w", "onerm1:h", "streak:h", "last:w"), keys(moveEntry(list, HomeCard.Streak, 2)))
        assertEquals(listOf("today:w", "onerm1:h", "streak:h", "last:w"), keys(moveEntry(list, HomeCard.Today, -5)))
        assertEquals(list, moveEntry(list, HomeCard.Recovery, 1))
    }

    @Test fun `a width flips for a card that can be half and never for one that cannot`() {
        val list = parseHome("today:w,week:w,onerm1:h")
        assertEquals(listOf("today:w", "week:h", "onerm1:h"), keys(toggleWidth(list, HomeCard.Week)))
        assertEquals(listOf("today:w", "week:w", "onerm1:w"), keys(toggleWidth(list, HomeCard.OneRm1)))
        assertEquals(list, toggleWidth(list, HomeCard.Today))
    }

    @Test fun `hidden cards are the ones left out`() {
        val hidden = hiddenHome(HOME_DEFAULT)
        assertTrue(HomeCard.Recovery in hidden && HomeCard.Today !in hidden)
        assertEquals(HomeCard.entries.size, hidden.size + HOME_DEFAULT.size)
    }

    @Test fun `half cards in a row group together and a wide card ends the group`() {
        val segments = homeSegments(parseHome("today:w,onerm1:h,streak:h,last:w,week:h,prs:h,recovery:w"))
        assertEquals(listOf(listOf("today:w"), listOf("onerm1:h", "streak:h"), listOf("last:w"), listOf("week:h", "prs:h"), listOf("recovery:w")), segments.map(::keys))
    }

    @Test fun `each half card goes to the shorter column`() {
        assertEquals(listOf(0, 1, 1, 1, 0), placeColumns(listOf(100, 40, 40, 40, 40), 10))
        // Equal so far goes left; a card that shows nothing is not placed and leaves no gap.
        assertEquals(listOf(0, -1, 1, 0), placeColumns(listOf(50, 0, 50, 50), 10))
        assertEquals(emptyList<Int>(), placeColumns(emptyList(), 10))
    }
}
