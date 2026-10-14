// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

/**
 * Numbers in .xopp files (docs/xopp-format.md §3).
 *
 * Writing produces the shortest decimal that parses back to the same double, without an
 * exponent. For values with at most 8 significant digits this equals Xournal++'s `%.8g`.
 * Reading is strict (a malformed value makes the caller keep the element verbatim) but
 * accepts everything `g_ascii_strtod` produces: exponents and `nan`/`inf` forms.
 */
internal object NumberCodec {
    private const val LL_SUFFIX = "ll"

    fun formatDouble(value: Double): String =
        when {
            value.isNaN() -> if (value.toRawBits() < 0) "-nan" else "nan"
            value == Double.POSITIVE_INFINITY -> "inf"
            value == Double.NEGATIVE_INFINITY -> "-inf"
            else -> toPlainDecimal(value.toString())
        }

    fun formatDoubleList(values: DoubleArray): String = values.joinToString(separator = " ") { formatDouble(it) }

    fun parseDouble(text: String): Double? = parseDouble(text, 0, text.length, DecimalScanner(text))

    /** Parses an integer, accepting the `ll` suffix some Xournal++ 1.0/1.1 builds wrote. */
    fun parseLong(text: String): Long? {
        val digits = text.removeSuffix(LL_SUFFIX)
        val start = if (digits.startsWith('-')) 1 else 0
        if (digits.length == start || !digits.substring(start).all { it in '0'..'9' }) return null
        return digits.toLongOrNull()
    }

    /** Parses whitespace-separated doubles; null if any token is malformed. */
    fun parseDoubleList(text: String): DoubleArray? {
        // Counting first allocates exactly one array: strokes hold most of a file's numbers.
        val values = DoubleArray(countTokens(text))
        val scanner = DecimalScanner(text)
        var count = 0
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isXmlSpace()) index++
            if (index == text.length) break
            val start = index
            while (index < text.length && !text[index].isXmlSpace()) index++
            values[count++] = parseDouble(text, start, index, scanner) ?: return null
        }
        return values
    }

    private fun parseDouble(
        text: String,
        start: Int,
        end: Int,
        scanner: DecimalScanner,
    ): Double? =
        // Nearly every number in a file is a short plain decimal, which the scanner reads
        // without allocating (on Android, substring and toDouble are JNI calls, costly for
        // millions of coordinates).
        scanner.parse(start, end)
            ?: nonFinite(text, start, end)
            ?: if (isDecimal(text, start, end)) text.substring(start, end).toDouble() else null

    /** `[+-]? digits [. digits] [(e|E) [+-]? digits]`, with at least one mantissa digit. */
    private fun isDecimal(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        val scanner = Scanner(text, start, end)
        scanner.skip('+', '-')
        var mantissaDigits = scanner.skipDigits()
        if (scanner.skip('.')) mantissaDigits += scanner.skipDigits()
        val exponentValid =
            if (scanner.skip('e', 'E')) {
                scanner.skip('+', '-')
                scanner.skipDigits() > 0
            } else {
                true
            }
        return mantissaDigits > 0 && exponentValid && scanner.atEnd
    }

    private class Scanner(
        private val text: String,
        private var index: Int,
        private val end: Int,
    ) {
        val atEnd: Boolean get() = index == end

        /** Consumes one character if it is one of [chars]. */
        fun skip(vararg chars: Char): Boolean {
            val matches = index < end && text[index] in chars
            if (matches) index++
            return matches
        }

        fun skipDigits(): Int {
            val start = index
            while (index < end && text[index].isAsciiDigit()) index++
            return index - start
        }
    }

    private fun nonFinite(
        text: String,
        start: Int,
        end: Int,
    ): Double? {
        val length = end - start
        if (length < NON_FINITE_MIN_LENGTH || length > NON_FINITE_MAX_LENGTH) return null
        val token = text.substring(start, end).lowercase()
        val negative = token.startsWith('-')
        return when (token.trimStart('+', '-').takeIf { token.length - it.length <= 1 }) {
            "nan" -> if (negative) NEGATIVE_NAN else Double.NaN
            "inf", "infinity" -> if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            else -> null
        }
    }

    /** Rewrites Kotlin's `Double.toString()` (possibly `1.0E-5`) as a plain decimal. */
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

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private const val NON_FINITE_MIN_LENGTH = 3 // "nan", "inf"
    private const val NON_FINITE_MAX_LENGTH = 9 // "-infinity"
    private val NEGATIVE_NAN: Double = Double.fromBits(Double.NaN.toRawBits() or Long.MIN_VALUE)
}

/** XML whitespace (space, tab, CR, LF). */
internal fun Char.isXmlSpace(): Boolean = this == ' ' || this == '\n' || this == '\t' || this == '\r'

/** The number of whitespace-separated tokens in [text]. */
private fun countTokens(text: String): Int {
    var count = 0
    var inToken = false
    for (char in text) {
        val space = char.isXmlSpace()
        if (!space && !inToken) count++
        inToken = !space
    }
    return count
}
