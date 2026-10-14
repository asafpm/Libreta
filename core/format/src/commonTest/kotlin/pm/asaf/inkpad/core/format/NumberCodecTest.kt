// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberCodecTest {
    @Test
    fun formatsLikePercentDot8gForShortValues() {
        // Values as Xournal++ >= 1.2 writes them (%.8g).
        assertEquals("612", NumberCodec.formatDouble(612.0))
        assertEquals("0.85", NumberCodec.formatDouble(0.85))
        assertEquals("611.99998", NumberCodec.formatDouble(611.99998))
        assertEquals("-3.5", NumberCodec.formatDouble(-3.5))
        assertEquals("0", NumberCodec.formatDouble(0.0))
        assertEquals("-0", NumberCodec.formatDouble(-0.0))
    }

    @Test
    fun keepsPrecisionBeyondEightDigits() {
        // Xournal++ 1.1 wrote 8 decimals; %.8g would round this, we must not.
        assertEquals("461.01539741", NumberCodec.formatDouble(461.01539741))
    }

    @Test
    fun neverUsesExponents() {
        assertEquals("0.00001", NumberCodec.formatDouble(1e-5))
        assertEquals("12345000000", NumberCodec.formatDouble(1.2345e10))
        assertEquals("0.000000123", NumberCodec.formatDouble(1.23e-7))
    }

    @Test
    fun formatsNonFiniteValuesLikeC() {
        assertEquals("nan", NumberCodec.formatDouble(Double.NaN))
        assertEquals("-nan", NumberCodec.formatDouble(Double.fromBits(Double.NaN.toRawBits() or Long.MIN_VALUE)))
        assertEquals("inf", NumberCodec.formatDouble(Double.POSITIVE_INFINITY))
        assertEquals("-inf", NumberCodec.formatDouble(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun formatThenParseIsExactForRandomDoubles() {
        val random = Random(seed = 20261013)
        repeat(20_000) {
            val value = Double.fromBits(random.nextLong())
            if (value.isNaN()) return@repeat
            val text = NumberCodec.formatDouble(value)
            assertFalse('E' in text || 'e' in text, "exponent in $text")
            val parsed = assertNotNull(NumberCodec.parseDouble(text), text)
            assertEquals(value.toRawBits(), parsed.toRawBits(), text)
        }
    }

    @Test
    fun formatThenParseIsExactForPageCoordinates() {
        val random = Random(seed = 7)
        repeat(20_000) {
            val value = random.nextDouble(-50.0, 1200.0)
            assertEquals(value, NumberCodec.parseDouble(NumberCodec.formatDouble(value)))
        }
    }

    @Test
    fun parsesEveryFormSeenInFiles() {
        assertEquals(1.41, NumberCodec.parseDouble("1.41000000"))
        assertEquals(12.0, NumberCodec.parseDouble("12.00"))
        assertEquals(1e-5, NumberCodec.parseDouble("1e-05"))
        assertEquals(3.0, NumberCodec.parseDouble("+3"))
        assertEquals(0.5, NumberCodec.parseDouble(".5"))
        assertEquals(5.0, NumberCodec.parseDouble("5."))
        assertEquals((-0.0).toRawBits(), NumberCodec.parseDouble("-0")?.toRawBits())
    }

    @Test
    fun parsesNonFiniteValuesLikeStrtod() {
        // width_recovery.xopp has "nan" and "-nan" in pressure lists.
        assertTrue(assertNotNull(NumberCodec.parseDouble("nan")).isNaN())
        val negativeNan = assertNotNull(NumberCodec.parseDouble("-nan"))
        assertTrue(negativeNan.isNaN() && negativeNan.toRawBits() < 0)
        assertTrue(assertNotNull(NumberCodec.parseDouble("NaN")).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, NumberCodec.parseDouble("inf"))
        assertEquals(Double.NEGATIVE_INFINITY, NumberCodec.parseDouble("-Infinity"))
    }

    @Test
    fun rejectsMalformedNumbers() {
        for (text in listOf("", " 1", "1 ", "abc", "1.2.3", "0x10", "1,5", "1.5f", "e5", ".", "-", "1e", "nanx")) {
            assertNull(NumberCodec.parseDouble(text), "should reject '$text'")
        }
    }

    @Test
    fun parsesIntegersWithAndWithoutLlSuffix() {
        assertEquals(0L, NumberCodec.parseLong("0"))
        assertEquals(0L, NumberCodec.parseLong("0ll"))
        assertEquals(1L, NumberCodec.parseLong("1ll"))
        assertEquals(42L, NumberCodec.parseLong("42"))
        assertEquals(-3L, NumberCodec.parseLong("-3"))
        for (text in listOf("", "ll", "1l", "1.5", "x", "1 ", "--1")) {
            assertNull(NumberCodec.parseLong(text), "should reject '$text'")
        }
    }

    @Test
    fun parsesWhitespaceSeparatedLists() {
        assertContentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.5), NumberCodec.parseDoubleList("1 2  3\n4.5 "))
        assertContentEquals(doubleArrayOf(), NumberCodec.parseDoubleList(""))
        assertContentEquals(doubleArrayOf(), NumberCodec.parseDoubleList("  \n"))
        assertNull(NumberCodec.parseDoubleList("1 x 2"))
    }

    @Test
    fun formatsListsWithSingleSpaces() {
        assertEquals("1.41 0.4 0.42", NumberCodec.formatDoubleList(doubleArrayOf(1.41, 0.4, 0.42)))
        assertEquals("", NumberCodec.formatDoubleList(doubleArrayOf()))
    }
}
