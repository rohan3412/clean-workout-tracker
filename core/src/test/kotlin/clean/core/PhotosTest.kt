package clean.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotosTest {
    private fun at(day: Int, hour: Int = 9, second: Int = 0) = LocalDateTime.of(2026, 10, day, hour, 30, second)
    private fun photo(day: Int, pose: Pose, hour: Int = 9) = at(day, hour).let { Photo(photoName(it, pose), it, pose) }

    @Test
    fun `a name is the moment and the pose`() {
        assertEquals("20261012-093005_front.jpg", photoName(at(12, 9, 5), Pose.FRONT))
        assertEquals(Photo("20261012-093005_back.jpg", at(12, 9, 5), Pose.BACK), parsePhoto("20261012-093005_back.jpg"))
    }

    @Test
    fun `names that are not photos are ignored`() {
        assertNull(parsePhoto("20261012-093005_legs.jpg"))    // no known pose
        assertNull(parsePhoto("20261012-093005.jpg"))         // no pose at all
        assertNull(parsePhoto("20261012-093005_front.png"))
        assertNull(parsePhoto("20261340-093005_front.jpg"))   // not a date
        assertNull(parsePhoto("2026101-093005_front.jpg"))    // not the exact spelling
        assertNull(parsePhoto("20261012-093005_front_2.jpg"))
        assertNull(parsePhoto("capture.jpg"))
    }

    @Test
    fun `a taken name gets a second added until it is free`() {
        val taken = setOf("20261012-093000_side.jpg", "20261012-093001_side.jpg")
        assertEquals("20261012-093002_side.jpg", freePhoto(at(12), Pose.SIDE, taken).name)
        assertEquals("20261012-093000_front.jpg", freePhoto(at(12), Pose.FRONT, taken).name)
        assertEquals(at(12, 9, 2), freePhoto(at(12), Pose.SIDE, taken).at)
    }

    @Test
    fun `photos are grouped by day, newest day first, front side back within a day`() {
        val all = listOf(photo(10, Pose.FRONT), photo(12, Pose.BACK), photo(12, Pose.FRONT), photo(12, Pose.SIDE), photo(11, Pose.SIDE))
        val days = groupByDay(all)
        assertEquals(listOf(12, 11, 10), days.map { it.day.dayOfMonth })
        assertEquals(listOf(Pose.FRONT, Pose.SIDE, Pose.BACK), days[0].photos.map { it.pose })
        assertEquals(listOf(11), groupByDay(all, Pose.SIDE).filter { it.day.dayOfMonth == 11 }.map { it.day.dayOfMonth })
        assertEquals(listOf(12, 11), groupByDay(all, Pose.SIDE).map { it.day.dayOfMonth }.take(2))
        assertEquals(listOf(12, 10), groupByDay(all, Pose.FRONT).map { it.day.dayOfMonth })
        assertEquals(emptyList(), groupByDay(all.filter { it.pose != Pose.BACK }, Pose.BACK))
    }

    @Test
    fun `the default pair is the oldest and the newest of the pose`() {
        val all = listOf(photo(10, Pose.FRONT), photo(11, Pose.SIDE), photo(12, Pose.FRONT), photo(13, Pose.FRONT))
        assertEquals(photo(10, Pose.FRONT) to photo(13, Pose.FRONT), defaultPair(all, Pose.FRONT))
        assertNull(defaultPair(all, Pose.SIDE))
        assertEquals(photo(10, Pose.FRONT) to photo(13, Pose.FRONT), defaultPair(all, null))
        assertNull(defaultPair(listOf(photo(10, Pose.FRONT)), null))
    }

    @Test
    fun `the pose to compare is the asked one, else the one with most photos, else all`() {
        val all = listOf(photo(10, Pose.FRONT), photo(11, Pose.SIDE), photo(12, Pose.SIDE), photo(13, Pose.SIDE), photo(14, Pose.BACK), photo(15, Pose.BACK))
        assertEquals(Pose.BACK, comparePose(all, Pose.BACK))
        assertEquals(Pose.SIDE, comparePose(all, Pose.FRONT))
        assertEquals(Pose.SIDE, comparePose(all, null))
        assertNull(comparePose(listOf(photo(10, Pose.FRONT), photo(11, Pose.SIDE)), null))
    }

    @Test
    fun `how far apart reads in days, weeks, then years`() {
        val d = LocalDate.of(2026, 10, 12)
        assertEquals("Same day", apartText(d, d))
        assertEquals("1 day apart", apartText(d, d.minusDays(1)))
        assertEquals("13 days apart", apartText(d.minusDays(13), d))
        assertEquals("14 weeks apart", apartText(d.minusWeeks(14), d))
        assertEquals("14 weeks apart", apartText(d, d.minusWeeks(14)))
        assertEquals("1 year apart", apartText(d.minusDays(365), d))
        assertEquals("1 year 3 months apart", apartText(d.minusDays(365 + 95), d))
        assertEquals("2 years apart", apartText(d.minusDays(730), d))
    }

    @Test
    fun `how long ago and how long after the first`() {
        val today = LocalDate.of(2026, 10, 12)
        assertEquals("Today", agoText(today, today))
        assertEquals("Yesterday", agoText(today.minusDays(1), today))
        assertEquals("5 days ago", agoText(today.minusDays(5), today))
        assertEquals("3 weeks ago", agoText(today.minusDays(21), today))
        assertEquals("4 months ago", agoText(today.minusDays(125), today))
        assertEquals("2 years ago", agoText(today.minusDays(800), today))
        assertEquals("First photo", afterFirstText(today, today))
        assertEquals("1 day after the first", afterFirstText(today, today.plusDays(1)))
        assertEquals("98 days after the first", afterFirstText(today, today.plusDays(98)))
    }

    @Test
    fun `an exif date is read, and an empty one is not`() {
        assertEquals(at(12, 9, 5), parseExifDate("2026:10:12 09:30:05"))
        assertNull(parseExifDate("0000:00:00 00:00:00"))
        assertNull(parseExifDate(null))
        assertNull(parseExifDate("garbage"))
    }
}
