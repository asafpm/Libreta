// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.view.MotionEvent
import pm.asaf.inkpad.core.geometry.InkSample
import pm.asaf.inkpad.core.geometry.StrokeBuilder
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.PenSettings
import pm.asaf.inkpad.core.model.StrokeTool

/** Where a page is on screen while a stroke is written: page units to view pixels and back. */
internal class PageMapping(
    val page: Int,
    /** View position of the page's top-left corner. */
    private val left: Double,
    private val top: Double,
    /** Pixels per point. */
    val zoom: Double,
) {
    fun toPageX(viewX: Float): Double = (viewX - left) / zoom

    fun toPageY(viewY: Float): Double = (viewY - top) / zoom

    fun toViewX(x: Double): Float = (left + x * zoom).toFloat()

    fun toViewY(y: Double): Float = (top + y * zoom).toFloat()
}

/**
 * Turns touches into strokes. The stylus draws (fingers too if [Host.fingerDraws]); its
 * eraser end and a pressed stylus button don't (they belong to the eraser, Phase 5). While a
 * stroke is written, other pointers (a resting palm) are ignored; a cancelled pointer drops
 * the stroke.
 */
internal class InkInput(
    private val host: Host,
) {
    interface Host {
        val pen: PenSettings
        val fingerDraws: Boolean
        val wetInk: WetInk

        /** The page under the view point, or null if there is none. */
        fun pageAt(
            viewX: Float,
            viewY: Float,
        ): PageMapping?

        /** Predicted next positions of [pointerId] after [event], in view pixels (x, y pairs). */
        fun predict(
            event: MotionEvent,
            pointerId: Int,
        ): FloatArray

        /** Hands the rest of a touch sequence that a finger stroke started over to gestures. */
        fun startGesture(event: MotionEvent)

        fun commit(
            page: Int,
            stroke: Element.Stroke,
        )
    }

    /** One stroke in progress. */
    private class Writing(
        val pointerId: Int,
        val page: PageMapping,
        val builder: StrokeBuilder,
        val color: Int,
        val byFinger: Boolean,
    )

    private var writing: Writing? = null

    /** The rest of this touch sequence is ignored (e.g. the eraser end touched). */
    private var ignoring = false

    /** Whether a stroke is being written. */
    val isWriting: Boolean get() = writing != null

    /**
     * Handles [event] if it belongs to writing; false leaves it to the gestures. A touch
     * sequence that starts with the stylus is all ink.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val active = writing
        return when {
            ignoring -> {
                true.also { if (event.isSequenceEnd()) ignoring = false }
            }

            active != null -> {
                continueStroke(active, event)
            }

            event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN -> {
                start(
                    event,
                )
            }

            else -> {
                false
            }
        }
    }

    private fun start(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val tool = event.getToolType(index)
        val stylus = tool == MotionEvent.TOOL_TYPE_STYLUS
        val finger = tool == MotionEvent.TOOL_TYPE_FINGER
        return when {
            tool == MotionEvent.TOOL_TYPE_ERASER || (
                stylus &&
                    event.isButtonPressed(
                        MotionEvent.BUTTON_STYLUS_PRIMARY,
                    )
            ) -> {
                ignoring = !event.isSequenceEnd()
                true
            }

            stylus || (finger && host.fingerDraws && event.actionMasked == MotionEvent.ACTION_DOWN) -> {
                begin(event, index, byFinger = finger)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun begin(
        event: MotionEvent,
        index: Int,
        byFinger: Boolean,
    ) {
        val x = event.getX(index)
        val y = event.getY(index)
        val page = host.pageAt(x, y)
        if (page == null) {
            // Not on a page: like Xournal++, nothing is drawn.
            ignoring = !event.isSequenceEnd()
            return
        }
        val pen = host.pen
        val builder = StrokeBuilder(StrokeTool.PEN, pen.color, pen.size.width)
        val color = pen.color.let { android.graphics.Color.argb(it.alpha, it.red, it.green, it.blue) }
        val stroke = Writing(event.getPointerId(index), page, builder, color, byFinger)
        writing = stroke
        add(stroke, builder.add(sample(stroke.page, x, y, event.getPressure(index), byFinger)), emptyList())
    }

    private fun continueStroke(
        stroke: Writing,
        event: MotionEvent,
    ): Boolean {
        val index = event.findPointerIndex(stroke.pointerId)
        val cancelled =
            event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0
        when {
            index < 0 || cancelled -> cancel(event)
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && stroke.byFinger -> handOver(event)
            event.actionMasked == MotionEvent.ACTION_MOVE -> move(stroke, event, index)
            event.isLift(index) -> finish(stroke, event, index)
            else -> Unit // another pointer, e.g. a palm: ignored
        }
        return true
    }

    private fun move(
        stroke: Writing,
        event: MotionEvent,
        index: Int,
    ) {
        var first = stroke.builder.pointCount
        for (h in 0 until event.historySize) {
            val sample =
                sample(
                    stroke.page,
                    event.getHistoricalX(index, h),
                    event.getHistoricalY(index, h),
                    event.getHistoricalPressure(index, h),
                    stroke.byFinger,
                )
            first = minOf(first, stroke.builder.add(sample))
        }
        first =
            minOf(
                first,
                stroke.builder.add(
                    sample(
                        stroke.page,
                        event.getX(index),
                        event.getY(index),
                        event.getPressure(index),
                        stroke.byFinger,
                    ),
                ),
            )
        add(stroke, first, prediction(stroke, event))
    }

    private fun finish(
        stroke: Writing,
        event: MotionEvent,
        index: Int,
    ) {
        writing = null
        val pressure = if (stroke.byFinger) null else event.getPressure(index).toDouble()
        host.commit(stroke.page.page, stroke.builder.finish(pressure))
        if (!event.isSequenceEnd()) ignoring = true
    }

    private fun cancel(event: MotionEvent) {
        writing = null
        host.wetInk.clear()
        ignoring = !event.isSequenceEnd()
    }

    /** A second finger: the stroke was the start of a pinch or a two-finger pan. */
    private fun handOver(event: MotionEvent) {
        writing = null
        host.wetInk.clear()
        host.startGesture(event)
    }

    /** Draws the points from [first] on (and the segment leading to them) as wet ink. */
    private fun add(
        stroke: Writing,
        first: Int,
        prediction: List<WetSegment>,
    ) {
        val builder = stroke.builder
        val page = stroke.page
        val segments = ArrayList<WetSegment>()
        for (i in maxOf(first, 0) until builder.pointCount) {
            val from = if (i == 0) 0 else i - 1
            segments +=
                WetSegment(
                    page.toViewX(builder.x(from)),
                    page.toViewY(builder.y(from)),
                    page.toViewX(builder.x(i)),
                    page.toViewY(builder.y(i)),
                    (builder.width(from) * page.zoom).toFloat(),
                )
        }
        host.wetInk.update(stroke.color, segments, prediction)
    }

    private fun prediction(
        stroke: Writing,
        event: MotionEvent,
    ): List<WetSegment> {
        val points = host.predict(event, stroke.pointerId)
        val builder = stroke.builder
        val last = builder.pointCount - 1
        if (points.isEmpty() || last < 0) return emptyList()
        val width = (builder.width(last) * stroke.page.zoom).toFloat()
        var x = stroke.page.toViewX(builder.x(last))
        var y = stroke.page.toViewY(builder.y(last))
        val segments = ArrayList<WetSegment>()
        for (i in points.indices step 2) {
            segments += WetSegment(x, y, points[i], points[i + 1], width)
            x = points[i]
            y = points[i + 1]
        }
        return segments
    }
}

/** A sample at view point ([x], [y]) on [page]; fingers have no pressure. */
private fun sample(
    page: PageMapping,
    x: Float,
    y: Float,
    pressure: Float,
    byFinger: Boolean,
) = InkSample(page.toPageX(x), page.toPageY(y), if (byFinger) null else pressure.toDouble())

private fun MotionEvent.isSequenceEnd() =
    actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL

private fun MotionEvent.isLift(index: Int) =
    actionMasked == MotionEvent.ACTION_UP || (actionMasked == MotionEvent.ACTION_POINTER_UP && actionIndex == index)
