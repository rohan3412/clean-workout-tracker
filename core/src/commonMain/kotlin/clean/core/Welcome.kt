package clean.core

import kotlin.math.roundToInt

/**
 * Whether the welcome questionnaire is offered: a profile with no workout rows, no measurements and no stored "done".
 * A profile that already holds data never gets it, whatever else is missing.
 */
fun showWelcome(workoutRows: Int, measurements: Int, done: Boolean): Boolean = workoutRows == 0 && measurements == 0 && !done

const val CM_PER_INCH = 2.54

/** A height in whole inches, as the questionnaire's ruler picks it, in centimetres. */
fun inchesToCm(inches: Int): Double = inches * CM_PER_INCH

/** The nearest whole inch to [cm]. */
fun cmToInches(cm: Double): Int = (cm / CM_PER_INCH).roundToInt()

/** Whole inches as feet and the inches over, `70` as 5 and 10. */
fun feetAndInches(inches: Int): Pair<Int, Int> = inches / 12 to inches % 12
