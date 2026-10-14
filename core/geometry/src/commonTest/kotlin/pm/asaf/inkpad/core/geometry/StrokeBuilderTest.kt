// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.StrokeTool
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeBuilderTest {
    private val blue = Color(0x3333ccffu)

    private fun builder(width: Double = 2.0) = StrokeBuilder(StrokeTool.PEN, blue, width)

    private fun assertClose(
        expected: List<Double>,
        actual: List<Double>,
    ) {
        assertEquals(expected.size, actual.size, "$expected vs $actual")
        expected.zip(actual).forEach { (e, a) -> assertTrue(abs(e - a) < 1e-9, "$expected vs $actual") }
    }

    private fun StrokeBuilder.coordinates() = (0 until pointCount).flatMap { listOf(x(it), y(it)) }

    @Test
    fun withoutPressureTheWidthIsConstant() {
        val stroke =
            builder().run {
                add(InkSample(10.0, 10.0, null))
                add(InkSample(10.1, 10.1, null)) // closer than 0.3 pt: dropped
                add(InkSample(11.0, 10.0, null))
                add(InkSample(12.0, 10.0, null))
                finish(null)
            }
        assertEquals(listOf(10.0, 10.0, 11.0, 10.0, 12.0, 10.0), stroke.points.toInterleavedArray().toList())
        assertNull(stroke.segmentWidths)
        assertEquals(2.0, stroke.width)
        assertEquals(blue, stroke.color)
        assertEquals(StrokeTool.PEN, stroke.tool)
    }

    @Test
    fun pressureScalesTheWidthOfEachSegment() {
        val builder = builder(width = 1.0)
        for ((x, pressure) in listOf(0.0 to 0.5, 1.0 to 0.6, 2.0 to 0.4, 3.0 to 0.2, 4.0 to 0.01)) {
            builder.add(InkSample(x, 0.0, pressure))
        }
        // Below the minimum pressure 0.05.
        assertClose(listOf(0.05), listOf(builder.width(4)))
        val stroke = builder.finish(0.01)
        assertEquals(5, stroke.points.size)
        // One width per point but the last: pressure x width.
        assertClose(listOf(0.5, 0.6, 0.4, 0.2), widths(stroke))
    }

    private fun widths(stroke: Element.Stroke) = checkNotNull(stroke.segmentWidths).toDoubleArray().toList()

    @Test
    fun aLargeWidthChangeIsSpreadOverShortSegments() {
        val builder = builder(width = 1.0)
        builder.add(InkSample(0.0, 0.0, 0.25))
        builder.add(InkSample(2.0, 0.0, 1.0))
        // Width 0.25 -> 1.0 needs ceil(0.75 / 0.3) = 3 steps; the 2 pt segment allows them
        // (>= 0.3 pt each). Like Xournal++, the interpolated widths run one step ahead.
        assertClose(listOf(0.0, 0.0, 2.0 / 3, 0.0, 4.0 / 3, 0.0, 2.0, 0.0), builder.coordinates())
        assertClose(listOf(0.25, 0.75, 1.0, 1.0), (0 until builder.pointCount).map { builder.width(it) })
    }

    @Test
    fun aShortSegmentLimitsTheSteps() {
        val builder = builder(width = 1.0)
        builder.add(InkSample(0.0, 0.0, 0.25))
        builder.add(InkSample(0.5, 0.0, 1.0))
        // 3 steps wanted, but only floor(0.5 / 0.3) = 1 fits: no intermediate point.
        assertEquals(2, builder.pointCount)
    }

    @Test
    fun theFirstPointTakesTheHighestEarlyPressure() {
        val builder = builder(width = 2.0)
        assertEquals(0, builder.add(InkSample(5.0, 5.0, 0.2)))
        // Pen pressure builds up before the pen moves: the first point gets thicker.
        assertEquals(0, builder.add(InkSample(5.1, 5.0, 0.4)))
        assertClose(listOf(0.8), listOf(builder.width(0)))
        // Not thinner again.
        assertEquals(1, builder.add(InkSample(5.2, 5.0, 0.3)))
        assertClose(listOf(0.8), listOf(builder.width(0)))
    }

    @Test
    fun aDotHasTwoPoints() {
        val stroke =
            builder(width = 2.0).run {
                add(InkSample(7.0, 8.0, 0.3))
                finish(0.5)
            }
        assertEquals(listOf(7.0, 8.0, 7.0, 8.0), stroke.points.toInterleavedArray().toList())
        // The release pressure, if higher, sets the width.
        assertClose(listOf(1.0), widths(stroke))
    }

    @Test
    fun showsTheStrokeSoFar() {
        val builder = builder(width = 2.0)
        builder.add(InkSample(7.0, 8.0, 0.5))
        // A dot until it moves.
        assertEquals(
            listOf(7.0, 8.0, 7.0, 8.0),
            builder
                .current()
                .points
                .toInterleavedArray()
                .toList(),
        )
        builder.add(InkSample(17.0, 8.0, 0.5))
        val current = builder.current()
        assertEquals(2, builder.pointCount)
        assertEquals(current, builder.finish(0.5))
    }

    @Test
    fun movesWithoutPressureAreIgnored() {
        val builder = builder()
        builder.add(InkSample(0.0, 0.0, 0.5))
        builder.add(InkSample(1.0, 0.0, 0.5))
        // Some styluses report pressure 0 as they lift.
        assertEquals(builder.pointCount, builder.add(InkSample(2.0, 0.0, 0.0)))
        assertEquals(2, builder.pointCount)
    }

    @Test
    fun reportsWhatToRedraw() {
        val builder = builder()
        assertEquals(0, builder.add(InkSample(0.0, 0.0, null)))
        assertEquals(1, builder.add(InkSample(1.0, 0.0, null)))
        assertEquals(2, builder.add(InkSample(1.1, 0.0, null))) // nothing new
        assertEquals(2, builder.add(InkSample(2.0, 0.0, null)))
        assertEquals(Bounds(0.0, 0.0, 2.0, 0.0), builder.pointBounds())
    }

    @Test
    fun builtStrokesKeepTheirWidthsThroughPressureRepair() {
        val stroke =
            builder().run {
                add(InkSample(0.0, 0.0, 0.5))
                add(InkSample(1.0, 1.0, 0.7))
                add(InkSample(2.0, 1.0, 0.6))
                finish(0.6)
            }
        val pieces = PressureRepair.pieces(stroke)
        assertEquals(1, pieces.size)
        assertEquals(stroke.points, pieces.single().points)
    }
}
