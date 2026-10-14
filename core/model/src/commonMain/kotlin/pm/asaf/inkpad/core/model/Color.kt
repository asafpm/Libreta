// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import kotlin.jvm.JvmInline

/**
 * An sRGB colour with alpha, packed as `0xRRGGBBAA` (the byte order of the `#rrggbbaa`
 * strings in .xopp files).
 */
@JvmInline
value class Color(
    val rgba: UInt,
) {
    val red: Int get() = component(RED_SHIFT)
    val green: Int get() = component(GREEN_SHIFT)
    val blue: Int get() = component(BLUE_SHIFT)
    val alpha: Int get() = component(ALPHA_SHIFT)

    private fun component(shift: Int): Int = (rgba shr shift).toInt() and COMPONENT_MAX

    override fun toString(): String = "Color(#" + rgba.toString(radix = 16).padStart(length = 8, padChar = '0') + ")"

    companion object {
        private const val COMPONENT_MAX = 0xff
        private const val RED_SHIFT = 24
        private const val GREEN_SHIFT = 16
        private const val BLUE_SHIFT = 8
        private const val ALPHA_SHIFT = 0

        fun of(
            red: Int,
            green: Int,
            blue: Int,
            alpha: Int = COMPONENT_MAX,
        ): Color {
            val components = intArrayOf(red, green, blue, alpha)
            require(components.all { it in 0..COMPONENT_MAX }) {
                "colour components must be in 0..255: ${components.joinToString()}"
            }
            val packed =
                (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or (alpha shl ALPHA_SHIFT)
            return Color(packed.toUInt())
        }

        val BLACK: Color = Color(0x000000ffu)
        val WHITE: Color = Color(0xffffffffu)
    }
}
