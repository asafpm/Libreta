// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/view/StrokeView.cpp and StrokeViewHelper.cpp,
// © Xournal++ contributors.

package pm.asaf.inkpad.android.render

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import pm.asaf.inkpad.core.geometry.Blend
import pm.asaf.inkpad.core.geometry.DashPattern
import pm.asaf.inkpad.core.geometry.PressureRepair
import pm.asaf.inkpad.core.geometry.StrokeAppearance
import pm.asaf.inkpad.core.geometry.StrokeContour
import pm.asaf.inkpad.core.geometry.StrokeGeometry
import pm.asaf.inkpad.core.geometry.StrokePiece
import pm.asaf.inkpad.core.model.CapStyle
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PointList
import kotlin.math.roundToInt

/** Draws strokes (docs/rendering.md §3.2) in page units. */
internal class StrokePainter {
    private val paint = Paint().apply { isAntiAlias = true }
    private val layerPaint = Paint()

    fun paint(
        canvas: Canvas,
        stroke: Element.Stroke,
    ) {
        val appearance = StrokeAppearance.of(stroke)
        // Xournal++ splits strokes with invalid pressures into separate strokes when loading.
        for (piece in PressureRepair.pieces(stroke)) {
            if (piece.points.size < 2) continue
            if (appearance.combineFill) {
                paintAsOneShape(canvas, stroke, piece, appearance)
            } else {
                paintPiece(canvas, stroke, piece, appearance)
            }
        }
    }

    /** Pens and erasers, and unfilled highlighters. */
    private fun paintPiece(
        canvas: Canvas,
        stroke: Element.Stroke,
        piece: StrokePiece,
        appearance: StrokeAppearance,
    ) {
        appearance.fillOpacity?.let { fillOpacity ->
            setUp(stroke, Blend.NORMAL, fillOpacity)
            paint.style = Paint.Style.FILL
            canvas.drawPath(polyline(piece.points), paint)
        }
        setUp(stroke, appearance.blend, appearance.opacity)
        drawShape(canvas, stroke, piece)
    }

    /**
     * Filled highlighters: fill and stroke are painted opaque into a layer that is then
     * multiplied onto the page at the fill opacity (Xournal++ uses a mask for this).
     */
    private fun paintAsOneShape(
        canvas: Canvas,
        stroke: Element.Stroke,
        piece: StrokePiece,
        appearance: StrokeAppearance,
    ) {
        layerPaint.alpha = (appearance.opacity * MAX_ALPHA).roundToInt()
        layerPaint.blendMode = BlendMode.MULTIPLY
        val bounds = StrokeGeometry.bounds(stroke)?.toRectF()
        val saved = canvas.saveLayer(bounds, layerPaint)
        setUp(stroke, Blend.NORMAL, 1.0)
        paint.style = Paint.Style.FILL
        canvas.drawPath(polyline(piece.points), paint)
        drawShape(canvas, stroke, piece)
        canvas.restoreToCount(saved)
    }

    /** The stroke itself: a pressure outline, or a constant-width line. */
    private fun drawShape(
        canvas: Canvas,
        stroke: Element.Stroke,
        piece: StrokePiece,
    ) {
        val dashes = DashPattern.of(stroke.lineStyle)
        if (StrokeGeometry.usesPressure(stroke, piece)) {
            paint.style = Paint.Style.FILL
            canvas.drawPath(StrokeContour.outline(piece, dashes).toAndroidPath(), paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke.width.toFloat()
            paint.strokeCap = stroke.capStyle.toPaintCap()
            paint.strokeJoin = Paint.Join.ROUND
            paint.pathEffect = dashes?.let { DashPathEffect(it.evenLength(), 0f) }
            canvas.drawPath(polyline(piece.points), paint)
        }
    }

    private fun setUp(
        stroke: Element.Stroke,
        blend: Blend,
        opacity: Double,
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = stroke.color.toOpaqueArgb()
        paint.alpha = (opacity * MAX_ALPHA).roundToInt()
        paint.blendMode = if (blend == Blend.MULTIPLY) BlendMode.MULTIPLY else BlendMode.SRC_OVER
    }

    private fun polyline(points: PointList): Path =
        Path().apply {
            moveTo(points.x(0).toFloat(), points.y(0).toFloat())
            for (index in 1 until points.size) lineTo(points.x(index).toFloat(), points.y(index).toFloat())
        }

    /** Cairo repeats odd-length dash patterns with on/off swapped; DashPathEffect needs an even count. */
    private fun DoubleArray.evenLength(): FloatArray {
        val lengths = if (size % 2 == 0) this else this + this
        return FloatArray(lengths.size) { lengths[it].toFloat() }
    }

    /** Unknown cap styles draw round, like Xournal++. */
    private fun CapStyle?.toPaintCap(): Paint.Cap =
        when (this) {
            CapStyle.BUTT -> Paint.Cap.BUTT
            CapStyle.SQUARE -> Paint.Cap.SQUARE
            else -> Paint.Cap.ROUND
        }

    private companion object {
        const val MAX_ALPHA = 255
    }
}
