// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.view.MotionEvent
import pm.asaf.inkpad.core.geometry.InkSample
import pm.asaf.inkpad.core.geometry.StrokeBuilder
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.StrokeTool
import pm.asaf.inkpad.core.model.ToolSettings

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
 * Turns touches into strokes and erasing. The stylus uses the chosen tool (fingers too if
 * [Host.fingerDraws]); its eraser end erases, and so does holding its side button unless
 * that is turned off. While a stroke is written or erasing goes on, other pointers (a resting
 * palm) are ignored; a cancelled pointer drops the stroke.
 */
internal class InkInput(
    private val host: Host,
) {
    interface Host {
        val tools: ToolSettings
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

        /** Starts erasing: deleting the strokes touched, or cutting them; a square of half side [halfSize] points. */
        fun startErasing(
            deleteStrokes: Boolean,
            halfSize: Double,
        )

        /** Erases at a view point, going on from the last one. */
        fun eraseAt(
            viewX: Float,
            viewY: Float,
        )

        fun finishErasing()
    }

    /** One stroke in progress. */
    private class Writing(
        val pointerId: Int,
        val page: PageMapping,
        val builder: StrokeBuilder,
        val color: Int,
        val byFinger: Boolean,
        val pressure: Boolean,
    )

    private var writing: Writing? = null

    private val eraser = EraserInput(host)

    /** The rest of this touch sequence is ignored (e.g. it started off the pages). */
    private var ignoring = false

    /** Whether a stroke is being written, or erasing goes on. */
    val isActive: Boolean get() = writing != null || eraser.isActive

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

            eraser.isActive -> {
                if (!eraser.onTouchEvent(event)) ignoring = !event.isSequenceEnd()
                true
            }

            event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN -> {
                start(event)
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
        val tools = host.tools
        val erases = tools.erases(event, index)
        val draws =
            stylus || tool == MotionEvent.TOOL_TYPE_ERASER ||
                (finger && host.fingerDraws && event.actionMasked == MotionEvent.ACTION_DOWN)
        when {
            !draws -> return false
            erases && tools.eraser.type == EraserType.WHITEOUT -> begin(event, index, finger, whiteout = true)
            erases -> eraser.begin(event, index)
            else -> begin(event, index, finger, whiteout = false)
        }
        return true
    }

    private fun begin(
        event: MotionEvent,
        index: Int,
        byFinger: Boolean,
        whiteout: Boolean,
    ) {
        val x = event.getX(index)
        val y = event.getY(index)
        val page = host.pageAt(x, y)
        if (page == null) {
            // Not on a page: like Xournal++, nothing is drawn.
            ignoring = !event.isSequenceEnd()
            return
        }
        val tools = host.tools
        // Like Xournal++: whiteout is a white eraser stroke, and only the pen uses pressure.
        val ink = if (whiteout) WHITE else tools.pen.color
        val builder =
            if (whiteout) {
                StrokeBuilder(StrokeTool.ERASER, ink, tools.eraser.width)
            } else {
                StrokeBuilder(StrokeTool.PEN, ink, tools.pen.size.width)
            }
        val color = android.graphics.Color.argb(ink.alpha, ink.red, ink.green, ink.blue)
        val stroke =
            Writing(
                event.getPointerId(index),
                page,
                builder,
                color,
                byFinger,
                pressure =
                    !whiteout && !byFinger,
            )
        writing = stroke
        add(stroke, builder.add(sample(stroke, x, y, event.getPressure(index))), emptyList())
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
                    stroke,
                    event.getHistoricalX(index, h),
                    event.getHistoricalY(index, h),
                    event.getHistoricalPressure(index, h),
                )
            first = minOf(first, stroke.builder.add(sample))
        }
        first =
            minOf(
                first,
                stroke.builder.add(sample(stroke, event.getX(index), event.getY(index), event.getPressure(index))),
            )
        add(stroke, first, prediction(stroke, event))
    }

    private fun finish(
        stroke: Writing,
        event: MotionEvent,
        index: Int,
    ) {
        writing = null
        val pressure = if (stroke.pressure) event.getPressure(index).toDouble() else null
        host.wetInk.commit()
        host.commit(stroke.page.page, stroke.builder.finish(pressure))
        if (!event.isSequenceEnd()) ignoring = true
    }

    private fun cancel(event: MotionEvent) {
        writing = null
        host.wetInk.cancel()
        ignoring = !event.isSequenceEnd()
    }

    /** A second finger: the stroke was the start of a pinch or a two-finger pan. */
    private fun handOver(event: MotionEvent) {
        writing = null
        host.wetInk.cancel()
        host.startGesture(event)
    }

    /** A sample of [stroke] at view point ([x], [y]). */
    private fun sample(
        stroke: Writing,
        x: Float,
        y: Float,
        pressure: Float,
    ) = InkSample(stroke.page.toPageX(x), stroke.page.toPageY(y), if (stroke.pressure) pressure.toDouble() else null)

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

private val WHITE = Color.of(255, 255, 255)

private fun MotionEvent.isSequenceEnd() =
    actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL

private fun MotionEvent.isLift(index: Int) =
    actionMasked == MotionEvent.ACTION_UP || (actionMasked == MotionEvent.ACTION_POINTER_UP && actionIndex == index)
