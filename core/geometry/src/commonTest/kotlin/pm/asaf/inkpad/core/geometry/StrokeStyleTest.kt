// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.LineStyle
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrokeStyleTest {
    @Test
    fun namedDashPatterns() {
        assertContentEquals(doubleArrayOf(6.0, 3.0), DashPattern.of(LineStyle.DASH))
        assertContentEquals(doubleArrayOf(6.0, 3.0, 0.5, 3.0), DashPattern.of(LineStyle.DASH_DOT))
        assertContentEquals(doubleArrayOf(0.5, 3.0), DashPattern.of(LineStyle.DOT))
    }

    @Test
    fun customDashPatterns() {
        assertContentEquals(doubleArrayOf(1.0, 2.5, 3.0), DashPattern.of(LineStyle("cust: 1.00 2.50 3.00")))
        // Like a C++ stream: reading stops at the first non-number.
        assertContentEquals(doubleArrayOf(1.0), DashPattern.of(LineStyle("cust: 1 x 2")))
    }

    @Test
    fun anythingElseIsSolid() {
        for (style in listOf(null, LineStyle("plain"), LineStyle("cust: "), LineStyle("cust:1 2"), LineStyle("wavy"))) {
            assertNull(DashPattern.of(style), style.toString())
        }
    }

    private fun stroke(
        tool: StrokeTool,
        fill: Int? = null,
    ) = Element.Stroke(
        tool = tool,
        color = Color.BLACK,
        width = 1.0,
        points = PointList.of(0.0, 0.0, 1.0, 1.0),
        fill = fill,
    )

    @Test
    fun appearanceFollowsXournalpp() {
        assertEquals(
            StrokeAppearance(Blend.NORMAL, 1.0, null, combineFill = false),
            StrokeAppearance.of(stroke(StrokeTool.PEN)),
        )
        assertEquals(
            StrokeAppearance(Blend.NORMAL, 1.0, null, combineFill = false),
            StrokeAppearance.of(stroke(StrokeTool.ERASER)),
        )
        assertEquals(
            StrokeAppearance(Blend.NORMAL, 1.0, 128 / 255.0, combineFill = false),
            StrokeAppearance.of(stroke(StrokeTool.PEN, fill = 128)),
        )
        assertEquals(
            StrokeAppearance(Blend.MULTIPLY, 0.47, null, combineFill = false),
            StrokeAppearance.of(stroke(StrokeTool.HIGHLIGHTER)),
        )
        // A filled highlighter is one shape, fill and outline, at fill/255.
        assertEquals(
            StrokeAppearance(Blend.MULTIPLY, 64 / 255.0, 64 / 255.0, combineFill = true),
            StrokeAppearance.of(stroke(StrokeTool.HIGHLIGHTER, fill = 64)),
        )
    }
}
