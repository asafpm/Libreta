// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.geometry.FontFamily.MONO
import pm.asaf.inkpad.core.geometry.FontFamily.SANS
import pm.asaf.inkpad.core.geometry.FontFamily.SERIF
import kotlin.test.Test
import kotlin.test.assertEquals

class FontDescriptionTest {
    @Test
    fun namesFromSampleFiles() {
        val expected =
            mapOf(
                "Sans" to FontDescription(SANS, bold = false, italic = false),
                "Liberation Serif Bold" to FontDescription(SERIF, bold = true, italic = false),
                "Liberation Sans" to FontDescription(SANS, bold = false, italic = false),
                "DejaVu Serif Italic" to FontDescription(SERIF, bold = false, italic = true),
                "Nimbus Sans" to FontDescription(SANS, bold = false, italic = false),
                "Nimbus Roman" to FontDescription(SERIF, bold = false, italic = false),
                "DejaVu Sans Mono Bold Oblique" to FontDescription(MONO, bold = true, italic = true),
                "Courier New" to FontDescription(MONO, bold = false, italic = false),
                "Times New Roman Semibold" to FontDescription(SERIF, bold = true, italic = false),
                "Sans Bold 12" to FontDescription(SANS, bold = true, italic = false),
                "Serif Light" to FontDescription(SERIF, bold = false, italic = false),
                "" to FontDescription(SANS, bold = false, italic = false),
            )
        for ((name, description) in expected) assertEquals(description, FontDescription.parse(name), name)
    }

    @Test
    fun styleWordsCountOnlyAtTheEnd() {
        // "Black" as part of a family name in the middle does not make the font bold.
        assertEquals(FontDescription(SANS, bold = false, italic = false), FontDescription.parse("Black Ops Sans"))
    }
}
