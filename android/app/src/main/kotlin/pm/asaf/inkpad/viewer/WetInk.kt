// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
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

    /** Removes all wet ink, e.g. once the page shows the stroke, or when it is cancelled. */
    fun clear()

    /** No wet ink: strokes appear when committed. */
    object None : WetInk {
        override fun update(
            color: Int,
            segments: List<WetSegment>,
            prediction: List<WetSegment>,
        ) = Unit

        override fun clear() = Unit
    }
}

/**
 * Wet ink in a front buffer ([LowLatencyCanvasView], androidx.graphics): segments are drawn
 * straight into the buffer on screen, without waiting for the next frame. The predicted
 * continuation is erased and redrawn with each update.
 */
class LowLatencyWetInk(
    private val view: LowLatencyCanvasView,
) : WetInk {
    private val lock = Any()

    // Guarded by lock: written on the UI thread, drawn on the render thread.
    private var color = Color.BLACK
    private val queued = ArrayList<WetSegment>()
    private var prediction: List<WetSegment> = emptyList()
    private val drawn = ArrayList<WetSegment>()
    private var predicted: RectF? = null

    private val paint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

    init {
        view.setRenderCallback(
            object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(
                    canvas: Canvas,
                    width: Int,
                    height: Int,
                ) {
                    // The page draws committed strokes; nothing stays here.
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
            this.color = color
            queued += segments
            this.prediction = prediction
        }
        view.renderFrontBufferedLayer()
    }

    override fun clear() {
        synchronized(lock) {
            queued.clear()
            drawn.clear()
            prediction = emptyList()
            predicted = null
        }
        view.clear()
    }

    private fun drawFrontBuffer(canvas: Canvas) {
        synchronized(lock) {
            paint.color = color
            // Erase the last prediction, and redraw the real ink it covered.
            predicted?.let { area ->
                val saved = canvas.save()
                canvas.clipRect(area)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                for (segment in drawn) if (segment.bounds().intersect(area)) draw(canvas, segment)
                canvas.restoreToCount(saved)
            }
            for (segment in queued) draw(canvas, segment)
            drawn += queued
            queued.clear()
            for (segment in prediction) draw(canvas, segment)
            predicted = prediction.map { it.bounds() }.reduceOrNull { a, b -> a.apply { union(b) } }
        }
    }

    private fun draw(
        canvas: Canvas,
        segment: WetSegment,
    ) {
        paint.strokeWidth = segment.width
        canvas.drawLine(segment.x0, segment.y0, segment.x1, segment.y1, paint)
    }

    /** The area [segment] paints, with a pixel to spare for anti-aliasing. */
    private fun WetSegment.bounds(): RectF {
        val margin = width / 2 + 1
        return RectF(min(x0, x1) - margin, min(y0, y1) - margin, max(x0, x1) + margin, max(y0, y1) + margin)
    }
}
