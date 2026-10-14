// SPDX-License-Identifier: GPL-2.0-or-later
// Expected values derived from Xournal++ v1.3.8 src/core/control/xojfile/LoadHandlerHelper.cpp
// and test/unit_tests/control/LoadHandlerTest.cpp (testText), © Xournal++ contributors.

package pm.asaf.inkpad.core.format

import pm.asaf.inkpad.core.model.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorCodecTest {
    @Test
    fun parsesHexKeepingAlpha() {
        assertEquals(Color(0x3333ccffu), ColorCodec.parse("#3333ccff", ColorCodec.Usage.ELEMENT))
        assertEquals(Color(0xf8e45c7fu), ColorCodec.parse("#f8e45c7f", ColorCodec.Usage.ELEMENT))
        assertEquals(Color(0xff00ff7fu), ColorCodec.parse("#FF00FF7F", ColorCodec.Usage.ELEMENT))
    }

    @Test
    fun formatsLowercaseRrggbbaa() {
        assertEquals("#ff00ff7f", ColorCodec.format(Color(0xff00ff7fu)))
        assertEquals("#000000ff", ColorCodec.format(Color.BLACK))
    }

    @Test
    fun parsesXournalElementColorNames() {
        val expected =
            mapOf(
                "black" to 0x000000ffu,
                "blue" to 0x3333ccffu,
                "red" to 0xff0000ffu,
                "green" to 0x008000ffu,
                "gray" to 0x808080ffu,
                "lightblue" to 0x00c0ffffu,
                "lightgreen" to 0x00ff00ffu,
                "magenta" to 0xff00ffffu,
                "orange" to 0xff8000ffu,
                "yellow" to 0xffff00ffu,
                "white" to 0xffffffffu,
            )
        for ((name, rgba) in expected) {
            assertEquals(Color(rgba), ColorCodec.parse(name, ColorCodec.Usage.ELEMENT), name)
        }
    }

    @Test
    fun backgroundNamesDifferFromElementNames() {
        val expected =
            mapOf(
                "blue" to 0xa0e8ffffu,
                "pink" to 0xffc0d4ffu,
                "green" to 0x80ffc0ffu,
                "orange" to 0xffc080ffu,
                "yellow" to 0xffff80ffu,
                // Not a background name: falls back to the element names.
                "white" to 0xffffffffu,
                "black" to 0x000000ffu,
            )
        for ((name, rgba) in expected) {
            assertEquals(Color(rgba), ColorCodec.parse(name, ColorCodec.Usage.BACKGROUND), name)
        }
    }

    @Test
    fun rejectsUnknownColors() {
        for (text in listOf("", "#", "#12345", "#1234567", "#123456789", "#1234567g", "purple", "pink")) {
            assertNull(ColorCodec.parse(text, ColorCodec.Usage.ELEMENT), "should reject '$text'")
        }
    }
}
