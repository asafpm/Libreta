// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Layer
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapesTest {
    private fun page(
        style: PageStyle = PageStyle.PLAIN,
        config: String? = null,
    ) = Page(595.0, 842.0, Background.Solid(Color.WHITE, style, config = config), listOf(Layer(emptyList())))

    private val plain = ShapeGrid.of(page())

    private fun shape(
        type: DrawingType,
        start: ShapePoint,
        end: ShapePoint,
        width: Double = 1.41,
        square: Boolean = false,
        fromCenter: Boolean = false,
        snap: Boolean = false,
    ) = Shapes.points(type, start, end, width, plain, ShapeModifiers(square, fromCenter, snap))

    private fun assertNear(
        expected: ShapePoint,
        actual: ShapePoint,
        tolerance: Double = 1e-9,
    ) = assertTrue(
        abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
        "expected $expected, got $actual",
    )

    @Test
    fun aLineGoesFromTheStartToThePen() {
        val points = shape(DrawingType.LINE, ShapePoint(10.0, 20.0), ShapePoint(110.0, 70.0))
        assertEquals(listOf(ShapePoint(10.0, 20.0), ShapePoint(110.0, 70.0)), points)
    }

    @Test
    fun linesSnapTo15DegreeStepsAndTheGrid() {
        // 1° off the horizontal: snapped flat, then to the nearest grid point if close to one.
        val start = ShapePoint(0.0, 0.0)
        val end = ShapePoint(100 * cos(PI / 180), 100 * sin(PI / 180))
        val snapped = shape(DrawingType.LINE, start, end, snap = true).last()
        // Flat at (100, 0), then onto the grid point 0.8 away.
        assertNear(ShapePoint(7 * 14.17, 0.0), snapped)
        // 3° off is further than 2.25°: not snapped (the end is not near a grid point either).
        val off = ShapePoint(100 * cos(3 * PI / 180), 100 * sin(3 * PI / 180))
        val kept = shape(DrawingType.LINE, start, off, snap = true).last()
        assertEquals(3.0, atan2(kept.y, kept.x) * 180 / PI, 1e-6)
    }

    @Test
    fun rectanglesAreClosedSquaresWithShiftAndCenteredWithCtrl() {
        val start = ShapePoint(10.0, 10.0)
        assertEquals(
            listOf(
                ShapePoint(10.0, 10.0),
                ShapePoint(10.0, 40.0),
                ShapePoint(70.0, 40.0),
                ShapePoint(70.0, 10.0),
                ShapePoint(10.0, 10.0),
            ),
            shape(DrawingType.RECTANGLE, start, ShapePoint(70.0, 40.0)),
        )
        // The longer side wins, keeping the direction of each.
        val square = shape(DrawingType.RECTANGLE, start, ShapePoint(40.0, -10.0), square = true)
        assertEquals(ShapePoint(40.0, -20.0), square[2])
        val centered = shape(DrawingType.RECTANGLE, start, ShapePoint(30.0, 20.0), fromCenter = true)
        assertEquals(ShapePoint(-10.0, 0.0), centered[0])
        assertEquals(ShapePoint(30.0, 20.0), centered[2])
    }

    @Test
    fun ellipsesFillTheirBoxWithXournalsNumberOfPoints() {
        val points = shape(DrawingType.ELLIPSE, ShapePoint(0.0, 0.0), ShapePoint(100.0, 60.0))
        val perQuadrant = ceil(5 + 0.3 * (50 + 30)).toInt()
        assertEquals(4 * perQuadrant + 1, points.size)
        assertEquals(points.first(), points.last())
        assertNear(ShapePoint(100.0, 30.0), points.first())
        assertNear(ShapePoint(50.0, 60.0), points[perQuadrant])
        assertNear(ShapePoint(0.0, 30.0), points[2 * perQuadrant])
        assertEquals(0.0, points.minOf { it.y }, 1e-9)
        // Every point on the ellipse.
        for (p in points) assertEquals(1.0, ((p.x - 50) / 50).let { it * it } + ((p.y - 30) / 30).let { it * it }, 1e-9)
        // A circle with Shift; centred on the start with Ctrl.
        val circle = shape(DrawingType.ELLIPSE, ShapePoint(0.0, 0.0), ShapePoint(100.0, 60.0), square = true)
        assertNear(ShapePoint(50.0, 100.0), circle[circle.size / 4])
        val centered = shape(DrawingType.ELLIPSE, ShapePoint(0.0, 0.0), ShapePoint(30.0, 20.0), fromCenter = true)
        assertNear(ShapePoint(30.0, 0.0), centered.first())
    }

    @Test
    fun arrowsHaveAHeadSevenTimesTheirWidthWhenLongEnough() {
        val width = 2.0
        val points = shape(DrawingType.ARROW, ShapePoint(0.0, 0.0), ShapePoint(100.0, 0.0), width)
        assertEquals(6, points.size)
        val tip = ShapePoint(100.0, 0.0)
        assertEquals(listOf(ShapePoint(0.0, 0.0), tip), listOf(points[0], points[1]))
        assertEquals(tip, points[3])
        assertEquals(tip, points[5])
        for (leg in listOf(points[2], points[4])) {
            assertEquals(7 * width, hypot(leg.x - tip.x, leg.y - tip.y), 1e-9)
            assertEquals(PI / 6, abs(atan2(leg.y, leg.x - tip.x).let { if (it < 0) it + 2 * PI else it } - PI), 1e-9)
        }
        val double = shape(DrawingType.DOUBLE_ARROW, ShapePoint(0.0, 0.0), ShapePoint(100.0, 0.0), width)
        assertEquals(10, double.size)
        assertEquals(7 * width, hypot(double[1].x, double[1].y), 1e-9)
    }

    @Test
    fun shortArrowsHaveSmallerHeads() {
        val width = 2.0
        // Regime 2: 0.4 of the length.
        val medium = shape(DrawingType.ARROW, ShapePoint(0.0, 0.0), ShapePoint(20.0, 0.0), width)
        assertEquals(8.0, hypot(medium[2].x - 20, medium[2].y), 1e-9)
        // Regime 4: very short, a wide head.
        val tiny = shape(DrawingType.ARROW, ShapePoint(0.0, 0.0), ShapePoint(2.0, 0.0), width)
        val leg = hypot(tiny[2].x - 2, tiny[2].y)
        assertEquals(2 * 0.8 * sin(PI / 6) / sin(PI / 3), leg, 1e-9)
    }

    @Test
    fun coordinateSystemsAreAnL() {
        val axes = shape(DrawingType.COORDINATE_SYSTEM, ShapePoint(10.0, 10.0), ShapePoint(50.0, 40.0))
        assertEquals(listOf(ShapePoint(10.0, 10.0), ShapePoint(10.0, 40.0), ShapePoint(50.0, 40.0)), axes)
        val swapped =
            shape(DrawingType.COORDINATE_SYSTEM, ShapePoint(10.0, 10.0), ShapePoint(50.0, 40.0), fromCenter = true)
        assertEquals(listOf(ShapePoint(10.0, 40.0), ShapePoint(10.0, 10.0), ShapePoint(50.0, 10.0)), swapped)
    }

    @Test
    fun theGridIsFiveMillimetresFromTheOriginOrBelowALinedPagesHeader() {
        val near = ShapePoint(2 * 14.17 + 1.0, 3 * 14.17 - 1.0)
        assertEquals(ShapePoint(2 * 14.17, 3 * 14.17), plain.snap(near))
        // Further than half the cell's half-diagonal: left alone.
        val far = ShapePoint(2 * 14.17 + 5.0, 3 * 14.17 + 5.0)
        assertEquals(far, plain.snap(far))
        val lined = ShapeGrid.of(page(PageStyle.LINED))
        assertEquals(ShapePoint(0.0, 80.0 + 14.17), lined.snap(ShapePoint(0.5, 80.0 + 14.17 + 0.5)))
    }

    @Test
    fun isometricPagesSnapToTheirTriangles() {
        val iso = ShapeGrid.of(page(PageStyle.ISO_GRAPH, "r1=20"))
        val xStep = kotlin.math.sqrt(3.0) / 2 * 20
        val vertex = iso.snap(ShapePoint(iso.xOffset + 3 * xStep + 0.5, iso.yOffset + 4 * 10.0 - 0.5))
        assertNear(ShapePoint(iso.xOffset + 3 * xStep, iso.yOffset + 40.0), vertex)
    }

    @Test
    fun snappingCanBeTurnedOff() {
        val start = ShapePoint(0.0, 0.0)
        val end = ShapePoint(100.0, 1.0)
        assertEquals(end, shape(DrawingType.LINE, start, end, snap = false).last())
    }
}
