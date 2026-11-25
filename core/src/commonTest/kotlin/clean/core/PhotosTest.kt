package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

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
    fun `photos are grouped by day newest day first front side back within a day`() {
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
    fun `the pose to compare is the asked one else the one with most photos else all`() {
        val all = listOf(photo(10, Pose.FRONT), photo(11, Pose.SIDE), photo(12, Pose.SIDE), photo(13, Pose.SIDE), photo(14, Pose.BACK), photo(15, Pose.BACK))
        assertEquals(Pose.BACK, comparePose(all, Pose.BACK))
        assertEquals(Pose.SIDE, comparePose(all, Pose.FRONT))
        assertEquals(Pose.SIDE, comparePose(all, null))
        assertNull(comparePose(listOf(photo(10, Pose.FRONT), photo(11, Pose.SIDE)), null))
    }

    @Test
    fun `how far apart reads in days weeks then years`() {
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
    fun `an exif date is read and an empty one is not`() {
        assertEquals(at(12, 9, 5), parseExifDate("2026:10:12 09:30:05"))
        assertNull(parseExifDate("0000:00:00 00:00:00"))
        assertNull(parseExifDate(null))
        assertNull(parseExifDate("garbage"))
    }

    // ---- Round 39: one Photos section over the two stores ----

    private fun snap(day: Int, hour: Int = 9, id: String = "0a1b2c3d") = momentOf(at(day, hour), id)

    @Test
    fun `both stores merge newest first and the label says which kind`() {
        val pics = mergedPics(listOf(photo(5, Pose.FRONT), photo(1, Pose.SIDE)), listOf(snap(9), snap(3, id = "0a1b2c3e")))
        assertEquals(listOf("Snapshot", "Front", "Snapshot", "Side"), pics.map { it.label })
        assertEquals(listOf(9, 5, 3, 1), pics.map { it.day.dayOfMonth })
        assertEquals(true, pics.first().snapshot)
        assertEquals(Pose.FRONT, pics[1].pose)
    }

    @Test
    fun `the filters pick the kind and the pose`() {
        val pics = mergedPics(listOf(photo(5, Pose.FRONT), photo(4, Pose.SIDE), photo(3, Pose.BACK)), listOf(snap(9)))
        assertEquals(4, pics.count { PhotoFilter.ALL.matches(it) })
        assertEquals(3, pics.count { PhotoFilter.PROGRESS.matches(it) })
        assertEquals(1, pics.count { PhotoFilter.SNAPSHOTS.matches(it) })
        assertEquals(listOf("Side"), pics.filter { PhotoFilter.SIDE.matches(it) }.map { it.label })
        assertEquals(PhotoFilter.BACK, PhotoFilter.of(Pose.BACK))
        assertEquals(PhotoFilter.PROGRESS, PhotoFilter.of(null))
        assertEquals(listOf(9, 5), groupPicsByDay(pics.filter { it.day.dayOfMonth != 4 && it.day.dayOfMonth != 3 }).map { it.day.dayOfMonth })
    }

    @Test
    fun `compare is offered for progress photos only`() {
        val pics = mergedPics(listOf(photo(5, Pose.FRONT), photo(1, Pose.FRONT), photo(2, Pose.SIDE)), listOf(snap(9), snap(8, id = "0a1b2c3e")))
        assertEquals(false, compareOffered(pics, PhotoFilter.ALL))
        assertEquals(false, compareOffered(pics, PhotoFilter.SNAPSHOTS))
        assertEquals(true, compareOffered(pics, PhotoFilter.PROGRESS))
        assertEquals(true, compareOffered(pics, PhotoFilter.FRONT))
        assertEquals(false, compareOffered(pics, PhotoFilter.SIDE))
    }

    @Test
    fun `the summary line counts both kinds and says when`() {
        val pics = mergedPics(listOf(photo(1, Pose.FRONT), photo(5, Pose.FRONT)), listOf(snap(12)))
        // Under a week: relative.
        assertEquals("3 photos · 2 progress · since Oct 2026 · last 2 days ago", photosSummary(pics, at(14, 9)))
        // A week or more: the date.
        assertEquals("3 photos · 2 progress · since Oct 2026 · last 12 Oct", photosSummary(pics, at(24, 9)))
        // No progress photos: that part is left out; one photo is singular.
        assertEquals("1 photo · since Oct 2026 · last 12 Oct", photosSummary(mergedPics(emptyList(), listOf(snap(12))), at(24, 9)))
        assertEquals("", photosSummary(emptyList(), at(24)))
    }

    @Test
    fun `the span is between the first and latest photo of the pose with most`() {
        val pics = mergedPics(listOf(photo(1, Pose.FRONT), photo(15, Pose.FRONT), photo(3, Pose.SIDE)), listOf(snap(20)))
        assertEquals("2 weeks between your first and latest front photo", photosSpan(pics))
        assertNull(photosSpan(mergedPics(listOf(photo(1, Pose.FRONT)), listOf(snap(20)))))
    }

    @Test
    fun `refiling keeps each kind in its own store and a change of kind is a rename between them`() {
        val none = emptySet<String>()
        val front = photo(5, Pose.FRONT).asPic()
        // Progress to snapshot: a new moment name with a new id; the photo's own name is no longer in the photo store.
        val toSnap = refiled(front, front.at, null, setOf(front.name), none) { "deadbeef" }
        assertEquals(true, toSnap.snapshot)
        assertEquals("20261005-093000_deadbeef.jpg", toSnap.name)
        // And back: the snapshot becomes a photo of the pose, named as photos are.
        val back = refiled(toSnap, toSnap.at, Pose.SIDE, none, setOf(toSnap.name)) { "x" }
        assertEquals("20261005-093000_side.jpg", back.name)
        assertEquals(Pose.SIDE, back.pose)
        // A snapshot given a new date keeps its id; a taken name gets a new id.
        val moved = refiled(snap(9).asPic(), at(10), null, none, setOf(snap(9).name)) { "newid000" }
        assertEquals("20261010-093000_0a1b2c3d.jpg", moved.name)
        val clash = refiled(snap(9).asPic(), at(10), null, none, setOf(snap(9).name, moved.name)) { "newid000" }
        assertEquals("20261010-093000_newid000.jpg", clash.name)
        // Both stores' names stay parseable by their own store only.
        assertNull(parsePhoto(toSnap.name)); assertNull(parseMoment(back.name))
    }
}
