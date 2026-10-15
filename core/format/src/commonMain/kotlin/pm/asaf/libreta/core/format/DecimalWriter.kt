// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Writes doubles as the shortest decimal that parses back to the same double, without an
 * exponent: closest to the double among the shortest, as Double.toString chooses.
 */
internal object DecimalWriter {
    /** Appends [value]. */
    fun append(
        out: StringBuilder,
        value: Double,
    ) {
        // Nearly every number in a file is a short decimal, written without allocating (on
        // Android, Double.toString and substrings cost seconds for a large note).
        if (!appendShortDecimal(out, value)) out.append(formatViaToString(value))
    }

    /** The shortest exact decimal of any double, through Double.toString (slow on Android). */
    fun formatViaToString(value: Double): String =
        when {
            value.isNaN() -> if (value.toRawBits() < 0) "-nan" else "nan"
            value == Double.POSITIVE_INFINITY -> "inf"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            else -> toPlainDecimal(value.toString())
        }

    /**
     * Appends [value] if it is a decimal with few enough digits to check exactly: the fewest
     * decimal places d such that n / 10^d, n = round(|value| × 10^d), is exactly |value|. With
     * n < 2^53 and d ≤ 22 that division is correctly rounded, so the text n·10^-d parses back
     * to [value] (Clinger's fast path); fewer places would not, so it is the shortest. Returns
     * false, appending nothing, for any other value.
     */
    private fun appendShortDecimal(
        out: StringBuilder,
        value: Double,
    ): Boolean {
        val magnitude = abs(value)
        var places = 0
        var scaled = 0L
        var exact = value == 0.0
        // Never true for NaN and infinities.
        while (!exact && places < POWERS_OF_TEN.size && magnitude * POWERS_OF_TEN[places] < TWO_TO_53) {
            scaled = (magnitude * POWERS_OF_TEN[places]).roundToLong()
            exact = scaled.toDouble() / POWERS_OF_TEN[places] == magnitude
            if (!exact) places++
        }
        if (exact) {
            if (value.toRawBits() < 0) out.append('-')
            appendScaled(out, scaled, places)
        }
        return exact
    }

    /** Appends [scaled] × 10^-[places], e.g. 1234 and 2 as "12.34", 5 and 3 as "0.005". */
    private fun appendScaled(
        out: StringBuilder,
        scaled: Long,
        places: Int,
    ) {
        // Digits from the last one, at the end of the buffer.
        val digits = CharArray(MAX_SCALED_CHARS)
        var start = digits.size
        var rest = scaled
        var written = 0
        while (rest > 0 || written <= places) {
            if (written == places && places > 0) digits[--start] = '.'
            digits[--start] = '0' + (rest % DECIMAL_BASE).toInt()
            rest /= DECIMAL_BASE
            written++
        }
        out.appendRange(digits, start, digits.size)
    }

    private fun toPlainDecimal(text: String): String {
        val negative = text.startsWith('-')
        val unsigned = if (negative) text.substring(1) else text
        val exponentIndex = unsigned.indexOfFirst { it == 'E' || it == 'e' }
        val mantissa = if (exponentIndex < 0) unsigned else unsigned.substring(0, exponentIndex)
        val exponent = if (exponentIndex < 0) 0 else unsigned.substring(exponentIndex + 1).toInt()
        val dot = mantissa.indexOf('.')
        val integerPart = if (dot < 0) mantissa else mantissa.substring(0, dot)
        val fractionPart = if (dot < 0) "" else mantissa.substring(dot + 1)

        var digits = integerPart + fractionPart
        var pointPosition = integerPart.length + exponent
        val firstNonZero = digits.indexOfFirst { it != '0' }
        if (firstNonZero < 0) return if (negative) "-0" else "0"
        digits = digits.substring(firstNonZero).trimEnd('0')
        pointPosition -= firstNonZero

        val body =
            when {
                pointPosition <= 0 -> "0." + "0".repeat(-pointPosition) + digits
                pointPosition >= digits.length -> digits + "0".repeat(pointPosition - digits.length)
                else -> digits.substring(0, pointPosition) + "." + digits.substring(pointPosition)
            }
        return if (negative) "-$body" else body
    }

    private const val DECIMAL_BASE = 10L
    private const val TWO_TO_53 = 9007199254740992.0

    /** Up to 22 places with a leading zero and a point, or 16 digits. */
    private const val MAX_SCALED_CHARS = 24

    /** 10^0 to 10^22: exactly representable as doubles. */
    private val POWERS_OF_TEN =
        DoubleArray(23).also { powers ->
            powers[0] = 1.0
            for (i in 1 until powers.size) powers[i] = powers[i - 1] * DECIMAL_BASE
        }
}
