// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

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
    fun shortDecimalsAreWrittenAsDoubleToStringWouldBe() {
        val random = Random(seed = 42)
        repeat(50_000) {
            // Decimals as files have them: up to 12 places, positive and negative.
            val places = random.nextInt(0, 13)
            val digits = random.nextLong(0, 10_000_000_000_000L).toString().padStart(places + 1, '0')
            val text = digits.dropLast(places) + (if (places > 0) "." + digits.takeLast(places) else "")
            val value = text.toDouble() * (if (random.nextBoolean()) -1 else 1)
            assertEquals(DecimalWriter.formatViaToString(value), NumberCodec.formatDouble(value), text)
        }
        for (value in listOf(0.1, 0.2, 0.3, 1e-7, 1e-22, 5e-324, 123.0, 1e15, 9007199254740991.0, 9007199254740993.0)) {
            assertEquals(DecimalWriter.formatViaToString(value), NumberCodec.formatDouble(value))
            assertEquals(DecimalWriter.formatViaToString(-value), NumberCodec.formatDouble(-value))
        }
    }

    @Test
    fun anyDoubleIsWrittenAsDoubleToStringWouldBe() {
        val random = Random(seed = 43)
        repeat(50_000) {
            val value = if (it % 2 == 0) random.nextDouble(-50.0, 1200.0) else Double.fromBits(random.nextLong())
            assertEquals(DecimalWriter.formatViaToString(value), NumberCodec.formatDouble(value))
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

    @Test
    fun fastPathParsesExactlyLikeToDouble() {
        val forms =
            listOf(
                "0",
                "-0",
                "+0",
                "0.0",
                "-0.0",
                "5.",
                ".5",
                "-.5",
                "+12.25",
                "007.50",
                "0.000000000000000000001",
                "0.0000000000000000000000000123",
                "123456789012345",
                "1234567890123456",
                "99999999999999.9",
                "0.1",
                "0.2",
                "0.3",
                "1.1",
                "2.675",
                "841.88976378",
                "595.27559055",
                "1e5",
                "1.5E-3",
            )
        for (form in forms) assertEquals(form.toDouble().toRawBits(), NumberCodec.parseDouble(form)?.toRawBits(), form)
        val random = Random(7)
        repeat(200_000) {
            val digits = random.nextInt(1, 18)
            val raw = (1..digits).map { '0' + random.nextInt(10) }.joinToString("")
            val point = random.nextInt(0, digits + 1)
            val sign = listOf("", "-", "+")[random.nextInt(3)]
            val form = sign + raw.substring(0, point) + "." + raw.substring(point)
            assertEquals(form.toDouble().toRawBits(), NumberCodec.parseDouble(form)?.toRawBits(), form)
        }
    }
}
