package clean.core

/** The four outer tabs, by lowercase name, in the order the bar has until the user changes it. Workout is not among them: it stays in the middle. */
val NAV_DEFAULT: List<String> = listOf("home", "progress", "exercises", "profile")

/**
 * The outer tabs in the order stored as `home,progress,exercises,profile`. Names are read without regard to case or spaces; an unknown
 * name (Workout included) and a repeat are dropped, and a tab left out goes back after the others in the default order, so the result
 * always holds the four, whatever was stored.
 */
fun navOrder(stored: String?): List<String> {
    val read = stored.orEmpty().split(',').map { it.trim().lowercase() }.filter { it in NAV_DEFAULT }.distinct()
    return read + NAV_DEFAULT.filter { it !in read }
}

/** The five tabs as the bar and the pager lay them out: the four outer tabs in [outer]'s order with [middle] third. */
fun <T> withMiddle(outer: List<T>, middle: T): List<T> = outer.take(2) + middle + outer.drop(2)
