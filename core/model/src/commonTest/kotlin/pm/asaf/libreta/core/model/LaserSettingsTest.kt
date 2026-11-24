// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LaserSettingsTest {
    @Test
    fun aRedThickPenThatFadesAfterASecondByDefault() {
        val laser = ToolSettings().laser
        assertEquals(LaserLook.PEN, laser.look)
        assertEquals(Color.of(237, 83, 83), laser.color)
        assertEquals(PenSize.THICK, laser.size)
        assertEquals(1000L, laser.fadeDelayMillis)
    }

    @Test
    fun itsWidthsAreThePensOrTheHighlightersByLook() {
        val pen = LaserSettings(look = LaserLook.PEN, size = PenSize.THICK)
        assertEquals(PenSize.THICK.width, pen.width)
        assertEquals(StrokeTool.PEN, pen.strokeTool)
        val highlighter = pen.copy(look = LaserLook.HIGHLIGHTER)
        assertEquals(HighlighterSettings(size = PenSize.THICK).width, highlighter.width)
        assertEquals(StrokeTool.HIGHLIGHTER, highlighter.strokeTool)
    }
}
