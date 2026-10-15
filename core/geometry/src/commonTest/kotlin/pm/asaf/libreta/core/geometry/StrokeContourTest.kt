// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.PointList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeContourTest {
    private fun piece(
        coordinates: DoubleArray,
        vararg widths: Double,
    ) = StrokePiece(PointList.fromInterleaved(coordinates), DoubleList.of(*widths))

    private fun ShapePath.assertInside(
        x: Double,
        y: Double,
    ) = assertTrue(contains(x, y), "($x, $y) should be inside")

    private fun ShapePath.assertOutside(
        x: Double,
        y: Double,
    ) = assertFalse(contains(x, y), "($x, $y) should be outside")

    @Test
    fun singleDotIsACircle() {
        val outline = StrokeContour.outline(piece(doubleArrayOf(10.0, 10.0, 10.0, 10.0), 4.0))
        assertEquals(listOf<PathCommand>(PathCommand.Arc(10.0, 10.0, 2.0, 0.0, 2 * PI)), outline.commands)
        outline.assertInside(11.9, 10.0)
        outline.assertOutside(12.1, 10.0)
    }

    @Test
    fun twoPointStrokeIsACapsule() {
        // From (0, 0) to (20, 0), width 4: a 20 x 4 rectangle with half-discs of radius 2.
        val outline = StrokeContour.outline(piece(doubleArrayOf(0.0, 0.0, 20.0, 0.0), 4.0))
        for (x in listOf(-1.9, 0.0, 10.0, 20.0, 21.9)) outline.assertInside(x, 0.0)
        for (x in listOf(0.0, 10.0, 20.0)) {
            outline.assertInside(x, 1.9)
            outline.assertInside(x, -1.9)
            outline.assertOutside(x, 2.1)
            outline.assertOutside(x, -2.1)
        }
        outline.assertOutside(-2.1, 0.0)
        outline.assertOutside(22.1, 0.0)
        // Round ends: the corner of the bounding rectangle is outside.
        outline.assertOutside(-1.8, 1.8)
    }

    @Test
    fun segmentsHaveTheirOwnWidths() {
        // (0,0) -> (20,0) width 2, then (20,0) -> (40,0) width 8.
        val outline = StrokeContour.outline(piece(doubleArrayOf(0.0, 0.0, 20.0, 0.0, 40.0, 0.0), 2.0, 8.0))
        outline.assertInside(10.0, 0.9)
        outline.assertOutside(10.0, 1.5)
        outline.assertInside(30.0, 3.9)
        outline.assertOutside(30.0, 4.1)
        // The last end cap uses the last segment's width.
        outline.assertInside(43.9, 0.0)
        outline.assertOutside(44.1, 0.0)
    }

    @Test
    fun turnsAreCoveredWithoutGaps() {
        // A right angle: (0,0) -> (20,0) -> (20,20), width 4 everywhere.
        val outline = StrokeContour.outline(piece(doubleArrayOf(0.0, 0.0, 20.0, 0.0, 20.0, 20.0), 4.0, 4.0))
        // Points around the corner within the half width are inside (round join).
        for (step in 0..16) {
            val angle = step * 2 * PI / 16
            outline.assertInside(20.0 + 1.9 * cos(angle), 1.9 * sin(angle))
        }
        outline.assertInside(10.0, 0.0)
        outline.assertInside(20.0, 10.0)
        outline.assertOutside(10.0, 10.0)
        outline.assertOutside(22.0 + 1.0, -2.0 - 1.0)
    }

    @Test
    fun zigzagWithVaryingWidthsCoversItsCentreLine() {
        val coordinates = doubleArrayOf(0.0, 0.0, 10.0, 6.0, 12.0, 0.0, 30.0, 4.0, 31.0, 30.0)
        val outline = StrokeContour.outline(piece(coordinates, 1.0, 3.0, 0.5, 2.0))
        for (segment in 0 until 4) {
            for (step in 0..10) {
                val t = step / 10.0
                val x = coordinates[2 * segment] + t * (coordinates[2 * segment + 2] - coordinates[2 * segment])
                val y = coordinates[2 * segment + 1] + t * (coordinates[2 * segment + 3] - coordinates[2 * segment + 1])
                outline.assertInside(x, y)
            }
        }
    }

    @Test
    fun dashesLeaveGaps() {
        // Straight line of length 30, width 1, dash 6 on / 3 off: dashes at [0,6], [9,15], [18,24], [27,30].
        val outline = StrokeContour.outline(piece(doubleArrayOf(0.0, 0.0, 30.0, 0.0), 1.0), doubleArrayOf(6.0, 3.0))
        for (x in listOf(3.0, 12.0, 21.0, 28.0)) outline.assertInside(x, 0.0)
        // Gaps (minus the round ends of radius 0.5).
        for (x in listOf(7.5, 16.5, 25.5)) outline.assertOutside(x, 0.0)
        assertEquals(4, outline.toPolygons().size)
    }

    @Test
    fun dashesContinueAcrossSegments() {
        // Two segments of length 5 each; the first dash (length 6) crosses the corner.
        val outline =
            StrokeContour.outline(
                piece(doubleArrayOf(0.0, 0.0, 5.0, 0.0, 10.0, 0.0), 1.0, 1.0),
                doubleArrayOf(6.0, 3.0),
            )
        outline.assertInside(5.5, 0.0)
        outline.assertOutside(7.5, 0.0)
        outline.assertInside(9.5, 0.0)
    }
}
