package clean.app

import clean.core.Workout

/** What an arrangeable page (Home, Profile) needs to know about each of its cards; the arrange screen and the layout rules work on this. */
interface ArrangeCard {
    val key: String
    val label: String
    val about: String
    val wide: Boolean
    val canHalf: Boolean
}

/**
 * A card Home can show. [key] is what is stored (kv `home.cards:<profileId>`), [about] its line in Arrange Home, [wide] the width it
 * has until the user changes it, and [canHalf] whether it can be a half-width card at all (a calendar or a long chart cannot).
 */
enum class HomeCard(override val key: String, override val label: String, override val about: String, override val wide: Boolean, override val canHalf: Boolean = true) : ArrangeCard {
    Today("today", "Today", "What to train next, with Start", wide = true, canHalf = false),
    OneRm1("onerm1", "1RM: first lift", "The estimated 1RM of the lift you log most, over time", wide = false),
    Streak("streak", "Week streak", "Weeks in a row that met the weekly target", wide = false),
    Week("week", "This week", "The ring, and the program when there is one", wide = false),
    OneRm2("onerm2", "1RM: second lift", "The same for the lift you log second most", wide = false),
    Weight("weight", "Body weight", "The latest weigh-in, with a small chart and a + to log one", wide = false),
    Volume("volume", "Volume", "What you have lifted this week", wide = false),
    Last("last", "Last workout", "The most recent one", wide = true, canHalf = false),
    Workouts("workouts", "Workouts", "How many you have logged", wide = false),
    Month("month", "This month", "Workouts this month", wide = false),
    Recovery("recovery", "Recovery", "Which muscles are ready", wide = true),
    Prs("prs", "PRs, 30 days", "How many PRs lately", wide = false),
    WeightChart("weightchart", "Body weight chart", "The weigh-in chart", wide = true, canHalf = false),
    MonthVolume("monthvolume", "Monthly volume", "What you have lifted this calendar month", wide = false),
    LongestStreak("beststreak", "Longest streak", "The most weeks in a row that met the weekly target", wide = false),
    WeekFigure("weekfigure", "Trained this week", "The body figure shaded by this week's sets", wide = false),
    TopMuscles("topmuscles", "Top muscles this week", "The muscles with the most sets this week", wide = false),
    RecentPrs("recentprs", "Recent PRs", "The last few PRs, with the set", wide = true),
    RecentWorkouts("recentworkouts", "Recent workouts", "The last three workouts", wide = true, canHalf = false),
    ChosenLift("chosenlift", "1RM: a lift you pick", "The estimated 1RM of an exercise you choose", wide = false),
    WeeklyBars("weeklybars", "Weekly volume", "The last eight weeks as bars", wide = true),
    NextWorkout("nextworkout", "Next workout", "The template that comes next, with Start", wide = false),
    AvgDuration("avgduration", "Average duration", "How long workouts have been lately", wide = false),
    FatigueGlance("fatigueglance", "Fatigue at a glance", "The body figure in the fatigue colours", wide = false),
    Goals("goals", "Goals", "Exercise, body weight and weekly goals, with progress and the day the trend reaches them", wide = true),
    YearAgo("yearago", "A year ago", "A lift now against about a year back, shown only when it moved by 5 % or more", wide = false),
}

/** A card with the width it is shown at. */
data class CardEntry<C : ArrangeCard>(val card: C, val wide: Boolean) {
    /** `key:w` or `key:h`, as stored. */
    val stored: String get() = card.key + (if (wide) ":w" else ":h")
}

typealias HomeEntry = CardEntry<HomeCard>

/** What Home shows until the user changes it (and after Reset): Today, then two columns of half cards, then the last workout. */
val HOME_DEFAULT: List<HomeEntry> = listOf(
    HomeEntry(HomeCard.Today, true), HomeEntry(HomeCard.OneRm1, false), HomeEntry(HomeCard.Streak, false), HomeEntry(HomeCard.Week, false),
    HomeEntry(HomeCard.OneRm2, false), HomeEntry(HomeCard.Weight, false), HomeEntry(HomeCard.Volume, false), HomeEntry(HomeCard.Last, true),
)

private fun <C : ArrangeCard> entry(card: C, wide: Boolean) = CardEntry(card, wide || !card.canHalf)

/**
 * The shown cards of a page whose cards are [cards], in order, from the stored text `key:w,key:h,...`, or [default] for null. A key without a
 * width takes its card's own. Unknown keys are skipped and a card is listed once.
 */
fun <C : ArrangeCard> parseCards(stored: String?, cards: List<C>, default: List<CardEntry<C>>): List<CardEntry<C>> {
    if (stored == null) return default
    val out = mutableListOf<CardEntry<C>>()
    for (token in stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        val card = cards.firstOrNull { it.key == token.substringBefore(':') } ?: continue
        out += entry(card, when (token.substringAfter(':', "")) { "w" -> true; "h" -> false; else -> card.wide })
    }
    return out.distinctBy { it.card }
}

/**
 * The shown cards, in order, from the stored text: `today:w,onerm1:h,...`, or null for the default. A key without a width takes its
 * card's own. A list stored before widths existed (no `:` anywhere) is kept as it was: every card wide, "At a glance" in place as its
 * four tiles (half), and the old weigh-in chart card as the Body weight chart. Unknown keys are skipped and a card is listed once.
 */
fun parseHome(stored: String?): List<HomeEntry> {
    if (stored == null) return HOME_DEFAULT
    val tokens = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val legacy = tokens.none { ':' in it }
    if (!legacy) return parseCards(stored, HomeCard.entries, HOME_DEFAULT)
    val out = mutableListOf<HomeEntry>()
    for (token in tokens) {
        val key = token.substringBefore(':')
        val mark = token.substringAfter(':', "")
        if (legacy && key == "glance") {
            for (card in listOf(HomeCard.Workouts, HomeCard.Month, HomeCard.Streak, HomeCard.Weight)) out += entry(card, false)
            continue
        }
        val card = HomeCard.entries.firstOrNull { it.key == (if (legacy && key == "weight") "weightchart" else key) } ?: continue
        out += entry(card, when {
            legacy -> true
            mark == "w" -> true
            mark == "h" -> false
            else -> card.wide
        })
    }
    return out.distinctBy { it.card }
}

/** The text [parseHome] reads back. */
fun <C : ArrangeCard> encodeHome(entries: List<CardEntry<C>>): String = entries.joinToString(",") { it.stored }

/** [list] with [card] moved to place [to] (clamped into the list); the others keep their order and widths. */
fun <C : ArrangeCard> moveEntry(list: List<CardEntry<C>>, card: C, to: Int): List<CardEntry<C>> {
    val from = list.indexOfFirst { it.card == card }
    if (from < 0) return list
    val target = to.coerceIn(0, list.size - 1)
    return if (target == from) list else list.toMutableList().also { it.add(target, it.removeAt(from)) }
}

/** [list] with [card] flipped between wide and half; a card that cannot be half stays wide. */
fun <C : ArrangeCard> toggleWidth(list: List<CardEntry<C>>, card: C): List<CardEntry<C>> =
    list.map { if (it.card == card && card.canHalf) it.copy(wide = !it.wide) else it }

/** The cards left out of [shown], in the order of the enum. */
fun hiddenHome(shown: List<HomeEntry>): List<HomeCard> = hiddenCards(HomeCard.entries, shown)

/** The cards of [all] left out of [shown], in the order of [all]. */
fun <C : ArrangeCard> hiddenCards(all: List<C>, shown: List<CardEntry<C>>): List<C> = all.filter { c -> shown.none { it.card == c } }

/** The cards as rows of Home: each wide card alone, each run of half cards together (to be laid out in columns or in pairs). */
fun <C : ArrangeCard> homeSegments(entries: List<CardEntry<C>>): List<List<CardEntry<C>>> {
    val out = mutableListOf<MutableList<CardEntry<C>>>()
    for (e in entries) {
        if (!e.wide && out.lastOrNull()?.firstOrNull()?.wide == false) out.last() += e else out += mutableListOf(e)
    }
    return out
}

/**
 * Which of two columns each half card goes to: in order, each into the column that is shorter so far (the left one on a tie). A card with
 * no height (one that shows nothing) is not placed (-1) and takes no [gap].
 */
fun placeColumns(heights: List<Int>, gap: Int): List<Int> {
    val used = intArrayOf(0, 0)
    return heights.map { h ->
        if (h <= 0) -1
        else {
            val c = if (used[1] < used[0]) 1 else 0
            used[c] += h + gap
            c
        }
    }
}

/** The days of history that decide a lift is "logged most": twelve weeks. */
const val HOME_LIFT_DAYS = 84

/** The exercises done in [workouts] on or after [since] (`YYYY-MM-DD`), most sessions first, then A to Z. */
fun liftsByFrequency(workouts: List<Workout>, since: String): List<String> {
    val counts = HashMap<String, Int>()
    for (w in workouts) if (w.day >= since) for (name in w.blocks.filter { it.sets.isNotEmpty() }.map { it.exercise }.distinct()) counts.merge(name, 1, Int::plus)
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
}
