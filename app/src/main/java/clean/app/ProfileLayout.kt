package clean.app

/**
 * A card the Profile tab can show, arranged like Home's (kv `profile.cards:<profileId>`, `key:w,key:h`; the layout rules are Home's, in
 * `HomeLayout.kt`). [about] is its line in Arrange profile, [wide] the width it has until the user changes it, [canHalf] whether it can be half width.
 */
enum class ProfileCard(override val key: String, override val label: String, override val about: String, override val wide: Boolean, override val canHalf: Boolean = true) : ArrangeCard {
    Details("profile", "Profile", "Name, units and height, with Edit profile and Switch profile", wide = true, canHalf = false),
    Totals("totals", "Lifetime totals", "Workouts, time trained, volume lifted and the week streak, since the first workout", wide = true),
    Photos("photos", "Progress photos", "The latest photos, adding one, and the first against the latest", wide = true),
    MemberSince("membersince", "Member since", "The month of your first workout, and how long ago", wide = false),
    Streak("streak", "Week streak", "Weeks in a row that met the weekly target", wide = false),
    Measurements("measurements", "Body measurements", "The latest of each measurement, with its change", wide = true),
    Milestones("milestones", "Milestones", "The latest reached and the next one, with its progress", wide = true, canHalf = false),
    Trophies("trophies", "Trophies", "Badges, the streak and the program", wide = true),
    Weight("weight", "Body weight", "The latest weigh-in, with a small chart and a + to log one", wide = false),
    Workouts("workouts", "Workouts", "How many you have logged", wide = false),
    RecentPrs("recentprs", "Recent PRs", "The last few PRs, with the set", wide = true),
    Goals("goals", "Goals", "Exercise, body weight and weekly goals, with progress and the day the trend reaches them", wide = true),
}

/** What Profile shows until the user changes it (and after Reset). */
val PROFILE_DEFAULT: List<CardEntry<ProfileCard>> = listOf(
    CardEntry(ProfileCard.Details, true), CardEntry(ProfileCard.Totals, true), CardEntry(ProfileCard.Photos, true),
    CardEntry(ProfileCard.MemberSince, false), CardEntry(ProfileCard.Weight, false), CardEntry(ProfileCard.Measurements, true),
    CardEntry(ProfileCard.Milestones, true), CardEntry(ProfileCard.Goals, true), CardEntry(ProfileCard.Trophies, true),
)

/** The cards Profile shows, from the stored text, else the default. */
fun parseProfile(stored: String?): List<CardEntry<ProfileCard>> = parseCards(stored, ProfileCard.entries, PROFILE_DEFAULT)
