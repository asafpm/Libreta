// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.geometry

import pm.asaf.libreta.core.model.Bounds

/**
 * What a view shows of a [DocumentLayout]: [zoom] pixels per point, and the layout point
 * ([offsetX], [offsetY]) at the view's top-left corner. View sizes are in pixels.
 */
data class Viewport(
    val zoom: Double,
    val offsetX: Double,
    val offsetY: Double,
    val viewWidth: Double,
    val viewHeight: Double,
) {
    /** The visible part of the layout, in layout units. */
    val visible: Bounds get() = Bounds(offsetX, offsetY, offsetX + viewWidth / zoom, offsetY + viewHeight / zoom)

    fun layoutToView(
        x: Double,
        y: Double,
    ): Pair<Double, Double> = Pair((x - offsetX) * zoom, (y - offsetY) * zoom)

    fun viewToLayout(
        x: Double,
        y: Double,
    ): Pair<Double, Double> = Pair(offsetX + x / zoom, offsetY + y / zoom)
}

/** A position that survives a change of view size: page, point at the view's top, relative zoom. */
data class ViewportAnchor(
    val page: Int,
    /** Layout y of the view's top edge, relative to the page's top, in points. */
    val pageY: Double,
    /** Layout x of the view's left edge, relative to the page's left, in points. */
    val pageX: Double,
    /** Zoom divided by the fit-width zoom. */
    val relativeZoom: Double,
)

/** Scrolling and zooming rules for a [DocumentLayout]. */
class ViewportNavigator(
    private val layout: DocumentLayout,
) {
    /** Zoom at which the widest page (with the gaps) fills the view's width. */
    fun fitWidthZoom(viewWidth: Double): Double = viewWidth / layout.width

    fun minZoom(viewWidth: Double): Double = fitWidthZoom(viewWidth) * MIN_ZOOM_FACTOR

    fun maxZoom(viewWidth: Double): Double = fitWidthZoom(viewWidth) * MAX_ZOOM_FACTOR

    /** Fit width, at the top of the document. */
    fun initial(
        viewWidth: Double,
        viewHeight: Double,
    ): Viewport = clamped(Viewport(fitWidthZoom(viewWidth), 0.0, 0.0, viewWidth, viewHeight))

    /** Moves the content by ([dx], [dy]) view pixels (positive values scroll towards the end). */
    fun scrolledBy(
        viewport: Viewport,
        dx: Double,
        dy: Double,
    ): Viewport =
        clamped(
            viewport.copy(
                offsetX = viewport.offsetX + dx / viewport.zoom,
                offsetY =
                    viewport.offsetY + dy / viewport.zoom,
            ),
        )

    /**
     * Multiplies the zoom by [factor] (within limits), keeping the layout point under the view
     * point (focusX, focusY) in place.
     */
    fun zoomedAround(
        viewport: Viewport,
        focusX: Double,
        focusY: Double,
        factor: Double,
    ): Viewport {
        val zoom = (viewport.zoom * factor).coerceIn(minZoom(viewport.viewWidth), maxZoom(viewport.viewWidth))
        val (focusLayoutX, focusLayoutY) = viewport.viewToLayout(focusX, focusY)
        return clamped(
            viewport.copy(
                zoom = zoom,
                offsetX = focusLayoutX - focusX / zoom,
                offsetY =
                    focusLayoutY - focusY / zoom,
            ),
        )
    }

    /** Keeps the content in view; content smaller than the view is centred. */
    fun clamped(viewport: Viewport): Viewport {
        val visibleWidth = viewport.viewWidth / viewport.zoom
        val visibleHeight = viewport.viewHeight / viewport.zoom
        return viewport.copy(
            offsetX = clampAxis(viewport.offsetX, visibleWidth, layout.width),
            offsetY = clampAxis(viewport.offsetY, visibleHeight, layout.height),
        )
    }

    private fun clampAxis(
        offset: Double,
        visible: Double,
        content: Double,
    ): Double = if (visible >= content) (content - visible) / 2 else offset.coerceIn(0.0, content - visible)

    fun anchorOf(viewport: Viewport): ViewportAnchor {
        val top = viewport.offsetY.coerceAtLeast(0.0)
        val page =
            layout.pagesIntersecting(top, top + viewport.viewHeight / viewport.zoom).firstOrNull()
                ?: (0 until layout.pageCount).lastOrNull { layout.pageBounds(it).top <= top }
                ?: 0
        val bounds = layout.pageBounds(page)
        return ViewportAnchor(
            page,
            viewport.offsetY - bounds.top,
            viewport.offsetX - bounds.left,
            viewport.zoom / fitWidthZoom(viewport.viewWidth),
        )
    }

    fun restore(
        anchor: ViewportAnchor,
        viewWidth: Double,
        viewHeight: Double,
    ): Viewport {
        if (layout.pageCount == 0) return initial(viewWidth, viewHeight)
        val bounds = layout.pageBounds(anchor.page.coerceIn(0, layout.pageCount - 1))
        val zoom = (fitWidthZoom(viewWidth) * anchor.relativeZoom).coerceIn(minZoom(viewWidth), maxZoom(viewWidth))
        return clamped(Viewport(zoom, bounds.left + anchor.pageX, bounds.top + anchor.pageY, viewWidth, viewHeight))
    }

    companion object {
        const val MIN_ZOOM_FACTOR = 0.25
        const val MAX_ZOOM_FACTOR = 8.0
    }
}
