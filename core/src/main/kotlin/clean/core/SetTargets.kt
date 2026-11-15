package clean.core

/** Sets a week to aim for in a muscle group, from [min] to [max] (fractional sets counted as on the Body tab). */
data class SetRange(val min: Int, val max: Int) {
    /** `10-20`. */
    fun encode() = "$min-$max"

    companion object {
        val DEFAULT = SetRange(10, 20)

        fun decode(text: String?): SetRange? {
            val min = text?.substringBefore('-')?.trim()?.toIntOrNull() ?: return null
            val max = text.substringAfter('-', "").trim().toIntOrNull() ?: return null
            return if (min in 0..max && max > 0) SetRange(min, max) else null
        }
    }
}

/**
 * The range that applies to one muscle: its own text (`10-20`, or `off` for none), else the default
 * text, else [SetRange.DEFAULT]. Null means no target.
 */
fun setRangeFor(own: String?, default: String?): SetRange? =
    if (own == "off") null else SetRange.decode(own) ?: SetRange.decode(default) ?: SetRange.DEFAULT

enum class RangeState { UNDER, WITHIN, OVER }

/** Where a week's [sets] stand against [range]: under its minimum, within it (ends included) or over its maximum. */
fun weeklySetState(sets: Double, range: SetRange): RangeState = when {
    sets < range.min -> RangeState.UNDER
    sets > range.max -> RangeState.OVER
    else -> RangeState.WITHIN
}
