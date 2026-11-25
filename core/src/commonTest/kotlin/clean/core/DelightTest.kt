package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class DelightTest {
    @Test
    fun `round workouts are the tenth 25th 50th 100th 150th 200th and every hundred after`() {
        val round = (1..520).filter(::isRoundWorkout)
        assertEquals(listOf(10, 25, 50, 100, 150, 200, 300, 400, 500), round)
    }

    @Test
    fun `ordinals`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "50th", "101st", "111th"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 50, 101, 111).map(::ordinal))
        assertEquals("Your 50th workout", roundWorkoutLine(50))
        assertNull(roundWorkoutLine(51))
    }

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `months pill leaves the year out inside this year and names it otherwise`() {
        assertEquals("Mar", monthsInView(d("2026-03-02"), d("2026-03-30"), 2026))
        assertEquals("Mar – Jul", monthsInView(d("2026-03-02"), d("2026-07-30"), 2026))
        assertEquals("Mar 2025", monthsInView(d("2025-03-02"), d("2025-03-30"), 2026))
        assertEquals("Mar – Jul 2025", monthsInView(d("2025-03-02"), d("2025-07-30"), 2026))
    }

    @Test
    fun `months pill names the year when it takes in january`() {
        assertEquals("Jan 2026", monthsInView(d("2026-01-05"), d("2026-01-30"), 2026))
        assertEquals("Jan – Apr 2026", monthsInView(d("2026-01-05"), d("2026-04-30"), 2026))
        assertEquals("Nov 2025 – Feb 2026", monthsInView(d("2025-11-03"), d("2026-02-28"), 2026))
    }

    @Test
    fun `a milestone is glinted once and a first run glints nothing`() {
        assertEquals(emptyList(), unglinted(listOf("a", "b"), null))
        assertEquals(listOf("b"), unglinted(listOf("a", "b"), setOf("a")))
        assertEquals(emptyList(), unglinted(listOf("a", "b"), setOf("a", "b")))
    }

    @Test
    fun `the anniversary of the first workout is its month and day, a year on or more`() {
        assertEquals(1, anniversaryYears(d("2025-11-20"), d("2026-11-20")))
        assertEquals(3, anniversaryYears(d("2023-03-05"), d("2026-03-05")))
        assertNull(anniversaryYears(d("2025-11-20"), d("2026-11-19")))
        assertNull(anniversaryYears(d("2025-11-20"), d("2026-11-21")))
        assertNull(anniversaryYears(d("2025-11-20"), d("2025-11-20")))
        assertNull(anniversaryYears(d("2025-11-20"), d("2026-10-20")))
    }

    @Test
    fun `a first workout on 29 February is celebrated on 28 February in years without a 29th`() {
        assertEquals(1, anniversaryYears(d("2024-02-29"), d("2025-02-28")))
        assertNull(anniversaryYears(d("2024-02-29"), d("2025-03-01")))
        assertEquals(2, anniversaryYears(d("2024-02-29"), d("2026-02-28")))
        // In a leap year the day itself is the anniversary, and the 28th is not.
        assertEquals(4, anniversaryYears(d("2024-02-29"), d("2028-02-29")))
        assertNull(anniversaryYears(d("2024-02-29"), d("2028-02-28")))
        // A first workout on the 28th is not moved to the 29th.
        assertNull(anniversaryYears(d("2023-02-28"), d("2028-02-29")))
    }

    @Test
    fun `the anniversary line is pluralised and absent on other days`() {
        assertEquals("1 year of training today", anniversaryLine(d("2025-11-20"), d("2026-11-20")))
        assertEquals("2 years of training today", anniversaryLine(d("2024-11-20"), d("2026-11-20")))
        assertNull(anniversaryLine(d("2024-11-20"), d("2026-11-21")))
        assertNull(anniversaryLine(null, d("2026-11-20")))
    }
}
