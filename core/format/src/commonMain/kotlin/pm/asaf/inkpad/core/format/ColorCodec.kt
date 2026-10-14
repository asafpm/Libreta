// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/control/xojfile/LoadHandlerHelper.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.core.format

import pm.asaf.inkpad.core.model.Color

/** Colours in .xopp files (docs/xopp-format.md §6): `#rrggbbaa` or a legacy Xournal name. */
internal object ColorCodec {
    /** Colour names mean different colours on backgrounds and on strokes/texts. */
    enum class Usage { ELEMENT, BACKGROUND }

    private const val HEX_DIGITS = 8

    private val elementNames: Map<String, Color> =
        mapOf(
            "black" to Color(0x000000ffu),
            "blue" to Color(0x3333ccffu),
            "red" to Color(0xff0000ffu),
            "green" to Color(0x008000ffu),
            "gray" to Color(0x808080ffu),
            "lightblue" to Color(0x00c0ffffu),
            "lightgreen" to Color(0x00ff00ffu),
            "magenta" to Color(0xff00ffffu),
            "orange" to Color(0xff8000ffu),
            "yellow" to Color(0xffff00ffu),
            "white" to Color(0xffffffffu),
        )

    private val backgroundNames: Map<String, Color> =
        mapOf(
            "blue" to Color(0xa0e8ffffu),
            "pink" to Color(0xffc0d4ffu),
            "green" to Color(0x80ffc0ffu),
            "orange" to Color(0xffc080ffu),
            "yellow" to Color(0xffff80ffu),
        )

    fun parse(
        text: String,
        usage: Usage,
    ): Color? =
        when {
            text.startsWith('#') -> parseHex(text.substring(1))
            usage == Usage.BACKGROUND -> backgroundNames[text] ?: elementNames[text]
            else -> elementNames[text]
        }

    fun format(color: Color): String = "#" + color.rgba.toString(radix = 16).padStart(HEX_DIGITS, '0')

    private fun parseHex(hex: String): Color? =
        if (hex.length == HEX_DIGITS && hex.all { it.isHexDigit() }) Color(hex.toUInt(radix = 16)) else null

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
