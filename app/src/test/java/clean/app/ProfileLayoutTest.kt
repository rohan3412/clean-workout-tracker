package clean.app

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ProfileLayoutTest {
    private fun keys(list: List<CardEntry<ProfileCard>>) = list.map { it.stored }

    @Test
    fun `the default is the list the user asked for`() {
        assertEquals(PROFILE_DEFAULT, parseProfile(null))
        assertEquals(
            listOf("profile:w", "totals:w", "photos:w", "membersince:h", "weight:h", "measurements:w", "milestones:w", "goals:w", "trophies:w"),
            keys(PROFILE_DEFAULT),
        )
    }

    @Test
    fun `stored text round-trips and a card that cannot be half stays wide`() {
        val list = parseProfile("profile:h,photos:h,streak:w,bogus:w,photos:w")
        // profile cannot be half; photos is listed once; the unknown key is skipped.
        assertEquals(listOf("profile:w", "photos:h", "streak:w"), keys(list))
        assertEquals(list, parseProfile(encodeHome(list)))
        assertTrue(parseProfile("").isEmpty())
    }

    @Test
    fun `hidden cards are the ones left out and moving works as on Home`() {
        val hidden = hiddenCards(ProfileCard.entries, PROFILE_DEFAULT)
        assertTrue(ProfileCard.Streak in hidden && ProfileCard.Totals !in hidden)
        assertEquals(ProfileCard.entries.size, hidden.size + PROFILE_DEFAULT.size)
        assertEquals("trophies:w", keys(moveEntry(PROFILE_DEFAULT, ProfileCard.Trophies, 0)).first())
        assertEquals("photos:h", keys(toggleWidth(PROFILE_DEFAULT, ProfileCard.Photos))[2])
        assertEquals(PROFILE_DEFAULT, toggleWidth(PROFILE_DEFAULT, ProfileCard.Details))
    }
}
