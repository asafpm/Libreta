// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/control/tools/BaseShapeHandler.cpp: the start snaps to the
// grid, the whole shape is made again from it to the pen on every move (Shift, Ctrl and Alt
// changing it), and a shape that never left its start is dropped. © Xournal++ contributors.

package pm.asaf.libreta.viewer

import android.view.KeyEvent
import android.view.MotionEvent
import pm.asaf.libreta.core.geometry.ShapeGrid
import pm.asaf.libreta.core.geometry.ShapeModifiers
import pm.asaf.libreta.core.geometry.ShapePoint
import pm.asaf.libreta.core.geometry.Shapes
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.DrawingType
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.PointList
import pm.asaf.libreta.core.model.StrokeTool
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings

/** A stroke's tool, colour and width (points). */
internal class StrokeInk(
    val tool: StrokeTool,
    val color: Color,
    val width: Double,
)

/**
 * Drawing shapes for [InkInput]: one pointer drags a [DrawingType] shape from where it went
 * down, drawn by the view as it changes, and added as one stroke when it lifts. A keyboard's
 * Shift (squares, circles), Ctrl (from the centre) and Alt (no snapping) change it.
 */
internal class ShapeInput(
    private val host: InkInput.Host,
) {
    private class Shaping(
        val pointerId: Int,
        val page: PageMapping,
        val type: DrawingType,
        val ink: StrokeInk,
        val start: ShapePoint,
        val grid: ShapeGrid,
    ) {
        var stroke: Element.Stroke? = null
    }

    private var shaping: Shaping? = null

    val isActive: Boolean get() = shaping != null

    /**
     * Starts a shape of [tools]' drawing type at pointer [index] of [event], in the
     * highlighter's ink if [tool] is the highlighter, the pen's otherwise; false if it is not
     * over a page.
     */
    fun begin(
        event: MotionEvent,
        index: Int,
        tools: ToolSettings,
        tool: Tool,
    ): Boolean {
        val type = tools.drawingType
        val ink =
            if (tool == Tool.HIGHLIGHTER) {
                StrokeInk(StrokeTool.HIGHLIGHTER, tools.highlighter.color, tools.highlighter.width)
            } else {
                StrokeInk(StrokeTool.PEN, tools.pen.color, tools.pen.size.width)
            }
        val page = host.pageAt(event.getX(index), event.getY(index)) ?: return false
        val grid = ShapeGrid.of(host.page(page.page))
        val point = ShapePoint(page.toPageX(event.getX(index)), page.toPageY(event.getY(index)))
        val start = if (event.alt) point else grid.snap(point)
        shaping = Shaping(event.getPointerId(index), page, type, ink, start, grid)
        return true
    }

    /**
     * Goes on from a freehand stroke held still: a line in [ink] from [start] (page units on
     * [page]) following pointer [pointerId].
     */
    fun straighten(
        pointerId: Int,
        page: PageMapping,
        ink: StrokeInk,
        start: ShapePoint,
        event: MotionEvent,
    ) {
        shaping = Shaping(pointerId, page, DrawingType.LINE, ink, start, ShapeGrid.of(host.page(page.page)))
        event.findPointerIndex(pointerId).takeIf { it >= 0 }?.let { shapeTo(event, it) }
    }

    /** Handles [event] while drawing a shape; returns false once the pointer is done. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val active = shaping ?: return false
        val index = event.findPointerIndex(active.pointerId)
        when {
            index < 0 || event.actionMasked == MotionEvent.ACTION_CANCEL -> {
                finish(active, keep = false)
            }

            event.actionMasked == MotionEvent.ACTION_MOVE -> {
                shapeTo(event, index)
            }

            event.actionMasked == MotionEvent.ACTION_UP ||
                (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == index) -> {
                shapeTo(event, index)
                finish(active, keep = true)
            }

            else -> {} // another pointer, e.g. a palm
        }
        return isActive
    }

    /** The shape from its start to pointer [index] of [event]. */
    private fun shapeTo(
        event: MotionEvent,
        index: Int,
    ) {
        val active = shaping ?: return
        val page = active.page
        val end = ShapePoint(page.toPageX(event.getX(index)), page.toPageY(event.getY(index)))
        val modifiers = ShapeModifiers(event.shift, event.ctrl, snap = !event.alt)
        val points = Shapes.points(active.type, active.start, end, active.ink.width, active.grid, modifiers)
        val coordinates = DoubleArray(points.size * 2) { if (it % 2 == 0) points[it / 2].x else points[it / 2].y }
        // Like Xournal++, nothing until the shape leaves its start.
        active.stroke =
            if (points.all { it == active.start }) {
                null
            } else {
                Element.Stroke(active.ink.tool, active.ink.color, active.ink.width, PointList.of(*coordinates))
            }
        host.drawInView(page.page, active.stroke)
    }

    private fun finish(
        active: Shaping,
        keep: Boolean,
    ) {
        shaping = null
        val stroke = active.stroke
        if (keep && stroke != null) host.commit(active.page.page, stroke)
        host.drawInView(active.page.page, null)
    }
}

// A keyboard's modifiers, held while drawing.
private val MotionEvent.shift: Boolean get() = metaState and KeyEvent.META_SHIFT_ON != 0
private val MotionEvent.ctrl: Boolean get() = metaState and KeyEvent.META_CTRL_ON != 0
private val MotionEvent.alt: Boolean get() = metaState and KeyEvent.META_ALT_ON != 0
