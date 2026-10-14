// SPDX-License-Identifier: GPL-2.0-or-later
// Bounding boxes follow Xournal++ v1.3.8 src/core/model/Stroke.cpp:816-857 and
// Text.cpp:67-76, © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.StrokeTool

object StrokeGeometry {
    /** Whether [piece] of [stroke] is drawn as a pressure outline; highlighters ignore pressure. */
    fun usesPressure(
        stroke: Element.Stroke,
        piece: StrokePiece,
    ): Boolean = stroke.tool != StrokeTool.HIGHLIGHTER && piece.segmentWidths != null

    /** Area covered by the drawn stroke, or null if nothing is drawn. Square caps may extend slightly beyond. */
    fun bounds(stroke: Element.Stroke): Bounds? =
        PressureRepair
            .pieces(stroke)
            .map { pieceBounds(stroke, it) }
            .reduceOrNull(Bounds::union)

    private fun pieceBounds(
        stroke: Element.Stroke,
        piece: StrokePiece,
    ): Bounds {
        val widths = piece.segmentWidths
        val halfWidth =
            if (widths != null && usesPressure(stroke, piece)) {
                (0 until widths.size).maxOf { widths[it] } / 2
            } else {
                stroke.width / 2
            }
        val points = piece.points
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (index in 0 until points.size) {
            left = minOf(left, points.x(index))
            right = maxOf(right, points.x(index))
            top = minOf(top, points.y(index))
            bottom = maxOf(bottom, points.y(index))
        }
        return Bounds(left - halfWidth, top - halfWidth, right + halfWidth, bottom + halfWidth)
    }
}

/** Measures the layout of a text element; text layout is platform-specific. */
fun interface TextMeasurer {
    fun measure(text: Element.Text): Size
}

object ElementGeometry {
    /** Area covered by [element], or null if it draws nothing (or is unknown). */
    fun bounds(
        element: Element,
        textMeasurer: TextMeasurer,
    ): Bounds? =
        when (element) {
            is Element.Stroke -> {
                StrokeGeometry.bounds(element)
            }

            is Element.Image -> {
                element.bounds
            }

            is Element.TexImage -> {
                element.bounds
            }

            is Element.Text -> {
                val size = textMeasurer.measure(element)
                Bounds(element.x, element.y, element.x + size.width, element.y + size.height)
            }

            is Element.Unknown -> {
                null
            }
        }
}
