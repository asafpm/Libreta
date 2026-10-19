// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.android.render.TileKey
import pm.asaf.libreta.android.render.TileRenderer
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.ElementDiff
import pm.asaf.libreta.core.geometry.StrokeGeometry
import pm.asaf.libreta.core.geometry.Viewport
import pm.asaf.libreta.core.geometry.intersects
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PageChange
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings
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

    /** Called when the selection changes, goes, or moves on screen. */
    var onSelection: (() -> Unit)? = null

    private val selectionHost =
        object : SelectionTool.Host {
            override fun pageAt(
                viewX: Float,
                viewY: Float,
            ): PageMapping? = mappingAt(viewX, viewY)

            override fun mappingOf(index: Int): PageMapping? = host.state?.let { placedPage(it, index) }

            override val pages: List<Page> get() = checkNotNull(host.state).pages.all

            override fun page(index: Int): Page = checkNotNull(host.state).pages[index]

            override fun layer(index: Int): Int = checkNotNull(host.state).pages.view(index).selected

            override val renderer: PageRenderer? get() = this@Inking.renderer

            override fun hide(hidden: HiddenElements?) {
                val state = host.state ?: return
                val page = hidden?.page ?: state.pages.hiddenElements?.page ?: return
                replacePage(page) { hiddenElements = hidden }
            }

            override fun edit(edit: Edit) {
                onEdit?.invoke(edit)
                // The last state of each page changed; what was left out is back, moved.
                val pages = edit.changes.filterIsInstance<PageChange>().associate { it.index to it.after }
                for ((index, page) in pages) {
                    replacePage(index) {
                        hiddenElements = null
                        this[index] = page
                    }
                }
                host.state?.pages?.hiddenElements?.let { hidden ->
                    replacePage(hidden.page) { hiddenElements = null }
                }
            }

            override fun offset(
                from: Int,
                to: Int,
            ): Pair<Double, Double> {
                val layout = checkNotNull(host.state).layout
                val a = layout.pageBounds(from)
                val b = layout.pageBounds(to)
                return (a.left - b.left) to (a.top - b.top)
            }

            override fun visibleArea(index: Int): Bounds? {
                val state = host.state ?: return null
                val page = state.layout.pageBounds(index)
                val visible = state.viewport.visible
                val area =
                    Bounds(
                        maxOf(visible.left, page.left) - page.left,
                        maxOf(visible.top, page.top) - page.top,
                        minOf(visible.right, page.right) - page.left,
                        minOf(visible.bottom, page.bottom) - page.top,
                    )
                return area.takeIf { it.right > it.left && it.bottom > it.top }
            }

            override fun redraw() = view.invalidate()

            override fun changed() {
                onSelection?.invoke()
                view.invalidate()
            }
        }

    val selection = SelectionTool(selectionHost).apply { density = view.resources.displayMetrics.density }

    /** Text typed or changed with the text tool. */
    val textEditing = TextEditing(selectionHost)

    /** Where the hovering stylus would write. */
    private val penCursor = PenCursor()

    /** What can be done with the selection. */
    val selectionCommands: SelectionCommands by lazy { SelectionCommands(selection, selectionHost) }

    val input =
        InkInput(
            object : InkInput.Host {
                override val tools: ToolSettings get() = this@Inking.tools
                override val selectionTool: SelectionTool get() = selection
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

                override fun page(index: Int): Page = checkNotNull(host.state).pages[index]

                override fun tapText(
                    viewX: Float,
                    viewY: Float,
                ) = textEditing.tap(viewX, viewY, tools.text)

                override val touchSlop: Float = TAP_DP * view.resources.displayMetrics.density

                override val darkPages: Boolean get() = this@Inking.darkPages
            },
        )

    /** Records [event] for motion prediction. */
    fun record(event: MotionEvent) {
        predictor?.record(event)
    }

    /**
     * The stylus hovers: shows where the eraser would erase, or where the pen would write. Any
     * other [event] (a touch starting) hides them: some devices (the Tab S9) send no hover exit
     * before the stylus writes.
     */
    fun hover(event: MotionEvent) {
        val eraser = tools.eraser
        val chosen = tools.toolFor(event, 0)
        val leaving =
            event.actionMasked != MotionEvent.ACTION_HOVER_MOVE && event.actionMasked != MotionEvent.ACTION_HOVER_ENTER
        val halfSize =
            when {
                chosen != Tool.ERASER || leaving -> null
                eraser.type == EraserType.WHITEOUT -> eraser.width / 2
                else -> eraser.width
            }
        erasing.hover(event.x, event.y, halfSize)
        val zoom = mappingAt(event.x, event.y)?.zoom?.takeUnless { leaving }
        penCursor.show(event.x, event.y, zoom?.let { cursorLook(tools, chosen, it, darkPages) })
        view.invalidate()
    }

    /** The view moved: wet ink of finished strokes would be out of place, and the selection moved on screen. */
    fun viewportChanged() {
        textEditing.viewportChanged()
        if (selection.selected != null) onSelection?.invoke()
        if (wetFramesLeft > 0) {
            wetFramesLeft = 0
            wetInk.clear()
        }
    }

    fun reset() {
        textEditing.cancel()
        selection.clear()
        pending.clear()
        inView = null
        wetFramesLeft = 0
        wetInk.clear()
    }

    /** Pages shown dark (PageRecolor): what is drawn outside the view's pages follows. */
    var darkPages: Boolean = false
        set(value) {
            field = value
            selection.darkPages = value
            textEditing.darkPages = value
        }

    /**
     * Draws the pages: their tiles ([drawTiles]), then the changes the tiles don't show yet and
     * the stroke written in the view, all through [recolor] if pages are shown dark; then, over
     * them, the selection and the eraser's cursor. Returns whether to draw again soon.
     */
    fun draw(
        canvas: Canvas,
        recolor: Paint?,
        drawTiles: () -> Unit,
    ): Boolean {
        val layer = recolor?.let { canvas.saveLayer(null, it) }
        drawTiles()
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
        layer?.let(canvas::restoreToCount)
        selection.draw(canvas)
        erasing.drawCursor(canvas)
        penCursor.draw(canvas)
        if (wetFramesLeft > 0 && --wetFramesLeft == 0) wetInk.clear()
        return wetFramesLeft > 0
    }

    /**
     * Page [index] as [change] leaves the pages (e.g. undone, its layers shown otherwise, or
     * a selection left out); what changed is drawn until the tiles are re-rendered.
     */
    fun replacePage(
        index: Int,
        change: ShownPages.() -> Unit,
    ) {
        val state = host.state ?: return
        val measurer = renderer?.textMeasurer ?: return
        val before = state.pages.rendered(index)
        state.pages.change()
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
            replacePage(index) { setView(index, shown) }
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
        val (x, y) = state.viewport.viewToLayout(viewX.toDouble(), viewY.toDouble())
        return state.layout.pageAt(x, y)?.let { placedPage(state, it) }
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

        /** A pointer moving less than this (dp) taps. */
        const val TAP_DP = 10f
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

/** Page [index], placed where the tiles draw it (whole pixels). */
internal fun placedPage(
    state: Inking.ViewState,
    index: Int,
): PageMapping {
    val viewport = state.viewport
    val page = state.layout.pageBounds(index)
    val left = ((page.left - viewport.offsetX) * viewport.zoom).roundToInt().toDouble()
    val top = ((page.top - viewport.offsetY) * viewport.zoom).roundToInt().toDouble()
    return PageMapping(index, left, top, viewport.zoom)
}

/**
 * What the hovering stylus shows with [chosen] at [zoom] (pixels a point); null: nothing (e.g.
 * the eraser has its own cursor).
 */
private fun cursorLook(
    tools: ToolSettings,
    chosen: Tool,
    zoom: Double,
    darkPages: Boolean,
): PenCursor.Look? {
    fun shown(color: Color): Int {
        val argb = android.graphics.Color.argb(color.alpha, color.red, color.green, color.blue)
        return if (darkPages) PageRecolor.map(argb) else argb
    }
    return when (chosen) {
        Tool.PEN -> {
            PenCursor.Look.Dot(shown(tools.pen.color), (tools.pen.size.width * zoom).toFloat())
        }

        Tool.HIGHLIGHTER -> {
            // See-through, like highlighter ink.
            val color = shown(tools.highlighter.color) and RGB or HALF_OPAQUE
            PenCursor.Look.Bar(color, (tools.highlighter.width * zoom).toFloat())
        }

        Tool.TEXT -> {
            PenCursor.Look.Caret((tools.text.size * zoom).toFloat())
        }

        Tool.ERASER, Tool.SELECT -> {
            null
        }
    }
}

private const val RGB = 0x00FFFFFF
private const val HALF_OPAQUE = 0x80000000.toInt()
