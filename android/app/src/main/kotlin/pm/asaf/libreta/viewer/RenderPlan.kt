// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import pm.asaf.libreta.android.render.RenderJob
import pm.asaf.libreta.core.geometry.DocumentLayout
import pm.asaf.libreta.core.geometry.TileGrid
import pm.asaf.libreta.core.geometry.Viewport
import pm.asaf.libreta.core.model.Bounds
import kotlin.math.hypot

/** What to render for a viewport, most important first. */
internal object RenderPlan {
    /** Thumbnails are prepared for this many pages before and after the visible ones. */
    private const val NEIGHBOURS = 2

    /**
     * Thumbnails of the visible pages, tiles of the visible area at the current zoom (centre
     * first), thumbnails of the pages around, and tiles a screen above and below. Tiles only
     * once [settled]: while pinching, the zoom is about to change.
     */
    fun jobs(
        layout: DocumentLayout,
        viewport: Viewport,
        settled: Boolean,
    ): List<RenderJob> {
        val visible = viewport.visible
        val pages = layout.pagesIntersecting(visible.top, visible.bottom)
        val jobs = ArrayList<RenderJob>()
        pages.mapTo(jobs) { RenderJob.ThumbnailJob(it) }
        if (settled) jobs += tileJobs(layout, viewport, visible)
        if (!pages.isEmpty()) {
            val first = (pages.first - NEIGHBOURS).coerceAtLeast(0)
            val last = (pages.last + NEIGHBOURS).coerceAtMost(layout.pageCount - 1)
            (first..last).filter { it !in pages }.mapTo(jobs) { RenderJob.ThumbnailJob(it) }
        }
        if (settled) {
            val screen = visible.bottom - visible.top
            jobs +=
                tileJobs(layout, viewport, visible.copy(top = visible.top - screen, bottom = visible.bottom + screen))
        }
        return jobs
    }

    /** Tiles at the current zoom covering [area] (layout units), nearest the view's centre first. */
    private fun tileJobs(
        layout: DocumentLayout,
        viewport: Viewport,
        area: Bounds,
    ): List<RenderJob.TileJob> {
        val zoom = viewport.zoom.toFloat()
        val visible = viewport.visible
        val centreX = (visible.left + visible.right) / 2
        val centreY = (visible.top + visible.bottom) / 2
        val jobs =
            layout.pagesIntersecting(area.top, area.bottom).flatMap { index ->
                val page = layout.pageBounds(index)
                val clip =
                    Bounds(
                        area.left - page.left,
                        area.top - page.top,
                        area.right - page.left,
                        area.bottom - page.top,
                    )
                val tiles =
                    TileGrid.tilesCovering(
                        page.right - page.left,
                        page.bottom - page.top,
                        zoom.toDouble(),
                        clip,
                    )
                tiles.map { tile ->
                    val x = page.left + (tile.bounds.left + tile.bounds.right) / 2
                    val y = page.top + (tile.bounds.top + tile.bounds.bottom) / 2
                    hypot(x - centreX, y - centreY) to RenderJob.TileJob(index, zoom, tile)
                }
            }
        return jobs.sortedBy { it.first }.map { it.second }
    }
}
