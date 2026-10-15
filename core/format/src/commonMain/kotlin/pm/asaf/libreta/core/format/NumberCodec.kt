// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

/**
 * Numbers in .xopp files (docs/xopp-format.md §3).
 *
 * Writing produces the shortest decimal that parses back to the same double, without an
 * exponent ([DecimalWriter]). For values with at most 8 significant digits this equals
 * Xournal++'s `%.8g`.
 * Reading is strict (a malformed value makes the caller keep the element verbatim) but
 * accepts everything `g_ascii_strtod` produces: exponents and `nan`/`inf` forms.
 */
internal object NumberCodec {
    private const val LL_SUFFIX = "ll"

    fun formatDouble(value: Double): String = buildString { DecimalWriter.append(this, value) }

    /** Appends [value] as [formatDouble] writes it. */
    fun appendDouble(
        out: StringBuilder,
        value: Double,
    ) = DecimalWriter.append(out, value)

    fun formatDoubleList(values: DoubleArray): String =
        buildString {
            for (index in values.indices) {
                if (index > 0) append(' ')
                DecimalWriter.append(this, values[index])
            }
        }

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
