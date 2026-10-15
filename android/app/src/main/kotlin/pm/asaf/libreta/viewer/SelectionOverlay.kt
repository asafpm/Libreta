// SPDX-License-Identifier: GPL-2.0-or-later
// Looks as in Xournal++ v1.3.8 (src/core/control/tools/EditSelection.cpp paint, and
// src/core/view/overlays/SelectorView.cpp): cornflower blue, filled at 0.3 alpha, the box
// dashed, white handles, a red rotation handle; © Xournal++ contributors.

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import pm.asaf.libreta.core.geometry.SelectionBox
import pm.asaf.libreta.core.geometry.SelectionHandle
import pm.asaf.libreta.core.geometry.SelectionTransform
import pm.asaf.libreta.core.model.SelectMode
import kotlin.math.PI

/** Draws selecting: the area being drawn, the selection's box and handles, and a moving selection. */
internal class SelectionOverlay {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FILL }
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LINE
            style = Paint.Style.STROKE
            strokeWidth = LINE_WIDTH
        }
    private val dashed = Paint(line).apply { pathEffect = DashPathEffect(floatArrayOf(DASH, DASH), 0f) }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HANDLE }
    private val rotateFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROTATE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** A rectangle (from the first point to the last) or a lasso (through them all), in view pixels. */
    fun drawArea(
        canvas: Canvas,
        points: List<Float>,
        mode: SelectMode,
    ) {
        if (points.size < 2) return
        val path = Path()
        when (mode) {
            SelectMode.RECTANGLE -> {
                val x0 = points[0]
                val y0 = points[1]
                val x1 = points[points.size - 2]
                val y1 = points[points.size - 1]
                path.addRect(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1), Path.Direction.CW)
            }

            SelectMode.LASSO -> {
                path.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
                path.close()
            }
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
    }

    /** The box of a selection on the page at [mapping], with handles of [radius] (pixels). */
    fun drawBox(
        canvas: Canvas,
        selection: SelectionBox,
        mapping: PageMapping,
        radius: Float,
        rotateOffset: Float,
    ) {
        val box = selection.box
        val left = mapping.toViewX(box.left)
        val top = mapping.toViewY(box.top)
        val right = mapping.toViewX(box.right)
        val bottom = mapping.toViewY(box.bottom)
        canvas.drawRect(left, top, right, bottom, fill)
        canvas.drawRect(left, top, right, bottom, dashed)
        val half = radius * HANDLE_SHARE
        for (handle in selection.handles) {
            val (px, py) = selection.position(handle, rotateOffset / mapping.zoom)
            val x = mapping.toViewX(px)
            val y = mapping.toViewY(py)
            if (handle == SelectionHandle.ROTATE) {
                canvas.drawCircle(x, y, half, rotateFill)
                canvas.drawCircle(x, y, half, line)
            } else {
                canvas.drawRect(x - half, y - half, x + half, y + half, handleFill)
                canvas.drawRect(x - half, y - half, x + half, y + half, line)
            }
        }
    }

    /**
     * A selection being moved: its [image] (over [selection]'s box) and the box outline, as
     * [transform] puts them, on the page at [mapping].
     */
    fun drawMoving(
        canvas: Canvas,
        image: SelectionImage?,
        selection: SelectionBox,
        mapping: PageMapping,
        transform: SelectionTransform,
    ) {
        val box = selection.box
        val pageToView = toView(transform, mapping)
        if (image != null) {
            val matrix = Matrix()
            matrix.setScale(1 / image.zoom, 1 / image.zoom)
            matrix.postTranslate(box.left.toFloat(), box.top.toFloat())
            matrix.postConcat(pageToView)
            canvas.drawBitmap(image.bitmap, matrix, bitmapPaint)
        }
        val corners =
            floatArrayOf(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat(),
                box.left.toFloat(),
                box.bottom.toFloat(),
            )
        pageToView.mapPoints(corners)
        val outline = Path()
        outline.moveTo(corners[0], corners[1])
        for (i in 2 until corners.size step 2) outline.lineTo(corners[i], corners[i + 1])
        outline.close()
        canvas.drawPath(outline, dashed)
    }

    /** [transform] (page units), then the page at [mapping] onto the view. */
    private fun toView(
        transform: SelectionTransform,
        mapping: PageMapping,
    ): Matrix =
        Matrix().apply {
            setTranslate(transform.dx.toFloat(), transform.dy.toFloat())
            postScale(
                transform.fx.toFloat(),
                transform.fy.toFloat(),
                transform.originX.toFloat(),
                transform.originY.toFloat(),
            )
            postRotate((transform.angle * DEGREES).toFloat(), transform.centerX.toFloat(), transform.centerY.toFloat())
            postScale(mapping.zoom.toFloat(), mapping.zoom.toFloat())
            postTranslate(mapping.toViewX(0.0), mapping.toViewY(0.0))
        }

    private companion object {
        /** Xournal++'s selection colour, cornflower blue, and its fill at 0.3 alpha. */
        const val LINE = 0xFF729FCF.toInt()
        const val FILL = 0x4D729FCF
        const val HANDLE = 0xFFFFFFFF.toInt()
        const val ROTATE = 0xFFE53935.toInt()
        const val LINE_WIDTH = 2f
        const val DASH = 10f

        /** A handle is drawn smaller than where it can be taken. */
        const val HANDLE_SHARE = 0.7f
        const val DEGREES = 180 / PI
    }
}
