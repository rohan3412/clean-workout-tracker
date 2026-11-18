package clean.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryRankingTest {
    private fun e(name: String, favourite: Boolean = false, sessions: Int = 0, recent: Int = 0, last: String = "", other: List<String> = emptyList()) =
        LibraryEntry(name, other, emptyList(), favourite, sessions, recent, last)

    private fun order(query: String, entries: List<LibraryEntry>): List<String> = rankLibrary(query, entries).let { r -> (r.done + r.more).map { it.name } }

    @Test fun `favourite beats performed, performed beats the library`() {
        val entries = listOf(
            e("Bench Press (Barbell)", sessions = 9, recent = 5, last = "2026-11-10"),
            e("Bench Press (Dumbbell)"),
            e("Bench Press (Smith Machine)", favourite = true),
        )
        val r = rankLibrary("bench press", entries)
        assertEquals(listOf("Bench Press (Smith Machine)", "Bench Press (Barbell)"), r.done.map { it.name })
        assertEquals(listOf("Bench Press (Dumbbell)"), r.more.map { it.name })
    }

    @Test fun `a typing slip on a performed exercise beats an exact match on a never done one`() {
        val entries = listOf(e("Lateral Raise (Cable)"), e("Lateral Raise (Dumbbell)", sessions = 3, recent = 1, last = "2026-10-01"))
        // "dumbell" is a slip of Dumbbell; "Lateral Raise (Cable)" does not match it at all, so use a word both have.
        assertEquals("Lateral Raise (Dumbbell)", order("laterl raise", entries).first())
        val exact = listOf(e("Laterl Raise"), e("Lateral Raise (Dumbbell)", sessions = 3, last = "2026-10-01"))
        assertEquals("Lateral Raise (Dumbbell)", order("laterl raise", exact).first())
    }

    @Test fun `closer match wins inside a group`() {
        val entries = listOf(
            e("Incline Chest Press (Machine)", sessions = 20, recent = 10, last = "2026-11-17"),
            e("Chest Press (Machine)", sessions = 1, last = "2025-01-01"),
        )
        assertEquals(listOf("Chest Press (Machine)", "Incline Chest Press (Machine)"), order("chest press", entries))
        val library = listOf(e("Wide Chest Press"), e("Chest Press"), e("Chest Press (Cable)"))
        assertEquals(listOf("Chest Press", "Chest Press (Cable)", "Wide Chest Press"), order("chest press", library))
    }

    @Test fun `recency breaks ties`() {
        val entries = listOf(
            e("Row (Cable)", sessions = 40, recent = 0, last = "2026-02-01"),
            e("Row (Machine)", sessions = 2, recent = 2, last = "2026-11-01"),
            e("Row (Barbell)", sessions = 2, recent = 2, last = "2026-11-15"),
        )
        assertEquals(listOf("Row (Barbell)", "Row (Machine)", "Row (Cable)"), order("row", entries))
        // The same date and use: the name.
        assertEquals(listOf("A Curl", "B Curl"), order("curl", listOf(e("B Curl", sessions = 1, last = "2026-01-01"), e("A Curl", sessions = 1, last = "2026-01-01"))))
    }

    @Test fun `a filter leaves entries out and a blank query keeps all`() {
        val entries = listOf(e("Squat", sessions = 1), e("Press"))
        val r = rankLibrary(SearchQuery("squat"), entries, keep = { false })
        assertTrue(r.done.isEmpty() && r.more.isEmpty())
        assertEquals(2, rankLibrary(SearchQuery(""), entries).let { it.done.size + it.more.size })
    }

    @Test fun `the nearest name`() {
        val entries = listOf(e("Bench Press (Barbell)"), e("Pulldown (Cable)"), e("Squat (Barbell)"))
        assertEquals("Bench Press (Barbell)", nearestName("bnech", entries))
        assertEquals("Bench Press (Barbell)", nearestName("bnech pres", entries))
        assertEquals("Pulldown (Cable)", nearestName("pulldwon", entries))
        assertNull(nearestName("zzzzzz", entries))
        assertNull(nearestName("ab", entries))
        // The one done more often wins a tie.
        assertEquals("Squat (Smith)", nearestName("squta", listOf(e("Squat (Barbell)"), e("Squat (Smith)", sessions = 4))))
    }

    @Test fun `joined and split words and shorthand find each other`() {
        val t = { n: String -> SearchTarget(n) }
        assertTrue(searchRank("pulldown", t("Lat Pull Down (Cable)")) != null)
        assertTrue(searchRank("pull down", t("Lat Pulldown (Cable)")) != null)
        assertTrue(searchRank("pushdown", t("Triceps Push-Down")) != null)
        assertTrue(searchRank("facepull", t("Face Pull (Cable)")) != null)
        assertTrue(searchRank("face pull", t("Facepull")) != null)
        assertTrue(searchRank("skullcrusher", t("Skull Crusher")) != null)
        assertTrue(searchRank("ghr", t("Glute Ham Raise")) != null)
        assertTrue(searchRank("ht", t("Hip Thrust (Barbell)")) != null)
        assertTrue(searchRank("lunges", t("Walking Lunge (Dumbbell)")) != null)
        assertTrue(searchRank("pullup", t("Pull Up")) != null)
        assertTrue(searchRank("pullups", t("Chin Up")) != null)
        assertTrue(searchRank("pull up", t("Chin Up (Assisted)")) != null)
        assertTrue(searchRank("chin up", t("Pullup")) != null)
        assertTrue(searchRank("dl", t("Deadlift (Barbell)")) != null)
        assertTrue(searchRank("sq", t("Squat (Barbell)")) != null)
        assertTrue(searchRank("flies", t("Chest Fly (Cable)")) != null)
        assertTrue(searchRank("flye", t("Chest Fly (Cable)")) != null)
        assertTrue(searchRank("rdls", t("Romanian Deadlift (Barbell)")) != null)
        assertTrue(searchRank("kb swing", t("Swing (Kettlebell)")) != null)
        assertTrue(searchRank("ez curl", t("Curl (EZ Bar)")) != null)
        assertTrue(searchRank("sldl", t("Stiff Leg Deadlift")) != null)
        assertTrue(searchRank("pressdown", t("Triceps Pushdown")) != null)
    }
}
