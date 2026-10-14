// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.TileKey
import pm.asaf.inkpad.android.render.TileRenderer
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.ElementDiff
import pm.asaf.inkpad.core.geometry.StrokeGeometry
import pm.asaf.inkpad.core.geometry.Viewport
import pm.asaf.inkpad.core.geometry.intersects
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Edit
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageChange
import pm.asaf.inkpad.core.model.PenSettings
import kotlin.math.roundToInt

/**
 * Writing on a [DocumentView]: input ([InkInput]), wet ink while a stroke is written, and
 * committing it to its page, where it is drawn on top ([PendingInk]) until the page's tiles
 * show it. Its wet ink goes once the page has drawn it for a few frames, so it never
 * flickers; at once if the view moves.
 */
internal class Inking(
    view: View,
    private val host: Host,
) {
    /** What is shown, once there is a document and a size. */
    class ViewState(
        val layout: DocumentLayout,
        val renderer: TileRenderer,
        val caches: RenderCaches,
        val viewport: Viewport,
    )

    /** What writing needs from the view. */
    interface Host {
        val state: ViewState?

        /** A page changed: render and draw again. */
        fun pageChanged()

        /** Gives the touch sequence of [event] to the gestures. */
        fun startGesture(event: MotionEvent)
    }

    var pen = PenSettings()
    var fingerDraws = false
    var wetInk: WetInk = WetInk.None
    var onEdit: ((Edit) -> Unit)? = null

    /** Renders committed strokes on the UI thread. */
    var renderer: PageRenderer? = null

    private val pending = PendingInk()
    private var wetFramesLeft = 0
    private val predictor: MotionEventPredictor? = runCatching { MotionEventPredictor.newInstance(view) }.getOrNull()

    val input =
        InkInput(
            object : InkInput.Host {
                override val pen: PenSettings get() = this@Inking.pen
                override val fingerDraws: Boolean get() = this@Inking.fingerDraws
                override val wetInk: WetInk get() = this@Inking.wetInk

                override fun pageAt(
                    viewX: Float,
                    viewY: Float,
                ): PageMapping? = mappingAt(viewX, viewY)

                override fun predict(
                    event: MotionEvent,
                    pointerId: Int,
                ): FloatArray = predicted(pointerId)

                override fun startGesture(event: MotionEvent) = host.startGesture(event)

                override fun commit(
                    page: Int,
                    stroke: Element.Stroke,
                ) = commitStroke(page, stroke)
            },
        )

    /** Records [event] for motion prediction. */
    fun record(event: MotionEvent) {
        predictor?.record(event)
    }

    /** The view moved: wet ink of finished strokes would be out of place. */
    fun viewportChanged() {
        if (wetFramesLeft > 0) {
            wetFramesLeft = 0
            wetInk.clear()
        }
    }

    fun reset() {
        pending.clear()
        wetFramesLeft = 0
        wetInk.clear()
    }

    /** Draws the committed strokes the tiles don't show yet. Returns whether to draw again soon. */
    fun draw(canvas: Canvas): Boolean {
        val state = host.state
        val painter = renderer
        if (state != null && painter != null) {
            pending.draw(canvas, state.layout, state.viewport, state.caches, painter, state.renderer::version)
        }
        if (wetFramesLeft > 0 && --wetFramesLeft == 0) wetInk.clear()
        return wetFramesLeft > 0
    }

    /** The page under a view point, placed where the tiles draw it (whole pixels). */
    private fun mappingAt(
        viewX: Float,
        viewY: Float,
    ): PageMapping? {
        val state = host.state ?: return null
        val viewport = state.viewport
        val (x, y) = viewport.viewToLayout(viewX.toDouble(), viewY.toDouble())
        return state.layout.pageAt(x, y)?.let { index ->
            val page = state.layout.pageBounds(index)
            val left = ((page.left - viewport.offsetX) * viewport.zoom).roundToInt().toDouble()
            val top = ((page.top - viewport.offsetY) * viewport.zoom).roundToInt().toDouble()
            PageMapping(index, left, top, viewport.zoom)
        }
    }

    /** Where [pointerId] is predicted to go next, as (x, y) pairs. */
    private fun predicted(pointerId: Int): FloatArray {
        val event = predictor?.predict() ?: return FloatArray(0)
        val index = event.findPointerIndex(pointerId)
        val points = ArrayList<Float>()
        if (index >= 0) {
            for (h in 0 until event.historySize) {
                points += event.getHistoricalX(index, h)
                points += event.getHistoricalY(index, h)
            }
            points += event.getX(index)
            points += event.getY(index)
        }
        event.recycle()
        return points.toFloatArray()
    }

    /** Adds [stroke] to page [index]: tiles touching it re-render while it is drawn on top. */
    private fun commitStroke(
        index: Int,
        stroke: Element.Stroke,
    ) {
        val state = host.state
        val bounds = StrokeGeometry.bounds(stroke)
        if (state == null || bounds == null) return
        val before = state.renderer.page(index)
        val page = before.withElementAdded(stroke)
        val version = state.renderer.updatePage(index, page, bounds, onScreen(state, index))
        pending.add(index, version, stroke, bounds)
        wetFramesLeft = WET_INK_FRAMES
        onEdit?.invoke(Edit(listOf(PageChange(index, before, page))))
        host.pageChanged()
    }

    /**
     * Shows [pages] (by index) in place of the current ones, e.g. on undo. Tiles touching what
     * changed re-render; meanwhile the old ones stand in.
     */
    fun showPages(pages: Map<Int, Page>) {
        val state = host.state ?: return
        val measurer = renderer?.textMeasurer ?: return
        for ((index, page) in pages) {
            val changed = ElementDiff.changedBounds(state.renderer.page(index), page, measurer)
            // Unchanged-looking pages too: the renderer has the document's pages.
            state.renderer.updatePage(index, page, changed, onScreen(state, index))
            pending.clear(index)
        }
        // Wet ink of a stroke just undone would still show it.
        viewportChanged()
        host.pageChanged()
    }

    /** Whether a tile of page [index] is on screen at the current zoom (then it stands in while stale). */
    private fun onScreen(
        state: ViewState,
        index: Int,
    ): (TileKey) -> Boolean {
        val viewport = state.viewport
        val zoom = viewport.zoom.toFloat()
        val placed = state.layout.pageBounds(index)
        val visible = viewport.visible
        val onScreen =
            Bounds(
                visible.left - placed.left,
                visible.top - placed.top,
                visible.right - placed.left,
                visible.bottom - placed.top,
            )
        return { it.zoom == zoom && it.bounds.intersects(onScreen) }
    }

    private companion object {
        /** Frames the page draws a committed stroke before its wet ink goes (frames take a few to show). */
        const val WET_INK_FRAMES = 4
    }
}
