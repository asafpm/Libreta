// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.graphics.Canvas
import pm.asaf.libreta.android.render.PageRenderer
import pm.asaf.libreta.android.render.RenderCaches
import pm.asaf.libreta.android.render.TileKey
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.geometry.Viewport
import pm.asaf.libreta.core.geometry.expandedBy
import pm.asaf.libreta.core.geometry.intersects
import pm.asaf.libreta.core.model.Bounds
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.Page
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws with [draw] in page units of page [index], placed where the tiles draw it (whole pixels). */
internal inline fun Canvas.onPage(
    layout: DocumentLayout,
    viewport: Viewport,
    index: Int,
    draw: () -> Unit,
) {
    val placed = layout.pageBounds(index)
    val saved = save()
    translate(
        ((placed.left - viewport.offsetX) * viewport.zoom).roundToInt().toFloat(),
        ((placed.top - viewport.offsetY) * viewport.zoom).roundToInt().toFloat(),
    )
    draw()
    restoreToCount(saved)
}

/**
 * Page changes drawn over the tiles until the tiles show them: the tiles touching a change are
 * re-rendered in the background, and the old ones stand in meanwhile. A stroke just written is
 * drawn alone on top; other changes (erasing, undo) redraw their area from the page.
 */
internal class PendingChanges {
    private class Pending(
        val page: Int,
        /** The page version that contains the change. */
        val version: Int,
        val bounds: Bounds,
        /** A stroke added; null: the area is drawn from the page. */
        val stroke: Element.Stroke?,
    )

    private val changes = ArrayList<Pending>()

    val isEmpty: Boolean get() = changes.isEmpty()

    /** [stroke] was added to [page] at [version], within [bounds]. */
    fun addStroke(
        page: Int,
        version: Int,
        stroke: Element.Stroke,
        bounds: Bounds,
    ) {
        changes += Pending(page, version, bounds, stroke)
    }

    /** [page] changed within [bounds] at [version]. */
    fun addArea(
        page: Int,
        version: Int,
        bounds: Bounds,
    ) {
        // Strokes added earlier may have changed since: their areas are drawn from the page too.
        changes.replaceAll {
            if (it.page == page &&
                it.stroke != null
            ) {
                Pending(page, it.version, it.bounds, null)
            } else {
                it
            }
        }
        changes += Pending(page, version, bounds, null)
    }

    fun clear() = changes.clear()

    /**
     * Forgets the changes whose visible tiles at the current zoom are rendered with them, then
     * draws the others with [renderer] (at the page positions the tiles use): areas from the
     * pages ([pageAt]), then strokes.
     */
    fun draw(
        canvas: Canvas,
        layout: DocumentLayout,
        viewport: Viewport,
        caches: RenderCaches,
        renderer: PageRenderer,
        pageAt: (Int) -> Page,
        version: (Int) -> Int,
    ) {
        // The tiles drawn below may show some already; drawn again, translucent ink would darken.
        changes.removeAll { shown(it, layout, viewport, caches, version(it.page)) }
        if (changes.isEmpty()) return
        val zoom = viewport.zoom.toFloat()
        for (pending in changes.sortedBy { it.stroke != null }) {
            val clip = visiblePart(pending, layout.pageBounds(pending.page), viewport) ?: continue
            canvas.onPage(layout, viewport, pending.page) {
                if (pending.stroke != null) {
                    renderer.renderElements(listOf(pending.stroke), canvas, zoom)
                } else {
                    renderer.render(pageAt(pending.page), canvas, zoom, clip)
                }
            }
        }
    }

    /** The part of [pending] on screen, in page units, or null if none. */
    private fun visiblePart(
        pending: Pending,
        placed: Bounds,
        viewport: Viewport,
    ): Bounds? {
        val visible = viewport.visible
        // Anti-aliasing reaches a little beyond what changed.
        val bounds = pending.bounds.expandedBy(AA_MARGIN / viewport.zoom)
        val onScreen =
            Bounds(
                max(visible.left - placed.left, bounds.left),
                max(visible.top - placed.top, bounds.top),
                min(visible.right - placed.left, bounds.right),
                min(visible.bottom - placed.top, bounds.bottom),
            )
        return onScreen.takeIf { it.intersects(bounds) }
    }

    /** Whether every visible tile under [pending] at the current zoom includes it. */
    private fun shown(
        pending: Pending,
        layout: DocumentLayout,
        viewport: Viewport,
        caches: RenderCaches,
        current: Int,
    ): Boolean {
        val placed = layout.pageBounds(pending.page)
        val clip = visiblePart(pending, placed, viewport) ?: return true
        val zoom = viewport.zoom.toFloat()
        return TileGrid.tilesCovering(placed.right - placed.left, placed.bottom - placed.top, viewport.zoom, clip).all {
            val newest = caches.tiles.newest(TileKey(pending.page, current, zoom, it.x, it.y))
            (newest?.first?.version ?: -1) >= pending.version
        }
    }

    private companion object {
        /** View pixels around a change that anti-aliasing may touch. */
        const val AA_MARGIN = 1.5
    }
}
