package clean.core

import kotlin.math.floor

/** java's `Math.round`: halves go up (`Math.round(-2.5)` is -2), unlike Kotlin's `roundToLong`, which goes to even. */
fun jRound(d: Double): Long {
    if (d.isNaN()) return 0
    val f = floor(d)
    return (if (d - f >= 0.5) f + 1 else f).toLong()
}

/** java's `Math.round(float)`: halves go up, as an Int. */
fun jRound(f: Float): Int {
    if (f.isNaN()) return 0
    val fl = floor(f)
    return (if (f - fl >= 0.5f) fl + 1 else fl).toInt()
}

/**
 * The number as `BigDecimal(value).setScale(scale, HALF_UP)` made it, as the unscaled integer: `roundedUnscaled(2.675, 2)` is 267
 * because the double 2.675 is a hair under it. Exact: the double's bits are scaled in whole numbers, never through another double.
 * Throws on NaN and infinity (as BigDecimal did) and on values too large for a Long once scaled.
 */
fun roundedUnscaled(value: Double, scale: Int): Long {
    require(value.isFinite()) { "Infinite or NaN" }
    require(scale in 0..9) { "scale" }
    if (value == 0.0) return 0
    val bits = value.toRawBits()
    val negative = bits < 0
    val exponentBits = ((bits ushr 52) and 0x7FF).toInt()
    var mantissa = bits and 0xFFFFFFFFFFFFFL
    var exponent: Int
    if (exponentBits == 0) { exponent = -1074 } else { mantissa = mantissa or (1L shl 52); exponent = exponentBits - 1075 }
    // value = mantissa * 2^exponent; take out the factors of two so that odd mantissas stay short.
    val zeros = mantissa.countTrailingZeroBits()
    mantissa = mantissa ushr zeros
    exponent += zeros
    var factor = 1L
    repeat(scale) { factor *= 5 } // 10^scale = 5^scale * 2^scale
    // result = mantissa * 5^scale * 2^(exponent + scale), rounded half up when that power is negative
    val shift = exponent + scale
    val result: Long = if (shift >= 0) {
        require(shift < 62 && mantissa < (Long.MAX_VALUE shr shift) / factor) { "Too large" }
        mantissa * factor shl shift
    } else {
        val t = -shift
        // A = mantissa * factor as a 128-bit pair (hi * 2^32 + lo); mantissa has at most 53 bits, factor at most 22.
        val low = (mantissa and 0xFFFFFFFFL) * factor
        val high = (mantissa ushr 32) * factor + (low ushr 32)
        var lo = low and 0xFFFFFFFFL
        var hi = high
        if (t - 1 < 32) { lo += 1L shl (t - 1); hi += lo ushr 32; lo = lo and 0xFFFFFFFFL } else if (t - 1 < 75) hi += 1L shl (t - 1 - 32)
        when {
            t >= 94 -> 0L
            t >= 32 -> hi ushr (t - 32)
            else -> {
                require(hi < (1L shl (30 + t))) { "Too large" }
                (hi shl (32 - t)) or (lo ushr t)
            }
        }
    }
    return if (negative) -result else result
}

/**
 * [value] to [scale] decimals, half up, in plain digits with no trailing zeros (`12.5`, `10`, `0`): what
 * `BigDecimal(value).setScale(scale, HALF_UP).stripTrailingZeros().toPlainString()` gave.
 */
fun plainDecimal(value: Double, scale: Int): String {
    val unscaled = roundedUnscaled(value, scale)
    if (unscaled == 0L) return "0"
    val digits = (if (unscaled < 0) -unscaled else unscaled).toString().padStart(scale + 1, '0')
    val whole = digits.dropLast(scale)
    val fraction = digits.takeLast(scale).trimEnd('0')
    return (if (unscaled < 0) "-" else "") + whole + if (fraction.isEmpty()) "" else ".$fraction"
}


/** java's `"%0{width}d".format(value)`: the sign counts toward the width. */
fun zeroPad(value: Long, width: Int): String =
    if (value < 0) "-" + (-value).toString().padStart(width - 1, '0') else value.toString().padStart(width, '0')
