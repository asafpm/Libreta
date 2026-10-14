// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.DoubleList
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeGeometryTest {
    private fun stroke(
        tool: StrokeTool = StrokeTool.PEN,
        width: Double = 2.0,
        points: PointList = PointList.of(10.0, 20.0, 30.0, 5.0),
        segmentWidths: DoubleList? = null,
    ) = Element.Stroke(tool = tool, color = Color.BLACK, width = width, points = points, segmentWidths = segmentWidths)

    @Test
    fun constantWidthStrokeIsPaddedByHalfItsWidth() {
        assertEquals(Bounds(9.0, 4.0, 31.0, 21.0), StrokeGeometry.bounds(stroke()))
    }

    @Test
    fun pressureStrokeIsPaddedByHalfItsLargestWidth() {
        val points = PointList.of(0.0, 0.0, 10.0, 0.0, 20.0, 0.0)
        assertEquals(
            Bounds(-3.0, -3.0, 23.0, 3.0),
            StrokeGeometry.bounds(stroke(points = points, segmentWidths = DoubleList.of(1.0, 6.0))),
        )
    }

    @Test
    fun highlightersIgnorePressure() {
        val points = PointList.of(0.0, 0.0, 10.0, 0.0)
        val highlighter =
            stroke(tool = StrokeTool.HIGHLIGHTER, width = 8.0, points = points, segmentWidths = DoubleList.of(1.0))
        assertEquals(Bounds(-4.0, -4.0, 14.0, 4.0), StrokeGeometry.bounds(highlighter))
        assertFalse(StrokeGeometry.usesPressure(highlighter, PressureRepair.pieces(highlighter).single()))
    }

    @Test
    fun negativeCoordinatesWork() {
        // Xournal++ starts its maxima at the smallest positive double and gets this wrong.
        val points = PointList.of(-30.0, -20.0, -10.0, -5.0)
        assertEquals(Bounds(-31.0, -21.0, -9.0, -4.0), StrokeGeometry.bounds(stroke(points = points)))
    }

    @Test
    fun strokeWithOnlyInvalidPressuresHasNoBounds() {
        assertNull(StrokeGeometry.bounds(stroke(segmentWidths = DoubleList.of(-1.0))))
    }

    @Test
    fun elementBounds() {
        val image = Element.Image(Bounds(1.0, 2.0, 3.0, 4.0), okio.ByteString.EMPTY)
        assertEquals(Bounds(1.0, 2.0, 3.0, 4.0), ElementGeometry.bounds(image) { error("not a text") })
        val text = Element.Text("hi", "Sans", 12.0, x = 5.0, y = 6.0, color = Color.BLACK)
        assertEquals(Bounds(5.0, 6.0, 25.0, 20.0), ElementGeometry.bounds(text) { Size(20.0, 14.0) })
    }

    @Test
    fun overlapMustBeStrictlyPositive() {
        val a = Bounds(0.0, 0.0, 10.0, 10.0)
        assertTrue(a.intersects(Bounds(9.0, 9.0, 20.0, 20.0)))
        assertFalse(a.intersects(Bounds(10.0, 0.0, 20.0, 10.0)))
        assertEquals(Bounds(0.0, -5.0, 20.0, 10.0), a.union(Bounds(15.0, -5.0, 20.0, 1.0)))
    }
}
