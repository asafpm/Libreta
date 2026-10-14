// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

/**
 * Parses plain decimals (`[+-]? digits [. digits]`) of [text] with at most [MAX_DIGITS]
 * significant digits, allocating nothing, so that one scanner can read all the numbers of a
 * stroke. Anything else (exponents, `nan`, more digits) gives null, for the general parser.
 *
 * The result is exact (Clinger's fast path): the digits and the power of ten are exact
 * doubles, so one correctly rounded division gives the same double as `toDouble`.
 */
internal class DecimalScanner(
    private val text: String,
) {
    private var index = 0
    private var end = 0
    private var mantissa = 0L
    private var significant = 0
    private var digits = 0

    /** The number in `text[start, end)`, or null if it is not a short plain decimal. */
    fun parse(
        start: Int,
        end: Int,
    ): Double? {
        index = start
        this.end = end
        mantissa = 0L
        significant = 0
        digits = 0
        val negative = sign()
        readDigits()
        val integerDigits = digits
        if (index < end && text[index] == '.') {
            index++
            readDigits()
        }
        val fractionDigits = digits - integerDigits
        val valid = index == end && digits > 0 && significant <= MAX_DIGITS && fractionDigits < POWERS_OF_TEN.size
        val magnitude = mantissa.toDouble() / POWERS_OF_TEN[if (valid) fractionDigits else 0]
        return when {
            !valid -> null
            negative -> -magnitude
            else -> magnitude
        }
    }

    /** Skips a sign; true if it is a minus. */
    private fun sign(): Boolean {
        val char = if (index < end) text[index] else ' '
        if (char == '-' || char == '+') index++
        return char == '-'
    }

    /** Reads digits, stopping early once there are too many to be exact. */
    private fun readDigits() {
        while (index < end && significant <= MAX_DIGITS) {
            val char = text[index]
            if (char !in '0'..'9') return
            if (mantissa != 0L || char != '0') significant++
            mantissa = mantissa * TEN + (char - '0')
            digits++
            index++
        }
    }

    private companion object {
        const val TEN = 10L

        /** Integers below 10^15 are exact doubles. */
        const val MAX_DIGITS = 15
        const val MAX_EXACT_POWER = 22

        /** 10^0 to 10^22, all exact doubles. */
        val POWERS_OF_TEN =
            DoubleArray(MAX_EXACT_POWER + 1) { 1.0 }.also { powers ->
                for (i in 1 until powers.size) powers[i] = powers[i - 1] * TEN
            }
    }
}
