// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.graphics.lowlatency.LowLatencyCanvasView
import kotlin.math.max
import kotlin.math.min

/** A straight piece of a stroke being written, in view pixels; a dot when both ends are equal. */
data class WetSegment(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val width: Float,
)

/**
 * The stroke being written, drawn on top of the page as fast as possible. Like Xournal++'s
 * StrokeToolView, it is a series of round-capped segments, each as wide as its first point;
 * the page renders the finished stroke properly.
 */
interface WetInk {
    /**
     * Adds [segments] (more of the stroke, in [color], ARGB) and replaces the predicted
     * continuation with [prediction].
     */
    fun update(
        color: Int,
        segments: List<WetSegment>,
        prediction: List<WetSegment>,
    )

    /** The stroke is finished: it stays on screen, without its prediction, until [clear]. */
    fun commit()

    /** Drops the stroke being written. */
    fun cancel()

    /** Removes finished strokes (the page draws them by now); a stroke being written stays. */
    fun clear()

    /** No wet ink: strokes appear when committed. */
    object None : WetInk {
        override fun update(
            color: Int,
            segments: List<WetSegment>,
            prediction: List<WetSegment>,
        ) = Unit

        override fun commit() = Unit

        override fun cancel() = Unit

        override fun clear() = Unit
    }
}

/**
 * Wet ink in a front buffer ([LowLatencyCanvasView], androidx.graphics): segments are drawn
 * straight into the buffer on screen, without waiting for the next frame. The predicted
 * continuation is erased and redrawn with each update.
 *
 * Finished strokes stay in the front buffer until [clear], once the page below has drawn them
 * for a few frames; then they are erased (painted over with transparency, and any wet ink they
 * overlapped is redrawn). The page shows them all along, so no frame lacks them.
 */
class LowLatencyWetInk(
    private val view: LowLatencyCanvasView,
) : WetInk {
    /** A stroke's segments in the front buffer. */
    private class Stroke(
        val color: Int,
    ) {
        val segments = ArrayList<WetSegment>()
    }

    private val lock = Any()

    // Guarded by lock: written on the UI thread, drawn on the render thread.
    private var current: Stroke? = null
    private val queued = ArrayList<WetSegment>()
    private var prediction: List<WetSegment> = emptyList()
    private var predicted: RectF? = null
    private val finished = ArrayList<Stroke>()
    private val erasing = ArrayList<Stroke>()

    private val paint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val eraser =
        Paint(paint).apply {
            isAntiAlias = false
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

    init {
        view.setRenderCallback(
            object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(
                    canvas: Canvas,
                    width: Int,
                    height: Int,
                ) {
                    // Everything is in the front buffer.
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                }

                override fun onDrawFrontBufferedLayer(
                    canvas: Canvas,
                    width: Int,
                    height: Int,
                ) = drawFrontBuffer(canvas)
            },
        )
    }

    override fun update(
        color: Int,
        segments: List<WetSegment>,
        prediction: List<WetSegment>,
    ) {
        synchronized(lock) {
            if (current == null) current = Stroke(color)
            queued += segments
            this.prediction = prediction
        }
        view.renderFrontBufferedLayer()
    }

    override fun commit() {
        synchronized(lock) {
            current?.let { finished += it }
            current = null
            prediction = emptyList()
        }
        // Draws what is queued and erases the prediction.
        view.renderFrontBufferedLayer()
    }

    override fun cancel() {
        synchronized(lock) {
            current?.let { erasing += it }
            current = null
            queued.clear()
            prediction = emptyList()
        }
        view.renderFrontBufferedLayer()
    }

    override fun clear() {
        synchronized(lock) {
            erasing += finished
            finished.clear()
        }
        view.renderFrontBufferedLayer()
    }

    private fun drawFrontBuffer(canvas: Canvas) {
        synchronized(lock) {
            val stroke = current ?: finished.lastOrNull()
            // New segments belong to the stroke being written, or the one just finished.
            stroke?.let { s ->
                paint.color = s.color
                for (segment in queued) draw(canvas, segment, paint)
                s.segments += queued
            }
            queued.clear()
            // Erase what goes, then redraw the ink that stays where it was.
            val erased = ArrayList<RectF>()
            predicted?.let { area ->
                val saved = canvas.save()
                canvas.clipRect(area)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.restoreToCount(saved)
                erased += area
            }
            for (gone in erasing) {
                for (segment in gone.segments) {
                    eraser.strokeWidth = segment.width + ERASE_MARGIN
                    canvas.drawLine(segment.x0, segment.y0, segment.x1, segment.y1, eraser)
                    erased += segment.bounds()
                }
            }
            erasing.clear()
            if (erased.isNotEmpty()) redraw(canvas, erased)
            stroke?.let { s ->
                paint.color = s.color
                for (segment in prediction) draw(canvas, segment, paint)
            }
            predicted = prediction.map { it.bounds() }.reduceOrNull { a, b -> a.apply { union(b) } }
        }
    }

    /** Redraws the segments of the remaining strokes that touch [areas]. */
    private fun redraw(
        canvas: Canvas,
        areas: List<RectF>,
    ) {
        for (stroke in finished + listOfNotNull(current)) {
            paint.color = stroke.color
            for (segment in stroke.segments) {
                val bounds = segment.bounds()
                if (areas.any { RectF.intersects(it, bounds) }) draw(canvas, segment, paint)
            }
        }
    }

    private fun draw(
        canvas: Canvas,
        segment: WetSegment,
        paint: Paint,
    ) {
        paint.strokeWidth = segment.width
        canvas.drawLine(segment.x0, segment.y0, segment.x1, segment.y1, paint)
    }

    /** The area [segment] paints, with a pixel to spare for anti-aliasing. */
    private fun WetSegment.bounds(): RectF {
        val margin = width / 2 + ERASE_MARGIN
        return RectF(min(x0, x1) - margin, min(y0, y1) - margin, max(x0, x1) + margin, max(y0, y1) + margin)
    }

    private companion object {
        /** Pixels erased beyond a stroke's width, for its anti-aliased edge. */
        const val ERASE_MARGIN = 2f
    }
}
