// SPDX-License-Identifier: GPL-2.0-or-later
// Derived from Xournal++ v1.3.8 src/core/control/xojfile/LoadHandler.cpp (parserText,
// fixNullPressureValues), © Xournal++ contributors.

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.DoubleList
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PointList

/** A stroke, or part of one, as it is drawn. */
data class StrokePiece(
    val points: PointList,
    /** Width of each segment (one fewer than points), or null for the stroke's constant width. */
    val segmentWidths: DoubleList?,
)

/**
 * Applies Xournal++'s load-time pressure rules (docs/rendering.md §3.2) to a stroke whose
 * widths are stored exactly as written:
 * - fewer widths than segments: the pressure is ignored;
 * - extra widths are dropped;
 * - non-positive or NaN widths split the stroke into the runs of valid widths, and a
 *   stroke without any valid width disappears.
 */
object PressureRepair {
    fun pieces(stroke: Element.Stroke): List<StrokePiece> {
        val usable = usableWidths(stroke) ?: return listOf(StrokePiece(stroke.points, null))
        return if (usable.all { it.isValidWidth() }) {
            listOf(StrokePiece(stroke.points, DoubleList.fromArray(usable)))
        } else {
            splitAtInvalidWidths(stroke.points, usable)
        }
    }

    /** One width per segment, or null if the stroke has no usable pressure. */
    private fun usableWidths(stroke: Element.Stroke): DoubleArray? {
        val segments = stroke.points.size - 1
        val widths = stroke.segmentWidths
        return if (widths == null || segments < 1 ||
            widths.size < segments
        ) {
            null
        } else {
            DoubleArray(segments) { widths[it] }
        }
    }

    private fun splitAtInvalidWidths(
        points: PointList,
        widths: DoubleArray,
    ): List<StrokePiece> {
        val pieces = mutableListOf<StrokePiece>()
        var index = 0
        while (index < widths.size) {
            if (!widths[index].isValidWidth()) {
                index++
                continue
            }
            val start = index
            while (index < widths.size && widths[index].isValidWidth()) index++
            // A run of k valid widths covers k segments, i.e. k + 1 points.
            pieces +=
                StrokePiece(points.subList(start, index + 1), DoubleList.fromArray(widths.copyOfRange(start, index)))
        }
        return pieces
    }

    /** False for NaN as well. */
    private fun Double.isValidWidth(): Boolean = this > 0.0
}
