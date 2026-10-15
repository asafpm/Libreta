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
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PageChange
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.core.model.ToolSettings
import kotlin.math.roundToInt

/**
 * Writing and erasing on a [DocumentView]: input ([InkInput]), wet ink while a stroke is
 * written, and committing it to its page, where it is drawn on top ([PendingChanges]) until
 * the page's tiles show it. Its wet ink goes once the page has drawn it for a few frames, so
 * it never flickers; at once if the view moves. Erasing ([Erasing]) redraws what it changed
 * right away the same way.
 */
internal class Inking(
    private val view: View,
    private val host: Host,
) {
    /** What is shown, once there is a document and a size. */
    class ViewState(
        val layout: DocumentLayout,
        val renderer: TileRenderer,
        val caches: RenderCaches,
        val viewport: Viewport,
        /** The document's pages; the renderer has them as shown. */
        val pages: ShownPages,
    )

    /** What writing needs from the view. */
    interface Host {
        val state: ViewState?

        /** A page changed: render and draw again. */
        fun pageChanged()

        /** Gives the touch sequence of [event] to the gestures. */
        fun startGesture(event: MotionEvent)
    }

    var tools = ToolSettings()
    var fingerDraws = false
    var wetInk: WetInk = WetInk.None
    var onEdit: ((Edit) -> Unit)? = null

    /** Called when writing shows a page's hidden layer again. */
    var onLayerView: ((index: Int, view: LayerView) -> Unit)? = null

    /** Renders committed strokes and changed areas on the UI thread. */
    var renderer: PageRenderer? = null

    private val pending = PendingChanges()

    /** A stroke being written that the view draws (a highlighter), and its page. */
    private var inView: Pair<Int, Element.Stroke>? = null
    private var wetFramesLeft = 0
    private val predictor: MotionEventPredictor? = runCatching { MotionEventPredictor.newInstance(view) }.getOrNull()

    private val erasing =
        Erasing(
            object : Erasing.Host {
                override fun pageAt(
                    viewX: Float,
                    viewY: Float,
                ): PageMapping? = mappingAt(viewX, viewY)

                override fun page(index: Int): Page = checkNotNull(host.state).pages[index]

                override fun layer(index: Int): Int = showSelectedLayer(index)

                override fun show(
                    index: Int,
                    page: Page,
                    changed: Bounds,
                ) {
                    val state = host.state ?: return
                    state.pages[index] = page
                    showPage(index, state.pages.rendered(index), changed)
                }

                override fun edited(edit: Edit) {
                    onEdit?.invoke(edit)
                }
            },
        )

    val input =
        InkInput(
            object : InkInput.Host {
                override val tools: ToolSettings get() = this@Inking.tools
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

                override fun startErasing(
                    deleteStrokes: Boolean,
                    halfSize: Double,
                ) = erasing.start(deleteStrokes, halfSize)

                override fun eraseAt(
                    viewX: Float,
                    viewY: Float,
                ) {
                    erasing.eraseAt(viewX, viewY)
                    // The cursor moved.
                    view.invalidate()
                }

                override fun finishErasing() {
                    erasing.finish()
                    view.invalidate()
                }

                override fun drawInView(
                    page: Int,
                    stroke: Element.Stroke?,
                ) {
                    inView = stroke?.let { page to it }
                    view.invalidate()
                }
            },
        )

    /** Records [event] for motion prediction. */
    fun record(event: MotionEvent) {
        predictor?.record(event)
    }

    /** The stylus hovers: shows where the eraser would erase, if it would. */
    fun hover(event: MotionEvent) {
        val eraser = tools.eraser
        val erases = tools.toolFor(event, 0) == Tool.ERASER
        val halfSize =
            when {
                !erases || event.actionMasked == MotionEvent.ACTION_HOVER_EXIT -> null
                eraser.type == EraserType.WHITEOUT -> eraser.width / 2
                else -> eraser.width
            }
        erasing.hover(event.x, event.y, halfSize)
        view.invalidate()
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
        inView = null
        wetFramesLeft = 0
        wetInk.clear()
    }

    /** Draws the changes the tiles don't show yet, and the eraser. Returns whether to draw again soon. */
    fun draw(canvas: Canvas): Boolean {
        val state = host.state
        val painter = renderer
        if (state != null && painter != null) {
            pending.draw(
                canvas,
                state.layout,
                state.viewport,
                state.caches,
                painter,
                state.pages::rendered,
                state.renderer::version,
            )
            inView?.let { (page, stroke) ->
                canvas.onPage(state.layout, state.viewport, page) {
                    painter.renderElements(listOf(stroke), canvas, state.viewport.zoom.toFloat())
                }
            }
        }
        erasing.drawCursor(canvas)
        if (wetFramesLeft > 0 && --wetFramesLeft == 0) wetInk.clear()
        return wetFramesLeft > 0
    }

    /**
     * Page [index] is now [page] (null: unchanged) with its layers shown as [view] (null: as
     * they were), e.g. on undo; what changed is drawn until the tiles are re-rendered.
     */
    fun replacePage(
        index: Int,
        page: Page?,
        view: LayerView?,
    ) {
        val state = host.state ?: return
        val measurer = renderer?.textMeasurer ?: return
        val before = state.pages.rendered(index)
        page?.let { state.pages[index] = it }
        view?.let { state.pages.setView(index, it) }
        val after = state.pages.rendered(index)
        val changed = ElementDiff.changedBounds(before, after, measurer)
        if (changed != null) {
            showPage(index, after, changed)
        } else {
            // The renderer still gets the page as shown.
            state.renderer.updatePage(index, after, null, onScreen(state, index))
        }
        // Wet ink of a stroke just undone would still show it.
        viewportChanged()
        host.pageChanged()
    }

    /** The layer of page [index] that ink goes on, shown again if it was hidden. */
    private fun showSelectedLayer(index: Int): Int {
        val state = host.state ?: return 0
        val view = state.pages.view(index)
        if (view.selected in view.hidden) {
            val shown = view.copy(hidden = view.hidden - view.selected)
            replacePage(index, null, shown)
            onLayerView?.invoke(index, shown)
        }
        return view.selected
    }

    /** Page [index] is now [page], changed within [changed]: drawn from it until the tiles show it. */
    private fun showPage(
        index: Int,
        page: Page,
        changed: Bounds,
    ) {
        val state = host.state ?: return
        val version = state.renderer.updatePage(index, page, changed, onScreen(state, index))
        pending.addArea(index, version, changed)
        host.pageChanged()
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
        val layer = showSelectedLayer(index)
        val before = state.pages[index]
        val page = before.withElementAdded(stroke, layer)
        state.pages[index] = page
        val version = state.renderer.updatePage(index, state.pages.rendered(index), bounds, onScreen(state, index))
        // Drawn alone on top, unless layers above would cover it.
        if (layer ==
            page.layers.lastIndex
        ) {
            pending.addStroke(index, version, stroke, bounds)
        } else {
            pending.addArea(index, version, bounds)
        }
        wetFramesLeft = WET_INK_FRAMES
        onEdit?.invoke(Edit(listOf(PageChange(index, before, page))))
        host.pageChanged()
    }

    private companion object {
        /** Frames the page draws a committed stroke before its wet ink goes (frames take a few to show). */
        const val WET_INK_FRAMES = 4
    }
}

/** Whether a tile of page [index] is on screen at the current zoom (then it stands in while stale). */
private fun onScreen(
    state: Inking.ViewState,
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
