// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.RenderThreads
import pm.asaf.inkpad.android.render.TileRenderer
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.Size
import pm.asaf.inkpad.core.geometry.Viewport
import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.core.geometry.ViewportNavigator
import pm.asaf.inkpad.core.model.Page
import pm.asaf.inkpad.core.model.PenSettings
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
        private class Content(
            pages: List<Page>,
            val caches: RenderCaches,
            val renderer: TileRenderer,
        ) {
            val layout = DocumentLayout(pages.map { Size(it.width, it.height) })
            val navigator = ViewportNavigator(layout)
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

        /** The pen new strokes are written with. */
        var pen: PenSettings
            get() = inking.pen
            set(value) {
                inking.pen = value
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

        /** Called with each page as a stroke is added to it, and the page's new version. */
        var onPageEdited: ((index: Int, page: Page, version: Int) -> Unit)?
            get() = inking.onPageEdited
            set(value) {
                inking.onPageEdited = value
            }

        /**
         * Shows [pages], rendered into [caches] by renderers from [newRenderer]. [versions] are
         * the pages' edit versions if [caches] were used before. The previous document's
         * renderer is closed.
         */
        fun setDocument(
            pages: List<Page>,
            caches: RenderCaches,
            newRenderer: () -> PageRenderer,
            threads: RenderThreads = RenderThreads(),
            versions: IntArray = IntArray(pages.size),
        ) {
            content?.renderer?.close()
            inking.reset()
            inking.renderer = newRenderer()
            content =
                Content(pages, caches, TileRenderer(pages, caches, newRenderer, { invalidate() }, threads, versions))
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
                requestRendering()
                invalidate()
            }
        }

        private fun requestRendering() {
            val content = content ?: return
            val viewport = viewport ?: return
            content.renderer.request(RenderPlan.jobs(content.layout, viewport, settled = !scaling))
        }

        override fun onDraw(canvas: Canvas) {
            val content = content
            val viewport = viewport
            if (content == null || viewport == null) {
                super.onDraw(canvas)
            } else {
                // Compose hosts views without clipping them: stay off the bars around.
                canvas.clipRect(0, 0, width, height)
                painter.draw(canvas, content.layout, content.caches, viewport, content.renderer::version)
                if (inking.draw(canvas)) postInvalidateOnAnimation()
            }
        }

        // Touch

        override fun onTouchEvent(event: MotionEvent): Boolean {
            inking.record(event)
            val wasWriting = inking.input.isWriting
            if (!inking.input.onTouchEvent(event)) {
                feedGestures(event)
            } else if (inking.input.isWriting && !wasWriting) {
                // The stylus took over a finger gesture: end it.
                if (gestureActive) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    feedGestures(cancel)
                    cancel.recycle()
                }
                scroller.forceFinished(true)
                inking.strokeStarted()
                // Every input event as it comes, not once per frame.
                requestUnbufferedDispatch(event)
            }
            return true
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
                        if (!scaling) fling(velocityX, velocityY)
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

        private fun fling(
            velocityX: Float,
            velocityY: Float,
        ) {
            val layout = content?.layout ?: return
            val viewport = viewport ?: return
            // The scroller works in view pixels from the layout origin.
            val zoom = viewport.zoom
            val x = ScrollRange.of((viewport.offsetX * zoom).roundToInt(), layout.width * zoom, width)
            val y = ScrollRange.of((viewport.offsetY * zoom).roundToInt(), layout.height * zoom, height)
            scroller.fling(
                x.start,
                y.start,
                -velocityX.roundToInt(),
                -velocityY.roundToInt(),
                x.min,
                x.max,
                y.min,
                y.max,
            )
            postInvalidateOnAnimation()
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
