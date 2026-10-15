// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/control/tools/EditSelection.cpp (the box, its handles and
// how dragging them scales or rotates), © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Where a selection box is held. */
enum class SelectionHandle(
    /** Which way the handle moves the box's sides: -1 the left (top), 1 the right (bottom), 0 neither. */
    val sideX: Int,
    val sideY: Int,
) {
    MOVE(0, 0),
    TOP_LEFT(-1, -1),
    TOP(0, -1),
    TOP_RIGHT(1, -1),
    RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1),
    BOTTOM(0, 1),
    BOTTOM_LEFT(-1, 1),
    LEFT(-1, 0),
    ROTATE(0, 0),
    ;

    val isCorner: Boolean get() = sideX != 0 && sideY != 0
    val isEdge: Boolean get() = (sideX == 0) != (sideY == 0)
}

/**
 * A selection's box on its page: its contents' bounds padded by 12 pt, with corner handles
 * (scaling evenly), edge handles (stretching one way, unless [canStretch] is false) and a
 * rotation handle right of the box ([canRotate]). Scaling past a side mirrors only if
 * [canMirror].
 */
data class SelectionBox(
    val contents: Bounds,
    val canRotate: Boolean = true,
    val canStretch: Boolean = true,
    val canMirror: Boolean = true,
) {
    val box: Bounds = contents.expandedBy(PADDING)
    val centerX: Double get() = (box.left + box.right) / 2
    val centerY: Double get() = (box.top + box.bottom) / 2

    /** Where [handle] is, the rotation handle [rotateOffset] right of the box. */
    fun position(
        handle: SelectionHandle,
        rotateOffset: Double,
    ): Pair<Double, Double> =
        if (handle == SelectionHandle.ROTATE) {
            box.right + rotateOffset to centerY
        } else {
            side(handle.sideX, box.left, box.right) to side(handle.sideY, box.top, box.bottom)
        }

    /** The handles shown. */
    val handles: List<SelectionHandle>
        get() =
            SelectionHandle.entries.filter {
                it.isCorner || (it.isEdge && canStretch) || (it == SelectionHandle.ROTATE && canRotate)
            }

    /**
     * The handle at ([x], [y]), within [radius] of it, as Xournal++ looks for them: corners,
     * then rotation, then edges; or MOVE inside the box; null outside.
     */
    fun handleAt(
        x: Double,
        y: Double,
        radius: Double,
        rotateOffset: Double,
    ): SelectionHandle? {
        val shown = handles
        val order =
            shown.filter { it.isCorner } + shown.filter { it == SelectionHandle.ROTATE } + shown.filter { it.isEdge }
        return order.firstOrNull { handle ->
            val (hx, hy) = position(handle, rotateOffset)
            abs(hx - x) <= radius && abs(hy - y) <= radius
        } ?: SelectionHandle.MOVE.takeIf { x >= box.left && x <= box.right && y >= box.top && y <= box.bottom }
    }

    /**
     * The transform of dragging [handle] from ([startX], [startY]) to ([x], [y]): moving it, scaling
     * about the opposite corner or side (evenly from a corner), or rotating about the centre
     * (snapped to 15°). The box stays at least [minSize] wide and high.
     */
    @Suppress("LongParameterList") // the handle, where it was taken and where it is
    fun transform(
        handle: SelectionHandle,
        startX: Double,
        startY: Double,
        x: Double,
        y: Double,
        minSize: Double,
    ): SelectionTransform =
        when {
            handle == SelectionHandle.MOVE -> {
                SelectionTransform(dx = x - startX, dy = y - startY)
            }

            handle == SelectionHandle.ROTATE -> {
                val angle = Selecting.snappedAngle(atan2(y - centerY, x - centerX))
                SelectionTransform(angle = angle, centerX = centerX, centerY = centerY)
            }

            else -> {
                scale(handle, x, y, minSize)
            }
        }

    private fun scale(
        handle: SelectionHandle,
        x: Double,
        y: Double,
        minSize: Double,
    ): SelectionTransform {
        // The opposite corner or side stays put.
        val fixedX = side(-handle.sideX, box.left, box.right)
        val fixedY = side(-handle.sideY, box.top, box.bottom)
        val handleX = side(handle.sideX, box.left, box.right)
        val handleY = side(handle.sideY, box.top, box.bottom)
        val width = box.right - box.left
        val height = box.bottom - box.top
        val (fx, fy) =
            if (handle.isCorner) {
                // Along the diagonal: the drag projected onto it.
                val ax = handleX - fixedX
                val ay = handleY - fixedY
                val f = ((x - fixedX) * ax + (y - fixedY) * ay) / (ax * ax + ay * ay)
                val even = limited(f, minSize / width, minSize / height)
                even to even
            } else if (handle.sideX != 0) {
                limited((x - fixedX) / (handleX - fixedX), minSize / width, 0.0) to 1.0
            } else {
                1.0 to limited((y - fixedY) / (handleY - fixedY), 0.0, minSize / height)
            }
        return SelectionTransform(fx = fx, fy = fy, originX = fixedX, originY = fixedY)
    }

    /** [f], at least [minX] and [minY] in size, and positive unless mirroring is allowed. */
    private fun limited(
        f: Double,
        minX: Double,
        minY: Double,
    ): Double {
        val least = max(minX, minY)
        return when {
            abs(f) >= least && (f > 0 || canMirror) -> f
            f < 0 && canMirror -> -least
            else -> least
        }
    }

    private fun side(
        which: Int,
        low: Double,
        high: Double,
    ): Double =
        when {
            which < 0 -> low
            which > 0 -> high
            else -> (low + high) / 2
        }

    companion object {
        /** Around the contents, in points. */
        const val PADDING = 12.0
    }
}
