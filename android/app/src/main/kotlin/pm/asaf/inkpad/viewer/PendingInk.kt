// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.viewer

import android.graphics.Canvas
import pm.asaf.inkpad.android.render.PageRenderer
import pm.asaf.inkpad.android.render.RenderCaches
import pm.asaf.inkpad.android.render.TileKey
import pm.asaf.inkpad.core.geometry.DocumentLayout
import pm.asaf.inkpad.core.geometry.TileGrid
import pm.asaf.inkpad.core.geometry.Viewport
import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Strokes just committed, drawn over the page until its tiles show them: the tiles touching a
 * stroke are re-rendered in the background, and the old ones stand in meanwhile.
 */
internal class PendingInk {
    private class Pending(
        val page: Int,
        /** The page version that contains the stroke. */
        val version: Int,
        val stroke: Element.Stroke,
        val bounds: Bounds,
    )

    private val strokes = ArrayList<Pending>()

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun add(
        page: Int,
        version: Int,
        stroke: Element.Stroke,
        bounds: Bounds,
    ) {
        strokes += Pending(page, version, stroke, bounds)
    }

    fun clear() = strokes.clear()

    /**
     * Draws the pending strokes with [renderer] (at the page positions the tiles use), then
     * forgets those whose visible tiles at the current zoom are rendered with them.
     */
    fun draw(
        canvas: Canvas,
        layout: DocumentLayout,
        viewport: Viewport,
        caches: RenderCaches,
        renderer: PageRenderer,
        version: (Int) -> Int,
    ) {
        if (strokes.isEmpty()) return
        val zoom = viewport.zoom.toFloat()
        for (pending in strokes) {
            val page = layout.pageBounds(pending.page)
            val saved = canvas.save()
            canvas.translate(
                ((page.left - viewport.offsetX) * viewport.zoom).roundToInt().toFloat(),
                ((page.top - viewport.offsetY) * viewport.zoom).roundToInt().toFloat(),
            )
            renderer.renderElements(listOf(pending.stroke), canvas, zoom)
            canvas.restoreToCount(saved)
        }
        strokes.removeAll { shown(it, layout, viewport, caches, version(it.page)) }
    }

    /** Whether every visible tile under [pending] at the current zoom includes it. */
    private fun shown(
        pending: Pending,
        layout: DocumentLayout,
        viewport: Viewport,
        caches: RenderCaches,
        current: Int,
    ): Boolean {
        val page = layout.pageBounds(pending.page)
        val visible = viewport.visible
        val clip =
            Bounds(
                max(visible.left - page.left, pending.bounds.left),
                max(visible.top - page.top, pending.bounds.top),
                min(visible.right - page.left, pending.bounds.right),
                min(visible.bottom - page.top, pending.bounds.bottom),
            )
        val zoom = viewport.zoom.toFloat()
        return TileGrid.tilesCovering(page.right - page.left, page.bottom - page.top, viewport.zoom, clip).all { tile ->
            val newest = caches.tiles.newest(TileKey(pending.page, current, zoom, tile.x, tile.y))
            (newest?.first?.version ?: -1) >= pending.version
        }
    }
}
