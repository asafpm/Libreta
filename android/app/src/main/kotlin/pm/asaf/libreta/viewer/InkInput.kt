// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.view.MotionEvent
import pm.asaf.libreta.core.geometry.InkSample
import pm.asaf.libreta.core.geometry.ShapePoint
import pm.asaf.libreta.core.geometry.StrokeBuilder
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings
import kotlin.math.hypot

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

        /** Selecting, and what is selected. */
        val selectionTool: SelectionTool

        /** Draws [stroke], being written on [page], with the page; null: no more. */
        fun drawInView(
            page: Int,
            stroke: Element.Stroke?,
        )

        /** The document's page [index] (its background places the grid shapes snap to). */
        fun page(index: Int): Page

        /** A tap with the text tool at a view point: edits text there. */
        fun tapText(
            viewX: Float,
            viewY: Float,
        )

        /** How far (pixels) a pointer moves before a tap is no longer one. */
        val touchSlop: Float

        /** Pages are shown dark: wet ink is recoloured like them. */
        val darkPages: Boolean
    }

    /** One stroke in progress. */
    private class Writing(
        val pointerId: Int,
        val page: PageMapping,
        val ink: StrokeInk,
        val builder: StrokeBuilder,
        val color: Int,
        val byFinger: Boolean,
        val pressure: Boolean,
        /** Drawn by the view rather than as wet ink. */
        val inView: Boolean,
    ) {
        /** Where and when the pen last moved more than a jitter: held there long enough, the stroke straightens. */
        private var stillX = Float.NaN
        private var stillY = Float.NaN
        private var stillSince = 0L

        /** Whether the pen has stayed put long enough, far enough from where the stroke began, to straighten it. */
        fun heldStill(
            event: MotionEvent,
            index: Int,
        ): Boolean {
            val x = event.getX(index)
            val y = event.getY(index)
            if (stillX.isNaN() || hypot(x - stillX, y - stillY) > STILL_PX) {
                stillX = x
                stillY = y
                stillSince = event.eventTime
                return false
            }
            val length = hypot(page.toViewX(builder.x(0)) - x, page.toViewY(builder.y(0)) - y)
            return event.eventTime - stillSince >= HOLD_MS && length >= MIN_STRAIGHT_PX
        }
    }

    private enum class Ink { PEN, HIGHLIGHTER, WHITEOUT }

    private var writing: Writing? = null

    private val eraser = EraserInput(host)
    private val selection = SelectionInput(host)
    private val shapes = ShapeInput(host)
    private val textTap = TextTapInput(host)
    private val pointerInputs = listOf<PointerInput>(eraser, selection, shapes)

    /** The rest of this touch sequence is ignored (e.g. it started off the pages). */
    private var ignoring = false

    /** Whether a stroke is being written, or erasing goes on. */
    val isActive: Boolean
        get() = writing != null || textTap.isActive || pointerInputs.any { it.isActive }

    /**
     * Handles [event] if it belongs to writing; false leaves it to the gestures. A touch
     * sequence that starts with the stylus is all ink.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val active = writing
        val busy = pointerInputs.firstOrNull { it.isActive }
        return when {
            ignoring -> {
                true.also { if (event.isSequenceEnd()) ignoring = false }
            }

            active != null -> {
                continueStroke(active, event)
            }

            busy != null -> {
                if (!busy.onTouchEvent(event)) ignoring = !event.isSequenceEnd()
                true
            }

            textTap.isActive -> {
                textTap.onTouchEvent(event)
                true
            }

            event.startsText(host.tools) -> {
                textTap.begin(event, event.actionIndex)
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
        val chosen = tools.toolFor(event, index)
        val draws =
            stylus || tool == MotionEvent.TOOL_TYPE_ERASER ||
                (finger && host.fingerDraws && event.actionMasked == MotionEvent.ACTION_DOWN)
        when {
            !draws -> {
                return false
            }

            chosen == Tool.ERASER && tools.eraser.type == EraserType.WHITEOUT -> {
                begin(
                    event,
                    index,
                    finger,
                    Ink.WHITEOUT,
                )
            }

            chosen == Tool.ERASER -> {
                eraser.begin(event, index)
            }

            chosen == Tool.SELECT -> {
                selection.begin(event, index)
            }

            tools.drawingType != DrawingType.FREEHAND -> {
                // Like Xournal++, nothing is drawn off the pages.
                if (!shapes.begin(event, index, tools, chosen)) ignoring = !event.isSequenceEnd()
            }

            chosen == Tool.HIGHLIGHTER -> {
                begin(event, index, finger, Ink.HIGHLIGHTER)
            }

            else -> {
                begin(event, index, finger, Ink.PEN)
            }
        }
        return true
    }

    private fun begin(
        event: MotionEvent,
        index: Int,
        byFinger: Boolean,
        ink: Ink,
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
        val strokeInk =
            when (ink) {
                Ink.PEN -> StrokeInk(StrokeTool.PEN, tools.pen.color, tools.pen.size.width)
                Ink.WHITEOUT -> StrokeInk(StrokeTool.ERASER, WHITE, tools.eraser.width)
                Ink.HIGHLIGHTER -> StrokeInk(StrokeTool.HIGHLIGHTER, tools.highlighter.color, tools.highlighter.width)
            }
        val color = strokeInk.color
        val builder = StrokeBuilder(strokeInk.tool, color, strokeInk.width)
        val stroke =
            Writing(
                pointerId = event.getPointerId(index),
                page = page,
                ink = strokeInk,
                builder = builder,
                color =
                    android.graphics.Color
                        .argb(color.alpha, color.red, color.green, color.blue)
                        .let { if (host.darkPages) PageRecolor.map(it) else it },
                byFinger = byFinger,
                pressure = ink == Ink.PEN && !byFinger,
                // Highlighters blend with the page: drawn in it, as they will look.
                inView = ink == Ink.HIGHLIGHTER,
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
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && stroke.byFinger -> cancel(event, handOver = true)
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
        if (stroke.heldStill(event, index)) {
            straighten(stroke, event)
            return
        }
        add(stroke, first, prediction(stroke, event))
    }

    /** Turns [stroke] into a straight line from where it began, following the pen until it lifts. */
    private fun straighten(
        stroke: Writing,
        event: MotionEvent,
    ) {
        writing = null
        if (stroke.inView) host.drawInView(stroke.page.page, null) else host.wetInk.cancel()
        val start = ShapePoint(stroke.builder.x(0), stroke.builder.y(0))
        shapes.straighten(stroke.pointerId, stroke.page, stroke.ink, start, event)
    }

    private fun finish(
        stroke: Writing,
        event: MotionEvent,
        index: Int,
    ) {
        writing = null
        val pressure = if (stroke.pressure) event.getPressure(index).toDouble() else null
        if (!stroke.inView) host.wetInk.commit()
        host.commit(stroke.page.page, stroke.builder.finish(pressure))
        if (stroke.inView) host.drawInView(stroke.page.page, null)
        if (!event.isSequenceEnd()) ignoring = true
    }

    /** Drops the stroke: cancelled, or ([handOver]) the start of a pinch or a two-finger pan. */
    private fun cancel(
        event: MotionEvent,
        handOver: Boolean = false,
    ) {
        val stroke = writing
        writing = null
        if (stroke?.inView == true) host.drawInView(stroke.page.page, null) else host.wetInk.cancel()
        if (handOver) host.startGesture(event) else ignoring = !event.isSequenceEnd()
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
        if (stroke.inView) {
            host.drawInView(page.page, builder.current())
            return
        }
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

/** Held within this many pixels for [HOLD_MS], a freehand stroke straightens, if it is [MIN_STRAIGHT_PX] long. */
private const val STILL_PX = 6f
private const val HOLD_MS = 500L
private const val MIN_STRAIGHT_PX = 24f

/** Whether this touch goes down with the text tool: a stylus or a finger, which may tap to edit text. */
private fun MotionEvent.startsText(tools: ToolSettings): Boolean {
    val type = getToolType(actionIndex)
    return actionMasked == MotionEvent.ACTION_DOWN &&
        tools.toolFor(this, actionIndex) == Tool.TEXT &&
        (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_FINGER)
}

private fun MotionEvent.isSequenceEnd() =
    actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL

private fun MotionEvent.isLift(index: Int) =
    actionMasked == MotionEvent.ACTION_UP || (actionMasked == MotionEvent.ACTION_POINTER_UP && actionIndex == index)
