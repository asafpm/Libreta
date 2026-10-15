// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.android.render.RenderThreads
import pm.asaf.libreta.android.render.TileRenderer
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.Size
import pm.asaf.libreta.core.geometry.Viewport
import pm.asaf.libreta.core.geometry.ViewportAnchor
import pm.asaf.libreta.core.geometry.ViewportNavigator
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Edit
import pm.asaf.libreta.core.model.Page
import pm.asaf.libreta.core.model.PenSettings
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings
import pm.asaf.libreta.core.model.pageMapping
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shows a document's pages in a vertical strip, with pinch zoom, panning, flinging and
 * double-tap zoom, and lets the stylus write on them ([Inking]). Pages are rendered in the
 * background ([TileRenderer]); drawing only copies the cached bitmaps ([DocumentPainter]).
 */
class DocumentView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        /** A document as shown: its pages, their layout, and their rendering into [caches]. */
        private class Content(
            val pages: ShownPages,
            val caches: RenderCaches,
            private val newRenderer: () -> PageRenderer,
            private val threads: RenderThreads,
            versions: IntArray,
            private val onRendered: () -> Unit,
        ) {
            val renderer =
                TileRenderer(List(pages.size, pages::rendered), caches, newRenderer, onRendered, threads, versions)
            val layout = DocumentLayout(pages.all.map { Size(it.width, it.height) })
            val navigator = ViewportNavigator(layout)

            /**
             * The content for [next], a rearrangement of the pages (inserted, deleted, moved):
             * pages kept keep their tiles, versions and layer views. Closes this one's renderer.
             * Also returns each old page's new index (-1: gone).
             */
            fun rearranged(next: List<Page>): Pair<Content, IntArray> {
                val mapping = pageMapping(pages.all, next)
                val moved = IntArray(pages.size) { -1 }
                mapping.forEachIndexed { i, old -> if (old >= 0) moved[old] = i }
                renderer.close()
                caches.tiles.remap { old -> moved[old].takeIf { it >= 0 } }
                caches.thumbnails.remap { old -> moved[old].takeIf { it >= 0 } }
                val views = mapping.mapIndexed { i, old -> if (old >= 0) pages.view(old) else LayerView.of(next[i]) }
                val versions = IntArray(next.size) { i -> mapping[i].takeIf { it >= 0 }?.let(renderer::version) ?: 0 }
                return Content(ShownPages(next, views), caches, newRenderer, threads, versions, onRendered) to moved
            }
        }

        private var content: Content? = null

        /** What is shown; null until there is a document and the view has a size. */
        var viewport: Viewport? = null
            private set

        private var pendingAnchor: ViewportAnchor? = null
        private var scaling = false
        private var gestureActive = false
        private val scroller = OverScroller(context)
        private val painter = DocumentPainter()

        private val inking =
            Inking(
                this,
                object : Inking.Host {
                    override val state: Inking.ViewState?
                        get() {
                            val content = content ?: return null
                            return viewport?.let {
                                Inking.ViewState(
                                    content.layout,
                                    content.renderer,
                                    content.caches,
                                    it,
                                    content.pages,
                                )
                            }
                        }

                    override fun pageChanged() {
                        requestRendering()
                        invalidate()
                    }

                    override fun startGesture(event: MotionEvent) {
                        val down =
                            MotionEvent.obtain(
                                event.downTime,
                                event.eventTime,
                                MotionEvent.ACTION_DOWN,
                                event.getX(0),
                                event.getY(0),
                                0,
                            )
                        feedGestures(down)
                        down.recycle()
                        feedGestures(event)
                    }
                },
            )

        /** The tools the stylus (and fingers that draw) use. */
        var tools: ToolSettings
            get() = inking.tools
            set(value) {
                // Another tool: nothing selected any more, and no text edited.
                if (value.tool != Tool.SELECT) inking.selection.clear()
                if (value.tool == Tool.TEXT) inking.textEditing.restyle(value.text) else inking.textEditing.finish()
                inking.tools = value
            }

        /** Text typed or changed with the text tool. */
        val text: ViewText by lazy { ViewText(inking.textEditing) }

        /** The recolouring pages are drawn through while they are shown dark. */
        private var recolor: Paint? = null

        /** Whether pages are shown dark (PageRecolor); the document does not change. */
        var darkPages: Boolean
            get() = recolor != null
            set(value) {
                if (value == darkPages) return
                recolor = if (value) Paint().apply { colorFilter = PageRecolor.filter() } else null
                inking.darkPages = value
                invalidate()
            }

        /** Called with a picture dropped from another app, and where (view pixels); null: none accepted. */
        var onImageDropped: ((uri: Uri, viewX: Float, viewY: Float) -> Unit)? = null

        override fun onDragEvent(event: DragEvent): Boolean {
            val dropped = onImageDropped ?: return false
            return when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription?.hasMimeType(IMAGES) == true
                }

                DragEvent.ACTION_DROP -> {
                    val uri =
                        event.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.uri
                    // Reading another app's picture needs its permission.
                    context.activity()?.requestDragAndDropPermissions(event)
                    uri?.let { dropped(it, event.x, event.y) }
                    uri != null
                }

                else -> {
                    true
                }
            }
        }

        /** The pen new strokes are written with. */
        var pen: PenSettings
            get() = inking.tools.pen
            set(value) {
                inking.tools = inking.tools.copy(pen = value)
            }

        /** Whether fingers write too (then two fingers pan and zoom). */
        var fingerDraws: Boolean
            get() = inking.fingerDraws
            set(value) {
                inking.fingerDraws = value
            }

        /** Where the stroke being written is shown before it is committed. */
        var wetInk: WetInk
            get() = inking.wetInk
            set(value) {
                inking.wetInk = value
            }

        /** Called with each edit done here, e.g. a stroke written. */
        var onEdit: ((Edit) -> Unit)?
            get() = inking.onEdit
            set(value) {
                inking.onEdit = value
            }

        /** Called when writing shows a page's hidden layer again. */
        var onLayerView: ((index: Int, view: LayerView) -> Unit)?
            get() = inking.onLayerView
            set(value) {
                inking.onLayerView = value
            }

        /** What is selected (with the selection tool). */
        val selection: ViewSelection by lazy { ViewSelection(inking.selection, inking.selectionCommands) }

        /** Called when the selection changes, goes, or moves on screen. */
        var onSelection: (() -> Unit)?
            get() = inking.onSelection
            set(value) {
                inking.onSelection = value
            }

        /** Called with the page at the middle of the view, when it changes. */
        var onCurrentPage: ((Int) -> Unit)? = null
        private var lastCurrentPage = -1

        /** The page at the middle of the view (0 before there is one). */
        val currentPage: Int
            get() {
                val content = content ?: return 0
                val viewport = viewport ?: return pendingAnchor?.page ?: 0
                val (x, y) = viewport.viewToLayout(width / 2.0, height / 2.0)
                return content.layout.pageAt(x, y) ?: content.navigator.anchorOf(viewport).page
            }

        /** How each page's layers are shown: hidden ones aren't drawn, ink goes on the selected one. */
        var layerViews: List<LayerView>
            get() = content?.pages?.let { pages -> List(pages.size, pages::view) } ?: emptyList()
            set(value) {
                val content = content ?: return
                for (i in 0 until minOf(value.size, content.pages.size)) {
                    if (content.pages.view(i) == value[i].fitting(content.pages[i])) continue
                    if (viewport != null) {
                        inking.replacePage(i) { setView(i, value[i]) }
                    } else {
                        // Not shown yet: whatever was rendered of the page goes.
                        content.pages.setView(i, value[i])
                        val page = content.pages.rendered(i)
                        content.renderer.updatePage(i, page, Bounds(0.0, 0.0, page.width, page.height)) { false }
                    }
                }
            }

        /**
         * Shows [pages], the document after an edit or undo: pages changed in place are
         * redrawn where they changed; inserted, deleted or moved pages relayout the document,
         * keeping the tiles of the pages kept and the view on the same page.
         */
        fun showDocument(pages: List<Page>) {
            val content = content ?: return
            // The document changed under the selection.
            inking.selection.clear()
            val mapping = pageMapping(content.pages.all, pages)
            if (pages.size == content.pages.size && mapping.withIndex().all { (i, old) -> old == i || old == -1 }) {
                for (i in pages.indices) if (mapping[i] == -1) inking.replacePage(i) { this[i] = pages[i] }
                return
            }
            val anchor = anchor
            val (next, moved) = content.rearranged(pages)
            inking.reset()
            this.content = next
            pendingAnchor =
                anchor?.let {
                    val page = moved.getOrNull(it.page)?.takeIf { page -> page >= 0 }
                    if (page !=
                        null
                    ) {
                        it.copy(page = page)
                    } else {
                        it.copy(page = it.page.coerceAtMost(pages.lastIndex), pageY = 0.0)
                    }
                }
            viewport = null
            showViewport()
        }

        /** Scrolls to the top of page [index], at the same zoom. */
        fun scrollToPage(index: Int) {
            val anchor = anchor ?: return
            scroller.forceFinished(true)
            restore(anchor.copy(page = index.coerceIn(0, (content?.pages?.size ?: 1) - 1), pageY = 0.0))
        }

        /** The pages' versions (a copy), for [setDocument] with the same caches later. */
        val versions: IntArray
            get() {
                val renderer = content?.renderer ?: return IntArray(0)
                return IntArray(renderer.pageCount) { renderer.version(it) }
            }

        /**
         * Shows [pages] (their layers as in [layers]), rendered into [caches] by renderers from
         * [newRenderer]. [versions] are the pages' edit versions if [caches] were used before.
         * The previous document's renderer is closed.
         */
        fun setDocument(
            pages: List<Page>,
            caches: RenderCaches,
            newRenderer: () -> PageRenderer,
            threads: RenderThreads = RenderThreads(),
            versions: IntArray = IntArray(pages.size),
            layers: List<LayerView> = emptyList(),
        ) {
            content?.renderer?.close()
            inking.reset()
            inking.renderer = newRenderer()
            content = Content(ShownPages(pages, layers), caches, newRenderer, threads, versions) { invalidate() }
            viewport = null
            scroller.forceFinished(true)
            showViewport()
        }

        /** Stops background rendering. */
        fun close() {
            scroller.forceFinished(true)
            content?.renderer?.close()
            content = null
            viewport = null
        }

        /** The current position, for [restore] after the view is recreated (e.g. on rotation). */
        val anchor: ViewportAnchor?
            get() = viewport?.let { viewport -> content?.navigator?.anchorOf(viewport) } ?: pendingAnchor

        /** Goes back to [anchor], now or once the view has a size. */
        fun restore(anchor: ViewportAnchor) {
            pendingAnchor = anchor
            viewport = null
            showViewport()
        }

        /** Zooms to fit the page width, keeping the top of the view in place. */
        fun fitWidth() = zoomTo(0f, 0f) { navigator -> navigator.fitWidthZoom(width.toDouble()) }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            viewport?.let { pendingAnchor = content?.navigator?.anchorOf(it) }
            viewport = null
            showViewport()
        }

        private fun showViewport() {
            val content = content ?: return
            if (width == 0 || height == 0) return
            val w = width.toDouble()
            val h = height.toDouble()
            viewport = pendingAnchor?.let { content.navigator.restore(it, w, h) } ?: content.navigator.initial(w, h)
            pendingAnchor = null
            requestRendering()
            invalidate()
        }

        private inline fun update(change: (ViewportNavigator, Viewport) -> Viewport) {
            val content = content ?: return
            val current = viewport ?: return
            val next = change(content.navigator, current)
            if (next != current) {
                viewport = next
                inking.viewportChanged()
                requestRendering()
                invalidate()
            }
        }

        private fun requestRendering() {
            val content = content ?: return
            val viewport = viewport ?: return
            content.renderer.request(RenderPlan.jobs(content.layout, viewport, settled = !scaling))
            // The view moved: the page in the middle may be another one.
            val current = currentPage
            if (current != lastCurrentPage) {
                lastCurrentPage = current
                onCurrentPage?.invoke(current)
            }
        }

        override fun onDraw(canvas: Canvas) {
            val content = content
            val viewport = viewport
            if (content == null || viewport == null) {
                super.onDraw(canvas)
            } else {
                // Compose hosts views without clipping them: stay off the bars around.
                canvas.clipRect(0, 0, width, height)
                // Pages shown dark are drawn through the recolouring; what is over them is not.
                val again =
                    inking.draw(canvas, recolor) {
                        painter.draw(canvas, content.layout, content.caches, viewport, content.renderer::version)
                    }
                if (again) postInvalidateOnAnimation()
            }
        }

        // Touch

        override fun onTouchEvent(event: MotionEvent): Boolean {
            inking.record(event)
            val wasActive = inking.input.isActive
            if (!inking.input.onTouchEvent(event)) {
                feedGestures(event)
            } else if (inking.input.isActive && !wasActive) {
                // The stylus took over a finger gesture: end it.
                if (gestureActive) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    feedGestures(cancel)
                    cancel.recycle()
                }
                scroller.forceFinished(true)
                // Every input event as it comes, not once per frame.
                requestUnbufferedDispatch(event)
            }
            return true
        }

        override fun onHoverEvent(event: MotionEvent): Boolean {
            inking.hover(event)
            return super.onHoverEvent(event)
        }

        private fun feedGestures(event: MotionEvent) {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            gestureActive =
                event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        }

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        scaling = true
                        scroller.forceFinished(true)
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val focusX = detector.focusX.toDouble()
                        val focusY = detector.focusY.toDouble()
                        val factor = detector.scaleFactor.toDouble()
                        update { navigator, viewport -> navigator.zoomedAround(viewport, focusX, focusY, factor) }
                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        scaling = false
                        requestRendering()
                    }
                },
            ).apply {
                // Double tap toggles the zoom instead.
                isQuickScaleEnabled = false
            }

        private val gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        scroller.forceFinished(true)
                        return true
                    }

                    // With two fingers this follows their midpoint, so a pinch also pans.
                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float,
                    ): Boolean {
                        update {
                            navigator,
                            viewport,
                            ->
                            navigator.scrolledBy(viewport, distanceX.toDouble(), distanceY.toDouble())
                        }
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        val layout = content?.layout
                        val viewport = viewport
                        if (!scaling && layout != null && viewport != null) {
                            scroller.fling(layout, viewport, width, height, velocityX, velocityY)
                            postInvalidateOnAnimation()
                        }
                        return true
                    }

                    /** Fit width ↔ [DOUBLE_TAP_ZOOM] × fit width, around the tapped point. */
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        zoomTo(e.x, e.y) { navigator ->
                            val fit = navigator.fitWidthZoom(width.toDouble())
                            val current = viewport?.zoom ?: fit
                            if (abs(current - fit) / fit < FIT_TOLERANCE) fit * DOUBLE_TAP_ZOOM else fit
                        }
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()
                },
            )

        override fun performClick(): Boolean = super.performClick()

        private inline fun zoomTo(
            focusX: Float,
            focusY: Float,
            target: (ViewportNavigator) -> Double,
        ) {
            scroller.forceFinished(true)
            update { navigator, viewport ->
                navigator.zoomedAround(
                    viewport,
                    focusX.toDouble(),
                    focusY.toDouble(),
                    target(navigator) / viewport.zoom,
                )
            }
        }

        override fun computeScroll() {
            if (scroller.computeScrollOffset()) {
                val x = scroller.currX.toDouble()
                val y = scroller.currY.toDouble()
                update { navigator, viewport ->
                    navigator.clamped(
                        viewport.copy(
                            offsetX = x / viewport.zoom,
                            offsetY =
                                y / viewport.zoom,
                        ),
                    )
                }
                postInvalidateOnAnimation()
            }
        }

        companion object {
            /** Double tap at fit width zooms to this many times fit width. */
            const val DOUBLE_TAP_ZOOM = 2.5
            private const val FIT_TOLERANCE = 0.05
        }
    }

/** Flings from [viewport] of [layout] in a view [width] x [height], at the given velocities. */
@Suppress("LongParameterList") // the view's size and the velocity
private fun OverScroller.fling(
    layout: DocumentLayout,
    viewport: Viewport,
    width: Int,
    height: Int,
    velocityX: Float,
    velocityY: Float,
) {
    // The scroller works in view pixels from the layout origin.
    val zoom = viewport.zoom
    val x = ScrollRange.of((viewport.offsetX * zoom).roundToInt(), layout.width * zoom, width)
    val y = ScrollRange.of((viewport.offsetY * zoom).roundToInt(), layout.height * zoom, height)
    fling(x.start, y.start, -velocityX.roundToInt(), -velocityY.roundToInt(), x.min, x.max, y.min, y.max)
}

/** Fling limits along one axis, in view pixels. */
private class ScrollRange(
    val start: Int,
    val min: Int,
    val max: Int,
) {
    companion object {
        /** Content no larger than the view stays put (centred). */
        fun of(
            start: Int,
            content: Double,
            view: Int,
        ): ScrollRange =
            if (content <=
                view
            ) {
                ScrollRange(start, start, start)
            } else {
                ScrollRange(start, 0, (content - view).roundToInt())
            }
    }
}

/** The activity a view's context belongs to. */
private fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

private const val IMAGES = "image/*"
