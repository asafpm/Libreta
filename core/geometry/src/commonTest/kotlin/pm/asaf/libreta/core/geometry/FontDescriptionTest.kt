// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.geometry.FontFamily.DEJAVU_SANS
import pm.asaf.libreta.core.geometry.FontFamily.DEJAVU_SANS_MONO
import pm.asaf.libreta.core.geometry.FontFamily.DEJAVU_SERIF
import pm.asaf.libreta.core.geometry.FontFamily.LIBERATION_MONO
import pm.asaf.libreta.core.geometry.FontFamily.LIBERATION_SANS
import pm.asaf.libreta.core.geometry.FontFamily.LIBERATION_SERIF
import kotlin.test.Test
import kotlin.test.assertEquals

class FontDescriptionTest {
    private fun plain(family: FontFamily) = FontDescription(family, bold = false, italic = false)

    @Test
    fun namesFromSampleFiles() {
        val expected =
            mapOf(
                "Sans" to plain(DEJAVU_SANS),
                "Liberation Serif Bold" to FontDescription(LIBERATION_SERIF, bold = true, italic = false),
                "Liberation Sans" to plain(LIBERATION_SANS),
                "DejaVu Serif Italic" to FontDescription(DEJAVU_SERIF, bold = false, italic = true),
                "Nimbus Sans" to plain(LIBERATION_SANS),
                // Pango reads "Roman" as a style: the unknown family "Nimbus" gets the default font,
                // as measured in Xournal++ 1.3.8 (same for "Times New Roman").
                "Nimbus Roman" to plain(DEJAVU_SANS),
                "Nimbus Roman No9 L" to plain(LIBERATION_SERIF),
                "Times" to plain(LIBERATION_SERIF),
                "Roman" to plain(DEJAVU_SERIF),
                "DejaVu Sans Mono Bold Oblique" to FontDescription(DEJAVU_SANS_MONO, bold = true, italic = true),
                "Courier New" to plain(LIBERATION_MONO),
                "Times New Roman Semibold" to FontDescription(DEJAVU_SANS, bold = true, italic = false),
                "Arial Italic" to FontDescription(LIBERATION_SANS, bold = false, italic = true),
                "Helvetica" to plain(LIBERATION_SANS),
                "Sans Bold 12" to FontDescription(DEJAVU_SANS, bold = true, italic = false),
                "Serif Light" to plain(DEJAVU_SERIF),
                "Monospace" to plain(DEJAVU_SANS_MONO),
                "C059" to plain(DEJAVU_SERIF), // xournalpp/development/README-Eraser…
                "P052 Bold Italic" to FontDescription(DEJAVU_SERIF, bold = true, italic = true),
                "Nimbus Mono PS" to plain(LIBERATION_MONO),
                "Noto Sans" to plain(DEJAVU_SANS),
                "" to plain(DEJAVU_SANS),
            )
        for ((name, description) in expected) assertEquals(description, FontDescription.parse(name), name)
    }

    @Test
    fun styleWordsCountOnlyAtTheEnd() {
        // "Black" as part of a family name in the middle does not make the font bold.
        assertEquals(plain(DEJAVU_SANS), FontDescription.parse("Black Ops Sans"))
    }
}
