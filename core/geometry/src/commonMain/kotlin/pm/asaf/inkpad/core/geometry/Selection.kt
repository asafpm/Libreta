// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8: selection shapes from src/core/control/tools/Selector.cpp and
// Element::isInSelection (src/core/model/Element.cpp, Stroke.cpp); object picking from
// src/core/gui/PageViewFindObjectHelper.h and Stroke::distanceTo; transforms from
// src/core/control/tools/EditSelectionContents.cpp (finalizeSelection) with Stroke::scale,
// Stroke::rotate, Text::scale and Image::scale; rotation snapping from src/core/model/Snapping.cpp,
// © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.DoubleList
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** The area a selection tool encloses, in page units. */
sealed interface SelectionShape {
    fun contains(
        x: Double,
        y: Double,
    ): Boolean

    /** A rectangle between two corners, edges included. */
    data class Rect(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
    ) : SelectionShape {
        override fun contains(
            x: Double,
            y: Double,
        ): Boolean = x >= min(x1, x2) && x <= max(x1, x2) && y >= min(y1, y2) && y <= max(y1, y2)
    }

    /** A lasso: the polygon through [points] (x, y pairs), closed, inside by the even-odd rule. */
    class Lasso(
        private val points: DoubleArray,
    ) : SelectionShape {
        private val count = points.size / 2

        override fun contains(
            x: Double,
            y: Double,
        ): Boolean {
            if (count < MIN_POINTS) return false
            var inside = false
            var j = count - 1
            for (i in 0 until count) {
                val xi = points[2 * i]
                val yi = points[2 * i + 1]
                val xj = points[2 * j]
                val yj = points[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        private companion object {
            const val MIN_POINTS = 3
        }
    }
}

/** Selecting and picking elements. */
object Selecting {
    /**
     * Whether [shape] encloses [element]: every point of a stroke (its width aside), or the
     * four corners of anything else's box. Unknown elements are never selected.
     */
    fun encloses(
        shape: SelectionShape,
        element: Element,
        measurer: TextMeasurer,
    ): Boolean =
        when (element) {
            is Element.Stroke -> {
                (0 until element.points.size).all { shape.contains(element.points.x(it), element.points.y(it)) }
            }

            is Element.Unknown -> {
                false
            }

            else -> {
                val box = ElementGeometry.bounds(element, measurer)
                box != null &&
                    shape.contains(box.left, box.top) &&
                    shape.contains(box.right, box.top) &&
                    shape.contains(box.left, box.bottom) &&
                    shape.contains(box.right, box.bottom)
            }
        }

    /** The indices of [elements] that [shape] encloses, in order. */
    fun enclosed(
        shape: SelectionShape,
        elements: List<Element>,
        measurer: TextMeasurer,
    ): List<Int> = elements.indices.filter { encloses(shape, elements[it], measurer) }

    /**
     * The element of [elements] picked at ([x], [y]): one the point is on, front-most first,
     * or else the nearest within [radius]; null if none.
     */
    fun picked(
        elements: List<Element>,
        x: Double,
        y: Double,
        radius: Double,
        measurer: TextMeasurer,
    ): Int? {
        var best: Int? = null
        var bestDistance = radius
        for (i in elements.indices.reversed()) {
            val distance = distance(elements[i], x, y, measurer) ?: continue
            if (distance <= 0.0) return i
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /** How far ([x], [y]) is from what [element] draws (0 or less: on it); null if it draws nothing. */
    fun distance(
        element: Element,
        x: Double,
        y: Double,
        measurer: TextMeasurer,
    ): Double? =
        when (element) {
            is Element.Stroke -> {
                strokeDistance(element, x, y)
            }

            is Element.Unknown -> {
                null
            }

            else -> {
                ElementGeometry.bounds(element, measurer)?.let { box ->
                    hypot(max(max(box.left - x, x - box.right), 0.0), max(max(box.top - y, y - box.bottom), 0.0))
                }
            }
        }

    /** To the nearest segment, less half its width. */
    private fun strokeDistance(
        stroke: Element.Stroke,
        x: Double,
        y: Double,
    ): Double {
        val points = stroke.points
        val widths = stroke.segmentWidths?.takeIf { it.size >= points.size - 1 }
        if (points.size == 1) return hypot(points.x(0) - x, points.y(0) - y) - stroke.width / 2
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until points.size - 1) {
            val half = (widths?.get(i) ?: stroke.width) / 2
            best = min(best, segmentDistance(points.x(i), points.y(i), points.x(i + 1), points.y(i + 1), x, y) - half)
        }
        return best
    }

    @Suppress("LongParameterList") // a segment and a point
    private fun segmentDistance(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        x: Double,
        y: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / length).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx - x, ay + t * dy - y)
    }

    /** The area of [elements] together, or null if they draw nothing. */
    fun bounds(
        elements: List<Element>,
        measurer: TextMeasurer,
    ): Bounds? = elements.mapNotNull { ElementGeometry.bounds(it, measurer) }.reduceOrNull(Bounds::union)

    /** Rotation could turn these: strokes only (Xournal++ can't rotate text or images). */
    fun canRotate(elements: List<Element>): Boolean = elements.all { it is Element.Stroke }

    /** Scaling could mirror these: strokes only. */
    fun canMirror(elements: List<Element>): Boolean = elements.all { it is Element.Stroke }

    /** Text can only grow or shrink evenly: no stretching along one side. */
    fun canStretch(elements: List<Element>): Boolean = elements.none { it is Element.Text }

    /** [angle] (radians) snapped to the nearest 15° when within 2.25° of it, like Xournal++. */
    fun snappedAngle(angle: Double): Double {
        val step = PI / ROTATION_STEPS
        val nearest = round(angle / step) * step
        return if (abs(angle - nearest) <= step * ROTATION_TOLERANCE / 2) nearest else angle
    }

    /** 180° in 15° steps. */
    private const val ROTATION_STEPS = 12

    /** Xournal++'s snapping tolerance, a share of half a step. */
    private const val ROTATION_TOLERANCE = 0.3
}

/**
 * Moving, then scaling by ([fx], [fy]) about ([originX], [originY]), then rotating by [angle]
 * (radians) about ([centerX], [centerY]): Xournal++'s order. Origins are in the moved
 * coordinates.
 */
data class SelectionTransform(
    val dx: Double = 0.0,
    val dy: Double = 0.0,
    val fx: Double = 1.0,
    val fy: Double = 1.0,
    val originX: Double = 0.0,
    val originY: Double = 0.0,
    val angle: Double = 0.0,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
) {
    /** Where ([x], [y]) goes, as (x, y). */
    fun map(
        x: Double,
        y: Double,
    ): Pair<Double, Double> {
        val sx = originX + (x + dx - originX) * fx
        val sy = originY + (y + dy - originY) * fy
        if (angle == 0.0) return sx to sy
        val cos = cos(angle)
        val sin = sin(angle)
        val rx = sx - centerX
        val ry = sy - centerY
        return (centerX + rx * cos - ry * sin) to (centerY + rx * sin + ry * cos)
    }

    /** [element] transformed, by Xournal++'s rules for each kind. */
    fun apply(element: Element): Element =
        when (element) {
            is Element.Stroke -> {
                stroke(element)
            }

            is Element.Text -> {
                // Text scales its font by fx (with text, fx and fy are kept equal) and doesn't rotate.
                val (x, y) = map(element.x, element.y)
                element.copy(
                    x = x.toEightDigits(),
                    y = y.toEightDigits(),
                    size = (element.size * abs(fx)).toEightDigits(),
                )
            }

            is Element.Image -> {
                element.copy(bounds = box(element.bounds))
            }

            is Element.TexImage -> {
                element.copy(bounds = box(element.bounds))
            }

            is Element.Unknown -> {
                element
            }
        }

    /** Points mapped; widths and pressures scaled by √|fx·fy|. */
    private fun stroke(stroke: Element.Stroke): Element.Stroke {
        val coordinates = DoubleArray(stroke.points.size * 2)
        for (i in 0 until stroke.points.size) {
            val (x, y) = map(stroke.points.x(i), stroke.points.y(i))
            coordinates[2 * i] = x.toEightDigits()
            coordinates[2 * i + 1] = y.toEightDigits()
        }
        val fz = sqrt(abs(fx * fy))
        val widths =
            stroke.segmentWidths?.let { w ->
                DoubleList.fromArray(DoubleArray(w.size) { (w[it] * fz).toEightDigits() })
            }
        return stroke.copy(
            points = PointList.fromInterleaved(coordinates),
            width = (stroke.width * fz).toEightDigits(),
            segmentWidths = widths,
        )
    }

    /** A box moved and scaled (boxes don't rotate or mirror). */
    private fun box(bounds: Bounds): Bounds {
        val (left, top) = map(bounds.left, bounds.top)
        val (right, bottom) = map(bounds.right, bounds.bottom)
        return Bounds(
            min(left, right).toEightDigits(),
            min(top, bottom).toEightDigits(),
            max(left, right).toEightDigits(),
            max(top, bottom).toEightDigits(),
        )
    }
}
