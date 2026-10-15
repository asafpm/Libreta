// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import okio.ByteString
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.DoubleList
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.ForeignElement
import pm.asaf.inkpad.core.model.PointList
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionTest {
    private val measurer = TextMeasurer { Size(it.text.length * 6.0, it.size) }
    private val black = Color.of(0, 0, 0)

    private fun stroke(
        vararg points: Double,
        width: Double = 2.0,
    ) = Element.Stroke(StrokeTool.PEN, black, width, PointList.of(*points))

    private val line = stroke(10.0, 10.0, 30.0, 10.0)
    private val text = Element.Text("hello", "Sans", 10.0, 50.0, 50.0, black)
    private val image = Element.Image(Bounds(100.0, 100.0, 140.0, 120.0), ByteString.EMPTY)

    @Test
    fun rectanglesEncloseWholeElements() {
        val rect = SelectionShape.Rect(40.0, 0.0, 0.0, 40.0)
        // Every point inside, the stroke's width aside.
        assertTrue(Selecting.encloses(rect, line, measurer))
        assertFalse(Selecting.encloses(rect, stroke(10.0, 10.0, 50.0, 10.0), measurer))
        // Text and images: all four corners of the box.
        assertTrue(Selecting.encloses(SelectionShape.Rect(50.0, 50.0, 80.0, 60.0), text, measurer))
        assertFalse(Selecting.encloses(SelectionShape.Rect(50.0, 50.0, 79.0, 60.0), text, measurer))
        assertTrue(Selecting.encloses(SelectionShape.Rect(90.0, 90.0, 150.0, 130.0), image, measurer))
        assertFalse(
            Selecting.encloses(
                SelectionShape.Rect(0.0, 0.0, 1000.0, 1000.0),
                Element.Unknown(ForeignElement("x")),
                measurer,
            ),
        )
        assertEquals(
            listOf(0, 2),
            Selecting.enclosed(
                SelectionShape.Rect(0.0, 0.0, 200.0, 200.0),
                listOf(line, Element.Unknown(ForeignElement("x")), image),
                measurer,
            ),
        )
    }

    @Test
    fun lassosAreEvenOddPolygons() {
        // A U shape: the gap between its arms is outside.
        val u =
            SelectionShape.Lasso(
                doubleArrayOf(
                    0.0,
                    0.0,
                    30.0,
                    0.0,
                    30.0,
                    30.0,
                    20.0,
                    30.0,
                    20.0,
                    10.0,
                    10.0,
                    10.0,
                    10.0,
                    30.0,
                    0.0,
                    30.0,
                ),
            )
        assertTrue(u.contains(5.0, 20.0))
        assertTrue(u.contains(25.0, 20.0))
        assertFalse(u.contains(15.0, 20.0))
        assertTrue(Selecting.encloses(u, stroke(2.0, 5.0, 28.0, 5.0), measurer))
        // Like Xournal++, only the points count: a line across the gap with its ends inside is enclosed.
        assertTrue(Selecting.encloses(u, stroke(5.0, 20.0, 25.0, 20.0), measurer))
        assertFalse(Selecting.encloses(u, stroke(5.0, 20.0, 15.0, 20.0, 25.0, 20.0), measurer))
        assertFalse(SelectionShape.Lasso(doubleArrayOf(0.0, 0.0, 10.0, 10.0)).contains(5.0, 5.0))
    }

    @Test
    fun picksWhatIsUnderTheFingerOrNearest() {
        val elements = listOf(line, stroke(10.0, 10.0, 10.0, 30.0), image)
        // On both strokes: the front one.
        assertEquals(1, Selecting.picked(elements, 10.0, 10.0, 5.0, measurer))
        // On the first only, within half its width.
        assertEquals(0, Selecting.picked(elements, 20.0, 10.9, 5.0, measurer))
        // Near the first, 4 pt away (less the half width).
        assertEquals(0, Selecting.picked(elements, 20.0, 15.0, 5.0, measurer))
        assertNull(Selecting.picked(elements, 20.0, 18.0, 5.0, measurer))
        assertEquals(2, Selecting.picked(elements, 120.0, 110.0, 5.0, measurer))
        assertEquals(2, Selecting.picked(elements, 143.0, 110.0, 5.0, measurer))
    }

    @Test
    fun transformsStrokesWithTheirWidths() {
        val pressure = line.copy(segmentWidths = DoubleList.of(1.0))
        // Moved by (5, 0), then doubled about (0, 0).
        val moved = SelectionTransform(dx = 5.0, fx = 2.0, fy = 2.0).apply(pressure) as Element.Stroke
        assertEquals(listOf(30.0, 20.0, 70.0, 20.0), moved.points.toInterleavedArray().toList())
        assertEquals(4.0, moved.width)
        assertEquals(listOf(2.0), moved.segmentWidths?.toDoubleArray()?.toList())
        // Stretched along one side: widths by √(fx·fy).
        val stretched = SelectionTransform(fx = 4.0, fy = 1.0).apply(line) as Element.Stroke
        assertEquals(4.0, stretched.width)
        // A quarter turn about (20, 10).
        val turned = SelectionTransform(angle = PI / 2, centerX = 20.0, centerY = 10.0).apply(line) as Element.Stroke
        assertEquals(listOf(20.0, 0.0, 20.0, 20.0), turned.points.toInterleavedArray().toList())
    }

    @Test
    fun transformsTextAndImagesByTheirRules() {
        val scaled = SelectionTransform(dx = 10.0, fx = 2.0, fy = 2.0, originX = 60.0, originY = 50.0).apply(text)
        assertEquals(text.copy(x = 60.0, y = 50.0, size = 20.0), scaled)
        val picture = SelectionTransform(fx = 2.0, fy = 0.5, originX = 100.0, originY = 100.0).apply(image)
        assertEquals(image.copy(bounds = Bounds(100.0, 100.0, 180.0, 110.0)), picture)
        // Only strokes rotate and mirror; text can't stretch.
        assertTrue(Selecting.canRotate(listOf(line)))
        assertFalse(Selecting.canRotate(listOf(line, image)))
        assertFalse(Selecting.canMirror(listOf(text)))
        assertFalse(Selecting.canStretch(listOf(line, text)))
        assertEquals(Bounds(9.0, 9.0, 140.0, 120.0), Selecting.bounds(listOf(line, image), measurer))
    }

    @Test
    fun snapsRotationsTo15Degrees() {
        val degree = PI / 180
        assertEquals(15 * degree, Selecting.snappedAngle(17 * degree), 1e-12)
        assertEquals(19 * degree, Selecting.snappedAngle(19 * degree), 1e-12)
        assertEquals(-90 * degree, Selecting.snappedAngle(-88 * degree), 1e-12)
    }
}
