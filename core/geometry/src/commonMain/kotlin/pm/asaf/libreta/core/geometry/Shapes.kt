// SPDX-License-Identifier: GPL-2.0-or-later
// Port of Xournal++ v1.3.8 src/core/control/tools/RulerHandler.cpp, RectangleHandler.cpp,
// EllipseHandler.cpp, ArrowHandler.cpp and CoordinateSystemHandler.cpp (createShape), with
// snapping from src/core/model/Snapping.cpp and src/core/control/tools/SnapToGridInputHandler.cpp,
// © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Background
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign

/** A point on a page, in points. */
data class ShapePoint(
    val x: Double,
    val y: Double,
)

/**
 * How a shape is drawn: Xournal++'s Shift ([square]: squares, circles, equal axes), Ctrl
 * ([fromCenter]: from the centre; axes with the corner swapped) and snapping ([snap]; Alt turns
 * it off there).
 */
data class ShapeModifiers(
    val square: Boolean = false,
    val fromCenter: Boolean = false,
    val snap: Boolean = true,
)

/**
 * The grid shape points snap to, as Xournal++ lays it on a page: 5 mm squares from the page's
 * corner, below the header of ruled and lined pages, or the triangles of isometric ones.
 */
class ShapeGrid(
    private val columnSpacing: Double,
    private val rowSpacing: Double,
    val xOffset: Double,
    val yOffset: Double,
) {
    /** The grid point nearest [point] if it is within half the cell's half-diagonal; [point] otherwise. */
    fun snap(point: ShapePoint): ShapePoint {
        val vertex =
            ShapePoint(
                nearestMultiple(point.x - xOffset, columnSpacing) + xOffset,
                nearestMultiple(point.y - yOffset, rowSpacing) + yOffset,
            )
        val threshold = HALF * hypot(columnSpacing, rowSpacing) * TOLERANCE
        return if (hypot(vertex.x - point.x, vertex.y - point.y) < threshold) vertex else point
    }

    companion object {
        /** Xournal++'s default grid size, 5 mm. */
        const val SIZE = 14.17

        /** Below the header of ruled and lined pages. */
        private const val RULED_HEADER = 80.0

        /** Xournal++'s default grid tolerance. */
        private const val TOLERANCE = 0.5
        private const val HALF = 0.5

        /** The grid on [page]. */
        fun of(page: Page): ShapeGrid {
            val solid = page.background as? Background.Solid
            return when (solid?.style) {
                PageStyle.ISO_DOTTED, PageStyle.ISO_GRAPH -> {
                    isometric(
                        page,
                        BackgroundConfig(solid.config).double("r1") ?: SIZE,
                    )
                }

                PageStyle.RULED, PageStyle.LINED -> {
                    ShapeGrid(SIZE, SIZE, 0.0, RULED_HEADER)
                }

                else -> {
                    ShapeGrid(SIZE, SIZE, 0.0, 0.0)
                }
            }
        }

        private fun isometric(
            page: Page,
            triangle: Double,
        ): ShapeGrid {
            val xStep = sqrt(3.0) / 2 * triangle
            val yStep = triangle / 2
            // The pattern is centred on the page, a triangle in from the edges.
            val columns = floor((page.width - 2 * triangle) / xStep)
            val rows = floor((page.height - 2 * triangle) / yStep)
            return ShapeGrid(xStep, yStep, (page.width - columns * xStep) / 2, (page.height - rows * yStep) / 2)
        }

        private fun nearestMultiple(
            value: Double,
            multiple: Double,
        ): Double = round(value / multiple) * multiple
    }
}

/** Xournal++'s shapes, drawn from a start point to the pen. */
object Shapes {
    /**
     * The points of a [type] shape dragged from [start] (already snapped to the grid) to [end],
     * for strokes [width] wide, on [grid]. Freehand gives just the two points.
     */
    fun points(
        type: DrawingType,
        start: ShapePoint,
        end: ShapePoint,
        width: Double,
        grid: ShapeGrid,
        modifiers: ShapeModifiers = ShapeModifiers(),
    ): List<ShapePoint> {
        // Lines and arrows snap their angle, then to the grid; boxes only to the grid.
        val angled =
            modifiers.snap &&
                (type == DrawingType.LINE || type == DrawingType.ARROW || type == DrawingType.DOUBLE_ARROW)
        val rotated = if (angled) snapRotation(end, start) else end
        val snapped = if (modifiers.snap) grid.snap(rotated) else rotated
        return when (type) {
            DrawingType.FREEHAND, DrawingType.LINE -> listOf(start, snapped)
            DrawingType.RECTANGLE -> rectangle(start, snapped, modifiers)
            DrawingType.ELLIPSE -> ellipse(start, snapped, modifiers)
            DrawingType.ARROW -> arrow(start, snapped, width, doubleEnded = false)
            DrawingType.DOUBLE_ARROW -> arrow(start, snapped, width, doubleEnded = true)
            DrawingType.COORDINATE_SYSTEM -> axes(start, snapped, modifiers)
        }
    }

    /** [point] turned about [center] to the nearest 15° when within 2.25° of it. */
    fun snapRotation(
        point: ShapePoint,
        center: ShapePoint,
    ): ShapePoint {
        val distance = hypot(point.x - center.x, point.y - center.y)
        val angle = Selecting.snappedAngle(atan2(point.y - center.y, point.x - center.x))
        return ShapePoint(center.x + distance * cos(angle), center.y + distance * sin(angle))
    }

    /** Width and height from [start] to [end], equal (the larger) for a square. */
    private fun squared(
        start: ShapePoint,
        end: ShapePoint,
        square: Boolean,
    ): Pair<Double, Double> {
        val width = end.x - start.x
        val height = end.y - start.y
        if (!square) return width to height
        val signW = if (width > 0) 1.0 else -1.0
        val signH = if (height > 0) 1.0 else -1.0
        val side = max(width * signW, height * signH)
        return side * signW to side * signH
    }

    private fun rectangle(
        start: ShapePoint,
        end: ShapePoint,
        modifiers: ShapeModifiers,
    ): List<ShapePoint> {
        val (width, height) = squared(start, end, modifiers.square)
        val p1 = if (modifiers.fromCenter) ShapePoint(start.x - width, start.y - height) else start
        val p2 = ShapePoint(start.x + width, start.y + height)
        return listOf(p1, ShapePoint(p1.x, p2.y), p2, ShapePoint(p2.x, p1.y), p1)
    }

    private fun ellipse(
        start: ShapePoint,
        end: ShapePoint,
        modifiers: ShapeModifiers,
    ): List<ShapePoint> {
        var width = end.x - start.x
        var height = end.y - start.y
        if (modifiers.square) {
            width = if (modifiers.fromCenter) hypot(width, height) else max(abs(width), abs(height)).withSign(width)
            height = width.withSign(if (height == 0.0) 1.0 else sign(height))
        }
        // Radii, and the centre: the start, or the middle of the box dragged.
        val rx = if (modifiers.fromCenter) width else width / 2
        val ry = if (modifiers.fromCenter) height else height / 2
        val cx = if (modifiers.fromCenter) start.x else start.x + rx
        val cy = if (modifiers.fromCenter) start.y else start.y + ry
        val perQuadrant = ceil(ELLIPSE_BASE + ELLIPSE_PER_POINT * (abs(rx) + abs(ry))).toInt()
        val step = PI / 2 / perQuadrant
        // The first quadrant, then mirrored across the axes.
        val quadrant = ArrayList<ShapePoint>(4 * perQuadrant + 1)
        quadrant += ShapePoint(cx + rx, cy)
        for (j in 1 until perQuadrant) {
            val target = step * j
            val angle = ELLIPSE_PULL * atan2(abs(ry) * sin(target), abs(rx) * cos(target)) + (1 - ELLIPSE_PULL) * target
            quadrant += ShapePoint(cx + rx * cos(angle), cy + ry * sin(angle))
        }
        quadrant += ShapePoint(cx, cy + ry)
        val half = quadrant + quadrant.asReversed().drop(1).map { ShapePoint(2 * cx - it.x, it.y) }
        return half + half.asReversed().drop(1).map { ShapePoint(it.x, 2 * cy - it.y) }
    }

    private fun arrow(
        start: ShapePoint,
        tip: ShapePoint,
        thickness: Double,
        doubleEnded: Boolean,
    ): List<ShapePoint> {
        val length = hypot(tip.x - start.x, tip.y - start.y)
        val slimness = length / thickness
        val longHead = if (doubleEnded) DOUBLE_SHORT_HEAD else SHORT_HEAD
        var delta = HEAD_ANGLE
        // The head is 7 widths long, unless the line is too short for it.
        var head = thickness * HEAD_WIDTHS
        when {
            slimness >= HEAD_WIDTHS / HEAD_SHARE -> {
                Unit
            }

            slimness >= MIN_HEAD_WIDTHS / HEAD_SHARE -> {
                head = length * HEAD_SHARE
            }

            slimness >= MIN_HEAD_WIDTHS / longHead -> {
                head = thickness * MIN_HEAD_WIDTHS
                val wide = MIN_HEAD_WIDTHS / longHead - MIN_HEAD_WIDTHS / HEAD_SHARE
                delta = (1 + (slimness - MIN_HEAD_WIDTHS / HEAD_SHARE) / wide) * HEAD_ANGLE
                head *= sin(HEAD_ANGLE) / sin(delta)
            }

            else -> {
                head = length * longHead
                delta = WIDE_HEAD_ANGLE
                head *= sin(HEAD_ANGLE) / sin(WIDE_HEAD_ANGLE)
            }
        }
        val angle = atan2(tip.y - start.y, tip.x - start.x)

        fun leg(
            from: ShapePoint,
            direction: Double,
        ) = ShapePoint(from.x + head * cos(direction), from.y + head * sin(direction))
        val tail =
            if (doubleEnded) {
                listOf(start, leg(start, angle + delta), start, leg(start, angle - delta), start)
            } else {
                listOf(start)
            }
        return tail + listOf(tip, leg(tip, angle + delta + PI), tip, leg(tip, angle - delta + PI), tip)
    }

    private fun axes(
        start: ShapePoint,
        end: ShapePoint,
        modifiers: ShapeModifiers,
    ): List<ShapePoint> {
        val (width, height) = squared(start, end, modifiers.square)
        return if (modifiers.fromCenter) {
            listOf(ShapePoint(start.x, start.y + height), start, ShapePoint(start.x + width, start.y))
        } else {
            listOf(start, ShapePoint(start.x, start.y + height), ShapePoint(start.x + width, start.y + height))
        }
    }

    // Xournal++'s arrow head: half its opening (wider on very short arrows), and THICK1,
    // THICK3, LENGTH2 and LENGTH4.
    private const val HEAD_ANGLE = PI / 6
    private const val WIDE_HEAD_ANGLE = PI / 3
    private const val HEAD_WIDTHS = 7.0
    private const val MIN_HEAD_WIDTHS = 1.6
    private const val HEAD_SHARE = 0.4
    private const val SHORT_HEAD = 0.8
    private const val DOUBLE_SHORT_HEAD = 0.5

    // Xournal++'s ellipse resolution, and how its points lean towards even spacing.
    private const val ELLIPSE_BASE = 5.0
    private const val ELLIPSE_PER_POINT = 0.3
    private const val ELLIPSE_PULL = 0.25
}
