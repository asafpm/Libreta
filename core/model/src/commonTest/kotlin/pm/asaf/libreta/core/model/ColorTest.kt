// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorTest {
    @Test
    fun componentsComeFromRrggbbaa() {
        val color = Color(0x3333cc7fu)
        assertEquals(0x33, color.red)
        assertEquals(0x33, color.green)
        assertEquals(0xcc, color.blue)
        assertEquals(0x7f, color.alpha)
    }

    @Test
    fun ofPacksComponents() {
        assertEquals(Color(0xff80007fu), Color.of(red = 0xff, green = 0x80, blue = 0x00, alpha = 0x7f))
        assertEquals(Color(0x008000ffu), Color.of(red = 0x00, green = 0x80, blue = 0x00))
    }

    @Test
    fun ofRejectsOutOfRangeComponents() {
        assertFailsWith<IllegalArgumentException> { Color.of(red = 256, green = 0, blue = 0) }
    }

    @Test
    fun toStringIsHex() {
        assertEquals("Color(#000000ff)", Color.BLACK.toString())
    }
}
